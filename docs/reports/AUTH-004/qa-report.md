# AUTH-004 QA 독립 검증 보고서

## 작업 정보

- 작업 ID: `AUTH-004`
- 역할: QA Engineer
- 기준 브랜치: `main`
- 기준 commit: `3c7fcf30a727e156a7a9059ebeba2a6edacb8d24`
- 작업 브랜치: `test/qa`
- 선행 조건: PR #34와 PR #37이 사용자 승인으로 `main`에 병합됨

## 작업 목적

- PR #34로 병합된 세션 인증 API를 AUTH-003 승인 계약과 Backend→QA 인수인계로 독립 검증한다.
- Backend 보고를 승인 근거로 대신하지 않고 승인 문서, 병합 코드, 자동 테스트와 실제 실행 결과를 직접 대응시킨다.
- 재현 가능한 제품 코드 결함이 없고 승인 항목이 충족되면 검증 증거의 한계를 조건에 명시해 QA 판정을 남긴다.

## 입력 문서

- 보고서 구조: `docs/reports/task-report-template.md`
- 참고·검증 입력: `docs/handoffs/AUTH-003/tl-to-be.md`
- 참고·검증 입력: `docs/reports/AUTH-004/be-report.md`
- 참고·검증 입력: `docs/handoffs/AUTH-004/be-to-qa.md`
- 구현·테스트 증거: PR #34 최종 diff와 merge commit `bc0d17de4d0e04a7cd045f02759c964e8e525100`
- Security 회귀 증거: PR #37 merge commit `a319164cac53ed83937dba66895c26e3f21a3dab` 이후 `main`

## 승인 입력

- AUTH-003 Approved 계약: `docs/adr/AUTH-003-session-authentication-approved-inputs.md`
- 위 문서만 사용자 승인 범위를 직접 정의한다. Backend 보고서, 인수인계, PR diff와 자동 테스트는 승인 원본이 아니라 구현·검증 증거로 사용한다.

## 변경 범위

- 승인된 인증 API 네 개의 method, URI, 인증·CSRF, 성공·오류 응답 검토
- 로그인 전후 session ID·CSRF token 회전과 principal·SecurityContext 저장 검토
- credential 실패 동일성, BCrypt 비교 횟수, dummy hash 생성·재사용 검토
- validation, email 254/255자, malformed JSON과 예외 처리 검토
- 승인 logout과 비승인 기본 `/logout` 경계 및 logout 정리 검토
- 응답·URL·로그의 민감정보 비노출과 공개 상품 GET Security 회귀 검토
- 병합 구현과 기존 자동 테스트의 직접 대응이 충분한지 판단

## 변경하지 않은 범위

- 인증·보안 및 그 밖의 제품 코드
- AUTH-003 승인 계약과 email 문법 정책
- DB schema, Flyway migration, dependency와 CI workflow
- JWT, OAuth2, 회원가입, 비밀번호 생명주기, CORS와 cross-origin cookie 정책
- Frontend 로그인 화면·라우팅, Open Redirect와 PRODUCT-001 전체 QA
- 결함이 없으므로 `docs/qa/AUTH-004/**`와 `docs/handoffs/AUTH-004/qa-to-be.md`는 작성하지 않음

## 인수 조건 매핑

| 승인 항목 | 병합 구현과 자동 증거 | QA 결과 |
| --- | --- | --- |
| 익명 CSRF와 session 획득 | `HttpSessionCsrfTokenRepository`, `/api/auth/csrf`, `csrfEndpointReturnsNoStoreTokenForAnonymousSession` | 계약 일치 |
| 유효 token login, session ID 변경, CSRF 갱신 | `AuthApplicationService`, 복합 session strategy, `loginChangesSessionIdPersistsSafePrincipalAndRotatesCsrfToken` | 계약 일치 |
| `/api/auth/me`의 principal `memberId` | `AuthenticatedMemberPrincipal`, client 입력 무시와 JPA Member·credential 부재 assertion | 계약 일치 |
| 갱신 token logout과 session·context·token·cookie 정리 | 복합 logout handler, session 무효화·JSESSIONID Max-Age 0·기존 session/token 재사용 거부 assertion | 계약 일치 |
| logout 후 보호 API | 기존 JSESSIONID로 `/api/auth/me` 호출 시 `401 AUTH_REQUIRED` assertion | 계약 일치 |
| 두 credential 실패의 동일 응답·비교 1회·context 미저장 | 통합 응답 비교와 `AuthApplicationServiceTests`의 `times(1)`, 상호작용 부재 assertion | 계약 일치 |
| dummy BCrypt hash 시작 시 1회 생성·재사용 | 생성자 `encode`와 두 요청에 대한 encode 1회·matches 2회 assertion | 계약 일치 |
| null·빈 값·잘못된 email, 254/255자, malformed JSON | `EmailNormalizerTests`와 `validationAndMalformedJsonReturnJsonErrors` | 계약 일치 |
| CSRF 누락·불일치, 익명·인증 logout | Security 통합 테스트의 누락 token, logout 후 폐기된 token, 익명 유효 token 경계 | 계약 일치 |
| 기본 `GET/POST /logout` 비활성화 | `logout.disable()`과 두 요청 후 session·`/api/auth/me` 유지 assertion | 계약 일치 |
| 예상 외 예외의 안전한 500과 서버 로그 | `AuthExceptionHandler`의 고정 응답과 원인 stack trace를 확인하는 output capture test. 예외 message 자동 redaction은 증명하지 않음 | 응답 계약 일치, 로그 증거 제한 |
| 공개 상품 목록·상세 GET 회귀 | `SecurityConfig`, `SecurityFoundationIntegrationTests`, `ProductApiIntegrationTests`의 익명·CSRF 없는 GET | 회귀 없음 |

