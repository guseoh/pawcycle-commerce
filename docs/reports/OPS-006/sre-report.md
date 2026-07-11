# OPS-006 Platform/SRE 작업 보고서

## 작업 정보

- 작업 ID: `OPS-006`
- 역할: Platform/SRE
- 기준 브랜치: `main`
- 작업 브랜치: `ops/sre`
- 작업일: 2026-07-10
- 작업 유형: CI 데이터베이스 기반

## 작업 목적

후속 Backend의 JPA·Flyway·Spring Security 구현이 GitHub Actions에서 실제 MySQL을 사용할 수 있도록 Application validation에 MySQL 8.4 LTS service, health check와 CI datasource 환경 변수를 제공한다.

이번 작업은 service 기동과 환경 제공까지만 검증한다. Backend에 datasource, JDBC, JPA와 Flyway가 없으므로 실제 연결·migration·schema 검증 성공으로 보고하지 않는다.

## 입력 문서

- 사용자 OPS-006 요청
- 사용자 OPS-006 PR #32 리뷰 수정·로컬 브랜치 복구 승인
- `AGENTS.md`
- `infra/AGENTS.md`
- `.agents/skills/platform-sre/SKILL.md`
- `docs/roles/platform-sre.md`
- `docs/adr/ARCH-006-first-backend-implementation-approved-inputs.md`
- `docs/handoffs/ARCH-006/tl-to-be.md`
- `docs/adr/AUTH-003-session-authentication-approved-inputs.md`
- `docs/handoffs/AUTH-003/tl-to-be.md`
- `docs/adr/FOUNDATION-000-technology-version-decision.md`
- `.github/workflows/validate-conventions.yml`
- `docs/runbook/FOUNDATION-002-ci-validation.md`

## 승인 입력

- PR #29의 ARCH-006 Approved Inputs
- PR #31의 AUTH-003 Approved 입력
- CI MySQL service를 이용한 Backend 검증
- MySQL 8.4 LTS를 첫 개발·검증 target으로 사용하는 프로젝트 방향
- Java 25 Backend test/build와 기존 Repository Validation 유지
- Backend가 `.github/**` 변경을 필요로 하면 Platform/SRE 작업으로 분리
- Testcontainers Deferred, Docker Compose Explicitly Excluded
- 실제 Secret 저장 금지
- OPS-006 후속 요청에서 사용자와 Tech Lead가 CI 검증용 `mysql:8.4` mutable tag 유지와 tag drift 위험 수용을 승인
- digest 고정은 재현 가능한 릴리스 검증, 공급망 보안 강화 또는 예상하지 않은 tag drift가 확인되는 후속 작업에서 재검토

MySQL 8.4 image는 이번 CI 구현에서 제안하고 workflow로 검증하는 물리 세부값이다. FOUNDATION-000 원본 상태를 변경하거나 문서 전체를 Approved로 승격하지 않는다.

## 기존 CI 상태

- Commit and PR conventions job은 PR 제목·본문·작업 산출물·커밋 메시지·공백을 검증했다.
- Application validation job은 Java 25 Backend test/build와 Node.js 24 Frontend install/lint/build를 실행했다.
- MySQL service, health check, datasource 환경 변수와 DB 실패 진단은 없었다.
- OPS-006 산출물 생성 전 기준 validator는 보고서와 인수인계 부재로 실패했다. 이는 현재 작업 산출물이 아직 없어서 발생한 예상 기준 실패이며 다른 기존 작업 실패와 구분했다.

## 변경 범위

- Application validation에 MySQL 8.4 service 추가
- service health check와 version·character set·collation 확인 step 추가
- Backend test/build에 CI datasource 환경 변수 제공
- FOUNDATION-002 CI 런북에 실행·진단·롤백 절차 추가
- OPS-006 SRE 보고서와 Backend Engineer 인수인계 작성

## 변경하지 않은 범위

- `backend/**`, `frontend/**`
- ADR, API와 DATA 원본 문서
- DB migration, JPA Entity, Repository, Service, Controller, DTO, SecurityConfig
- application datasource 설정과 Gradle dependency
- members, products, skus schema와 Flyway V1
- session 로그인, CSRF, 공개 상품 API, JWT, OAuth2와 구독 API
- Testcontainers, Docker Compose와 배포 인프라
- 실제 Secret 또는 production credential

## 인수 조건 매핑

