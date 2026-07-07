# ARCH-004 Backend 구현 결정 요청

## 문서 정보

- 작업 ID: `ARCH-004`
- 역할: Tech Lead
- 문서 상태: Decision Request
- 기준 브랜치: `main`
- 작업 브랜치: `ops/tl`
- 선행 PR: #24 `docs(backend): Backend 구현 계획과 의존성 도입안 정리`

## 목적

PR #24 병합 이후 첫 Backend 실제 구현 전에 사용자가 직접 결정해야 할 기술·설계 항목을 한 문서로 모은다.

이번 문서는 구현 작업이 아니다. 신규 의존성, DB migration, JPA Entity, Repository, Service, Controller, DTO, SecurityConfig, Backend/Frontend 코드는 추가하지 않는다.

## 전체 문서 재검토 결과 요약

- 루트 `AGENTS.md`, `README.md`, `backend/AGENTS.md`, Backend 역할 Skill, 제품·도메인·UX·ADR·DATA·API·런북·보고서·인수인계·검증 스크립트·GitHub Actions workflow를 최신 `main` 기준으로 다시 확인했다.
- Tech Lead 전용 역할 문서와 Tech Lead 전용 Skill은 저장소에서 확인되지 않았다. 따라서 루트 `AGENTS.md`, 선행 설계 문서, 사용자 지시를 기준으로 이 요청서를 작성한다.
- `PS-002`, `PS-003`은 제품 요구사항과 제품 결정 입력으로 사용할 수 있다.
- `DOMAIN-001`은 도메인 불변 조건과 책임 구분 입력으로 사용할 수 있다.
- `DATA-001`, `API-001`, `AUTH-001`, `ARCH-001`, `FOUNDATION-000`, `ARCH-002`, `ARCH-003`은 구현 후보를 제공하지만 실제 DB schema, Controller 계약, Spring Security 설정, 신규 의존성, 패키지 구조, 트랜잭션 경계를 사용자 결정 없이 구현 기준으로 적용하면 안 된다.
- `FOUNDATION-001`, `FOUNDATION-002` 기준으로 현재 Backend/Frontend scaffold와 PR 검증 명령은 존재하지만, DB, Flyway, JPA, Security, MySQL service, Testcontainers, OpenAPI 검증은 아직 없다.

## 현재 승인된 입력

| 입력 | 구현 전 활용 범위 |
| --- | --- |
| `PS-002` | 첫 MVP 요구사항, 인수 조건, 상품 탐색과 구독 생성·조회 범위 |
| `PS-003` | 비회원 상품 탐색, 로그인 후 안전한 내부 GET 복귀, 생성 후 상세 이동, 생성 전 예정일 미표시 제품 결정 |
| `DOMAIN-001` | SKU 하나 구독, 수량 1~10, 배송 주기 2/4/8, 다음 주문 예정일 계산, 소유자 검증 의미 |
| `UX-001` | 화면 상태와 사용자 흐름 입력. 구체 라우트와 구현 방식은 별도 결정 필요 |

## 아직 Proposed 상태인 입력

| 입력 | 아직 사용자 결정이 필요한 이유 |
| --- | --- |
| `ARCH-001` | 실제 패키지 구조, 트랜잭션 경계, 예외 처리 경계로 사용할지 결정 필요 |
| `DATA-001` | 실제 table, column, FK, index, DB 타입, Flyway migration 기준으로 사용할지 결정 필요 |
| `API-001` | 실제 URI, DTO, HTTP status, 오류 JSON, Controller 계약으로 사용할지 결정 필요 |
| `AUTH-001` | 세션/token, cookie, CSRF, principal, Spring Security 설정 방식 결정 필요 |
| `FOUNDATION-000` | 기술 버전과 신규 의존성 방향을 실제 도입 기준으로 사용할지 결정 필요 |
| `ARCH-002` | Backend 구현 착수 기준과 중단 조건 입력으로 유지 |
| `ARCH-003` | Backend 구현 계획과 의존성 후보 입력으로 유지 |

## 결정 요청 항목

1. 첫 Backend 구현 범위
2. 신규 의존성 도입 범위
3. `DATA-001`을 실제 DB schema 입력으로 사용할지 여부
4. Flyway 도입 여부와 최초 migration 작성 여부
5. MySQL 연결 정책과 Secret 전달 방식
6. `AUTH-001`을 Spring Security 구현 기준으로 사용할지 여부
7. 인증 방식: session 기반 또는 token 기반
8. CSRF, cookie, principal 구조
9. `API-001`을 Controller 계약으로 사용할지 여부
10. 오류 응답 JSON 구조
11. `ARCH-001`을 구현 아키텍처 기준으로 사용할지 여부
12. CI 확장 범위

보완 결정 항목:

- DB 의존성 도입 시 CI 검증 경로
- 인증 주체 생성 경로
- API 인증 실패 응답 계약
- CSRF 토큰 전달 계약

### 결정 항목 1. 첫 Backend 구현 범위

현재 상태:

제품·도메인 범위는 첫 수직 MVP로 제한되어 있다. 하지만 DB schema, API 계약, 인증 방식, 신규 의존성은 아직 사용자 결정이 필요하다.

선택지:

| 선택지 | 설명 | 장점 | 단점 | 후속 영향 |
| --- | --- | --- | --- | --- |
| A. 첫 수직 MVP 최소 API 5개 | 상품 목록, 상품 상세, 구독 생성, 내 구독 목록, 내 구독 상세를 첫 구현 대상으로 삼는다. | 요구사항 흐름을 한 번에 검증할 수 있다. | DB, API, 인증, 오류 계약 결정이 모두 필요하다. | BE 구현 작업이 크므로 테스트와 리뷰 범위가 넓어진다. |
| B. 공개 상품 조회만 먼저 구현 | 인증 없는 상품 목록과 상세만 먼저 구현한다. | 인증 결정을 늦출 수 있다. | 수직 MVP 검증이 구독 생성까지 닿지 않는다. | 이후 보호 API와 보안 경계를 별도 작업으로 추가한다. |
| C. 구현 전 결정 문서만 추가 작성 | 실제 Backend 구현을 더 미루고 DB/API/Auth별 승인 요청서를 더 쪼갠다. | 위험을 더 작게 나눌 수 있다. | 구현 착수가 늦어진다. | DATA/API/AUTH 후속 결정 작업이 늘어난다. |

추천안:

A를 선택하되, 이번 요청서의 나머지 결정 항목이 사용자에게 선택된 뒤에만 첫 Backend 구현 작업으로 이동한다.

근거:

ARCH-003은 첫 구현 후보를 공개 상품 조회와 보호 구독 API 3개로 제안했다. 첫 MVP의 가치도 구독 생성과 내 구독 조회까지 연결될 때 검증된다.

사용자 승인 필요 문장:

`ARCH-004-DEC-001`: 첫 Backend 구현 범위를 상품 목록, 상품 상세, 구독 생성, 내 구독 목록, 내 구독 상세의 최소 API 5개로 진행해도 되는지 승인해 주세요.

### 결정 항목 2. 신규 의존성 도입 범위

현재 상태:

현재 Backend에는 `spring-boot-starter-webmvc`, `spring-boot-starter-validation`, `spring-boot-starter-test`가 있다. JPA, MySQL JDBC driver, Flyway, Security, Security test, Testcontainers, OpenAPI 도구는 아직 없다.

선택지:

| 선택지 | 설명 | 장점 | 단점 | 후속 영향 |
| --- | --- | --- | --- | --- |
| A. JPA, MySQL JDBC, Flyway, Security, Security test | 첫 구현에 필요한 영속성·보안 최소 묶음을 추가한다. | 실제 DB와 보호 API 흐름을 구현할 수 있다. | build file, 테스트, 로컬 실행 조건이 바뀐다. | DB schema, auth, CI 결정이 함께 필요하다. |
| B. A + Testcontainers | MySQL 통합 테스트를 Docker 기반으로 검증한다. | DB 방언과 migration 검증 신뢰도가 높다. | Docker 실행 조건과 CI 시간이 늘어난다. | SRE 검토와 CI 확장이 필요하다. |
| C. A + OpenAPI 검증 도구 | API 계약 drift를 줄이는 도구를 추가한다. | 문서와 Controller 불일치를 줄일 수 있다. | API 문서 형식과 생성물 정책을 먼저 정해야 한다. | API 계약 관리 방식 결정이 필요하다. |
| D. 신규 의존성 없음 | 현재 scaffold만 유지한다. | 변경 위험이 작다. | 실제 DB·보안 기반 구현이 어렵다. | 임시 저장소나 비보호 API가 생길 위험이 있다. |

추천안:

첫 Backend 구현에서는 JPA, MySQL JDBC driver, Flyway, Spring Security, `spring-security-test`를 도입 후보로 요청하고, Testcontainers와 OpenAPI 검증 도구는 다음 단계 후보로 분리한다. 단, JPA, MySQL JDBC driver, Flyway를 함께 요청하는 경우 CI에서 `./gradlew test`와 `./gradlew build`가 계속 실행될 수 있는 테스트 datasource와 Flyway 처리 경로도 같은 결정 묶음에 포함한다.

근거:

첫 MVP는 영속성, 보호 API, 인증 회원 식별이 필요하다. 반면 Testcontainers와 OpenAPI 도구는 CI와 계약 관리 방식까지 넓히므로 첫 구현 위험을 키울 수 있다.

사용자 승인 필요 문장:

`ARCH-004-DEC-002`: 첫 Backend 구현에서 JPA, MySQL JDBC driver, Flyway, Spring Security, `spring-security-test` 도입을 허용하고, DB 의존성 도입 시 CI 실행 가능 경로를 함께 결정하며, Testcontainers와 OpenAPI 도구는 별도 결정으로 분리해도 되는지 승인해 주세요.

### 결정 항목 3. DATA-001을 실제 DB schema 입력으로 사용할지 여부

현재 상태:

`DATA-001`은 Product, SKU, Member, Subscription 저장 구조 후보를 제시한다. 하지만 실제 DDL, table name, column name, FK, index, DB 타입은 사용자 결정이 필요하다.

선택지:

