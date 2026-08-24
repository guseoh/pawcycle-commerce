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

두 endpoint는 측정 profile에서만 인증 없이 허용되고 CSRF가 비활성이다. Docker publish는 loopback으로 한정하며 application-level remote-address 제한은 Docker bridge/NAT 정상 호출을 막을 수 있어 사용하지 않는다. 대신 Compose 기본값은 `run-armed=false`이고 `-RunBeforeFirstResult`가 명시적 cohort와 함께 시작한 runtime에서만 arm한다. capability validation runtime은 endpoint를 직접 호출해도 disarmed 상태여야 한다.

drain은 production automation과 동등한 eligible candidate 조건으로 fixture 외 candidate가 0이고 fixture candidate가 기대 backlog와 같은지 marker 전에 fail-close 검증한다. 그 후 첫 service 호출 직전에 bind-mounted host local temp marker를 `CREATE_NEW`로 기록한다. marker 이전 실패는 disposable project/volume을 정리한 뒤 재시도할 수 있다. marker 이후에는 driver, collector, summary 또는 correctness 결과와 관계없이 `NEVER RERUN`이다. full summary가 privacy/serialization 검증에 실패하면 민감 payload를 버리고 whitelist 기반 failure artifact를 남긴다. marker와 full summary는 repository 밖 host local temp에만 두되, PERF-PH10-003 이후에는 그것을 장기 보존의 유일한 위치로 해석하지 않는다.

Raw drain은 실제 호출들의 elapsed time과 batch duration이다. Default scheduler projection은 측정값이 아니며 `raw drain elapsed + (projected ticks - 1) × 60,000ms`로 별도 기록한다. 기본 batch size는 100이고 projected ticks는 `ceil(initial backlog / 100)`이다.

## Evidence와 판정 입력

summary는 source SHA, cohort, backlog, processed/created/failure/duplicate-no-op, batch duration p50/p95/max, raw orders/sec와 scheduler projection을 기록한다. same-window runtime capability evidence와 workload 종료 뒤 fresh Prometheus scrape를 보존하고, automation counter delta를 driver aggregate와 대조한다. Backend actuator payload를 measurement-only sample마다 한 번 fetch해 Hikari active/pending 및 runtime peak를 보강한 뒤 200ms sleep을 적용한다. 따라서 200ms는 정확한 sample 주기가 아니라 sample collection time에 뒤따르는 sleep 간격이다. JVM CPU/memory/GC/thread, Hikari max/acquire/usage, Backend container CPU/memory/PID/health/restart/OOM과 MySQL relevant statement, connection, row-lock wait aggregate도 함께 보존한다. DB order cardinality, schedule당 중복 부재와 다음 future schedule 수를 service aggregate와 대조한다.

### Evidence durability와 상태

PERF-PH10-003은 workload 실행 여부와 durable evidence 존재 여부를 다음처럼 분리한다.

- `NOT_STARTED`: authoritative consumed 사실과 workload marker가 모두 없고 durable candidate도 없음
- `CONSUMED_SUMMARY_AVAILABLE`: workload가 소비됐고 durable redacted evidence candidate가 있음
- `CONSUMED_SUMMARY_MISSING`: workload가 소비됐지만 durable redacted evidence candidate가 없음

marker가 있는데 candidate가 없으면 `CONSUMED_SUMMARY_MISSING`이며 재실행 근거가 아니다. 이 first-result는 Issue #206의 권위 사실로 이미 소비된 상태를 harness에 고정하므로, 새 PC에서 host-local marker가 보이지 않아도 `-RunBeforeFirstResult`는 항상 fail-close한다. 기존 `RunBeforeFirstResult`의 marker-before-workload 경계는 변경하지 않는다.

full summary와 marker를 확보한 경우에만 다음 non-workload 경로로 `docs/reports/PERF-PH10-003/evidence-candidates/` 아래 redacted candidate를 생성할 수 있다. 이 명령은 workload, commit, push를 실행하지 않으며 기존 파일을 덮어쓰지 않는다. schema-valid 여부만으로는 충분하지 않다. historical first-result의 workload identity `phase10-subscription-burst-before-local`, cohort `5,000`, source SHA `3f11a5cc6489d3096d024290008a1b91fabe634c`와 source summary·candidate가 모두 일치해야 한다. 그 다음 source SHA가 저장소 commit인지, batch/fixed-delay 계약, marker와 summary의 시작 시각 및 완료 시각, 필요한 driver/runtime/Hikari/MySQL aggregate를 검증한 뒤 whitelist projection만 기록한다. 원시 row·식별자·주소·결제·credential·driver stdout/stderr 또는 privacy denylist field가 원본에 있으면 promotion을 거부한다.

```powershell
pwsh -NoProfile -File infra/performance/phase10/run-subscription-burst-before.ps1 -InspectEvidenceState
pwsh -NoProfile -File infra/performance/phase10/run-subscription-burst-before.ps1 -PromoteEvidence -EvidenceSourceSummaryPath <host-temp-summary> -EvidenceMarkerPath <host-temp-marker>
```

candidate는 source SHA와 workload 시작 시각을 파일명에 포함하고 `CREATE_NEW` 의미로 생성한다. 사용자가 내용을 검토한 뒤 별도 commit/PR로 보존하며 measurement runtime은 Git commit/push를 수행하지 않는다.

### 2026-08-24 first-result 보존 상태

- synthetic cohort `5,000` first-result는 2026-08-24에 1회 실행 완료됐다.
- isolated runtime이 정상 기동했고 summary artifact 생성 완료 메시지를 확인했다.
- 작업 PC 변경 후 기존 PC의 `%TEMP%` 상세 summary에는 현재 접근할 수 없다.
- detailed performance values는 unavailable이며 추정·생성하지 않는다.
- 이 workload는 `NEVER RERUN`이다. PERF-PH9 workload와 Redis After workload도 재실행하지 않는다.
- 원본 PC·디스크·백업에서 summary와 marker를 찾으면 새 측정이 아니라 기존 evidence recovery로만 취급한다.

Before 이후에는 cadence/batch 설정, 순차 처리량, connection/transaction/lock pressure와 downstream 속도 결합을 먼저 구분한다. 그 뒤 no-change 또는 scheduler/batch 조정, bounded worker/async, durable queue, Kafka·동등 messaging, Outbox/idempotent consumer/retry/DLQ를 해결 능력과 운영 요구에 따라 비교한다.

## 저장소 검증과 향후 실행

아래 명령은 performance workload를 시작하지 않는다.

```powershell
pwsh -NoProfile -File infra/performance/phase10/run-subscription-burst-before.ps1 -ValidateOnly
pwsh -NoProfile -File infra/performance/phase10/run-subscription-burst-before.ps1 -ValidateRuntimeCapability
```

아래 역사적 first-result 명령은 이미 소비되어 현재 모든 호스트에서 fail-close하며 실행하지 않는다.

```powershell
pwsh -NoProfile -File infra/performance/phase10/run-subscription-burst-before.ps1 -CohortSize <approved> -RunBeforeFirstResult
```

결과 검토 후 disposable containers와 전용 volumes만 제거한다. first-result marker와 artifact는 보존한다.

```powershell
pwsh -NoProfile -File infra/performance/phase10/run-subscription-burst-before.ps1 -CleanupIsolatedRuntime
```
