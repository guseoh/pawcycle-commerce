# FOUNDATION-004 Backend 작업 보고서

## 작업 정보

- 작업 ID: `FOUNDATION-004`
- 역할: Backend Engineer
- 기준 브랜치: `main`
- 작업 브랜치: `feat/be`
- 상태: 리뷰 반영 완료, 최종 head 검증 대기

## 작업 목적

첫 구독 MVP의 실제 브라우저 통합 검증에 필요한 전용 QA 회원·공개 상품·구독 가능 SKU를 local-only 조건에서 반복 가능하게 생성하고, 명시적 설정이 있을 때만 해당 회원의 구독을 초기화한다.

## 입력 문서

- `docs/adr/AUTH-003-session-authentication-approved-inputs.md`
- `docs/handoffs/AUTH-003/tl-to-be.md`
- `docs/domain/glossary.md`
- `docs/domain/rules.md`
- `docs/api/API-002-public-product-api-contract-proposal.md`
- `docs/api/API-003-subscription-api-contract-decision-request.md`
- `docs/handoffs/FRONTEND-001/fe-to-qa.md`

## 변경 범위

- `local-integration` profile과 기본 비활성 bootstrap 설정
- 환경 변수 기반 QA email·password 검증과 실행 시 BCrypt hash 생성
- 예약 local-part를 사용하는 전용 QA 회원 생성·재사용
- 예약 이름과 전체 필드 일치로 식별하는 공개 상품·구독 가능 SKU fixture
- 기존 fixture 충돌·복수 후보의 fail-fast 처리
- 명시적 reset에서 전용 QA 회원의 구독만 삭제
- profile, credential, 멱등성, 충돌, rollback과 reset 회귀 테스트
- Backend → Platform/SRE 실행 환경 인수인계

## 변경하지 않은 범위

- API 요청·응답·오류 계약과 Controller
- DB schema, Flyway migration과 production seed
- Frontend, reverse proxy, CORS, cookie 계약, Docker, CI와 배포
- 회원가입, 관리자 계정과 일반 상품·SKU 데이터 생성
- 구독 변경·일시정지·재개·해지
- 신규 dependency와 성능 최적화

## 실행 조건과 안전 경계

- `local-integration` profile이 활성화되고 `pawcycle.local-qa-bootstrap.enabled=true`일 때만 runner가 생성된다.
- `test`, `production` 또는 `prod` profile이 함께 활성화되면 runner가 생성되지 않는다.
- local profile 파일의 `enabled`와 `reset-subscriptions` 기본값은 모두 `false`다.
- local profile 파일은 session cookie 보안을 완화하지 않으며 기존 `SESSION_COOKIE_SECURE` 계약을 유지한다.
- email과 password에는 fallback·기본값이 없으며 누락·invalid 입력은 DB 접근 전에 일반화된 오류로 시작을 중단한다.
- email은 기존 AUTH-003 정규화 규칙을 사용하고 local-part가 예약 식별자 `qa-foundation-004`일 때만 허용한다.
- raw password, hash, session ID와 CSRF token을 출력하는 logger·응답·문서를 추가하지 않았다.

## fixture와 충돌 처리

- 회원: 정규화 email의 기존 UNIQUE 계약으로 조회하고 bootstrap 전용 비관적 쓰기 잠금을 사용한다.
- 기존 회원: `PasswordEncoder.matches`가 성공할 때만 재사용하며 password hash를 덮어쓰지 않는다.
- 신규 회원: 환경 변수 password를 기존 `PasswordEncoder`로 실행 시 hash한 뒤 저장한다.
- 상품: 예약 이름 후보가 없으면 결정적 필드로 생성하고, 하나이면 모든 필드가 일치할 때만 재사용한다.
- SKU: fixture 상품 ID와 FOUNDATION-004 예약 이름, 가격 `19900.00`, 구독 가능, 표시 순서 `1`이 모두 일치할 때만 재사용한다.
- fixture 값은 local 통합 검증용이며 운영 seed나 제품 정책이 아니다.
- QA email 전체 값과 password는 저장소에 없고 환경 변수로만 전달한다.

## 트랜잭션과 rollback

- `LocalQaBootstrapService.bootstrap` 하나가 transaction 경계다.
- 처리 순서는 credential 검증 → 잠금 회원 조회·생성 → 상품 조회·생성 → SKU 조회·생성 → 선택적 구독 reset이다.
- 회원·상품·SKU 생성 또는 reset 중 예외가 밖으로 전파되면 transaction 전체가 rollback된다.
- 충돌 감지 전에 신규 회원이 flush됐더라도 같은 transaction이므로 부분 회원이 남지 않는다.
- reset JPQL은 `WHERE subscription.member.id = :memberId` 조건만 사용한다.

## 예상 SQL 경계

- 정규화 email 회원 잠금 조회와 필요 시 회원 INSERT
- 예약 상품 이름 후보 조회와 필요 시 상품 INSERT
- 상품 ID·예약 SKU 이름 후보 조회와 필요 시 SKU INSERT
- reset 활성 시 QA 회원 ID 조건의 구독 DELETE
- 일반 상품·SKU UPDATE/DELETE와 다른 회원 구독 DELETE는 실행하지 않는다.

## 보안 메모

- raw password, password hash, session ID와 CSRF token을 로그·응답·문서에 출력하는 코드가 없다.
- bootstrap exception은 입력값을 포함하지 않는 고정된 설명만 사용한다.
- main·test resource에 credential이나 password hash 기본값을 추가하지 않았다.

