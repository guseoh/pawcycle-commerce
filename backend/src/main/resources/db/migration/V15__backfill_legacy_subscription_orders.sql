INSERT INTO orders (order_number, member_id, source, status, original_amount, discount_amount, shipping_fee, payment_amount, created_at)
SELECT CONCAT('LEGACY-SUB-', legacy.id), legacy.member_id, 'SUBSCRIPTION', 'CREATED',
       legacy.package_total_krw, 0, 0, legacy.package_total_krw, legacy.processed_at
FROM subscription_orders legacy;

INSERT INTO subscription_order_context (order_id, subscription_id, schedule_id, effective_snapshot_id, source_plan_version_id, scheduled_date)
SELECT common_order.id, legacy.subscription_id, legacy.schedule_id, legacy.effective_snapshot_id,
       legacy.source_plan_version_id, legacy.scheduled_date
FROM subscription_orders legacy
JOIN orders common_order ON common_order.order_number = CONCAT('LEGACY-SUB-', legacy.id);

INSERT INTO order_items (order_id, sku_id, snapshot_quality, quantity)
SELECT context.order_id, legacy_item.sku_id, 'LEGACY_PARTIAL', legacy_item.quantity
FROM subscription_order_items legacy_item
JOIN subscription_orders legacy ON legacy.id = legacy_item.order_id
JOIN subscription_order_context context ON context.schedule_id = legacy.schedule_id;
