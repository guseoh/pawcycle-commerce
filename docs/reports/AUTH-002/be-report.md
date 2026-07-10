# AUTH-002 Backend Engineer 작업 보고서

## 작업 정보

- 작업 ID: `AUTH-002`
- 역할: Backend Engineer
- 기준 브랜치: `main`
- 작업 브랜치: `feat/be`
- 작업 유형: session 인증 최소 계약 Decision Proposal
- 결정 상태: Decision Proposal

## 작업 목적

ARCH-006에서 승인되지 않은 DR1 최소 인증 API, DR2 CSRF·cookie, DR3 최소 인증 데이터에 대해 사용자가 선택할 수 있는 추천안과 대안을 문서화한다. 실제 Backend 구현은 하지 않는다.

## 입력 문서

- PR #29 병합 결과와 ARCH-006 승인 입력·Backend 인수인계
- AUTH-001 인증·인가 경계
- API-001 공통 오류와 인증 실패 후보
- DATA-001·DATA-002의 `members` 후보
- PS-002 인증 요구사항과 인수 조건
- Spring Security session·CSRF·logout·password storage 공식 문서
- Spring Boot session cookie 속성 문서와 MySQL 문자열 타입 문서

## 승인 입력

- session, Spring Security, BCrypt, CSRF 활성화
- HttpOnly session cookie와 session id 비노출
- 최소 `memberId` principal, Entity principal 금지, client memberId 불신
- `/api/**` 401·403 JSON과 login failure 401 JSON
- `code`, `message`, `fieldErrors` 공통 오류 구조

신규 URI, field, code, cookie 속성, DB 타입과 fixture 방식은 승인 입력이 아니라 `Recommended`다.

## DR1~DR3 매핑

| ARCH-006 결정 | AUTH-002 결과 | 상태 |
| --- | --- | --- |
| DR1 최소 인증 API | email login, login/logout/me/csrf API와 status/body/error code 추천 | Decision Required |
| DR2 CSRF·cookie | session CSRF repository, JSON token endpoint/header, cookie 속성·갱신·FE 절차 추천 | Decision Required |
| DR3 최소 인증 데이터 | email 정규화·unique, `password_hash VARCHAR(100) NOT NULL`, fixture 분리 추천 | Decision Required |

## 제안 요약

- `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`, `GET /api/auth/csrf`
- `HttpSessionCsrfTokenRepository`, JSON `token`, `X-CSRF-TOKEN`, CSRF cookie 미사용
- login 전 token 획득, 성공 직후 재획득, logout 후 폐기
- `JSESSIONID`, HttpOnly, SameSite Lax, HTTPS Secure, host-only, Path `/`, no Max-Age, idle timeout 30분
- same-origin 우선, cross-origin은 CORS·배포 origin 결정 전 지원으로 간주하지 않음
- ASCII email login, local-part case 보존·domain 소문자화, case-sensitive unique·not-null email, non-null `password_hash VARCHAR(100)`
- production seed 금지, test fixture와 local-only bootstrap 분리

## 대안과 제외

- CSRF cookie repository, response header token, strict `CHAR(60)`, credential 분리 테이블을 대안으로 비교했다.
- redirect form login, CSRF 비활성화, production 고정 test member는 추천하지 않았다.
- JWT, OAuth2, 구독, CORS, 실제 코드·migration·설정·의존성 변경은 Deferred 또는 Explicitly Excluded로 유지했다.

## 변경 범위

- `docs/adr/AUTH-002-session-authentication-contract-proposal.md`
- `docs/reports/AUTH-002/be-report.md`
- `docs/handoffs/AUTH-002/be-to-tl.md`

README에는 기존 task artifact validator가 세 산출물을 직접 추적하므로 링크를 추가하지 않았다.

## 변경하지 않은 범위

- `backend/**`, `frontend/**`, `.github/**`
- build·dependency·datasource·Flyway·JPA·Security·API·Frontend 구현
- AUTH-001, API-001, DATA-001, DATA-002, ARCH-006 원본 문서
- Secret, credential, 실제 token·session 값

## 인수 조건 매핑