| 선택지 | 설명 | 장점 | 단점 | 후속 영향 |
| --- | --- | --- | --- | --- |
| A. DATA-001을 첫 migration 입력으로 사용 | DATA-001 후보를 실제 schema 작성 기준으로 삼는다. | 선행 문서와 구현 추적성이 좋다. | 후보에 남은 세부 결정을 함께 선택해야 한다. | Flyway V1 작성이 가능해진다. |
| B. DATA-001을 수정한 뒤 사용 | table/column/FK/index를 별도 문서로 조정한 뒤 사용한다. | DB 세부 위험을 줄일 수 있다. | Backend 구현이 늦어진다. | DATA 후속 작업이 먼저 필요하다. |
| C. DATA-001은 참고만 하고 임시 저장소 사용 | DB schema 없이 in-memory 또는 mock 저장소로 시작한다. | DB 결정을 늦출 수 있다. | 실제 MVP 구현과 멀어진다. | 이후 DB 전환 비용이 생긴다. |

추천안:

A를 선택하고, 첫 migration에는 `members`, `products`, `skus`, `subscriptions`와 필수 제약·인덱스 최소 후보만 포함하도록 요청한다.

근거:

첫 MVP 구독 생성과 본인 조회는 영속성 없이 의미 있는 검증이 어렵다. DATA-001은 이미 요구사항과 도메인 규칙을 데이터 구조로 연결했다.

사용자 승인 필요 문장:

`ARCH-004-DEC-003`: `DATA-001`의 Product, SKU, Member, Subscription 구조를 첫 DB schema 입력으로 사용해도 되는지 승인해 주세요.

### 결정 항목 4. Flyway 도입 여부와 최초 migration 작성 여부

현재 상태:

Flyway는 FOUNDATION-000과 ARCH-003에서 후보로 제안됐다. 아직 의존성, migration 파일, versioning 규칙은 없다.

선택지:

| 선택지 | 설명 | 장점 | 단점 | 후속 영향 |
| --- | --- | --- | --- | --- |
| A. Flyway와 최초 V1 migration 작성 | 첫 schema를 SQL migration으로 남긴다. | DB 변경 이력이 리뷰 가능하다. | 초기 DDL 선택 비용이 생긴다. | migration 테스트와 운영 규칙이 필요하다. |
| B. Flyway 없이 JPA schema generation 사용 | 개발 초기에 Hibernate 생성에 의존한다. | 초기 구현이 빠르다. | schema 이력과 리뷰 가능성이 낮다. | 운영 전 Flyway 전환 비용이 생긴다. |
| C. Flyway 도입만 하고 migration은 보류 | 의존성만 준비하고 schema는 다음 작업으로 넘긴다. | 도입 위치를 열어둘 수 있다. | 첫 구현에서 DB 기능이 막힐 수 있다. | 후속 migration 작업이 필요하다. |

추천안:

A를 선택하고 첫 파일은 `V1__create_first_mvp_tables.sql` 같은 단순한 이름으로 시작하도록 요청한다. Flyway를 첫 구현에 포함한다면 테스트에서 migration을 적용할지, test profile에서 별도 datasource를 사용할지, 또는 특정 테스트 범위에서 Flyway를 비활성화할지 같은 CI 실행 경로도 함께 결정해야 한다.

근거:

첫 MVP의 DB 구조는 상품·SKU·회원·구독의 핵심 계약이다. 변경 이력을 migration으로 남겨야 리뷰와 재현성이 좋아진다.

사용자 승인 필요 문장:

`ARCH-004-DEC-004`: Flyway를 도입하고 첫 Backend 구현에서 최초 schema migration을 작성하되, CI의 Backend test/build가 통과할 수 있는 테스트 datasource와 Flyway 적용 범위를 함께 결정해도 되는지 승인해 주세요.

### 결정 항목 5. MySQL 연결 정책과 Secret 전달 방식

현재 상태:

MySQL 8.4 LTS는 후보로 문서화됐지만 로컬 DB 실행 방식, schema 생성 방식, 접속 URL, 계정, 비밀번호 전달 방식은 정해지지 않았다.

선택지:

| 선택지 | 설명 | 장점 | 단점 | 후속 영향 |
| --- | --- | --- | --- | --- |
| A. 환경 변수 기반 로컬 MySQL | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 같은 환경 변수로 전달한다. | Secret을 저장소에 넣지 않는다. | 개발자 로컬 설정이 필요하다. | 런북 보완과 placeholder 설정이 필요하다. |
| B. ignored local properties 사용 | 커밋하지 않는 로컬 설정 파일을 사용한다. | IDE 실행이 편하다. | 파일 관리 실수가 있을 수 있다. | `.gitignore`와 런북 확인이 필요하다. |
| C. Docker Compose 기반 MySQL | compose로 DB를 제공한다. | 로컬 재현성이 좋다. | 현재 범위에서 Docker는 제외되어 있다. | OPS/SRE 작업과 compose 파일이 필요하다. |
| D. CI/GitHub Secrets까지 즉시 연결 | GitHub Actions에서 DB secret을 사용한다. | CI 통합 검증이 가능하다. | Secret 설정과 DB service 결정이 필요하다. | SRE 검토가 필요하다. |

추천안:

첫 구현은 환경 변수 기반 로컬 MySQL 정책으로 시작하고, 실제 Secret 값은 문서나 저장소에 쓰지 않는다. CI의 MySQL 연결은 Testcontainers 또는 service container 결정 후 별도 작업으로 분리한다.

근거:

현재 Docker와 DB CI는 범위 밖이다. 환경 변수 방식은 Secret 관리 원칙을 지키면서 첫 로컬 구현을 시작할 수 있다.

