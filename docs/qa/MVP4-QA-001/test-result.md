# MVP4-QA-001 테스트 결과

## 실행 요약

- 작업 ID: `MVP4-QA-001`
- 실행일: 2026-08-25 (Asia/Seoul)
- 기준 ref: 최신 `main` `236156b`
- 실행 환경: `infra/local-integration`, 단일 origin `http://localhost:8080`, Docker Linux Engine
- 제품 코드/API 계약/DB schema/fixture 원본/dependency 변경: 없음
- 인증 API 검증은 `.env.local`의 local QA credential을 프로세스 메모리에서만 사용했다. credential, password, cookie, session ID, CSRF token, Idempotency-Key, ETag 원문과 주소 원문은 기록하지 않았다.
- raw 로그·스크린샷은 민감정보 혼입을 피하기 위해 저장소에 추가하지 않고, 아래에 판정 가능한 요약만 남긴다.

### 케이스 판정 개수

총 69개 케이스를 계획했고 다음과 같이 판정했다.

| PASS | FAIL | PARTIAL | NOT_RUN | BLOCKED |
| ---: | ---: | ---: | ---: | ---: |
| 15 | 3 | 28 | 20 | 3 |

`FAIL`은 재현 가능한 제품 동작 불일치, `BLOCKED`는 환경·승인 fixture 때문에 기대 동작 자체를 관찰할 수 없었던 경우다. `PARTIAL`은 같은 케이스에서 일부 경로만 직접 관찰한 경우다.

## 환경 기준선

| ID | 실제 결과 | 판정 | 증거 | 남은 위험 |
| --- | --- | --- | --- | --- |
| ENV-01 | Docker Client/Server `28.5.1`, Server OS `linux`; Compose config가 오류 없이 해석됐다. | PASS | `docker version --format ...`; `docker compose --env-file .env.local config --quiet` | 없음 |
| ENV-02 | `mysql`, `backend`, `frontend`, `proxy`가 healthy가 됐고 Redis도 healthy로 확인됐다. | PASS | `docker compose --env-file .env.local ps` | observability orphan container warning은 있었지만 QA 대상 stack health에는 영향이 없었다. |
| ENV-03 | 기존 `smoke.ps1 -Scenario Full`과 `-Scenario Preserved`가 각각 passed했다. | PASS | `FOUNDATION-004 smoke scenario passed: Full/Preserved` | legacy smoke는 MVP4 전체 증거가 아니다. |
| ENV-04 | Frontend `npm test` 28/28, lint, typecheck, build가 통과했다. Backend `compileJava`는 성공했다. 직접 `gradlew test`는 253 tests 중 131 failures로 ApplicationContext가 실패했다. | PARTIAL | Backend 첫 원인은 test profile의 datasource URL이 `jdbc` URL로 주입되지 않은 `'url' must start with "jdbc"`였다. | Backend 전체 테스트는 local direct 실행으로 검증되지 않았다. CI의 MySQL service 경로와 local direct 경로를 동일시하지 않는다. |

## Anonymous / Auth

