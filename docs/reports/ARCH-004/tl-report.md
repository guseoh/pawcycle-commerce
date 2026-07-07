# ARCH-004 Tech Lead 작업 보고서

## 작업 목적

PR #24 병합 이후 첫 Backend 실제 구현 전에 사용자가 결정해야 할 기술·설계 항목을 결정 요청 문서로 정리했다.

이번 작업은 문서 작업이다. Backend 코드, Frontend 코드, DB migration, JPA Entity, Repository, Service, Controller, DTO, SecurityConfig, 신규 의존성, CI workflow는 변경하지 않는다.

## 선행 PR 확인 결과

| 항목 | 결과 |
| --- | --- |
| PR #23 | `MERGED`, merged at `2026-07-06T17:56:01Z`, merge commit `71cdc99aa078c0b9ba58f4f6863c447375b21bd0` |
| PR #23 head | `ops/tl`, head OID `8bc273e8eac409bb8126c8002edb4421ff6fa087` |
| PR #24 | `MERGED`, merged at `2026-07-07T05:43:43Z`, merge commit `205b070544fae44d2b75b25b41a2b1b85d8650f0` |

## 다른 PC 시작 환경 확인

초기 Codex 작업 경로 `C:\Users\guseo\OneDrive\문서\pawcycle-commerce`는 Git 저장소가 아니었다. 요청서의 다른 PC 시작 규칙에 따라 기본 경로 존재 여부를 확인했고, `C:\Users\guseo\IdeaProjects\pawcycle-commerce`에서 작업했다.

| 확인 항목 | 결과 |
| --- | --- |
| 기본 경로 | `C:\Users\guseo\IdeaProjects\pawcycle-commerce` 존재 |
| 대체 경로 | `C:\dev\pawcycle-commerce` 없음 |
| 사용한 로컬 경로 | `C:\Users\guseo\IdeaProjects\pawcycle-commerce` |
| Git | `git version 2.45.1.windows.1` |
| GitHub CLI | `gh version 2.96.0 (2026-07-02)` |
| GitHub 인증 | `guseoh` 계정 인증 성공 |
| Java | `openjdk version "17.0.16" 2025-07-15 LTS` |
| Node.js | `v22.17.1` |
| npm | `10.9.2` |

로컬 PATH의 Java와 Node.js는 런북 기준인 Java 25 LTS, Node.js 24 LTS와 다르다. 애플리케이션 검증 결과는 아래 검증 섹션에 별도 기록한다.

## 원격 브랜치 정리 확인

작업 시작 시 `origin/ops/tl`에 PR #23의 이전 head 커밋이 남아 있었다. 사용자가 원격 `ops/tl` 정리를 승인했고, 아래 조건을 확인한 뒤 삭제했다.

삭제 전 `origin/ops/tl` 커밋 목록:

```text
8bc273e docs(architecture): ARCH-002 Git 결과 확정
3197d54 docs(architecture): Backend 구현 착수 기준 보완
6753558 docs(architecture): Backend 구현 착수 기준 정리
```

정리 조건 확인:

- PR #23은 `MERGED` 상태였다.
- PR #23 headRefName은 `ops/tl`이었다.
- PR #23 headRefOid는 삭제 전 `origin/ops/tl` OID와 일치했다.
- PR #24는 `MERGED` 상태였다.
- `gh pr list --state open --head ops/tl` 결과는 빈 배열이었다.
- `gh pr list --state open` 결과는 빈 배열이었다.

실행 결과:

- `git push origin --delete ops/tl`: 성공
- `git fetch --all --prune`: 성공
- `git switch main`: 성공
- `git pull --ff-only origin main`: 이미 최신
- `git switch -c ops/tl`: 최신 `main`에서 새 로컬 브랜치 생성

reset, rebase, force push, main history rewrite는 사용하지 않았다. 다른 역할 브랜치는 삭제하지 않았다.

## 전체 파일 및 문서 재검토 결과

필수 구조 확인 명령을 실행했다.

