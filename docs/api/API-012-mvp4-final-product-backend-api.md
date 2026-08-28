# API-012 MVP4 최종 상품 백엔드 API delta

- 작업 ID: `MVP4-FINAL-001`
- 실행 구분: 저장소 변경
- 기준: 기존 API-008/009/010과 현재 V2 Subscription 계약에 additive하게 연결

기존 Checkout, Payment, Subscription 생성·명령의 계약은 유지한다. 아래 API는 최종 MVP 상품 백엔드 delta이며, Production DB 적용과 운영 실행을 포함하지 않는다.

## Pet Profile

`PATCH /api/v2/pets/{petId}`는 인증 회원 본인의 Pet만 수정한다. Body는 `name`, `breed`, `weightKg` 중 하나 이상이며 `breed`와 `weightKg`는 `null`로 지울 수 있다. `petType`은 무시하거나 변경하지 않으며 Pet 삭제 API는 없다.

Pet 응답에는 기존 필드에 `breed`, `weightKg`, `profileComplete`를 추가한다. `profileComplete`는 두 신규 값이 모두 non-null일 때만 `true`다. 이름은 trim 후 1~50자, breed는 null 또는 trim 후 1~80자, weightKg는 null 또는 `0 < weightKg <= 200.00`이며 제어 문자는 거부한다. 타 회원·없는 Pet은 `404 PET_NOT_FOUND`다.

## Interaction Events

`POST /api/interactions` (인증 필수)

```json
{
  "events": [
    {
      "eventId": "client-generated-uuid",
      "type": "PRODUCT_VIEW",
      "productId": 101,
      "petId": 7,
      "source": "product-detail",
      "context": {"petType": "DOG", "category": "food"}
    }
  ]
}
```

한 요청은 1~50개다. 성공은 `204 No Content`이며 `memberId`와 `occurredAt`은 서버가 정한다. `PRODUCT_IMPRESSION`·`PRODUCT_VIEW`는 `productId`가 필수이고, `SEARCH`·`FILTER`는 Product 없이 승인된 구조화 context만 보낸다. 추천 impression/click은 `productId`와 `recommendationRequestId`가 필수이며, Pet을 기준으로 한 추천이면 소유한 `petId`를 함께 보낸다. context는 `hasTextQuery` Boolean, `petType` DOG/CAT, category/subcategory/brand slug, Product sort enum, 0 이상 숫자형 `minPrice`/`maxPrice`, 최대 20개의 `key:value` facets만 허용한다. raw search text, 임의 객체·중첩 배열과 client member ID는 저장하지 않는다. 한 batch는 하나의 transaction으로 처리되며 어느 event든 validation/insert가 실패하면 앞서 삽입한 event도 모두 rollback된다. 실패 batch는 재시도할 수 있고 `(member_id,event_id)` 중복 재전송은 무해하다.

## Recommendation V2

기존 `GET /api/recommendations/products?petId={petId}` 응답에 opaque `requestId`와 상품별 `strategy`를 추가한다. strategy 예시는 `PERSONALIZED`, `EXPLORATION`이다. 상품은 항상 요청 Pet Type, PUBLIC Product, active Category/Brand, ACTIVE SKU, 재고, non-medical 경계를 만족한다. AI 실패·비활성·잘못된 후보/이유는 deterministic fallback으로 처리하며 내부 점수는 노출하지 않는다.

추가 public GET API:

| Endpoint | 기본/최대 | 응답 전략 |
| --- | ---: | --- |
| `/api/recommendations/popular?petType=` | 10 / 10 | `POPULAR` |
| `/api/recommendations/trending?petType=` | 10 / 10 | `TRENDING` |
| `/api/products/{productId}/related` | 4 / 6 | `RELATED` |
| `/api/products/{productId}/complementary` | 4 / 6 | `COMPLEMENTARY` |

모든 응답은 기존 recommendation product display fields를 포함하며 `requestId`와 `products[].strategy`를 추가한다. Popular는 최근 30일 paid buyer/cart/wishlist/recommendation click/product view의 distinct member 신호를 사용하고, Trending은 최근 7일 가중치와 직전 7일 가중치의 delta를 사용한다. Related는 Category→Brand→Facet overlap→Popularity, Complementary는 성공한 `ONE_TIME` paid order의 실제 co-purchase를 우선하며, 데이터가 부족하면 다른 Category·같은 Pet Type·Popularity로 fallback한다. source Product를 결과에 포함하지 않는다.

