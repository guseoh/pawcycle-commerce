# DATA-004 정기배송 Order 자동화 데이터 설계

- 작업 ID: `SUB-AUTO-001`
- 문서 상태: Approved implementation input
- 관련 요구사항: [PS-005](../product/PS-005-subscription-automation-requirements.md)
- 관련 도메인: [DOMAIN-003](../domain/DOMAIN-003-subscription-automation-domain.md)
- 관련 ADR: [ARCH-008](../adr/ARCH-008-subscription-order-automation-consistency.md)

## 저장 구조

### `subscription_orders`

| 열 | 의미 |
| --- | --- |
| `id` | Order 식별자 |
| `member_id` | 생성 당시 소유 Member 식별자 |
| `subscription_id` | 원본 Subscription 식별자 |
| `schedule_id` | 처리한 Schedule 식별자, unique |
| `effective_snapshot_id` | 가격·구성의 원본 SubscriptionSnapshot |
| `source_plan_version_id` | snapshot이 참조한 PlanVersion |
| `scheduled_date` | 원래 Schedule 예정일 |
| `processed_at` | 실제 처리 UTC instant (`DATETIME(6)`) |
| `package_total_krw` | 생성 당시 패키지 전체 KRW 가격 |
| `status` | 이번 기준선에서는 `CREATED`만 허용 |

### `subscription_order_items`

`order_id + sku_id`를 PK로 두고 양의 `quantity`를 보존한다. Order 생성 시 `subscription_snapshot_items`에서 복사하며 이후 PlanItem·SubscriptionSnapshot pointer 변경과 무관하게 유지한다.

FK는 기존 Member, Subscription, Schedule, SubscriptionSnapshot, PlanVersion, SKU를 참조한다. `schedule_id` unique가 Schedule당 Order 최대 하나를 DB에서 강제한다. 가격은 기존 snapshot과 같은 `0..9,007,199,254,740,991`, 수량은 양수, status는 `CREATED` CHECK를 사용한다.

## due query와 index

후보는 다음 조건을 모두 만족한다.

- Subscription: `mvp2_managed=true`, `status='ACTIVE'`
- Schedule: `status='SCHEDULED'`, `scheduled_date <= :today`
- 같은 `schedule_id`의 Order 없음
- 같은 Subscription의 더 오래된 unprocessed due Schedule 없음

정렬은 `scheduled_date, schedule_id`이고 `LIMIT :batchSize`를 적용한다. 전체 ACTIVE Subscription을 먼저 읽지 않는다. 이 correctness/query-support 경로를 위해 `subscription_schedules(status, scheduled_date, id, subscription_id)` index를 추가한다. 성능 향상 수치는 주장하지 않으며 Production batch size는 이 설계에서 확정하지 않는다.

## transaction write 순서

1. candidate의 Subscription을 `FOR UPDATE`로 조회한다.
2. Schedule을 `FOR UPDATE`로 조회하고 status·due·Order 부재를 재검사한다.
3. pending target과 current/effective snapshot header·items를 검증한다.
4. Order header와 item snapshot을 insert한다.
5. Schedule effective snapshot을 갱신한다.
6. 필요하면 current snapshot을 승격하고 pending row를 삭제한다.
7. 원래 예정일에서 기존 주기로 계산한 첫 미래 Schedule을 insert한다.
8. Subscription version을 증가시킨다.

모든 DML은 한 transaction이다. plain insert와 unique/FK/CHECK 실패는 transaction 전체를 rollback한다. Order insert unique 경쟁은 같은 Schedule의 기존 Order가 확인될 때만 duplicate/no-op로 판정한다.

## Flyway와 partial-application 경계

현재 최신 V8 다음을 사용한다.

- V9: `subscription_orders` 한 table 생성
- V10: `subscription_order_items` 한 table 생성
- V11: due candidate 지원 index 한 개 생성

각 version은 MySQL non-transactional DDL의 부분 적용 범위를 단일 DDL로 제한한다. 예를 들어 V9 성공 뒤 V10이 실패하면 V9 table을 삭제하지 않고 실패 원인을 고친 뒤 Flyway repair 경계를 확인해 V10부터 재시도한다. down migration, 기존 row 변환·reset과 destructive DDL은 없다.

fresh migration과 V8→V11 upgrade를 MySQL 8.4 local/CI에서 검증한다. 이 문서는 Production schema 적용 또는 Production data 안전성 검증을 주장하지 않는다.
