# MVP4-FINAL-001 Backend → Frontend handoff

이번 변경은 backend API와 data contract만 포함한다. Frontend/Admin login `returnTo`와 기존 command 흐름은 변경하지 않는다.

## Endpoints

- Pet: `PATCH /api/v2/pets/{petId}`; 기존 `GET /api/v2/pets`, `GET /api/v2/pets/{petId}`, Subscription detail의 Pet에 `breed`, `weightKg`, `profileComplete`가 additive하게 온다. PATCH는 요청에 실제로 포함된 `name`, `breed`, `weightKg`만 갱신하며 omitted field는 보존한다. `breed:null`과 `weightKg:null`은 해당 값을 비운다.
- Interaction: 인증 후 `POST /api/interactions`; `events` 최대 50개, 성공 204. `PRODUCT_IMPRESSION`·`PRODUCT_VIEW`는 `productId`, 추천 impression/click은 `productId`와 `recommendationRequestId`가 필수다. `SEARCH`·`FILTER` context는 승인된 구조화 값만 허용한다.
- Personalized: 기존 `GET /api/recommendations/products?petId=`; response에 `requestId`, `products[].strategy` 추가.
- Public recommendation: `GET /api/recommendations/popular`, `GET /api/recommendations/trending`, `GET /api/products/{productId}/related`, `GET /api/products/{productId}/complementary`.
- Repeat commerce: `GET /api/recommendations/reorder-timing`, `GET /api/v2/subscriptions/{id}/cycle-suggestion`, `GET /api/orders/{id}/subscription-options`.
- Add-on commands: `POST /api/v2/subscriptions/{id}/commands/set-next-delivery-addon`, `POST /api/v2/subscriptions/{id}/commands/remove-next-delivery-addon`. 기존 `Idempotency-Key`, `If-Match`, ETag를 그대로 보낸다.
- AI: `GET /api/products/{productId}/reviews/summary`, `GET /api/products/compare?productId=1&productId=2`.
- Admin readback: 세 read-only API는 ADMIN 권한이 필요하다.

## Recommendation response

기존 상품 표시 필드에 다음이 추가된다.

```json
{
  "requestId": "opaque-uuid",
  "products": [
    {
      "productId": 101,
      "name": "상품명",
      "shortDescription": "설명",
      "thumbnailUrl": null,
      "category": {"categoryId": 1, "name": "사료", "slug": "food"},
      "reason": "반려동물 유형에 맞는 구매 가능 상품입니다.",
      "strategy": "PERSONALIZED"
    }
  ]
}
```

strategy enum은 `PERSONALIZED`, `EXPLORATION`, `POPULAR`, `TRENDING`, `RELATED`, `COMPLEMENTARY`를 사용한다. personalized 결과가 10개를 초과하면 AI에는 top 9만 보내고 public response에는 personalized 9개와 deterministic `EXPLORATION` 1개를 반환한다. `requestId`와 상품 ID로 impression/click event를 만들고, pet 기반 결과는 `petId`를 함께 보낸다.

## Interaction context and transaction

context는 `hasTextQuery` Boolean, `petType` DOG/CAT, category/subcategory/brand slug, Product sort enum, non-negative numeric `minPrice`/`maxPrice`, 최대 20개의 bounded `key:value` facets만 허용한다. raw search text나 임의 중첩 객체/배열은 허용하거나 저장하지 않는다. 한 요청의 batch 전체는 하나의 transaction으로 실행되며 event 하나의 validation 또는 insert 실패 시 앞선 event도 rollback된다. 실패 요청은 재시도할 수 있고 `(member_id,event_id)` deduplication으로 성공한 batch의 재전송은 idempotent하다. member ID와 occurredAt은 서버 context/Clock이 정한다.

## Pet and Add-on DTO

Pet은 `breed: string|null`, `weightKg: number|null`, `profileComplete: boolean`이다. `petType`은 수정하지 않는다.

`nextDelivery`는 다음을 추가한다.

```json
{
  "addOns": [{
    "skuId": 2001,
    "productId": 201,
    "productName": "간식",
    "skuName": "소형",
    "quantity": 2,
    "unitPriceKrw": 3000.55,
    "lineAmountKrw": 6001.10
  }],
  "addOnTotalKrw": 6001.10,
  "orderTotalKrw": 26001.10
}
```

Add-on은 current/pending Snapshot 상품이 아니며 다음 한 번의 Schedule에만 적용된다. SET quantity는 1~10이다. 가격은 canonical SKU DECIMAL(12,2)를 BigDecimal로 snapshot해 DECIMAL(18,2)로 반환·계산하며 자동 반올림/long truncation을 하지 않는다.

`ORDER_STOCK_UNAVAILABLE`은 recoverable `HELD` 상태다. due processor가 재고와 catalog를 다시 평가하고, 회복되면 정상 atomic due flow에서 HELD 이유를 지운다. `REMOVE_NEXT_DELIVERY_ADDON`은 이 HELD Schedule에도 적용할 수 있다. RESCHEDULE·PAUSE·RESUME·CHANGE_DELIVERY_CYCLE은 같은 Add-on을 보존하고, SKIP은 새 actual next Schedule로 이동하며, CANCEL은 미소비 Add-on을 삭제한다.

## Status and errors

- Review summary: `INSUFFICIENT_REVIEWS`, `AVAILABLE`, `UNAVAILABLE`
- Comparison: `aiStatus=AVAILABLE|UNAVAILABLE`, canonical products는 AI 실패에도 반환
- Reorder: `OVERDUE`, `DUE_SOON`
- Add-on: `ADDON_SKU_ALREADY_INCLUDED`, `ADDON_CONFLICTS_WITH_PLAN`, `ADDON_LIMIT_EXCEEDED`, `ADDON_NOT_AVAILABLE`, `ADDON_NOT_FOUND`
- Due stock issue: detail의 기존 issue shape에서 `code=STOCK_UNAVAILABLE`
- Pet/상품/소유권 masking: `404 PET_NOT_FOUND` 또는 기존 resource not found 계약

`SUBSCRIPTION_DELIVERY_REMINDER`는 Seoul date 기준 `today < scheduledDate <= today + 3 days`인 ACTIVE·mvp2·SCHEDULED·미생성 주문 Schedule에 대한 in-app 알림이다. 반복 실행은 unique identity로 중복을 막고, Schedule이 window 밖으로 이동하거나 SKIP·PAUSE·CANCEL되면 read 여부와 관계없이 stale state를 제거한다. 같은 Schedule이 다시 window에 들어오면 새 알림이 생성될 수 있다. Email/SMS/push는 제공하지 않는다.

AI summary는 null일 수 있다. AI response를 화면의 가격·재고·구매 가능 여부 판단에 사용하지 말고 canonical fields만 사용한다. 모든 AI 기능은 기본 disabled이며 `UNAVAILABLE`을 정상 상태로 처리한다.
