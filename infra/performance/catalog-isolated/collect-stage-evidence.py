#!/usr/bin/env python3
"""Allowlisted, stage-aligned evidence for the isolated Catalog experiment."""

import argparse
import datetime as dt
import json
import math
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request


METRICS = {
    "process_cpu_usage": "processCpu",
    "system_cpu_usage": "systemCpu",
    "jvm_memory_used_bytes": "jvmMemoryUsed",
    "jvm_memory_committed_bytes": "jvmMemoryCommitted",
    "jvm_memory_max_bytes": "jvmMemoryMax",
    "jvm_gc_pause_seconds_count": "gcPauseCount",
    "jvm_gc_pause_seconds_sum": "gcPauseSeconds",
    "jvm_threads_live_threads": "liveThreads",
    "jvm_threads_peak_threads": "peakThreads",
    "tomcat_threads_busy_threads": "tomcatBusy",
    "tomcat_threads_config_max_threads": "tomcatMax",
    "hikaricp_connections_active": "hikariActive",
    "hikaricp_connections_idle": "hikariIdle",
    "hikaricp_connections_pending": "hikariPending",
    "hikaricp_connections_max": "hikariMax",
    "hikaricp_connections_acquire_seconds_count": "hikariAcquireCount",
    "hikaricp_connections_acquire_seconds_sum": "hikariAcquireSeconds",
    "hikaricp_connections_usage_seconds_count": "hikariUsageCount",
    "hikaricp_connections_usage_seconds_sum": "hikariUsageSeconds",
}
PRODUCT_DISCOVERY_PHASES = (
    "count-query",
    "list-query",
    "row-mapping",
    "repository-total",
)
PRODUCT_DISCOVERY_PHASE_METRICS = {
    "pawcycle_catalog_product_discovery_phase_seconds_count": "count",
    "pawcycle_catalog_product_discovery_phase_seconds_sum": "sumSeconds",
    "pawcycle_catalog_product_discovery_phase_seconds_max": "maxSeconds",
}
LIFECYCLE_PHASES = (
    "transaction-begin", "transaction-commit", "transaction-rollback",
    "transaction-completion", "transaction-cleanup", "connection-acquire",
    "pre-repository", "repository-with-connection", "repository-body-paired",
    "post-repository-connection-hold", "connection-release", "connection-lease-total",
)
LIFECYCLE_PREFIX = "pawcycle_catalog_product_discovery_lifecycle"
LIFECYCLE_METRICS = {
    LIFECYCLE_PREFIX + "_seconds_count": "count",
    LIFECYCLE_PREFIX + "_seconds_sum": "sumSeconds",
    LIFECYCLE_PREFIX + "_seconds_max": "maxSeconds",
    LIFECYCLE_PREFIX + "_calls_total": "calls",
    LIFECYCLE_PREFIX + "_pairs_total": "pairs",
}


def parse_lifecycle_metrics(payload):
    phases = {phase: {} for phase in LIFECYCLE_PHASES}
    coverage = {unit: {} for unit in ("calls", "pairs")}
    seen = set()
    for line in payload.splitlines():
        name = line.split("{", 1)[0].split(" ", 1)[0]
        if name not in LIFECYCLE_METRICS:
            continue
        match = METRIC_LINE.fullmatch(line)
        if match is None:
            raise ValueError("discovery lifecycle metric is malformed")
        name, labels, raw = match.groups()
        statistic = LIFECYCLE_METRICS[name]
        counter = statistic in coverage
        label = re.fullmatch(r'result="(matched|unmatched)"' if counter else r'phase="([^"]+)"', labels or "")
        if label is None or (not counter and label[1] not in phases):
            raise ValueError("discovery lifecycle metric has unexpected labels")
        key = (name, label[1])
        if key in seen:
            raise ValueError("discovery lifecycle metric is duplicated")
        seen.add(key)
        value = float(raw)
        if not math.isfinite(value) or value < 0 or ((counter or statistic == "count") and not value.is_integer()):
            raise ValueError("discovery lifecycle metric has invalid value")
        if counter:
            coverage[statistic][label[1]] = value
        else:
            phases[label[1]][statistic] = value
    if not seen:
        return None
    result = {"phases": phases, "coverage": coverage}
    validate_lifecycle(result)
    return result


