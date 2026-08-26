# API-011 MVP4 구매 정합성 API delta

## 상태

- 작업 ID: `MVP4-BE-COMPLETE-001`
- 상태: 저장소 준비 구현
- 실행 구분: 저장소 변경만 수행

## Cart version

`GET /api/cart` 응답에 `version`을 추가한다. Cart item 추가, 실제 수량 변경, 실제 삭제와 결제 성공 후 Cart 소비처럼 Cart 내용이 바뀌는 경우에만 증가하며 no-op은 유지된다. 초기 Cart version은 `0`이다.

## Checkout

기존 `POST /api/checkout` request에 선택 필드 `cartVersion`을 추가한다.

- 새 Idempotency-Key의 최초 요청에서 전달된 `cartVersion`이 잠긴 현재 Cart version과 다르면 `409 CART_CHANGED`를 반환한다.
- 최초 요청에서 `cartVersion`을 생략하면 잠긴 현재 Cart version을 요청 identity로 사용한다.
- Idempotency 결과에는 `addressId`, `memberCouponId` 또는 `none`, 최초 요청 Cart version의 SHA-256 fingerprint와 최초 `request_cart_version`을 저장한다.
- 기존 Idempotency-Key가 있으면 현재 Cart stale 검증보다 먼저 저장된 최초 요청 identity를 검증한다.
- 같은 key·같은 address/coupon/최초 Cart version은 현재 Cart가 이후 변경되거나 결제 성공으로 소비되었어도 기존 결과를 replay한다.
- replay 요청에서 `cartVersion`을 생략하면 저장된 최초 `request_cart_version`을 사용해 identity를 검증한다.
- 같은 key에 다른 address, coupon 또는 명시적으로 다른 cartVersion을 사용하면 `409 IDEMPOTENCY_KEY_CONFLICT`다.
- fingerprint 또는 `request_cart_version`이 없는 legacy row는 잘못 replay하지 않고 `409 IDEMPOTENCY_KEY_CONFLICT`로 fail-close한다.
- replay/conflict 경로에서는 Order, Payment, Inventory reservation, Coupon reservation을 새로 만들지 않는다.

## Quick Reorder

`POST /api/orders/{orderId}/reorder`는 `Idempotency-Key`가 필수다.

응답은 `addedItems`, `skippedItems`, `cartVersion`을 포함한다. 본인 주문의 과거 `skuId`·`quantity`를 기준으로 현재 구매 가능 상태를 다시 검증하고, 가능한 item만 Cart에 추가한다. 품절 또는 판매 불가 item은 `skippedItems`에 포함하며 Inventory reserve/deduct는 수행하지 않는다.

Quick Reorder 결과는 회원·key·source order와 함께 저장한다. 같은 key·같은 source order는 최초 결과를 replay하고 Cart를 다시 변경하지 않으며, 다른 source order는 `409 IDEMPOTENCY_KEY_CONFLICT`다. 예상하지 못한 persistence/system 오류는 하나의 transaction에서 Cart와 결과 저장을 함께 rollback한다.

## Migration / transaction boundary

기존 migration은 수정하지 않는다. V22에서 `carts.version`, Checkout fingerprint와 Quick Reorder idempotency 결과 테이블을 추가하고, review correction의 V23에서 Checkout 최초 요청의 `request_cart_version`을 additive하게 추가한다. `CheckoutIdempotencyService`가 public Checkout의 request identity/replay 경계를 맡고, 최초 실행의 Order·Payment·Inventory·Coupon 처리는 기존 `CommerceService`를 재사용한다. 회원 row 잠금과 Cart row 잠금을 통해 최초 요청을 직렬화하며, 최초 Checkout 결과와 `request_cart_version` 저장은 하나의 transaction 경계에 둔다. Production migration이나 데이터 적용은 수행하지 않는다.