- `Get-ChildItem -Force`: 성공
- `Get-ChildItem docs -Recurse -File | Select-Object FullName`: 성공
- `Get-ChildItem .agents -Recurse -File -ErrorAction SilentlyContinue | Select-Object FullName`: 성공
- `Get-ChildItem backend -Recurse -File | Select-Object FullName`: 성공
- `Get-ChildItem frontend -Recurse -File | Select-Object FullName`: `node_modules`와 `.next` 생성 산출물이 많아 timeout 발생
- `Get-ChildItem scripts -File | Select-Object Name`: 성공
- `Get-ChildItem .github -Recurse -File | Select-Object FullName`: 성공

Frontend 전체 listing timeout 보완:

- `rg --files frontend -g '!node_modules/**' -g '!.next/**'`: 성공
- 확인한 주요 파일: `frontend/AGENTS.md`, `frontend/package.json`, `frontend/package-lock.json`, `frontend/src/app/page.tsx`, `frontend/src/app/layout.tsx`

반드시 읽고 반영할 문서는 모두 확인했다.

- `AGENTS.md`
- `README.md`
- `backend/AGENTS.md`
- `.agents/skills/backend-engineer/SKILL.md`
- `docs/roles/backend-engineer.md`
- `docs/product/PS-002-first-mvp-requirements.md`
- `docs/product/PS-003-ux-product-decisions.md`
- `docs/domain/DOMAIN-001-first-mvp-subscription-domain.md`
- `docs/design/UX-001-first-mvp-subscription-experience.md`
- `docs/adr/ARCH-001-first-vertical-mvp-architecture.md`
- `docs/adr/AUTH-001-login-return-and-auth-boundary.md`
- `docs/adr/FOUNDATION-000-technology-version-decision.md`
- `docs/adr/ARCH-002-first-backend-implementation-readiness.md`
- `docs/adr/ARCH-003-backend-implementation-plan-and-dependency-proposal.md`
- `docs/data/DATA-001-first-mvp-data-model.md`
- `docs/api/API-001-first-mvp-api-contract.md`
- `docs/runbook/FOUNDATION-001-local-development.md`
- `docs/runbook/FOUNDATION-002-ci-validation.md`
- `docs/reports/ARCH-003/be-report.md`
- `docs/handoffs/ARCH-003/be-to-tl.md`
- `scripts/validate-task-artifacts.py`
- `scripts/validate-commit-message.sh`
- `.github/workflows/validate-conventions.yml`
- `.github/workflows/notify-ci-result.yml`
- `.github/workflows/notify-collaboration.yml`
- `.github/workflows/record-merged-pr.yml`

Tech Lead 전용 역할 문서와 Skill은 저장소에 없었다. 이 점은 결정 요청서에 기록했다.

## 변경 범위

- `docs/adr/ARCH-004-backend-implementation-decision-request.md`
- `docs/reports/ARCH-004/tl-report.md`
- `docs/handoffs/ARCH-004/tl-to-po.md`
- `README.md`

`README.md`는 ARCH-004 결정 요청 문서 링크 1줄만 추가한다.

## 변경하지 않은 범위

- `backend/**`
- `frontend/**`
- `.github/**`
- `docs/api/API-001-first-mvp-api-contract.md`
- `docs/data/DATA-001-first-mvp-data-model.md`
- `docs/adr/AUTH-001-login-return-and-auth-boundary.md`
- `docs/adr/ARCH-001-first-vertical-mvp-architecture.md`
- `docs/adr/FOUNDATION-000-technology-version-decision.md`
- `docs/adr/ARCH-003-backend-implementation-plan-and-dependency-proposal.md`
- `docs/domain/**`
- `docs/product/**`
- build file과 package file
- Docker, Docker Compose, DB migration
- JPA Entity, Repository, Service, Controller, DTO, SecurityConfig
- 신규 의존성
- 신규 Secret
- 검증 스크립트의 작업 ID 접두사

## 주요 결과

- `ARCH-004` 결정 요청서를 작성했다.
- 첫 Backend 구현 전 사용자 결정이 필요한 항목을 12개로 정리했다.
- 각 항목마다 선택지, 추천안, 근거, 영향, 위험, 사용자 승인 필요 문장을 작성했다.
- Product Owner가 바로 검토할 수 있도록 인수인계를 작성했다.
- 실제 구현, 의존성 추가, DB migration, Security 설정, workflow 변경은 하지 않았다.

