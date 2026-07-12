# API-002 Tech Lead 작업 보고서

## 작업 정보

- 작업 ID: `API-002`
- 역할: Tech Lead
- 기준 브랜치: `main`
- 작업 브랜치: `ops/tl`
- 결정 상태: `Proposed`, `Decision Required`

## 작업 목적

ARCH-006에서 승인된 공개 상품 API 두 개의 범위를 유지하면서 API-001의 구현 차단 후보를 사용자가 선택할 수 있는 최소 계약으로 정리한다.

## 입력 문서

- `docs/product/PS-002-first-mvp-requirements.md`
- `docs/adr/ARCH-006-first-backend-implementation-approved-inputs.md`
- `docs/api/API-001-first-mvp-api-contract.md`
- `docs/data/DATA-001-first-mvp-data-model.md`
- `docs/data/DATA-002-first-mvp-logical-erd.md`
- `docs/domain/DOMAIN-001-first-mvp-subscription-domain.md`
- `docs/reports/FOUNDATION-003/be-report.md`

## 승인 입력

- `GET /api/products`, `GET /api/products/{productId}` 공개 접근과 성공 범위
- `REQ-PRODUCT-001`, `REQ-PRODUCT-002`의 표시 정보와 공개 접근
- `code`, `message`, `fieldErrors` 공통 오류 shape와 내부 정보 비노출
- 검색·필터·정렬 요청·페이지네이션과 구독·재고·결제·배송 제외

## 변경 범위

- 목록·상세 JSON 구조 후보와 모든 필드의 타입·nullable·빈 배열 규칙
- 가격 요약, 배송 주기, 공개 상태, 순서, 빈 값, 가격, 오류 D1~D7 결정 요청
- Product Owner/Tech Lead 선택 인수인계

## 변경하지 않은 범위

- Backend, Frontend, DB, CI와 인프라 구현
- API-001 또는 ARCH-006의 상태 변경
- 재고·품절·판매·관리자·할인·결제·통화·배송 정책
- 구독 API와 Subscription 구현

## 인수 조건 매핑

| 완료 조건 | 결과 |
| --- | --- |
| 목록·상세 정확한 JSON 예시 | Proposed 계약에 정상·빈 예시 기록 |
| 모든 필드 의미·타입·nullable·빈 배열 | 목록·상세 필드 사전 기록 |
| 가격 요약 별도 결정 | D1에서 SKU별 가격과 범위 비교 |
| 공개 기준·비공개 상세 | D3에서 literal, 목록·상세 filter, 통합 404, 상태 비노출 비교 |
| 배송 주기와 false 관계 | D2에서 SKU 내부 배열과 false의 `[]` 추천 |
| 순서 | D4에서 상품 ID와 SKU display order 후보 비교 |
| 빈 값 | D5에서 빈 목록·SKU·null 규칙 비교 |
| 가격 | D6에서 JSON number와 Frontend formatting 책임 비교 |
| 오류 | D7에서 404·목록 500·상세 500 후보 기록 |
| 추적성 | REQ-PRODUCT-001/002, ARCH-006, API-001 연결 표 기록 |

## 주요 결과

- 추천 목록은 `skuPriceSummary.skuPrices[]`로 SKU별 가격 요구를 보존한다.
- 추천 상세는 SKU마다 `availableDeliveryCycles`를 두고 비구독 SKU는 빈 배열을 사용한다.
- 공개 literal은 판매 상태와 분리된 `PUBLIC` 하나를 추천하고 미존재·비공개 상세는 같은 404로 숨긴다.
- 상품은 `productId`, SKU는 `display_order, id`의 결정적 순서를 추천한다.
- nullable 필드는 생략하지 않고 `null`, 컬렉션은 빈 배열을 추천한다.
- 가격은 JSON number, 화면 형식은 Frontend 책임을 추천한다.
- 목록과 상세의 예상 외 실패에 안전한 상품별 오류 코드를 추천한다.

## 변경 파일

- `docs/api/API-002-public-product-api-contract-proposal.md`
- `docs/reports/API-002/tl-report.md`
- `docs/handoffs/API-002/tl-to-po.md`

## 결정 상태

- D1~D7: `Decision Required`
- 추천안: `Recommended`, 승인 아님
- Backend 구현: 시작 금지

## API 영향

승인 전 API 동작 영향은 없다. 사용자 선택 후 공개 상품 DTO·query·오류 mapping의 구현 입력이 된다.

## DB 영향

없음. 기존 `products`, `skus` 물리 구조를 근거로만 사용했으며 schema·migration을 변경하지 않았다.

## 보안 영향

비공개 상품과 미존재 상품을 외부에서 구분하지 않고 내부 상태·예외·SQL·stack trace를 응답하지 않는 후보를 제안했다.

## 운영 영향

없음. 배포·관측성·CI 설정을 변경하지 않았다.

## 성능 영향

측정이나 최적화를 수행하지 않았다. SKU별 가격 배열은 응답 크기가 SKU 수에 비례한다는 영향만 기록했다.

## 실행한 검증

- `python scripts\\validate-task-artifacts.py --task-id API-002`: `task artifacts validated for API-002`
- 필수 상태·D1~D7·추적성·오류 코드 검색: 통과
- `git diff --check`: 통과

## 실행하지 못한 검증과 이유

- Backend test·build: 문서 후보 작업이며 `backend/**`를 변경하지 않아 실행 대상이 아니다.
- Frontend test·build: `frontend/**`를 변경하지 않았다.

## QA 필요 여부

현재는 구현 전 결정 후보라 독립 QA 실행 대상이 아니다. 승인 후 Backend 구현 작업에서 QA 계획이 필요하다.

## QA 문서 경로 또는 생략 사유

별도 QA 문서를 만들지 않는다. 승인 전 QA 기준 후보는 Proposed 계약의 역할별 영향과 인수인계에 기록했다.

## AI 리뷰 반영 여부

PR을 요청받지 않았으므로 AI PR 리뷰는 실행하지 않았다.

## AI 리뷰 미반영 항목과 이유

없음.

## 적용 방법

1. 사용자가 D1~D7을 승인·수정·보류한다.
2. Tech Lead가 선택된 값만 별도 승인 입력 또는 후속 사용자 승인으로 기록한다.
3. 모든 구현 차단 항목이 해소된 뒤 Backend·Frontend·QA 역할에 전달한다.

## 위험과 제한

- `petType` 허용 literal과 관리자 상태 전이는 API-002에서 새로 승인하지 않았다.
- `PUBLIC` 추천은 공개 여부 최소 literal 후보이며 사용자 선택 전 DB fixture나 query 기준이 아니다.
- JSON number는 화면의 소수 자릿수 형식을 보장하지 않는다.

## 남은 위험

D1~D7 중 하나라도 미선택이면 정확한 DTO, query filter, ordering 또는 오류 mapping 구현이 막힌다.

## 다음 작업

- Product Owner/Tech Lead가 D1~D7 선택
- 승인 입력 기록
- Backend 공개 상품 API 구현 작업 분리
- Frontend 연동과 QA 계획은 Backend 계약 승인 후 진행

## Git 결과

- commit: 검증 후 기록 예정
- push: 검증 후 `ops/tl` 일반 push 예정

## PR 결과

- 사용자가 요청하지 않아 PR을 생성하지 않는다.
- 자동 병합하지 않는다.
