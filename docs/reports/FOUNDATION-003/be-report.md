# FOUNDATION-003 Backend 작업 보고서

## 작업 정보

- 작업 ID: `FOUNDATION-003`
- 역할: Backend Engineer
- 기준 브랜치: `main`
- 작업 브랜치: `feat/be`
- 작업 유형: MySQL·Flyway·JPA·Spring Security 기반

## 목적

첫 Backend 구현의 1차 분할 범위로 `members`, `products`, `skus` 영속성, V1 migration, 환경 변수 기반 MySQL datasource와 공통 Spring Security 경계를 구성한다.

로그인·로그아웃·현재 회원·CSRF token API, 실제 Authentication 생성과 공개 상품 Controller·Service는 후속 Backend 작업으로 남긴다.

## 승인 입력

- 사용자 FOUNDATION-003 요청
- `docs/adr/ARCH-006-first-backend-implementation-approved-inputs.md`
- `docs/adr/AUTH-003-session-authentication-approved-inputs.md`
- `docs/data/DATA-001-first-mvp-data-model.md`의 승인된 세 테이블 subset
- `docs/data/DATA-002-first-mvp-logical-erd.md`의 승인된 세 테이블 subset
- `docs/handoffs/OPS-006/sre-to-be.md`
- Java 25, Spring Boot 4.1.0, Gradle 9.5.1과 CI `mysql:8.4` 계약

## 변경 범위

- 승인된 Backend 의존성
- datasource·Flyway·JPA·session cookie 설정
- `members`, `products`, `skus` V1과 JPA mapping·Repository
- 공통 Security·CSRF·401·403 JSON 기반
- migration·Repository·Security·cookie 설정 테스트
- FOUNDATION-003 보고서와 QA 인수인계

## 변경하지 않은 범위

- 로그인·로그아웃·현재 회원·CSRF token과 공개 상품 실제 API
- Authentication·SecurityContext·principal과 logout 유스케이스
- subscriptions, 주문, 결제, 배송, 재고와 관리자 기능
- Frontend, GitHub Actions, Docker·Testcontainers·H2
- CORS, JWT, OAuth2, remember-me와 OpenAPI 자동화
- 실제 Secret, production credential과 seed data

## 브랜치 준비

- PR #32 병합과 `origin/main` 최신 상태를 확인했다.
- 기존 `feat/be@ef810dc`는 열린 PR이 없고 PR #30의 병합된 head임을 확인했다.
- 기존 branch는 `backup/feat-be-before-FOUNDATION-003`에 보존했다.
- 원격 역할 branch를 삭제한 뒤 `origin/main@e5c6e64`에서 새 `feat/be`를 생성하고 일반 push했다.
- reset, rebase와 force push를 사용하지 않았다.

## 추가 의존성과 이유

| 의존성 | Gradle scope | 이유 |
| --- | --- | --- |
| `spring-boot-starter-data-jpa` | `implementation` | JPA Entity, Repository와 Hibernate schema validation |
| `spring-boot-starter-flyway` | `implementation` | Spring Boot 4.1 Flyway 자동 구성 |
| `spring-boot-starter-security` | `implementation` | SecurityFilterChain, CSRF, session과 PasswordEncoder 기반 |
| `mysql-connector-j` | `runtimeOnly` | 실제 MySQL JDBC 연결 |
| `flyway-mysql` | `runtimeOnly` | Flyway 12의 별도 MySQL database 지원 모듈 |
| `spring-security-test` | `testImplementation` | Security 경계와 인증 test request 지원 |

Spring Boot 4.1 공식 Database Initialization 문서는 Flyway 실행에 `spring-boot-starter-flyway`를 사용하고 MySQL에는 `org.flywaydb:flyway-mysql` 모듈을 함께 추가하도록 안내한다. Spring Boot dependency management가 관련 버전을 관리하므로 별도 버전을 고정하지 않았다.

- Spring Boot Database Initialization: `https://docs.spring.io/spring-boot/how-to/data-initialization.html`
- Spring Boot managed coordinates: `https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html`
- Flyway MySQL module: `https://documentation.red-gate.com/fd/mysql-277579322.html`

승인 목록 밖 Testcontainers, H2, JWT, OAuth2와 OpenAPI 도구는 추가하지 않았다.

## V1 DDL 결정

### 공통

- PK: MySQL `BIGINT AUTO_INCREMENT`, JPA `Long`와 `IDENTITY`
- table engine과 기본 문자 집합: `InnoDB`, `utf8mb4`, `utf8mb4_0900_ai_ci`
- schema 생성 책임: Flyway V1만 사용
- Hibernate: `ddl-auto=validate`
- `schema.sql`, `data.sql`, 회원 credential row와 production seed 없음

