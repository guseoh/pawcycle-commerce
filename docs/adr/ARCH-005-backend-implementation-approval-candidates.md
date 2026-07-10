# ARCH-005 Backend 구현 승인 후보 정리

## 문서 정보

- 작업 ID: `ARCH-005`
- 역할: Tech Lead
- 문서 상태: Decision Candidates
- 기준 브랜치: `main`
- 작업 브랜치: `ops/tl`
- 선행 PR: #26 `chore(harness): 역할과 산출물 검증 강화`
- 선행 PR: #27 `docs(data): 첫 MVP 논리 ERD 정리`

## 목적

ARCH-004의 Backend 구현 결정 요청과 DATA-002 논리 ERD를 함께 검토해, 사용자 승인 전 첫 Backend 구현 후보를 정리한다.

이번 문서는 실제 Backend 구현 작업이 아니다. 신규 의존성, DB migration, JPA Entity, Repository, Service, Controller, DTO, SecurityConfig는 작성하지 않는다.

## 권한 구분

- 사용자는 Product Owner이자 Tech Lead로 최종 제품 결정, 기술 결정, 위험 수용, PR 병합을 직접 결정한다.
- AI Tech Lead 보조 역할은 승인 가능 여부를 검토하고 선택지, 추천안, 영향, 위험, 중단 지점을 정리한다.
- 사용자 명시 결정이 없는 항목은 Decision Required 또는 Pending으로 남긴다.
- 이 문서는 어떤 Proposed 문서도 최종 입력으로 바꾸지 않는다.

## ARCH-004 결정 항목 요약

| 번호 | 결정 항목 | ARCH-004 기본 방향 | ARCH-005 상태 |
| --- | --- | --- | --- |
| 1 | 첫 Backend 구현 범위 | 상품 목록, 상품 상세, 구독 생성, 내 구독 목록, 내 구독 상세 API 5개 후보 | Decision Required |
| 2 | 신규 의존성 도입 범위 | JPA, MySQL JDBC, Flyway, Spring Security, security test 우선 후보 | Decision Required |
| 3 | DATA-001과 DATA-002를 실제 DB schema 입력으로 사용할지 여부 | DATA-001과 DATA-002를 함께 검토하되 최종 DB 입력 여부는 사용자 결정 | Decision Required |
| 4 | Flyway 도입 여부와 최초 migration 작성 여부 | Flyway와 최초 V1 migration 후보 | Decision Required |
| 5 | MySQL 연결 정책과 Secret 전달 방식 | 환경 변수 기반 로컬 MySQL 후보 | Decision Required |
| 6 | AUTH-001을 Spring Security 구현 기준으로 사용할지 여부 | 보호 API 경계 기준 후보 | Decision Required |
| 7 | 인증 방식 | session 기반 우선 후보, token 기반은 보류 후보 | Decision Required |
| 8 | CSRF, cookie, principal 구조 | session cookie, CSRF, `memberId` principal 후보 | Decision Required |
| 9 | API-001을 Controller 계약으로 사용할지 여부 | API-001의 5개 API 후보 사용 | Decision Required |
| 10 | 오류 응답 JSON 구조 | `code`, `message`, `fieldErrors` 후보 | Decision Required |
| 11 | ARCH-001을 구현 아키텍처 기준으로 사용할지 여부 | feature package와 계층 책임 후보 | Decision Required |
| 12 | CI 확장 범위 | 기존 Repository Validation 유지, DB/OpenAPI 확장은 별도 후보 | Decision Required |

## DATA-002 반영 요약

- DATA-002는 Proposed 논리 ERD 보완 산출물이며 최종 DB schema가 아니다.
- DATA-002는 `members`, `products`, `skus`, `subscriptions` 논리 관계 후보를 제시한다.
- `subscriptions.member_id`는 회원 소유자 검증 후보이고, `subscriptions.sku_id`는 SKU 단일 구독 대상 후보다.
- `next_order_date`, `delivery_cycle_weeks`, `quantity`, `subscribable`은 도메인/애플리케이션 검증과 DB 제약 후보를 함께 검토해야 한다.
- DATA-002는 현재 구독 상태 필드를 첫 MVP에 넣지 않는다. 결제, 배송, 재고, 구독 상태 전이는 후속 MVP 후보로 남긴다.
- 실제 SQL DDL, DB 타입, FK/인덱스 이름, JPA 매핑은 후속 사용자 결정이 필요하다.

## 보완 결정 항목 요약

