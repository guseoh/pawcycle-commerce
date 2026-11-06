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

## Cloudflare Free Cron dispatcher — short-lived, not deployed

Task ID: `OCI-A1-CRON-001`. As of 2026-11-04 this is **repository-only**
preparation. It does not change the running OCI environment, create VMs,
enable Auto Provisioner, turn on paid cloud resources, or deploy Cloudflare.
The expected temporary use window is until approximately **November 6–7,
2026**, at which point the operator decides to stop or extend monitoring.

### Architecture and responsibility

- Cloudflare Workers Free: `*/5 * * * *` triggers a dedicated scheduler
  with no HTTP handler, routes, public URL or KV/D1/DO binding. Its checked-in
  default is `ENABLED=false`.
- Cloudflare sends a fixed `main` / `workflow_dispatch(mode=check)` to
  the existing GitHub Actions Capacity Watch. OCI credentials, Python report
  logic, genuine `AVAILABLE` transition and GitHub Issue tracking **stay
  inside GitHub**.
- Existing Discord notifications remain the sole real-time capacity alert
  channel. Existing two-hour GitHub health watchdog and daily status report
  remain unchanged. **Healthchecks.io and Slack are NOT required** for this
  short-term configuration. Slack may replace Discord later only after
  separately approved notification-contract and delivery tests.
- HTTP 204 from GitHub is only dispatch acceptance. It does not prove the
  job ran or OCI was queried. A `success` on an OCI monitor job does not
  imply A1 capacity was `AVAILABLE`.

The Worker reads up to 100 recent `main` workflow runs, accepts only
`schedule` and exact-title `OCI A1 capacity check` dispatches, and
suppresses a new dispatch if a check is queued/running or its GitHub run
record was created less than **four minutes** ago. GitHub may register an
accepted dispatch roughly 30 seconds after its originating five-minute Cron
tick, so a strict five-minute check on `created_at` can systematically skip
every other slot. A one-minute registration-lag allowance permits the next
five-minute Cron tick while retaining the busy-check guard and a four-minute
minimum age. It compares against the **actual invocation time**,
not the potentially delayed `scheduledTime`. This is best-effort timing, not
exactly-once execution or guaranteed five-minute cadence. It rejects malformed/future
timestamps and GitHub API/network failures. If no completed successful
check was created in the last ten minutes, it emits `stale_check` in
Cloudflare Cron status/logs after attempting a dispatch if idle. A
`stale_check` is a failure signal, **not an automatic Discord warning**.
GitHub concurrency serializes overlapping runs, but exactly-once
delivery is not guaranteed. Do not retry ambiguous POSTs automatically.

### Free-tier and credential preflight

Cloudflare account observations (2026-11-04): Workers Free, Dark Todo
Production and Preview, and two configured Dark Todo Production Cron
triggers. An additional PawCycle Cron would be three of five free triggers,
subject to later account changes. Dark Todo had 292 Worker invocations
in a 24-hour metric window and zero errors; these aggregate numbers
**do not prove 288 successful individual Cron executions**. PawCycle Worker
CPU under Free's 10ms limit must be measured independently.

Create a **short-lived fine-grained GitHub PAT** with only
`guseoh/pawcycle-commerce` selected and Actions read/write + mandatory
Metadata read; classic `repo` scope is unnecessary. Actions write can
dispatch other workflows in that same repository — fixed target code is
not a token-scoped security guarantee. Record rotation and revoke at
the end of this temporary campaign.

After separate authorization for Cloudflare Secrets, set
`GITHUB_DISPATCH_TOKEN` as an encrypted Worker secret. Never commit,
paste or log token, OCI private key, Discord webhook or secret values.
The Worker must not send the GitHub Authorization header to any other
host. Use no extra notification service and incur no paid-plan upgrade.

### Offline checks

```sh
node --test infra/oci-capacity-cron/worker.test.mjs
GITHUB_REPOSITORY=guseoh/pawcycle-commerce python -m unittest discover -s .github/scripts -p 'test_oci_a1*.py' -v
npm exec --yes --package=wrangler@4.157.0 -- wrangler deploy --dry-run --config infra/oci-capacity-cron/wrangler.jsonc
```

