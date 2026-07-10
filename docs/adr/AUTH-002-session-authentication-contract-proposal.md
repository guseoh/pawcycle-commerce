# AUTH-002: 세션 인증 최소 계약 제안

## 상태

Decision Proposal

## 날짜

2026-07-10

## 작업 ID

`AUTH-002`

## 목적

ARCH-006의 `Decision Required`인 DR1 최소 인증 API, DR2 CSRF·session cookie, DR3 최소 인증 데이터를 첫 Backend 구현 전에 사용자가 선택할 수 있는 하나의 계약으로 제안한다. 이 문서는 결정 제안이며 Spring Security, API, DB 또는 Frontend 구현을 승인하거나 수행하지 않는다.

## 상태 용어

| 상태 | 의미 |
| --- | --- |
| Approved Input | ARCH-006에서 사용자가 이미 승인한 입력 |
| Recommended | AUTH-002가 사용자 승인을 요청하는 단일 추천안 |
| Alternative | 추천안과 함께 검토한 대안이며 선택되지 않음 |
| Decision Required | 사용자가 승인·수정·보류해야 구현 입력이 되는 항목 |
| Deferred | 첫 Backend 구현 뒤로 명시적으로 미룬 항목 |
| Explicitly Excluded | AUTH-002와 첫 인증 구현 범위에 포함하면 안 되는 항목 |

`Recommended`는 `Approved`가 아니다. 아래 DR1~DR3는 사용자의 명시적 선택 전까지 구현 입력으로 사용할 수 없다.

## Approved Input

다음 항목만 ARCH-006에서 이미 승인됐다.

- 브라우저 인증은 session 기반이며 Spring Security를 사용한다.
- 비밀번호 검증에는 `BCryptPasswordEncoder`를 사용하고 평문 비밀번호를 저장하지 않는다.
- CSRF 보호를 활성화하며 상태 변경 요청은 유효한 CSRF token을 요구한다. 공개 `GET`은 token을 요구하지 않는다.
- session cookie는 `HttpOnly`이고 session id는 응답 body, URL, 로그에 노출하지 않는다.
- 인증 principal은 최소 `memberId`를 보유하고 JPA Entity를 principal로 쓰지 않는다.
- 보호 API의 회원 식별자는 body/query가 아니라 인증 컨텍스트의 `memberId`를 사용한다.
- `/api/**` 인증 실패는 `401` JSON, 권한 실패는 `403` JSON이며 로그인 실패도 redirect가 아닌 `401` JSON이다.
- 공통 오류 응답은 `code`, `message`, `fieldErrors`를 가지며 field 오류가 없으면 빈 배열을 사용한다.
- 첫 Backend 구현의 인증 범위는 최소 로그인·로그아웃·현재 회원 식별 기반까지다.

## DR1. 최소 인증 API 계약

### Recommended

- 로그인 식별자는 정규화한 `email` 하나를 사용한다.
- 로그인은 `POST /api/auth/login`, 로그아웃은 `POST /api/auth/logout`, 현재 인증 회원 조회는 `GET /api/auth/me`를 사용한다.
- CSRF 획득 지원 API로 `GET /api/auth/csrf`를 사용한다.
- 로그인 성공은 `200 OK`와 최소 `{ "memberId": number }`, 로그아웃 성공은 `204 No Content`, 현재 회원 조회 성공은 `200 OK`와 최소 `{ "memberId": number }`를 반환한다.
- 인증 오류 코드는 `INVALID_CREDENTIALS`, `AUTH_REQUIRED`, `ACCESS_DENIED`, `CSRF_INVALID`로 고정한다. 요청 검증 실패에는 공통 `VALIDATION_FAILED`를 사용한다.
- 비밀번호나 email 존재 여부, session id, password hash는 성공·실패 body와 로그에 포함하지 않는다.

### API 계약 표

| Method | URI | 인증 | CSRF | 요청 | 성공 | 주요 실패 | 안정 코드 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `GET` | `/api/auth/csrf` | 불필요 | 불필요 | 없음 | `200`, `{ "token": "..." }`, `Cache-Control: no-store` | `500` 공통 오류 | `INTERNAL_ERROR` |
| `POST` | `/api/auth/login` | 불필요 | 필요 | `{ "email": "member@example.com", "password": "..." }` | `200`, `{ "memberId": 1 }`; session id 변경 및 HttpOnly cookie 설정 | 형식 오류 `400`; 자격 증명 불일치 `401`; CSRF 누락·불일치 `403` | `VALIDATION_FAILED`, `INVALID_CREDENTIALS`, `CSRF_INVALID` |
| `POST` | `/api/auth/logout` | 필요 | 필요 | 없음 | `204`; session·security context·CSRF token 폐기 | 미인증 `401`; CSRF 누락·불일치 `403` | `AUTH_REQUIRED`, `CSRF_INVALID` |
| `GET` | `/api/auth/me` | 필요 | 불필요 | 없음 | `200`, `{ "memberId": 1 }` | 미인증·만료 session `401` | `AUTH_REQUIRED` |

