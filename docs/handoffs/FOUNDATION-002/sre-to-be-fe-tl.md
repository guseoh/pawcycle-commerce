# FOUNDATION-002 Platform/SRE 인수인계

## 목적

Repository Validation에 Backend와 Frontend 최소 검증이 연결된 뒤 후속 역할이 따라야 할 CI 기준을 정리한다.

## Backend Engineer에게 넘길 CI 기준

- CI는 Java 25 Temurin을 사용한다.
- CI는 `backend/gradlew`를 실행 가능한 상태로 만든 뒤 `./gradlew test`와 `./gradlew build`를 실행한다.
- Backend 변경은 최소한 두 명령을 로컬에서 통과시킨 뒤 PR로 올린다.
- DB, JPA, Flyway, Security가 추가되는 후속 작업에서는 CI 검증 범위 확대가 필요한지 SRE 또는 TL에게 넘긴다.

## Frontend Engineer에게 넘길 CI 기준

- CI는 Node.js 24를 사용한다.
- CI는 npm과 `frontend/package-lock.json`을 기준으로 `npm ci`를 실행한다.
- CI는 `npm run lint`와 `npm run build`를 실행한다.
- Frontend 변경은 최소한 세 명령을 로컬에서 통과시킨 뒤 PR로 올린다.
- typecheck 전용 script, 테스트 도구, UI 테스트가 필요하면 후속 FE/SRE 작업에서 별도 결정한다.

## Tech Lead에게 넘길 확인 사항

- Repository Validation은 기존 PR 제목, PR 본문, 작업 산출물, 커밋 메시지, whitespace 검증을 유지한다.
- 애플리케이션 검증은 기존 하네스 검증 뒤에 실행된다.
- Docker, DB, 배포, 성능 검증은 아직 연결하지 않았다.
- Backend/Frontend 코드나 의존성을 바꾸지 않고 workflow와 문서만 변경했다.

## 후속 작업에서 CI에 추가할 후보

- Backend 단위/통합 테스트 범위 확대
- Frontend typecheck script 추가 후 CI 연결
- 승인된 테스트 도구 도입 후 FE 테스트 연결
- DATA/Backend 작업 이후 MySQL/Flyway 검증 연결
- 승인 후 Docker 기반 로컬/CI 실행 검증
- 배포 workflow와 운영 알림 세분화

## 아직 CI에 넣지 않은 것

- Docker build
- Docker Compose
- MySQL service container
- Flyway migration
- JPA 통합 테스트
- Spring Security 인증/인가 테스트
- API contract 검증
- Frontend E2E 또는 component test
- 배포 workflow
- 성능 테스트

## 중단 조건

- Java 25 설치가 GitHub Actions에서 실패하고 workflow 범위만으로 해결되지 않는 경우
- Node.js 24 설치가 GitHub Actions에서 실패하고 workflow 범위만으로 해결되지 않는 경우
- Backend test/build 실패가 Backend 코드 또는 의존성 수정 없이는 해결되지 않는 경우
- Frontend install/lint/build 실패가 Frontend 코드 또는 의존성 수정 없이는 해결되지 않는 경우
- Docker, DB service container, Secret, 배포 설정이 필요해지는 경우
- reset, rebase, force push, history rewrite가 필요한 Git 상태가 되는 경우
