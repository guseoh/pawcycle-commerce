# API-002 공개 상품 API 승인 계약

## 문서 상태

- 작업 ID: `API-002`
- 역할: Tech Lead
- 상태: `Approved API Contract`
- 구현 상태: Backend 구현 입력 사용 가능
- 사용자 승인 일자: `2026-07-12`
- 승인 근거: API-002 후속 요청에서 사용자가 `D1-A`, `D2-A`, `D3-PUBLIC`, `D4-A`, `D5-A`, `D6-A`, `D7-A`를 명시적으로 승인
- 대상 API: `GET /api/products`, `GET /api/products/{productId}`

이 문서는 사용자가 명시적으로 선택한 D1~D7을 공개 상품 API 구현 계약으로 기록한다. 선택되지 않은 대안과 API-001의 구독 API·기타 후보는 승인하지 않는다. API-001 문서 전체 상태는 계속 `Proposed`다.

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

## 최종 계약 JSON

아래 예시는 승인된 D1~D7의 정확한 구조다. ID, 이름, URL과 `petType` 값은 형식 설명용 예시이며 실제 데이터나 새 enum 승인이 아니다.

### 상품 목록 성공 응답

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

조회 결과가 없을 때는 다음과 같다.

```json
{
  "products": []
}
```

### 상품 상세 성공 응답

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

공개 상품에 SKU가 없을 때의 상세 응답은 `"skus": []`다.

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

| ID | 결정 항목 | 승인값 | 상태 | 승인 근거 |
| --- | --- | --- | --- | --- |
| D1 | 목록 가격 요약 | `skuPriceSummary.skuPrices[]`로 SKU별 가격 제공 | Approved | 사용자 `D1-A` 승인 |
| D2 | 상세 배송 주기 | SKU 내부 배열, `subscribable=false`이면 `[]` | Approved | 사용자 `D2-A` 승인 |
| D3 | 상품 공개 기준 | `display_status=PUBLIC`, 목록·상세 동일, 미존재·비공개 동일 404, 상태 비노출 | Approved | 사용자 `D3-PUBLIC` 승인 |
| D4 | 응답 순서 | 상품 `productId ASC`, SKU `display_order ASC, id ASC` 보장 | Approved | 사용자 `D4-A` 승인 |
| D5 | 빈 값 | 빈 배열, SKU 없는 공개 상품 유지, nullable 필드는 명시적 `null` | Approved | 사용자 `D5-A` 승인 |
| D6 | 가격 표현 | JSON number, 화면 표시 형식은 Frontend 책임 | Approved | 사용자 `D6-A` 승인 |
| D7 | 오류 계약 | 비공개·미존재 동일 404, 목록·상세별 안전한 500 코드 | Approved | 사용자 `D7-A` 승인 |

## D1. 상품 목록과 `skuPriceSummary`

### 근거

- `REQ-PRODUCT-001`은 SKU별 표시 가격을 요구한다.
- API-001은 `skuPriceSummary`를 SKU별 가격 또는 가격 범위 후보로 남겼다.
- `skus.price`는 `DECIMAL(12,2)`이고 상품별 SKU 관계가 존재한다.

### 선택지

| 선택 | 구조 | 장점 | 영향 |
| --- | --- | --- | --- |
| `D1-A` Approved | `skuPriceSummary: { skuPrices: [{skuId, skuName, price}] }` | SKU별 가격 요구를 정보 손실 없이 충족하고 상세와 식별자를 맞출 수 있음 | 응답 크기가 SKU 수에 비례. BE projection과 FE 카드 표현, QA 배열 검증 필요 |
| `D1-B` 미선택 | `skuPriceSummary: { minPrice, maxPrice }` | 카드가 가격 범위만 표시하면 작고 단순함 | 승인되지 않았으며 구현하지 않음 |

승인 결정은 `D1-A`다. 가격 범위 필드와 중복 `minPrice`, `maxPrice`는 추가하지 않는다.

## D2. 상세와 배송 주기

### 근거

- `REQ-PRODUCT-002`는 SKU별 구독 가능 여부와 선택 가능한 2·4·8주 표시를 요구한다.
- SKU별 배송 주기 제한 정책은 제외돼 모든 구독 가능 SKU의 후보 집합이 같다.

