# 현재 데이터 스키마 개요

이 문서는 현재 테이블을 업무 영역별로 묶은 색인이다. **스키마의 원본은 `backend/src/main/resources/db/migration`의 Flyway migration이다.** 컬럼·제약·인덱스는 migration을 직접 본다. 과거 설계 문서(DATA-001~004, MVP4-DATA-*)는 당시 설계 기록이다.

- 확인 기준: `main` `70af0c3d`, migration V1~V28, 테이블 61개
- 상태 값과 전이는 [상태 전이 문서](../domain/state-machines.md)를 본다.
- 이 목록은 migration의 `CREATE TABLE`과 대조해 빠진 테이블이 없음을 확인하고 작성했다.

## 회원·인증

코드: `member, subscription(pets)`

| 테이블 | 생성 migration |
| --- | --- |
| `members` | V1 |
| `member_addresses` | V13 |
| `pets` | V3 |

## 카탈로그

코드: `catalog`

| 테이블 | 생성 migration |
| --- | --- |
| `brands` | V24 |
| `categories` | V12 |
| `category_facets` | V24 |
| `facet_definitions` | V24 |
| `facet_options` | V24 |
| `products` | V1 |
| `product_images` | V24 |
| `product_option_groups` | V24 |
| `product_option_values` | V24 |
| `product_facet_values` | V24 |
| `product_detail_sections` | V21 |
| `skus` | V1 |
| `sku_option_values` | V24 |

## 참여·추천 신호

코드: `catalog/engagement, commerce/wishlist, interaction`

| 테이블 | 생성 migration |
| --- | --- |
| `reviews` | V21 |
| `product_review_summaries` | V27 |
| `product_questions` | V21 |
| `wishlist_items` | V13 |
| `interaction_events` | V25 |

## 장바구니·Checkout·주문·결제

코드: `commerce/cart, checkout, order, payment`

| 테이블 | 생성 migration |
| --- | --- |
| `carts` | V13 |
| `cart_items` | V13 |
| `checkout_idempotency_results` | V14 |
| `quick_reorder_idempotency_results` | V22 |
| `orders` | V14 |
| `order_items` | V14 |
| `payments` | V14 |

## 재고·쿠폰·배송

코드: `commerce/inventory, coupon, delivery`

| 테이블 | 생성 migration |
| --- | --- |
| `inventories` | V13 |
| `inventory_movements` | V14 |
| `coupons` | V13 |
| `member_coupons` | V13 |
| `deliveries` | V16 |

## 취소·반품·환불

코드: `commerce/cancellation, returning, refund`

| 테이블 | 생성 migration |
| --- | --- |
| `order_cancellations` | V16 |
| `order_returns` | V16 |
| `refunds` | V16 |

## 정기결제 수단

코드: `commerce/billing`

| 테이블 | 생성 migration |
| --- | --- |
| `billing_payment_methods` | V14 |
| `billing_payment_method_preparations` | V14 |

## 멤버십·알림·감사

코드: `commerce/membership, notification, audit`

| 테이블 | 생성 migration |
| --- | --- |
| `membership_grades` | V13 |
| `member_memberships` | V13 |
| `membership_histories` | V13 |
| `notifications` | V17 |
| `admin_audit_logs` | V17 |

## 구독 플랜

코드: `subscription`

| 테이블 | 생성 migration |
| --- | --- |
| `subscription_plans` | V3 |
| `plan_versions` | V3 |
| `plan_items` | V3 |
| `plan_version_delivery_cycles` | V3 |

## 구독·회차

코드: `subscription`

| 테이블 | 생성 migration |
| --- | --- |
| `subscriptions` | V2 |
| `subscription_snapshots` | V3 |
| `subscription_snapshot_items` | V3 |
| `subscription_schedules` | V3 |
| `subscription_schedule_addons` | V26 |
| `pending_plan_changes` | V3 |
| `subscription_shipping_snapshots` | V14 |

## 구독 명령·멱등성

코드: `subscription`

| 테이블 | 생성 migration |
| --- | --- |
| `subscription_command_history` | V3 |
| `subscription_command_idempotency_results` | V3 |
| `subscription_creation_idempotency_results` | V3 |

## 구독 주문

코드: `subscription, commerce/billing`

| 테이블 | 생성 migration |
| --- | --- |
| `subscription_orders` | V9 |
| `subscription_order_items` | V10 |
| `subscription_order_addon_items` | V26 |
| `subscription_order_context` | V14 |

## 데이터 원칙

- 상태 값은 문자열이며 `CHECK` 제약으로 허용 값을 막는다.
- 주문·결제·구독 이력은 snapshot 테이블(`order_items`, `subscription_snapshots`, `subscription_shipping_snapshots` 등)에 당시 값을 보존하며, 현재 상품·플랜 값으로 소급 변경하지 않는다.
- 구독 멱등성 결과는 `subscription_*_idempotency_results`에 저장한다. 30일 보존 뒤 삭제하는 로직(`SubscriptionIdempotencyCleanupService`)은 있지만, **런타임에서 이를 호출하는 스케줄러는 없다**(OPS-IDEMP-001에서 보류). `SubscriptionMetrics`는 정리 대상 건수만 집계한다. [알려진 위험 R9](../architecture/known-risks.md)
- JPA는 `validate` 모드로 동작하며 스키마를 만들거나 바꾸지 않는다(ARCH-009).
