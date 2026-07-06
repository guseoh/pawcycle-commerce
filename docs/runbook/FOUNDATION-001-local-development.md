# FOUNDATION-001 로컬 개발 런북

## 목적

FOUNDATION-001에서 생성한 최소 Backend와 Frontend 애플리케이션 기반을 로컬에서 설치, 검증, 빌드하는 절차를 정리한다.

이번 런북은 애플리케이션 기반 검증용이다. 도메인 기능, API, 인증, DB schema, Flyway migration, Docker 실행 절차는 포함하지 않는다.

## 필요한 로컬 도구

| 영역 | 기준 |
| --- | --- |
| Java | 25 LTS |
| Gradle | `backend/gradlew` wrapper 사용, Gradle 9.5.1 |
| Node.js | 24 LTS |
| npm | Node.js 24와 함께 사용하는 npm |
| Git | 저장소 clone, branch, PR 작업 |

버전 확인:

```bash
java -version
node --version
npm --version
```

Windows PowerShell에서 특정 JDK를 사용할 때:

```powershell
$env:JAVA_HOME = '<JDK 25 설치 경로>'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

## Backend 검증

위치:

```text
backend/
```

명령:

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

현재 Backend는 Spring Boot 애플리케이션 기본 진입점과 context load 테스트만 포함한다. Controller, Service, Repository, Entity, DTO, SecurityConfig, Flyway migration은 아직 만들지 않았다.

## Frontend 검증

위치:

```text
frontend/
```

명령:

```bash
cd frontend
npm ci
npm run lint
npm run build
cd ..
```

`npm run typecheck` 스크립트는 이번 scaffold에 추가하지 않았다. TypeScript 검사는 `next build`에서 함께 수행된다.

현재 Frontend는 Next.js App Router 기반 최소 앱이다. 상품 화면, 구독 화면, 마이페이지, API client, 인증 UI, UI 라이브러리, 상태관리 라이브러리는 아직 만들지 않았다.

## Docker와 DB

Docker는 이번 작업에서 사용하지 않는다. `Dockerfile`, `docker-compose.yml`, Kubernetes, Nginx 설정은 없다.

MySQL 8.4 LTS는 후속 작업의 대상 DB로만 문서화되어 있다. 이번 작업에서는 실제 MySQL 연결, schema 생성, Flyway migration, JDBC driver, DB secret을 추가하지 않았다.

## 실패 시 확인 항목

- `java -version`이 Java 25 LTS인지 확인한다.
- `backend/gradle/wrapper/gradle-wrapper.properties`의 Gradle 배포판이 9.1 이상 9.x인지 확인한다.
- `node --version`이 Node.js 24 LTS인지 확인한다.
- `frontend/package-lock.json`이 변경된 뒤에는 `npm install` 대신 `npm ci`로 재현 설치를 확인한다.
- Frontend build에서 workspace root 경고가 다시 나타나면 `frontend/next.config.ts`의 `turbopack.root` 설정을 확인한다.
- DB 연결 오류가 발생한다면 이번 범위가 아닌 설정이나 의존성이 추가됐는지 먼저 확인한다.
