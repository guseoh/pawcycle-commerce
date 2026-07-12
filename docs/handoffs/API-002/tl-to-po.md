# API-002 Tech Lead → Product Owner 인수인계

## 전달 목적

공개 상품 API 구현 전에 D1~D7의 추천안·대안·영향을 검토하고 승인·수정·보류할 수 있도록 전달한다.

## 대상 역할

- Product Owner / Tech Lead: 사용자
- 승인 후 후속 역할: Backend Engineer, Frontend Engineer, QA Engineer

## 입력 문서

- `docs/api/API-002-public-product-api-contract-proposal.md` (`Proposed`, `Decision Required`)
- `docs/adr/ARCH-006-first-backend-implementation-approved-inputs.md` (공개 API 범위 Approved)
- `docs/api/API-001-first-mvp-api-contract.md` (공개 API 세부 후보 Proposed)
- `docs/product/PS-002-first-mvp-requirements.md`

## 완료된 작업

- 목록·상세 정확한 JSON 후보와 필드 사전 작성
- D1 가격 요약, D2 배송 주기, D3 공개 기준, D4 순서, D5 빈 값, D6 가격, D7 오류 선택지 작성
- 각 항목의 근거, 추천 이유, 대안 영향과 BE·FE·QA 영향 기록

## 사용 가능한 결과

사용자는 다음 값을 그대로 선택하거나 수정할 수 있다.

```text
D1: A  # SKU별 가격 배열
D2: A  # SKU 내부 배송 주기, false는 []
D3: PUBLIC  # 단일 공개 literal, 미존재·비공개 통합 404, 상태 비노출
D4: A  # productId ASC, SKU display_order/id ASC
D5: A  # 빈 배열, nullable 명시적 null, SKU 없는 공개 상품 유지
D6: A  # JSON number, 화면 형식은 Frontend 책임
D7: A  # PRODUCT_DETAIL_UNAVAILABLE 사용
```

## 관련 파일

- `docs/api/API-002-public-product-api-contract-proposal.md`
- `docs/reports/API-002/tl-report.md`
- `docs/handoffs/API-002/tl-to-po.md`

## 인수 조건과 추적성

- `REQ-PRODUCT-001`: 공개 목록, 필수 상품 필드, SKU별 가격, 구독 가능 요약
- `REQ-PRODUCT-002`: 공개 상세, SKU·가격·구독 가능 여부, 2·4·8주
- ARCH-006 A1·A8·A9: 공개 API 두 개, API-001 공개 범위, 공통 오류 shape
- API-001: `skuPriceSummary`, `availableDeliveryCycles`, `PRODUCT_NOT_FOUND`, `PRODUCT_LIST_UNAVAILABLE` 후보 구체화

## 확정된 결정

- 공개 API URI·method와 비회원·회원 접근
- 요구사항의 표시 정보
- 공통 오류 JSON shape와 내부 정보 비노출
- 검색·필터·정렬 입력·페이지네이션 및 구독·재고·결제·배송 제외

## 미확정 결정

- D1~D7 전체
- `PUBLIC` 또는 `VISIBLE` 공개 literal
- 상세 예상 외 500 code

## 승인 필요 항목

| ID | 추천 | 사용자 동작 |
| --- | --- | --- |
| D1 | A | A 승인, B 선택 또는 수정 |
| D2 | A | A 승인, B 선택 또는 수정 |
| D3 | `PUBLIC` | `PUBLIC`, `VISIBLE`, 별도 결정 중 선택 |
| D4 | A | 순서 보장 또는 미보장 선택 |
| D5 | A | 안정 shape 또는 생략/제외 정책 선택 |
| D6 | A | JSON number 또는 string 선택 |
| D7 | A | 상세 전용 500 또는 `INTERNAL_ERROR` 선택 |

## 다음 역할의 입력

사용자 승인 후 선택된 값만 Backend·Frontend·QA 입력으로 전환한다. 이 인수인계 자체는 구현 승인이 아니다.

## 지켜야 할 규칙

- 미선택 항목을 Approved로 해석하지 않는다.
- API-001 전체나 구독 API를 구현 입력으로 확장하지 않는다.
- 재고·판매·관리자·할인·결제·통화·배송 정책을 추가하지 않는다.
- Entity를 API 응답으로 직접 사용하지 않는다.

## 적용·실행 방법

1. Proposed 계약의 D1~D7을 검토한다.
2. 선택값과 수정 문구를 명시한다.
3. Tech Lead가 승인된 값만 기록한다.
4. 이후 Backend 구현 작업을 새 작업 ID와 `feat/be`에서 시작한다.

## 다음 역할의 검증 포인트

- BE: 공개 filter, DTO shape, read-only transaction, 결정적 order, 안전 오류 mapping
- FE: 빈 배열·null, number formatting, SKU별 배송 주기와 오류 상태
- QA: 비회원·회원 접근, JSON exact shape, 공개/비공개 404 동일성, 순서·빈 값·500 내부 정보 비노출

## QA 필요 여부

결정 후보 문서 자체의 독립 QA는 생략한다. 승인 후 구현에는 QA 독립 검증이 필요하다.

## AI 리뷰에서 남은 확인 항목

PR이 없으므로 AI PR 리뷰는 수행하지 않았다.

## 알려진 위험

- 가격 범위만 선택하면 SKU별 가격 요구사항 변경이 필요하다.
- 여러 공개 상태를 선택하면 현재 범위를 넘는 상태 정책이 필요하다.
- 순서 미보장은 FE·QA에 별도 정렬 책임을 만든다.

## 남은 위험과 주의 사항

추천안은 승인값이 아니다. D1~D7이 해소되기 전 Backend 구현을 시작하면 DTO와 query 계약이 갈릴 수 있다.

## 다음 권장 작업

사용자가 추천 조합 `D1-A, D2-A, D3-PUBLIC, D4-A, D5-A, D6-A, D7-A`를 승인·수정·보류한다.

## 완료 조건

- D1~D7 선택값이 명시됨
- 선택된 값만 승인 입력으로 기록됨
- 미선택 항목은 Decision Required 유지
- Backend 구현 작업이 별도 시작됨

## 중단 조건

- 재고·판매·관리자 상태 정책이 필요함
- 여러 공개 상태와 전이 규칙이 필요함
- 구독·결제·배송 계약이 함께 필요함
- Backend·Frontend·DB·CI 변경이 API-002 문서 작업에 요구됨
