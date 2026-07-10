# AUTH-003 세션 인증 계약 승인 입력

## 문서 정보

- 작업 ID: `AUTH-003`
- 역할: Tech Lead
- 문서 상태: Approved
- 승인일: 2026-07-10
- 기준 브랜치: `main`
- 작업 브랜치: `ops/tl`
- 선행 작업: ARCH-006, AUTH-002
- 선행 PR: #29, #30

## 목적

AUTH-002에서 `Recommended`와 `Decision Required`로 제안한 DR1~DR3를 사용자가 AUTH-003 요청에서 모두 승인한 세션 인증 구현 입력으로 기록한다.

이 문서는 승인 기록이며 Spring Security, API, JPA, Flyway migration, Frontend 또는 CI를 구현하지 않는다. AUTH-002 원본의 문서 상태는 `Decision Proposal`로 유지하며, PR #30 병합 자체를 승인 근거로 해석하지 않는다.

## 승인 주체와 승인 근거

- 승인 주체: 사용자(Product Owner 및 Tech Lead)
- 승인 입력: AUTH-003 요청에 명시된 DR1, DR2, DR3 전체 승인과 추가 수정 사항 없음
- 선행 기술 입력: PR #29로 병합된 `docs/adr/ARCH-006-first-backend-implementation-approved-inputs.md`
- 결정 제안: PR #30으로 병합된 `docs/adr/AUTH-002-session-authentication-contract-proposal.md`
- 해석: AUTH-002의 `Recommended`는 AUTH-002 안에서는 승인 상태로 바꾸지 않는다. 사용자 승인 범위만 이 AUTH-003 문서에서 `Approved`로 기록한다.
- 충돌 확인: 사용자 승인 입력과 제품 요구사항, AUTH-001, API-001, DATA-001, DATA-002, ARCH-001, ARCH-003, ARCH-006, FOUNDATION-000 사이에 AUTH-003 기록을 막는 충돌 없음

## 승인 상태 매핑

| 결정 묶음 | AUTH-002 상태 | AUTH-003 상태 | 사용자 수정 |
| --- | --- | --- | --- |
| DR1 최소 인증 API 계약 | Recommended, Decision Required | Approved | 없음 |
| DR2 CSRF·session cookie 계약 | Recommended, Decision Required | Approved | 없음 |
| DR3 최소 인증 데이터 계약 | Recommended, Decision Required | Approved | 없음 |
| 첫 인증 구현 origin 전제 | Recommended | Approved: same-origin 또는 reverse proxy | 없음 |

## Approved DR1. 최소 인증 API 계약

### 공통 원칙

- 로그인 식별자는 정규화된 `email`이다.
- `/api/**`는 인증·인가·CSRF 실패 시 HTML 또는 redirect가 아니라 JSON 오류 응답을 반환한다.
- 오류 응답은 `code`, `message`, `fieldErrors` 구조를 사용하고 field error가 없으면 `fieldErrors`는 빈 배열이다.
- 존재하지 않는 email과 잘못된 password는 같은 `401 INVALID_CREDENTIALS`로 처리해 회원 존재 여부를 구분하지 않는다.
- session id, password, password hash를 응답과 로그에 노출하지 않는다.

### API 계약

| Method | URI | 인증 | CSRF | 요청 | 성공 응답 | 주요 오류 코드 |
| --- | --- | --- | --- | --- | --- | --- |
| `GET` | `/api/auth/csrf` | 불필요 | 불필요 | 없음 | `200 OK`, `{ "token": "..." }`, `Cache-Control: no-store` | `INTERNAL_ERROR` |
| `POST` | `/api/auth/login` | 불필요 | 필요 | `{ "email": "...", "password": "..." }` | `200 OK`, `{ "memberId": number }` | `VALIDATION_FAILED`, `INVALID_CREDENTIALS`, `CSRF_INVALID`, `INTERNAL_ERROR` |
| `POST` | `/api/auth/logout` | 필요 | 필요 | 없음 | `204 No Content` | `AUTH_REQUIRED`, `CSRF_INVALID`, `INTERNAL_ERROR` |
| `GET` | `/api/auth/me` | 필요 | 불필요 | 없음 | `200 OK`, `{ "memberId": number }` | `AUTH_REQUIRED`, `INTERNAL_ERROR` |

