# HARNESS-CODE-001 Tech Lead 작업 보고서

## 작업

- 작업 ID: HARNESS-CODE-001
- 작업 등급: 고위험
- 실행 구분: 저장소 준비
- 역할: Tech Lead
- 시작 기준: `origin/main` `82375f5a8fe6b93d5c180bca2452957aece9cc03`
- 작업 branch: `ops/tl/HARNESS-CODE-001`

## 목적

PawCycle backend 전체 Java source와 test를 전수 조사하고 외부 계약을 보존할 수 있는 formatting, typed API boundary, persistence 격리, exception logging, request correlation 개선을 적용한다. 동시에 PCC_V5 기준으로 Harness drift를 줄이고 branch 보존 상태를 분류한다.

## 전체 조사 증거

- 조사 대상: production Java 258개, test Java 99개, 합계 357개
- 최초 formatter 입력: 당시 존재하던 production/test Java 324개 전체
- 변경 후 raw `Map` production 파일: 55개
- 변경 후 Controller `Map` 파일: 8개
- 변경 후 직접 JDBC/SQL 검색 후보: 47개
- 변경 후 `CommerceRequests`/`EngagementRequests` 참조: 0개
- `AdminCatalogRequests` 참조: 10개
- 변경 경로: tracked diff 328개, working tree entry 363개

`Map`과 JDBC 수치는 정규식 기반 후보 수이며 architecture 적합 판정 수가 아니다. 모든 후보를 사람이 확인해야 하는 이유가 바로 이 작업의 미완료 위험이다.

## 적용 결과

### API와 application 경계

- `CommerceRequests`와 `EngagementRequests` holder를 제거했다.
- commerce HTTP request 13개를 top-level record로 분리했다.
- engagement HTTP request 6개와 application command 4개를 분리했다.
- billing, coupon, membership grade, address의 정적 응답 7개를 typed response로 전환했다.
- interaction 500 오류 응답의 2-field JSON 계약을 보존하는 `InteractionErrorResponse`를 추가했다.
- 대표 응답 JSON shape를 고정하는 serialization regression test를 추가했다.
- `legacyPayload()` 변환을 제거하고 address/coupon/membership 입력을 typed request로 전달한다.

### 책임 격리

- `InteractionService`의 직접 JDBC/SQL과 row conversion을 `InteractionRepository`로 이동했다.
- transaction과 HTTP route/status, DB schema/migration은 변경하지 않았다.
- commerce 전체 package 이동은 상호 의존성과 회귀 범위가 커서 이번 안전한 변경에 포함하지 않았다.

### Logging과 correlation

- 새 dependency 없이 `RequestCorrelationFilter`를 추가했다.
- 안전한 `X-Request-ID`만 MDC `requestId`로 수용하고, 그 외 값은 UUID로 대체하며 request 종료 시 `finally`에서 clear한다.
- 외부 response header 계약은 변경하지 않았다.
- 주요 checkout/billing 완료와 idempotent replay에 parameterized INFO/DEBUG log를 추가했다.
- commerce, engagement, recommendation, interaction, subscription의 예상치 못한 500 boundary에 단일 stack trace log를 추가했다.
- 새 logger 호출의 문자열 결합과 password/authKey/paymentKey/billingKey/token/session/CSRF/PII 직접 참조 검색 결과는 0건이다.
- 유지한 `System.out`/`System.err`는 사용자 실행형 maintenance command의 CLI 출력이다.

### Formatting

- 기존 production/test Java 324개 전체에 google-java-format 1.36.1을 일회성 도구로 적용했다.
- formatter/build dependency나 plugin은 저장소에 추가하지 않았다.
- annotation, field, constructor, method, import, spacing과 긴 표현식을 동일 규칙으로 정리했다.

### Harness

- `docs/runbook/lean-harness.md`의 stale HARNESS-LEAN-002 기준을 PCC_V5/current main 기준으로 교체했다.
- Delta 추출, Final Lightweight Delta Prompt, prompt 외 metadata, 최소 validator, feedback loop를 명시했다.
- root/backend AGENTS와 onboarding/runbook의 branch 삭제 조건에 명시적 사용자 승인을 추가했다.
- `backend/AGENTS.md`에 feature 중심 package, typed boundary, raw Map 제한, persistence 격리, JDBC 허용 조건, formatting/logging/민감정보 규칙을 간결하게 고정했다.

