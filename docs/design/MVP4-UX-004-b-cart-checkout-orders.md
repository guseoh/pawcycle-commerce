# MVP4-UX-004 B. 장바구니·결제·주문

상태: `Proposed Design Contract / Draft / Pending Product Owner Approval`

## 적용 범위

대상은 `/cart`, `/checkout`, `/checkout/success`, `/checkout/fail`, `/orders`, `/orders/[orderId]`와 재주문·주문 후 정기배송 진입이다. 서버의 cart version, checkout idempotency, `409 cart changed`, 부분 재주문 결과를 시각 상태와 상호작용으로 연결한다.

거래 화면은 최대 1,180px의 12열 셸을 쓰고, 데스크톱에서 내용 8열+주문 요약 4열, 모바일에서 단일 열+하단 핵심 CTA로 전환한다. 결제·주문 상태는 장식보다 명시적인 텍스트와 다음 행동을 우선한다.

## B1. Cart `/cart`

현재 Checkout API는 선택된 item ID 목록을 받지 않으므로 **Checkout 대상은 서버 Cart 전체**다. Coupon도 Cart mutation이 아니라 Checkout command의 optional `memberCouponId`다. 따라서 Cart에서 selection/coupon 상태를 만들지 않는다.

### 데스크톱 와이어프레임

```text
H1 장바구니
┌ Items 8 cols ─────────────────────┐ ┌ Summary 4 cols (sticky top 64px) ┐
│ item row: product / sku           │ │ 상품금액                          │
│ quantity / unit price / line sum  │ │ 할인/배송/총액                     │
│ unavailable group / remove        │ │ [장바구니 전체 주문하기]            │
└───────────────────────────────────┘ └───────────────────────────────────┘
```

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | 인증된 cart API는 `skuId`, 상품/SKU명, 수량, 단가/행 금액, 구매 가능 재고·상태, pricing, cart version을 제공한다. CartItem에는 thumbnail과 선택 상태가 없다. |
| 문제 | 수량 저장 중, 품절/구매불가, 가격 변경, 409 충돌이 일반 오류로 섞일 수 있다. 응답에 없는 이미지·선택 상태·Cart coupon을 디자인이 요구하면 구현자가 N+1 조회나 API 확장을 임의로 만들게 된다. |
| 레퍼런스 | Kurly/IKEA의 수량·삭제·semantic summary와 명확한 거래 CTA는 `CONFIRMED/ADAPT`. 외부 Cart의 selection/coupon 동작은 PawCycle 현 API가 지원하지 않으므로 구조적 참고만 하고 그대로 도입하지 않는다. 둥근 card 남용은 `REJECT`; 행과 divider를 우선한다. |
| 최종 IA | heading→구매 가능 item group→구매 불가/변경 item group→sticky order summary. Empty에서는 summary 대신 `장바구니가 비어 있어요`와 계속 쇼핑 CTA를 명확히 둔다. |
| visual hierarchy | 상품명·SKU명·수량·행 금액이 1차, 삭제가 2차, 재고/구매 불가는 warning alert. 주문 CTA는 서버 Cart 전체 수량 합과 최종 금액을 사용한다. |
| 컴포넌트 | `CartItemRow`, `QuantityStepper`, `AvailabilityNotice`, `OrderSummary`, `ConflictPanel`. `CartSelectionBar`, Cart `CouponSummary`, thumbnail 필수 slot은 사용하지 않는다. |
| detailed interaction | 수량 변경은 item 단위 pending→서버 확인 후 Cart 재조회/권위 값 반영. 연속 클릭을 queue하지 않는다. 삭제는 item 단위 pending이며 서버 성공 후 제거한다. Cart에서 coupon 적용/해제하지 않는다. |
| hover/focus/navigation | row 전체 clickable 금지. 응답에 image가 없으므로 상품명만 PDP 링크의 기본값이다. 수량 버튼 disabled 이유를 제공한다. Checkout CTA는 `/checkout`로 이동하며 선택 snapshot을 전달하지 않는다. |
| loading | cart skeleton 3행+summary skeleton. 인증 확인 전 empty를 보이지 않는다. item mutation은 해당 행만 busy, summary는 계산 중 표시. |
| empty | `장바구니가 비어 있어요` + 상품 보기. anonymous면 로그인 CTA도 제공하되 cart 존재를 추정하지 않는다. |
| error/retry | 최초 실패 page section retry. item 실패는 원래 값 유지/복구+inline retry. `409`는 `다른 화면에서 장바구니가 변경됐어요`와 서버 최신 항목/가격을 다시 불러온 후 사용자가 재확인하게 한다. |
| success | 수량·삭제 성공 후 서버 pricing과 Cart 총 수량을 갱신하고 금액 변화는 live region으로 한 번 알린다. toast만으로 가격 변화를 숨기지 않는다. |
| responsive | D8 SSOT: 1024px 이상 8/4, summary `top:64px` sticky. 1023px 이하 single column, 767px 이하 64px `총 N개 · 총액 주문하기` action이다. CartItem에 이미지가 없으므로 320–359에서도 상품/SKU명→수량→금액→삭제 순의 text row를 사용한다. |
| accessibility | 수량 버튼 `상품명 수량 1개 줄이기`, 삭제 `상품명 삭제`, 합계 definition list, 오류 `role=alert`. selection checkbox semantics를 만들지 않는다. |
| gap·impact | cart row DOM, item별 pending/error, conflict panel, mobile sticky CTA가 필요하다. API/DB 변경은 없다. |
| acceptance | 일부 item 오류가 전체 Cart를 지우지 않고, 409 이후 서버 합계를 확인하기 전 Checkout이 다시 활성화되지 않으며, keyboard로 수량·삭제·전체 Cart 주문이 가능하다. 선택 상품 결제나 Cart coupon을 지원한다고 오표현하지 않는다. |

