# ARCH-005 Tech Lead → Backend Engineer 인수인계

## 전달 목적

ARCH-004 결정 요청과 DATA-002 논리 ERD를 함께 반영한 Backend 구현 승인 후보를 Backend Engineer에게 전달한다.

이번 인수인계는 Backend 구현 착수 지시가 아니다. 사용자 승인 전에는 Backend Engineer가 build file, DB migration, JPA Entity, Repository, Service, Controller, DTO, SecurityConfig를 작성하면 안 된다.

## 대상 역할

- Backend Engineer

## 입력 문서

- `AGENTS.md`
- `backend/AGENTS.md`
- `docs/roles/backend-engineer.md`
- `.agents/skills/backend-engineer/SKILL.md`
- `docs/roles/tech-lead.md`
- `.agents/skills/tech-lead/SKILL.md`
- `docs/adr/ARCH-004-backend-implementation-decision-request.md`
- `docs/adr/ARCH-005-backend-implementation-approval-candidates.md`
- `docs/data/DATA-001-first-mvp-data-model.md`
- `docs/data/DATA-002-first-mvp-logical-erd.md`
- `docs/api/API-001-first-mvp-api-contract.md`
- `docs/adr/AUTH-001-login-return-and-auth-boundary.md`
- `docs/adr/ARCH-001-first-vertical-mvp-architecture.md`
- `docs/adr/ARCH-003-backend-implementation-plan-and-dependency-proposal.md`
- `docs/adr/FOUNDATION-000-technology-version-decision.md`

## 사용 가능한 결과

- ARCH-005 Decision Candidates 문서
- ARCH-004 12개 결정 항목 요약
- DATA-002 Proposed 논리 ERD 반영 요약
- 보완 결정 항목 5개
- Backend 구현 전 중단 조건
- 사용자 확인 질문 목록

## 인수 조건과 추적성

| 입력 | ARCH-005 반영 |
| --- | --- |
| ARCH-004 결정 요청 | 12개 결정 항목을 Decision Required로 재정리 |
| DATA-002 논리 ERD | `members`, `products`, `skus`, `subscriptions` 관계와 제약 후보 반영 |
| ARCH-003 구현 계획 | 의존성, package, 테스트, CI 후보 반영 |
| DATA-001/API-001/AUTH-001 | Proposed 입력으로 유지하고 사용자 결정 필요 항목으로 분리 |
| Tech Lead 역할 기준 | 사용자 최종 승인권과 AI 보조 검토 권한을 분리 |

## Backend 구현 전 사용자 승인 필요 항목

- 첫 Backend 구현 범위
- 신규 의존성 도입 범위
- DATA-001과 DATA-002를 실제 DB schema 후보 입력으로 사용할지 여부
- Flyway 도입 여부와 최초 migration 작성 여부
- MySQL 연결 정책과 Secret 전달 방식
- AUTH-001을 Spring Security 구현 기준으로 사용할지 여부
- session 기반 또는 token 기반 인증 방식
- CSRF, cookie, principal 구조
- API-001을 Controller 계약 후보로 사용할지 여부
- 오류 응답 JSON 구조
- ARCH-001과 ARCH-003 package/계층 경계 사용 여부
- CI 확장 범위
- DB 의존성 도입 시 CI 검증 경로
- 인증 주체 생성 경로
- API 인증 실패 응답 계약
- CSRF token 전달 계약

## 승인되지 않으면 중단할 지점

- `backend/build.gradle` 또는 `backend/settings.gradle` 수정 전
- DB migration 작성 전
- JPA Entity 작성 전
- Repository, Service, Controller, DTO 작성 전
- SecurityConfig 또는 security failure handler 작성 전
- datasource, Flyway, test profile 설정 전
- API 오류 응답 공통 구조 작성 전
- GitHub Actions workflow 수정 전

## DATA-002를 사용할 때의 주의 사항

- DATA-002는 Proposed 논리 ERD 보완 산출물이며 최종 DB schema가 아니다.
- `members`, `products`, `skus`, `subscriptions`는 논리 테이블 후보로만 사용한다.
- 실제 SQL DDL, DB 타입, FK/인덱스 이름, JPA 매핑은 Backend 구현 PR에서 다시 설명해야 한다.
- `subscriptions.member_id`와 `subscriptions.sku_id` 관계는 소유자 검증과 단일 SKU 구독 후보로 사용한다.
- `next_order_date`, `delivery_cycle_weeks`, `quantity`, `subscribable`은 도메인/애플리케이션 검증과 DB 제약 후보를 함께 검토한다.
- 구독 상태, 결제, 배송, 재고, 삭제 정책은 DATA-002에서 첫 MVP 범위로 넣지 않았다.

## 다음 역할의 검증 포인트

- 사용자 선택 없이 Decision Required 항목을 최종 입력처럼 사용하지 않았는지 확인한다.
- 신규 의존성을 추가하기 전 승인 범위가 문서에 남아 있는지 확인한다.
- 보호 API를 구현하기 전 인증 주체 생성 경로가 정해졌는지 확인한다.
- DB 의존성 도입 시 `./gradlew test`와 `./gradlew build`가 통과할 test profile 경로가 있는지 확인한다.
- API-001 오류 응답 후보와 Spring Security 실패 응답이 충돌하지 않는지 확인한다.
- DATA-002 후보가 실제 schema로 바뀌는 순간 SQL DDL과 JPA 매핑 근거가 설명 가능한지 확인한다.

## QA 필요 여부

- QA 문서 불필요.
- 이유: 이번 인수인계는 문서 기반 결정 후보 정리이며 제품 동작과 실제 DB schema를 변경하지 않는다.
- 실제 Backend 구현 PR부터는 QA 독립 검증 필요 여부를 다시 판단해야 한다.

## AI 리뷰에서 남은 확인 항목

- PR 생성 전 AI 리뷰 없음.
- PR 생성 후 CodeRabbit/Codex Review가 남으면 Tech Lead가 반영, 일부 미반영, 후속 작업 분리를 판단해야 한다.

## 중단 조건

- 사용자가 결정하지 않은 항목을 최종 입력으로 사용해야 하는 경우
- 실제 Backend 구현을 시작해야 하는 경우
- 신규 의존성 추가가 필요한 경우
- DB migration, Entity, Repository, Service, Controller, DTO, SecurityConfig 작성이 필요한 경우
- API-001, DATA-001, DATA-002, AUTH-001, ARCH-001, FOUNDATION-000 원본 상태를 바꿔야 하는 경우
- Secret 또는 민감정보 노출이 의심되는 경우
- reset, rebase, force push, history rewrite가 필요한 경우

## 남은 위험

- 첫 Backend 구현 범위를 API 5개로 선택하면 DB/API/Auth 결정이 동시에 필요하다.
- 공개 상품 API만 먼저 선택하면 수직 MVP 구독 흐름 검증이 늦어진다.
- 보호 API를 포함하면서 인증 주체 생성 경로가 없으면 임시 인증 구현 위험이 생긴다.
- DB 의존성만 먼저 추가하고 CI datasource/Flyway 경로를 정하지 않으면 검증 실패 가능성이 높다.
- Testcontainers와 OpenAPI 검증을 보류하면 DB 통합 검증과 계약 drift 방지는 후속 위험으로 남는다.

## 다음 권장 작업

1. 사용자가 ARCH-005 Decision Required 항목을 선택한다.
2. 선택 결과를 별도 승인 문서 또는 Backend 구현 작업 입력으로 고정한다.
3. 그 뒤 Backend Engineer가 첫 수직 MVP Backend 최소 구현 작업을 시작한다.
