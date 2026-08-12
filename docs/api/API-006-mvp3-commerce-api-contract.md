# API-006 MVP3 Commerce API 계약

## 상태

- 작업 ID: `MVP3-COMMERCE-002`
- 상태: Approved input 구현
- 실행 구분: 저장소 준비만

## 회원 Commerce API

| Method | URI | 설명 |
| --- | --- | --- |
| GET, POST | `/api/cart`, `/api/cart/items` | 장바구니 조회와 SKU 추가 |
| PATCH, DELETE | `/api/cart/items/{skuId}` | 수량 변경과 삭제 |
| GET, POST, DELETE | `/api/wishlist`, `/api/wishlist/{productId}` | 위시리스트 |
| GET, POST | `/api/addresses` | 배송지 조회와 생성 |
| PATCH, DELETE | `/api/addresses/{addressId}` | 소유 배송지 수정과 삭제 |
| PUT | `/api/addresses/{addressId}/default` | 기본 배송지 지정 |
| POST | `/api/checkout` | `Idempotency-Key` 필수, `addressId`, 선택 `memberCouponId` |
| POST | `/api/payments/toss/confirm` | `paymentKey`, `providerOrderId`, `amount` 확인 |
| GET | `/api/orders`, `/api/orders/{orderId}` | 본인 주문 조회 |
| POST | `/api/payment-methods/toss/billing/prepare`, `/complete` | Billing 준비와 등록 |
| PUT | `/api/subscriptions/{subscriptionId}/shipping-address` | 미래 미처리 Schedule에만 적용되는 구독 배송지 snapshot 변경 |

Checkout 응답은 `orderId`, `orderNumber`, `paymentId`, `providerOrderId`, `orderName`, `amount`를 포함한다. Cart는 가격·재고를 예약하지 않으며 Checkout에서 PUBLIC Product, ACTIVE SKU, 현재 가격과 재고를 다시 검증한다.

`/complete`는 opaque `prepareToken`, Toss `authKey`만 받고 `customerKey`, `billingKey`를 응답·로그·예외에 포함하지 않는다. 이 저장소 구현은 network를 호출하지 않는 Toss Sandbox adapter만 제공한다.

## Admin API

`ADMIN` 권한이 필요하다.

| Method | URI | 설명 |
| --- | --- | --- |
| GET, POST | `/api/admin/inventories`, `/api/admin/inventories/{skuId}/adjustments` | 재고 조회 및 delta 조정 |
| GET, POST | `/api/admin/coupons` | 쿠폰 조회와 생성 |
| PATCH | `/api/admin/coupons/{couponId}` | 쿠폰 변경 |
| POST | `/api/admin/coupons/{couponId}/issues` | 회원 쿠폰 발급 |
| GET, POST | `/api/admin/membership-grades` | 등급 조회와 생성 |
| POST | `/api/admin/members/{memberId}/membership/evaluate` | 최근 12개월 PAID 주문 기준 평가 |

## Category 및 Subscription 보류

- Product 생성은 활성 실제 Category가 필수다. 시스템 `uncategorized` Category는 legacy backfill 전용이며 신규 지정 또는 되돌리기에 사용할 수 없다.
- due Schedule은 구독 배송지 snapshot과 ACTIVE BillingPaymentMethod를 먼저 확인한다. 없으면 각각 `HELD/MISSING_SHIPPING_ADDRESS`, `HELD/MISSING_BILLING_METHOD`로 전환하며 Order·Payment·Inventory reservation을 만들지 않는다.
- 배송지 snapshot은 주문의 주소 snapshot과 독립적이다. 회원 주소 변경은 기존 구독 snapshot이나 생성된 주문을 변경하지 않는다.
- `UNKNOWN` 결제는 reservation과 쿠폰을 유지하며 재시도 결제로 변환하지 않는다. reconciliation은 최대 10회 구조만 제공하며 실제 scheduler 실행은 이 작업 범위 밖이다.
