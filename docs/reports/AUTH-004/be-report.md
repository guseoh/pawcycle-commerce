# AUTH-004 Backend 작업 보고서

## 작업 정보

- 작업 ID: `AUTH-004`
- 역할: Backend Engineer
- 기준 브랜치: `main`
- 작업 브랜치: `feat/be`
- 상태: 리뷰 수정과 Java 25·MySQL 원격 검증 완료

## 목적

AUTH-003에서 승인한 최소 session 인증 API와 session·CSRF 생명주기를 구현한다.

## 승인 입력

- `docs/adr/AUTH-003-session-authentication-approved-inputs.md`
- `docs/handoffs/AUTH-003/tl-to-be.md`
- `docs/adr/ARCH-006-first-backend-implementation-approved-inputs.md`
- `docs/handoffs/FOUNDATION-003/be-to-qa.md`

## 변경 범위

- `GET /api/auth/csrf`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- email 정규화·검증, BCrypt credential 확인
- memberId 전용 principal, session fixation과 SecurityContext 저장
- 로그인·로그아웃 CSRF token 교체·폐기와 logout 정리
- Spring Security 기본 `GET /logout`, `POST /logout`과 redirect logout 비활성화
- JSON validation·credential·security 오류 응답
- 집중 단위·통합 테스트

## 변경하지 않은 범위

- 공개 상품 API와 회원 가입
- JWT, OAuth2, CORS, remember-me
- DB schema, V1 migration, 신규 dependency
- local bootstrap, production seed와 실제 credential

## 주요 결과

- 미등록 email도 애플리케이션 시작 시 한 번 생성한 dummy BCrypt hash로 비교해 credential 실패 경로마다 `PasswordEncoder.matches`를 정확히 한 번 실행한다.
- dummy hash는 요청마다 생성하지 않으며 실제 credential, raw 값과 hash를 응답·문서·로그에 기록하지 않는다.
- 예상하지 못한 인증 API 예외는 기존 `500 INTERNAL_ERROR` 응답을 유지하고 안정적인 서버 메시지와 stack trace를 기록한다.
- 정확히 254자인 email 정상 경계를 테스트하며, AUTH-003에서 승인하지 않은 local-part·domain label 구조 정책은 추가하지 않는다.
- filter-chain 기본 logout은 비활성화하고 승인된 `POST /api/auth/logout`만 애플리케이션 서비스의 logout handler를 실행한다.

## 계층과 트랜잭션

- API 계층은 HTTP 요청·응답과 인증 principal 매핑만 담당한다.
- application 계층은 정규화, 회원 조회, BCrypt, session strategy와 SecurityContext repository를 조율한다.
- login 회원 조회는 read-only transaction이며 session·SecurityContext 변경은 DB transaction과 분리된다.
- logout은 DB transaction 없이 Spring Security logout handler로 session·context·CSRF token·cookie를 정리한다.

## email 정규화 규칙

- 앞뒤 ASCII space·tab만 제거한다.
- local-part case는 보존하고 domain만 `Locale.ROOT`로 소문자화한다.
- ASCII, 단일 `@`, 비어 있지 않은 local/domain과 정규화 후 최대 254자를 검증한다.
- 제어 문자·내부 공백·여러 `@`·non-ASCII를 거부한다.
- password는 trim하거나 변형하지 않는다.

## Authentication과 session

- principal은 `AuthenticatedMemberPrincipal(memberId)`이며 JPA Entity와 email·credential을 포함하지 않는다.
- `UsernamePasswordAuthenticationToken` credentials는 null이고 권한은 현재 승인 범위에서 비어 있다.
- `ChangeSessionIdAuthenticationStrategy`가 login session id를 변경한다.
- `HttpSessionSecurityContextRepository`가 SecurityContext를 HTTP session에 저장한다.
- `CsrfAuthenticationStrategy`가 login 전 token을 폐기하고 다음 `/csrf` 요청에서 새 token을 생성한다.
- logout은 `CsrfLogoutHandler`, `SecurityContextLogoutHandler`, `CookieClearingLogoutHandler`로 token·session·context·JSESSIONID를 정리한다.
- Spring Security 기본 `/logout` filter와 확인 페이지는 명시적으로 비활성화하며, 비승인 `/logout` 요청은 session을 종료하지 않는다.

## 공통 오류 응답

