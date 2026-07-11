# FOUNDATION-003 Backend → QA 인수인계

## 전달 목적

MySQL·Flyway·JPA·Spring Security 기반에서 현재 검증 가능한 범위와 후속 인증 구현 뒤 QA가 검증할 범위를 분리한다.

## 다음 역할 또는 대상 역할

- 수신: QA Engineer
- 후속 협업: Backend Engineer, Platform/SRE, Tech Lead

## 입력 문서

- `docs/adr/ARCH-006-first-backend-implementation-approved-inputs.md`
- `docs/adr/AUTH-003-session-authentication-approved-inputs.md`
- `docs/handoffs/OPS-006/sre-to-be.md`
- `docs/reports/FOUNDATION-003/be-report.md`
- FOUNDATION-003 PR의 Java 25·MySQL 검증 결과

## 사용 가능한 결과

- V1 migration과 JPA mapping
- Member/Product/Sku Repository
- SecurityFilterChain, CSRF repository와 공통 401·403 JSON
- migration·Repository·Security·cookie 설정 자동 테스트

## 검증 완료 결과

- Repository Validation run `29144751627`
- Java 25 Backend test/build 통과
- OPS-006 MySQL container 초기화와 `Verify MySQL service` 통과
- fresh V1, JPA validate, Repository와 Security 기반 테스트 통과
- Frontend install/lint/build 회귀 통과

후속 Repository Validation run `29145031383`, `29145057897`은 동일하게 DB 제약 negative-path test 1건이 실패했다. 테스트 데이터 격리를 위한 `@Transactional`과 익명 logout의 유효 CSRF 인증 경계 회귀 테스트를 추가했으며, 수정 head의 Java 25·MySQL 8.4 재검증 결과는 새 Repository Validation 완료 후 이 문서에 갱신한다.

첫 수정 head의 Repository Validation run `29151260379`에서는 동일 transaction 안에서 중복 email 위반 뒤 실행한 음수 price assertion이 실패했다. 두 제약 검증을 각각 독립된 rollback transaction 테스트로 분리했으며 재검증 결과는 대기 중이다.

현재 검증은 datasource·migration·JPA·Security filter 기반까지다. 실제 로그인·logout·principal과 상품 API 동작 완료를 의미하지 않는다.

## 현재 구현 범위

- 환경 변수 기반 MySQL datasource
- `members`, `products`, `skus` V1 migration
- Product 1:N SKU FK와 최소 JPA mapping·Repository
- Flyway migration 후 Hibernate schema validation
- Spring Security 공개·보호 matcher, CSRF, session cookie와 401·403 JSON 기반
- test source에서 실행 시 생성한 password를 BCrypt hash로 저장하는 격리 fixture

## 현재 구현하지 않은 범위

- 로그인·로그아웃·현재 회원·CSRF token Controller와 Service
- 실제 Authentication 생성, SecurityContext 저장과 principal
- 실제 logout handler
- 공개 상품 목록·상세 Controller, Service와 DTO
- subscriptions와 확장 도메인
- CORS, JWT, OAuth2와 운영 배포 구성

## DB 검증 입력

| 항목 | 기대 결과 |
| --- | --- |
| datasource | CI가 제공하는 `SPRING_DATASOURCE_*` 세 변수 사용 |
| database | 격리된 `pawcycle_test` |
| migration | fresh schema에 V1 한 번 적용, 반복 migrate 재적용 없음 |
| table | `members`, `products`, `skus`와 Flyway history만 존재 |
| email | `VARCHAR(254)`, ASCII, `ascii_bin`, NOT NULL, UNIQUE |
| password hash | `VARCHAR(100) NOT NULL`, default와 seed row 없음 |
| Product-SKU | `skus.product_id` 필수 FK |
| price | `DECIMAL(12,2)`, 음수 CHECK 거부 |

## Security 검증 입력

