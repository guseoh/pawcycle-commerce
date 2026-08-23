# PERF-PH10-001 Phase 10 Scale Scenario 계약

## 범위

Phase 10은 기존 구조의 단일 method 최적화가 아니라 증거가 있는 scale 문제에 기술 후보를 하나씩 적용하고 동일 조건으로 검증한다. 이번 순서는 Read Scale의 Redis After 준비와 다음 Subscription Burst 측정 계약까지다. 이 문서는 동적 진행 상태나 Production 권고가 아니다.

## Read Scale: Redis After

Before는 완료된 PERF-PH9-010 CPU2.0 결과를 그대로 재사용하며 재실행하지 않는다. After는 다음 조건을 함께 고정한다.

| 항목 | 고정값 |
| --- | --- |
| workload | 기존 `capacity-api-products.js`, target 250 RPS |
| warm-up / measurement | 30초 / 120초 |
| backend | Tomcat128, CPU2.0, memory1GiB, PID256, MaxRAMPercentage65, Hikari10 |
| cache | `pawcycle:catalog:product-list:v1`, local Redis, warm-up 중 hit counter 증가 필수 |

`infra/performance/phase10/run-products-redis-after.ps1`은 host local temp 외의 artifact 경로를 거부한다. `-ValidateOnly`와 `-ValidateRuntimeCapability`는 k6를 시작하지 않는다. `-RunAfterFirstResult`는 외부에서 별도 승인된 first-result에만 사용하며 workload 호출 전에 영구 marker를 생성한다. marker가 있으면 결과 성공 여부와 관계없이 `NEVER RERUN`으로 중단한다.

```powershell
pwsh -NoProfile -File infra/performance/phase10/run-products-redis-after.ps1 -ValidateOnly
pwsh -NoProfile -File infra/performance/phase10/run-products-redis-after.ps1 -ValidateRuntimeCapability
```

아래 명령은 이 저장소 준비 작업에서 실행하지 않는다.

```powershell
pwsh -NoProfile -File infra/performance/phase10/run-products-redis-after.ps1 -RunAfterFirstResult
```

After summary는 기존 actual RPS, dropped iterations, p50/p95/p99/max, backend CPU/memory/GC/threads, Hikari active/pending/acquire/usage와 SQL delta에 cache hit/miss/error delta 및 Redis container health/resource를 추가한다. Redis 효과는 처리량·tail·dropped 개선과 함께 miss 이후 measurement의 SQL/Hikari 감소가 일관될 때만 지지한다.

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

Kafka는 현재 구조에서 backlog가 허용 시간 안에 소진되지 않거나, 주문 생성과 후속 처리 속도 분리가 필요하거나, process 재시작을 넘는 durable 전달·독립 consumer 확장·명시적 재처리가 필요하다는 증거가 함께 확인될 때만 후속 후보로 검토한다. 해당 증거 없이 queue, topic, producer/consumer 또는 Kafka 운영 구성을 설계·구현하지 않는다.

## 실행 상태

PERF-PH10-001에서는 k6, JFR, Redis After와 Subscription Burst workload를 실행하지 않는다. CI와 repository validator 통과는 Production Verified를 의미하지 않는다.
