# PERF-PH10-006 Subscription Scheduler Tuning After 10k 계약

## 범위와 실행 경계

이 문서는 Issue #212의 저장소 준비 계약이다. 실제 `PERF-PH10-006` workload는 이 PR에서 실행하지 않는다. 기존 PERF-PH10-004 10k, PERF-PH10-002, PERF-PH9와 Redis After는 모두 `NEVER RERUN`이며, marker·summary·candidate 삭제는 재실행 근거가 아니다.

After workload identity는 `phase10-subscription-burst-scheduler-tuning-after-10k-v1`이다. cohort `10,000`, measurement-only batch size `500`, fixed delay `15,000ms`, synthetic decision target `900s`를 고정한다. 이는 Production scheduler 값이나 제품 동작을 변경하지 않는다.

## Measurement contract와 격리

`SubscriptionBurstMeasurementService`는 `subscription-burst-measurement` profile에서만 batch/fixed-delay 값을 받는다. setup summary, raw sequential drain, expected tick count와 scheduler projection은 동일 값을 사용하며 양수가 아니면 backend가 기동 시 fail-close한다.

`compose.phase10-subscription-burst-scheduler-tuning-after-10k.yaml`은 After 전용 Compose project와 MySQL·Prometheus volume을 사용한다. isolated runtime은 CPU `2.0`, memory `1GiB`, PID `256`이며, raw drain에는 artificial sleep을 넣지 않는다. scheduler projection은 raw drain과 configured fixed delay를 구분해 기록한다.

workload 이전에는 사용자가 승인한 40자리 `ApprovedSourceSha`가 clean local HEAD와 일치해야 한다. harness는 이를 marker identity/source SHA/cohort 및 batch/fixed-delay environment binding에 전달한 뒤에만 runtime을 시작한다.

## One-shot evidence

marker는 첫 automation service 호출 직전에 `CREATE_NEW`로 생성된다. marker payload는 workload identity, approved source SHA, cohort, invocation flag와 UTC start timestamp를 기록한다. marker 이후에는 success/failure, collector failure, summary 생성 여부와 관계없이 항상 `NEVER RERUN`이다.

evidence state는 다음과 같다.

- `NOT_STARTED`: After marker와 After durable candidate가 모두 없음
- `CONSUMED_SUMMARY_AVAILABLE`: authoritative identity/source SHA/cohort/batch/fixed-delay contract와 일치하는 candidate가 있음
- `CONSUMED_SUMMARY_MISSING`: marker 또는 candidate가 있지만 valid authoritative candidate가 없음

candidate는 `docs/reports/PERF-PH10-006/evidence-candidates/`에 redacted whitelist aggregate만 `CREATE_NEW`로 생성한다. marker의 identity/source SHA/cohort/timestamp 또는 source summary·candidate의 identity/source SHA/cohort/batch/fixed delay/timestamp가 authoritative contract와 다르면 fail-close한다. 자동 Git commit/push는 하지 않는다.

자동 promotion이 실패한 뒤 기존 source summary와 marker를 복구한 경우에도, 실행 당시 사용자가 source SHA를 확인·승인한 경우에만 다음처럼 promotion한다.

```powershell
pwsh -File infra/performance/phase10/run-subscription-burst-scheduler-tuning-after-10k.ps1 `
  -PromoteEvidence -ApprovedSourceSha <approved-40-character-sha> `
  -EvidenceSourceSummaryPath <host-temp-summary-path> -EvidenceMarkerPath <host-temp-marker-path>
```

이 명령은 workload 재실행이나 자동 commit/push를 수행하지 않는다.

## 결과 판정

durable evidence는 correctness, initial/final backlog, processed/created/failures/duplicate-no-op, raw elapsed/throughput, batch p50/p95/max, configured batch/fixed delay, scheduler projection, 900초 target, CPU/JVM, Hikari, MySQL row-lock과 automation metric reconciliation을 포함한다.

Prometheus measurement-end snapshot은 workload 종료 뒤 freshness를 확인한 하나의 evaluation timestamp를 모든 instant query에 사용한다. 이는 collector consistency를 위한 것으로 애플리케이션 metric 의미를 변경하지 않는다.

After 결과가 correctness를 유지하고 scheduler projection이 900초 이내이며 CPU/JVM/Hikari/MySQL saturation이 없으면 설정 튜닝을 우선한다. 그렇지 않을 때만 후속 task에서 bounded catch-up 등의 다른 설계를 검토한다. 이 문서는 그 구현이나 Production 적용을 승인하지 않는다.
