# API-003 구독 API 계약 통합 승인 요청

## 문서 상태

- 작업 ID: `API-003`
- 역할: Tech Lead
- 상태: `Decision Required`
- 대상: 구독 생성, 내 구독 목록, 내 구독 상세의 Backend 수직 기능
- 승인 주체: 사용자
- 기준 브랜치: PR #38 병합 이후 최신 `main`

이 문서는 D1~D8 추천안을 하나의 승인 묶음으로 제안한다. PR 병합은 사용자 승인으로 해석하지 않으며, 사용자가 명시적으로 승인하기 전에는 Backend 구현 입력이나 `Approved` 계약으로 사용하지 않는다. `API-001`과 `DATA-002` 원본 문서 전체는 `Proposed` 상태를 유지한다.

## 사용자 승인용 요약

| ID | 결정 | 추천안 요약 | 상태 |
| --- | --- | --- | --- |
| D1 | API와 성공 상태 | `POST /api/subscriptions` 201, 목록·상세 GET 200 | `Decision Required` |
| D2 | 생성 요청·응답 | 필수 `skuId`, `quantity`, `deliveryCycleWeeks`; 응답 `subscriptionId`, `nextOrderDate` | `Decision Required` |
| D3 | 목록·상세 응답 | 중첩 Product/SKU 요약, 목록 `subscriptionId DESC`, 상세에 가격·생성일 추가 | `Decision Required` |
| D4 | 인증·소유권 | session principal의 `memberId` 사용, 미존재·타인 소유·비숫자 ID 동일 404 | `Decision Required` |
| D5 | 입력·도메인·안전 오류 | 입력 오류는 단일 `VALIDATION_FAILED`, 도메인·endpoint별 안전 오류 분리 | `Decision Required` |
| D6 | 동일 조건·중복 요청 | 동일 조건 복수 구독 허용, 요청 1회당 1건, MVP 멱등성 저장소 없음 | `Decision Required` |
| D7 | 구독 물리 데이터 | 새 Flyway migration, 최소 필드·FK·CHECK·`(member_id, id)` 인덱스 | `Decision Required` |
| D8 | Backend 구현·검증 | API 3개와 migration을 한 PR에서 구현·테스트, FE 완료 후 통합 QA | `Decision Required` |

전체 추천안을 수정 없이 승인하려면 다음과 같이 응답할 수 있다.

```text
API-003 D1~D8 전체 추천안 승인
```

일부 결정만 수정하려면 다음 형식을 사용한다. 언급하지 않은 결정은 추천안을 승인하는 것으로 명시해야 한다.

```text
API-003 D1, D2, D3, D4, D5, D7, D8 추천안 승인
D6 수정: <선택할 대안 또는 수정 내용>
```

## 이미 승인된 기준선

- 구독 하나는 SKU 하나만 대상으로 한다.
- SKU가 존재하고 `subscribable=true`여야 한다.
- 수량은 1~10, 배송 주기는 2주·4주·8주다.
- 생성일과 다음 주문 예정일은 `Asia/Seoul` 날짜 단위이며, 다음 주문 예정일은 생성일에 배송 주기를 더해 계산한다.
- 휴일·주말·영업일 보정은 하지 않는다.
- 회원은 자신의 구독 목록과 상세만 조회할 수 있다.
- 구독 상태, 결제, 재고, 배송, 변경·해지·일시정지와 자동 주문 생성은 제외한다.
- 공개 상품 API는 API-002 승인 계약과 PRODUCT-001 구현·QA 결과를 유지한다.
- 인증은 AUTH-003의 session·CSRF·principal 계약과 AUTH-004 구현·QA 결과를 유지한다.

## D1. API와 성공 상태

### 결정 ID

`D1`

### 이미 승인된 기준선

구독 생성과 본인 목록·상세는 인증된 회원만 사용하며 세 API를 하나의 Backend 수직 기능으로 구현한다.

### 추천안

| 목적 | Method | URI | 성공 상태 | 인증·CSRF |
| --- | --- | --- | --- | --- |
| 구독 생성 | `POST` | `/api/subscriptions` | `201 Created` | 인증 필요, 유효한 CSRF token 필요 |
| 내 구독 목록 | `GET` | `/api/subscriptions` | `200 OK` | 인증 필요, CSRF 불필요 |
| 내 구독 상세 | `GET` | `/api/subscriptions/{subscriptionId}` | `200 OK` | 인증 필요, CSRF 불필요 |

