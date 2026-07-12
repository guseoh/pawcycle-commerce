# AUTH-004 Backend → QA 인수인계

## 전달 목적

AUTH-003 session 인증 계약의 구현 범위와 QA 검증 입력을 전달한다.

## 다음 역할 또는 대상 역할

- 수신: QA Engineer
- 후속 협업: Backend Engineer, Tech Lead

## 입력 문서

- `docs/adr/AUTH-003-session-authentication-approved-inputs.md`
- `docs/handoffs/AUTH-003/tl-to-be.md`
- `docs/reports/AUTH-004/be-report.md`

## 사용 가능한 결과

- session 인증 API 4개
- email 정규화·credential 검증
- 최소 principal과 session·CSRF 생명주기 테스트

## 현재 상태

- 검증 head: `b52b3152a5cd46665a67d9533a582e0509010670`
- Repository Validation run: `29178576306`
- Java 25 Backend test/build, MySQL 8.4와 Frontend install/lint/build 회귀 검증 통과
- 검증 head는 미등록 email dummy BCrypt 비교, 예상 외 예외 로그와 254자 email 경계 테스트를 포함한다.
- Spring Security 기본 `/logout` 비활성화 변경은 Java 25 Repository Validation 실행 후 검증 head와 run을 갱신한다.

## test member 경계

- test profile의 격리 MySQL에 실행 중 생성한다.
- email과 password는 테스트마다 생성하고 BCrypt hash만 저장한다.
- migration·production profile에 credential row가 없다.

## 인증 검증 절차

1. 익명 `GET /api/auth/csrf`로 token과 session을 받는다.
2. 현재 token으로 `POST /api/auth/login`을 호출한다.
3. session id 변경과 `200 {memberId}`를 확인한다.
4. 새 `GET /api/auth/csrf` token이 이전 token과 다른지 확인한다.
5. 같은 session의 `GET /api/auth/me`가 principal memberId를 반환하는지 확인한다.
6. 새 token으로 `POST /api/auth/logout`을 호출한다.
7. `204`, session·context·token 정리와 JSESSIONID 종료를 확인한다.
8. logout 후 `/api/auth/me`의 `401 AUTH_REQUIRED`를 확인한다.

## 비승인 logout 경로 검증

1. 승인된 login으로 인증 session과 새 CSRF token을 준비한다.
2. 같은 session으로 `GET /logout`을 호출해 HTML 확인 페이지와 redirect가 없고 `403 ACCESS_DENIED`인지 확인한다.
3. 같은 session과 유효한 CSRF token으로 `POST /logout`을 호출해 redirect와 session 종료가 없고 `403 ACCESS_DENIED`인지 확인한다.
4. 두 요청 뒤 같은 session의 `GET /api/auth/me`가 기존 memberId로 `200`을 반환하는지 확인한다.
5. 이어서 승인된 `POST /api/auth/logout`을 호출해 `204`와 session·SecurityContext·CSRF token·JSESSIONID 정리를 확인한다.

## 실패와 validation

- 존재하지 않는 email과 잘못된 password는 동일한 `401 INVALID_CREDENTIALS` status·code·message다.
- 두 credential 실패 경로는 요청마다 `PasswordEncoder.matches`를 정확히 한 번 호출하고 SecurityContext를 저장하지 않는다.
- dummy hash는 애플리케이션 시작 시 한 번 생성해 재사용하며 요청마다 encode하지 않는다.
- email 형식과 null·빈 password는 `400 VALIDATION_FAILED`와 field·message만 있는 fieldErrors다.
- 정확히 254자인 승인 범위 email은 통과하고 255자는 거부한다.
- malformed JSON은 HTML이 아닌 `VALIDATION_FAILED` JSON이다.
- 익명 logout + 유효 CSRF는 `401`; CSRF 누락은 `403 CSRF_INVALID`다.
- 예상하지 못한 인증 API 예외는 안전한 `500 INTERNAL_ERROR`를 반환하고 서버에 안정적인 메시지와 예외 stack trace를 기록한다.
- Spring Security 기본 `GET /logout`, `POST /logout`은 비활성화되어 redirect와 session 종료를 수행하지 않는다.

## 민감정보 확인

- 응답·URL·로그에 password, hash와 session id가 없어야 한다.
- CSRF token은 `/api/auth/csrf` 성공 body 이외에 노출하지 않는다.
- 추가 예외 로그 메시지에 email, password, password hash, session id와 CSRF token을 직접 넣지 않는다.
- principal은 JPA Member가 아니며 credentials는 null이어야 한다.

## 검증 포인트

- login 전후 session id와 CSRF token이 모두 변경되는가?
- generic credential 실패 응답으로 회원 존재 여부가 구분되지 않는가?
- `/api/auth/me`가 client 입력이 아닌 principal memberId만 사용하는가?
- logout 뒤 기존 session·token이 재사용되지 않는가?

## 미결정 사항 또는 승인 필요 항목

- AUTH-004 범위 안의 추가 Product Decision은 없다.
- cross-origin 배포가 필요하면 CORS·cookie·CSRF 계약을 별도로 결정해야 한다.

## 남은 위험 또는 주의 사항

- 실제 reverse proxy와 HTTPS cookie 전달은 배포 환경에서 별도 확인해야 한다.
- 현재 자동 검증은 실제 login/logout/product API를 함께 완료했다는 의미가 아니며 공개 상품 API는 미구현이다.

## 제외 범위와 중단 조건

- 실제 공개 상품 API는 아직 구현하지 않는다.
- CORS·cross-site cookie, JWT·OAuth2, schema 변경이나 production credential이 필요하면 QA를 중단한다.
- Java 25 Backend test/build 또는 MySQL 8.4 검증 실패 시 인수 완료로 판단하지 않는다.
