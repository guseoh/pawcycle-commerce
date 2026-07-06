# FOUNDATION-001 Tech Lead 인수인계

## 목적

Backend, Frontend, Platform/SRE 후속 작업이 동일한 최소 애플리케이션 기반과 로컬 검증 기준에서 시작할 수 있도록 전달 사항을 정리한다.

## Backend Engineer 입력

- Backend 위치: `backend/`
- Spring Boot: `4.1.0`
- Java toolchain: `25`
- Gradle wrapper: `9.5.1`
- 기본 검증: `.\gradlew.bat test`, `.\gradlew.bat build`
- 현재 포함된 애플리케이션 코드는 Spring Boot 진입점과 context load 테스트뿐이다.
- DB, JPA, Flyway, Security 의존성은 아직 추가하지 않았다.

후속 후보:

- DATA 또는 Backend 구현 작업에서 MySQL 연결, JPA, Flyway migration 추가 여부 결정
- API 작업에서 Controller, request/response DTO, 오류 응답 구현
- AUTH 작업에서 Spring Security와 인증/인가 구현

## Frontend Engineer 입력

- Frontend 위치: `frontend/`
- Node.js 기준: 24 LTS
- Next.js: `16.2.10`
- TypeScript: `6.0.3`
- React: `19.2.4`
- package manager: npm
- 기본 검증: `npm ci`, `npm run lint`, `npm run build`
- App Router와 `src/` 디렉터리를 사용한다.
- Tailwind CSS, UI 라이브러리, 상태관리 라이브러리는 추가하지 않았다.
- 루트 페이지는 화면 구현이 아니라 빈 scaffold 상태다.

후속 후보:

- UX 승인 흐름을 받은 뒤 실제 페이지와 컴포넌트 구현
- API 계약이 승인된 뒤 API client 또는 타입 생성 방식 결정
- 필요한 경우 별도 FE 작업에서 typecheck script 추가

## Platform/SRE 입력

- 로컬 개발 런북: `docs/runbook/FOUNDATION-001-local-development.md`
- Docker는 이번 작업에서 보류됐다.
- Dockerfile, Docker Compose, Kubernetes, Nginx 설정은 없다.
- CI/CD workflow는 이번 작업에서 추가하지 않았다.
- MySQL은 target DB로만 문서화되어 있고 실제 로컬 DB 실행 절차나 접속 정보는 없다.

후속 후보:

- 승인 후 CI에 Backend test/build와 Frontend install/lint/build 연결
- 승인 후 Docker 또는 Docker Compose 개발 환경 설계
- 승인 후 로컬 MySQL 8.4 LTS 실행 방식과 Secret 전달 방식 문서화

## 아직 만들지 않은 것

- 상품, SKU, 회원, 구독 Entity
- Repository, Service, Controller, DTO
- Flyway migration
- Spring Security 설정
- API client
- 상품 목록, 상품 상세, 구독 생성, 마이페이지 화면
- Docker 기반 실행
- GitHub Actions 검증 workflow
- 배포 설정

## 중단 조건

- Java 25, Spring Boot 4.1, Gradle 9.x 조합에서 build가 깨지는 경우
- Node.js 24, Next.js 16, TypeScript 6.0 조합에서 lint/build가 깨지는 경우
- DB/API/Auth 구현이 FOUNDATION 범위를 넘어 필요한 경우
- Docker 도입이 필요해지는 경우
- 신규 의존성이나 운영 도구 도입이 필요한 경우
- Secret 또는 민감정보가 필요하거나 노출될 위험이 있는 경우
- reset, force push, rebase, history rewrite가 필요한 Git 상태가 되는 경우