사용자 승인 필요 문장:

`ARCH-004-DEC-005`: 첫 Backend 구현의 MySQL 접속 정보는 환경 변수로만 전달하고 실제 Secret을 저장소에 남기지 않는 방식으로 진행해도 되는지 승인해 주세요.

### 결정 항목 6. AUTH-001을 Spring Security 구현 기준으로 사용할지 여부

현재 상태:

`AUTH-001`은 공개 기능과 보호 기능 경계, 로그인 복귀, Open Redirect 방지 후보를 정리했다. Spring Security 설정 방식은 아직 사용자 결정이 필요하다.

선택지:

| 선택지 | 설명 | 장점 | 단점 | 후속 영향 |
| --- | --- | --- | --- | --- |
| A. AUTH-001을 보호 API 구현 기준으로 사용 | 공개 API와 보호 API 경계를 AUTH-001 기준으로 구현한다. | 인증·인가 흐름의 추적성이 좋다. | 세션/token, CSRF, principal 결정이 함께 필요하다. | SecurityConfig와 테스트가 필요하다. |
| B. 인증 구현 없이 공개 API만 먼저 구현 | 보호 API를 뒤로 미룬다. | 보안 결정 부담을 줄인다. | 구독 생성·내 구독 조회가 늦어진다. | 첫 구현 범위가 상품 조회로 축소된다. |
| C. 임시 개발용 사용자 식별 사용 | Security 없이 고정 member를 사용한다. | 빠르게 도메인 흐름을 볼 수 있다. | 실제 보안 경계와 달라진다. | 나중에 재작업 위험이 크다. |

추천안:

A를 선택하고 보호 API는 Spring Security 경계가 적용된 상태에서만 추가하도록 요청한다.

근거:

구독 생성과 내 구독 조회는 회원 본인 데이터에 접근한다. 임시 인증은 소유자 검증, 오류 응답, QA 기준을 흐리게 만든다.

사용자 승인 필요 문장:

`ARCH-004-DEC-006`: `AUTH-001`을 첫 Backend 구현의 Spring Security 경계 기준으로 사용해도 되는지 승인해 주세요.

### 결정 항목 7. 인증 방식: session 기반 또는 token 기반

현재 상태:

AUTH-001은 세션 기반인지 token 기반인지 결정하지 않았다. 로그인 화면, 인증 저장 방식, 인증된 session과 `memberId` principal이 생성되는 경로도 아직 없다.

선택지:

| 선택지 | 설명 | 장점 | 단점 | 후속 영향 |
| --- | --- | --- | --- | --- |
| A. Session 기반 | 서버 session과 browser cookie를 사용한다. | 브라우저 MVP와 로그인 복귀 흐름에 자연스럽다. | CSRF와 cookie 속성 결정이 필요하다. | Spring Security form/login 또는 동등 흐름 설계가 필요하다. |
| B. Token 기반 | bearer token 또는 JWT 후보를 사용한다. | API client와 분리 배포에 익숙하다. | token 발급·보관·만료 정책이 필요하다. | XSS/저장소/refresh 정책 결정이 필요하다. |
| C. 인증 방식 결정을 보류 | 첫 구현에서 보호 API를 넣지 않는다. | 보안 선택을 늦출 수 있다. | 첫 수직 MVP 완성이 늦어진다. | 공개 상품 조회만 먼저 가능하다. |
| D. 보호 API를 공개 상품 API 뒤로 분리 | 인증 주체 생성 경로가 준비되기 전에는 상품 목록/상세만 구현한다. | 임시 인증을 피할 수 있다. | 구독 생성·조회가 후속 작업으로 밀린다. | 첫 구현 범위가 축소된다. |

추천안:

첫 MVP는 session 기반을 우선 요청한다. 다만 보호 API 3개를 첫 구현에 포함하려면 최소 로그인/session 생성 경로와 `memberId` principal 생성 방식을 같은 승인 요청에 포함한다. 이 경로가 선택되지 않으면 보호 API는 뒤로 미루고 공개 상품 API부터 진행한다.

근거:

현재 요구사항은 브라우저 기반 상품 탐색과 보호 화면 접근, 로그인 후 내부 GET 복귀다. 모바일 API나 외부 client 요구가 없으므로 token 정책을 먼저 만들 필요가 낮다.

사용자 승인 필요 문장:

`ARCH-004-DEC-007`: 첫 MVP 인증 방식은 session 기반으로 시작하고, 보호 API를 포함하는 경우 최소 로그인/session 생성 경로와 `memberId` principal 생성 방식을 함께 결정해도 되는지 승인해 주세요.

#### 보완 결정 항목 7-A. 인증 주체 생성 경로

현재 상태:

보호 API는 인증된 회원의 `memberId`가 필요하지만, 로그인/session 생성 경로와 principal 생성 방식은 아직 구현되지 않았다. 요청 body나 query parameter의 `memberId`를 신뢰하지 않는다는 원칙은 유지해야 한다.

선택지:

| 선택지 | 설명 | 장점 | 단점 | 후속 영향 |
| --- | --- | --- | --- | --- |
| A. 첫 구현에 최소 로그인/session 생성 경로 포함 | 보호 API 구현과 함께 인증 주체 생성 경로를 만든다. | 구독 생성·조회 흐름을 실제로 검증할 수 있다. | Auth 구현 범위가 커진다. | AUTH/API/FE 연동 기준이 필요하다. |
| B. 보호 API를 뒤로 미루고 공개 상품 API부터 구현 | 인증 주체 결정 전에는 공개 상품 목록/상세만 구현한다. | Auth 결정을 늦출 수 있다. | 첫 수직 MVP 완성이 늦어진다. | 구독 기능은 후속 작업으로 이동한다. |
| C. 테스트 전용 인증 주체만 사용 | 운영 경로 없이 테스트에서만 principal을 주입한다. | 일부 service/controller 테스트가 가능하다. | 실제 사용자 흐름 검증은 불가능하다. | 운영 API 구현 전 추가 결정이 필요하다. |

추천안:

보호 API를 첫 Backend 구현 범위에 포함하려면 A를 승인 요청에 포함한다. 인증 주체 생성 경로를 선택하지 않는다면 보호 API는 구현하지 않고 공개 상품 API부터 진행한다. 임시 고정 member나 요청 body/query의 `memberId`를 신뢰하는 방식은 추천하지 않는다.

근거:

구독 생성과 내 구독 조회는 본인 구독만 다뤄야 한다. 인증 주체 생성 경로가 없으면 실제 session과 principal 없이 보호 API를 임시 구현하게 되어 보안 경계와 QA 기준이 흐려진다.

사용자 승인 필요 문장:

`ARCH-004-DEC-007-A`: 첫 Backend 구현에서 보호 API를 포함하려면 최소 로그인/session 생성 경로와 `memberId` principal 생성 방식을 함께 승인해 주세요.

### 결정 항목 8. CSRF, cookie, principal 구조

현재 상태:

CSRF 정책, cookie 속성, 인증 컨텍스트의 회원 식별자 구조가 아직 없다.

선택지:

| 선택지 | 설명 | 장점 | 단점 | 후속 영향 |
| --- | --- | --- | --- | --- |
| A. Session cookie + CSRF 사용 | unsafe method에 CSRF 보호를 둔다. | 브라우저 session 보안 기준에 맞다. | FE가 CSRF token을 처리해야 한다. | FE/API 연동 규칙이 필요하다. |
| B. Session cookie + CSRF 일시 비활성 | 개발 단순화를 위해 CSRF를 미룬다. | 구현이 쉽다. | POST 보호 기준이 약해진다. | 보안 검토 부채가 생긴다. |
| C. Token 기반 + CSRF 미사용 | Authorization header token을 사용한다. | CSRF 부담은 낮아진다. | token 저장·만료 정책이 필요하다. | token 인증 결정과 함께 움직인다. |

추천안:

Session 기반을 선택한다면 CSRF를 사용하고, cookie는 `HttpOnly`, 운영 환경 `Secure`, `SameSite=Lax` 후보로 둔다. principal은 최소 `memberId`와 표시·로그인 식별에 필요한 값만 포함한다. CSRF를 사용하는 경우 token repository, 노출 방식, cookie/header 이름, FE 요청 규칙도 같은 승인 요청에 포함한다.

근거:

보호 API는 구독 생성이라는 상태 변경을 포함한다. 요청 본문이나 query의 `memberId`를 신뢰하지 않고 인증 컨텍스트의 principal에서 회원 식별자를 가져와야 한다.

사용자 승인 필요 문장:

`ARCH-004-DEC-008`: session cookie, CSRF 사용, 최소 `memberId` principal 구조와 CSRF token 전달 계약을 첫 인증 구현 기준으로 삼아도 되는지 승인해 주세요.

#### 보완 결정 항목 8-A. CSRF 토큰 전달 계약

현재 상태:

Session 기반 인증과 CSRF 사용을 첫 구현 기준으로 삼으려면 FE가 CSRF token을 어떻게 받고 어떤 header로 다시 보낼지 결정해야 한다. 이 계약이 없으면 `POST /api/subscriptions`가 403 응답으로 막히거나 구현자가 임의의 전달 방식을 만들 수 있다.

선택지:

| 선택지 | 설명 | 장점 | 단점 | 후속 영향 |
| --- | --- | --- | --- | --- |
| A. Cookie 기반 CSRF token 노출 + header 전송 규칙 승인 | 서버가 CSRF token을 cookie 또는 동등 방식으로 노출하고 FE가 정해진 header로 전송한다. | 브라우저 session API에서 실사용 가능하다. | cookie/header 이름과 노출 정책 결정이 필요하다. | FE API client 규칙이 필요하다. |
| B. CSRF token 발급 endpoint 추가 | FE가 별도 endpoint에서 token을 받아 저장한다. | 흐름이 명시적이다. | API endpoint가 늘어난다. | API-001 보완이 필요하다. |
| C. CSRF 사용을 후속으로 보류 | 첫 구현에서는 CSRF 결정을 미룬다. | 구현은 단순해진다. | 보안 부채가 생긴다. | 보호 POST API 기준이 약해진다. |

추천안:

Session 기반을 선택한다면 A를 우선 승인 요청한다. 단, cookie/header 이름과 FE 전송 규칙은 실제 구현 전 사용자 승인 항목으로 남긴다.

근거:

보호 API에는 `POST /api/subscriptions`가 포함된다. CSRF를 사용하면서 token 전달 계약이 없으면 API-001의 성공·오류 계약과 FE 호출 규칙이 다음 구현에서 흔들릴 수 있다.

사용자 승인 필요 문장:

`ARCH-004-DEC-008-A`: session 기반 인증에서 CSRF를 사용할 경우 token 노출 방식, cookie/header 이름, FE 전송 규칙을 함께 승인해 주세요.

### 결정 항목 9. API-001을 Controller 계약으로 사용할지 여부

현재 상태:

`API-001`은 URI, DTO field, 성공·오류 status, 오류 code 후보를 제공한다. Controller, DTO, 테스트는 아직 없다.

선택지:

| 선택지 | 설명 | 장점 | 단점 | 후속 영향 |
| --- | --- | --- | --- | --- |
| A. API-001을 Controller 계약 기준으로 사용 | 제안된 5개 API와 필드를 구현 대상으로 삼는다. | FE/QA 추적성이 좋다. | 오류 JSON과 status 선택을 함께 결정해야 한다. | Controller/DTO 테스트 작성 가능 |
| B. API-001을 수정한 뒤 사용 | URI, field, status를 먼저 별도 조정한다. | 계약 변경 위험을 줄인다. | 구현이 늦어진다. | API 후속 결정 문서가 필요하다. |
| C. OpenAPI 문서부터 작성 | OpenAPI 파일을 계약 원천으로 둔다. | 도구 검증 기반을 만들 수 있다. | 도구 선택과 생성물 정책이 필요하다. | OpenAPI 도입 결정이 필요하다. |

추천안:

A를 선택하고 OpenAPI 도구는 첫 구현 뒤 계약 검증 필요가 커질 때 별도로 검토한다. API-001을 Controller 계약으로 사용할 경우 `/api/**` 보호 API의 미인증·권한 부족 응답도 JSON 계약으로 분리해 승인 요청에 포함한다.

근거:

API-001은 첫 MVP 요구사항과 DATA-001 매핑을 이미 제공한다. 첫 구현에서는 문서 계약과 Controller 테스트를 맞추는 정도가 적절하다.

사용자 승인 필요 문장:

`ARCH-004-DEC-009`: `API-001`의 5개 API 후보를 첫 Backend Controller 계약 기준으로 사용하고, `/api/**` 보호 API의 인증 실패 응답을 JSON 계약으로 처리해도 되는지 승인해 주세요.

#### 보완 결정 항목 9-A. API 인증 실패 응답 계약

현재 상태:

Session 기반 인증을 사용하더라도 `/api/**` 요청은 브라우저 페이지 이동보다 API JSON 계약을 우선해야 한다. `AUTH-001`은 브라우저 redirect와 API `401`의 최종 분기를 후속 결정으로 남겼으므로, 첫 Backend 구현 전 API 실패 응답 기준을 함께 정해야 한다.

선택지:

| 선택지 | 설명 | 장점 | 단점 | 후속 영향 |
| --- | --- | --- | --- | --- |
| A. `/api/**`는 401/403 JSON으로 처리 | API 인증 실패를 JSON으로 고정한다. | FE/QA 계약이 명확하다. | Security 예외 처리 설정이 필요하다. | AuthenticationEntryPoint/AccessDeniedHandler 기준 필요 |
| B. 기본 form login redirect 사용 | 미인증 요청을 로그인 페이지로 보낸다. | Spring Security 기본 흐름과 가깝다. | API 호출에서 HTML/redirect가 섞인다. | FE 오류 처리와 API-001 계약이 흔들린다. |
| C. 인증 실패 계약을 후속으로 보류 | API 구현 전에 다시 결정한다. | 현재 문서 변경은 작다. | 다음 구현에서 다시 중단될 수 있다. | 보호 API 구현 지연 |

추천안:

API-001을 Controller 계약으로 사용할 경우 A를 함께 승인 요청한다. 미인증 보호 API 요청은 `401`과 `AUTH_REQUIRED` 계열 오류 JSON을 반환하고, 인증은 되었지만 접근 권한이 없는 경우는 `403`과 권한 오류 JSON을 반환하는 기준을 요청한다.

근거:

Session 기반 인증의 화면 흐름과 API 호출 실패 계약은 분리되어야 한다. 보호 API에서 HTML redirect가 섞이면 FE와 QA가 API-001의 오류 응답을 안정적으로 검증할 수 없다.

사용자 승인 필요 문장:

`ARCH-004-DEC-009-A`: session 기반 인증을 사용하더라도 `/api/**` 보호 API의 미인증 요청은 `401` JSON, 권한 부족 요청은 `403` JSON으로 처리하는 기준을 승인해 주세요.

### 결정 항목 10. 오류 응답 JSON 구조

현재 상태:

API-001은 `code`, `message`, `fieldErrors` 후보를 제시한다. Spring의 `ProblemDetail` 사용 여부나 최종 field 구조는 아직 없다.

선택지:

| 선택지 | 설명 | 장점 | 단점 | 후속 영향 |
| --- | --- | --- | --- | --- |
| A. API-001 공통 구조 | `{ code, message, fieldErrors }` 형태를 사용한다. | FE가 처리하기 쉽고 API-001과 일치한다. | 표준 `ProblemDetail`과 별도 구조가 된다. | Global exception handler가 필요하다. |
| B. Spring `ProblemDetail` 기반 | RFC 9457 계열 구조를 따른다. | Spring 생태계 기본값과 가깝다. | API-001의 fieldErrors 후보를 다시 맞춰야 한다. | FE 오류 처리 계약 재검토 필요 |
| C. API별 최소 오류 | endpoint별 단순 message만 반환한다. | 초기 구현이 단순하다. | 일관성이 낮고 QA 기준이 흐려진다. | 후속 정리가 필요하다. |

추천안:

A를 선택하고 `fieldErrors`는 입력 검증 오류에만 포함하도록 요청한다.

근거:

첫 MVP는 입력 검증과 도메인 오류가 많다. `code`와 field 단위 오류가 있으면 FE와 QA가 요구사항별 실패를 검증하기 쉽다.

사용자 승인 필요 문장:

`ARCH-004-DEC-010`: 첫 Backend API 오류 응답은 `code`, `message`, `fieldErrors` 공통 구조로 진행해도 되는지 승인해 주세요.

### 결정 항목 11. ARCH-001을 구현 아키텍처 기준으로 사용할지 여부

현재 상태:

`ARCH-001`은 시스템 경계와 책임 분리를 정리했다. `ARCH-003`은 feature package 후보를 제시했다. 실제 패키지, 트랜잭션, 예외 계층은 아직 구현되지 않았다.

선택지:

| 선택지 | 설명 | 장점 | 단점 | 후속 영향 |
| --- | --- | --- | --- | --- |
| A. Feature package + 계층 분리 | `catalog`, `subscription`, `member`, `common` 아래 `domain/application/infra/api` 후보를 사용한다. | 기능 흐름과 책임이 잘 드러난다. | 초기 파일 수가 늘 수 있다. | 트랜잭션은 application service에 둔다. |
| B. 전통적 layer package | `controller/service/repository/domain` 중심으로 둔다. | 단순하고 익숙하다. | 기능 경계가 흐려질 수 있다. | 도메인별 확장 시 재배치 가능성이 있다. |
| C. 단일 package에서 시작 | 최소 파일로 시작한다. | 빠르다. | 책임 경계가 빨리 섞일 수 있다. | 리팩터링 부채가 생긴다. |

추천안:

A를 선택하고, 트랜잭션 경계는 application service, HTTP 변환은 api, JPA 접근은 infra, 도메인 규칙은 domain에 둔다.

근거:

첫 MVP도 상품 탐색과 구독 생성·조회가 분리된다. 기능별 package는 후속 결제·재고·배송 확장 전에 경계를 설명하기 쉽다.

사용자 승인 필요 문장:

`ARCH-004-DEC-011`: 첫 Backend 구현에서 `ARCH-001` 책임 경계와 `ARCH-003` feature package 후보를 구현 구조 기준으로 사용해도 되는지 승인해 주세요.

### 결정 항목 12. CI 확장 범위

현재 상태:

Repository Validation은 Backend `test/build`와 Frontend `npm ci/lint/build`를 실행한다. MySQL, Flyway migration 적용, Security test, API contract 검증은 아직 없다. JPA, MySQL JDBC driver, Flyway를 첫 구현에 포함하면 현재 `SpringBootTest`가 datasource/Flyway 자동 설정 때문에 CI에서 실패할 수 있으므로 DB 의존성과 CI 검증 경로를 분리해서 결정하면 안 된다.

선택지:

| 선택지 | 설명 | 장점 | 단점 | 후속 영향 |
| --- | --- | --- | --- | --- |
| A. Test profile 기반 최소 datasource 경로 승인 | 첫 구현에서 test profile 또는 테스트 전용 datasource 설정으로 `SpringBootTest`가 통과하도록 한다. | workflow 변경을 최소화할 수 있다. | 실제 MySQL 검증 강도는 제한된다. | DB 통합 검증은 후속 작업으로 남는다. |
| B. MySQL service container 추가 | CI에서 실제 MySQL을 띄워 migration과 repository를 검증한다. | DB 검증이 강해진다. | workflow와 Secret/서비스 설정 검토가 필요하다. | SRE 작업 범위가 생긴다. |
| C. Testcontainers 기반 통합 테스트 | 테스트가 Docker로 MySQL을 띄운다. | 로컬/CI 일관성이 좋다. | Docker 사용과 실행 시간이 늘어난다. | Testcontainers 의존성 승인이 필요하다. |
| D. OpenAPI 검증 task 추가 | API 계약 drift를 CI에서 검증한다. | 계약 품질이 좋아진다. | 도구와 계약 원천 결정이 필요하다. | API 도구 도입 작업이 필요하다. |
| E. DB 의존성 도입 보류 | DB 자동 설정을 만들지 않고 공개 API 또는 문서 작업만 먼저 진행한다. | CI 실패 위험이 낮다. | 구독 생성·조회 구현이 지연된다. | 첫 수직 MVP가 늦어진다. |

추천안:

첫 Backend 구현에서 JPA/MySQL/Flyway를 요청한다면 A를 동시에 승인 요청한다. MySQL service container, Testcontainers, OpenAPI 검증은 별도 SRE 또는 후속 기술 결정으로 분리한다. DB 의존성의 도입과 CI에서 실행 가능한 Backend test/build 경로는 같은 결정 묶음으로 다룬다.

