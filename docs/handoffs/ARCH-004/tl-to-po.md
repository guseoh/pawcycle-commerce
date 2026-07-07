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

## 리뷰 반영으로 추가된 보완 결정

| 보완 결정 | Product Owner가 함께 봐야 할 이유 |
| --- | --- |
| DB 의존성 승인 시 CI 검증 경로 | JPA, MySQL JDBC driver, Flyway를 도입하면 기존 `SpringBootTest`와 Application validation이 datasource/Flyway 설정 때문에 실패할 수 있다. |
| 인증 주체 생성 경로 | 보호 API를 첫 구현에 포함하려면 인증된 session과 `memberId` principal이 어디서 생성되는지 정해야 한다. |
| API 인증 실패 JSON 응답 계약 | session 기반 인증을 쓰더라도 `/api/**` 보호 API는 로그인 페이지 redirect/HTML이 아니라 `401`/`403` JSON 응답을 우선해야 한다. |
| CSRF token 전달 계약 | CSRF를 사용하면 FE가 token을 어떻게 받고 어떤 header로 보낼지 정해야 `POST /api/subscriptions`가 403으로 막히지 않는다. |

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
| 인증 주체 생성 경로 | 보호 API에서 신뢰할 수 있는 `memberId` principal을 공급할 수 없다. |
| CSRF/cookie/principal | 구독 생성 POST와 현재 회원 식별 구현이 멈춘다. |
| API 인증 실패 JSON 응답 | API-001의 `AUTH_REQUIRED` 후보와 실제 Security 실패 응답이 어긋날 수 있다. |
| CSRF token 전달 | 보호 POST API가 403으로 막히거나 FE 구현자가 임의 전달 방식을 만들 수 있다. |
| `API-001` 사용 여부 | Controller URI, DTO, status, 오류 code를 구현할 수 없다. |
| 오류 응답 JSON | FE와 QA가 실패 응답을 검증할 기준이 없다. |
| 구현 아키텍처 | package, transaction, exception handler 위치가 정해지지 않는다. |
| CI 확장 | 첫 구현 PR에서 어느 검증까지 요구할지 정할 수 없다. DB 의존성을 도입한다면 test profile 기반 datasource와 Flyway 처리 경로도 같이 정해야 한다. |

## 승인 후 진행 가능한 다음 작업

1. Backend 첫 수직 MVP 최소 구현 작업
2. DATA 후속 작업: 첫 schema와 Flyway migration 세부 검토
3. AUTH 후속 작업: session, CSRF, principal, Open Redirect 테스트 기준 보완
4. API 후속 작업: 오류 응답 구조와 DTO field 세부 검토
5. SRE 후속 작업: MySQL service, Testcontainers, OpenAPI CI 확장 검토

## 구현 전 금지 사항

- 신규 의존성 추가
- DB migration 작성
- test profile, datasource 설정, Flyway 설정 작성
- JPA Entity, Repository, Service, Controller, DTO, SecurityConfig 작성
- AuthenticationEntryPoint, AccessDeniedHandler 또는 동등한 Security 예외 처리 코드 작성
- CSRF 설정 또는 FE API client 작성
- Backend/Frontend 코드 변경
- 실제 Secret, password, token, 외부 알림 URL, DB 접속 정보 저장
- `.github/**` workflow 변경
- `DATA-001`, `API-001`, `AUTH-001`, `ARCH-001`, `FOUNDATION-000` 상태 변경
- reset, rebase, force push, history rewrite

## 위험과 확인 질문

- 첫 구현 범위를 최소 API 5개로 잡으면 실제 수직 흐름을 검증할 수 있지만 DB/API/Auth 결정을 한 번에 요구한다.
- DB 의존성을 승인할 때 CI 검증 경로를 같이 고르지 않으면 다음 Backend 구현 PR에서 Application validation이 실패할 수 있다.
- Testcontainers와 OpenAPI 검증 도구를 분리하면 첫 구현은 단순해지지만 DB 통합 검증과 계약 drift 방지는 후속 위험으로 남는다.
- 보호 API를 승인하려면 인증 주체 생성 경로도 함께 골라야 한다. 임시 고정 member나 요청 body/query의 `memberId`를 신뢰하는 방식은 피해야 한다.
- session 기반을 선택하면 CSRF, cookie 속성, `/api/**` 인증 실패 JSON 응답 기준까지 함께 다뤄야 한다.
- CSRF 사용을 선택하면 token 노출 방식, cookie/header 이름, FE 전송 규칙을 같이 정해야 한다.
- 환경 변수 기반 MySQL 연결은 Secret 원칙에 맞지만 개발자 로컬 DB 준비가 필요하다.
- 존재하지 않는 구독과 다른 회원 소유 구독을 같은 오류로 표현하는 API-001 후보를 유지할지 확인이 필요하다.
- `code`, `message`, `fieldErrors` 구조를 유지할지, Spring `ProblemDetail` 기반으로 바꿀지 확인이 필요하다.

## Product Owner 검토 요청

`docs/adr/ARCH-004-backend-implementation-decision-request.md`의 12개 결정 항목에서 선택지를 검토하고, 기본 추천안을 그대로 사용할지 또는 수정할 항목이 있는지 결정해 주세요.
