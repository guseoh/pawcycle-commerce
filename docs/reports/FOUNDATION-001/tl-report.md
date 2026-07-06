# FOUNDATION-001 Tech Lead 작업 보고서

## 작업 목적

PR #20 `FOUNDATION-000 기술 버전 결정안` 병합 이후 첫 번째 수직 MVP 구현 전 단계로, Backend와 Frontend의 최소 애플리케이션 기반을 생성하고 로컬 검증 기준을 문서화했다.

## 선행 확인

- PR #20 상태: `MERGED`
- PR #20 mergedAt: `2026-07-06T08:55:58Z`
- PR #20 head: `ops/tl`
- PR #20 head SHA: `94b16e5e36574b97d7f3042de2bfeba1b4e1cd75`
- PR #20 merge commit: `f2a112da0af32f4e087ee7e2269f6d0935169d89`
- 기존 local `ops/tl`: PR #20 잔여 브랜치로 확인 후 삭제
- 기존 `origin/ops/tl`: PR #20 잔여 브랜치로 확인 후 삭제
- 새 작업 브랜치: 최신 `main`의 `3fa1845`에서 `ops/tl` 재생성

## 승인된 입력과 결정

- Backend: Spring Boot
- Frontend: Next.js + TypeScript
- Database: MySQL 8.4 LTS 대상
- Docker: 이번 작업에서는 보류
- Java: 25 LTS
- Spring Boot: 4.1.x 최신 patch 우선
- Gradle: 9.1 이상 9.x
- Node.js: 24 LTS
- Next.js: 16.x
- TypeScript: 6.0.x
- Package manager: npm

## 변경 범위

- `backend/**`
- `frontend/**`
- `docs/runbook/FOUNDATION-001-local-development.md`
- `docs/reports/FOUNDATION-001/tl-report.md`
- `docs/handoffs/FOUNDATION-001/tl-to-be-fe-sre.md`
- `README.md`

## 변경하지 않은 범위

- 도메인 기능 구현
- API Controller, Service, Repository, DTO
- Entity, DB schema, Flyway migration
- Spring Security 설정과 인증 구현
- 상품, 구독, 마이페이지 화면
- Frontend API client
- Dockerfile, Docker Compose, Kubernetes, Nginx
- GitHub Actions
- 배포 설정
- Secret 또는 실제 DB 접속 정보
- DATA-001, API-001, AUTH-001, FOUNDATION-000 상태 변경

## 생성/수정 파일

- `backend/build.gradle`
- `backend/settings.gradle`
- `backend/gradlew`
- `backend/gradlew.bat`
- `backend/gradle/wrapper/gradle-wrapper.jar`
- `backend/gradle/wrapper/gradle-wrapper.properties`
- `backend/src/main/java/com/pawcycle/backend/PawcycleBackendApplication.java`
- `backend/src/main/resources/application.properties`
- `backend/src/test/java/com/pawcycle/backend/PawcycleBackendApplicationTests.java`
- `backend/.gitattributes`
- `backend/.gitignore`
- `frontend/package.json`
- `frontend/package-lock.json`
- `frontend/next.config.ts`
- `frontend/eslint.config.mjs`
- `frontend/tsconfig.json`
- `frontend/src/app/layout.tsx`
- `frontend/src/app/page.tsx`
- `frontend/.gitignore`
- `frontend/README.md`
- `docs/runbook/FOUNDATION-001-local-development.md`
- `docs/reports/FOUNDATION-001/tl-report.md`
- `docs/handoffs/FOUNDATION-001/tl-to-be-fe-sre.md`
- `README.md`

## Backend 생성 방식과 버전

Spring Initializr 공식 `starter.zip`을 사용해 임시 디렉터리에 생성한 뒤 기존 `backend/AGENTS.md`를 보존하며 `backend/`로 복사했다.

- Spring Boot: `4.1.0`
- Java toolchain: `25`
- Gradle wrapper: `9.5.1`
- 검증 JVM: Temurin `25.0.3`
- group: `com.pawcycle`
- package: `com.pawcycle.backend`

Spring Initializr metadata에는 `4.1.0.RELEASE`가 표시됐지만 starter 생성 시 BOM `org.springframework.boot:spring-boot-dependencies:4.1.0.RELEASE` 해석이 실패했다. 같은 공식 생성 경로에서 Maven artifact 버전 형식인 `4.1.0`으로 재시도해 생성했다.

Spring Boot 4.1 공식 생성 결과로 `web` 입력이 `spring-boot-starter-webmvc`, `validation` 입력이 `spring-boot-starter-validation`로 반영됐다. 테스트 baseline은 리뷰 반영 과정에서 표준 `spring-boot-starter-test`로 정리했다. DB, Security, JPA, Flyway 의존성은 추가하지 않았다.

## Frontend 생성 방식과 버전

공식 `create-next-app` `16.2.10`을 사용해 임시 디렉터리에 생성한 뒤 기존 `frontend/AGENTS.md`를 보존하며 `frontend/`로 복사했다.

- Node.js 검증 런타임: `24.18.0`
- npm 검증 버전: `10.9.8`
- Next.js: `16.2.10`
- React: `19.2.4`
- TypeScript: `6.0.3`
- App Router: 사용
- TypeScript: 사용
- ESLint: 사용
- Tailwind CSS: 사용하지 않음
- UI 라이브러리와 상태관리 라이브러리: 추가하지 않음

