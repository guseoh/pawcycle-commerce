# API 문서(API Documents)

이 디렉터리는 승인된 API 계약(API Contract)과 API 변경 메모를 보관한다.

사용 범위는 다음과 같다.

- OpenAPI 계약(OpenAPI Contract)
- 엔드포인트(Endpoint) 동작 메모
- 요청(Request)과 응답(Response) 예시
- 인증(Authentication)과 인가(Authorization) 기대 사항
- API 변경 기록(Change Log)
- 백엔드에서 프론트엔드로 전달하는 인수인계 참조

초안 API 메모는 사용자가 승인하기 전까지 승인된 계약으로 취급하지 않는다.

## API 변경 체크리스트

- 작업 ID가 있다.
- 관련 제품 요구사항이 연결됐다.
- 요청과 응답 형태가 문서화됐다.
- 오류 응답(Error Response)이 문서화됐다.
- 인가 동작이 문서화됐다.
- 하위 호환성(Backward Compatibility) 영향이 기록됐다.
- 필요한 경우 프론트엔드와 QA 인수인계가 작성됐다.
