# ARCH-006 첫 Backend 구현 승인 입력

## 문서 정보

- 작업 ID: `ARCH-006`
- 역할: Tech Lead
- 문서 상태: Approved Inputs
- 기준 브랜치: `main`
- 작업 브랜치: `ops/tl`
- 선행 작업: ARCH-005
- 선행 PR: #28 `docs(architecture): Backend 구현 승인 후보 정리`
- PR #28 상태: 2026-07-10 병합 완료

## 목적

ARCH-005의 `Decision Required` 항목 중 사용자가 ARCH-006 요청에서 명시적으로 선택한 값만 첫 Backend 구현의 승인 입력으로 기록한다.

이 문서는 실제 Backend 구현이 아니다. ARCH-005나 관련 Proposed 문서 전체를 포괄 승인하지 않으며, 사용자가 선택하지 않은 세부값을 승인된 결정으로 만들지 않는다.

## 승인 해석 원칙

- PR #28 병합은 ARCH-005 결정 후보 문서가 `main`에 반영됐다는 의미이며 개별 후보 승인으로 해석하지 않는다.
- 사용자 승인 입력과 충돌하지 않는 승인 요구사항·도메인 문서는 구현 제약과 인수 조건으로 사용한다.
- `DATA-001`, `DATA-002`, `API-001`, `AUTH-001`, `ARCH-001`, `ARCH-003`, `FOUNDATION-000`은 원본 상태를 유지한다.
- Proposed 문서는 사용자가 이번 요청에서 지정한 범위만 구현 입력으로 사용한다.
- 사용자 입력에서 세부 계약 보완을 요구한 항목은 `Decision Required`로 남긴다.
- `Approved`는 첫 Backend 구현에서 사용할 수 있는 입력, `Deferred`는 후속 작업 전까지 보류할 입력, `Decision Required`는 구현 전 또는 구현 PR 검토에서 별도 결정해야 할 입력, `Explicitly Excluded`는 현재 작업이나 첫 구현에서 변경·구현하면 안 되는 범위다.

## 사용자 승인 입력 매핑

| 번호 | 사용자 선택 | ARCH-006 판정 |
| --- | --- | --- |
| 1 | 공개 상품 API 2개와 최소 세션 로그인 기반 | Approved |
| 2 | JPA, MySQL JDBC, Flyway, Spring Security, security test | Approved, 나머지 후보 Deferred |
| 3 | DATA-001·DATA-002를 `members`, `products`, `skus` 구현 입력으로 사용 | Approved, 구독·결제·배송·재고 Deferred |
| 4 | Flyway와 최초 V1 migration | Approved |
| 5 | 환경 변수 기반 로컬 MySQL | Approved |
| 6 | AUTH-001을 Spring Security·세션 로그인 기준으로 사용 | Approved, 충돌·미결정 발견 시 중단 |
| 7 | session 기반 인증 | Approved, JWT Deferred |
| 8 | CSRF 활성화, HttpOnly cookie·principal·BCrypt 기준 | Approved, cookie 세부 정책·CSRF 전달 계약 Decision Required |
| 9 | API-001 공개 상품 API 2개만 사용 | Approved, 구독 API Deferred |
| 10 | `code`·`message`·`fieldErrors` 공통 오류 응답 | Approved |
| 11 | ARCH-001·ARCH-003 package·계층 경계 | Approved, 지정 범위 밖 구조 Excluded |
| 12 | 기존 검증, Java 25, DB·Flyway·Security·MySQL CI 검증 | Approved, Testcontainers·OpenAPI 자동화 Deferred |
| 13 | 최소 로그인과 session 생성 | Approved |
| 14 | `/api/**` 인증·인가 실패는 401·403 JSON | Approved |
| 15 | JWT 확장 방향 | Deferred |
| 16 | 범위 금지 조건과 필요 시 PR 분할 순서 | Approved scope guard |

## Approved

### A1. 첫 Backend 구현 범위