| ID | 실제 결과 | 판정 | 증거 | 남은 위험 |
| --- | --- | --- | --- | --- |
| ANON-01 | `/` Home guest 상태와 `/products` 공개 상품 목록·guest Header를 확인했다. | PASS | Browser DOM snapshot: guest CTA와 공개 상품 fixture 표시 | authenticated Home 개인화는 별도 미실행이다. |
| ANON-02 | `q=정기배송`, `petType=DOG`, `category=qa-foundation-004` 조합으로 fixture 1건이 반환됐다. | PASS | URL query와 상품 목록 snapshot 비교 | 다른 pet/category 데이터 조합은 fixture 부족으로 추가 확인하지 않았다. |
| ANON-03 | `/products/1`에서 category, pet type, SKU 옵션/가격, 구독 시작 CTA를 확인했다. | PASS | Browser DOM snapshot | 상품 이미지가 없는 fixture라 이미지 렌더링은 확인하지 않았다. |
| ANON-04 | `/my`, `/cart`, `/wishlist`, `/subscriptions` 모두 보호 상태를 표시했지만 `/my`, `/cart`, `/wishlist`의 로그인 CTA가 `returnTo`를 각 path가 아닌 `/products`로 만들었다. | FAIL | 각 URL 재현; `/subscriptions`만 `/subscriptions`를 유지 | 로그인 후 원래 작업 복귀가 끊겨 보호 화면 사용성이 저하된다. D-01 참조 |
| AUTH-01 | login 화면과 내부 `/subscriptions/new` 요청의 login 진입은 보였으나 password 입력·성공 login 후 복귀는 실행하지 않았다. | PARTIAL | Browser snapshot: login form 및 내부 login URL | password를 browser에 입력하려면 action-time confirmation이 필요하다. |
| AUTH-02 | API login `200`, `/api/auth/me` `200`을 확인했으나 authenticated Header/Home UI는 관찰하지 않았다. | PARTIAL | 인증 API harness 요약 `AUTH login=200 me=200` | GUI의 member projection 및 UI 상태는 사용자 GUI 확인 필요 |
| AUTH-03 | API logout `204`를 확인했으나 browser에서 logout 후 화면·재진입은 관찰하지 않았다. | PARTIAL | API harness 요약 `AUTH logout=204` | GUI session cleanup은 사용자 GUI 확인 필요 |
| AUTH-04 | session/auth failure를 안전하게 재현할 승인 수단이 없어 실행하지 않았다. | NOT_RUN | 승인된 failure injection 수단 없음 | 인증 만료·실패 UI 회귀 위험이 남는다. |

## Pet / Recommendation

| ID | 실제 결과 | 판정 | 증거 | 남은 위험 |
| --- | --- | --- | --- | --- |
| REC-01 | API로 QA Pet 생성 `201`, 호환 Plan 1건, 서버 cycle `2,4,8`을 확인했다. `/subscriptions/new` UI의 Pet selector 반영은 authenticated browser에서 확인하지 않았다. | PARTIAL | API harness `PET status=201`, `plans=200 count=1 cycles=2,4,8` | Pet 등록 UI와 Home selector 연결은 사용자 GUI 확인 필요 |
| REC-02 | 생성된 Pet의 추천 API는 `200`이나 상품 `0`건이었다. | BLOCKED | API harness `RECOMMENDATION status=200 count=0`; 동일 fixture checkout도 재고 부족 | in-stock 공개 SKU가 없어서 추천 성공 카드 Journey를 만들 수 없다. DB 재고 조작 금지 |
| REC-03 | 추천 상품이 0건이라 상품명/category/reason의 실제 카드 비교를 할 수 없었다. | BLOCKED | 추천 응답 `products=[]` | 대표 추천의 reason·category 정합성은 재고 fixture 보충 후 재검증 필요 |
| REC-04 | 응답/UI에 AI provider·fallback·secret 필드를 기록하거나 노출하지 않았고, authenticated recommendation UI는 관찰하지 않았다. | PARTIAL | API response summary에 내부 상태 없음; browser UI는 guest 범위 | AI disabled/fallback의 최종 사용자 표시를 GUI에서 확인하지 못했다. AI 활성화·실제 key는 미실행 |

## Wishlist / Cart