- 공개 matcher: `GET /api/products`, `GET /api/products/**`, `GET /api/auth/csrf`, `POST /api/auth/login`
- 인증 matcher: `POST /api/auth/logout`, `GET /api/auth/me`, 그 밖의 `/api/**`
- 미인증: `401 AUTH_REQUIRED`와 빈 `fieldErrors`
- 권한 부족: `403 ACCESS_DENIED`와 빈 `fieldErrors`
- CSRF 실패: `403 CSRF_INVALID`와 빈 `fieldErrors`
- HTML, redirect, stack trace와 내부 예외 정보 없음
- CSRF repository: HTTP session, `X-CSRF-TOKEN`, `_csrf`, 별도 CSRF cookie 없음
- session: `JSESSIONID`, HttpOnly, SameSite=Lax, host-only, Path=/, persistent Max-Age 없음, idle timeout 30분
- Secure: production 기본 `true`; local/test HTTP에서만 명시적으로 `false`

## test fixture 경계

- 회원 email과 password는 test 실행 시 생성한다.
- BCrypt hash는 test 실행 중 생성해 격리 DB에 삽입한다.
- migration, main resource와 production profile에 credential row가 없다.
- password, hash, connection string과 session id를 로그나 결과 보고에 출력하지 않는다.

## 현재 자동 검증

- V1 DDL·JPA 물리 계약과 제외 table
- Flyway 반복 migrate
- Repository 저장·조회와 Product-SKU 관계
- runtime BCrypt fixture
- 공개·보호·CSRF filter 경계와 401·403 JSON
- 유효한 CSRF를 포함한 익명 logout 요청의 `401 AUTH_REQUIRED`, 빈 `fieldErrors`, redirect 부재
- session cookie profile 설정

## 후속 인증 구현 후 QA 검증

1. login 전 CSRF 획득과 유효 token login
2. 로그인 성공 시 session id 변경과 SecurityContext 유지
3. 로그인 성공 직후 CSRF token 재획득
4. 잘못된 email/password의 동일 `INVALID_CREDENTIALS`
5. logout 시 session·SecurityContext·CSRF token 정리
6. 보호 API의 실제 `memberId` principal 사용
7. cookie 속성과 HTTPS Secure 동작
8. password, hash, session id와 CSRF token의 비승인 위치 노출 부재

## 검증 포인트

- migration과 JPA mapping이 같은 nullable·길이·precision·FK를 사용하는가?
- fresh MySQL과 반복 기동에서 Flyway가 안정적으로 동작하는가?
- test fixture가 runtime BCrypt hash와 격리 DB만 사용하는가?
- 공개 matcher, 401·403 JSON과 CSRF 거부가 승인 계약과 일치하는가?
- production 기본 Secure와 local/test override 경계가 유지되는가?
- 실제 인증·상품 API가 아직 구현되지 않았음을 결과에서 구분하는가?

## 실행 방법

GitHub Actions의 Repository Validation에서 OPS-006 MySQL service 뒤에 Backend test/build가 실행된다. 로컬에서는 실제 값이 아닌 격리된 MySQL의 다음 환경 변수가 필요하다.

```text
SPRING_DATASOURCE_URL=<격리된 MySQL JDBC URL>
SPRING_DATASOURCE_USERNAME=<격리된 test 사용자>
SPRING_DATASOURCE_PASSWORD=<격리된 test 비밀번호>
```

실제 값은 저장소, 문서, PR과 로그에 기록하지 않는다.

## 알려진 제한

- 현재 Backend test/build 성공은 실제 로그인 유스케이스 성공을 뜻하지 않는다.
- production Controller 없이 존재하지 않는 승인 경로의 404와 Security filter 선행 결과를 구분해 matcher를 검증한다.
- mutable `mysql:8.4` tag drift 위험은 OPS-006 승인 상태를 유지한다.
- 실제 deployment cookie와 reverse proxy 동작은 배포 환경에서 재검증해야 한다.

## 미결정 사항 또는 승인 필요 항목

- FOUNDATION-003 범위 안의 추가 승인 필요 항목은 없다.
- 실제 상품 상태 값, 상품 조회 query와 transaction은 후속 공개 상품 API 작업에서 결정한다.
- 실제 인증 principal·Authentication·logout transaction은 후속 세션 인증 작업에서 구현한다.
- cross-origin 또는 운영 배포 전제가 바뀌면 새 Technical Decision이 필요하다.

## QA 중단 조건

- production credential 또는 실제 회원 데이터가 필요함
- `subscriptions`나 제외된 API가 필요함
- CORS·cross-site cookie 또는 배포 정책 변경이 필요함
- CI MySQL service 또는 `.github/**` 변경이 필요함
- migration, JPA mapping, Security 기반 필수 검증 실패
