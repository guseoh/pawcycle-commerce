# AUTH-001 Tech Lead 작업 보고서

## 작업 정보

- 작업 ID: `AUTH-001`
- 역할: Tech Lead
- 기준 브랜치: 최신 `main`
- 작업 브랜치: `ops/tl`
- 작업 유형: 인증 설계 문서화
- 결정 상태: Proposed Authentication Design

Tech Lead 전용 역할 문서와 Tech Lead 전용 `.agents/skills/**`는 저장소에서 확인되지 않아 루트 `AGENTS.md`, 선행 문서, 사용자 지시를 기준으로 진행했다.

## 작업 목적

첫 번째 수직 MVP의 인증 경계, 로그인 후 복귀 경로, 보호 API 접근 규칙, Open Redirect 방지 기준을 설계 제안으로 정리했다.

Backend, Frontend, QA가 Spring Security, 세션, 토큰, 쿠키, 라우팅 구현 방식을 아직 확정하지 않고도 공개 기능과 보호 기능, 인증 실패와 인가 실패, 안전한 복귀 경로 기준을 추측 없이 이어받을 수 있게 했다.

## 승인된 입력

- `AGENTS.md`
- `docs/product/PS-002-first-mvp-requirements.md`
- `docs/product/PS-003-ux-product-decisions.md`
- `docs/domain/DOMAIN-001-first-mvp-subscription-domain.md`
- `docs/design/UX-001-first-mvp-subscription-experience.md`
- `docs/adr/ARCH-001-first-vertical-mvp-architecture.md`
- `docs/data/DATA-001-first-mvp-data-model.md`
- `docs/api/API-001-first-mvp-api-contract.md`
- `docs/reports/API-001/be-report.md`
- `docs/handoffs/API-001/be-to-fe-qa.md`

선행 PR 확인:

| PR | 제목 | 상태 | 병합 시각 |
| --- | --- | --- | --- |
| #17 | `docs(data): 첫 수직 MVP 데이터 모델 설계` | `MERGED` | `2026-07-05T16:59:55Z` |
| #18 | `docs(api): 첫 수직 MVP API 계약 설계` | `MERGED` | `2026-07-05T18:01:45Z` |

## 변경 범위

- 인증 설계 ADR 작성
- 인증·인가 경계 정리
- 로그인 후 복귀 경로 정책 정리
- Open Redirect 방지 규칙 정리
- API와 화면 흐름에 미치는 영향 정리
- Backend/Frontend/QA 인수인계 작성
- 작업 보고서 작성
- `AUTH-001` 산출물 검증을 위한 작업 ID 검증기 접두사 보강

## 변경하지 않은 범위

- Spring Security 구현은 수행하지 않았다.
- 로그인 화면 구현은 수행하지 않았다.
- Next.js 라우팅 구현은 수행하지 않았다.
- 세션, 토큰, 쿠키 코드는 구현하지 않았다.
- Controller, Service, Repository 구현은 수행하지 않았다.
- JPA Entity와 Flyway 마이그레이션은 작성하지 않았다.
- 테스트 코드는 작성하지 않았다.
- 신규 의존성은 추가하지 않았다.
- API-001 또는 DATA-001을 `Approved`로 변경하지 않았다.
- 결제, 재고, 배송, 구독 상태 모델은 추가하지 않았다.
- soft delete, 탈퇴, 보관, 익명화 정책은 확정하지 않았다.
- GitHub Actions와 CodeRabbit 설정은 변경하지 않았다.
- 자동 병합하지 않는다.

## 주요 결과

- `GET /api/products`, `GET /api/products/{productId}`를 공개 API로 유지했다.
- `POST /api/subscriptions`, `GET /api/subscriptions`, `GET /api/subscriptions/{subscriptionId}`를 보호 API로 정리했다.
- 보호 API는 인증 컨텍스트의 `memberId` 또는 동등한 회원 식별자를 기준으로 처리해야 한다고 정리했다.
- 요청 본문이나 클라이언트 파라미터의 `memberId`를 신뢰하지 않도록 Backend 입력에 명시했다.
- 로그인 후 복귀는 안전한 서비스 내부 GET 화면 경로로만 허용하고, 실패 시 상품 목록으로 fallback하도록 정리했다.
- 이전 POST 요청 자동 재실행과 구독 생성 폼 상태 자동 복원을 첫 MVP 제외 범위로 유지했다.
- Open Redirect 방지를 위해 외부 URL, scheme-relative URL, 프로토콜 포함 URL, 역슬래시, 인코딩 우회, 제어 문자, allowlist 밖 내부 경로 차단 기준을 정리했다.
- 인증 실패는 `AUTH_REQUIRED`, `401 Unauthorized` 후보를 유지하고, 브라우저 로그인 이동과 API 응답 최종 분기는 후속 구현 결정으로 남겼다.
- 존재하지 않는 구독과 다른 회원 소유 구독은 `SUBSCRIPTION_NOT_FOUND`, `404 Not Found` 후보로 동일하게 표현하는 API-001 기준을 유지했다.
- `scripts/validate-task-artifacts.py`가 `AUTH-001`을 정확히 인식하도록 `AUTH` 접두사를 추가하고 fixture 테스트를 보강했다.

## 변경 파일

- `docs/adr/AUTH-001-login-return-and-auth-boundary.md`
- `docs/reports/AUTH-001/tl-report.md`
- `docs/handoffs/AUTH-001/tl-to-be-fe-qa.md`
- `scripts/validate-task-artifacts.py`
- `scripts/test-validate-task-artifacts.py`

