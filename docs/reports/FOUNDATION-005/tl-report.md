# FOUNDATION-005 1차 MVP 완료·학습 Tech Lead 보고서

## 작업 정보

- 작업 ID: `FOUNDATION-005`
- 역할: Tech Lead
- 기준선: PR #46 `test(qa): FOUNDATION-004 첫 구독 MVP 검증` 병합 결과가 포함된 `main`
- 기준 증거: PR #46 merge commit `0429f0cceb10cdff1abe802f12a48f351ca4c97f`
- 작업 성격: 첫 수직 MVP 종료 기준과 학습용 추적성 정리
- 최종 판정: **1차 MVP 완료(조건부 QA 위험 수용 기준선)**

이 보고서는 제품 기능이나 계약을 새로 승인하지 않는다. 사용자가 PR #46을 병합하면서 수용한 `조건부 통과`를 첫 사용자 가치 흐름의 완료 기준선으로 설명한다. 미실행 검증은 통과로 바꾸지 않으며, 프로젝트 전체가 완료됐다는 의미로도 확장하지 않는다.

## 승인 입력

- `docs/product/PS-002-first-mvp-requirements.md`
- `docs/product/PS-003-ux-product-decisions.md`
- `docs/domain/DOMAIN-001-first-mvp-subscription-domain.md`의 승인 입력과 Accepted Domain Design
- `docs/adr/ARCH-006-first-backend-implementation-approved-inputs.md`의 지정 승인 범위
- `docs/adr/AUTH-003-session-authentication-approved-inputs.md`의 Approved DR1~DR3
- `docs/api/API-002-public-product-api-contract-proposal.md`의 Approved D1~D7
- `docs/api/API-003-subscription-api-contract-decision-request.md`의 Approved D1~D8
- `docs/reports/FOUNDATION-004/qa-report.md`의 조건부 통과와 PR #46 병합에 따른 사용자 수용

## 1. 1차 MVP 완료 판정

### 완료 기준과 증거

| 완료 기준 | 충족 증거 | 판정 |
| --- | --- | --- |
| 승인된 첫 사용자 가치가 정의됨 | `PS-002`, `PS-003`, `DOMAIN-001`의 승인 입력과 Accepted Domain Design | 충족 |
| 공개 상품과 보호 구독 API 계약이 확정됨 | `API-002` D1~D7, `API-003` D1~D8, `AUTH-003` DR1~DR3 | 충족 |
| Backend 수직 흐름이 구현됨 | 공개 상품, session 인증, 구독 생성·목록·상세, V1·V2 migration과 관련 테스트 | 충족 |
| Frontend 수직 흐름이 구현됨 | 상품·로그인·구독 route, API client, 인증·CSRF 상태, 오류·빈 상태·재시도 | 충족 |
| 실제 실행 환경에서 연결됨 | `pawcycle-local-integration` Compose, Nginx same-origin proxy, QA bootstrap, Full·Preserved·Empty smoke | 충족 |
| 독립 브라우저 QA가 수행됨 | 25건 중 통과 17건, 일부 또는 전체 미실행 8건, 실패 0건, 차단 0건 | 조건부 충족 |
| 중대한 완료 차단 증거가 없음 | 재현 가능한 P0·P1과 승인 계약 위반 없음 | 충족 |
| 사용자가 열린 위험을 확인하고 기준선을 수용함 | 조건부 QA 결과를 담은 PR #46 병합 | 충족 |

### 완료율 100%의 의미

여기서 `100%`는 **PS-002·PS-003가 정의한 첫 수직 MVP 범위의 구현, 통합 환경 구성, 독립 QA 판정과 위험 기록이 모두 완료 단계에 도달했다**는 뜻이다. 브라우저 테스트 25건이 모두 통과했다는 뜻은 아니다. 실제 결과는 통과 17건과 미실행 8건이며, 미실행 8건은 열린 품질 위험으로 남는다.

또한 다음을 뜻하지 않는다.

- PawCycle Commerce 전체 제품의 완료
- 구독 변경·일시정지·재개·해지와 정기 주문 생성의 완료
- 일반 구매, 주문, 결제, 재고, 배송의 완료
- 운영 배포, 관측성, 부하·성능 기준과 장애 대응 체계의 완료
- Proposed 또는 Decision Required 문서 전체의 Approved 전환

### 문서 상태 경계

| 문서 | 이 보고서의 해석 |
| --- | --- |
| `PS-002`, `PS-003` | 승인된 제품 요구사항과 Product Decision |
| `DOMAIN-001` | 승인 입력과 Accepted Domain Design은 기준선이며 Proposed Technical Design은 그대로 Proposed |
| `UX-001` | 원본은 `Proposed UX Design`; PS-003가 해결한 네 제품 결정과 API-003가 지정한 표시 범위만 승인 입력으로 사용 |
| `ARCH-001` | 원본은 `Proposed Architecture Decision`; `ARCH-006`이 지정해 승인한 부분만 구현 입력 |
| `ARCH-006` | `Approved Inputs`와 명시된 Deferred·Decision Required·Excluded 경계 |
| `AUTH-003` | DR1~DR3와 same-origin/reverse proxy 전제가 `Approved` |
| `API-002` | 공개 상품 D1~D7이 `Approved API Contract` |
| `API-003` | 구독 D1~D8이 `Approved API Contract` |
| `DATA-001`, `DATA-002` | 원본 전체는 Proposed; `ARCH-006`의 V1 지정 범위와 `API-003` D7의 V2 물리 범위만 승인 구현 입력 |

병합 사실은 위 원본 문서의 상태를 바꾸지 않는다.

## 2. 사용자 가치 흐름

### 정상 흐름

1. **공개 상품 탐색**
   - `/`는 `/products`로 이동한다.
   - 비회원도 `/products`, `/products/{productId}`와 `GET /api/products`, `GET /api/products/{productId}`를 사용할 수 있다.
   - 상품 상세에서 SKU 가격, 구독 가능 여부와 서버가 제공한 2·4·8주 선택지를 확인한다.