인증된 사용자가 권한 없는 다른 `/api/**` 기능에 접근하면 `ACCESS_DENIED`를 사용한다. 구독 소유권 API는 Deferred이므로 AUTH-003에서 해당 URI나 상세 응답을 확정하지 않는다.

## Approved DR2. CSRF·session cookie 계약

### CSRF 저장과 전달

- `HttpSessionCsrfTokenRepository`를 사용하고 기대 token을 HTTP session에 저장한다.
- `GET /api/auth/csrf`의 JSON body로 token을 전달하며 응답 필드명은 `token`이다.
- 상태 변경 요청의 header 이름은 `X-CSRF-TOKEN`, request parameter 이름은 `_csrf`다.
- CSRF 전용 cookie는 사용하지 않는다.
- Frontend는 token을 영속 저장소가 아니라 메모리에 보관한다.

### CSRF token 생명주기

1. 로그인 전에 token을 획득한다.
2. 로그인 `POST`에 현재 token을 전달한다.
3. 로그인 성공 직후 token을 다시 획득한다.
4. logout `POST`에 현재 token을 전달한다.
5. logout 성공 후 Frontend token을 폐기한다.
6. CSRF 실패 시 비멱등 요청을 자동 재실행하지 않는다.
7. token을 다시 획득한 뒤 사용자의 명시적 재시도를 사용한다.

### session cookie

| 속성 | Approved 값 |
| --- | --- |
| 이름 | `JSESSIONID` |
| `HttpOnly` | `true` |
| `SameSite` | `Lax` |
| `Secure` | HTTPS 환경 `true`; 로컬 HTTP 개발 환경에서만 `false` |
| `Domain` | 미설정, host-only |
| `Path` | `/` |
| 지속성 | persistent `Max-Age` 없음 |
| 서버 유휴 만료 | 30분 |

### session 보안

- 로그인 성공 시 session id를 변경한다.
- 로그아웃 시 session을 무효화하고 SecurityContext를 정리하며 CSRF token을 폐기한다.
- session id를 응답 body, URL과 로그에 노출하지 않는다.

### 배포 경계

- 첫 인증 구현은 same-origin 또는 reverse proxy 기반으로 진행한다.
- Frontend가 같은 origin의 `/api/**`를 호출하는 구조를 기본으로 사용한다.
- 별도 origin과 cross-site 배포는 현재 지원 범위에서 제외한다.
- 별도 origin이 필요하면 CORS, SameSite, Secure와 CSRF 계약을 별도 사용자 결정으로 다룬다.

## Approved DR3. 최소 인증 데이터 계약

### email 계약

- 로그인 식별자는 `members.email`이다.
- 첫 MVP는 ASCII email만 허용한다.
- 앞뒤 ASCII space와 tab을 제거한다.
- local-part의 대소문자는 보존하고 domain만 locale-independent ASCII 소문자로 변환한다.
- Unicode local-part, IDN과 SMTPUTF8은 Deferred다.
- provider별 점 제거, `+tag` 제거, alias 통합, provider 또는 domain allowlist 적용을 금지한다.
- 제어 문자, local-part 또는 domain 내부 공백, 여러 `@`, 빈 local-part, 빈 domain과 전체 길이 254자 초과 입력은 `VALIDATION_FAILED`로 거부한다.

### email 물리 계약과 충돌 처리

```text
VARCHAR(254)
CHARACTER SET ascii
COLLATE ascii_bin
NOT NULL
UNIQUE
```

- 정규화 결과가 기존 credential과 충돌하면 작업을 실패시킨다.
- 자동 병합과 자동 덮어쓰기를 금지한다.
- migration, test fixture와 local bootstrap에도 같은 규칙을 적용한다.

### 비밀번호 계약

