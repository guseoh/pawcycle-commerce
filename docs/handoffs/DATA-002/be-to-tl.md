# DATA-002 Backend Engineer → Tech Lead 인수인계

## 전달 목적

첫 MVP 논리 ERD 산출물을 `DATA-002` 작업 ID로 정리한 결과를 Tech Lead 검토에 전달한다.

이 인수인계는 Backend 구현 착수 지시가 아니다. 사용자 승인 전에는 DB migration, JPA Entity, Repository, Service, Controller, DTO, SecurityConfig를 작성하지 않는다.

## 대상 역할

- Tech Lead

## 입력 문서

- `AGENTS.md`
- `backend/AGENTS.md`
- `docs/roles/backend-engineer.md`
- `.agents/skills/backend-engineer/SKILL.md`
- `docs/product/PS-002-first-mvp-requirements.md`
- `docs/domain/DOMAIN-001-first-mvp-subscription-domain.md`
- `docs/adr/ARCH-001-first-vertical-mvp-architecture.md`
- `docs/data/DATA-001-first-mvp-data-model.md`
- `docs/api/API-001-first-mvp-api-contract.md`
- `docs/data/DATA-002-first-mvp-logical-erd.md`
- `docs/reports/DATA-002/be-report.md`

## 사용 가능한 결과

- DATA-002 논리 ERD 문서
- `members`, `products`, `skus`, `subscriptions` 논리 테이블 후보
- 회원-구독, 상품-SKU, SKU-구독 관계 후보
- 수량, 배송 주기, SKU 구독 가능 여부, 날짜 계산 기준의 제약 후보
- DATA-001/API-001 매핑과 요구사항 추적성

DATA-001의 테이블 후보와 DATA-002의 논리 ERD는 Proposed 데이터 설계 입력이다. 사용자 승인 전까지 실제 DB schema, Flyway migration, JPA Entity의 최종 입력으로 보지 않는다.

## 인수 조건과 추적성

| 인수 조건 | DATA-002 반영 |
| --- | --- |
| 첫 MVP 데이터 모델 기준 유지 | DATA-001 매핑 유지 |
| API 계약 변경 금지 | API-001 매핑만 문서화 |
| 실제 구현 금지 | DB migration, JPA, Repository, Service, Controller 미작성 |
| 사용자 승인 전 Proposed 유지 | 문서 상태를 Proposed 데이터 설계 보완 산출물로 유지 |
| 검증기 접두사 수정 금지 | DATA-002 작업 ID 사용, 검증 스크립트 수정 없음 |

## 다음 역할의 검증 포인트

- DATA-002가 DATA-001과 API-001을 잘못 확정하거나 변경한 부분이 없는지 확인한다.
- DATA-001과 DATA-002의 테이블·컬럼 후보가 최종 DB 설계처럼 해석되지 않도록 Proposed 상태가 유지되는지 확인한다.
- 논리 테이블 4개가 첫 MVP 범위와 맞는지 확인한다.
- 구독 상태, 결제, 재고, 배송, 삭제 정책을 첫 MVP ERD에 포함하지 않은 판단이 맞는지 확인한다.
- `next_order_date`를 서버 계산값으로 보존하는 후보가 승인 가능한지 확인한다.
- 후속 Backend 구현 전에 Flyway/JPA/API 오류 응답/인증 컨텍스트 결정이 별도 승인되어야 하는지 확인한다.

## QA 필요 여부

- QA 문서 불필요.
- 이유: 문서 기반 논리 ERD 정리이며 제품 동작과 실제 DB schema를 변경하지 않는다.
- 실제 Backend 구현 PR부터는 QA 독립 검증 필요 여부를 다시 판단해야 한다.

## AI 리뷰에서 남은 확인 항목

- PR #27의 CodeRabbit/Codex Review thread 6건은 DATA-002 리뷰 반영 커밋으로 반영했다.
- 후속 리뷰가 새로 남으면 Tech Lead가 반영, 일부 미반영, 후속 작업 분리를 판단해야 한다.

## 승인 필요 항목

- DATA-002 논리 ERD를 후속 Backend 구현 입력으로 사용할지 여부
- Flyway 마이그레이션 작성 시작 여부
- JPA Entity와 값 객체 매핑 방향
- MySQL CHECK 제약 적용 여부
- 인증 회원과 `members` 테이블의 연결 방식
- 삭제·탈퇴·보관·익명화 정책 보류 유지 여부

## 중단 조건

- 사용자 승인 전 실제 Flyway/JPA 구현을 시작해야 하는 경우
- SQL DDL, DB 타입, FK/인덱스 이름을 지금 확정해야 하는 경우
- 구독 상태, 결제, 재고, 배송 모델이 필요해지는 경우
- soft delete, hard delete, 삭제·탈퇴·보관·익명화 정책을 확정해야 하는 경우
- DATA-001, API-001, DATA-002 사이에 충돌이 발견되는 경우
- 신규 의존성이나 GitHub Actions 변경이 필요한 경우
- Secret 또는 민감정보 노출이 의심되는 경우

## 남은 위험

- DATA-002는 Proposed 산출물이며 최종 DB 설계가 아니다.
- 실제 DB 타입, 제약 이름, 인덱스 이름, JPA 연관관계는 확정되지 않았다.
- 인증 구현 방식이 확정되지 않아 `members`와 인증 모델의 상세 관계는 보류 상태다.
- 후속 결제·재고 MVP에서 SKU 구독 가능 여부와 판매/재고 상태 분리를 재검토할 수 있다.