2. **보호 기능 접근**
   - 비회원이 `/subscriptions`, 양의 구독 상세 또는 상품 상세의 구독 생성에 접근하면 `/login`으로 이동한다.
   - `frontend/src/lib/frontend-utils.ts`의 `sanitizeReturnTo`는 승인된 상품·구독 내부 GET 경로만 허용한다.
   - 외부 URL, protocol-relative URL, `/login`, 0·음수·비정상 식별자는 `/products`로 fallback한다.
3. **session 로그인과 현재 회원 식별**
   - Frontend가 필요할 때 `GET /api/auth/csrf`로 token을 얻고 `POST /api/auth/login`을 보낸다.
   - Backend는 credential을 검증하고 session id를 변경한 뒤 최소 `memberId` principal을 SecurityContext에 저장한다.
   - `GET /api/auth/me`가 현재 회원을 확인하고 Header가 회원 상태를 표시한다.
4. **구독 입력**
   - 사용자는 구독 가능한 SKU 하나, 수량 1~10, 서버가 제공한 배송 주기 2·4·8주 중 하나를 선택한다.
   - 생성 전에는 정확한 다음 주문 예정일을 계산하지 않고 생성 후 확인 안내만 표시한다.
5. **CSRF가 필요한 구독 생성**
   - token이 메모리에 없을 때만 지연 획득하고 `X-CSRF-TOKEN`으로 `POST /api/subscriptions`에 전달한다.
   - Backend는 session principal의 `memberId`를 소유자로 사용하며 request body의 회원 식별자를 신뢰하지 않는다.
   - `SubscriptionApplicationService.create`가 SKU 존재·구독 가능 여부를 확인하고 `Subscription.create`가 수량·주기·날짜 불변식을 보호한다.
6. **생성 후 상세 이동**
   - `201 Created`의 `subscriptionId`를 사용해 `/subscriptions/{id}?created=1`로 이동한다.
   - 생성 성공 안내와 서버가 확정한 생성일, 다음 주문 예정일, 현재 SKU 표시 가격을 보여 준다.
7. **내 구독 조회**
   - `GET /api/subscriptions`는 session 회원 소유 구독만 ID 내림차순으로 반환한다.
   - `GET /api/subscriptions/{id}`는 ID와 소유자를 함께 조건으로 조회해 존재하지 않음과 타인 소유를 구분해 노출하지 않는다.
8. **로그아웃**
   - `POST /api/auth/logout`에 CSRF token을 전달한다.
   - Backend가 CSRF, SecurityContext, session과 `JSESSIONID`를 정리하고 Frontend도 회원과 token 메모리 상태를 폐기한다.

### 주요 실패 흐름

- 비공개·미존재·비숫자 상품은 동일한 `404 PRODUCT_NOT_FOUND`로 수렴한다.
- 잘못된 email 또는 password는 회원 존재 여부를 드러내지 않는 `401 INVALID_CREDENTIALS`로 수렴한다.
- 보호 API의 미인증은 redirect나 HTML이 아닌 `401 AUTH_REQUIRED` JSON이다.
- 누락·타입·수량·주기 오류는 `400 VALIDATION_FAILED`와 필드 오류로 반환된다.
- 미존재 SKU는 `404 SKU_NOT_FOUND`, 구독 불가능 SKU는 `409 SKU_NOT_SUBSCRIBABLE`이다.
- 존재하지 않음·타인 소유·비숫자 구독 상세는 동일한 `404 SUBSCRIPTION_NOT_FOUND`다.
- `CSRF_INVALID`이면 Frontend는 기존 token을 폐기하고 새 token을 얻지만 실패한 POST를 자동 재실행하지 않는다.
- 목록·상세 조회 실패는 endpoint별 안전한 오류와 명시적 GET 재시도를 제공한다.
- 생성 처리 중 form과 버튼을 잠가 빠른 반복 입력의 중복 POST를 막는다. timeout 뒤 서버 반영 여부를 확정하는 멱등성 저장소는 아직 없다.

## 3. 요구사항부터 구현까지의 추적성

### 승인 원본에서 실행 결과까지