| OPS-006 요구 | 결과 |
| --- | --- |
| MySQL 8.4 service | `application.services.mysql.image: mysql:8.4` |
| CI database | `pawcycle_test` |
| 충돌 안전 port | container `3306/tcp`만 선언하고 GitHub Actions dynamic host port 사용 |
| health check | `mysqladmin ping`, interval 10초, timeout 5초, retries 10, start period 20초 |
| character set·collation | 첫 step에서 `utf8mb4`, `utf8mb4_0900_ai_ci` 확인 |
| Backend 환경 변수 | test/build에 `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` 제공 |
| 기존 CI 보존 | conventions, Java 25, Backend test/build, Node 24, Frontend install/lint/build 유지 |
| Secret 경계 | CI job 전용 비운영 값, GitHub Secret 신규 요구 없음, 로그 출력 금지 |
| 실패 진단 | runbook에 container, pull, health, port, datasource, Flyway 구분 기록 |

## MySQL image와 port 선택 근거

- `mysql:8.4`는 승인된 MySQL 8.4 LTS 계열을 명시하고 변동성이 큰 `latest`를 사용하지 않는다.
- OPS-006 후속 요청에서 사용자와 Tech Lead가 CI 검증용 mutable `mysql:8.4` tag 유지를 승인했다. 같은 tag가 다른 patch 또는 내부 image 내용을 가리킬 수 있는 tag drift 위험을 현재 단계에서 수용한다.
- `Verify MySQL service`는 실제 server version `8.4.*`를 확인하지만 image 내부 패키지 전체의 동일성까지 보장하지 않는다.
- digest 고정은 현재 범위에서 제외하며 재현 가능한 릴리스 검증, 공급망 보안 강화 또는 예상하지 않은 tag drift가 확인되면 공식 digest와 갱신·검증 절차를 Tech Lead 결정 항목으로 다시 올린다.
- 이 결정은 CI 검증용 image에만 적용하며 운영 환경의 MySQL image 정책을 확정하지 않는다.
- `ports: 3306/tcp`는 GitHub Actions가 사용 가능한 host port를 job마다 동적으로 할당하므로 GitHub-hosted runner의 다른 service와 고정 port 충돌을 피한다.
- Backend URL은 `job.services.mysql.ports['3306']` 값을 사용한다.

## CI database·credential 경계

- database: `pawcycle_test`
- application user: CI 전용 `pawcycle_ci`
- password와 root password: workflow job에서만 쓰는 명백한 비운영 테스트 값
- Repository 또는 Environment Secret 신규 요구 없음
- production database 이름, 실제 운영 계정과 실제 Secret 사용 없음
- 비밀번호와 전체 connection string을 로그에 출력하는 진단 명령 없음

값이 저장소에 보이는 이유는 폐기 가능한 CI service 초기화에 필요한 고정 test fixture이기 때문이다. 이 값을 로컬 공유 계정이나 production에서 재사용하면 안 된다.

## health check 방식

Docker service health check는 container 내부에서 다음을 실행한다.

```text
mysqladmin ping --host=127.0.0.1 --silent
```

GitHub Actions는 service가 healthy 상태가 되기 전에 job step을 시작하지 않는다. 이후 `Verify MySQL service` step이 container 내부 client로 MySQL 8.4 계열과 server character set·collation을 확인한다. health 실패는 Backend test 전에 job을 중단한다.

## Backend 환경 변수 전달 방식

Backend test와 build step 각각에 다음 변수를 전달한다.

```text
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:<dynamic-port>/pawcycle_test
SPRING_DATASOURCE_USERNAME=<CI 전용 사용자>
SPRING_DATASOURCE_PASSWORD=<CI 전용 비운영 값>
```

URL의 port는 `${{ job.services.mysql.ports['3306'] }}` 표현식에서 가져온다. JDBC option은 Backend application-test 설정과 함께 후속 작업에서 결정한다.

## 주요 결과

- 기존 Application validation 순서를 유지하면서 Backend 검증보다 앞에 MySQL service 검증 기반을 추가했다.
- 고정 host port를 만들지 않고 job별 dynamic mapping을 사용했다.
- service 기동 실패와 datasource·Flyway 실패를 구분하는 runbook을 제공했다.
- Backend가 `.github/**`를 변경하지 않고 application-test 설정과 migration에 집중할 수 있는 인수인계를 작성했다.

## 변경 파일

- `.github/workflows/validate-conventions.yml`
- `docs/runbook/FOUNDATION-002-ci-validation.md`
- `docs/reports/OPS-006/sre-report.md`
- `docs/handoffs/OPS-006/sre-to-be.md`

README는 기존 FOUNDATION-002 runbook이 이미 주요 문서에 연결되어 있어 변경하지 않는다.

## 결정 상태

