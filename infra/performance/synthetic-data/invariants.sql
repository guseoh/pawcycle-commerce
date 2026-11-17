-- PawCycle domain invariants for performance datasets.
-- Each statement returns one row: <check_name> <TAB> <violation_count>. Every count must be 0.
-- The checks are written against current domain rules, not against the generator,
-- so they can be re-run after a write load (Checkout, payment, subscription automation).
-- Scope notes are next to each check. Read-only: SELECT statements only.

-- Orders and payments (ONE_TIME orders created by Checkout) ---------------------------------

-- original_amount = sum of FULL snapshot line amounts.
SELECT 'order_original_amount_matches_items', COUNT(*) FROM orders o
WHERE o.source = 'ONE_TIME'
  AND NOT EXISTS (SELECT 1 FROM order_items x WHERE x.order_id = o.id AND x.snapshot_quality <> 'FULL')
  AND o.original_amount <> (SELECT COALESCE(SUM(i.line_amount), -1) FROM order_items i WHERE i.order_id = o.id);

SELECT 'order_item_line_amount', COUNT(*) FROM order_items
WHERE snapshot_quality = 'FULL' AND line_amount <> unit_price * quantity;

-- Checkout creates exactly one NORMAL payment per ONE_TIME order.
SELECT 'one_time_order_has_single_normal_payment', COUNT(*) FROM orders o
WHERE o.source = 'ONE_TIME'
  AND (SELECT COUNT(*) FROM payments p WHERE p.order_id = o.id AND p.type = 'NORMAL') <> 1;

SELECT 'normal_payment_amount_matches_order', COUNT(*) FROM payments p JOIN orders o ON o.id = p.order_id
WHERE p.type = 'NORMAL' AND p.amount <> o.payment_amount;

-- Allowed (order.status, NORMAL payment.status) pairs; see docs/domain/state-machines.md §1.
SELECT 'order_payment_state_combination', COUNT(*) FROM orders o JOIN payments p ON p.order_id = o.id AND p.type = 'NORMAL'
WHERE o.source = 'ONE_TIME' AND NOT (
     (o.status = 'PAYMENT_PENDING' AND p.status IN ('READY', 'PROCESSING', 'UNKNOWN'))
  OR (o.status = 'PAID' AND p.status = 'SUCCEEDED')
  OR (o.status = 'PAYMENT_FAILED' AND p.status = 'FAILED')
  OR (o.status = 'EXPIRED' AND p.status = 'FAILED' AND p.provider_status = 'EXPIRED')
  OR (o.status = 'PAYMENT_ACTION_REQUIRED' AND p.status IN ('UNKNOWN', 'PROCESSING')));

SELECT 'paid_order_has_paid_at', COUNT(*) FROM orders WHERE status = 'PAID' AND paid_at IS NULL;

SELECT 'succeeded_payment_has_approval', COUNT(*) FROM payments
WHERE status = 'SUCCEEDED' AND (approved_at IS NULL OR payment_key IS NULL);

-- Payment success creates the delivery in the same transaction.
SELECT 'paid_one_time_order_has_delivery', COUNT(*) FROM orders o
WHERE o.source = 'ONE_TIME' AND o.status = 'PAID' AND NOT EXISTS (SELECT 1 FROM deliveries d WHERE d.order_id = o.id);

SELECT 'delivery_only_for_paid_order', COUNT(*) FROM deliveries d JOIN orders o ON o.id = d.order_id
WHERE o.status <> 'PAID';

SELECT 'checkout_idempotency_points_to_own_order', COUNT(*) FROM checkout_idempotency_results r
JOIN orders o ON o.id = r.order_id JOIN payments p ON p.id = r.payment_id
WHERE o.member_id <> r.member_id OR p.order_id <> r.order_id;

-- Inventory ------------------------------------------------------------------------------------
-- Ledger checks apply to SKUs whose history starts with an INBOUND movement from zero
-- (all synthetic SKUs). Inventory rows seeded without a ledger origin are skipped.

SELECT 'inventory_ledger_origin_from_zero', COUNT(*) FROM inventory_movements m
WHERE m.type = 'INBOUND' AND m.id = (SELECT MIN(f.id) FROM inventory_movements f WHERE f.sku_id = m.sku_id)
  AND (m.available_before <> 0 OR m.reserved_before <> 0);

SELECT 'inventory_matches_ledger', COUNT(*) FROM inventories i
JOIN (SELECT sku_id, SUM(available_after - available_before) available_sum,
             SUM(reserved_after - reserved_before) reserved_sum
      FROM inventory_movements GROUP BY sku_id) l ON l.sku_id = i.sku_id
WHERE EXISTS (SELECT 1 FROM inventory_movements o WHERE o.sku_id = i.sku_id AND o.type = 'INBOUND')
  AND (i.available_quantity <> l.available_sum OR i.reserved_quantity <> l.reserved_sum);

SELECT 'inventory_movement_chain_continuity', COUNT(*) FROM (
  SELECT sku_id, available_before, reserved_before,
         LAG(available_after) OVER (PARTITION BY sku_id ORDER BY id) prev_available,
         LAG(reserved_after) OVER (PARTITION BY sku_id ORDER BY id) prev_reserved
  FROM inventory_movements
  WHERE sku_id IN (SELECT sku_id FROM inventory_movements WHERE type = 'INBOUND')) c
WHERE c.prev_available IS NOT NULL
  AND (c.available_before <> c.prev_available OR c.reserved_before <> c.prev_reserved);

