# OPS-006 Platform/SRE → Backend Engineer 인수인계

## 전달 목적

후속 Backend의 JPA·Flyway·Spring Security 구현이 GitHub Actions의 MySQL 8.4 service를 사용해 실제 datasource와 migration을 검증하도록 CI 연결 지점과 역할 경계를 전달한다.

## 대상 역할

- 수신: Backend Engineer
- 후속 협업: Platform/SRE, QA Engineer, Tech Lead

## 입력 문서

- `AGENTS.md`
- `backend/AGENTS.md`
- `docs/roles/backend-engineer.md`
- `.agents/skills/backend-engineer/SKILL.md`
- `docs/adr/ARCH-006-first-backend-implementation-approved-inputs.md`
- `docs/adr/AUTH-003-session-authentication-approved-inputs.md`
- `docs/handoffs/AUTH-003/tl-to-be.md`
- `.github/workflows/validate-conventions.yml`
- `docs/runbook/FOUNDATION-002-ci-validation.md`
- `docs/reports/OPS-006/sre-report.md`

## 완료된 작업

- Application validation에 `mysql:8.4` service를 추가했다.
- dynamic host port와 Docker health check를 설정했다.
- MySQL 8.4, `utf8mb4`, `utf8mb4_0900_ai_ci` 검증 step을 Backend보다 앞에 추가했다.
- Backend test/build에 datasource 환경 변수 세 개를 제공했다.
- CI MySQL 실패 진단과 롤백 절차를 기존 FOUNDATION-002 runbook에 추가했다.

## 사용 가능한 결과

- CI MySQL service image: `mysql:8.4`
- CI database: `pawcycle_test`
- application user: CI 전용 `pawcycle_ci`
- host: GitHub-hosted runner의 `127.0.0.1`
- host port: `${{ job.services.mysql.ports['3306'] }}` dynamic mapping
- service health와 server version·character set·collation 선행 검증
- Backend test/build에 전달되는 datasource 환경 변수

## 관련 파일

- `.github/workflows/validate-conventions.yml`
- `docs/runbook/FOUNDATION-002-ci-validation.md`
- `docs/reports/OPS-006/sre-report.md`
- `docs/handoffs/OPS-006/sre-to-be.md`

## 인수 조건과 추적성

| Backend 필요 조건 | OPS-006 결과 |
| --- | --- |
| MySQL 8.4 LTS | `mysql:8.4` service |
| 빈 test database | `pawcycle_test` |
| service readiness | Docker health check와 `Verify MySQL service` |
| 충돌 안전 host port | GitHub Actions dynamic mapping |
| datasource URL | `127.0.0.1:<dynamic-port>/pawcycle_test` |
| credential 경계 | job 전용 비운영 값, Secret 신규 요구 없음 |
| existing validation | Java 25 Backend와 Node 24 Frontend 검사 유지 |

## 확정된 결정

- CI image는 MySQL 8.4 LTS 계열의 `mysql:8.4`다.
- OPS-006 후속 요청에서 사용자와 Tech Lead가 CI 검증용 `mysql:8.4` mutable tag 유지를 승인했다.
- 같은 tag가 다른 8.4 patch 또는 내부 image 내용을 가리킬 수 있는 tag drift 위험을 현재 단계에서 수용한다.
- `Verify MySQL service`의 실제 server version `8.4.*` 검증을 유지한다.
- 이 결정은 CI 검증에만 적용하며 운영 환경의 image 정책으로 확대하지 않는다.
- database 이름은 `pawcycle_test`다.
- host는 runner의 `127.0.0.1`, host port는 GitHub Actions dynamic mapping을 사용한다.
- server character set·collation target은 `utf8mb4`와 `utf8mb4_0900_ai_ci`다.
- Testcontainers와 Docker Compose를 사용하지 않는다.

## 미확정 결정

- JDBC URL option
- Backend application-test profile의 파일·property 구성
- Flyway V1 schema와 migration 내용
- JPA mapping과 transaction 경계

digest 고정은 현재 범위에서 제외한다. 재현 가능한 릴리스 검증, 공급망 보안 강화 또는 예상하지 않은 tag drift가 확인되면 공식 digest와 갱신·검증 절차를 확인해 Tech Lead 결정 항목으로 다시 올린다.

## 승인 필요 항목

- 위 미확정 항목이 ARCH-006·AUTH-003 범위를 바꾸면 Backend가 임의 결정하지 않고 사용자 또는 Tech Lead에게 요청한다.
- CI workflow 변경이 추가로 필요하면 Backend가 `.github/**`를 직접 수정하지 않고 Platform/SRE에게 요청한다.

## 다음 역할의 입력

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `docs/runbook/FOUNDATION-002-ci-validation.md`
- OPS-006 PR의 Application validation 결과

## Backend application-test 연결 지점

- test profile은 workflow가 제공하는 세 `SPRING_DATASOURCE_*` 환경 변수를 소비해야 한다.
- URL host와 port를 고정하지 않는다. CI의 `<dynamic-port>`는 workflow expression이 완성한다.
- application-test 설정에 실제 credential, production fallback 또는 공용 seed 회원을 넣지 않는다.
- 필요한 JDBC option은 근거와 테스트를 포함해 Backend 구현에서 제안한다.

## 현재 OPS-006에서 검증한 범위

- MySQL service container 초기화와 Docker health check
- `Verify MySQL service`의 실제 server version `8.4.*`, `utf8mb4`, `utf8mb4_0900_ai_ci` 확인
- GitHub Actions dynamic host port 할당과 문자열 port key 참조 확인
- Backend test/build step에 세 `SPRING_DATASOURCE_*` 환경 변수 전달 정의
- Java 25 Backend test/build와 Node.js 24 Frontend install/lint/build step 실행 성공

