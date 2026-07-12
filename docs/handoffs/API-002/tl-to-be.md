# API-002 Tech Lead → Backend Engineer 인수인계

## 전달 목적

사용자가 승인한 공개 상품 목록·상세 API 계약을 Backend Engineer가 별도 제품·API 추측 없이 구현할 수 있도록 전달한다.

## 대상 역할

- Backend Engineer
- 후속 검증: QA Engineer
- 후속 연동: Frontend Engineer

## 입력 문서

- `docs/api/API-002-public-product-api-contract-proposal.md` (`Approved API Contract`)
- `docs/adr/ARCH-006-first-backend-implementation-approved-inputs.md`
- `docs/api/API-001-first-mvp-api-contract.md` (공개 API 두 개의 원천 후보, 문서 전체는 Proposed)
- `docs/product/PS-002-first-mvp-requirements.md`
- `docs/data/DATA-001-first-mvp-data-model.md`
- `docs/data/DATA-002-first-mvp-logical-erd.md`
- `docs/domain/DOMAIN-001-first-mvp-subscription-domain.md`

## 완료된 작업

- D1-A SKU별 가격 배열 승인
- D2-A SKU 내부 `availableDeliveryCycles`와 비구독 SKU의 빈 배열 승인
- D3-PUBLIC 공개 filter·통합 404·내부 상태 비노출 승인
- D4-A 상품·SKU 정렬 보장 승인
- D5-A 빈 배열·명시적 null·SKU 없는 공개 상품 유지 승인
- D6-A JSON number와 Frontend 표시 책임 승인
- D7-A 목록·상세 예상 외 실패 코드 승인

## 사용 가능한 결과

Backend는 다음 두 API를 구현할 수 있다.

| Method | URI | 인증 | 성공 |
| --- | --- | --- | --- |
| `GET` | `/api/products` | 불필요 | `200 OK`, 승인된 `{ "products": [...] }` |
| `GET` | `/api/products/{productId}` | 불필요 | `200 OK`, 승인된 상품·SKU 상세 |

정확한 필드명·중첩·타입·nullable·예시는 Approved API-002 계약을 그대로 사용한다. 새 필드를 추가하거나 이름을 바꾸지 않는다.

## 관련 파일

- `docs/api/API-002-public-product-api-contract-proposal.md`
- `docs/reports/API-002/tl-report.md`
- `docs/handoffs/API-002/tl-to-be.md`

## 인수 조건과 추적성

| 요구사항 | 구현 인수 조건 |
| --- | --- |
| `REQ-PRODUCT-001` | 비회원·회원 목록 200, 상품 필드, `skuPriceSummary.skuPrices[]`, `hasSubscribableSku`, 빈 목록 |
| `REQ-PRODUCT-002` | 비회원·회원 상세 200, SKU 필드, `subscribable`, SKU 내부 배송 주기, 조회 불가 404 |
| ARCH-006 A1·A8 | 공개 API 두 개만 구현하고 구독 API를 추가하지 않음 |
| ARCH-006 A9 | 공통 오류 shape, HTML·redirect·내부 정보 비노출 |
| API-001 | 공개 상품 URI·기본 필드 근거만 사용하며 문서 전체를 승인 입력으로 확장하지 않음 |

## 확정된 결정

### 목록

- 최상위 `products` 배열
- 상품 필드: `productId`, `name`, `petType`, `shortDescription`, `thumbnailUrl`, `skuPriceSummary`, `hasSubscribableSku`
- `skuPriceSummary`는 `{ "skuPrices": [...] }`
- 가격 원소: `skuId`, `skuName`, `price`
- 상품 순서: `productId ASC`
- 가격 배열 SKU 순서: `display_order ASC`, 동률이면 DB `id ASC`
- 결과 없음: `{ "products": [] }`

### 상세

- 상품 필드: `productId`, `name`, `petType`, `description`, `thumbnailUrl`, `skus`
- SKU 필드: `skuId`, `skuName`, `price`, `subscribable`, `availableDeliveryCycles`
- `subscribable=true`: `[2, 4, 8]`
- `subscribable=false`: `[]`
- SKU 순서: `display_order ASC`, 동률이면 DB `id ASC`

### 공개 기준

- `products.display_status`가 정확히 `PUBLIC`인 상품만 목록·상세 공개
- 목록과 상세에 같은 기준 사용
- `PUBLIC` 외 상태와 미존재는 상세에서 동일한 `404 PRODUCT_NOT_FOUND`
- 응답에 `displayStatus` 또는 다른 내부 상태 필드를 포함하지 않음

### 빈 값과 가격

- SKU 없는 `PUBLIC` 상품도 목록·상세에 포함
- 해당 목록 상품: `skuPriceSummary.skuPrices=[]`, `hasSubscribableSku=false`
- 해당 상세 상품: `skus=[]`
- nullable `description`, `thumbnailUrl`은 필드를 생략하지 않고 `null`
- 모든 컬렉션은 `null` 대신 `[]`
- `price`는 JSON number
- 통화 기호, 천 단위 구분과 소수점 화면 형식은 Frontend 책임

### 오류

