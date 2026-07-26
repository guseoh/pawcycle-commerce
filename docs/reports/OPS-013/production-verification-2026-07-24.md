# OPS-013 2026-07-24 비민감 운영 검증 증거

## 작업 정보

- 원 작업 ID: OPS-013
- 후속 수정 ID: OPS-013-REVIEW-001
- 작업 등급: 고위험
- 역할: Platform/SRE
- 증거 성격: 사용자가 제공한 실제 운영 결과의 식별자 제거 요약

## 입력 문서

- 2026-07-24 사용자 실행 결과와 후속 승인 사실
- `docs/runbook/OPS-013-production-db-backup-restore.md`
- `docs/reports/OPS-013/sre-report.md`
- `docs/handoffs/OPS-013/sre-to-tl.md`

## 명시적 승인 근거

사용자는 OPS-013 backup·isolated restore와 2026-07-24 일회성 유지보수 절차를 승인하고 직접 실행했다. 메모리 여유 확보를 위해 proxy·frontend·backend를 일시 중지하고 짧은 서비스 중단을 수용했지만 production MySQL과 `pawcycle-production-mysql-data`는 유지했다. 이 예외는 application을 중지하지 않는 Runbook 기본 정책을 변경하지 않는다.

## 변경 범위

사용자가 제공한 S3·IAM, 운영 backup, S3 재다운로드 object set의 isolated restore, 데이터 비교, production 보존, 서비스 smoke와 cleanup PASS marker를 비민감 요약으로 보존한다.

## 변경하지 않은 범위

raw console log는 저장소에 보존하지 않았다. 실제 bucket·prefix·account ID·ARN·role명·hostname·IP·backup ID 값·application SHA·credential·dump row·table count 값과 object key를 기록하지 않는다. actual production restore·자동화·알림을 완료 상태로 확장하지 않는다.

## 적용 전 검증

- 사용자가 일회성 application 중지와 짧은 서비스 중단을 별도 승인했다.
- production MySQL과 `pawcycle-production-mysql-data` 유지 경계를 확인했다.
- S3 계약과 EC2 instance role 최소 권한을 실제 실행 gate로 확인했다.

## 적용 후 검증

| 검증 범위 | 비민감 결과 |
| --- | --- |
| S3 bucket 계약 | 서울 region·expected owner·Public Access Block 4/4·SSE-S3 `AES256`·versioning 비활성·지정 prefix 14일 lifecycle PASS |
| IAM | EC2 instance role 최소 권한 PASS, 자동 삭제 권한 미추가 |
| 운영 backup | production MySQL 논리 backup PASS |
| S3 upload·무결성 | upload와 무결성 검증 PASS |
| backup ID 추적 | 성공한 동일 backup ID를 같은 실행의 `restore-verify` 입력으로 사용, 저장소 밖 장기 별도 보관 여부는 미확인 |
| isolated restore | S3에서 다시 내려받은 object set의 임시 DB 복원 PASS |
| 데이터 비교 | schema·Flyway history·핵심 table count 일치 PASS, 실제 값 미기록 |
| production 보존 | production MySQL container와 `pawcycle-production-mysql-data` 보존 PASS |
| 승인된 유지보수 예외 | proxy·frontend·backend 일시 중지, 짧은 서비스 중단 수용, MySQL·production volume 유지 |
| 자원 확인 | application 중지 후 `MemAvailable` 970 MiB, 복구 후 640 MiB |
| 서비스 smoke | proxy·frontend·backend·mysql healthy, HTTPS와 공개 상품 API PASS |
| cleanup | temporary restore container·volume·work directory 부재, temporary source 제거 |
| temporary Swap | 2 GiB 구성의 사용량 0 MiB 확인 후 제거 |
| 유지보수 종료 | 새 Session Manager 세션의 읽기 재검증 PASS |

실제 production DB에는 restore하지 않았다. 위 결과는 actual production restore 완료나 무중단 실행 증거가 아니다.

## 독립 검증

구현자가 아닌 사용자·Tech Lead가 실제 운영환경에서 위 항목을 실행하고 PASS marker를 제공했다. 저장소 구현과 동적 CI·review 상태는 다음 GitHub 링크를 권위 원본으로 사용한다.

- [OPS-013 구현 PR #62](https://github.com/guseoh/pawcycle-commerce/pull/62)
- [PR #62 checks](https://github.com/guseoh/pawcycle-commerce/pull/62/checks)
- [OPS-013 운영 증거 반영 PR #63](https://github.com/guseoh/pawcycle-commerce/pull/63)
- [PR #63 checks](https://github.com/guseoh/pawcycle-commerce/pull/63/checks)

run ID·head SHA·check ID는 이 파일에 고정하지 않는다. raw console log 대신 사용자가 제공한 비민감 PASS marker만 요약했다.

## 실행한 검증

- 사용자 실행 S3 계약·IAM 최소 권한 PASS marker 확인
- 운영 backup·S3 upload·무결성·isolated restore PASS marker 확인
- schema·Flyway·핵심 table count 비교와 production 보존 PASS marker 확인
- 서비스 smoke·temporary resource·source·Swap 제거와 새 Session Manager 세션 재검증 PASS marker 확인
- 보고서·인수인계·Runbook과의 완료·미완료 상태 상호 대조

## 실행하지 못한 검증과 이유

raw console log는 저장소에 보존되지 않아 명령별 원문을 독립 재생하지 못한다. actual production restore, 자동 schedule·실패 알림, cross-region, versioning 활성화·versioned backup 보존, 장기 보존, 별도 restore EC2와 RPO/RTO는 승인·실행 범위가 아니다. OPS-012 rollback은 `previous-sha` 부재로 Deferred다.

## 복구·롤백 증거

일시 중지한 proxy·frontend·backend를 복구한 뒤 proxy·frontend·backend·mysql health, HTTPS와 공개 상품 API smoke를 재검증했다. production MySQL container와 volume은 유지됐고 temporary restore container·volume·work directory, temporary source와 temporary Swap은 최종 상태에 남지 않았다. 새 Session Manager 세션의 읽기 재검증도 PASS했다.

## 남은 위험

- 성공한 backup ID는 동일 실행의 `restore-verify`에 사용됐지만 저장소 밖 장기 별도 보관 여부는 미확인이다.
- 향후 동일 backup 재검증에는 backup ID를 저장소 밖 접근이 제한된 운영 기록에 보관해야 한다.
- application 중지는 짧은 서비스 중단을 수용한 승인된 일회성 예외이며 반복 실행의 기본 절차가 아니다.
- actual production restore·자동화·장기 보존·RPO/RTO는 검증되지 않았다.

## QA 필요 여부

별도 QA 독립 실행은 생략한다. 사용자·Tech Lead의 실제 운영 검증과 Repository Validation을 독립 근거로 사용한다.

## QA 문서 경로 또는 생략 사유

제품·API·DB schema 변경이 없고 이 파일은 사용자 실행 결과의 비민감 증거 요약이므로 별도 QA 문서를 만들지 않는다.

## Git 결과

`ops/sre`의 OPS-013 후속 문서 commit으로 관리하며 동적 commit 상태는 GitHub를 권위 원본으로 확인한다.

## PR 결과

[PR #63](https://github.com/guseoh/pawcycle-commerce/pull/63)과 [checks 페이지](https://github.com/guseoh/pawcycle-commerce/pull/63/checks)를 동적 권위 원본으로 사용한다. 자동 병합하지 않는다.