## Repeat Commerce

인증 회원 API:

- `GET /api/recommendations/reorder-timing`: Subscription을 제외한 `ONE_TIME` `PAID` 상품의 최근 최대 5회 purchase date로 median interval을 계산한다. 최소 3회가 필요하고 `expectedReorderDate <= today + 7 days`만 반환한다. 상태는 `OVERDUE` 또는 `DUE_SOON`이며 최대 10개다.
- `GET /api/v2/subscriptions/{subscriptionId}/cycle-suggestion`: 본인 ACTIVE Subscription의 성공 처리 주문이 3회 이상일 때 실제 Schedule interval median과 현재 허용 cycle(2/4/8)을 비교해 제안한다. 같은 거리의 후보가 여러 개면 현재 cycle, 그 다음 긴 cycle을 선택한다. 현재 cycle이면 `suggestion: null`이며 자동 변경하지 않는다.
- `GET /api/orders/{orderId}/subscription-options`: 본인 `ONE_TIME` `PAID` 주문과 현재 판매 가능한 PlanVersion을 비교해 `planVersionId`, `planName`, `matchingProductIds`, `compatibleOwnedPetIds`, `allowedDeliveryCycleWeeks`, `packagePriceKrw`를 반환한다. 실제 생성은 기존 `POST /api/v2/subscriptions`를 사용한다.

## Next-delivery Add-on

기존 command endpoint를 그대로 사용한다.

- `POST /api/v2/subscriptions/{id}/commands/set-next-delivery-addon`
- `POST /api/v2/subscriptions/{id}/commands/remove-next-delivery-addon`

두 API 모두 기존 `Idempotency-Key`, `If-Match`, ETag, ownership masking, replay-before-stale 규칙을 적용한다. SET body는 `{ "skuId": 2001, "quantity": 2 }`, REMOVE body는 `{ "skuId": 2001 }`다. 수량은 1~10, 한 Schedule의 distinct Add-on SKU는 최대 10개다. SET은 다음 `SCHEDULED` Schedule에만 적용한다. REMOVE는 다음 `SCHEDULED` Schedule과 `ORDER_STOCK_UNAVAILABLE` 사유의 recoverable `HELD` Schedule에서 허용된다. 그 외 HELD 상태에서는 기존 상태 복구 또는 CANCEL만 허용한다. Product PUBLIC·Category/Brand active·SKU ACTIVE·현재 재고를 SET 시점에 확인하지만 stock을 선점하지 않는다. `subscribable`은 요구하지 않는다.

SET 성공 시 현재 SKU 가격을 `unitPriceKrw`로 snapshot하고 같은 SKU 재호출은 수량과 가격 snapshot을 갱신한다. 금액은 SKU와 동일한 소수 금액을 보존하는 `BigDecimal`/DECIMAL(18,2)이며 자동 반올림·long 변환을 하지 않는다. 기본 effective Plan에 이미 포함된 SKU면 `409 ADDON_SKU_ALREADY_INCLUDED`, 이후 CHANGE_PLAN 대상에 충돌하면 `409 ADDON_CONFLICTS_WITH_PLAN`, limit 초과는 `409 ADDON_LIMIT_EXCEEDED`다. 없는 Add-on은 `404 ADDON_NOT_FOUND`, 현재 catalog/stock 불가 Add-on은 `409 ADDON_NOT_AVAILABLE`다.

Subscription detail의 `nextDelivery`에 `addOns[]`, `addOnTotalKrw`, `orderTotalKrw`를 추가한다. Add-on 항목은 `skuId`, `productId`, `productName`, `skuName`, `quantity`, `unitPriceKrw`, `lineAmountKrw`를 제공하며 current/pending Snapshot에는 넣지 않는다. `SCHEDULED` 상태의 `availableActions`에는 `SET_NEXT_DELIVERY_ADDON`을 노출하고 실제 Add-on이 있으면 `REMOVE_NEXT_DELIVERY_ADDON`도 노출한다. `ORDER_STOCK_UNAVAILABLE` HELD에서는 실제 Add-on이 있을 때 `REMOVE_NEXT_DELIVERY_ADDON`과 `CANCEL`만 노출한다. RESCHEDULE·PAUSE·RESUME·CHANGE_DELIVERY_CYCLE은 같은 Add-on을 보존하고, SKIP은 새 next Schedule로 이동하며, CANCEL은 미소비 Add-on을 삭제한다.