| 조건 | HTTP | code | message | fieldErrors |
| --- | --- | --- | --- | --- |
| 상세 미존재 또는 비공개 | 404 | `PRODUCT_NOT_FOUND` | `상품을 확인할 수 없습니다.` | `[]` |
| 예상하지 못한 목록 실패 | 500 | `PRODUCT_LIST_UNAVAILABLE` | `상품 목록을 불러오지 못했습니다.` | `[]` |
| 예상하지 못한 상세 실패 | 500 | `PRODUCT_DETAIL_UNAVAILABLE` | `상품 정보를 불러오지 못했습니다.` | `[]` |

오류 응답에 내부 예외, SQL, schema·table·column 이름과 stack trace를 포함하지 않는다. 서버 로그는 민감정보를 새로 추가하지 않고 원인을 추적할 수 있어야 한다.

## 미확정 결정

D1~D7에는 없음. 다음은 승인 범위 밖이므로 구현 중 임의로 결정하지 않는다.

- `petType` 새 허용값 집합 또는 enum 정책
- `PUBLIC` 외 공개 상태와 상태 전이
- 관리자 상품 관리 정책
- 재고·품절·판매 가능 여부
- 할인·결제·통화·환율
- 구독·배송 정책

현재 저장된 `pet_type` 문자열은 승인 응답의 string으로 전달하되 새 허용값을 만들지 않는다.

## 승인 필요 항목

Approved API-002 범위 안의 추가 승인 필요 항목은 없다.

## 다음 역할의 입력

- 별도 Backend 작업 ID
- 최신 `main`에서 새 `feat/be`
- Approved API-002 계약과 이 인수인계
- 기존 Product·Sku entity/repository와 MySQL V1 schema

## 지켜야 할 규칙

- JPA Entity를 API 응답으로 직접 노출하지 않는다.
- 목록·상세 read-only transaction 경계를 구현 보고서에 기록한다.
- N+1 또는 중복 query 여부는 실제 SQL·테스트 근거로 확인한다.
- 검색·필터·정렬 요청·페이지네이션을 추가하지 않는다.
- Product `display_order`, 새 상태 enum, 신규 dependency와 migration을 추가하지 않는다.
- 인증·CSRF·session 계약을 변경하지 않는다.
- API-001 구독 DTO·오류·서비스를 미리 만들지 않는다.

## 적용·실행 방법

1. 별도 Backend 작업 ID와 범위를 확정한다.
2. Product 목록·상세 response DTO와 query 경계를 설계한다.
3. `PUBLIC` filter와 승인 정렬을 repository/service에 구현한다.
4. 승인 오류를 공통 오류 shape로 mapping한다.
5. focused service/API/DB 통합 테스트를 작성한다.
6. Backend test·build와 Repository Validation을 통과시킨다.
7. QA 인수인계와 작업 보고서를 작성한다.

## 다음 역할의 검증 포인트

- 비회원과 로그인 회원 모두 두 API 접근 가능
- 목록·상세 JSON이 Approved 예시와 exact shape 일치
- `productId ASC`, SKU `display_order ASC, id ASC`
- SKU 없는 `PUBLIC` 상품 유지와 모순 없는 빈 값
- 비구독 SKU 배송 주기 `[]`
- `PUBLIC` 외 상품과 미존재 상품의 동일 404
- `displayStatus`와 내부 예외·SQL·stack trace 비노출
- 목록·상세 500 코드 구분
- 기존 인증·CSRF·session 회귀 없음

## QA 필요 여부

Backend 구현 완료 후 QA 독립 검증이 필요하다.

## AI 리뷰에서 남은 확인 항목

API-002 승인 기록 PR의 리뷰 결과를 구현 시작 전에 확인한다. 구현 PR에서는 DTO shape, 공개 filter, query 수, ordering과 오류 mapping을 중점 검토한다.

## 알려진 위험

- SKU별 가격 배열 때문에 목록 query가 잘못 구성되면 N+1이 발생할 수 있다.
- `PUBLIC` 문자열의 대소문자·fixture 불일치는 공개 결과 누락을 만든다.
- JSON number는 화면의 소수 자릿수 형식을 보장하지 않는다.

## 남은 위험과 주의 사항

현재 schema·entity는 `display_status`와 `pet_type`을 문자열로 저장한다. API-002 승인값을 이유로 migration이나 새 enum을 추가하면 범위를 벗어난다.

## 다음 권장 작업

Backend Engineer가 공개 상품 목록·상세 API 구현을 별도 작업으로 시작한다.

## 완료 조건

- 두 공개 API와 승인 DTO 구현
- 공개 filter·정렬·빈 값·가격·오류 계약 충족
- 관련 focused test, Backend test/build와 Repository Validation 통과
- QA 인수인계와 Backend 보고서 작성

## 중단 조건

- 승인 JSON에 없는 필드나 구조가 필요함
- `PUBLIC` 외 공개 상태 또는 관리자 상태 전이가 필요함
- 재고·품절·판매·할인·결제·통화·구독·배송 정책이 필요함
- DB migration, 새 dependency, Frontend·CI·인프라 변경이 필요함
- API-001 나머지 후보를 함께 구현해야 함