## Branch audit

`git fetch --prune origin` 후 local/remote ref 170개를 `origin/main` 포함 여부, unique commit, open PR, active worktree로 분류했다.

- DELETE_CANDIDATE: 25
- KEEP: 35
- REVIEW: 110
- 실제 삭제: 0
- open PR: 0 (`gh pr list` read-only 확인)
- active worktree branch: 17

각 ref의 판정은 [HARNESS-CODE-001-branch-audit.md](../../audits/HARNESS-CODE-001-branch-audit.md)에 보존한다. DELETE_CANDIDATE도 사용자 승인 전에는 삭제하지 않는다.

## 검증 결과

### 최초 19개 실패 root cause matrix

동일 JDK 25, Gradle 9.5.1, MySQL 8.4.11과 fresh database에서 `origin/main` 고정 SHA와 변경 branch를 비교했다.

| # | Test | clean main | 변경 전 isolated | 변경 전 suite | 원인·분류 | 변경 후 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Auth process `duplicateProcessFailsWithoutChangingMemberOrRevealingDetails` | FAIL | FAIL | FAIL | Windows command line 32K 제한, `ENVIRONMENT` | PASS |
| 2 | Auth process `invalidGate... [missing]` | FAIL | FAIL | FAIL | 같은 classpath 길이 제한, `ENVIRONMENT` | PASS |
| 3 | Auth process `invalidGate... [false]` | FAIL | FAIL | FAIL | 같은 classpath 길이 제한, `ENVIRONMENT` | PASS |
| 4 | Auth process `invalidGate... [typo]` | FAIL | FAIL | FAIL | 같은 classpath 길이 제한, `ENVIRONMENT` | PASS |
| 5 | Auth process `invalidGate... [invalid]` | FAIL | FAIL | FAIL | 같은 classpath 길이 제한, `ENVIRONMENT` | PASS |
| 6 | Auth process `databaseInitializationFailureIsSanitized` | FAIL | FAIL | FAIL | 같은 classpath 길이 제한, `ENVIRONMENT` | PASS |
| 7 | Auth process `successfulProcessForcesFlywayOffAndPrintsOnlyPassLine` | FAIL | FAIL | FAIL | 같은 classpath 길이 제한, `ENVIRONMENT` | PASS |
| 8 | Repeat commerce `multipleSkusInOneOrderProduceOneProductPurchaseDate` | FAIL | FAIL | FAIL | JVM 기본 timezone에 따른 fixture 날짜 이동, `ENVIRONMENT` | PASS |
| 9 | Admin catalog `productListFetchesDistinctCategoriesInOneQuery` | PASS | PASS | reused DB에서 FAIL | 이전 suite catalog fixture 잔존, `SHARED_STATE` | PASS |
| 10 | Admin catalog concurrency `concurrentIdenticalProductTransitions...` | PASS | PASS | reused DB에서 FAIL | 이전 fixture와 cleanup FK 충돌, `SHARED_STATE` | PASS |
| 11 | Admin catalog concurrency `concurrentCategoryAndSkuDuplicates...` | PASS | PASS | reused DB에서 FAIL | 이전 fixture와 cleanup FK 충돌, `SHARED_STATE` | PASS |
| 12 | Catalog expansion `concurrentSkuOptionAssignments...` | PASS | PASS | reused DB에서 FAIL | 이전 fixture와 cleanup FK 충돌, `SHARED_STATE` | PASS |
| 13 | Catalog expansion `invalidCompareAtPriceReports...` | PASS | PASS | reused DB에서 FAIL | 이전 fixture와 cleanup FK 충돌, `SHARED_STATE` | PASS |
| 14 | Catalog expansion `patchDistinguishesOmittedFields...` | PASS | PASS | reused DB에서 FAIL | 이전 fixture와 cleanup FK 충돌, `SHARED_STATE` | PASS |
| 15 | Customer catalog import `applyCreatesOneHundredProduct...` | PASS | PASS | reused DB에서 FAIL | 전체 catalog count의 fresh DB 전제, `SHARED_STATE` | PASS |
| 16 | Demo product import `generatedSmallManifestImports...` | PASS | PASS | reused DB에서 FAIL | 전체 catalog count의 fresh DB 전제, `SHARED_STATE` | PASS |
| 17 | Product API `onlyPublicProductsAreExposed` | PASS | PASS | reused DB에서 FAIL | 이전 public product 잔존, `SHARED_STATE` | PASS |
| 18 | Product API `anonymousListMatchesPaginatedShape...` | PASS | PASS | reused DB에서 FAIL | 이전 public product 잔존, `SHARED_STATE` | PASS |
| 19 | Product API `anonymousEmptyListReturnsEmptyPage` | PASS | PASS | reused DB에서 FAIL | 이전 public product 잔존, `SHARED_STATE` | PASS |

