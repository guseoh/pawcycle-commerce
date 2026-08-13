# API-007 MVP3 Final Commerce API 계약

## 상태

- 작업 ID: `MVP3-FINAL-001`
- 상태: Approved input 구현
- 실행 구분: 저장소 준비만

## Additive API

회원 API는 `GET /api/payment-capabilities`, `GET /api/payment-methods/toss/billing`, `POST /api/orders/{orderId}/cancellations`, `POST /api/orders/{orderId}/returns`, `GET /api/notifications`, `PATCH /api/notifications/{id}/read`, `PATCH /api/notifications/read-all`을 제공한다. 주문 상세는 기존 필드를 유지하고 `payment`, `delivery`, `cancellation`, `return`, `refunds`, `availableActions`를 additive하게 제공한다. `availableActions`는 서버가 결정하며 UNKNOWN 결제·환불에는 재시도 action을 제공하지 않는다.

관리자 API는 Delivery ship/complete/fail, Return approve/reject/receive, Refund process/retry/reconcile, Payment reconcile, Orders, Operations, Audit log 조회를 `/api/admin/**`에 제공한다. admin endpoint는 `ROLE_ADMIN`이고, 그 외 `/api/**`는 인증이 필요하다. 모든 mutation은 기존 CSRF 규칙을 사용하며 다른 회원 주문·알림은 404이다.

취소는 PAID + PREPARING delivery + SUCCEEDED payment에서만 가능하고 같은 요청은 기존 aggregate를 반환한다. 반품은 delivered 후 7일 이내 전체 주문에 한해 요청할 수 있다. Refund provider 호출은 transaction 밖에서 이뤄지며, provider 미구성은 503, 상태 전이·retry 한도 위반은 409으로 기존 `ApiErrorResponse`로 응답한다.

## Provider와 운영 경계

Sandbox capability는 `local-integration`에서만 노출한다. Production/AWS/운영 DB/Secret/Toss live 호출은 이 계약과 구현 범위 밖이다. Notifications는 DB in-app 전용이며 Email, SMS, Kafka, Outbox를 포함하지 않는다.
