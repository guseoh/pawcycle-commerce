# AUTH-003 Tech Lead → Backend Engineer 인수인계

## 전달 목적

사용자가 승인한 AUTH-002 DR1~DR3를 실제 세션 인증 구현의 `Approved` 입력으로 Backend Engineer에게 전달한다.

## 대상 역할

- 수신: Backend Engineer
- 후속 협업: Frontend Engineer, QA Engineer, 필요 시 Platform/SRE

## 입력 문서

- `AGENTS.md`
- `backend/AGENTS.md`
- `docs/roles/backend-engineer.md`
- `.agents/skills/backend-engineer/SKILL.md`
- `docs/adr/ARCH-006-first-backend-implementation-approved-inputs.md`
- `docs/adr/AUTH-003-session-authentication-approved-inputs.md`
- `docs/adr/AUTH-002-session-authentication-contract-proposal.md`
- `docs/adr/AUTH-001-login-return-and-auth-boundary.md`
- `docs/api/API-001-first-mvp-api-contract.md`
- `docs/data/DATA-001-first-mvp-data-model.md`
- `docs/data/DATA-002-first-mvp-logical-erd.md`
- `docs/product/PS-002-first-mvp-requirements.md`
- `docs/qa/README.md`

AUTH-003과 ARCH-006을 Approved 구현 입력으로 사용한다. AUTH-002는 추천 근거와 대안을 설명하는 `Decision Proposal` 상태로만 참조한다.

## 완료된 작업

- 사용자가 AUTH-002 DR1~DR3를 수정 없이 모두 승인했다.
- DR1 최소 인증 API, DR2 CSRF·session cookie, DR3 최소 인증 데이터 계약을 AUTH-003에서 `Approved`로 기록했다.
- same-origin 또는 reverse proxy 배포 전제를 Approved로 기록했다.
- Deferred, Explicitly Excluded, 검증, QA와 구현 중단 조건을 정리했다.

## 사용 가능한 결과

- 실제 인증 구현에 사용할 method·URI·request·response·error code
- CSRF repository·전달 규칙·token 생명주기
- session cookie 속성과 session 보안 규칙
- email 정규화·unique와 password hash 물리 계약
- production seed 금지, test fixture와 local bootstrap 경계
- 구현 테스트, 독립 QA 필요 조건과 중단 조건

## 관련 파일

- `docs/adr/AUTH-003-session-authentication-approved-inputs.md`
- `docs/reports/AUTH-003/tl-report.md`
- `docs/handoffs/AUTH-003/tl-to-be.md`

## 실제 구현에 사용할 Approved 입력

### DR1 API method·URI·request·response

