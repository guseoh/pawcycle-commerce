#!/usr/bin/env python3
"""Run the AUTH-006 anonymous CSRF session baseline in an isolated local runtime."""

from __future__ import annotations

import argparse
import http.cookiejar
import json
import math
import os
import platform
import re
import subprocess
import sys
import tempfile
import time
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable


WORKLOAD_IDENTITY = "auth006-anonymous-csrf-session-baseline-local-v1"
PROJECT_NAME = "pawcycle-auth006-csrf-session-measurement"
MYSQL_VOLUME = "pawcycle-auth006-csrf-session-measurement-mysql-data"
CSRF_PATH = "/api/auth/csrf"
DEFAULT_REQUEST_COUNT = 50
DEFAULT_WARMUP_REQUESTS = 3
MIN_REQUEST_COUNT = 10
MAX_REQUEST_COUNT = 200
HTTP_TIMEOUT_SECONDS = 5
REPO_ROOT = Path(__file__).resolve().parents[3]
LOCAL_INTEGRATION_DIR = REPO_ROOT / "infra" / "local-integration"
BASE_COMPOSE = LOCAL_INTEGRATION_DIR / "compose.yaml"
OVERLAY_COMPOSE = LOCAL_INTEGRATION_DIR / "compose.auth-csrf-session-measurement.yaml"
PRODUCTION_PROPERTIES = REPO_ROOT / "backend" / "src" / "main" / "resources" / "application.properties"
PRODUCTION_INFRA = REPO_ROOT / "infra" / "production"

SYNTHETIC_ENV = {
    "MYSQL_DATABASE": "pawcycle_auth006",
    "MYSQL_USER": "pawcycle_auth006",
    "MYSQL_PASSWORD": "auth006-local-not-a-secret",
    "MYSQL_ROOT_PASSWORD": "auth006-root-local-not-a-secret",
    "PAWCYCLE_LOCAL_QA_BOOTSTRAP_EMAIL": "auth006@example.invalid",
    "PAWCYCLE_LOCAL_QA_BOOTSTRAP_PASSWORD": "auth006-local-not-a-secret",
}

SAMPLE_PATTERN = re.compile(
    r"^(?P<name>[a-zA-Z_:][a-zA-Z0-9_:]*)(?:\{(?P<labels>[^}]*)\})?\s+"
    r"(?P<value>[-+]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][-+]?\d+)?|NaN|[+-]Inf)(?:\s+\d+)?$"
)
LABEL_PATTERN = re.compile(r'(\w+)="((?:\\.|[^"\\])*)"')
FORBIDDEN_EVIDENCE_KEY = re.compile(
    r"csrf.?token|jsessionid|session.?id|authorization|cookie|password|credential|secret",
    re.IGNORECASE,
)
FORBIDDEN_EVIDENCE_VALUE = re.compile(
    r"JSESSIONID=|X-CSRF-TOKEN|Authorization:|Cookie:", re.IGNORECASE
)


@dataclass(frozen=True)
class MetricSample:
    name: str
    labels: dict[str, str]
    value: float


def compose_command(*arguments: str) -> list[str]:
    return [
        "docker",
        "compose",
        "-f",
        BASE_COMPOSE.name,
        "-f",
        OVERLAY_COMPOSE.name,
        *arguments,
    ]


def isolated_environment() -> dict[str, str]:
    environment = os.environ.copy()
    environment.update(SYNTHETIC_ENV)
    environment["PAWCYCLE_LOCAL_HTTP_PORT"] = "0"
    return environment


def run_command(arguments: list[str], *, timeout: int = 900) -> str:
    try:
        completed = subprocess.run(
            arguments,
            cwd=LOCAL_INTEGRATION_DIR,
            env=isolated_environment(),
            capture_output=True,
            text=True,
            timeout=timeout,
            check=False,
        )
    except FileNotFoundError as exception:
        raise RuntimeError(f"Required command is unavailable: {arguments[0]}") from exception
    except subprocess.TimeoutExpired as exception:
        raise RuntimeError(f"Command timed out: {arguments[0]} {arguments[1]}") from exception
    if completed.returncode != 0:
        raise RuntimeError(
            f"Command failed with exit code {completed.returncode}: {arguments[0]} {arguments[1]}"
        )
    return completed.stdout.strip()


def load_compose_model() -> dict[str, Any]:
    raw = run_command(compose_command("config", "--format", "json"), timeout=60)
    try:
        return json.loads(raw)
    except json.JSONDecodeError as exception:
        raise RuntimeError("Docker Compose returned an invalid JSON model") from exception


