# AUTH-003 Tech Lead 작업 보고서

## 작업 정보

- 작업 ID: `AUTH-003`
- 역할: Tech Lead
- 기준 브랜치: `main`
- 작업 브랜치: `ops/tl`
- 작업일: 2026-07-10
- 작업 유형: 사용자 승인 입력 기록

## 작업 목적

AUTH-002의 DR1~DR3 추천안을 사용자가 승인한 최소 세션 인증 구현 입력으로 기록하고 Backend Engineer에게 구현 경계와 검증 조건을 전달한다.

## 입력 문서

- 사용자 AUTH-003 요청과 DR1~DR3 승인 입력
- `AGENTS.md`
- `.agents/skills/tech-lead/SKILL.md`
- `docs/roles/tech-lead.md`
- `docs/adr/ARCH-006-first-backend-implementation-approved-inputs.md`
- `docs/adr/AUTH-002-session-authentication-contract-proposal.md`
- `docs/handoffs/AUTH-002/be-to-tl.md`
- 사용자 승인과 충돌하지 않는 PS-002, AUTH-001, API-001, DATA-001, DATA-002, ARCH-001, ARCH-003, FOUNDATION-000

## 사용자 승인 입력

- DR1 최소 인증 API 계약: AUTH-002 추천안 전체 승인
- DR2 CSRF·session cookie 계약: AUTH-002 추천안 전체 승인
- DR3 최소 인증 데이터 계약: AUTH-002 추천안 전체 승인
- AUTH-002 추천안 외 수정 사항: 없음
- 첫 인증 구현 origin 전제: same-origin 또는 reverse proxy
- 승인 주체: 사용자(Product Owner 및 Tech Lead)

PR #30 병합은 제안 문서 반영 근거이고 승인 자체의 근거가 아니다. AUTH-003 요청에 포함된 사용자의 명시적 승인을 근거로 새 승인 문서를 작성했다.

## 변경 범위

- AUTH-003 `Approved` 승인 입력 문서 작성
- Tech Lead 작업 보고서 작성
- Backend Engineer 인수인계 작성
- README 주요 문서에 AUTH-003 링크 추가

## AUTH-002 Recommended에서 AUTH-003 Approved로 변경된 범위

| 범위 | AUTH-002 | AUTH-003 |
| --- | --- | --- |
| DR1 최소 인증 API | Recommended, Decision Required | Approved |
| DR2 CSRF·session cookie | Recommended, Decision Required | Approved |
| DR3 최소 인증 데이터 | Recommended, Decision Required | Approved |
| same-origin 우선 | Recommended | Approved 전제 |

승인 상태의 변경은 AUTH-003 문서에만 기록했다. AUTH-002 원본 상태와 문구는 변경하지 않았다.

## 변경하지 않은 범위

- `backend/**`, `frontend/**`, `.github/**`
- build, dependency, Spring Security, Controller, DTO, JPA, Flyway migration, datasource, cookie, CORS와 CI 설정
- Docker, Docker Compose, Secret과 실제 credential
- AUTH-001, AUTH-002, API-001, DATA-001, DATA-002, ARCH-001, ARCH-003, ARCH-006, FOUNDATION-000 원본 상태
- JWT, OAuth2, 회원 가입, 비밀번호 lifecycle, email 인증, 계정 잠금, rate limit, remember-me, 동시 session 제한, cross-site 배포, 국제화 email·IDN, 구독 API

## 요구사항과 인수 조건 매핑

| 요구사항·승인 입력 | AUTH-003 결과 | 후속 구현 인수 조건 |
| --- | --- | --- |
| `REQ-AUTH-001`, `AC-AUTH-001-01~10` | 공개·보호 경계, 로그인 후 안전한 흐름과 POST 자동 재실행 금지 유지 | `/api/**` 401 JSON, 공개 GET 허용, 비멱등 요청 자동 재실행 없음 |
| ARCH-006 session·Security 승인 | session, BCrypt, CSRF, HttpOnly와 최소 `memberId` principal 유지 | 로그인 성공·실패, session fixation, logout 정리, principal 검증 |
| DR1 사용자 승인 | 네 인증 API와 안정 오류 코드 Approved | method·URI·status·body·오류 코드 통합 테스트 |
| DR2 사용자 승인 | session CSRF, token lifecycle, cookie 속성과 same-origin 전제 Approved | CSRF 전후 흐름, cookie 환경 속성, 30분 timeout 검증 |
| DR3 사용자 승인 | email 정규화·물리 제약, `password_hash`, seed·fixture 경계 Approved | MySQL migration, unique 충돌, BCrypt, production 차단 검증 |

`REQ-AUTH-002`의 구독 소유권 동작은 요구사항 추적성만 유지하며 구독 API가 Deferred이므로 이번 승인 기록이나 첫 인증 구현 범위에 포함하지 않는다.

## 주요 결과

