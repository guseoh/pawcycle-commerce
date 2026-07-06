# ARCH-002 Tech Lead 작업 보고서

## 작업 목적

PR #22 `ci(foundation): 애플리케이션 검증 연결` 병합 이후 첫 Backend 구현 전에 구현 입력 문서의 상태를 점검했다.

이번 작업은 Backend 구현 착수 기준을 정리하는 문서 작업이다. Backend 코드, Frontend 코드, DB migration, API 구현, Docker, 배포 설정은 변경하지 않았다.

## 승인된 입력과 선행 PR

- 작업 ID: `ARCH-002`
- 역할: Tech Lead
- 선행 PR: #22 `ci(foundation): 애플리케이션 검증 연결`
- PR #22 상태: `MERGED`
- PR #22 merge commit: `ab8637aa6543b43207b104b1a07b246ca84e9c5a`
- 기준 브랜치: `main`
- 작업 브랜치: `ops/tl`

## 작업 환경 확인

- 작업 경로: `C:\dev\pawcycle-commerce`
- ASCII 경로 여부: 예
- 시작 방식: 기존 OneDrive/한글 경로 대신 ASCII 경로에 새 clone 후 작업
- `gh auth status`: `guseoh` 계정 인증 확인
- `main`: `origin/main` 최신 상태 확인
- `origin/ops/tl`: 없음
- `origin/ops/sre`: PR #22 이후 잔여 브랜치가 보였으나 이번 작업에서는 삭제하지 않고 기록만 함

## 변경 범위

- `docs/adr/ARCH-002-first-backend-implementation-readiness.md`
- `docs/reports/ARCH-002/tl-report.md`
- `docs/handoffs/ARCH-002/tl-to-be.md`
- `README.md`

## 변경하지 않은 범위

- `backend/**`
- `frontend/**`
- `.github/**`
- `docs/api/API-001-first-mvp-api-contract.md`
- `docs/data/DATA-001-first-mvp-data-model.md`
- `docs/adr/AUTH-001-login-return-and-auth-boundary.md`
- `docs/domain/**`
- `docs/product/**`
- `build.gradle`
- `package.json`
- `package-lock.json`
- `Dockerfile`
- `docker-compose.yml`
- DB migration
- JPA Entity
- Controller, Service, Repository, DTO, SecurityConfig
- API client
- 화면 구현
- 신규 의존성
- 신규 Secret
- 배포 workflow
- 성능 최적화
- 검증 스크립트의 작업 ID 접두사

## 검토한 문서

- `AGENTS.md`
- `README.md`
- `docs/product/PS-001-first-mvp-domain-scope.md`
- `docs/product/PS-002-first-mvp-requirements.md`
- `docs/product/PS-003-ux-product-decisions.md`
- `docs/design/UX-001-first-mvp-subscription-experience.md`
- `docs/reports/UX-002/ux-report.md`
- `docs/handoffs/UX-002/ux-to-tl.md`
- `docs/domain/DOMAIN-001-first-mvp-subscription-domain.md`
- `docs/adr/ARCH-001-first-vertical-mvp-architecture.md`
- `docs/data/DATA-001-first-mvp-data-model.md`
- `docs/api/API-001-first-mvp-api-contract.md`
- `docs/adr/AUTH-001-login-return-and-auth-boundary.md`
- `docs/adr/FOUNDATION-000-technology-version-decision.md`
- `docs/reports/FOUNDATION-001/tl-report.md`
- `docs/runbook/FOUNDATION-001-local-development.md`
- `docs/runbook/FOUNDATION-002-ci-validation.md`
- `docs/reports/FOUNDATION-002/sre-report.md`
- `docs/handoffs/FOUNDATION-002/sre-to-be-fe-tl.md`

Tech Lead 전용 역할 문서와 Tech Lead 전용 Skill은 저장소에서 확인되지 않았다. 따라서 루트 `AGENTS.md`, 사용자 지시, 선행 설계 문서를 기준으로 작업했다.

## 주요 확인 결과

- PS-002와 PS-003은 제품 요구사항과 승인 Product Decision 입력으로 사용할 수 있다.
- UX-002는 PS-003 결정을 UX 문서에 반영했고, 구체 URI, 라우팅, 인증 구현은 Deferred Technical Decision으로 남겼다.
- DOMAIN-001은 승인 제품 규칙을 도메인 의미와 불변 조건으로 정리했지만 Aggregate, DB/API 표현은 Proposed 또는 Deferred로 남겨뒀다.
- ARCH-001은 Proposed Architecture Decision이다.
- DATA-001은 Proposed Data Design이다.
- API-001은 Proposed API Contract다.
- AUTH-001은 Proposed Authentication Design이다.
- FOUNDATION-000은 Status: Proposed다.
- FOUNDATION-001은 최소 Backend/Frontend 기반을 만들었지만 DB, JPA, Flyway, Security, Controller, Service, Repository, Entity, DTO는 만들지 않았다.
- FOUNDATION-002는 Backend test/build와 Frontend install/lint/build를 CI에 연결했지만 Docker, MySQL, Flyway, JPA 통합 테스트, Security 테스트는 포함하지 않았다.

