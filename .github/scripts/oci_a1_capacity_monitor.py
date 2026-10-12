#!/usr/bin/env python3
"""OCI capacity REPORT monitoring. Never creates or retries instances."""
import json
import os
import re
import sys
from datetime import datetime, timezone
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

REGION = "ap-tokyo-1"
AD = "nREs:AP-TOKYO-1-AD-1"
SHAPE = "VM.Standard.A1.Flex"
OCPUS = 2.0
MEMORY_GB = 12.0
TITLE = "[OCI A1 Capacity] Tokyo AD-1 / 2 OCPU / 12 GB"
MARKER = re.compile(r"<!-- oci-a1-capacity-monitor status=(AVAILABLE|OUT_OF_HOST_CAPACITY) -->")


def required_environment():
    names = ("OCI_TENANCY_OCID", "OCI_USER_OCID", "OCI_API_FINGERPRINT",
             "OCI_API_PRIVATE_KEY", "GITHUB_TOKEN", "GITHUB_REPOSITORY")
    missing = [name for name in names if not os.environ.get(name, "").strip()]
    if missing:
        raise ValueError("Missing required Secrets/environment: " + ", ".join(missing))
    if not os.environ["OCI_TENANCY_OCID"].startswith("ocid1.tenancy."):
        raise ValueError("OCI_TENANCY_OCID must be the root tenancy OCID")
    if os.environ["GITHUB_REPOSITORY"] != "guseoh/pawcycle-commerce":
        raise ValueError("Only guseoh/pawcycle-commerce may run this monitor")


def fetch_capacity():
    import oci
    config = {
        "user": os.environ["OCI_USER_OCID"],
        "tenancy": os.environ["OCI_TENANCY_OCID"],
        "fingerprint": os.environ["OCI_API_FINGERPRINT"],
        "key_content": os.environ["OCI_API_PRIVATE_KEY"].replace("\\n", "\n"),
        "region": REGION,
    }
    if os.environ.get("OCI_API_PASSPHRASE"):
        config["pass_phrase"] = os.environ["OCI_API_PASSPHRASE"]
    oci.config.validate_config(config)
    client = oci.core.ComputeClient(config, timeout=(10, 30))
    details = oci.core.models.CreateComputeCapacityReportDetails(
        compartment_id=config["tenancy"],
        availability_domain=AD,
        shape_availabilities=[
            oci.core.models.CreateCapacityReportShapeAvailabilityDetails(
                instance_shape=SHAPE,
                instance_shape_config=oci.core.models.CapacityReportInstanceShapeConfig(
                    ocpus=OCPUS, memory_in_gbs=MEMORY_GB,
                ),
            )
        ],
    )
    # This method generates a report only. No LaunchInstance / reservation APIs.
    response = client.create_compute_capacity_report(create_compute_capacity_report_details=details)
    return parse_report(response.data)


def parse_report(report):
    entries = report.shape_availabilities or []
    if len(entries) != 1:
        raise ValueError("Expected exactly one shape availability")
    entry = entries[0]
    cfg = entry.instance_shape_config
    if (entry.instance_shape != SHAPE or cfg is None or
            float(cfg.ocpus) != OCPUS or float(cfg.memory_in_gbs) != MEMORY_GB):
        raise ValueError("Capacity Report returned unexpected shape/configuration")
    status = entry.availability_status
    if status not in ("AVAILABLE", "OUT_OF_HOST_CAPACITY"):
        raise ValueError("Unexpected availability status: " + str(status))
    count = entry.available_count
    if count is not None and (not isinstance(count, int) or count < 0):
        raise ValueError("Invalid available_count")
    if status == "AVAILABLE" and count == 0:
        raise ValueError("AVAILABLE with zero available_count; refusing to alert")
    return status, count


