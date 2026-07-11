# FOUNDATION-002 CI 검증 런북

## 목적

Repository Validation workflow에서 협업 하네스 검증과 함께 Backend와 Frontend의 최소 애플리케이션 검증을 실행한다.

이번 CI 기준선은 FOUNDATION-001 scaffold가 Pull Request마다 깨지지 않는지 확인하고, 후속 Backend의 JPA·Flyway·Spring Security 구현이 사용할 MySQL service 기반을 제공하기 위한 것이다. 도메인 기능, API, 인증, DB schema, datasource 연결, Flyway migration과 배포 검증은 포함하지 않는다.

## PR 검증 목록

Repository Validation은 다음을 확인한다.

- PR 제목 커밋 메시지 규칙
- PR 본문 UTF-8 인코딩
- 작업 보고서와 인수인계 산출물 존재
- PR에 포함된 커밋 메시지 규칙
- whitespace 오류
- MySQL 8.4 service 기동과 health check
- MySQL server의 `utf8mb4`와 `utf8mb4_0900_ai_ci` 기본값
- Backend test/build
- Frontend install/lint/build

## CI MySQL service

Application validation의 `application` job은 다음 CI 전용 MySQL service를 시작한다.

| 항목 | 값 |
| --- | --- |
| image | `mysql:8.4` |
| database | `pawcycle_test` |
| application user | `pawcycle_ci` |
| host | GitHub-hosted runner의 `127.0.0.1` |
| container port | `3306/tcp` |
| host port | GitHub Actions가 job마다 동적으로 할당 |
| character set | `utf8mb4` |
| collation | `utf8mb4_0900_ai_ci` |

`mysql:8.4` mutable tag 유지는 OPS-006 후속 요청에서 사용자와 Tech Lead가 승인한 CI 검증 정책이다. 이 tag는 향후 다른 MySQL 8.4 patch 버전이나 내부 image 내용을 가리킬 수 있으며, 현재 단계에서는 이 tag drift 위험을 수용한다. `Verify MySQL service`는 실제 server version이 `8.4.*`인지 확인하지만 image 내부 패키지 전체의 동일성까지 보장하지 않는다. 이 결정은 CI 검증용 image에만 적용하며 운영 환경의 MySQL image 정책을 확정하지 않는다.

재현 가능한 릴리스 검증이나 공급망 보안 강화가 필요해지거나 예상하지 않은 tag 변경이 확인되면 digest 고정을 Tech Lead 결정 항목으로 다시 올린다. 그때는 공식 image의 실제 digest, 갱신 주기와 검증 절차를 확인한 뒤 별도 SRE 작업으로 반영하며 digest를 추측하거나 검증 없이 고정하지 않는다.

service는 다음 Docker health check를 통과해야 job step이 시작된다.

```text
mysqladmin ping --host=127.0.0.1 --silent
interval: 10s
timeout: 5s
retries: 10
start period: 20s
```

Application validation은 첫 step에서 service container 내부의 MySQL client를 사용해 8.4 계열, `utf8mb4`, `utf8mb4_0900_ai_ci`를 확인한다. 비밀번호와 전체 connection string은 로그에 출력하지 않는다.

CI database와 credential은 GitHub Actions job이 끝나면 폐기되는 명백한 비운영 테스트 값이다. Repository 또는 Environment Secret을 요구하지 않으며 production database 이름이나 credential로 재사용하면 안 된다.

## Backend CI database 환경 변수

Backend test와 build step에는 다음 환경 변수를 제공한다.

```text
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:<dynamic-port>/pawcycle_test
SPRING_DATASOURCE_USERNAME=<CI 전용 사용자>
SPRING_DATASOURCE_PASSWORD=<CI 전용 비운영 값>
```

`<dynamic-port>`는 `${{ job.services.mysql.ports['3306'] }}`에서 가져온다. JDBC option은 이 SRE 작업에서 추가로 고정하지 않는다.

현재 Backend에 JPA, MySQL JDBC와 Flyway가 없으면 이 환경 변수는 소비되지 않는다. 따라서 service 기동과 변수 전달 성공을 datasource 연결, migration 또는 schema 검증 성공으로 해석하지 않는다. 후속 Backend 작업이 승인된 dependency와 application-test 설정을 추가한 뒤 같은 CI 경로에서 실제 연결과 migration을 검증한다.

## Backend CI 명령

GitHub Actions는 Ubuntu runner에서 Java 25 Temurin을 설정한 뒤 다음 명령을 실행한다.

```bash
cd backend
chmod +x ./gradlew
./gradlew test
./gradlew build
```

## Frontend CI 명령

GitHub Actions는 Node.js 24를 설정하고 `frontend/package-lock.json` 기준 npm cache를 사용한 뒤 다음 명령을 실행한다.

```bash
cd frontend
npm ci
npm run lint
npm run build
```

## 로컬 재현

