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

**현재 등록된 전체 엔드포인트**는 [current-endpoints.md](current-endpoints.md)에 있다. 이 색인은 테스트가 Spring 매핑에서 생성하고 어긋나면 CI가 실패한다. API-001~012는 단계별 계약의 원본이다.

## 공통 응답 헤더

| 헤더 | 내용 | 도입 |
| --- | --- | --- |
| `X-Request-ID` | 모든 backend 응답에 붙는 요청 ID. 요청에 안전한 형식(`[A-Za-z0-9._-]`, 1~64자)의 `X-Request-ID`가 있으면 그 값을 쓰고, 없거나 형식이 다르면 새로 만든다(proxy가 만든 값 또는 UUID). 형식이 다른 요청 값은 응답에 돌려주지 않는다. 같은 값이 backend 로그(`requestId`)와 proxy access log(`request_id`)에 남으므로, 문의나 장애 보고 때 이 값으로 로그를 찾는다. | 2026-11-16, OBS-LOG-001 |

추가 헤더이므로 기존 client에는 하위 호환 영향이 없다.

## 공통 오류 응답

현재 오류 응답의 원본은 이 절이다. 처음 형태(`code`, `message`, `fieldErrors`)는 ARCH-006에서 승인됐고, 이 절은 그 뒤에 추가된 필드까지 포함한다.

| 형태 | 필드 | 사용처 |
| --- | --- | --- |
| `ApiErrorResponse` | `code`, `message`, `fieldErrors`(필드 오류가 없으면 빈 배열), `requestId` | interaction을 제외한 모든 API, 인증·인가 실패(401·403) |
| `InteractionErrorResponse` | `code`, `message`, `requestId` | `/api/interactions` |

- `requestId`(2026-11-16, OBS-LOG-002 추가): 응답 헤더 `X-Request-ID`와 같은 값이다. HTTP 요청 밖에서 만든 응답에서만 빠진다. 실제 서버 응답에는 항상 있다.
- 예상하지 못한 오류는 모두 500 `INTERNAL_ERROR`("요청을 처리할 수 없습니다.")로 응답한다. 내부 예외 내용은 응답에 넣지 않는다. 모든 REST Controller에 이 처리기가 있는지는 `UnexpectedExceptionCoverageTests`가 검사한다.
- 하위 호환: 필드가 추가될 뿐 기존 필드는 그대로다. frontend는 `requestId`를 선택 필드로 읽고, 서버 오류(5xx)일 때 결제 화면과 관리자 오류 메시지에 함께 보여 준다.

## API 변경 체크리스트

- 작업 ID가 있다.
- 관련 제품 요구사항이 연결됐다.
- 요청과 응답 형태가 문서화됐다.
- 오류 응답(Error Response)이 문서화됐다.
- 인가 동작이 문서화됐다.
- 하위 호환성(Backward Compatibility) 영향이 기록됐다.
- 필요한 경우 프론트엔드와 QA 인수인계가 작성됐다.