URI version prefix, 별도 command endpoint와 생성용 action suffix는 추가하지 않는다. AUTH-003의 session 인증, JSON Security 오류와 CSRF 전달 계약을 그대로 사용한다.

### 실질적인 대안

- 생성 성공을 `200 OK`로 통일할 수 있으나 새 리소스 생성 의미와 상세 이동 식별자를 명확히 드러내는 `201 Created`보다 표현력이 낮다.
- `/api/v1/**` 또는 `/api/subscriptions/create`를 도입할 수 있으나 첫 MVP에 버전 운영·command URI 비용만 추가한다.

### 추천 이유

API-001의 기존 후보와 REST 리소스 의미를 유지하면서 Frontend와 Backend가 추가 라우팅 결정을 만들지 않게 한다.

### Backend·Frontend·DB·QA 영향

- Backend: 세 endpoint와 기존 Security chain 경계를 구현한다.
- Frontend: 생성은 POST, 목록·상세는 GET으로 호출한다.
- DB: D1 자체의 추가 영향은 없다.
- QA: method·URI·status, 인증과 CSRF 경계를 Backend 테스트에서 검증한다.

### 위험

후속 API version 전략이 필요해지면 별도 결정과 호환 정책이 필요하다.

### 사용자 선택 항목

`D1 추천안 승인` 또는 성공 상태·URI 대안 지정.

### 상태

`Decision Required`

## D2. 구독 생성 요청과 성공 응답

### 결정 ID

`D2`

### 이미 승인된 기준선

구독은 SKU 하나, 수량 1~10, 배송 주기 2·4·8주로 생성하며 다음 주문 예정일은 서버가 계산한다.

### 추천안

요청의 세 필드는 모두 필수다.

```json
{
  "skuId": 1001,
  "quantity": 2,
  "deliveryCycleWeeks": 4
}
```

성공 응답은 상세 이동 식별자와 서버가 확정한 다음 주문 예정일만 반환한다.

```json
{
  "subscriptionId": 501,
  "nextOrderDate": "2026-08-10"
}
```

API 날짜는 ISO-8601 local date 문자열을 사용한다. 생성 전 클라이언트는 정확한 `nextOrderDate`를 계산하거나 확정값으로 표시하지 않는다.

### 실질적인 대안

- 성공 응답에 전체 구독 상세를 포함할 수 있으나 생성 DTO와 상세 DTO가 중복되고, 승인된 생성 후 상세 GET 이동 흐름을 약화시킨다.
- 클라이언트가 `createdDate`나 `nextOrderDate`를 보낼 수 있으나 서버 날짜·도메인 계산 책임과 충돌한다.

### 추천 이유

승인된 입력만 받고 서버 계산 책임을 보존하며, Frontend가 상세로 이동하는 데 필요한 최소 결과를 제공한다.

### Backend·Frontend·DB·QA 영향

- Backend: body type·필수값 검증 후 현재 날짜와 다음 주문 예정일을 계산한다.
- Frontend: 세 입력만 전송하고 응답 ID로 상세로 이동한다.
- DB: D7의 `sku_id`, `quantity`, `delivery_cycle_weeks`, 날짜 필드에 매핑한다.
- QA: 필수값, 타입, 범위, 날짜 형식과 서버 확정값을 테스트한다.

### 위험

생성 응답만으로 전체 표시 정보를 구성할 수 없으므로 후속 상세 GET이 실패하면 생성 성공 후 화면 표시가 지연될 수 있다.

### 사용자 선택 항목

`D2 추천안 승인` 또는 생성 성공 응답 범위 수정.

### 상태

`Decision Required`

## D3. 내 구독 목록·상세 응답

### 결정 ID

`D3`

### 이미 승인된 기준선

목록과 상세는 본인 구독만 제공하며 상품명, SKU, 수량, 배송 주기와 다음 주문 예정일을 표시한다. 상세는 SKU 표시 가격과 구독 생성일도 제공한다.

### 추천안

목록 최상위는 `subscriptions` 배열이고 빈 결과는 `[]`다. `subscriptionId DESC`로 고정해 최신 생성 구독을 먼저 제공한다.

```json
{
  "subscriptions": [
    {
      "subscriptionId": 501,
      "product": {
        "productId": 101,
        "name": "반려견 사료"
      },
      "sku": {
        "skuId": 1001,
        "skuName": "2kg"
      },
      "quantity": 2,
      "deliveryCycleWeeks": 4,
      "nextOrderDate": "2026-08-10"
    }
  ]
}
```