### members

- `id BIGINT`
- `email VARCHAR(254) CHARACTER SET ascii COLLATE ascii_bin NOT NULL`
- `password_hash VARCHAR(100) NOT NULL`, default 없음
- `uk_members_email` unique constraint

AUTH-003의 ASCII·case-sensitive email과 password hash 물리 계약을 그대로 사용한다. display name과 감사 필드는 현재 로그인 식별·credential 기반에 필요하지 않아 추가하지 않았다.

### products

- 공개 상품 계약 후보에 필요한 `name`, `short_description`, `description`, `pet_type`, `thumbnail_url`, `display_status`만 포함
- 문자열 길이는 MySQL·JPA schema validation이 같은 물리 타입을 사용하도록 명시
- pet type과 display status의 값 집합은 이번 기반 작업에서 새 제품 정책으로 고정하지 않음

### skus

- Product FK, `name`, `price DECIMAL(12,2)`, `subscribable`, `display_order`
- `fk_skus_product`로 Product 1:N SKU 관계 보존
- `chk_skus_price_nonnegative`로 음수 가격 거부
- `idx_skus_product_display(product_id, display_order, id)`로 상품별 SKU 조회·정렬 지원

재고, 판매 상태, 구독 주기와 결제 정책은 추가하지 않았다.

## JPA mapping

- `Member`, `Product`, `Sku` 세 Entity만 구현했다.
- `Sku`에서 `Product`로 필수 LAZY `ManyToOne`을 사용한다.
- cascade와 orphan removal은 사용하지 않는다. Product와 SKU 생성·변경 transaction은 후속 상품 유스케이스에서 명시한다.
- Repository는 ID 기본 연산, 회원 email 조회와 상품별 SKU 표시 순서 조회만 제공한다.
- Entity를 API 응답으로 노출하는 Controller는 만들지 않았다.

## datasource·Flyway·profile 우선순위

- datasource는 `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`를 필수로 소비한다.
- 실제 값이나 fallback credential을 두지 않아 변수가 없으면 안전하게 startup에 실패한다.
- Flyway가 `classpath:db/migration`의 V1을 먼저 적용하고 Hibernate가 schema를 validate한다.
- SQL 기본 초기화는 `spring.sql.init.mode=never`로 비활성화했다.
- production 기본 session cookie Secure 값은 `${SESSION_COOKIE_SECURE:true}`로 `true`다.
- local HTTP는 ignore 대상인 `application-local.properties`를 사용하며 추적 가능한 `application-local.example.properties`를 복사해 명시적으로 `false`를 선택한다.
- test profile만 `application-test.properties`에서 Secure를 `false`로 덮어쓴다.
- production 또는 기본 profile에서 환경 변수가 없을 때 Secure가 조용히 `false`가 되지 않는다.

## Security 기반

- CSRF 활성화와 `HttpSessionCsrfTokenRepository`
- header `X-CSRF-TOKEN`, parameter `_csrf`, CSRF cookie 없음
- `PasswordEncoder`와 `BCryptPasswordEncoder`
- session fixation `changeSessionId`
- 기본 cookie: `JSESSIONID`, HttpOnly, SameSite=Lax, host-only, Path=/, session cookie, idle timeout 30분
- 공개 matcher: 상품 GET, CSRF GET, login POST
- 인증 matcher: logout POST, me GET, 그 밖의 `/api/**`
- 미인증: `401 AUTH_REQUIRED` JSON
- 권한 부족: `403 ACCESS_DENIED` JSON
- CSRF 실패: `403 CSRF_INVALID` JSON
- 오류 body: `code`, `message`, `fieldErrors`

실제 auth/product Controller가 없으므로 test source의 격리 Controller로 filter 경계만 검증한다. CORS, JWT, OAuth2, remember-me와 동시 session 제한을 추가하지 않았다.

## 테스트

- MySQL 실제 connection과 8.4 version
- V1 적용 table allowlist와 제외 table 부재
- email charset·collation·unique와 password hash 물리 계약
- Flyway 반복 migrate 시 V1 재적용 없음
- runtime 생성 password를 BCrypt hash로 저장하는 test fixture
- Member/Product/Sku Repository와 Product-SKU mapping
- 공개 경계, 보호 경계 401, CSRF 403, AccessDenied 403 JSON
- production 기본 Secure와 local/test 명시 override 설정

## 실행한 검증