## B2. Checkout `/checkout`

Checkout은 한 화면 안에서 두 개의 서로 다른 mutation 단계를 명확히 분리한다. **Phase A는 Order/Payment context 생성**, **Phase B는 Toss provider 결제 승인**이다.

### Phase A — 주문 및 결제 준비

```text
Cart server state
→ 배송지 선택
→ optional coupon 선택
→ cartVersion + 서버 금액 확인
→ [주문 및 결제 준비]
→ POST /api/checkout
→ CheckoutResult(orderId/paymentId/providerOrderId/orderName/amount)
```

### Phase B — Toss 결제

```text
CheckoutResult
→ Toss widget loading
→ provider payment methods / agreement ready
→ [결제하기]
→ provider redirect
→ /checkout/success 또는 /checkout/fail
→ server confirmation
```

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | 인증, 주소 목록, coupon 목록, cartVersion, Idempotency-Key, `POST /api/checkout`, CheckoutResult, Toss payment widget과 confirm API를 사용한다. 저장 billing method selector는 Checkout 필수 계약이 아니다. |
| 문제 | Order/Payment context 생성과 실제 provider 승인을 한 CTA처럼 표현하면 결제 준비가 곧 결제 완료처럼 보일 수 있다. 저장 결제수단 미등록을 blocker로 만들면 실제 Toss widget 계약과 충돌한다. |
| 레퍼런스 | IKEA의 progressive checkout section과 영구 summary는 `CONFIRMED/ADAPT`. PawCycle의 `POST /api/checkout`→CheckoutResult→Toss→confirm 순서는 내부 코드/API 계약이 권위다. 외부 결제 화면 자체는 `UNVERIFIED`. |
| 최종 IA | heading+진행 설명→주문 상품/금액 요약→배송지→coupon/할인→Phase A 서버 최종 확인→Phase B Toss payment panel. `/billing-methods` 등록 여부를 Checkout 진행 조건이나 selector로 사용하지 않는다. |
| visual hierarchy | Phase A에서는 서버 최종 금액과 `주문 및 결제 준비`가 1차다. CheckoutResult 생성 후 Phase B에서는 provider amount와 `결제하기`가 1차다. 동시에 두 primary CTA를 활성화하지 않는다. |
| 컴포넌트 | `CheckoutSection`, `AddressSelector`, `CouponSelector`, `OrderItemsDisclosure`, `CheckoutSummary`, `ValidationSummary`, `CheckoutPreparation`, `TossWidgetBoundary`, `PaymentVerificationNotice`. 저장 BillingMethod CRUD/selector component를 두지 않는다. |
| Phase A interaction | 주소와 optional coupon을 선택하고 Cart의 최신 `version`과 금액을 재확인한다. CTA 활성화 후 첫 activation에서 중복 제출을 막고 동일 intention의 Idempotency-Key를 유지한다. 성공해야 Phase B를 연다. 주소/쿠폰/Cart가 바뀌면 이전 prepared context를 그대로 새 의도로 사용하지 않는다. |
| Phase B interaction | CheckoutResult가 있을 때만 Toss widget을 렌더한다. widget ready 후 `결제하기`로 provider approval을 시작한다. provider redirect 전 주문번호/결제 성공을 추정하지 않는다. back/refresh/provider cancel에서 approval을 자동 재전송하지 않는다. |
| hover/focus/navigation | 주소 추가/편집은 `/addresses`로 안전한 GET `returnTo`를 사용하고 복귀 후 사용자가 다시 선택·확인한다. `/billing-methods`로 이동을 요구하지 않는다. 외부 widget과 document focus 순서를 시각 순서에 맞춘다. |
| loading | 주소/coupon은 section skeleton. Phase A pending은 CTA 폭 유지+`준비 중`; 성공 후 Toss widget 고정 높이 placeholder. Widget 준비 전 Phase B CTA를 활성화하지 않는다. |
| empty | 주소 없음: `배송지를 먼저 추가해 주세요`. coupon 없음은 정상 상태이며 할인 없이 진행 가능. Cart 없음은 `/cart` 복귀. 저장 결제수단 없음 empty/blocker는 만들지 않는다. |
| error/retry | field 오류는 해당 field와 summary. `409 CART_CHANGED`는 Phase A 중단→최신 Cart→변경 핵심 요약→Cart/Checkout 재확인. widget load 실패는 Phase B section retry. provider 실패는 fail route, network/unknown은 성공·실패로 추정하지 않는다. |
| success | Phase A 성공은 `결제 준비 완료`이지 주문 결제 완료가 아니다. Toss redirect 뒤 success route에서 서버 confirm이 끝나야 결제 완료로 표시한다. |
| responsive | D8 SSOT: 1024px 이상 8/4 summary sticky, 1023px 이하 단일 열. 767px 이하 Phase A/B 각각 64px 금액+현재 CTA 하나만 사용하고 keyboard가 열리면 fixed CTA를 static으로 바꾼다. |
| accessibility | section `h2`, fieldset/legend, error summary anchor, widget iframe title, 금액 변경 polite live, pending `aria-disabled`와 실제 중복 방지. Phase A/B의 이름과 상태를 텍스트로 구분한다. |
| gap·impact | 한 페이지 section hierarchy, 두 단계 CTA/state, error summary, widget boundary, 409 diff panel, mobile CTA가 필요하다. API 변경 없음. |
| acceptance | 주소 없음/validation/cart changed/Phase A pending/CheckoutResult/widget 실패/provider pending/unknown/confirmed를 구분하고, 저장 billing method 없이도 현 계약대로 결제 흐름에 진입하며, 이중 클릭·back·refresh가 중복 주문/결제를 유발하지 않는다. |