상세는 목록 항목의 모든 필드에 `sku.price`와 `createdDate`를 추가한다. `price`는 API-002처럼 JSON number이며 결제 금액이나 가격 snapshot이 아니다.

```json
{
  "subscriptionId": 501,
  "product": {
    "productId": 101,
    "name": "반려견 사료"
  },
  "sku": {
    "skuId": 1001,
    "skuName": "2kg",
    "price": 19900.00
  },
  "quantity": 2,
  "deliveryCycleWeeks": 4,
  "createdDate": "2026-07-13",
  "nextOrderDate": "2026-08-10"
}
```

nullable 컬렉션을 사용하지 않는다. 결제 금액, 가격 snapshot, 구독 상태와 배송 예정일은 추가하지 않는다.

### 실질적인 대안

- Product/SKU 필드를 평탄화할 수 있으나 공개 상품 API와 의미 그룹이 달라지고 필드 충돌 가능성이 커진다.
- 목록에 `sku.price`와 `createdDate`까지 포함할 수 있으나 현재 목록 표시 요구보다 payload와 조회 책임을 늘린다.
- 정렬을 미보장할 수 있으나 FE와 테스트 결과가 불안정해진다.

### 추천 이유

화면이 요구하는 최소 정보를 안정적인 중첩 구조로 제공하고, 추가 요청 파라미터 없이 결정적 순서를 보장한다.

### Backend·Frontend·DB·QA 영향

- Backend: 본인 목록을 `subscriptionId DESC`로 조회하고 N+1 없이 Product/SKU 요약을 조립한다.
- Frontend: 입력 순서를 그대로 표시하고 빈 배열을 빈 상태로 처리한다.
- DB: D7의 `(member_id, id)` 인덱스가 필터와 정렬을 지원한다.
- QA: shape, 빈 배열, 순서, 날짜와 가격 number를 검증한다.

### 위험

현재 SKU 가격 변경은 상세 응답에 즉시 반영되며 과거 가격을 보존하지 않는다. 가격 snapshot은 범위 밖이다.

### 사용자 선택 항목

`D3 추천안 승인` 또는 응답 필드·정렬 수정.

### 상태

`Decision Required`

## D4. 인증·소유권·정보 비노출

### 결정 ID

`D4`

### 이미 승인된 기준선

세 API는 session 인증을 사용하고 회원은 자신의 구독만 조회한다. principal은 최소 `memberId`다.

### 추천안

- 세 API 모두 인증이 필요하다.
- 생성 소유자는 request body가 아니라 session principal의 `memberId`로 정한다.
- 목록은 `memberId` 소유 구독만 조회한다.
- 상세는 subscription ID와 현재 `memberId`를 함께 조건으로 조회한다.
- 존재하지 않는 구독, 다른 회원 소유 구독과 숫자가 아닌 상세 식별자는 모두 `404 Not Found`, `SUBSCRIPTION_NOT_FOUND`와 같은 message·빈 `fieldErrors`를 사용한다.
- 소유자 ID와 다른 회원 구독의 존재 여부를 응답하지 않는다.

### 실질적인 대안

- 타인 소유 구독을 `403 ACCESS_DENIED`로 구분할 수 있으나 구독 존재 여부를 노출한다.
- 숫자가 아닌 ID를 `400 VALIDATION_FAILED`로 구분할 수 있으나 외부 관찰 결과가 달라지고 상세 조회 불가 계약이 복잡해진다.

### 추천 이유

API-001 후보와 승인된 “조회할 수 없는 구독” UX를 유지하고 소유권 정보를 최소화한다.

### Backend·Frontend·DB·QA 영향

- Backend: client member ID를 신뢰하지 않고 소유자 조건 조회를 사용한다.
- Frontend: 세 상세 실패를 같은 조회 불가 화면으로 처리한다.
- DB: `member_id`와 `id` 복합 조회가 필요하다.
- QA: 다른 회원의 존재와 소유자 정보가 status·body·URL·로그에 노출되지 않는지 검증한다.

### 위험

서버 로그와 metric에서도 소유자·구독 식별자 취급 기준을 지켜야 하며 실제 배포 로그는 통합 QA에서 재확인해야 한다.

### 사용자 선택 항목

`D4 추천안 승인` 또는 타인 소유·잘못된 ID의 외부 표현 수정.