| 번호 | 보완 결정 항목 | 필요한 이유 | 상태 |
| --- | --- | --- | --- |
| 1 | DB 의존성 도입 시 CI 검증 경로 | JPA/Flyway/MySQL 도입 후 GitHub Actions test/build 실패 방지 | Decision Required |
| 2 | 인증 주체 생성 경로 | 보호 API가 요청 body/query의 `memberId`를 신뢰하지 않도록 하기 위함 | Decision Required |
| 3 | API 인증 실패 응답 계약 | 브라우저 redirect와 `/api/**` JSON 실패 응답을 분리하기 위함 | Decision Required |
| 4 | CSRF 토큰 전달 계약 | 보호 POST API와 FE 전송 규칙을 맞추기 위함 | Decision Required |
| 5 | DATA-002 논리 ERD를 Flyway/JPA 구현 입력으로 사용할지 여부 | DATA-001만으로 부족했던 관계·제약 후보를 함께 볼지 결정하기 위함 | Decision Required |

## 기본 추천안

AI Tech Lead 보조 추천안은 다음과 같다.

1. 첫 Backend 구현 후보는 API 5개로 잡되, 보호 API 3개는 인증 주체 생성 경로가 같이 결정될 때만 포함한다.
2. JPA, MySQL JDBC, Flyway, Spring Security, `spring-security-test`를 첫 구현 후보로 묶고, Testcontainers와 OpenAPI 검증 도구는 보류한다.
3. DATA-001과 DATA-002를 함께 첫 DB schema 후보로 사용하되, 실제 table, column, FK, index, DB 타입은 Backend 구현 PR에서 다시 설명 가능하게 문서화한다.
4. session 기반 인증을 우선 검토하고, `/api/**` 보호 API는 `401`/`403` JSON 응답 계약을 별도로 둔다.
5. DB 의존성 도입 시 test profile datasource와 Flyway 처리 방식을 같은 결정 묶음에 넣는다.

## 결정 항목 1. 첫 Backend 구현 범위

ARCH-004 / DATA-002 기준:

상품 목록, 상품 상세, 구독 생성, 내 구독 목록, 내 구독 상세가 첫 수직 MVP의 최소 흐름 후보다. DATA-002는 이 범위를 `products`, `skus`, `members`, `subscriptions` 관계 후보로 보완한다.

승인하면 가능한 작업:

Backend Engineer가 첫 수직 MVP API 5개 구현 계획을 상세화하고, 필요한 domain/application/api/persistence 테스트 범위를 작성할 수 있다.

승인하지 않으면 멈추는 지점:

실제 Controller, Service, Repository, DTO 구현은 시작하지 않는다. 공개 상품 API만 먼저 할지, 추가 결정 문서를 쪼갤지 다시 정해야 한다.

AI Tech Lead 보조 검토 의견:

구독 생성과 내 구독 조회까지 연결되어야 수직 MVP 가치가 보인다. 다만 보호 API는 인증 주체 생성 경로가 없으면 보안 경계가 흐려진다.

사용자 확인 질문:

첫 Backend 구현 범위를 API 5개로 둘까요, 아니면 공개 상품 API 2개를 먼저 분리할까요?

상태:
- Decision Required

## 결정 항목 2. 신규 의존성 도입 범위

ARCH-004 / DATA-002 기준:

현재 backend는 webmvc, validation, test 기반이다. DATA-002 후보를 실제 DB에 연결하려면 JPA, MySQL JDBC, Flyway가 필요하고 보호 API에는 Spring Security와 security test가 필요하다.

승인하면 가능한 작업:

Backend Engineer가 build file 변경, 테스트 범위, local profile, CI 영향 메모를 포함한 구현 PR을 준비할 수 있다.

승인하지 않으면 멈추는 지점:

영속성, migration, 보호 API 구현을 시작하지 않는다. 임시 저장소나 임시 인증은 만들지 않는다.

AI Tech Lead 보조 검토 의견:

첫 구현에는 JPA, MySQL JDBC, Flyway, Spring Security, `spring-security-test`까지를 후보로 두고 Testcontainers와 OpenAPI 검증 도구는 분리하는 편이 범위 관리에 안전하다.

사용자 확인 질문:

첫 Backend 구현에서 위 5개 의존성 후보를 허용할까요?

상태:
- Decision Required

## 결정 항목 3. DATA-001과 DATA-002를 실제 DB schema 입력으로 사용할지 여부

ARCH-004 / DATA-002 기준:

DATA-001과 DATA-002는 모두 Proposed 데이터 설계 입력이다. DATA-002는 DATA-001의 테이블 후보에 논리 ERD, 관계, 제약, 인덱스 후보를 보완했다.

승인하면 가능한 작업:

Backend Engineer가 `members`, `products`, `skus`, `subscriptions`의 첫 migration 후보와 JPA Entity 후보를 작성할 수 있다.

승인하지 않으면 멈추는 지점:

Flyway migration, JPA Entity, Repository 구현을 시작하지 않는다. DATA 후속 결정 작업으로 table/column/FK/index를 더 좁혀야 한다.

AI Tech Lead 보조 검토 의견:

첫 MVP 구독 생성과 조회는 DB 없이 검증 가치가 낮다. 다만 DATA-002는 최종 schema가 아니므로 Backend 구현 PR에서 SQL DDL과 매핑 근거를 다시 설명해야 한다.

사용자 확인 질문:

DATA-001과 DATA-002를 함께 첫 DB schema 후보 입력으로 사용해도 될까요?

상태:
- Decision Required

## 결정 항목 4. Flyway 도입 여부와 최초 migration 작성 여부

ARCH-004 / DATA-002 기준:

FOUNDATION-000과 ARCH-004는 Flyway를 schema 변경 이력 후보로 제안했다. DATA-002는 최초 schema 후보를 논리 수준으로 정리했다.

승인하면 가능한 작업:

Backend Engineer가 최초 migration 파일, migration naming, test profile 적용 방식을 제안할 수 있다.

승인하지 않으면 멈추는 지점:

DB schema 변경 이력을 시작하지 않는다. JPA schema generation만 쓰는 임시 방향도 사용자 결정 없이 선택하지 않는다.

AI Tech Lead 보조 검토 의견:

첫 migration은 main 병합 후 되돌리기 비용이 크므로 최소 table과 제약만 담는 후보로 시작하는 편이 안전하다.

사용자 확인 질문:

Flyway를 첫 Backend 구현에 포함하고 최초 schema migration 후보를 작성해도 될까요?

상태:
- Decision Required

## 결정 항목 5. MySQL 연결 정책과 Secret 전달 방식

ARCH-004 / DATA-002 기준:

MySQL 8.4 LTS는 기술 후보이나 로컬 DB 실행 방식, 접속 URL, 계정, 비밀번호 전달 방식은 아직 결정되지 않았다.

승인하면 가능한 작업:

Backend Engineer가 환경 변수 placeholder와 safe-fail 설정을 문서화하고, 실제 Secret 없이 로컬 실행 경로를 준비할 수 있다.

승인하지 않으면 멈추는 지점:

DB 연결 설정, datasource 설정, Secret 이름, 로컬 properties 파일은 만들지 않는다.

AI Tech Lead 보조 검토 의견:

첫 구현은 환경 변수 기반 로컬 MySQL 정책이 Secret 원칙에 맞다. Docker Compose와 CI DB service는 SRE 후속 후보로 분리한다.

사용자 확인 질문:

MySQL 접속 정보는 환경 변수로만 전달하고 실제 Secret 값은 저장소에 남기지 않는 방식으로 둘까요?

상태:
- Decision Required

## 결정 항목 6. AUTH-001을 Spring Security 구현 기준으로 사용할지 여부

ARCH-004 / DATA-002 기준:

AUTH-001은 공개 기능과 보호 기능, 로그인 복귀, 소유자 검증, Open Redirect 방지 후보를 정리했다.

승인하면 가능한 작업:

Backend Engineer가 공개 API와 보호 API 경계를 Spring Security 설정과 테스트로 구현할 수 있다.

승인하지 않으면 멈추는 지점:

SecurityConfig, 인증 필터, 보호 API Controller 구현을 시작하지 않는다.

AI Tech Lead 보조 검토 의견:

구독 생성과 내 구독 조회는 회원 본인 데이터이므로 AUTH-001을 보호 경계 후보로 쓰는 편이 안전하다.

사용자 확인 질문:

AUTH-001을 첫 Backend 구현의 Spring Security 경계 기준 후보로 사용할까요?

상태:
- Decision Required

## 결정 항목 7. 인증 방식: session 기반 또는 token 기반

ARCH-004 / DATA-002 기준:

AUTH-001은 session과 token 중 하나를 고르지 않았다. 브라우저 기반 MVP와 로그인 후 내부 GET 복귀 흐름은 session 후보와 잘 맞는다.