SELECT 'inventory_movement_delta_matches_type', COUNT(*) FROM inventory_movements
WHERE NOT (
     (type IN ('INBOUND', 'CANCEL_RESTORE', 'RETURN_RESTORE') AND available_after - available_before = quantity AND reserved_after = reserved_before)
  OR (type = 'RESERVE' AND available_after - available_before = -quantity AND reserved_after - reserved_before = quantity)
  OR (type = 'RELEASE' AND available_after - available_before = quantity AND reserved_after - reserved_before = -quantity)
  OR (type = 'DEDUCT' AND available_after = available_before AND reserved_after - reserved_before = -quantity)
  OR (type = 'ADMIN_ADJUST' AND ABS(available_after - available_before) = quantity AND reserved_after = reserved_before));

-- A NORMAL payment's reservation ends exactly as the payment ended:
-- SUCCEEDED -> RESERVE + DEDUCT, FAILED -> RESERVE + RELEASE, still open -> RESERVE only.
SELECT 'normal_payment_reservation_outcome', COUNT(*) FROM (
  SELECT p.id, p.status, m.sku_id,
         SUM(m.type = 'RESERVE') reserves, SUM(m.type = 'DEDUCT') deducts, SUM(m.type = 'RELEASE') releases
  FROM payments p JOIN inventory_movements m ON m.payment_id = p.id
  WHERE p.type = 'NORMAL' GROUP BY p.id, p.status, m.sku_id) r
WHERE NOT (
     (r.status = 'SUCCEEDED' AND r.reserves = 1 AND r.deducts = 1 AND r.releases = 0)
  OR (r.status = 'FAILED' AND r.reserves = 1 AND r.deducts = 0 AND r.releases = 1)
  OR (r.status IN ('READY', 'PROCESSING', 'UNKNOWN') AND r.reserves = 1 AND r.deducts = 0 AND r.releases = 0));

-- Every ONE_TIME order line of an order that reached payment has a reservation for that SKU.
SELECT 'one_time_order_line_reserved', COUNT(*) FROM order_items i
JOIN orders o ON o.id = i.order_id AND o.source = 'ONE_TIME'
JOIN payments p ON p.order_id = o.id AND p.type = 'NORMAL'
WHERE NOT EXISTS (SELECT 1 FROM inventory_movements m
                  WHERE m.payment_id = p.id AND m.sku_id = i.sku_id AND m.type = 'RESERVE' AND m.quantity = i.quantity);

-- Reviews ----------------------------------------------------------------------------------------
-- Review creation requires a DELIVERED purchase of the product (ProductEngagementPersistence).
SELECT 'review_requires_delivered_purchase', COUNT(*) FROM reviews r
WHERE NOT EXISTS (SELECT 1 FROM orders o JOIN order_items oi ON oi.order_id = o.id
                  JOIN skus s ON s.id = oi.sku_id JOIN deliveries d ON d.order_id = o.id AND d.status = 'DELIVERED'
                  WHERE o.member_id = r.member_id AND s.product_id = r.product_id);

-- Subscriptions (runtime-managed) ------------------------------------------------------------------

SELECT 'subscription_current_snapshot_owned', COUNT(*) FROM subscriptions s
LEFT JOIN subscription_snapshots ss ON ss.id = s.current_snapshot_id
WHERE s.runtime_managed = 1 AND (ss.id IS NULL OR ss.subscription_id <> s.id);

SELECT 'subscription_snapshot_cycle_offered_by_plan', COUNT(*) FROM subscriptions s
JOIN subscription_snapshots ss ON ss.id = s.current_snapshot_id
WHERE s.runtime_managed = 1 AND NOT EXISTS (
  SELECT 1 FROM plan_version_delivery_cycles c
  WHERE c.plan_version_id = ss.source_plan_version_id AND c.delivery_cycle_weeks = ss.delivery_cycle_weeks);

SELECT 'subscription_sku_subscribable', COUNT(*) FROM subscriptions s JOIN skus k ON k.id = s.sku_id
WHERE k.subscribable = 0;

-- An open schedule is SCHEDULED/HELD and not yet turned into a subscription order.
SELECT 'canceled_subscription_has_open_schedule', COUNT(*) FROM subscriptions s
JOIN subscription_schedules sc ON sc.subscription_id = s.id
WHERE s.status = 'CANCELED' AND sc.status IN ('SCHEDULED', 'HELD')
  AND NOT EXISTS (SELECT 1 FROM subscription_orders so WHERE so.schedule_id = sc.id)
  AND NOT EXISTS (SELECT 1 FROM subscription_order_context oc WHERE oc.schedule_id = sc.id);

SELECT 'paused_subscription_has_open_scheduled', COUNT(*) FROM subscriptions s
JOIN subscription_schedules sc ON sc.subscription_id = s.id
WHERE s.status = 'PAUSED' AND sc.status = 'SCHEDULED'
  AND NOT EXISTS (SELECT 1 FROM subscription_orders so WHERE so.schedule_id = sc.id)
  AND NOT EXISTS (SELECT 1 FROM subscription_order_context oc WHERE oc.schedule_id = sc.id);

SELECT 'active_runtime_subscription_has_open_schedule', COUNT(*) FROM subscriptions s
WHERE s.status = 'ACTIVE' AND s.runtime_managed = 1 AND NOT EXISTS (
  SELECT 1 FROM subscription_schedules sc WHERE sc.subscription_id = s.id AND sc.status IN ('SCHEDULED', 'HELD')
    AND NOT EXISTS (SELECT 1 FROM subscription_orders so WHERE so.schedule_id = sc.id)
    AND NOT EXISTS (SELECT 1 FROM subscription_order_context oc WHERE oc.schedule_id = sc.id));
