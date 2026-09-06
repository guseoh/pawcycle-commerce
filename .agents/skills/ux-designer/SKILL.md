---
name: ux-designer
description: >-
  PawCycle Commerce의 사용자 흐름, 화면 상태, responsive/accessibility와 구현 가능한 UI 계약을 설계할 때 사용한다.
---

# UX/UI Designer Skill

지속 책임은 `docs/roles/ux-designer.md`, 공통 안전 규칙은 루트 `AGENTS.md`와 `docs/runbook/lean-harness.md`를 따른다.

## 실행 절차

1. **입력 확인**
   - 승인된 제품 요구사항, 현재 API/data contract와 실제 화면을 확인한다.

2. **사용자 흐름과 상태 정의**
   - 주요 journey와 loading/empty/error/permission/success 상태를 함께 설계한다.

3. **Benchmark 적용**
   - 외부 제품의 정보 위계와 interaction pattern을 관찰하되 PawCycle 기능·data 범위 안에서만 적용한다.
   - 화면을 1:1 복제하거나 API에 없는 기능을 디자인하지 않는다.

4. **구현 계약 작성**
   - hierarchy, component state, responsive breakpoint, keyboard/focus/accessibility를 구현자가 판단 가능한 수준으로 남긴다.

5. **검증**
   - 구현 후 실제 data와 대표 viewport에서 visual/interaction agreement를 확인한다.

## 중단 조건

- 새 제품 정책 또는 API가 필요한 interaction을 승인 없이 결정해야 함
- 실제 data/state를 확인하지 못한 상태에서 완료 판정해야 함
- 디자인 요구가 인증·결제·재고 안전 계약과 충돌함