clean main의 MySQL 8.4 fresh suite는 376개 중 위 baseline 8개만 실패했다. 변경 전 fresh suite에서는 신규 test 3개가 추가된 379개 중 같은 8개만 실패했고, DB를 재사용한 suite에서 catalog 11개가 추가되어 최초 19개가 재현됐다.

### Test infrastructure correction

- Auth child process classpath를 Java argument file로 전달해 Windows command-line 길이 제한을 제거하고 argument file을 `finally`에서 삭제한다.
- Repeat commerce test timestamp를 UTC instant로 생성해 JVM 기본 timezone과 무관하게 DB 날짜를 고정한다.
- catalog assertion은 완화하지 않았다. CI의 job별 fresh `mysql:8.4` service 전략을 유지하고 local 비교도 suite마다 별도 database를 사용한다.
- catalog 관련 6개 class의 MySQL 8.4 fresh isolated 실행은 모두 PASS했다.

### 최종 검증

- `backend/gradlew compileJava compileTestJava`: PASS
- 변경 영역 targeted integration/unit test: PASS
  - request correlation
  - commerce response serialization
  - interaction service/integration
  - admin commerce transaction
  - checkout idempotency
  - security foundation
  - auth exception handler
- backend 전체 `test`, JDK 25 / Gradle 9.5.1 / MySQL 8.4.11 fresh DB: PASS, 379개, unexpected failure 0
- test 수는 request correlation 2개와 API serialization 1개 추가로 baseline 376개에서 379개로 증가했다.
- `backend/gradlew build -x test`: PASS
- Harness validator unittest: PASS, 131개
- HARNESS-CODE-001 task artifact validation: PASS
- commit message convention fixture: PASS
- `git diff --check`: PASS
- 기존 container, system MySQL service와 data는 변경하지 않았다.

### Commit 분리 판단

`git diff --ignore-all-space --name-only`에서도 formatter의 line wrapping과 semantic 변경이 같은 328개 tracked file에 나타나 formatting-only file 집합을 안전하게 만들 수 없었다. patch surgery 없이 test determinism, backend modernization, Harness 문서·validator의 경로 단위 commit으로 분리한다.

## 위험·제한

이번 작업에서 동결한 구조 부채는 별도 승인 작업으로 남으며, 대규모 formatter diff는 semantic 변경과 안전하게 분리할 수 없어 review 시 whitespace ignore 비교를 병행해야 한다.

### 후속 BACKEND-REFACTOR-001 입력

- Controller `Map` 후보 8개와 raw `Map` production 후보 55개가 남았다. 일부는 request/dynamic adapter지만 각각의 schema 근거를 아직 완결하지 못했다.
- `AdminCatalogRequests` holder와 API type을 application/import service가 참조하는 경계가 남았다.
- commerce flat package의 feature package 이동은 56개 클래스의 transaction/component/test 영향을 동반해 정상 동작을 보존할 수 없는 대규모 rewrite 중단 조건에 해당한다.
- service/JDBC 후보 전체를 repository로 분리하지 못했고 Interaction 한 영역만 완료했다.
- formatter로 인한 대규모 diff와 semantic 변경을 한 PR에 함께 두면 review risk가 높다.

후속 semantic refactoring 후보는 catalog admin DTO/command 분리, commerce feature package 단계적 이동, 남은 Controller response typed 전환, query repository 분리, test fixture isolation이다. 각각 별도 승인 목적과 rollback 경계를 갖는 작업으로 나누는 것이 안전하다.

## 운영·Git 상태

- Product/Domain/API 정책 변경: 0
- DB schema/migration 변경: 0
- Production/Cloud/운영 DB mutation: 0
- branch 삭제/reset/rebase/force push/history rewrite: 0
- disposable baseline worktree와 로컬 MySQL test runtime/data는 검증 후 제거했으며 복구 대상이 아니다.
- merge: 미실행, 사용자 최종 결정
