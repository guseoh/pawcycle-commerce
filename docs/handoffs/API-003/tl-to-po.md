# API-003 Tech Lead → Product Owner 인수인계

## 전달 목적

구독 생성, 내 구독 목록과 내 구독 상세를 하나의 Backend 수직 기능으로 구현하기 전에 필요한 D1~D8 추천안을 사용자 한 번의 응답으로 승인하거나 수정할 수 있게 전달한다.

## 다음 역할 또는 대상 역할

- 대상: Product Owner인 사용자
- 승인 후 다음 역할: Backend Engineer, Platform/SRE

## 입력 문서

- 승인 기준: `docs/product/PS-002-first-mvp-requirements.md`
- 승인 기준: `docs/domain/DOMAIN-001-first-mvp-subscription-domain.md`
- 승인 기준: `docs/design/UX-001-first-mvp-subscription-experience.md`
- 승인 기준: `docs/product/PS-003-ux-product-decisions.md`
- 승인 기준: `docs/api/API-002-public-product-api-contract-proposal.md`
- 승인 기준: `docs/adr/AUTH-003-session-authentication-approved-inputs.md`
- 참고 Proposed: `docs/api/API-001-first-mvp-api-contract.md`
- 참고 Proposed: `docs/data/DATA-002-first-mvp-logical-erd.md`
- 결정 요청: `docs/api/API-003-subscription-api-contract-decision-request.md`

## 사용 가능한 결과

- D1 API·성공 상태
- D2 생성 request·response
- D3 목록·상세 shape와 정렬
- D4 인증·소유권·정보 비노출
- D5 validation·도메인·안전 오류
- D6 동일 조건·중복 요청
- D7 물리 데이터 필드·제약·인덱스
- D8 Backend transaction·query·테스트·QA 경계

모든 결정의 상태는 `Decision Required`다. PR 병합만으로 승인됐다고 해석하지 않는다.

## 인수 조건 또는 추적성

| 결정 | 구현 차단 해제 범위 |
| --- | --- |
| D1 | Controller method·URI·성공 status |
| D2 | 생성 DTO·서버 날짜 계산·성공 DTO |
| D3 | 목록·상세 조회 DTO·정렬·빈 결과 |
| D4 | session principal·소유자 query·404 비노출 |
| D5 | validation·도메인 오류·안전 500 mapping |
| D6 | unique·멱등성 범위와 중복 생성 수용 |
| D7 | migration·Entity 물리 입력 |
| D8 | transaction·Clock·JPA 관계·query count·테스트·QA 방식 |

## 사용자 결정 요청

추천안을 수정 없이 전체 승인하려면 다음과 같이 응답한다.

```text
API-003 D1~D8 전체 추천안 승인
```

특정 결정만 수정하려면 다음 형식을 사용한다.

```text
API-003 D1, D2, D3, D4, D5, D7, D8 추천안 승인
D6 수정: <선택할 대안 또는 수정 내용>
```

승인하는 ID와 수정하는 ID를 모두 적어야 한다. 미언급 결정은 자동 승인하지 않고 `Decision Required`로 유지하므로 Backend 구현을 시작할 수 없다.

## 승인 전 금지 사항

- 구독 Backend 제품 코드, migration과 JPA Entity 구현
- API-003 `Approved` 표기
- API-001·DATA-002 전체 Approved 전환
- 사용자 선택 없이 D6 중복 위험 수용
- 신규 dependency, CI 변경과 자동 병합

## 승인 후 작업 순서

1. 구독 Backend 3개 API와 migration을 하나의 수직 작업으로 구현한다.
2. 구현 PR에 단위·MySQL 통합·Security 회귀와 query count 테스트를 포함한다.
3. Docker 로컬 실행·Health Check SRE 작업을 병렬 착수한다.
4. Frontend가 승인 계약을 연동한다.
5. Frontend 완료 후 인증·상품·구독 전체 흐름을 독립 통합 QA로 한 번 검증한다.

## 검증 포인트

- D1~D8 추천안이 모두 `Decision Required`인지 확인한다.
- API-001·DATA-002 원본이 Proposed로 유지되는지 확인한다.
- D6 중복 생성 위험을 사용자가 명시적으로 수용하거나 대안을 선택하는지 확인한다.
- D3 정렬과 D7 인덱스, D6 중복과 D7 unique 제외가 서로 일치하는지 확인한다.
- 승인 후 Backend가 session principal `memberId`, CSRF와 본인 소유 query를 테스트하는지 확인한다.

## QA 필요 여부와 문서

- API-003 문서 자체의 별도 QA PR은 만들지 않는다.
- Backend 구현 PR 자체 테스트와 Tech Lead 검토를 사용한다.
- Frontend 완료 후 통합 QA를 한 번 수행하며, 그 작업에서 별도 QA 보고서를 작성한다.

## AI 리뷰에서 남은 확인 항목

Draft PR 생성 후 CodeRabbit/Codex Review에서 승인 상태 오표기, 응답 shape 불일치, 소유권 노출과 D6·D7 모순을 확인한다.

## 미결정 사항 또는 승인 필요 항목

- D1~D8 전체
- 특히 D6의 동일 조건 복수 구독 허용과 Idempotency-Key 미도입에 따른 중복 생성 위험

## 중단 조건

- 사용자가 D1~D8을 승인하거나 수정하기 전에는 Backend 구현을 시작할 수 없음
- 기존 승인 제품·도메인·인증 규칙과 충돌하는 수정 요청
- 결제·재고·배송·상태를 포함해야 하는 수정 요청
- 신규 dependency, DB 범위 확장 또는 CI 변경이 필요한 수정 요청
- Secret 노출, destructive Git 또는 history rewrite 필요

## 남은 위험 또는 주의 사항

- D6 추천안에서는 timeout·retry가 중복 구독을 만들 수 있다.
- 상세 가격은 현재 SKU 표시 가격이며 과거 가격 snapshot이 아니다.
- 배포 환경 인증 오류 로그와 cookie는 Frontend 이후 통합 QA에서 재확인한다.
- 최종 승인과 PR 병합은 사용자가 직접 수행한다.
