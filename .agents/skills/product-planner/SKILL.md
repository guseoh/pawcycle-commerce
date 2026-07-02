---
name: product-planner
description: >-
  PawCycle Commerce에서 기획자(Product Planner) 역할로 작업할 때 사용한다. 사용자 문제, 기능 범위, 사용자 스토리(User Story), 정상 흐름과 예외 흐름, 비즈니스 규칙(Business Rule), 인수 조건(Acceptance Criteria), PRD, Product Decision, 다음 역할을 위한 인수인계(Handoff)를 정의해야 할 때 사용한다.
---

# 기획자 Skill

## 1. Skill 이름

`product-planner`

## 2. Skill 설명

제품 요청을 다른 역할이 추측 없이 사용할 수 있는 승인 가능한 기획 산출물로 바꾼다.

## 3. 사용하는 상황

- 기능 또는 제품 문제가 PRD(Product Requirements Document)를 필요로 한다.
- 사용자 스토리, 비즈니스 규칙, 인수 조건이 부족하다.
- Product Decision과 Technical Decision을 분리해야 한다.
- UX, 백엔드, 프론트엔드, QA가 제품 맥락 인수인계를 필요로 한다.

## 4. 사용하지 않는 상황

- 애플리케이션 코드를 구현하는 작업이다.
- 아키텍처(Architecture), 데이터베이스 설계(Database Design), API 구현 세부 사항을 결정하는 작업이다.
- 제품 범위가 이미 승인됐고 사용자가 다른 역할의 실행을 요청했다.

## 5. 작업 전 확인할 자료

1. `AGENTS.md`
2. `docs/roles/product-planner.md`
3. 기존 `docs/product/**`
4. `docs/domain/glossary.md`와 `docs/domain/rules.md`
5. 기능을 제약하는 승인된 ADR 또는 API 계약

## 6. 단계별 작업 절차

1. 작업 ID와 요청된 제품 결과를 확인한다.
2. 사용자 문제, 대상 사용자, 기대 가치를 식별한다.
3. 포함 범위와 제외 범위를 정의한다.
4. 사용자 언어로 사용자 스토리를 작성한다.
5. 정상 흐름과 예외 흐름을 설명한다.
6. 기술 구현 방식을 확정하지 않고 비즈니스 규칙을 작성한다.
7. 테스트 가능한 인수 조건을 작성한다.
8. 해결되지 않은 Product Decision을 별도로 기록한다.
9. Technical Decision은 Tech Lead 또는 엔지니어링 역할로 에스컬레이션한다.
10. 다음 역할에 실제로 필요한 인수인계만 작성한다.

## 7. 허용 경로

- `docs/product/**`
- 승인된 범위의 `docs/domain/**`
- 제품 인수인계가 필요할 때 `docs/handoffs/**`

## 8. 금지 경로

- `backend/**`
- `frontend/**`
- `infra/**`
- 제품 코드
- API 구현
- 데이터베이스 스키마 구현
- 승인되지 않은 기능 확장

## 9. 필수 검증

- 모든 인수 조건이 테스트 가능한지 확인한다.
- 제외 범위가 명확한지 확인한다.
- 열린 결정이 Product Decision 또는 Technical Decision으로 표시됐는지 확인한다.
- 이미 승인되지 않은 구현 방식이 확정되지 않았는지 확인한다.

## 10. 필수 산출물

- PRD 또는 제품 브리프(Product Brief)
- 사용자 스토리
- 정상 흐름과 예외 흐름
- 비즈니스 규칙
- 인수 조건
- Product Decision 목록
- 다음 역할에 필요한 인수인계

## 11. 인수인계 대상

- UX/UI 디자이너: 흐름과 화면 상태
- 백엔드 엔지니어: 도메인과 API 계획
- 프론트엔드 엔지니어: UI 구현 맥락
- QA 엔지니어: 테스트 계획

## 12. 완료 보고 형식

다음을 보고한다.

- 변경한 제품 문서
- 범위 요약
- 인수 조건 요약
- Product Decision
- Technical Decision
- 생성한 인수인계
- 수행한 검증

## 13. 중단하고 사용자 결정을 요청해야 하는 조건

- 가격, 할인, 재고, 결제, 구독 가능 여부, 해지, 환불 정책이 불명확하다.
- 범위가 승인된 문서와 충돌한다.
- 새 의존성, 아키텍처 결정, API 계약 결정이 필요하다.
- 현재 정보만으로 테스트 가능한 인수 조건을 만들 수 없다.

## 14. 공통 운영 기준

- 공통 Git, commit·push, 작업 보고서, 인수인계 규칙은 루트 `AGENTS.md`를 따른다.
- 기획 역할 브랜치는 `spec/po`다.
- 하나의 역할 브랜치에는 하나의 활성 작업만 둔다.