| 제품 요구사항 | 도메인·UX·계약 | Backend 구현과 테스트 | Frontend 구현과 테스트 | 브라우저 QA |
| --- | --- | --- | --- | --- |
| `REQ-PRODUCT-001` 공개 목록 | `DOMAIN-001` Product·SKU, `UX-SCREEN-001`, API-002 D1·D3~D7 | `ProductController.products`, `ProductQueryService.findProducts`, `ProductRepository.findAllPublicOrderById`, `ProductApiIntegrationTests` | `frontend/src/app/products/page.tsx`, `productApi.list` | QA-F004-001·002 통과, QA-F004-019 일부 미실행 |
| `REQ-PRODUCT-002` 공개 상세·SKU | `UX-SCREEN-002`, API-002 D2~D7 | `ProductController.product`, `ProductQueryService.findProduct`, `SkuRepository`, `ProductQueryServiceTests` | `products/[productId]/page.tsx`, `ProductDetailScreen` | QA-F004-002 통과, 일부 상세 장애 미실행 |
| `REQ-AUTH-001` 보호 경계·로그인 | PS-003 로그인 복귀, AUTH-003 DR1~DR3 | `SecurityConfig`, `AuthController`, `AuthApplicationService`, `AuthIntegrationTests`, `SecurityFoundationIntegrationTests` | `AuthProvider`, `LoginForm`, `sanitizeReturnTo`, `frontend-utils.test.mts` | QA-F004-003·005~007·009 통과; 004·008·010 일부/전체 미실행 |
| `REQ-AUTH-002` 타인 구독 차단 | DOMAIN-001 소유권, API-003 D4 | `SubscriptionRepository.findOwnedWithCatalog`, `SubscriptionApiIntegrationTests.missingOtherOwnedAndNonNumericDetailsReturnIdenticalNotFoundBodies` | `SubscriptionDetailScreen`의 공통 조회 불가 상태 | QA-F004-017 통과, 타인 계정 브라우저 재현은 Backend 테스트로 대체 |
| `REQ-SUB-001` 구독 생성 | DOMAIN-001 불변식, UX-SCREEN-002~004, API-003 D1·D2·D5·D6 | `SubscriptionController.create`, `SubscriptionApplicationService.create`, `Subscription.create`, 관련 API·서비스·도메인 테스트 | `ProductDetailScreen.handleSubmit`, `subscriptionApi.create`, `validateSubscriptionDraft`, CSRF lifecycle 테스트 | QA-F004-011~013 통과, 014 timeout 미실행 |
| `REQ-SUB-002` 내 목록 | API-003 D3·D4 | `findOwnedSubscriptions`, `findAllOwnedWithCatalogOrderByIdDesc`, 목록 query 수 통합 테스트 | `frontend/src/app/subscriptions/page.tsx` | QA-F004-015·018 통과 |
| `REQ-SUB-003` 내 상세 | PS-003 생성 후 상세, API-003 D3~D5 | `findOwnedSubscription`, `findOwnedWithCatalog`, 상세 API 통합 테스트 | `SubscriptionDetailScreen`, `subscriptions/[subscriptionId]/page.tsx` | QA-F004-015·017 통과 |
| `REQ-SUB-004` 다음 주문 예정일 | DOMAIN-001 생성일+주기, PS-003 생성 후 서버값 | `Subscription.create`, `SubscriptionTests.createsNextOrderDateByAddingDeliveryCycleWithoutCalendarAdjustment` | `formatIsoLocalDate`, 생성 전 안내와 상세·목록 표시 | QA-F004-016 통과 |

### 물리 데이터 추적성

- `ARCH-006` A3·A4가 DATA-001·DATA-002 중 `members`, `products`, `skus` 범위와 V1 Flyway 적용을 승인했다.
- API-003 D7이 `subscriptions` 최소 필드·FK·CHECK·`(member_id, id)` 인덱스를 V2 구현 입력으로 승인했다.
- 실제 SQL은 `V1__create_member_and_catalog_foundation.sql`, `V2__create_subscriptions.sql`이다.
- JPA 매핑은 `Member`, `Product`, `Sku`, `Subscription`에 있으며 `spring.jpa.hibernate.ddl-auto=validate`로 Flyway schema와 대조한다.
- `DatabaseFoundationIntegrationTests`와 `SubscriptionDatabaseIntegrationTests`가 MySQL metadata, FK·CHECK·index와 실패 제약을 검증한다.

### 구현 PR 연결

- PR #31: AUTH-003 승인 입력
- PR #32~#34: MySQL CI 기반, JPA·Flyway·Security 기반, session 인증 API
- PR #35~#38: 공개 상품 계약·구현·독립 상품/인증 QA
- PR #39와 #42: 구독 API 승인 계약과 Backend 구현
- PR #43: 첫 구독 Frontend
- PR #44~#45: QA bootstrap과 local-integration
- PR #46: 실제 브라우저 QA와 조건부 완료 기준선

## 4. Backend 구조

### 공개 상품 조회

`ProductController`는 문자열 path를 숫자로 정제하고 `ProductQueryService`에 위임한다. Service의 목록·상세 메서드는 `@Transactional(readOnly = true)`다. `ProductRepository`는 MySQL collation과 무관하게 정확한 대문자 `PUBLIC`만 공개하도록 `BINARY p.display_status = 'PUBLIC'`을 사용한다. 목록은 Product 한 번과 SKU batch 한 번, 상세는 Product와 정렬된 SKU 조회로 DTO를 조립하며 Entity를 응답에 직접 노출하지 않는다.

`ProductExceptionHandler`는 비공개·미존재를 같은 404로 처리하고 예상하지 못한 목록·상세 실패를 각각 `PRODUCT_LIST_UNAVAILABLE`, `PRODUCT_DETAIL_UNAVAILABLE`로 안전하게 변환한다.

### session 인증과 Spring Security 경계

- 공개: 상품 GET, CSRF GET, login POST
- 보호: logout POST, 현재 회원 GET, 모든 구독 API
- 그 밖의 `/api/**`: 인증 필요
- 그 밖의 요청: deny

`SecurityConfig`는 `HttpSessionCsrfTokenRepository`, `HttpSessionSecurityContextRepository`, session fixation 방어와 BCrypt를 구성한다. `ApiAuthenticationEntryPoint`는 401 `AUTH_REQUIRED`, `ApiAccessDeniedHandler`는 CSRF 예외를 403 `CSRF_INVALID`, 그 밖의 인가 실패를 `ACCESS_DENIED`로 JSON 응답한다.

`AuthApplicationService.login`은 email을 정규화하고 존재하지 않는 계정에도 재사용 dummy hash로 BCrypt 비교를 수행한다. 성공 시 `AuthenticatedMemberPrincipal(memberId)`만 session SecurityContext에 저장한다. `logout`은 CSRF token, SecurityContext, session과 cookie를 정리한다.

### 구독 생성·조회와 트랜잭션

- `create`: 쓰기 트랜잭션. SKU 조회 → 구독 가능 검사 → principal 회원 reference → Asia/Seoul 생성일 → 도메인 생성 → 저장 순서다. 예상하지 못한 예외는 `SubscriptionCreateFailedException`으로 바뀌고 트랜잭션은 rollback된다.
- `findOwnedSubscriptions`: read-only 트랜잭션. 회원 조건과 SKU·Product fetch join, ID 내림차순을 한 query로 수행한다.
- `findOwnedSubscription`: read-only 트랜잭션. 구독 ID와 회원 ID를 함께 조건으로 조회한다.