| 검증 | 결과 |
| --- | --- |
| Java 25 local `test`/`build` | 미실행: 로컬 Java 17·21만 존재, Java 25 toolchain 없음 |
| 비추적 init script를 사용한 Java 17 `compileTestJava` | 통과, 저장소 Java 25 설정은 변경하지 않음 |
| Java 17 `SessionCookieConfigurationTests` | 통과 |
| 로컬 Docker MySQL | 미실행: Docker Desktop Linux daemon 없음 |
| 로컬 설치 MySQL | 미사용: 승인된 격리 credential이 제공되지 않아 기존 로컬 DB에 접근하지 않음 |
| 최초 Java 25·CI MySQL Backend test | 실패: JSON Content-Type exact match 3건 |
| focused Security test assertion 수정 | `application/json;charset=UTF-8` 호환 비교로 정정 |
| Repository Validation run `29144751627` | Java 25 Backend test/build, MySQL, Frontend 전체 통과 |
| Repository Validation run `29145031383`, `29145057897` | Backend test 실패: `databaseConstraintsRejectDuplicateEmailAndNegativePrice` 173행 assertion, 12개 중 1개 실패 |
| 실패 원인 후속 수정 | DB 제약 negative-path test에 `@Transactional`을 적용하고 익명 logout의 유효 CSRF 인증 경계 회귀 테스트 추가 |

## 로컬에서 실행하지 못한 검증과 이유

- 로컬 Java 25 test/build: Java 25 toolchain이 설치되지 않았고 download repository가 구성되지 않았다.
- 로컬 Docker MySQL: Docker Desktop Linux daemon이 실행 중이 아니다.
- 로컬 설치 MySQL 통합 테스트: 기존 서비스에 접근할 승인된 격리 credential이 없어 사용하지 않았다.

## API 영향

- 실제 API Controller, Service와 DTO를 추가하지 않았다.
- Security matcher와 공통 오류 JSON 기반만 추가했다.

## DB 영향

- fresh MySQL에 `members`, `products`, `skus`, Flyway history table이 생성된다.
- `subscriptions`, 주문, 결제, 배송, 재고 table은 생성하지 않는다.
- migration에 데이터 row를 삽입하지 않는다.

## 트랜잭션

- 현재 애플리케이션 유스케이스 transaction은 없다.
- Spring Data Repository 기본 쓰기 연산만 repository transaction을 사용한다.
- 로그인과 상품 변경 transaction은 후속 작업에서 유스케이스 경계와 함께 정의한다.

## 위험과 제한

- 실제 Java 25·MySQL 8.4 통합 결과는 원격 CI가 완료돼야 확정된다.
- mutable `mysql:8.4` tag drift 위험은 OPS-006 승인 결정을 유지한다.
- MySQL major version만 확인하자는 CodeRabbit 제안은 OPS-006의 MySQL 8.4.* CI 계약을 약화하므로 미반영했다.
- product enum 값, 실제 상품 조회 query와 transaction은 후속 공개 상품 API 작업 범위다.
- 실제 Authentication 생성, SecurityContext 저장, logout handler, principal과 CSRF token API는 미구현이다.
- production HTTPS와 reverse proxy의 실제 cookie 전달은 배포 환경에서 별도 확인이 필요하다.

## 다음 권장 Backend 작업

1. 승인된 session login·logout·현재 회원·CSRF token API 구현
2. Authentication·SecurityContext·principal과 logout 정리 검증
3. 공개 상품 목록·상세 Controller·Service·DTO 구현
4. 실제 인증·credential·cookie·migration에 대한 QA 독립 검증

## Git 결과

- 새 `feat/be` 기준 SHA: `e5c6e648e406386f45a1c074b70694513fe37c79`
- 구현 commit: `75318c01bb35f61c6da8bbaa023d36777fb411e4`
- 구현 제목: `feat(backend): MySQL 영속성과 세션 보안 기반 추가`
- 테스트 보완 commit: `8e63a27b9fbe454fb4e3cef0b439e774d6fa79a5`
- 테스트 보완 제목: `test(backend): Security JSON 응답 검증 보완`
- 두 commit 모두 force 없이 일반 push했다.
- PR을 자동 병합하지 않는다.

## PR 결과

- PR #33 `feat(backend): MySQL 영속성과 세션 보안 기반 추가`
- URL: `https://github.com/guseoh/pawcycle-commerce/pull/33`
- base/head: `main` ← `feat/be`
- 상태: OPEN, Ready for review
- 검증 head: `8e63a27b9fbe454fb4e3cef0b439e774d6fa79a5`
- Repository Validation run `29144751627` 전체 통과
- CodeRabbit review는 완료됐으며 지적별 반영 결과는 후속 보고 갱신에 기록한다.
- Codex Review는 사용량 한도 초과로 실행하지 못했다.
- 원격 제목·본문·head·base와 UTF-8 상태를 확인했다.
- 자동 병합하지 않는다.