승인하면 가능한 작업:

Backend Engineer가 선택된 인증 방식에 맞는 로그인/session 또는 token 처리 후보와 테스트 범위를 제안할 수 있다.

승인하지 않으면 멈추는 지점:

보호 API 구현은 보류한다. 공개 상품 API만 먼저 구현할 수 있는지 별도 결정해야 한다.

AI Tech Lead 보조 검토 의견:

현재는 session 기반을 기본 추천한다. token 기반은 발급, 만료, 저장, refresh 정책이 추가로 필요하다.

사용자 확인 질문:

첫 MVP 인증 방식은 session 기반으로 검토할까요?

상태:
- Decision Required

## 결정 항목 8. CSRF, cookie, principal 구조

ARCH-004 / DATA-002 기준:

session 기반을 선택하면 cookie 속성, CSRF 토큰 전달, 인증 principal에서 `memberId`를 얻는 구조가 필요하다.

승인하면 가능한 작업:

Backend Engineer가 cookie/CSRF/principal 후보를 SecurityConfig와 테스트 설계에 반영할 수 있다.

승인하지 않으면 멈추는 지점:

구독 생성 POST 보호 API와 현재 회원 식별 로직을 만들지 않는다.

AI Tech Lead 보조 검토 의견:

CSRF를 끄는 방식은 브라우저 session 기반 MVP에 맞지 않을 수 있다. FE와 token 전달 규칙을 같이 정해야 한다.

사용자 확인 질문:

session cookie, CSRF 토큰 전달 방식, `memberId` principal 구조를 첫 구현 전에 함께 정할까요?

상태:
- Decision Required

## 결정 항목 9. API-001을 Controller 계약으로 사용할지 여부

ARCH-004 / DATA-002 기준:

API-001은 공개 상품 API 2개와 보호 구독 API 3개, 요청/응답 field 후보, 오류 후보를 제공한다.

승인하면 가능한 작업:

Backend Engineer가 Controller URI, DTO, HTTP status, validation, error mapping 후보를 구현할 수 있다.

승인하지 않으면 멈추는 지점:

Controller와 DTO 구현을 시작하지 않는다. API-001 수정 또는 별도 API 결정 작업이 필요하다.

AI Tech Lead 보조 검토 의견:

API-001을 기준으로 구현해야 FE와 QA가 같은 계약을 보고 움직일 수 있다. 단, 오류 JSON과 인증 실패 응답은 보완 결정이 필요하다.

사용자 확인 질문:

API-001의 5개 API 후보를 첫 Controller 계약 후보로 사용할까요?

상태:
- Decision Required

## 결정 항목 10. 오류 응답 JSON 구조

ARCH-004 / DATA-002 기준:

API-001은 `code`, `message`, `fieldErrors` 후보를 둔다. Spring `ProblemDetail` 사용 여부는 아직 정하지 않았다.

승인하면 가능한 작업:

Backend Engineer가 공통 오류 응답, field validation error, domain error, auth error 매핑을 구현할 수 있다.

승인하지 않으면 멈추는 지점:

Exception handler, error DTO, security failure response를 작성하지 않는다.

AI Tech Lead 보조 검토 의견:

첫 MVP에서는 API-001의 단순 구조를 유지하는 편이 FE/QA 추적성에 유리하다. ProblemDetail 전환은 별도 API 결정 후보로 둘 수 있다.

사용자 확인 질문:

첫 Backend 구현의 오류 응답은 `code`, `message`, `fieldErrors` 구조 후보를 사용할까요?

상태:
- Decision Required

## 결정 항목 11. ARCH-001을 구현 아키텍처 기준으로 사용할지 여부

ARCH-004 / DATA-002 기준:

ARCH-001은 책임 경계와 인증/오류/날짜 책임 후보를 제시했고 ARCH-003은 feature package 후보를 제안했다.

승인하면 가능한 작업:

Backend Engineer가 `catalog`, `subscription`, `member`, `common` 같은 package 후보와 계층별 책임을 구현할 수 있다.

승인하지 않으면 멈추는 지점:

package 구조, transaction boundary, exception handler 위치를 정하지 않는다.

AI Tech Lead 보조 검토 의견:

첫 MVP는 기능별 package가 책임 추적에 유리하다. 공통 계층에는 시간, 오류, 보안 경계만 최소로 둔다.

사용자 확인 질문:

ARCH-001과 ARCH-003의 feature package 후보를 첫 구현 아키텍처 후보로 사용할까요?

상태:
- Decision Required