`create-next-app`의 기본 install 후 `next typegen` 호출이 npm exec 문법 문제로 실패해, 공식 CLI의 `--skip-install` 생성물을 사용하고 `typescript`를 `6.0.3`으로 고정한 뒤 `npm install --package-lock-only`로 lockfile을 생성했다. 이후 `npm ci`, `npm run lint`, `npm run build`가 통과했다.

## 실행한 검증 명령과 결과

- `git status --short --branch`: 작업 전 clean, 새 `ops/tl` 생성 후 변경 파일만 표시
- `git remote -v`: origin `https://github.com/guseoh/pawcycle-commerce.git`
- `git fetch --all --prune`: 성공
- `git switch main`: 성공
- `git pull --ff-only origin main`: 성공
- `gh pr view 20 --repo guseoh/pawcycle-commerce --json number,title,state,mergedAt,headRefName,headRefOid,baseRefName,mergeCommit`: PR #20 merged 확인
- `git log --oneline main..ops/tl`: PR #20 잔여 커밋 확인
- `git log --oneline main..origin/ops/tl`: PR #20 잔여 커밋 확인
- `git diff --name-status ops/tl main`: `main`에만 PR #20 학습 기록이 추가된 상태 확인
- `git branch -d ops/tl`: local 잔여 브랜치 삭제 성공
- `git push origin --delete ops/tl`: remote 잔여 브랜치 삭제 성공
- `git switch -c ops/tl`: 최신 `main`에서 새 작업 브랜치 생성 성공
- `git diff --check`: 성공
- `git diff --stat`: 변경 파일 통계 확인
- `git diff --name-status`: 허용 범위 변경 확인
- `backend/.\\gradlew.bat test`: 성공
- `backend/.\\gradlew.bat build`: 성공
- `frontend/npm ci`: 성공
- `frontend/npm run lint`: 성공
- `frontend/npm run build`: 성공
- `echo "FOUNDATION-001" | python scripts/validate-task-artifacts.py --from-stdin`: PowerShell의 `python` launcher 문제로 실패
- `Write-Output 'FOUNDATION-001' | py -3 scripts/validate-task-artifacts.py --from-stdin`: 성공
- `sh scripts/validate-commit-message.sh --message "chore(foundation): 애플리케이션 기반 생성"`: PowerShell에서 `sh` 미인식으로 실패
- `& "C:\\Program Files\\Git\\bin\\bash.exe" scripts/validate-commit-message.sh --message "chore(foundation): 애플리케이션 기반 생성"`: 성공

## 실패 후 수정 내용

- Spring Initializr `bootVersion=4.1.0.RELEASE` 생성 실패 후 `bootVersion=4.1.0`으로 공식 생성 재시도
- `create-next-app` 기본 install 후 typegen 실패로 `--skip-install` 생성과 lockfile 생성 절차로 전환
- `create-next-app` 기본 `Hello world` 페이지를 빈 루트 페이지로 최소화해 화면 구현으로 읽히지 않게 조정
- Next.js build의 workspace root 추정 경고를 줄이기 위해 `frontend/next.config.ts`에 `turbopack.root` 설정 추가

## 실행하지 못한 검증과 이유

- `npm run typecheck`: script를 추가하지 않아 실행 대상이 아님
- Docker 실행 검증: Docker 사용이 이번 작업 범위에서 보류됨
- DB 연결 검증: MySQL은 대상 DB로만 문서화했고 실제 연결을 만들지 않음
- 제품 기능 테스트: 도메인/API/Auth/UI 기능을 구현하지 않음

## Docker 보류와 DB/API/Auth 미구현

Docker 보류 결정은 유지했다. Dockerfile, Docker Compose, Kubernetes, Nginx 설정은 만들지 않았다.

MySQL은 대상 DB로만 남겼다. JDBC driver, JPA, Flyway, schema, migration, DB secret은 추가하지 않았다.

API Controller, Service, Repository, DTO, Entity, Spring Security, 인증 UI, API client는 만들지 않았다.

## 남은 위험

- 사용자 로컬 PATH의 기본 Java가 17이면 Backend 검증 전 Java 25 `JAVA_HOME` 설정이 필요하다.
- 사용자 로컬 PATH의 기본 Node.js가 24가 아니면 Frontend 검증 전 Node.js 24 LTS 전환이 필요하다.
- `create-next-app` 16.2.10과 npm exec 조합의 typegen 호출 문제는 후속 CLI 버전에서 다시 확인할 필요가 있다.
- `npm audit`은 생성 직후 2건의 moderate 취약점을 보고한다. `npm audit fix --force`는 breaking change 가능성이 있어 이번 작업에서는 실행하지 않았다.
- Spring Boot 4.1 생성 starter 명칭이 기존 `spring-boot-starter-web` 기대와 다르게 세분화되어 있다. 후속 Backend 작업에서 Spring Boot 4.1 starter 정책을 다시 확인한다.

## Git 결과

- 작업 브랜치: `ops/tl`
- 기반 생성 커밋 메시지: `chore(foundation): 애플리케이션 기반 생성`
- 기반 생성 커밋 SHA: `67129d64ebcbe57ecb427de8411c644e99ca2812`
- push 결과: `origin/ops/tl` push 완료

## PR 결과

- PR 번호: #21
- PR 제목: `chore(foundation): 애플리케이션 기반 생성`
- PR 링크: `https://github.com/guseoh/pawcycle-commerce/pull/21`
- 상태: `OPEN`
- base: `main`
- head: `ops/tl`
- 자동 병합: 수행하지 않음
