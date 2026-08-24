# MVP4-REC-001 Backend → Frontend 인수인계

## Delta

- 로그인 상태에서 `GET /api/recommendations/products?petId={id}`를 호출한다. `petId`는 회원 소유 Pet이어야 하며 `404 PET_NOT_FOUND`는 Pet 선택을 다시 유도한다.
- 추천 응답은 `products[]`이고 각 항목에 `productId`, `name`, `shortDescription`, `thumbnailUrl`, `category`, `reason`이 있다. 빈 배열은 추천 가능한 재고 상품이 없다는 정상 상태다.
- AI 사용 여부나 fallback 여부를 UI에 노출하지 않는다. 이유는 항상 짧은 한국어 텍스트로 렌더링한다.
- 기존 공개 `GET /api/products`는 `q`, `petType`, `category`(slug)를 선택적으로 받으며 조합 필터가 가능하다. 세 필터는 대소문자를 구분하지 않는다. 목록과 상세 모두 V13 이후 필수인 Product Category를 제공한다.

## 호환성·오류

- 기존 Product 필드는 유지된다.
- 추천 API는 인증이 필요하며 비로그인 요청은 기존 공통 인증 오류를 따른다. GET이므로 CSRF 토큰은 필요 없다.
- AI 장애·잘못된 ID·이유 검증 실패는 일반 추천으로 흡수되어 별도 오류 상태를 만들지 않는다.
- 추천 결과를 받은 뒤에도 Product 상세나 장바구니/주문 시점의 기존 서버 검증을 그대로 신뢰한다. 추천 응답 자체를 가격·재고의 권위 원본으로 사용하지 않는다.

## 제외

Frontend 변경, Production AI 활성화·API Key/모델 등록, 추천 전용 cache와 의료 관련 UX는 이 작업에 포함하지 않는다.
