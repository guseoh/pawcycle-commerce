# OPS-013 Platform/SRE → 사용자·Tech Lead 인수인계

## 작업 정보

- 작업 ID: OPS-013
- 작업 등급: 고위험

## 전달 목적

병합된 OPS-013 기반으로 사용자가 준비하고 검증한 비공개 S3·IAM 계약과 실제 운영 backup·isolated restore 결과를 전달한다. 완료된 범위와 actual production restore·자동화 등 남은 승인 범위를 분리한다.

## 대상 역할 또는 운영자

- Product Owner이자 Tech Lead인 사용자
- AWS bucket·IAM과 EC2 Runbook을 직접 적용하는 운영자

## 입력 문서

- 현재 OPS-013 사용자 승인
- `docs/runbook/OPS-013-production-db-backup-restore.md`
- `docs/reports/OPS-013/sre-report.md`
- `docs/reports/OPS-013/production-verification-2026-07-24.md`
- `docs/runbook/OPS-010-production-single-release.md`
- `docs/runbook/OPS-011-production-https.md`

## 완료된 작업

- production MySQL health·image·volume·disk·memory fail-close preflight
- 압축 consistent logical dump를 격리 import해 같은 snapshot에서 생성한 schema·Flyway·핵심 table count manifest와 SHA-256
- bucket·region·prefix·expected owner의 `PAWCYCLE_BACKUP_*` 환경 변수 전용 전달과 S3 식별자 CLI flag 거부
- S3 서울 region·expected owner·PAB 4/4·SSE-S3 `AES256`·versioning 비활성·유일한 14일 lifecycle, IMDS·service endpoint override 차단, upload size·encryption·download checksum과 completion marker gate
- pinned MySQL, `none` network, 무 publish·고유 named volume isolated restore
- 실제 압축 해제 크기 기준 restore disk preflight
- success·failure cleanup과 production container·volume·state 보존 계약
- fake AWS·MySQL lifecycle test, 정적 validator와 Repository Validation 연결
- 사용자 실행 Runbook과 비민감 증거 형식

## 2026-07-24 실제 운영 검증 결과

- S3 bucket의 서울 region·expected owner·Public Access Block 4/4·SSE-S3 `AES256`·versioning 비활성·지정 prefix 14일 lifecycle: Verified
- EC2 instance role 최소 권한과 자동 삭제 권한 미추가: Verified
- production MySQL 논리 backup, S3 upload와 무결성 검증: Verified
- 성공한 동일 backup ID의 `restore-verify` 입력 사용: Verified
- backup ID의 저장소 밖 장기 별도 보관 여부: 미확인
- S3에서 다시 내려받은 object set의 isolated restore: Verified
- schema·Flyway history·핵심 table count의 dump snapshot manifest 일치: Verified
- production MySQL container와 `pawcycle-production-mysql-data` 보존: Verified
- proxy·frontend·backend·mysql health와 HTTPS·공개 상품 API smoke: Verified
- temporary restore container·volume·work directory 부재와 temporary source 제거: Verified
- 2 GiB temporary Swap 사용량 0 MiB 확인 후 제거: Verified
- 승인된 일회성 유지보수 예외로 proxy·frontend·backend를 일시 중지하고 짧은 서비스 중단 수용, MySQL·production volume 유지: Verified
- application 중지 후 `MemAvailable` 970 MiB, 서비스 복구 후 640 MiB 확인: Verified
- 유지보수 종료 후 새 Session Manager 세션의 읽기 재검증: Verified

실제 production DB에는 restore하지 않았다. 위 결과는 운영 backup과 별도 임시 DB의 복원 가능성 검증이며 actual production restore 완료나 무중단 실행을 의미하지 않는다. application 중지는 사용자가 승인한 역사적 일회성 예외이며 재실행의 기본 절차가 아니다. 실제 identifier·backup ID 값·credential·row·table count 값은 기록하지 않았다.

## 관련 파일