`Subscription`은 소유자·SKU 필수, 수량 1~10, 배송 주기 2·4·8, `nextOrderDate = createdDate + deliveryCycleWeeks`를 생성 시점에 보장한다. DB는 수량·주기·날짜 순서를 보조하지만 주 단위 정확한 날짜 계산은 도메인이 보호한다. `SubscriptionDatabaseIntegrationTests.databaseAllowsLaterDateThatDoesNotMatchDeliveryCycle`은 DB의 의도된 한계를 명시해 도메인 검증을 생략하지 못하게 한다.

### 오류 응답 계약

모든 API 오류는 `ApiErrorResponse(code, message, fieldErrors)`를 사용한다. validation은 실제 필드명을 보존하고, 비필드 오류는 빈 배열을 사용한다. 응답에는 내부 exception message, SQL·schema 정보, stack trace, 소유자와 credential을 포함하지 않는다.

### 핵심 Backend 테스트

- 상품: `ProductQueryServiceTests`, `ProductControllerTests`, `ProductApiIntegrationTests`
- 인증·Security: `AuthApplicationServiceTests`, `AuthIntegrationTests`, `SecurityFoundationIntegrationTests`, `SessionCookieConfigurationTests`
- 구독: `SubscriptionTests`, `SubscriptionApplicationServiceTests`, `SubscriptionExceptionHandlerTests`, `SubscriptionApiIntegrationTests`
- DB: `DatabaseFoundationIntegrationTests`, `SubscriptionDatabaseIntegrationTests`
- bootstrap: `LocalQaBootstrapConfigurationTests`, `LocalQaBootstrapServiceTests`, `LocalQaBootstrapIntegrationTests`

## 5. Frontend 구조

### route와 공개·보호 화면

| Route | 화면 | 경계 |
| --- | --- | --- |
| `/` | `/products` redirect | 공개 |
| `/products` | 상품 목록 | 공개 |
| `/products/[productId]` | 상품 상세와 구독 입력 | 조회 공개, 생성 보호 |
| `/login` | session 로그인 | 공개 |
| `/subscriptions` | 내 구독 목록 | 보호 |
| `/subscriptions/[subscriptionId]` | 내 구독 상세 | 보호 |

`AuthProvider`가 최초 `GET /api/auth/me`로 회원 상태를 확인하고 보호 화면은 anonymous이면 안전한 login href로 이동한다. 현재 회원 확인 자체가 일시 실패하면 로그인으로 단정하지 않고 별도 오류·재시도를 제공한다.

### API·CSRF와 복귀 경계

- `frontend/src/lib/api.ts`: 상대 `/api/**`, `credentials: "same-origin"`, `cache: "no-store"`, 공통 `ApiError`
- `frontend/src/lib/frontend-utils.ts`: 승인된 내부 상품·구독 GET route allowlist와 `/products` fallback
- `frontend/src/lib/csrf-lifecycle.ts`: token 지연 획득, 성공 login 후 회전 token 재획득, `CSRF_INVALID` 시 token 교체, 원 POST 비재실행
- `frontend/src/lib/auth-context.tsx`: member/token 상태를 한 경계에서 폐기하고 오래된 비동기 auth 응답을 generation으로 무시

### 화면 상태와 입력 안전

- `ProductsPage`, `ProductDetailScreen`: loading, empty/not-found, 오류와 GET 재시도
- `SubscriptionsPage`, `SubscriptionDetailScreen`: 인증 확인, empty, 공통 소유권 비노출 상태, 오류와 GET 재시도
- `ProductDetailScreen`: SKU·수량·주기 client validation, 서버 field error 매핑, 오류 요약 focus, 제출 중 전체 입력 잠금
- 생성 전에는 SKU 표시 가격만 보여 주고 합계나 확정 다음 주문일을 계산하지 않는다.
- 생성 후에는 서버 응답 ID로 상세 이동하고 API가 반환한 현재 가격·날짜를 표시한다.
- `AppHeader`: 현재 회원, 로그아웃 처리 상태와 session 만료·CSRF·일반 실패 안내

### 반응형·접근성 구현과 테스트

시맨틱 `form`, `fieldset`, `legend`, label, `aria-describedby`, `aria-invalid`, `role=alert/status`, skip link와 `:focus-visible`이 구현됐다. QA는 390×844와 1280×800에서 핵심 정보 유지와 수평 overflow 없음을 확인했다. 다만 keyboard-only 전체 순회는 도구 제약으로 미실행이므로 구현 존재와 실제 사용성 검증을 구분한다.

Frontend 순수 로직 테스트는 다음 12개다.

- `frontend-utils.test.mts`: returnTo allowlist, 날짜 표시, 구독 입력 경계
- `csrf-lifecycle.test.mts`: 익명 전환, token 획득·폐기, POST 1회, CSRF 실패 비재실행
- `logout-feedback.test.mts`: session 만료, token 갱신 실패, CSRF 실패, 일반 실패 안내

## 6. 데이터와 실행 환경

### 테이블 관계

```text
members (1) ──< subscriptions >── (1) skus >── (1) products
                               각 subscription은 회원 1명과 SKU 1개를 참조
                               한 product는 여러 SKU를 제공
```

| 테이블 | 핵심 제약·index | 목적 |
| --- | --- | --- |
| `members` | PK `id`, unique `email`; email은 ASCII binary | session principal의 회원 식별과 중복 email 차단 |
| `products` | PK `id`, `display_status` 필수 | 공개 상품 공통 정보와 정확한 `PUBLIC` filter |
| `skus` | PK, Product FK, `price >= 0`, `(product_id, display_order, id)` index | 상품별 안정적 SKU 순서, 가격과 구독 가능 여부 |
| `subscriptions` | PK, Member·SKU FK, 수량·주기·날짜 CHECK, `(member_id, id)` index | 단일 SKU 구독, 본인 최신순 조회와 무결성 보조 |

