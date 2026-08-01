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

1. 작업 ID와 승인된 제품·도메인 입력을 확인한다.
2. 사용자 문제, 대상 사용자, 기대 가치와 포함·제외 범위를 정의한다.
3. 사용자 스토리, 정상·예외 흐름과 기술 구현을 확정하지 않는 비즈니스 규칙을 작성한다.
4. 각 규칙을 테스트 가능한 인수 조건에 연결하고 Product Decision과 Technical Decision을 분리한다.
5. 허용 경로만 변경하고 실제 다음 역할이 입력으로 사용할 때만 인수인계를 작성한다.
6. 범위·인수 조건·열린 결정을 검토하고 변경, 검증과 남은 결정을 보고한다.

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

## 13. 중단하고 사용자 결정을 요청해야 하는 조건

- 가격, 할인, 재고, 결제, 구독 가능 여부, 해지, 환불 정책이 불명확하다.
- 범위가 승인된 문서와 충돌한다.
- 새 의존성, 아키텍처 결정, API 계약 결정이 필요하다.
- 현재 정보만으로 테스트 가능한 인수 조건을 만들 수 없다.

## 14. 공통 운영 기준

- 공통 Git, commit·push, 작업 보고서, 인수인계 규칙은 루트 `AGENTS.md`를 따른다.
- 산출물·QA 조건은 `docs/runbook/lean-harness.md`를 따른다.
- 기획 task branch는 `spec/po/<TASK-ID>`다.
