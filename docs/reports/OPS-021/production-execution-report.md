# OPS-021 Production 실행 보고서

## 작업 정보

- 작업 ID: OPS-021
- 작업 등급: 고위험
- 역할: Platform/SRE
- 실행일: 2026-07-30 KST
- 기록 근거: 사용자/Product Owner·Tech Lead가 제공한 비민감 실행 결과

## 작업 목적

OPS-021에서 준비한 Production Control 채택, 실제 Application rollback과 원래 Release 재배포 결과를 영구 기록한다. 이 문서는 사용자 실행 결과를 저장소 계약에 대조한 기록이며 Codex가 Production 명령을 실행하거나 원시 로그를 열람했다는 증거가 아니다.

## 승인 입력

사용자가 다음 Production 실행 결과의 기록을 승인했다.

- Control SHA: `1b78b93a6a0f9c593e3c792698eb18ddf5990810`
- 시작·최종 Application SHA: `2e9222b568a3469e8ccc5edce1b5301218c6888e`
- 실제 rollback SHA: `b9cf3cf51c5ffd4b85c6eafc78706ed079e299d6`
- `rollback.sh` 성공 뒤 이전 Release health·내부 Smoke·외부 HTTPS·MySQL volume 보존
- `deploy.sh` 성공 marker: `Release activated: 2e9222b568a3469e8ccc5edce1b5301218c6888e`
- 최종 네 Container healthy, 내부 Smoke·외부 HTTPS·state SHA 정상, OPS-020 임시 Container 부재와 Production MySQL volume 보존

정확한 실행 시각, 원시 로그와 민감 운영 식별자는 제공되지 않았으므로 추측하거나 기록하지 않는다.

## 명시적 승인 근거

현재 OPS-025 요청에서 사용자가 2026-07-30 KST의 OPS-021 실제 Production Control·Application rollback·재배포 결과를 승인된 입력으로 제공하고 비민감 증거의 영구 기록을 요청했다.

## 변경 범위

사용자가 확인한 적용 전후, 실제 rollback·재배포, 독립 대조, DB·Secret 경계와 잔여 위험을 이 보고서에 기록한다. OPS-021 저장소 준비 보고서와 OPS-010 현재 Runbook에는 최소 링크만 추가한다.

## 변경하지 않은 범위

이 기록 작업에서 Production, AWS, Docker, DB 또는 Secret 명령을 실행하지 않는다. DB restore·schema downgrade·Flyway 수정·volume 삭제, OPS-VERIFY-001 최종 판정, Blue/Green과 PERF-OPS-001 판단을 수행하지 않는다.

## 주요 결과

사용자 실행 결과에 따르면 OPS-021 Control 계약 아래 이전 Application Release로 실제 rollback한 뒤 원래 Application Release를 다시 배포했다. rollback과 재배포 각각에서 Release health, 내부 Smoke, 외부 HTTPS와 Production MySQL volume 보존이 확인됐다.

최종 Application SHA는 시작 SHA와 같은 `2e9222b568a3469e8ccc5edce1b5301218c6888e`이며 deploy 성공 marker와 state SHA가 일치했다. 최종 네 Container는 healthy이고 OPS-020 임시 Container는 남지 않았다.

## 저장소 계약과 사용자 확인의 구분

| 구분 | 근거 | 판정 범위 |
| --- | --- | --- |
| 저장소 계약 | OPS-021의 Control/Application 상태 분리, clean Control·contract gate, deploy·rollback health·Smoke·HTTPS, volume 삭제 금지 | 명령이 실패를 닫고 이전 Release 복구를 시도하는 방식 |
| 사용자 확인 | 제공된 Control·Application SHA, rollback·재배포 성공, 최종 health·Smoke·HTTPS·state·volume·임시 Container 상태 | 2026-07-30 KST 실제 Production 실행 결과 |
| Codex 확인 | 제공된 비민감 결과를 Runbook과 Script 계약에 대조 | Production 재실행·원시 로그·Secret·DB row 독립 열람 아님 |

## 적용 전 검증

사용자 제공 결과에서 시작 Application SHA와 최종 복귀 대상이 같고, 실제 rollback 대상 SHA가 별도로 식별됐다. Control SHA도 Application SHA와 분리되어 제공됐다. 이는 OPS-021의 Control/Application 상태 분리 계약과 일치한다.