### 상태

`Decision Required`

## D5. 입력·도메인·안전 오류 계약

### 결정 ID

`D5`

### 이미 승인된 기준선

공통 오류는 `code`, `message`, `fieldErrors`를 사용하고 비필드 오류는 빈 배열이다. 내부 예외와 저장소 세부 정보는 응답에 노출하지 않는다.

### 추천안

| 조건 | HTTP | code | fieldErrors |
| --- | --- | --- | --- |
| 필수값 누락, JSON 타입 오류, 수량·배송 주기 위반 | 400 | `VALIDATION_FAILED` | 관련 필드별 항목 |
| 존재하지 않는 SKU | 404 | `SKU_NOT_FOUND` | `[]` |
| `subscribable=false` SKU | 409 | `SKU_NOT_SUBSCRIBABLE` | `[]` |
| 존재하지 않거나 조회할 수 없는 구독 | 404 | `SUBSCRIPTION_NOT_FOUND` | `[]` |
| 예상하지 못한 생성 오류 | 500 | `SUBSCRIPTION_CREATE_FAILED` | `[]` |
| 예상하지 못한 목록 오류 | 500 | `SUBSCRIPTION_LIST_UNAVAILABLE` | `[]` |
| 예상하지 못한 상세 오류 | 500 | `SUBSCRIPTION_DETAIL_UNAVAILABLE` | `[]` |

예시 validation 오류:

```json
{
  "code": "VALIDATION_FAILED",
  "message": "입력 내용을 확인해 주세요.",
  "fieldErrors": [
    {
      "field": "quantity",
      "message": "수량은 1개 이상 10개 이하로 입력해 주세요."
    }
  ]
}
```

내부 exception message, SQL, schema·table·column 이름, stack trace와 소유자 정보를 응답에 포함하지 않는다. `AUTH_REQUIRED`, `CSRF_INVALID`은 AUTH-003의 기존 Security 계약을 사용한다.

### 실질적인 대안

API-001처럼 `REQUIRED_FIELD_MISSING`, `INVALID_QUANTITY`, `INVALID_DELIVERY_CYCLE`을 분리할 수 있다. 이 방식은 code만으로 원인을 구분하지만 같은 입력 form에서 code 수와 FE 분기·QA 조합이 늘고 공통 `fieldErrors`의 역할과 중복된다.

### 추천 이유

입력 문제는 단일 code와 필드별 세부 정보로 처리하고, SKU 상태·조회 불가·endpoint별 서버 실패처럼 사용자 행동과 운영 대응이 다른 오류만 별도 code로 둔다.

### Backend·Frontend·DB·QA 영향

- Backend: request binding·validation을 하나의 안전한 오류 shape로 매핑한다.
- Frontend: `VALIDATION_FAILED`의 `fieldErrors`를 입력별로 표시하고 비필드 code만 별도 분기한다.
- DB: CHECK·FK 실패가 그대로 노출되지 않도록 애플리케이션 검증과 안전 mapping이 필요하다.
- QA: 누락·null·타입·범위·malformed JSON과 모든 안전 500 body를 검증한다.

### 위험

fieldErrors의 순서나 중복 정책을 구현 세부로 과도하게 고정하면 유지보수가 어려우므로 필드와 message 존재를 중심으로 검증해야 한다.

### 사용자 선택 항목

`D5 추천안 승인` 또는 API-001의 분리 validation code 유지 지정.

### 상태

`Decision Required`

## D6. 동일 조건 구독과 중복 요청

### 결정 ID

`D6`

### 이미 승인된 기준선

구독 하나는 SKU 하나를 대상으로 하며 입력 form과 이전 POST 요청을 자동 복원·재실행하지 않는다.

### 추천안

- 동일 회원이 동일 SKU·수량·배송 주기로 여러 구독을 보유하는 것을 금지하지 않는다.
- 동일 조건 unique 제약을 추가하지 않는다.
- 유효한 HTTP 요청 한 번마다 구독 한 건을 생성한다.
- `Idempotency-Key`와 서버 중복 요청 저장소는 이번 MVP에서 도입하지 않는다.
- Frontend는 처리 중 제출 버튼을 비활성화해 일반적인 이중 클릭을 줄인다.
- 네트워크 retry와 멱등성 보장은 후속 결정으로 남긴다.

### 실질적인 대안

