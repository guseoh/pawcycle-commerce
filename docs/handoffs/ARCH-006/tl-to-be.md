# ARCH-006 Tech Lead → Backend Engineer 인수인계

## 전달 목적

사용자가 명시적으로 선택한 첫 Backend 구현 승인 입력과 구현 금지 범위를 Backend Engineer에게 전달한다.

이 인수인계는 ARCH-005 전체를 승인하지 않는다. `docs/adr/ARCH-006-first-backend-implementation-approved-inputs.md`의 `Approved`만 사용할 수 있다.

## 대상 역할

- Backend Engineer

## 입력 문서

- `AGENTS.md`
- `backend/AGENTS.md`
- `docs/roles/backend-engineer.md`
- `.agents/skills/backend-engineer/SKILL.md`
- `docs/adr/ARCH-006-first-backend-implementation-approved-inputs.md`
- `docs/adr/ARCH-005-backend-implementation-approval-candidates.md`
- `docs/product/PS-002-first-mvp-requirements.md`
- `docs/domain/DOMAIN-001-first-mvp-subscription-domain.md`
- `docs/adr/ARCH-001-first-vertical-mvp-architecture.md`
- `docs/adr/ARCH-003-backend-implementation-plan-and-dependency-proposal.md`
- `docs/adr/AUTH-001-login-return-and-auth-boundary.md`
- `docs/api/API-001-first-mvp-api-contract.md`
- `docs/data/DATA-001-first-mvp-data-model.md`
- `docs/data/DATA-002-first-mvp-logical-erd.md`
- `docs/adr/FOUNDATION-000-technology-version-decision.md`
- `docs/qa/README.md`

## 완료된 작업

- ARCH-005 결정 후보와 사용자 승인 입력을 1:1로 대조했다.
- Approved, Deferred, Decision Required, Explicitly Excluded를 분리했다.
- 첫 Backend 구현 최대 범위와 필요 시 PR 분할 순서를 정리했다.
- 최소 인증 API·CSRF·credential 계약 누락을 구현 전 중단 조건으로 식별했다.

## 사용 가능한 결과

- ARCH-006 Approved Inputs 문서
- 첫 Backend 구현의 허용 의존성·DB·API·Security·CI 범위
- 구현 금지 범위
- 테스트·QA 판단 기준
- 중단 조건과 남은 위험

## 관련 파일

- `docs/adr/ARCH-006-first-backend-implementation-approved-inputs.md`
- `docs/reports/ARCH-006/tl-report.md`
- `docs/handoffs/ARCH-006/tl-to-be.md`

## 실제 구현에 사용할 Approved 입력

### 기능

- `GET /api/products`
- `GET /api/products/{productId}`
- 회원 인증 정보 조회
- session 로그인과 로그아웃
- 현재 인증 회원의 최소 `memberId` 식별

### 의존성

- Spring Data JPA
- MySQL JDBC Driver
- Flyway
- Spring Security
- `spring-security-test`

### 데이터

- `members`
- `products`
- `skus`
- `products` 1:N `skus`
- 로그인과 공개 상품 조회에 필요한 최소 제약·index
- V1 Flyway migration

### 인증·보안

- 서버 관리 session
- SecurityContext 기반 인증 유지
- CSRF 활성화
- HttpOnly session cookie
- 운영 HTTPS의 Secure cookie
- SameSite 기본 후보 Lax
- 최소 `memberId` principal
- `PasswordEncoder`와 `BCryptPasswordEncoder`
- `/api/**` 401·403 JSON과 로그인 실패 401 JSON
- `code`, `message`, `fieldErrors` 공통 오류 응답

### 아키텍처·검증

- ARCH-001·ARCH-003의 지정된 member/catalog/common/security 책임 경계
- Java 25 Backend test/build
- test profile datasource와 Flyway migration 검증
- Spring Security, 공개 API, 로그인, 미인증·인가 실패 테스트
- CI MySQL service 검증

## 구현하면 안 되는 Deferred·Excluded 항목

### Deferred

- `subscriptions` schema와 package
- 구독 생성, 내 구독 목록, 내 구독 상세 API
- JWT access/refresh token과 관련 의존성·추상화
- OAuth2 Client와 OAuth2 로그인
- Testcontainers
- OpenAPI 자동 생성·검증 도구

### Explicitly Excluded

- 결제, 주문, 배송, 재고, 구독 상태와 상태 전이
- 관리자 기능과 관리자 인증·인가
- Docker, Docker Compose
- 실제 Secret 저장
- 평문 비밀번호 저장·비교·로그 출력
- session id 응답 body 노출
- 요청 body·query·고정값의 `memberId` 신뢰
- CSRF 전체 비활성화
- JPA Entity 전체를 principal에 보관
- 미래 확장만을 위한 인터페이스·공통 추상화·범용 인증 프레임워크

Deferred 항목은 후속 가능성을 뜻할 뿐 이번 구현에 빈 package, interface, dependency 또는 placeholder를 만들 수 있다는 뜻이 아니다.

## 첫 Backend PR의 최대 범위