| 요구사항·조건 | 문서 반영 |
| --- | --- |
| ARCH-006 DR1 | 인증 API 계약 표, generic login failure, 401·403 JSON |
| ARCH-006 DR2 | token 저장·노출·이름·갱신·login/logout·FE 절차와 cookie 상세 |
| ARCH-006 DR3 | email·password hash·test/local member 계약 |
| REQ-AUTH-001 / AC-AUTH-001-01~10 | session 기반 인증 경계와 안전한 API 실패; return-to 구현은 Deferred |
| REQ-AUTH-002 / AC-AUTH-002-01~04 | principal 최소 memberId와 client memberId 불신; 구독 구현은 Deferred |

## API 영향

구현 영향만 제안했다. 네 인증 지원 API와 안정 오류 코드를 추가하면 Frontend와 QA가 동일 계약을 사용할 수 있다. 실제 API 파일과 Controller는 변경하지 않았다.

## DB 영향

`members.email`과 새 `password_hash`의 물리 후보 및 seed 금지를 제안했다. migration, Entity, 실제 row는 변경하지 않았다.

## 보안 영향

CSRF token lifecycle, session fixation 방어, cookie 속성, generic credential 오류와 민감값 비노출 검증을 구체화했다. 추천안은 사용자 승인 전 보안 설정 입력이 아니다.

## Frontend 영향

Frontend는 token 획득·재획득, credentials 포함, `X-CSRF-TOKEN`, `401`/`403` 분기와 비멱등 요청 비자동 재시도 절차가 필요하다. API client나 화면은 변경하지 않았다.

## 운영·성능 영향

- 익명 CSRF endpoint가 session을 만들 수 있어 session 저장량을 관찰해야 한다.
- HTTPS 환경의 `Secure=true`와 실제 same-origin 배포 구조를 구현 전에 확인해야 한다.
- BCrypt cost는 구현 환경에서 측정해야 하며 AUTH-002가 값은 결정하지 않는다.

## 실행한 검증

- GitHub 원격에서 PR #29 `merged=true`와 merge commit을 확인했다.
- 고정 경로와 clean working tree를 확인했다.
- stale `feat/be`가 병합 완료 작업임을 확인하고 backup branch를 만든 뒤 최신 `main`에서 새 `feat/be`를 준비했다.
- 공식 Spring Security, Spring Boot, MySQL 문서로 CSRF lifecycle, session fixation, logout cleanup, password storage와 cookie/문자열 설정 근거를 대조했다.
- `Write-Output 'AUTH-002' | py -3 scripts/validate-task-artifacts.py --from-stdin`: 통과
- `py -3 scripts/validate-task-artifacts.py --task-id AUTH-002`: 통과
- 변경 파일 allowlist: 허용된 문서 3개만 확인
- `Decision Proposal`·상태 용어·미결정 placeholder·Secret 의심 문자열 검사: 통과
- 로컬 문서 참조 10개 존재 검사: 통과
- `git diff --check`: 통과
- commit 제목 `docs(auth): 세션 인증 최소 계약 제안` convention 검사: 통과
- validator 단위 테스트 26개: Windows 기본 코드페이지에서는 stderr decode 6건이 실패했으나 `PYTHONUTF8=1` 재실행에서 전부 통과
- Frontend `npm ci`: 통과, 343 packages 설치, 기존 moderate 취약점 2건 보고
- Frontend `npm run lint`: 통과
- Frontend `npm run build`: 통과, Next.js 16.2.10 production build

## 실행하지 못한 검증과 이유

- Backend `gradlew test`와 `gradlew build`: 로컬에는 Java 17·21만 있고 요구 Java 25 toolchain과 자동 다운로드 저장소가 없어 dependency 계산 전에 중단됐다. 코드·테스트 실패가 아니며 원격 CI의 Java 25 환경에서 확인해야 한다.
- 실제 browser cookie·CSRF 통합 검증: 구현이 없으므로 수행할 수 없다.
- DB migration/JPA 검증: migration·Entity가 Explicitly Excluded다.
- 독립 보안 침투 테스트: 승인 전 계약 제안 단계이며 실행 가능한 인증 endpoint가 없다.