## PR #25 리뷰 반영 결과

PR #25의 thread-aware GraphQL 조회로 다음 4개 review thread가 `isResolved=false`, `isOutdated=false` 상태임을 확인했다.

| Thread | 위치 | 반영 내용 |
| --- | --- | --- |
| DB 의존성과 CI 검증 경로 | `docs/adr/ARCH-004-backend-implementation-decision-request.md` line 368 | JPA/MySQL/Flyway 도입 시 test profile 기반 datasource와 Flyway 처리 경로를 같은 승인 묶음으로 결정하도록 보완했다. |
| 인증 주체 생성 경로 | `docs/adr/ARCH-004-backend-implementation-decision-request.md` line 245 | 보호 API를 포함하려면 최소 로그인/session 생성 경로와 `memberId` principal 생성 방식을 함께 승인하도록 보완했다. |
| API 인증 실패 응답 계약 | `docs/adr/ARCH-004-backend-implementation-decision-request.md` line 297 | session 기반 인증을 쓰더라도 `/api/**` 보호 API는 `401`/`403` JSON 응답 계약을 우선하도록 보완했다. |
| CSRF 토큰 전달 계약 | `docs/adr/ARCH-004-backend-implementation-decision-request.md` line 271 | CSRF 사용 시 token 노출 방식, cookie/header 이름, FE 전송 규칙을 함께 승인하도록 보완했다. |

보고서와 PO 인수인계에도 위 4개 보완 결정을 추가했다. 리뷰 반영 push 후 4개 thread는 모두 `isOutdated=true`가 되었고, 수정 반영이 명확해 `resolveReviewThread`로 모두 `isResolved=true` 처리했다.

## 결정 요청 항목 요약

1. 첫 Backend 구현 범위
2. 신규 의존성 도입 범위
3. `DATA-001`을 실제 DB schema 입력으로 사용할지 여부
4. Flyway 도입 여부와 최초 migration 작성 여부
5. MySQL 연결 정책과 Secret 전달 방식
6. `AUTH-001`을 Spring Security 구현 기준으로 사용할지 여부
7. 인증 방식: session 기반 또는 token 기반
8. CSRF, cookie, principal 구조
9. `API-001`을 Controller 계약으로 사용할지 여부
10. 오류 응답 JSON 구조
11. `ARCH-001`을 구현 아키텍처 기준으로 사용할지 여부
12. CI 확장 범위

## 사용자 승인 필요 항목

- 첫 구현을 최소 API 5개로 진행할지 여부
- JPA, MySQL JDBC driver, Flyway, Spring Security, `spring-security-test` 도입 여부
- DB 의존성 도입 시 CI test/build가 통과할 test profile 기반 datasource와 Flyway 처리 경로
- Testcontainers와 OpenAPI 검증 도구를 첫 구현에서 제외하고 별도 결정으로 둘지 여부
- `DATA-001`의 Product, SKU, Member, Subscription 후보를 첫 DB schema 입력으로 사용할지 여부
- Flyway와 최초 V1 migration 작성 여부
- MySQL 접속 정보를 환경 변수로만 전달할지 여부
- `AUTH-001`을 Spring Security 경계 기준으로 사용할지 여부
- session 기반 인증을 우선할지 여부
- CSRF, cookie, principal 구조
- 보호 API 포함 시 최소 로그인/session 생성 경로와 `memberId` principal 생성 방식
- `/api/**` 보호 API의 `401`/`403` JSON 응답 계약
- CSRF token 노출 방식, cookie/header 이름, FE 전송 규칙
- `API-001` 5개 API 후보를 Controller 계약 기준으로 사용할지 여부
- `code`, `message`, `fieldErrors` 오류 응답 구조
- `ARCH-001`/`ARCH-003` 기반 feature package 구조
- 첫 구현 PR에서는 기존 Repository Validation을 유지하고 DB/OpenAPI CI 확장을 분리할지 여부

