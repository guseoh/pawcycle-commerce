# API-010 MVP4 Product Detail·Trust delta 계약

## 범위

작업 ID `MVP4-BE-COMPLETE-001`의 저장소 준비 계약이다. 기존 Product/SKU/가격/재고/subscribable 응답은 유지하고 Product Detail의 콘텐츠, Review, Product Q&A를 additive하게 제공한다. 모든 공개 응답은 작성자의 email/memberId를 포함하지 않는다.

## Product Detail

`GET /api/products/{productId}` 응답에 다음 필드를 추가한다.

```json
{
  "shortDescription": "짧은 설명",
  "detailSections": [
    {"sectionId": 1, "title": "급여 방법", "body": "plain text", "displayOrder": 1, "visible": true, "createdAt": "...", "updatedAt": "..."}
  ],
  "trust": {"averageRating": 4.5, "reviewCount": 2, "questionCount": 1}
}
```

공개 상세 섹션은 `visible=true`, `displayOrder ASC, sectionId ASC`로만 노출하며 없으면 `[]`다. title/body는 plain text 저장 계약이며 HTML 렌더링·HTML 저장 계약은 추가하지 않는다. trust의 Review 평균·개수와 Q&A 개수는 visible 항목만 집계한다.

## Admin Detail Section

`GET/POST /api/admin/products/{productId}/detail-sections`, `PATCH/DELETE /api/admin/products/{productId}/detail-sections/{sectionId}`를 제공한다. POST body는 `title`, `body`, `displayOrder`, `visible`이고 PATCH는 일부 필드 수정이다. `/api/admin/**` 기존 ADMIN·CSRF·Audit 경계를 그대로 사용한다.

## Review

- `GET /api/products/{productId}/reviews?page=&size=`: visible Review page
- `GET /api/products/{productId}/reviews/me`: 로그인 회원의 Review, 없으면 `REVIEW_NOT_FOUND`
- `POST /api/products/{productId}/reviews`: `{rating: 1..5, content}`
- `PATCH/DELETE /api/reviews/{reviewId}`: 작성자만 가능
- `GET /api/admin/product-reviews?page=&size=&productId=`: 전체 상태 조회
- `PATCH /api/admin/product-reviews/{reviewId}/visibility`: `{visible}`

Review 작성은 회원 소유 Order의 해당 Product SKU가 실제 존재하고 연결 Delivery가 `DELIVERED`인 경우에만 가능하다. 회원·상품당 하나의 Review만 허용하며 신규 Review는 visible로 생성된다. Admin visibility 변경과 작성자 수정은 기존 visible 값을 보존한다.

## Product Q&A

- `GET /api/products/{productId}/questions?page=&size=`: visible Question page
- `POST /api/products/{productId}/questions`: `{content}`
- `PATCH/DELETE /api/product-questions/{questionId}`: 답변 전 작성자만 가능
- `GET /api/admin/product-questions?page=&size=&productId=`
- `PUT /api/admin/product-questions/{questionId}/answer`: `{answer}`
- `PATCH /api/admin/product-questions/{questionId}/visibility`: `{visible}`

공개 Question 응답은 `questionId`, `content`, `answer`, `answered`, `createdAt`, `updatedAt`만 포함한다. 최초 답변 transaction에서 기존 Notification 구조로 `PRODUCT_QUESTION_ANSWERED`를 한 번 생성하고, 답변 수정에서는 생성하지 않는다. 답변이 등록된 Question은 작성자가 수정·삭제할 수 없다.

목록은 `{items, page, size, totalElements, totalPages}`이며 공개 정렬은 최신 `createdAt DESC, id DESC`다. 공통 오류는 기존 `code`, `message`, `fieldErrors` shape를 따른다.

## DB 영향과 실행 경계

기존 migration은 수정하지 않고 `V21__add_product_detail_reviews_questions.sql`을 추가했다. `product_detail_sections`, `reviews`, `product_questions`와 Notification type 확장을 additive하게 적용한다. 이번 작업은 `실행 구분: 저장소 변경`이며 운영 DB migration·실제 Production 데이터 적용은 수행하지 않는다. 저장소 변경의 복구는 revert PR 경계로 제한한다.