- 컬럼명은 `password_hash`다.
- 타입은 `VARCHAR(100) NOT NULL`이며 default는 없다.
- `BCryptPasswordEncoder`를 사용한다.
- 평문 비밀번호 저장과 직접 비교를 금지한다.
- 비밀번호와 hash를 로그에 출력하지 않는다.

### 데이터 생성 경계

- V1 migration에는 회원 credential row를 넣지 않는다.
- production 공용 seed 회원을 만들지 않는다.
- 테스트 회원은 test profile fixture에서 만들고 테스트 실행 시 BCrypt hash를 생성해 격리된 DB에 삽입한다.
- 로컬 수동 회원은 local-only bootstrap으로 만들고 실제 credential 값은 환경 변수로 전달한다.
- credential 값을 저장소나 문서에 기록하지 않는다.
- production profile에서는 local bootstrap을 실행하지 않는다.

## 영향

### API 영향

- Backend와 Frontend가 공유할 login, logout, 현재 회원, CSRF endpoint의 method·URI·요청·응답·오류 코드가 확정됐다.
- 인증·인가·CSRF 실패는 `/api/**` JSON 오류 계약을 따른다.
- session id와 credential은 API에 노출하지 않는다.

### DB 영향

- 후속 구현의 `members.email` 정규화·ASCII·case-sensitive unique 계약과 `password_hash VARCHAR(100) NOT NULL` 입력이 확정됐다.
- AUTH-003은 schema나 migration을 변경하지 않는다.
- production seed 금지와 test/local 데이터 생성 경계가 확정됐다.

### Security 영향

- session 저장 CSRF, token endpoint/header/parameter, 로그인·로그아웃 전후 token 생명주기가 확정됐다.
- session cookie 속성, session fixation 방어, logout 정리와 30분 idle timeout이 구현 입력이 됐다.
- cross-site 배포는 지원하지 않으며 별도 결정 없이는 CORS를 추가하지 않는다.

### Frontend 영향

- token은 메모리에 보관하고 로그인 전·성공 직후 재획득하며 logout 성공 후 폐기한다.
- `401 AUTH_REQUIRED`와 `403 CSRF_INVALID`을 구분해야 한다.
- CSRF 실패 후 상태 변경 요청은 자동 재실행하지 않고 사용자 재시도를 요구한다.
- AUTH-003은 Frontend 코드를 변경하지 않는다.

## 구현 시 검증 조건

### API·Security

- 로그인 전 CSRF 획득, 유효 token을 포함한 로그인, 로그인 성공 직후 token 재획득, 유효 token을 포함한 logout, logout 후 token 폐기 흐름을 통합 테스트로 증명한다.
- 로그인 성공 시 session id가 바뀌고 logout·만료 후 보호 API가 `401 AUTH_REQUIRED` JSON을 반환한다.
- login 성공·실패, logout, `/api/auth/me`, CSRF 누락·불일치의 status·body·code가 계약과 일치한다.
- 존재하지 않는 email과 잘못된 password의 상태·코드·메시지가 같고 HTML·redirect가 없다.
- cookie의 이름, HttpOnly, SameSite, Secure, Domain, Path, Max-Age와 idle timeout을 환경별로 검증한다.

### DB·데이터

- email 정규화, local-part case 보존과 domain 소문자화를 테스트한다.
- 제어 문자, 앞뒤 ASCII space·tab 제거 후 빈 값, local-part 또는 domain 내부 공백, 여러 `@`, 빈 local-part, 빈 domain, 전체 길이 254자 초과와 ASCII 범위 밖 문자를 `VALIDATION_FAILED`로 거부하는지 테스트한다.
- 정규화 결과의 unique 충돌이 자동 병합이나 덮어쓰기 없이 실패하는지 테스트한다.
- 빈 MySQL schema에 migration을 적용해 email과 `password_hash` 물리 계약을 확인한다.
- migration에 credential row가 없고 production profile에서 test fixture와 local bootstrap이 실행되지 않음을 검증한다.
- test fixture는 실행 시 BCrypt hash를 만들고 격리된 DB를 사용한다.

### 민감정보·경계

