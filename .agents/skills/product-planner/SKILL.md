---
name: product-planner
description: >-
  PawCycle Commerce의 사용자 문제, 제품 범위, 비즈니스 규칙과 Acceptance Criteria를 정리할 때 사용한다.
---

# Product Planner Skill

지속 책임은 `docs/roles/product-planner.md`, 공통 안전 규칙은 루트 `AGENTS.md`와 `docs/runbook/lean-harness.md`를 따른다.

## 실행 절차

1. **문제 정의**
   - 사용자가 해결하려는 문제와 기대 결과를 확인한다.
   - 현재 기능·계약과 새 요구를 구분한다.

2. **범위 설정**
   - 포함·제외 범위와 핵심 정상/실패 흐름을 정한다.
   - 파일·기술·화면 개수가 아니라 하나의 사용자 목적을 기준으로 묶는다.

3. **규칙과 인수 조건**
   - 구현자가 추측하면 안 되는 비즈니스 규칙만 명시한다.
   - 관찰 가능한 Acceptance Criteria로 바꾼다.

4. **미결정 분리**
   - 제품 결정, 기술 선택, 구현 세부사항을 섞지 않는다.
   - 구현을 막는 제품 결정만 사용자 승인 대상으로 올린다.

5. **지속 문서 여부 판단**
   - 장기 제품 계약이면 canonical product/domain 문서를 갱신한다.
   - 일회성 구현 범위면 PR/task spec으로 충분한지 우선 본다.

## 중단 조건

- 사용자의 제품 정책 선택이 필요한데 승인되지 않음
- expected behavior가 승인되지 않았거나 관찰 가능한 Acceptance Criteria를 작성할 수 없음
- 기존 승인 계약과 새 요청이 충돌함
- 기술 구현을 제품 요구사항으로 위장해야만 진행 가능함
