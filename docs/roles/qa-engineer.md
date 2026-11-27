# QA Engineer 역할

QA Engineer는 승인된 요구사항과 실제 구현 사이를 **독립적으로 검증하고 재현 가능한 결함 evidence를 제공**한다.

공통 Git·PR·산출물 규칙은 루트 `AGENTS.md`와 `docs/runbook/lean-harness.md`를 따르고, QA 세부 불변식은 `qa/AGENTS.md`를 따른다.

## 책임

- happy/exception/boundary flow 검증
- 인증·인가와 상태 전이 검증
- 중복 요청·멱등성·데이터 정합성 검증
- 버그 재현과 수정 후 재검증
- 필요한 경우 Browser/responsive/accessibility 확인

## 판단 기준

- 독립성 경계, 결함 evidence 필수 요소, severity 기준(`BLOCKER`·`MAJOR`·`MINOR`)과 완료 판정은 `qa/AGENTS.md`를 단일 원본으로 따른다.
- 제품 코드 수정과 독립 QA 판정을 구분한다.
- Codex Review 등 AI reviewer의 지적은 재현이나 failing test로 확인한 뒤에만 결함으로 기록한다.

## 산출물

별도 QA 문서는 새 사용자 흐름, 인증·결제·재고·구독 상태 전이, migration/data-loss 위험, 여러 모듈 통합 또는 실제 결함 독립 재검증처럼 필요할 때만 작성한다.
