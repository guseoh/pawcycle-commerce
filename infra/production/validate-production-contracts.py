#!/usr/bin/env python3
"""Validate the provider-neutral Production repository boundary after AWS retirement."""

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
PRODUCTION = ROOT / "infra" / "production"
WORKFLOWS = ROOT / ".github" / "workflows"
RUNBOOKS = ROOT / "docs" / "runbook"
ARCHITECTURE = ROOT / "docs" / "architecture" / "production-operations-overview.md"
VALIDATOR = Path(__file__).resolve()
OBSERVABILITY_COMPOSE = ROOT / "infra" / "production-observability" / "compose.yaml"
METRICS_PROXY_COMPOSE = ROOT / "infra" / "production-metrics-proxy" / "compose.yaml"
METRICS_PROXY_CONFIG = ROOT / "infra" / "production-metrics-proxy" / "metrics-proxy.conf"
GRAFANA_DATASOURCE = ROOT / "infra" / "production-observability" / "grafana" / "provisioning" / "datasources" / "prometheus.yaml"
OBSERVABILITY_ADR = ROOT / "docs" / "adr" / "ARCH-012-production-observability-boundary.md"
OBSERVABILITY_RUNBOOK = RUNBOOKS / "OPS-OBS-001-production-observability.md"

RETIRED_PATHS = (
    WORKFLOWS / "production-deploy.yml",
    WORKFLOWS / "validate-rds-automation-preflight.yml",
    WORKFLOWS / "validate-same-sha-runtime-activation.yml",
    PRODUCTION / "materialize-ssm-env.sh",
    PRODUCTION / "ssm-parameters.env.example",
    PRODUCTION / "pawcycle-production-deploy-ssm-document.json",
    PRODUCTION / "validate-production-ssm-document.py",
    PRODUCTION / "ec2-status-check-alarm-common.sh",
    PRODUCTION / "create-ec2-status-check-alarm.sh",
    PRODUCTION / "cleanup-ec2-status-check-alarm.sh",
    PRODUCTION / "test-ec2-status-check-alarm.sh",
    PRODUCTION / "rds-read-only-preflight.sh",
    PRODUCTION / "rds-transition-gate.sh",
    PRODUCTION / "test-rds-readiness.sh",
    PRODUCTION / "db-backup-restore.sh",
    PRODUCTION / "test-db-backup-restore.sh",
    PRODUCTION / "release-common.sh",
    PRODUCTION / "deploy.sh",
    PRODUCTION / "rollback.sh",
    PRODUCTION / "https.sh",
    PRODUCTION / "production-db-restore.sh",
    PRODUCTION / "subscription-automation-control.sh",
    PRODUCTION / "subscription-automation-preflight.sh",
    PRODUCTION / "test-subscription-automation-preflight-datasource.sh",
    PRODUCTION / "test-production-scripts.sh",
    PRODUCTION / "test-rollback-control-compatibility.sh",
    PRODUCTION / "test-same-sha-runtime-activation.sh",
    PRODUCTION / "release.env.example",
    RUNBOOKS / "OPS-009-aws-operations-foundation.md",
    RUNBOOKS / "OPS-010-production-single-release.md",
    RUNBOOKS / "OPS-011-production-https.md",
    RUNBOOKS / "OPS-013-production-db-backup-restore.md",
    RUNBOOKS / "OPS-015-ec2-status-check-alarm.md",
    RUNBOOKS / "OPS-025-production-db-restore.md",
    RUNBOOKS / "OPS-AUTO-007-production-ssm-document-rollback.md",
    RUNBOOKS / "OPS-DB-002-rds-migration-cutover.md",
    RUNBOOKS / "SUB-AUTO-002-production-subscription-automation.md",
)

AWS_EXECUTION_PATTERNS = (
    re.compile(r"aws-actions/configure-aws-credentials", re.I),
    re.compile(r"\baws\s+(?:ssm|ec2|rds|s3|cloudwatch|sns)\b", re.I),
    re.compile(r"\.rds\.amazonaws\.com", re.I),
)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"ERROR: {message}")


def active_text_files(root: Path):
    for path in root.rglob("*"):
        if (
            path.is_file()
            and path.resolve() != VALIDATOR
            and path.suffix in {".sh", ".py", ".yml", ".yaml", ".json", ".env"}
        ):
            yield path


