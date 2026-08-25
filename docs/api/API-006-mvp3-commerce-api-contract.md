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

Checkout 응답은 `orderId`, `orderNumber`, `paymentId`, `providerOrderId`, `orderName`, `amount`, `tossTestEnabled`를 포함한다. `tossTestEnabled`는 현재 Backend가 실제 Toss Test adapter로 confirm하도록 명시적으로 활성화되었는지를 나타내는 서버 권위 값이다. Frontend는 이 값이 `true`이고 로컬 `test_ck_` client key가 유효할 때만 Toss Test Browser UI를 시작한다. Fake sandbox adapter가 활성화된 경우 실제 Toss Browser 결제를 시작하지 않는다.

Cart는 가격·재고를 예약하지 않으며 Checkout에서 PUBLIC Product, ACTIVE SKU, 현재 가격과 재고를 다시 검증한다. Checkout은 회원 단위로 직렬화하여 동일 `Idempotency-Key`의 동시 요청도 기존 결과를 재생한다. Cart 차감은 결제 성공 시 Order에 포함된 수량만 반영한다.

Toss confirm의 성공·실패·미확정 및 동일 요청 replay 응답은 기존 `paymentId`, `status`에 `orderId`를 additive하게 포함한다. Frontend는 confirm 요청의 금액으로 Checkout 응답의 서버 금액을 사용하고, Toss redirect URL의 금액은 일치성 검증에만 사용한다.

Toss Test Browser 연동은 `local-integration`에서 명시적 opt-in으로만 허용한다. Backend는 `PAWCYCLE_TOSS_TEST_ENABLED=true`와 `test_sk_` secret이 모두 유효할 때 실제 Toss Test adapter를 선택하며, live secret은 거부한다. Frontend client key는 `NEXT_PUBLIC_TOSS_TEST_CLIENT_KEY`로만 주입하며 `test_ck_`가 아니면 결제 위젯을 열지 않는다. 실제 Provider endpoint는 공식 Toss API 주소로 고정하며 Secret과 실제 key 값은 코드·문서·로그·PR에 기록하지 않는다.

일반 Checkout의 READY Payment는 생성 시점부터 30분의 만료 시각을 가진다. 만료 전 confirm되지 않은 주문은 멱등적으로 `EXPIRED` 처리하며 Inventory reservation과 예약 Coupon을 반환한다. PROCESSING/UNKNOWN Payment는 이 만료 처리 대상이 아니다.

`/complete`는 opaque `prepareToken`, Toss `authKey`만 받고 `customerKey`, `billingKey`를 응답·로그·예외에 포함하지 않는다. Sandbox adapter는 `local-integration` 프로필에서만 활성화한다. 그 외 환경에 실제 Provider가 구성되지 않은 경우 결제 confirm과 Billing 등록은 fail-closed로 종료하며 가짜 결제 성공을 만들지 않는다.

## Admin API

`ADMIN` 권한이 필요하다.

| Method | URI | 설명 |
| --- | --- | --- |
| GET, POST | `/api/admin/inventories`, `/api/admin/inventories/{skuId}/adjustments` | 재고 조회 및 delta 조정, `delta=0`은 400 |
| GET, POST | `/api/admin/coupons` | 쿠폰 조회와 생성 |
| PATCH | `/api/admin/coupons/{couponId}` | 쿠폰 변경 |
| POST | `/api/admin/coupons/{couponId}/issues` | 회원 쿠폰 발급 |
| GET, POST | `/api/admin/membership-grades` | 등급 조회와 생성 |
| POST | `/api/admin/members/{memberId}/membership/evaluate` | 최근 12개월 PAID 주문 기준 평가, 존재하지 않는 회원은 404 |

## Category 및 Subscription 보류

- Product 생성은 활성 실제 Category가 필수다. 내부 시스템 slug `__pawcycle_uncategorized__` Category는 legacy backfill 전용이며 신규 지정 또는 되돌리기에 사용할 수 없다. 이 slug는 일반 Category API의 slug 형식으로 생성할 수 없다.
- due Schedule은 구독 배송지 snapshot과 ACTIVE BillingPaymentMethod를 먼저 확인한다. 없으면 각각 `HELD/MISSING_SHIPPING_ADDRESS`, `HELD/MISSING_BILLING_METHOD`로 전환하며 Order·Payment·Inventory reservation을 만들지 않는다.
- 배송지 snapshot은 주문의 주소 snapshot과 독립적이다. 회원 주소 변경은 기존 구독 snapshot이나 생성된 주문을 변경하지 않는다.
- Billing retry는 같은 Order에 `attempt_no`별 최대 한 Payment만 허용한다. 동일 실패 Payment의 중복 retry 요청은 이미 생성된 다음 attempt를 재사용한다.
- `UNKNOWN` 결제는 reservation과 쿠폰을 유지하며 재시도 결제로 변환하지 않는다. reconciliation은 최대 10회이며 10회 이후 호출은 상태를 더 증가시키지 않는다. 실제 reconciliation scheduler 실행은 이 작업 범위 밖이다.
