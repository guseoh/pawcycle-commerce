# API-002 Tech Lead → Product Owner 인수인계

## 전달 목적

사용자가 승인한 D1~D7이 계약에 정확히 반영됐음을 기록하고 Backend 구현 단계로 넘긴다.

## 대상 역할

- Product Owner / Tech Lead: 사용자
- 승인 후 후속 역할: Backend Engineer, Frontend Engineer, QA Engineer

## 전달 상태

- 작업 브랜치: `ops/tl`
- 승인 계약 commit: `08d3f7e20c266660f4f2d75fbb49dbec0a823000`
- PR: #35 Draft, `main` ← `ops/tl`
- Repository Validation run: `29179790452`, 전체 통과
- CodeRabbit: 완료, 미해결 review thread 없음

## 입력 문서

- `docs/api/API-002-public-product-api-contract-proposal.md` (`Approved API Contract`)
- `docs/adr/ARCH-006-first-backend-implementation-approved-inputs.md` (공개 API 범위 Approved)
- `docs/api/API-001-first-mvp-api-contract.md` (공개 API 세부 후보 Proposed)
- `docs/product/PS-002-first-mvp-requirements.md`

## 완료된 작업

- 목록·상세 최종 JSON과 필드 사전 승인 기록
- D1-A, D2-A, D3-PUBLIC, D4-A, D5-A, D6-A, D7-A 승인 반영
- 미선택 대안과 API-001 나머지 후보 미승인 상태 유지
- Backend 구현 인수인계 작성

## 사용 가능한 결과

사용자는 다음 값을 명시적으로 승인했다.

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
- `docs/handoffs/API-002/tl-to-be.md`

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
- D1-A SKU별 가격 배열
- D2-A SKU 내부 배송 주기와 비구독 SKU의 `[]`
- D3-PUBLIC 단일 공개 상태, 미존재·비공개 통합 404, 내부 상태 비노출
- D4-A 상품·SKU 결정적 순서
- D5-A 빈 배열·명시적 null·SKU 없는 공개 상품 유지
- D6-A JSON number와 Frontend 화면 표시 책임
- D7-A `PRODUCT_DETAIL_UNAVAILABLE`

## 미확정 결정

- D1~D7 안에는 없음
- API-001의 구독 API와 기타 후보는 계속 Proposed
- `petType` 허용값 집합과 관리자 상태 전이는 이번 승인 범위 밖

## 승인 필요 항목

D1~D7 추가 승인 필요 항목은 없다. 범위 밖 정책이 필요하면 별도 작업으로 결정한다.

## 다음 역할의 입력

Approved 계약과 `tl-to-be.md`를 Backend 구현 입력으로 사용한다.

## 지켜야 할 규칙

- 미선택 항목을 Approved로 해석하지 않는다.
- API-001 전체나 구독 API를 구현 입력으로 확장하지 않는다.
- 재고·판매·관리자·할인·결제·통화·배송 정책을 추가하지 않는다.
- Entity를 API 응답으로 직접 사용하지 않는다.

## 적용·실행 방법

1. Approved 계약의 D1~D7 반영 결과를 확인한다.
2. Backend 구현 작업을 새 작업 ID와 `feat/be`에서 시작한다.
3. 구현 완료 후 Frontend·QA에 계약과 실제 API를 전달한다.

## 다음 역할의 검증 포인트

- BE: 공개 filter, DTO shape, read-only transaction, 결정적 order, 안전 오류 mapping
- FE: 빈 배열·null, number formatting, SKU별 배송 주기와 오류 상태
- QA: 비회원·회원 접근, JSON exact shape, 공개/비공개 404 동일성, 순서·빈 값·500 내부 정보 비노출

## QA 필요 여부

승인 기록 문서 자체의 독립 QA는 생략한다. Backend 구현에는 QA 독립 검증이 필요하다.

## AI 리뷰에서 남은 확인 항목

API-002 Draft PR에서 AI 리뷰 결과를 확인한다.

## 알려진 위험

- Backend가 미선택 대안을 섞으면 승인 JSON과 달라진다.
- `PUBLIC` 외 값을 공개하면 내부 상태가 노출될 수 있다.
- JSON number의 화면 formatting을 Backend가 맡으면 책임 경계가 달라진다.

## 남은 위험과 주의 사항

API-001 전체가 승인된 것은 아니다. Backend는 공개 상품 API 두 개와 D1~D7만 구현한다.

## 다음 권장 작업

Backend Engineer가 `docs/handoffs/API-002/tl-to-be.md`를 받아 공개 상품 API 구현을 시작한다.

## 완료 조건

- D1~D7 승인값이 정확히 기록됨
- 미선택 대안과 API-001 나머지 후보는 미승인 유지
- Backend 구현 작업이 별도 시작됨

## 중단 조건

- 재고·판매·관리자 상태 정책이 필요함
- 여러 공개 상태와 전이 규칙이 필요함
- 구독·결제·배송 계약이 함께 필요함
- Backend·Frontend·DB·CI 변경이 API-002 문서 작업에 요구됨