def validate_compose_model(model: dict[str, Any]) -> None:
    if model.get("name") != PROJECT_NAME:
        raise ValueError("Measurement Compose project identity is not isolated")
    services = model.get("services", {})
    backend = services.get("backend", {})
    environment = backend.get("environment", {})
    if environment.get("SERVER_TOMCAT_MBEANREGISTRY_ENABLED") != "true":
        raise ValueError("Measurement Tomcat MBean registry is not enabled")
    if environment.get("PAWCYCLE_LOCAL_QA_BOOTSTRAP_ENABLED") != "false":
        raise ValueError("Local QA bootstrap must remain disabled")
    ports = backend.get("ports", [])
    loopback_ports = [
        port
        for port in ports
        if int(port.get("target", 0)) == 8080 and port.get("host_ip") == "127.0.0.1"
    ]
    if len(loopback_ports) != 1:
        raise ValueError("Measurement Backend must publish exactly one loopback-only port")
    volume = model.get("volumes", {}).get("mysql-data", {})
    if volume.get("name") != MYSQL_VOLUME:
        raise ValueError("Measurement MySQL volume is not dedicated")


def validate_repository_contract() -> None:
    overlay = OVERLAY_COMPOSE.read_text(encoding="utf-8")
    if 'SERVER_TOMCAT_MBEANREGISTRY_ENABLED: "true"' not in overlay:
        raise AssertionError("Measurement-only Tomcat metric setting is missing")
    forbidden_settings = (
        "server.tomcat.mbeanregistry.enabled",
        "server_tomcat_mbeanregistry_enabled",
    )
    if any(
        setting in PRODUCTION_PROPERTIES.read_text(encoding="utf-8").lower()
        for setting in forbidden_settings
    ):
        raise AssertionError("Production application defaults enable the Tomcat MBean registry")
    for path in PRODUCTION_INFRA.rglob("*"):
        if path.is_file() and path.suffix.lower() in {".yaml", ".yml", ".properties", ".sh"}:
            production_text = path.read_text(encoding="utf-8", errors="ignore").lower()
            if any(setting in production_text for setting in forbidden_settings):
                raise AssertionError(f"Production runtime enables measurement setting: {path}")


def parse_prometheus_samples(payload: str) -> list[MetricSample]:
    samples: list[MetricSample] = []
    for raw_line in payload.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        match = SAMPLE_PATTERN.match(line)
        if not match:
            continue
        value = float(match.group("value"))
        if not math.isfinite(value):
            continue
        labels = {
            label.group(1): bytes(label.group(2), "utf-8").decode("unicode_escape")
            for label in LABEL_PATTERN.finditer(match.group("labels") or "")
        }
        samples.append(MetricSample(match.group("name"), labels, value))
    return samples


def discover_session_metric_names(samples: Iterable[MetricSample]) -> dict[str, str]:
    names = {sample.name for sample in samples if sample.name.startswith("tomcat_sessions_")}
    created = sorted(name for name in names if "created" in name)
    active = sorted(name for name in names if "active" in name and "current" in name)
    if len(created) != 1 or len(active) != 1:
        raise ValueError("Required Tomcat session metric names are missing or ambiguous")
    return {"created": created[0], "activeCurrent": active[0]}


def metric_sum(
    samples: Iterable[MetricSample], metric_name: str, required_labels: dict[str, str] | None = None
) -> float:
    required_labels = required_labels or {}
    values = [
        sample.value
        for sample in samples
        if sample.name == metric_name
        and all(sample.labels.get(key) == value for key, value in required_labels.items())
    ]
    if not values:
        raise ValueError(f"Required runtime metric is unavailable: {metric_name}")
    return sum(values)


def fetch_text(url: str) -> str:
    with urllib.request.urlopen(url, timeout=HTTP_TIMEOUT_SECONDS) as response:
        if response.status != 200:
            raise RuntimeError(f"Unexpected HTTP status while reading local runtime: {response.status}")
        return response.read().decode("utf-8")


def metric_snapshot(base_url: str, metric_names: dict[str, str]) -> dict[str, float | None]:
    samples = parse_prometheus_samples(fetch_text(f"{base_url}/actuator/prometheus"))
    return {
        "sessionCreated": metric_sum(samples, metric_names["created"]),
        "sessionActiveCurrent": metric_sum(samples, metric_names["activeCurrent"]),
        "jvmHeapUsedBytes": metric_sum(samples, "jvm_memory_used_bytes", {"area": "heap"}),
        "processCpuUsage": metric_sum(samples, "process_cpu_usage"),
    }