| Method | URI | 인증 | CSRF | 요청 | 성공 응답 |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/auth/csrf` | 불필요 | 불필요 | 없음 | `200 OK`, `{ "token": "..." }`, `Cache-Control: no-store` |
| `POST` | `/api/auth/login` | 불필요 | 필요 | `{ "email": "...", "password": "..." }` | `200 OK`, `{ "memberId": number }` |
| `POST` | `/api/auth/logout` | 필요 | 필요 | 없음 | `204 No Content` |
| `GET` | `/api/auth/me` | 필요 | 불필요 | 없음 | `200 OK`, `{ "memberId": number }` |

### 오류 코드와 정책

- `INVALID_CREDENTIALS`: 존재하지 않는 email과 잘못된 password를 같은 `401` 상태·코드·메시지로 처리
- `AUTH_REQUIRED`: 보호 API 미인증·session 만료 `401`
- `ACCESS_DENIED`: 인증된 사용자의 권한 부족 `403`
- `CSRF_INVALID`: CSRF token 누락·불일치 `403`
- `VALIDATION_FAILED`: 요청 형식·필드 검증 실패 `400`
- `INTERNAL_ERROR`: 내부 오류 `500`
- `/api/**`는 HTML과 redirect를 반환하지 않음
- 공통 오류 구조는 `code`, `message`, `fieldErrors`; field error가 없으면 빈 배열
- session id, password와 password hash를 응답·URL·로그에 노출하지 않음

## CSRF repository와 token lifecycle

### repository·전달

- `HttpSessionCsrfTokenRepository`
- 기대 token은 HTTP session에 저장
- 획득: `GET /api/auth/csrf` JSON body의 `token`
- 요청 header: `X-CSRF-TOKEN`
- request parameter: `_csrf`
- CSRF 전용 cookie 없음
- Frontend 메모리에 token 보관

### lifecycle

1. 로그인 전에 token 획득
2. 로그인 `POST`에 현재 token 전달
3. 로그인 성공 직후 token 재획득
4. logout `POST`에 현재 token 전달
5. logout 성공 후 Frontend token 폐기
6. CSRF 실패 시 비멱등 요청 자동 재실행 금지
7. token 재획득 후 사용자 명시적 재시도

## session cookie 속성

- 이름: `JSESSIONID`
- `HttpOnly=true`
- `SameSite=Lax`
- `Secure=true`: HTTPS 환경
- `Secure=false`: 로컬 HTTP 개발 환경에서만 허용
- `Domain`: 미설정, host-only
- `Path=/`
- persistent `Max-Age`: 없음
- server idle timeout: 30분

로그인 성공 시 session id를 변경한다. 로그아웃 시 session 무효화, SecurityContext 정리와 CSRF token 폐기를 수행한다. session id는 body, URL과 로그에 노출하지 않는다.

## same-origin 또는 reverse proxy 전제

- 첫 구현은 Frontend가 같은 origin의 `/api/**`를 호출하는 구조다.
- 개발 proxy 또는 배포 reverse proxy를 사용할 수 있지만 관련 인프라 구현은 Backend 범위가 아니다.
- 별도 origin, cross-site cookie와 credentialed CORS는 지원하지 않는다.
- 실제 배포가 전제와 다르면 CORS, SameSite, Secure와 CSRF 신뢰 경계를 별도 사용자 결정으로 올리고 구현을 중단한다.

## email 정규화와 unique 계약

- 로그인 식별자: `members.email`
- ASCII email만 허용
- 앞뒤 ASCII space와 tab 제거
- local-part case 보존
- domain만 locale-independent ASCII 소문자화
- Unicode local-part, IDN과 SMTPUTF8은 Deferred
- provider별 점 제거, `+tag` 제거, alias 통합과 provider/domain allowlist 금지
- 물리 계약: `VARCHAR(254) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE`
- 정규화 충돌 시 작업 실패; 자동 병합·덮어쓰기 금지
- migration, test fixture와 local bootstrap에 같은 규칙 적용

## password_hash 계약

- 컬럼명: `password_hash`
- 타입: `VARCHAR(100) NOT NULL`
- default 없음
- `BCryptPasswordEncoder` 사용
- 평문 저장·직접 비교·로그 출력 금지
- hash 로그 출력 금지

## 데이터 생성 경계

### production seed 금지

- V1 migration에 회원 credential row를 넣지 않는다.
- production 공용 seed 회원을 만들지 않는다.
- 실제 credential을 repository, 문서, fixture 기본값과 로그에 기록하지 않는다.

### test fixture

- test profile에서만 생성한다.
- 테스트 실행 시 BCrypt hash를 생성한다.
- 격리된 DB에 삽입한다.
- email 정규화와 unique 충돌 규칙을 동일하게 적용한다.

### local bootstrap

- local-only 경로로 분리한다.
- 실제 credential 값은 환경 변수로 전달한다.
- production profile에서 실행되지 않게 차단하고 테스트로 증명한다.
- 기본 credential, fallback credential 또는 production 공용 회원을 만들지 않는다.

## 구현하면 안 되는 Deferred 항목

- JWT
- OAuth2
- 회원 가입
- 비밀번호 재설정·변경
- email 인증
- 계정 잠금
- rate limit
- remember-me
- 동시 session 제한
- cross-site Frontend·Backend 배포
- 국제화 email과 IDN
- 구독 API

위 항목을 위한 dependency, package, interface, endpoint, DTO, migration, 설정 또는 미래 추상화를 미리 만들지 않는다.

## 미결정 사항 또는 승인 필요 항목

- AUTH-003의 DR1~DR3와 same-origin 또는 reverse proxy 전제에는 남은 승인 필요 항목이 없다.
- BCrypt strength는 AUTH-003에서 특정 값으로 승인되지 않았다. 구현 환경에서 측정하고 승인 범위를 바꾸지 않는 검증 가능한 설정 근거를 보고해야 한다.
- CI MySQL service 변경이 필요하면 Backend가 임의로 `.github/**`를 변경하지 않고 Platform/SRE 작업과 사용자 승인을 요청한다.
- 실제 배포가 별도 origin 또는 cross-site 구조라면 CORS, SameSite, Secure와 CSRF 신뢰 경계를 새로운 Technical Decision으로 요청한다.

## Explicitly Excluded

- 실제 password, password hash, session id, CSRF token, 실제 email 또는 Secret의 저장소·문서·로그 기록
- production seed credential
- CSRF 전체 비활성화
- session id body·URL·로그 노출
- 평문 password 저장·직접 비교
- JPA Entity principal과 client `memberId` 신뢰
- cross-site cookie·CORS 구현
- JWT·OAuth2·구독 범위 구현
- Backend 작업에서 `.github/**`, Frontend, Docker 또는 승인 목록 밖 dependency 변경

## 테스트와 검증 조건

### API·Security

- login 전 CSRF 획득과 token 필수 검증
- login 성공 시 session id 변경과 token 재획득
- 존재하지 않는 email과 잘못된 password의 동일한 `401 INVALID_CREDENTIALS`
- `/api/auth/me`의 인증 `200`과 미인증·만료 `401 AUTH_REQUIRED`
- logout의 CSRF 필수, `204`, session·SecurityContext·token 폐기
- `ACCESS_DENIED`, `CSRF_INVALID`, `VALIDATION_FAILED`, `INTERNAL_ERROR`의 status·body·code
- `/api/**` HTML·redirect 부재
- cookie 속성과 30분 idle timeout의 환경별 검증

### DB·credential

- ASCII email 허용·거부, trim, local-part case 보존, domain 소문자화
- unique·not-null·254자·정규화 충돌 실패
- `password_hash` non-null, BCrypt 저장·match와 평문 부재
- fresh MySQL migration과 JPA mapping 정합성
- V1 seed credential 부재
- test fixture 격리와 production profile 미실행
- local bootstrap의 production 차단과 credential 미기록

### 민감정보·회귀

- 응답, URL, 예외, 애플리케이션·테스트 로그에서 password, hash와 session id 부재
- CSRF token은 승인 endpoint 성공 JSON 외 위치에서 부재
- Java 25 Backend test/build와 MySQL·Flyway·Security 통합 검증
- 기존 공개 상품 API와 공통 오류 계약 회귀 검증
- 작업 산출물 validator, `git diff --check`, 금지 경로와 Secret 검색

## 다음 역할의 검증 포인트

- DR1의 네 endpoint가 승인된 method·URI·인증·CSRF·status·body·code를 정확히 구현하는가?
- DR2의 session CSRF repository, token 재획득·폐기와 비멱등 요청 자동 재실행 금지가 통합 테스트로 보호되는가?
- session cookie 속성과 session fixation·logout 정리가 로컬 HTTP와 HTTPS 환경별로 검증되는가?
- DR3의 email 정규화와 MySQL `ascii_bin` unique 계약이 같은 결과를 내는가?
- `password_hash`와 production seed 금지, test fixture·local bootstrap 격리가 schema·profile·테스트로 증명되는가?
- password, hash, session id와 승인 범위 밖 CSRF token이 응답·URL·예외·로그에 노출되지 않는가?
- Deferred·Explicitly Excluded 항목과 승인 목록 밖 dependency·설정·미래 추상화가 diff에 없는가?
- 실제 인증 동작에 대한 독립 QA 계획과 결과가 준비되는가?

## QA 필요 조건

실제 구현이 다음 중 하나라도 포함하면 독립 QA 검증이 필요하다.

- login/logout/session/CSRF 동작
- cookie 속성 또는 배포 origin 경계
- `members` credential migration
- test fixture 또는 local bootstrap
- 401/403 JSON과 Frontend 인증 상태·재시도 처리

QA는 정상·실패·권한·CSRF·cookie·민감정보·production 차단과 회귀 기준을 별도 테스트 계획으로 작성한다. AUTH-003 문서 작업만을 위한 QA 문서는 생략했다.

## AI 리뷰에서 남은 확인 항목

- AUTH-003 작성 시 새 AI 리뷰 지적은 없다.
- 구현 PR에서는 보안, 인증·인가, credential, 개인정보와 테스트 누락을 CodeRabbit/Codex Review 결과와 함께 사람이 다시 판단한다.
- Deferred 기능이나 미래 추상화 제안은 AUTH-003 범위와 충돌하므로 반영하지 않는다.

## 남은 위험

- 익명 CSRF session 증가와 session store 비용
- session 만료와 CSRF 실패가 겹칠 때 오류 우선순위
- same-origin 전제와 실제 배포 경계 불일치
- local bootstrap production 차단 실패
- BCrypt strength 미측정
- credential과 session 정보의 로그 노출 회귀

## 다음 권장 작업

1. 최신 `main`에서 새 `feat/be`를 준비하고 AUTH-003 작업 ID와 분할 범위를 확정한다.
2. DB·Flyway·JPA·Security 기반과 인증 API를 리뷰 가능한 단위로 나눈다.
3. CI MySQL service가 필요하면 Platform/SRE 인수인계를 먼저 작성한다.
4. 구현과 동시에 migration·Security·통합 테스트를 작성한다.
5. Frontend CSRF·인증 상태 처리는 별도 Frontend 작업으로 전달한다.
6. QA가 실제 인증 동작을 독립 검증한다.

## 완료 조건

- ARCH-006과 AUTH-003 Approved 범위만 구현
- DR1~DR3 method·URI·security·data 계약 충족
- Deferred·Explicitly Excluded 구현 없음
- Java 25, MySQL, Flyway, Security와 API 검증 통과
- 민감정보 비노출과 production seed·bootstrap 차단 증명
- QA 필요 여부와 문서 경로 기록
- Backend 보고서와 필요한 역할 인수인계 작성

## 구현 전 중단 조건

- 현재 배포 origin이 same-origin 또는 reverse proxy가 아님
- AUTH-003 또는 ARCH-006과 다른 API·cookie·CSRF·email·credential 계약이 필요함
- JWT, OAuth2, 회원 가입, 구독 또는 cross-site 범위를 포함해야 함
- 실제 password, hash, session id, CSRF token, 실제 email 또는 Secret 노출 의심
- production seed credential이나 기본 local credential이 필요함
- Backend 역할 밖 `.github/**`, Frontend, Docker 변경이 필요하지만 별도 작업 승인이 없음
- 승인 목록 밖 dependency 또는 미래 추상화가 필요함
- Java 25 test/build, MySQL/Flyway/Security와 필수 보안 검증 실패
- 다른 활성 `feat/be` 작업이나 mixed working tree 존재
- reset, rebase, force push 또는 history rewrite 필요