- `mysql:8.4`: OPS-006 workflow 물리 구현값
- CI MySQL image 정책: mutable `mysql:8.4` tag 유지 `Approved`
- tag drift: 현재 CI 단계의 제한으로 위험 수용
- digest 고정: 현재 제외, 재현 가능한 릴리스 검증·공급망 보안 강화·예상하지 않은 drift 발생 시 재검토
- dynamic host port: OPS-006 충돌 회피 전략
- Testcontainers: Deferred 유지
- Docker Compose: Explicitly Excluded 유지
- datasource·Flyway·schema: 후속 Backend 검증
- FOUNDATION-000 원본 상태: 변경 없음

## API 영향

- API 변경 없음.

## DB 영향

- 영속 DB schema와 migration 변경 없음.
- CI job 수명 동안 `pawcycle_test` 빈 database를 생성한다.
- 실제 datasource 연결, Flyway 적용과 schema 검증은 아직 수행하지 않는다.

## 보안 영향

- 실제 Secret과 production credential을 추가하지 않았다.
- CI credential은 job 종료 시 service와 함께 폐기되는 비운영 값이다.
- credential과 전체 connection string을 로그에 직접 출력하지 않는다.

## 운영 영향

- Application validation마다 MySQL 8.4 image pull·container 시작 시간이 추가된다.
- GitHub-hosted runner의 Docker service에 의존한다.
- MySQL health 실패는 Backend test 이전에 명확히 실패한다.

## 성능 영향

- 애플리케이션 성능 최적화 작업이 아니다.
- CI 소요 시간은 image cache와 MySQL 초기화에 따라 증가할 수 있으며 원격 run 결과로 관찰한다.

## 실제로 검증된 범위

- workflow diff에서 기존 conventions·Backend·Frontend step 보존
- service image, dynamic port 표현식, health option과 environment 전달 정의
- runbook의 service·image pull·port·datasource·Flyway 실패 구분
- 로컬 Frontend install/lint/build
- OPS-006 산출물 validator와 diff·Secret 검사
- 원격 `Initialize containers`와 `Verify MySQL service`
- 원격 Java 25 Backend test/build와 Node.js 24 Frontend install/lint/build

## 아직 검증되지 않은 datasource·Flyway 범위

- Backend의 `SPRING_DATASOURCE_*` 환경 변수 소비
- MySQL JDBC 실제 connection
- Flyway V1 migration과 재실행
- JPA Entity·Repository mapping
- members, products, skus schema와 제약
- authentication 또는 공개 상품 API의 DB 통합 동작

위 항목은 승인된 Backend 구현이 dependency, application-test 설정, migration과 테스트를 추가한 뒤 같은 CI 경로에서 검증한다.

## 실행한 검증

| 검증 | 결과 |
| --- | --- |
| 지정 저장소 경로·원격 저장소·GitHub CLI 인증 | 확인 |
| PR #31 상태 | `MERGED` 확인 |
| 기존 `ops/sre` PR·commit 확인 | 열린 PR 없음, 로컬·원격 head 모두 병합 완료 작업 |
| `main` fast-forward와 새 `ops/sre` | 최신 `origin/main` 기준 생성 |
| OPS-006 기준 validator | 예상 실패: 보고서와 인수인계 생성 전 부재 |
| `docker info` | 실패: Docker Desktop Linux daemon 미실행 |
| Backend `gradlew.bat test` | 미실행: 현재 로컬 Java 17, 요구 Java 25 toolchain 없음 |
| Backend `gradlew.bat build` | 미실행: 현재 로컬 Java 17, 요구 Java 25 toolchain 없음 |
| Frontend `npm ci` | 통과, 기존 audit moderate 2건 보고 |
| Frontend `npm run lint` | 통과 |
| Frontend `npm run build` | 통과, Next.js production build |
| 기존 `js-yaml` 기반 workflow 구문·구조 검사 | 통과, 신규 dependency 설치 없음 |
| workflow 필수 항목·기존 step 보존 검사 | 통과 |
| workflow service port 문자열 키 3곳 검사 | 통과, `${{ job.services.mysql.ports['3306'] }}`로 통일 |
| `Write-Output 'OPS-006' \| py -3 scripts\validate-task-artifacts.py --from-stdin` | 통과, `task artifacts validated for OPS-006` |
| `py -3 scripts\validate-task-artifacts.py --task-id OPS-006` | 통과, `task artifacts validated for OPS-006` |
| `git diff --check` | 통과 |
| 변경 경로 allowlist | workflow, runbook, OPS-006 보고서·인수인계만 변경 |
| `git diff --cached --check` | 통과 |
| staged name·stat | 허용 파일 4개만 포함됨을 확인 |
| staged Secret 의심 패턴 검사 | CI 비운영 root fixture와 dynamic localhost JDBC URL만 일치, 실제 Secret 없음 |
| 커밋 메시지 검증 | `ci(database): MySQL 검증 서비스 기반 추가` 통과 |
| PR #32 본문 local·remote UTF-8 검사 | 통과 |
| 리뷰 수정 head `794e53c` Commit and PR conventions | 통과, 최신 Repository Validation run `29142768167` |
| 리뷰 수정 head `794e53c` Application validation | 통과, 최신 Repository Validation run `29142768167` |
| 원격 `Initialize containers`와 `Verify MySQL service` | 통과 |
| 원격 Java 25 Backend test/build | 두 step 모두 통과 |
| 원격 Node.js 24 Frontend install/lint/build | 세 step 모두 통과 |
| 원격 CodeRabbit check | 통과 |

