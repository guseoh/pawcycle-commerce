# API-009 MVP4 추천과 상품 탐색 API

- 작업 ID: `MVP4-REC-001`
- 상태: Approved Input 구현 계약
- 등급: 고위험
- 실행 구분: 저장소 변경

## `GET /api/recommendations/products?petId={petId}`

인증된 회원 전용 API다. `petId`는 필수이며 요청 회원이 소유하지 않거나 존재하지 않으면 `404 PET_NOT_FOUND`를 반환한다. GET이므로 CSRF 토큰은 필요하지 않다.

응답은 최대 10개의 상품을 반환한다.

```json
{
  "products": [{
    "productId": 301,
    "name": "성견 사료",
    "shortDescription": "매일 먹는 기본 사료",
    "thumbnailUrl": null,
    "category": { "categoryId": 10, "name": "사료", "slug": "food" },
    "reason": "현재 관심 카테고리와 잘 맞는 상품입니다."
  }]
}
```

- 후보는 Pet 타입 일치, `PUBLIC` Product, 재고가 있는 `ACTIVE` SKU를 모두 만족한다.
- 현재 Pet의 ACTIVE 정기배송, 회원의 결제 완료 구매, Wishlist에서 카테고리 성향만 사용한다. 개인 구매 원문이나 PII는 응답·AI 입력에 포함하지 않는다.
- AI에 전달하는 후보는 서버가 정렬한 상위 10개 이하로 제한한다.
- 외부 AI 호출 뒤 상품 공개 상태·ACTIVE SKU·재고를 다시 조회해 최종 응답 후보를 재검증한다.
- 의료·질병·치료·약품·처방 관련 상품과 이유는 추천 대상에서 제외한다.
- AI가 비활성, 실패, 잘못된 구조, 중복 또는 후보 밖 ID를 반환하거나 이유 검증에 실패해도 `500` 대신 일반 추천을 반환한다. 후보가 없으면 `products: []`다.

AI는 기본 비활성이다. 실제 활성화는 저장소 변경과 분리하며 다음 외부 환경이 필요하다.

```text
PAWCYCLE_RECOMMENDATION_AI_ENABLED=true
PAWCYCLE_RECOMMENDATION_AI_MODEL=<approved-model>
SPRING_AI_OPENAI_API_KEY=<secret>
```

Secret과 실제 모델 선택은 저장소에 고정하지 않는다. API key 또는 모델이 없는 상태에서 AI를 활성화하면 안전하게 애플리케이션 시작이 실패해야 하며, 기본 disabled 상태에서는 둘 다 필요하지 않다.

## `GET /api/products`

기존 공개 상품 목록에 다음 optional query parameter를 추가한다. 모두 함께 사용할 수 있다.

| parameter | 의미 |
| --- | --- |
| `q` | 상품명 또는 짧은 설명의 대소문자 비구분 부분 검색 |
| `petType` | `DOG` 또는 `CAT` 상품 타입 필터(대소문자 비구분) |
| `category` | Category `slug` 필터(대소문자 비구분) |
| `page` | 0부터 시작하는 페이지 번호(기본 0) |
| `size` | 페이지 크기(기본 20, 최대 100) |
| `subcategory` | 2·3 depth Category의 leaf `slug` 필터 |
| `brand` | Brand `slug` 필터 |
| `facet` | `key:value` 형식 facet option 필터(여러 번 전달 가능) |
| `minPrice`, `maxPrice` | ACTIVE SKU 가격 범위 필터 |
| `sort` | `NEWEST`, `PRICE_ASC`, `PRICE_DESC`, `RATING`, `REVIEW_COUNT` (기본 `NEWEST`) |

상품 목록의 `items[]`와 `GET /api/products/{productId}` 상세에 다음 `category` 필드를 추가한다.

```json
"category": { "categoryId": 10, "name": "사료", "slug": "food" }
```

Category는 V13 이후 Product의 필수 DB 관계다.

상세 SKU에는 `availableQuantity`와 Backend가 계산한 `purchasable`이 포함된다. 이 값은 재고 표시와 구매 가능 상태의 authoritative source이며 Frontend가 수량을 추정하지 않는다.

응답은 DB-native filter/sort/pagination 결과다. `products` 전체를 JVM에 materialize하거나 요청별로 필터링하지 않는다. `purchasable`은 공개 Product의 ACTIVE SKU와 DB inventory를 기준으로 Backend가 계산하며 Frontend가 재구성하지 않는다. pageable 공개 목록은 기존 전체 목록 Redis cache를 사용하지 않는다. Redis key `pawcycle:catalog:product-list:v2`는 legacy/no-argument reader 호환성 검증용으로만 유지하며 filter별 key는 만들지 않는다.

목록 응답은 `items`, `page`, `size`, `totalElements`, `totalPages`를 포함한다. 기존 no-argument legacy reader/cache caller에는 내부 `products()` 호환 accessor를 유지하지만 pageable HTTP 응답의 권위 필드는 `items`다.

V24 이후 각 목록 item에는 `brand`, `representativePrice`, 선택적 `compareAtPrice`·`discountRate`, `averageRating`, `reviewCount`가 additive하게 포함된다. 상세에는 Brand, gallery images, option groups와 SKU별 `selectedOptions`가 추가된다. 공개 목록과 상세는 Brand가 active인 Product만 노출한다.
