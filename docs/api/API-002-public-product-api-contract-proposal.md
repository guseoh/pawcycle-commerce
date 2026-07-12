# API-002 공개 상품 API 계약 제안

## 문서 상태

- 작업 ID: `API-002`
- 역할: Tech Lead
- 상태: `Proposed API Contract`, `Decision Required`
- 구현 상태: 구현 미승인
- 대상 API: `GET /api/products`, `GET /api/products/{productId}`

이 문서는 공개 상품 API 구현을 막는 세부 계약의 선택지와 추천안을 정리한다. 사용자가 명시적으로 선택하기 전에는 어떤 추천안도 `Approved` 구현 입력이 아니며 Backend·Frontend 구현을 시작하지 않는다.

## 승인된 기준선

다음 항목은 API-002에서 다시 결정하지 않는다.

- 두 API는 비회원과 로그인 회원 모두 호출할 수 있다.
- 목록은 상품 ID, 상품명, 대상 동물, 짧은 설명, 대표 이미지, SKU별 표시 가격과 구독 가능한 SKU 존재 여부를 제공한다.
- 상세는 상품 설명, SKU 목록, SKU 이름, 가격과 구독 가능 여부를 제공한다.
- 선택 가능한 배송 주기는 첫 MVP 공통값 `2`, `4`, `8`주다.
- 공통 오류는 `code`, `message`, `fieldErrors`를 사용하고 비필드 오류의 `fieldErrors`는 빈 배열이다.
- 검색, 정렬 조건 입력, 필터와 페이지네이션은 제외한다.
- 구독 생성·조회, 재고·품절·판매 상태, 할인·결제·통화와 배송 정책은 추가하지 않는다.

근거 문서는 `REQ-PRODUCT-001`, `REQ-PRODUCT-002`, `ARCH-006`, `API-001`, `DATA-001`, `DATA-002`, `DOMAIN-001`이다.

## 추천 계약 JSON

아래 예시는 일곱 결정 항목의 추천안을 모두 선택했을 때의 정확한 후보 구조다. ID, 이름, URL과 `petType` 값은 형식 설명용 예시이며 실제 데이터나 새 enum 승인이 아니다.

### 상품 목록 성공 응답 후보

```json
{
  "products": [
    {
      "productId": 101,
      "name": "강아지 사료",
      "petType": "DOG",
      "shortDescription": "매일 먹는 기본 사료",
      "thumbnailUrl": null,
      "skuPriceSummary": {
        "skuPrices": [
          {
            "skuId": 1001,
            "skuName": "2kg",
            "price": 19900.00
          },
          {
            "skuId": 1002,
            "skuName": "5kg",
            "price": 39900.00
          }
        ]
      },
      "hasSubscribableSku": true
    }
  ]
}
```

조회 결과가 없을 때의 후보는 다음과 같다.

```json
{
  "products": []
}
```

### 상품 상세 성공 응답 후보

```json
{
  "productId": 101,
  "name": "강아지 사료",
  "petType": "DOG",
  "description": null,
  "thumbnailUrl": null,
  "skus": [
    {
      "skuId": 1001,
      "skuName": "2kg",
      "price": 19900.00,
      "subscribable": true,
      "availableDeliveryCycles": [2, 4, 8]
    },
    {
      "skuId": 1002,
      "skuName": "5kg",
      "price": 39900.00,
      "subscribable": false,
      "availableDeliveryCycles": []
    }
  ]
}
```

공개 상품에 SKU가 없을 때의 상세 후보는 `"skus": []`다.

## 응답 필드 사전

### 목록