오류 body는 ARCH-006의 공통 구조를 따른다. 비필드 오류의 `fieldErrors`는 `[]`다. 로그인 입력 누락·형식 오류의 추천 배열 원소는 `{ "field": "email", "message": "..." }`처럼 `field`와 `message`만 가진다.

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "이메일 또는 비밀번호를 확인해 주세요.",
  "fieldErrors": []
}
```

로그인 자격 증명 실패는 존재하지 않는 email과 잘못된 비밀번호를 같은 상태·코드·메시지로 처리한다. 인증된 사용자가 권한이 없는 다른 `/api/**` 기능에 접근할 때는 `403 ACCESS_DENIED`를 사용한다. CSRF 검사는 인증보다 먼저 실패할 수 있으므로 CSRF가 없거나 만료된 상태 변경 요청은 인증 상태와 무관하게 `403 CSRF_INVALID`가 될 수 있다.

### 근거

- DATA-001과 DATA-002는 `members.email`을 로그인 식별 후보로 두고 있으며 ARCH-006은 `members`와 최소 `memberId` principal을 승인했다.
- `200`의 최소 회원 body는 Frontend가 로그인 성공과 현재 session의 주체를 같은 형태로 확인하게 하면서 email 같은 개인정보 노출을 늘리지 않는다.
- `POST` logout은 CSRF가 활성화된 Spring Security의 안전한 기본 방향과 맞는다. Spring Security도 login과 logout 요청의 CSRF 보호를 권고한다. [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- 자격 증명 오류를 하나로 합치면 회원 존재 여부 추측을 줄인다.

### 적용 범위와 영향

- Backend: JSON login 처리, session 인증 저장, logout, 현재 principal 조회, JSON failure handler의 계약 입력이 된다.
- Frontend: 로그인 폼 필드는 `email`, `password`이며 로그인 후 `memberId`를 인증 상태의 최소 식별자로 사용할 수 있다.
- QA: 성공·실패 status/body, redirect 부재, session id 비노출, generic credential 오류를 검증한다.

### 위험

- Frontend가 로그인 성공 body를 사용자 프로필 API로 오해하면 개인정보 필드 확장이 발생할 수 있다. 이 body는 인증 주체 확인용 최소 계약이다.
- CSRF 실패와 session 만료가 동시에 발생할 때 `403`이 먼저 보일 수 있으므로 Frontend는 `CSRF_INVALID`과 `AUTH_REQUIRED`를 구분해야 한다.
- rate limit, 계정 잠금, email 인증 상태가 없으므로 credential stuffing 방어는 별도 결정이 필요하다.

### Alternative

1. 로그인 성공과 현재 회원 조회를 모두 `204`로 반환: body가 더 작지만 Frontend가 인증 주체를 확인할 별도 호출·상태가 필요하다.
2. `username` 또는 email/username 복합 로그인: 회원 데이터와 검증·오류 분기가 늘어나 첫 MVP 최소 범위를 넘는다.
3. Spring Security 기본 form login URI와 redirect 사용: `/api/**` JSON 실패라는 Approved Input과 충돌한다.
4. `DELETE /api/auth/session` logout: 의미는 명확하지만 CSRF·Spring Security 기본 logout 연동과 팀의 `POST` API 관례를 추가 조정해야 한다.

## DR2. CSRF 전달과 session cookie 계약

### Recommended: CSRF 저장·전달

- 기대 CSRF token은 Spring Security 기본 `HttpSessionCsrfTokenRepository`로 session에 저장한다.
- Frontend 획득 endpoint는 `GET /api/auth/csrf`, 응답 필드는 `token`, 요청 header는 `X-CSRF-TOKEN`, request parameter 이름은 `_csrf`로 한다.
- CSRF 전용 cookie는 만들지 않는다. 따라서 CSRF cookie 이름과 JavaScript 접근은 해당 없음이며 token은 Frontend 메모리에만 둔다.
- `GET /api/auth/csrf` 응답은 캐시하지 않는다.
- 로그인 전 token을 한 번 획득해 login `POST`에 보낸다. 인증 성공은 기존 token을 폐기하므로 성공 직후 다시 획득한다.
- logout `POST`에는 현재 token을 보내고, 성공하면 Frontend 메모리 token을 폐기한다. 이후 다시 로그인할 때 새 token을 획득한다.
- CSRF 실패 후 비멱등 `POST`를 자동 재실행하지 않는다. token을 다시 획득하고 사용자의 명시적 재시도를 유도한다.

Spring Security의 기본 session repository와 MVC token 인자 노출을 사용하면 CSRF 전용 JavaScript-readable cookie와 SPA 전용 cookie request handler를 도입하지 않아도 된다. Spring Security는 인증 성공과 logout 성공 시 이전 token을 지우므로 재발급 절차가 필요하다고 명시한다. [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html), [Spring MVC integration](https://docs.spring.io/spring-security/reference/servlet/integrations/mvc.html)

### Recommended: session cookie

| 항목 | 추천값 | 근거·영향 | 위험 |
| --- | --- | --- | --- |
| 이름 | `JSESSIONID` | Servlet 기본 이름을 유지해 불필요한 커스텀 계약을 줄임 | 인프라가 이름을 가정하면 후속 변경 비용이 생김 |
| `HttpOnly` | `true` | Approved Input; JavaScript의 session id 접근 차단 | XSS 자체를 막지는 않음 |
| `SameSite` | `Lax` | 첫 구현의 same-origin 브라우저 흐름에 충분하고 일반 cross-site 전송을 줄임 | 완전한 cross-site FE/BE에는 동작하지 않음 |
| `Secure` | HTTPS 환경 `true`; 로컬 HTTP에서만 `false` | 전송 중 cookie 노출 방지와 로컬 실행 가능성 균형 | 배포 환경에서 누락하면 보안 저하 |
| `Domain` | 미설정(host-only) | session cookie를 API host로 제한 | 여러 host가 session을 공유할 수 없음 |
| `Path` | `/` | `/api/**`와 logout을 포함한 동일 애플리케이션 범위 지원 | 같은 host의 다른 앱과 경로를 공유하면 범위가 넓음 |
| 지속 시간 | persistent `Max-Age` 없음; server idle timeout `30m` | 브라우저 session과 서버 만료를 함께 사용 | 장시간 입력 중 만료 가능 |
| 로그인 시 | `changeSessionId`로 session fixation 방어 | Spring Security/Servlet의 기본 보호와 일치 | 커스텀 login이 전략 호출을 빠뜨리면 보호가 깨짐 |
| 로그아웃 시 | session·security context·CSRF token 무효화, cookie 만료 | 서버·브라우저 상태를 함께 종료 | 일부 응답 실패 시 FE 상태와 서버 상태가 다를 수 있음 |

Spring Boot는 `HttpOnly`, `SameSite`, `Secure`, `Domain`, `Path`, `Max-Age`와 session timeout을 설정할 수 있다. [Spring Boot application properties](https://docs.spring.io/spring-boot/appendix/application-properties/) Spring Security는 로그인 시 기본적으로 session id를 바꿔 fixation을 방어하고, logout 시 session과 security context 및 CSRF token을 정리한다. [Session management](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html), [Logout](https://docs.spring.io/spring-security/reference/servlet/authentication/logout.html)

### same-origin·cross-origin 기준

- 첫 Backend 구현의 Recommended 배포 계약은 Frontend가 같은 origin의 `/api/**`를 호출하는 구조다. 개발 proxy 또는 배포 reverse proxy를 사용할 수 있지만 그 구현은 AUTH-002 범위가 아니다.
- origin이 다르면 브라우저 요청은 credentials를 명시해야 하고 CORS allowlist, credential 허용, origin별 CSRF 노출을 함께 결정해야 한다. CORS 구현이 Explicitly Excluded이므로 첫 구현에서 별도 origin 조합을 지원한다고 간주하지 않는다.
- 서로 다른 site가 필수라면 `SameSite=None; Secure`, 명시적 CORS와 CSRF 신뢰 origin 검토가 선행돼야 하며 별도 사용자 결정 없이는 구현하지 않는다.

### Frontend 요청 절차

1. 로그인 화면 진입 또는 CSRF token 부재 시 credentials를 포함해 `GET /api/auth/csrf`를 호출하고 `token`을 메모리에 저장한다.
2. `POST /api/auth/login`에 JSON body와 `X-CSRF-TOKEN`을 보내며 cookie는 브라우저가 관리한다.
3. 성공 직후 `GET /api/auth/csrf`를 다시 호출해 인증 후 token으로 교체한다.
4. 이후 상태 변경 요청마다 현재 `X-CSRF-TOKEN`을 보낸다. 공개·보호 `GET`에는 header가 필수는 아니다.
5. `POST /api/auth/logout` 성공 시 인증 상태와 token을 메모리에서 지운다. 다음 로그인 전에 새 token을 받는다.
6. `401 AUTH_REQUIRED`이면 인증 상태를 지우고 로그인으로 이동한다. `403 CSRF_INVALID`이면 token을 갱신하되 비멱등 요청을 자동 재실행하지 않는다.

### Alternative

1. `CookieCsrfTokenRepository.withHttpOnlyFalse()`와 `XSRF-TOKEN`/`X-XSRF-TOKEN`: SPA 프레임워크 연동은 편하지만 JavaScript-readable cookie와 별도 request handler, 로그인·logout 후 cookie 재발급 처리가 필요하다. Spring Security가 제공하는 공식 대안이다. [Cookie CSRF repository](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
2. token을 response header로 전달: body DTO는 줄지만 browser header 노출과 CORS 계약이 더 필요하다.
3. CSRF 비활성화: session 인증과 상태 변경 API라는 Approved Input에 충돌하므로 선택 가능한 대안이 아니다.
4. `SameSite=None`을 기본값으로 사용: cross-site 가능성은 높지만 `Secure`와 credentialed CORS를 강제하고 첫 MVP의 공격면·운영 복잡도를 늘린다.

### 위험

- `/api/auth/csrf` 호출은 익명 session을 만들 수 있어 bot 트래픽에서 session 저장 비용이 생긴다.
- token을 메모리에만 두므로 새로고침 뒤 다시 획득해야 한다.
- same-origin 가정이 배포 구조와 다르면 cookie·CORS·CSRF가 함께 실패한다. 구현 전 배포 origin 확인이 중단 조건이다.
- `Secure=false`가 로컬 외 환경에 적용되거나 session cookie가 로그에 남으면 Approved Input을 위반한다.

## DR3. 최소 인증 데이터 계약

### Recommended

| 항목 | 추천 계약 | 검증 조건 |
| --- | --- | --- |
| 로그인 식별자 | `members.email` | 요청의 앞뒤 공백을 제거하고 소문자로 정규화한 뒤 조회·저장 |
| email 물리 후보 | `VARCHAR(254) NOT NULL`, `UNIQUE` | 중복·null 거부, 정규화 뒤 동일 email 중복 거부 |
| password hash 컬럼 | `password_hash VARCHAR(100) NOT NULL`, default 없음 | 평문·null 거부, BCrypt 결과 저장과 일치 검증 |
| API 노출 | login 요청 외 email 비노출, hash 항상 비노출 | 응답·예외·로그·테스트 보고서에서 hash와 평문 부재 확인 |
| principal | 최소 `memberId`; Entity 미보유 | 인증 컨텍스트 값으로 회원 조회, body/query memberId 무시 |
| V1 migration seed | schema만 작성하고 운영·공용 seed 회원을 넣지 않음 | migration에 email·hash·실제 credential 부재 확인 |
| 자동 테스트 회원 | test profile fixture가 테스트 실행 시 BCrypt hash를 생성하고 격리된 DB에 삽입 | 성공·실패 로그인, hash 비평문, unique email 검증 |
| 로컬 수동 검증 회원 | 별도 local-only bootstrap에서 환경 변수로 받은 가짜 credential을 BCrypt 처리 | 값 미커밋, 미로그, production profile 비활성 |

`password_hash` 길이 `100`은 60자 BCrypt 결과와 Spring Security의 `{bcrypt}` 접두 형식을 수용하면서 hash 형식 전환 시 즉시 schema 변경을 강제하지 않는 최소 여유다. Spring Security의 password storage 형식은 `{id}encodedPassword`이고 BCrypt는 adaptive one-way 함수다. [Spring Security password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html) MySQL의 `VARCHAR(M)`은 최대 문자 수를 지정한다. [MySQL string types](https://dev.mysql.com/doc/refman/8.4/en/string-type-syntax.html)

### 근거와 영향

- DATA-001/002의 `members.email` unique·not-null 후보를 로그인 식별자로 좁히고, 빠져 있던 credential 컬럼만 추가 제안한다.
- 회원 가입 API가 없는 첫 범위에서 production seed에 credential을 넣지 않고 test/local 경로를 분리하면 Secret 저장 위험을 줄인다.
- Backend는 email 정규화 위치, unique 충돌, BCrypt match와 generic failure를 테스트해야 한다.
- V1 migration/JPA 구현은 이 항목이 사용자 승인된 뒤에만 컬럼과 제약을 작성할 수 있다.

### Alternative

1. `CHAR(60) NOT NULL`: 순수 BCrypt 결과에는 정확하지만 `{bcrypt}` prefix나 후속 password encoder 교체 시 migration이 즉시 필요하다.
2. 별도 `member_credentials` 테이블: credential lifecycle 분리가 가능하지만 첫 회원·로그인만 있는 MVP에는 join과 모델이 늘어난다.
3. email 원문 저장 + case-insensitive collation만 의존: 구현은 단순하지만 DB collation 변경에 정규화 의미가 좌우된다.
4. V1 production migration에 고정 test member 삽입: 빠른 수동 확인은 가능하지만 credential과 환경별 데이터가 schema migration에 영구 결합되므로 추천하지 않는다.

### 위험

- 국제화 email, alias, 탈퇴 후 재가입, email 변경 정책은 정해지지 않았다. 첫 MVP는 정규화 가능한 일반 email만 가정한다.
- `VARCHAR(100)`은 특정 encoder를 DB가 검증하지 않으므로 애플리케이션 테스트가 필요하다.
- local bootstrap의 환경 변수 이름·수명·운영 차단이 구현 PR에서 명확하지 않으면 production test account 위험이 생긴다.
- BCrypt strength는 실행 환경에서 측정해야 하며 AUTH-002가 cost를 승인하지 않는다.

## Decision Required

사용자는 다음 세 묶음을 명시적으로 승인·수정·보류해야 한다.

1. DR1: email login, 네 API URI·method·status·body, 안정 오류 코드
2. DR2: session 저장 CSRF와 JSON endpoint/header 절차, CSRF cookie 미사용, session cookie 속성·timeout, same-origin 우선
3. DR3: email 정규화·제약, `password_hash VARCHAR(100) NOT NULL`, production seed 금지와 test/local fixture 분리

세 묶음 중 하나라도 승인되지 않으면 관련 Controller/DTO/SecurityConfig/migration/JPA 구현을 시작하지 않는다. 사용자가 일부만 승인하면 승인된 묶음만 후속 승인 입력 문서에 기록하고 나머지는 `Decision Required`로 유지한다.

## Deferred

- JWT, refresh token, OAuth2, social login
- 회원 가입, 비밀번호 재설정·변경, email 인증, 계정 잠금·rate limit
- remember-me, 동시 session 제한, device/session 목록과 강제 로그아웃
- 구독 API와 소유권 구현
- 국제화 email, email 변경·탈퇴·재가입 정책
- cross-site FE/BE 배포와 credentialed CORS 계약
- BCrypt strength 측정값과 encoder upgrade 정책

## Explicitly Excluded

- Spring Security, Controller, DTO, JPA Entity, Repository, Service, Flyway migration 구현
- datasource·session store·cookie·CORS 설정 변경
- Backend/Frontend/.github/build 파일과 신규 의존성 변경
- 실제 회원, 실제 email, 실제 password·hash·session id·CSRF token·Secret 기록
- JWT/OAuth2/구독/화면/API client/Docker 구현
- AUTH-001, API-001, DATA-001, DATA-002, ARCH-006 원본 상태 변경

## 구현 시 검증 조건

- login 전 CSRF 획득, 성공 후 token 교체, logout 후 폐기 흐름이 통합 테스트로 증명된다.
- session id가 로그인 시 바뀌고 logout·만료 후 보호 API가 `401` JSON을 반환한다.
- session cookie 속성이 환경별 추천값과 일치하며 body·URL·로그에 session id가 없다.
- login 성공·실패, logout, `/api/auth/me`, CSRF 누락·불일치의 status/body/code가 계약과 일치한다.
- email 존재 여부에 따른 오류 메시지 차이가 없고 평문 password/hash가 응답·로그에 없다.
- V1 migration은 unique email과 non-null hash를 검증하되 seed credential을 포함하지 않는다.
- test fixture는 production profile에서 생성되지 않고 테스트마다 격리된다.

## 관련 요구사항·설계 문서

- `docs/adr/ARCH-006-first-backend-implementation-approved-inputs.md`
- `docs/handoffs/ARCH-006/tl-to-be.md`
- `docs/adr/AUTH-001-login-return-and-auth-boundary.md`
- `docs/api/API-001-first-mvp-api-contract.md`
- `docs/data/DATA-001-first-mvp-data-model.md`
- `docs/data/DATA-002-first-mvp-logical-erd.md`
- `docs/product/PS-002-first-mvp-requirements.md`
- `docs/adr/ARCH-001-first-vertical-mvp-architecture.md`

## 사용자 승인

미승인. ARCH-006 DR1~DR3에 대한 AUTH-002 추천안을 사용자 또는 Tech Lead가 검토해야 한다.