| ID | 실제 결과 | 판정 | 증거 | 남은 위험 |
| --- | --- | --- | --- | --- |
| SHOP-01 | API Wishlist 추가 `204` → 조회 `200`/1건 → 삭제 `204`를 확인했다. 상품 상세 UI와 Wishlist 화면은 authenticated browser에서 확인하지 않았다. | PARTIAL | API harness `WISHLIST add=204 get=200 count=1 delete=204` | UI button/empty state는 사용자 GUI 확인 필요 |
| SHOP-02 | API Cart 추가 `204`와 조회 `200`을 확인했다. | PARTIAL | API harness `CART add=204 get=200` | 상품 상세의 명시적 SKU 선택·CTA 연결은 GUI 미확인 |
| SHOP-03 | Cart quantity `12` PATCH `204`, 재조회에서 quantity `12`를 확인했다. | PARTIAL | API harness `update12=204 observed_quantity=12` | 두 자리 입력과 명시적 적용 버튼은 GUI 미확인 |
| SHOP-04 | quantity `0` PATCH가 `400 VALIDATION_FAILED`였고, UI field validation은 GUI로 확인하지 않았다. | PARTIAL | API harness `invalid=400:VALIDATION_FAILED`; 기존 Frontend unit/contract test는 pass | 음수·소수·문자별 실제 UI 안내는 사용자 GUI 확인 필요 |
| SHOP-05 | API Cart item 삭제 `204`를 확인했다. | PARTIAL | API harness `delete=204` | 삭제 후 UI empty state 미확인 |
| SHOP-06 | 두 authenticated browser context를 이용한 사용자 state isolation은 실행하지 않았다. | NOT_RUN | 별도 승인 회원/session 없음 | member state leakage 위험이 독립 검증되지 않았다. |
| SHOP-07 | Wishlist/Cart 중복 요청 계약은 별도 실행하지 않았다. | NOT_RUN | 중복 요청 전용 승인 입력 없음 | 중복 추가의 최종 정책은 API 계약/추가 fixture로 재검증 필요 |

## Address / Checkout

| ID | 실제 결과 | 판정 | 증거 | 남은 위험 |
| --- | --- | --- | --- | --- |
| CHK-01 | API 배송지 create `201`, update `204`, default `204`, 조회 `200` 및 default 1건을 확인했다. 주소 원문은 기록하지 않았다. | PARTIAL | API harness `ADDRESS create=201 update=204 default=204 get=200 default_count=1` | `/addresses` form과 default 표시 UI는 GUI 미확인 |
| CHK-02 | API에서 배송지가 생성·default 처리됐지만 Cart/Checkout 화면의 선택 UI는 관찰하지 않았다. | PARTIAL | Address API 결과; browser authenticated checkout 미실행 | Checkout 2열·주소 선택 상태는 사용자 GUI 확인 필요 |
| CHK-03 | Cart 추가는 `204`였으나 Checkout은 `409 INVENTORY_INSUFFICIENT`로 주문을 만들지 못했다. | BLOCKED | API harness `CHECKOUT add_cart=204 checkout=409 ... code=INVENTORY_INSUFFICIENT` | 재고가 있는 승인 fixture가 없어 성공 주문을 검증할 수 없다. DB row/재고 조작 금지 |
| CHK-04 | 성공 주문 화면이 생성되지 않아 결제 전 주문 생성 문구를 확인하지 못했다. | NOT_RUN | CHK-03 blocker | 주문 생성 성공 후 Provider 안내 UI를 재검증해야 한다. |
| CHK-05 | 재고 부족을 확인하고 retry/DB 조작 없이 중단·기록했다. | PASS | `INVENTORY_INSUFFICIENT`를 환경 blocker로 분류하고 상태 변경 없음 | in-stock fixture가 준비되기 전까지 성공 Journey는 남은 위험이다. |
| CHK-06 | Toss confirm/paymentKey 호출을 하지 않았다. | NOT_RUN | 명시된 결제 Provider 경계 준수 | 실제 결제 완료는 이 QA 결과에 포함되지 않는다. |

## Orders / Notifications

