#!/usr/bin/env python3
"""Independent GitHub Actions health watch for the read-only OCI A1 capacity monitor.

No VM creation or retry APIs. Only a daily report probes OCI; watch mode reads
GitHub Actions run history. Never print credentials or webhook URLs.
"""

from __future__ import annotations

import importlib.util
import json
import os
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

REPO = "guseoh/pawcycle-commerce"
MONITOR_WORKFLOW = "oci-a1-capacity-watch.yml"
MONITOR_TITLE = "[OCI A1 Monitor] Scheduled-check health alert"
MARKER = "<!-- oci-a1-schedule-health -->"
STALE_AFTER = timedelta(hours=3)
REPORT_WINDOW = timedelta(hours=24)


def utc_time(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(timezone.utc)


def github_request(method: str, path: str, payload=None):
    if os.environ.get("GITHUB_REPOSITORY") != REPO:
        raise ValueError("Unexpected GitHub repository")
    token = os.environ.get("GITHUB_TOKEN", "")
    if not token:
        raise ValueError("Missing GITHUB_TOKEN")
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    request = Request("https://api.github.com/repos/" + REPO + path, data=data,
                      method=method, headers={
                          "Authorization": "Bearer " + token,
                          "Accept": "application/vnd.github+json",
                          "X-GitHub-Api-Version": "2022-11-28",
                          "User-Agent": "pawcycle-oci-a1-health-watch",
                          "Content-Type": "application/json",
                      })
    try:
        with urlopen(request, timeout=25) as response:
            raw = response.read()
    except HTTPError as exc:
        raise RuntimeError("GitHub API HTTP " + str(exc.code)) from None
    except URLError:
        raise RuntimeError("GitHub API unreachable") from None
    return json.loads(raw) if raw else None


def scheduled_runs():
    result = github_request(
        "GET",
        "/actions/workflows/" + MONITOR_WORKFLOW + "/runs?event=schedule&per_page=100",
    )
    runs = result.get("workflow_runs", [])
    if not isinstance(runs, list):
        raise ValueError("Unexpected GitHub workflow run response")
    return sorted(runs, key=lambda item: utc_time(item["created_at"]), reverse=True)


def evaluate(runs, now: datetime):
    """Detect missed checks and failures. Never confuse workflow success with OCI availability."""
    recent = [r for r in runs if utc_time(r["created_at"]) >= now - REPORT_WINDOW]
    passed = [r for r in runs if r.get("conclusion") == "success"]
    latest = runs[0] if runs else None
    latest_good = passed[0] if passed else None
    reasons = []
    if latest is not None and latest.get("status") == "completed" and latest.get("conclusion") != "success":
        reasons.append("Most recent scheduled workflow: " + str(latest.get("conclusion") or "unknown"))
    if latest_good is None:
        reasons.append("No successful scheduled capacity check found")
    elif now - utc_time(latest_good["created_at"]) > STALE_AFTER:
        reasons.append("Last successful scheduled check is older than 3 hours")
    return {
        "unhealthy": bool(reasons),
        "reasons": reasons,
        "last_run": latest,
        "last_good": latest_good,
        "success_24h": sum(r.get("conclusion") == "success" for r in recent),
        "failed_24h": sum(r.get("conclusion") in ("failure", "timed_out", "cancelled", "action_required") for r in recent),
        "skipped_24h": sum(r.get("conclusion") == "skipped" for r in recent),
        "total_24h": len(recent),
    }


def run_url(run):
    return (run.get("html_url") if run else None) or (
        "https://github.com/" + REPO + "/actions/workflows/" + MONITOR_WORKFLOW)


def discord_payload(title, description, fields, link, now):
    return {
        "username": "PawCycle A1 Capacity Watch",
        "allowed_mentions": {"parse": []},
        "embeds": [{
            "title": title,
            "description": description,
            "color": 15105570,
            "url": link,
            "fields": [{"name": key, "value": str(value), "inline": False} for key, value in fields],
            "timestamp": now.isoformat(),
            "footer": {"text": "OCI Capacity Report only · no VM changes"},
        }],
    }


def post_discord(payload):
    webhook = os.environ.get("DISCORD_WEBHOOK_URL", "").strip()
    if not webhook:
        raise ValueError("Missing DISCORD_WEBHOOK_URL")
    path = Path(__file__).with_name("send-discord-notification.py")
    spec = importlib.util.spec_from_file_location("oci_health_sender", path)
    if spec is None or spec.loader is None:
        raise RuntimeError("Discord sender unavailable")
    sender = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(sender)
    result = sender.send(webhook, payload, retries=3, event="oci_a1_health")
    if result != 0:
        raise RuntimeError("Discord health notification delivery failed")


def find_health_issue():
    for page in range(1, 16):
        issues = github_request("GET", "/issues?state=all&per_page=100&page=" + str(page))
        for issue in issues:
            if ("pull_request" not in issue and issue.get("title") == MONITOR_TITLE
                    and MARKER in (issue.get("body") or "")):
                return issue
        if len(issues) < 100:
            break
    return None


def issue_body(report, now):
    return "\n".join([
        MARKER,
        "## OCI A1 scheduled-check health",
        "",
        "Last evaluated (UTC): " + now.strftime("%Y-%m-%d %H:%M:%S"),
        "Reasons: " + "; ".join(report["reasons"]),
        "Successful scheduled runs (24h): " + str(report["success_24h"]),
        "Latest scheduled run: " + run_url(report["last_run"]),
        "Last successful scheduled run: " + run_url(report["last_good"]),
        "",
        "Capacity availability is **not** inferred from workflow health.",
        "This watcher only reports monitoring health and never creates VMs.",
    ])


def sync_health(report, now, *, dry_run=False):
    """One Discord alert per unhealthy incident; close the tracking Issue after recovery."""
    if dry_run:
        return "would_alert" if report["unhealthy"] else "healthy"
    issue = find_health_issue()
    if report["unhealthy"]:
        if issue is not None and issue.get("state") == "open":
            return "alert_already_open"
        payload = discord_payload(
            "OCI A1 monitor: health warning",
            "Scheduled capacity checks may be missing or failing. "
            "This is NOT a capacity-availability notification.",
            [
                ("Reason", "; ".join(report["reasons"])),
                ("Successful checks (24h)", str(report["success_24h"]) + " / " + str(report["total_24h"]) + " scheduled runs"),
                ("Last completed check", run_url(report["last_good"])),
            ],
            run_url(report["last_run"]), now,
        )
        # Commit state only after Discord delivery succeeds, so delivery failures are retried.
        post_discord(payload)
        if issue is None:
            github_request("POST", "/issues", {
                "title": MONITOR_TITLE, "body": issue_body(report, now),
            })
        else:
            github_request("PATCH", "/issues/" + str(issue["number"]), {
                "state": "open", "body": issue_body(report, now), "assignees": [],
            })
        return "new_health_alert"
    if issue is not None and issue.get("state") == "open":
        github_request("PATCH", "/issues/" + str(issue["number"]), {
            "state": "closed", "state_reason": "completed", "assignees": [],
        })
        return "recovered"
    return "healthy"


def daily_report(report, now):
    # A fresh capacity report, not a cached/guessed result from past Actions runs.
    from oci_a1_capacity_monitor import GithubIssues, fetch_capacity, sync_issue

    error = None
    try:
        status, count = fetch_capacity()
    except Exception as exc:
        # Errors are classified only; never forward SDK messages or sensitive details.
        status, count, error = "CHECK_FAILED", None, type(exc).__name__
    else:
        # Preserve the original AVAILABLE transition alert + GitHub Issue semantics.
        # OUT_OF_HOST_CAPACITY never triggers a capacity-available Discord alert.
        observed = now.strftime("%Y-%m-%d %H:%M:%S UTC")
        sync_issue(GithubIssues(), status, count, observed, run_url(report["last_run"]))

    recent = str(report["success_24h"]) + " successful / " + str(report["total_24h"]) + " scheduled"
    fields = [
        ("Tokyo A1 (2 OCPU / 12GB)", status),
        ("Available count", "not provided" if count is None else str(count)),
        ("Scheduled checks (past 24h)", recent),
        ("Failed / skipped", str(report["failed_24h"]) + " / " + str(report["skipped_24h"])),
        ("Last successful scheduled check", run_url(report["last_good"])),
        ("Scheduler health", "WARNING" if report["unhealthy"] else "OK"),
    ]
    if error:
        fields.append(("Capacity API", "Check failed: " + error))
    payload = discord_payload(
        "OCI A1: daily capacity & monitoring status",
        "Daily status; a point-in-time Capacity Report is not a reservation. "
        "Verify pawcycle-arm-01 across compartments before any manual provisioning.",
        fields, run_url(report["last_run"]), now,
    )
    post_discord(payload)
    if error:
        raise RuntimeError("Daily OCI Capacity Report failed: " + error)
    return status


def main():
    mode = os.environ.get("OCI_A1_HEALTH_MODE", "preview")
    if mode not in ("preview", "watch", "daily"):
        raise ValueError("Invalid OCI_A1_HEALTH_MODE")
    now = datetime.now(timezone.utc)
    report = evaluate(scheduled_runs(), now)
    print("Scheduled capacity checks (24h): {} successful / {} total; unhealthy={}".format(
        report["success_24h"], report["total_24h"], report["unhealthy"]))
    if report["reasons"]:
        print("Health findings: " + "; ".join(report["reasons"]))
    if mode == "preview":
        print("Preview only: no OCI query, Discord alert, or Issue mutation.")
        return
    # A daily run sends its heartbeat regardless of OUT_OF_HOST_CAPACITY.
    # Watch runs never use OCI keys and alert only on new health incidents.
    if mode == "daily":
        daily_report(report, now)
    print("Health state: " + sync_health(report, now))


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        # Don't print SDK response bodies, request URLs or credentials.
        print("OCI health watch error: " + type(exc).__name__, file=sys.stderr)
        sys.exit(1)
