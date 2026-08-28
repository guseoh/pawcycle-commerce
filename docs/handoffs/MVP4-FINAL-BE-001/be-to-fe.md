# MVP4-FINAL-BE-001 Backend → Frontend handoff

이번 변경은 backend API와 data contract만 포함한다. Frontend/Admin login `returnTo`와 기존 command 흐름은 변경하지 않는다.

## Endpoints

- Pet: `PATCH /api/v2/pets/{petId}`; 기존 `GET /api/v2/pets`, `GET /api/v2/pets/{petId}`, Subscription detail의 Pet에 `breed`, `weightKg`, `profileComplete`가 additive하게 온다.
- Interaction: 인증 후 `POST /api/interactions`; `events` 최대 50개, 성공 204.
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

strategy enum은 `PERSONALIZED`, `EXPLORATION`, `POPULAR`, `TRENDING`, `RELATED`, `COMPLEMENTARY`를 사용한다. personalized 결과가 10개를 초과할 수 있는 경우 1~9번은 personalized, 10번은 exploration이다. `requestId`와 상품 ID로 impression/click event를 만들고, pet 기반 결과는 `petId`를 함께 보낸다.

## Pet and Add-on DTO

Pet은 `breed: string|null`, `weightKg: number|null`, `profileComplete: boolean`이다. PATCH에서 `breed:null` 또는 `weightKg:null`은 해당 값을 비운다. `petType`은 수정하지 않는다.

`nextDelivery`는 다음을 추가한다.

```json
{
  "addOns": [{
    "skuId": 2001,
    "productId": 201,
    "productName": "간식",
    "skuName": "소형",
    "quantity": 2,
    "unitPriceKrw": 3000,
    "lineAmountKrw": 6000
  }],
  "addOnTotalKrw": 6000,
  "orderTotalKrw": 26000
}
```

Add-on은 current/pending Snapshot 상품이 아니며 다음 한 번의 Schedule에만 적용된다. SET quantity는 1~10이다.

## Status and errors

- Review summary: `INSUFFICIENT_REVIEWS`, `AVAILABLE`, `UNAVAILABLE`
- Comparison: `aiStatus=AVAILABLE|UNAVAILABLE`, canonical products는 AI 실패에도 반환
- Reorder: `OVERDUE`, `DUE_SOON`
- Add-on: `ADDON_SKU_ALREADY_INCLUDED`, `ADDON_CONFLICTS_WITH_PLAN`, `ADDON_LIMIT_EXCEEDED`, `ADDON_NOT_AVAILABLE`, `ADDON_NOT_FOUND`
- Due stock issue: detail의 기존 issue shape에서 `code=STOCK_UNAVAILABLE`
- Pet/상품/소유권 masking: `404 PET_NOT_FOUND` 또는 기존 resource not found 계약

AI summary는 null일 수 있다. AI response를 화면의 가격·재고·구매 가능 여부 판단에 사용하지 말고 canonical fields만 사용한다. 모든 AI 기능은 기본 disabled이며 `UNAVAILABLE`을 정상 상태로 처리한다.