- `infra/production/db-backup-restore.sh`
- `infra/production/test-db-backup-restore.sh`
- `infra/production/validate-production-contracts.py`
- `.github/workflows/validate-conventions.yml`
- `docs/runbook/OPS-013-production-db-backup-restore.md`
- `docs/reports/OPS-013/sre-report.md`
- `docs/reports/OPS-013/production-verification-2026-07-24.md`

## 재실행 시 소비자 입력

- 승인 서울 region `ap-northeast-2`에 새로 생성한 OPS-013 전용 빈 private S3 bucket과 expected owner
- 지정 backup prefix
- bucket 관리 권한과 최소 EC2 instance role 권한
- healthy production MySQL과 충분한 EC2·Docker disk·available memory

실제 bucket·region·prefix, account·role ARN, credential, dump·row·count와 application SHA 값은 저장소·PR·보고서에 입력하지 않는다.

## 검증 근거

- 로컬 Git for Windows Bash syntax, production 계약 validator, OPS-013 고위험 산출물 validator와 `git diff --check` 통과
- 로컬 Windows Git Bash의 `flock` 부재와 Docker engine unavailable로 production script 회귀·isolated lifecycle은 GitHub Repository Validation에서 실행
- 최신 head의 Backend·Frontend 포함 전체 Repository Validation, review thread와 Draft/Ready 상태는 GitHub를 권위 원본으로 확인
- 2026-07-24 사용자·Tech Lead 실제 실행에서 S3·IAM 계약, 운영 backup, S3 재다운로드 object set의 isolated restore, 데이터 비교, production 보존과 cleanup PASS
- 비민감 운영 결과는 [`production-verification-2026-07-24.md`](../../reports/OPS-013/production-verification-2026-07-24.md), 구현과 검토 상태는 [PR #62](https://github.com/guseoh/pawcycle-commerce/pull/62), [PR #63](https://github.com/guseoh/pawcycle-commerce/pull/63)과 [PR #63 checks](https://github.com/guseoh/pawcycle-commerce/pull/63/checks)를 동적 권위 원본으로 확인

## 완료된 실제 적용·검증 순서

1. 최신 main과 production release·HTTPS·MySQL·volume 기준을 확인했다.
2. OPS-013 전용 private bucket의 서울 region·expected owner·PAB 4/4·SSE-S3 `AES256`·versioning 비활성·지정 prefix 14일 lifecycle을 확인했다.
3. EC2 instance role의 최소 권한 경계를 확인했다.
4. 운영 MySQL 논리 backup과 S3 upload·무결성 검증을 완료했다.
5. S3에서 object set을 다시 내려받아 isolated restore를 완료했다.
6. schema·Flyway·핵심 table count 일치를 실제 값 없이 확인했다.
7. temporary resource cleanup과 production MySQL container·volume 보존을 확인했다.
8. 서비스 health·HTTPS·공개 상품 API smoke와 새 Session Manager 세션의 읽기 재검증을 완료했다.

## 중단 조건

- production MySQL health·pinned image·고정 volume 또는 runtime Secret 경계 불일치
- DDL·migration·대량 쓰기 진행 중
- bucket이 OPS-013 전용 신규 빈 bucket인지 불명확함
- bucket이 승인 서울 region·expected owner와 일치하지 않거나 PAB·SSE-S3·versioning 비활성·14일 lifecycle 또는 최소 role 권한이 불명확
- instance role 대신 access key·Secret 입력 필요
- disk·available memory safety floor 미달
- compressed dump가 승인된 5,000,000,000 byte single-request upload 한도 초과
- restore container의 network·port·volume 격리 불명확
- production service 중지, DB 쓰기·restore 또는 volume 변경 필요
- 실제 identifier·credential·dump row를 로그나 증거에 출력해야 함

2026-07-24에는 사용자가 짧은 서비스 중단을 수용하고 proxy·frontend·backend 일시 중지를 별도로 승인했다. 이는 위 중단 조건을 바꾸지 않는 역사적 일회성 예외이며, 반복 실행에서는 application을 중지하지 않고 별도 승인을 다시 받아야 한다.

## 복구

script는 종료 trap으로 temporary container·volume·work file을 제거한다. 강제 종료 뒤에는 정확한 backup ID로 `cleanup`을 실행한다. cleanup은 OPS-013 label, `none` network와 production volume 미사용을 확인한 resource만 제거한다. lifecycle test도 자신이 만든 production 이름의 fixture volume만 삭제한다. production MySQL·volume·release·HTTPS state와 S3 object는 삭제하지 않는다.

부분 upload는 completion marker가 없으므로 restore 대상이 아니며 14일 lifecycle로 만료된다. upload·checksum·restore·schema·Flyway·count 불일치는 성공으로 기록하지 않는다.

2026-07-24 실행에서는 temporary restore container·volume·work directory와 temporary source가 남지 않았고 production MySQL container·volume이 유지됐다. 일시 중지한 proxy·frontend·backend를 복구한 뒤 네 서비스 health, HTTPS와 공개 상품 API를 재검증했다. S3 object는 14일 lifecycle 대상으로 두며 instance role에 `DeleteObject` 등 자동 삭제 권한을 추가하지 않았다.

## 소비자 검증 포인트

- bucket이 OPS-013 전용 신규 빈 bucket이며 EC2와 같은 승인 region인가?
- PAB 4/4·SSE-S3 `AES256`·versioning 비활성·14일 prefix lifecycle인가?
- instance role이 지정 bucket·prefix 밖에 접근하지 않고 `DeleteObject`·KMS 권한이 없는가?
- source가 healthy production MySQL 한 개와 고정 volume인가?
- object size·SSE-S3·checksum과 completion marker가 모두 확인됐는가?
- restore disk preflight가 실제 압축 해제 크기를 기준으로 통과했는가?
- restore container가 pinned image·`none` network·무 publish·전용 volume만 사용하는가?
- schema·Flyway history·핵심 table count가 dump snapshot에서 생성한 manifest와 일치하는가?
- temporary resource가 성공·실패 뒤 모두 사라지고 production resource가 유지되는가?

## 미완료 또는 승인 필요 항목

자동 schedule·실패 알림, 실제 production restore, 기존 shared bucket 재사용, cross-region, versioning 활성화·versioned backup 보존, Glacier·KMS, 별도 restore EC2, 장기 보존과 RPO/RTO 확정은 별도 승인이 필요하다. OPS-013 bucket의 `versioning 비활성` 검증은 완료 항목이다. 현재 검증 결과를 actual production restore 완료나 자동화 완료로 확장하지 않는다.

## 남은 위험

- 같은 EC2·EBS 장애는 backup source와 restore 검증 환경에 함께 영향을 줄 수 있다.
- compressed dump 5,000,000,000 byte 초과는 multipart 권한·cleanup 설계가 필요한 별도 승인 항목이다.
- restore 전 실제 압축 해제 크기 측정은 추가 CPU·I/O를 사용하지만 같은 호스트의 disk 고갈 위험을 줄인다.
- dump 중 DDL은 금지되며 backup-time snapshot manifest 생성을 위한 isolated import가 추가 자원을 사용한다.
- 실제 S3 bucket 계약과 instance role 최소 권한, production 보존은 2026-07-24 사용자 실행 범위에서 검증됐다. 별도 restore EC2·RPO/RTO와 actual production restore 복구 훈련은 미검증이다.
- 성공한 backup ID는 동일 실행의 `restore-verify`에 사용됐지만 저장소 밖 장기 별도 보관 여부는 미확인이다. 향후 동일 backup 재검증에는 접근이 제한된 운영 기록에 값을 보관해야 한다.
- application 중지는 짧은 서비스 중단을 수용한 승인된 일회성 예외이며 기본 Runbook 절차가 아니다.
- 현재 OPS-012 rollback은 `previous-sha` 부재로 Deferred이며 OPS-013 성공으로 해소되지 않는다.

## QA 필요 여부

별도 QA 문서는 생략한다. Repository Validation을 독립 자동 검증으로 사용했고 사용자·Tech Lead가 실제 AWS·S3, 운영 backup·isolated restore와 production 보존 gate를 검증했다.