def csrf_request(opener: urllib.request.OpenerDirector, base_url: str) -> bool:
    request = urllib.request.Request(f"{base_url}{CSRF_PATH}", method="GET")
    try:
        with opener.open(request, timeout=HTTP_TIMEOUT_SECONDS) as response:
            if response.status != 200:
                return False
            payload = json.loads(response.read().decode("utf-8"))
            return isinstance(payload, dict) and isinstance(payload.get("token"), str) and bool(
                payload["token"]
            )
    except Exception:
        return False


def run_cohort(base_url: str, request_count: int, *, reuse_session: bool) -> dict[str, Any]:
    successes = 0
    started = time.perf_counter()
    if reuse_session:
        cookie_jar = http.cookiejar.CookieJar()
        opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookie_jar))
        for _ in range(request_count):
            successes += int(csrf_request(opener, base_url))
        if not any(cookie.name == "JSESSIONID" for cookie in cookie_jar):
            raise RuntimeError("Reused cohort did not retain an anonymous session cookie")
    else:
        for _ in range(request_count):
            opener = urllib.request.build_opener()
            successes += int(csrf_request(opener, base_url))
    duration_ms = round((time.perf_counter() - started) * 1000, 3)
    return {
        "requestCount": request_count,
        "successCount": successes,
        "failureCount": request_count - successes,
        "durationMs": duration_ms,
    }


def delta(before: float | None, after: float | None) -> float | None:
    if before is None or after is None:
        return None
    return after - before


def cohort_evidence(
    workload: dict[str, Any], before: dict[str, float | None], after: dict[str, float | None]
) -> dict[str, Any]:
    return {
        **workload,
        "sessionCreatedDelta": delta(before["sessionCreated"], after["sessionCreated"]),
        "sessionActiveCurrentBefore": before["sessionActiveCurrent"],
        "sessionActiveCurrentAfter": after["sessionActiveCurrent"],
        "sessionActiveCurrentDelta": delta(
            before["sessionActiveCurrent"], after["sessionActiveCurrent"]
        ),
        "jvmHeapUsedBytesBefore": before["jvmHeapUsedBytes"],
        "jvmHeapUsedBytesAfter": after["jvmHeapUsedBytes"],
        "jvmHeapUsedBytesDelta": delta(before["jvmHeapUsedBytes"], after["jvmHeapUsedBytes"]),
        "processCpuUsageBefore": before["processCpuUsage"],
        "processCpuUsageAfter": after["processCpuUsage"],
    }


def validate_evidence_privacy(value: Any, path: str = "root") -> None:
    if isinstance(value, dict):
        for key, nested in value.items():
            if FORBIDDEN_EVIDENCE_KEY.search(str(key)):
                raise ValueError(f"Sensitive evidence key rejected at {path}")
            validate_evidence_privacy(nested, f"{path}.{key}")
    elif isinstance(value, list):
        for index, nested in enumerate(value):
            validate_evidence_privacy(nested, f"{path}[{index}]")
    elif isinstance(value, str) and FORBIDDEN_EVIDENCE_VALUE.search(value):
        raise ValueError(f"Sensitive evidence value rejected at {path}")


def validate_measurement_summary(summary: dict[str, Any]) -> None:
    fresh = summary["cohorts"]["fresh"]
    reused = summary["cohorts"]["reused"]
    if fresh["failureCount"] != 0 or reused["failureCount"] != 0:
        raise ValueError("All cohort requests must succeed")
    if fresh["requestCount"] != reused["requestCount"]:
        raise ValueError("Fresh and reused cohorts must have the same request count")
    if fresh["sessionCreatedDelta"] != fresh["successCount"]:
        raise ValueError("Fresh cohort session-created delta does not match successful requests")
    if reused["sessionCreatedDelta"] != 1:
        raise ValueError("Reused cohort must create exactly one session")
    if fresh["sessionCreatedDelta"] <= reused["sessionCreatedDelta"]:
        raise ValueError("Fresh cohort did not create more sessions than reused cohort")
    validate_evidence_privacy(summary)


def synthetic_compose_model() -> dict[str, Any]:
    return {
        "name": PROJECT_NAME,
        "services": {
            "backend": {
                "environment": {
                    "SERVER_TOMCAT_MBEANREGISTRY_ENABLED": "true",
                    "PAWCYCLE_LOCAL_QA_BOOTSTRAP_ENABLED": "false",
                },
                "ports": [{"target": 8080, "host_ip": "127.0.0.1", "published": "0"}],
            }
        },
        "volumes": {"mysql-data": {"name": MYSQL_VOLUME}},
    }