- AUTH-002의 `Recommended`와 AUTH-003의 `Approved`를 문서별로 분리했다.
- 로그인, 로그아웃, 현재 회원과 CSRF API의 최소 계약을 승인 입력으로 고정했다.
- CSRF repository·전달·생명주기와 session cookie 속성을 승인 입력으로 고정했다.
- email 정규화·물리 제약, password hash와 데이터 생성 경계를 승인 입력으로 고정했다.
- malformed email 거부 조건과 `VALIDATION_FAILED` 검증을 AUTH-002 승인 범위와 일치시켰다.
- device/session 관리와 email 변경·탈퇴·재가입 정책을 Deferred 범위와 일치시켰다.
- 실제 구현, Deferred와 Explicitly Excluded 범위를 분리했다.
- Backend 구현의 진입 조건, 테스트, QA와 중단 조건을 전달했다.

## 변경 파일

- `docs/adr/AUTH-003-session-authentication-approved-inputs.md`
- `docs/reports/AUTH-003/tl-report.md`
- `docs/handoffs/AUTH-003/tl-to-be.md`
- `README.md`

## 결정 상태

- AUTH-003 DR1: Approved
- AUTH-003 DR2: Approved
- AUTH-003 DR3: Approved
- AUTH-002 원본: Decision Proposal 유지
- Deferred와 Explicitly Excluded: 유지
- session 관리 Deferred: device/session 목록 조회, 다른 session 또는 device 강제 로그아웃
- email 생명주기 Deferred: 국제화 email, IDN, SMTPUTF8, email 변경, 회원 탈퇴 후 email 보존·재사용, 탈퇴 후 재가입 정책
- malformed email 검증: 제어 문자, trim 후 빈 값, 내부 공백, 여러 `@`, 빈 local-part, 빈 domain, 254자 초과, ASCII 범위 밖 문자와 정규화 unique 충돌을 `VALIDATION_FAILED` 또는 충돌 실패로 검증
- 추가 Product Decision 또는 Technical Decision: 없음

## API 영향

- 네 인증 endpoint의 method·URI·인증·CSRF·성공 응답과 오류 코드를 후속 구현 입력으로 확정했다.
- `/api/**`에서 HTML과 redirect를 반환하지 않고 공통 JSON 오류를 사용한다.
- AUTH-003은 Controller, DTO 또는 OpenAPI 파일을 변경하지 않았다.

## DB 영향

- 후속 구현 입력으로 `members.email`의 ASCII·`ascii_bin`·unique·not-null 계약과 `password_hash VARCHAR(100) NOT NULL`을 확정했다.
- production seed 금지와 test fixture·local bootstrap 경계를 확정했다.
- AUTH-003은 schema, migration, JPA와 datasource를 변경하지 않았다.

## Security 영향

- `HttpSessionCsrfTokenRepository`, `X-CSRF-TOKEN`, `_csrf`, token lifecycle, cookie 속성, session fixation 방어와 logout 정리를 확정했다.
- same-origin 또는 reverse proxy를 첫 구현 배포 경계로 확정했다.
- AUTH-003은 SecurityConfig와 실제 cookie·CORS 설정을 변경하지 않았다.

## Frontend 영향

- token 메모리 보관, 로그인 전·후 획득, logout 후 폐기, CSRF 실패 뒤 사용자 재시도 절차가 후속 구현 입력이 됐다.
- 별도 origin과 cross-site 배포는 제외했다.
- AUTH-003은 Frontend 파일을 변경하지 않았다.

## 실행한 검증

| 검증 | 결과 |
| --- | --- |
| 지정 저장소 경로 | 확인 |
| 원격 저장소 일치 | 확인 |
| `git status --short --branch` | 시작 시 clean working tree 확인 |
| GitHub CLI 인증 상태 | 확인 |
| PR #30 조회 | `MERGED`, merge commit `ef1e4e6` 확인 |
| 열린 `ops/tl` PR과 branch head 확인 | 열린 PR 없음, 로컬 head는 PR #28, 원격 head는 PR #29 병합 완료 작업 |
| 최신 branch 준비 | `main`을 `origin/main`으로 fast-forward하고 `4f6a2d8`에서 새 `ops/tl` 생성 |
| 승인 입력 대조 | AUTH-002 DR1~DR3와 사용자 승인 입력 일치, 추가 수정 없음 |
| 관련 문서 충돌 검색 | AUTH-001, API-001, DATA-001·002, ARCH-001·006과 충돌 없음 |
| `Write-Output 'AUTH-003' \| py -3 scripts\validate-task-artifacts.py --from-stdin` | `task artifacts validated for AUTH-003` |
| `py -3 scripts\validate-task-artifacts.py --task-id AUTH-003` | `task artifacts validated for AUTH-003` |
| 변경 경로 allowlist | 승인 문서, 보고서, 인수인계, README만 변경 |
| 승인 상태·필수 계약 검색 | Approved/Recommended/Decision Required/Deferred/Explicitly Excluded 구분과 필수 문구 확인 |
| endpoint·Deferred 검사 | 네 endpoint와 AUTH-002 Deferred 범위 확인 |
| `git diff --check` | 통과 |
| `git diff --cached --check` | 통과 |
| staged diff Secret 의심 패턴 검사 | 일치 없음 |
| 커밋 메시지 검증 | Git for Windows Bash로 통과 |
| PR 본문 로컬·원격 encoding validator | 두 경로 모두 통과 |
| PR #31 원격 metadata | 제목·본문·`ops/tl` → `main`·Draft·head SHA 정상 |

