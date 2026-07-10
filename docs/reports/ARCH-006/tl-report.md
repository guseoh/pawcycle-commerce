# ARCH-006 Tech Lead 작업 보고서

## 작업 정보

- 작업 ID: `ARCH-006`
- 역할: Tech Lead
- 기준 브랜치: `main`
- 작업 브랜치: `ops/tl`
- 작업 유형: 첫 Backend 구현 승인 입력 기록
- 작업 위치: repository root
- 결정 상태: Approved Inputs with Deferred and Decision Required

## 작업 목적

PR #28로 병합된 ARCH-005의 결정 후보 중 사용자가 ARCH-006 요청에서 명시적으로 선택한 값만 첫 Backend 구현 입력으로 기록한다.

이번 작업은 승인 기록 문서 작업이다. Backend·Frontend 구현, 신규 의존성, DB schema와 CI workflow는 변경하지 않는다.

## 입력 문서

- ARCH-006 사용자 승인 입력
- `AGENTS.md`
- `README.md`
- `docs/roles/tech-lead.md`
- `.agents/skills/tech-lead/SKILL.md`
- `docs/adr/ARCH-005-backend-implementation-approval-candidates.md`
- `docs/reports/ARCH-005/tl-report.md`
- `docs/handoffs/ARCH-005/tl-to-be.md`
- `docs/product/PS-002-first-mvp-requirements.md`
- `docs/domain/DOMAIN-001-first-mvp-subscription-domain.md`
- `docs/adr/ARCH-001-first-vertical-mvp-architecture.md`
- `docs/adr/ARCH-003-backend-implementation-plan-and-dependency-proposal.md`
- `docs/adr/AUTH-001-login-return-and-auth-boundary.md`
- `docs/api/API-001-first-mvp-api-contract.md`
- `docs/data/DATA-001-first-mvp-data-model.md`
- `docs/data/DATA-002-first-mvp-logical-erd.md`
- `docs/adr/FOUNDATION-000-technology-version-decision.md`
- `docs/qa/README.md`
- 작업 보고서·인수인계 템플릿과 산출물 검증 스크립트

## 사용자 승인 입력

| 번호 | 사용자 선택 요약 | 기록 결과 |
| --- | --- | --- |
| 1 | 공개 상품 API 2개와 최소 세션 로그인 기반 | Approved |
| 2 | JPA·MySQL JDBC·Flyway·Security·security test | Approved, 나머지 Deferred |
| 3 | DATA-001·DATA-002 중 `members`·`products`·`skus` | Approved |
| 4 | Flyway와 V1 migration | Approved |
| 5 | 환경 변수 기반 로컬 MySQL | Approved |
| 6 | AUTH-001을 구현 기준으로 사용 | Approved, 충돌·미결정 시 중단 |
| 7 | session 인증 | Approved |
| 8 | CSRF·cookie·principal·BCrypt 기준 | Approved, 세부 전달 계약 Decision Required |
| 9 | API-001 공개 상품 API만 사용 | Approved |
| 10 | `code`·`message`·`fieldErrors` | Approved |
| 11 | ARCH-001·ARCH-003 package·계층 경계 | Approved subset |
| 12 | Java 25·MySQL service·DB/Flyway/Security CI | Approved, 도구 일부 Deferred |
| 13 | 최소 로그인과 session 생성 | Approved |
| 14 | `/api/**` 401·403 JSON | Approved |
| 15 | JWT 확장 방향 | Deferred |
| 16 | 추가 범위 금지와 필요 시 PR 분할 | Approved scope guard |

## Approved·Deferred·Excluded 구분

### Approved