| JSON 경로 | 타입 | nullable | 빈 값 | 의미 |
| --- | --- | --- | --- | --- |
| `products` | array<object> | 아니오 | 결과 없음: `[]` | 공개 상품 배열 |
| `products[].productId` | number, integer | 아니오 | 불가 | 상품 식별자 |
| `products[].name` | string | 아니오 | 빈 문자열 사용 안 함 | 상품명 |
| `products[].petType` | string | 아니오 | 빈 문자열 사용 안 함 | 대상 동물 식별자. API-002는 새 허용값 집합을 승인하지 않음 |
| `products[].shortDescription` | string | 아니오 | 빈 문자열 사용 안 함 | 목록용 짧은 설명 |
| `products[].thumbnailUrl` | string | 예 | 이미지 없음: `null` | 대표 이미지 URL |
| `products[].skuPriceSummary` | object | 아니오 | SKU가 없어도 객체 유지 | SKU별 표시 가격 묶음 |
| `products[].skuPriceSummary.skuPrices` | array<object> | 아니오 | SKU 없음: `[]` | SKU별 가격 배열 |
| `products[].skuPriceSummary.skuPrices[].skuId` | number, integer | 아니오 | 불가 | SKU 식별자 |
| `products[].skuPriceSummary.skuPrices[].skuName` | string | 아니오 | 빈 문자열 사용 안 함 | SKU명 또는 용량·구성 |
| `products[].skuPriceSummary.skuPrices[].price` | number | 아니오 | 불가 | 0 이상 표시 가격 |
| `products[].hasSubscribableSku` | boolean | 아니오 | SKU 없음: `false` | `subscribable=true`인 SKU 존재 여부 |

### 상세

| JSON 경로 | 타입 | nullable | 빈 값 | 의미 |
| --- | --- | --- | --- | --- |
| `productId` | number, integer | 아니오 | 불가 | 상품 식별자 |
| `name` | string | 아니오 | 빈 문자열 사용 안 함 | 상품명 |
| `petType` | string | 아니오 | 빈 문자열 사용 안 함 | 대상 동물 식별자 |
| `description` | string | 예 | 설명 없음: `null` | 상품 상세 설명 |
| `thumbnailUrl` | string | 예 | 이미지 없음: `null` | 대표 이미지 URL |
| `skus` | array<object> | 아니오 | SKU 없음: `[]` | SKU 배열 |
| `skus[].skuId` | number, integer | 아니오 | 불가 | SKU 식별자 |
| `skus[].skuName` | string | 아니오 | 빈 문자열 사용 안 함 | SKU명 또는 용량·구성 |
| `skus[].price` | number | 아니오 | 불가 | 0 이상 표시 가격 |
| `skus[].subscribable` | boolean | 아니오 | 불가 | 구독 대상 선택 가능 여부 |
| `skus[].availableDeliveryCycles` | array<number, integer> | 아니오 | 구독 불가: `[]` | 구독 가능한 SKU의 선택 가능 주기 `2`, `4`, `8` |

필드는 항상 존재한다. nullable 필드를 응답에서 생략하지 않고 JSON `null`로 표현하며 컬렉션은 `null` 대신 빈 배열을 사용한다.

## 결정 요약

| ID | 결정 항목 | 추천안 | 상태 | 사용자 선택 값 |
| --- | --- | --- | --- | --- |
| D1 | 목록 가격 요약 | `skuPriceSummary.skuPrices[]`로 SKU별 가격 제공 | Decision Required | `D1-A`, `D1-B` |
| D2 | 상세 배송 주기 | SKU 내부 배열, `subscribable=false`이면 `[]` | Decision Required | `D2-A`, `D2-B` |
| D3 | 상품 공개 기준 | `display_status=PUBLIC`, 목록·상세 동일, 미존재·비공개 동일 404, 상태 비노출 | Decision Required | `D3-A`, `D3-B`, `D3-C` |
| D4 | 응답 순서 | 상품 `productId ASC`, SKU `display_order ASC, id ASC` 보장 | Decision Required | `D4-A`, `D4-B` |
| D5 | 빈 값 | 빈 배열, SKU 없는 공개 상품 유지, nullable 필드는 명시적 `null` | Decision Required | `D5-A`, `D5-B` |
| D6 | 가격 표현 | JSON number, 화면 소수점 형식은 Frontend 책임 | Decision Required | `D6-A`, `D6-B` |
| D7 | 오류 계약 | 비공개·미존재 동일 404, 목록·상세별 안전한 500 코드 | Decision Required | `D7-A`, `D7-B` |

## D1. 상품 목록과 `skuPriceSummary`

### 근거

- `REQ-PRODUCT-001`은 SKU별 표시 가격을 요구한다.
- API-001은 `skuPriceSummary`를 SKU별 가격 또는 가격 범위 후보로 남겼다.
- `skus.price`는 `DECIMAL(12,2)`이고 상품별 SKU 관계가 존재한다.