- validation·malformed JSON은 `400 VALIDATION_FAILED` JSON이다.
- 존재하지 않는 email과 잘못된 password는 동일한 `401 INVALID_CREDENTIALS` message를 사용한다.
- 기존 `AUTH_REQUIRED`, `ACCESS_DENIED`, `CSRF_INVALID` Security handler를 재사용한다.
- 예상하지 못한 MVC 오류는 내부 정보를 숨긴 `500 INTERNAL_ERROR` JSON이다.

## local bootstrap

- 승인 문서와 runbook상 수동 검증의 필수 조건이 아니므로 구현하지 않았다.
- test profile fixture만 사용하며 production·migration seed가 없다.

## 검증 결과

- 로컬 focused test: Java 25 toolchain 부재로 미실행
- JDK downgrade, H2와 Testcontainers를 사용하지 않음
- `git diff --check`: 통과
- 최초 Repository Validation run `29152545987`: AUTH-004 산출물 부재로 conventions 실패, Application validation skip
- Repository Validation run `29152600218`: Java 25 compile 통과, validation fieldErrors 배열 assertion 1건 실패
- 집중 수정: fieldErrors 순서·크기를 개별 JSON path로 검증하도록 assertion 구체화
- Repository Validation run `29152674405`, head `c3915fe327a86949b9b6bad5990c5acf9d832265`: Java 25 Backend test/build, MySQL 8.4, Frontend install/lint/build 전체 통과
- AUTH-004 산출물 validator: 통과
- AUTH-004 리뷰 수정 focused test: 로컬 Java 25 toolchain 부재로 실행 진입 전 실패
- Repository Validation run `29178576306`, head `b52b3152a5cd46665a67d9533a582e0509010670`: conventions, Java 25 Backend test/build, MySQL 8.4, Frontend install/lint/build 전체 통과
- `git diff --check`: 통과
- 기본 `/logout` 비활성화 회귀 테스트: Java 25 Repository Validation 실행 예정

## 적용 방법

1. 추가 설정·dependency·DB migration 없이 기존 Spring Boot 애플리케이션을 새 head로 빌드한다.
2. 애플리케이션 시작 시 configured `PasswordEncoder`가 재사용할 dummy hash를 한 번 생성한다.
3. 배포 후 미등록 email과 잘못된 password의 동일한 `401 INVALID_CREDENTIALS` 응답, 인증 context 미저장과 예상 외 예외의 안전한 `500` 응답·서버 로그를 확인한다.
4. same-origin 또는 reverse proxy, 기존 session·CSRF 계약은 변경 없이 유지한다.

## 실행하지 못한 검증과 이유

- 로컬 Java 25 test/build: Java 25 toolchain이 설치되지 않았고 download repository가 구성되지 않았다.
- 로컬 격리 MySQL 검증: 승인된 격리 credential과 실행 환경이 없어 접근하지 않았다.

## 위험과 제한

- same-origin 또는 reverse proxy 전제만 지원하며 cross-site 배포는 별도 결정이 필요하다.
- 실제 deployment cookie와 reverse proxy 동작은 운영 환경에서 재검증해야 한다.

## 다음 작업

- QA가 두 credential 실패 경로의 동일 응답·SecurityContext 미저장과 민감정보 비노출을 재검증한다.
- QA가 비승인 `GET /logout`, `POST /logout`의 redirect·session 종료 부재와 승인된 `/api/auth/logout` 회귀를 재검증한다.
- AUTH-003 범위 밖 email 구조 정책은 Product Owner/Tech Lead의 별도 결정 전까지 추가하지 않는다.

## 민감정보와 DB 영향

- principal과 응답에는 memberId만 포함한다.
- password hash는 BCrypt 비교에만 사용하며 session·응답에 저장하지 않는다.
- session id와 CSRF token은 승인된 위치 밖에 기록하지 않는다.
- DB schema와 migration 변경 없음

## Git 결과

- 구현 commit: `6c0f5dafab1d5e985edcd7a8e47a2c0525ba1ac6`
- 검증 준비 commit: `9ef296d6d1967b7b164c2554b255345526bc2117`
- 테스트 보완 commit: `c3915fe327a86949b9b6bad5990c5acf9d832265`
- 리뷰 수정 commit: `b52b3152a5cd46665a67d9533a582e0509010670`
- PR #34 Ready for review, `main` ← `feat/be`
- 일반 push만 사용했다.
- 자동 병합하지 않는다.

## PR 상태

- PR #34는 Ready for review이며 자동 병합하지 않는다.
