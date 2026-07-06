# ARCH-002 Backend 구현 착수 기준

## 문서 상태

- 작업 ID: `ARCH-002`
- 역할: Tech Lead
- 문서 상태: Proposed Implementation Readiness
- 기준 브랜치: `main`
- 작업 브랜치: `ops/tl`
- 선행 PR: #22 `ci(foundation): 애플리케이션 검증 연결`

이 문서는 첫 Backend 구현 전에 사용할 수 있는 입력 문서와 사용자 승인이 필요한 결정을 구분한다. 이 문서만으로 `DATA-001`, `API-001`, `AUTH-001`, `ARCH-001`, `FOUNDATION-000`의 상태를 `Approved`로 바꾸지 않는다.

## 목적

PR #22 병합 이후 Backend 구현을 바로 시작할 수 있는지 점검한다.

구현 착수 전 다음을 분리한다.

- 현재 승인 입력으로 사용할 수 있는 제품·도메인 규칙
- Backend 구현 계획 수립에 사용할 수 있는 Proposed 설계
- 실제 코드, DB schema, API 계약, 인증 방식, 신규 의존성 도입 전에 사용자 승인이 필요한 항목
- 다음 Backend 작업의 권장 범위와 중단 조건

## 현재 구현 가능한 범위

현재 바로 가능한 작업은 실제 Backend 코드 구현이 아니라 구현 준비와 승인 대기 항목 정리다.

- Backend 의존성 도입안 검토
- JPA, Flyway, MySQL, Security 도입 필요성 확인
- DATA-001, API-001 기반 첫 수직 MVP Backend 구현 계획 작성
- 테스트 범위 정의
- 사용자 승인 대기 항목 정리
- CI 기준선에서 Backend 검증 명령을 재확인하는 작업

## 구현 전 사용자 승인이 필요한 범위

다음 항목은 사용자 명시 승인 없이 확정하거나 구현하지 않는다.

- DATA-001을 실제 DB schema로 확정
- API-001을 실제 Controller 계약으로 확정
- AUTH-001을 실제 Spring Security 구현으로 확정
- ARCH-001을 최종 아키텍처 결정으로 확정
- FOUNDATION-000의 Proposed 기술 버전과 의존성 방향을 최종 승인 입력으로 확정
- 신규 의존성 추가
- Flyway migration 작성
- MySQL 연결 정책 확정
- 세션, 토큰, 쿠키, CSRF, principal 구조 확정
- OpenAPI 파일 또는 API contract 검증 도구 도입
- Testcontainers, Spring Security Test 등 신규 테스트 의존성 도입

## 입력 문서 상태 확인

| 문서 | 현재 상태 | 구현 입력 판단 |
| --- | --- | --- |
| `docs/product/PS-002-first-mvp-requirements.md` | 승인된 Product Decision과 인수 조건 포함 | 제품 요구사항과 비즈니스 규칙 입력으로 사용 가능 |
| `docs/product/PS-003-ux-product-decisions.md` | Approved Product Decision | 공개 탐색, 로그인 복귀, 생성 후 상세 이동, 생성 전 예정일 미표시 정책 입력으로 사용 가능 |
| `docs/design/UX-001-first-mvp-subscription-experience.md`, `docs/reports/UX-002/ux-report.md`, `docs/handoffs/UX-002/ux-to-tl.md` | PS-003 반영, Deferred Technical Decision 분리 | 사용자 흐름과 화면 상태 입력으로 사용 가능. 구체 URI와 라우팅은 미확정 |
| `docs/domain/DOMAIN-001-first-mvp-subscription-domain.md` | Approved Input, Accepted Domain Design, Proposed Technical Design 혼재 | 도메인 불변 조건은 구현 계획 입력으로 사용 가능. Aggregate, DB/API 표현은 확정 전 |
| `docs/adr/ARCH-001-first-vertical-mvp-architecture.md` | Proposed Architecture Decision | 책임 경계 검토 입력으로 사용 가능. 최종 아키텍처 승인으로 간주하지 않음 |
| `docs/data/DATA-001-first-mvp-data-model.md` | Proposed Data Design | 데이터 설계 후보로 사용 가능. DB schema, DDL, JPA 매핑은 승인 전 |
| `docs/api/API-001-first-mvp-api-contract.md` | Proposed API Contract | API 계약 후보로 사용 가능. URI, DTO, HTTP 상태, 오류 JSON은 승인 전 |
| `docs/adr/AUTH-001-login-return-and-auth-boundary.md` | Proposed Authentication Design | 인증 경계 후보로 사용 가능. Spring Security, 세션/토큰, 쿠키, CSRF는 승인 전 |
| `docs/adr/FOUNDATION-000-technology-version-decision.md` | Status: Proposed | 기술 버전 후보로 사용 가능. 신규 의존성 도입 승인으로 간주하지 않음 |
| `docs/reports/FOUNDATION-001/tl-report.md`, `docs/runbook/FOUNDATION-001-local-development.md` | 최소 Backend/Frontend 기반 생성 기록 | 실행 기반과 로컬 검증 기준으로 사용 가능 |
| `docs/runbook/FOUNDATION-002-ci-validation.md`, `docs/handoffs/FOUNDATION-002/sre-to-be-fe-tl.md` | CI 기준선 문서 | Backend test/build와 Frontend install/lint/build 기준으로 사용 가능 |