한 PR로 진행할 때의 절대 상한은 다음뿐이다.

1. 승인된 5개 의존성
2. `members`, `products`, `skus` V1·JPA·Repository 최소 범위
3. Spring Security session·CSRF·cookie·principal 최소 구성
4. 로그인·로그아웃·현재 회원 식별
5. 공개 상품 목록·상세 API
6. 위 범위를 보호하는 테스트와 런북

다음 중 하나라도 발생하면 한 PR에 모두 넣지 않고 사용자 승인 입력의 순서대로 분리한다.

- 변경 설명이 DB, 인증, 공개 API 세 주제를 독립적으로 리뷰하기 어려운 크기
- CI MySQL service를 위해 Platform/SRE 작업이 선행돼야 함
- 최소 인증 계약 보완이 별도 PR로 필요함
- 한 실패가 다른 계층 검증을 막아 원인 분리가 어려움

분할 순서:

1. DB·Flyway·JPA·Security 기반
2. 세션 로그인·로그아웃
3. 공개 상품 목록·상세 API
4. QA 독립 검증

## 요구사항과 인수 조건

| 요구사항 | 구현 입력 | 최소 인수 조건 |
| --- | --- | --- |
| `REQ-PRODUCT-001` | `GET /api/products`, Product·SKU | 비회원·회원 모두 목록 조회, 상품 ID·명·대상 동물·짧은 설명·SKU 가격·구독 가능 요약 |
| `REQ-PRODUCT-002` | `GET /api/products/{productId}`, Product 1:N SKU | 비회원·회원 모두 상세 조회, SKU 이름·가격·구독 가능 여부, 없는 상품 실패 |
| `REQ-AUTH-001` 공개 경계 | AUTH-001, Spring Security | 공개 상품 API는 인증 없이 접근 |
| ARCH-006 session 승인 | 로그인·로그아웃·SecurityContext | 성공 시 세션 유지, 실패 시 인증 세션 미생성, 로그아웃 후 인증 제거 |
| ARCH-006 API 오류 승인 | 공통 오류 DTO, Security failure handler | 401·403·로그인 실패가 JSON이며 HTML·redirect·내부 예외 미노출 |
| ARCH-006 데이터 승인 | DATA-001·DATA-002 subset, V1 | fresh MySQL에 세 테이블과 Product-SKU 관계 적용, `subscriptions` 없음 |

API-001의 구독 요구사항과 DOMAIN-001의 Subscription 설계는 이번 구현 인수 조건이 아니다.

## 미결정 사항 또는 승인 필요 항목

실제 인증·V1 구현 전에 다음 최소 계약을 보완한다.

1. 로그인 식별자와 request field
2. 로그인·로그아웃·현재 회원 조회 URI, method, 성공 status와 body
3. 인증 실패·인가 실패의 안정적인 application error code
4. CSRF token 획득 방식, token/header/cookie 이름과 갱신 흐름
5. 비밀번호 해시 column의 이름·타입·길이·NOT NULL과 테스트 회원 생성 방식

다음 세부는 구현 PR에서 제안하고 근거·테스트로 검증할 수 있다.

- SQL 타입과 제약·index 이름
- JPA 연관관계·fetch·cascade
- 환경 변수 이름과 profile 우선순위
- CI MySQL version/image
- 개발·운영 cookie 세부 설정

상위 승인 입력과 충돌하거나 보안 경계를 바꾸면 구현 PR에서 임의 결정하지 말고 중단한다.

## 지켜야 할 규칙

- `backend/AGENTS.md`와 Backend Engineer 역할 Skill을 먼저 읽는다.
- ARCH-006 Approved 범위 밖 Proposed 문서를 최종 입력처럼 사용하지 않는다.
- build·dependency 변경 전 허용 목록을 다시 확인한다.
- 실제 접속 정보와 인증정보를 문서·로그·fixture에 남기지 않는다.
- public path와 authenticated path를 명시적으로 구분한다.
- Security 기본 설정만으로 모든 요청을 허용하지 않는다.
- JPA 자동 schema 생성으로 Flyway를 대체하지 않는다.
- CI workflow 변경이 필요하면 Platform/SRE 인수인계를 작성한다.

## 적용·실행 방법

1. DR1~DR3 최소 계약 보완 여부를 확인한다.
2. `main` 최신 상태와 clean `feat/be` 역할 브랜치를 준비한다.
3. Backend 입력 문서와 허용·금지 경로를 다시 확인한다.
4. 첫 PR 범위를 정하고 필요한 경우 분할한다.
5. 구현과 동시에 단위·slice·통합·Security·migration 테스트를 작성한다.
6. Java 25와 MySQL CI 경로까지 통과시킨다.
7. 작업 보고서, 필요한 QA 문서와 다음 역할 인수인계를 작성한다.

## 테스트 및 검증 기준

### DB·Flyway

- 빈 MySQL schema에 V1 적용
- 재기동 시 migration 재적용 오류 없음
- `members`, `products`, `skus`와 Product-SKU FK 확인
- 최소 unique·NOT NULL·index와 JPA mapping 일치
- `subscriptions`와 확장 도메인 table 부재

