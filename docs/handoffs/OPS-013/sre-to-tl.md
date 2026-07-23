# OPS-013 Platform/SRE → 사용자·Tech Lead 인수인계

## 작업 정보

- 작업 ID: OPS-013
- 작업 등급: 고위험

## 전달 목적

병합된 OPS-013 기반으로 사용자가 비공개 S3 계약을 준비하고 production DB를 변경하지 않는 실제 backup·isolated restore 검증을 수행할 수 있도록 실행 경계와 남은 위험을 전달한다.

## 대상 역할 또는 운영자

- Product Owner이자 Tech Lead인 사용자
- AWS bucket·IAM과 EC2 Runbook을 직접 적용하는 운영자

## 입력 문서

- 현재 OPS-013 사용자 승인
- `docs/runbook/OPS-013-production-db-backup-restore.md`
- `docs/reports/OPS-013/sre-report.md`
- `docs/runbook/OPS-010-production-single-release.md`
- `docs/runbook/OPS-011-production-https.md`

## 완료된 작업

- production MySQL health·image·volume·disk·memory fail-close preflight
- 압축 consistent logical dump, schema·Flyway·핵심 table count manifest와 SHA-256
- S3 region·PAB·SSE-S3·14일 lifecycle, upload size·encryption·download checksum과 completion marker gate
- pinned MySQL, `none` network, 무 publish·고유 named volume isolated restore
- 실제 압축 해제 크기 기준 restore disk preflight
- success·failure cleanup과 production container·volume·state 보존 계약
- fake AWS·MySQL lifecycle test, 정적 validator와 Repository Validation 연결
- 사용자 실행 Runbook과 비민감 증거 형식

## 관련 파일

- `infra/production/db-backup-restore.sh`
- `infra/production/test-db-backup-restore.sh`
- `infra/production/validate-production-contracts.py`
- `.github/workflows/validate-conventions.yml`
- `docs/runbook/OPS-013-production-db-backup-restore.md`
- `docs/reports/OPS-013/sre-report.md`

## 소비자 입력

- 같은 서울 region에 새로 생성한 OPS-013 전용 빈 private S3 bucket
- 지정 backup prefix
- bucket 관리 권한과 최소 EC2 instance role 권한
- healthy production MySQL과 충분한 EC2·Docker disk·available memory

실제 bucket·region·prefix, account·role ARN, credential, dump·row·count와 application SHA 값은 저장소·PR·보고서에 입력하지 않는다.

## 적용 순서

1. 최신 main과 production release·HTTPS·MySQL·volume 기준을 확인한다.
2. OPS-013 전용 신규 빈 bucket을 생성한다.
3. bucket Public Access Block 4/4, SSE-S3와 지정 prefix 14일 lifecycle을 적용한다.
4. instance role에 bucket 계약 read와 지정 prefix `PutObject`·`GetObject`만 허용한다.
5. DDL·migration이 없는 저부하 시점에 `backup`을 실행한다.
6. completion marker까지 성공한 backup ID로 `restore-verify`를 실행한다.
7. schema·Flyway·핵심 table count 일치와 temporary resource cleanup을 확인한다.
8. production release·HTTPS state·MySQL container·volume이 바뀌지 않았음을 비민감 상태로 기록한다.

## 중단 조건

- production MySQL health·pinned image·고정 volume 또는 runtime Secret 경계 불일치
- DDL·migration·대량 쓰기 진행 중
- bucket이 OPS-013 전용 신규 빈 bucket인지 불명확함
- bucket region·PAB·SSE-S3·versioning 비활성·14일 lifecycle 또는 최소 role 권한 불명확
- instance role 대신 access key·Secret 입력 필요
- disk·available memory safety floor 미달
- compressed dump가 승인된 5,000,000,000 byte single-request upload 한도 초과
- restore container의 network·port·volume 격리 불명확
- production service 중지, DB 쓰기·restore 또는 volume 변경 필요
- 실제 identifier·credential·dump row를 로그나 증거에 출력해야 함

## 복구

script는 종료 trap으로 temporary container·volume·work file을 제거한다. 강제 종료 뒤에는 정확한 backup ID로 `cleanup`을 실행한다. cleanup은 OPS-013 label, `none` network와 production volume 미사용을 확인한 resource만 제거한다. lifecycle test도 자신이 만든 production 이름의 fixture volume만 삭제한다. production MySQL·volume·release·HTTPS state와 S3 object는 삭제하지 않는다.

부분 upload는 completion marker가 없으므로 restore 대상이 아니며 14일 lifecycle로 만료된다. upload·checksum·restore·schema·Flyway·count 불일치는 성공으로 기록하지 않는다.

## 소비자 검증 포인트

- bucket이 OPS-013 전용 신규 빈 bucket이며 EC2와 같은 승인 region인가?
- PAB 4/4·SSE-S3·versioning 비활성·14일 prefix lifecycle인가?
- instance role이 지정 bucket·prefix 밖에 접근하지 않고 `DeleteObject`·KMS 권한이 없는가?
- source가 healthy production MySQL 한 개와 고정 volume인가?
- object size·SSE-S3·checksum과 completion marker가 모두 확인됐는가?
- restore disk preflight가 실제 압축 해제 크기를 기준으로 통과했는가?
- restore container가 pinned image·`none` network·무 publish·전용 volume만 사용하는가?
- schema·Flyway history·핵심 table count가 backup-time manifest와 일치하는가?
- temporary resource가 성공·실패 뒤 모두 사라지고 production resource가 유지되는가?

## 미결정 사항 또는 승인 필요 항목

실제 bucket·IAM·lifecycle 적용, 운영 backup·isolated restore와 증거 확인은 사용자 작업이다. 자동 schedule·알림, 실제 production restore, 기존 shared bucket 재사용, cross-region·versioning·Glacier·KMS와 장기 보존은 별도 승인이 필요하다.

## 남은 위험

- 같은 EC2·EBS 장애는 backup source와 restore 검증 환경에 함께 영향을 줄 수 있다.
- compressed dump 5,000,000,000 byte 초과는 multipart 권한·cleanup 설계가 필요한 별도 승인 항목이다.
- restore 전 실제 압축 해제 크기 측정은 추가 CPU·I/O를 사용하지만 같은 호스트의 disk 고갈 위험을 줄인다.
- dump 중 DDL은 금지되며 동시 row 쓰기는 count mismatch를 일으켜 안전 실패할 수 있다.
- 실제 AWS 정책과 production 보존은 사용자 실행 전까지 미검증이다.
- 현재 OPS-012 rollback은 `previous-sha` 부재로 Deferred이며 OPS-013 성공으로 해소되지 않는다.

## QA 필요 여부

별도 QA 문서는 생략한다. Repository Validation을 독립 자동 검증으로 사용하고 사용자·Tech Lead가 실제 AWS·S3와 production 보존 gate를 검증한다.
