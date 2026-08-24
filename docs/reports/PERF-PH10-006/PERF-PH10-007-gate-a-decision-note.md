# PERF-PH10-006 Scheduler Tuning After Gate A Decision

## Evidence boundary

This note preserves the synthetic local decision workload evidence for Issue #214. It is not Production traffic, a customer count, Production capacity evidence, or an actual-user 10k result. The `900s` target is a synthetic decision target, not a Production SLA or SLO.

PERF-PH10-006 is consumed and `NEVER RERUN`. The generated durable redacted candidate is preserved without modification, regeneration, formatting, or field reordering:

`evidence-candidates/subscription-burst-scheduler-tuning-after-10k-43d0198814e8-20260824T0638310795301Z.json`

- SHA-256 before and after this change: `18A540B7EEBA0F5D4EBDE32DC6B2A486D31EFD082F2D87A7742090C78635576C`
- source SHA: `43d0198814e8b9d5640f39a4298323651248aa77`
- workload identity: `phase10-subscription-burst-scheduler-tuning-after-10k-v1`
- cohort: `10,000`; batch: `500`; fixed delay: `15,000ms`

## Before / After comparison

| Evidence | PERF-PH10-004 Before | PERF-PH10-006 After |
| --- | --- | --- |
| cohort / batch / fixed delay | `10,000` / `100` / `60,000ms` | `10,000` / `500` / `15,000ms` |
| raw drain / throughput | `495193.796338ms` / `20.1941140497939 orders/s` | `458710.08151ms` / `21.80026208946968 orders/s` |
| batches | `100` | `20` (p50 `22603.944866ms`, p95 `25470.042458ms`, max `26634.926627ms`) |
| correctness | `10,000 -> 0`; failures/no-op `0 / 0` | `10,000 -> 0`; processed/created `10,000 / 10,000`; failures/no-op `0 / 0` |
| scheduler projection | `6435194ms` | `20` ticks, `743710ms` |
| Hikari | active peak `2`; pending `0` | active peak `2`; pending `0` |
| MySQL row locks | waits/time `0 / 0ms` | waits/time `0 / 0ms`; final current lock waits `0` |
| harness / reconciliation | historical `harnessFailure=true`; reconciliation mismatch | `harnessFailure=false`; `collectorFailure=false`; reconciliation `true`; driver exit `0` |

The After raw decision target is `true`, and its scheduler projection passes: `743710 <= 900000`. `743710ms` is not a measured scheduler completion time: it is the measured raw drain plus configured `15,000ms` fixed delays between projected scheduler ticks.

After runtime aggregates: CPU peak `123.06%` in the isolated 2 CPU envelope, process CPU peak `0.52`, memory peak `36.48%`, PIDs peak `41`, JVM live threads `27`, MySQL relevant statements `210074`, and final backend `health=healthy|restart=0|oom=false`.

PERF-PH10-004 remains historical `harnessFailure=true` due to its Prometheus metric reconciliation mismatch. This decision does not retroactively convert that historical result to Green.

## Gate A decision

Gate A is supported.

- scheduler tuning: **KEEP**
- bounded catch-up: **DEFER**
- bounded async/worker: **DEFER**
- Queue/Kafka/Outbox/DLQ: **NOT SELECTED**

The sequential raw throughput supports the synthetic 15-minute target. The batch/cadence-only change reduces the scheduler projection to about 12 minutes 24 seconds without correctness loss, resource saturation evidence, or reconciliation failure. No Production scheduler setting changes are authorized by this decision.

Queue/Kafka remains deferred until operating requirements show a need for backlog survival across process restart, producer/consumer decoupling, independent consumer scaling, retry/replay, or delivery observability.
