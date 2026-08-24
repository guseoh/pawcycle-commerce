# DOMAIN-004 MVP4 정기배송 직접 관리 도메인

- 작업 ID: `MVP4-SUB-BE-001`
- 상태: Accepted Domain Design
- 등급: 고위험
- 실행 구분: 저장소 준비만
- 승인 입력: 사용자 명시 승인

## Delta 기준

이 문서는 [DOMAIN-002](DOMAIN-002-second-mvp-subscription-domain.md)의 구독 관리와 [DOMAIN-003](DOMAIN-003-subscription-automation-domain.md)의 주문 자동화에 MVP4 규칙만 추가한다. 기존 endpoint, snapshot, Schedule, 주문 자동화, `Idempotency-Key`, `If-Match`와 Subscription→Schedule lock 순서는 유지한다.

MVP4에서는 pending snapshot의 배송 주기가 current snapshot과 같아야 한다는 DOMAIN-003 불변 조건과, pending 적용 뒤에도 기존 주기로 다음 Schedule을 계산한다는 처리 규칙을 아래 규칙으로 대체한다. 이 delta는 과거 주문·Schedule·명령 결과를 소급 변경하지 않는다.

## 다음 회차 날짜 변경

- `RESCHEDULE_NEXT`는 ACTIVE Subscription의 Order가 아직 없는 가장 가까운 SCHEDULED 한 회차에만 적용한다.
- 새 날짜는 Asia/Seoul 기준 오늘보다 미래여야 하며 같은 Subscription의 다른 Schedule 날짜 또는 현재 날짜와 같을 수 없다.
- Schedule ID와 pending target 관계는 유지하고 `scheduled_date`만 바꾼다.
- 변경한 날짜가 해당 회차 처리 뒤 다음 배송 주기를 더하는 기준일이다.
- 명령과 Scheduler는 Subscription row를 먼저, Schedule row를 다음에 잠근다. Scheduler가 먼저 Order를 만들면 이전 `If-Match` 명령은 실패하고, 날짜 변경이 먼저 끝나면 Scheduler는 변경된 미래 회차에 Order를 만들지 않는다.

## 배송 주기 변경과 단일 pending

- `CHANGE_DELIVERY_CYCLE`은 ACTIVE에서만 가능하며 2·4·8주 중 하나를 예약한다.
- 실제 적용될 PlanVersion이 요청 주기를 지원해야 한다. pending 플랜 변경이 있으면 그 PlanVersion을, 없으면 current snapshot의 PlanVersion을 검증한다.
- 현재 다음 배송일과 current snapshot은 즉시 바꾸지 않는다. 다음 회차부터 적용할 새 snapshot만 만든다.
- pending은 플랜·가격·구성·배송 주기를 합친 snapshot 하나다. Subscription당 `pending_plan_changes` 한 행만 유지한다.
- `CHANGE_PLAN`은 기존 pending 배송 주기를 유지하고 요청한 PlanVersion만 바꾼다. `CHANGE_DELIVERY_CYCLE`은 기존 pending PlanVersion·가격·구성을 유지하고 요청한 주기만 바꾼다.
- 플랜→주기와 주기→플랜 순서 모두 마지막으로 요청된 각 항목을 합친 동일한 예약 결과를 만든다. 새 명령은 다른 항목의 예약 값을 지우지 않는다.
- SKIP_NEXT는 pending target을 새 회차로 옮기고, PAUSE와 RESUME은 pending을 유지한다. CANCEL은 pending을 제거하며 적용하지 않는다.

## 자동 주문 적용

- pending target Schedule의 Order에는 pending snapshot을 실제 effective snapshot으로 사용한다.
- pending 적용 전 `subscriptions.delivery_cycle_weeks`와 current snapshot pointer는 바꾸지 않는다.
- pending 적용 transaction은 Order와 Schedule effective snapshot을 기록하고, current snapshot pointer와 `subscriptions.delivery_cycle_weeks`를 함께 갱신한 뒤 pending을 제거한다.
- 다음 미래 Schedule은 실제 effective snapshot의 배송 주기를 원래 Schedule 날짜에 반복해서 더해 오늘보다 미래인 첫 날짜로 만든다.
- pending이 적용되지 않는 회차는 current snapshot의 배송 주기를 계속 사용한다.

## 사용자 상세 projection

- 기존 상세 필드를 유지하고 `nextDelivery`, `pendingChange`, `issue`, `availableActions`를 추가한다.
- `nextDelivery`는 ACTIVE에서 실제 다음 회차가 사용할 effective snapshot을 기준으로 날짜·상태·PlanVersion·가격·주기·상품을 보여 준다. PAUSED와 CANCELED에는 없다.
- `pendingChange`는 단일 pending snapshot의 적용 Schedule과 결합 예약값을 보여 준다. SKIP·PAUSE·RESUME 이후에도 유지되고 CANCEL 또는 적용 뒤에는 없다.
- 상품명·이미지·SKU 이름은 기존 Product·SKU 조회 데이터를 사용자 표시용으로 결합한다. 주문 이력의 불변 snapshot을 대체하지 않는다.
- Schedule의 내부 `hold_reason`은 그대로 노출하지 않고 사용자용 issue code와 안내 문구로 변환한다.
- Backend는 현재 상태와 실제 다음 Schedule 상태로 실행 가능한 `availableActions`를 결정한다. Subscription에 새 HELD 상태를 추가하지 않는다.
- 배송지 누락 HELD는 기존 배송지 변경 경로를 `UPDATE_SHIPPING_ADDRESS`로 안내하고, 결제수단 누락 HELD는 기존 Billing Method 등록 경로를 `REGISTER_BILLING_METHOD`로 안내한다. 이는 재시도 명령이 아니라 기존 선행조건 보완 기능으로 연결하는 것이다.
- 정상 ACTIVE 회차와 PAUSED에서도 기존 API가 허용하는 배송지 변경은 `UPDATE_SHIPPING_ADDRESS`로 노출할 수 있으며, Payment 재시도·재고 강제 재처리 같은 새 action은 추가하지 않는다.

## 안전성과 복구 경계

- 두 새 명령은 기존 command scope 멱등성, replay 우선 판정, stale `If-Match`와 command history 규칙을 그대로 사용한다.
- 날짜 변경, pending 교체, version 증가, history와 성공 replay는 기존 단일 command transaction 안에서 처리한다.
- 배송지·결제수단 누락은 기존 Commerce 복구 경계를 재사용하고, 사용자가 필요한 선행조건을 보완하면 기존 로직이 HELD를 정상화한다.
- 새 table·column·Flyway migration·의존성·Queue·Kafka·Redis·별도 retry API를 추가하지 않는다.
- 저장소 변경은 일반 revert PR로 복구한다. Production·운영 DB·Secret·배포·자동 merge는 이 문서가 승인하지 않는다.
