# DOMAIN-003 정기배송 주문 자동화 도메인

- 작업 ID: `SUB-AUTO-001`
- 상태: Accepted Domain Design
- 승인 입력: [PS-005](../product/PS-005-subscription-automation-requirements.md)

## 용어와 책임

| 용어 | 책임 |
| --- | --- |
| SubscriptionSchedule | 구독의 예정된 한 회차와 원래 예정일을 보존한다. 실제 처리 결과가 아니다. |
| Recurring Order | 한 Schedule을 실제로 처리해 생성된 최소 주문 결과다. |
| Effective Snapshot | 해당 Schedule의 Order 가격·구성을 결정한 SubscriptionSnapshot이다. |
| Order Item Snapshot | Order 생성 시점의 SKU와 수량을 고정한 항목이다. |
| Order Automation | due 후보 조회와 Schedule별 독립 transaction을 조율하는 정상 업무 경로다. |
| Schedule Reconciliation | 이미 처리된 결과의 안전한 cardinality 보정을 담당하는 별도 복구 경로다. |

## 불변 조건

- `SubscriptionSchedule 1개 → Recurring Order 0개 또는 1개`다.
- Order가 없는 due Schedule은 처리되지 않은 상태이며 reconciliation이 이를 처리된 것으로 바꾸지 않는다.
- Order의 가격·items·source PlanVersion·effective snapshot·scheduled date·processed instant는 생성 뒤 불변이다.
- 성공한 due 처리에는 Order, Schedule effective snapshot, pending 승격, 다음 미래 Schedule, Subscription version 증가가 모두 존재한다.
- 실패한 due 처리에는 위 성공 결과가 하나도 새로 남지 않는다.
- ACTIVE Subscription의 정상 처리 뒤 `today`보다 미래인 SCHEDULED 회차는 정확히 하나다.
- pending snapshot의 배송 주기는 기존 current snapshot 배송 주기와 같아야 한다. 자동화가 주기를 변경하지 않는다.

## 처리 규칙

1. Asia/Seoul의 `today`를 한 실행 기준으로 정한다.
2. bounded 후보 중 Subscription별 가장 오래된 unprocessed due Schedule 하나를 선택한다.
3. transaction 안에서 Subscription과 Schedule을 다시 확인하고 경쟁 writer와 직렬화한다.
4. pending target이면 pending snapshot, 아니면 current snapshot을 effective snapshot으로 사용한다.
5. effective snapshot의 header와 items를 Order snapshot으로 복사한다.
6. pending target이면 current pointer를 바꾸고 pending을 제거한다.
7. 원래 scheduled date에서 기존 주기를 더해 `today`보다 미래인 첫 날짜 하나만 만든다.
8. version을 증가시키고 transaction을 commit한다.

예를 들어 원래 예정일이 7월 1일, 주기가 2주, 복구일이 8월 1일이면 7월 1일 Schedule의 Order 하나만 만들고 다음 Schedule은 8월 12일이다. 7월 15일·29일용 Order는 만들지 않는다.

## 경쟁 결과

- 같은 Schedule을 두 transaction이 처리하면 Subscription/Schedule lock과 Order unique constraint로 하나만 `CREATED`가 된다.
- 후행 transaction은 이미 존재하는 Order를 duplicate/no-op로 판정하며 Schedule을 다시 advance하지 않는다.
- 사용자 명령이 먼저 Schedule을 SKIPPED·HELD·CANCELED로 바꾸거나 Subscription을 PAUSED·CANCELED로 바꾸면 자동화는 no-op다.
- 자동화가 먼저 commit하면 version이 증가하므로 이전 ETag의 사용자 명령은 기존 optimistic version 계약에 따라 충돌한다.
- 실패 target은 다음 실행에도 due 후보이며, 다른 target의 독립 transaction에는 영향을 주지 않는다.

## reconciliation 책임

reconciliation은 Order가 없는 due Schedule을 수정하지 않는다. 이미 Order가 생성됐지만 미래 Schedule이 누락된 상태처럼 Order 원본만으로 다음 날짜를 안전하게 재구성할 수 있을 때만 보정한다. 여러 미래 Schedule, snapshot 불일치처럼 안전한 단일 결과를 정할 수 없는 상태는 자동 변경하지 않고 실패로 관측한다.

Payment, 재고, 배송과 외부 메시징은 이 도메인에 포함하지 않는다.
