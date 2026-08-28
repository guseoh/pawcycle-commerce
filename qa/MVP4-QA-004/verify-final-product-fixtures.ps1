[CmdletBinding()]
param(
    [string] $EnvFile
)

$ErrorActionPreference = 'Stop'
$project = 'pawcycle-mvp4-final-qa'

if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $EnvFile = Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) 'infra/local-integration/.env.local'
}
if (-not (Test-Path -LiteralPath $EnvFile -PathType Leaf)) {
    throw "Local env input is required but was not found: $EnvFile"
}

$baseCompose = (Resolve-Path (Join-Path $PSScriptRoot '../../infra/local-integration/compose.yaml')).Path
$qaCompose = (Resolve-Path (Join-Path $PSScriptRoot 'compose.final-product-qa.yaml')).Path
$composeArgs = @('-p', $project, '--env-file', $EnvFile, '-f', $baseCompose, '-f', $qaCompose)
$mysql = docker compose @composeArgs ps -q mysql
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($mysql)) {
    throw 'MySQL container lookup failed'
}

$sql = @'
SET @qa_today = DATE(UTC_TIMESTAMP() + INTERVAL 9 HOUR);
CREATE TEMPORARY TABLE qa_verify_guard (ok TINYINT NOT NULL CHECK (ok = 1));

INSERT INTO qa_verify_guard SELECT CASE WHEN (SELECT COUNT(*) FROM members WHERE email LIKE 'qa-foundation-004@%') = 1 THEN 1 ELSE 0 END;
INSERT INTO qa_verify_guard SELECT CASE WHEN (SELECT COUNT(*) FROM pets WHERE member_id IN (SELECT id FROM members WHERE email LIKE 'qa-foundation-004@%') AND name = 'MVP4 QA DOG' AND pet_type = 'DOG' AND breed = 'Pomeranian' AND weight_kg = 4.20) = 1 THEN 1 ELSE 0 END;
INSERT INTO qa_verify_guard SELECT CASE WHEN (SELECT COUNT(*) FROM pets WHERE member_id IN (SELECT id FROM members WHERE email LIKE 'qa-foundation-004@%') AND pet_type = 'CAT') = 0 THEN 1 ELSE 0 END;
INSERT INTO qa_verify_guard SELECT CASE WHEN (SELECT COUNT(*) FROM orders WHERE order_number LIKE 'MVP4-QA-004-%') = 6 THEN 1 ELSE 0 END;

SELECT id INTO @member_id FROM members WHERE email LIKE 'qa-foundation-004@%' LIMIT 1;
SELECT id INTO @dog_pet_id FROM pets WHERE member_id = @member_id AND name = 'MVP4 QA DOG' AND pet_type = 'DOG';
SELECT oi.sku_id, sku.product_id INTO @base_sku_id, @base_product_id
FROM order_items oi JOIN orders o ON o.id = oi.order_id JOIN skus sku ON sku.id = oi.sku_id
WHERE o.order_number = 'MVP4-QA-004-OT-42' LIMIT 1;
SELECT snapshot.source_plan_version_id INTO @base_plan_version_id
FROM subscriptions subscription JOIN subscription_snapshots snapshot ON snapshot.id = subscription.current_snapshot_id
WHERE subscription.member_id = @member_id AND subscription.pet_id = @dog_pet_id AND subscription.status = 'ACTIVE'
ORDER BY subscription.id LIMIT 1;

