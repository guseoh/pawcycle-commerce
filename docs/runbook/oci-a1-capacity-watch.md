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

## Cloudflare Cron + independent missing-check alarms — staged, NOT deployed

Task: `OCI-A1-CRON-001`. This is repository-only preparation on Draft PR #502.
It does **not** create a VM, enable the Auto Provisioner, update OCI, alter the
existing GitHub schedules, activate a Cloudflare Worker, create Healthchecks
checks or handle credentials. Every Cloud/Secret/live-check/cutover step still
requires explicit owner approval. Keep Always Free; never upgrade to PAYG to
support this task.

### Why separate the signals?

The GitHub Actions `schedule` event has been delayed or absent even after
setting a five-minute cron. A successful `workflow_dispatch` HTTP 204 only
means GitHub accepted a request, NOT that a runner executed the OCI API query.
Cloudflare Cron alone cannot detect its own complete stoppage. Two independently
watched Healthchecks.io checks are a **proposed free-tier failure-detection
integration**, distinct from Discord capacity notifications:

| Check name (suggested) | Origin of success signal | Health criterion |
| --- | --- | --- |
| `pawcycle-a1-dispatcher` | Cloudflare Worker after successful history processing and dispatch **or** justified skip for busy/recent check | Worker cron and dispatch control path actually ran |
| `pawcycle-a1-oci-check` | GitHub Actions `monitor` job **after** Python OCI Capacity Report and existing Issue/Discord processing succeed | Actual main-branch live-check path completed |

Set **each check's Simple period to 5 minutes and grace to an initial 10
minutes**, then measure delay and adjust only with evidence. Trigger-delivery
timing is not guaranteed. `OUT_OF_HOST_CAPACITY` counts as a **successful
API check**, not A1 availability. `AVAILABLE` is sent only through the
existing Python alert transition logic. Never fake a real production
`AVAILABLE` response.

Healthchecks.io Hobbyist Free currently supports 20 monitored jobs with 100
recent log entries per job; 5-minute pings retain about 8 hours of history
there. Collect **24-hour evidence from GitHub runs and Cloudflare metrics**,
not by treating Healthchecks retained pings as a full-day ledger. External
alert delivery (email or other supported free destination) is unverified
until an operator actually tests the configured destination.

### Current free-tier evidence (2026-11-04, read only)

- Cloudflare dashboard confirms Workers Free, two existing Dark Todo Workers
  (Production and Preview), and two active Production Cron triggers.
  Adding one PawCycle Cron would use **3 of 5** free triggers, assuming no
  intervening changes.
- Dark Todo had 292 Worker invocations and zero Worker errors during a
  recent 24-hour dashboard window, but the Cron Events listing only showed
  entries through 2026-10-30 KST. Workers Observability was disabled.
  These aggregate invocations do **not** prove 288 individual Cron successes
  or that the PawCycle integration will satisfy the Free 10ms CPU limit.
- Cloudflare Free and Healthchecks Hobbyist usage, enabled triggers, errors,
  credentials and actual alert delivery must be rechecked at rollout.

### Identity, secrets, and trust boundaries

1. Generate a short-lived **fine-grained GitHub PAT** scoped to repository
   `guseoh/pawcycle-commerce` only, with Actions read/write and mandatory
   Metadata read. Dispatch permission applies to Actions broadly within the
   selected repository, **not to one workflow**. Record owner, rotation date,
   and revocation procedure outside source control. Alternatively, design a
   short-lived GitHub App token broker in a separate approved change.
2. After explicit operator approval, create **two** separate free
   Healthchecks.io checks and attach an independently reachable alert
   destination. Configure the schedule and grace above. Copy each generated
   unique UUID Ping URL **only into the designated secrets**; do not paste it
   in Issues, PRs, chat, Wiki, logs or shell command arguments. A Ping URL
   grants the capability to mark a check healthy and must be treated as
   confidential. The implementation accepts only canonical
   `https://hc-ping.com/<uuid>` URLs and never follows redirects.
3. Cloudflare Worker encrypted secrets: `GITHUB_DISPATCH_TOKEN` and
   `HEALTHCHECKS_SCHEDULER_PING_URL`. The first is never sent to Healthchecks.
   `ENABLED=false` is the checked-in source default: no external calls
   while disabled. With `ENABLED=true`, **both** secrets are mandatory.
4. GitHub Actions repository secret: `HEALTHCHECKS_OCI_PING_URL`.
   It is intentionally **optional until the operator provisions the check**
   so existing GitHub schedule jobs cannot break from an unconfigured
   external monitor. The live job prints a non-sensitive *unconfigured*
   warning; that is NOT independent monitoring. Once configured, an invalid
   URL or non-OK response causes the live monitor job to fail. OCI API keys
   and the Discord webhook remain exclusively in GitHub Actions.
5. No D1, KV, Durable Object, Cloudflare paid plan, storage, VM or new
   webhook endpoint is required. The Worker has no HTTP handler, public
   route or preview URL. Keep `workers_dev=false` and `preview_urls=false`.

### Execution semantics