근거:

현재 workflow는 이미 모든 PR에서 실행된다. 첫 구현에서 workflow까지 넓히면 변경 원인이 섞일 수 있다. DB 통합 검증은 Flyway와 Testcontainers 선택 후 별도 작업으로 강화하는 편이 안전하다.

사용자 승인 필요 문장:

`ARCH-004-DEC-012`: 첫 Backend 구현에서 JPA/MySQL/Flyway를 포함한다면 test profile 기반 datasource/Flyway 실행 경로를 함께 결정하고, MySQL service container, Testcontainers, OpenAPI CI 확장은 별도 작업으로 분리해도 되는지 승인해 주세요.

## 추천안 요약

| 항목 | 추천 |
| --- | --- |
| 첫 구현 범위 | 첫 수직 MVP 최소 API 5개 |
| 의존성 | JPA, MySQL JDBC driver, Flyway, Spring Security, `spring-security-test` |
| DB schema | `DATA-001`을 첫 schema 입력으로 사용 |
| Migration | Flyway 도입과 최초 V1 migration 작성 |
| MySQL Secret | 환경 변수 기반 전달, 실제 Secret 저장 금지 |
| 인증 | `AUTH-001` 기준, session 기반 우선 |
| 인증 주체 | 보호 API 포함 시 최소 로그인/session 생성 경로와 `memberId` principal 생성 방식 함께 결정 |
| CSRF/cookie/principal | CSRF 사용, HttpOnly/SameSite cookie 후보, 최소 `memberId` principal, token 전달 계약 포함 |
| API 계약 | `API-001` 5개 API 후보와 `/api/**` 인증 실패 JSON 계약 사용 |
| 오류 JSON | `code`, `message`, `fieldErrors` 공통 구조 |
| 아키텍처 | feature package + domain/application/infra/api 분리 |
| CI | DB 의존성 도입 시 test profile 기반 CI 실행 경로를 함께 결정, MySQL/Testcontainers/OpenAPI 확장은 별도 작업 |

## 영향

- 사용자 결정 이후에는 Backend build file, DB migration, JPA Entity, Repository, Service, Controller, DTO, SecurityConfig, 테스트가 후속 Backend 작업 범위에 들어갈 수 있다.
- MySQL 연결과 Secret 전달 방식이 정해져야 로컬 실행 런북을 안전하게 보완할 수 있다.
- API 오류 응답과 인증 방식이 정해져야 FE와 QA가 같은 실패 조건을 검증할 수 있다.
- DB 의존성을 도입하는 경우 CI에서 test/build가 실행 가능한 datasource와 Flyway 처리 경로가 함께 정해져야 한다.
- CI 확장 범위를 작게 유지하면 첫 구현 PR의 원인 분석이 쉬워지지만 DB 통합 검증은 후속 위험으로 남는다.

## 위험

- DATA/API/AUTH/ARCH/FOUNDATION 입력을 사용자 결정 없이 구현에 적용하면 문서와 코드가 다른 방향으로 갈 수 있다.
- Flyway migration은 병합 후 변경 비용이 커서 첫 schema 범위를 작게 잡아야 한다.
- session 기반 인증은 CSRF, cookie 속성, principal 생성 경로, `/api/**` JSON 실패 응답 계약을 함께 다루지 않으면 보호 API 보안 기준과 API 계약이 약해진다.
- CSRF token 전달 계약이 없으면 보호 POST API가 403으로 막히거나 FE 구현자가 임의의 token 전달 방식을 만들 수 있다.
- DB 의존성과 CI 검증 경로를 함께 정하지 않으면 다음 Backend 구현 PR에서 datasource 또는 Flyway 자동 설정으로 Application validation이 실패할 수 있다.
- Testcontainers나 MySQL CI를 미루면 로컬 DB와 CI의 검증 강도 차이가 남는다.
- OpenAPI 검증을 미루면 API-001과 Controller 구현의 차이를 리뷰와 테스트에 의존하게 된다.

## 승인 후 가능한 다음 작업

사용자가 위 결정 항목을 선택하면 다음 작업을 시작할 수 있다.

1. Backend 첫 수직 MVP 최소 구현 작업
2. DATA 후속 작업: 첫 schema와 Flyway migration 세부 검토
3. AUTH 후속 작업: session, CSRF, principal, Open Redirect 테스트 기준 보완
4. API 후속 작업: 오류 응답 구조와 DTO field 최종 검토
5. SRE 후속 작업: MySQL/Testcontainers/OpenAPI CI 확장 검토

## 승인 전 금지 작업

- 신규 의존성 추가
- DB migration 작성
- test profile, datasource 설정, Flyway 설정 작성
- JPA Entity, Repository, Service, Controller, DTO, SecurityConfig 작성
- AuthenticationEntryPoint, AccessDeniedHandler 또는 동등한 Security 예외 처리 코드 작성
- CSRF 설정 또는 FE API client 작성
- `DATA-001`, `API-001`, `AUTH-001`, `ARCH-001`, `FOUNDATION-000`의 상태 변경
- Backend/Frontend 코드 변경
- MySQL Secret 또는 실제 접속 정보 저장
- `.github/**` workflow 변경
- reset, rebase, force push, history rewrite