동일 회원의 동일 SKU·수량·주기 조합에는 unique 제약이 없다. API-003 D6에 따라 요청 한 번당 한 건을 만들되 동일 조건 복수 구독은 허용한다.

### Flyway와 JPA

1. V1: `members`, `products`, `skus`, Product-SKU FK와 SKU index
2. V2: `subscriptions`, 회원·SKU FK, 수량·주기·날짜 CHECK와 회원 조회 index

애플리케이션은 Flyway migration 후 Hibernate `ddl-auto=validate`를 사용하고 JPA가 schema를 생성하지 않는다. `open-in-view=false`라서 Service 트랜잭션 안에서 필요한 관계를 조회·DTO로 변환한다.

### QA bootstrap 안전 경계

`LocalQaBootstrapConfiguration`은 `local-integration & !test & !production & !prod` profile과 명시적 `enabled=true`가 모두 필요하다. `LocalQaBootstrapService.bootstrap`은 하나의 트랜잭션에서 credential 검증, 예약 QA 회원·상품·SKU 확인/생성, 선택적 reset을 수행한다.

- 실제 password는 실행 환경에서 받아 BCrypt hash만 저장한다.
- 기존 fixture가 정확히 일치하지 않거나 모호하면 덮어쓰지 않고 시작을 실패시킨다.
- `reset=true`는 해석된 QA 회원 ID의 구독만 삭제한다.
- 최초 빈 DB의 여러 Backend 동시 bootstrap은 지원하지 않으며 Backend 한 인스턴스가 전제다.

### local-integration

- MySQL `8.4.10`, charset `utf8mb4`, collation `utf8mb4_0900_ai_ci`
- named volume `pawcycle-local-integration-mysql-data`
- Java 25 Backend, Node.js 24/Next.js Frontend, Nginx reverse proxy
- 외부 단일 origin의 `/api/**`는 Backend, 나머지 route는 Frontend로 전달
- 요청 host와 port는 `$http_host`로 두 upstream에 전달하며 내부 Nginx port를 별도 forwarded port로 강제하지 않음
- Nginx read timeout은 60초

일반 `stop/start` 또는 `down/up`은 named volume을 보존한다. `down --volumes`만 전체 삭제이며 명시 승인 없이 사용하지 않는다. reset은 한 번 `true`로 Backend·proxy를 재생성하고 Empty를 확인한 뒤 즉시 `false`로 복원해 다시 재생성한다.

실제 DB·QA credential은 ignored `.env.local`과 현재 프로세스 환경에만 둔다. 저장소, PR, 로그와 화면 캡처에 실제 값, session ID 또는 CSRF token을 남기지 않는다.

## 7. 검증 결과

### CI 검증 범위

`.github/workflows/validate-conventions.yml`의 Repository Validation은 다음을 수행한다.

- PR·commit 규칙, PR 본문 UTF-8, task artifact, whitespace
- MySQL 8.4 service의 실제 version·charset·collation 확인
- Java 25 Backend `test`와 `build`
- Flyway·JPA·Security·상품·인증·구독·bootstrap 테스트
- Node.js 24 Frontend `npm ci`, lint와 production build

관련 구현 PR의 최종 Repository Validation은 성공했다. 특정 과거 run 번호를 현재 상태처럼 복제하지 않으며 각 PR check가 권위 원본이다.

### SRE smoke와 local-integration

- 공식 image pull과 Backend·Frontend image build 통과
- Compose config와 Nginx 문법 통과
- `mysql`, `backend`, `frontend`, `proxy` healthy
- Full smoke: Frontend → 공개 상품 → CSRF → 로그인 → 현재 회원 → 생성 → 목록 → 상세 → 로그아웃 통과
- Preserved smoke: 일반 재시작 뒤 구독과 fixture, MySQL volume 보존 통과
- Empty smoke: reset true 뒤 QA 구독 빈 상태, false 복원 뒤에도 빈 상태 통과
- MySQL 8.4.10, utf8mb4, utf8mb4_0900_ai_ci 직접 확인
- 최종 reset=false와 Backend 단일 인스턴스 확인

### 실제 브라우저 QA

- 통과: 17건
- 일부 또는 전체 미실행: 8건
- 실패: 0건
- 차단: 0건
- 재현 가능한 P0·P1 또는 승인 계약 위반: 없음

핵심 공개 탐색 → 안전한 로그인 복귀 → session·CSRF → 구독 생성 → 최신순 목록·상세 → 로그아웃은 실제 same-origin 브라우저에서 통과했다. 수량 0·11은 POST 전에 차단됐고 빠른 더블클릭은 생성 POST 1건으로 제한됐다. reset=false 일반 재시작과 Docker engine 재기동 뒤 QA 구독 보존도 확인했다.

### 대체 증거와 실제 미재현 구분

| 항목 | 대체 증거 | 브라우저 상태 |
| --- | --- | --- |
| 음수·비정상 returnTo | `frontend-utils.test.mts` allowlist | 개별 조합 미실행 |
| session 만료 logout | `logout-feedback.test.mts`, Backend 인증 테스트 | 미실행 |
| CSRF_INVALID | `csrf-lifecycle.test.mts`, Security·Auth 통합 테스트 | token 변조 없이 미실행 |
| 타인 소유 상세 | `SubscriptionApiIntegrationTests` | 별도 타인 계정 미생성 |
| 일부 GET 장애 | 상품 목록 장애·복구 확인 | 목록·상세 전체 조합 미실행 |
| 비QA reset 보존 | `LocalQaBootstrapIntegrationTests` | 비QA 대조군 미생성 |

### 이번 Tech Lead 작업에서 실행한 검증

