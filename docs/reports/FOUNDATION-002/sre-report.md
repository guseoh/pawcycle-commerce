# FOUNDATION-002 Platform/SRE 작업 보고서

## 작업 목적

PR #21 `FOUNDATION-001 애플리케이션 기반 생성` 병합 이후 Repository Validation workflow가 Backend와 Frontend의 최소 검증을 함께 실행하도록 CI 기준선을 강화한다.

## 승인된 입력과 선행 PR

- 선행 PR: #21 `chore(foundation): 애플리케이션 기반 생성`
- PR #21 상태: `MERGED`
- PR #21 merge commit: `75633f6ac22fa8e5240e27d5dbb15f3590083933`
- Backend 기준: Java 25, Spring Boot 4.1.0, Gradle wrapper 9.5.1
- Frontend 기준: Node.js 24 LTS, Next.js 16.2.10, TypeScript 6.0.3, npm
- Docker는 이번 작업에서도 보류
- DB 연결, Flyway migration, JPA, Security는 아직 구현하지 않음

## 변경 범위

- `.github/workflows/validate-conventions.yml`
- `docs/runbook/FOUNDATION-002-ci-validation.md`
- `docs/reports/FOUNDATION-002/sre-report.md`
- `docs/handoffs/FOUNDATION-002/sre-to-be-fe-tl.md`
- `README.md`

## 변경하지 않은 범위

- Backend 코드와 Gradle 설정
- Frontend 코드와 npm 의존성
- Java/Node 버전 변경
- Dockerfile, Docker Compose, Kubernetes, Nginx
- MySQL service container, DB schema, Flyway migration
- JPA Entity, Controller, Service, Repository, DTO
- SecurityConfig, API client, 화면 구현
- 배포 workflow
- 신규 Secret 또는 외부 SaaS 연동
- 성능 최적화

## 변경 파일

- `.github/workflows/validate-conventions.yml`
- `docs/runbook/FOUNDATION-002-ci-validation.md`
- `docs/reports/FOUNDATION-002/sre-report.md`
- `docs/handoffs/FOUNDATION-002/sre-to-be-fe-tl.md`
- `README.md`

## CI workflow 변경 내용

기존 Repository Validation workflow의 `conventions` job은 유지했다.

새 `application` job을 추가하고 `needs: conventions`로 기존 하네스 검증이 통과한 뒤 실행되도록 했다.

Backend CI:

- `actions/setup-java@v5`로 Temurin Java 25 설정
- `backend/gradlew` 실행 권한 보장
- `backend`에서 `./gradlew test`
- `backend`에서 `./gradlew build`

Frontend CI:

- `actions/setup-node@v5`로 Node.js 24 설정
- npm cache를 `frontend/package-lock.json` 기준으로 사용
- `frontend`에서 `npm ci`
- `frontend`에서 `npm run lint`
- `frontend`에서 `npm run build`

## 실행한 로컬 검증 명령과 결과

- `git status --short --branch`: `ops/sre`에서 변경 파일만 표시
- `git diff --check`: 성공
- `git diff --stat`: 변경 파일 통계 확인
- `git diff --name-status`: 허용 범위 변경 확인
- `backend/.\\gradlew.bat test`: 성공
- `backend/.\\gradlew.bat build`: 성공
- `frontend/npm ci`: 성공
- `frontend/npm run lint`: 성공
- `frontend/npm run build`: 성공
- `echo "FOUNDATION-002" | python scripts/validate-task-artifacts.py --from-stdin`: PowerShell의 `python` launcher 문제로 실패
- `Write-Output 'FOUNDATION-002' | py -3 scripts/validate-task-artifacts.py --from-stdin`: 성공
- `sh scripts/validate-commit-message.sh --message "ci(foundation): 애플리케이션 검증 연결"`: PowerShell에서 `sh` 미인식으로 실패
- `& "C:\\Program Files\\Git\\bin\\bash.exe" scripts/validate-commit-message.sh --message "ci(foundation): 애플리케이션 검증 연결"`: 성공

검증 런타임:

- Backend: Temurin Java `25.0.3`, Gradle wrapper `9.5.1`
- Frontend: Node.js `24.18.0`, npm `10.9.8`

`npm ci`는 moderate 취약점 2건을 보고했지만, 자동 수정은 breaking change 가능성이 있어 이번 CI 연결 범위에서 실행하지 않았다.

## GitHub Actions 실행 결과

PR 생성 후 Repository Validation 실행 결과를 확인해 이 보고서와 PR 본문에 반영한다.

## 실패 후 수정 내용

현재까지 workflow 작성 및 로컬 검증 중 실패 후 수정한 내용은 없다.

## 실행하지 못한 검증과 이유

- PR 생성 전에는 GitHub Actions 원격 실행 결과를 확인할 수 없다.
- Linux/macOS 로컬 `./gradlew test`, `./gradlew build`는 현재 Windows PowerShell 환경이라 실행하지 않았다. 대신 Windows 명령인 `.\gradlew.bat test`, `.\gradlew.bat build`를 실행했다.

## 남은 위험

- GitHub hosted runner에서 `actions/setup-java@v5`의 Java 25 설치가 실패하면 workflow 범위에서 조정 가능한지 확인해야 한다.
- GitHub hosted runner에서 `actions/setup-node@v5`의 Node.js 24 설치가 실패하면 workflow 범위에서 조정 가능한지 확인해야 한다.
- Backend 또는 Frontend 자체 test/build가 CI에서 실패하고 소스 또는 의존성 수정이 필요해지면 이번 SRE 범위를 벗어난다.
- npm audit moderate 취약점은 FOUNDATION-001에서 확인됐지만 이번 CI 연결 범위에서는 자동 수정하지 않는다.
- Docker와 DB 검증은 아직 CI에 포함되지 않았다.

## Git 결과

- 작업 브랜치: `ops/sre`
- 기준 브랜치: `main`
- 커밋 메시지: `ci(foundation): 애플리케이션 검증 연결`
- 커밋 SHA: 커밋 후 완료 보고에 기록
- push 결과: push 후 완료 보고에 기록

## PR 결과

PR 생성 후 번호, 링크, head/base, GitHub Actions 결과를 확인해 완료 보고와 PR 본문에 기록한다.