- 승인 내용: 공개 상품 목록 `GET /api/products`, 공개 상품 상세 `GET /api/products/{productId}`와 최소 세션 로그인 기반을 첫 Backend 구현 범위로 사용한다. 최소 세션 로그인 기반에는 회원 인증 정보 조회, 로그인, 로그아웃, 세션 생성·무효화, 현재 인증 회원 식별이 포함된다.
- 승인 근거: 사용자 승인 입력 1, 13, 16과 ARCH-005 결정 항목 1·보완 결정 항목 2.
- 적용 범위: `member` 인증 최소 범위, `product`, `sku`, Spring Security 기본 경계, 공개 경로와 인증 필요 경로의 명시적 분리.
- 제외 범위: 구독 생성, 내 구독 목록, 내 구독 상세, JWT, OAuth2, 관리자 인증·인가.
- 구현 시 검증 조건: 두 공개 API는 비회원과 로그인 회원 모두 호출 가능해야 한다. 로그인 성공은 유효한 인증 주체와 세션을 만들고, 실패는 세션을 인증 상태로 만들지 않아야 한다. 로그아웃은 SecurityContext와 세션을 정리해야 한다.
- 관련 요구사항·설계 문서: `REQ-PRODUCT-001`, `REQ-PRODUCT-002`, `REQ-AUTH-001`, `PS-002`, `ARCH-001`, `API-001`, `AUTH-001`.
- 위험: 로그인·로그아웃·현재 회원 조회의 세부 API 계약이 기존 문서에 없으므로 최소 계약 보완 전 임의 URI·필드·상태를 확정하면 Backend와 Frontend 계약이 갈릴 수 있다.

### A2. 신규 의존성 범위

- 승인 내용: Spring Data JPA, MySQL JDBC Driver, Flyway, Spring Security, `spring-security-test`를 첫 Backend 구현에 추가할 수 있다.
- 승인 근거: 사용자 승인 입력 2와 ARCH-005 결정 항목 2.
- 적용 범위: `members`, `products`, `skus` 영속성, V1 migration, 세션 인증과 Security 테스트에 필요한 최소 의존성.
- 제외 범위: Testcontainers, OpenAPI 자동 검증 도구, JWT 관련 라이브러리, OAuth2 Client와 승인되지 않은 추가 라이브러리.
- 구현 시 검증 조건: 의존성 목록과 도입 이유를 구현 PR에서 설명하고, Java 25 기준 Backend `test`와 `build`가 통과해야 한다. 사용하지 않는 의존성을 추가하면 안 된다.
- 관련 요구사항·설계 문서: `ARCH-003`, `ARCH-005`, `FOUNDATION-000`, `FOUNDATION-001`.
- 위험: Spring Boot·JDBC·Flyway·MySQL 조합의 실제 호환성을 검증하지 않으면 애플리케이션 시작이나 migration에서 실패할 수 있다.

### A3. 첫 DB schema 입력과 범위

- 승인 내용: `DATA-001`과 `DATA-002`를 함께 사용해 `members`, `products`, `skus`만 첫 DB schema와 JPA 구현 입력으로 삼는다. `products` 1:N `skus` 관계와 로그인·상품 조회에 필요한 최소 데이터만 구현한다.
- 승인 근거: 사용자 승인 입력 3, 16과 ARCH-005 결정 항목 3·보완 결정 항목 5.
- 적용 범위: 인증 회원 식별과 로그인에 필요한 최소 회원 데이터, 공개 상품 목록·상세 응답에 필요한 상품·SKU 데이터와 관계.
- 제외 범위: `subscriptions`, 결제, 주문, 배송, 재고, 구독 상태 전이, 삭제·탈퇴·보관·익명화 정책.
- 구현 시 검증 조건: fresh schema에서 세 테이블과 Product-SKU 관계가 재현돼야 한다. 공개 목록·상세 조회와 로그인 식별에 불필요한 컬럼·관계는 추가하지 않는다. SQL DDL과 JPA 매핑 근거를 구현 PR에서 설명한다.
- 관련 요구사항·설계 문서: `DATA-001`, `DATA-002`, `REQ-PRODUCT-001`, `REQ-PRODUCT-002`, `DOMAIN-001`, `API-001`.
- 위험: DATA 문서는 비밀번호 해시 저장 필드와 로그인 식별 계약을 확정하지 않았으므로 V1 작성 전에 최소 인증 데이터 계약 보완이 필요하다.

