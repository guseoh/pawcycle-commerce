# OCI Tokyo A1 Capacity Watch (GitHub Issues)

This monitor uses a **Compute Capacity Report**, not Compute VM creation. It **never** launches VMs, retries instance creation, creates capacity reservations, or changes running resources. Monitoring is **disabled by default** until the owner enables it.

## Target and behavior

- Target region: `ap-tokyo-1`; availability domain: `nREs:AP-TOKYO-1-AD-1`.
- Shape: `VM.Standard.A1.Flex`, exactly **2 OCPU / 12 GB**.
- GitHub Actions executes each hour at minute 17 UTC, subject to scheduling delay or skipped runs.
- `OUT_OF_HOST_CAPACITY`: no new Issue. If there was an AVAILABLE alert, close it when status changes.
- `AVAILABLE`: create/assign one GitHub Issue to `guseoh`, or reopen it and mention the owner on the next AVAILABLE transition.
- Repeated AVAILABLE results are silent; a manually closed AVAILABLE Issue is not reopened until an unavailable observation has occurred.
- Unexpected capacity status, authentication error, missing secret, or failed GitHub API request: job **fails**, never reports capacity as available.
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

GitHub automatically supplies `GITHUB_TOKEN` with `issues: write` only in this workflow. Do not use the temporary OCI Cloud Shell security-token session for unattended Actions.

## 3. Validate before enabling hourly checks

1. Once merged to **main**, open the [workflow page](https://github.com/guseoh/pawcycle-commerce/actions/workflows/oci-a1-capacity-watch.yml) and click **Run workflow → self_test**. The offline tests must pass with **no OCI credentials**.
2. After the Secrets and OCI IAM policy are configured, click **Run workflow → check**. This makes **one** real report query; confirm a valid status in the run summary.
3. Only then visit https://github.com/guseoh/pawcycle-commerce/settings/variables/actions and create a repository variable **`OCI_A1_MONITOR_ENABLED`** with value **`true`**. Scheduled checks start then.
4. Configure [GitHub notifications](https://github.com/settings/notifications) for participating/assigned/@mentioned Issues via web, email, or mobile; the alert Issue is assigned to the repository owner and mentions them.
5. Occasionally check failed scheduled runs in Actions; scheduling isn't continuous or guaranteed.

To disable immediately, set `OCI_A1_MONITOR_ENABLED` to `false` or remove the variable. No real VM creation is possible in this workflow.

## 4. Before acting on an alert

Reopen the [OCI Console](https://cloud.oracle.com/) and confirm the latest Capacity Report manually. In **Compute → Instances**, check all relevant compartments for `pawcycle-arm-01` before any instance provisioning; also review Always Free quotas, remaining credits and any active billing charges.

## Files and rollback

- `.github/workflows/oci-a1-capacity-watch.yml`
- `.github/scripts/oci_a1_capacity_monitor.py`
- `.github/scripts/test_oci_a1_capacity_monitor.py`
- `docs/runbook/oci-a1-capacity-watch.md`

Disable the repository variable, remove the workflow with a PR, and revoke the dedicated OCI monitor API key if no longer needed.