| ID | 실제 결과 | 판정 | 증거 | 남은 위험 |
| --- | --- | --- | --- | --- |
| ORD-01 | 성공 주문이 없어 주문 목록/상세를 실행하지 않았다. | NOT_RUN | CHK-03 주문 생성 blocker | 주문 상태·금액·배송 projection 미검증 |
| ORD-02 | 비교할 생성 주문의 `availableActions`가 없어 실행하지 않았다. | NOT_RUN | 성공 order fixture 없음 | server action과 표시 버튼 정합성 미검증 |
| ORD-03 | UNKNOWN payment/refund 승인 fixture가 없어 실행하지 않았다. | NOT_RUN | 승인 fixture 없음; 임의 상태 생성 금지 | UNKNOWN 상태에서 retry 금지 UI 미검증 |
| ORD-04 | 주문 성공 Journey가 없어 목록/상세 성공 케이스를 PASS로 추정하지 않았다. | NOT_RUN | CHK-03 blocker | 주문 회귀 위험이 남는다. |
| NOTI-01 | `/api/notifications` `200`, 0건을 확인했다. authenticated notification UI는 관찰하지 않았다. | PARTIAL | API harness `NOTIFICATIONS list=200 count=0` | empty state/error state UI 미확인 |
| NOTI-02 | 읽을 알림이 없어 read transition은 실행하지 않았다. `read-all` `204`, 재조회 `200`은 확인했지만 대상 0건이다. | NOT_RUN | API harness `unread_before=0 read_all=204 reread=200 unread_after=0` | unread→read 상태 전이는 fixture 후 재검증 필요 |
| NOTI-03 | 알림을 만들기 위한 DB 변경을 하지 않았다. | NOT_RUN | 승인된 notification fixture 없음 | 사용자 알림 생성 후 읽음 UI 미검증 |

## V2 Subscription 생성

| ID | 실제 결과 | 판정 | 증거 | 남은 위험 |
| --- | --- | --- | --- | --- |
| SUB-01 | guest가 canonical `/subscriptions/new`에서 `/login?returnTo=%2Fproducts`로 이동했다. 기대한 `/subscriptions/new` returnTo가 보존되지 않았다. | FAIL | Browser direct navigation 재현 | canonical 생성 Journey가 login 뒤 원래 위치로 돌아가지 않는다. D-01 참조 |
| SUB-02 | API Pet 생성과 Plan 조회는 성공했지만 UI Pet 선택은 authenticated browser에서 실행하지 않았다. | PARTIAL | `PET status=201`, plans 1건 | canonical form UI 연결 미확인 |
| SUB-03 | Plan detail API가 서버 cycle `2,4,8`을 반환했다. UI에서 그 선택지만 보이는지는 확인하지 않았다. | PARTIAL | API harness cycles `2,4,8` | UI가 서버 선택지를 그대로 쓰는지 GUI 미확인 |
| SUB-04 | V2 subscription create `201`과 detail을 확인했고 동일 create key replay도 `201`이었다. | PARTIAL | `SUB_CREATE status=201 ... replay_status=201 replay=true` | UI submit·생성 성공 toast/route는 미확인 |
| SUB-05 | API Location은 `/api/v2/subscriptions/{id}`였고, browser authenticated success route는 확인하지 않았다. | PARTIAL | API create response summary; canonical route source review | `/subscriptions/{id}` 이동과 `/mvp2/**` 미사용은 GUI 재검증 필요 |
| SUB-06 | 동일 body/key 재시도는 replay했고 중복 생성은 확인되지 않았다. 다른 body의 key 재사용 오류는 실행하지 않았다. | PARTIAL | create replay header summary `replay=true` | fingerprint mismatch의 `IDEMPOTENCY_KEY_REUSED` 미검증 |

## Subscription Self-Service

