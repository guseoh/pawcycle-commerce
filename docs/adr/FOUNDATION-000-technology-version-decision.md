# FOUNDATION-000 기술 버전 결정안

## 상태

Status: Proposed

## 날짜

2026-07-06

## 작업 ID

`FOUNDATION-000`

이 문서는 첫 번째 수직 MVP 구현 전에 사용할 기술 버전과 개발 기반 경계를 제안한다. 최종 승인은 사용자 결정이 필요하며, 이 문서만으로 DATA-001, API-001, AUTH-001의 Proposed 상태를 Approved로 바꾸지 않는다.

## 목적

FOUNDATION-001 애플리케이션 기반 작업이 동일한 Java, Spring Boot, Gradle, Node.js, Next.js, TypeScript, MySQL 기준에서 시작할 수 있도록 입력을 만든다.

이번 작업은 버전과 개발 기반 경계 결정이다. Spring Boot 프로젝트, Next.js 프로젝트, Gradle wrapper, package.json, Docker Compose, GitHub Actions, Flyway 마이그레이션, JPA Entity, Controller, Service, Repository, OpenAPI 파일은 생성하지 않는다.

## 상위 방향 입력

| 영역 | 입력 |
| --- | --- |
| Backend | Spring Boot |
| Frontend | Next.js + TypeScript |
| Database | MySQL |
| 성능 개선 | 측정 후 필요 시 진행 |

## 공식 확인 근거

