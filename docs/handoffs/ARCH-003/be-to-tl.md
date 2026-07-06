# ARCH-003 Backend Engineer to Tech Lead 인수인계

## 전달 목적

첫 Backend 구현 전에 필요한 구현 계획, 의존성 도입 후보, 사용자 승인 필요 항목을 Tech Lead 검토 대상으로 전달한다.

이번 인수인계는 실제 Backend 구현 요청이 아니다. 신규 의존성, DB schema, migration, JPA Entity, Controller, Service, Repository, DTO, SecurityConfig는 아직 추가하지 않았다.

## Backend Engineer 권고

첫 Backend 구현은 바로 코드로 들어가기보다 의존성 승인과 DB/API/Auth 확정을 먼저 완료하는 편이 안전하다.

권고 순서:

1. 의존성 도입 범위 승인
2. `DATA-001` 기반 실제 DB schema와 Flyway 도입 여부 승인
3. `AUTH-001` 기반 Spring Security 구현 방식 승인
4. `API-001` 기반 Controller 계약 승인
5. `ARCH-001` 기반 패키지, 트랜잭션, 예외 처리 경계 승인
6. 첫 수직 MVP Backend 최소 구현 착수

구독 생성과 내 구독 조회 같은 보호 API는 인증 경계가 적용된 같은 변경에서만 추가한다.

## 사용자 결정 필요 항목

- `DATA-001`을 실제 DB schema로 확정할지 여부
- `API-001`을 실제 Controller 계약으로 확정할지 여부
- `AUTH-001`을 실제 Spring Security 구현 기준으로 확정할지 여부
- `ARCH-001`을 실제 구현 아키텍처로 확정할지 여부
- `FOUNDATION-000`의 기술 버전과 의존성 도입 방향을 승인할지 여부
- 첫 구현 범위를 공개 상품 조회, 구독 생성, 내 구독 조회로 제한할지 여부
- 구독 생성 시 상품명과 가격 snapshot 저장 여부
- Product와 SKU Aggregate 경계
- API 오류 응답 공통 구조
- 보호 API 인증 방식과 principal 구조

## 의존성 추가 승인 요청

첫 구현에서 검토할 의존성 후보:

- `spring-boot-starter-data-jpa`
- MySQL JDBC driver
- Flyway
- `spring-boot-starter-security`
- `spring-security-test`
- Testcontainers
- OpenAPI generation/validation tool

승인 없이 위 의존성은 build file에 추가하지 않는다.

## DB schema 승인 요청

DB 구현 전 확정이 필요한 항목:

- 실제 table name과 column name
- PK, FK, index
- audit column 기준
- `subscribable` 저장 방식
- `delivery_cycle_weeks` 저장 방식
- `created_date`, `next_order_date`, `created_at`, `updated_at` 저장 기준
- 첫 Flyway migration 작성 여부
- migration versioning과 naming 규칙
- MySQL 로컬 실행 방식과 Secret 전달 방식

## API 계약 승인 요청

API 구현 전 확정이 필요한 항목:

- 실제 URI와 HTTP method
- request/response DTO field
- 성공 HTTP status
- 검증 실패와 도메인 오류의 HTTP status
- 오류 응답 JSON 구조
- 존재하지 않는 구독과 타인 구독 접근의 응답 통합 여부
- OpenAPI 문서 생성 또는 검증 방식

## 인증 방식 승인 요청

보호 API 구현 전 확정이 필요한 항목:

- session 기반인지 token 기반인지 여부
- cookie 속성과 CSRF 정책
- 인증 실패 응답 방식
- 로그인 후 복귀 경로 저장 방식
- Open Redirect 방지 검증 위치
- 인증 context에서 member 식별자를 얻는 principal 구조
- Security 테스트 범위

## CI 확장 필요

신규 의존성이 승인되면 CI 확장 검토가 필요하다.

- Flyway migration 적용 검증
- MySQL 기반 Repository 또는 integration test
- Security test
- API contract 검증
- Testcontainers 사용 시 Docker 실행 조건
- Backend build cache와 dependency resolution 정책

이 항목은 Platform/SRE 또는 Tech Lead 후속 작업에서 다루는 것이 좋다.

## 다음 작업 후보

후보 1:

```text
ARCH-004 또는 FOUNDATION-003: Backend 의존성 승인과 도입 범위 확정
```

후보 2:

```text
승인 이후 별도 Backend 구현 작업: 첫 수직 MVP Backend 최소 구현
```

실제 다음 작업 ID는 사용자가 하네스에서 허용하는 접두사로 별도 선택해야 한다.

## 중단 조건

다음 조건이 있으면 Backend 구현 작업을 시작하지 않는다.

- 승인되지 않은 Proposed 문서 내용을 실제 구현 기준으로 사용해야 하는 경우
- 신규 의존성 추가가 필요한데 사용자 승인이 없는 경우
- DB schema, Flyway, MySQL 연결 정책이 확정되지 않은 경우
- API 계약과 오류 응답 구조가 확정되지 않은 경우
- 인증 방식, CSRF, principal 구조가 확정되지 않은 경우
- Secret 전달 방식이 불명확한 경우
- 트랜잭션 경계와 실패 상태를 설명할 수 없는 경우