## 실행하지 못한 검증과 이유

- Backend test/build, MySQL/Flyway, Spring Security와 Frontend lint/build는 실행하지 않았다. 이번 변경은 문서 승인 기록뿐이며 실제 코드, schema, dependency와 설정을 변경하지 않는다.
- 실제 API·cookie·CSRF·session·email 정규화 동작은 구현 산출물이 없어 검증할 수 없다. 후속 Backend·Frontend 구현 및 QA 작업에서 검증해야 한다.
- Codex Review와 CodeRabbit 리뷰는 작성 시점에 실행 결과가 없다. PR 생성 후 AI 리뷰가 제공되면 사용자가 보안·인증·credential 항목을 직접 다시 판단해야 한다.

## QA 필요 여부

- AUTH-003 문서 작업 자체는 독립 QA 문서가 필요하지 않다.
- 실제 인증 구현은 독립 QA 검증이 필요하다.

## QA 문서 경로 또는 생략 사유

- AUTH-003 QA 문서를 생략했다.
- 사유: 승인 상태와 구현 입력을 기록하는 문서만 변경하며 사용자-facing 동작, API 실행, DB schema, 인증·인가, cookie 또는 credential 처리를 변경하지 않는다.
- 후속 구현이 login/logout/session/CSRF, cookie, credential migration, test/local 회원 또는 Frontend 오류 처리를 포함하면 QA Engineer가 별도 테스트 계획과 재검증 결과를 작성해야 한다.

## AI 리뷰 반영 여부

- AUTH-002 PR #30의 최종 문서와 인수인계를 입력으로 사용했다.
- PR #31의 Codex review 3건을 반영했다: malformed email 거부 조건, session 관리 Deferred, email 생명주기 Deferred 복원.
- PR #31의 CodeRabbit review 1건을 반영했다: 커밋된 보고서의 개인 로컬 절대 경로와 인증 계정 정보 제거.
- AUTH-002에 없는 새로운 추천이나 미승인 정책을 추가하지 않았다.

## AI 리뷰 미반영 항목과 이유

- 미반영 항목 없음.

## 적용 방법

- 후속 Backend 작업은 ARCH-006과 AUTH-003을 함께 Approved 입력으로 사용한다.
- AUTH-002는 선택 근거와 대안을 설명하는 Decision Proposal로만 참조한다.
- Backend Engineer는 `feat/be`에서 구현하고 실제 인증·credential 변경을 QA와 분리 검증한다.

## 위험과 제한

- 문서 승인만으로 실제 Spring Security, cookie, migration과 Frontend 동작이 검증된 것은 아니다.
- same-origin 전제가 바뀌면 CORS, SameSite, Secure와 CSRF 계약을 별도 승인해야 한다.
- 구현이 DB·Security·API·Frontend·CI를 한 PR에 과도하게 결합하면 검토와 실패 원인 분리가 어려워진다.

## 남은 위험

- 익명 CSRF session 증가와 저장 비용
- session 만료와 CSRF 실패의 오류 우선순위
- local bootstrap의 production profile 차단
- BCrypt strength의 측정 근거
- 민감정보 비노출과 generic credential failure의 실제 검증

## 다음 작업

1. Backend Engineer가 AUTH-003 인수인계를 검토하고 구현 범위를 분할한다.
2. 필요 시 Platform/SRE가 CI MySQL service를 별도 작업으로 준비한다.
3. Backend가 DB·Flyway·JPA·Security 기반과 세션 인증을 승인 범위 안에서 구현한다.
4. Frontend가 승인된 CSRF와 인증 오류 흐름을 별도 작업으로 구현한다.
5. QA가 인증·인가·cookie·CSRF·credential을 독립 검증한다.

## Git 결과

- 기준 commit: `4f6a2d8 docs(obsidian): PR #30 기록 추가`
- 최신 `origin/main`에서 새 `ops/tl` 생성
- 기존 로컬 역할 branch head는 `backup/local-ops-tl-before-AUTH-003`으로 보존
- PR #29 병합 완료 원격 `ops/tl` 삭제 후 새 역할 branch push
- reset, rebase, force push와 history rewrite 사용 없음
- 승인 입력 commit: `35c3fdc7f7572b8f92e063147c4606e3d71d32dd`
- commit 제목: `docs(auth): 세션 인증 계약 승인 입력 정리`
- `origin/ops/tl` push와 upstream 설정 성공
- 이 보고서의 Git·PR 결과 갱신은 후속 `docs(report)` commit으로 추가

## PR 결과

- PR: #31 `docs(auth): 세션 인증 계약 승인 입력 정리`
- URL: `https://github.com/guseoh/pawcycle-commerce/pull/31`
- base/head: `main` ← `ops/tl`
- 상태: OPEN, Draft
- 원격 제목·본문·head/base·Draft와 head SHA 검증 완료
- PR 본문 로컬·원격 UTF-8 검증 통과
- 자동 병합하지 않음
