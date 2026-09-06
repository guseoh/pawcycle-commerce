---
name: backend-engineer
description: >-
  PawCycle Commerce의 Backend 도메인, HTTP API, transaction, persistence, security와 관련 테스트를 구현할 때 사용한다.
---

# Backend Engineer Skill

지속 책임은 `docs/roles/backend-engineer.md`, Backend 코드 불변식은 `backend/AGENTS.md`, 공통 안전 규칙은 루트 `AGENTS.md`를 따른다.

## 실행 절차

1. **계약 확인**
   - 승인된 도메인·API·data/security contract와 현재 구현을 확인한다.
   - 외부 동작 변경인지 내부 구현 변경인지 구분한다.

2. **상태 경계 설계**
   - transaction, lock, idempotency, snapshot, failure/rollback 경계를 먼저 확인한다.
   - Controller/Application/Domain/Persistence 중 책임이 어디에 있어야 하는지 결정한다.

3. **최소 구현**
   - 승인 범위를 만족하는 가장 작은 변경을 한다.
   - 새 dependency, schema, architecture는 필요성과 승인 없이 추가하지 않는다.

4. **검증**
   - 관련 unit test에서 시작한다.
   - HTTP/security는 contract/integration, persistence/transaction/lock 의미가 바뀌면 실제 MySQL integration으로 확대한다.
   - 버그 수정은 재현 가능한 regression을 남긴다.

5. **결과 보고**
   - 변경한 의미, 실행한 검증, 실패·미실행과 남은 correctness 위험을 정리한다.

## 중단 조건

- 새 제품 정책 또는 외부 API/DB/security 결정이 필요함
- transaction/locking 의미를 안전하게 보존할 근거가 없음
- 실제 Production·운영 DB 실행이 필요함
- Secret 또는 개인정보 노출 가능성