## 결정 상태

| 항목 | 상태 | 판단 |
| --- | --- | --- |
| 제품 요구사항 | 승인된 입력 포함 | 구현 계획 입력으로 사용 가능 |
| UX 제품 결정 | Approved Product Decision | 구현 계획 입력으로 사용 가능 |
| 도메인 불변 조건 | Accepted Domain Design 포함 | 구현 계획 입력으로 사용 가능 |
| ARCH-001 | Proposed Architecture Decision | 사용자 승인 전 최종 아키텍처로 간주하지 않음 |
| DATA-001 | Proposed Data Design | 사용자 승인 전 DB schema로 구현하지 않음 |
| API-001 | Proposed API Contract | 사용자 승인 전 Controller 계약으로 구현하지 않음 |
| AUTH-001 | Proposed Authentication Design | 사용자 승인 전 Spring Security 구현으로 확정하지 않음 |
| FOUNDATION-000 | Proposed | 사용자 승인 전 신규 의존성 도입 근거로 사용하지 않음 |

## 사용자 승인 필요 항목

- DATA-001을 실제 DB schema로 확정
- API-001을 실제 Controller 계약으로 확정
- AUTH-001을 실제 Spring Security 구현으로 확정
- ARCH-001을 최종 아키텍처 결정으로 승인
- FOUNDATION-000의 기술 버전과 의존성 방향 승인
- JPA, Flyway, MySQL connector, Spring Security 등 신규 의존성 추가
- Flyway migration 작성
- MySQL 연결 정책, schema 생성 방식, Secret 전달 방식
- 세션, 토큰, 쿠키, CSRF, principal 구조
- Open Redirect 방지 검증 유틸 구현 위치와 테스트 범위

## 다음 Backend 작업 제안

추천 1:

```text
후속 Backend 계획 작업: Backend 구현 계획과 의존성 도입안 정리
```

실제 코드 구현 전 JPA, Flyway, MySQL, Security 의존성 도입 범위와 테스트 전략을 정리한다.

추천 2:

```text
후속 Backend 구현 작업: 첫 수직 MVP Backend 최소 구현
```

단, 사용자가 DATA-001, API-001, AUTH-001, ARCH-001, FOUNDATION-000을 구현 입력으로 승인한 경우에만 진행한다.

## 실행한 검증 명령과 결과

작업 전 확인:

- `pwd`: `C:\dev\pawcycle-commerce`
- `git --version`: 성공
- `gh --version`: 성공
- `gh auth status`: 성공
- `git status --short --branch`: `ops/tl`, clean
- `git remote -v`: `https://github.com/guseoh/pawcycle-commerce.git`
- `git fetch --all --prune`: 성공
- `git switch main`: 성공
- `git pull --ff-only origin main`: 성공
- `gh pr view 22 --repo guseoh/pawcycle-commerce --json number,title,state,mergedAt,headRefName,headRefOid,baseRefName,mergeCommit`: `MERGED` 확인
- `git log --oneline --decorate -n 30`: 최신 `main` 이력 확인
- `git log --oneline main..ops/tl`: 미병합 커밋 없음
- `git log --oneline main..origin/ops/tl`: `origin/ops/tl` 없음
- `git log --oneline main..origin/ops/sre`: PR #22 잔여 브랜치 커밋 확인, 삭제하지 않음

문서 작성 후 검증:

- `git status --short --branch`: `README.md` 수정과 `ARCH-002` 산출물 추가 확인
- `git diff --check`: 성공
- `git diff --stat`: 추적 파일 기준 `README.md` 1줄 변경 확인
- `git diff --name-status`: 추적 파일 기준 `README.md` 변경 확인
- `git diff --cached --check`: 성공
- `git diff --cached --stat`: `README.md`, ARCH-002 ADR, 보고서, 인수인계 4개 파일 변경 확인
- `git diff --cached --name-status`: 허용 변경 범위 4개 파일 확인
- `Test-Path docs/adr/ARCH-002-first-backend-implementation-readiness.md`: `True`
- `Test-Path docs/reports/ARCH-002/tl-report.md`: `True`
- `Test-Path docs/handoffs/ARCH-002/tl-to-be.md`: `True`
- `Write-Output 'ARCH-002' | python scripts/validate-task-artifacts.py --from-stdin`: 성공
- `Write-Output 'ARCH-002' | py -3 scripts/validate-task-artifacts.py --from-stdin`: 성공
- `sh scripts/validate-commit-message.sh --message "docs(architecture): Backend 구현 착수 기준 정리"`: PowerShell에서 `sh` 미인식으로 실패
- `& "C:\Program Files\Git\bin\bash.exe" scripts/validate-commit-message.sh --message "docs(architecture): Backend 구현 착수 기준 정리"`: 성공
- Secret 의심 패턴 검사: 변경 파일에서 GitHub token, private key, 실제 webhook URL, 실제 DB password 패턴 없음

PR #23 생성 후 GitHub Actions 확인:

- Repository Validation: 성공
- Commit and PR conventions: 성공
- Application validation: 성공
- Backend `./gradlew test`: GitHub Actions에서 성공
- Backend `./gradlew build`: GitHub Actions에서 성공
- Frontend `npm ci`: GitHub Actions에서 성공
- Frontend `npm run lint`: GitHub Actions에서 성공
- Frontend `npm run build`: GitHub Actions에서 성공

리뷰 반영 전 확인:

- `gh pr view 23 --repo guseoh/pawcycle-commerce --json number,title,state,isDraft,headRefName,baseRefName,headRefOid,mergeable,url`: PR #23 `OPEN`, Draft `false`, head `ops/tl`, base `main`, mergeable 확인
- `gh pr checks 23 --repo guseoh/pawcycle-commerce`: Application validation, CodeRabbit, Commit and PR conventions, Discord notification 성공 확인
- `fetch_comments.py`: unresolved review thread 6개 확인

리뷰 반영 내용:

- `docs/adr/ARCH-002-first-backend-implementation-readiness.md`: 실제 최소 Backend 구현 조건에 `ARCH-001` 승인 조건 추가, 후속 Backend 작업 ID를 확정값처럼 보이지 않게 정리
- `docs/handoffs/ARCH-002/tl-to-be.md`: 검증 명령의 `BE-001` 고정 작업 ID 제거, 후속 작업 ID는 하네스 허용 접두사로 정하도록 명시, 실제 최소 구현 조건에 `ARCH-001` 승인 조건 추가
- `docs/reports/ARCH-002/tl-report.md`: 실제 최소 Backend 구현 조건에 `ARCH-001` 승인 조건 추가, Git/PR/GitHub Actions 결과 갱신

리뷰 반영 중 로컬 애플리케이션 검증:

- `java -version`: 기본 PATH는 Temurin Java 21로 확인
- `backend/.\\gradlew.bat test`: 기본 Java 21 환경에서 Java 25 toolchain 미탐지로 실패
- `backend/.\\gradlew.bat build`: 기본 Java 21 환경에서 Java 25 toolchain 미탐지로 실패
- `$env:JAVA_HOME='C:\Users\guseo\AppData\Local\Temp\pawcycle-temurin-25\jdk-25.0.3+9'`: 임시 Temurin Java 25 지정
- `backend/.\\gradlew.bat test`: Java 25 지정 후 성공
- `backend/.\\gradlew.bat build`: Java 25 지정 후 성공
- `frontend/npm ci`: 성공, moderate 취약점 2건 보고, 자동 수정은 수행하지 않음
- `frontend/npm run lint`: 성공
- `frontend/npm run build`: 성공

## 실행하지 못한 검증과 이유

- Docker, DB, Flyway 검증: 이번 작업 범위가 아니며 해당 설정을 추가하지 않았다.

## 남은 위험

- DATA-001, API-001, AUTH-001, ARCH-001, FOUNDATION-000은 Proposed 상태이므로 사용자의 승인 없이 실제 구현으로 들어가면 계약과 구현이 어긋날 수 있다.
- 인증 구현 방식과 Open Redirect 방지 구현이 확정되지 않아 보호 API 구현 전 추가 결정이 필요하다.
- DB schema, JPA 매핑, Flyway migration이 확정되지 않아 영속성 구현 전 추가 결정이 필요하다.
- 신규 의존성 추가가 필요하면 사용자 승인이 필요하다.
- `origin/ops/sre` 잔여 브랜치가 남아 있으나 이번 작업에서는 삭제하지 않았다.

## Git 결과

- 작업 브랜치: `ops/tl`
- 최초 커밋 메시지: `docs(architecture): Backend 구현 착수 기준 정리`
- 최초 커밋 SHA: `6753558639bd8410d3e0bc6ca44d5fbda4f90d7b`
- 리뷰 반영 전 PR head SHA: `6753558639bd8410d3e0bc6ca44d5fbda4f90d7b`
- 리뷰 반영 커밋 메시지: `docs(architecture): Backend 구현 착수 기준 보완`
- 리뷰 반영 커밋 SHA: 이 보고서가 포함된 커밋이므로 완료 보고에서 확정
- push 결과: 리뷰 반영 커밋 후 완료 보고에서 확정

## PR 결과

- PR 번호: #23
- PR 제목: `docs(architecture): Backend 구현 착수 기준 정리`
- PR 링크: `https://github.com/guseoh/pawcycle-commerce/pull/23`
- 상태: `OPEN`
- Draft: `false`
- base: `main`
- head: `ops/tl`
- Repository Validation: 성공
- Commit and PR conventions: 성공
- Application validation: 성공
- Backend `./gradlew test`: GitHub Actions에서 성공
- Backend `./gradlew build`: GitHub Actions에서 성공
- Frontend `npm ci`: GitHub Actions에서 성공
- Frontend `npm run lint`: GitHub Actions에서 성공
- Frontend `npm run build`: GitHub Actions에서 성공
- 자동 병합: 수행하지 않음
