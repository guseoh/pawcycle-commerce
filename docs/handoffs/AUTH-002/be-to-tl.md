# AUTH-002 Backend Engineer → Tech Lead 인수인계

## 전달 목적

ARCH-006 DR1~DR3를 해결하기 위한 session 인증 최소 계약 추천안과 대안을 Tech Lead에게 전달한다. 이 문서의 추천안은 아직 사용자 승인 입력이 아니다.

## 대상 역할

- 수신: Tech Lead
- 후속 협업: 사용자/Product Owner, Backend Engineer, Frontend Engineer, QA

## 입력 문서

- `docs/adr/ARCH-006-first-backend-implementation-approved-inputs.md`
- `docs/handoffs/ARCH-006/tl-to-be.md`
- `docs/adr/AUTH-001-login-return-and-auth-boundary.md`
- `docs/api/API-001-first-mvp-api-contract.md`
- `docs/data/DATA-001-first-mvp-data-model.md`
- `docs/data/DATA-002-first-mvp-logical-erd.md`
- `docs/product/PS-002-first-mvp-requirements.md`

## 사용 가능한 결과

- `docs/adr/AUTH-002-session-authentication-contract-proposal.md`: DR1~DR3의 추천안·대안·영향·위험
- `docs/reports/AUTH-002/be-report.md`: 입력, 추적성, 검증, 미구현 범위와 Git/PR 결과
- 본 문서: 승인 요청과 후속 구현 중단점

## Approved Input과 Recommended 구분

### Approved Input

- session + Spring Security + BCrypt + CSRF 활성화
- HttpOnly session cookie와 session id 비노출
- 최소 `memberId` principal과 client memberId 불신
- `/api/**` 401·403 JSON, login failure 401 JSON
- `code`, `message`, `fieldErrors` 공통 오류 구조

### Recommended — 사용자 승인 필요

- email login과 login/logout/me/csrf URI·method·status·body·code
- session CSRF repository, JSON token endpoint, `X-CSRF-TOKEN`, token lifecycle와 FE 절차
- CSRF cookie 미사용
- `JSESSIONID`, SameSite Lax, 환경별 Secure, host-only, Path `/`, no Max-Age, idle timeout 30분
- same-origin 우선과 cross-origin 미지원 경계
- ASCII email의 local-part case 보존·domain 소문자화·case-sensitive unique, `password_hash VARCHAR(100) NOT NULL`
- production seed 금지와 test/local fixture 분리

`Recommended`를 ARCH-006의 `Approved Input`으로 승격하지 말고 사용자의 명시 결정 이후 별도 승인 기록으로 남겨야 한다.

## 승인 요청 항목

| 묶음 | 추천안 | 승인 전 중단점 |
| --- | --- | --- |
| DR1 | email login, `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`, `GET /api/auth/csrf`, 계약된 status/body/code | Controller·DTO·failure handler 작성 전 |
| DR2 | session CSRF + JSON token, CSRF cookie 없음, cookie 속성·30분 timeout·same-origin | SecurityConfig·cookie·CSRF·FE client 설정 전 |
| DR3 | ASCII email의 local-part case 보존·domain 소문자화·충돌 거부, case-sensitive unique/not-null, `password_hash VARCHAR(100) NOT NULL`, seed 금지·fixture 분리 | V1 migration·Entity·test data 작성 전 |

사용자는 세 묶음을 각각 승인, 수정 또는 보류해야 한다. 일부만 승인하면 나머지는 `Decision Required`로 유지한다.

## 영향 요약

### API

- Frontend와 QA가 공유할 최소 login/logout/current/CSRF 계약이 생긴다.
- generic `INVALID_CREDENTIALS`, `AUTH_REQUIRED`, `ACCESS_DENIED`, `CSRF_INVALID` 분기가 생긴다.
- login 성공과 me는 동일한 최소 `{memberId}` 형태다.

### DB

- 기존 `members.email` 후보가 login identifier로 좁혀진다.
- `password_hash` non-null 컬럼이 V1 migration 입력이 된다.
- production migration에는 credential row를 넣지 않는다.

### 보안

- login/logout도 CSRF 보호 대상이다.
- token은 session에 저장하고 JSON으로 전달하며 JavaScript-readable CSRF cookie는 없다.
- 로그인 시 session id 변경, logout 시 session/security context/token 폐기가 필요하다.
- session cookie는 HttpOnly·SameSite Lax·HTTPS Secure·host-only·Path `/`·비영속으로 제한한다.

### Frontend

- login 전과 login 성공 후 CSRF token 획득, logout 후 폐기가 필요하다.
- `401`은 인증 상태 제거, `403 CSRF_INVALID`은 token 갱신 후 사용자 재시도로 처리한다.
- 상태 변경 요청을 자동 재실행하면 안 된다.

## 구현 전 필요한 확인

