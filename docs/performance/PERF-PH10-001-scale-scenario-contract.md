# PERF-PH10-001 Phase 10 Scale Scenario 계약

## 범위

Phase 10은 기존 구조의 단일 method 최적화가 아니라 증거가 있는 scale 문제에 기술 후보를 하나씩 적용하고 동일 조건으로 검증한다. 이번 순서는 Read Scale의 Redis After 준비와 다음 Subscription Burst 측정 계약까지다. 이 문서는 동적 진행 상태나 Production 권고가 아니다.

## 상위 방향과 기술 선택 흐름

PawCycle 후반부는 AI Agent 결과를 통제하는 Lean Harness와 실행 환경, 배포·관측·장애 대응·복구를 포함한 운영 자동화, 대규모 트래픽 Scale Scenario 기반 아키텍처 역량을 함께 발전시킨다. Phase 10은 세 번째 축을 본격적으로 수행한다.

각 Scale Scenario는 다음 순서를 지킨다.

```text
Scale Scenario → Before evidence → 병목·운영 요구 확인 → 기술 후보 비교
→ 기술 선택 → 구현 → After measurement → 유지 또는 보류
```

Redis나 Kafka 자체는 목표가 아니다. 반대로 대규모 트래픽 아키텍처 구현도 프로젝트의 명시적 목표이므로, 측정된 문제·운영 요구와 후보 기술의 해결 능력이 충분히 연결되면 운영 복잡성만을 이유로 회피하지 않고 승인된 범위에서 적극적으로 구현·검증한다.

후보는 cache와 messaging에 한정하지 않는다. 측정 결과에 따라 bounded worker/async executor, Transactional Outbox, idempotent consumer, retry/backoff/DLQ, backpressure/rate limiting, query/read model과 데이터 확장, CDN/object storage, vertical scaling, multi-instance/LB/autoscaling, container orchestration까지 비교할 수 있다. 선택은 현재 병목, correctness·failure handling·operability 요구, 예상 비용과 복구 경계를 함께 설명할 수 있어야 한다.

PERF-PH10-001의 구현 범위는 아래 Read Scale Redis 준비와 Subscription Burst 측정 계약까지로 유지한다. Kafka, Queue, multi-instance, LB와 다른 후보 기술을 이 작업에 추가 구현하지 않는다.

## Read Scale: Redis After

Before는 완료된 PERF-PH9-010 CPU2.0 결과를 그대로 재사용하며 재실행하지 않는다. After는 다음 조건을 함께 고정한다.

| 항목 | 고정값 |
| --- | --- |
| workload | 기존 `capacity-api-products.js`, target 250 RPS |
| warm-up / measurement | 30초 / 120초 |
| backend | Tomcat128, CPU2.0, memory1GiB, PID256, MaxRAMPercentage65, Hikari10 |
| cache | data key `pawcycle:catalog:product-list:v1` + generation key `pawcycle:catalog:product-list:v1:generation`, local Redis, warm-up 중 hit counter 증가 필수 |

`infra/performance/phase10/run-products-redis-after.ps1`은 host local temp 외의 artifact 경로를 거부한다. `-ValidateOnly`와 `-ValidateRuntimeCapability`는 k6를 시작하지 않는다. `-RunAfterFirstResult`는 외부에서 별도 승인된 first-result에만 사용한다. child diagnostic의 preflight가 끝나고 실제 `Start-Process`가 성공한 직후 workload-start marker를 기록하며, 그 이전 실패는 first-result를 소비하지 않는다. marker가 한 번 생성된 뒤에는 이후 성공·실패와 관계없이 `NEVER RERUN`으로 중단한다.

```powershell
pwsh -NoProfile -File infra/performance/phase10/run-products-redis-after.ps1 -ValidateOnly
pwsh -NoProfile -File infra/performance/phase10/run-products-redis-after.ps1 -ValidateRuntimeCapability
```

아래 명령은 이 저장소 준비 작업에서 실행하지 않는다.

```powershell
pwsh -NoProfile -File infra/performance/phase10/run-products-redis-after.ps1 -RunAfterFirstResult
```

After summary는 기존 actual RPS, dropped iterations, p50/p95/p99/max, backend CPU/memory/GC/threads, Hikari active/pending/acquire/usage와 SQL delta에 cache hit/miss/error delta 및 Redis container health/resource를 추가한다. Redis 효과는 처리량·tail·dropped 개선과 함께 miss 이후 measurement의 SQL/Hikari 감소가 일관될 때만 지지한다.

### Read Scale cache correctness

