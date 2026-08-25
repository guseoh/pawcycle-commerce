# MVP4-QA-001 테스트 계획

## 작업 기준과 범위

- 작업 ID: `MVP4-QA-001`
- 등급: 일반
- 역할: QA Engineer
- 기준 ref: 최신 `main` (`236156b`, PR #218~#221 병합 완료 확인)
- 실행 구분: 저장소 변경 및 로컬 QA 실행만 수행. Production·Cloud·운영 DB·Secret·실제 Provider 실행은 하지 않는다.
- 목적: PR #218~#221로 완성된 MVP4 사용자 흐름을 기존 `infra/local-integration` 단일 origin에서 독립 검증하고, 관찰 결과와 미실행 경계를 재현 가능한 증거로 남긴다.
- 제품 코드, API 계약, DB schema, fixture 원본, dependency는 변경하지 않는다.

현재 canonical Frontend route는 `/subscriptions`, `/subscriptions/new`, `/subscriptions/{id}`이며, 구현 내부 component 이름이 `Mvp2*`여도 이 QA의 신규 사용자 Journey는 `/mvp2/**`로 판정하지 않는다. Backend V2 API는 `/api/v2/**`, 추천은 `/api/recommendations/products`, 일반 상품 탐색은 `/api/products`를 사용한다.

## 승인된 입력

1. 사용자 승인 범위와 MVP4 최소 테스트 범위
2. `docs/api/API-008-mvp4-subscription-self-service-api-contract.md`
3. `docs/api/API-009-mvp4-recommendation-and-product-discovery-api.md`
4. `docs/runbook/FOUNDATION-004-local-integration.md`
5. `docs/runbook/lean-harness.md`
6. `docs/qa/README.md`, `qa/AGENTS.md`, `docs/roles/qa-engineer.md`
7. 최신 `main`의 PR #218~#221 최종 병합 결과와 현재 Frontend/Backend 구현
8. 기존 `infra/local-integration/smoke.ps1` 및 저장소에 이미 포함된 Backend/Frontend 검증 명령

환경 credential은 `infra/local-integration/.env.local`에서만 사용하고 값은 명령, 로그, 문서, PR에 기록하지 않는다. `PAWCYCLE_LOCAL_QA_BOOTSTRAP_RESET_SUBSCRIPTIONS`는 평상시 `false`를 유지하며, reset이 필요해도 승인된 QA 회원의 fixture 범위 밖 데이터를 삭제하지 않는다.

## 인수 조건 매핑

| AC | 인수 조건 | 검증 케이스 |
| --- | --- | --- |
| AC-01 | Compose 기준선과 서비스 health가 확인되고 기존 smoke·Repository Validation·Frontend 검증 상태가 구분 기록된다. | ENV-01~04 |
| AC-02 | Guest는 공개 Home/상품 탐색을 사용할 수 있고 검색·필터·상세가 동작한다. 보호 화면은 로그인으로 유도한다. | ANON-01~04 |
| AC-03 | login `returnTo`와 logout 후 auth/member 화면 상태가 안전하게 정리된다. | AUTH-01~03 |
| AC-04 | 인증 회원이 Pet을 만들고 Home selector에서 선택해 추천 상품명·category·reason을 확인한다. AI/fallback 내부 상태는 UI에 노출되지 않는다. | REC-01~04 |
| AC-05 | Wishlist와 Cart의 추가·조회·삭제, 수량 12 적용, 잘못된 수량 validation이 사용자별로 동작한다. | SHOP-01~07 |
| AC-06 | 배송지 CRUD/default, Cart 배송지 선택, 결제 전 주문 생성 상태를 확인한다. Toss confirm은 실행하지 않는다. | CHK-01~06 |
| AC-07 | 생성 주문/availableActions 및 알림 목록·읽음·재조회를 확인하되 사전 조건이 없으면 미실행으로 남긴다. | ORD-01~04, NOTI-01~03 |
| AC-08 | `/subscriptions/new`에서 Pet·호환 Plan·서버 허용 cycle을 선택해 생성하고 `/subscriptions/{id}`로 이동한다. `/mvp2/**`로 새지 않는다. | SUB-01~06 |
| AC-09 | Server `availableActions`가 허용한 self-service만 ETag/If-Match·Idempotency-Key와 함께 실행하고 최신 상태를 확인한다. | CMD-01~08 |
| AC-10 | Plan/cycle 변경 후 pendingChange의 적용일·가격·주기·상품 구성이 nextDelivery와 구분된다. | PEND-01~03 |
| AC-11 | 승인 fixture/API로 재현 가능한 issue 복구 경로와 Billing configured/registered/prepare를 확인한다. Provider credential·authKey complete는 미실행이다. | ISSUE-01~04, BILL-01~04 |
| AC-12 | Desktop·좁은 Desktop/Tablet·320px Mobile에서 주요 반응형/접근성 동작을 관찰하고 GUI를 관찰하지 못한 항목은 통과시키지 않는다. | UI-01~08 |

## 환경 기준선

| ID | 절차 | 기대 결과 | 증거 방법 |
| --- | --- | --- | --- |
| ENV-01 | `docker version`, `docker compose version`을 확인하고 `infra/local-integration`에서 `docker compose --env-file .env.local config --quiet` 실행 | Docker Linux Engine 및 Compose v2가 확인되고 config가 오류 없이 해석된다. | 명령·exit code만 기록. daemon/credential 값은 기록하지 않음 |
| ENV-02 | Runbook 순서로 `pull mysql proxy`, `build backend frontend`, `up --detach`, `ps` 실행 | `mysql`, `backend`, `frontend`, `proxy`가 모두 `healthy`가 된다. | `docker compose ps` 요약과 health 상태 기록 |
| ENV-03 | 단일 origin `http://localhost:8080`(또는 실제 선택 포트)에서 기존 `smoke.ps1 -Scenario Full` 실행 후 가능하면 `Preserved` 실행 | Full smoke의 실제 결과를 남기되 legacy Subscription 중심 smoke는 MVP4 전체 PASS로 집계하지 않는다. | smoke exit code와 민감정보 제거한 단계별 결과 |
| ENV-04 | 저장소에 이미 포함된 Frontend `npm test`, `npm run lint`, `npm run typecheck`, `npm run build` 및 Backend `gradlew test`/필요 시 `gradlew build -x test` 실행 | 최신 main의 검증 상태를 QA 실행 결과와 별도로 기록한다. 도구/환경 부재는 NOT_RUN/BLOCKED로 구분한다. | 명령, exit code, 첫 actionable failure만 기록 |

## Anonymous / Auth

| ID | 절차 | 기대 결과 | 증거 방법 |
| --- | --- | --- | --- |
| ANON-01 | 로그아웃 또는 새 guest browser context로 `/`와 `/products` 접근 | Home guest 상태, 공개 CTA와 상품 목록이 보이고 보호 정보가 노출되지 않는다. | 화면 텍스트/URL 관찰 기록 |
| ANON-02 | `/products`에서 `q`, `petType`, `category`를 각각 및 조합으로 적용하고 초기화 | 상품명/설명 부분 검색과 DOG/CAT/category slug 필터가 조합대로 적용된다. | URL·결과 개수·대표 상품명 기록 |
| ANON-03 | 공개 상품 상세로 이동 | category, pet type, SKU 옵션/가격과 상품 상세가 보인다. | URL·화면 필드 기록 |
| ANON-04 | guest 상태에서 `/my`, `/cart`, `/wishlist`, `/subscriptions` 접근 | 로그인 필요 상태와 해당 화면으로 돌아가기 위한 `returnTo`가 올바른 path로 구성된다. | 보호 화면별 URL/CTA/redirect 관찰 |
| AUTH-01 | 보호 화면 CTA 또는 직접 `/login?returnTo=%2Fsubscriptions%2Fnew` 접근 | 로그인 화면이 보이고 성공 login 뒤 requested returnTo로 이동한다. 허용되지 않은 외부 returnTo는 내부 안전 경로로 정규화된다. | login URL과 최종 내부 URL 기록. credential 미기록 |
| AUTH-02 | 유효한 local QA credential로 login 후 `/api/auth/me`가 authenticated member projection을 반환하는지 확인 | 로그인 회원 상태가 Home/Header와 보호 화면에 일관되게 반영된다. | `me` 응답은 member id/role을 기록하지 않고 status만 기록 |
| AUTH-03 | UI logout 실행 후 Home/Header와 `/api/auth/me` 재조회 | logout 후 session/member 상태가 anonymous로 정리되고 보호 화면 재접근은 login으로 유도된다. | logout status, 화면 상태, redirect 기록. cookie/session 값 미기록 |
| AUTH-04 | session/auth failure를 안전하게 재현할 승인 수단이 없으면 시도하지 않음 | 관찰하지 않은 실패 동작은 PASS로 기록하지 않고 NOT_RUN 사유를 남긴다. | 승인된 재현 수단의 부재를 명시 |

## Pet / Recommendation

| ID | 절차 | 기대 결과 | 증거 방법 |
| --- | --- | --- | --- |
| REC-01 | `/subscriptions/new`에서 QA Pet 이름과 DOG 또는 CAT을 입력해 등록 | 등록된 Pet이 선택 목록에 즉시 반영되고 선택 후 호환 Plan만 로드된다. | 화면·Pet type·Plan 목록 요약 기록 |
| REC-02 | Home으로 이동해 Pet selector를 선택하고 `추천 보기` 실행 | `/api/recommendations/products?petId=...` 결과가 최대 10개로 표시된다. | HTTP status/응답 개수와 화면 결과 기록; petId는 필요 시 비식별화 |
| REC-03 | 추천 카드에서 상품명, category name/slug, reason 확인 | 상품명·category·reason이 응답과 일치하고 비의료 상품만 보인다. | 대표 카드 필드 비교 기록 |
| REC-04 | 추천 결과 화면에서 AI enabled/fallback/provider/secret 관련 내부 상태 검색 | 사용자 UI에 AI provider, fallback 내부 상태, API key/secret이 노출되지 않는다. | 화면 텍스트 관찰 및 민감정보 미검출 기록. AI 활성화·실제 key 설정은 금지 |

## Wishlist / Cart

| ID | 절차 | 기대 결과 | 증거 방법 |
| --- | --- | --- | --- |
| SHOP-01 | 상품 상세에서 로그인 후 Wishlist 추가, `/wishlist` 조회, 삭제, 재조회 | 추가·조회·삭제 결과가 같은 회원 state에 반영된다. | UI 상태·목록 개수·HTTP 결과 기록 |
| SHOP-02 | 상품 상세에서 SKU와 수량 1을 선택해 Cart 추가, `/cart` 조회 | 선택한 상품/SKU/수량이 회원 Cart에 보인다. | 화면 필드·수량 기록 |
| SHOP-03 | Cart 수량 input에 `12`를 입력하고 `적용`을 명시적으로 클릭 | 두 자리 수량 12가 서버 응답과 화면에 반영된다. | 적용 전/후 화면 및 재조회 결과 기록 |
| SHOP-04 | 수량 `0`, 음수, 소수, 공백/문자 등 승인된 validation 경계를 입력하고 적용 시도 | UI가 잘못된 수량을 오류로 표시하고 유효하지 않은 update를 보내지 않거나 서버 오류를 안전하게 표시한다. | 입력값 종류·오류 문구·서버 status 기록. 수량은 성공 조건을 만들기 위해 DB 수정하지 않음 |
| SHOP-05 | Cart item 삭제 후 재조회 | item이 제거되고 빈 상태/CTA가 올바르게 표시된다. | 화면·재조회 결과 기록 |
| SHOP-06 | Wishlist/Cart 보호 화면에서 다른 인증 사용자로 가능한 범위의 state isolation 확인 | 첫 회원의 item이 두 번째 회원에게 노출·재사용되지 않는다. | 두 browser/session의 결과 개수와 대표 상태만 기록; session/cookie 미기록 |
| SHOP-07 | 동일 Wishlist 추가 또는 동일 Cart 추가를 재시도 | 승인 계약이 정한 중복 결과를 관찰하고, 관찰하지 못한 정책은 NOT_RUN으로 남긴다. | status/body의 민감정보 제거 요약 |

## Address / Checkout

| ID | 절차 | 기대 결과 | 증거 방법 |
| --- | --- | --- | --- |
| CHK-01 | `/addresses`에서 배송지 생성 → 목록 확인 → 다른 배송지 생성/수정 또는 default 지정 → 재조회 | 회원 소유 배송지 CRUD와 default projection이 일관되게 반영된다. | 주소 원문을 기록하지 않고 label/상태/개수만 기록 |
| CHK-02 | Cart에서 `/checkout` 진입 후 default 또는 선택 배송지 확인 | Cart item과 배송지 선택 UI가 함께 보이고 주소가 없으면 등록 CTA가 보인다. | 화면 상태 기록 |
| CHK-03 | 승인 fixture 상품과 선택 배송지로 `주문 생성` 실행 | 성공 시 order number/id와 금액이 생성되고 상세 링크로 이동할 수 있다. | order identifier는 문서에 필요 최소한의 비식별화/부분값만 기록 |
| CHK-04 | Checkout 성공 화면의 안내 문구 확인 | 현재 상태가 결제 완료가 아니라 결제 Provider 연동 전 주문 생성임을 명시한다. `paymentKey`/Toss confirm 호출은 하지 않는다. | 성공 화면 문구 기록 |
| CHK-05 | 재고 부족 등 승인된 fixture 상태가 아니어서 Checkout이 막히면 재고를 임의 조작하지 않음 | 정확한 environment/data blocker로 NOT_RUN 또는 BLOCKED 기록; 임의 DB INSERT/UPDATE/DELETE 금지 | 서버 오류 code/message 요약과 blocker 기록 |
| CHK-06 | `POST /api/payments/toss/confirm` 또는 실제 Toss Provider 호출 | 명시적으로 미실행. `paymentKey`, Provider credential, 실제 결제는 사용하지 않는다. | NOT_RUN 경계만 기록 |

## Orders / Notifications

| ID | 절차 | 기대 결과 | 증거 방법 |
| --- | --- | --- | --- |
| ORD-01 | CHK-03 성공 주문이 있으면 `/orders`와 `/orders/{id}` 조회 | 생성 주문이 목록/상세에 나타나고 상태·금액·배송/환불 projection이 일치한다. | order 정보 최소 요약 기록 |
| ORD-02 | Order 상세의 `availableActions`와 표시 버튼 비교 | 서버가 허용한 action만 표시되고 불허 action은 임의 retry 버튼으로 나타나지 않는다. | action 집합 비교 기록 |
| ORD-03 | refund/payment 상태가 `UNKNOWN`인 승인 fixture가 있으면 화면 확인 | UNKNOWN 상태에서 임의 retry가 노출되지 않는다. fixture가 없으면 NOT_RUN. | fixture 유무와 관찰 결과 기록 |
| ORD-04 | 주문이 없거나 성공 Journey가 blocker이면 주문 목록/상세 성공 케이스는 미실행 | 사전 데이터가 없는 것을 PASS로 해석하지 않는다. | NOT_RUN 사유 기록 |
| NOTI-01 | `/notifications` 조회 | 회원 알림 목록과 빈 상태/오류 상태가 안전하게 표시된다. | 화면·status 기록 |
| NOTI-02 | 읽음 처리 후 목록 재조회 | 선택 알림 또는 전체 알림이 읽음 상태로 바뀌고 재조회에도 유지된다. | 읽음 전/후 개수·상태 기록 |
| NOTI-03 | 알림이 없어 처리 대상을 만들기 위해 DB를 조작해야 하면 실행하지 않음 | NOT_RUN으로 기록한다. | fixture/data blocker 기록 |

## V2 Subscription 생성

| ID | 절차 | 기대 결과 | 증거 방법 |
| --- | --- | --- | --- |
| SUB-01 | canonical `/subscriptions/new` 접근 및 인증 | guest는 `/login?returnTo=%2Fsubscriptions%2Fnew`로 유도되고 인증 회원은 생성 화면을 본다. | URL/화면 기록 |
| SUB-02 | QA Pet 선택 또는 REC-01에서 만든 Pet 생성 → 선택 | Pet 선택 목록에 반영되고 선택된 Pet에 대한 호환 Plan만 표시된다. | 화면·Plan 요약 기록 |
| SUB-03 | Plan 상세에서 서버가 반환한 `allowedDeliveryCycleWeeks`만 선택 | UI가 서버 허용 cycle을 표시하고 다른 임의 주기를 제공하지 않는다. | 응답/화면 cycle 비교 |
| SUB-04 | Pet·Plan·cycle을 선택하고 `구독 만들기` 실행 | 201 응답, `Location`/상세 조회, 동일 회원의 ACTIVE subscription과 다음 회차가 생성된다. | status, Location path, 상태 projection 기록 |
| SUB-05 | 생성 성공 후 route 확인 | 최종 route가 `/subscriptions/{id}`이고 `/mvp2/**`로 새지 않는다. | 최종 URL 기록 |
| SUB-06 | 같은 생성 요청을 동일 UI 재시도하거나 승인된 Idempotency-Key 흐름으로 확인 | 중복 subscription이 생성되지 않고 replay가 계약대로 처리된다. body fingerprint가 다른 재사용은 `IDEMPOTENCY_KEY_REUSED`여야 한다. | status, replay header 유무, subscription 수 요약 기록; key 값은 기록하지 않음 |

## Subscription Self-Service / ETag / Idempotency

### 공통 실행 규칙

상세 GET에서 받은 `ETag`를 그대로 다음 command의 `If-Match`로 사용하고, command마다 새 `Idempotency-Key`를 만든다. 성공 command는 반환된 최신 body와 새 ETag로 갱신한다. 프론트가 `availableActions`를 다시 계산해 실행하지 않도록, 각 버튼 표시를 서버 action 집합과 먼저 비교한다. 동일 command를 같은 body/key로 재시도할 때만 replay를 확인하고 key 값은 증거에 적지 않는다.

| ID | 절차 | 기대 결과 | 증거 방법 |
| --- | --- | --- | --- |
| CMD-01 | `availableActions`에 `RESCHEDULE_NEXT`가 있을 때 미래의 다른 날짜를 입력해 실행 | 200, 최신 상세와 새 ETag, nextDelivery 날짜 변경. 과거/오늘은 `SCHEDULE_DATE_NOT_FUTURE`, 기존 날짜/충돌 날짜는 `SCHEDULE_DATE_CONFLICT`. | status/code, old/new ETag의 존재와 version 변화만 기록 |
| CMD-02 | `CHANGE_DELIVERY_CYCLE`가 허용될 때 서버 제공 cycle을 선택해 실행 | 200, pendingChange의 cycle/가격/구성 projection 갱신. 허용되지 않는 cycle은 `DELIVERY_CYCLE_NOT_ALLOWED`. | pendingChange 비교 기록 |
| CMD-03 | `CHANGE_PLAN`가 허용될 때 현재 cycle을 지원하는 서버 Plan을 선택해 실행 | 200, pendingChange의 PlanVersion/가격/구성이 갱신되고 현재 nextDelivery와 구분된다. | plan/cycle/price/items 요약 기록 |
| CMD-04 | `SKIP_NEXT` 실행 | 성공 후 기존 회차가 SKIPPED, 새 nextDelivery가 생성되고 pendingChange target이 새 회차와 정합한다. | schedules/nextDelivery/pendingChange 관계 기록 |
| CMD-05 | `PAUSE` 실행 | ACTIVE→PAUSED, 해당 next 회차가 HELD, availableActions가 `RESUME/CANCEL/UPDATE_SHIPPING_ADDRESS`에 맞는다. | 상태·actions 비교 |
| CMD-06 | PAUSED에서 `RESUME` 실행 | PAUSED→ACTIVE, HELD 회차가 미래로 재계산되고 availableActions가 서버 응답과 맞는다. | 상태·날짜·actions 비교 |
| CMD-07 | ACTIVE 또는 PAUSED에서 `CANCEL` 실행 | CANCELED, unordered SCHEDULED/HELD 및 pendingChange가 계약대로 정리되고 availableActions가 빈 배열이다. | 상태·actions·pendingChange 기록 |
| CMD-08 | 각 실행 전후에 동일 key/body 재요청, stale ETag/누락 If-Match를 승인된 범위에서 확인 | 동일 요청은 성공 replay이며 중복 side effect가 없다. stale ETag는 `412 SUBSCRIPTION_VERSION_MISMATCH`, 누락 If-Match는 `428 IF_MATCH_REQUIRED`. | status/code/replay header와 version 변화 기록. raw headers/key 미기록 |

## Pending Change

| ID | 절차 | 기대 결과 | 증거 방법 |
| --- | --- | --- | --- |
| PEND-01 | Plan 또는 cycle 변경 성공 직후 상세를 재조회 | `pendingChange`가 표시되고 `appliesOn`은 target schedule 날짜다. | pendingChange 필드 요약 기록 |
| PEND-02 | pendingChange와 `nextDelivery`의 날짜·가격·주기·상품 구성을 나란히 비교 | nextDelivery는 현재 target 회차, pendingChange는 적용 예정 snapshot으로 구분된다. | 필드별 비교 표 기록 |
| PEND-03 | Plan→cycle 및 cycle→Plan 순서를 가능한 동일 subscription에서 각각 확인 | pending row는 하나이고 두 변경이 기존 pending snapshot 규칙을 따른다. | 최종 pending snapshot과 command history 요약 기록 |

## Issue / Recovery / Billing

| ID | 절차 | 기대 결과 | 증거 방법 |
| --- | --- | --- | --- |
| ISSUE-01 | 승인 fixture/API로 `SHIPPING_ADDRESS_REQUIRED`가 재현될 때 상세 확인 후 배송지 저장 | issue code/message가 사용자 계약과 일치하고 배송지 보완 후 최신 상태를 재조회한다. 임의 retry action은 없다. | issue/action/복구 후 상태 기록 |
| ISSUE-02 | 승인 fixture/API로 `BILLING_METHOD_REQUIRED`가 재현될 때 Billing CTA 선택 | `/billing-methods`로 이동하고 prepare 진입이 가능하다. 임의 payment retry는 없다. | issue/action/route 기록 |
| ISSUE-03 | `PAYMENT_SUPPORT_REQUIRED` 또는 `STOCK_UNAVAILABLE`가 재현될 때 화면 확인 | 고객지원/재고 확인 안내만 보이고 임의 retry가 없다. | issue code·표시 action 기록 |
| ISSUE-04 | 승인 fixture/API가 없으면 issue 상태를 만들기 위해 DB를 변경하지 않음 | NOT_RUN 사유를 정확히 남긴다. | 승인 fixture/API 부재 증거 |
| BILL-01 | `/billing-methods`에서 configured와 registered 상태 확인 | 서비스 설정/등록 상태가 사용자 메시지로 구분된다. | 화면·status 요약 기록 |
| BILL-02 | `configured=true`이고 미등록이면 `결제수단 등록 준비` 실행 | prepare 진입 후 Provider Client가 연결되지 않았다는 안내가 정확히 표시된다. | 화면 문구·status 기록 |
| BILL-03 | `authKey` 획득, Browser Provider complete, 실제 billing complete | 미실행. 실제 Provider credential/외부 결제수단을 사용하지 않는다. | NOT_RUN 경계 기록 |
| BILL-04 | configured=false 또는 provider unavailable인 경우 | 버튼 비활성/안전한 안내를 확인하고 우회 실행하지 않는다. | 화면 상태 기록 |

## Responsive / Accessibility 수동 확인

브라우저가 실제 GUI viewport와 keyboard/focus를 관찰하지 못하면 해당 케이스를 PASS로 처리하지 않고 `사용자 GUI 확인 필요`로 기록한다. 화면 캡처가 저장될 경우 credential, cookie, session id, CSRF token, secret이 포함되지 않은 상태만 증거로 사용한다.

| ID | viewport/절차 | 기대 결과 | 증거 방법 |
| --- | --- | --- | --- |
| UI-01 | Desktop에서 Header, Home, 상품 목록/상세, 구독 상세 확인 | Header overflow가 없고 주요 CTA와 상태/오류/재시도가 보인다. | 실제 viewport와 화면 관찰 |
| UI-02 | 좁은 Desktop/Tablet에서 Cart·Checkout 확인 | Cart/Checkout 2열이 1열로 안전하게 재배치되고 CTA가 접근 가능하다. | viewport·layout 관찰 |
| UI-03 | 320px Mobile에서 상품/구독 카드 확인 | 카드·버튼·텍스트·가격이 가로 overflow 없이 읽힌다. | viewport·overflow 관찰 |
| UI-04 | 320px Mobile에서 form과 validation 확인 | Pet/주소/수량/Checkout form이 잘리지 않고 오류가 해당 입력과 구분된다. | focus/오류 관찰 |
| UI-05 | keyboard Tab/Enter/Space로 Header, form, CTA 이동 | 기본 조작이 가능하고 focus-visible 위치가 보인다. | 실제 keyboard 관찰 |
| UI-06 | 오류/재시도/성공 message가 발생하는 화면 확인 | `role=alert/status`, focus 이동, 재시도 CTA가 사용자에게 이해 가능하다. | DOM/화면 관찰 |
| UI-07 | `prefers-reduced-motion` 브라우저 설정 또는 코드로 가능한 범위 확인 | 설정을 관찰하지 못하면 PASS하지 않고 사용자 GUI 확인 필요로 남긴다. | 설정과 관찰 경계 기록 |
| UI-08 | 두 browser context 또는 로그아웃 후 동일 화면 재진입 | 이전 사용자 상태/화면 캐시가 새 사용자에게 재사용되지 않는다. | 화면·네트워크 결과 요약 |

## 예외·권한·멱등성·상태 전이 기준

- 인증: 공개 GET과 회원 전용 GET/변경 endpoint를 분리하고, 보호 화면은 login returnTo로 안전하게 유도되는지 확인한다.
- 소유권: 다른 회원의 Pet/Subscription/Cart/Wishlist/Address/Order를 ID 추측으로 조회·변경하지 않는다. 승인된 안전한 수단이 없으면 권한 실패 케이스는 NOT_RUN이다.
- 입력 경계: 수량 12와 잘못된 수량, 날짜 미래/오늘/과거/충돌, cycle 허용/비허용, Plan-Pet mismatch를 임의 DB 조작 없이 확인한다.
- 멱등성: 생성과 command는 같은 request body/key 재시도의 replay와 다른 body의 key 재사용 오류를 구분한다. key 원문은 기록하지 않는다.
- 동시성/버전: 상세 GET ETag→If-Match command→새 ETag 흐름을 기록하고 stale version은 412로 구분한다.
- 상태 전이: subscription `ACTIVE→PAUSED→ACTIVE`, `ACTIVE/PAUSED→CANCELED`, schedule `SCHEDULED→SKIPPED/HELD/CANCELED`, pending target 변경을 확인한다.
- 결제/Provider: checkout은 결제 전 주문 생성까지만, Toss confirm·paymentKey·authKey·billing complete·AI provider activation은 미실행이다.
- 데이터 안전: 성공 조건을 만들기 위한 DB row INSERT/UPDATE/DELETE 및 비fixture 삭제를 하지 않는다.

## 회귀 위험

1. `/subscriptions/**`와 legacy `/mvp2/**` route가 같은 component를 사용하므로 링크/redirect가 legacy route로 회귀할 위험이 있다.
2. server `availableActions`와 프론트 버튼 필터가 어긋나면 허용되지 않은 command 또는 retry가 노출될 수 있다.
3. ETag/If-Match와 Idempotency-Key 누락·재사용은 구독 중복 변경, stale overwrite 위험이 있다.
4. pendingChange와 nextDelivery projection을 혼동하면 사용자가 현재 배송과 미래 변경을 잘못 이해할 수 있다.
5. 주문 생성은 재고/배송지/회원 fixture에 의존하고 결제 Provider가 비활성이라 성공 Journey가 부분적으로만 검증될 수 있다.
6. 추천은 인증 Pet 소유권·공개 상품·ACTIVE SKU·재고·비의료 필터와 AI fallback 경계를 함께 가진다.
7. 로그인/로그아웃과 browser context 재사용은 다른 회원 state leakage를 만들 수 있다.
8. 좁은 viewport에서 grid/form/CTA overflow와 focus-visible 손상이 발생할 수 있다.

## 차단 사유와 중단 조건

다음은 테스트를 PASS로 채우지 않고 정확한 상태로 기록한다.

- Docker Linux Engine/daemon, local port, `.env.local` placeholder 또는 QA bootstrap 접근이 준비되지 않음: ENV 기준선과 관련 통합 Journey를 BLOCKED 또는 NOT_RUN.
- 최신 main에 필요한 Backend/Frontend 검증 도구가 없거나 실행 환경이 맞지 않음: 해당 검증 NOT_RUN.
- 승인된 fixture/API 없이 issue·UNKNOWN payment/refund·재고 부족을 만들려면 DB 변경이 필요함: NOT_RUN.
- credential, Secret, 실제 Provider/API key, Toss authKey/paymentKey가 필요함: 해당 케이스 NOT_RUN하고 credential을 출력하지 않음.
- 제품 코드·API/DB/auth 계약 수정이 필요하거나 browser dependency 추가가 필요함: 즉시 중단하고 담당 역할로 에스컬레이션.
- GUI keyboard/viewport/focus를 실제 관찰할 수 없음: UI 케이스를 PASS로 기록하지 않고 `사용자 GUI 확인 필요`.

## 결과 판정 규칙

- `PASS`: 기대 결과를 실제 환경에서 직접 관찰하고 증거가 있다.
- `FAIL`: 재현 가능한 기대 불일치가 있고 재현 절차·기대·실제가 기록되어 있다. 제품 코드는 수정하지 않는다.
- `PARTIAL`: Journey 일부만 관찰되고 나머지는 환경/fixture/Provider 경계로 미실행이다.
- `NOT_RUN`: 사전 조건·승인 입력·안전 경계 때문에 실행하지 않았다.
- `BLOCKED`: 환경 또는 승인 경계가 해당 케이스 실행 자체를 막았다.
- 결과 보고서에는 케이스별 실제 결과·판정·증거·남은 위험을 남기며, 관찰하지 않은 동작을 PASS로 추정하지 않는다.