- 동일 조건 unique 제약은 단순 중복을 막지만 사용자가 같은 조건의 독립 구독을 의도적으로 만들 수 없고 수정·해지 없는 MVP에서 복구가 어렵다.
- `Idempotency-Key`는 retry 안전성을 높이지만 key 수명, 저장소, 응답 replay와 장애 복구 계약이 추가된다.

### 추천 이유

승인되지 않은 “동일 조건은 같은 구독” 정책을 만들지 않고 첫 수직 기능의 범위를 유지한다.

### Backend·Frontend·DB·QA 영향

- Backend: 요청마다 독립 transaction으로 한 건을 생성한다.
- Frontend: 처리 중 제출을 비활성화하지만 네트워크 retry를 자동 수행하지 않는다.
- DB: 동일 조건 unique index와 idempotency table이 없다.
- QA: 동일 조건의 연속 두 유효 요청이 서로 다른 두 구독을 생성함을 확인한다.

### 위험

timeout 후 사용자가 재시도하거나 client·proxy가 요청을 반복하면 중복 구독이 생성될 수 있다. 이 위험을 수용하는지 사용자 별도 승인이 필요하다.

### 사용자 선택 항목

`D6 추천안 승인` 또는 unique·Idempotency 대안 지정.

### 상태

`Decision Required`

## D7. 구독 물리 데이터 입력

### 결정 ID

`D7`

### 이미 승인된 기준선

Subscription은 소유 회원, SKU 하나, 수량, 배송 주기, 생성일과 다음 주문 예정일을 가진다. 상태·결제·배송 데이터는 없다.

### 추천안

- 기존 V1 다음 순번의 새 Flyway migration으로 `subscriptions`를 추가한다.
- 필드: `id`, `member_id`, `sku_id`, `quantity`, `delivery_cycle_weeks`, `created_date`, `next_order_date`, `created_at`, `updated_at`.
- PK와 자동 증가 식별자를 사용한다.
- `member_id`, `sku_id`는 NOT NULL FK다.
- `quantity`는 NOT NULL이며 1~10 CHECK를 둔다.
- `delivery_cycle_weeks`는 NOT NULL이며 2·4·8 CHECK를 둔다.
- `created_date`, `next_order_date`는 NOT NULL 날짜 필드이며 `next_order_date > created_date` CHECK를 둔다.
- 정확한 `created_date + delivery_cycle_weeks` 계산은 도메인 로직과 테스트가 보장하고 복잡한 DB 계산 제약은 두지 않는다.
- `subscriptions(member_id, id)` 인덱스 하나를 추가한다.
- 상태, soft delete, 가격 snapshot, 결제·배송·주문 FK, 동일 조건 unique와 측정 근거 없는 추가 인덱스는 추가하지 않는다.
- 세부 SQL type·제약·인덱스 이름은 기존 V1의 MySQL 8.4 convention에 맞추되 위 의미 계약을 변경하지 않는다.

### 실질적인 대안

- 도메인 검증만 두고 CHECK를 생략할 수 있으나 직접 SQL이나 향후 코드 결함으로 잘못된 값이 저장될 수 있다.
- `(member_id, next_order_date)` 인덱스를 추가할 수 있으나 이번 목록 정렬은 ID이고 측정 근거가 없다.

### 추천 이유

도메인과 DB가 핵심 범위를 함께 보호하면서 현재 세 API에 필요한 최소 조회만 지원한다.

### Backend·Frontend·DB·QA 영향

- Backend: migration과 Entity mapping을 같은 PR에서 작성하고 MySQL 8.4로 검증한다.
- Frontend: 직접 영향은 없다.
- DB: 새 테이블·FK·CHECK·복합 인덱스가 추가된다.
- QA: fresh migration, 제약 위반, FK와 query plan·query count를 검증한다.

### 위험

대용량 회원별 목록에서 인덱스 효과는 실제 데이터로 다시 측정해야 하며, 감사 시각 저장 방식은 기존 공통 convention을 따라야 한다.

### 사용자 선택 항목

`D7 추천안 승인` 또는 CHECK·인덱스·필드 수정.

### 상태

`Decision Required`

## D8. Backend 구현·검증 경계

### 결정 ID

`D8`

### 이미 승인된 기준선

세 API를 API별 작업으로 분리하지 않고 하나의 Backend 수직 작업으로 구현하며, 별도 API별 QA PR을 만들지 않는다.

### 추천안

