# API-008 MVP4 정기배송 직접 관리 API 계약

- 작업 ID: `MVP4-SUB-BE-001`
- 상태: Approved Input 구현 계약
- 등급: 고위험
- 실행 구분: 저장소 준비만
- 기준 계약: [API-004](API-004-second-mvp-api-contract.md)

## 공통 delta

기존 `/api/v2/subscriptions/{id}/commands/{command}` endpoint, session·CSRF·소유권, `Idempotency-Key`, `If-Match`, `ETag`, replay header와 오류 shape를 유지한다. 새 명령도 `Member + Subscription ID + command type + Idempotency-Key` scope를 사용하고 성공 replay를 version 검사보다 먼저 판정한다. 모든 성공 명령은 기존과 같이 `200 OK` 최신 Subscription 상세를 반환한다.

## 새 명령

| command | body | 성공 조건과 결과 |
| --- | --- | --- |
| `RESCHEDULE_NEXT` / `reschedule-next` | `{ "scheduledDate": "2026-09-10" }` | ACTIVE, Order 미생성 다음 SCHEDULED만 같은 ID로 날짜 변경. 날짜는 Asia/Seoul 오늘보다 미래이며 같은 Subscription의 기존 Schedule 날짜와 달라야 한다. |
| `CHANGE_DELIVERY_CYCLE` / `change-delivery-cycle` | `{ "deliveryCycleWeeks": 8 }` | ACTIVE, 2·4·8주만 허용. pending PlanVersion이 있으면 그 version, 없으면 current PlanVersion이 요청 주기를 지원해야 한다. 현재 다음 배송일과 적용 주기는 유지하고 다음 회차용 단일 pending snapshot을 교체한다. |

`CHANGE_PLAN`은 pending이 있으면 그 snapshot의 배송 주기를 유지한 채 PlanVersion·가격·구성만 바꾼다. `CHANGE_DELIVERY_CYCLE`은 pending의 PlanVersion·가격·구성을 유지한 채 주기만 바꾼다. 두 명령의 순서와 관계없이 pending row는 하나다.

추가 오류는 다음과 같다.

| HTTP | code | 조건 |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | 날짜 형식 또는 `deliveryCycleWeeks` JSON 타입이 잘못됨 |
| 409 | `SCHEDULE_DATE_NOT_FUTURE` | 요청 날짜가 Asia/Seoul 오늘 또는 과거 |
| 409 | `SCHEDULE_DATE_CONFLICT` | 요청 날짜가 같은 Subscription의 기존 Schedule 날짜와 같음 |
| 409 | `DELIVERY_CYCLE_NOT_ALLOWED` | 2·4·8주가 아니거나 적용될 PlanVersion이 요청 주기를 지원하지 않음 |
| 409 | `SUBSCRIPTION_COMMAND_NOT_ALLOWED` | 상태 또는 Order 미생성 SCHEDULED 부재로 명령을 적용할 수 없음 |

기존 `409 IDEMPOTENCY_KEY_REUSED`, `412 SUBSCRIPTION_VERSION_MISMATCH`, `428 IF_MATCH_REQUIRED` 계약은 그대로 적용한다.

## 상세 응답 additive projection

`GET /api/v2/subscriptions/{subscriptionId}`와 명령 성공 상세은 기존 `currentSnapshot`, `pendingSnapshot`, `nextScheduledDate`, `schedules`, `commandHistory`를 유지하고 다음 필드를 추가한다.

```json
{
  "nextDelivery": {
    "scheduleId": 701,
    "scheduledDate": "2026-09-10",
    "status": "SCHEDULED",
    "planVersionId": 31,
    "packagePriceKrw": 45900,
    "deliveryCycleWeeks": 8,
    "items": [{
      "skuId": 2001,
      "skuName": "대용량",
      "productId": 301,
      "productName": "성견 사료",
      "thumbnailUrl": "https://cdn.example.test/products/301.png",
      "quantity": 2
    }]
  },
  "pendingChange": {
    "targetScheduleId": 701,
    "appliesOn": "2026-09-10",
    "planVersionId": 31,
    "packagePriceKrw": 45900,
    "deliveryCycleWeeks": 8,
    "items": []
  },
  "issue": null,
  "availableActions": [
    "CHANGE_PLAN",
    "CHANGE_DELIVERY_CYCLE",
    "RESCHEDULE_NEXT",
    "SKIP_NEXT",
    "PAUSE",
    "CANCEL",
    "UPDATE_SHIPPING_ADDRESS"
  ]
}
```