## DATA-001 상태 확인

DATA-001은 `Proposed Data Design`이다. Product, SKU, Member, Subscription의 테이블 후보, 제약 후보, 인덱스 후보, 날짜 저장 기준을 제공하지만 실제 DB schema가 아니다.

Backend 구현 전 필요한 사용자 승인 항목은 다음과 같다.

- 실제 테이블명, 컬럼명, FK, 인덱스
- ID 타입과 감사 필드 저장 방식
- `subscribable` 표현 방식
- `delivery_cycle_weeks` 저장 방식
- `created_date`, `next_order_date`, `created_at`, `updated_at`의 실제 DB 타입
- Flyway migration 작성 여부와 파일 범위

## API-001 상태 확인

API-001은 `Proposed API Contract`다. 공개 상품 조회, 보호 구독 API, 요청·응답 필드 후보, HTTP 상태 후보, 오류 코드 후보를 제공하지만 최종 Controller 계약이 아니다.

Backend 구현 전 필요한 사용자 승인 항목은 다음과 같다.

- 실제 URI와 HTTP method
- Request/Response DTO 필드명
- 성공 HTTP 상태
- 오류 HTTP 상태와 오류 JSON
- `SUBSCRIPTION_NOT_FOUND`로 존재하지 않는 구독과 다른 회원 소유 구독을 통합하는 최종 방식
- OpenAPI 파일 생성 여부

## AUTH-001 상태 확인

AUTH-001은 `Proposed Authentication Design`이다. 공개 기능과 보호 기능의 경계, 로그인 후 안전한 내부 GET 복귀, Open Redirect 방지 후보를 정리하지만 실제 Spring Security 구현은 확정하지 않는다.

Backend 구현 전 필요한 사용자 승인 항목은 다음과 같다.

- 세션 기반인지 토큰 기반인지
- 쿠키 속성과 CSRF 정책
- Spring Security 설정 방식
- 인증 실패 응답과 브라우저 리다이렉트의 최종 분기
- 인증 컨텍스트에서 회원 식별자를 얻는 principal 구조
- 로그인 복귀 경로 저장 위치와 수명
- Open Redirect 검증 유틸의 구현 위치와 테스트 범위

## ARCH-001 상태 확인

ARCH-001은 `Proposed Architecture Decision`이다. 상품 탐색, 구독 생성, 내 구독 조회의 책임 경계와 후속 결정 항목을 정리했지만 최종 승인된 아키텍처 결정으로 표시되어 있지 않다.

Backend 구현 전 필요한 사용자 승인 항목은 다음과 같다.

- 도메인, 애플리케이션, API, 영속성 패키지 구조
- 트랜잭션 경계
- Product와 SKU Aggregate 경계
- Subscription과 SKU 참조 방식
- 오류 처리 경계와 예외 계층

## FOUNDATION-000/001/002 기준선

FOUNDATION-000은 `Status: Proposed`다. Java 25 LTS, Spring Boot 4.1.x, Gradle 9.x, Node.js 24 LTS, Next.js 16.x, MySQL 8.4 LTS, Flyway 후보를 제안하지만 사용자 승인 전 최종 기술 결정으로 간주하지 않는다.

