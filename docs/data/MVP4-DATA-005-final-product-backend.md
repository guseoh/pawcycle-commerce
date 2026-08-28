# MVP4-DATA-005 최종 상품 백엔드 데이터 delta

- 작업 ID: `MVP4-FINAL-001`
- 기준 main: `3e274bb1dc4c47e566b682d082262ea330a90122`
- Migration: `V25`, `V26`, `V27` additive forward-only
- 실행 구분: 저장소 변경

기존 migration은 수정하지 않는다. Production database migration·repair·rollback은 수행하지 않았다.

## V25 Personalization

`pets`에 nullable `breed VARCHAR(80)`, `weight_kg DECIMAL(5,2)`와 양수/최대 200kg check를 추가한다. 기존 Pet row에는 fake backfill을 하지 않는다. `interaction_events`는 member-scoped client UUID와 server `occurred_at`을 저장한다.

핵심 제약과 access path:

- unique `(member_id,event_id)`로 batch retry를 무해하게 만든다.
- event type check는 `PRODUCT_IMPRESSION`, `PRODUCT_VIEW`, `SEARCH`, `FILTER`, `RECOMMENDATION_IMPRESSION`, `RECOMMENDATION_CLICK`만 허용한다.
- 추천 impression/click에는 `product_id`와 `recommendation_request_id`가 필요하다.
- FK는 member/product/pet에 연결하고, `(member_id,occurred_at,id)`, `(member_id,product_id,occurred_at,id)`, `(event_type,occurred_at,product_id)`, `(pet_id,occurred_at,id)` index를 둔다.
- raw search text는 column과 persistence path를 제공하지 않는다.

Recommendation query는 breed/weight를 읽지 않는다. 둘은 추후 호환성 metadata가 승인될 때까지 personalization 입력으로만 보존한다.

## V26 Next-delivery Add-on

`subscription_schedule_addons`는 `(schedule_id,sku_id)` PK, `quantity 1..10`, snapshot `unit_price_krw DECIMAL(18,2)`, created/updated timestamp, Schedule/SKU FK와 SKU lookup index를 가진다. SKU의 canonical `DECIMAL(12,2)` 가격을 BigDecimal로 받아 round/truncate하지 않는다. 한 Schedule의 최대 10개 distinct SKU는 command validation으로 제한한다.

`subscription_order_addon_items`는 소비 후 immutable history이며 `(subscription_order_id,sku_id)` PK, quantity, snapshot `unit_price_krw DECIMAL(18,2)`와 order/SKU FK를 가진다. `subscription_orders.package_total_krw`도 V26에서 DECIMAL(18,2)로 확장한다. Schedule Add-on은 due transaction에서 lock한 뒤 common Order `order_items`(DECIMAL(18,2))에도 포함하고, 소비가 성공한 같은 transaction에서 history insert 후 current row를 삭제한다. 기본 `subscription_order_items.order_id`는 common `orders.id`가 아니라 `subscription_orders.id`를 참조한다.

Schedule hold reason check에 internal `ORDER_STOCK_UNAVAILABLE`을 additive하게 추가한다. 이 상태만 자동 재평가 가능한 recoverable HELD로 취급하고, API projection은 이를 기존 user-facing `STOCK_UNAVAILABLE` issue로 매핑한다.

## V27 AI/Reminder

`product_review_summaries`는 product별 단일 lazy cache다.

- PK `product_id`, Product FK
- `source_fingerprint CHAR(64)`, `summary VARCHAR(500)`, `generated_at`
- summary length check

Fingerprint는 visible review 전체 집합의 review ID, rating, content, updated time을 stable order로 포함한다. AI input은 latest 30개로 bounded되지만 30개 밖의 visible review 변경도 cache를 stale하게 만든다. visible review가 3개 미만이면 AI/cache를 사용하지 않는다.

`notifications.type` check에 `SUBSCRIPTION_DELIVERY_REMINDER`를 추가한다. reminder identity는 기존 `(member_id,type,reference_type,reference_id)` unique key를 사용하며, `reference_type=SCHEDULE`과 Schedule ID로 만든다. Processor가 Seoul date 기준 eligible Schedule을 만들고, 현재 조건을 벗어난 read/unread reminder를 정리해 Schedule 재진입을 허용한다.

## Transaction and lock invariants

기존 Subscription command는 `Subscription FOR UPDATE → Schedule FOR UPDATE` 순서를 유지한다. Add-on command는 그 뒤 current Schedule Add-on을 읽고 쓴다. Due automation은 다음 하나의 transaction에서 진행한다.

`Subscription lock → Schedule lock → Add-on lock → effective base snapshot → base/add-on eligibility and inventory lock → one Order → one Billing Payment → base/add-on order_items and reservations → Add-on history → current Add-on delete → pending snapshot promotion → next Schedule → Subscription version`

base 또는 Add-on 검증 실패 시 common Order/Payment/reservation을 만들지 않고 Schedule만 `HELD`로 전환한다. 생성된 common Order와 Subscription order history 사이에 예외가 발생하면 전체 transaction이 rollback된다. Add-on SET 자체는 재고를 reserve하지 않는다.

SKIP은 current Add-on row의 Schedule FK를 새 actual next Schedule로 이동하며, RESCHEDULE·CHANGE_CYCLE·PAUSE/RESUME은 보존하고, CANCEL은 미소비 row를 삭제한다. Add-on은 Plan Snapshot에 넣지 않는다.

## Recovery boundary

V25~V27은 MySQL/Flyway forward migration이다. DDL auto-commit 경계가 있으므로 down migration이나 자동 데이터 rollback을 제공하지 않는다. 실패 시 Flyway validation 상태와 대상 schema를 확인한 후 승인된 migration repair 또는 revert PR/backup 복구 절차를 별도로 판단한다. 이 branch에서는 local/test schema 준비만 하며 Production 적용 성공을 주장하지 않는다.