- root·origin·branch·작업 트리와 PR #46 병합 상태 확인: 통과
- 최신 원격 fetch와 latest `main` fast-forward: 통과
- 기존 `ops/tl`의 병합 PR·열린 PR·로컬/원격 tip 확인 후 역할 브랜치 재생성: 통과
- 승인 원본 상태와 실제 path·class·function·endpoint·migration·test 이름 대조: 통과
- 전체 Backend test, Frontend build, Compose smoke와 브라우저 QA는 제품·설정 변경이 없는 문서 작업이고 기존 성공/미실행 경계를 유지해야 하므로 반복하지 않음

문서 validator와 정적 검사 결과는 이 보고서의 최종 검증 절에 기록한다.

### 실행하지 못한 검증과 이유

- 전체 Backend test·build: 제품 코드, migration과 build 설정을 변경하지 않은 문서 정리이며 관련 Java 25·MySQL 검증은 병합된 구현·QA PR의 성공 이력을 사용한다.
- Frontend lint·build와 실제 브라우저 QA: Frontend를 변경하지 않았고 QA의 미실행 8건을 이번 Tech Lead 문서 작업에서 임의로 통과시키지 않기 위해 반복하지 않는다.
- Compose image build·Full·Preserved·Empty smoke: 인프라 설정을 변경하지 않았고 기존 성공 결과가 유효하며 반복 실행은 reset과 데이터 상태를 불필요하게 바꿀 수 있어 실행하지 않는다.
- 성능·부하 테스트: 승인 범위 밖이며 기준 조건과 성능 목표가 아직 결정되지 않았다.

## 8. 남은 위험과 제한

| 열린 위험 | 현재 영향 | 재검증 방법 |
| --- | --- | --- |
| keyboard-only 전체 흐름 | 시맨틱 구현은 있으나 실제 순회 증거 없음 | 실제 키보드로 skip link, 상품, SKU, 수량, 주기, 제출, 오류 focus, 재시도 순회 |
| session 만료 상태의 logout | 만료 안내·fallback은 단위 증거에 의존 | 승인된 방식으로 session만 만료한 뒤 로그아웃 UI·요청 횟수 확인 |
| 브라우저 `CSRF_INVALID` | token 폐기·수동 재시도는 단위/Backend 증거에 의존 | 민감 token을 기록하지 않는 전용 test hook 또는 격리 proxy로 403 주입 |
| 생성 POST timeout | 사용자 재시도 때 이미 생성됐는지 모를 수 있음 | 승인된 fault injection과 DB/API 대조로 POST 횟수·생성 건수 확인; 멱등성 정책은 별도 결정 |
| 음수·비정상 ID returnTo | 단위 allowlist는 통과, 실제 login redirect 조합 일부 미실행 | 각 입력으로 로그인 후 `/products` fallback과 외부 이동 0건 확인 |
| 일부 GET 장애 | 상품 목록 장애만 대표 확인 | 상품 상세·구독 목록·상세별 안전 오류, retry, 복구를 각각 확인 |
| 비QA 데이터 reset 보존 | Backend 통합 테스트 증거만 있고 실제 대조군 없음 | 승인된 비QA fixture의 pre/post hash 또는 row count를 비민감하게 비교 |
| 타 프로젝트 volume·data 보존 | 다른 컨테이너 동일 ID만 확인 | 별도 프로젝트 소유자 승인 아래 volume identity와 비민감 data checksum 비교 |
| Nginx 60초 timeout | Backend 단절 오류 UI가 약 60초 뒤 표시 | timeout 경계 측정 후 UX/운영 요구를 별도 결정; 근거 없이 값 변경 금지 |
| 운영 배포·관측성·성능 | local-only 성공이 운영 준비를 보장하지 않음 | 배포 환경 결정, SLI·로그·metric·trace 기준, 기준 부하와 rollback 검증을 별도 작업화 |

이 항목들은 현재 재현된 P0·P1이 아니다. 다만 사용자 경험, 중복 생성, 데이터 보존 또는 운영 판단에 영향을 줄 수 있으므로 후속 검증 전까지 열린 위험으로 유지한다.

## 9. 핵심 설계 선택과 이유

| 선택 | 이유 | 상태 근거 |
| --- | --- | --- |
| JWT 대신 server-side session | 첫 브라우저 same-origin 흐름에서 credential·인증 상태를 서버가 관리하고 logout·CSRF 경계를 명확히 함 | AUTH-003 Approved, JWT Deferred |
| 상품 공개, 구독 보호 | 가입 전 탐색 가치를 열고 개인 구독과 생성은 회원 소유권으로 보호 | PS-003 Approved Product Decision |
| same-origin Nginx proxy | cookie·CSRF를 cross-origin CORS 예외 없이 한 origin에서 연결 | AUTH-003 Approved 전제, 구현은 FOUNDATION-004 |
| 서버 확정 날짜·가격 표시 | Frontend 시간대·달력 계산과 가격 snapshot 추측을 피함 | PS-003·DOMAIN-001·API-003 Approved |
| 이전 POST 자동 재실행 금지 | 로그인·CSRF 복구 뒤 중복 구독 생성 위험 회피 | PS-003·AUTH-003·API-003 Approved |
| 구독 하나당 SKU 하나 | 첫 수직 흐름의 도메인·데이터·UI 범위를 작게 유지 | PS-002·DOMAIN-001 Approved Input |
| 수량 1~10, 주기 2·4·8 | 제품 규칙을 API, 도메인과 DB CHECK에서 중복 방어 | PS-002·DOMAIN-001 Approved Input |
| Backend 단일 인스턴스 bootstrap | local-only fixture를 충돌 시 실패하는 단순 트랜잭션으로 안전하게 제공 | FOUNDATION-004 내부 운영 선택과 명시적 제한 |
| Redis·메시징·MSA 미도입 | 현재 사용자 가치와 병목 측정 없이 운영 복잡성을 늘리지 않음 | 저장소 측정 우선 원칙; 향후 선택은 미승인 |