### A4. Flyway와 최초 V1 migration

- 승인 내용: Flyway를 도입하고 `members`, `products`, `skus`와 Product-SKU 관계를 포함하는 최초 V1 migration을 작성한다.
- 승인 근거: 사용자 승인 입력 4와 ARCH-005 결정 항목 4.
- 적용 범위: 로그인과 상품 조회에 필요한 최소 NOT NULL, unique, FK, CHECK 또는 동등 제약과 필요한 최소 index.
- 제외 범위: `subscriptions`, payment, order, inventory, delivery, JWT·refresh token 저장 구조.
- 구현 시 검증 조건: 비어 있는 MySQL schema에 V1이 한 번에 적용돼야 하며 반복 기동 시 재적용 오류가 없어야 한다. JPA 자동 schema 생성으로 Flyway를 대체하지 않는다. migration과 JPA mapping의 불일치를 테스트한다.
- 관련 요구사항·설계 문서: `DATA-001`, `DATA-002`, `ARCH-003`, `ARCH-005`.
- 위험: 실제 SQL 타입, 제약·인덱스 이름, MySQL CHECK 표현은 아직 승인된 고정값이 아니므로 구현 PR에서 검증 근거가 필요하다.

### A5. MySQL 연결과 Secret 경계

- 승인 내용: 로컬 MySQL 연결은 환경 변수 기반으로 구성하고 저장소에는 환경 변수 이름, placeholder, 실행·검증 방법과 실패 확인 사항만 기록한다.
- 승인 근거: 사용자 승인 입력 5와 ARCH-005 결정 항목 5.
- 적용 범위: 로컬 datasource와 test/CI datasource에 필요한 비밀값 주입 경계.
- 제외 범위: 실제 DB URL, 사용자명, 비밀번호, 개인 로컬 설정값, Docker·Docker Compose.
- 구현 시 검증 조건: 환경 변수가 없을 때 안전하게 실패해야 하고 실제 값이 저장소·보고서·PR·로그에 나타나면 안 된다. placeholder는 실제 접속 정보처럼 오인되지 않아야 한다.
- 관련 요구사항·설계 문서: `AGENTS.md` Secret 관리, `ARCH-005`, `FOUNDATION-000`, `FOUNDATION-001` 로컬 개발 런북.
- 위험: 환경 변수 이름과 profile 우선순위가 문서·코드·CI에서 다르면 로컬과 CI가 서로 다른 datasource를 사용할 수 있다.

### A6. AUTH-001과 session 인증

- 승인 내용: `AUTH-001`의 공개·보호 경계와 인증 컨텍스트 원칙을 첫 Spring Security와 세션 로그인 구현 기준으로 사용한다. 서버가 인증 상태를 관리하고 로그인 성공 시 세션과 SecurityContext를 만들며 로그아웃 시 정리한다.
- 승인 근거: 사용자 승인 입력 6, 7, 13과 ARCH-005 결정 항목 6·7·보완 결정 항목 2.
- 적용 범위: 최소 회원 인증 정보 조회, PasswordEncoder 검증, Authentication 생성, SecurityContext 저장, 세션 유지와 로그아웃.
- 제외 범위: JWT access/refresh token, 토큰 재발급·폐기 목록, session·JWT 동시 지원, 개발용 우회 인증.
- 구현 시 검증 조건: 로그인 성공·실패, 세션 유지, 로그아웃 후 접근, 고정 memberId 금지, 요청 body·query의 memberId 불신뢰를 자동 테스트한다. `AUTH-001`과 실제 구현 입력이 충돌하거나 미결정이면 구현을 중단한다.
- 관련 요구사항·설계 문서: `AUTH-001`, `REQ-AUTH-001`, `ARCH-001`, `DOMAIN-001`.
- 위험: 기존 AUTH-001은 인증 방식과 로그인 API 세부 계약을 의도적으로 보류했으므로 ARCH-006의 명시 선택 밖 세부값을 자동 승인하면 안 된다.