| ID | 실제 결과 | 판정 | 증거 | 남은 위험 |
| --- | --- | --- | --- | --- |
| CMD-01 | `RESCHEDULE_NEXT` `200`, 새 ETag 존재, 동일 request replay `200/Idempotency-Replayed=true`, stale ETag `412 SUBSCRIPTION_VERSION_MISMATCH`를 확인했다. | PASS | API harness `CMD_RESCHEDULE ... stale=412:SUBSCRIPTION_VERSION_MISMATCH` | 날짜 충돌·오늘/과거 오류 경계는 별도 미실행 |
| CMD-02 | 허용 cycle 변경 `200`, pendingChange 존재를 확인했다. | PASS | `CMD_CYCLE status=200 pending_present=True` | cycle 비허용 오류는 별도 미실행 |
| CMD-03 | Plan 변경 `200`, pendingChange 존재를 확인했다. | PASS | `CMD_PLAN status=200 pending_present=True` | 다른 Pet 소유권/Plan mismatch는 미실행 |
| CMD-04 | `SKIP_NEXT` `200` 후 nextDelivery가 존재하고 최신 상태로 반환됐다. | PASS | `CMD_SKIP status=200 next_delivery=True` | schedule row의 세부 비교는 결과 요약에 남기지 않았다. |
| CMD-05 | `PAUSE` `200`, ACTIVE→PAUSED, actions가 `RESUME,CANCEL,UPDATE_SHIPPING_ADDRESS`로 바뀌었다. | PASS | `CMD_PAUSE status=200 state=PAUSED actions=...` | GUI button visibility는 미확인 |
| CMD-06 | `RESUME` `200`, PAUSED→ACTIVE 및 actions 재계산을 확인했다. | PASS | `CMD_RESUME status=200 state=ACTIVE actions=...` | 실제 날짜 충돌이 있는 HELD fixture는 미확인 |
| CMD-07 | `CANCEL` `200`, CANCELED 및 빈 availableActions를 확인했다. | PASS | `CMD_CANCEL status=200 state=CANCELED actions=` | QA 생성 subscription을 최종 cancel한 상태다. |
| CMD-08 | 성공 replay와 stale ETag는 확인했으나 누락 If-Match `428`, 다른 body의 key 재사용 `409`는 실행하지 않았다. | PARTIAL | CMD-01 replay/stale 증거 | protocol 경계 일부가 남아 있다. |

## Pending Change

| ID | 실제 결과 | 판정 | 증거 | 남은 위험 |
| --- | --- | --- | --- | --- |
| PEND-01 | cycle/Plan command 성공 응답에서 pendingChange가 존재했다. | PASS | `pending_present=True` 두 command 결과 | field별 raw 값은 기록하지 않았다. |
| PEND-02 | pendingChange와 nextDelivery를 같은 응답에서 받았지만 적용일·가격·주기·상품 구성의 field-by-field 비교는 결과 증거로 남기지 않았다. | PARTIAL | command response의 pending presence만 요약 | current/next/pending 표시 혼동 회귀가 남아 있다. |
| PEND-03 | cycle 변경 후 Plan 변경 순서를 실행했고 pendingChange는 계속 하나로 표시됐다. | PARTIAL | `CMD_CYCLE` 후 `CMD_PLAN`의 pending presence | pending snapshot 병합 규칙과 단일 row는 DB를 직접 읽지 않아 완전 검증하지 않았다. |

## Issue / Recovery / Billing

| ID | 실제 결과 | 판정 | 증거 | 남은 위험 |
| --- | --- | --- | --- | --- |
| ISSUE-01 | `SHIPPING_ADDRESS_REQUIRED` 승인 fixture/API를 확보하지 못해 issue 생성·배송지 복구를 실행하지 않았다. | NOT_RUN | detail에서 issue가 없는 정상 상태만 확인; DB 조작 금지 | 배송지 복구 후 HELD 정상화 미검증 |
| ISSUE-02 | `BILLING_METHOD_REQUIRED` fixture/API를 확보하지 못해 Billing 이동을 issue에서 실행하지 않았다. | NOT_RUN | 승인 fixture 없음 | issue action과 Billing route 정합성 미검증 |
| ISSUE-03 | `PAYMENT_SUPPORT_REQUIRED`/`STOCK_UNAVAILABLE` fixture/API를 확보하지 못해 실행하지 않았다. | NOT_RUN | 승인 fixture 없음; 임의 retry/DB 상태 생성 금지 | 임의 retry 부재를 실제 issue 화면에서 미검증 |
| ISSUE-04 | 승인 fixture/API 없이 issue를 만들지 않았다. | NOT_RUN | 사용 가능한 safe fixture 없음 | issue 변환과 사용자 message 회귀 위험이 남는다. |
| BILL-01 | Billing API `200`, `configured=true`, `registered=false`를 확인했다. UI status tile은 authenticated browser에서 확인하지 않았다. | PARTIAL | `BILLING status=200 configured=True registered=False` | configured/registered 표시 UI 미확인 |
| BILL-02 | Billing prepare `200` 및 prepare token 존재를 확인했다. raw token은 기록하지 않았다. Provider Client 부재 안내는 UI에서 확인하지 않았다. | PARTIAL | `prepare=200 prepare_token_present=True` | 사용자가 보는 Provider Client 안내는 GUI 확인 필요 |
| BILL-03 | authKey 획득, Browser Provider complete, billing complete는 실행하지 않았다. | NOT_RUN | 실제 Provider credential 경계 | billing complete는 이 결과에 포함되지 않는다. |
| BILL-04 | `configured=false` 상태는 실행하지 않았다. | NOT_RUN | local fixture는 configured 상태 | unavailable provider의 UI disable 문구 미검증 |

