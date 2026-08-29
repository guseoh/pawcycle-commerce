-- MVP4-QA-004 local-only fixture seed.
-- Apply only to pawcycle-mvp4-final-qa-mysql-data through the verification script.
SET @qa_today = DATE(UTC_TIMESTAMP() + INTERVAL 9 HOUR);

CREATE TEMPORARY TABLE qa_seed_guard (
    ok TINYINT NOT NULL CHECK (ok = 1)
);

INSERT INTO qa_seed_guard
SELECT CASE WHEN (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'members', 'pets', 'products', 'skus', 'inventories',
          'subscription_plans', 'plan_versions', 'plan_items',
          'plan_version_delivery_cycles', 'subscriptions', 'subscription_snapshots',
          'subscription_snapshot_items', 'subscription_schedules',
          'subscription_schedule_addons', 'subscription_orders', 'subscription_order_items',
          'subscription_order_context', 'orders', 'order_items', 'payments',
          'member_addresses', 'billing_payment_methods', 'subscription_shipping_snapshots',
          'notifications', 'categories', 'brands'
      )
) = 26 THEN 1 ELSE 0 END;

INSERT INTO qa_seed_guard
SELECT CASE WHEN (
    SELECT COUNT(*) FROM members WHERE email LIKE 'qa-foundation-004@%'
) = 1 THEN 1 ELSE 0 END;

INSERT INTO qa_seed_guard
SELECT CASE WHEN NOT EXISTS (
    SELECT 1 FROM pets
    WHERE member_id IN (SELECT id FROM members WHERE email LIKE 'qa-foundation-004@%')
) THEN 1 ELSE 0 END;

INSERT INTO qa_seed_guard
SELECT CASE WHEN NOT EXISTS (
    SELECT 1 FROM subscriptions
    WHERE member_id IN (SELECT id FROM members WHERE email LIKE 'qa-foundation-004@%')
) THEN 1 ELSE 0 END;

INSERT INTO qa_seed_guard
SELECT CASE WHEN NOT EXISTS (
    SELECT 1 FROM orders WHERE order_number LIKE 'MVP4-QA-004-%'
) THEN 1 ELSE 0 END;

INSERT INTO qa_seed_guard
SELECT CASE WHEN EXISTS (
    SELECT 1
    FROM products p
    JOIN categories c ON c.id = p.category_id AND c.active = TRUE
    JOIN brands b ON b.id = p.brand_id AND b.active = TRUE
    JOIN skus s ON s.product_id = p.id AND s.status = 'ACTIVE' AND s.subscribable = TRUE
    JOIN inventories i ON i.sku_id = s.id AND i.available_quantity > 0
    JOIN plan_items pi ON pi.sku_id = s.id
    JOIN plan_versions v ON v.id = pi.plan_version_id AND v.is_migration_only = FALSE
    JOIN subscription_plans plan ON plan.id = v.plan_id AND plan.current_plan_version_id = v.id
    WHERE p.display_status = 'PUBLIC'
      AND p.pet_type = 'DOG'
      AND plan.target_pet_type = 'DOG'
      AND plan.on_sale = TRUE
      AND (plan.sale_starts_on IS NULL OR plan.sale_starts_on <= @qa_today)
      AND (plan.sale_ends_on IS NULL OR plan.sale_ends_on >= @qa_today)
      AND EXISTS (SELECT 1 FROM plan_version_delivery_cycles c2 WHERE c2.plan_version_id = v.id AND c2.delivery_cycle_weeks = 2)
      AND EXISTS (SELECT 1 FROM plan_version_delivery_cycles c4 WHERE c4.plan_version_id = v.id AND c4.delivery_cycle_weeks = 4)
) THEN 1 ELSE 0 END;

SELECT
    p.id, s.id, plan.id, v.id, p.name, s.name, s.sku_code, s.price, plan.name, v.package_price_krw
