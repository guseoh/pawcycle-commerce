# 현재 상태 전이 (주문·결제·재고·쿠폰·사후 처리·구독)

이 문서는 **현재 `main` 코드와 Flyway migration이 실제로 허용하는 상태와 전이**를 한곳에 모은 것이다. 과거 MVP 단계별 설계(DOMAIN-001~004, DATA-*, API-006·007·011)를 대체하지 않고, 지금 동작을 빠르게 찾기 위한 색인이다. 상태 값의 원본은 migration의 `CHECK` 제약이고, 전이 규칙의 원본은 각 절에 적은 코드다. 둘이 이 문서와 다르면 코드와 migration이 맞다.

- 확인 기준: `main` `70af0c3d`, 2026-11-14
- 공통 원칙: 상태 값은 Java enum이 아니라 문자열이며, DB `CHECK` 제약이 허용 값을 막는다.

## 1. 주문(`orders.status`)과 결제(`payments.status`)

| 테이블 | 허용 값 (V14) |
| --- | --- |
| `orders` | `CREATED`, `PAYMENT_PENDING`, `PAID`, `PAYMENT_FAILED`, `EXPIRED`, `PAYMENT_ACTION_REQUIRED` |
| `payments` | `READY`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `UNKNOWN` (`type`: `NORMAL`, `BILLING`) |

### 일반 결제(NORMAL) 흐름

```text
Checkout:  주문 PAYMENT_PENDING + 결제 READY, 재고 RESERVE, 쿠폰 RESERVED
Confirm:   [트랜잭션 1] READY → PROCESSING
           [트랜잭션 밖] Toss confirm 호출
           [트랜잭션 2] PROCESSING → SUCCEEDED (주문 PAID, 재고 DEDUCT, 쿠폰 USED, 배송 생성, 알림, 장바구니·멤버십 반영)
                        PROCESSING → FAILED   (주문 PAYMENT_FAILED)
                        PROCESSING → UNKNOWN  (결과를 알 수 없음)
만료:      READY + PAYMENT_PENDING 이 기한을 넘기면 재고 RELEASE, 쿠폰 복구, 주문·결제 EXPIRED
```