Worker `*/5 * * * *` reads up to 100 recent `main` workflow runs, filtered
to scheduled checks and exact `OCI A1 capacity check` dispatches. It
suppresses duplicate dispatch when a check is in progress/queued/pending
or another check was **created** in the last five minutes. Time comparisons
use the Worker **actual invocation time** (`Date.now()`), not the
original `scheduledTime` of a delayed event. Any future run timestamp beyond
a minute of clock skew fails closed. GitHub's existing `concurrency` group
serializes overlapping jobs without cancellation; history visibility
delays still permit additional serial checks. Never assume exactly-once
dispatch or retry an ambiguous GitHub POST automatically.

Worker sends its scheduler success heartbeat **only after** the GitHub
history and dispatch/skip path succeed. On a known failure, it attempts
a Healthchecks `/fail` ping and fails the Cron event; if the Worker does not
run or the failure ping cannot be delivered, the missing-success alarm
provides the other detection path. HTTP 200 with a "not found" or "rate
limited" body is **not** treated as a valid Healthchecks acknowledgment.
No GitHub Authorization header is transmitted with the heartbeat.

An Actions `monitor` job sends its OCI success ping **only after**
`oci_a1_capacity_monitor.py` completes with exit status zero. No
`pull_request` self-test, `self_test` dispatch, or pure GitHub 204
can send a successful OCI ping. A manual `mode=check` is a real OCI
probe and may signal success; the **separate Cloudflare dispatcher check**
still detects a halted Worker. A failed OCI query, Discord transition or
Issue update must not send a success ping. The daily health workflow is
retained but is not counted as a primary five-minute heartbeat.

A Worker run can succeed when GitHub accepted a dispatch **even while the
OCI check is still queued**. That is intentional: only the second
independent check can certify the actual OCI execution. Existing GitHub
2-hour/3-hour health alarms remain a fallback and are not a substitute
for the independent outage detector.

### Offline tests (no live credentials or Cloud calls)

From the repository root:

```sh
node --test infra/oci-capacity-cron/worker.test.mjs
GITHUB_REPOSITORY=guseoh/pawcycle-commerce python -m unittest discover -s .github/scripts -p 'test_oci_a1*.py' -v
npm exec --yes --package=wrangler@4.157.0 -- wrangler deploy --dry-run --config infra/oci-capacity-cron/wrangler.jsonc
```

On PowerShell set `$env:GITHUB_REPOSITORY = 'guseoh/pawcycle-commerce'`
before the Python command. The PR offline lanes run the Node and Python
tests without tokens. Wrangler dry-run **only bundles locally**. CI Green,
mock success and a successful production deployment of another Worker do
not demonstrate this system's live execution.

### Rollout / stop / recovery (each requires explicit operational approval)

1. Confirm latest merged `main` SHA, PR review, required CI, GitHub workflow
   permissions, original schedules and Cloudflare free-tier capacity. Record
   rollback versions and test procedure with no secret values.
2. Create/verify both Healthchecks checks, set free notification channels,
   and establish private Ping URLs and secret rotation. The operator must
   confirm the correct external account; new third-party account enrollment
   is separate from committing this PR.
3. Deploy the separate Cloudflare Worker with `ENABLED=false` and no public
   route, add approved secrets interactively, and verify **zero dispatches**.
4. After **explicit live monitoring approval**, enable the Worker in a
   reviewed deployment configuration, retain existing GitHub schedules, and
   wait for Cron propagation. Observe at least 60 minutes / 12 expected
   Cloudflare slots. For each slot capture dispatch/skip, main Actions
   run creation, actual OCI job completion, and heartbeat successes.
   Duplicate-check suppression can legitimately yield fewer than 12 GitHub
   runs; record reasoning rather than claiming every slot created a job.
5. Validate both independent check alarms and recovery on **isolated test
   targets**; do not disable production credentials or forge OCI AVAILABLE
   reports. Each newly created Healthchecks check must receive its first
   **verified success ping** before it can be treated as an active dead-man
   detector; an unpinged check is not readiness evidence. Initial
   continuous-evidence target is 24 hours (288 nominal
   5-minute slots). Measure actual completed OCI checks and maximum gap;
   repeated gaps over 15 minutes fail the stability gate.
6. Only after observation and **separate cutover approval** may a follow-up
   PR remove the original capacity watch `schedule` stanza. Keep
   `workflow_dispatch`, health/daily schedules and
   `OCI_A1_MONITOR_ENABLED` intact. Disabling this shared variable
   suppresses the existing daily status, not Worker dispatches.
7. Stop on invalid/expired GitHub token, excessive CPU or API usage,
   repeated queued runs, stale OCI heartbeat, alert delivery failure,
   duplicate AVAILABLE notifications, or an unapproved cost condition.
   Disable the separate Worker (`ENABLED=false`) or remove its Cron and
   confirm dispatches stop after propagation. Keep or restore the original
   GitHub capacity schedule through a reviewed PR. Disable/pause the two
   external checks **only when intentionally stopping** the system, so a
   stopped system does not masquerade as healthy. Never cancel an in-flight
   OCI report while it may be updating transition state.

No Worker deployment, Healthchecks signup, secret registration, heartbeat
delivery, OCI live call, alert drill or schedule cutover was performed by
this repository change. Their statuses remain **NOT_RUN** until independently
verified against the exact rollout.