## 주요 결과

- 승인 계약과 PR #34 병합 코드는 API, session·CSRF 생명주기, credential 실패와 logout 경계에서 일치한다.
- PR #34 merge 이후 최신 `main`의 인증 제품·테스트 트리는 동일하며 PR #37의 공개 상품 구현과 함께 Security 회귀 증거가 유지된다.
- 기존 테스트는 요청된 session lifecycle, credential 실패와 Security 회귀를 직접 단언해 해당 범위에 QA 전용 테스트를 추가하지 않았다.
- 현재 코드·테스트에서 직접 확인한 범위에는 제품 코드 결함, 새로운 정책 결정 필요 사항과 민감정보 직접 노출이 없다.
- 예상 외 exception message에 대한 일반적인 민감정보 redaction은 자동화 증거가 없어 배포·통합 QA 재확인 조건으로 남긴다.

## 민감정보와 안전 오류 결과

- principal은 `memberId`만 포함하고 인증 credentials는 null이며 JPA `Member`를 session principal로 사용하지 않는다.
- login 성공 응답은 password, password hash와 session ID 부재를 직접 단언한다.
- credential 실패 응답은 회원 존재 여부와 입력 credential을 노출하지 않는 동일한 `401 INVALID_CREDENTIALS`다.
- 정적 검토에서 인증 제품 코드는 email, password, password hash, session ID와 CSRF token을 logger 인자로 직접 전달하지 않는다. session ID와 CSRF token을 승인 위치 외 응답·URL에 넣는 코드도 확인되지 않았다.
- `AuthValidationException`과 `InvalidCredentialsException`은 입력값을 포함하지 않는 고정 message를 사용하고 전용 handler는 이를 logger에 전달하지 않는다.
- 예상 외 예외 응답은 원인을 숨긴 `500 INTERNAL_ERROR`와 빈 `fieldErrors`를 사용한다. `AuthExceptionHandlerTests`는 고정 응답과 exception stack trace 기록을 검증한다.
- 해당 output-capture 테스트는 예외 객체의 message에 민감정보가 포함됐을 때 자동 제거된다는 사실을 증명하지 않는다. 실제 인증 흐름에서 민감정보가 exception message·로그에 포함되는 재현 경로는 확인되지 않았지만 일반적인 redaction 보장은 검증 범위 밖이다.

## 추가하거나 수정한 테스트

- 없음.
- 기존 통합·단위 테스트가 session lifecycle, 실패·경계 조건과 관련 공개 상품 Security 회귀를 직접 증명해 해당 범위의 중복 테스트를 추가하지 않았다.
- 예외 message redaction은 새로운 logging 정책과 제품 코드 변경이 필요할 수 있어 QA 테스트로 임의 정책을 고정하지 않았다.

## 검증 결과

- PR #34 상태와 최종 diff 확인: merged, merge commit `bc0d17de4d0e04a7cd045f02759c964e8e525100`
- PR #37 상태와 병합 확인: merged, merge commit `a319164cac53ed83937dba66895c26e3f21a3dab`
- 최신화 후 local `main` = `origin/main` = `3c7fcf30a727e156a7a9059ebeba2a6edacb8d24`
- PR #34 merge와 최신 `main`의 인증 main/test 경로 비교: 차이 없음
- 승인 원본, 병합 구현과 자동 테스트 직접 대조: 계약 충돌·제품 결함 없음
- 인증 custom exception과 logger 호출 정적 검토: 입력 credential·session·CSRF 값을 logger 인자로 직접 전달하는 코드와 실제 민감정보 로그 재현 경로 없음
- `java -version`: OpenJDK 17 확인
- focused Backend test 명령: 테스트 실행 전 Java 25 toolchain 탐색 실패
- `py scripts\validate-task-artifacts.py --task-id AUTH-004`: 통과
- `git diff --check`: 통과
- 문서 수정 전 검증 이력: Repository Validation run `29233020898`, head `6c70e6971ffe652079dc0c809f6ef59553dbddc2`에서 conventions, task artifact validator, Java 25·MySQL 8.4 Backend test/build와 Frontend install/lint/build 전체 통과
- 현재 PR head와 CI의 동적 상태는 GitHub PR #38을 권위 있는 원본으로 확인한다.

