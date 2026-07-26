# OPS-013 Platform/SRE 작업 보고서

## 작업 정보

- 작업 ID: OPS-013
- 작업 등급: 고위험
- 역할: Platform/SRE

## 작업 목적

운영 MySQL의 압축 논리 dump와 검증 metadata를 비공개 SSE-S3 bucket에 저장하고, production DB·volume·network를 변경하지 않는 임시 MySQL에서 실제 복원 가능성을 검증하는 저장소 기반을 제공한다.

## 입력 문서

- 현재 OPS-013 사용자 승인
- 루트·`infra/AGENTS.md`
- Platform/SRE 역할 문서와 Skill
- `docs/runbook/OPS-010-production-single-release.md`
- `docs/runbook/OPS-011-production-https.md`
- `docs/runbook/lean-harness.md`
- `docs/reports/OPS-013/production-verification-2026-07-24.md`

## 명시적 승인 근거 (고위험 필수)

사용자가 서울 region의 비공개 S3 Standard, SSE-S3, 지정 prefix 14일 만료, 압축 MySQL 논리 dump·checksum, 기존 EC2·EBS의 무 network·무 publish 임시 MySQL 복원 검증과 사용자 실행 Runbook을 승인했다. 구현 PR에서는 실제 S3·IAM·lifecycle 생성과 운영 backup·restore를 병합 후 사용자 작업으로 분리했다. 이 보고서는 구현 당시 미실행 상태와 2026-07-24 post-merge 사용자 실행 결과를 별도 근거로 구분한다.

2026-07-24 사용자는 메모리 여유 확보를 위해 `proxy`, `frontend`, `backend`를 일시 중지하는 유지보수 예외를 별도로 승인하고 직접 실행했다. 이는 application을 중지하지 않는 Runbook 기본 정책의 반복 가능한 절차가 아니며, 짧은 서비스 중단을 수용한 일회성 예외다. production MySQL과 `pawcycle-production-mysql-data`는 계속 유지했다.

## 변경 범위

- production MySQL health·image·volume·disk·memory preflight
- 일관된 압축 논리 dump를 격리 import해 같은 snapshot에서 생성한 schema·Flyway·핵심 table count manifest와 SHA-256
- S3 upload 전후 size·SSE-S3·download checksum과 completion marker 검증
- production과 분리된 pinned MySQL·임시 named volume 복원 lifecycle
- 성공·실패 cleanup, fake AWS·실제 MySQL lifecycle test와 정적 계약 validator
- Repository Validation, Runbook과 사용자·Tech Lead 인수인계

## 변경하지 않은 범위

구현 PR은 application/API/DB schema·migration, production DB 쓰기·중지·restore, `pawcycle-production-mysql-data`, production network·port·Security Group, release·HTTPS state와 Compose를 변경하지 않았다. 실제 AWS·S3·IAM과 backup·isolated restore 실행은 저장소 구현 범위에서 제외하고 병합 후 사용자 검증으로 분리했다. `versioning 비활성`은 OPS-013 bucket 계약과 실제 검증 범위에 포함한다. versioning 활성화와 versioned backup 보존 전략, 자동 schedule·알림, Glacier·KMS·cross-region 기능 도입은 구현과 post-merge 검증 범위에서 제외했다.

## 주요 결과

