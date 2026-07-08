# FOUNDATION-002 CI 검증 런북

## 목적

Repository Validation workflow에서 협업 하네스 검증과 함께 Backend와 Frontend의 최소 애플리케이션 검증을 실행한다.

이번 CI 기준선은 FOUNDATION-001 scaffold가 Pull Request마다 깨지지 않는지 확인하기 위한 것이다. 도메인 기능, API, 인증, DB schema, Docker, 배포 검증은 포함하지 않는다.

## PR 검증 목록

Repository Validation은 다음을 확인한다.

- PR 제목 커밋 메시지 규칙
- PR 본문 UTF-8 인코딩
- 작업 보고서와 인수인계 산출물 존재
- PR에 포함된 커밋 메시지 규칙
- whitespace 오류
- Backend test/build
- Frontend install/lint/build

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

Docker는 아직 CI에 포함하지 않는다. `Dockerfile`, `docker-compose.yml`, Kubernetes, Nginx, 배포 workflow는 이번 기준선에 없다.

MySQL도 아직 CI에 포함하지 않는다. service container, schema 생성, Flyway migration, JPA 통합 테스트, DB secret은 후속 DATA/Backend/SRE 작업에서 별도로 결정한다.

## 실패 시 확인 순서

1. PR 제목, 본문, 작업 산출물, 커밋 메시지 오류인지 먼저 확인한다.
2. `git diff --check` 실패라면 공백 또는 줄 끝 오류를 수정한다.
3. Backend 실패라면 Java 25 설치, `backend/gradlew` 실행 권한, Gradle wrapper, `./gradlew test`, `./gradlew build` 로그를 확인한다.
4. Frontend 실패라면 Node.js 24 설치, `frontend/package-lock.json`, `npm ci`, `npm run lint`, `npm run build` 로그를 확인한다.
5. workflow 범위만으로 해결할 수 없는 Backend/Frontend 코드 또는 의존성 문제가 보이면 해당 역할 작업으로 넘기고 SRE 작업에서는 중단한다.
6. Docker, DB, Secret이 필요해지는 경우 이번 FOUNDATION-002 범위를 넘어서므로 중단한다.