INSERT INTO qa_verify_guard SELECT CASE WHEN (
    SELECT COUNT(*) FROM subscriptions subscription
    JOIN subscription_schedules schedule ON schedule.subscription_id = subscription.id
    WHERE subscription.id = (SELECT MIN(id) FROM subscriptions WHERE member_id = @member_id AND status = 'ACTIVE')
      AND subscription.mvp2_managed = TRUE AND subscription.delivery_cycle_weeks = 4 AND schedule.status = 'SCHEDULED'
      AND schedule.scheduled_date = DATE_ADD(@qa_today, INTERVAL 2 DAY)
      AND NOT EXISTS (SELECT 1 FROM subscription_schedule_addons addon WHERE addon.schedule_id = schedule.id)
) = 1 THEN 1 ELSE 0 END;
INSERT INTO qa_verify_guard SELECT CASE WHEN (
    SELECT COUNT(*) FROM subscriptions subscription
    JOIN subscription_schedules schedule ON schedule.subscription_id = subscription.id
    JOIN subscription_schedule_addons addon ON addon.schedule_id = schedule.id
    WHERE subscription.member_id = @member_id AND subscription.status = 'ACTIVE'
      AND schedule.status = 'HELD' AND schedule.hold_reason = 'ORDER_STOCK_UNAVAILABLE'
      AND schedule.scheduled_date = DATE_ADD(@qa_today, INTERVAL 3 DAY)
      AND addon.quantity = 1
) = 1 THEN 1 ELSE 0 END;
INSERT INTO qa_verify_guard SELECT CASE WHEN (
    SELECT COUNT(*) FROM orders o JOIN payments p ON p.order_id = o.id AND p.status = 'SUCCEEDED'
    JOIN order_items item ON item.order_id = o.id AND item.sku_id = @base_sku_id
    WHERE o.member_id = @member_id AND o.source = 'ONE_TIME' AND o.status = 'PAID' AND o.paid_at IS NOT NULL
      AND o.order_number IN ('MVP4-QA-004-OT-42','MVP4-QA-004-OT-28','MVP4-QA-004-OT-14')
) = 3 THEN 1 ELSE 0 END;
INSERT INTO qa_verify_guard SELECT CASE WHEN (
    SELECT COUNT(*) FROM orders o JOIN payments p ON p.order_id = o.id AND p.status = 'SUCCEEDED'
    JOIN subscription_order_context context ON context.order_id = o.id
    WHERE context.subscription_id = (SELECT MIN(id) FROM subscriptions WHERE member_id = @member_id AND status = 'ACTIVE')
      AND o.source = 'SUBSCRIPTION' AND o.status = 'PAID'
      AND context.scheduled_date IN (DATE_SUB(@qa_today, INTERVAL 42 DAY), DATE_SUB(@qa_today, INTERVAL 28 DAY), DATE_SUB(@qa_today, INTERVAL 14 DAY))
) = 3 THEN 1 ELSE 0 END;
INSERT INTO qa_verify_guard SELECT CASE WHEN (
    SELECT COUNT(*) FROM notifications notification
    JOIN subscription_schedules schedule ON schedule.id = notification.reference_id
    WHERE notification.member_id = @member_id AND notification.type = 'SUBSCRIPTION_DELIVERY_REMINDER'
      AND notification.reference_type = 'SCHEDULE' AND schedule.subscription_id = (SELECT MIN(id) FROM subscriptions WHERE member_id = @member_id AND status = 'ACTIVE')
) = 1 THEN 1 ELSE 0 END;
INSERT INTO qa_verify_guard SELECT CASE WHEN (
    SELECT COUNT(*) FROM plan_version_delivery_cycles WHERE plan_version_id = @base_plan_version_id AND delivery_cycle_weeks IN (2,4)
) = 2 THEN 1 ELSE 0 END;
INSERT INTO qa_verify_guard SELECT CASE WHEN (
    SELECT COUNT(*) FROM products p JOIN categories c ON c.id = p.category_id AND c.active = TRUE JOIN brands b ON b.id = p.brand_id AND b.active = TRUE
    JOIN skus sku ON sku.product_id = p.id JOIN inventories i ON i.sku_id = sku.id
    WHERE p.id = @base_product_id AND p.display_status = 'PUBLIC' AND p.pet_type = 'DOG' AND sku.id = @base_sku_id AND sku.status = 'ACTIVE' AND i.available_quantity > 0
) = 1 THEN 1 ELSE 0 END;

SELECT 'FIXTURES_VERIFIED', @member_id, @dog_pet_id, @base_product_id, @base_sku_id, @base_plan_version_id,
       (SELECT MIN(id) FROM subscriptions WHERE member_id = @member_id AND status = 'ACTIVE'),
       (SELECT MIN(schedule.id) FROM subscription_schedules schedule JOIN subscriptions subscription ON subscription.id = schedule.subscription_id WHERE subscription.member_id = @member_id AND schedule.status = 'SCHEDULED' AND schedule.scheduled_date = DATE_ADD(@qa_today, INTERVAL 2 DAY)),
       (SELECT MIN(subscription.id) FROM subscriptions subscription JOIN subscription_schedules schedule ON schedule.subscription_id = subscription.id WHERE subscription.member_id = @member_id AND schedule.status = 'HELD' AND schedule.hold_reason = 'ORDER_STOCK_UNAVAILABLE'),
       @qa_today;
'@

$sql | docker exec -i $mysql sh -lc 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=TCP -uroot --database="$MYSQL_DATABASE" --batch --skip-column-names'
if ($LASTEXITCODE -ne 0) {
    throw 'Final Product fixture verification failed'
}
