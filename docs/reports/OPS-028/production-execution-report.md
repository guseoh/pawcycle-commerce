# OPS-028 Production 실행 보고서

## 작업 정보

- 작업 ID: `OPS-028`
- 작업 등급: 고위험
- 역할: Platform/SRE
- 작업 유형: 실제 Production 실행 결과의 저장소 증거화
- 저장소 기준: `main`의 `b174449d525dc0a053bfaf802a0204b13f705f31`
- 기록 근거: 사용자/Product Owner·Tech Lead가 단계별로 승인하고 직접 확인한 비민감 실행 결과

## 작업 목적

현재 Production Control 적용, 현재 Application 재검증, 기록된 이전 Application으로의 실제 rollback, 원래 Application 재배포와 최종 읽기 전용 검증 결과를 영구 증거로 기록한다. OPS-026에서 부분 충족으로 남은 현재 Control 경로의 실제 rollback 검증 공백을 후속 OPS-029 Tech Lead가 재판정할 수 있게 사실과 증거 경계를 분리한다.

이 보고서는 OPS-028 Production 실행 성공을 기록하지만 `OPS-VERIFY-001`을 판정하지 않는다.

## 입력 문서

- `docs/reports/OPS-026/tl-report.md`: 현재 Control의 실제 Production rollback 검증 공백과 판정 경계
- `docs/reports/OPS-027/sre-report.md`: rollback Control 호환성 저장소 준비와 병합 후 실행 순서
- `docs/runbook/OPS-010-production-single-release.md`: Control 채택, deploy·rollback state 전이와 비민감 증거 경계
- `docs/reports/OPS-025/sre-report.md`: `active-mysql-volume`과 실제 MySQL mount 보존 계약
- `docs/architecture/production-operations-overview.md`: 현재 운영 구조와 잔여 위험
- [PR #79](https://github.com/guseoh/pawcycle-commerce/pull/79): OPS-027 병합 결과와 Repository Validation

## 승인 입력

사용자는 다음 Production 단계를 각각 승인하고 실행 결과를 직접 확인했다.

- 최신 Production Control checkout
- 현재 Application을 유지한 새 Control 계약 채택
- 기록된 이전 Application으로 실제 rollback
- 원래 Application 재배포
- 각 전이 뒤 health, 내부 Smoke, HTTPS와 MySQL volume 보존 확인

공개 commit SHA와 비민감 판정 결과만 저장소에 기록하며, 추가 Production 실행은 승인하지 않았다.

## 명시적 승인 근거

사용자가 OPS-028 요청에서 고위험 Platform/SRE 역할, 승인된 실행 사실과 SHA, 실행 순서, 중간·최종 검증 결과, 보안 경계, 금지 판정과 Draft PR 완료 조건을 명시했다. 이는 이미 완료된 실행 결과의 증거화 승인으로 한정되며 새 Production·AWS·Docker·DB·Secret 명령 실행 승인이 아니다.

## 변경 범위

- `docs/reports/OPS-028/production-execution-report.md` 한 파일에 승인된 비민감 실행 결과를 기록한다.
- Control, Application, state, health·Smoke·HTTPS와 MySQL volume 보존 결과를 실행 단계별로 구분한다.
- OPS-029가 재판정할 수 있도록 확인된 사실, 미실행 항목과 잔여 위험을 분리한다.

## 변경하지 않은 범위

- Production·AWS·Docker·DB·Secret 추가 명령과 운영 상태 변경
- 운영 Script·Compose·Runbook·workflow·validator 변경
- Application 제품 코드, API, DB schema·Flyway history와 데이터 변경
- Production DB restore·훈련, schema downgrade와 물리 volume·Instance 복구
- OPS-026·OPS-027·운영 개요의 소급 수정
- `OPS-VERIFY-001` 판정, 무중단·RTO·자동복구·고가용성 판정

## 인수 조건 매핑

| 인수 조건 | 기록 위치 | 결과 |
| --- | --- | --- |
| 현재 Control 실제 Production 적용 | Control 채택 결과·최종 상태 | 확인 |
| 현재 `active-mysql-volume` 경로의 실제 rollback | rollback 결과·MySQL volume 보존 | 확인 |
| 원래 Application 재배포 | 원래 Release 재배포 결과 | 확인 |
| health·내부 Smoke·HTTPS | health·Smoke·HTTPS 검증 | 확인 |
| volume 보존 | MySQL volume 보존 | 확인 |
| 실행·판정 경계 유지 | 실행하지 않은 작업·결정 상태 | 확인 |

## 주요 결과

- Production Control HEAD를 `82cb5a22e34a8381ba82d4ba7458f24314c184a8`에서 `b174449d525dc0a053bfaf802a0204b13f705f31`로 전환했다.
- 현재 Application을 유지한 채 새 Control 계약을 채택했고 `contract-sha`가 새 Control과 일치했다.
- 기록된 이전 Application `b9cf3cf51c5ffd4b85c6eafc78706ed079e299d6`로 실제 rollback한 뒤 health·내부 Smoke·HTTPS와 MySQL volume 보존을 확인했다.
- 원래 Application `2e9222b568a3469e8ccc5edce1b5301218c6888e`를 재배포하고 같은 검증을 다시 통과했다.
- rollback과 재배포에서 DB restore나 자동복구는 발생하지 않았다.

## 변경 파일

- `docs/reports/OPS-028/production-execution-report.md`

## 결정 상태

- `OPS-028 Production 실행 = 성공`
- `최신 Control 채택 = 성공`
- `실제 Application rollback = 성공`
- `원래 Application 재배포 = 성공`
- `최종 health·내부 Smoke·HTTPS = 성공`
- `active MySQL volume 보존 = 확인`
- `OPS-VERIFY-001 = 미판정`: 후속 OPS-029 Tech Lead 작업의 책임이다.

## API 영향

API 계약과 동작을 변경하지 않았다. 내부·HTTPS 상품 API Smoke는 기존 공개 API의 가용성 확인에만 사용했다.

## DB 영향

DB restore, schema downgrade, Flyway history 수정과 데이터 변경을 실행하지 않았다. active MySQL volume과 실제 MySQL mount가 전이 전후 같은 상태로 보존됐다는 비민감 결과만 기록한다.

## 보안 영향

서버 hostname, IP, 실제 domain, AWS account·ARN, SSM 경로·값, Secret·token·password·certificate 원문, DB row·개인정보, backup ID와 전체 원시 로그를 기록하지 않았다. 공개 commit SHA와 비민감 PASS·보존 판정만 사용한다.

## 운영 영향

이 보고서 작성 자체는 운영 환경을 변경하지 않는다. 기록 대상 실행에서는 Control 채택, Application rollback과 재배포가 있었으며 최종 Application은 시작 Application으로 복귀했다. 정확한 서비스 중단 시간과 사용자 트래픽 영향은 측정하지 않았다.

## 성능 영향

성능 실험이 아니다. 최종 읽기 전용 확인 시 가용 메모리 `598 MiB`, Docker 디스크 여유 `29 GiB`가 관찰됐지만 기준 성능, 목표, 장기 capacity 또는 성능 충족 판정으로 사용하지 않는다.

## 실행 전 기준선

사용자가 Production 변경 전에 읽기 전용 기준선을 확인했다.

| 항목 | 확인된 기준 |
| --- | --- |
| 이전 Control HEAD | `82cb5a22e34a8381ba82d4ba7458f24314c184a8` |
| 원래 Application | `2e9222b568a3469e8ccc5edce1b5301218c6888e` |
| rollback 대상 | `b9cf3cf51c5ffd4b85c6eafc78706ed079e299d6` |
| 변경 대상 | Control 채택과 Application 전이 |
| 금지된 데이터 작업 | DB restore·schema downgrade·volume 삭제 |

원시 기준선 출력, 운영 식별자와 Secret은 제공되거나 저장되지 않았다.

## 적용 방법 또는 실행 순서

사용자가 승인한 실행 순서는 다음과 같다.

1. Production 변경 전 읽기 전용 기준선 확인
2. Control HEAD를 이전 Control에서 새 Control로 checkout
3. 현재 Application을 유지한 채 새 Control 계약 채택
4. 원래 Application에서 기록된 이전 Application으로 rollback
5. rollback 결과의 health, 내부 Smoke, HTTPS와 volume 보존 확인
6. 이전 Application에서 원래 Application으로 재배포
7. 최종 읽기 전용 state·revision·health·Smoke·HTTPS·volume 확인

OPS-028 문서 작성 과정에서는 위 절차나 다른 Production 명령을 다시 실행하지 않았다.

## Control 채택 결과

- 이전 Control HEAD: `82cb5a22e34a8381ba82d4ba7458f24314c184a8`
- 새 Control HEAD: `b174449d525dc0a053bfaf802a0204b13f705f31`
- 채택 후 `contract-sha`: `b174449d525dc0a053bfaf802a0204b13f705f31`
- 채택 중 유지한 Application: `2e9222b568a3469e8ccc5edce1b5301218c6888e`
- 결과: 현재 Application을 바꾸지 않고 새 Control 계약 채택 성공

Control checkout과 계약 채택을 Application 배포나 DB 변경으로 확대하지 않는다.

## rollback 결과

| 항목 | 확인 결과 |
| --- | --- |
| `current-sha` | `b9cf3cf51c5ffd4b85c6eafc78706ed079e299d6` |
| `previous-sha` | `2e9222b568a3469e8ccc5edce1b5301218c6888e` |
| `previous-contract-sha` | `b174449d525dc0a053bfaf802a0204b13f705f31` |
| Backend·Frontend revision | rollback 대상과 일치 |
| 네 Container | 모두 healthy |
| 내부 Smoke | PASS |
| HTTPS | PASS |
| MySQL volume | 보존 |
| DB restore | 실행하지 않음 |
| 자동복구 | 발생하지 않음 |

현재 Control과 기록된 이전 Release의 호환성 경로에서 실제 rollback이 성공했다. 이는 Production DB restore나 장애 주입 자동복구 증거가 아니다.

## 원래 Release 재배포 결과

| 항목 | 확인 결과 |
| --- | --- |
| `current-sha` | `2e9222b568a3469e8ccc5edce1b5301218c6888e` |
| `previous-sha` | `b9cf3cf51c5ffd4b85c6eafc78706ed079e299d6` |
| `previous-contract-sha` | `b174449d525dc0a053bfaf802a0204b13f705f31` |
| Backend·Frontend revision | 원래 Application과 일치 |
| 네 Container | 모두 healthy |
| 내부 Smoke | PASS |
| HTTPS | PASS |
| MySQL volume | 보존 |
| 자동복구 | 발생하지 않음 |

원래 Application 재배포 뒤 시작 Application으로 복귀했다.

## 최종 상태

| 항목 | 최종 확인 결과 |
| --- | --- |
| Control HEAD | `b174449d525dc0a053bfaf802a0204b13f705f31` |
| `contract-sha` | `b174449d525dc0a053bfaf802a0204b13f705f31` |
| `current-sha` | `2e9222b568a3469e8ccc5edce1b5301218c6888e` |
| `previous-sha` | `b9cf3cf51c5ffd4b85c6eafc78706ed079e299d6` |
| `previous-contract-sha` | `b174449d525dc0a053bfaf802a0204b13f705f31` |
| active MySQL volume·실제 mount | 변경 없이 보존 |
| mysql·backend·frontend·proxy | 모두 healthy |
| Backend·Frontend revision | `current-sha`와 일치 |
| 내부 상품 화면·상품 API Smoke | PASS |
| HTTPS 상품 화면·상품 API Smoke | PASS |
| 인증서 SAN·최소 유효기간 | PASS |
| 가용 메모리 | `598 MiB` 관찰 |
| Docker 디스크 여유 | `29 GiB` 관찰 |

최종 상태는 새 Control 계약 아래 원래 Application으로 복귀한 상태다.

## state 전이

| 단계 | Control HEAD | `contract-sha` | `current-sha` | `previous-sha` | `previous-contract-sha` |
| --- | --- | --- | --- | --- | --- |
| 실행 전 | `82cb5a22e34a8381ba82d4ba7458f24314c184a8` | 제공되지 않음 | `2e9222b568a3469e8ccc5edce1b5301218c6888e` | 제공되지 않음 | 제공되지 않음 |
| 새 Control 채택 | `b174449d525dc0a053bfaf802a0204b13f705f31` | `b174449d525dc0a053bfaf802a0204b13f705f31` | `2e9222b568a3469e8ccc5edce1b5301218c6888e` | 중간값 미기록 | 중간값 미기록 |
| rollback 성공 | `b174449d525dc0a053bfaf802a0204b13f705f31` | `b174449d525dc0a053bfaf802a0204b13f705f31` | `b9cf3cf51c5ffd4b85c6eafc78706ed079e299d6` | `2e9222b568a3469e8ccc5edce1b5301218c6888e` | `b174449d525dc0a053bfaf802a0204b13f705f31` |
| 원래 Release 재배포 | `b174449d525dc0a053bfaf802a0204b13f705f31` | `b174449d525dc0a053bfaf802a0204b13f705f31` | `2e9222b568a3469e8ccc5edce1b5301218c6888e` | `b9cf3cf51c5ffd4b85c6eafc78706ed079e299d6` | `b174449d525dc0a053bfaf802a0204b13f705f31` |

제공되지 않은 실행 전·중간 state 값은 추측하지 않는다.

## health·Smoke·HTTPS 검증

- rollback 성공 뒤 mysql·backend·frontend·proxy가 모두 healthy였다.
- rollback 대상과 Backend·Frontend revision이 일치했다.
- rollback 뒤 내부 상품 화면·상품 API와 HTTPS 경로가 모두 PASS였다.
- 원래 Application 재배포 뒤 네 Container가 다시 모두 healthy였다.
- 최종 Backend·Frontend revision은 `current-sha`와 일치했다.
- 최종 내부 상품 화면·상품 API와 HTTPS 상품 화면·상품 API가 모두 PASS였다.
- 인증서 SAN과 최소 유효기간 검사가 PASS였다.

실제 service domain, IP, 인증서 원문과 전체 HTTP 출력은 기록하지 않는다.

## MySQL volume 보존

- Control 채택, rollback과 원래 Application 재배포 전후 active MySQL volume이 변경되지 않았다.
- 최종 active MySQL volume state와 실제 MySQL mount가 일치했다.
- volume 이름과 운영 host 식별자는 기록하지 않는다.
- DB restore, volume 삭제·초기화와 schema downgrade를 실행하지 않았다.

이 결과는 현재 `active-mysql-volume` 경로의 보존 증거이며 물리 volume·EBS 장애 복구 증거가 아니다.

## 실행한 검증

Production 실행자가 확인한 운영 결과와 저장소 문서 검증을 다음과 같이 분리해 기록한다.

### 사용자가 Production에서 확인한 결과

- 현재 Control checkout과 계약 채택 성공
- 기록된 이전 Application rollback 성공
- 원래 Application 재배포 성공
- 각 Application 전이 뒤 네 Container health, revision, 내부 Smoke와 HTTPS PASS
- active MySQL volume과 실제 mount 보존
- 최종 state SHA와 Control SHA 일치
- 인증서 SAN·최소 유효기간 PASS

### 저장소 문서 검증

- OPS-028 고위험 task artifact validator: 변경 후 실행
- Markdown UTF-8·trailing whitespace·내부 경로: 변경 후 확인
- `git diff --check`: 변경 후 실행
- 변경 파일 범위와 민감정보 패턴: 변경 후 확인

## 적용 전 검증

- 사용자 실행에서 Production 변경 전 읽기 전용 기준선을 확인했다.
- 저장소 작업 시작 시 `origin/main`이 예상 SHA `b174449d525dc0a053bfaf802a0204b13f705f31`과 일치함을 확인했다.
- 열린 PR이 없고 PR #79가 병합됐으며 최종 head가 잔여 원격 `ops/sre`와 일치함을 확인했다.
- PR #79의 Commit and PR conventions와 Application validation이 모두 성공했음을 확인했다.
- 로컬의 오래된 `ops/sre`는 병합된 PR #76 이력에 포함되고 원격 `ops/sre`는 병합된 PR #79의 최종 head와 같음을 확인한 뒤, force push 없이 최신 `origin/main`에서 역할 브랜치를 재생성했다.

## 적용 후 검증

- rollback과 원래 Application 재배포 뒤 state, revision, 네 Container health, 내부 Smoke, HTTPS와 active MySQL volume 보존이 확인됐다.
- 최종 Control HEAD와 `contract-sha`, Application state와 revision이 서로 일치했다.
- OPS-028 보고서가 실행 성공과 미실행·금지 판정을 분리하는지 확인한다.
- 저장소 변경이 승인된 새 보고서 한 파일로 제한되는지 확인한다.
- 고위험 task artifact, UTF-8, 내부 경로와 diff 공백 검증을 실행한다.

## 독립 검증

- 실제 Production 실행자이자 Product Owner·Tech Lead인 사용자가 각 승인 단계와 중간·최종 결과를 직접 확인했다.
- Codex는 제공된 비민감 결과를 OPS-010 Runbook, OPS-025 active volume 계약, OPS-026 판정 공백과 OPS-027 저장소 준비에 대조한다.
- PR #79 Repository Validation은 실행에 사용한 Control 계약의 독립 저장소 검증이며 실제 Production 실행 자체를 대신하지 않는다.
- OPS-028 문서 delta의 독립 자동 검증은 Draft PR의 Repository Validation을 권위 원본으로 확인한다.

## 실행하지 못한 검증과 이유

- Production 추가 실행: 사용자 요청에서 명시적으로 금지했다.
- 정확한 서비스 중단 시간·무중단 여부·사용자 트래픽 영향: 실행 중 측정 결과가 제공되지 않았다.
- RTO: 목표와 복구 시간 측정이 없어 판정하지 않는다.
- 장애 주입 자동복구: 실제 장애를 의도적으로 발생시키지 않았고 자동복구도 발생하지 않았다.
- Production DB restore 훈련·schema downgrade: 실행 범위 밖이며 DB 복원도 발생하지 않았다.
- 물리 volume·EBS·Instance 장애 복구: 논리 Application rollback 범위 밖이다.
- Backend·Frontend 전체 테스트: 제품 코드·API·DB schema·Frontend를 변경하지 않는 증거 문서 한 파일 작업이므로 로컬에서 반복하지 않는다. Repository Validation이 자동 실행하면 그 결과를 사용한다.

## QA 필요 여부

별도 제품 QA 문서는 필요하지 않다. 제품 동작이나 운영 계약을 변경하지 않고 사용자/Tech Lead가 직접 확인한 실제 Production 실행 결과를 증거화한다. OPS-028 문서 형식과 범위는 validator·Repository Validation 및 후속 OPS-029 Tech Lead 검토로 독립 확인한다.

## QA 문서 경로 또는 생략 사유

- 별도 QA 문서는 생략한다.
- 제품 사용자 흐름·API·DB·운영 구현 변경이 없고 실제 실행 결과는 사용자가 단계별로 직접 검증했다.

## AI 리뷰 반영 여부

- PR 생성 전: 해당 없음
- PR 생성 후 CodeRabbit·Codex Review 결과는 GitHub를 동적 권위 원본으로 확인한다.

## AI 리뷰 미반영 항목과 이유

- 현재 없음. 리뷰가 생성되면 실행 사실, 비민감 경계와 승인 범위에 대조해 유효한 항목만 반영한다.

## 경고와 제한

실행 중 기존 Certbot 관련 named volume이 Docker Compose에서 생성되지 않았다는 경고가 출력됐다.

- 경고는 실행을 중단시키지 않았다.
- 인증서 SAN·최소 유효기간과 HTTPS 검증은 통과했다.
- OPS-028에서 Compose 선언이나 volume을 수정하지 않았다.
- 경고의 근본 원인이 해결됐다고 판정하지 않는다.

또한 정확한 서비스 중단 시간, 무중단 여부, RTO와 사용자 트래픽 영향은 측정하지 않았다. 최종 메모리·디스크 값은 한 시점의 관찰이며 장기 운영 안전성이나 capacity를 증명하지 않는다.

## 적용 방법

이 파일은 이미 완료된 실행의 비민감 증거로 사용한다. Production에 별도로 적용할 변경은 없다. OPS-028가 병합되면 OPS-029 Tech Lead가 본 보고서와 기존 실행·계약 증거를 대조해 OPS-VERIFY-001을 별도로 재판정한다.

## 복구·롤백 증거

- 실제 Application 복구 경로는 원래 Application에서 이전 Application으로 rollback 성공으로 확인됐다.
- 원래 상태 복귀 경로는 이전 Application에서 원래 Application으로 재배포 성공으로 확인됐다.
- 두 전이 뒤 health·내부 Smoke·HTTPS와 MySQL volume 보존이 확인됐다.
- DB restore, schema downgrade, Flyway history 수정, volume 삭제와 자동복구는 복구 수단으로 사용하지 않았다.
- OPS-028 문서가 잘못된 경우 판정·운영 증거에 미치는 영향을 고위험으로 검토한 별도 Revert PR로 되돌린다. 단순 링크 오타만 위험 하향 근거가 있을 때 작은 문서 수정으로 처리할 수 있다.

## 위험과 제한

- 사용자 제공 비민감 결과를 기록하며 Codex가 Production에 접속하거나 원시 로그·Secret·DB row를 독립 열람한 증거가 아니다.
- 성공한 Application rollback은 Actual Production DB restore나 schema downgrade 검증을 대신하지 않는다.
- 한 번의 실행 성공은 무중단, RTO, 자동복구, 모든 트래픽·장애 상황이나 고가용성을 증명하지 않는다.
- Certbot 관련 named volume 경고의 근본 원인은 해결되지 않았다.

## 남은 위험

- 정확한 서비스 중단 시간, 무중단 여부와 사용자 트래픽 영향 미측정
- RTO 미정·미검증
- 장애 주입 자동복구 미검증
- Production DB restore·복귀 훈련 미실행
- DB schema downgrade 미검증
- 물리 volume·EBS·Instance·filesystem 장애 복구 미검증
- Certbot 관련 named volume 경고의 근본 원인 미확인
- 외부 unknown Host, HTTPS 자동 갱신 schedule·certificate backup, 자동 backup·실패 알림·장기 보존, Blue/Green·고가용성·장기 관측성은 기존 잔여 위험으로 유지

## 다음 작업

- OPS-028 병합 뒤 별도 고위험 `OPS-029` Tech Lead 작업에서 OPS-VERIFY-001을 재판정한다.
- OPS-029 전에는 이 보고서를 `OPS-VERIFY-001 = Verified` 또는 전체 운영 완성 근거로 확대하지 않는다.
- Certbot 경고와 나머지 잔여 위험은 각각 별도 승인 작업에서 우선순위를 결정한다.

## 인수인계 생략

- 후속 OPS-029 Tech Lead가 이 보고서를 직접 권위 입력으로 사용하므로 별도 중복 handoff를 만들지 않는다.
- 실행 운영자에게 새 적용 절차를 전달하는 작업이 아니며 Production 추가 실행도 없다.

## Git 결과

- commit·push 결과는 Git을 권위 원본으로 확인한다.
- force push와 history rewrite를 사용하지 않는다.

## PR 결과

- `ops/sre`에서 `main`을 대상으로 Draft PR을 생성한다.
- PR head, Checks, 리뷰와 미해결 thread는 GitHub를 동적 권위 원본으로 확인한다.
- 자동 병합하지 않는다.
