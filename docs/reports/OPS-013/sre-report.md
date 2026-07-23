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

## 명시적 승인 근거 (고위험 필수)

사용자가 서울 region의 비공개 S3 Standard, SSE-S3, 지정 prefix 14일 만료, 압축 MySQL 논리 dump·checksum, 기존 EC2·EBS의 무 network·무 publish 임시 MySQL 복원 검증과 사용자 실행 Runbook을 승인했다. 실제 S3·IAM·lifecycle 생성과 운영 backup·restore는 병합 후 사용자 작업으로 분리했다.

## 변경 범위

- production MySQL health·image·volume·disk·memory preflight
- 일관된 압축 논리 dump를 격리 import해 같은 snapshot에서 생성한 schema·Flyway·핵심 table count manifest와 SHA-256
- S3 upload 전후 size·SSE-S3·download checksum과 completion marker 검증
- production과 분리된 pinned MySQL·임시 named volume 복원 lifecycle
- 성공·실패 cleanup, fake AWS·실제 MySQL lifecycle test와 정적 계약 validator
- Repository Validation, Runbook과 사용자·Tech Lead 인수인계

## 변경하지 않은 범위

application/API/DB schema·migration, production DB 쓰기·중지·restore, `pawcycle-production-mysql-data`, production network·port·Security Group, release·HTTPS state와 Compose는 변경하지 않는다. 실제 AWS·S3·IAM, 자동 schedule·알림, versioning·Glacier·KMS·cross-region과 backup·restore 실행도 범위에서 제외한다.

## 주요 결과

- source credential은 host에서 읽거나 command argument로 전달하지 않고 기존 MySQL container 내부 환경에서만 사용한다.
- ambient container credential과 AWS endpoint override를 거부하고 EC2 instance role·일반 S3 endpoint 경계를 강제한다.
- dump·manifest·checksum을 mode `600` root 전용 임시 경로에서 생성하고 S3 object set을 검증한다.
- production MySQL identity·health를 마지막으로 재확인한 뒤 completion marker를 업로드한다.
- restore 전 모든 S3 object의 size·SSE-S3와 work disk 여유를 확인해 대형 object의 선다운로드를 차단한다.
- checksum object는 hash와 기대 local basename 한 항목만 허용하며 외부·절대 경로를 읽지 않는다.
- 승인 서울 region과 expected bucket owner, Public Access Block 네 항목, SSE-S3, versioning 비활성과 지정 prefix 14일 lifecycle을 upload 전에 검사한다.
- dump를 임시 MySQL에 먼저 import해 manifest를 생성하므로 dump 이후 production row 쓰기가 backup snapshot 정합성을 깨뜨리지 않는다.
- dump 이외 metadata object는 upload 전부터 1 MiB로 제한한다.
- Runbook은 lifecycle·bucket policy 전체 교체 위험을 피하도록 OPS-013 전용 신규 빈 bucket만 허용한다.
- 단일 PUT은 `5,000,000,000` byte에서 fail-close하고 restore disk는 실제 압축 해제 크기를 측정해 산정한다.
- restore MySQL은 production과 같은 pinned image, `none` network, host port 없음, 고유 temporary volume·credential file과 resource limit을 사용한다.
- lifecycle test cleanup은 자신이 생성한 production 이름의 fixture volume만 제거한다.
- 실패와 성공 모두 temporary container·volume·work file을 정리하고 production container·volume·state와 S3 object를 삭제하지 않는다.

## API·DB·보안·성능 영향

API와 DB schema 변경은 없다. production DB에는 read-only 논리 dump와 metadata query만 실행한다. `--single-transaction --quick --skip-lock-tables`로 service 중지와 table lock을 피하지만 dump 중 DDL은 금지한다. backup·restore는 disk와 available memory safety floor를 통과해야 하며 isolated MySQL에 CPU·memory·PID 제한을 적용한다. 실제 압축 해제 크기 측정은 추가 CPU·I/O를 사용하지만 같은 host의 Docker disk 고갈 가능성을 줄인다.

## 적용 전 검증 (고위험 필수)

선행 운영 PR 병합 여부와 최신 `origin/main`, 깨끗한 새 `ops/sre`, 미병합 역할 PR 부재는 GitHub를 권위 원본으로 확인했다. production MySQL의 immutable image, runtime Secret의 mode `600` 경계, 고정 volume, 내부 data network와 application rollback의 volume 삭제 금지 계약을 확인했다. OPS-012 산출물은 main에 없으며 사용자 입력대로 `previous-sha` 부재 Deferred 상태를 유지한다.

## 적용 후 검증 (고위험 필수)

Shell syntax와 정적 계약 validator로 dump option, S3 PAB·SSE-S3·versioning 비활성·14일 lifecycle, completion marker 순서, credential 비노출, restore `none` network·무 publish·별도 volume, cleanup ownership과 production volume 삭제 금지를 확인했다. 실제 Docker engine이 없는 로컬 환경에서는 isolated MySQL lifecycle을 시작하지 못했으며 GitHub Repository Validation에서 실행하도록 연결했다.