- 코드: `commerce/payment/application/PaymentApplicationService`, `commerce/checkout/application/CheckoutExpirationProcessor`(스케줄러 `CheckoutExpirationTrigger`)
- **대사(reconciliation):** `UNKNOWN`과 정기결제의 `PROCESSING`만 대상으로 한다. 결제사 조회는 트랜잭션 밖에서 하고, 완료 트랜잭션에서 잠금 상태를 다시 확인한다. 결과가 여전히 확정되지 않은 채 시도가 10회에 이르면 주문을 `PAYMENT_ACTION_REQUIRED`로 바꾸고 사용자에게 알림을 보낸다. 대사는 관리자 요청으로 실행되며 감사 로그(`PAYMENT_RECONCILE`)를 남긴다. (`commerce/payment/application/PaymentReconciliationService`)
- **고립된 `PROCESSING` 복구:** 일반 결제가 `PROCESSING`인 상태로 앱이 멈추면(결제사 승인 직후 포함) 대사 대상이 아니다. 결제사 조회로 같은 결제의 `DONE`이 확인되면, 유지보수 명령 `ProcessingPaymentRecoveryCommand`가 정상 확정과 같은 효과를 한 번 적용한다(`PROCESSING → SUCCEEDED`). 실패 확정은 아직 없다. [PAYMENT-RECOVERY-001](../runbook/PAYMENT-RECOVERY-001-processing-success.md), [#417](https://github.com/guseoh/pawcycle-commerce/issues/417)

### 정기결제(BILLING) 흐름

`SubscriptionBillingTrigger` → `SubscriptionBillingProcessor`가 `READY → PROCESSING`으로 바꾼 뒤 트랜잭션 밖에서 결제사를 호출한다. 실패하면 `SubscriptionBillingRetryProcessor`가 재시도한다. 재시도는 `FAILED` 상태이고, 시도 한도 안이며, 재고 예약을 다시 잡을 수 있을 때만 한다. 주문이 `PAYMENT_ACTION_REQUIRED`이면 재시도하지 않는다.

## 2. 재고(`inventories`, `inventory_movements`)

`inventories`는 `available_quantity`와 `reserved_quantity`를 가지며 둘 다 0 이상(`CHECK`)이다. 모든 변경은 SKU 행을 `PESSIMISTIC_WRITE`로 잠근 뒤 수행하고 `inventory_movements`에 이동 전후 수량을 기록한다.

| 이동 유형 | 의미 | available | reserved |
| --- | --- | --- | --- |
| `RESERVE` | 결제 대기 주문 확보 | −n | +n |
| `RELEASE` | 만료·실패로 확보 해제 | +n | −n |
| `DEDUCT` | 결제 성공으로 판매 확정 | 0 | −n |
| `CANCEL_RESTORE` | 결제 후 취소 복원 | +n | 0 |
| `RETURN_RESTORE` | 반품 입고 복원 | +n | 0 |
| `ADMIN_ADJUST`, `INBOUND` | 관리자 조정·입고 | ±n | 0 |

- 코드: `commerce/inventory/application/InventoryService`, `commerce/inventory/persistence/InventoryRepository`

## 3. 쿠폰(`member_coupons.status`)

`AVAILABLE → RESERVED`(Checkout) → `USED`(결제 성공). 만료·실패 시에는 `RESERVED → AVAILABLE`로 되돌린다. 할인 유형은 `FIXED_AMOUNT`, `PERCENTAGE`다(V13).

## 4. 배송·취소·반품·환불

| 테이블 | 허용 값 (V16) | 흐름 |
| --- | --- | --- |
| `deliveries` | `PREPARING`, `SHIPPED`, `DELIVERED`, `FAILED`, `CANCELLED` | 결제 성공 시 생성, 관리자·callback으로 진행 |
| `order_cancellations` | `REFUND_PENDING`, `COMPLETED` | 취소 요청 → 환불 완료 |
| `order_returns` | `REQUESTED`, `APPROVED`, `REJECTED`, `REFUND_PENDING`, `COMPLETED` | 요청 → 승인/거절 → 환불 대기 → 완료 |
| `refunds` | `READY`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `UNKNOWN` | 결제와 같은 3단계(준비 → 트랜잭션 밖 호출 → 확정). V19부터 0원 환불 허용 |

- 반품 입고는 재고를 복원하는 경우, 복원하지 않는 경우, 취소하는 경우를 구분한다. 결과가 불확실한 경우도 따로 다룬다(PR #493, Wiki PWC-0127).
- 코드: `commerce/cancellation`, `commerce/returning`, `commerce/refund`, `commerce/delivery`

## 5. 구독(`subscriptions.status`)과 배송 회차(`subscription_schedules.status`)

| 대상 | 값 |
| --- | --- |
| 구독 | `ACTIVE`, `PAUSED`, `CANCELED` |
| 회차 | `SCHEDULED`, `SKIPPED`, `HELD`, `CANCELED` (V3) |
| 회차 보류 사유 | `MISSING_SHIPPING_ADDRESS`, `MISSING_BILLING_METHOD`, `PAYMENT_RETRY_EXHAUSTED`, `PAYMENT_RETRY_STOCK_UNAVAILABLE`, `ORDER_STOCK_UNAVAILABLE` (V26) |

### 구독 명령 (`SubscriptionCommandType`)

| 명령 | 허용 상태 | 결과 |
| --- | --- | --- |
| `PAUSE` | `ACTIVE` | 구독 `PAUSED`, 다음 `SCHEDULED` 회차 `HELD`, 배송 알림 삭제 |
| `RESUME` | `PAUSED` | `HELD` 회차를 오늘 + 주기부터 겹치지 않게 재배치, 구독 `ACTIVE` |
| `CANCEL` | `ACTIVE`, `PAUSED` | 구독 `CANCELED`, 주문되지 않은 회차 취소, 대기 플랜 변경·애드온·알림 삭제 |
| `SKIP_NEXT` | `ACTIVE` | 다음 회차 `SKIPPED`, 다음 미래 날짜에 새 회차 생성, 애드온·대기 변경 이동 |
| `CHANGE_PLAN`, `CHANGE_DELIVERY_CYCLE`, `RESCHEDULE_NEXT` | 명령별 검사 | 플랜·주기·다음 날짜 변경 |
| `SET_NEXT_DELIVERY_ADDON`, `REMOVE_NEXT_DELIVERY_ADDON` | `ACTIVE` | 다음 회차 애드온(수량 1~10) |

- 동시성: 명령은 `If-Match` 버전 검사와 멱등성 replay를 거친 뒤 `lockOwnedSubscription`으로 구독 행을 잠그고, 한 트랜잭션에서 전이·이력·응답 저장을 끝낸다. 행 잠금은 원 설계(ARCH-007)와 다르지만, 첫 구현(#87)부터 쓰였고 ARCH-011에서 승인됐다([ADR 색인](../adr/README.md)).
- 자동 주문: `SubscriptionOrderAutomationTrigger` → `SubscriptionOrderProcessor`가 기한이 된 회차마다 `REQUIRES_NEW` 트랜잭션으로 주문을 하나씩 만든다(ARCH-008). 회차 정합성은 `ScheduleReconciliationTrigger`가 맞춘다.
- 코드: `subscription/application/SubscriptionCommandApplicationService`, `subscription/automation/*`

## 6. 스케줄러 목록

| 스케줄러 | 역할 | 기본 주기 |
| --- | --- | --- |
| `CheckoutExpirationTrigger` | 결제 대기 주문 만료 | 60초 |
| `SubscriptionBillingTrigger` | 정기결제 실행 | 60초 |
| `SubscriptionOrderAutomationTrigger` | 구독 회차 주문 생성 | 60초 |
| `ScheduleReconciliationTrigger` | 회차 정합성 보정 | 60초 |
| `SubscriptionDeliveryReminderProcessor` | 배송 예정 알림 | 60초 |
| `CommerceMetrics`, `SubscriptionMetrics` | 운영 지표 갱신 | 설정값 |

모든 스케줄러는 인스턴스마다 독립적으로 돈다. 분산 잠금이 없으므로, 두 인스턴스가 동시에 떠 있으면 같은 작업을 함께 실행한다. 이때의 정합성은 각 작업의 DB 잠금과 CAS에 기대며, 아직 검증하지 않았다([알려진 위험](../architecture/known-risks.md)).
