# 아키텍처 결정 기록(Architecture Decision Records)

이 디렉터리는 장기적으로 의미 있는 기술 결정을 ADR(Architecture Decision Record)로 보관한다.

ADR은 아키텍처(Architecture), 의존성(Dependency), 계약(Contract), 영속성 전략(Persistence Strategy), 보안 모델(Security Model), 배포 모델(Deployment Model), 장기 유지보수성에 영향을 주는 결정에 사용한다.

한 작업 내부에만 머무르고 쉽게 바꿀 수 있는 작은 구현 세부 사항에는 ADR을 만들지 않는다.

## 이름 규칙

작업 ID를 접두어로 사용한다.

```text
<작업 ID>-short-title.md
```

예: `ARCH-014-product-list-redis-cache.md`, `AUTH-003-session-authentication-approved-inputs.md`

새 ADR은 `docs/adr/adr-template.md`를 기준으로 작성한다.

## 상태 색인

ADR 원문의 상태 표기는 작성 당시 값이다. Lean Harness §15(과거 문서 비소급)에 따라 원문은 고치지 않는다. **실제 상태는 이 색인에서 관리한다.** 새 결정이 기존 ADR을 바꾸면 원문 대신 이 표를 갱신한다.

- 기준: `main` `1a9c41e2`, 2026-11-14 확인
- 판정 근거: 후속 승인 문서(ARCH-006, AUTH-003), 현재 코드와 설정, [Backend Architecture V2](../architecture/backend-architecture-v2.md), [운영 개요](../architecture/production-operations-overview.md)

| 실제 상태 | 뜻 |
| --- | --- |
| **유효** | 현재 코드와 운영이 이 결정을 따른다 |
| **채택 후 변경** | 결정은 승인돼 구현됐고, 이후 작업에서 세부 구조나 버전이 바뀌었다. 현재 원본은 "현재 기준" 열을 본다 |
| **대체됨** | 이후 결정이 이 결정을 대신한다 |
| **확인 필요** | 원문과 현재 구현이 다르고, 최종 의도를 확인한 기록이 없다 (현재 해당 없음) |
| **승인 과정 기록** | 결정 요청·후보·제안 단계의 문서다. 결정 결과는 다른 ADR에 있다 |

### 현재 결정

| ADR | 주제 | 원문 표기 | 실제 상태 | 현재 기준 |
| --- | --- | --- | --- | --- |
| [ARCH-001](ARCH-001-first-vertical-mvp-architecture.md) | 첫 수직 MVP 아키텍처 | Proposed | 채택 후 변경 | ARCH-006 #11에서 package·계층 경계로 승인. 현재 구조는 [Backend Architecture V2](../architecture/backend-architecture-v2.md) |
| [FOUNDATION-000](FOUNDATION-000-technology-version-decision.md) | 기술 버전 | Proposed | 채택 후 변경 | ARCH-006 #12에서 승인(Java 25 등). 현재 버전은 `backend/build.gradle`(Java 25, Spring Boot 4.1.0)과 `frontend/package.json`(Next.js 16.4.3)이 원본 |
| [AUTH-001](AUTH-001-login-return-and-auth-boundary.md) | 로그인 복귀와 인증 경계 | Proposed | 채택 후 변경 | ARCH-006 #6에서 승인, 세션 계약은 AUTH-003으로 구체화 |
| [AUTH-003](AUTH-003-session-authentication-approved-inputs.md) | 세션 인증 계약 | Approved | 유효 | `common/security/SecurityConfig` |
| [ARCH-007](ARCH-007-second-mvp-subscription-consistency.md) | 2차 MVP 구독 일관성 경계 | Proposed | 채택 후 변경 | 원문은 optimistic version과 `If-Match`를 쓰고 비관적 잠금은 "추가하지 않는다"고 적었다. 그러나 같은 날 첫 구현 PR #87이 "Subscription row locking과 조건부 version 갱신"으로 보호했고, 이후 [ARCH-011](ARCH-011-subscription-v2-runtime-structure.md)(Accepted)이 `FOR UPDATE` 유지를 명시했다. 현재 명령 경로는 `lockOwnedSubscription` 행 잠금 + version CAS + 멱등성이다 |
| [ARCH-008](ARCH-008-subscription-order-automation-consistency.md) | 정기배송 주문 자동화 일관성 | Accepted | 유효 | 대상별 `REQUIRES_NEW` 트랜잭션, `subscription/automation` |
| [ARCH-009](ARCH-009-commerce-runtime-refactoring.md) | Commerce 런타임 리팩터링 | (표기 없음) | 채택 후 변경 | API·schema를 바꾸지 않고 책임을 나누는 원칙은 유지. 단일 `CommerceService`는 #275(2026-09-03)에서 제거됐고, 이후 구조는 Backend Architecture V2 T02·T06·T07 |
| [ARCH-010](ARCH-010-authentication-provider-ownership.md) | 인증 Provider 책임 | Accepted | 유효 | `MemberAuthenticationProvider` |
| [ARCH-011](ARCH-011-subscription-v2-runtime-structure.md) | Subscription V2 런타임 구조 | Accepted | 채택 후 변경 | 명시 SQL·typed projection·유스케이스 트랜잭션 원칙은 유지. `V2SubscriptionJdbcStore`는 #275(2026-09-03)에서 제거됐고, 이후 Backend Architecture V2 T08·T09에서 `subscription/persistence`의 조회·쓰기 영속 계층으로 정리됐다 |
| [ARCH-012](ARCH-012-production-observability-boundary.md) | Production 관측 경계 | Accepted | 유효 | OCI `app01` same-host 관측, `infra/production-observability` |
| [ARCH-013](ARCH-013-rds-single-az.md) | RDS Single-AZ 전환 준비 | Approved (저장소 준비) | 대체됨 | AWS 운영 종료 후 OCI로 전환. [운영 개요](../architecture/production-operations-overview.md) |
| [ARCH-014](ARCH-014-product-list-redis-cache.md) | 상품 목록 Redis cache | Accepted | 유효 | `ProductListCacheInvalidator` |
| [ARCH-015](ARCH-015-ai-product-recommendation-boundary.md) | AI 상품 추천 경계 | Accepted | 유효 | 서버 후보 검증 후 AI 재정렬. 기본 비활성(`PAWCYCLE_RECOMMENDATION_AI_ENABLED=false`) |

### 승인 과정 기록

| ADR | 단계 | 결과가 기록된 곳 |
| --- | --- | --- |
| [ARCH-002](ARCH-002-first-backend-implementation-readiness.md) | 구현 착수 기준 제안 | ARCH-006 |
| [ARCH-003](ARCH-003-backend-implementation-plan-and-dependency-proposal.md) | 구현 계획·의존성 제안 | ARCH-006 (#2 의존성, #11 package 경계 승인) |
| [ARCH-004](ARCH-004-backend-implementation-decision-request.md) | 결정 요청 | ARCH-006 |
| [ARCH-005](ARCH-005-backend-implementation-approval-candidates.md) | 승인 후보 | ARCH-006 |
| [ARCH-006](ARCH-006-first-backend-implementation-approved-inputs.md) | 첫 Backend 구현 승인 입력 | 이 문서 자체가 승인 결과 |
| [AUTH-002](AUTH-002-session-authentication-contract-proposal.md) | 세션 인증 계약 제안 | AUTH-003 |