## 실패 후 수정 내용

- OPS-006 기준 validator 실패는 산출물 생성 전 예상 결과이므로 보고서와 인수인계를 작성한 뒤 두 입력 방식으로 재검증한다.
- 첫 산출물 validator 재실행은 실수로 `frontend`에서 실행해 상대 경로의 `scripts`를 찾지 못했다. 저장소 루트에서 같은 명령을 다시 실행해 두 방식 모두 통과했다.
- 로컬 Backend는 현재 Java 17이고 요구 Java 25 toolchain이 없어 다시 실행하지 않았다. 제품 코드나 workflow 결함으로 해석하지 않고 Java 25를 제공하는 원격 Application validation로 보완한다.
- Docker daemon 부재를 우회하려고 Docker Desktop을 자동 시작하거나 새 도구를 설치하지 않았다. 원격 service container를 최종 기동 증거로 사용한다.

## 실행하지 못한 검증과 이유

- 로컬 MySQL 8.4 container health·version·charset·collation 검증: Docker CLI는 있으나 Docker Desktop Linux daemon이 실행 중이 아니어서 불가.
- 로컬 workflow YAML 전용 lint: 저장소와 환경에 actionlint, Ruby 또는 PyYAML이 없으며 새 dependency를 추가하지 않았다.
- 로컬 Backend test/build: Java 25 toolchain이 없고 toolchain download repository가 구성되지 않아 불가.
- 실제 datasource·Flyway: Backend dependency, application 설정과 migration이 아직 없어 검증 대상이 아님.

## QA 필요 여부

- 독립 QA 문서 불필요.
- OPS-006은 CI service와 문서 변경이며 사용자-facing 제품 동작, 인증·인가 구현, DB schema와 credential 저장 동작을 변경하지 않는다.
- 후속 Backend가 실제 datasource·Flyway·Security 동작을 추가하면 QA 독립 검증이 필요하다.

## QA 문서 경로 또는 생략 사유

- QA 문서 경로: 없음.
- 생략 사유: workflow service 기동과 환경 제공은 로컬·원격 CI 검증과 runbook으로 확인하며 실제 애플리케이션 동작은 변경하지 않는다.

## AI 리뷰 반영 여부

- PR #32는 Ready for review이며 CodeRabbit 상세 review thread 5건을 확인했다.
- port 참조 3곳을 문자열 키 `${{ job.services.mysql.ports['3306'] }}`로 통일했다.
- 보고서의 datasource 환경 변수명을 실제 workflow 계약과 일치시켰다.
- Runbook의 배포용 Docker 제외 범위와 CI MySQL service 포함 범위를 분리하고 image pull 진단을 중립적인 registry/network 표현으로 정정했다.
- Codex Review는 별도로 실행하지 않았으며 로컬 구조 검사와 원격 CI 증거로 검증했다.

## MySQL image 리뷰 결정

- 사용자와 Tech Lead가 `mysql:8.4` mutable tag 유지와 현재 단계의 tag drift 위험 수용을 승인했다.
- digest 고정 권고 자체는 재현성과 공급망 무결성 관점에서 유효하지만 현재 CI 단계에서는 구현하지 않는다.
- Runbook과 Backend 인수인계에 실제 version 확인, drift 장애 진단, 재검토 조건과 롤백 경계를 반영한다.
- 관련 CodeRabbit thread에는 승인 결정과 제한을 답변하고 문서 추적성이 확인되면 resolve한다.

## 적용 방법

- PR에서 Application validation을 실행하면 GitHub Actions가 MySQL service를 자동으로 생성한다.
- Backend Engineer는 승인된 application-test 설정에서 `SPRING_DATASOURCE_*`를 소비하고 같은 test/build step으로 connection과 Flyway를 검증한다.
- 실패 진단은 `docs/runbook/FOUNDATION-002-ci-validation.md`를 따른다.