## 실행하지 못한 검증과 이유

- 로컬 focused 인증·Security·공개 상품 통합 테스트와 Backend build는 Java 25 toolchain이 없고 download repository도 구성되지 않아 실행하지 못했다.
- 같은 toolchain 원인의 Gradle 명령은 반복하지 않았고 JDK downgrade, dependency·build 설정 변경이나 도구 설치를 수행하지 않았다.
- Java 25·MySQL 8.4 Backend test/build와 Frontend 회귀는 QA PR의 Repository Validation 성공 결과로 보완했다.

## 발견한 결함과 심각도

- 재현 가능한 제품 코드 결함과 보안 문제는 확인되지 않았다.
- 실제 인증 흐름의 민감정보 로그 노출 재현 경로가 없어 실패 테스트, 버그 보고서와 Backend 인수인계는 작성하지 않았다.
- 예상 외 exception message의 자동 redaction은 증명되지 않은 제한이며, 현재 확인된 제품 결함으로 과장하지 않는다.

## 최종 판정

- 최종 판정: **조건부 통과**
- 승인 계약, 병합 구현과 기존 자동 증거는 일치하며 확인된 제품 결함이 없다.
- 조건 1: 현재 PR head의 Repository Validation 전체가 성공해야 한다.
- 조건 2: 배포·통합 QA에서 실제 인증 오류 로그에 email, password, password hash, session ID와 CSRF token이 없는지 재확인해야 한다.

## 적용 방법

- 추가 제품 코드와 런타임 설정 적용 사항은 없다.
- 이 보고서는 AUTH-003 승인 항목과 PR #34 병합 구현·테스트의 대응 및 QA 판정 근거로 사용한다.
- 기존 인증·Security·공개 상품 테스트를 Repository Validation에서 그대로 실행한다.

## 위험과 제한

- 로컬 Java 25·MySQL 8.4 환경에서 직접 실행한 근거가 없다.
- 실제 HTTPS reverse proxy의 Secure·SameSite cookie 전달은 배포 환경에서 별도 확인해야 한다.
- same-origin 또는 reverse proxy 전제만 검증했으며 cross-origin 정책은 승인 범위 밖이다.
- 예상 외 예외는 stack trace를 기록하며 exception message의 일반적인 민감정보 redaction은 제품 코드와 현재 테스트가 자동 보장하지 않는다. 실제 인증 오류 로그의 비노출을 배포·통합 QA에서 다시 확인한다.

## 다음 작업

- 사용자가 QA 보고서와 독립 검증 근거를 검토하고 병합 여부를 결정한다.
- 배포·통합 QA에서 실제 인증 오류 로그의 민감정보 비노출을 재확인한다.
- 자동 병합하지 않으며 최종 병합 여부는 사용자가 결정한다.

## Git 결과

- 기존 PR #37 역할 브랜치 commit `0695f0c224184aa7ed0ecbc87ffa577c85701c93`은 로컬 `backup/test-qa-before-AUTH-004`에 보존했다.
- 기존 원격 `test/qa` tree가 PR #37 merge tree와 같음을 확인한 뒤 원격 역할 브랜치를 일반 삭제했다.
- 최신 `main`에서 새 로컬 `test/qa`를 생성했으며 reset, rebase와 force push를 사용하지 않았다.
- 최초 QA 보고서 commit `b6c7342dafe0faf51a2f54baa2a1412c51a474e2`를 일반 push했다.
- 최종 판정 갱신 commit `6c70e6971ffe652079dc0c809f6ef59553dbddc2`를 일반 push했다.
- 이번 리뷰 반영도 일반 commit과 일반 push로 갱신하며 현재 head는 GitHub PR #38을 권위 있는 원본으로 확인한다.

## PR 상태

- AUTH-004 QA PR #38은 `main` ← `test/qa`로 생성됐으며 생성 당시 Draft였다.
- 현재 head, Repository Validation, Ready와 mergeable 상태는 동적 정보이므로 GitHub PR #38을 권위 있는 원본으로 확인한다.
- 자동 병합하지 않는다.