1. 실제 배포가 same-origin 또는 proxy 기반인지 확인한다.
2. DR1~DR3의 사용자 승인 기록을 확인한다.
3. Backend 역할 범위를 넘는 CI MySQL service 변경이 필요하면 Platform/SRE 작업을 분리한다.
4. local-only member bootstrap이 production profile에서 절대 실행되지 않는 방식을 정한다.
5. BCrypt cost는 CI와 개발 환경에서 측정할 검증 기준을 정한다.

## 권장 구현 순서

1. 승인된 DR3만으로 V1 `members` credential schema와 test fixture를 구현한다.
2. session persistence, BCrypt, JSON 401/403, session fixation과 cookie 속성을 구현한다.
3. `/api/auth/csrf`와 token lifecycle 통합 테스트를 먼저 통과시킨다.
4. login/logout/me를 구현하고 성공·실패·만료 통합 테스트를 추가한다.
5. 필요하면 공개 상품 API 작업을 별도 PR로 진행한다.

DB·Security·인증 API·상품 API가 한 PR에서 리뷰 불가능해지면 PR을 분할한다.

## 요구사항과 인수 조건

- `REQ-AUTH-001`, `AC-AUTH-001-01~10`: 공개·보호 경계와 로그인 흐름을 보존한다.
- `REQ-AUTH-002`, `AC-AUTH-002-01~04`: 인증 principal의 `memberId`로 본인 소유권을 판단한다. 구독 자체는 Deferred다.
- ARCH-006: session, CSRF, HttpOnly, BCrypt, 401/403 JSON과 공통 오류 구조를 변경하지 않는다.
- AUTH-002: 사용자 승인된 DR만 실제 구현 입력으로 사용한다.

## 다음 역할의 검증 포인트

- login 전 token 발급과 login `POST`의 token 필수 검증
- login 성공 시 session id 변경과 token 재발급
- 잘못된 email과 password에 동일한 `401 INVALID_CREDENTIALS`
- `/api/auth/me`의 인증/만료 `200`/`401` JSON
- logout의 CSRF 필수, `204`, session/context/token 폐기
- `AUTH_REQUIRED`, `ACCESS_DENIED`, `CSRF_INVALID`의 status/body
- cookie의 HttpOnly, SameSite, Secure, Domain, Path, persistence와 timeout
- email 정규화·unique·not-null, BCrypt hash·non-null, 평문 부재
- production migration의 seed credential 부재와 production profile의 test fixture 미생성
- password/hash/session id는 모든 응답·로그·URL에서 부재, CSRF token은 `/api/auth/csrf` 성공 JSON body에서만 허용하고 다른 응답·로그·URL에서는 부재

## QA 독립 검증 필요 여부 판단

AUTH-002 문서 자체는 독립 QA가 필요하지 않다. 후속 구현은 인증·인가, cookie, 개인정보와 credential 저장을 바꾸므로 다음 중 하나라도 포함하면 QA가 독립 검증 계획을 작성해야 한다.

- 실제 login/logout/session/CSRF 동작
- cookie 속성 또는 cross-origin 배포
- `members` credential migration과 test/local member 생성
- 401/403 JSON과 Frontend redirect·재시도 처리

## Deferred·Explicitly Excluded

구현하면 안 되는 항목:

- JWT, OAuth2, refresh token, social login, remember-me
- 회원 가입, 비밀번호 재설정, email 인증, account lock/rate limit
- 구독 API와 소유권 기능
- cross-site credentialed CORS
- 실제 Secret·credential·고정 production test member
- AUTH-002 승인 전에 Controller/DTO/SecurityConfig/migration/JPA/Frontend 구현

## 중단 조건

- DR1~DR3 중 구현 대상의 사용자 승인 기록이 없다.
- Frontend/Backend origin이 same-origin 추천과 다르지만 CORS·cookie 결정이 없다.
- 기존 AUTH-001/API-001/DATA 문서와 충돌하는 구현이 필요하다.
- session id, password/hash, 실제 email, CSRF token 또는 Secret 노출이 의심된다.
- Backend 역할 밖 CI/인프라 변경이나 신규 dependency가 필요하지만 별도 승인이 없다.
- 필수 보안·DB·artifact 검증이 실패한다.
- reset, rebase, force push 또는 history rewrite가 필요하다.

## 남은 위험

- 익명 CSRF session 증가와 session store 용량
- session timeout 중 logout/POST의 CSRF failure 우선순위
- 실제 배포 origin과 cookie scope 불일치
- 가입 API 없는 환경의 안전한 local member provisioning
- 국제화 email, password lifecycle, rate limiting 미결정
- BCrypt cost 미측정

## 다음 권장 작업

Tech Lead가 `docs/adr/AUTH-002-session-authentication-contract-proposal.md`의 DR1~DR3를 사용자에게 항목별로 제시하고 명시 선택을 기록한다. 승인 전에는 실제 인증 구현을 시작하지 않는다.