## 롤백

- Backend가 MySQL 환경을 아직 소비하지 않는 동안 문제가 생기면 workflow의 service, verification step과 세 Backend 환경 변수를 한 PR에서 함께 제거한다.
- runbook의 MySQL 절차도 이전 기준선으로 되돌리고 Java 25·Node 24 검증을 재확인한다.
- Backend가 MySQL을 소비하기 시작한 뒤에는 대체 datasource 검증 경로 승인 없이 service만 제거하지 않는다.

## 위험과 제한

- `mysql:8.4` tag는 patch 또는 digest 고정이 아니므로 upstream 갱신에 따라 실행 image가 바뀔 수 있다.
- GitHub-hosted runner의 image pull 또는 Docker service 장애가 애플리케이션과 무관하게 CI를 막을 수 있다.
- 고정 비운영 credential도 공개 저장소에 보이므로 production에서 재사용하지 않는 운영 규율이 필요하다.
- 현재 Backend가 환경 변수를 소비하지 않아 Application validation 성공은 DB connection 성공을 의미하지 않는다.

## 남은 위험

- 실제 JDBC option과 timezone·TLS 정책은 Backend datasource 결정에 남아 있다.
- Flyway migration 실패와 JPA mapping 문제는 후속 구현에서 처음 드러날 수 있다.
- MySQL image 초기화 시간이 CI 비용과 대기 시간을 늘릴 수 있다.
- mutable tag drift는 승인된 제한이며, 예상하지 않은 변경이 확인되면 digest 고정을 Tech Lead 결정 항목으로 다시 올린다.

## 다음 작업

1. Backend Engineer가 JPA, MySQL JDBC, Flyway와 application-test 설정을 승인 범위 안에서 구현한다.
2. 빈 MySQL에 V1 migration 적용과 재실행을 검증한다.
3. JPA mapping과 credential test fixture를 격리된 CI database에서 검증한다.
4. 실제 인증·인가와 credential 동작은 QA가 독립 검증한다.

## Git 결과

- 기준 commit: `de9e971 docs(obsidian): PR #31 기록 추가`
- 기존 로컬 `ops/sre`는 `backup/local-ops-sre-before-OPS-006`으로 보존
- 병합 완료 원격 `ops/sre` 삭제 후 최신 `main`에서 새 역할 branch 생성
- 리뷰 수정 시작 전 로컬 `ops/sre` SHA `d250bcd6bf8f8e3fc645b9af09b442268865a76a`를 `backup/local-ops-sre-before-OPS-006-review-fix`에 보존했다.
- 로컬 고유 커밋은 `d250bcd chore(harness): 역할과 산출물 검증 강화` 1개이며 자동 재적용하지 않았다.
- 사용자 승인 범위에서 로컬 `ops/sre`만 `origin/ops/sre`로 reset했고 reset 후 SHA는 `eec524b4aa21bca827d7968e9bdc42bbf91e119f`로 일치했다.
- 백업 브랜치는 삭제하거나 원격 push하지 않았고 rebase·force push는 사용하지 않았다.
- 구현 commit: `8fee031e95f54268e5f703fc9ff78e859db6f8f6`
- commit 제목: `ci(database): MySQL 검증 서비스 기반 추가`
- `origin/ops/sre` push와 upstream 설정 성공
- 리뷰 수정 commit: `794e53c19a7708ff07400089a49ec4d7b239d2eb`
- 리뷰 수정 commit 제목: `fix(ci): MySQL 검증 리뷰와 문서 정합성 보완`
- `origin/ops/sre`에서 직접 이어진 commit만 force 옵션 없이 일반 push 성공

## PR 결과

- PR: #32 `ci(database): MySQL 검증 서비스 기반 추가`
- URL: `https://github.com/guseoh/pawcycle-commerce/pull/32`
- base/head: `main` ← `ops/sre`
- 상태: OPEN, Ready for review
- 검증된 리뷰 수정 head: `794e53c19a7708ff07400089a49ec4d7b239d2eb`
- 최신 Repository Validation run `29142768167`의 Commit and PR conventions와 Application validation 통과
- 같은 Application validation에서 container 초기화, MySQL 검증, Backend test/build와 Frontend install/lint/build 통과
- CodeRabbit check 통과
- CodeRabbit 상세 review thread 5건에 한국어 답변 완료
- port 문자열 키, datasource 환경 변수명, Docker 범위, image pull 진단 4건 resolve
- digest 고정 1건은 mutable tag 유지 승인 결정 반영 전 unresolved 상태
- 원격 PR 본문 UTF-8 검증 통과
- 자동 병합하지 않음
