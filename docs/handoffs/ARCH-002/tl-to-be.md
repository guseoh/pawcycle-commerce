# ARCH-002 TL → Backend Engineer 인수인계

## 다음 Backend 작업의 목적

첫 Backend 작업은 실제 구현보다 구현 계획과 의존성 도입안 정리로 시작하는 것을 권장한다.

권장 작업:

```text
BE-001 Backend 구현 계획과 의존성 도입안 정리
```

목적은 첫 수직 MVP Backend 구현 전에 JPA, Flyway, MySQL, Security 의존성 도입 범위와 테스트 전략을 정리하고, 사용자 승인이 필요한 결정을 확정하는 것이다.

## 사용할 입력 문서

- `AGENTS.md`
- `backend/AGENTS.md`
- `.agents/skills/backend-engineer/SKILL.md`
- `docs/product/PS-002-first-mvp-requirements.md`
- `docs/product/PS-003-ux-product-decisions.md`
- `docs/design/UX-001-first-mvp-subscription-experience.md`
- `docs/reports/UX-002/ux-report.md`
- `docs/handoffs/UX-002/ux-to-tl.md`
- `docs/domain/DOMAIN-001-first-mvp-subscription-domain.md`
- `docs/adr/ARCH-001-first-vertical-mvp-architecture.md`
- `docs/data/DATA-001-first-mvp-data-model.md`
- `docs/api/API-001-first-mvp-api-contract.md`
- `docs/adr/AUTH-001-login-return-and-auth-boundary.md`
- `docs/adr/FOUNDATION-000-technology-version-decision.md`
- `docs/runbook/FOUNDATION-001-local-development.md`
- `docs/runbook/FOUNDATION-002-ci-validation.md`
- `docs/handoffs/FOUNDATION-002/sre-to-be-fe-tl.md`
- `docs/adr/ARCH-002-first-backend-implementation-readiness.md`

## 아직 Approved가 아닌 결정

- `DATA-001`: Proposed Data Design
- `API-001`: Proposed API Contract
- `AUTH-001`: Proposed Authentication Design
- `ARCH-001`: Proposed Architecture Decision
- `FOUNDATION-000`: Status: Proposed

위 문서는 구현 계획의 후보 입력으로 사용할 수 있지만, 사용자 승인 전에는 최종 DB schema, Controller 계약, 인증 구현, 신규 의존성 도입 근거로 사용하지 않는다.

## 구현 가능 범위

다음은 사용자 승인 없이도 계획 문서 또는 승인 요청 정리 범위에서 진행할 수 있다.

- Backend 의존성 도입안 검토
- JPA, Flyway, MySQL, Security 도입 필요성 확인
- DATA-001, API-001 기반 구현 순서 정리
- 도메인, 애플리케이션, API, 영속성 패키지 구조 후보 작성
- 단위 테스트, slice 테스트, 통합 테스트 후보 분리
- CI 확장 필요 여부 정리
- 사용자 승인 요청 목록 작성

## 구현 금지 범위

사용자 승인 없이 다음을 구현하지 않는다.

- DB migration
- JPA Entity
- Repository, Service, Controller, DTO
- SecurityConfig
- 실제 Spring Security 설정
- MySQL 연결 설정
- API client
- Frontend 화면 구현
- Dockerfile, Docker Compose
- 배포 workflow
- 신규 의존성 추가
- 신규 Secret 추가
- 성능 최적화
- DATA-001, API-001, AUTH-001, ARCH-001, FOUNDATION-000 상태 변경

## 의존성 추가가 필요한 경우 중단 조건

다음 의존성이 필요해지면 구현을 멈추고 사용자 승인 또는 별도 기술 결정으로 넘긴다.

- `spring-boot-starter-data-jpa`
- `spring-boot-starter-security`
- MySQL JDBC driver
- Flyway 관련 artifact
- Spring Security Test
- Testcontainers
- OpenAPI 생성 또는 검증 도구
- 추가 assertion, fixture, mapper, query 라이브러리

## DB/API/Auth 확정이 필요한 경우 중단 조건

다음 질문에 답해야 코드 구현이 가능하면 중단한다.

- 실제 테이블명과 컬럼명은 무엇인가?
- FK, CHECK, index를 어떤 DDL로 생성할 것인가?
- `created_date`, `next_order_date`, 감사 필드의 DB 타입은 무엇인가?
- 실제 API URI, 요청 DTO, 응답 DTO는 무엇인가?
- 오류 응답 JSON과 HTTP 상태는 무엇인가?
- 세션 기반인가 토큰 기반인가?
- 인증 컨텍스트에서 회원 식별자를 어떤 principal 구조로 꺼낼 것인가?
- CSRF와 쿠키 정책은 무엇인가?
- 로그인 복귀 경로는 어디에 저장하고 어떻게 검증할 것인가?
- Open Redirect 검증 유틸은 어느 경계에 둘 것인가?

## 검증 기준

문서 계획 작업이면 최소 다음을 검증한다.

- `git status --short --branch`
- `git diff --check`
- `git diff --stat`
- `git diff --name-status`
- `Write-Output 'BE-001' | py -3 scripts/validate-task-artifacts.py --from-stdin`
- `& "C:\Program Files\Git\bin\bash.exe" scripts/validate-commit-message.sh --message "<커밋 메시지>"`

Backend 코드나 의존성을 변경하는 승인된 작업이면 최소 다음을 추가한다.

- `cd backend`
- `.\gradlew.bat test`
- `.\gradlew.bat build`

DB, JPA, Flyway, Security가 추가되면 CI 검증 범위 확대가 필요한지 SRE 또는 TL에게 넘긴다.

## 예상 PR 제목

구현 계획 작업:

```text
docs(backend): Backend 구현 계획과 의존성 도입안 정리
```

사용자가 Proposed 문서를 구현 입력으로 승인한 뒤 실제 최소 구현을 진행하는 경우:

```text
feat(backend): 첫 수직 MVP 구독 API 최소 구현
```

## 남은 주의 사항

- 첫 Backend 구현은 승인된 범위보다 넓어지기 쉽다. 결제, 재고, 배송, 구독 상태 모델, 구독 변경 액션은 포함하지 않는다.
- 요청 본문이나 query parameter의 `memberId`를 신뢰하지 않는다.
- 존재하지 않는 구독과 다른 회원 소유 구독을 구분해 노출하지 않는 원칙을 유지한다.
- 다음 주문 예정일은 서버가 계산한 확정값으로 다룬다.
- 클라이언트 사전 계산, 배송 예정일, 정기 주문 자동 생성은 첫 MVP 범위가 아니다.
