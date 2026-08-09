START TRANSACTION;

SET @member_id := (SELECT id FROM members ORDER BY id LIMIT 1);
SET @sku_id := (SELECT id FROM skus ORDER BY id LIMIT 1);
SET @plan_version_id := (
    SELECT current_plan_version_id
    FROM subscription_plans
    WHERE current_plan_version_id IS NOT NULL
    ORDER BY id
    LIMIT 1
);

INSERT INTO pets(member_id, name, pet_type)
VALUES (@member_id, 'INC-BASE-001', 'DOG');
SET @pet_id := LAST_INSERT_ID();

INSERT INTO subscriptions(
    member_id,
    sku_id,
    quantity,
    delivery_cycle_weeks,
    created_date,
    next_order_date,
    pet_id,
    status,
    version,
    current_snapshot_id,
    legacy_api_visible,
    mvp2_managed
) VALUES (
    @member_id,
    @sku_id,
    1,
    2,
    CURRENT_DATE,
    CURRENT_DATE + INTERVAL 14 DAY,
    @pet_id,
    'ACTIVE',
    0,
    NULL,
    FALSE,
    TRUE
);
SET @subscription_id := LAST_INSERT_ID();

INSERT INTO subscription_snapshots(
    subscription_id,
    source_plan_version_id,
    package_total_krw,
    delivery_cycle_weeks
)
SELECT @subscription_id, id, package_price_krw, 2
FROM plan_versions
WHERE id = @plan_version_id;
SET @snapshot_id := LAST_INSERT_ID();

INSERT INTO subscription_snapshot_items(snapshot_id, sku_id, quantity)
SELECT @snapshot_id, sku_id, quantity
FROM plan_items
WHERE plan_version_id = @plan_version_id;

UPDATE subscriptions
SET current_snapshot_id = @snapshot_id
WHERE id = @subscription_id;

INSERT INTO subscription_schedules(
    subscription_id,
    scheduled_date,
    status,
    effective_snapshot_id
) VALUES (
    @subscription_id,
    CURRENT_DATE + INTERVAL 14 DAY,
    'SCHEDULED',
    NULL
);

COMMIT;

SELECT CONCAT('FIXTURE_READY:SUBSCRIPTION_ID=', @subscription_id);
