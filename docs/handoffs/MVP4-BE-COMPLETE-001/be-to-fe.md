# MVP4-BE-COMPLETE-001 Backend → Frontend 인수인계

## 사용 가능한 계약

- Product 상세 additive 필드: `shortDescription`, `detailSections`, `trust.averageRating`, `trust.reviewCount`, `trust.questionCount`
- 공개 Review/Q&A page 응답은 `{items,page,size,totalElements,totalPages}`다.
- 공개 작성자 식별자는 응답하지 않는다. `GET reviews/me`는 본인 Review가 없으면 `REVIEW_NOT_FOUND`다.
- Admin 변경은 기존 session CSRF와 ADMIN 권한 경계를 사용한다.

상세 필드와 endpoint/request/error shape는 [API-010](../../api/API-010-mvp4-product-detail-trust-api.md)을 권위 계약으로 사용한다. Backend가 실제 Production 데이터나 migration을 실행한 것은 아니다.

## UI 소비 주의점

- `detailSections`는 빈 배열을 정상 상태로 처리하고 visible/order 결과를 그대로 표시한다.
- `trust`의 평균·개수는 공개 항목 기준이며, 리뷰/문의 작성·수정 결과를 화면에서 권위 데이터로 재계산하지 않는다.
- Review 작성 버튼은 배송 완료 구매 검증 실패를 별도 오류 상태로 표시할 수 있다.
- 답변된 Question은 작성자 수정·삭제가 불가능하다. Admin 답변 수정은 기존 Notification을 다시 만들지 않는다.