### A7. CSRF, session cookie, principal, 비밀번호

- 승인 내용: CSRF 보호를 활성화하고 상태 변경 요청에 유효한 CSRF token을 요구한다. 공개 GET 상품 API에는 token을 요구하지 않는다. session cookie는 HttpOnly를 사용하고 session id를 응답 body와 로그에 노출하지 않는다. principal은 최소 `memberId`만 제공하고 JPA Entity 전체를 담지 않는다. 비밀번호는 Spring Security `PasswordEncoder`와 `BCryptPasswordEncoder`로 검증한다.
- 승인 근거: 사용자 승인 입력 8, 13, 16과 ARCH-005 결정 항목 8·보완 결정 항목 4.
- 적용 범위: 브라우저 session 인증, 로그인·로그아웃 상태 변경, 현재 회원 식별, HttpOnly 적용과 session id 비노출.
- 제외 범위: CSRF 전체 비활성화, session id 응답 body·로그 노출, 평문 비밀번호 저장·비교·로그 출력, Entity principal, 클라이언트 memberId 신뢰, SameSite·Secure·domain·path·cookie 수명 확정.
- 구현 시 검증 조건: CSRF token 누락·오류 요청은 거부되고 승인된 전달 계약에 따른 유효한 token 요청은 처리돼야 한다. HttpOnly 적용과 session id 비노출을 확인하고, principal에서 `memberId`를 얻는 Security 테스트를 작성한다. 민감정보가 로그에 남지 않는지 확인한다.
- 관련 요구사항·설계 문서: `AUTH-001`, `ARCH-001`, `AGENTS.md` Secret 관리, Spring Security 구현 작업.
- 위험: CSRF 활성화만 Approved이며 token 전달 계약은 Decision Required다. HttpOnly만 승인된 cookie 요구사항이고 SameSite=Lax와 운영 Secure는 기본 후보일 뿐 확정 계약이 아니다. SameSite·Secure·domain·path·수명은 Frontend origin과 로컬·운영 배포 구성을 확인한 뒤 구현 PR에서 제안하고 검증해야 한다.

### A8. API-001 공개 상품 계약 사용 범위

- 승인 내용: `API-001` 중 `GET /api/products`와 `GET /api/products/{productId}` 두 공개 상품 API만 첫 Controller 계약 입력으로 사용한다.
- 승인 근거: 사용자 승인 입력 1, 9와 ARCH-005 결정 항목 9.
- 적용 범위: API-001의 공개 접근, URI, 성공 상태, 목록·상세 응답 필드, `PRODUCT_NOT_FOUND`와 `PRODUCT_LIST_UNAVAILABLE` 후보 중 승인 범위와 충돌하지 않는 부분.
- 제외 범위: `POST /api/subscriptions`, `GET /api/subscriptions`, `GET /api/subscriptions/{subscriptionId}`.
- 구현 시 검증 조건: 목록은 요구된 상품·SKU 요약을 반환하고, 상세는 Product와 SKU 목록·가격·구독 가능 여부를 반환해야 한다. 존재하지 않는 상품과 일시적 조회 실패를 계약에 맞게 테스트한다.
- 관련 요구사항·설계 문서: `API-001`, `REQ-PRODUCT-001`, `REQ-PRODUCT-002`, `DATA-001`, `DATA-002`.
- 위험: API-001 전체가 아니라 공개 상품 부분만 사용하므로 구독 DTO·오류 코드를 공통화 명목으로 미리 구현하면 범위가 확장된다.

### A9. 공통 오류와 API 인증 실패 응답