- source credential은 host에서 읽거나 command argument로 전달하지 않고 기존 MySQL container 내부 환경에서만 사용한다.
- bucket·region·prefix·expected owner는 `PAWCYCLE_BACKUP_*` 환경 변수로만 받고 S3 식별자 CLI flag는 거부한다.
- ambient container credential, IMDS endpoint와 AWS service endpoint override를 거부하고 EC2 instance role·일반 AWS endpoint 경계를 강제한다.
- dump·manifest·checksum을 mode `600` root 전용 임시 경로에서 생성하고 S3 object set을 검증한다.
- production MySQL identity·health를 마지막으로 재확인한 뒤 completion marker를 업로드한다.
- restore 전 모든 S3 object의 size·SSE-S3와 work disk 여유를 확인해 대형 object의 선다운로드를 차단한다.
- checksum object는 hash와 기대 local basename 한 항목만 허용하며 외부·절대 경로를 읽지 않는다.
- 승인 서울 region과 expected bucket owner, Public Access Block 네 항목, SSE-S3 `AES256`, versioning 비활성과 전용 bucket의 유일한 지정 prefix 14일 lifecycle을 upload 전에 검사한다.
- dump를 임시 MySQL에 먼저 import해 manifest를 생성하므로 dump 이후 production row 쓰기가 backup snapshot 정합성을 깨뜨리지 않는다.
- dump 이외 metadata object는 upload 전부터 1 MiB로 제한한다.
- Runbook은 bucket 생성 실패를 즉시 중단하고 lifecycle·bucket policy 전체 교체 위험을 피하도록 OPS-013 전용 신규 빈 bucket만 허용한다.
- 단일 PUT은 `5,000,000,000` byte에서 fail-close하고 restore disk는 실제 압축 해제 크기를 측정해 산정한다.
- restore MySQL은 production과 같은 pinned image, `none` network, host port 없음, 고유 temporary volume·credential file과 resource limit을 사용한다.
- lifecycle test cleanup은 자신이 생성한 production 이름의 fixture volume만 제거한다.
- 실패와 성공 모두 temporary container·volume·work file을 정리하고 production container·volume·state와 S3 object를 삭제하지 않는다.

## API·DB·보안·성능 영향

API와 DB schema 변경은 없다. production DB에는 read-only 논리 dump와 metadata query만 실행한다. `--single-transaction --quick --skip-lock-tables`로 service 중지와 table lock을 피하지만 dump 중 DDL은 금지한다. backup·restore는 disk와 available memory safety floor를 통과해야 하며 isolated MySQL에 CPU·memory·PID 제한을 적용한다. 실제 압축 해제 크기 측정은 추가 CPU·I/O를 사용하지만 같은 host의 Docker disk 고갈 가능성을 줄인다.

## 적용 전 검증 (고위험 필수)

선행 운영 PR 병합 여부와 최신 `origin/main`, 깨끗한 새 `ops/sre`, 미병합 역할 PR 부재는 GitHub를 권위 원본으로 확인했다. production MySQL의 immutable image, runtime Secret의 mode `600` 경계, 고정 volume, 내부 data network와 application rollback의 volume 삭제 금지 계약을 확인했다. OPS-012 산출물은 main에 없으며 사용자 입력대로 `previous-sha` 부재 Deferred 상태를 유지한다.

## 적용 후 검증 (고위험 필수)

구현 당시 Shell syntax와 정적 계약 validator로 dump option, S3 PAB·SSE-S3·versioning 비활성·14일 lifecycle, completion marker 순서, credential 비노출, restore `none` network·무 publish·별도 volume, cleanup ownership과 production volume 삭제 금지를 확인했다. 실제 Docker engine이 없는 로컬 환경에서는 isolated MySQL lifecycle을 시작하지 못했으며 GitHub Repository Validation에서 실행하도록 연결했다.

최초 Repository Validation은 source fixture의 credential 전달과 source·restore readiness 경계에서 순차 실패했다. restore MySQL 초기화 중 `mysqladmin ping` 조기 성공 가능성을 readiness race로 추정해 대상 DB의 `127.0.0.1` TCP 인증 쿼리 연속 2회 성공으로 변경하고 gzip·SQL import 종료 상태를 분리했다. 이후 리뷰에서 test-owned volume cleanup, S3 lifecycle·policy 교체 경계, 환경변수 전달, decimal 5 GB, 실제 압축 해제 크기, download 전 object preflight, completion marker 순서, ambient AWS 설정과 checksum target 검증을 추가 보완했다. 마지막으로 dump와 live manifest의 snapshot 불일치, 서울 region·expected bucket owner, metadata 1 MiB upload 한도와 HTTPS 승인 순서 회귀 검증을 보완했다. 후속 안정화에서는 parser와 lifecycle test에 남은 S3 식별자 CLI flag를 제거하고 환경 변수 전용 계약을 정적 validator로 고정했다.