단순 delete-only cache-aside는 concurrent miss가 이전 DB snapshot을 읽은 뒤 Admin commit의 delete보다 늦게 stale 값을 다시 쓸 수 있다. PERF-PH10-001은 이를 막기 위해 miss 시작 시 generation을 캡처하고, Redis conditional-set script가 generation이 그대로일 때만 data key를 저장한다. Product/SKU commit의 after-commit invalidation은 generation 증가와 data key 삭제를 Redis 원자 연산으로 수행한다. 따라서 invalidation 이전에 시작한 miss가 뒤늦게 완료되어도 stale value가 재삽입되지 않아야 한다.

## Local Redis rollback 계약

이번 변경은 local integration 전용이며 Production Redis/AWS resource를 만들지 않는다. 롤백은 authoritative MySQL과 `mysql-data` volume을 보존한 채 cache 경로만 제거하는 것을 원칙으로 한다.

- 즉시 기능 우회가 필요하면 Backend의 product-list cache enable 값을 `false`로 되돌리고 Backend를 기존 local integration 방식으로 재생성한다. 이 상태에서는 Redis가 남아 있어도 `/api/products`는 authoritative DB reader만 사용해야 한다.
- 전체 repository rollback은 PR 변경을 일반 revert하여 Redis dependency, local Redis service, cache 설정과 After harness를 함께 제거한다. history rewrite, reset, force push는 사용하지 않는다.
- Redis container는 persistence와 named volume을 사용하지 않으므로 cache data는 disposable artifact다. Redis service 제거 시 해당 local Redis container만 명시적으로 정리하고 `mysql-data`, 다른 service container·volume, 결과 artifact는 임의 삭제하지 않는다. `--remove-orphans`는 사용하지 않는다.
- rollback 후 Backend health와 `/api/products` 정상 응답, Redis 비의존 동작, MySQL data 보존을 확인한다. performance first-result marker와 이미 생성된 측정 artifact는 rollback과 무관하게 보존하며 재실행 근거로 삭제하지 않는다.
- rollback target인 base `main` local topology와 기존 DB-only product read 경로는 이 PR 이전 repository/CI 기준선이다. 이번 PR에서는 실제 destructive rollback이나 Production 실행을 수행하지 않았으며, forward compose syntax/runtime capability와 repository validation만 검증했다.

## 다음 Scale Scenario: Subscription Burst

현재 기준 경로는 `SubscriptionOrderAutomationTrigger`의 bounded batch 호출, stable due 후보 조회, subscription별 `REQUIRES_NEW` 처리와 다음 tick 재선택이다. 실험은 새 queue 없이 이 구조의 한계를 먼저 측정한다.

### 입력과 실행 경계

- local test data로 동일 시각 due subscription cohort를 준비하고, 승인된 단계별 cohort 크기와 고정 batch size를 기록한다.
- 같은 subscription의 중복 due를 만들지 않으며 기존 schedule/order unique·stable-order 계약을 보존한다.
- scheduler tick 또는 동등한 service entrypoint의 시작·종료를 한 실행 단위로 기록한다. Production data와 외부 결제 호출은 사용하지 않는다.
- 정상 cohort와 별도로 일부 target 실패 fixture를 두어 다른 target 처리 지속과 다음 tick 재선택을 확인한다.

### 수집 계약

- `orders.created / duration`으로 주문 생성 throughput과 tick별 처리 시간을 수집하고, due backlog가 0이 될 때까지 전체 elapsed time과 tick 수를 기록한다.
- processed, created, failure, duplicate/no-op counter와 DB row 결과를 대조한다.
- backend CPU/memory/GC/threads, Hikari active/pending/acquire/usage, MySQL statement/lock wait와 connection 수를 같은 window에서 수집한다.
- 후속 billing/payment 대상 생성 시각과 처리 시작·완료 시각 차이로 지연 전파를 기록한다.
- 실패 target의 transaction rollback, 다른 target 성공, 다음 tick 재선택과 중복 Order 부재를 검증해 실패 격리와 재처리 필요성을 판정한다.

### Kafka 후보 진입 조건

Kafka는 현재 구조에서 backlog가 허용 시간 안에 소진되지 않거나, 주문 생성과 후속 처리 속도 분리가 필요하거나, process 재시작을 넘는 durable 전달·독립 consumer 확장·명시적 재처리가 필요하다는 증거가 함께 확인될 때 후속 후보로 비교한다. 해당 요구와 Kafka의 해결 능력이 충분히 연결되면 적극적으로 도입·검증하되, 증거 없이 queue, topic, producer/consumer 또는 Kafka 운영 구성을 설계·구현하지 않는다.

## 실행 상태

PERF-PH10-001에서는 k6, JFR, Redis After와 Subscription Burst workload를 실행하지 않는다. CI와 repository validator 통과는 Production Verified를 의미하지 않는다.