최초 Repository Validation은 source fixture의 credential 전달과 source·restore readiness 경계에서 순차 실패했다. restore MySQL 초기화 중 `mysqladmin ping` 조기 성공 가능성을 readiness race로 추정해 대상 DB의 `127.0.0.1` TCP 인증 쿼리 연속 2회 성공으로 변경하고 gzip·SQL import 종료 상태를 분리했다. 이후 리뷰에서 test-owned volume cleanup, S3 lifecycle·policy 교체 경계, 환경변수 전달, decimal 5 GB, 실제 압축 해제 크기, download 전 object preflight, completion marker 순서, ambient AWS 설정과 checksum target 검증을 추가 보완했다. 마지막으로 dump와 live manifest의 snapshot 불일치, 서울 region·expected bucket owner, metadata 1 MiB upload 한도와 HTTPS 승인 순서 회귀 검증을 보완했다.

## 독립 검증 (고위험 필수)

구현 script와 분리된 `validate-production-contracts.py`가 OPS-013 보안·격리·보존 계약을 검사한다. Repository Validation의 Ubuntu Docker 환경에서 fake AWS 경계와 pinned source·restore MySQL lifecycle, 기존 production shell·Nginx·Compose·Backend·Frontend 회귀를 실행한다. 최신 head의 동적 run·check 상태는 GitHub를 권위 원본으로 확인한다.

## 실행한 검증

| 검증 | 결과 |
| --- | --- |
| `bash -n` OPS-013 script·test | 후속 수정마다 Repository Validation에서 실행 |
| `python infra/production/validate-production-contracts.py` | 후속 수정마다 Repository Validation에서 실행 |
| `bash infra/production/test-production-scripts.sh` | Repository Validation에 연결 |
| 로컬 `test-db-backup-restore.sh` | Docker engine unavailable로 fixture 생성 전 미실행 |
| OPS-013 고위험 task artifact validator | 통과 |
| commit message validator | 통과 |
| `git diff --check` | 통과 |
| 선행 Repository Validation | source credential·readiness·restore import 단계에서 순차 실패 후 수정 |
| 최신 Repository Validation | 격리 lifecycle과 Backend·Frontend 포함 전체 결과를 GitHub 권위 원본으로 확인 |

## 실행하지 못한 검증과 이유

실제 AWS·S3·IAM·lifecycle 변경, 운영 backup·restore와 production 보존 비교는 승인된 병합 후 사용자 작업이므로 미실행이다. 로컬 Docker engine이 없어 isolated MySQL lifecycle은 시작 전 중단됐으며 production 이름의 fixture container·volume은 생성되지 않았다.

## QA 필요 여부

별도 QA 문서는 생략한다. 제품 동작 변경이 없고 독립 계약 validator·Repository Validation과 사용자·Tech Lead의 실제 AWS·운영 Runbook 검증을 사용한다.

## QA 문서 경로 또는 생략 사유

제품·API·DB schema 변경이 없으며 데이터 손실 위험은 독립 CI와 실제 운영자의 명시적 중단 gate·비민감 증거 검토로 분리하므로 별도 QA 문서를 만들지 않는다.

## 적용 방법

`docs/runbook/OPS-013-production-db-backup-restore.md`의 전용 신규 bucket·IAM 준비 → production preflight → backup → S3 재검증 → isolated restore → schema·Flyway·count 비교 → cleanup 순서를 사용한다.

## 복구·롤백 증거 (고위험 필수)

자동 trap과 backup ID 기반 cleanup은 OPS-013 label, `none` network와 production volume 미사용을 재검증한 temporary container·volume·work path만 제거한다. lifecycle test 역시 생성 ownership flag가 설정된 fixture volume만 제거한다. upload·checksum·restore·verification 실패는 성공 문구를 출력하지 않고 completion marker 없는 부분 object는 14일 lifecycle에 맡긴다. production release·HTTPS state·MySQL container·volume을 변경하거나 삭제하는 경로는 없다.

## 위험과 제한

- dump 중 DDL은 MySQL consistent dump를 무효화할 수 있어 금지한다.
- dump snapshot manifest를 만들기 위한 backup-time isolated import가 같은 EC2의 CPU·memory·disk I/O를 추가 사용하므로 저부하 시점에 실행한다.
- 같은 EC2·EBS 장애는 source와 restore 검증 환경을 함께 손상시킬 수 있다.
- 실제 압축 해제 크기 측정과 isolated restore는 같은 EC2의 CPU·disk I/O를 추가 사용한다.
- 자동 schedule·알림, 실제 production restore, cross-region·versioning·KMS와 장기 backup은 없다.
- 최소 IAM과 부분 multipart 잔여물 방지를 위해 단일 object upload를 사용하며 compressed dump가 `5,000,000,000` byte를 넘으면 별도 설계 승인 없이 진행하지 않는다.
- 실제 AWS 정책의 독립 보안 검토와 운영 restore 증거는 사용자 실행 전까지 미확정이다.
- 현재 OPS-012 application rollback은 `previous-sha` 부재로 Deferred다.

## 다음 작업

병합 뒤 사용자·Tech Lead가 Runbook으로 전용 bucket·role·lifecycle을 준비하고 실제 backup·isolated restore·cleanup을 수행한다. 자동 schedule·알림, 실제 production restore와 OPS-012 rollback은 별도 승인 작업으로 유지한다.

## Git 결과

- branch: `ops/sre`
- 최초 commit 제목: `feat(sre): OPS-013 운영 DB 백업 복구 기반 구성`
- 후속 수정 commit과 정확한 push 상태는 GitHub를 권위 원본으로 확인한다.

## PR 결과

main 대상 PR의 동적 head·review·check·Draft/Ready 상태는 GitHub를 권위 원본으로 확인한다. 자동 병합하지 않는다.