## 결정 상태

- AUTH-001: Proposed Authentication Design
- DATA-001: Proposed Data Design 유지
- API-001: Proposed API Contract 유지

사용자 승인 전 AUTH-001, DATA-001, API-001을 `Approved`로 표시하지 않는다.

## API 영향

- 공개 API와 보호 API 분리 기준이 명확해졌다.
- 보호 API 인증 실패는 API-001 후보인 `AUTH_REQUIRED`, `401 Unauthorized`를 유지한다.
- 다른 회원 소유 구독 상세와 존재하지 않는 구독 상세는 `SUBSCRIPTION_NOT_FOUND`, `404 Not Found` 후보를 유지한다.
- 최종 오류 응답 JSON 구조는 API-001 Proposed 상태를 변경하지 않는다.

## DB 영향

- DB 스키마 변경은 없다.
- `subscriptions.member_id` 또는 동등한 소유자 기준으로 본인 구독 목록과 상세 조회를 제한해야 한다는 DATA-001 기준을 유지한다.
- 다른 회원 소유 구독의 존재 여부를 노출하지 않는 조회 방식이 Backend 구현 입력으로 남았다.

## 보안 영향

- Open Redirect 방지 기준을 명시했다.
- 복귀 경로는 안전한 내부 GET 화면 경로 allowlist로 제한한다.
- 외부 URL, scheme-relative URL, 인코딩 우회, 역슬래시, 제어 문자 후보를 차단 대상으로 정리했다.
- 인증 회원 식별자는 클라이언트 입력이 아니라 인증 컨텍스트에서 얻어야 한다.
- 세션/토큰, 쿠키, CSRF, Spring Security 설정은 Deferred Technical Decision으로 남았다.

## 운영 영향

- 런타임 설정, GitHub Actions, CodeRabbit 설정, 배포 구성 변경은 없다.
- PR 검증이 `AUTH-001` 산출물을 정확히 확인할 수 있도록 로컬 검증 스크립트의 작업 ID 접두사만 보강했다.

## 검증 명령과 결과

| 검증 | 결과 |
| --- | --- |
| `git status --short --branch` | 통과, `ops/tl`에서 AUTH-001 문서 3개 추가와 작업 ID 검증기 2개 변경 확인 |
| `git diff --check` | 통과 |
| `git diff --stat` | 통과, 5 files changed, 595 insertions |
| `git diff --name-status` | 통과, AUTH-001 ADR·보고서·인수인계 추가와 검증기 2개 수정 확인 |
| `Write-Output 'AUTH-001' \| py -3 scripts/validate-task-artifacts.py --from-stdin` | 통과, `task artifacts validated for AUTH-001` |
| `py -3 scripts/test-validate-task-artifacts.py` | 통과, `Task artifact validator fixture OK` |
| `sh scripts/validate-commit-message.sh --message "docs(auth): 로그인 복귀와 인증 경계 설계"` | Windows PowerShell에서 `sh`를 찾지 못해 실패 |
| `C:\Program Files\Git\bin\bash.exe scripts/validate-commit-message.sh --message "docs(auth): 로그인 복귀와 인증 경계 설계"` | 통과 |

## 적용 방법

AUTH-001은 문서 설계 제안이다. 사용자 검토 후 승인 또는 수정 지시를 받아 Backend, Frontend, QA 후속 작업에 입력으로 사용한다.

후속 구현 전에는 세션/토큰, 쿠키, CSRF, Spring Security 설정, 로그인 화면 위치, Next.js 라우팅, Open Redirect 검증 유틸 구현 방식을 별도 Technical Decision으로 확정해야 한다.

## 위험과 제한

- 이번 작업은 설계 제안이며 구현이 아니다.
- 인증 구현 방식이 미확정이므로 Backend 구현 전 추가 결정이 필요할 수 있다.
- Frontend 라우팅 구조가 정해지면 복귀 경로 allowlist를 재검토해야 한다.
- Open Redirect 방지 규칙은 코드와 테스트로 검증되기 전까지 구현 위험이 남아 있다.
- API-001과 DATA-001은 Proposed 상태이며 AUTH-001에서 최종 승인하지 않았다.

## 다음 작업

1. 사용자 검토로 AUTH-001 Proposed Authentication Design 승인 또는 수정
2. Backend 인증 구현 전 세션/토큰, 쿠키, CSRF, Spring Security 설정 결정
3. Backend 보호 API, 소유자 조회, Open Redirect 검증 유틸 구현과 테스트 작성
4. Frontend 로그인 이동, 안전한 복귀, `AUTH_REQUIRED` 처리, POST 자동 재실행 방지 구현
5. QA 인증·인가·Open Redirect 시나리오 작성

## Git 결과

- AUTH-001 시작 전 브랜치: `ops/tl`
- 시작 전 `HEAD`, `main`, `origin/main`: `ec7768c0bd34a2ffe89ade1aea8cf14243d4f88b`
- `git log --oneline main..ops/tl`: 출력 없음
- `git log --oneline ops/tl..main`: 출력 없음
- OPS-005에서 과거 `ops/tl` 잔여 브랜치 정리를 완료한 전제에 따라 reset, force push, rebase, history rewrite를 사용하지 않았다.
- 커밋 SHA: 생성 후 갱신 예정
- push 결과: 생성 후 갱신 예정

## PR 상태

- 열린 PR 목록 확인 결과: 없음
- PR #17 `DATA-001`: `MERGED`
- PR #18 `API-001`: `MERGED`
- AUTH-001 PR: 생성 후 갱신 예정