## 결정 항목 12. CI 확장 범위

ARCH-004 / DATA-002 기준:

현재 Repository Validation은 Java 25와 Node 24 환경에서 backend test/build, frontend install/lint/build를 실행한다.

승인하면 가능한 작업:

Backend Engineer 또는 SRE가 DB 의존성 도입 후에도 test/build가 통과할 test profile, datasource, Flyway 처리 경로를 준비할 수 있다.

승인하지 않으면 멈추는 지점:

DB service, Testcontainers, OpenAPI validation workflow는 추가하지 않는다.

AI Tech Lead 보조 검토 의견:

첫 구현에서는 기존 Repository Validation을 유지하고, DB/OpenAPI CI 확장은 별도 OPS/SRE 후보로 분리하는 편이 작다. 단 JPA/Flyway 도입 시 test profile 경로는 같은 PR에서 해결해야 한다.

사용자 확인 질문:

첫 Backend 구현 PR은 기존 Repository Validation을 유지하고, DB service/Testcontainers/OpenAPI CI 확장은 후속 작업으로 둘까요?

상태:
- Decision Required

## 보완 결정 항목 1. DB 의존성 도입 시 CI 검증 경로

ARCH-004 / DATA-002 기준:

JPA/Flyway/MySQL을 도입하면 로컬과 CI에서 datasource와 migration 적용 방식이 필요하다.

승인하면 가능한 작업:

test profile datasource, Flyway 활성/비활성 범위, repository test 범위를 작성할 수 있다.

승인하지 않으면 멈추는 지점:

DB 의존성을 build file에 추가하지 않는다.

AI Tech Lead 보조 검토 의견:

DB 의존성과 CI 검증 경로는 분리하면 다음 PR에서 빨리 실패할 수 있으므로 같은 승인 묶음이 안전하다.

사용자 확인 질문:

DB 의존성 도입과 test/build 통과 경로를 같은 Backend 구현 입력으로 묶을까요?

상태:
- Decision Required

## 보완 결정 항목 2. 인증 주체 생성 경로

ARCH-004 / DATA-002 기준:

보호 API는 인증 컨텍스트에서 `memberId`를 얻어야 하며 요청 body/query의 회원 식별자를 신뢰하면 안 된다.

승인하면 가능한 작업:

최소 로그인/session 생성 경로, principal 구조, security test를 설계할 수 있다.

승인하지 않으면 멈추는 지점:

구독 생성, 내 구독 목록, 내 구독 상세 보호 API 구현을 시작하지 않는다.

AI Tech Lead 보조 검토 의견:

보호 API를 첫 범위에 넣으려면 인증 주체 생성 경로도 같이 필요하다. 고정 member 방식은 추천하지 않는다.

사용자 확인 질문:

보호 API 포함 시 최소 로그인/session 생성 경로와 `memberId` principal 구조를 함께 정할까요?

상태:
- Decision Required

## 보완 결정 항목 3. API 인증 실패 응답 계약

ARCH-004 / DATA-002 기준:

AUTH-001은 화면 redirect와 API `401` 후보를 분리한다. API-001은 `AUTH_REQUIRED` 후보를 둔다.

승인하면 가능한 작업:

`/api/**` 보호 API의 인증 실패와 권한 실패 JSON 응답을 구현할 수 있다.

승인하지 않으면 멈추는 지점:

AuthenticationEntryPoint, AccessDeniedHandler 또는 동등한 security failure handler를 작성하지 않는다.

AI Tech Lead 보조 검토 의견:

브라우저 session을 쓰더라도 API 실패 응답은 redirect보다 JSON이 FE/QA에 더 명확하다.

사용자 확인 질문:

보호 API 인증 실패는 `AUTH_REQUIRED` JSON 응답 후보로 둘까요?

상태:
- Decision Required

## 보완 결정 항목 4. CSRF 토큰 전달 계약

ARCH-004 / DATA-002 기준:

session 기반 보호 POST API는 CSRF 정책과 FE 토큰 전달 방식이 필요하다.

승인하면 가능한 작업:

CSRF token 노출 방식, cookie/header 이름, FE 전송 규칙 후보를 구현할 수 있다.

승인하지 않으면 멈추는 지점:

구독 생성 POST 보호 API와 FE API client 구현을 시작하지 않는다.

AI Tech Lead 보조 검토 의견:

CSRF를 켤지 끌지보다, 사용자가 기대하는 브라우저 보안 모델과 FE 전달 계약을 먼저 정해야 한다.