정확한 적용 전 원시 출력, 시각, image digest와 state 파일 mode는 제공되지 않았으므로 이 보고서에서 PASS로 추정하지 않는다.

## 적용 후 검증

사용자가 다음을 확인했다.

- `rollback.sh` 성공 후 rollback 대상 Release health 정상
- rollback 대상 Release 내부 Smoke와 외부 HTTPS 정상
- rollback 전후 Production MySQL volume 보존
- `deploy.sh` 성공 marker와 최종 Application SHA 일치
- 최종 MySQL·Backend·Frontend·Proxy 네 Container healthy
- 최종 내부 Smoke와 외부 HTTPS 정상
- 최종 state SHA 정상
- OPS-020 임시 Container 부재

DB schema·Flyway·row 의미 검증과 Actual Production DB restore는 수행 결과로 제공되지 않았다.

## 독립 검증

실제 명령 실행자이자 Product Owner·Tech Lead인 사용자가 Production의 rollback 중간 상태와 재배포 최종 상태를 확인했다. Codex는 독립적으로 Production에 접속하지 않고, 제공된 결과가 OPS-010·OPS-021의 성공 marker, health·Smoke·HTTPS, state와 volume 보존 계약에 모순되지 않는지만 대조했다.

## 복구·롤백 증거

Application 복구 경로는 실제로 두 방향이 확인됐다.

1. 원래 Release에서 이전 Release로 `rollback.sh` 성공
2. 이전 Release에서 원래 Release로 `deploy.sh` 재배포 성공

두 단계 뒤 health·Smoke·HTTPS와 MySQL volume 보존이 확인됐고, 최종 Application SHA는 시작 SHA로 복귀했다. DB restore, schema downgrade, Flyway history 수정과 volume 삭제는 복구 수단으로 사용하지 않았다.

## DB·Secret 경계

- Production MySQL named volume 보존만 비민감 결과로 기록한다.
- DB schema, Flyway history, row count·row 값과 query 출력은 확인된 사실로 주장하지 않는다.
- Secret, credential, runtime env, domain, hostname, IP, account·bucket·backup 식별자와 원시 로그를 기록하지 않는다.
- Actual Production DB 논리 restore는 OPS-025 Runbook 준비와 별개의 미실행 고위험 작업이다.

## 실행한 검증

이 문서 작업에서는 제공된 SHA 형식과 OPS-010·OPS-021 저장소 계약의 성공·복구 경계를 대조한다. Markdown UTF-8, 고위험 task artifact validator와 `git diff --check` 결과는 OPS-025 보고서와 PR을 권위 원본으로 기록한다.

## 실행하지 못한 검증과 이유

- Production 명령 재실행: 현재 작업의 제외 범위
- 원시 로그·Secret·DB row 검증: 비민감 증거 경계와 권한 범위 밖
- 실제 중단 시간: 사용자 확인 값 없음
- Actual Production DB restore: 별도 고위험 사용자 실행으로 보류

## QA 필요 여부

별도 제품 QA 문서는 생략한다. 사용자의 실제 Production 확인을 독립 검증으로 사용하고, 이 문서는 저장소 계약과 비민감 결과의 정합성만 기록한다.

## QA 문서 경로 또는 생략 사유

제품 기능·API·DB를 변경하지 않는 역사적 실행 증거 기록이다. OPS-025의 새 restore 계약은 별도 fake·격리 lifecycle, CI와 사용자/Tech Lead 검토를 요구한다.

## 남은 위험

- Actual Production DB restore·복귀 훈련은 미실행이다.
- DB schema·Flyway·핵심 데이터 의미와 실제 중단 시간은 이 실행 증거로 검증되지 않았다.
- 단일 volume·Instance의 물리 장애, EBS 복구, RPO/RTO와 cross-region은 범위 밖이다.
- OPS-VERIFY-001 최종 판정과 PERF-OPS-001 판단은 후속 사용자 결정이다.

## Git 결과

이 보고서는 OPS-025 `ops/sre` 작업에서 추가한다. 최종 commit·push 결과는 OPS-025 보고서와 Git을 권위 원본으로 확인한다.

## PR 결과

OPS-025 `main` 대상 PR에 포함하고 자동 병합하지 않는다. 최종 병합은 사용자/Tech Lead가 결정한다.
