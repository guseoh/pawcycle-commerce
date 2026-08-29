# MVP4-UX-004 구현 계약 정합성 교정 기록

상태: `Historical Delta Record / Draft Design Work`

## 목적과 현재 지위

이 문서는 MVP4-UX-004 설계 과정에서 실제 `main` API·상태 계약과 대조해 발견한 정합성 correction을 기록한다.

과거에는 이 문서를 A–D보다 우선하는 delta override로 사용했지만, **2026-08-29 최종 통합 correction에서 유효한 내용이 A/B/C/D 본문에 흡수됐다.** 따라서 현재 구현/검토에서 이 문서는 권위 override가 아니라 **변경 이유를 추적하는 역사 기록**이다.

현재 화면 계약:

1. `MVP4-UX-004-customer-commerce-redesign.md`
2. A/B/C 화면 계약
3. D 공통 SSOT
4. Visual + Interaction Supplement
5. Benchmark evidence
6. 이 역사 기록

이 문서의 오래된 설명이 최신 A–D와 충돌하면 **A–D를 따른다.**

## 최초 API 정합성 correction

### New Subscription

잘못된 초기 설계:

`상품·수량 → 첫 배송일 → 배송지 → 결제수단 → 생성`

실제 V2 create 입력:

- `petId`
- `planVersionId`
- `deliveryCycleWeeks`

현재 A–D 반영:

`Pet → compatible on-sale Plan → allowed cycle → summary → idempotent create → server Detail`

첫 배송일·배송지·Billing·Plan item quantity는 create 입력이 아니다.

### Billing

잘못된 초기 설계:

- 결제수단 list
- default
- delete
- masked brand/card display

실제 계약:

- provider `configured`
- provider `registered`
- `prepareBilling`

현재 C8은 provider 상태/prepare만 표현하고 Checkout selector로 사용하지 않는다.

### Checkout / Toss

초기 설계는 Toss widget과 Checkout context 생성을 한 단계처럼 읽을 수 있었다.

현재 B2:

```text
Phase A
Cart + address + optional coupon + cartVersion
→ POST /api/checkout
→ CheckoutResult

Phase B
CheckoutResult
→ Toss widget
→ 결제하기
→ redirect
→ server confirmation
```

Phase A와 provider approval의 상태/CTA를 분리한다.

### PDP Option / SKU

현재 Product Detail은 `optionGroups[]`와 SKU별 `selectedOptions[]`를 제공한다.

A5는 다음을 본문 계약으로 가진다.

- option 없음+단일 purchasable SKU → 즉시 SKU 선택
- option 있음 → group별 선택→selectedOptions 대조→정확한 SKU 결정
- impossible combination disabled
- SKU별 price/compareAt/discount/stock/purchasable 권위 사용
- quantity/Cart CTA는 SKU 결정 후 활성화

### Review / Q&A

초기 Draft에서 mutation CTA가 빠질 위험이 있었으나 실제 API가 지원한다.

A5에 반영:

- Review list / myReview / create / update / delete
- Question list / create
- auth returnTo, pending/error/input preservation
- Review delete confirm

### Order List

`GET /api/orders`는 filter/page query가 없고 `OrderSummary[]`를 반환한다.

현재 B4는:

- orderId/orderNumber/status/paymentAmount/createdAt
- Detail navigation

만 사용한다.

기간/status filter, pagination, representative product summary는 만들지 않는다.

## 최종 통합 correction에서 추가로 발견한 항목

### Cart

실제 Checkout request에는 selected item IDs가 없다. Cart coupon mutation도 없다.

현재 B1:

- server Cart 전체 Checkout
- quantity/delete/version/pricing
- selection/selected subtotal/selected checkout 제거
- Cart coupon 제거
- Cart thumbnail 존재 가정 제거

### Subscription List vs Detail

`V2SubscriptionSummary`에는 `issue`, `availableActions`, `nextDelivery`가 없다.

현재 C1은 Summary-only list로:

- ACTIVE
- PAUSED
- CANCELED
- Pet/currentSnapshot/nextScheduledDate

만 사용한다.

Detail-only issue/action을 위해 N개의 Detail request를 만들지 않는다.

### Subscription `CHANGE_PLAN`

실제 command를 C2에 정식 반영했다.

- current Pet 호환
- on-sale/current PlanVersion
- effective cycle 지원
- `planVersionId` 중심 body
- legacy pet 보완은 서버 요구 시만
- add-on conflict 분리
- HELD schedule mutation 제한
- Plan 기본 item quantity edit 금지

### Subscription shipping

Subscription에 저장 addressId를 직접 연결하는 API가 아니다.

현재 C2:

`저장 주소 선택 → AddressRequest draft 복사 → 사용자 확인 → full AddressRequest PUT`

을 사용한다.

### Wishlist

`WishlistItem`은 `productId`, `productName`, `createdAt`만 제공한다.

A7은 full Product Card 가정을 제거하고 compact row를 사용한다.

삭제는 server DELETE 성공 뒤 UI에서 제거하고 실제 POST add API를 이용한 6초 Undo를 제공한다.

### PLP Quick Add

Product List 응답만으로 option 필요 여부를 확정할 수 없다.

A4/D7은 representative SKU quick add를 제거하고 PDP에서 SKU를 확정하도록 한다.

### Search copy

`q`는 product name/short description/description이다. Brand/category는 별도 filter다.

A1 placeholder는 `상품명 또는 설명 검색`으로 축소했다.

## 문서 정합성 correction

최종 통합 과정에서 다음 stale 값도 A–D에서 제거했다.

- PLP 5열
- 1024–1199 header 120px
- Cart sticky top 96px
- Address desktop modal 760px
- Pet DeleteConfirm
- Notification readAll 조건부 표현
- HELD/ENDED top-level Subscription state
- Checkout saved Billing Method requirement
- Cart selection/coupon
- OrderFilter/Order pagination
- 모든 PDP에서 option 선택 강제

## 보존하는 핵심 설계 원칙

- URL 기반 PLP 상태와 stale guard
- category/filter expand-collapse와 focus return
- PLP↔PDP scroll/focus restore
- server-authoritative price/state
- Cart version conflict
- Checkout/Reorder/Subscription idempotency
- payment verifying/confirmed/unknown/failed
- Subscription ETag/If-Match
- 날짜/주기/Plan command 분리
- independent async sections
- 320px/200% zoom/reduced motion
- card 남용 제거와 continuous PDP

`NO FRONTEND IMPLEMENTATION PERFORMED`