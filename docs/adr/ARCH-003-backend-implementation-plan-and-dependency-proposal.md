# ARCH-003 Backend 구현 계획과 의존성 도입안

## 문서 상태

- 작업 ID: `ARCH-003`
- 역할: Backend Engineer
- 문서 상태: Proposed Backend Implementation Plan
- 기준 브랜치: `main`
- 작업 브랜치: `feat/be`
- 선행 PR: #23 `docs(architecture): Backend 구현 착수 기준 보완`

이 문서는 PR #23에서 정리한 Backend 구현 착수 기준을 이어받아, 실제 Backend 코드 구현 전에 필요한 구현 순서와 신규 의존성 도입 후보를 정리한다.

이 문서만으로 `DATA-001`, `API-001`, `AUTH-001`, `ARCH-001`, `FOUNDATION-000`의 상태를 `Approved`로 바꾸지 않는다. 또한 이 문서는 신규 의존성을 추가하거나 DB schema, JPA Entity, Controller, Service, Repository, DTO, SecurityConfig를 생성하지 않는다.

## 목적

첫 Backend 구현을 시작하기 전에 다음 항목을 설명 가능한 형태로 분리한다.

- 어떤 승인 문서를 구현 입력으로 사용할 수 있는지
- 어떤 문서는 아직 Proposed 상태라서 사용자 승인이 필요한지
- 첫 구현의 최소 후보 범위가 무엇인지
- 어떤 패키지 구조가 적합한지
- 어떤 의존성이 필요하고, 왜 사용자 승인이 필요한지
- 테스트와 CI를 어디까지 확장해야 하는지
- 어떤 조건에서는 구현을 시작하지 않아야 하는지

## 승인된 입력과 미승인 입력

| 문서 | 현재 상태 | Backend 구현 판단 |
| --- | --- | --- |
| `docs/product/PS-002-first-mvp-requirements.md` | 승인된 요구사항과 인수 조건 포함 | 제품 요구사항 입력으로 사용 가능 |
| `docs/product/PS-003-ux-product-decisions.md` | Approved Product Decision | 공개 탐색, 로그인 복귀, 생성 후 상세 이동, 생성 후 예정일 미표시 정책 입력으로 사용 가능 |
| `docs/domain/DOMAIN-001-first-mvp-subscription-domain.md` | Approved Input, Accepted Domain Design 포함 | 도메인 불변 조건과 용어 입력으로 사용 가능 |
| `docs/adr/ARCH-001-first-vertical-mvp-architecture.md` | Proposed Architecture Decision | 패키지와 책임 경계 후보로만 사용 가능, 최종 구현 기준으로는 승인 필요 |
| `docs/data/DATA-001-first-mvp-data-model.md` | Proposed Data Design | DB schema 후보로만 사용 가능, migration과 Entity 구현 전 승인 필요 |
| `docs/api/API-001-first-mvp-api-contract.md` | Proposed API Contract | Controller 계약 후보로만 사용 가능, DTO와 HTTP 응답 구현 전 승인 필요 |
| `docs/adr/AUTH-001-login-return-and-auth-boundary.md` | Proposed Authentication Design | 인증 경계 후보로만 사용 가능, Spring Security 구현 전 승인 필요 |
| `docs/adr/FOUNDATION-000-technology-version-decision.md` | Status: Proposed | 기술 버전과 의존성 방향 후보로만 사용 가능, 신규 의존성 추가 전 승인 필요 |
| `docs/adr/ARCH-002-first-backend-implementation-readiness.md` | Proposed Implementation Readiness | 구현 착수 전 승인 필요 항목을 확인하는 입력으로 사용 |
| `docs/handoffs/ARCH-002/tl-to-be.md` | Tech Lead to Backend Engineer handoff | Backend Engineer의 다음 계획 작업 입력으로 사용 |

## 첫 Backend 구현 대상 후보

첫 실제 구현은 결제, 배송, 재고, 구독 변경 기능을 제외하고 첫 수직 흐름의 최소 API만 대상으로 삼는 것이 안전하다.

후보 범위:

- 공개 상품 목록 조회
- 공개 상품 상세 조회
- 보호된 구독 생성 요청
- 보호된 내 구독 목록 조회
- 보호된 내 구독 상세 조회

이 범위도 실제 구현 전에 `DATA-001`, `API-001`, `AUTH-001`, `ARCH-001`, `FOUNDATION-000`의 승인 또는 대체 결정이 필요하다.

## Backend 패키지 구조 후보

아래 구조는 코드 생성이 아니라 구현 방향 후보이다. 실제 디렉터리와 클래스는 사용자 승인 이후 별도 구현 작업에서 만든다.

```text
com.pawcycle.backend
  catalog
    domain
    application
    infra
    api
  subscription
    domain
    application
    infra
    api
  member
    domain
    application
    infra
  common
    error
    time
    validation
    security
```

판단 근거:

- 현재 Spring Boot 애플리케이션 진입점은 `com.pawcycle.backend` 패키지에 있으므로, 기본 component scan 범위에 들어가도록 후보 패키지도 그 하위로 둔다.
- `catalog`는 공개 상품 탐색과 SKU 조회를 담당한다.
- `subscription`은 구독 생성과 내 구독 조회를 담당한다.
- `member`는 보호 API에서 인증된 사용자 식별 경계를 담당한다.
- `common`은 오류 응답, 시간 기준, 입력 검증, 보안 공통 요소를 담되 도메인 규칙을 흩뜨리지 않는다.

열린 결정:

- Product와 SKU를 하나의 Aggregate로 둘지, 조회 모델과 구독 생성 모델을 분리할지 결정이 필요하다.
- Subscription이 SKU를 FK로만 참조할지, 생성 시점의 상품명과 가격 snapshot을 별도 값으로 저장할지 결정이 필요하다.
- API 오류 응답을 공통 구조로 고정할지, API별 최소 구조로 시작할지 결정이 필요하다.

## 의존성 도입 후보

| 의존성 후보 | 필요한 이유 | 첫 구현 포함 후보 | 사용자 승인 필요 | 제외 시 영향 |
| --- | --- | --- | --- | --- |
| `spring-boot-starter-data-jpa` | 도메인 모델을 MySQL 영속성에 매핑하고 Repository 테스트를 구성하기 위해 필요 | 예 | 예 | 실제 DB 기반 조회와 구독 생성 구현이 불가능하거나 임시 저장소가 필요해짐 |
| MySQL JDBC driver | Spring Boot 애플리케이션이 MySQL 8.4 LTS 후보 DB에 연결하기 위해 필요 | 예 | 예 | 로컬과 CI에서 MySQL 연결 검증이 불가능함 |
| Flyway | DB schema 변경을 버전 관리하고 배포 가능한 migration 이력을 만들기 위해 필요 | 예 | 예 | schema 변경 이력이 코드와 분리되어 재현성과 리뷰 가능성이 낮아짐 |
| `spring-boot-starter-security` | 보호 API, 인증 경계, 현재 사용자 식별을 구현하기 위해 필요 | 조건부 예 | 예 | 보호 API를 실제 보안 경계 없이 임시 구현해야 하며 AUTH-001 검증이 지연됨 |
| `spring-security-test` | 인증된 사용자, 비인증 사용자, 권한 경계 테스트를 작성하기 위해 필요 | 조건부 예 | 예 | Security 설정 도입 후 테스트가 우회되거나 수동 검증에 의존하게 됨 |
| Testcontainers | MySQL 기반 Repository, migration, 통합 테스트를 실제 DB에 가깝게 검증하기 위해 필요 | 조건부 예 | 예 | H2 등 대체 DB를 쓰거나 통합 검증을 지연해야 하며 MySQL 차이를 놓칠 수 있음 |
| OpenAPI generation/validation tool | API-001 계약과 Controller 구현 사이의 drift를 줄이고 문서와 테스트를 연결하기 위해 필요 | 조건부 예 | 예 | API 문서와 구현 불일치를 리뷰나 수동 확인에 더 의존하게 됨 |

의존성 도입 원칙:

- 의존성 추가는 사용자 승인 이후 별도 구현 작업에서만 진행한다.
- 한 번에 모든 후보를 도입하지 않고 첫 구현의 검증 가능성에 필요한 최소 묶음부터 도입한다.
- 보안, DB, API 계약 도구는 설정 파일과 CI에 영향을 주므로 승인 단위를 명확히 나눈다.

## 의존성별 승인 이유

`spring-boot-starter-data-jpa`와 MySQL JDBC driver는 DB schema와 JPA mapping을 사실상 결정하게 만든다. `DATA-001`이 Proposed 상태이므로 실제 테이블, 컬럼, FK, index, audit column, 날짜 저장 기준 승인이 먼저 필요하다.

Flyway는 첫 migration 파일명을 만들고 schema 이력을 시작한다. 한 번 main에 병합된 migration은 되돌리기 비용이 크므로, migration 도입 여부와 최초 버전 규칙을 승인해야 한다.

`spring-boot-starter-security`는 인증 실패 응답, 세션 또는 토큰, CSRF, cookie, principal 구조에 영향을 준다. `AUTH-001`이 Proposed 상태이므로 보호 API 구현 전에 승인해야 한다.

`spring-security-test`는 Security 도입과 함께 테스트 경계를 고정한다. Security를 첫 구현에 포함한다면 테스트 의존성도 같이 승인하는 것이 안전하다.

Testcontainers는 Docker 기반 테스트 실행 조건과 CI runtime에 영향을 준다. 로컬과 GitHub Actions의 Docker 사용 가능성, 실행 시간, MySQL image 정책을 승인해야 한다.

OpenAPI generation/validation tool은 build task, API 문서 위치, 생성물 commit 여부를 결정한다. API 계약을 코드에서 생성할지, 문서에서 검증할지 먼저 정해야 한다.

## 구현 순서 후보

