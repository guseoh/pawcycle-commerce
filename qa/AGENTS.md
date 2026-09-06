# QA 경로 규칙

이 파일은 `qa/**`와 테스트 전용 경로의 **독립 검증 경계**만 정의한다. 공통 승인·Git·PR·산출물·병합 규칙은 루트 `AGENTS.md`와 `docs/runbook/lean-harness.md`를 따른다.

## QA 책임

QA는 승인된 요구사항·인수 조건·API/도메인 계약과 실제 구현·실행 결과를 비교한다.

검증 초점:

- happy path와 exception path
- boundary value
- authentication / authorization
- state transition
- duplicate request / idempotency
- data consistency와 regression
- UI가 있으면 responsive/accessibility

## 독립성

- 결함을 통과시키기 위해 제품 코드나 인수 조건을 QA가 임의 수정하지 않는다.
- 재현되지 않은 관찰을 확정 버그로 기록하지 않는다.
- 구현에 맞추기 위해 expected result를 낮추지 않는다.
- 제품 수정이 필요한 경우 같은 PR의 승인된 cross-stack 후속 수정으로 개발 역할이 수정하고 QA는 같은 evidence path로 재검증한다.

## 결함 evidence

유효한 bug report에는 최소한 다음이 있어야 한다.

- 환경과 사전 조건
- 재현 절차 또는 failing test
- expected / actual
- 영향 또는 severity
- 재검증 결과

문서 형식을 채우는 것보다 다른 사람이 같은 실패를 재현할 수 있는지가 우선이다.

## 완료 판정

QA Green은 검증한 범위에만 적용한다. 실행하지 않은 browser/provider/Production 흐름을 PASS로 확대하지 않는다. 실제 운영 검증이 없으면 `Production Verified`를 선언하지 않는다.