### 선택지

| 선택 | 구조 | 장점 | 영향 |
| --- | --- | --- | --- |
| `D2-A` Approved | 각 `skus[]`에 `availableDeliveryCycles`; true는 `[2,4,8]`, false는 `[]` | 구독 가능 여부와 실제 선택지를 한 객체에서 일관되게 해석 | 동일 배열 반복. BE DTO·FE SKU 상태·QA 관계 검증 필요 |
| `D2-B` 미선택 | 상세 최상위에 `[2,4,8]`, SKU에는 `subscribable`만 제공 | 중복이 적음 | 승인되지 않았으며 구현하지 않음 |

승인 결정은 `D2-A`다. `subscribable=false`에 `null`이나 `[2,4,8]`을 사용하지 않고 빈 배열을 사용한다.

## D3. 상품 공개 기준

### 근거

- `display_status`는 공개 여부 후보이며 재고·품절·판매·구독 상태와 분리돼 있다.
- 현재 DB는 문자열 저장 공간만 제공하고 허용값 집합은 승인하지 않았다.
- 외부 응답은 존재하지 않음과 비공개 상태를 구분해 내부 상태를 노출하면 안 된다.

### 선택지

| 선택 | 기준 | 장점 | 영향 |
| --- | --- | --- | --- |
| `D3-PUBLIC` Approved | 공개 허용값 하나 `PUBLIC`; 목록·상세 모두 동일; 그 외 값은 비공개; 비공개·미존재 동일 404; `displayStatus` 응답 제외 | 공개 여부 의미가 명확하고 판매 상태를 새로 만들지 않으며 정보 노출 최소화 | fixture와 query가 정확히 `PUBLIC`을 사용해야 함. BE query와 QA 경계 테스트 필요 |
| `D3-B` 미선택 | 허용값 `VISIBLE` 사용, 나머지는 동일 | UI 노출 의미가 직접적 | 승인되지 않았으며 구현하지 않음 |
| `D3-C` 미선택 | 여러 공개값 또는 `displayStatus` 응답 포함 | 다양한 화면 상태 표현 가능 | 승인되지 않았으며 현재 범위 초과 |

승인 결정은 `D3-PUBLIC`이다. 목록과 상세 공개 기준을 다르게 두지 않으며 `PUBLIC` 외 모든 값은 외부에서 비공개로 취급한다.

## D4. 상품과 SKU 순서

### 근거

- 검색·정렬 요청 파라미터는 제외됐다.
- Product에는 표시 순서 필드가 없고 SKU에는 `display_order`가 있으며 `(product_id, display_order, id)` 인덱스가 있다.

### 선택지

| 선택 | 보장 | 장점 | 영향 |
| --- | --- | --- | --- |
| `D4-A` Approved | 상품 `productId ASC`; SKU와 목록 가격 `display_order ASC, id ASC` | 새 컬럼 없이 결정적 결과와 안정적 테스트 제공 | 상품 큐레이션 순서는 제공하지 못함. BE order by, FE 입력 순서 사용, QA 순서 검증 필요 |
| `D4-B` 미선택 | 계약상 순서 미보장 | Backend가 가장 자유로움 | 승인되지 않았으며 구현하지 않음 |

승인 결정은 `D4-A`다. 별도 정렬 query parameter나 Product `display_order`는 추가하지 않는다.

## D5. 빈 값과 컬렉션

### 근거

- 공통 계약은 배열 형태의 안정성을 중시한다.
- DB에서 `description`, `thumbnail_url`은 nullable이고 `short_description`은 non-null이다.
- SKU가 없는 공개 상품을 숨긴다는 정책은 승인되지 않았다.

### 선택지

| 선택 | 표현 | 장점 | 영향 |
| --- | --- | --- | --- |
| `D5-A` Approved | 빈 목록 `products: []`; SKU 없는 공개 상품 유지; `skus: []`, `skuPrices: []`, `hasSubscribableSku:false`; nullable 필드는 존재하며 `null` | 응답 shape가 안정적이고 새 공개 정책을 만들지 않음 | FE가 빈 배열·null placeholder를 처리하고 QA가 필드 존재를 검증 |
| `D5-B` 미선택 | nullable/빈 필드 생략 또는 SKU 없는 상품 제외 | payload 감소 가능 | 승인되지 않았으며 구현하지 않음 |