## 병합 후 실제 운영 검증 (2026-07-24)

사용자·Tech Lead가 실제 운영환경에서 수행한 비민감 결과는 다음과 같다. 세부 PASS marker와 추적성 제한은 [`production-verification-2026-07-24.md`](production-verification-2026-07-24.md)에 기록했다. bucket·prefix·account ID·ARN·role명·hostname·IP·backup ID 값·application SHA·credential·실제 row·table count는 기록하지 않았다.

| 검증 | 상태 | 비민감 근거 |
| --- | --- | --- |
| S3 bucket 계약 | Verified | 서울 region·expected owner·Public Access Block 4/4·SSE-S3 `AES256`·versioning 비활성·지정 prefix 14일 lifecycle PASS |
| EC2 instance role | Verified | 승인 bucket·prefix의 최소 권한 경계 PASS, 자동 삭제 권한은 추가하지 않음 |
| 운영 논리 backup | Verified | production MySQL 논리 dump, S3 upload와 무결성 검증 PASS |
| backup ID 추적 | 제한 있음 | 성공한 동일 backup ID를 같은 실행의 `restore-verify` 입력으로 사용, 저장소 밖 장기 별도 보관 여부는 미확인 |
| isolated restore | Verified | S3에서 다시 내려받은 동일 object set을 임시 MySQL에 복원 PASS |
| 데이터 검증 | Verified | schema·Flyway history·핵심 table count가 dump snapshot manifest와 일치 |
| production 보존 | Verified | production MySQL container와 `pawcycle-production-mysql-data` 보존 PASS |
| 서비스·공개 경로 | Verified | proxy·frontend·backend·mysql healthy, HTTPS와 공개 상품 API smoke PASS |
| 임시 자원 cleanup | Verified | temporary restore container·volume·work directory 부재, temporary source 제거 |
| 승인된 유지보수 예외 | Verified | proxy·frontend·backend 일시 중지로 짧은 서비스 중단 수용, MySQL·production volume 유지, `MemAvailable` 970 MiB 확인 후 서비스 복구 |
| 유지보수 자원 | Verified | 복구 후 `MemAvailable` 640 MiB, 2 GiB temporary Swap 사용량 0 MiB 확인 후 제거 |
| 유지보수 종료 재검증 | Verified | 새 Session Manager 세션에서 읽기 재검증 PASS |

이 검증은 production DB를 대상으로 backup을 수행하고 별도 임시 DB로 복원 가능성을 확인한 것이다. 실제 production DB에는 restore하지 않았으며 production restore 완료나 무중단 실행 증거로 해석하지 않는다. 사용한 backup ID 값은 저장소에 기록하지 않는다. 향후 동일 backup 재검증에는 해당 값을 저장소 밖 접근이 제한된 운영 기록에 보관해야 한다.

## 독립 검증 (고위험 필수)

구현 script와 분리된 `validate-production-contracts.py`가 OPS-013 보안·격리·보존 계약을 검사한다. Repository Validation의 Ubuntu Docker 환경에서 fake AWS 경계와 pinned source·restore MySQL lifecycle, 기존 production shell·Nginx·Compose·Backend·Frontend 회귀를 실행한다. 최신 head의 동적 run·check 상태는 GitHub를 권위 원본으로 확인한다.

