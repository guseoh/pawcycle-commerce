# API-009 MVP4 추천과 상품 탐색 API

- 작업 ID: `MVP4-REC-001`
- 상태: Approved Input 구현 계약
- 등급: 고위험
- 실행 구분: 저장소 변경

## `GET /api/recommendations/products?petId={petId}`

인증된 회원 전용 API다. `petId`는 필수이며 요청 회원이 소유하지 않거나 존재하지 않으면 `404 PET_NOT_FOUND`를 반환한다. CSRF 토큰은 GET 요청에 필요하지 않다.

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
- Category가 없는 구매 가능 상품은 `category: null`로 반환될 수 있으며 기본 추천 순위 후보로 취급한다.

AI는 기본 비활성이다. 실제 활성화는 저장소 변경과 분리하며 최소 다음 외부 환경이 필요하다.

```text
PAWCYCLE_RECOMMENDATION_AI_ENABLED=true
SPRING_AI_MODEL_CHAT=openai
SPRING_AI_OPENAI_API_KEY=<secret>
```

Secret과 실제 모델 선택은 저장소에 고정하지 않는다.

## `GET /api/products`

기존 공개 상품 목록에 다음 optional query parameter를 추가한다. 모두 함께 사용할 수 있다.

| parameter | 의미 |
| --- | --- |
| `q` | 상품명 또는 짧은 설명의 대소문자 비구분 부분 검색 |
| `petType` | `DOG` 또는 `CAT` 상품 타입 필터(대소문자 비구분) |
| `category` | Category `slug` 필터(대소문자 비구분) |

상품 목록의 `products[]`와 `GET /api/products/{productId}` 상세에 다음 `category` 필드를 추가한다.

```json
"category": { "categoryId": 10, "name": "사료", "slug": "food" }
```

기존 DB 계약상 `products.category_id`는 nullable이므로 Category가 없는 상품에서는 `category: null`이다. `category` 필터가 지정된 경우 Category가 없는 상품은 결과에서 제외한다.

목록 Redis cache는 전체 공개 목록의 기존 cache를 계속 사용하며 key format은 `pawcycle:catalog:product-list:v2`다. 검색·필터 전용 cache key는 만들지 않고 cache된 전체 목록에서 응답을 필터링한다.