- 공개 상품 목록·상세 API
- 최소 세션 로그인·로그아웃·현재 회원 식별 기반
- Spring Data JPA, MySQL JDBC Driver, Flyway, Spring Security, `spring-security-test`
- `members`, `products`, `skus`와 Product-SKU 관계
- 최초 V1 migration
- 환경 변수 기반 로컬 MySQL과 Secret 비저장
- CSRF 활성화, HttpOnly session cookie, 최소 `memberId` principal, BCrypt
- `code`·`message`·`fieldErrors` 오류 구조
- `/api/**` 401·403 JSON 실패
- ARCH-001·ARCH-003의 지정 범위 package·계층 경계
- Java 25, MySQL service, DB·Flyway·Security·API CI 검증

### Deferred

- 구독 API 3개와 `subscriptions`
- JWT, OAuth2
- Testcontainers와 OpenAPI 자동 검증
- 결제, 주문, 재고, 배송, 구독 상태 전이

### Decision Required

- 로그인·로그아웃·현재 인증 회원 조회의 최소 API 계약
- CSRF token 획득·header 전달의 정확한 계약
- 로그인 식별자와 비밀번호 해시 저장 필드의 최소 데이터 계약
- 구현 PR이 제안할 SQL/JPA/profile/CI image 세부값

### Explicitly Excluded

- ARCH-006에서의 실제 Backend·Frontend·CI·build·DB·Security 변경
- 첫 Backend 구현에서의 관리자, JWT·OAuth2·구독 선행 구조, Secret, Docker와 미래 확장용 추상화

## 변경 범위

- `docs/adr/ARCH-006-first-backend-implementation-approved-inputs.md`
- `docs/reports/ARCH-006/tl-report.md`
- `docs/handoffs/ARCH-006/tl-to-be.md`
- `README.md`의 ARCH-006 링크 한 줄

## 변경하지 않은 범위

- `backend/**`
- `frontend/**`
- `.github/**`
- `build.gradle`, `settings.gradle`, `package.json`, lock file
- DB migration, Entity, Repository, Service, Controller, DTO
- SecurityConfig, datasource, Flyway 설정
- API client와 화면
- Docker, Docker Compose
- 신규 의존성과 Secret
- `DATA-001`, `DATA-002`, `API-001`, `AUTH-001`, `ARCH-001`, `ARCH-003`, `ARCH-005`, `FOUNDATION-000` 원본 상태

## 인수 조건 매핑

| 인수 조건 | 결과 |
| --- | --- |
| PR #28 병합을 원격에서 확인 | GitHub PR metadata에서 merged=true 확인 |
| clean 고정 경로 사용 | 지정 경로에서 clean 상태 확인 후 작업 |
| 최신 `main`에서 새 `ops/tl` 준비 | stale 역할 브랜치를 백업하고 삭제한 뒤 `origin/main` 기준 생성 |
| 사용자 명시 선택만 Approved | 16개 승인 입력을 항목별 매핑하고 미선택 세부값은 Decision Required 유지 |
| Approved·Deferred·Decision Required·Explicitly Excluded 구분 | ARCH-006 승인 입력 문서에 별도 절로 기록 |
| 실제 코드·의존성·CI 변경 없음 | 허용된 문서 4개만 변경 |
| 보고서와 Backend 인수인계 | 본 보고서와 `tl-to-be.md` 작성 |
| 자동 병합 금지 | PR을 Draft로 생성하고 자동 병합하지 않음 |

## 주요 결과

- 첫 Backend 구현의 절대 상한을 공개 상품 API 2개와 최소 세션 로그인 기반으로 고정했다.
- DATA 문서 사용 범위를 `members`, `products`, `skus`로 제한했다.
- 구독, JWT, OAuth2, Testcontainers와 OpenAPI 자동화를 Deferred로 분리했다.
- 기존 문서에서 빠진 최소 인증 API, CSRF 전달, 인증 데이터 계약을 Decision Required로 식별했다.
- Backend Engineer가 구현하면 안 되는 범위, PR 분할 순서, QA 판단 기준과 중단 조건을 인수인계했다.

## 변경 파일