Backend:

```bash
cd backend
./gradlew test
./gradlew build
cd ..
```

Windows PowerShell:

```powershell
cd backend
.\gradlew.bat test
.\gradlew.bat build
cd ..
```

Frontend:

```bash
cd frontend
npm ci
npm run lint
npm run build
cd ..
```

## 제외 범위

배포용 Dockerfile, Docker Compose, Kubernetes, Nginx와 배포 workflow는 이번 기준선에 포함하지 않는다. CI 검증용 MySQL service container는 포함한다.

MySQL service container는 CI에 포함하지만 schema 생성, Flyway migration, JPA 통합 테스트와 실제 DB Secret은 포함하지 않는다. Testcontainers와 Docker Compose도 Deferred 또는 Explicitly Excluded 상태를 유지한다.

## 실패 시 확인 순서

1. PR 제목, 본문, 작업 산출물, 커밋 메시지 오류인지 먼저 확인한다.
2. `git diff --check` 실패라면 공백 또는 줄 끝 오류를 수정한다.
3. Application validation이 step 진입 전에 실패하면 job의 `Initialize containers`와 MySQL service 로그에서 image pull, container 시작과 health check 상태를 확인한다.
4. image pull 실패라면 workflow 변경 없이 재실행만 반복하지 않는다. `mysql:8.4` tag 가용성, 실제 pull 결과, container registry 또는 network 오류와 Docker pull 오류를 확인한다.
5. `Verify MySQL service` 실패라면 dynamic host port가 할당됐는지, database 이름이 `pawcycle_test`인지, 실제 server version·character set·collation 검사가 실패했는지 확인한다. password나 전체 connection string을 출력하지 않는다.
6. mutable tag의 예상하지 않은 변경이 의심되면 마지막 정상 run의 MySQL version, image pull·health check, character set과 collation 결과와 비교한다. 차이가 확인되면 반복 재실행으로 우회하지 않고 digest 고정 필요성을 Tech Lead 결정 항목으로 다시 올린다.
7. Backend datasource 연결 실패라면 실패 step이 service 확인 이후인지 확인하고, URL host가 `127.0.0.1`, port가 `${{ job.services.mysql.ports['3306'] }}`, database가 `pawcycle_test`인지 workflow 정의로 확인한다. 환경 변수 실제 값 전체를 로그에 출력하지 않는다.
8. Flyway 실패는 service 기동 성공 후 Backend test/build 로그의 migration 단계에서 발생한다. `Initialize containers` 또는 health 실패와 구분하고 migration·schema 문제는 Backend Engineer에게 전달한다.
9. Backend 실패라면 Java 25 설치, `backend/gradlew` 실행 권한, Gradle wrapper, `./gradlew test`, `./gradlew build` 로그를 확인한다.
10. Frontend 실패라면 Node.js 24 설치, `frontend/package-lock.json`, `npm ci`, `npm run lint`, `npm run build` 로그를 확인한다.
11. workflow 범위만으로 해결할 수 없는 Backend/Frontend 코드 또는 의존성 문제가 보이면 해당 역할 작업으로 넘기고 SRE 작업에서는 중단한다.

## 로컬 MySQL과 CI MySQL의 차이

- CI는 Ubuntu GitHub-hosted runner와 service container를 사용하고 host port를 동적으로 할당한다.
- 로컬 MySQL은 설치 방식, port, 계정과 데이터 수명이 개발자 환경에 따라 다르다.
- CI database와 credential은 job 전용 비운영 값이며 로컬 또는 production 값과 공유하지 않는다.
- 로컬 재현에서 고정 `3306`을 사용하더라도 CI URL에 고정 host port를 가정하지 않는다.

## 롤백

OPS-006 변경 때문에 MySQL service 초기화가 기존 Application validation을 막으면 다음 순서로 롤백한다.

1. tag drift가 의심되면 마지막 정상 run과 실제 version, pull, health, character set·collation 결과를 먼저 비교하고 digest를 추측해 임시 고정하지 않는다.
2. 검증된 digest 고정이 필요하면 Tech Lead 승인을 받아 별도 SRE 변경으로 처리한다.
3. 새 Backend 구현이 아직 MySQL 환경을 소비하지 않고 CI service 자체를 되돌려야 하면 `.github/workflows/validate-conventions.yml`의 `services.mysql`, `Verify MySQL service`와 Backend step의 세 datasource 환경 변수를 함께 제거한다.
4. 이 런북의 CI MySQL service·환경 변수·진단 절차를 이전 기준선으로 되돌린다.
5. Java 25 Backend test/build와 Node.js 24 Frontend 검증이 다시 통과하는지 확인한다.

MySQL을 실제로 소비하는 Backend 변경이 병합된 뒤에는 service만 제거하지 않는다. Backend와 SRE가 datasource 검증 대체 경로를 승인한 뒤 별도 PR로 변경한다.