### 결제 CTA 용어

이 Draft의 구현 제안은 Phase A를 **`주문 및 결제 준비`**, Phase B의 실제 Toss approval을 **`결제하기`**로 고정한다. 동일 문구로 두 단계를 합치지 않는다. 최종 Product Owner 승인은 전체 디자인 승인 과정에서 함께 확정한다.

## B3. Checkout Result `/checkout/success`, `/checkout/fail`

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | success는 Toss context/commerce API로 승인 결과를 확인하며 fail은 실패 복구를 제공한다. client redirect만으로 주문 완료를 확정할 수 없다. |
| 문제 | URL 진입 즉시 성공 confetti를 보이면 서버 확인 전 오확정할 수 있다. refresh, 늦은 응답, 이미 처리된 승인, 사용자가 닫았다 돌아온 경우가 모호하다. |
| 레퍼런스 | 외부 결제 결과 직접 증거 없음 `UNVERIFIED`; 서버 권위와 idempotency 원칙으로 설계한다. |
| 최종 IA | success route: `결제 확인 중`→확정 주문 요약→주문 상세/계속 쇼핑. fail route: 실패 제목→안전한 원인 설명→결제 결과 재확인/장바구니/지원. |
| visual hierarchy | 상태 icon+명시적 제목, 주문/금액, 다음 CTA 순서. 화려한 animation보다 확정 여부를 우선한다. |
| 컴포넌트 | `PaymentVerification`, `ConfirmedOrderSummary`, `PaymentFailurePanel`, `RecoveryActions`, `SupportLink`. |
| interaction | success refresh는 같은 거래 확인을 재조회하며 새 결제를 시작하지 않는다. fail의 `다시 시도`는 기존 승인 요청 재전송이 아니라 checkout 상태를 재확인한 뒤 사용자가 새 의도로 다시 진행한다. |
| navigation | 완료 CTA `/orders/[orderId]`, 보조 `/products`. fail은 유효한 server checkout context가 있으면 `/checkout`, 없으면 `/cart`; 양쪽 context가 없으면 `/orders`로 안내한다. |
| loading | `결제 결과를 확인하고 있어요`, progress indicator. 긴 대기는 상태 조회 retry를 제공한다. |
| empty | URL context 누락은 `결제 정보를 확인할 수 없어요`로 처리하고 주문 목록/지원 CTA. 성공으로 추정하지 않는다. |
| error/retry | 네트워크 확인 실패는 `결제 성공 여부를 아직 확인하지 못했어요`; 새 결제 금지, `결과 다시 확인`. 명시적 결제 실패와 구분한다. |
| success | 서버가 확정한 주문 ID·금액·배송 요약만 표시. 애니메이션은 reduced motion에서 제거. |
| responsive | 최대 760px 단일 열. 핵심 CTA full width, 상세 정보는 definition list. 320px에서도 긴 order ID wrap. |
| accessibility | 상태 icon은 장식 또는 accessible label 중 하나, heading으로 결과 전달, 자동 focus는 `h1`, live region 중복 낭독 금지. |
| gap·impact | verifying/confirmed/unknown/failed의 4상태 분리와 복구 CTA가 필요하다. |
| acceptance | URL 재방문·refresh·네트워크 실패가 중복 결제를 만들지 않고, 사용자가 확정/미확정/실패를 문구만으로 구별한다. |