### Security·session

- CSRF token 없는 상태 변경 요청 거부
- 올바른 CSRF token 요청 허용
- 로그인 성공·실패
- 로그인 성공 후 session과 SecurityContext 유지
- 로그아웃 후 session·SecurityContext 정리
- principal 최소 `memberId`
- request memberId·고정 memberId 불신뢰
- cookie 속성과 session id 비노출

### API

- 공개 목록·상세의 비회원·회원 성공
- 존재하지 않는 상품 실패
- 공통 오류 shape와 빈 `fieldErrors`
- 401·403·로그인 실패 JSON
- HTML, redirect, stack trace와 내부 예외 미노출

### 저장소·CI

- 기존 Repository Validation 유지
- Java 25 Backend `test`와 `build`
- MySQL service, Flyway, datasource 검증
- Secret 의심 패턴과 금지 범위 검색
- `git diff --check`와 작업 산출물 validator

## 다음 역할의 검증 포인트

- 승인된 5개 외 의존성이 추가되지 않았는가?
- `subscriptions`, JWT, OAuth2와 미래 추상화가 없는가?
- V1이 인증·상품 최소 범위에 한정되는가?
- 공개 GET과 인증 필요 경계가 명시적인가?
- principal이 JPA Entity가 아니고 최소 `memberId`를 제공하는가?
- 로그인 실패가 인증 session을 만들지 않는가?
- API 401·403이 JSON이고 로그인 redirect가 아닌가?
- MySQL·Flyway·Security가 CI에서 실제 검증되는가?

## QA 독립 검증 필요 여부 판단 기준

- ARCH-006 문서 작업 자체는 QA 문서가 필요하지 않다.
- 첫 Backend 구현에서 실제 인증·인가 동작, credential 저장, session·cookie·CSRF 또는 401·403 계약을 추가하면 QA 독립 검증이 필요하다.
- foundation PR이 의존성만 추가하고 사용자-facing 동작과 credential schema를 전혀 바꾸지 않는 경우에만 QA 문서 생략을 검토할 수 있으며, 보고서에 근거를 남긴다.
- QA가 필요한 경우 `docs/qa/<작업 ID>/test-plan.md`에 정상·실패·권한·CSRF·cookie·민감정보 회귀 기준을 기록한다.

## AI 리뷰에서 남은 확인 항목

- ARCH-006 PR 생성 전 AI 리뷰 없음.
- Backend 구현 PR에서는 보안, 인증·인가, credential, 오류 응답과 테스트 누락을 CodeRabbit/Codex Review 결과와 함께 사람이 다시 판단한다.
- 미래 추상화나 JWT 선행 구조 제안은 ARCH-006과 충돌하므로 반영하지 않는다.

## 알려진 위험

- 최소 인증 API와 credential schema가 아직 계약으로 고정되지 않았다.
- CI MySQL service 변경이 Platform/SRE 선행 작업을 요구할 수 있다.
- SameSite=Lax는 기본 후보이며 실제 Frontend origin·HTTPS 환경에서 검증이 필요하다.
- Testcontainers 보류로 MySQL 검증은 CI service 품질과 test profile에 의존한다.

## 남은 위험과 주의 사항

- Proposed 문서의 승인되지 않은 field·URI를 편의상 확정하면 ARCH-006 범위를 넘는다.
- DB·Security·API를 한 번에 구현하면 테스트 실패 원인과 리뷰 책임이 섞일 수 있다.
- 세션 로그와 테스트 출력에 식별자·cookie·비밀번호 원문이 남지 않도록 별도 확인이 필요하다.
- 공개 API가 Security 기본값 때문에 막히거나 전체 permitAll 때문에 보호 경계가 사라질 수 있다.

## 다음 권장 작업

1. 최소 인증 API·CSRF·credential 계약 보완
2. 필요 시 Platform/SRE CI MySQL service 작업
3. Backend DB·Flyway·JPA·Security 기반
4. 세션 로그인·로그아웃
5. 공개 상품 API 2개
6. QA 독립 검증

## 완료 조건

- Approved 범위만 구현
- Deferred·Excluded 구현 없음
- DR1~DR3 해결
- Java 25·MySQL·Flyway·Security·API 검증 통과
- QA 필요 여부와 문서 경로 또는 생략 사유 기록
- 보고서·인수인계·PR 작성
- 자동 병합하지 않고 사용자 검토 대기

## 중단 조건

- 최소 인증 API·CSRF·credential 계약이 해결되지 않은 상태에서 해당 구현을 확정해야 함
- AUTH-001, API-001, DATA-001·002와 사용자 승인 입력이 충돌함
- 승인 목록 밖 의존성이나 Deferred 기능이 필요함
- Backend Engineer가 `.github/**`를 직접 변경해야 함
- 실제 Secret 또는 민감정보 노출 의심
- Java 25 test/build, MySQL/Flyway/Security 필수 검증 실패
- 다른 활성 `feat/be` 작업이나 mixed working tree 존재
- reset, rebase, force push 또는 history rewrite 필요