## 실행한 검증 명령과 결과

작업 전·문서 작성 중 확인:

| 명령 | 결과 |
| --- | --- |
| `pwd` | `C:\Users\guseo\IdeaProjects\pawcycle-commerce` |
| `git --version` | `git version 2.45.1.windows.1` |
| `gh --version` | `gh version 2.96.0 (2026-07-02)` |
| `gh auth status` | `guseoh` 계정 인증 성공 |
| `java -version` | `openjdk version "17.0.16" 2025-07-15 LTS` |
| `node -v` | `v22.17.1` |
| `npm -v` | `10.9.2` |
| `git fetch --all --prune` | 성공 |
| `git switch main` | 성공 |
| `git pull --ff-only origin main` | 성공 |
| `gh pr view 23 --repo guseoh/pawcycle-commerce --json state,mergedAt,mergeCommit,headRefName,headRefOid` | `MERGED` 확인 |
| `gh pr view 24 --repo guseoh/pawcycle-commerce --json state,mergedAt,mergeCommit` | `MERGED` 확인 |
| `gh pr list --repo guseoh/pawcycle-commerce --state open --head ops/tl --json ...` | 빈 배열 |
| `gh pr list --repo guseoh/pawcycle-commerce --state open --json ...` | 빈 배열 |
| `git push origin --delete ops/tl` | 성공 |
| `git fetch --all --prune` | 성공 |
| `git switch -c ops/tl` | 성공 |

문서 산출물 검증:

| 명령 | 결과 |
| --- | --- |
| `git status --short --branch` | `README.md` 수정, ARCH-004 문서 3개 untracked |
| `git diff --check` | 성공 |
| `git diff --stat` | staging 전이라 tracked 변경인 `README.md` 1줄만 표시 |
| `git diff --name-status` | staging 전이라 tracked 변경인 `README.md`만 표시 |
| `Test-Path docs/adr/ARCH-004-backend-implementation-decision-request.md` | `True` |
| `Test-Path docs/reports/ARCH-004/tl-report.md` | `True` |
| `Test-Path docs/handoffs/ARCH-004/tl-to-po.md` | `True` |
| `Write-Output 'ARCH-004' \| py -3 scripts\validate-task-artifacts.py --from-stdin` | `task artifacts validated for ARCH-004` |
| `& "C:\Program Files\Git\bin\bash.exe" scripts\validate-commit-message.sh --message "docs(architecture): Backend 구현 결정 요청 정리"` | 성공 |
| 금지 표현 검색 | ARCH-004 산출물과 README에서 지정된 금지 문자열 없음 |
| `git diff --cached --check` | 성공 |
| `git diff --cached --stat` | 4 files changed, 764 insertions |
| `git diff --cached --name-status` | `README.md` 수정, ARCH-004 문서 3개 추가 |
| staged Secret 의심 패턴 검색 | 실제 Secret 의심 문자열 없음 |

애플리케이션 검증:

| 명령 | 결과 |
| --- | --- |
| `cd backend; .\gradlew.bat test` | 실패. Java 25 toolchain을 찾지 못함 |
| `cd backend; .\gradlew.bat build` | 실패. Java 25 toolchain을 찾지 못함 |
| `cd frontend; npm ci` | 성공. moderate severity vulnerabilities 2건 보고, 자동 수정하지 않음 |
| `cd frontend; npm run lint` | 성공 |
| `cd frontend; npm run build` | 성공 |

PR #25 리뷰 반영 검증:

| 명령 | 결과 |
| --- | --- |
| `git status --short --branch` | `docs/adr/ARCH-004...`, `docs/reports/ARCH-004...`, `docs/handoffs/ARCH-004...` 수정 |
| `git diff --check` | 성공 |
| `git diff --stat` | 3 files changed, 182 insertions, 25 deletions |
| `git diff --name-status` | ARCH-004 문서 3개 수정 |
| `git diff --cached --check` | 성공 |
| staged Secret 의심 패턴 검색 | 실제 Secret 의심 문자열 없음 |
| `Write-Output 'ARCH-004' \| py -3 scripts\validate-task-artifacts.py --from-stdin` | `task artifacts validated for ARCH-004` |
| `& "C:\Program Files\Git\bin\bash.exe" scripts\validate-commit-message.sh --message "docs(architecture): Backend 구현 결정 요청 보완"` | 성공 |
| `java -version` | `openjdk version "17.0.16" 2025-07-15 LTS` |
| `cd backend; .\gradlew.bat test` | 실패. Java 25 toolchain을 찾지 못함 |
| `cd backend; .\gradlew.bat build` | 실패. Java 25 toolchain을 찾지 못함 |
| `cd frontend; npm ci` | 성공. moderate severity vulnerabilities 2건 보고, 자동 수정하지 않음 |
| `cd frontend; npm run lint` | 성공 |
| `cd frontend; npm run build` | 성공 |

## 실행하지 못한 검증과 이유

- Backend `test`와 `build`: 현재 PC의 기본 Java는 17.0.16이고, 프로젝트는 Java 25 toolchain을 요구한다. Gradle이 Java 25 설치를 찾지 못했고 toolchain download repositories도 설정되어 있지 않아 실패했다.
- GitHub Actions에서는 PR #25의 리뷰 반영 전 Repository Validation이 Java 25 설정으로 성공했다. 리뷰 반영 push 후 다시 확인한다.
- Docker, DB, Flyway migration 검증: 이번 작업은 DB, migration, Docker 설정을 변경하지 않는 문서 작업이다.
- JPA, Security, Testcontainers 통합 검증: 해당 의존성을 추가하지 않았다.
- OpenAPI contract 검증: 도구와 계약 원천을 아직 사용자 결정 대상으로 남겼다.

## 남은 위험

- 사용자 결정 전에는 신규 의존성, DB schema, API Controller 계약, Spring Security 구현을 시작할 수 없다.
- Flyway migration은 병합 후 변경 비용이 크므로 첫 schema 범위가 과도하지 않은지 사용자 검토가 필요하다.
- session 기반 인증을 선택하면 CSRF, cookie 속성, 인증 주체 생성 경로, `/api/**` JSON 실패 응답 계약까지 함께 검토해야 한다.
- DB 의존성과 CI 검증 경로를 함께 정하지 않으면 다음 Backend 구현 PR에서 datasource 또는 Flyway 자동 설정으로 Application validation이 실패할 수 있다.
- MySQL service container, Testcontainers, OpenAPI CI를 첫 구현에서 분리하면 통합 검증 강도는 후속 작업까지 제한된다.
- 로컬 Java/Node 기본 버전이 런북 기준과 다르므로 애플리케이션 검증에서 도구 버전 차이를 확인해야 한다.

## Git 결과

- 원격 저장소: `https://github.com/guseoh/pawcycle-commerce.git`
- 기준 브랜치: `main`
- 작업 브랜치: `ops/tl`
- `origin/ops/tl` 정리: 사용자 승인 후 삭제 완료
- 새 `ops/tl`: 최신 `origin/main` 기준으로 생성
- reset/rebase/force push/history rewrite: 사용하지 않음
- 최초 ARCH-004 커밋: `3f7bcea6684126e3ba8f2deaffbf28272519afbc`
- 최초 push 결과: `origin/ops/tl`에 push 완료
- 리뷰 반영 커밋: `b27a135425ec340239dac90738f6af7dcb20a040`
- 리뷰 반영 push 결과: `origin/ops/tl`에 push 완료

## PR 결과

- PR: #25 `docs(architecture): Backend 구현 결정 요청 정리`
- URL: `https://github.com/guseoh/pawcycle-commerce/pull/25`
- 상태: `OPEN`, draft `false`, base `main`, head `ops/tl`
- PR #25 head SHA: 리뷰 반영 push 후 `b27a135425ec340239dac90738f6af7dcb20a040`
- Repository Validation: 리뷰 반영 전 `success`; 리뷰 반영 후 재실행 확인 중
- 리뷰 thread 상태: 4건 모두 outdated 및 resolved 처리 완료
- 자동 병합하지 않음