- 승인 내용: 공통 오류 응답은 `code`, `message`, `fieldErrors`를 사용하고 field error가 없으면 빈 배열을 사용한다. `/api/**` 미인증은 401 JSON, 권한 부족은 403 JSON, 로그인 실패는 401 JSON으로 반환하며 HTML이나 로그인 redirect를 API 응답으로 사용하지 않는다.
- 승인 근거: 사용자 승인 입력 10, 14와 ARCH-005 결정 항목 10·보완 결정 항목 3.
- 적용 범위: 공개 상품 API 실패, request validation, 로그인 실패, Spring Security AuthenticationEntryPoint·AccessDeniedHandler 또는 동등 경계.
- 제외 범위: HTML 오류 페이지, stack trace, 내부 예외 메시지, API 로그인 redirect, 승인되지 않은 ProblemDetail 전환.
- 구현 시 검증 조건: 정상·검증 실패·미인증·권한 부족·로그인 실패 응답의 Content-Type, HTTP status와 JSON shape를 테스트한다. `fieldErrors`는 항상 배열이어야 하며 내부 예외 정보가 없어야 한다.
- 관련 요구사항·설계 문서: `API-001`, `AUTH-001`, `ARCH-001`.
- 위험: 401과 403의 안정적인 애플리케이션 `code` 값은 아직 모두 정의되지 않았으므로 최소 인증 API 계약 보완이 필요하다.

### A10. 구현 package와 계층 경계

- 승인 내용: `ARCH-001`의 책임 경계와 `ARCH-003`의 feature package·계층 후보를 `member` 인증 최소 범위, `catalog/product/sku`, 공통 오류, security 구성에 한해 구현 기준으로 사용한다.
- 승인 근거: 사용자 승인 입력 11, 16과 ARCH-005 결정 항목 11.
- 적용 범위: `com.pawcycle.backend` 하위의 기능별 domain/application/infra/api 책임과 최소 `common.error`·`common.security` 경계.
- 제외 범위: 구독 package 구현, 주문·결제·배송·재고, JWT 전용 계층, 범용 인증 프레임워크, 사용되지 않는 추상화와 미래 확장 전용 인터페이스.
- 구현 시 검증 조건: package dependency와 transaction 책임을 PR에서 설명하고, 도메인·애플리케이션·API·영속성 책임이 역전되지 않았는지 자기 리뷰한다. 사용처 없는 추상화는 제거한다.
- 관련 요구사항·설계 문서: `ARCH-001`, `ARCH-003`, `DOMAIN-001`.
- 위험: ARCH-003 예시에는 `subscription` package가 포함되지만 ARCH-006은 이를 승인하지 않았다. 예시 구조 전체를 생성하면 명시 범위를 위반한다.

### A11. CI와 테스트 검증 범위

- 승인 내용: 기존 Repository Validation을 유지하고 Java 25 Backend test/build, test profile datasource, Flyway migration, Spring Security, 공개 API 정상·실패, 로그인 성공·실패, 미인증·인가 실패와 CI MySQL service 검증을 포함한다.
- 승인 근거: 사용자 승인 입력 12, 16과 ARCH-005 결정 항목 12·보완 결정 항목 1.
- 적용 범위: 첫 Backend 구현에 의해 추가되는 DB·Security·API 동작과 기존 저장소 검증.
- 제외 범위: Testcontainers, OpenAPI 자동 검증, JWT·OAuth2 테스트.
- 구현 시 검증 조건: DB·Security 의존성을 추가한 상태에서 Java 25 기준 `test`와 `build`, fresh Flyway migration, MySQL 기반 검증이 통과해야 한다. CI MySQL service나 workflow 변경이 Backend Engineer 경계를 벗어나면 Platform/SRE 작업을 먼저 분리한다.
- 관련 요구사항·설계 문서: `ARCH-003`, `ARCH-005`, `FOUNDATION-002` CI 런북, `docs/qa/README.md`.
- 위험: CI service 준비 없이 DB 의존성만 병합하면 원격 검증이 실패한다. 반대로 Backend 작업에서 `.github/**`를 직접 바꾸면 역할 경계를 위반할 수 있다.

