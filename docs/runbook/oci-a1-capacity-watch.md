# OCI Tokyo A1 Capacity Watch (Discord + GitHub Issue Tracking)

Task ID: `OCI-A1-001`

This is the **monitor-and-notify only** operating mode. The separate [OCI A1 Auto Provisioner](oci-a1-auto-provisioner.md) is **not activated**; no VM is created automatically and no provisioner IAM or systemd timer is needed for this workflow.

This monitor uses a **Compute Capacity Report**, not Compute VM creation. It **never** launches VMs, retries instance creation, creates capacity reservations, or changes running resources. Monitoring is **disabled by default** until the owner enables it.

## Target and behavior

- Target region: `ap-tokyo-1`; availability domain: `nREs:AP-TOKYO-1-AD-1`.
- Shape: `VM.Standard.A1.Flex`, exactly **2 OCPU / 12 GB**.
- Primary GitHub Actions capacity checks target **every five minutes** (UTC minutes 03, 08, 13, 18, 23, 28, 33, 38, 43, 48, 53, 58), subject to scheduling delays or skipped runs. An Actions `success` means the API query ran; it does **not** mean capacity was available.
- A separate **OCI A1 Monitor Health** workflow inspects main-branch schedule and identified live-dispatch history every 2 hours at minute 43 UTC; it does not start a new health job after every five-minute poll. If the last successful capacity check is older than **3 hours**, or the newest check failed/skipped, it sends **one Discord warning per incident** and creates/reopens an unassigned health-tracking Issue. Recovery closes that Issue without repeating warnings.
- At **00:47 UTC (~09:47 KST)** each day, the health workflow makes one additional read-only Capacity Report request and sends a **Discord daily status** even when the shape is OUT_OF_HOST_CAPACITY. The status contains a fresh point-in-time OCI result, plus successful/total scheduled runs and failed/skipped counts from the last 24 hours. The workflow paginates run history beyond the GitHub API's first 100 results. AVAILABLE uses the existing transition notification and Issue tracking; it is not re-alerted while status remains AVAILABLE.
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

## 3. Validate before enabling five-minute checks