On PowerShell set `$env:GITHUB_REPOSITORY = 'guseoh/pawcycle-commerce'`
before running Python. These offline checks do not provision OCI
resources or send Discord alerts. Dry-run is a local bundle only.

### 2026-11-07 cadence correction evidence (repository-only)

- The 2026-11-06 GitHub run history contains a repeated ~10-minute
  `workflow_dispatch` pattern despite the Worker declaring `*/5`.
  The strict five-minute `created_at` cooldown can explain alternating
  skipped Cron ticks; this is a code-based cause hypothesis, not a verified
  Cloudflare runtime trace.
- The last inspected dispatched capacity check (2026-11-06 14:50 UTC)
  returned `OUT_OF_HOST_CAPACITY`. The observed daily Discord status send
  returned HTTP 204. An actual `AVAILABLE` transition notification was
  not observed.
- This correction changes only the Worker cooldown plus offline tests.
  **It has not been deployed to Cloudflare**. The current Worker enabled
  state, token expiry, Free-tier Cron status and reasons for an hours-long
  absence of dispatches are **not verified** by GitHub Actions logs.
  Check actual Worker deployed version/ENABLED/trigger/tail logs without
  exposing tokens before approving any rollout.
- The temporary Cloudflare campaign's November 6–7 cutoff is not silently
  extended. Re-activation beyond the approved window needs a fresh explicit
  operations decision. Until then the existing GitHub monitor and daily
  health reporting continue independently, subject to their schedule delays.

### Rollout, evidence, and cutoff (separate operational approval required)

1. Recheck the approved `main`, required CI, review blockers,
   Cloudflare Free/active Cron limits, PAT permission/expiry, existing
   GitHub schedule and latest actual OCI check. Record rollback settings
   without recording secret values.
2. Deploy Worker with **`ENABLED=false`**, no public HTTP route, and
   verify it cannot dispatch. Register the dedicated token as a Secret.
3. With explicit live-monitoring approval, enable the dedicated Worker
   while keeping the existing GitHub capacity schedule unchanged.
   Cloudflare Cron propagation is not instantaneous. Monitor the first
   60 minutes and correlate Cloudflare tick timestamps, GitHub dispatch
   runs and `OCI A1: status=...` output. Do not count skipped ticks as
   successful OCI queries. Verify no repeated `AVAILABLE` alerts.
4. For the remaining ~2–3 days, periodically inspect the actual
   **completed OCI check timestamps and maximum gaps** rather than
   assuming every five-minute slot ran. The existing GitHub watchdog
   itself may be delayed or omitted. A complete Cloudflare/ GitHub
   outage may go unnoticed until an operator checks manually.
5. Stop on repeated >15-minute completed-check gaps, rate-limit/auth
   errors, a growing queue, repeated AVAILABLE alerts, unexpected
   Free-tier CPU limits or any unapproved cost. Keep the report-only,
   manual VM creation policy.
6. At the **November 6 or 7 cutoff**, disable the dedicated Worker/
   Cron and confirm no new dispatches. Revoke its GitHub token and
   preserve sanitized evidence. The original GitHub schedule stays
   unchanged in this PR; stop/continuation of that original schedule
   is a separate explicit decision. Do not disable
   `OCI_A1_MONITOR_ENABLED` merely to stop the Worker because it also
   affects the existing health/daily reports.

### Stop and rollback

Cloudflare: set `ENABLED=false` through an approved deployment or
remove its Cron trigger, confirm dispatches stop after propagation.
GitHub: retain the existing schedule and its monitor secrets.
If a later cutover removes GitHub schedule in a separately approved PR,
restore it using a reviewed PR. No Auto Provisioner, OCI Compute
or paid resource creation is a rollback action.

**Not yet verified:** live Worker deployment, new dispatch and OCI
execution, Discord actual delivery after this change, sustained
five-minute coverage, Cloudflare CPU use or manual cutoff. CI Green
cannot be reported as Production Verified.