## B4. Order List `/orders`

현재 `GET /api/orders`는 filter/page query를 받지 않고 `OrderSummary[]`를 반환한다. `OrderSummary`의 권위 필드는 `orderId`, `orderNumber`, `status`, `paymentAmount`, `createdAt`이다. 상품 요약도 목록 응답에 없다.

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | 인증된 주문 목록이 서버 반환 순서의 `OrderSummary[]`를 사용한다. `/my`에서도 최근 주문 요약을 사용한다. |
| 문제 | raw 상태 enum, 날짜 형식, 주문 없음과 목록 실패가 혼동될 수 있다. 지원되지 않는 기간/상태 filter, pagination, 대표 상품을 추가하면 API 변경이나 N+1 조회를 암묵적으로 요구한다. |
| 레퍼런스 | 직접 검증한 외부 account order list 없음 `UNVERIFIED`; `frontend/src/lib/commerce-final-api.ts`의 `GET /api/orders` 계약을 권위로 사용한다. |
| 최종 IA | heading+설명→서버가 반환한 주문 행 목록. 각 행은 주문 날짜, 주문번호, 고객 언어 상태, 결제 금액, `주문 상세` CTA만 표시한다. |
| visual hierarchy | 상태와 결제 금액이 1차, 날짜/주문번호가 2차. 행 divider 사용, card 중첩 금지. 상품명·상품 이미지·상품 수를 추정하지 않는다. |
| 컴포넌트 | `OrderList`, `OrderRow`, `OrderStatusBadge`. `OrderFilter`, order-list `Pagination`, infinite scroll은 사용하지 않는다. |
| interaction | 행 전체가 링크가 아니며 `주문 상세`가 명시적이다. 서버 반환 순서를 client에서 임의 재정렬하지 않는다. |
| navigation | detail로 이동 후 back 시 list scroll/focus 복원. filter/page 상태는 존재하지 않는다. anonymous는 안전한 login returnTo. |
| loading | 4행 skeleton. 기존 목록을 가진 refresh라면 마지막 성공 목록을 유지하고 busy 표시. |
| empty | 주문이 없으면 `아직 주문이 없어요`+`상품 둘러보기`. filter empty 개념은 만들지 않는다. |
| error/retry | 마지막 결과가 있으면 유지+inline retry, 최초 실패 section retry. 401은 재인증. |
| success | 서버가 반환한 총 행 수를 필요 시 polite live로 알리고 상태 label은 고객 언어로 표시한다. |
| responsive | desktop 행 grid, mobile 주문 단위 compact block. 주요 CTA 44px, 주문번호 wrap/copy는 지원할 때만. |
| accessibility | list semantics, heading level, 상태는 색+텍스트, 날짜 `<time>`, 상세 링크 이름에 주문번호 context. |
| gap·impact | enum formatter, empty 구분, history restore, mobile row가 필요하다. API 변경 없음. |
| acceptance | 주문 없음/실패를 구별하고 raw enum·ISO 문자열이 노출되지 않으며, 지원되지 않는 filter/pagination/상품 summary를 만들지 않고 detail back이 이전 위치를 보존한다. |