FOUNDATION-001은 최소 애플리케이션 기반을 만들었다.

- Backend: Spring Boot 4.1.0, Java 25, Gradle wrapper 9.5.1
- Frontend: Next.js 16.2.10, TypeScript 6.0.3, npm
- DB, JPA, Flyway, Security 의존성은 추가하지 않음
- Controller, Service, Repository, Entity, DTO, SecurityConfig는 만들지 않음

FOUNDATION-002는 Repository Validation에 Backend와 Frontend 최소 검증을 연결했다.

- Backend CI: `./gradlew test`, `./gradlew build`
- Frontend CI: `npm ci`, `npm run lint`, `npm run build`
- Docker, MySQL service container, Flyway migration, JPA 통합 테스트, Security 테스트는 아직 CI에 없음

## Backend 첫 구현 작업 추천 범위

추천 1은 다음 작업으로 가장 안전하다.

```text
BE-001 Backend 구현 계획과 의존성 도입안 정리
```

목적은 실제 코드 구현 전 JPA, Flyway, MySQL, Security 의존성 도입 범위와 테스트 전략을 정리하는 것이다.

포함 후보:

- DATA-001, API-001, AUTH-001, FOUNDATION-000의 승인 필요 항목 확인
- Backend 패키지 구조 후보
- 도메인 객체, 애플리케이션 서비스, Controller, Repository, Entity 구현 순서
- 테스트 범위와 도입 의존성 후보
- CI 확장 필요 여부
- 사용자 승인 요청 목록

추천 2는 조건부로만 가능하다.

```text
BE-001 첫 수직 MVP Backend 최소 구현
```

이 작업은 사용자가 DATA-001, API-001, AUTH-001, FOUNDATION-000을 구현 입력으로 승인한 경우에만 진행한다.

## Backend 첫 구현 작업 제외 범위

다음은 첫 Backend 작업에서 제외하거나 별도 승인 후 진행한다.

- 결제, 재고, 배송
- 구독 상태 모델
- 구독 변경, 건너뛰기, 일시정지, 재개, 해지
- 정기 주문 자동 생성
- 복수 SKU 구독
- 관리자 기능
- Docker, Docker Compose
- 배포 workflow
- 성능 최적화
- 신규 외부 SaaS 연동
- 실제 Secret 추가

## 중단 조건

다음 상황이면 Backend 구현을 시작하지 않는다.

- DATA-001이 Proposed 상태인데 실제 DB schema 확정이 필요한 경우
- API-001이 Proposed 상태인데 실제 Controller 계약 확정이 필요한 경우
- AUTH-001이 Proposed 상태인데 실제 Spring Security 구현 확정이 필요한 경우
- FOUNDATION-000이 Proposed 상태인데 신규 의존성 추가가 필요한 경우
- JPA, Flyway, MySQL connector, Spring Security, Testcontainers 등 신규 의존성이 필요한 경우
- MySQL 연결 정보, DB secret, schema 생성 정책이 필요한 경우
- reset, rebase, force push, history rewrite가 필요한 Git 상태가 되는 경우
- Secret 또는 민감정보 노출이 의심되는 경우
- 구현자가 설명할 수 없는 트랜잭션, SQL, 실패 상태가 남는 경우

## 다음 작업 후보

1. `BE-001 Backend 구현 계획과 의존성 도입안 정리`
2. `ARCH-003 Backend 패키지 구조와 트랜잭션 경계 결정`
3. `DATA-002 첫 MVP DB schema 승인안`
4. `API-002 첫 MVP API 계약 승인안`
5. `AUTH-002 Spring Security 구현 결정`

## 결정 요약

- 제품 규칙과 도메인 불변 조건은 구현 계획 입력으로 사용할 수 있다.
- DATA-001, API-001, AUTH-001, ARCH-001, FOUNDATION-000은 Proposed 상태를 유지한다.
- 사용자 승인 없이 DB schema, API 계약, 인증 방식, 신규 의존성은 확정하지 않는다.
- 다음 Backend 작업은 실제 구현보다 구현 계획과 의존성 도입안 정리가 더 안전하다.