def synthetic_summary() -> dict[str, Any]:
    cohort = {
        "requestCount": 10,
        "successCount": 10,
        "failureCount": 0,
        "durationMs": 10.0,
        "sessionCreatedDelta": 10,
        "sessionActiveCurrentBefore": 1,
        "sessionActiveCurrentAfter": 11,
        "sessionActiveCurrentDelta": 10,
        "jvmHeapUsedBytesBefore": 100,
        "jvmHeapUsedBytesAfter": 110,
        "jvmHeapUsedBytesDelta": 10,
        "processCpuUsageBefore": 0.01,
        "processCpuUsageAfter": 0.02,
    }
    reused = {**cohort, "sessionCreatedDelta": 1, "sessionActiveCurrentDelta": 1}
    return {
        "schemaVersion": 1,
        "workloadIdentity": WORKLOAD_IDENTITY,
        "metricNames": {
            "sessionCreated": "tomcat_sessions_created_sessions_total",
            "sessionActiveCurrent": "tomcat_sessions_active_current_sessions",
            "jvmHeapUsed": "jvm_memory_used_bytes",
            "processCpuUsage": "process_cpu_usage",
        },
        "cohorts": {"fresh": cohort, "reused": reused},
    }


def run_synthetic_validation() -> None:
    samples = parse_prometheus_samples(
        "tomcat_sessions_created_sessions_total 11\n"
        "tomcat_sessions_active_current_sessions 11\n"
        'jvm_memory_used_bytes{area="heap",id="G1 Old Gen"} 100\n'
    )
    names = discover_session_metric_names(samples)
    if names["created"] != "tomcat_sessions_created_sessions_total":
        raise AssertionError("Synthetic metric discovery failed")
    validate_compose_model(synthetic_compose_model())
    validate_measurement_summary(synthetic_summary())
    missing_rejected = False
    try:
        discover_session_metric_names(
            parse_prometheus_samples("tomcat_sessions_active_current_sessions 1\n")
        )
    except ValueError:
        missing_rejected = True
    if not missing_rejected:
        raise AssertionError("Missing session-created metric was not rejected")
    unsafe = synthetic_summary()
    unsafe["csrfToken"] = "sentinel-sensitive-value"
    privacy_rejected = False
    try:
        validate_measurement_summary(unsafe)
    except ValueError:
        privacy_rejected = True
    if not privacy_rejected:
        raise AssertionError("Sensitive evidence was not rejected")


def assert_safe_results_directory(results_directory: Path) -> Path:
    resolved = results_directory.resolve()
    temp_root = Path(tempfile.gettempdir()).resolve()
    try:
        resolved.relative_to(temp_root)
    except ValueError as exception:
        raise ValueError("Results directory must be inside the host temporary directory") from exception
    return resolved


def compose_down() -> None:
    run_command(compose_command("down", "--volumes", "--remove-orphans"), timeout=180)


def wait_for_backend() -> str:
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        container_id = run_command(compose_command("ps", "--quiet", "backend"), timeout=30)
        if container_id:
            health = run_command(
                ["docker", "inspect", "--format", "{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}", container_id],
                timeout=30,
            )
            if health == "healthy":
                published = run_command(compose_command("port", "backend", "8080"), timeout=30)
                match = re.search(r":(?P<port>\d+)$", published.splitlines()[0])
                if not match:
                    raise RuntimeError("Measurement Backend loopback port is unavailable")
                return f"http://127.0.0.1:{match.group('port')}"
        time.sleep(2)
    raise RuntimeError("Measurement Backend did not become healthy")


def discover_runtime_metrics(base_url: str) -> dict[str, str]:
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        samples = parse_prometheus_samples(fetch_text(f"{base_url}/actuator/prometheus"))
        try:
            return discover_session_metric_names(samples)
        except ValueError:
            time.sleep(1)
    raise RuntimeError("Tomcat session metrics were not exposed by the measurement runtime")