## B5. Order Detail `/orders/[orderId]`

### 정보 구조

1. 주문 상태와 핵심 다음 행동
2. 상품·금액
3. 배송 정보
4. 결제 정보
5. 취소·반품 상태/행동
6. 빠른 재주문 및 정기배송 옵션
7. 상황별 지원

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | 주문 상세, 취소·반품, quick reorder, order→subscription options를 사용한다. 재주문은 added/skipped의 부분 결과를 반환할 수 있다. |
| 문제 | 주문 상태, 취소/반품 가능 여부, 재주문, 정기배송 제안이 같은 CTA 수준이면 위험 행동과 다음 행동이 혼동된다. 주소·결제 정보가 raw data처럼 보일 수 있다. |
| 레퍼런스 | 외부 주문 상세 직접 증거 없음 `UNVERIFIED`; API `availableActions`와 제품 결정을 권위로 사용한다. |
| 최종 IA | breadcrumb→status header→상품/금액→배송→결제→취소·반품→재주문/구독→지원. 서버가 허용하지 않는 액션은 disabled로 꾸미지 않고 원인 설명과 함께 숨기거나 read-only 상태로 표시한다. |
| visual hierarchy | 상태 title과 다음 행동 1개가 1차. 취소/반품은 danger가 아닌 secondary destructive 스타일과 확인 dialog. 재주문은 상품 section 후. |
| 컴포넌트 | `OrderStatusHeader`, `OrderTimeline`, `OrderItemList`, `PriceBreakdown`, `DeliveryInfo`, `PaymentInfo`, `OrderActions`, `ReorderResult`, `SubscriptionOpportunity`, `ContextualSupport`. |
| interaction | 취소/반품은 범위·영향 설명 dialog→확인→pending. quick reorder는 Idempotency-Key 기반 1회 submit 후 부분 결과를 item별 표시. 구독 제안은 서버 options만 표시하고 임의 주기를 추천하지 않는다. |
| navigation | 주문 목록 breadcrumb, 재주문 성공 시 `/cart`, 구독 옵션은 `/subscriptions/new`다. 지원은 order ID context를 화면에서 보여주되 URL에 민감 정보를 넣지 않는다. |
| loading | status/summary skeleton 후 sections 독립. action 권한 확인 전 버튼 표시 금지. |
| empty | 주문 상품 없음은 정상 empty가 아니라 데이터 오류 panel. cancellation/return history 없음은 section을 축소한다. 구독 option 없음은 section 숨김. |
| error/retry | core order 실패 page retry/404. 각 action 실패는 dialog 닫지 않고 원인+retry. conflict는 최신 상태를 reload하고 이전 action이 더는 허용되지 않음을 설명한다. |
| success | 취소/반품은 새 status/timeline과 영구 confirmation. quick reorder는 `N개 담음, M개 제외`와 제외 이유·cart CTA. |
| responsive | D8 SSOT: 1024px 이상 8열 content+4열 status summary, 1023px 이하 single, timeline은 전 범위 vertical이다. 위험 확인은 mobile 중앙 modal, 취소·반품의 복잡 form은 full-screen dialog다. |
| accessibility | timeline ordered list, definition list, dialog focus trap, 파괴 확인 버튼에 대상·결과 포함, 부분 결과 summary heading. |
| gap·impact | available action 기반 CTA, partial reorder, conflict refresh, status formatter가 필요하다. API 변경 없음. |
| acceptance | 허용되지 않은 취소/반품을 실행할 수 없고, 부분 재주문이 전체 성공으로 오표현되지 않으며, action 후 최신 서버 상태가 페이지에 남는다. |