1. 의존성 승인
2. DB schema와 Flyway 승인
3. 인증 방식과 Spring Security 경계 승인
4. Product, SKU, Member, Subscription 최소 모델 구현
5. 상품 목록과 상세 조회 API 구현
6. 보호 API 인증 경계 적용
7. 구독 생성 API 구현
8. 내 구독 목록과 상세 API 구현
9. 테스트와 CI 확장

이 순서는 추천안이다. 사용자가 인증 경계와 DB schema를 먼저 확정하지 않는다면 4번 이후의 실제 구현은 시작하지 않는다. 구독 생성과 내 구독 조회 같은 보호 API는 인증 경계가 적용된 같은 변경에서만 추가한다.

## 테스트 전략 후보

첫 Backend 구현의 테스트는 다음 순서로 늘리는 것이 적절하다.

- 도메인 단위 테스트: 구독 가능 SKU, 배송 주기, 수량, 다음 배송일 미표시 정책 같은 규칙 검증
- 애플리케이션 서비스 테스트: 구독 생성 흐름, 존재하지 않는 SKU, 구독 불가 SKU, 타인 구독 접근 차단 후보 검증
- Repository 테스트: MySQL dialect와 FK, index, 날짜 저장 기준 검증
- Controller 테스트: 공개 API와 보호 API의 HTTP status, request validation, error response 검증
- Security 테스트: 비인증 접근, 인증된 사용자 context, Open Redirect 방지 후보 검증
- Migration 테스트: Flyway baseline 없이 첫 migration부터 적용되는지 검증

현재 CI는 Backend `test`, `build`와 Frontend `install`, `lint`, `build`를 실행한다. JPA, Flyway, MySQL, Security, Testcontainers를 추가하면 CI에 DB service나 Docker 기반 통합 테스트 단계가 필요할 수 있다.

## CI 확장 필요

신규 의존성을 승인할 경우 다음 CI 확장을 검토해야 한다.

- Backend 의존성 변경 감지 후 Gradle dependency resolution 검증
- Flyway migration 적용 검증
- MySQL 기반 Repository 또는 integration test 검증
- Security test 실행
- API 계약 검증 task 추가 여부
- Testcontainers 사용 시 GitHub Actions Docker 실행 시간과 cache 정책 확인

CI 확장은 Platform/SRE와 Tech Lead 결정이 필요하다. 이번 문서 작업에서는 `.github/**`를 변경하지 않는다.

## 사용자 승인 요청 목록

실제 Backend 구현 전 필요한 승인 항목은 다음과 같다.

- `DATA-001`을 실제 DB schema로 확정할지 여부
- `API-001`을 실제 Controller 계약으로 확정할지 여부
- `AUTH-001`을 실제 Spring Security 구현 기준으로 확정할지 여부
- `ARCH-001`을 실제 패키지, 트랜잭션, 예외 처리 경계로 확정할지 여부
- `FOUNDATION-000`의 기술 버전과 의존성 도입 방향을 확정할지 여부
- `spring-boot-starter-data-jpa`, MySQL JDBC driver, Flyway, Spring Security, Spring Security Test, Testcontainers, OpenAPI 도구 중 첫 구현에 포함할 의존성
- 최초 Flyway migration 작성 여부와 migration naming/versioning 규칙
- MySQL 연결 정책, 로컬 DB 생성 방식, Secret 전달 방식
- API 오류 응답 공통 구조와 검증 실패 응답 형식
- 보호 API에서 사용할 인증 방식, session/token/cookie/CSRF/principal 구조

## 중단 조건

다음 조건이 있으면 실제 Backend 구현으로 넘어가지 않는다.

- `DATA-001`, `API-001`, `AUTH-001`, `ARCH-001`, `FOUNDATION-000` 중 필요한 문서가 Proposed 상태인데 구현 확정이 필요한 경우
- 신규 의존성 추가가 필요하지만 사용자 승인이 없는 경우
- DB schema, Flyway migration, MySQL 연결 정책이 확정되지 않은 경우
- 인증 방식과 보호 API 경계가 확정되지 않은 경우
- Secret이나 개인정보 노출 가능성이 있는 경우
- reset, rebase, force push, history rewrite가 필요한 Git 상태가 되는 경우
- Backend 구현자가 트랜잭션, SQL, 실패 상태를 설명할 수 없는 경우

## 다음 작업 후보

후보 1:

```text
ARCH-004 또는 FOUNDATION-003: Backend 의존성 승인과 도입 범위 확정
```

목적은 첫 Backend 구현 전에 사용자 승인 대상 의존성, DB schema, 인증 방식, CI 확장 범위를 확정하는 것이다.

후보 2:

```text
승인 이후 별도 Backend 구현 작업: 첫 수직 MVP Backend 최소 구현
```

조건은 `DATA-001`, `API-001`, `AUTH-001`, `ARCH-001`, `FOUNDATION-000` 또는 그 후속 승인 문서가 실제 구현 입력으로 확정되는 것이다.

실제 다음 작업 ID는 사용자가 하네스에서 허용하는 접두사로 별도 선택한다.