- [Oracle Java Downloads](https://www.oracle.com/java/technologies/downloads/)는 JDK 26을 최신 릴리스, JDK 25를 최신 LTS, JDK 21을 이전 LTS로 안내한다.
- [Adoptium Temurin Support](https://adoptium.net/support)는 Java 25 LTS의 제공 가능 기간을 최소 2031년 9월로 안내한다.
- [Spring Boot Installing](https://docs.spring.io/spring-boot/installing.html)과 [System Requirements](https://docs.spring.io/spring-boot/system-requirements.html)는 Spring Boot가 Gradle 8.14 이상 8.x 또는 9.x와 호환된다고 안내한다.
- [Gradle Compatibility Matrix](https://docs.gradle.org/current/userguide/compatibility.html)는 Gradle 실행 JVM으로 17부터 26까지를 요구하고, Java 25는 toolchain과 Gradle 실행 모두 Gradle 9.1.0 이상에서 지원한다고 안내한다.
- [Spring Boot Testing](https://docs.spring.io/spring-boot/reference/testing/index.html)은 `spring-boot-starter-test`가 JUnit Jupiter, AssertJ, Hamcrest 등 일반 테스트 라이브러리를 가져온다고 안내한다.
- [Node.js Previous Releases](https://nodejs.org/en/about/previous-releases)는 Node.js 24를 LTS로, Node.js 26을 Current 계열로 안내한다.
- [Next.js 16](https://nextjs.org/blog/next-16)은 Node.js 20.9 이상과 TypeScript 5.1 이상을 요구한다.
- [TypeScript 6.0 공식 발표](https://devblogs.microsoft.com/typescript/announcing-typescript-6-0/)는 TypeScript 6.0을 stable release로 안내한다.
- [MySQL EOL 공지](https://www.mysql.com/support/eol-notice.html)는 MySQL 8.0 EOL 이후 MySQL 8.4 LTS 또는 9.7 LTS 업그레이드를 안내한다.
- [Flyway 공식 저장소](https://github.com/flyway/flyway)는 데이터베이스 스키마를 안정적으로 진화시키는 migration 도구로 Flyway를 설명한다.

## 추천안

| 항목 | 추천 | 근거 | 후속 작업 영향 | 상태 |
| --- | --- | --- | --- | --- |
| Java | 25 LTS | 최신 LTS이며 Spring Boot 4.1과 Gradle 실행 JVM 범위에 들어간다. Adoptium 기준 장기 제공 가능 기간도 확보된다. | Backend 프로젝트 생성 시 toolchain과 로컬 JDK 기준으로 사용한다. | Proposed |
| Spring Boot | 4.1.x | 현재 Spring Boot 4.1 계열은 Java 17 이상과 Gradle 8.14 이상 또는 9.x 조합을 지원한다. 새 프로젝트에서 Jakarta EE 11/Spring Framework 7 기준을 맞춘다. | FOUNDATION-001에서 Spring Initializr 또는 Gradle 설정 시 4.1 계열 최신 patch를 다시 확인한다. | Proposed |
| Gradle | 9.1 이상 9.x 최신 patch | Spring Boot 4.1 지원 범위에 포함되며, Java 25를 Gradle 실행 JVM 또는 toolchain 기준으로 사용할 때 필요한 최소 지원선인 9.1.0 이상을 만족한다. | Gradle wrapper 생성은 FOUNDATION-001에서 수행한다. plugin 호환성 문제가 있으면 중단 후 재결정한다. | Proposed |
| Backend test stack | `spring-boot-starter-test` 기반 | Spring Boot 공식 starter가 JUnit Jupiter, AssertJ, Hamcrest 등 기본 테스트 구성을 제공한다. 첫 MVP에는 별도 테스트 프레임워크를 늘리지 않는다. | Backend 생성 후 단위 테스트와 slice 테스트 기본값으로 사용한다. Testcontainers는 DB 통합 테스트가 승인될 때 별도 결정한다. | Proposed |
| Node.js | 24 LTS | 공식 릴리스 표 기준 LTS이며 Next.js 16의 Node.js 20.9 이상 요구사항을 만족한다. Node.js 26은 Current 계열이므로 첫 MVP 기준에서는 피한다. | Frontend 로컬 개발과 CI Node 버전 기준으로 사용한다. 정확한 patch는 FOUNDATION-001에서 재확인한다. | Proposed |
| Next.js | 16.x | 공식 문서 기준 현재 주 버전이며 Node.js 20.9 이상, TypeScript 5.1 이상과 호환된다. | Next.js 프로젝트 생성 시 16 계열 최신 patch를 사용한다. App Router 사용 여부와 구체 라우트는 후속 FE 작업에서 결정한다. | Proposed |
| TypeScript | 6.0.x | 공식 블로그에서 stable release로 안내됐고 Next.js 16 최소 요구사항인 TypeScript 5.1 이상을 만족한다. | FE scaffold 시 Next.js와 React 타입 생태계 호환성을 재검증한다. TypeScript 7 또는 native compiler 전환은 승인 없이 채택하지 않는다. | Proposed |
| Frontend package manager | npm | Node.js에 포함되어 별도 도입 비용이 없다. 첫 MVP에서는 pnpm, Yarn 같은 추가 도구를 늘리지 않는다. | `package-lock.json` 기준 재현성을 사용한다. 다른 package manager가 필요하면 별도 승인 후 변경한다. | Proposed |
| Frontend test/lint baseline | TypeScript type check, ESLint CLI, `next build` | Next.js 16은 ESLint CLI 기반 사용을 안내한다. 첫 MVP에서는 E2E 도구를 바로 추가하지 않는다. | FE 프로젝트 생성 후 `npm run lint`, `npm run typecheck`, `npm run build` 후보를 CI에 연결한다. | Proposed |
| MySQL | 8.4 LTS | MySQL 8.0 EOL 이후 안정적 LTS 대상으로 안내되는 계열이다. 9.7 LTS는 더 최신이지만 첫 MVP에는 드라이버와 운영 호환성 확인 부담이 크다. | 로컬 개발 DB와 이후 배포 DB의 기본 target으로 둔다. 9.7 LTS 채택은 JDBC, Flyway, 운영 환경 확인 후 별도 결정한다. | Proposed |
| Migration tool | Flyway | SQL migration 기반으로 단순하고 Spring Boot 생태계에서 널리 쓰인다. 첫 MVP 데이터 모델은 SQL 변경 이력을 남기는 쪽이 재현성이 높다. | 마이그레이션 파일 생성은 DATA/Backend 구현 작업에서 수행한다. `flyway-core`와 MySQL 지원 artifact는 Spring Boot 4.1 기준으로 재확인한다. | Proposed |
| Local development database approach | 로컬 MySQL 8.4 LTS 수동 실행 | Docker Compose는 이번 범위에서 제외되어 있다. 첫 기반 작업은 로컬 서비스 또는 개발자 설치 DB로 시작한다. | 접속 정보와 schema 생성 방식은 Secret 없이 문서화한다. Docker Compose가 필요하면 별도 OPS/SRE 승인 후 추가한다. | Proposed |
| CI baseline | 최소 문서/커밋 검증 후, 프로젝트 생성 뒤 Backend test와 Frontend lint/typecheck/build 추가 | 현재 저장소에는 backend/frontend 프로젝트 설정이 없다. 처음부터 배포나 성능 검증을 넣지 않고 생성된 프로젝트의 가장 작은 품질 신호부터 붙인다. | FOUNDATION-001 이후 `./gradlew test`, `npm ci`, `npm run lint`, `npm run typecheck`, `npm run build` 후보를 GitHub Actions에 연결한다. | Proposed |

## 결정하지 않는 항목

- 배포 플랫폼
- 클라우드 인프라
- Docker Compose 구성
- Redis, Kafka 같은 추가 인프라
- 결제 외부 연동
- 검색 엔진
- 캐시 전략
- 메시징 전략
- 성능 튜닝
- 인증 구현 세부 코드
- Spring Security 세션, 토큰, 쿠키, CSRF 구현 방식
- JPA Entity, Repository, Service, Controller 구조
- API URL, HTTP 상태, 오류 응답 JSON의 최종 승인

## FOUNDATION-001 입력

- Backend 프로젝트 생성 시 Java 25 LTS, Spring Boot 4.1.x, Gradle 9.1 이상 9.x 최신 patch를 우선 검토한다.
- Backend test baseline은 `spring-boot-starter-test`로 시작한다.
- Frontend 프로젝트 생성 시 Node.js 24 LTS, Next.js 16.x, TypeScript 6.0.x, npm을 우선 검토한다.
- Frontend 검증 baseline은 ESLint CLI, TypeScript type check, Next.js build 후보로 둔다.
- Database는 MySQL 8.4 LTS를 로컬 개발 target으로 둔다.
- Migration tool은 Flyway를 우선 검토하되 마이그레이션 파일은 DATA/Backend 구현 작업에서 생성한다.
- Docker Compose, GitHub Actions, 배포 설정, 신규 인프라, 성능 튜닝은 별도 승인 없이 생성하지 않는다.

## 위험과 중단 조건

- FOUNDATION-001에서 Spring Boot, Gradle, Java 조합이 실제 scaffold 또는 wrapper 생성에서 호환되지 않으면 중단하고 재결정한다.
- Next.js 16, TypeScript 6.0, Node.js 24 LTS 조합이 scaffold 또는 build에서 호환되지 않으면 중단하고 재결정한다.
- MySQL 8.4 LTS와 JDBC/Flyway 조합이 Spring Boot 4.1 기준에서 확인되지 않으면 마이그레이션 생성을 중단한다.
- 신규 의존성이 필요한 경우 사용자 승인 없이 추가하지 않는다.
- DATA-001, API-001, AUTH-001 문서가 Proposed 상태라면 구현에서 최종 승인 입력처럼 사용하지 않는다.
- reset, force push, rebase, history rewrite가 필요한 상태가 되면 중단한다.