- `README.md`
- `docs/adr/ARCH-006-first-backend-implementation-approved-inputs.md`
- `docs/reports/ARCH-006/tl-report.md`
- `docs/handoffs/ARCH-006/tl-to-be.md`

## 결정 상태

- ARCH-006 문서는 `Approved Inputs`다.
- ARCH-005 전체와 관련 Proposed 문서 전체는 승인하지 않았다.
- 사용자 선택 밖 세부 계약은 `Decision Required` 또는 `Deferred`로 유지했다.

## API 영향

- 실제 API 구현과 기존 계약 원본 변경은 없다.
- 첫 Backend 구현 입력으로 공개 상품 API 2개와 공통 오류·인증 실패 응답 방향만 승인했다.
- 로그인·로그아웃·현재 회원 조회와 CSRF 전달의 세부 계약은 보완 작업이 필요하다.

## DB 영향

- 실제 schema와 migration 변경은 없다.
- 첫 구현 입력은 `members`, `products`, `skus`와 V1 migration으로 제한했다.
- 인증 credential 필드의 물리 계약은 Decision Required다.

## 보안 영향

- 실제 Security 설정 변경은 없다.
- session, CSRF 활성화, HttpOnly·Secure 환경 경계, 최소 principal, BCrypt, 401·403 JSON을 승인 입력으로 기록했다.
- Secret과 민감정보를 추가하지 않았다.

## 운영 영향

- GitHub Actions workflow 변경은 없다.
- 첫 Backend 구현에는 CI MySQL service 검증이 필요하며, workflow 변경이 필요하면 Platform/SRE 작업을 먼저 분리하도록 기록했다.

## 성능 영향

- 성능 코드, 쿼리와 인덱스 변경은 없다.
- 실제 index는 DATA 후보와 조회 근거를 사용하되 구현 PR에서 최소성·필요성을 설명해야 한다.

## 실행한 검증

| 명령·확인 | 결과 |
| --- | --- |
| GitHub PR #28 metadata 조회 | `merged=true`, merge commit `5a31750ab5925d687964828a7885182784a7c12b` 확인 |
| 고정 경로와 `git status --short --branch` | 지정 경로와 clean 초기 working tree 확인 |
| 열린 `ops/tl` PR 검색 | 없음 |
| `git fetch --prune origin` | 성공, `origin/main` 최신화 |
| stale `ops/tl` backup·삭제·재생성 | 성공, 최신 `main`의 `c29de99`에서 새 `ops/tl` 생성 |
| `Write-Output 'ARCH-006' \| py -3 scripts\validate-task-artifacts.py --from-stdin` | `task artifacts validated for ARCH-006` |
| `py -3 scripts\validate-task-artifacts.py --task-id ARCH-006` | `task artifacts validated for ARCH-006` |
| 변경 경로 allowlist 검사 | 허용 문서 4개 외 변경 없음 |
| 미결정 placeholder·Secret 의심 값 검색 | 일치 없음 |
| Approved 항목 필수 필드 개수 검사 | 12개 항목 모두 7개 필드 보유 |
| `bash scripts/validate-commit-message.sh --message "docs(architecture): 첫 Backend 구현 승인 입력 정리"` | 통과 |
| `frontend/npm ci` | 재실행 통과, 343 packages 설치·audit moderate 2건 보고 |
| `frontend/npm run lint` | 통과 |
| `frontend/npm run build` | 통과, Next.js 16.2.10 production build |

## 실행하지 못한 검증과 이유

