# PERF-PH10-002 Subscription Burst Before 측정 계약

## 범위

이 문서는 Issue #204의 반복 설명이 아니라 future-scale first-result의 저장소 실행 경계를 고정한다. 현재 제품 scheduler와 transaction 알고리즘은 변경하지 않는다. 실제 Before 실행과 cohort 선택은 별도 사용자 승인 대상이며 이 repository preparation에서는 실행하지 않는다.

## 격리와 입력

`compose.phase10-subscription-burst.yaml`은 project를 `pawcycle-phase10-subscription-burst`로 고정하고 MySQL·Prometheus named volume을 기존 local integration과 분리한다. Backend port와 Prometheus port는 loopback ephemeral publish를 사용한다. 기존 `pawcycle-local-integration-mysql-data`를 mount하거나 삭제하지 않으며 cleanup에도 `--remove-orphans`를 사용하지 않는다.

허용 synthetic cohort는 100, 500, 1,000, 2,500, 5,000, 10,000이다. 이 값은 실제 PawCycle 사용자 규모를 뜻하지 않는다. fixture는 cohort별 독립 member, address, active billing method, pet, `ACTIVE + mvp2_managed` subscription, snapshot/item, shipping snapshot과 due `SCHEDULED` schedule을 전용 DB에 만든다. artifact에는 그 식별값이나 주소·결제·credential·원시 row를 기록하지 않는다.

## 측정 경계

local-only `subscription-burst-measurement` profile이 다음 두 endpoint를 전용 loopback runtime에만 제공한다.

- setup: isolated fixture를 만들고 initial backlog를 검증한다.
- drain: backend JVM 안에서 `processDueSchedules(100)`을 artificial sleep 없이 반복한다.

drain은 첫 service 호출 직전에 bind-mounted host local temp marker를 `CREATE_NEW`로 기록한다. marker 이전 실패는 disposable project/volume을 정리한 뒤 재시도할 수 있다. marker 이후에는 driver, collector, summary 또는 correctness 결과와 관계없이 `NEVER RERUN`이다. marker와 ResultsDir은 repository 밖 host local temp에만 둔다.

Raw drain은 실제 호출들의 elapsed time과 batch duration이다. Default scheduler projection은 측정값이 아니며 `raw drain elapsed + (projected ticks - 1) × 60,000ms`로 별도 기록한다. 기본 batch size는 100이고 projected ticks는 `ceil(initial backlog / 100)`이다.

## Evidence와 판정 입력

summary는 source SHA, cohort, backlog, processed/created/failure/duplicate-no-op, batch duration p50/p95/max, raw orders/sec와 scheduler projection을 기록한다. 같은 window의 automation counter, JVM CPU/memory/GC/thread, Hikari active/pending/max/acquire/usage, Backend container CPU/memory/PID/health/restart/OOM과 MySQL relevant statement, connection, row-lock wait aggregate를 함께 보존한다. DB order cardinality, schedule당 중복 부재와 다음 future schedule 수를 service aggregate와 대조한다.

Before 이후에는 cadence/batch 설정, 순차 처리량, connection/transaction/lock pressure와 downstream 속도 결합을 먼저 구분한다. 그 뒤 no-change 또는 scheduler/batch 조정, bounded worker/async, durable queue, Kafka·동등 messaging, Outbox/idempotent consumer/retry/DLQ를 해결 능력과 운영 요구에 따라 비교한다.

## 저장소 검증과 향후 실행

아래 명령은 performance workload를 시작하지 않는다.

```powershell
pwsh -NoProfile -File infra/performance/phase10/run-subscription-burst-before.ps1 -ValidateOnly
pwsh -NoProfile -File infra/performance/phase10/run-subscription-burst-before.ps1 -ValidateRuntimeCapability
```

아래 first-result 명령은 이번 작업에서 실행하지 않는다.

```powershell
pwsh -NoProfile -File infra/performance/phase10/run-subscription-burst-before.ps1 -CohortSize <approved> -RunBeforeFirstResult
```

결과 검토 후 disposable containers와 전용 volumes만 제거한다. first-result marker와 artifact는 보존한다.

```powershell
pwsh -NoProfile -File infra/performance/phase10/run-subscription-burst-before.ps1 -CleanupIsolatedRuntime
```