- `nextDelivery`는 ACTIVE의 가장 가까운 Order 미생성 SCHEDULED 또는 해결되지 않은 HELD 회차다. pending target이면 pending snapshot, 이미 처리 중인 HELD 회차이면 `effective_snapshot_id`, 그 밖에는 current snapshot을 표시한다. PAUSED·CANCELED에는 `null`이다.
- `pendingChange`는 pending이 없으면 `null`이다. `appliesOn`은 target Schedule 날짜이므로 RESCHEDULE_NEXT·SKIP_NEXT·RESUME 뒤 실제 target 날짜를 반영한다.
- `thumbnailUrl`은 Product에 값이 없으면 `null`이다. 상품·SKU 표시 정보는 현재 Catalog projection이며 과거 주문 snapshot 계약을 바꾸지 않는다.

## issue 변환

내부 `hold_reason` 문자열은 응답하지 않는다.

| 내부 값 | 사용자 `issue.code` | 사용자 message |
| --- | --- | --- |
| `MISSING_SHIPPING_ADDRESS` | `SHIPPING_ADDRESS_REQUIRED` | 배송지를 등록해 주세요. |
| `MISSING_BILLING_METHOD` | `BILLING_METHOD_REQUIRED` | 결제 수단을 등록해 주세요. |
| `PAYMENT_RETRY_EXHAUSTED` | `PAYMENT_SUPPORT_REQUIRED` | 결제를 완료하지 못했습니다. 고객 지원에 문의해 주세요. |
| `PAYMENT_RETRY_STOCK_UNAVAILABLE` | `STOCK_UNAVAILABLE` | 재고를 확보하지 못해 배송이 보류되었습니다. |

해당 issue가 없으면 `issue`는 `null`이다.

## availableActions

| 상태와 다음 회차 | actions |
| --- | --- |
| ACTIVE + Order 미생성 SCHEDULED | `CHANGE_PLAN`, `CHANGE_DELIVERY_CYCLE`, `RESCHEDULE_NEXT`, `SKIP_NEXT`, `PAUSE`, `CANCEL`, `UPDATE_SHIPPING_ADDRESS` |
| ACTIVE + HELD + `MISSING_SHIPPING_ADDRESS` | `UPDATE_SHIPPING_ADDRESS`, `CANCEL` |
| ACTIVE + HELD + `MISSING_BILLING_METHOD` | `REGISTER_BILLING_METHOD`, `CANCEL` |
| ACTIVE + 그 밖의 HELD 또는 실행 가능한 SCHEDULED 없음 | `CANCEL` |
| PAUSED | `RESUME`, `CANCEL`, `UPDATE_SHIPPING_ADDRESS` |
| CANCELED | 빈 배열 |

`UPDATE_SHIPPING_ADDRESS`는 기존 `PUT /api/subscriptions/{subscriptionId}/shipping-address`를 사용한다. `REGISTER_BILLING_METHOD`는 기존 Toss Billing Method 등록 흐름을 사용한다. 두 action은 별도 retry 명령이 아니라 기존 선행조건 보완 경로이며, 배송지·결제수단 보완 후 기존 Commerce 로직이 해당 HELD를 정상화한다.

Backend가 실제 명령 선행 조건과 기존 Commerce 복구 경계에 맞춰 `availableActions`를 결정한다. 새 Subscription HELD 상태나 별도 retry action은 추가하지 않는다.

## transaction·자동 주문 delta

Scheduler와 사용자 명령은 기존 Subscription→Schedule lock 순서를 유지한다. pending 적용 회차에서는 Order·Schedule effective snapshot, current snapshot pointer, `subscriptions.delivery_cycle_weeks`, pending 제거와 다음 Schedule 계산을 같은 target transaction에서 완료한다. 다음 Schedule은 실제 effective snapshot의 주기로 계산한다. pending 적용 전에는 `subscriptions.delivery_cycle_weeks`를 변경하지 않는다.

새 DB table·column·Flyway migration, command history payload 확장, 의존성, Queue·Kafka·Redis, 별도 retry API와 Production 실행은 없다.