Due automation은 base와 Add-on을 하나의 common Order·Billing Payment·Inventory reservation으로 처리한다. 어느 하나라도 due-time stock/catalog 검증에 실패하면 부분 주문을 만들지 않고 Schedule을 internal `ORDER_STOCK_UNAVAILABLE`로 `HELD` 처리한다. 이 HELD 상태는 다음 due processor가 재평가하며, 회복되면 정상 처리와 함께 HELD 이유를 지운다. `REMOVE_NEXT_DELIVERY_ADDON`은 이 recoverable HELD Schedule에서도 허용된다. User-facing issue는 `STOCK_UNAVAILABLE`로 매핑한다. 소비된 Add-on은 `subscription_order_addon_items`에 보존한다.

## Reminder

`SUBSCRIPTION_DELIVERY_REMINDER`는 property-gated in-app notification이다. `today < scheduledDate <= today + 3 days`, ACTIVE·mvp2·SCHEDULED·미생성 주문 Schedule만 대상으로 하며 identity는 `referenceType=SCHEDULE`, `referenceId=scheduleId`다. 반복 실행은 unique notification event로 idempotent하다. Schedule이 window 밖으로 이동하거나 SKIP·PAUSE·CANCEL되면 읽음 여부와 관계없이 stale reminder를 제거하고, 이후 다시 window에 들어오면 새 reminder를 만들 수 있다. notification projection에는 `subscriptionId`, `scheduledDate`를 추가한다. Email/SMS/push는 제공하지 않는다.

## AI Commerce

기능 flag 기본값은 disabled다. 설정된 Spring AI/OpenAI client가 실패하거나 output이 invalid면 canonical Commerce 응답은 유지하고 AI 상태만 unavailable로 반환한다.

- `GET /api/products/{productId}/reviews/summary`: visible review만 사용하고 3개 미만이면 `status=INSUFFICIENT_REVIEWS`, `summary=null`이다. 3개 이상이면 latest 30개만 AI input으로 보내며 응답은 `AVAILABLE` 또는 `UNAVAILABLE`, `summary`, `reviewCount`, `averageRating`을 포함한다. cache는 visible source fingerprint가 같은 동안 재사용한다.
- `GET /api/products/compare?productId=1&productId=2[&productId=3]`: 서로 다른 2~3개 public Catalog Product만 허용한다. 응답은 서버 canonical facts를 항상 반환하고 `aiStatus=AVAILABLE|UNAVAILABLE`, `aiSummary`를 추가한다. AI에는 name/thumbnail/brand/category/price/compare-at/discount/rating/review count/subscription eligibility/purchasable/facets만 전달한다.

AI text는 Korean plain text, 최대 500 characters, HTML 금지, 질병·치료·약·처방 등 medical claim 금지다. invalid output은 저장·노출하지 않는다.

## Admin readback

기존 ADMIN authorization을 유지하는 read-only API:

- `GET /api/admin/products/{productId}/skus/{skuId}/option-values` → 실제 `optionValueIds`
- `GET /api/admin/products/{productId}/facet-values` → 실제 `facetOptionIds`
- `GET /api/admin/categories/{categoryId}/facets` → 실제 `facets[{categoryId,facetDefinitionId,displayOrder}]`

정렬은 각각 Option Group order/ID→Option Value order/ID, Category Facet order→Definition ID, Category Facet `displayOrder`→Definition ID다. 없는 parent/resource 오류는 기존 Admin service 오류 계약을 따른다.

## 공통 오류 및 운영 경계

유효성 오류는 기존 `VALIDATION_FAILED` 형식을 따르고, ownership masking은 `404`로 유지한다. 이번 변경은 Flyway forward migration과 application code만 준비하며 Production migration, secret 입력, deploy는 수행하지 않는다.
