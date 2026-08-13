# API-007 MVP3 Final Commerce API 계약

## 상태

- 작업 ID: `MVP3-FINAL-001`
- 상태: Approved input 구현
- 실행 구분: 저장소 준비만

## Additive API

회원 API는 `GET /api/payment-capabilities`, `GET /api/payment-methods/toss/billing`, `POST /api/orders/{orderId}/cancellations`, `POST /api/orders/{orderId}/returns`, `GET /api/notifications`, `PATCH /api/notifications/{id}/read`, `PATCH /api/notifications/read-all`을 제공한다. 주문 상세는 기존 필드를 유지하고 `payment`, `delivery`, `cancellation`, `return`, `refunds`, `availableActions`를 additive하게 제공한다. `availableActions`는 서버가 결정하며 UNKNOWN 결제·환불에는 재시도 action을 제공하지 않는다.

관리자 API는 Delivery ship/complete/fail, Return approve/reject/receive, Refund process/retry/reconcile, Payment reconcile, 명시적 Billing retry(`/api/admin/payments/{id}/retry-billing`), Orders, Operations, Audit log 조회를 `/api/admin/**`에 제공한다. Operations projection은 `PREPARING`/`SHIPPED`/`FAILED` 배송, `APPROVED` 반품, `PROCESSING`/`UNKNOWN` 결제·환불, 재고 부족 Billing 보류를 각각 실제 mutation action과 함께 노출한다. admin endpoint는 `ROLE_ADMIN`이고, 그 외 `/api/**`는 인증이 필요하다. 모든 mutation은 기존 CSRF 규칙을 사용하며 다른 회원 주문·알림은 404이다.

취소는 PAID + PREPARING delivery + SUCCEEDED payment에서만 가능하고 같은 요청은 기존 aggregate를 반환한다. 반품 요청의 최초·멱등 응답은 `returnId`, `status`, `reason`, `rejectionReason`, `restock`, `requestedAt` projection을 동일하게 반환한다. 반품 요청 가능 기간은 `pawcycle.commerce.return-request-days` 설정을 사용하며 기본값은 배송 완료 후 7일이고, 주문 상세 `availableActions`도 같은 `delivered_at + requestDays` 조건을 사용한다. Billing preparation은 DB에서 READY→PROCESSING을 원자적으로 점유한 뒤 Provider issuance를 한 번만 실행한다. Billing/Refund PROCESSING 대사는 Provider write를 재실행하지 않고 query 경로만 사용하며, unresolved 결과는 원래 미확정 상태를 유지한다. Billing 대사 실패는 기존 명시적 실패와 동일한 release/retry/HELD 정책을 따른다. 명시적 Billing retry의 재고 확보 실패는 schedule을 `HELD/PAYMENT_RETRY_STOCK_UNAVAILABLE`로 유지하고 READY attempt를 만들지 않으며, 성공한 재예약은 다음 payment ID의 `RESERVE` inventory movement를 남긴다. reconciliation 한도가 10인 결제·환불은 Operations reconcile action을 노출하지 않는다. 0원 refund는 Provider 호출 없이 로컬에서 성공 처리하며, 0원보다 큰 refund만 Provider I/O를 수행한다. Refund provider 호출은 transaction 밖에서 이뤄지며, provider 미구성은 503, 상태 전이·retry 한도 위반은 409으로 기존 `ApiErrorResponse`로 응답한다.

## Provider와 운영 경계

Sandbox capability는 `local-integration`에서만 노출한다. Production/AWS/운영 DB/Secret/Toss live 호출은 이 계약과 구현 범위 밖이다. Notifications는 DB in-app 전용이며 Email, SMS, Kafka, Outbox를 포함하지 않는다.
