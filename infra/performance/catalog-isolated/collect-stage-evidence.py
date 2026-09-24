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
OCI_METRICS = {
    "CPUUtilization": "mean",
    "MemoryUtilization": "mean",
    "ActiveConnections": "max",
    "CurrentConnections": "max",
    "Statements": "sum",
    "StatementLatency": "mean",
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
    return subprocess.run(args, check=True, capture_output=True, text=True, timeout=15).stdout.strip()


def parse_metrics(payload):
    result = {}
    for line in payload.splitlines():
        match = METRIC_LINE.fullmatch(line)
        if not match or match[1] not in METRICS:
            continue
        name, labels, raw = match.groups()
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
            continue
        result[key] = result.get(key, 0.0) + value
    required = {"processCpu", "systemCpu", "jvmMemoryUsedHeap", "jvmMemoryCommittedHeap",
                "jvmMemoryMaxHeap", "jvmMemoryUsedNonHeap", "gcPauseCount",
                "gcPauseSeconds", "liveThreads", "peakThreads", "tomcatBusy",
                "tomcatMax", "hikariActive", "hikariIdle", "hikariPending",
                "hikariMax", "hikariAcquireCount", "hikariAcquireSeconds",
                "hikariUsageCount", "hikariUsageSeconds"}
    if required - result.keys():
        raise ValueError("isolated actuator is missing required allowlisted metrics")
    return result


def cpu_ticks():
    fields = Path("/proc/stat").read_text().splitlines()[0].split()
    if fields[0] != "cpu":
        raise ValueError("host CPU counters unavailable")
    values = [int(value) for value in fields[1:]]
    return sum(values[:8]), values[0] + values[1], values[2], values[4]


def host_sample(previous):
    current = cpu_ticks()
    total = current[0] - previous[0]
    if total <= 0:
        raise ValueError("host CPU counters did not advance")
    mem = dict(re.findall(r"^(\w+):\s+(\d+) kB$", Path("/proc/meminfo").read_text(), re.M))
    disk = os.statvfs("/")
    return current, {
        "cpuUserPct": 100 * (current[1] - previous[1]) / total,
        "cpuSystemPct": 100 * (current[2] - previous[2]) / total,
        "cpuIoWaitPct": 100 * (current[3] - previous[3]) / total,
        "memAvailableBytes": int(mem["MemAvailable"]) * 1024,
        "swapTotalBytes": int(mem["SwapTotal"]) * 1024,
        "swapFreeBytes": int(mem["SwapFree"]) * 1024,
        "filesystemAvailableBytes": disk.f_bavail * disk.f_frsize,
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


def sample(args):
    if args.port < 1 or args.port > 65535 or args.duration_seconds < 1:
        raise ValueError("invalid sampling port or duration")
    previous = cpu_ticks()
    deadline = time.monotonic() + args.duration_seconds
    while time.monotonic() < deadline:
        time.sleep(min(args.interval_seconds, max(0, deadline - time.monotonic())))
        previous, host = host_sample(previous)
        container = container_sample()
        if container["restartCount"] or container["oomKilled"] or container["health"] != "healthy":
            raise ValueError("isolated container became unhealthy, restarted, or OOM killed")
        with urllib.request.urlopen(f"http://127.0.0.1:{args.port}/actuator/prometheus", timeout=3) as response:
            metrics = parse_metrics(response.read(2_000_000).decode("utf-8"))
        print(json.dumps({"timestampUtc": utc_now(), "host": host,
                          "container": container, "jvmTomcatHikari": metrics},
                         sort_keys=True), flush=True)


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
        points = [{"timestampUtc": point["timestamp"], "value": point["value"]}
                  for item in series for point in item["aggregated-datapoints"]]
    except (subprocess.SubprocessError, KeyError, ValueError) as exc:
        raise ValueError(f"OCI Monitoring query failed for {metric}") from None
    return points


def assemble(args):
    summary = json.loads(Path(args.summary).read_text())
    start = parse_utc(summary["measurementStartUtc"])
    end = parse_utc(summary["measurementEndUtc"])
    if not start < end or (end - start).total_seconds() > 121:
        raise ValueError("invalid k6 measurement window")
    samples = [json.loads(line) for line in Path(args.host_samples).read_text().splitlines() if line]
    selected = [sample for sample in samples if start <= parse_utc(sample["timestampUtc"]) <= end]
    if len(selected) < 15:
        raise ValueError("insufficient Host/JVM/Hikari samples in measurement window")
    if any(sample["container"]["restartCount"] or sample["container"]["oomKilled"] or sample["container"]["health"] != "healthy" for sample in selected):
        raise ValueError("isolated container unhealthy in measurement window")
    mysql = {}
    for attempt in range(4):
        for metric, statistic in OCI_METRICS.items():
            if metric in mysql:
                continue
            points = oci_points(metric, statistic, start.isoformat().replace("+00:00", "Z"),
                                (end + dt.timedelta(minutes=1)).isoformat().replace("+00:00", "Z"))
            selected_points = [point for point in points if start <= parse_utc(point["timestampUtc"]) <= end]
            if selected_points:
                mysql[metric] = selected_points
        if len(mysql) == len(OCI_METRICS):
            break
        if attempt < 3:
            time.sleep(20)
    if len(mysql) != len(OCI_METRICS):
        missing = sorted(OCI_METRICS.keys() - mysql.keys())
        raise ValueError(f"OCI Monitoring has no measurement-window datapoint for {', '.join(missing)}")
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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="mode", required=True)
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
        else:
            assemble(args)
    except (OSError, KeyError, ValueError, subprocess.SubprocessError) as exc:
        print(f"evidence_collection=FAIL reason={exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