def validate_observability_contract() -> None:
    architecture = OBSERVABILITY_ADR.read_text(encoding="utf-8")
    runbook = OBSERVABILITY_RUNBOOK.read_text(encoding="utf-8")
    observability = OBSERVABILITY_COMPOSE.read_text(encoding="utf-8")
    metrics_proxy = METRICS_PROXY_COMPOSE.read_text(encoding="utf-8")
    metrics_proxy_config = METRICS_PROXY_CONFIG.read_text(encoding="utf-8")
    datasource = GRAFANA_DATASOURCE.read_text(encoding="utf-8")

    for marker in (
        "OCI `app01`",
        "same-host Lean baseline",
        "Deferred — Evidence Triggered",
        "metrics-proxy:9464",
        "과거 AWS 선택의 보존",
    ):
        require(marker in architecture, f"ARCH-012 observability marker is missing: {marker}")

    for retired in (
        "t4g.small",
        "Security Group",
        "SSM",
        "Observability EC2",
        "Production EC2",
        "active-mysql-volume",
    ):
        require(retired not in runbook, f"retired observability execution premise remains in OPS-OBS-001: {retired}")

    require("PAWCYCLE_APP_NETWORK" in observability, "Prometheus app network injection is missing")
    require("observability:" in observability and "internal: true" in observability, "shared internal observability network is missing")
    require("url: http://prometheus:9090" in datasource, "Grafana datasource must target Prometheus by service name")
    require("metrics-proxy:9464" in runbook, "same-host metrics target is missing from OPS-OBS-001")
    require("ports:" not in metrics_proxy, "metrics-proxy must not publish a host port")
    require("PAWCYCLE_METRICS_PORT" not in metrics_proxy, "metrics-proxy host port override must be retired")
    require("external: true" in metrics_proxy and "PAWCYCLE_APP_NETWORK" in metrics_proxy, "metrics-proxy app network must remain external")
    require("location = /actuator/prometheus" in metrics_proxy_config, "metrics-proxy metrics location is missing")
    require("location /" in metrics_proxy_config and "return 404" in metrics_proxy_config, "metrics-proxy must reject non-metrics paths")
    require("server backend:8080 resolve;" in metrics_proxy_config, "metrics-proxy dynamic Backend resolution is missing")
    require("/proc" in runbook and "/sys" in runbook, "host collector decision boundary is missing")
    require("OFF" in runbook and "ON" in runbook and "latency/error-rate" in runbook, "same-host calibration contract is incomplete")
    require("worktree add --detach" in runbook, "first-time observability control bootstrap is missing")
    require("@sha256:" in runbook and "RepoDigests" in runbook, "pinned image digest verification is missing")
    require("linux/amd64" in runbook, "current OCI amd64 runtime verification is missing")
    require("--scope production" in runbook and "--scope observability" in runbook, "same-host backend diagnostic flow is missing")


def main() -> None:
    validate_observability_contract()
    for path in RETIRED_PATHS:
        require(not path.exists(), f"retired AWS/Production artifact is still active: {path.relative_to(ROOT)}")

    for root in (WORKFLOWS, PRODUCTION):
        for path in active_text_files(root):
            text = path.read_text(encoding="utf-8")
            for pattern in AWS_EXECUTION_PATTERNS:
                require(
                    pattern.search(text) is None,
                    f"active AWS execution contract remains in {path.relative_to(ROOT)}: {pattern.pattern}",
                )

    publish = (WORKFLOWS / "publish-production-images.yml").read_text(encoding="utf-8")
    require("workflow_dispatch:" in publish, "production image publication must remain explicitly dispatchable")
    require("branches: [main]" in publish or "branches:\n      - main" in publish, "production images must remain main-bound")
    require("production-deploy.yml" not in publish, "image publication must not dispatch a retired deployment workflow")

    readiness = (WORKFLOWS / "production-release-readiness.yml").read_text(encoding="utf-8")
    require("There is no active Production deployment target." in readiness, "readiness workflow must state that no Production target is active")
    require("zero-downtime deployment remain DEFER" in readiness, "readiness workflow must keep zero-downtime deployment deferred")

    architecture = ARCHITECTURE.read_text(encoding="utf-8")
    for marker in (
        "Production deployment target: **없음**",
        "CD: **DEFER**",
        "무중단 배포: **DEFER**",
        "Production Verified가 아니다",
    ):
        require(marker in architecture, f"current Production status marker is missing: {marker}")

    print("Production contract validation passed")


if __name__ == "__main__":
    main()