### 선택지

| 선택 | 구조 | 장점 | 영향 |
| --- | --- | --- | --- |
| `D1-A` 추천 | `skuPriceSummary: { skuPrices: [{skuId, skuName, price}] }` | SKU별 가격 요구를 정보 손실 없이 충족하고 상세와 식별자를 맞출 수 있음 | 응답 크기가 SKU 수에 비례. BE projection과 FE 카드 표현, QA 배열 검증 필요 |
| `D1-B` | `skuPriceSummary: { minPrice, maxPrice }` | 카드가 가격 범위만 표시하면 작고 단순함 | SKU별 가격 요구를 충족하지 못해 FE가 상세를 추가 조회하거나 요구사항 변경 필요 |

추천은 `D1-A`다. 가격 범위는 파생 가능하지만 범위만으로 SKU별 가격을 복원할 수 없다. 최소 계약에는 중복 `minPrice`, `maxPrice`를 함께 넣지 않는다.

사용자 선택 필요: `D1-A` 또는 SKU별 가격 요구 변경을 전제로 한 `D1-B`.

## D2. 상세와 배송 주기

### 근거

- `REQ-PRODUCT-002`는 SKU별 구독 가능 여부와 선택 가능한 2·4·8주 표시를 요구한다.
- SKU별 배송 주기 제한 정책은 제외돼 모든 구독 가능 SKU의 후보 집합이 같다.

### 선택지

| 선택 | 구조 | 장점 | 영향 |
| --- | --- | --- | --- |
| `D2-A` 추천 | 각 `skus[]`에 `availableDeliveryCycles`; true는 `[2,4,8]`, false는 `[]` | 구독 가능 여부와 실제 선택지를 한 객체에서 일관되게 해석 | 동일 배열 반복. BE DTO·FE SKU 상태·QA 관계 검증 필요 |
| `D2-B` | 상세 최상위에 `[2,4,8]`, SKU에는 `subscribable`만 제공 | 중복이 적음 | 구독 불가 SKU에도 주기가 적용되는 것처럼 오해할 수 있고 향후 SKU별 제한 확장이 어려움 |

추천은 `D2-A`다. `subscribable=false`에 `null`이나 `[2,4,8]`을 사용하지 않고 빈 배열을 사용한다.

사용자 선택 필요: `D2-A` 또는 `D2-B`.

## D3. 상품 공개 기준

### 근거

- `display_status`는 공개 여부 후보이며 재고·품절·판매·구독 상태와 분리돼 있다.
- 현재 DB는 문자열 저장 공간만 제공하고 허용값 집합은 승인하지 않았다.
- 외부 응답은 존재하지 않음과 비공개 상태를 구분해 내부 상태를 노출하면 안 된다.

### 선택지

| 선택 | 기준 | 장점 | 영향 |
| --- | --- | --- | --- |
| `D3-A` 추천 | 공개 허용값 하나 `PUBLIC`; 목록·상세 모두 동일; 그 외 값은 비공개; 비공개·미존재 동일 404; `displayStatus` 응답 제외 | 공개 여부 의미가 명확하고 판매 상태를 새로 만들지 않으며 정보 노출 최소화 | 기존/fixture 데이터가 정확히 `PUBLIC`을 사용해야 함. BE query와 QA 경계 테스트 필요 |
| `D3-B` | 허용값 `VISIBLE` 사용, 나머지는 `D3-A`와 동일 | UI 노출 의미가 직접적 | 기존 문서 용어 `PUBLIC`보다 새 literal 설명이 필요 |
| `D3-C` | 여러 공개값 또는 `displayStatus` 응답 포함 | 다양한 화면 상태 표현 가능 | 승인되지 않은 상태 전이·관리 정책과 내부 상태 노출을 새로 결정해야 해 현재 범위 초과 |

추천은 `D3-A`다. 목록과 상세 공개 기준을 다르게 두지 않는다.

사용자 선택 필요: 공개 literal `PUBLIC` 또는 `VISIBLE`; 여러 공개 상태나 상태 공개가 필요하면 별도 Product Decision으로 분리.

## D4. 상품과 SKU 순서

### 근거