def validate_lifecycle(value):
    if not isinstance(value, dict) or set(value) != {"phases", "coverage"}:
        raise ValueError("discovery lifecycle field is malformed")
    for group, keys, fields in (("phases", LIFECYCLE_PHASES, ("count", "sumSeconds", "maxSeconds")),
                                 ("coverage", ("calls", "pairs"), ("matched", "unmatched"))):
        entries = value[group]
        if not isinstance(entries, dict) or set(entries) != set(keys):
            raise ValueError("discovery lifecycle field is incomplete")
        for statistics in entries.values():
            if not isinstance(statistics, dict) or set(statistics) != set(fields):
                raise ValueError("discovery lifecycle field is incomplete")
            for key, number in statistics.items():
                if (isinstance(number, bool) or not isinstance(number, (int, float))
                        or not math.isfinite(number) or number < 0
                        or ((group == "coverage" or key == "count") and number != int(number))):
                    raise ValueError("discovery lifecycle field has invalid value")
OCI_METRICS = {
    "CPUUtilization": "mean",
    "MemoryUtilization": "mean",
    "ActiveConnections": "max",
    "CurrentConnections": "max",
    "Statements": "sum",
    "StatementLatency": "mean",
}
OCI_BUCKET_WIDTH = dt.timedelta(minutes=1)
OCI_PUBLICATION_RETRY_INTERVAL_SECONDS = 20
OCI_PUBLICATION_MAX_RETRIES = 6
GC_PAUSE_METRICS = {
    "jvm_gc_pause_seconds_count": "gcPauseCount",
    "jvm_gc_pause_seconds_sum": "gcPauseSeconds",
}
CONTAINER = "pawcycle-performance-catalog-backend-1"
METRIC_LINE = re.compile(r'^([a-zA-Z_:][a-zA-Z0-9_:]*)(?:\{([^}]*)\})?\s+(-?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?)$')