앞 여섯 선택은 승인 문서가 직접 근거다. bootstrap의 구체 구현과 package, fetch query, React state 구성은 승인 범위를 만족하기 위한 내부 구현 선택이다. Redis·메시징·MSA를 사용하지 않은 것은 영구 금지 결정이 아니라 현재 근거가 없다는 범위 통제다.

## 10. 사용자 학습 확인

### 코드를 읽는 추천 순서

1. `docs/product/PS-002-first-mvp-requirements.md`
2. `docs/product/PS-003-ux-product-decisions.md`
3. `docs/domain/DOMAIN-001-first-mvp-subscription-domain.md`
4. `docs/adr/AUTH-003-session-authentication-approved-inputs.md`
5. `docs/api/API-002-public-product-api-contract-proposal.md`, `docs/api/API-003-subscription-api-contract-decision-request.md`
6. V1·V2 migration과 네 JPA Entity
7. `SecurityConfig`, `AuthController`, `AuthApplicationService`
8. `ProductController`/`ProductQueryService`, `SubscriptionController`/`SubscriptionApplicationService`/`SubscriptionRepository`
9. `frontend/src/lib/api.ts`, `auth-context.tsx`, `csrf-lifecycle.ts`, `frontend-utils.ts`
10. 상품·로그인·구독 page/component
11. Backend 통합 테스트와 Frontend 순수 로직 테스트
12. FOUNDATION-004 Runbook, smoke, QA 계획과 QA 보고서

### 구독 생성 요청 한 건의 추적

```text
ProductDetailScreen.handleSubmit
→ validateSubscriptionDraft
→ AuthProvider.executeWithCsrf
→ GET /api/auth/csrf (메모리 token이 없을 때만)
→ subscriptionApi.create POST /api/subscriptions
→ Nginx location /api/
→ SecurityFilterChain: session principal + CSRF 검사
→ SubscriptionController.create
→ SubscriptionApplicationService.create @Transactional
→ SkuRepository.findById + 구독 가능 검사
→ MemberRepository.getReferenceById
→ Subscription.create: 수량·주기·날짜 불변식
→ SubscriptionRepository.save
→ MySQL subscriptions INSERT
→ 201 {subscriptionId, nextOrderDate}
→ /subscriptions/{id}?created=1
→ GET 상세: 소유자 조건 fetch join
→ 서버 확정 가격·날짜 표시
```

실패 시 확인 순서는 브라우저 오류와 요청 횟수 → Nginx proxy path/status → Security code → Controller validation → Service 예외 mapping → transaction rollback → DB row 유무다. credential, cookie와 CSRF token 원문은 확인 자료로 복사하지 않는다.

### 직접 확인할 테스트

- 정상·오류·query 수: `ProductApiIntegrationTests`, `SubscriptionApiIntegrationTests`
- session·CSRF: `AuthIntegrationTests`, `SecurityFoundationIntegrationTests`
- 도메인 불변식: `SubscriptionTests`
- 트랜잭션·DTO mapping: `SubscriptionApplicationServiceTests`
- schema 경계: `DatabaseFoundationIntegrationTests`, `SubscriptionDatabaseIntegrationTests`
- reset 안전: `LocalQaBootstrapIntegrationTests`
- returnTo·날짜·입력: `frontend-utils.test.mts`
- CSRF 비재실행: `csrf-lifecycle.test.mts`
- 실제 사용자 흐름: `docs/qa/FOUNDATION-004/first-mvp-browser-test-plan.md`

### 이해 점검 질문

답은 이 보고서에 바로 적지 않는다. 각 질문의 파일과 관점을 따라 직접 설명할 수 있는지 확인한다.

1. 공개 상품 GET과 보호 구독 API를 나누는 최종 Security 규칙은 무엇인가? — `SecurityConfig`, AUTH-003의 인증·CSRF 관점
2. 로그인 성공 때 session id와 CSRF token은 어떤 순서로 바뀌는가? — `AuthApplicationService`, `SessionAuthenticationStrategy`, `AuthIntegrationTests`
3. 왜 `returnTo=/login`, 외부 URL과 음수 ID가 상품 목록으로 가는가? — `frontend-utils.ts`, PS-003의 Open Redirect 관점
4. 구독 소유자는 왜 request body가 아니라 principal에서 오는가? — `SubscriptionController`, API-003 D4의 신뢰 경계
5. 수량과 배송 주기는 Frontend, API, 도메인, DB에서 각각 무엇을 보장하는가? — `ProductDetailScreen`, `SubscriptionCreateRequest`, `Subscription`, V2 migration
6. 다음 주문 예정일이 정확히 배송 주기와 맞는다는 조건을 DB CHECK만으로 충분히 보장하지 않는 이유는 무엇인가? — `Subscription`, `SubscriptionDatabaseIntegrationTests`
7. 목록·상세 조회가 N+1과 타인 데이터 노출을 어떻게 피하는가? — `SubscriptionRepository`, Hibernate query-count 통합 테스트
8. `CSRF_INVALID` 뒤 token은 갱신하면서 POST를 재실행하지 않는 이유는 무엇인가? — `csrf-lifecycle.ts`, AUTH-003와 API-003의 중복 생성 관점
9. 동일 조건 구독을 여러 건 허용하면서 빠른 더블클릭은 어떻게 한 건으로 제한하는가? — API-003 D6, `ProductDetailScreen`, QA-F004-013·014
10. reset이 QA 회원 외 데이터를 지우지 않는다는 보장은 어디까지 자동 테스트됐고 어디부터 브라우저 미실행인가? — `LocalQaBootstrapService`, 통합 테스트, QA-F004-024·025

## 11. 후속 작업 후보

