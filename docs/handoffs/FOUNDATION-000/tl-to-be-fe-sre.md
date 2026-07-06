# FOUNDATION-000 Tech Lead 인수인계

## 목적

FOUNDATION-001 애플리케이션 기반 작업 전에 Backend, Frontend, Platform/SRE가 같은 기술 버전 제안 위에서 시작할 수 있도록 입력을 전달한다.

Status: Proposed

## Backend Engineer 입력

- Java는 25 LTS를 우선 기준으로 둔다.
- Spring Boot는 4.1.x 최신 patch를 우선 기준으로 둔다.
- Gradle은 9.x를 우선 기준으로 둔다.
- Backend 테스트 baseline은 `spring-boot-starter-test`로 시작한다.
- MySQL target은 8.4 LTS다.
- Migration tool은 Flyway를 우선 검토한다.
- Spring Boot 프로젝트, Gradle wrapper, JPA Entity, Repository, Service, Controller, Flyway migration은 FOUNDATION-000에서 만들지 않았다.

## Frontend Engineer 입력

- Node.js는 24 LTS를 우선 기준으로 둔다.
- Next.js는 16.x 최신 patch를 우선 기준으로 둔다.
- TypeScript는 6.0.x를 우선 기준으로 둔다.
- Frontend package manager는 npm을 우선 기준으로 둔다.
- Frontend 검증 baseline 후보는 ESLint CLI, TypeScript type check, `next build`다.
- Next.js 프로젝트, package.json, 라우트, 화면, 상태 관리는 FOUNDATION-000에서 만들지 않았다.

## Platform/SRE 입력

- Local development database는 Docker Compose 없이 로컬 MySQL 8.4 LTS 수동 실행을 우선 기준으로 둔다.
- CI는 프로젝트 생성 전까지 문서/커밋 검증을 유지한다.
- 프로젝트 생성 후 CI 후보는 Backend `./gradlew test`, Frontend `npm ci`, `npm run lint`, `npm run typecheck`, `npm run build`다.
- Docker Compose, GitHub Actions, 배포 설정, Secret, 성능 튜닝은 FOUNDATION-000에서 만들지 않았다.

## FOUNDATION-001에서 지켜야 할 기술 버전

| 영역 | 기준 |
| --- | --- |
| Java | 25 LTS |
| Spring Boot | 4.1.x |
| Gradle | 9.x |
| Backend test | `spring-boot-starter-test` |
| Node.js | 24 LTS |
| Next.js | 16.x |
| TypeScript | 6.0.x |
| Package manager | npm |
| MySQL | 8.4 LTS |
| Migration | Flyway 후보 |

## 아직 확정하지 않은 항목

- 배포 플랫폼
- Docker Compose 도입 여부
- GitHub Actions 상세 workflow
- Redis, Kafka, 검색 엔진, 캐시, 메시징 전략
- 결제 외부 연동
- Spring Security 세션, 토큰, 쿠키, CSRF 구현 방식
- JPA mapping, API URL, HTTP 상태, 오류 응답 JSON 최종 승인
- Testcontainers, E2E 테스트, 브라우저 테스트 도입 여부

## 구현 시작 전 중단 조건

- Java 25 LTS, Spring Boot 4.1.x, Gradle 9.x 조합이 실제 scaffold에서 호환되지 않는 경우
- Node.js 24 LTS, Next.js 16.x, TypeScript 6.0.x 조합이 실제 scaffold 또는 build에서 호환되지 않는 경우
- MySQL 8.4 LTS와 JDBC/Flyway 조합 확인 없이 DB migration을 만들 필요가 생긴 경우
- DATA-001, API-001, AUTH-001 Proposed 문서를 Approved 입력처럼 사용해야 하는 경우
- 신규 의존성, Docker Compose, GitHub Actions, 배포 설정, Secret 추가가 필요해지는 경우
- reset, force push, rebase, history rewrite가 필요해지는 경우

## 필요한 검증 명령 후보

```bash
java --version
node --version
npm --version
./gradlew test
npm ci
npm run lint
npm run typecheck
npm run build
```

위 명령 중 `./gradlew`와 `npm` script는 FOUNDATION-001에서 실제 프로젝트를 생성한 뒤 사용할 수 있다.