## Responsive / Accessibility

| ID | 실제 결과 | 판정 | 증거 | 남은 위험 |
| --- | --- | --- | --- | --- |
| UI-01 | Desktop Home·상품 목록 DOM, 좁은 viewport Home DOM을 확인했다. authenticated 주요 화면과 실제 focus-visible 스타일은 모두 확인하지 않았다. | PARTIAL | Browser snapshots at 1440px/900px | 전체 authenticated Header/CTA 회귀가 남는다. |
| UI-02 | Cart/Checkout 2열→1열을 authenticated UI에서 확인하지 않았다. | NOT_RUN | 인증 GUI 경계 및 checkout blocker | responsive checkout layout 미검증 |
| UI-03 | 320px Mobile 상품 목록 screenshot에서 Header/heading/설명 일부가 viewport 오른쪽에서 잘리는 현상을 재현했다. | FAIL | 320px screenshot: 상품 목록 heading과 설명이 우측에서 clipped | narrow mobile overflow가 주요 탐색을 저해한다. D-02 참조 |
| UI-04 | 320px 상품 검색 form control은 보였지만 heading/설명 clipping이 함께 관찰됐고, 주소/Cart form은 확인하지 않았다. | PARTIAL | 320px screenshot 및 DOM snapshot | form 전체와 오류 focus는 사용자 GUI 추가 확인 필요 |
| UI-05 | Browser keyboard `Tab` 조작 후 DOM active 상태는 읽었으나 focus-visible 시각 스타일과 Enter/Space 전체 흐름은 확인하지 않았다. | PARTIAL | DOM snapshot active marker | keyboard-only 사용자 흐름 미완료 |
| UI-06 | guest 보호 오류의 `role=alert`와 login form 상태는 확인했으나 retry/checkout/subscription 오류·성공 상태는 확인하지 않았다. | PARTIAL | Browser DOM snapshots | error/retry/focus recovery 미검증 |
| UI-07 | `prefers-reduced-motion` 브라우저 설정을 관찰하지 않았다. | NOT_RUN | 설정 미실행 | reduced-motion 접근성 미검증 |
| UI-08 | 두 authenticated context의 state isolation을 실행하지 않았다. | NOT_RUN | 별도 승인 회원/session 없음 | 사용자 state leakage 위험이 남는다. |

## 발견 결함

### D-01 로그인 returnTo가 일부 보호 화면에서 `/products`로 손실됨

- 심각도: Medium
- 환경: local integration, latest main `236156b`, guest browser
- 재현 절차:
  1. `http://localhost:8080/my`에 guest로 접근한다.
  2. 화면의 `로그인` CTA를 확인한다.
  3. 같은 방법으로 `/cart`, `/wishlist`, `/subscriptions/new`를 확인한다.
- 기대 결과: `/my`, `/cart`, `/wishlist`, `/subscriptions/new` 각각이 로그인 성공 뒤 원래 요청 화면으로 돌아갈 수 있도록 해당 내부 `returnTo`를 보존한다.
- 실제 결과: `/my`, `/cart`, `/wishlist`, `/subscriptions/new`가 모두 `/login?returnTo=%2Fproducts`로 이동했다. `/subscriptions`와 상품 상세는 각 내부 경로를 보존했다.
- 증거: Browser URL/DOM snapshot; Frontend `sanitizeReturnTo`의 허용 정규식이 위 path를 포함하지 않는 것을 구현과 대조했다.
- 의심 영역: `frontend/src/lib/frontend-utils.ts`의 `SAFE_RETURN_PATH`와 보호 화면 login CTA 연결.
- QA 조치: 제품 코드 수정 없이 FAIL 기록 및 Frontend 역할 에스컬레이션.