def execute_measurement(request_count: int, warmup_requests: int) -> dict[str, Any]:
    model = load_compose_model()
    validate_compose_model(model)
    print("Preparing isolated AUTH-006 local runtime")
    compose_down()
    cleanup_error: Exception | None = None
    summary: dict[str, Any] | None = None
    try:
        run_command(compose_command("up", "--build", "--detach", "mysql", "redis", "backend"))
        base_url = wait_for_backend()
        warmup = run_cohort(base_url, warmup_requests, reuse_session=True)
        if warmup["failureCount"] != 0:
            raise RuntimeError("Warm-up requests failed")
        metric_names = discover_runtime_metrics(base_url)

        reused_before = metric_snapshot(base_url, metric_names)
        reused_workload = run_cohort(base_url, request_count, reuse_session=True)
        reused_after = metric_snapshot(base_url, metric_names)

        fresh_before = reused_after
        fresh_workload = run_cohort(base_url, request_count, reuse_session=False)
        fresh_after = metric_snapshot(base_url, metric_names)

        reused = cohort_evidence(reused_workload, reused_before, reused_after)
        fresh = cohort_evidence(fresh_workload, fresh_before, fresh_after)
        summary = {
            "schemaVersion": 1,
            "workloadIdentity": WORKLOAD_IDENTITY,
            "executionScope": "local-isolated",
            "endpointPath": CSRF_PATH,
            "executionOrder": ["reused", "fresh"],
            "requestCountPerCohort": request_count,
            "warmupRequestCount": warmup_requests,
            "measurementMetricSetting": "local-compose-overlay-only",
            "metricNames": {
                "sessionCreated": metric_names["created"],
                "sessionActiveCurrent": metric_names["activeCurrent"],
                "jvmHeapUsed": "jvm_memory_used_bytes",
                "processCpuUsage": "process_cpu_usage",
            },
            "runtime": {
                "dockerServerVersion": run_command(
                    ["docker", "version", "--format", "{{.Server.Version}}"], timeout=30
                ),
                "hostPlatform": platform.platform(),
                "pythonVersion": platform.python_version(),
            },
            "cohorts": {"fresh": fresh, "reused": reused},
            "comparison": {
                "sessionCreatedDeltaDifference": fresh["sessionCreatedDelta"]
                - reused["sessionCreatedDelta"],
                "sessionCreatedDeltaRatio": fresh["sessionCreatedDelta"]
                / reused["sessionCreatedDelta"],
            },
            "evidenceContainsSensitiveValues": False,
            "capacityOrSloConclusion": False,
        }
        validate_measurement_summary(summary)
    finally:
        try:
            compose_down()
        except Exception as exception:
            cleanup_error = exception
    if cleanup_error is not None:
        raise RuntimeError("Isolated measurement runtime cleanup failed") from cleanup_error
    if summary is None:
        raise RuntimeError("Measurement did not produce aggregate evidence")
    return summary


def write_summary(results_directory: Path, summary: dict[str, Any]) -> Path:
    results_directory.mkdir(parents=True, exist_ok=True)
    target = results_directory / "auth006-csrf-session-baseline.json"
    temporary = target.with_suffix(".json.tmp")
    temporary.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temporary.replace(target)
    return target


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--validate-only", action="store_true")
    mode.add_argument("--run", action="store_true")
    mode.add_argument("--cleanup", action="store_true")
    parser.add_argument("--request-count", type=int, default=DEFAULT_REQUEST_COUNT)
    parser.add_argument("--warmup-requests", type=int, default=DEFAULT_WARMUP_REQUESTS)
    parser.add_argument(
        "--results-dir",
        type=Path,
        default=Path(tempfile.gettempdir()) / "pawcycle-auth006-csrf-session",
    )
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    if not MIN_REQUEST_COUNT <= arguments.request_count <= MAX_REQUEST_COUNT:
        raise ValueError(
            f"request-count must be between {MIN_REQUEST_COUNT} and {MAX_REQUEST_COUNT}"
        )
    if not 1 <= arguments.warmup_requests <= 10:
        raise ValueError("warmup-requests must be between 1 and 10")
    validate_repository_contract()
    run_synthetic_validation()
    if arguments.validate_only:
        print("AUTH-006 measurement harness synthetic and fail-closed validation passed")
        return 0
    compose_model = load_compose_model()
    validate_compose_model(compose_model)
    if arguments.cleanup:
        compose_down()
        print("AUTH-006 isolated measurement runtime cleanup completed")
        return 0
    results_directory = assert_safe_results_directory(arguments.results_dir)
    summary = execute_measurement(arguments.request_count, arguments.warmup_requests)
    output = write_summary(results_directory, summary)
    fresh = summary["cohorts"]["fresh"]
    reused = summary["cohorts"]["reused"]
    print(
        "AUTH-006 local baseline completed: "
        f"requests={arguments.request_count}, fresh-created={fresh['sessionCreatedDelta']}, "
        f"reused-created={reused['sessionCreatedDelta']}"
    )
    print(f"Aggregate evidence: {output}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, RuntimeError, ValueError) as error:
        print(f"AUTH-006 measurement failed closed: {error}", file=sys.stderr)
        raise SystemExit(1)