INTO
    @base_product_id, @base_sku_id, @base_plan_id, @base_plan_version_id,
    @base_product_name, @base_sku_name, @base_sku_code, @base_sku_price, @base_plan_name, @base_package_price
FROM products p
JOIN categories c ON c.id = p.category_id AND c.active = TRUE
JOIN brands b ON b.id = p.brand_id AND b.active = TRUE
JOIN skus s ON s.product_id = p.id AND s.status = 'ACTIVE' AND s.subscribable = TRUE
JOIN inventories i ON i.sku_id = s.id AND i.available_quantity > 0
JOIN plan_items pi ON pi.sku_id = s.id
JOIN plan_versions v ON v.id = pi.plan_version_id AND v.is_migration_only = FALSE
JOIN subscription_plans plan ON plan.id = v.plan_id AND plan.current_plan_version_id = v.id
WHERE p.display_status = 'PUBLIC'
  AND p.pet_type = 'DOG'
  AND plan.target_pet_type = 'DOG'
  AND plan.on_sale = TRUE
  AND (plan.sale_starts_on IS NULL OR plan.sale_starts_on <= @qa_today)
  AND (plan.sale_ends_on IS NULL OR plan.sale_ends_on >= @qa_today)
  AND EXISTS (SELECT 1 FROM plan_version_delivery_cycles c2 WHERE c2.plan_version_id = v.id AND c2.delivery_cycle_weeks = 2)
  AND EXISTS (SELECT 1 FROM plan_version_delivery_cycles c4 WHERE c4.plan_version_id = v.id AND c4.delivery_cycle_weeks = 4)
ORDER BY p.id, s.id, plan.id
LIMIT 1;

INSERT INTO qa_seed_guard
SELECT CASE WHEN EXISTS (
    SELECT 1
    FROM products p
    JOIN categories c ON c.id = p.category_id AND c.active = TRUE
    JOIN brands b ON b.id = p.brand_id AND b.active = TRUE
    JOIN skus s ON s.product_id = p.id AND s.status = 'ACTIVE'
    JOIN inventories i ON i.sku_id = s.id AND i.available_quantity >= 10
    WHERE p.display_status = 'PUBLIC'
      AND p.id <> @base_product_id
      AND NOT EXISTS (
          SELECT 1
          FROM plan_items pi
          JOIN plan_versions v ON v.id = pi.plan_version_id
          JOIN subscription_plans plan ON plan.id = v.plan_id AND plan.current_plan_version_id = v.id
          WHERE pi.sku_id = s.id AND plan.on_sale = TRUE
      )
) THEN 1 ELSE 0 END;

SELECT p.id, s.id, p.name, s.name, s.sku_code, s.price
INTO @addon_product_id, @addon_sku_id, @addon_product_name, @addon_sku_name, @addon_sku_code, @addon_sku_price
FROM products p
JOIN categories c ON c.id = p.category_id AND c.active = TRUE
JOIN brands b ON b.id = p.brand_id AND b.active = TRUE
JOIN skus s ON s.product_id = p.id AND s.status = 'ACTIVE'
JOIN inventories i ON i.sku_id = s.id AND i.available_quantity >= 10
WHERE p.display_status = 'PUBLIC'
  AND p.id <> @base_product_id
  AND NOT EXISTS (
      SELECT 1
      FROM plan_items pi
      JOIN plan_versions v ON v.id = pi.plan_version_id
      JOIN subscription_plans plan ON plan.id = v.plan_id AND plan.current_plan_version_id = v.id
      WHERE pi.sku_id = s.id AND plan.on_sale = TRUE
  )
ORDER BY p.id, s.id
LIMIT 1;

SELECT id INTO @member_id FROM members WHERE email LIKE 'qa-foundation-004@%' LIMIT 1;

START TRANSACTION;

INSERT INTO pets(member_id, name, pet_type, breed, weight_kg)
VALUES (@member_id, 'MVP4 QA DOG', 'DOG', 'Pomeranian', 4.20);
SET @dog_pet_id = LAST_INSERT_ID();

