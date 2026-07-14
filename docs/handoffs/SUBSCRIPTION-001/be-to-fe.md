# SUBSCRIPTION-001 Backend → Frontend 인수인계

## 전달 목적

Frontend가 API-003에서 승인된 구독 생성·내 목록·내 상세 API를 session·CSRF 계약에 맞춰 연동할 수 있도록 구현 결과와 오류 처리를 전달한다.

## 다음 역할 또는 대상 역할

- 수신: Frontend Engineer
- 후속 협업: Backend Engineer, QA Engineer, Tech Lead

## 입력 문서

- `docs/api/API-003-subscription-api-contract-decision-request.md` D1~D8
- `docs/adr/AUTH-003-session-authentication-approved-inputs.md`
- `docs/reports/SUBSCRIPTION-001/be-report.md`

## 사용 가능한 결과

- `POST /api/subscriptions`
- `GET /api/subscriptions`
- `GET /api/subscriptions/{subscriptionId}`
- 승인 JSON shape와 오류 응답
- session principal 소유권, 서울 날짜 계산과 본인 전용 조회

## 인증과 CSRF

- 세 API 모두 AUTH-003 session 인증이 필요하다.
- 생성 POST는 `GET /api/auth/csrf`로 받은 token을 `X-CSRF-TOKEN` header에 넣는다.
- 목록·상세 GET은 CSRF token이 필요 없다.
- request body나 header로 회원 ID를 보내지 않는다. 소유자는 session principal로 결정된다.
- 익명 접근은 `401 AUTH_REQUIRED`, POST의 CSRF 누락·오류는 `403 CSRF_INVALID`다.

## 구독 생성

```http
POST /api/subscriptions
Content-Type: application/json
X-CSRF-TOKEN: <현재 session token>
```

```json
{
  "skuId": 1001,
  "quantity": 2,
  "deliveryCycleWeeks": 4
}
```

성공은 `201 Created`다.

```json
{
  "subscriptionId": 501,
  "nextOrderDate": "2026-08-10"
}
```

- 생성 전에는 정확한 `nextOrderDate`를 클라이언트 확정값으로 표시하지 않는다.
- 성공 후 `subscriptionId`로 상세 화면에 이동한다.
- 유효 요청 1회마다 구독 1건이 생성되므로 제출 중 버튼을 비활성화하고 자동 retry하지 않는다.

## 내 구독 목록

```http
GET /api/subscriptions
```

- 성공은 `200 OK`다.
- 본인 구독만 `subscriptionId DESC` 순서로 반환한다.
- 빈 결과는 `{ "subscriptions": [] }`다.

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

## 내 구독 상세

```http
GET /api/subscriptions/{subscriptionId}
```

- 성공은 `200 OK`다.
- 목록 항목에 현재 `sku.price`와 `createdDate`가 추가된다.
- `price`는 JSON number이며 가격 snapshot이나 결제 금액이 아니다.

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

## 오류 처리

| HTTP | code | Frontend 처리 |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | `fieldErrors`를 입력 필드에 표시 |
| 401 | `AUTH_REQUIRED` | 로그인 필요 흐름 |
| 403 | `CSRF_INVALID` | token 재획득 후 사용자의 명시적 재시도 |
| 404 | `SKU_NOT_FOUND` | SKU 새로 선택 안내 |
| 409 | `SKU_NOT_SUBSCRIBABLE` | 구독 불가 안내 후 상품 정보 갱신 |
| 404 | `SUBSCRIPTION_NOT_FOUND` | 미존재·타인 소유·비숫자 ID를 같은 조회 불가 화면으로 처리 |
| 500 | `SUBSCRIPTION_CREATE_FAILED` | 생성 실패 일반 안내 |
| 500 | `SUBSCRIPTION_LIST_UNAVAILABLE` | 목록 재시도 안내 |
| 500 | `SUBSCRIPTION_DETAIL_UNAVAILABLE` | 상세 재시도 안내 |

공통 오류 JSON은 `code`, `message`, `fieldErrors`다. 비필드 오류는 `fieldErrors: []`다. 404를 근거로 구독 존재 여부나 소유자를 구분하지 않는다.

## 날짜와 표시

- `createdDate`, `nextOrderDate`는 ISO-8601 local date 문자열이다.
- 서버 기준은 `Asia/Seoul`이며 휴일·주말 보정은 없다.
- 화면 표시 형식은 승인된 UX 기준을 사용하고 클라이언트 timezone 변환으로 날짜를 바꾸지 않는다.

## 제외 범위와 주의사항

- 구독 상태·변경·해지·일시정지와 주문·결제·재고 차감은 없다.
- 가격 snapshot과 배송 처리·자동 주문 생성은 없다.
- `nextOrderDate`는 구독의 다음 주문 예정일로 응답한다.
- `Idempotency-Key`와 자동 retry 보장은 없다.
- Backend Entity나 소유자 ID는 응답에 노출되지 않는다.

## 검증 상태

- Backend 보조 Java 21 compile과 focused 단위 테스트는 통과했다.
- PR #42의 Repository Validation에서 Java 25·MySQL 8.4 Backend 테스트 74개와 build, API·Security·query 수 검증이 통과했다.
- 실제 원격 CI와 PR 리뷰 상태는 GitHub를 권위 있는 원본으로 사용한다.

## 검증 포인트

Frontend 완료 후 QA가 실제 session login→CSRF 획득→생성→목록→상세 흐름과 오류·소유권 비노출을 독립 검증한다.

- 생성 성공 후 응답 `subscriptionId`로 상세 이동
- 목록 빈 배열과 `subscriptionId DESC`
- 미존재·타인 소유·비숫자 상세 ID의 동일 404 화면
- CSRF 실패 뒤 자동 POST 재실행 금지
- ISO-8601 날짜를 timezone 변환 없이 날짜 단위로 표시

## 미결정 사항 또는 승인 필요 항목

SUBSCRIPTION-001과 API-003 승인 범위 안의 추가 결정은 없다. 구독 변경·해지, 멱등성, 가격 snapshot과 자동 주문은 별도 승인 작업이 필요하다.

## 남은 위험 또는 주의 사항

- timeout 뒤 사용자가 재시도하면 별도 구독이 생성될 수 있다.
- 상세 가격은 현재 SKU 가격이며 과거 가격을 보존하지 않는다.
- 병합 전 추가 commit으로 Repository Validation이 다시 실패하면 Frontend 연동 기준으로 사용하지 않는다.

## 중단 조건

- PR의 필수 Repository Validation이 실패함
- 실제 응답이 API-003 JSON·오류 계약과 다름
- cross-site 배포를 위해 CORS·cookie·CSRF 결정을 변경해야 함
- `Idempotency-Key`, 구독 상태, 결제·재고·배송 또는 신규 dependency가 필요함