### A12. 범위 통제와 구현 분할 조건

- 승인 내용: 첫 Backend 구현의 절대 상한은 A1~A11이며, 범위가 커지면 DB·Flyway·JPA·Security 기반, 세션 로그인·로그아웃, 공개 상품 API, QA 독립 검증 순서로 PR을 분리한다.
- 승인 근거: 사용자 승인 입력 15, 16.
- 적용 범위: Backend Engineer와 Platform/SRE·QA 간 작업 분할, 각 PR의 범위 통제.
- 제외 범위: 구독 API, JWT 확장 ADR과 구현은 앞 단계 검증 뒤 별도 작업으로 진행한다.
- 구현 시 검증 조건: 각 PR은 자체 승인 입력, 테스트, 중단 조건과 인수인계를 갖고 독립적으로 설명 가능해야 한다. 앞 단계 필수 검증이 실패하면 다음 단계로 넘어가지 않는다.
- 관련 요구사항·설계 문서: `AGENTS.md` 역할 경계, `ARCH-003` 구현 순서, `ARCH-005`, `docs/qa/README.md`.
- 위험: 승인 항목을 한 PR에 모두 넣으면 리뷰와 실패 원인 분리가 어려워질 수 있다. 반대로 기반만 추가하고 검증 가능한 사용 흐름을 남기지 않으면 불필요한 구조가 될 수 있다.

## Deferred

| 항목 | 보류 내용 | 다시 검토할 조건 |
| --- | --- | --- |
| 구독 API와 데이터 | 구독 생성, 내 구독 목록, 내 구독 상세, `subscriptions` schema와 package | 세션 인증과 공개 상품 API 검증 완료 후 별도 Backend 작업 |
| JWT | access/refresh token, 재발급, 폐기, key rotation, cookie/header 저장, session 전환·병행 | 모바일·외부 클라이언트, 독립 배포, 무상태 인증, 외부 API token 요구 또는 session 운영 한계가 검증된 경우 별도 ADR |
| OAuth2 | OAuth2 Client와 로그인 | 별도 사용자 승인과 인증 설계 작업 |
| Testcontainers | 의존성과 Docker 기반 DB 통합 테스트 | 로컬·CI 실행 비용과 image 정책을 승인한 후 |
| OpenAPI 자동화 | 생성·검증 도구와 build/CI task | 계약 drift 위험과 도구 운영 방식을 별도 승인한 후 |
| 확장 도메인 | 결제, 주문, 재고, 배송, 구독 상태 전이와 변경 기능 | 해당 제품 요구사항·데이터·API·QA 입력 승인 후 |

Deferred 항목은 후속 가능성을 기록한 것이며 첫 Backend 구현에서 미리 구조·의존성·코드를 만들 수 있다는 뜻이 아니다.

## Decision Required

### DR1. 최소 인증 API 계약 보완

`AUTH-001`과 `API-001`에는 다음 값이 확정돼 있지 않다.

- 로그인 식별자와 요청 필드
- 로그인·로그아웃·현재 인증 회원 조회의 URI와 HTTP method
- 성공 응답 status와 body
- 현재 회원 응답의 최소 필드
- 로그인 실패, 미인증, 권한 부족의 안정적인 application error code

사용자 승인 입력은 위 기능의 포함과 보안 방향을 승인했지만 세부 계약값을 승인하지 않았다. Backend 구현자는 임의 확정하지 않고 최소 계약 보완 작업으로 분리해야 한다.

### DR2. CSRF token 전달 계약

CSRF 보호 활성화만 Approved다. CSRF token 전달 계약은 Decision Required이며 Backend Engineer는 다음 값을 임의 확정하지 않는다.

- token 획득 방식
- cookie 기반 노출 여부
- response body 또는 별도 endpoint 사용 여부
- CSRF token 이름과 request header 이름
- cookie를 사용할 경우 cookie 이름과 JavaScript 접근 가능성
- token 갱신 시점
- 로그인 전후 token 처리
- 로그아웃 후 token 처리
- Frontend API client 전송 규칙