실제 SQL과 bind credential 값은 로그·보고서에 기록하지 않는다.

## 테스트 구성

- `LocalQaBootstrapConfigurationTests`: 기본·test·production 비실행과 local 이중 활성화 조건
- `LocalQaBootstrapServiceTests`: credential, 반복 재사용, password 충돌, 상품·SKU 모호성·필드 충돌, 제한 reset
- `LocalQaBootstrapIntegrationTests`: MySQL 최초·반복 실행, reset 비활성 보존, 다른 회원·비fixture 데이터 보존, 충돌 rollback
- 기존 Security·인증·상품·구독 테스트와 full build는 Repository Validation에서 회귀 확인

테스트 credential과 hash는 테스트 실행 시 UUID와 기존 `PasswordEncoder`로 생성한다.

## 검증 결과

- 변경 전 `gradlew test --tests ...EmailNormalizerTests --tests ...SubscriptionTests`: 실행 전 실패. 현재 PC에 Java 25 toolchain이 없고 설치된 JDK가 17·21뿐이다.
- datasource 환경 변수 세 개는 현재 셸에 없어 로컬 MySQL 통합 테스트를 실행할 수 없다.
- PR #44 첫 head `72b68d14cf1782db57ade0fa9d1891ab03fac329`의 Repository Validation: 통과
- Repository Validation run `29327298510`: Java 25·MySQL Backend test와 build, Frontend lint와 build, 작업 산출물·PR·commit 규칙 검증 모두 통과
- `scripts/validate-task-artifacts.py --task-id FOUNDATION-004`: 통과
- `git diff --check`: 통과
- 변경 경로 Secret·private key·BCrypt hash 형태 정적 검색: 검출 없음

## 실행하지 못한 검증과 이유

- 로컬 Gradle test/build는 저장소 코드 실패가 아니라 Java 25 toolchain 부재로 compile task 구성 전에 중단됐다.
- MySQL 통합 테스트는 실제 값을 출력하지 않고 환경 변수 존재 여부만 확인했으며 현재 셸에는 datasource 환경이 없다.
- 승인 없는 JDK 설치, build toolchain 변경 또는 임시 DB 구성을 추가하지 않았다.

## 적용·실행 방법

구체적인 로컬 datasource 설정은 `docs/handoffs/FOUNDATION-004/be-to-sre.md`의 환경 변수 계약을 사용하고, Platform/SRE가 같은 작업 ID의 Runbook을 완성한다. Backend bootstrap 설정 계약은 다음과 같다.

```text
SPRING_PROFILES_ACTIVE=local-integration
PAWCYCLE_LOCAL_QA_BOOTSTRAP_ENABLED=true
PAWCYCLE_LOCAL_QA_BOOTSTRAP_EMAIL=<로컬 환경 변수에서 제공>
PAWCYCLE_LOCAL_QA_BOOTSTRAP_PASSWORD=<로컬 환경 변수에서 제공>
PAWCYCLE_LOCAL_QA_BOOTSTRAP_RESET_SUBSCRIPTIONS=false
```

email local-part는 `qa-foundation-004`여야 한다. 빈 구독 상태가 필요할 때만 reset 값을 `true`로 한 번 명시한다.

## API·DB 영향

- API 변경: 없음
- schema·migration 변경: 없음
- 기존 `members`, `products`, `skus`, `subscriptions`와 승인 제약만 사용
- production credential·fixture row: 없음

## 알려진 위험과 제한

- local profile과 bootstrap 활성화는 운영자가 함께 명시해야 하므로 SRE Runbook에서 profile 조합을 그대로 사용해야 한다.
- 여러 Backend 인스턴스를 동시에 시작하는 방식은 권장하지 않는다. 같은 QA 회원은 DB lock으로 직렬화하지만 local 검증은 단일 인스턴스를 기준으로 한다.
- 실제 browser same-origin 구성은 후속 로컬 통합 환경과 QA에서 검증한다.
- 로컬 Java 25와 MySQL 검증은 현재 PC 환경 제약으로 미실행했으며, 같은 검증은 Repository Validation에서 통과했다.

## 다음 작업

1. Platform/SRE가 같은 작업 ID로 datasource, Backend, Frontend를 연결하는 local-only 실행 환경과 Runbook을 작성한다.
2. QA가 Runbook으로 로그인, 공개 상품, 구독 생성·목록·상세와 reset 뒤 빈 상태를 검증한다.
3. Product Owner/Tech Lead가 fixture 경계, transaction과 민감정보 비노출을 직접 검토한다.

## Git 결과

- 최신 `origin/main` `807fc3567e1d3b846a292da101c0d14263649092`에서 새 `feat/be`를 생성했다.
- 이전 로컬·원격 `feat/be` 고유 커밋은 각각 병합된 PR #36·#42에 속함을 확인한 뒤 squash merge 규칙에 따라 삭제했다.
- reset, rebase, force push와 history rewrite를 사용하지 않았다.
- 구현 commit: `72b68d14cf1782db57ade0fa9d1891ab03fac329` (`feat(backend): 로컬 QA bootstrap 구성`)
- `origin/feat/be`로 일반 push하고 upstream 추적을 설정했다.

## PR 상태

- `main` 대상 PR #44 생성 후 Ready for review로 전환: `https://github.com/guseoh/pawcycle-commerce/pull/44`
- 첫 implementation head와 첫 리뷰 반영 head의 Repository Validation 성공을 확인했다. 추가 리뷰 반영을 포함한 최종 head 검증은 대기 중이다.
- 자동 병합하지 않는다.