- 검색·정렬 요청 파라미터는 제외됐다.
- Product에는 표시 순서 필드가 없고 SKU에는 `display_order`가 있으며 `(product_id, display_order, id)` 인덱스가 있다.

### 선택지

| 선택 | 보장 | 장점 | 영향 |
| --- | --- | --- | --- |
| `D4-A` 추천 | 상품 `productId ASC`; SKU와 목록 가격 `display_order ASC, skuId ASC` | 새 컬럼 없이 결정적 결과와 안정적 테스트 제공 | 상품 큐레이션 순서는 제공하지 못함. BE order by, FE 입력 순서 사용, QA 순서 검증 필요 |
| `D4-B` | 계약상 순서 미보장 | Backend가 가장 자유로움 | 응답 순서가 실행마다 달라질 수 있어 FE와 QA가 자체 정렬 기준을 새로 가져야 함 |

추천은 `D4-A`다. 별도 정렬 query parameter나 Product `display_order`는 추가하지 않는다.

사용자 선택 필요: `D4-A` 또는 `D4-B`.

## D5. 빈 값과 컬렉션

### 근거

- 공통 계약은 배열 형태의 안정성을 중시한다.
- DB에서 `description`, `thumbnail_url`은 nullable이고 `short_description`은 non-null이다.
- SKU가 없는 공개 상품을 숨긴다는 정책은 승인되지 않았다.

### 선택지

| 선택 | 표현 | 장점 | 영향 |
| --- | --- | --- | --- |
| `D5-A` 추천 | 빈 목록 `products: []`; SKU 없는 공개 상품 유지; `skus: []`, `skuPrices: []`, `hasSubscribableSku:false`; nullable 필드는 존재하며 `null` | 응답 shape가 안정적이고 새 공개 정책을 만들지 않음 | FE가 빈 배열·null placeholder를 처리하고 QA가 필드 존재를 검증 |
| `D5-B` | nullable/빈 필드 생략 또는 SKU 없는 상품 제외 | payload 감소 가능 | 필드 존재 여부 분기와 승인되지 않은 공개 제외 정책이 생김 |

추천은 `D5-A`다. `skuPriceSummary` 자체는 `null`이 아니라 `{ "skuPrices": [] }`다.

사용자 선택 필요: `D5-A` 또는 필드 생략·상품 제외 규칙을 별도 정책으로 승인한 `D5-B`.

## D6. 가격 표현

### 근거

- 저장 타입은 `DECIMAL(12,2)`이고 음수는 허용되지 않는다.
- 요구사항은 표시 가격을 요구하지만 할인·결제·통화 정책은 제외한다.

### 선택지

| 선택 | 표현 | 장점 | 영향 |
| --- | --- | --- | --- |
| `D6-A` 추천 | JSON number, 예 `19900.00`; Backend는 수치만 전달하고 소수점·지역화 표시는 Frontend 책임 | 계산·비교가 가능하고 문자열 파싱 불필요 | JSON number는 화면의 고정 소수 자릿수를 보장하지 않으므로 FE formatting과 QA 수치 비교 필요 |
| `D6-B` | JSON string, 예 `"19900.00"` | 자릿수 표면 형식 보존 | 계산·정렬에 파싱 필요하고 표시 형식이 API 책임으로 섞임 |

추천은 `D6-A`다. currency, 할인 가격, 결제 금액은 추가하지 않는다.

사용자 선택 필요: `D6-A` 또는 `D6-B`.

## D7. 오류 계약

### 근거

- ARCH-006은 공통 오류 shape와 내부 예외 비노출을 승인했다.
- API-001은 `PRODUCT_NOT_FOUND`, `PRODUCT_LIST_UNAVAILABLE`을 후보로 제시했다.
- 비공개와 미존재의 외부 표현에서 내부 상태를 노출하지 않아야 한다.

### 추천 오류

| API·조건 | HTTP | code | message | fieldErrors |
| --- | --- | --- | --- | --- |
| 상세의 미존재 또는 비공개 상품 | 404 | `PRODUCT_NOT_FOUND` | `상품을 확인할 수 없습니다.` | `[]` |
| 예상하지 못한 목록 조회 실패 | 500 | `PRODUCT_LIST_UNAVAILABLE` | `상품 목록을 불러오지 못했습니다.` | `[]` |
| 예상하지 못한 상세 조회 실패 | 500 | `PRODUCT_DETAIL_UNAVAILABLE` | `상품 정보를 불러오지 못했습니다.` | `[]` |