1. Once merged to **main**, open the [workflow page](https://github.com/guseoh/pawcycle-commerce/actions/workflows/oci-a1-capacity-watch.yml) and click **Run workflow → self_test**. The offline tests must pass with **no OCI credentials and no Discord message**.
2. After the Secrets and OCI IAM policy are configured, click **Run workflow → check**. This makes **one** real report query; confirm a valid status in the run summary. If it detects a new AVAILABLE transition, it will send a real Discord alert.
   - After deploying the health workflow, its **Run workflow → self_test** checks both monitor and health unit tests without credentials; **Run workflow → preview** inspects run history without an OCI call, Discord message or Issue write. There is no manual live-health-alert option.
3. Only then visit https://github.com/guseoh/pawcycle-commerce/settings/variables/actions and create a repository variable **`OCI_A1_MONITOR_ENABLED`** with value **`true`**. Scheduled checks start then.
4. Capacity alerts go to the channel configured by `DISCORD_WEBHOOK_URL`; GitHub Issues are unassigned tracking records without owner mentions. Repository watchers may still receive GitHub notifications according to their own settings.
5. Occasionally check failed scheduled runs in Actions; scheduling isn't continuous or guaranteed.

To disable both GitHub capacity and health schedules, set `OCI_A1_MONITOR_ENABLED` to `false` or remove the variable. The health workflow is gated by the same variable; its offline self-test and read-only preview remain available. This variable does not stop Worker/manual dispatches; stop the Worker separately as described below. No real VM creation is possible in either workflow.

**Alert troubleshooting:** `OUT_OF_HOST_CAPACITY` never sends an urgent capacity notification. Use the daily Discord status and [capacity watch Actions history](https://github.com/guseoh/pawcycle-commerce/actions/workflows/oci-a1-capacity-watch.yml) to verify polling. For missed/failed checks, inspect [monitor health Actions history](https://github.com/guseoh/pawcycle-commerce/actions/workflows/oci-a1-monitor-health.yml). A daily Discord report is evidence of the daily job, not proof that every five-minute scheduled check ran.

## Monitor-health acceptance checks (after separate deployment approval)

1. Review the new `OCI A1 Monitor Health` PR offline test results. Do not treat PR CI as a live OCI/Discord test.
2. After merge, run its `workflow_dispatch → self_test`, then `preview` on `main`. Preview only reads scheduled run history and must not post to Discord or change an Issue.
3. Once scheduled in production, confirm **one daily Discord status** appears around 09:47 KST, including the direct OCI Capacity Report status and the preceding 24-hour successful/total scheduled check counts. `OUT_OF_HOST_CAPACITY` is a normal capacity status, not a failed job.
4. If the latest primary scheduled job fails or the last success becomes over 3 hours old, expect one *health-warning* Discord alert and an unassigned `[OCI A1 Monitor] Scheduled-check health alert` Issue. A later healthy observation should close that Issue without another warning.
5. Validate the **AVAILABLE transition alert** only against a genuine OCI `AVAILABLE` report; never fake availability, create a VM, or emit a false production notification just to test Discord.

The health workflow does not check `pawcycle-arm-01` automatically, and scheduled watchdog runs can themselves be delayed or skipped by GitHub Actions.

**Operations choice (2026-11-03):** Use report-only monitoring and manually create A1 after receiving a genuine AVAILABLE alert. Do **not** create a new provisioner IAM user, register its key, initialize its state, or enable its systemd timer. The staged `/etc/pawcycle-a1/provisioner.json` remains `DISABLED` on `app01`; its staged installation is not needed for this GitHub Actions monitor. GitHub Actions is a best-effort 5-minute scheduler, not a guaranteed instant notification service. Standard GitHub-hosted runners in this public repository have no runner-minute charge; OCI API rate limits and GitHub scheduling limitations still apply.

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

## Cloudflare Cron dispatcher — staged, not deployed

Task ID: `OCI-A1-CRON-001`. Repository preparation only; Cloud deployment, secrets,
live checks, schedule cutover and merge require separate user approval. Always Free
only, no PAYG upgrade, no Terraform or automatic A1 creation. The existing Python
Capacity Report, Discord transition deduplication and Issue behavior stay unchanged.
OCI credentials stay in GitHub. The Worker has no HTTP handler, routes or public preview.

### Authentication and secrets

- Create a short-lived **fine-grained PAT**, owner `guseoh`, selected repository
  **only `pawcycle-commerce`**, **Actions: read and write** plus mandatory Metadata read.
  No Contents write, Issues write, administration or classic `repo` scope. Actions
  write is the minimum dispatch permission, but is repository-wide, not workflow-scoped;
  the hardcoded target is a code guard, not a token permission boundary.
- Record the operator and expiry reminder (for example, rotate within 30 days).
  A renewable GitHub App installation token is a future alternative; do not store
  a one-hour installation token as a permanent secret.
- After separate Cloud/secret approval, store the PAT only as encrypted Cloudflare
  secret `GITHUB_DISPATCH_TOKEN`, via dashboard or interactive
  `wrangler secret put GITHUB_DISPATCH_TOKEN` from `infra/oci-capacity-cron`.
  Never put it in `vars`, command arguments, PR, logs or chat. Local `.dev.vars*`
  and `.env*` are ignored; offline tests need no real secrets. Never copy the OCI
  key, Discord webhook or personal CLI token into Cloudflare.
- Operator Cloudflare authentication is separate: select the intended account and
  restrict the deployment token to Workers Scripts edit for that account, plus only
  account-read permissions needed by the chosen CLI flow. No GitHub deployment job
  or Cloudflare credentials in Actions are introduced.

### Duplicate suppression and status monitoring

`*/5 * * * *` requests the fixed workflow on `main` with `mode=check`. Source config
is **ENABLED=false**. A tick reads the newest 100 main workflow runs and suppresses
dispatch if a check is unfinished (including queued/waiting/pending) or was created
less than five minutes ago. The existing Actions concurrency group serializes all
primary monitor jobs with `cancel-in-progress: false`. History reads are not atomic:
simultaneous Cron delivery or GitHub visibility delay can enqueue an extra serial
check. Exactly-once delivery is not guaranteed; no immediate retry of an ambiguous
POST, no KV/Durable Object or paid resource is added.

Run history accepts `schedule` and dispatches with exact API `display_title`
**OCI A1 capacity check**. Workflow `run-name` distinguishes check from self_test;
legacy manual/branch/self-test successes do not refresh health. The existing Python
watchdog now uses this filter and paginates up to 1,000 raw runs, failing on truncation.
Its legacy “scheduled” labels/counts now include identified dispatch checks. Its
3-hour threshold, 2-hour schedule and incident-deduplicated Discord/Issue behavior
remain unchanged, as does the daily fresh OCI report.

Worker logs record `dispatched`, `skipped`, `healthy` or `error` with redacted codes.
HTTP 204 is acceptance, **not** proof of OCI query or Discord delivery. A completed
successful live check newer than ten minutes is required for Worker health, measured
conservatively from run creation. Missing/stale success fails the Cron event with
`stale_check`, after attempting dispatch if idle. A flood of self-tests can hide a
success beyond the newest-100 window and cause a conservative stale warning.
Network/15-second timeout, invalid history, auth/rate-limit and other HTTP failures
fail visibly without response-body/secret logging. No failure implies AVAILABLE.

**Logs are not automatic paging.** Full-sampled Workers Logs and Cron execution
status expose failures; this Worker does not send Discord health alerts. Before
unattended rollout, arrange and verify an independently observed alarm for failed
ticks **and absence of a healthy live check for >10 minutes**. The existing GitHub
watchdog is slower and can miss schedules itself. It cannot reliably detect total
GitHub/Cloudflare failure. Without an independent dead-man monitor, keep attended
observation and do not claim continuous coverage. Neither scheduler guarantees
exact five-minute execution.

### Offline checks before approval

From repository root:

```sh
node --test infra/oci-capacity-cron/worker.test.mjs
GITHUB_REPOSITORY=guseoh/pawcycle-commerce python -m unittest discover -s .github/scripts -p 'test_oci_a1*.py'
npm exec --yes --package=wrangler@4.157.0 -- wrangler deploy --dry-run --config infra/oci-capacity-cron/wrangler.jsonc
```

On PowerShell, set `$env:GITHUB_REPOSITORY = 'guseoh/pawcycle-commerce'` before Python.
Dry run bundles locally without uploading or configuring secrets. The capacity PR
lane runs Node scheduler tests and Python monitor tests; the health PR lane runs
all OCI offline tests. The normal classifier remains unchanged and conservatively
enables all lanes for workflow/new infra changes. Mock tests and PR CI do not prove
live dispatch, Cron, OCI or Discord operation.

### Rollout — separate user approval required

1. Keep the PR Draft until reviewed. Confirm the approved main SHA includes the
   run-name/filter changes and green CI; merge requires its own approval.
2. Confirm intended Cloudflare account/free plan, available Cron capacity, current
   request/CPU/log allowances and no paid bindings or automatic plan upgrade.
   At most 288 ticks/day, normally two GitHub API calls/tick; check actual account
   usage and official limits. Record current Worker version (or absence), config,
   token expiry and GitHub variables/schedules without secret values.
3. After Cloud/secret approval, deploy **ENABLED=false** first and add the secret
   interactively. Verify no outbound dispatches, no routes and previews disabled.
4. With explicit live-check approval, use a reviewed local config copy changing
   only `vars.ENABLED` to `true` and deploy using that config. Keep workflow/main/check
   fixed; retain this enabled config for subsequent attended deployments.
5. Keep GitHub schedules unchanged during dual-run observation. Cron propagation
   can take up to 15 minutes. After propagation, observe at least 60 minutes / 12
   expected ticks. Record tick time, dispatch acceptance, resulting main run link,
   monitor job completion, actual OCI summary and last-success age. Confirm no
   concurrent primary checks or repeated AVAILABLE alerts. Validate failure/recovery
   alert delivery in an isolated target; do not break production credentials or fake
   production AVAILABLE. Genuine AVAILABLE notification remains unverified until
   OCI actually reports it; record that limitation.
6. Stop on auth errors, growing queue, success age >10 minutes, duplicate alerts,
   limit/cost risk or missing independent alerts; follow rollback below.
7. **Obtain separate approval to disable the GitHub capacity schedule.** Use a
   follow-up reviewed PR removing only its `schedule` block. Keep workflow_dispatch,
   health schedules and `OCI_A1_MONITOR_ENABLED` unchanged. Do not disable the workflow
   or turn the shared variable false: that would also disable health/daily reporting.
   Repeat observation after cutover. No schedule is disabled by this PR.

### Stop and rollback — within approved operations scope

- Disable Worker `ENABLED` and deploy, or remove its Cron trigger in the dashboard.
  Confirm dispatches stop after propagation and inspect queued runs. Do not cancel
  a running monitor while it may update Discord/Issue transition state.
- `OCI_A1_MONITOR_ENABLED=false` stops GitHub schedules, **not Worker/manual dispatch**.
  Stop the Worker separately; token revocation is additional containment if needed.
- Before cutover, the original GitHub schedule remains the fallback. After cutover,
  restore `3-59/5 * * * *` through an approved PR, retaining manual and health paths.
  This restores the previous best-effort behavior, including its missed-run limitation.
- Check enable/Cron config when restoring a recorded Worker version: code-version
  rollback alone must not be assumed to restore triggers/secrets. Revoke the dedicated
  PAT and remove the Worker if retiring it. Repository rollback uses a revert PR,
  never direct main write/history rewrite. No OCI resource/provisioner change is needed.

References: [Cloudflare Cron](https://developers.cloudflare.com/workers/configuration/cron-triggers/),
[secrets](https://developers.cloudflare.com/workers/configuration/secrets/),
[limits](https://developers.cloudflare.com/workers/platform/limits/),
[GitHub dispatch permissions](https://docs.github.com/en/rest/actions/workflows#create-a-workflow-dispatch-event).