- 응답, URL, 예외와 로그에 password, password hash와 session id가 없음을 확인한다.
- CSRF token은 승인된 `/api/auth/csrf` 성공 JSON 외 응답·URL·로그에 노출하지 않는다.
- same-origin 또는 reverse proxy 전제를 실제 개발·배포 구조에서 확인한다.
- 실제 인증 구현은 독립 QA 검증을 거친다.

## Deferred

- JWT
- OAuth2
- 회원 가입
- 비밀번호 재설정·변경
- email 인증
- 계정 잠금
- rate limit
- remember-me
- 동시 session 제한
- device/session 목록 조회
- 다른 session 또는 device 강제 로그아웃
- cross-site Frontend·Backend 배포
- 국제화 email
- IDN
- SMTPUTF8
- email 변경 정책
- 회원 탈퇴 후 email 보존·재사용 정책
- 탈퇴 후 재가입 정책
- 구독 API

Deferred 항목은 후속 가능성을 기록한 것이며 이번 승인이나 첫 구현에서 관련 의존성, 추상화, endpoint 또는 설정을 미리 만들 수 있다는 뜻이 아니다.

## Explicitly Excluded

### AUTH-003 문서 작업에서 제외

- `backend/**`, `frontend/**`, `.github/**`
- `build.gradle`, `settings.gradle`
- DB migration, JPA Entity, Repository, Service, Controller, DTO, SecurityConfig
- datasource·Flyway·cookie·CORS 설정
- 신규 의존성, Docker, Docker Compose, Secret

### 승인으로 해석하면 안 되는 범위

- AUTH-001, AUTH-002, API-001, DATA-001, DATA-002, ARCH-001, ARCH-003, ARCH-006, FOUNDATION-000 원본 상태 변경
- JWT, OAuth2, 구독 API와 cross-site 배포 구현
- 실제 회원, 실제 email, 실제 password·hash·session id·CSRF token·Secret 기록
- production 공용 seed 회원

## 남은 위험

- `/api/auth/csrf`가 익명 session을 만들 수 있어 bot 트래픽과 session 저장 비용을 측정해야 한다.
- CSRF 실패와 session 만료가 동시에 발생하면 오류 우선순위에 따라 Frontend 분기가 복잡해질 수 있다.
- same-origin 또는 reverse proxy 가정과 실제 배포 구조가 다르면 cookie·CSRF 계약을 다시 결정해야 한다.
- 로컬 bootstrap의 production 차단이 불완전하면 test account가 운영에 생성될 수 있다.
- BCrypt strength는 AUTH-003에서 값이 확정되지 않았으며 구현 환경에서 측정 근거가 필요하다.
- 실제 인증·credential 구현은 보안과 개인정보 영향 때문에 사용자와 QA의 독립 검토가 필요하다.

## 다음 Backend 작업 진입 조건

1. 최신 `main`에서 clean `feat/be` 역할 브랜치를 준비한다.
2. Backend Engineer 역할의 `AGENTS.md`, 역할 문서와 Skill을 읽는다.
3. ARCH-006 Approved Inputs와 이 AUTH-003 Approved 문서를 함께 구현 입력으로 사용한다.
4. 실제 Frontend·Backend 배포가 same-origin 또는 reverse proxy인지 확인한다.
5. 구현 범위와 PR 분할, Java 25·MySQL·Flyway·Security 검증 방법을 명시한다.
6. 인증·인가, cookie, CSRF, credential 저장을 검증할 독립 QA 작업을 준비한다.
7. CI MySQL service 변경이 필요하면 Backend에서 `.github/**`를 수정하지 않고 Platform/SRE 작업으로 분리한다.
8. Deferred·Explicitly Excluded 항목, 실제 credential 또는 Secret이 필요하면 구현을 중단한다.

## 사용자 승인

2026-07-10 AUTH-003 요청에서 사용자가 AUTH-002의 DR1, DR2, DR3를 수정 없이 모두 승인했다. 이 승인 범위와 same-origin 또는 reverse proxy 전제를 AUTH-003의 `Approved` 구현 입력으로 기록한다.
