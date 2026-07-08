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

- `actions/checkout`을 commit SHA로 고정하고 `persist-credentials: false`로 checkout credential persistence 비활성화
- `actions/setup-java@v5`가 가리키는 commit SHA로 Temurin Java 25 설정
- `backend/gradlew` 실행 권한 보장
- `backend`에서 `./gradlew test`
- `backend`에서 `./gradlew build`

Frontend CI:

- `actions/setup-node@v5`가 가리키는 commit SHA로 Node.js 24 설정
- npm cache를 `frontend/package-lock.json` 기준으로 사용
- `frontend`에서 `npm ci`
- `frontend`에서 `npm run lint`
- `frontend`에서 `npm run build`

## CodeRabbit 리뷰 반영

PR #22의 CodeRabbit 리뷰 1건을 최소 범위로 반영했다.

- `.github/workflows/validate-conventions.yml`의 GitHub Actions 참조를 tag 기반에서 commit SHA 기반으로 고정했다.
- `actions/checkout` step에 `persist-credentials: false`를 추가했다.
- `conventions` job의 `fetch-depth: 0`은 유지했다.
- Backend/Frontend 검증 명령, Java 25, Node.js 24, job 구조, `needs` 관계는 변경하지 않았다.
- Backend/Frontend 코드, 의존성, Docker, DB, 배포 설정, Secret은 변경하지 않았다.

Action commit SHA 확인 방법:

- `git ls-remote https://github.com/actions/checkout.git refs/tags/v4 refs/tags/v4^{}`: `34e114876b0b11c390a56381ad16ebd13914f8d5`
- `git ls-remote https://github.com/actions/setup-java.git refs/tags/v5 refs/tags/v5^{}`: `1bcf9fb12cf4aa7d266a90ae39939e61372fe520`
- `git ls-remote https://github.com/actions/setup-node.git refs/tags/v5 refs/tags/v5^{}`: `a0853c24544627f65ddf259abe73b1d18a591444`

## 실행한 로컬 검증 명령과 결과

- `git status --short --branch`: `ops/sre`에서 변경 파일만 표시
- `git diff --check`: 성공
- `git diff --stat`: 변경 파일 통계 확인
- `git diff --name-status`: 허용 범위 변경 확인
- `Select-String -Path '.github\workflows\validate-conventions.yml' -Pattern 'actions/(checkout|setup-java|setup-node)@v'`: tag 기반 action 참조 없음
- Secret 의심 패턴 검사: 변경 파일에서 GitHub token, private key, 실제 webhook URL, 실제 DB password 패턴 없음
- YAML 전용 검사: `actionlint`, `yq`, `ruby`, `PyYAML`이 현재 로컬 환경에 없어 생략
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

CodeRabbit 리뷰 반영 추가 검증:

- 현재 PC 기본 Java는 Temurin `21.0.11`이라 `backend/.\\gradlew.bat test` 최초 실행은 Java 25 toolchain 미탐지로 실패했다.
- 저장소 변경 없이 임시 디렉터리에 Temurin `25.0.3+9` JDK를 내려받아 `JAVA_HOME`으로 지정했다.
- 원래 작업 경로 `C:\Users\guseo\OneDrive\문서\pawcycle-commerce`에서는 Java 25 지정 후에도 `PawcycleBackendApplicationTests` `ClassNotFoundException`으로 `backend/.\\gradlew.bat test`, `backend/.\\gradlew.bat clean test`, `backend/.\\gradlew.bat build`가 실패했다.
- 같은 HEAD와 같은 diff를 ASCII 경로 임시 worktree `C:\Users\guseo\AppData\Local\Temp\pawcycle-ci-20260706230702`에 적용해 `backend/.\\gradlew.bat clean test`와 `backend/.\\gradlew.bat build`를 실행했고 둘 다 성공했다.
- Frontend 검증은 원래 작업 경로에서 `npm ci`, `npm run lint`, `npm run build` 모두 성공했다.

## GitHub Actions 실행 결과

PR #22 생성 후 Repository Validation 실행 결과를 확인했다.

- `Application validation`: 성공, `1m26s`
- `Commit and PR conventions`: 성공, `5s`
- `Discord collaboration notification`: 성공, `7s`
- `CodeRabbit`: review completed, check 성공

`Application validation`에서 Backend test/build와 Frontend install/lint/build가 함께 실행됐다.

## 실패 후 수정 내용

현재까지 workflow 작성 및 로컬 검증 중 실패 후 수정한 내용은 없다.

## 실행하지 못한 검증과 이유

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
- CI 연결 커밋 SHA: `4abf3eaa4dcb88e526cc7a9964f135149447df69`
- push 결과: `origin/ops/sre` push 완료

## PR 결과

- PR 번호: #22
- PR 제목: `ci(foundation): 애플리케이션 검증 연결`
- PR 링크: `https://github.com/guseoh/pawcycle-commerce/pull/22`
- 상태: `OPEN`
- Draft: `false`
- base: `main`
- head: `ops/sre`
- 자동 병합: 수행하지 않음
