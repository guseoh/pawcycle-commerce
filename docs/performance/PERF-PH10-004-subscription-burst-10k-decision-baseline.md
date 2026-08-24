# PERF-PH10-004 Subscription Burst 10k Decision Baseline 계약

## 범위와 불변 경계

이 문서는 Issue #208의 repository harness 준비 계약을 기록한다. workload identity는 `phase10-subscription-burst-decision-10k-v1`, synthetic cohort는 `10,000`으로 고정한다. 기존 PERF-PH10-002 5,000건 first-result, PERF-PH9 workload와 Redis After workload는 모두 `NEVER RERUN`이며 `run-subscription-burst-before.ps1`의 permanent consumed gate를 변경하지 않는다. 제품 domain/API/DB schema와 Production scheduler 동작도 변경하지 않는다.

이 작업에서는 실제 PERF-PH10-004 workload와 Docker performance runtime을 실행하지 않는다. 향후 실제 first-result는 사용자의 별도 명시적 승인과 승인된 source SHA가 있어야 한다.

## 격리와 one-shot 상태

`compose.phase10-subscription-burst-decision-10k.yaml`은 project와 MySQL·Prometheus named volume을 PERF-PH10-004 전용 이름으로 분리한다. marker는 repository 밖 host-local `%TEMP%/pawcycle-phase10-subscription-burst-decision-10k-v1-marker/workload-started.json`을 사용한다. marker는 backend가 첫 raw drain service 호출 직전에 workload identity, 승인 source SHA, cohort `10,000`, workload 시작 여부와 시각을 결합해 `CREATE_NEW`로 기록한다. marker 이전 실패만 disposable runtime 정리 후 재시도할 수 있다. marker 이후에는 workload·collector·summary·correctness 결과와 관계없이 항상 `NEVER RERUN`이다.

evidence state는 다음과 같다.

- `NOT_STARTED`: 전용 marker와 durable candidate가 모두 없음
- `CONSUMED_SUMMARY_AVAILABLE`: 승인된 identity/cohort/source SHA 계약과 일치하는 durable candidate가 있음
- `CONSUMED_SUMMARY_MISSING`: marker 또는 candidate가 존재하지만 승인 계약과 일치하는 durable candidate가 없음

marker가 있고 candidate가 없거나, candidate의 identity/cohort/source SHA가 다르거나, 이미 소비된 상태에서 `ApprovedSourceSha`가 제공되지 않아 candidate를 권위 있게 검증할 수 없으면 fail-close하여 `CONSUMED_SUMMARY_MISSING`으로 판정한다. 이는 재실행 허용 상태가 아니다.

## ApprovedSourceSha 결합

실제 run은 40자리 repository commit인 `ApprovedSourceSha`를 명시적으로 받아야 한다. harness는 Docker 기동 전에 다음을 모두 검증한다.

- `ApprovedSourceSha`가 repository commit임
- `ApprovedSourceSha`와 local `HEAD`가 일치함
- worktree가 clean임
- cohort가 정확히 `10,000`임
- 전용 authoritative marker가 아직 없음

하나라도 다르면 workload 준비 전에 중단한다. promotion과 사후 state inspection은 실행 시 승인됐던 SHA를 다시 주입받아 candidate의 source SHA를 검증한다. 다른 commit의 schema-valid candidate는 가용 evidence로 인정하지 않는다.

## raw decision과 scheduler projection

15분 synthetic decision target은 `900초`, required raw throughput은 정확히 `10,000 / 900`, 즉 약 `11.11 orders/s`다. `rawDecisionTargetMet`은 다음 두 조건이 모두 참일 때만 `true`다.

- `rawDrainElapsedMs <= 900,000`
- `ordersPerSecond >= 10,000 / 900`

raw sequential drain은 artificial scheduler delay 없이 backend service를 순차 호출한 관측값이다. default scheduler projection은 batch size `100`, fixed delay `60,000ms`를 사용하는 별도 projection이며 raw decision 판정에 섞지 않는다. production scheduler 설정은 변경하지 않는다.

## evidence durability와 privacy

full summary와 driver stdout/stderr는 host-local `%TEMP%` intermediate artifact로 유지한다. workload가 marker 이후 종료되면 harness는 `docs/reports/PERF-PH10-004/evidence-candidates/`에 redacted candidate를 자동 생성한다. 파일명은 source SHA 일부와 workload 시작 시각을 포함하며 `CREATE_NEW`로 생성되어 기존 파일을 덮어쓰지 않는다. harness는 Git commit/push를 하지 않으며, 사용자가 candidate를 검토한 뒤 별도 commit/PR 여부를 결정한다.

자동 promotion이 실패했지만 실행 당시 full summary와 marker를 보존한 경우에만 아래 수동 복구 경로를 사용한다. 사용자는 실행 당시 source SHA가 marker·summary와 일치하는지 먼저 확인하고 그 SHA를 명시적으로 승인해야 한다. 이 명령도 workload, Git commit 또는 push를 실행하지 않는다.

```powershell
pwsh -NoProfile -File infra/performance/phase10/run-subscription-burst-decision-10k.ps1 -PromoteEvidence -ApprovedSourceSha <approved-40-character-sha> -EvidenceSourceSummaryPath <host-temp-summary> -EvidenceMarkerPath <host-temp-marker>
```

candidate는 whitelist projection으로 다음 aggregate만 보존한다.

- source SHA, workload identity, cohort, batch/fixed-delay와 15분 target 계약
- workload 시작/완료와 marker timestamp
- processed/created/failure/duplicate-no-op, raw elapsed/throughput/target verdict, batch aggregate
- default scheduler projection
- JVM/runtime/Hikari/MySQL aggregate와 측정 peak/delta
- correctness reconciliation 및 harness/collector state

원시 row, member/subscription/order identifier, recipient/address, billing/payment identifier, credential/secret, driver raw stdout/stderr는 포함하지 않는다. source summary에 privacy denylist field가 있거나 identity/cohort/source SHA/timestamp/aggregate/target 계약이 불일치하면 promotion을 거부한다. candidate validation에서도 같은 계약을 재검증하므로 잘못된 candidate는 `CONSUMED_SUMMARY_AVAILABLE` 근거가 될 수 없다.

## non-workload 검증과 향후 실행 경계

다음 명령은 Docker와 performance workload를 시작하지 않는다.

```powershell
pwsh -NoProfile -File infra/performance/phase10/run-subscription-burst-decision-10k.ps1 -ValidateOnly
pwsh -NoProfile -File infra/performance/phase10/run-subscription-burst-decision-10k.ps1 -InspectEvidenceState
pwsh -NoProfile -File infra/performance/k6/validate-harness.ps1 -SkipK6Inspect
```

향후 marker 또는 candidate가 생긴 뒤에는 실행 당시 승인 SHA를 제공해야 권위 있는 AVAILABLE 판정이 가능하다.

```powershell
pwsh -NoProfile -File infra/performance/phase10/run-subscription-burst-decision-10k.ps1 -InspectEvidenceState -ApprovedSourceSha <approved-40-character-sha>
```

아래 실제 first-result 명령은 이번 작업에서 실행하지 않는다. 사용자 별도 승인 후 clean reviewed HEAD에서 단 한 번만 사용할 수 있으며 marker 이후 재실행은 금지된다.

```powershell
pwsh -NoProfile -File infra/performance/phase10/run-subscription-burst-decision-10k.ps1 -ApprovedSourceSha <approved-40-character-sha> -RunDecisionFirstResult
```