## QA 필요 여부

AUTH-002 자체의 독립 QA 문서는 불필요하다. 사용자-facing 동작이나 데이터 schema를 변경하지 않는 문서 작업이기 때문이다. 후속 인증 구현 PR은 session·CSRF·cookie·credential을 실제 변경하므로 QA 독립 검증 여부를 반드시 다시 판단한다.

## AI 리뷰 반영 여부

- Draft PR #30에서 CodeRabbit 수동 review를 실행했고 actionable thread 4건을 확인했다.
- email 정규화 경계와 충돌 거부, CSRF token 예외 문구, PR/CI 상태, npm audit 위험 추적 지적을 모두 반영했다.
- email 전체 소문자화 대신 RFC 5321에 맞춰 ASCII local-part case를 보존하고 domain만 소문자화하도록 추천안을 수정했다.
- 미반영 actionable thread는 없다.

## 적용 방법

Tech Lead와 사용자는 DR1~DR3를 각각 승인·수정·보류한다. 승인된 항목만 후속 승인 입력으로 기록한 뒤 Backend Engineer가 migration/Security/API 구현을 시작한다.

## 위험과 제한

- `Recommended`를 `Approved`로 오독하면 사용자 승인 없는 보안·DB 계약이 구현될 수 있다.
- same-origin 가정이 실제 배포와 다르면 cookie, CORS, CSRF 결정을 다시 열어야 한다.
- 로그인 API가 생겨도 가입·password reset·rate limit은 없다.
- CSRF/session 만료가 겹치면 Frontend failure 처리 순서가 복잡해질 수 있다.
- Frontend의 기존 `next@16.2.10`이 transitive `postcss@8.4.31`을 사용해 [GHSA-qx2v-qp2m-jg93](https://github.com/advisories/GHSA-qx2v-qp2m-jg93) moderate XSS advisory 2건 집계에 포함된다. `main`과 같은 lockfile이며 AUTH-002가 package나 user content CSS 처리를 바꾸지 않아 이 문서 PR의 차단 사유는 아니다. 담당은 Frontend Engineer와 Tech Lead이고 기존 추적 issue는 없으므로 별도 dependency maintenance task를 생성해야 한다.

## 남은 결정

- DR1~DR3 사용자 승인 여부
- 실제 Frontend와 Backend의 배포 origin
- BCrypt strength와 실행 환경 측정 기준
- local-only member bootstrap의 구체 실행 방식과 환경 변수 이름
- 로그인 성공 후 안전한 내부 GET 복귀 상태의 저장 위치

## 다음 작업

1. Tech Lead가 추천안과 대안을 검토해 사용자 승인을 요청한다.
2. 승인 결과를 별도 승인 입력 문서에 기록한다.
3. 승인 범위에서 V1 member credential migration·test fixture를 구현한다.
4. Spring Security session·CSRF·JSON failure 기반을 구현한다.
5. login/logout/me/csrf API와 통합 테스트를 구현한다.
6. Frontend dependency maintenance task에서 GHSA-qx2v-qp2m-jg93 영향과 안전한 upgrade 경로를 확인한다.

## Git 결과

- 작업 브랜치: `feat/be`
- 시작 기준: 최신 `origin/main`의 `40b6e00`
- 이전 local/remote `feat/be`는 각각 backup branch로 보존했다.
- reset, rebase, force push, history rewrite를 사용하지 않았다.
- 최초 commit: `d211019 docs(auth): 세션 인증 최소 계약 제안`
- `origin/feat/be` push와 upstream 설정을 완료했다.
- CodeRabbit review 반영은 후속 문서 commit으로 push한다.

## PR 결과

- Draft PR #30: `https://github.com/guseoh/pawcycle-commerce/pull/30`
- 제목: `docs(auth): 세션 인증 최소 계약 제안`
- head/base: `feat/be` → `main`, 상태 `OPEN`, Draft
- Repository Validation의 Commit and PR conventions와 Application validation이 모두 통과했다.
- CodeRabbit 수동 review가 완료됐고 actionable thread 4건을 모두 문서에 반영했다.
- 자동 병합하지 않는다.