class GithubIssues:
    def __init__(self):
        self.repo = os.environ["GITHUB_REPOSITORY"]
        self.owner = self.repo.split("/")[0]
        self.url = "https://api.github.com/repos/" + self.repo

    def request(self, method, path, payload=None):
        data = None if payload is None else json.dumps(payload).encode("utf-8")
        req = Request(self.url + path, data=data, method=method, headers={
            "Authorization": "Bearer " + os.environ["GITHUB_TOKEN"],
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "pawcycle-oci-a1-capacity-watch",
            "Content-Type": "application/json",
        })
        try:
            with urlopen(req, timeout=20) as response:
                content = response.read()
        except HTTPError as exc:
            raise RuntimeError("GitHub Issues API failed: HTTP " + str(exc.code)) from None
        except URLError:
            raise RuntimeError("GitHub Issues API unreachable") from None
        return json.loads(content) if content else None

    def find_issue(self):
        for page in range(1, 16):
            issues = self.request("GET", "/issues?state=all&per_page=100&page=" + str(page))
            for issue in issues:
                if ("pull_request" not in issue and issue.get("title") == TITLE
                        and MARKER.search(issue.get("body") or "")):
                    return issue
            if len(issues) < 100:
                break
        return None


def issue_body(status, count, observed_at, run_url):
    count_text = "not provided" if count is None else str(count)
    return "\n".join([
        "<!-- oci-a1-capacity-monitor status=" + status + " -->",
        "# OCI A1 Capacity Report",
        "",
        "| Item | Value |",
        "| --- | --- |",
        "| Region / AD | `ap-tokyo-1` / `nREs:AP-TOKYO-1-AD-1` |",
        "| Shape | `VM.Standard.A1.Flex` |",
        "| CPU / Memory | `2 OCPU / 12 GB` |",
        "| Latest status | `" + status + "` |",
        "| Available count | " + count_text + " |",
        "| Checked UTC | " + observed_at + " |",
        "| GitHub Actions run | " + run_url + " |",
        "",
        "**Point-in-time capacity only: not a VM reservation or launch guarantee.**",
        "",
        "Before manually provisioning, check the OCI Console for an existing",
        "`pawcycle-arm-01` instance across relevant compartments.",
        "Also confirm Always Free quotas and billing status.",
        "",
        "This workflow cannot create, modify, or retry any OCI VM or infrastructure.",
    ])


def sync_issue(api, status, count, observed_at, run_url):
    existing = api.find_issue()
    previous = MARKER.search(existing.get("body") or "").group(1) if existing else None
    if status == "OUT_OF_HOST_CAPACITY":
        if existing and previous != status:
            api.request("PATCH", "/issues/" + str(existing["number"]), {
                "state": "closed", "state_reason": "not_planned",
                "body": issue_body(status, count, observed_at, run_url),
            })
            print("OUT_OF_HOST_CAPACITY: existing available alert closed.")
        else:
            print("OUT_OF_HOST_CAPACITY: no notification.")
        return

    if existing and previous == "AVAILABLE":
        print("AVAILABLE unchanged: no duplicate alert.")
        return

    body = issue_body(status, count, observed_at, run_url)
    if existing:
        api.request("PATCH", "/issues/" + str(existing["number"]), {
            "state": "open", "body": body,
        })
        api.request("POST", "/issues/" + str(existing["number"]) + "/comments", {
            "body": "@" + api.owner + " OCI A1 capacity is reported AVAILABLE again. "
                    "Check OCI and existing `pawcycle-arm-01` before any manual launch. "
                    + run_url,
        })
        print("AVAILABLE transition: alert reopened and owner mentioned.")
    else:
        created = api.request("POST", "/issues", {
            "title": TITLE,
            "body": "@" + api.owner + " Capacity indication detected.\n\n" + body,
            "assignees": [api.owner],
        })
        print("AVAILABLE transition: alert Issue assigned. " + created["html_url"])


def main():
    required_environment()
    status, count = fetch_capacity()
    observed_at = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
    run_url = "{}/{}/actions/runs/{}".format(
        os.environ.get("GITHUB_SERVER_URL", "https://github.com"),
        os.environ["GITHUB_REPOSITORY"], os.environ.get("GITHUB_RUN_ID", "unknown"),
    )
    print("OCI A1: status={} count={}".format(status, count))
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as out:
        out.write("### OCI Tokyo A1 Capacity Report\n\n")
        out.write("- Status: `" + status + "`\n- Available count: " + str(count) + "\n")
        out.write("- No VM creation/retry performed.\n")
    sync_issue(GithubIssues(), status, count, observed_at, run_url)


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        # No stack trace or configuration details: these can leak credentials.
        print("OCI A1 monitoring error: " + type(exc).__name__ + ": " + str(exc), file=sys.stderr)
        sys.exit(1)