Frontend와의 전달 계약이 승인되기 전에는 CSRF repository나 전용 endpoint를 최종 구조로 확정하지 않는다. 상태 변경 API 구현 전에 Backend와 Frontend가 같은 계약을 사용하도록 보완해야 한다.

### DR3. 최소 인증 데이터 계약

`DATA-001`과 `DATA-002`의 `members` 후보에는 `email`과 표시 정보가 있지만 비밀번호 해시 저장 필드와 로그인 식별자의 최종 선택이 없다.

- 로그인 식별자로 `email`을 사용할지 여부
- 비밀번호 해시 필드의 이름, 타입, NOT NULL·길이 제약
- 테스트 회원 생성 경로와 seed 정책

평문 저장 금지와 BCrypt 사용은 Approved지만 위 물리 계약은 승인되지 않았다. V1 migration 작성 전에 최소 데이터 계약을 보완해야 한다.

### DR4. 구현 PR에서 제안·검증할 물리 세부

다음 값은 ARCH-006이 특정 값으로 승인하지 않는다. 다만 DATA 문서가 Backend 구현 단계에 위임한 범위이므로 승인 경계 안에서 구현 PR이 제안하고 검증할 수 있다.

- SQL 타입, FK·index·CHECK 이름과 MySQL CHECK 표현
- JPA 연관관계 방향, fetch, cascade와 audit 처리
- 환경 변수의 정확한 이름과 profile 우선순위
- MySQL service의 정확한 version/image
- SameSite 값, 개발·운영 환경별 Secure 적용 방식, Frontend·Backend cross-origin cookie 정책, domain, path와 cookie 수명
- API DTO 클래스명과 내부 예외 클래스명

사용자 승인 입력이나 상위 요구사항과 충돌하면 구현을 중단하고 별도 결정을 요청한다.

## Explicitly Excluded

### ARCH-006 승인 기록 작업에서 제외

- `backend/**`, `frontend/**`, `.github/**`
- build·settings·package 파일
- DB migration, JPA Entity, Repository, Service, Controller, DTO, SecurityConfig
- datasource·Flyway 설정, API client, 화면
- 신규 의존성, Secret, Docker, Docker Compose

### 첫 Backend 구현에서 제외

- 결제, 주문, 배송, 재고, 구독 상태와 상태 전이
- 관리자 기능과 관리자 인증·인가
- 실제 Secret 저장 또는 민감정보 로그 출력
- 요청 body·query·고정값의 `memberId`를 인증 주체로 사용
- CSRF 전체 비활성화
- JWT·OAuth2·구독 기능을 위한 선행 추상화
- 사용되지 않는 공통 계층과 미래 확장 전용 인터페이스

## 원본 문서 상태

ARCH-006은 다음 문서의 원본 상태를 변경하지 않는다.

- `DATA-001`: Proposed Data Design
- `DATA-002`: Proposed Logical ERD
- `API-001`: Proposed API Contract
- `AUTH-001`: Proposed Authentication Design
- `ARCH-001`: Proposed Architecture Decision
- `ARCH-003`: Proposed Backend Implementation Plan
- `FOUNDATION-000`: Proposed
- `ARCH-005`: Decision Candidates

첫 Backend 구현은 이 문서의 Approved 범위와 사용자 승인 입력을 통해 위 Proposed 문서의 일부를 제한적으로 사용할 수 있을 뿐이다.

## 다음 작업 진입 조건

- DR1~DR3 최소 계약 보완 또는 사용자 명시 결정
- Backend Engineer가 `backend/AGENTS.md`, 역할 문서와 Skill 확인
- CI MySQL service 변경이 필요하면 Platform/SRE 작업 선행
- 첫 구현 PR 범위와 분할 여부 명시
- QA 독립 검증 필요 여부와 문서 경로 결정
- 실제 Secret 없이 로컬·CI 검증 경로 준비