- `backend/gradlew.bat test`는 실행했으나 로컬 Java가 17이고 프로젝트가 Java 25 toolchain을 요구해 실패했다. Java 25 설치를 찾지 못했고 toolchain download repository도 설정돼 있지 않다.
- `backend/gradlew.bat build`도 같은 Java 25 toolchain 부재로 실패했다.
- 로컬 Node는 `v22.17.1`로 원격 CI의 Node 24와 다르다. Frontend 검증은 로컬에서 통과했지만 Node 24 검증은 원격 CI로 보완한다.
- 첫 병렬 `npm ci` 시도는 잔여 동시 실행과 충돌해 Windows `ENOTEMPTY`로 실패했으나 프로세스 종료를 확인한 뒤 단독 재실행해 install·lint·build가 모두 통과했다.
- PR 생성 전이므로 원격 GitHub Actions와 AI 리뷰는 아직 실행되지 않았다. 원격 Application Validation의 Java 25와 Node 24 결과를 최종 완료 판단에 사용한다.
- 실제 DB migration, JPA, API, Security와 MySQL 동작 검증은 구현 산출물이 없는 승인 기록 작업이므로 실행 대상이 아니다.

## QA 필요 여부

- ARCH-006 자체 QA 문서 불필요.

## QA 문서 경로 또는 생략 사유

- 생략 사유: 문서 기반 승인 기록만 변경하며 사용자-facing 동작, API 실행, DB schema, 인증·인가 동작과 개인정보 처리를 변경하지 않는다.
- 첫 Backend 구현에서 인증·인가나 credential 데이터가 변경되면 `docs/qa/README.md` 기준으로 독립 QA 검증 필요 여부를 다시 판단한다.

## AI 리뷰 반영 여부

- PR 생성 전 AI 리뷰 없음.
- PR 생성 후 CodeRabbit/Codex Review가 남으면 사용자 승인 범위와 대조해 반영 여부를 판단한다.

## AI 리뷰 미반영 항목과 이유

- PR 생성 전 미반영 항목 없음.

## 적용 방법

- ARCH-006 문서는 첫 Backend 구현 작업 요청에서 승인 입력으로 사용한다.
- Backend Engineer는 `Approved`만 구현 입력으로 사용하고 `Deferred`와 `Explicitly Excluded`를 구현하지 않는다.
- `Decision Required` 중 DR1~DR3는 실제 인증·V1 구현 전에 최소 계약 보완 작업으로 해결한다.

## 위험과 제한

- 승인 범위는 기술 방향과 최대 구현 범위를 정하지만 로그인·CSRF·credential의 세부 계약을 자동 확정하지 않는다.
- `AUTH-001`, `API-001`, DATA 문서 원본은 Proposed 상태이므로 ARCH-006의 지정 범위를 벗어난 내용을 승인으로 해석하면 안 된다.
- CI MySQL service 변경은 Backend Engineer 역할 밖일 수 있다.

## 남은 위험

- 최소 인증 계약 보완이 늦어지면 로그인·로그아웃과 V1 migration 구현을 안전하게 시작할 수 없다.
- MySQL·Flyway 조합과 CI service 설정을 함께 검증하지 않으면 로컬 통과와 원격 실패가 갈릴 수 있다.
- 한 PR에 DB·Security·로그인·상품 API를 모두 넣으면 리뷰와 실패 원인 분리가 어려울 수 있다.

## 다음 작업

1. 최소 인증 API·CSRF·credential 계약 보완
2. 필요 시 Platform/SRE CI MySQL service 작업
3. Backend Engineer의 DB·Flyway·JPA·Security 기반 구현
4. 세션 로그인·로그아웃 구현
5. 공개 상품 목록·상세 API 구현
6. QA 독립 검증

## Git 결과

- 기준 commit: `c29de99 docs(obsidian): PR #28 기록 추가`
- 기준 브랜치: `main`
- 작업 브랜치: `ops/tl`
- stale 역할 브랜치 backup:
  - `backup/ops-tl-local-before-ARCH-006`
  - `backup/ops-tl-origin-before-ARCH-006`
- reset, rebase, force push, history rewrite 사용 없음
- commit·push 전

## PR 결과

- PR 생성 전
- 제목 예정: `docs(architecture): 첫 Backend 구현 승인 입력 정리`
- base/head 예정: `main` ← `ops/tl`
- Draft로 생성하고 자동 병합하지 않는다.
