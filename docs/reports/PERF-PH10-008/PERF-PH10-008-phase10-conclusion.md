# PERF-PH10-008 Phase 10 Conclusion

## 목적

Phase 10 Read Scale와 Subscription Burst의 authoritative evidence를 연결해 기술 선택과 Phase 11 residual limit를 확정한다.

## Evidence boundary

This note connects the completed Phase 10 synthetic/local decision evidence. It does not report a Production capacity limit, customer count, Production traffic result, SLA, or SLO. No workload was rerun for this conclusion.

The evidence sources are:

- Read Scale Before: [PERF-PH9-014 Phase 9 conclusion](../../performance/PERF-PH9-014-phase9-conclusion.md)
- Read Scale After result: [Issue #202 PERF-PH10-001 Read Scale result](https://github.com/guseoh/pawcycle-commerce/issues/202#issuecomment-5386957588)
- Read Scale After contract and fixed target: [PERF-PH10-001 scale scenario contract](../../performance/PERF-PH10-001-scale-scenario-contract.md)
- Subscription Burst Before: [PERF-PH10-005 historical result note](../PERF-PH10-004/PERF-PH10-005-historical-result-note.md)
- Subscription Burst After and Gate A: [PERF-PH10-007 decision note](../PERF-PH10-006/PERF-PH10-007-gate-a-decision-note.md)

The authoritative Redis After measured result is the Issue #202 result linked above; the repository scale-scenario document defines the experiment contract and fixed target rather than serving as the measured-result record.

## Read Scale conclusion

PERF-PH9-010 Before recorded about `193.49 RPS`, `5,808` dropped iterations, p95 `6854.56ms`, p99 `8590.75ms`, Hikari pending peak `116`, and `48,154` relevant SQL statements. The Redis After experiment processed its `250 RPS` target with `0` dropped iterations, p95 `4.71ms`, p99 `26.99ms`, `194` relevant SQL statements, cache hit/miss/error `33035 / 97 / 0`, and Hikari active/pending peaks `0 / 0`.

- Redis cache: **KEEP**

Redis removes structural cost from the repeated public read path. The `250 RPS` figure is the target handled by this experiment, not a maximum capacity claim or a Production SLA/SLO.

## Subscription Burst conclusion

PERF-PH10-004 Before used cohort `10,000`, batch `100`, and fixed delay `60,000ms`. Its raw drain was `495193.796338ms` at `20.1941140497939 orders/s`, with `100` batches and a scheduler projection of `6435194ms`. Correctness remained `10,000 -> 0` with failures/no-op `0 / 0`; Hikari active/pending was `2 / 0` and MySQL row-lock waits/time was `0 / 0ms`.

PERF-PH10-006 After used cohort `10,000`, batch `500`, and fixed delay `15,000ms`. Its raw drain was `458710.08151ms` at `21.80026208946968 orders/s`, with `20` projected ticks and a `743710ms` scheduler projection. It retained `10,000 -> 0` correctness, processed/created `10,000 / 10,000`, failures/no-op `0 / 0`, Hikari active/pending `2 / 0`, MySQL row-lock waits/time `0 / 0ms`, matched metric reconciliation, and `harnessFailure=false` / `collectorFailure=false`.

`743710ms` is not a measured scheduler completion time. It is a projection from the measured raw drain plus configured fixed delays; the `900s` target is synthetic, not a Production SLA/SLO. PERF-PH10-004 remains historical `harnessFailure=true` because of its metric snapshot reconciliation mismatch and is not retroactively changed. PERF-PH10-006 is `CONSUMED` and `NEVER RERUN`.

- scheduler tuning: **KEEP**
- bounded catch-up: **DEFER**
- bounded async/worker: **DEFER**
- Queue/Kafka/Outbox/DLQ: **NOT SELECTED**

The sequential raw throughput and batch/cadence-only projection support the synthetic target without correctness loss, resource saturation evidence, or reconciliation failure.

## Event Reliability

Issue #202 makes Event Reliability conditional on adopting messaging/event-driven structure. That structure is not selected here, so Transactional Outbox, idempotent consumer, retry, and DLQ implementations are not added to complete Phase 10.

Reconsider them only when evidence shows a need for backlog survival across process restart, producer/consumer decoupling, independent consumer scaling, retry/replay, or delivery observability.

## Residual limit — Phase 11 handoff

The residual limit after Phase 10 is the scale and failure domain beyond a single application instance. Phase 11 must collect separate evidence before selecting a solution for:

- single-instance resource ceiling and vertical scaling effectiveness
- multi-instance readiness, session sharing/stickiness, and scheduler duplicate execution/singleton coordination
- Load Balancer distribution/target health and instance failure isolation
- horizontal scaling Before/After

These Phase 10 results do not establish that multi-instance, Load Balancing, ECS, or EKS is required.

## 위험·제한

This conclusion is limited to the documented synthetic/local evidence. It neither validates Production capacity nor removes the need for separate Phase 11 evidence before selecting single-instance scaling, multi-instance, or failure-isolation technology.

## Future candidates

No implementation or roadmap numbering changes are made here.

- Lean Harness improvements: future candidate
- code refactoring and quality improvements: future candidate
- MVP4: review candidate only; not committed