병합 후에는 구현자가 아닌 사용자·Tech Lead가 실제 AWS 계약, 운영 backup, S3 재다운로드 object set의 isolated restore, 데이터 비교, production 보존과 cleanup을 독립 실행해 모두 PASS로 확인했다. 민감 식별자와 실제 데이터 값은 증거에서 제외했다. 비민감 운영 결과는 [`production-verification-2026-07-24.md`](production-verification-2026-07-24.md), 구현과 현재 검토 상태는 [PR #62](https://github.com/guseoh/pawcycle-commerce/pull/62), [PR #63](https://github.com/guseoh/pawcycle-commerce/pull/63)과 [PR #63 checks](https://github.com/guseoh/pawcycle-commerce/pull/63/checks)를 동적 권위 원본으로 사용한다.

## 실행한 검증

| 검증 | 결과 |
| --- | --- |
| `bash -n` OPS-013 script·test | Git for Windows Bash에서 통과 |
| `python infra/production/validate-production-contracts.py` | 로컬 통과 |
| `bash infra/production/test-production-scripts.sh` | 로컬 Git Bash의 `flock` 부재로 중단, Repository Validation에서 실행 |
| 로컬 `test-db-backup-restore.sh` | Docker engine unavailable로 미실행, Repository Validation에서 실행 |
| OPS-013 고위험 task artifact validator | 통과 |
| commit message validator | 통과 |
| `git diff --check` | 통과 |
| 선행 Repository Validation | source credential·readiness·restore import 단계에서 순차 실패 후 수정 |
| 최신 Repository Validation | 격리 lifecycle과 Backend·Frontend 포함 전체 결과를 GitHub 권위 원본으로 확인 |
| 2026-07-24 실제 S3·IAM 계약 검증 | 사용자 실행 PASS |
| 2026-07-24 운영 backup·S3 upload·무결성 검증 | 사용자 실행 PASS |
| 2026-07-24 S3 object set isolated restore·데이터 비교 | 사용자 실행 PASS |
| 2026-07-24 production 보존·서비스 smoke·cleanup | 사용자 실행 PASS |
| 2026-07-24 비민감 운영 evidence 검토 | `production-verification-2026-07-24.md`와 상호 추적 확인 |

## 실행하지 못한 검증과 이유

구현 당시 로컬에서는 실제 AWS·S3·IAM·lifecycle 변경과 운영 backup·restore·production 보존 비교를 실행하지 않았다. 로컬 Docker engine이 없어 isolated MySQL lifecycle을 시작하지 않았고 Windows Git Bash에는 `flock`이 없어 기존 production script 회귀는 Repository Validation을 독립 실행 근거로 사용했다. 이 역사적 미실행 상태는 2026-07-24 사용자 실행으로 실제 S3·IAM 계약, 운영 backup, isolated restore와 production 보존 범위에서 해소됐다.

실제 production DB restore, 자동 schedule·실패 알림, cross-region, versioning 활성화·versioned backup 보존 전략, Glacier·KMS, 별도 restore EC2와 RPO/RTO 확정은 실행하거나 검증하지 않았다. OPS-013 계약의 `versioning 비활성` 상태 검증은 완료 항목이다.

## QA 필요 여부

별도 QA 문서는 생략한다. 제품 동작 변경이 없고 독립 계약 validator·Repository Validation과 사용자·Tech Lead의 실제 AWS·운영 Runbook 검증을 사용한다.

## QA 문서 경로 또는 생략 사유

제품·API·DB schema 변경이 없으며 데이터 손실 위험은 독립 CI와 실제 운영자의 명시적 중단 gate·비민감 증거 검토로 분리하므로 별도 QA 문서를 만들지 않는다.

## 적용 방법

`docs/runbook/OPS-013-production-db-backup-restore.md`의 전용 신규 bucket·IAM 준비 → production preflight → backup → S3 재검증 → isolated restore → schema·Flyway·count 비교 → cleanup 순서를 사용한다.

## 복구·롤백 증거 (고위험 필수)

자동 trap과 backup ID 기반 cleanup은 OPS-013 label, `none` network와 production volume 미사용을 재검증한 temporary container·volume·work path만 제거한다. lifecycle test 역시 생성 ownership flag가 설정된 fixture volume만 제거한다. upload·checksum·restore·verification 실패는 성공 문구를 출력하지 않고 completion marker 없는 부분 object는 14일 lifecycle에 맡긴다. production release·HTTPS state·MySQL container·volume을 변경하거나 삭제하는 경로는 없다.

2026-07-24 실제 실행 후 temporary restore container·volume·work directory와 temporary source가 남지 않았고, production MySQL container와 `pawcycle-production-mysql-data`가 보존됐다. 승인된 일회성 유지보수 예외로 중지한 proxy·frontend·backend를 복구한 뒤 네 서비스 health, HTTPS와 공개 상품 API를 재검증했다. temporary Swap은 사용량 0 MiB 확인 뒤 제거됐으며 새 Session Manager 세션의 읽기 재검증까지 PASS했다. S3 backup object는 지정 prefix의 14일 lifecycle 대상으로 유지하며 instance role에 자동 삭제 권한을 추가하지 않았다.

## 위험과 제한

- dump 중 DDL은 MySQL consistent dump를 무효화할 수 있어 금지한다.
- dump snapshot manifest를 만들기 위한 backup-time isolated import가 같은 EC2의 CPU·memory·disk I/O를 추가 사용하므로 저부하 시점에 실행한다.
- 같은 EC2·EBS 장애는 source와 restore 검증 환경을 함께 손상시킬 수 있다.
- 실제 압축 해제 크기 측정과 isolated restore는 같은 EC2의 CPU·disk I/O를 추가 사용한다.
- 자동 schedule·실패 알림, 실제 production restore, cross-region, versioning 활성화·versioned backup 보존, Glacier·KMS와 장기 backup 전략은 없다. bucket의 `versioning 비활성` 계약 검증은 완료됐다.
- 최소 IAM과 부분 multipart 잔여물 방지를 위해 단일 object upload를 사용하며 compressed dump가 `5,000,000,000` byte를 넘으면 별도 설계 승인 없이 진행하지 않는다.
- 실제 S3 bucket 계약과 EC2 instance role 최소 권한, 운영 backup·isolated restore·production 보존은 2026-07-24 사용자 검증 범위에서 확인됐다. 별도 restore EC2, RPO/RTO와 actual production restore 복구 훈련은 미확정이다.
- 성공한 backup ID는 동일 실행의 `restore-verify`에 사용됐지만 저장소 밖 장기 별도 보관 여부는 확인되지 않았다. 향후 재검증 전 접근이 제한된 운영 기록의 보관 여부를 확인해야 한다.
- 2026-07-24 application 중지는 짧은 서비스 중단을 수용한 승인된 일회성 예외이며 반복 실행의 기본 절차가 아니다.
- 현재 OPS-012 application rollback은 `previous-sha` 부재로 Deferred다.

## 다음 작업

자동 schedule·실패 알림, cross-region, versioning 활성화·versioned backup 보존, Glacier·KMS, 별도 restore EC2, RPO/RTO 확정과 실제 production restore 훈련은 별도 승인 작업으로 유지한다. 향후 동일 backup 재검증에는 backup ID를 저장소 밖 제한된 운영 기록에 보관한다. OPS-012 application rollback은 `previous-sha` 부재 Deferred 상태를 변경하지 않는다.

## Git 결과

- branch: `ops/sre`
- 최초 commit 제목: `feat(sre): OPS-013 운영 DB 백업 복구 기반 구성`
- [구현 PR #62](https://github.com/guseoh/pawcycle-commerce/pull/62)는 병합됐으며 후속 운영 검증 문서 commit과 정확한 push 상태는 GitHub를 권위 원본으로 확인한다.

## PR 결과

[PR #63](https://github.com/guseoh/pawcycle-commerce/pull/63)의 동적 head·review·Draft/Ready 상태와 [checks 페이지](https://github.com/guseoh/pawcycle-commerce/pull/63/checks)를 GitHub 권위 원본으로 확인한다. run ID·head SHA·check ID는 문서에 고정하지 않으며 자동 병합하지 않는다.
