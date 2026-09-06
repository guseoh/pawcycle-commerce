---
name: frontend-engineer
description: >-
  PawCycle Commerce의 Next.js/React UI, TypeScript API client, UI state, accessibility와 Frontend 테스트를 구현할 때 사용한다.
---

# Frontend Engineer Skill

지속 책임은 `docs/roles/frontend-engineer.md`, Frontend 코드 불변식은 `frontend/AGENTS.md`, 공통 안전 규칙은 루트 `AGENTS.md`를 따른다.

## 실행 절차

1. **계약 확인**
   - 승인된 UX와 Backend API contract를 확인한다.
   - 서버 권위 값과 local UI state를 구분한다.

2. **상태 설계**
   - loading, empty, error, success, retry, auth-expiry 상태를 함께 정의한다.
   - mutation 재시도와 입력 보존이 서버 안전 경계를 우회하지 않는지 확인한다.

3. **최소 구현**
   - 기존 shared HTTP/client/component 패턴을 재사용한다.
   - API에 없는 제품 정책이나 가짜 데이터를 만들지 않는다.

4. **검증**
   - helper와 API adapter contract test를 먼저 실행한다.
   - 중요한 interaction regression을 추가하고 lint, typecheck, test, build로 확대한다.

5. **결과 보고**
   - 사용자에게 보이는 변화, contract 영향, 미실행 Browser/Provider 흐름과 남은 UX 위험을 정리한다.

## 중단 조건

- 승인된 UX에 필요한 상태·필드·작업이 Backend API contract에 없어서 Backend 동작을 추측하거나 fallback을 만들어야 함
- API에 없는 제품 동작을 새로 결정해야 함
- 인증·결제·가격·재고 정책 변경이 필요함
- 새 Backend contract나 dependency 승인이 필요함
- 실제 Production 실행이 필요함
