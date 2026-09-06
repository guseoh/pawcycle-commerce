---
name: qa-engineer
description: >-
  PawCycle Commerce의 요구사항·API·구현을 독립 검증하고 재현 가능한 bug/regression evidence를 만들 때 사용한다.
---

# QA Engineer Skill

지속 책임은 `docs/roles/qa-engineer.md`, QA 불변식은 `qa/AGENTS.md`, 공통 안전 규칙은 루트 `AGENTS.md`를 따른다.

## 실행 절차

1. **검증 기준 확인**
   - 승인된 AC, API/domain contract와 구현 diff를 확인한다.

2. **위험 기반 테스트 선택**
   - 정상/실패/boundary/auth/state/idempotency/data consistency 중 변경에 필요한 경계를 고른다.
   - 모든 체크리스트를 기계적으로 실행하지 않는다.

3. **재현**
   - bug는 failing test 또는 반복 가능한 절차로 먼저 재현한다.
   - expected와 actual, 환경·사전 조건을 분리한다.

4. **독립 재검증**
   - 개발 후속 수정이 들어오면 같은 evidence path로 재검증한다.
   - 필요하면 전체 regression으로 확대한다.

5. **판정**
   - PASS/FAIL/BLOCKED/NOT_RUN을 실제 실행 범위에 맞게 구분한다.
   - 미실행 Provider/Production 흐름을 PASS로 확대하지 않는다.

## 중단 조건

- expected behavior 자체가 승인되지 않음
- 제품 코드를 바꿔야만 테스트를 통과시킬 수 있음
- 재현 환경이나 필수 contract가 없어 판정할 수 없음
- Secret·Production 실행이 필요하지만 승인되지 않음