- API 3개와 D7 migration을 하나의 후속 Backend PR에서 구현한다.
- 생성은 하나의 원자적 transaction으로 처리한다.
- 목록·상세는 read-only transaction을 사용한다.
- `Asia/Seoul` 날짜를 테스트 가능하게 공급하는 `Clock` 또는 동등한 최소 구조를 사용한다.
- JPA 관계는 필요한 방향만 두고 미래 기능용 양방향 관계·cascade를 추가하지 않는다.
- 목록·상세 N+1을 방지하고 실제 query 수를 MySQL 통합 테스트로 확인한다.
- 신규 dependency를 추가하지 않는다.
- 구현 PR에 domain/application 단위 테스트, API·MySQL 통합 테스트와 Security 회귀를 포함한다.
- 별도 QA PR은 만들지 않고 Backend 구현 PR의 테스트와 Tech Lead 검토로 완료한다.
- Frontend 완료 후 인증·상품·구독 전체 흐름의 독립 통합 QA를 한 번 수행한다.
- 승인 후 Docker 로컬 실행·Health Check SRE 작업은 별도 역할 범위에서 병렬 착수할 수 있다.

### 실질적인 대안

- API별 Backend·QA PR로 분리하면 변경 단위는 작아지지만 migration·Entity·조회 모델을 반복 조정하고 전체 흐름 완료가 늦어진다.
- 시스템 현재 시각을 직접 사용할 수 있으나 날짜 경계 테스트가 불안정해진다.

### 추천 이유

하나의 migration·domain·Security 경계를 공유하는 기능을 수직으로 완성하면서 날짜·query·소유권 회귀를 구현 PR 안에서 즉시 보호한다.

### Backend·Frontend·DB·QA 영향

- Backend: 하나의 역할 작업과 PR로 구현·검증한다.
- Frontend: 승인 계약을 기준으로 후속 연동하며 API별 별도 승인 작업을 기다리지 않는다.
- DB: migration과 mapping이 구현 PR에서 함께 검증된다.
- QA: 별도 API별 PR 없이 Backend 테스트를 검토하고 FE 완료 후 독립 통합 QA를 수행한다.

### 위험

한 PR의 범위가 커질 수 있으므로 기능 경계별 테스트와 변경 파일 설명을 명확히 해야 한다.

### 사용자 선택 항목

`D8 추천안 승인` 또는 작업·QA 분할 방식 수정.

### 상태

`Decision Required`

## 결정 간 일관성

- D1의 보호 endpoint는 AUTH-003 session·CSRF 계약을 재사용한다.
- D2·D3 JSON은 PS-002·DOMAIN-001·UX-001/PS-003 표시 요구를 넘지 않는다.
- D4는 principal `memberId`와 본인 소유 규칙을 연결한다.
- D5의 단일 validation code는 API-001의 세 분리 code 후보를 대체하는 이번 추천안이며 API-001 원본 상태는 바꾸지 않는다.
- D6의 중복 허용은 D7의 unique 제약 제외와 일치한다.
- D3의 정렬은 D7의 `(member_id, id)` 인덱스와 일치한다.
- D8은 D1~D7을 하나의 Backend 구현 입력으로 전달하되 사용자 승인 전에는 구현을 시작하지 않는다.

## 승인 후 실행 순서

1. 구독 Backend 3개 API, migration과 테스트를 하나의 수직 작업으로 구현한다.
2. Docker 로컬 실행·Health Check SRE 작업을 별도 역할에서 병렬 착수한다.
3. Frontend가 승인 계약을 연동한다.
4. Frontend 완료 후 인증·상품·구독 전체 흐름을 독립 통합 QA로 한 번 검증한다.

## 명시적 제외

- Backend·Frontend 제품 코드와 Flyway/JPA 실제 구현
- 구독 상태, 변경, 해지, 일시정지, 주문, 결제, 재고와 배송
- `Idempotency-Key`, 중복 요청 저장소와 자동 retry
- OpenAPI 생성 도구와 신규 dependency
- Docker·Health Check 실제 구성과 CI workflow 변경
- API별 별도 QA·문서 PR
- `API-001`·`DATA-002` 전체 Approved 전환
- 사용자 승인 전 `Approved` 기록과 자동 병합

## 사용자 결정 요청

D1~D8은 모두 `Decision Required`다. 사용자는 전체 추천안을 한 번에 승인하거나 특정 ID만 수정할 수 있다. 승인 응답이 없으면 Backend 구현을 시작하지 않는다.