| 후보 | 사용자 가치 | 선행 결정 | 주 역할 | 주요 위험·검증 경계 |
| --- | --- | --- | --- | --- |
| QA·접근성 보강 | 현재 MVP를 더 신뢰하고 다양한 입력 방식으로 사용 | keyboard 기준, 안전한 fault injection, 대조 fixture 승인 | QA, FE, BE, SRE | 실제 keyboard-only, session 만료, CSRF_INVALID, timeout, GET 장애, reset 대조를 기존 계약 변경 없이 검증 |
| 관측성·기준 성능 측정 | 느림·오류·중복의 원인을 수치와 로그로 판단 | SLI/SLO 후보, 개인정보·로그 redaction, 부하 조건, 보존 기간 | SRE, TL, BE | 먼저 baseline; metric을 좋게 만들기 위한 제품 변경 금지, 60초 proxy 경계 포함 |
| 구독 생명주기 관리 | 사용자가 주기·상품·수량을 변경하고 일시정지·재개·해지 | 상태 전이, 효력 시점, 다음 주문일 재계산, 멱등성·동시성 Product/Technical Decision | PO, UX, BE, FE, QA | 상태 전이·인가·감사·동시 요청, 기존 활성 구독 migration과 회귀 |
| 일반 구매와 주문 | 구독 외 일회성 구매로 커머스 가치 확대 | 장바구니/즉시구매, 가격 snapshot, 주문 상태와 취소 정책 | PO, Domain/BE, UX/FE, QA | 금액·snapshot·transaction·중복 주문·개인정보 경계 |
| 가상 결제와 재고 | 주문의 결제·재고 결과를 끝까지 학습 | 결제 실패·재시도·멱등성, 재고 예약·차감·복구, 외부 연동 모사 범위 | PO, TL, BE, SRE, QA | 중복 결제, oversell, 보상·재시도, Secret, 장애 주입과 감사 증거 |

추천 순서는 다음과 같다.

1. QA·접근성의 열린 위험을 작은 재검증으로 닫기
2. 관측성과 기준 성능을 먼저 측정해 다음 복잡성의 비교 기준 만들기
3. 현재 핵심 도메인을 확장하는 구독 생명주기 관리
4. 일반 구매와 주문
5. 가상 결제와 재고

이 순서는 현재 코드의 학습 연속성과 위험 축소를 기준으로 한 추천일 뿐이다. 어떤 후보도 Approved가 아니며 제품 가치, 학습 목표와 시간 우선순위는 사용자가 최종 결정한다.

## 변경 범위와 비범위

### 변경 범위

- 이 Tech Lead 보고서 한 파일
- 승인 원본, 실제 코드·테스트·실행 결과의 추적성 정리
- 완료 의미, 열린 위험, 학습 순서와 후속 후보 제안

### 변경하지 않은 범위

- Backend·Frontend 제품 코드와 테스트
- API, 인증·CSRF·CORS 계약
- Flyway migration, JPA mapping과 DB schema
- Docker Compose, Nginx, CI와 Runbook
- Proposed·Decision Required·Deferred 문서 상태
- QA 결과와 미실행 항목
- 다음 기능의 최종 우선순위
- 형식적인 다음 역할 인수인계

## 인수인계 생략

- 이번 작업은 첫 수직 MVP의 종료·학습 기준선을 정리하며 다음 실행 역할이 확정되지 않았다.
- 후속 후보와 추천 순서는 제안일 뿐 사용자가 우선순위를 승인하기 전 특정 역할에 실행 책임을 넘기지 않는다.
- 따라서 내용이 중복되는 형식적 `docs/handoffs/FOUNDATION-005` 문서를 만들지 않고 이 보고서에 생략 이유를 명시한다.

## 최종 정적 검증

- FOUNDATION-005 task artifact validator: 최초 실행에서 보고서 heading과 handoff 필수 요구로 실패. heading을 보완하고 사용자 승인에 따라 명시적 인수인계 생략을 허용하는 validator와 회귀 테스트를 추가한 뒤 통과
- validator Python compile과 단위 테스트: 최초 16 tests 통과. 리뷰에서 확인된 혼합 생략 절, 보고서별 생략 선언, 부정형 생략 답변의 우회 가능성을 차례로 보완한 뒤 19 tests 통과
- commit 제목 validator: `docs(tl): FOUNDATION-005 첫 MVP 완료 기준 정리` 통과
- whitespace 검사: 통과
- 실제 경로·class·function·endpoint·migration·test 이름 대조: 통과
- Secret·credential·cookie·CSRF token·개인 로컬 경로·임시 파일 점검: 통과

## Git 결과

- 역할 브랜치: `ops/tl`
- 최신 `main`과 PR #46 병합을 확인하고 오래된 병합 완료 역할 branch ref를 정리한 뒤 새 `ops/tl`을 준비했다.
- 명시적 인수인계 생략을 허용하는 validator 변경은 사용자 추가 승인을 근거로 같은 집중 commit에 포함한다.
- 첫 게시 commit `655ec782727f2c925f916447c5ab0372118db408`과 CodeRabbit 검토 후속 수정을 `origin/ops/tl`에 push했다.
- 최종 head와 commit 이력의 권위 원본은 Git과 GitHub다.

## PR 결과

- 대상 브랜치: `main`
- PR #47 `docs(tl): FOUNDATION-005 첫 MVP 완료 기준 정리`를 Draft가 아닌 검토 가능 상태로 생성했다.
- CodeRabbit과 Codex Review의 validator 지적은 유효한 기능 결함으로 분류해 모든 생략 절과 보고서별 선언을 검사하고 부정형 답변을 거부하는 최소 수정·회귀 테스트에 반영했다.
- 현재 head, CI, mergeable, Draft·Ready와 review 상태는 GitHub를 권위 원본으로 확인한다.
- 자동 병합하지 않으며 최종 병합과 후속 우선순위는 사용자가 결정한다.
