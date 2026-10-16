# OCI Tokyo A1 Capacity Watch (Discord + GitHub Issue Tracking)

Task ID: `OCI-A1-001`

This monitor uses a **Compute Capacity Report**, not Compute VM creation. It **never** launches VMs, retries instance creation, creates capacity reservations, or changes running resources. Monitoring is **disabled by default** until the owner enables it.

## Target and behavior

- Target region: `ap-tokyo-1`; availability domain: `nREs:AP-TOKYO-1-AD-1`.
- Shape: `VM.Standard.A1.Flex`, exactly **2 OCPU / 12 GB**.
- Primary GitHub Actions capacity checks target **each hour at minute 17 UTC** (KST minute 17), subject to scheduling delays or skipped runs. An Actions `success` means the API query ran; it does **not** mean capacity was available.
- A separate **OCI A1 Monitor Health** workflow inspects scheduled-run history every 2 hours at minute 43 UTC and on completed primary scheduled runs. If the last successful capacity check is older than **3 hours**, or the newest scheduled run failed/skipped, it sends **one Discord warning per incident** and creates/reopens an unassigned health-tracking Issue. Recovery closes that Issue without repeating warnings.
- At **00:47 UTC (~09:47 KST)** each day, the health workflow makes one additional read-only Capacity Report request and sends a **Discord daily status** even when the shape is OUT_OF_HOST_CAPACITY. The status contains a fresh point-in-time OCI result, plus successful/total scheduled runs and failed/skipped counts from the last 24 hours. AVAILABLE uses the existing transition notification and Issue tracking; it is not re-alerted while status remains AVAILABLE.
- The health watcher is also hosted on GitHub Actions. GitHub may delay/skip its schedules too, so this is not an external always-on availability guarantee. For outages of GitHub Actions itself, an off-platform heartbeat monitor would be required.
- `OUT_OF_HOST_CAPACITY`: no Discord alert. If a tracking Issue exists, close it and clear assignments when status changes; stale assignments are also cleared on unchanged reports.
- `AVAILABLE` transition: send one Discord embed, then create or reopen an unassigned GitHub Issue for status tracking. The Issue contains no owner mention, and any legacy owner assignment is removed.
- Repeated AVAILABLE results are silent; a manually closed AVAILABLE Issue is not reopened until an unavailable observation has occurred. Failed Discord delivery leaves the previous Issue status intact so the next scheduled run can retry.
- Unexpected capacity status, authentication error, missing secret, or failed GitHub/Discord API request: job **fails**, never reports capacity as available.
- The result is point-in-time information; it is neither a reservation nor a successful provisioning guarantee.
- Existing `pawcycle-arm-01` instances are **not checked automatically**; verify across compartments before manual provisioning.

## 1. Create a limited OCI identity (manual in OCI Console)

1. Create an OCI IAM group such as `OCI-Capacity-Report-Monitors` and a separate OCI IAM user for the monitor. Add only this monitor user to the group.
2. Generate an OCI API signing key for that user; download its private key securely. Never commit the private key, send it in Issues, or paste it into chat.
3. Create the **only** required policy statement in the root tenancy (adjust syntax for identity domains where needed):

```text
Allow group OCI-Capacity-Report-Monitors to manage compute-capacity-reports in tenancy
```

Oracle's `CreateComputeCapacityReport` API requires `COMPUTE_CAPACITY_REPORT_CREATE`, not `LaunchInstance`. The report policy grants neither VM launch nor VM deletion rights. Do **not** use an administrator user or grant `manage instance-family`.

Reference: https://docs.oracle.com/en-us/iaas/Content/Identity/Reference/corepolicyreference.htm

## 2. Configure GitHub Actions secrets (manual; not accessible via repository code)

Open https://github.com/guseoh/pawcycle-commerce/settings/secrets/actions and create these **repository secrets**:

| GitHub Actions secret | Value |
| --- | --- |
| `OCI_TENANCY_OCID` | Root tenancy OCID, starting with `ocid1.tenancy.` |
| `OCI_USER_OCID` | Dedicated monitor user's OCID |
| `OCI_API_FINGERPRINT` | API key fingerprint registered under that user |
| `OCI_API_PRIVATE_KEY` | Complete private PEM, including BEGIN and END lines |
| `OCI_API_PASSPHRASE` | Optional; only for an encrypted PEM |
| `DISCORD_WEBHOOK_URL` | Discord webhook for AVAILABLE transition alerts; never printed by the workflow |

GitHub automatically supplies `GITHUB_TOKEN` with `issues: write` only in this workflow. Do not use the temporary OCI Cloud Shell security-token session for unattended Actions.

## 3. Validate before enabling hourly checks

1. Once merged to **main**, open the [workflow page](https://github.com/guseoh/pawcycle-commerce/actions/workflows/oci-a1-capacity-watch.yml) and click **Run workflow → self_test**. The offline tests must pass with **no OCI credentials and no Discord message**.
2. After the Secrets and OCI IAM policy are configured, click **Run workflow → check**. This makes **one** real report query; confirm a valid status in the run summary. If it detects a new AVAILABLE transition, it will send a real Discord alert.
   - After deploying the health workflow, its **Run workflow → self_test** checks both monitor and health unit tests without credentials; **Run workflow → preview** inspects run history without an OCI call, Discord message or Issue write. There is no manual live-health-alert option.
3. Only then visit https://github.com/guseoh/pawcycle-commerce/settings/variables/actions and create a repository variable **`OCI_A1_MONITOR_ENABLED`** with value **`true`**. Scheduled checks start then.
4. Capacity alerts go to the channel configured by `DISCORD_WEBHOOK_URL`; GitHub Issues are unassigned tracking records without owner mentions. Repository watchers may still receive GitHub notifications according to their own settings.
5. Occasionally check failed scheduled runs in Actions; scheduling isn't continuous or guaranteed.

To disable both capacity and health checks immediately, set `OCI_A1_MONITOR_ENABLED` to `false` or remove the variable. The health workflow is gated by the same variable; its offline self-test and read-only preview remain available. No real VM creation is possible in either workflow.

**Alert troubleshooting:** `OUT_OF_HOST_CAPACITY` never sends an urgent capacity notification. Use the daily Discord status and [capacity watch Actions history](https://github.com/guseoh/pawcycle-commerce/actions/workflows/oci-a1-capacity-watch.yml) to verify polling. For missed/failed checks, inspect [monitor health Actions history](https://github.com/guseoh/pawcycle-commerce/actions/workflows/oci-a1-monitor-health.yml). A daily Discord report is evidence of the daily job, not proof that all 24 hourly checks ran.

## 4. Before acting on an alert

Reopen the [OCI Console](https://cloud.oracle.com/) and confirm the latest Capacity Report manually. In **Compute → Instances**, check all relevant compartments for `pawcycle-arm-01` before any instance provisioning; also review Always Free quotas, remaining credits and any active billing charges.

## Files and rollback

- `.github/workflows/oci-a1-capacity-watch.yml`
- `.github/scripts/oci_a1_capacity_monitor.py`
- `.github/scripts/test_oci_a1_capacity_monitor.py`
- `.github/workflows/oci-a1-monitor-health.yml`
- `.github/scripts/oci_a1_monitor_health.py`
- `.github/scripts/test_oci_a1_monitor_health.py`
- `docs/runbook/oci-a1-capacity-watch.md`

Disable the repository variable, remove the workflow with a PR, and revoke the dedicated OCI monitor API key if no longer needed.