사용자 확인 질문:

CSRF token 전달 계약을 첫 Backend 구현 전에 정할까요?

상태:
- Decision Required

## 보완 결정 항목 5. DATA-002 논리 ERD를 Flyway/JPA 구현 입력으로 사용할지 여부

ARCH-004 / DATA-002 기준:

DATA-002는 DATA-001을 보완해 관계, 제약, 인덱스 후보를 명확히 했다. 하지만 최종 DB schema는 아니다.

승인하면 가능한 작업:

Backend Engineer가 DATA-001과 DATA-002를 함께 보고 migration/JPA 후보를 작성할 수 있다.

승인하지 않으면 멈추는 지점:

DATA-002 기반 DDL, JPA 연관관계, Repository query 후보를 작성하지 않는다.

AI Tech Lead 보조 검토 의견:

DATA-002는 첫 DB 구현의 좋은 입력이지만, SQL DDL과 JPA 매핑은 구현 PR에서 다시 검증 가능하게 설명해야 한다.

사용자 확인 질문:

DATA-002 논리 ERD를 첫 Flyway/JPA 구현 입력 후보로 함께 사용할까요?

상태:
- Decision Required

## 승인 시 다음 Backend 구현 입력

사용자가 위 후보를 선택하면 다음 입력 묶음으로 Backend 구현 작업을 시작할 수 있다.

- 첫 구현 API 범위
- 허용 의존성 목록
- DATA-001/DATA-002 사용 범위
- Flyway migration 작성 여부
- MySQL 연결과 Secret 전달 방식
- AUTH-001 사용 범위와 인증 방식
- CSRF, cookie, principal 구조
- API-001 Controller 계약 사용 범위
- 오류 응답 JSON 구조
- ARCH-001/ARCH-003 package와 계층 경계
- CI test/build 보완 경로

## 승인하지 않으면 멈추는 지점

- build file 수정 전
- DB migration 작성 전
- Entity, Repository, Service, Controller, DTO, SecurityConfig 작성 전
- 인증 주체 생성 경로 작성 전
- API 오류 응답 공통 구조 작성 전
- `.github/**` workflow 변경 전

## 보류 권장 항목

- Testcontainers 도입
- OpenAPI generation/validation 도구 도입
- Docker Compose 기반 로컬 MySQL
- GitHub Actions DB service container
- 결제, 재고, 배송, 구독 상태 전이
- soft delete, hard delete, 삭제·탈퇴·보관·익명화 정책
- 상품 검색, 정렬, 페이지네이션
- 구독 변경, 건너뛰기, 일시정지, 재개, 해지

## 사용자 확인 질문

1. 첫 Backend 구현 범위를 API 5개로 둘까요?
2. 보호 API를 포함한다면 최소 로그인/session 생성 경로도 같은 작업에 포함할까요?
3. JPA, MySQL JDBC, Flyway, Spring Security, `spring-security-test`를 첫 구현 후보로 허용할까요?
4. DATA-001과 DATA-002를 함께 첫 DB schema 후보 입력으로 사용할까요?
5. 오류 응답은 `code`, `message`, `fieldErrors` 구조 후보를 유지할까요?
6. session 기반 인증과 CSRF token 전달 계약을 우선할까요?
7. DB/OpenAPI CI 확장은 후속 OPS/SRE 작업으로 분리할까요?

## 구현 전 금지 사항

- 신규 의존성 추가
- DB migration 작성
- JPA Entity 작성
- Repository, Service, Controller, DTO, SecurityConfig 작성
- test profile, datasource, Flyway 설정 작성
- 인증 필터, principal, CSRF 설정 작성
- API client 또는 화면 구현
- 실제 Secret, 비밀값, token, 외부 알림 URL 저장
- `.github/**` workflow 변경
- DATA-001, DATA-002, API-001, AUTH-001, ARCH-001, FOUNDATION-000 원본 상태 변경
- reset, rebase, force push, history rewrite

## 다음 Backend 작업 후보

후보 1:

```text
ARCH-006 또는 FOUNDATION-003: Backend 구현 입력 승인 결정
```

목적은 사용자가 ARCH-005의 Decision Required 항목을 선택하고 첫 Backend 구현 입력을 확정하는 것이다.

후보 2:

```text
승인 이후 BE 작업: 첫 수직 MVP Backend 최소 구현
```

조건은 데이터, API, 인증, 의존성, CI 보완 경로가 사용자 결정으로 정리되는 것이다.