승인 결정은 `D5-A`다. `skuPriceSummary` 자체는 `null`이 아니라 `{ "skuPrices": [] }`다.

## D6. 가격 표현

### 근거

- 저장 타입은 `DECIMAL(12,2)`이고 음수는 허용되지 않는다.
- 요구사항은 표시 가격을 요구하지만 할인·결제·통화 정책은 제외한다.

### 선택지

| 선택 | 표현 | 장점 | 영향 |
| --- | --- | --- | --- |
| `D6-A` Approved | JSON number, 예 `19900.00`; Backend는 수치만 전달하고 통화 기호·천 단위·소수점 표시는 Frontend 책임 | 계산·비교가 가능하고 문자열 파싱 불필요 | JSON number는 화면의 고정 소수 자릿수를 보장하지 않으므로 FE formatting과 QA 수치 비교 필요 |
| `D6-B` 미선택 | JSON string, 예 `"19900.00"` | 자릿수 표면 형식 보존 | 승인되지 않았으며 구현하지 않음 |

승인 결정은 `D6-A`다. currency code, 할인 가격, 결제 금액은 추가하지 않는다.

## D7. 오류 계약

### 근거

- ARCH-006은 공통 오류 shape와 내부 예외 비노출을 승인했다.
- API-001은 `PRODUCT_NOT_FOUND`, `PRODUCT_LIST_UNAVAILABLE`을 후보로 제시했다.
- 비공개와 미존재의 외부 표현에서 내부 상태를 노출하지 않아야 한다.

### 승인 오류

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
| `D7-A` Approved | `PRODUCT_DETAIL_UNAVAILABLE` | 목록과 상세 실패가 대칭이고 FE·QA가 재시도/표시 위치를 구분 가능 | Backend 오류 mapping과 QA 검증 필요 |
| `D7-B` 미선택 | 공통 `INTERNAL_ERROR` | 코드 수가 적고 기존 공통 오류 재사용 | 승인되지 않았으며 구현하지 않음 |

승인 결정은 `D7-A`다. `PRODUCT_NOT_FOUND`는 미존재와 비공개에 동일 적용하고, 목록 500은 `PRODUCT_LIST_UNAVAILABLE`, 상세 500은 `PRODUCT_DETAIL_UNAVAILABLE`을 사용한다.

## 역할별 영향

| 역할 | 영향 |
| --- | --- |
| Backend | 승인 후 DTO, 공개 query, 결정적 정렬, 공개 상태 filter, 오류 mapping과 read-only transaction 구현. Entity 직접 노출 금지 |
| Frontend | 안정된 필드 shape, 빈 배열·null placeholder, number 가격 formatting, SKU별 배송 주기와 오류 code별 화면 상태 구현 |
| QA | 공개 접근, 정확한 JSON shape, 빈 상태, 순서, 공개/비공개 404 동일성, SKU 구독 가능 관계, 안전한 500과 내부 정보 비노출 검증 |

## 추적성

| 요구사항·문서 | API-002 연결 | 상태 |
| --- | --- | --- |
| `REQ-PRODUCT-001`, `AC-PRODUCT-001-01~05` | 목록 필드, 공개 접근, SKU별 가격, 구독 가능 요약, 빈 목록 | 요구사항과 API-002 세부 계약 Approved |
| `REQ-PRODUCT-002`, `AC-PRODUCT-002-01~08` | 상세 필드, SKU, 구독 가능 여부, 2·4·8주, 공개 접근, 조회 불가 표현 | 요구사항과 API-002 세부 계약 Approved |
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

## 사용자 승인 기록

2026-07-12 API-002 후속 요청에서 사용자는 다음 값을 명시적으로 승인했다.

```text
D1: A
D2: A
D3: PUBLIC
D4: A
D5: A
D6: A
D7: A
```

위 D1~D7은 `Approved`다. 선택되지 않은 D1-B, D2-B, D3-B·C, D4-B, D5-B, D6-B, D7-B와 API-001의 나머지 후보는 승인되지 않았다.