```json
{
  "code": "PRODUCT_NOT_FOUND",
  "message": "상품을 확인할 수 없습니다.",
  "fieldErrors": []
}
```

내부 예외 메시지, SQL, schema·table·column 이름과 stack trace는 응답에 포함하지 않는다. 서버 로그 정책은 응답 계약과 분리하되 원인 추적이 가능해야 한다.

### 선택지

| 선택 | 상세 500 | 장점 | 영향 |
| --- | --- | --- | --- |
| `D7-A` 추천 | `PRODUCT_DETAIL_UNAVAILABLE` | 목록과 상세 실패가 대칭이고 FE·QA가 재시도/표시 위치를 구분 가능 | 새 안정 오류 코드 하나를 승인해야 함 |
| `D7-B` | 공통 `INTERNAL_ERROR` | 코드 수가 적고 기존 공통 오류 재사용 | 상품 상세 맥락이 사라지고 목록만 도메인별 코드가 되는 비대칭 발생 |

`PRODUCT_NOT_FOUND`는 미존재와 비공개에 동일 적용하고 `displayStatus`나 차이 메시지를 반환하지 않는 안을 공통 추천으로 둔다.

사용자 선택 필요: 상세 500에 `D7-A` 또는 `D7-B`; 404 통합 처리와 목록 500 후보 승인 여부.

## 역할별 영향

| 역할 | 영향 |
| --- | --- |
| Backend | 승인 후 DTO, 공개 query, 결정적 정렬, 공개 상태 filter, 오류 mapping과 read-only transaction 구현. Entity 직접 노출 금지 |
| Frontend | 안정된 필드 shape, 빈 배열·null placeholder, number 가격 formatting, SKU별 배송 주기와 오류 code별 화면 상태 구현 |
| QA | 공개 접근, 정확한 JSON shape, 빈 상태, 순서, 공개/비공개 404 동일성, SKU 구독 가능 관계, 안전한 500과 내부 정보 비노출 검증 |

## 추적성

| 요구사항·문서 | API-002 연결 | 상태 |
| --- | --- | --- |
| `REQ-PRODUCT-001`, `AC-PRODUCT-001-01~05` | 목록 필드, 공개 접근, SKU별 가격, 구독 가능 요약, 빈 목록 | 요구사항 Approved, 세부 계약 Proposed |
| `REQ-PRODUCT-002`, `AC-PRODUCT-002-01~08` | 상세 필드, SKU, 구독 가능 여부, 2·4·8주, 공개 접근, 조회 불가 표현 | 요구사항 Approved, 세부 계약 Proposed |
| `ARCH-006` A1·A8·A9 | 공개 API 두 개, API-001 사용 범위, 공통 오류 shape·내부 정보 비노출 | Approved 입력 유지 |
| `API-001` | URI·필드·오류 후보를 D1~D7로 구체화 | API-001 Proposed 유지 |
| `DATA-001`, `DATA-002`, `FOUNDATION-003` | nullable, `DECIMAL(12,2)`, `display_status`, `display_order` 근거 | 저장 기반을 읽기 계약 후보로만 사용 |

## 명시적 제외

- 검색, 필터, 정렬 요청 parameter, 페이지네이션
- 재고, 품절, 판매 상태와 상품 관리 상태 전이
- 할인, 결제 금액, 통화와 배송 정책
- 상품·SKU 생성·수정·삭제 관리자 API
- 구독 생성·조회 API와 Subscription 데이터
- Backend·Frontend·DB·CI 구현과 신규 dependency

## 사용자 결정 요청

사용자는 다음 형식으로 일부 또는 전부를 선택할 수 있다.

```text
D1: A
D2: A
D3: PUBLIC
D4: A
D5: A
D6: A
D7: A
```

선택되지 않은 항목은 `Decision Required`로 유지한다. 모든 구현 차단 항목이 승인된 뒤 별도 승인 입력 문서 또는 사용자 명시 승인으로 Backend·Frontend·QA에 전달한다.