### D-02 320px Mobile 상품 탐색에서 텍스트가 viewport 밖으로 잘림

- 심각도: Medium
- 환경: local integration, latest main `236156b`, browser viewport 320px × 900px
- 재현 절차:
  1. viewport를 320px × 900px로 설정한다.
  2. `http://localhost:8080/products`에 접근한다.
  3. 상품 탐색 heading과 설명을 확인한다.
- 기대 결과: Header, heading, 설명, 검색 form이 320px 안에서 가로 overflow 없이 읽힌다.
- 실제 결과: 상품 탐색 heading과 설명의 우측 텍스트가 viewport 경계에서 clipped되며 Header의 일부 메뉴도 좁게 배치된다.
- 증거: 320px browser screenshot과 DOM snapshot.
- 의심 영역: Frontend global layout/typography/header responsive CSS.
- QA 조치: 제품 코드 수정 없이 FAIL 기록 및 Frontend 역할 에스컬레이션.

## 환경 blocker 및 미실행 경계

1. FOUNDATION-004 공개 SKU는 노출되지만 추천의 재고 조건을 만족하는 후보가 0건이었다. 같은 SKU Checkout도 `409 INVENTORY_INSUFFICIENT`였다. 성공 추천/주문을 만들기 위해 DB 재고를 조작하지 않았다.
2. issue 상태(`SHIPPING_ADDRESS_REQUIRED`, `BILLING_METHOD_REQUIRED`, `PAYMENT_SUPPORT_REQUIRED`, `STOCK_UNAVAILABLE`)와 UNKNOWN payment/refund를 안전하게 만드는 승인 fixture/API가 없어 실행하지 않았다.
3. browser에 local password를 입력하는 authenticated GUI 검증은 action-time confirmation 경계 때문에 실행하지 않았다. API login은 process memory로 확인했으며, 사용자가 직접 확인해야 하는 authenticated UI는 별도 GUI 확인으로 남겼다.
4. Toss confirm/paymentKey, Billing authKey complete, 실제 Provider credential/API key, recommendation AI activation은 모두 미실행했다.
5. 두 번째 인증 회원/browser context가 없어 state isolation을 독립 확인하지 않았다.
6. Backend direct `gradlew test`는 local test profile datasource URL 차단으로 실패했다. 이는 이 QA branch 제품 코드 변경으로 해결하지 않았고, CI MySQL service 경로와 별도 환경 blocker로 기록했다.

## 최신 main 및 PR 검증 상태 참고

PR #218~#221은 모두 `main`에 병합됐다.

- #218: `MERGED`, subscription Backend; historical Repository Validation Application/metadata check가 실패했다.
- #219: `MERGED`; Application validation 및 Backend/MySQL validation 성공, Frontend validation은 변경 분류상 skipped.
- #220: `MERGED`; Application 및 Frontend validation 성공.
- #221: `MERGED`; Application 및 Frontend validation 성공, Backend/MySQL validation은 skipped.

현재 QA 실행에서는 Frontend test/lint/typecheck/build와 Backend compileJava를 직접 확인했고, Backend 전체 test의 local datasource blocker를 별도로 기록했다.

## QA 결론

MVP4 사용자 흐름은 API와 공개 guest UI의 넓은 범위가 관찰됐고 V2 self-service 핵심 상태 전이는 계약대로 동작했다. 그러나 D-01/D-02 제품 결함, 재고 부족으로 인한 추천·주문 성공 Journey blocker, 인증 GUI·issue·Provider·responsive 전체 확인 경계 때문에 MVP4 전체 PASS 또는 병합 권고 상태로 판정하지 않는다. 제품 수정 후에는 동일 branch의 제품 코드가 아닌 담당 Frontend 수정 결과를 기준으로 D-01/D-02 재현 경로를 우선 재검증해야 한다.
