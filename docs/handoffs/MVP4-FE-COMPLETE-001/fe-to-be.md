# MVP4 Frontend → Backend 인수인계

## 계약 gap

인증 회원이 상품별로 작성한 자기 Product Question을 새로고침 뒤 서버 권위로 조회할 수 있는 계약이 필요합니다.

- 목적: 답변 전 자기 문의의 수정·삭제 UI에서 서버 권위 소유권을 확인
- 범위: Product별 자기 Question 조회 계약
- 유지 조건: 기존 공개 Question 응답은 작성자 email/memberId 등 개인정보를 노출하지 않음

이번 Frontend에서는 공개 Question을 자기 문의로 추정하거나 브라우저 저장소를 소유권 원본으로 사용하지 않았고, Question 수정·삭제 self-service UI도 추가하지 않았습니다.