현재 Backend가 datasource 환경 변수를 소비하지 않으므로 Backend test/build 성공은 실제 MySQL connection, Flyway 또는 JPA 검증 성공을 뜻하지 않는다.

## 후속 Backend 구현 후 검증할 범위

1. test profile이 세 `SPRING_DATASOURCE_*` 환경 변수를 실제로 소비한다.
2. Backend가 `pawcycle_test` datasource에 연결한다.
3. 빈 database에 fresh Flyway migration을 적용하고 재실행 오류가 없는지 확인한다.
4. JPA mapping과 MySQL schema 정합성을 검증한다.
5. 세션 인증 관련 Backend 통합 테스트를 같은 CI MySQL 경로에서 실행한다.

## 지켜야 할 규칙

- Backend 작업에서 `.github/**`를 변경하지 않는다.
- `mysql:8.4`, database와 dynamic port 계약을 임의로 바꾸지 않는다.
- 실제 Secret, production database 이름과 credential을 fixture·문서·로그에 넣지 않는다.
- V1 migration에 production 공용 credential row를 넣지 않는다.
- Testcontainers, Docker Compose, JWT, OAuth2와 구독 API를 추가하지 않는다.
- Application validation 성공만으로 datasource·Flyway 검증 성공을 선언하지 않는다.

## 적용·실행 방법

Backend 구현 PR에서 별도 workflow 변경 없이 기존 Application validation을 실행한다. 연결 또는 migration 실패는 `docs/runbook/FOUNDATION-002-ci-validation.md`의 확인 순서를 따른다.

## 다음 역할의 검증 포인트

- `SPRING_DATASOURCE_*`가 test profile에서 실제 소비되는가?
- 빈 `pawcycle_test` database에 V1이 한 번 적용되는가?
- 애플리케이션 재시작 또는 별도 test에서 migration 재적용 오류가 없는가?
- JPA mapping이 MySQL schema와 일치하는가?
- `members.email`과 `password_hash`가 AUTH-003 물리 계약과 일치하는가?
- production profile에서 test fixture와 local bootstrap이 실행되지 않는가?
- Backend test/build가 MySQL service 이후 Java 25에서 통과하는가?
- password, connection string, hash와 session 정보가 로그에 없는가?

## QA 필요 여부

- OPS-006 자체는 독립 QA 문서가 필요하지 않다.
- 실제 datasource·migration·credential·Security 동작을 구현하는 Backend 작업은 QA 독립 검증이 필요하다.

## AI 리뷰에서 남은 확인 항목

- PR #32의 CodeRabbit 상세 review thread 5건을 확인했다.
- port 문자열 키, 보고서 환경 변수명, Runbook Docker 범위와 image pull 진단 4건은 OPS-006에서 반영한다.
- MySQL image digest 고정 1건은 승인된 mutable tag 유지 결정, tag drift 위험 수용과 digest 재검토 조건을 문서화하고 답변해 resolve했다.
- health check, dynamic port, Secret 경계, action pinning과 기존 step 회귀는 최신 head의 검증 결과로 확인한다.

## 알려진 위험

- `mysql:8.4` tag가 다른 patch 또는 내부 image 내용을 가리킬 수 있는 tag drift
- GitHub-hosted runner의 image pull과 Docker service 장애
- MySQL 초기화에 따른 CI 시간 증가
- 현재 Backend가 환경 변수를 소비하지 않아 connection이 아직 검증되지 않음

## 남은 위험과 주의 사항

- JDBC option, timezone과 TLS는 후속 Backend 결정이다.
- Flyway와 JPA 실패는 service 기동 성공 이후 별도 애플리케이션 문제로 진단해야 한다.
- CI 비운영 credential을 로컬 공용 또는 production에서 재사용하면 안 된다.

## 롤백

- Backend가 datasource 환경을 소비하기 전에는 SRE가 service, verification step과 Backend 환경 변수를 함께 제거하고 기존 Java·Frontend 검증을 재확인할 수 있다.
- Backend가 MySQL을 소비한 뒤에는 대체 검증 경로 승인 없이 service만 제거하지 않는다.

## 다음 권장 작업

1. Backend가 승인된 JPA·MySQL JDBC·Flyway dependency와 test profile을 구현한다.
2. V1 migration, Entity·Repository와 test fixture를 작성한다.
3. 같은 Application validation에서 실제 connection·migration·mapping을 검증한다.
4. QA가 인증·credential·migration 동작을 독립 검증한다.

## 후속 Backend 작업 완료 조건

- Backend test profile이 CI 환경 변수를 소비
- fresh MySQL datasource 연결과 Flyway 적용 검증
- Java 25 Backend test/build 통과
- 실제 Secret과 production seed 부재
- QA 필요 여부와 인수인계 기록

## SRE로 되돌리는 조건

- MySQL service가 `Initialize containers`에서 healthy가 되지 않음
- `mysql:8.4` image pull 또는 dynamic port 할당 실패
- `Verify MySQL service`의 version·charset·collation 검사가 실패함
- workflow expression이나 GitHub-hosted runner 환경 때문에 Backend step 전에 실패함
- `.github/**` 변경이 추가로 필요함

## 중단 조건

- ARCH-006 또는 AUTH-003과 충돌하는 schema·credential 정책이 필요함
- 실제 Secret이나 production database 접근이 필요함
- Testcontainers, Docker Compose 또는 승인 밖 dependency가 필요함
- Backend가 `.github/**`를 직접 변경해야 함
- Java 25 test/build, Flyway와 Security 필수 검증이 실패함
- reset, rebase, force push 또는 history rewrite가 필요함
