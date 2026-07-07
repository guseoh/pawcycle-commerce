# ARCH-004 Tech Lead to Product Owner 인수인계

## 전달 목적

첫 Backend 실제 구현 전에 Product Owner가 직접 선택해야 할 기술·설계 결정을 전달한다.

이번 인수인계는 구현 요청이 아니다. 신규 의존성, DB migration, JPA Entity, Repository, Service, Controller, DTO, SecurityConfig, Backend/Frontend 코드는 아직 추가하지 않았다.

## 사용자가 승인해야 할 결정 목록

| 번호 | 결정 | 기본 추천안 |
| --- | --- | --- |
| 1 | 첫 Backend 구현 범위 | 상품 목록, 상품 상세, 구독 생성, 내 구독 목록, 내 구독 상세의 최소 API 5개 |
| 2 | 신규 의존성 도입 범위 | JPA, MySQL JDBC driver, Flyway, Spring Security, `spring-security-test`; Testcontainers와 OpenAPI 도구는 별도 결정 |
| 3 | `DATA-001` 사용 여부 | 첫 DB schema 입력으로 사용 |
| 4 | Flyway와 최초 migration | Flyway 도입, 최초 V1 migration 작성 |
| 5 | MySQL 연결과 Secret 전달 | 환경 변수 기반, 실제 Secret 저장 금지 |
| 6 | `AUTH-001` 사용 여부 | Spring Security 경계 기준으로 사용 |
| 7 | 인증 방식 | 첫 MVP는 session 기반 우선 |
| 8 | CSRF, cookie, principal | CSRF 사용, HttpOnly/SameSite cookie 후보, 최소 `memberId` principal |
| 9 | `API-001` 사용 여부 | 5개 API 후보를 Controller 계약 기준으로 사용 |
| 10 | 오류 응답 JSON | `code`, `message`, `fieldErrors` 공통 구조 |
| 11 | 구현 아키텍처 | `ARCH-001` 책임 경계와 `ARCH-003` feature package 후보 사용 |
| 12 | CI 확장 범위 | 첫 구현 PR은 기존 Gradle/npm 검증 유지, DB/OpenAPI CI 확장은 별도 작업 |

## 승인하지 않으면 다음 구현에서 멈추는 지점

| 결정 | 멈추는 지점 |
| --- | --- |
| 첫 구현 범위 | Backend Engineer가 어떤 API부터 구현할지 정할 수 없다. |
| 신규 의존성 | `build.gradle` 변경을 할 수 없어 JPA, DB, Security 기반 구현이 멈춘다. |
| `DATA-001` 사용 여부 | Flyway migration과 JPA Entity 설계를 시작할 수 없다. |
| Flyway 여부 | schema 변경 이력의 시작 방식을 정할 수 없다. |
| MySQL 연결과 Secret 전달 | 로컬 DB 실행과 환경 변수 런북을 작성할 수 없다. |
| `AUTH-001` 사용 여부 | 보호 API 인증·인가 경계를 구현할 수 없다. |
| session/token | Spring Security 설정 방향이 정해지지 않는다. |
| CSRF/cookie/principal | 구독 생성 POST와 현재 회원 식별 구현이 멈춘다. |
| `API-001` 사용 여부 | Controller URI, DTO, status, 오류 code를 구현할 수 없다. |
| 오류 응답 JSON | FE와 QA가 실패 응답을 검증할 기준이 없다. |
| 구현 아키텍처 | package, transaction, exception handler 위치가 정해지지 않는다. |
| CI 확장 | 첫 구현 PR에서 어느 검증까지 요구할지 정할 수 없다. |

## 승인 후 진행 가능한 다음 작업

1. Backend 첫 수직 MVP 최소 구현 작업
2. DATA 후속 작업: 첫 schema와 Flyway migration 세부 검토
3. AUTH 후속 작업: session, CSRF, principal, Open Redirect 테스트 기준 보완
4. API 후속 작업: 오류 응답 구조와 DTO field 세부 검토
5. SRE 후속 작업: MySQL service, Testcontainers, OpenAPI CI 확장 검토

## 구현 전 금지 사항

- 신규 의존성 추가
- DB migration 작성
- JPA Entity, Repository, Service, Controller, DTO, SecurityConfig 작성
- Backend/Frontend 코드 변경
- 실제 Secret, password, token, 외부 알림 URL, DB 접속 정보 저장
- `.github/**` workflow 변경
- `DATA-001`, `API-001`, `AUTH-001`, `ARCH-001`, `FOUNDATION-000` 상태 변경
- reset, rebase, force push, history rewrite

## 위험과 확인 질문

- 첫 구현 범위를 최소 API 5개로 잡으면 실제 수직 흐름을 검증할 수 있지만 DB/API/Auth 결정을 한 번에 요구한다.
- Testcontainers와 OpenAPI 검증 도구를 분리하면 첫 구현은 단순해지지만 DB 통합 검증과 계약 drift 방지는 후속 위험으로 남는다.
- session 기반을 선택하면 CSRF와 cookie 속성까지 함께 다뤄야 한다.
- 환경 변수 기반 MySQL 연결은 Secret 원칙에 맞지만 개발자 로컬 DB 준비가 필요하다.
- 존재하지 않는 구독과 다른 회원 소유 구독을 같은 오류로 표현하는 API-001 후보를 유지할지 확인이 필요하다.
- `code`, `message`, `fieldErrors` 구조를 유지할지, Spring `ProblemDetail` 기반으로 바꿀지 확인이 필요하다.

## Product Owner 검토 요청

`docs/adr/ARCH-004-backend-implementation-decision-request.md`의 12개 결정 항목에서 선택지를 검토하고, 기본 추천안을 그대로 사용할지 또는 수정할 항목이 있는지 결정해 주세요.
