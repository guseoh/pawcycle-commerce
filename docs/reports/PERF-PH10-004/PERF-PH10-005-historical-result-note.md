# PERF-PH10-004 Historical 10k Result Note

## Evidence boundary

PERF-PH10-004 first-result is consumed and `NEVER RERUN`. This note preserves the historical decision evidence only; it is not Production capacity evidence or an actual-user 10k result.

The generated durable redacted candidate is preserved without modification:

`evidence-candidates/subscription-burst-decision-10k-91a96a880332-20260824T0416116631842Z.json`

- workload identity: `phase10-subscription-burst-decision-10k-v1`
- cohort: `10,000`
- historical source SHA: `91a96a88033293bcc1910603f632adc97991373b`

## Historical result

- processed/created: `10,000 / 10,000`; correctness failures and duplicate-no-op: `0 / 0`
- raw drain: `495193.796ms`; raw throughput: `20.1941 orders/s`
- synthetic 15-minute decision target: PASS
- default scheduler projection: `6435194ms`
- resource saturation evidence: none observed in the preserved candidate metrics (Hikari active peak `2`, pending `0`; MySQL row-lock wait/time `0 / 0ms`)

The harness result remains `harnessFailure=true`. The reason is the Prometheus metric reconciliation mismatch: the driver expected `100` batches, `10,000` processed, and `10,000` created, while the collected end metrics were `98 / 9,800 / 9,800` with duration count `100`. This PR does not retroactively change that historical harness result to success.

The durable candidate retains its generated integer `rawDrainElapsedMs` aggregate unchanged; the fractional raw-drain value above is the historical driver result.

## Decision

Gate A is scheduler/batch policy priority. A later bounded catch-up design and After experiment require their own approved one-shot contract; this evidence does not authorize a workload rerun or a Production scheduler change.