def utc_now():
    return dt.datetime.now(dt.timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def parse_utc(value):
    parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise ValueError("UTC timestamp must include a timezone")
    return parsed.astimezone(dt.timezone.utc)


def command(*args):
    stage = {("docker", "inspect"): "docker_inspect",
             ("docker", "stats"): "docker_stats"}.get(tuple(args[:2]), "external_command")
    try:
        return subprocess.run(args, check=True, capture_output=True, text=True, timeout=15).stdout.strip()
    except subprocess.TimeoutExpired:
        raise ValueError(f"{stage}_timeout") from None
    except (OSError, subprocess.CalledProcessError):
        raise ValueError(f"{stage}_failed") from None


def format_utc(value):
    return value.astimezone(dt.timezone.utc).isoformat().replace("+00:00", "Z")


def oci_bucket_point(timestamp, value):
    bucket_end = parse_utc(timestamp)
    bucket_start = bucket_end - OCI_BUCKET_WIDTH
    return {"windowStartUtc": format_utc(bucket_start),
            "windowEndUtc": format_utc(bucket_end), "value": value}


def oci_query_bounds(start, end):
    # OCI endTime is exclusive; one extra resolution interval includes the last overlapping bucket.
    return format_utc(start - OCI_BUCKET_WIDTH), format_utc(end + OCI_BUCKET_WIDTH)


def select_oci_points(points, start, end):
    return [point for point in points
            if parse_utc(point["windowEndUtc"]) > start
            and parse_utc(point["windowStartUtc"]) < end]


def expected_oci_bucket_ends(start, end):
    bucket_end = start.replace(second=0, microsecond=0) + OCI_BUCKET_WIDTH
    expected = []
    while bucket_end - OCI_BUCKET_WIDTH < end:
        expected.append(bucket_end)
        bucket_end += OCI_BUCKET_WIDTH
    return expected


def wait_for_last_oci_bucket(bucket_ends, now=None):
    if not bucket_ends:
        raise ValueError("measurement window has no OCI aggregation bucket")
    current_time = now or dt.datetime.now(dt.timezone.utc)
    wait_seconds = (bucket_ends[-1] - current_time).total_seconds()
    if wait_seconds > 0:
        time.sleep(wait_seconds)


def validate_k6_summary(summary):
    if not isinstance(summary, dict):
        raise ValueError("k6 summary must be a JSON object")
    timestamps = ("measurementStartUtc", "measurementEndUtc")
    if any(not isinstance(summary.get(key), str) or not summary[key].strip() for key in timestamps):
        raise ValueError("k6 summary measurement timestamps must be non-empty strings")
    try:
        start = parse_utc(summary["measurementStartUtc"])
        end = parse_utc(summary["measurementEndUtc"])
    except ValueError:
        raise ValueError("k6 summary measurement timestamps must include a valid timezone") from None
    max_latency_ms = summary.get("maxMs")
    if isinstance(max_latency_ms, bool) or not isinstance(max_latency_ms, (int, float)):
        raise ValueError("k6 summary maxMs must be a finite non-negative number")
    try:
        max_latency_ms = float(max_latency_ms)
    except (OverflowError, ValueError):
        raise ValueError("k6 summary maxMs must be a finite non-negative number") from None
    if not math.isfinite(max_latency_ms) or max_latency_ms < 0:
        raise ValueError("k6 summary maxMs must be a finite non-negative number")
    return start, end, max_latency_ms


def parse_metrics(payload):
    result = {}
    phase_values = {
        phase: {"count": None, "sumSeconds": None, "maxSeconds": None}
        for phase in PRODUCT_DISCOVERY_PHASES
    }
    seen_phase_metrics = set()
    for line in payload.splitlines():
        match = METRIC_LINE.fullmatch(line)
        if not match:
            metric_name = line.split("{", 1)[0].split(" ", 1)[0]
            if metric_name in GC_PAUSE_METRICS:
                raise ValueError("isolated actuator GC pause metric is malformed")
            if metric_name in PRODUCT_DISCOVERY_PHASE_METRICS:
                raise ValueError("isolated actuator product discovery phase metric is malformed")
            continue
        name, labels, raw = match.groups()
        if name in PRODUCT_DISCOVERY_PHASE_METRICS:
            phase_match = re.fullmatch(r'phase="([^"]+)"', labels or "")
            if phase_match is None or phase_match[1] not in phase_values:
                raise ValueError("product discovery phase metric has unexpected labels")
            phase = phase_match[1]
            statistic = PRODUCT_DISCOVERY_PHASE_METRICS[name]
            series = (phase, statistic)
            if series in seen_phase_metrics:
                raise ValueError("product discovery phase metric is duplicated")
            value = float(raw)
            if not math.isfinite(value) or value < 0:
                raise ValueError("product discovery phase metric is not finite and non-negative")
            if statistic == "count" and not value.is_integer():
                raise ValueError("product discovery phase count is not an integer")
            seen_phase_metrics.add(series)
            phase_values[phase][statistic] = value
            continue
        if name not in METRICS:
            continue
        if name.startswith("jvm_memory_"):
            if 'area="heap"' in (labels or ""):
                key = METRICS[name] + "Heap"
            elif 'area="nonheap"' in (labels or ""):
                key = METRICS[name] + "NonHeap"
            else:
                continue
        else:
            key = METRICS[name]
        value = float(raw)
        if not math.isfinite(value):
            if name in GC_PAUSE_METRICS:
                raise ValueError("isolated actuator GC pause metric is not finite")
            continue
        result[key] = result.get(key, 0.0) + value
    for key in GC_PAUSE_METRICS.values():
        result.setdefault(key, 0.0)
    required = {"processCpu", "systemCpu", "jvmMemoryUsedHeap", "jvmMemoryCommittedHeap",
                "jvmMemoryMaxHeap", "jvmMemoryUsedNonHeap", "liveThreads",
                "peakThreads", "tomcatBusy",
                "tomcatMax", "hikariActive", "hikariIdle", "hikariPending",
                "hikariMax", "hikariAcquireCount", "hikariAcquireSeconds",
                "hikariUsageCount", "hikariUsageSeconds"}
    if required - result.keys():
        raise ValueError("isolated actuator is missing required allowlisted metrics")
    expected_phase_metrics = {
        (phase, statistic)
        for phase in PRODUCT_DISCOVERY_PHASES
        for statistic in ("count", "sumSeconds", "maxSeconds")
    }
    if expected_phase_metrics - seen_phase_metrics:
        raise ValueError("isolated actuator is missing required product discovery phase metrics")
    result["productDiscoveryPhases"] = phase_values
    lifecycle = parse_lifecycle_metrics(payload)
    if lifecycle is not None:
        result["productDiscoveryLifecycle"] = lifecycle
    return result


def validate_product_discovery_phases(sample):
    phases = sample.get("productDiscoveryPhases")
    if not isinstance(phases, dict) or set(phases) != set(PRODUCT_DISCOVERY_PHASES):
        raise ValueError("sample is missing the fixed product discovery phases")
    expected_statistics = {"count", "sumSeconds", "maxSeconds"}
    for phase, statistics in phases.items():
        if not isinstance(statistics, dict) or set(statistics) != expected_statistics:
            raise ValueError(f"sample has malformed product discovery phase: {phase}")
        for name, value in statistics.items():
            if (isinstance(value, bool) or not isinstance(value, (int, float))
                    or not math.isfinite(value) or value < 0):
                raise ValueError(f"sample has invalid product discovery phase value: {phase}/{name}")


def cpu_ticks():
    fields = Path("/proc/stat").read_text().splitlines()[0].split()
    if fields[0] != "cpu":
        raise ValueError("host CPU counters unavailable")
    values = [int(value) for value in fields[1:]]
    return sum(values[:8]), values[0] + values[1], values[2], values[4]


def parse_swap_counters(payload):
    counters = {}
    for line in payload.splitlines():
        fields = line.split()
        if fields and fields[0] in {"pswpin", "pswpout"}:
            if len(fields) != 2 or fields[0] in counters or not fields[1].isdigit():
                raise ValueError("host swap activity counters are malformed")
            counters[fields[0]] = int(fields[1])
    if counters.keys() != {"pswpin", "pswpout"}:
        raise ValueError("host swap activity counters are unavailable")
    return counters["pswpin"], counters["pswpout"]


def swap_activity(previous, current):
    page_size = os.sysconf("SC_PAGE_SIZE")
    if page_size <= 0 or current[0] < previous[0] or current[1] < previous[1]:
        raise ValueError("host swap activity counters moved backwards")
    swap_in_pages = current[0] - previous[0]
    swap_out_pages = current[1] - previous[1]
    return {
        "swapInActivityPages": swap_in_pages,
        "swapOutActivityPages": swap_out_pages,
        "swapInActivityBytes": swap_in_pages * page_size,
        "swapOutActivityBytes": swap_out_pages * page_size,
    }


def host_sample(previous_cpu, previous_swap):
    current = cpu_ticks()
    current_swap = parse_swap_counters(Path("/proc/vmstat").read_text())
    total = current[0] - previous_cpu[0]
    if total <= 0:
        raise ValueError("host CPU counters did not advance")
    mem = dict(re.findall(r"^(\w+):\s+(\d+) kB$", Path("/proc/meminfo").read_text(), re.M))
    disk = os.statvfs("/")
    return (current, current_swap), {
        "cpuUserPct": 100 * (current[1] - previous_cpu[1]) / total,
        "cpuSystemPct": 100 * (current[2] - previous_cpu[2]) / total,
        "cpuIoWaitPct": 100 * (current[3] - previous_cpu[3]) / total,
        "memAvailableBytes": int(mem["MemAvailable"]) * 1024,
        "swapTotalBytes": int(mem["SwapTotal"]) * 1024,
        "swapFreeBytes": int(mem["SwapFree"]) * 1024,
        "filesystemAvailableBytes": disk.f_bavail * disk.f_frsize,
        **swap_activity(previous_swap, current_swap),
    }


def memory_bytes(value):
    match = re.fullmatch(r"([\d.]+)([kMGT]?i?B)", value.strip())
    if not match:
        raise ValueError("Docker memory usage format unavailable")
    units = {"B": 1, "kB": 1000, "MB": 1000**2, "GB": 1000**3,
             "TB": 1000**4, "KiB": 1024, "MiB": 1024**2,
             "GiB": 1024**3, "TiB": 1024**4}
    return float(match[1]) * units[match[2]]


def container_sample():
    selected = command("docker", "inspect", "--format",
                       '{{index .Config.Labels "com.docker.compose.project"}}|{{index .Config.Labels "com.pawcycle.performance.scope"}}|{{index .Config.Labels "com.pawcycle.performance.role"}}|{{.RestartCount}}|{{.State.OOMKilled}}|{{if .State.Health}}{{.State.Health.Status}}{{end}}',
                       CONTAINER).split("|")
    if len(selected) != 6 or selected[:3] != ["pawcycle-performance-catalog", "catalog-isolated", "backend"]:
        raise ValueError("isolated container identity mismatch")
    stats = json.loads(command("docker", "stats", "--no-stream", "--format", "{{json .}}", CONTAINER))
    used, limit = stats["MemUsage"].split(" / ")
    return {
        "cpuPct": float(stats["CPUPerc"].rstrip("%")),
        "memoryUsedBytes": memory_bytes(used),
        "memoryLimitBytes": memory_bytes(limit),
        "restartCount": int(selected[3]),
        "oomKilled": selected[4].lower() == "true",
        "health": selected[5],
    }


def read_isolated_metrics(port):
    try:
        with urllib.request.urlopen(f"http://127.0.0.1:{port}/actuator/prometheus", timeout=3) as response:
            payload = response.read(2_000_000).decode("utf-8")
    except (OSError, urllib.error.URLError) as exc:
        timed_out = isinstance(exc, TimeoutError) or isinstance(getattr(exc, "reason", None), TimeoutError)
        raise ValueError("actuator_fetch_timeout" if timed_out else "actuator_fetch_failed") from None
    return parse_metrics(payload)


def sample(args):
    if args.port < 1 or args.port > 65535 or args.duration_seconds < 1:
        raise ValueError("invalid sampling port or duration")
    previous_cpu = cpu_ticks()
    previous_swap = parse_swap_counters(Path("/proc/vmstat").read_text())
    deadline = time.monotonic() + args.duration_seconds
    while time.monotonic() < deadline:
        time.sleep(min(args.interval_seconds, max(0, deadline - time.monotonic())))
        (previous_cpu, previous_swap), host = host_sample(previous_cpu, previous_swap)
        container = container_sample()
        if container["restartCount"] or container["oomKilled"] or container["health"] != "healthy":
            raise ValueError("isolated container became unhealthy, restarted, or OOM killed")
        metrics = read_isolated_metrics(args.port)
        product_discovery_phases = metrics.pop("productDiscoveryPhases")
        lifecycle = metrics.pop("productDiscoveryLifecycle", None)
        sample = {"timestampUtc": utc_now(), "host": host,
                          "container": container, "jvmTomcatHikari": metrics,
                          "productDiscoveryPhases": product_discovery_phases}
        if lifecycle is not None:
            sample["productDiscoveryLifecycle"] = lifecycle
        print(json.dumps(sample, sort_keys=True), flush=True)


def oci_points(metric, statistic, start, end):
    compartment = os.environ.get("PAWCYCLE_PERF_OCI_COMPARTMENT_ID", "")
    db_system = os.environ.get("PAWCYCLE_PERF_OCI_DB_SYSTEM_ID", "")
    if not compartment.startswith("ocid1.compartment.") or not db_system.startswith("ocid1.mysqldbsystem."):
        raise ValueError("OCI Monitoring resource identity is missing")
    query = f'{metric}[1m]{{resourceID = "{db_system}"}}.{statistic}()'
    try:
        raw = command("oci", "monitoring", "metric-data", "summarize-metrics-data",
                      "--compartment-id", compartment, "--namespace", "oci_mysql_database",
                      "--query-text", query, "--start-time", start, "--end-time", end,
                      "--resolution", "1m")
        series = json.loads(raw)["data"]
        if len(series) != 1:
            raise ValueError("ambiguous or missing OCI metric stream")
        points = [oci_bucket_point(point["timestamp"], point["value"])
                  for item in series for point in item["aggregated-datapoints"]]
    except (subprocess.SubprocessError, KeyError, ValueError) as exc:
        raise ValueError(f"OCI Monitoring query failed for {metric}") from None
    return points


def assemble(args):
    summary = json.loads(Path(args.summary).read_text())
    start, end, max_latency_ms = validate_k6_summary(summary)
    allowed_window_seconds = 120 + max_latency_ms / 1000 + 1
    if not start < end or max_latency_ms < 0 or (end - start).total_seconds() > allowed_window_seconds:
        raise ValueError("invalid k6 measurement window")
    query_start, query_end = oci_query_bounds(start, end)
    expected_bucket_ends = expected_oci_bucket_ends(start, end)
    expected_bucket_set = set(expected_bucket_ends)
    samples = [json.loads(line) for line in Path(args.host_samples).read_text().splitlines() if line]
    selected = [sample for sample in samples if start <= parse_utc(sample["timestampUtc"]) <= end]
    if len(selected) < 15:
        raise ValueError("insufficient Host/JVM/Hikari samples in measurement window")
    for sample in selected:
        validate_product_discovery_phases(sample)
        if "productDiscoveryLifecycle" in sample:
            validate_lifecycle(sample["productDiscoveryLifecycle"])
    if any(sample["container"]["restartCount"] or sample["container"]["oomKilled"] or sample["container"]["health"] != "healthy" for sample in selected):
        raise ValueError("isolated container unhealthy in measurement window")
    wait_for_last_oci_bucket(expected_bucket_ends)
    mysql = {}
    for attempt in range(OCI_PUBLICATION_MAX_RETRIES + 1):
        for metric, statistic in OCI_METRICS.items():
            if metric in mysql:
                continue
            points = oci_points(metric, statistic, query_start, query_end)
            selected_points = select_oci_points(points, start, end)
            point_by_bucket_end = {}
            for point in selected_points:
                bucket_end = parse_utc(point["windowEndUtc"])
                if bucket_end in expected_bucket_set:
                    point_by_bucket_end.setdefault(bucket_end, point)
            if expected_bucket_set.issubset(point_by_bucket_end):
                mysql[metric] = [point_by_bucket_end[bucket_end]
                                 for bucket_end in expected_bucket_ends]
        if len(mysql) == len(OCI_METRICS):
            break
        if attempt < OCI_PUBLICATION_MAX_RETRIES:
            time.sleep(OCI_PUBLICATION_RETRY_INTERVAL_SECONDS)
    if len(mysql) != len(OCI_METRICS):
        missing = sorted(OCI_METRICS.keys() - mysql.keys())
        raise ValueError(f"OCI Monitoring has no complete expected bucket set for {', '.join(missing)}")
    result = {"datasetId": summary["datasetId"], "targetRps": summary["targetRps"],
              "measurementStartUtc": summary["measurementStartUtc"],
              "measurementEndUtc": summary["measurementEndUtc"],
              "workload": summary, "hostJvmTomcatHikariSamples": selected,
              "ociMysql": mysql}
    output = Path(args.output)
    if output.exists():
        raise ValueError("evidence output already exists")
    with output.open("x", encoding="utf-8") as stream:
        json.dump(result, stream, sort_keys=True)
        stream.write("\n")


def preflight():
    # Use the same OCI CLI auth/config/profile/region resolution as assembly.
    # Older, closed buckets avoid depending on publication of the current minute.
    end = dt.datetime.now(dt.timezone.utc) - dt.timedelta(minutes=2)
    start = end - dt.timedelta(minutes=10)
    for metric, statistic in OCI_METRICS.items():
        points = oci_points(metric, statistic, format_utc(start), format_utc(end))
        if not select_oci_points(points, start, end):
            raise ValueError(f"OCI Monitoring preflight has no recent datapoints for {metric}")
    print("evidence_preflight=PASS")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="mode", required=True)
    sub.add_parser("preflight")
    sampling = sub.add_parser("sample")
    sampling.add_argument("--port", type=int, required=True)
    sampling.add_argument("--duration-seconds", type=int, default=165)
    sampling.add_argument("--interval-seconds", type=float, default=5)
    assembling = sub.add_parser("assemble")
    assembling.add_argument("--summary", required=True)
    assembling.add_argument("--host-samples", required=True)
    assembling.add_argument("--output", required=True)
    args = parser.parse_args()
    try:
        if args.mode == "sample":
            sample(args)
        elif args.mode == "preflight":
            preflight()
        else:
            assemble(args)
    except (OSError, KeyError, ValueError, subprocess.SubprocessError) as exc:
        print(f"evidence_collection=FAIL reason={exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