INSERT INTO member_addresses(member_id, name, recipient_name, recipient_phone, postal_code, address_line1, address_line2, created_at, updated_at)
VALUES (@member_id, 'MVP4 QA address', 'MVP4 QA recipient', '01000000000', '06236', 'MVP4 QA local address', NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
SET @address_id = LAST_INSERT_ID();
UPDATE members SET default_address_id = @address_id WHERE id = @member_id;

INSERT INTO billing_payment_methods(member_id, provider, customer_key, billing_key, status, created_at)
VALUES (@member_id, 'TOSS', 'qa-mvp4-final-004-customer', 'qa-mvp4-final-004-billing', 'ACTIVE', UTC_TIMESTAMP(6));

INSERT INTO subscriptions(member_id, sku_id, quantity, delivery_cycle_weeks, created_date, next_order_date, pet_id, status, version, current_snapshot_id, legacy_api_visible, mvp2_managed)
VALUES (@member_id, @base_sku_id, 1, 4, DATE_SUB(@qa_today, INTERVAL 60 DAY), DATE_ADD(@qa_today, INTERVAL 2 DAY), @dog_pet_id, 'ACTIVE', 0, NULL, TRUE, TRUE);
SET @normal_subscription_id = LAST_INSERT_ID();

INSERT INTO subscription_snapshots(subscription_id, source_plan_version_id, package_total_krw, delivery_cycle_weeks)
VALUES (@normal_subscription_id, @base_plan_version_id, @base_package_price, 4);
SET @normal_snapshot_id = LAST_INSERT_ID();
INSERT INTO subscription_snapshot_items(snapshot_id, sku_id, quantity)
SELECT @normal_snapshot_id, sku_id, quantity FROM plan_items WHERE plan_version_id = @base_plan_version_id;
UPDATE subscriptions SET current_snapshot_id = @normal_snapshot_id WHERE id = @normal_subscription_id;
INSERT INTO subscription_shipping_snapshots(subscription_id, recipient_name, recipient_phone, postal_code, address_line1, address_line2, updated_at)
SELECT @normal_subscription_id, recipient_name, recipient_phone, postal_code, address_line1, address_line2, UTC_TIMESTAMP(6)
FROM member_addresses WHERE id = @address_id;

INSERT INTO subscription_schedules(subscription_id, scheduled_date, status, effective_snapshot_id)
VALUES (@normal_subscription_id, DATE_ADD(@qa_today, INTERVAL 2 DAY), 'SCHEDULED', NULL);
SET @normal_schedule_id = LAST_INSERT_ID();

INSERT INTO subscriptions(member_id, sku_id, quantity, delivery_cycle_weeks, created_date, next_order_date, pet_id, status, version, current_snapshot_id, legacy_api_visible, mvp2_managed)
VALUES (@member_id, @base_sku_id, 1, 4, DATE_SUB(@qa_today, INTERVAL 60 DAY), DATE_ADD(@qa_today, INTERVAL 3 DAY), @dog_pet_id, 'ACTIVE', 0, NULL, TRUE, TRUE);
SET @held_subscription_id = LAST_INSERT_ID();

INSERT INTO subscription_snapshots(subscription_id, source_plan_version_id, package_total_krw, delivery_cycle_weeks)
VALUES (@held_subscription_id, @base_plan_version_id, @base_package_price, 4);
SET @held_snapshot_id = LAST_INSERT_ID();
INSERT INTO subscription_snapshot_items(snapshot_id, sku_id, quantity)
SELECT @held_snapshot_id, sku_id, quantity FROM plan_items WHERE plan_version_id = @base_plan_version_id;
UPDATE subscriptions SET current_snapshot_id = @held_snapshot_id WHERE id = @held_subscription_id;
INSERT INTO subscription_shipping_snapshots(subscription_id, recipient_name, recipient_phone, postal_code, address_line1, address_line2, updated_at)
SELECT @held_subscription_id, recipient_name, recipient_phone, postal_code, address_line1, address_line2, UTC_TIMESTAMP(6)
FROM member_addresses WHERE id = @address_id;
INSERT INTO subscription_schedules(subscription_id, scheduled_date, status, hold_reason, effective_snapshot_id)
VALUES (@held_subscription_id, DATE_ADD(@qa_today, INTERVAL 3 DAY), 'HELD', 'ORDER_STOCK_UNAVAILABLE', NULL);
SET @held_schedule_id = LAST_INSERT_ID();
INSERT INTO subscription_schedule_addons(schedule_id, sku_id, quantity, unit_price_krw, created_at, updated_at)
VALUES (@held_schedule_id, @addon_sku_id, 1, @addon_sku_price, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO notifications(member_id, type, reference_type, reference_id, created_at)
VALUES (@member_id, 'SUBSCRIPTION_DELIVERY_REMINDER', 'SCHEDULE', @normal_schedule_id, UTC_TIMESTAMP(6));

SET @one_time_date_42 = DATE_SUB(@qa_today, INTERVAL 42 DAY);
SET @one_time_date_28 = DATE_SUB(@qa_today, INTERVAL 28 DAY);
SET @one_time_date_14 = DATE_SUB(@qa_today, INTERVAL 14 DAY);

INSERT INTO orders(order_number, member_id, source, status, original_amount, discount_amount, shipping_fee, payment_amount, created_at, paid_at)
VALUES ('MVP4-QA-004-OT-42', @member_id, 'ONE_TIME', 'PAID', @base_sku_price, 0, 0, @base_sku_price, TIMESTAMP(@one_time_date_42, '12:00:00'), TIMESTAMP(@one_time_date_42, '12:00:00'));
SET @one_time_order_42 = LAST_INSERT_ID();
INSERT INTO order_items(order_id, sku_id, snapshot_quality, sku_code_snapshot, product_name_snapshot, sku_name_snapshot, unit_price, quantity, line_amount)
VALUES (@one_time_order_42, @base_sku_id, 'FULL', @base_sku_code, @base_product_name, @base_sku_name, @base_sku_price, 1, @base_sku_price);
INSERT INTO payments(order_id, type, provider, status, provider_status, amount, provider_order_id, payment_key, idempotency_key, attempt_no, requested_at, approved_at, created_at)
VALUES (@one_time_order_42, 'NORMAL', 'TOSS', 'SUCCEEDED', 'DONE', @base_sku_price, 'qa-mvp4-final-004-provider-ot-42', 'qa-mvp4-final-004-payment-ot-42', 'qa-mvp4-final-004-idem-ot-42', 1, TIMESTAMP(@one_time_date_42, '12:00:00'), TIMESTAMP(@one_time_date_42, '12:00:00'), TIMESTAMP(@one_time_date_42, '12:00:00'));

INSERT INTO orders(order_number, member_id, source, status, original_amount, discount_amount, shipping_fee, payment_amount, created_at, paid_at)
VALUES ('MVP4-QA-004-OT-28', @member_id, 'ONE_TIME', 'PAID', @base_sku_price, 0, 0, @base_sku_price, TIMESTAMP(@one_time_date_28, '12:00:00'), TIMESTAMP(@one_time_date_28, '12:00:00'));
SET @one_time_order_28 = LAST_INSERT_ID();
INSERT INTO order_items(order_id, sku_id, snapshot_quality, sku_code_snapshot, product_name_snapshot, sku_name_snapshot, unit_price, quantity, line_amount)
VALUES (@one_time_order_28, @base_sku_id, 'FULL', @base_sku_code, @base_product_name, @base_sku_name, @base_sku_price, 1, @base_sku_price);
INSERT INTO payments(order_id, type, provider, status, provider_status, amount, provider_order_id, payment_key, idempotency_key, attempt_no, requested_at, approved_at, created_at)
VALUES (@one_time_order_28, 'NORMAL', 'TOSS', 'SUCCEEDED', 'DONE', @base_sku_price, 'qa-mvp4-final-004-provider-ot-28', 'qa-mvp4-final-004-payment-ot-28', 'qa-mvp4-final-004-idem-ot-28', 1, TIMESTAMP(@one_time_date_28, '12:00:00'), TIMESTAMP(@one_time_date_28, '12:00:00'), TIMESTAMP(@one_time_date_28, '12:00:00'));

INSERT INTO orders(order_number, member_id, source, status, original_amount, discount_amount, shipping_fee, payment_amount, created_at, paid_at)
VALUES ('MVP4-QA-004-OT-14', @member_id, 'ONE_TIME', 'PAID', @base_sku_price, 0, 0, @base_sku_price, TIMESTAMP(@one_time_date_14, '12:00:00'), TIMESTAMP(@one_time_date_14, '12:00:00'));
SET @one_time_order_14 = LAST_INSERT_ID();
INSERT INTO order_items(order_id, sku_id, snapshot_quality, sku_code_snapshot, product_name_snapshot, sku_name_snapshot, unit_price, quantity, line_amount)
VALUES (@one_time_order_14, @base_sku_id, 'FULL', @base_sku_code, @base_product_name, @base_sku_name, @base_sku_price, 1, @base_sku_price);
INSERT INTO payments(order_id, type, provider, status, provider_status, amount, provider_order_id, payment_key, idempotency_key, attempt_no, requested_at, approved_at, created_at)
VALUES (@one_time_order_14, 'NORMAL', 'TOSS', 'SUCCEEDED', 'DONE', @base_sku_price, 'qa-mvp4-final-004-provider-ot-14', 'qa-mvp4-final-004-payment-ot-14', 'qa-mvp4-final-004-idem-ot-14', 1, TIMESTAMP(@one_time_date_14, '12:00:00'), TIMESTAMP(@one_time_date_14, '12:00:00'), TIMESTAMP(@one_time_date_14, '12:00:00'));

SET @sub_date_42 = DATE_SUB(@qa_today, INTERVAL 42 DAY);
SET @sub_date_28 = DATE_SUB(@qa_today, INTERVAL 28 DAY);
SET @sub_date_14 = DATE_SUB(@qa_today, INTERVAL 14 DAY);

INSERT INTO subscription_schedules(subscription_id, scheduled_date, status, effective_snapshot_id)
VALUES (@normal_subscription_id, @sub_date_42, 'SCHEDULED', @normal_snapshot_id);
SET @sub_schedule_42 = LAST_INSERT_ID();
INSERT INTO subscription_schedules(subscription_id, scheduled_date, status, effective_snapshot_id)
VALUES (@normal_subscription_id, @sub_date_28, 'SCHEDULED', @normal_snapshot_id);
SET @sub_schedule_28 = LAST_INSERT_ID();
INSERT INTO subscription_schedules(subscription_id, scheduled_date, status, effective_snapshot_id)
VALUES (@normal_subscription_id, @sub_date_14, 'SCHEDULED', @normal_snapshot_id);
SET @sub_schedule_14 = LAST_INSERT_ID();

INSERT INTO orders(order_number, member_id, source, status, original_amount, discount_amount, shipping_fee, payment_amount, created_at, paid_at)
VALUES ('MVP4-QA-004-SUB-42', @member_id, 'SUBSCRIPTION', 'PAID', @base_package_price, 0, 0, @base_package_price, TIMESTAMP(@sub_date_42, '13:00:00'), TIMESTAMP(@sub_date_42, '13:00:00'));
SET @sub_order_42 = LAST_INSERT_ID();
INSERT INTO subscription_order_context(order_id, subscription_id, schedule_id, effective_snapshot_id, source_plan_version_id, scheduled_date)
VALUES (@sub_order_42, @normal_subscription_id, @sub_schedule_42, @normal_snapshot_id, @base_plan_version_id, @sub_date_42);
INSERT INTO order_items(order_id, sku_id, snapshot_quality, sku_code_snapshot, product_name_snapshot, sku_name_snapshot, unit_price, quantity, line_amount)
SELECT @sub_order_42, s.id, 'FULL', s.sku_code, p.name, s.name, s.price, item.quantity, s.price * item.quantity
FROM subscription_snapshot_items item JOIN skus s ON s.id = item.sku_id JOIN products p ON p.id = s.product_id
WHERE item.snapshot_id = @normal_snapshot_id;
INSERT INTO payments(order_id, type, provider, status, provider_status, amount, provider_order_id, payment_key, idempotency_key, attempt_no, requested_at, approved_at, created_at)
VALUES (@sub_order_42, 'BILLING', 'TOSS', 'SUCCEEDED', 'DONE', @base_package_price, 'qa-mvp4-final-004-provider-sub-42', 'qa-mvp4-final-004-payment-sub-42', 'qa-mvp4-final-004-idem-sub-42', 1, TIMESTAMP(@sub_date_42, '13:00:00'), TIMESTAMP(@sub_date_42, '13:00:00'), TIMESTAMP(@sub_date_42, '13:00:00'));
INSERT INTO subscription_orders(member_id, subscription_id, schedule_id, effective_snapshot_id, source_plan_version_id, scheduled_date, processed_at, package_total_krw, status)
VALUES (@member_id, @normal_subscription_id, @sub_schedule_42, @normal_snapshot_id, @base_plan_version_id, @sub_date_42, TIMESTAMP(@sub_date_42, '13:00:00'), @base_package_price, 'CREATED');
SET @legacy_sub_order_42 = LAST_INSERT_ID();
INSERT INTO subscription_order_items(order_id, sku_id, quantity)
SELECT @legacy_sub_order_42, sku_id, quantity FROM subscription_snapshot_items WHERE snapshot_id = @normal_snapshot_id;

INSERT INTO orders(order_number, member_id, source, status, original_amount, discount_amount, shipping_fee, payment_amount, created_at, paid_at)
VALUES ('MVP4-QA-004-SUB-28', @member_id, 'SUBSCRIPTION', 'PAID', @base_package_price, 0, 0, @base_package_price, TIMESTAMP(@sub_date_28, '13:00:00'), TIMESTAMP(@sub_date_28, '13:00:00'));
SET @sub_order_28 = LAST_INSERT_ID();
INSERT INTO subscription_order_context(order_id, subscription_id, schedule_id, effective_snapshot_id, source_plan_version_id, scheduled_date)
VALUES (@sub_order_28, @normal_subscription_id, @sub_schedule_28, @normal_snapshot_id, @base_plan_version_id, @sub_date_28);
INSERT INTO order_items(order_id, sku_id, snapshot_quality, sku_code_snapshot, product_name_snapshot, sku_name_snapshot, unit_price, quantity, line_amount)
SELECT @sub_order_28, s.id, 'FULL', s.sku_code, p.name, s.name, s.price, item.quantity, s.price * item.quantity
FROM subscription_snapshot_items item JOIN skus s ON s.id = item.sku_id JOIN products p ON p.id = s.product_id
WHERE item.snapshot_id = @normal_snapshot_id;
INSERT INTO payments(order_id, type, provider, status, provider_status, amount, provider_order_id, payment_key, idempotency_key, attempt_no, requested_at, approved_at, created_at)
VALUES (@sub_order_28, 'BILLING', 'TOSS', 'SUCCEEDED', 'DONE', @base_package_price, 'qa-mvp4-final-004-provider-sub-28', 'qa-mvp4-final-004-payment-sub-28', 'qa-mvp4-final-004-idem-sub-28', 1, TIMESTAMP(@sub_date_28, '13:00:00'), TIMESTAMP(@sub_date_28, '13:00:00'), TIMESTAMP(@sub_date_28, '13:00:00'));
INSERT INTO subscription_orders(member_id, subscription_id, schedule_id, effective_snapshot_id, source_plan_version_id, scheduled_date, processed_at, package_total_krw, status)
VALUES (@member_id, @normal_subscription_id, @sub_schedule_28, @normal_snapshot_id, @base_plan_version_id, @sub_date_28, TIMESTAMP(@sub_date_28, '13:00:00'), @base_package_price, 'CREATED');
SET @legacy_sub_order_28 = LAST_INSERT_ID();
INSERT INTO subscription_order_items(order_id, sku_id, quantity)
SELECT @legacy_sub_order_28, sku_id, quantity FROM subscription_snapshot_items WHERE snapshot_id = @normal_snapshot_id;

INSERT INTO orders(order_number, member_id, source, status, original_amount, discount_amount, shipping_fee, payment_amount, created_at, paid_at)
VALUES ('MVP4-QA-004-SUB-14', @member_id, 'SUBSCRIPTION', 'PAID', @base_package_price, 0, 0, @base_package_price, TIMESTAMP(@sub_date_14, '13:00:00'), TIMESTAMP(@sub_date_14, '13:00:00'));
SET @sub_order_14 = LAST_INSERT_ID();
INSERT INTO subscription_order_context(order_id, subscription_id, schedule_id, effective_snapshot_id, source_plan_version_id, scheduled_date)
VALUES (@sub_order_14, @normal_subscription_id, @sub_schedule_14, @normal_snapshot_id, @base_plan_version_id, @sub_date_14);
INSERT INTO order_items(order_id, sku_id, snapshot_quality, sku_code_snapshot, product_name_snapshot, sku_name_snapshot, unit_price, quantity, line_amount)
SELECT @sub_order_14, s.id, 'FULL', s.sku_code, p.name, s.name, s.price, item.quantity, s.price * item.quantity
FROM subscription_snapshot_items item JOIN skus s ON s.id = item.sku_id JOIN products p ON p.id = s.product_id
WHERE item.snapshot_id = @normal_snapshot_id;
INSERT INTO payments(order_id, type, provider, status, provider_status, amount, provider_order_id, payment_key, idempotency_key, attempt_no, requested_at, approved_at, created_at)
VALUES (@sub_order_14, 'BILLING', 'TOSS', 'SUCCEEDED', 'DONE', @base_package_price, 'qa-mvp4-final-004-provider-sub-14', 'qa-mvp4-final-004-payment-sub-14', 'qa-mvp4-final-004-idem-sub-14', 1, TIMESTAMP(@sub_date_14, '13:00:00'), TIMESTAMP(@sub_date_14, '13:00:00'), TIMESTAMP(@sub_date_14, '13:00:00'));
INSERT INTO subscription_orders(member_id, subscription_id, schedule_id, effective_snapshot_id, source_plan_version_id, scheduled_date, processed_at, package_total_krw, status)
VALUES (@member_id, @normal_subscription_id, @sub_schedule_14, @normal_snapshot_id, @base_plan_version_id, @sub_date_14, TIMESTAMP(@sub_date_14, '13:00:00'), @base_package_price, 'CREATED');
SET @legacy_sub_order_14 = LAST_INSERT_ID();
INSERT INTO subscription_order_items(order_id, sku_id, quantity)
SELECT @legacy_sub_order_14, sku_id, quantity FROM subscription_snapshot_items WHERE snapshot_id = @normal_snapshot_id;

COMMIT;

SELECT 'FIXTURE_IDS', @member_id, @dog_pet_id, @base_product_id, @base_sku_id, @base_plan_id, @base_plan_version_id, @addon_product_id, @addon_sku_id, @normal_subscription_id, @normal_schedule_id, @held_subscription_id, @held_schedule_id;
SELECT 'FIXTURE_DATES', @qa_today, DATE_ADD(@qa_today, INTERVAL 2 DAY), DATE_ADD(@qa_today, INTERVAL 3 DAY), @one_time_date_42, @one_time_date_28, @one_time_date_14;