## B6. 재주문과 주문 후 정기배송 진입

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | 재주문 타이밍, quick reorder, 주문별 정기배송 옵션 API가 있다. 추천 주기는 자동 명령이 아니다. |
| 문제 | `다시 주문할 때`와 `정기배송으로 바꾸기`를 합치면 사용자가 즉시 구매와 반복 배송을 혼동한다. 제외 상품이 있는 재주문도 단일 성공 toast로 축소될 수 있다. |
| 레퍼런스 | PetSmart의 반복 배송 설명은 `INDIRECT/ADAPT`; PawCycle에서 지원하는 액션만 사용한다. |
| 최종 IA | 주문 상세의 `다시 담기`와 `정기배송으로 다시 받기`를 분리. 정기배송 CTA 아래에 플랜·주기는 다음 화면에서 최종 확인한다고 설명한다. |
| visual hierarchy | Recommended Default는 `다시 담기` primary, `정기배송으로 다시 받기` secondary다. PO가 신규 구독의 주 진입점을 확정하기 전까지 후자는 검토 기준이며 자동 할인·무료 배송을 보장하지 않는다. |
| 컴포넌트 | `ReorderTimingHint`, `QuickReorderButton`, `PartialResultPanel`, `SubscriptionOptionList`. |
| interaction | timing hint는 정보일 뿐 자동 action 없음. quick reorder는 Idempotency-Key를 사용한다. 구독 option 선택은 create flow로 이동하며 명령을 자동 실행하지 않는다. |
| navigation | `다시 담기` 성공은 `/cart`, `정기배송으로 다시 받기`는 `/subscriptions/new`로 이동한다. login 만료 시 현재 주문 GET detail로 복귀한 뒤 사용자가 다시 확인한다. |
| loading | 각 option 영역 독립. action 중 target만 disable. |
| empty | reorder 대상 없음은 이유+`상품 둘러보기`. subscription option 없음은 CTA를 숨기고 section에 `현재 정기배송 가능한 상품이 없어요`를 표시한다. |
| error/retry | 일부 상품 실패 item별. timing 실패는 action 차단 없이 hint 숨김. option 실패는 section retry. |
| success | 담긴/제외된 수와 이유, 다음 위치를 명확히 표시. 구독은 create 완료 전 성공 표현 금지. |
| responsive | mobile CTA stack, 결과 item 행. sticky는 한 화면에 하나의 primary만. |
| accessibility | action 이름에 주문/상품 context, 부분 결과 live summary, 자동 focus는 결과 heading. |
| gap·impact | 현재 기능을 분리된 계층과 부분 결과 panel로 재구성. |
| acceptance | 재주문과 구독 생성이 서로 다른 사용자 의도로 표현되고, 추천이 자동 변경처럼 보이지 않는다. |

## 거래 공통 상태 매트릭스

| 상태 | 화면 표현 | 허용 행동 |
| --- | --- | --- |
| 인증 확인 중 | 셸 유지, 거래 내용 skeleton | 제출 없음 |
| anonymous | 로그인 필요 설명 | 안전한 GET returnTo |
| submitting | 대상 CTA 진행 라벨, 중복 방지 | 취소가 안전할 때만 취소 |
| stale/conflict | 서버 최신 상태와 변경 요약 | 다시 확인 후 새 제출 |
| partial success | 성공/제외 item 분리 | cart 확인, 실패 이유 확인 |
| unknown payment | 성공·실패로 추정하지 않는 warning | 결과 다시 확인, 주문 목록, 지원 |
| confirmed | 영구 status/summary | 다음 route CTA |

## B 번들 검증 시나리오

- Cart 수량 연속 클릭, 느린 응답, 다른 탭 Cart 변경, 삭제 실패, 구매 불가 item.
- Checkout 주소 없음, coupon 없음/선택, Phase A double submit, cart changed, Toss widget 실패, back/refresh.
- success URL 직접 접근, confirm API 지연/실패, 이미 처리된 결제.
- 주문 0건, OrderSummary 필드만으로 목록 렌더링, partial reorder, action conflict, 구독 option 없음.
- 320/375/768/1024/1440, 200% 확대, keyboard-only, screen reader status announcement.

외부 벤치마크 관찰일: 2026-08-29. 확인 URL: `https://www.kurly.com/cart`.