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
CREATE TEMPORARY TABLE qa_interaction_verify_guard (ok TINYINT NOT NULL CHECK (ok = 1));
SELECT id INTO @member_id FROM members WHERE email LIKE 'qa-foundation-004@%' LIMIT 1;
INSERT INTO qa_interaction_verify_guard SELECT CASE WHEN @member_id IS NOT NULL THEN 1 ELSE 0 END;

INSERT INTO qa_interaction_verify_guard SELECT CASE WHEN (
    SELECT COUNT(*) FROM subscriptions WHERE member_id = @member_id AND status = 'ACTIVE'
) = 2 THEN 1 ELSE 0 END;
INSERT INTO qa_interaction_verify_guard SELECT CASE WHEN (
    SELECT COUNT(*) FROM pets
    WHERE member_id = @member_id AND name = 'MVP4 QA CAT' AND pet_type = 'CAT'
      AND breed IS NULL AND weight_kg IS NULL
) = 1 THEN 1 ELSE 0 END;
INSERT INTO qa_interaction_verify_guard SELECT CASE WHEN (
    SELECT COUNT(*) FROM pets
    WHERE member_id = @member_id AND name = 'MVP4 QA DOG' AND pet_type = 'DOG'
      AND breed = 'Pomeranian' AND weight_kg = 4.20
) = 1 THEN 1 ELSE 0 END;
INSERT INTO qa_interaction_verify_guard SELECT CASE WHEN (
    SELECT COUNT(*) FROM orders WHERE member_id = @member_id AND order_number LIKE 'MVP4-QA-004-%'
) = 6 THEN 1 ELSE 0 END;

INSERT INTO qa_interaction_verify_guard SELECT CASE WHEN (
    SELECT COUNT(*) FROM interaction_events WHERE member_id = @member_id AND event_type = 'PRODUCT_IMPRESSION'
) > 0 THEN 1 ELSE 0 END;
INSERT INTO qa_interaction_verify_guard SELECT CASE WHEN (
    SELECT COUNT(*) FROM interaction_events WHERE member_id = @member_id AND event_type = 'PRODUCT_VIEW'
) >= 2 THEN 1 ELSE 0 END;
INSERT INTO qa_interaction_verify_guard SELECT CASE WHEN (
    SELECT COUNT(*) FROM interaction_events WHERE member_id = @member_id AND event_type = 'SEARCH'
) > 0 THEN 1 ELSE 0 END;
INSERT INTO qa_interaction_verify_guard SELECT CASE WHEN (
    SELECT COUNT(*) FROM interaction_events WHERE member_id = @member_id AND event_type = 'FILTER'
) > 0 THEN 1 ELSE 0 END;
INSERT INTO qa_interaction_verify_guard SELECT CASE WHEN (
    SELECT COUNT(*) FROM interaction_events WHERE member_id = @member_id AND event_type = 'RECOMMENDATION_IMPRESSION'
) > 0 THEN 1 ELSE 0 END;
INSERT INTO qa_interaction_verify_guard SELECT CASE WHEN (
    SELECT COUNT(*) FROM interaction_events WHERE member_id = @member_id AND event_type = 'RECOMMENDATION_CLICK'
) > 0 THEN 1 ELSE 0 END;

INSERT INTO qa_interaction_verify_guard SELECT CASE WHEN (
    SELECT COUNT(*) FROM interaction_events event
    WHERE event.member_id = @member_id
      AND event.event_type = 'SEARCH'
      AND JSON_EXTRACT(event.context, '$.hasTextQuery') = TRUE
      AND JSON_CONTAINS_PATH(event.context, 'one', '$.petType', '$.sort')
) > 0 THEN 1 ELSE 0 END;
INSERT INTO qa_interaction_verify_guard SELECT CASE WHEN NOT EXISTS (
    SELECT 1 FROM interaction_events event
    WHERE event.member_id = @member_id
      AND event.event_type IN ('SEARCH', 'FILTER')
      AND JSON_CONTAINS_PATH(event.context, 'one', '$.q', '$.query', '$.rawQuery', '$.searchText')
) THEN 1 ELSE 0 END;
INSERT INTO qa_interaction_verify_guard SELECT CASE WHEN (
    SELECT COUNT(*) FROM interaction_events event
    WHERE event.member_id = @member_id
      AND event.event_id REGEXP '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$'
) = (
    SELECT COUNT(*) FROM interaction_events event WHERE event.member_id = @member_id
) THEN 1 ELSE 0 END;
INSERT INTO qa_interaction_verify_guard SELECT CASE WHEN NOT EXISTS (
    SELECT 1 FROM interaction_events event
    WHERE event.member_id = @member_id
      AND event.event_type IN ('RECOMMENDATION_IMPRESSION', 'RECOMMENDATION_CLICK')
      AND (event.recommendation_request_id IS NULL OR event.product_id IS NULL)
) THEN 1 ELSE 0 END;
INSERT INTO qa_interaction_verify_guard SELECT CASE WHEN EXISTS (
    SELECT 1 FROM interaction_events click
    JOIN interaction_events impression
      ON impression.member_id = click.member_id
     AND impression.event_type = 'RECOMMENDATION_IMPRESSION'
     AND impression.recommendation_request_id = click.recommendation_request_id
     AND impression.product_id = click.product_id
    WHERE click.member_id = @member_id AND click.event_type = 'RECOMMENDATION_CLICK'
) THEN 1 ELSE 0 END;
INSERT INTO qa_interaction_verify_guard SELECT CASE WHEN EXISTS (
    SELECT 1 FROM interaction_events event
    JOIN pets pet ON pet.id = event.pet_id AND pet.member_id = event.member_id
    WHERE event.member_id = @member_id
      AND event.event_type = 'RECOMMENDATION_IMPRESSION'
      AND pet.pet_type = 'DOG'
) THEN 1 ELSE 0 END;
INSERT INTO qa_interaction_verify_guard SELECT CASE WHEN (
    SELECT COUNT(*) FROM subscriptions subscription
    JOIN subscription_schedules schedule ON schedule.subscription_id = subscription.id
    WHERE subscription.id = (SELECT MIN(id) FROM subscriptions WHERE member_id = @member_id AND status = 'ACTIVE')
      AND subscription.delivery_cycle_weeks = 4 AND schedule.status = 'SCHEDULED'
      AND schedule.scheduled_date = DATE_ADD(DATE(UTC_TIMESTAMP() + INTERVAL 9 HOUR), INTERVAL 2 DAY)
      AND NOT EXISTS (SELECT 1 FROM subscription_schedule_addons addon WHERE addon.schedule_id = schedule.id)
) = 1 THEN 1 ELSE 0 END;
INSERT INTO qa_interaction_verify_guard SELECT CASE WHEN (
    SELECT COUNT(*) FROM subscriptions subscription
    JOIN subscription_schedules schedule ON schedule.subscription_id = subscription.id
    JOIN subscription_schedule_addons addon ON addon.schedule_id = schedule.id
    WHERE subscription.id = (SELECT MAX(id) FROM subscriptions WHERE member_id = @member_id AND status = 'ACTIVE')
      AND schedule.status = 'HELD' AND schedule.hold_reason = 'ORDER_STOCK_UNAVAILABLE'
      AND schedule.scheduled_date = DATE_ADD(DATE(UTC_TIMESTAMP() + INTERVAL 9 HOUR), INTERVAL 3 DAY)
      AND addon.quantity = 1
) = 1 THEN 1 ELSE 0 END;
INSERT INTO qa_interaction_verify_guard SELECT CASE WHEN (
    SELECT COUNT(*) FROM notifications notification
    JOIN subscription_schedules schedule ON schedule.id = notification.reference_id
    WHERE notification.member_id = @member_id AND notification.type = 'SUBSCRIPTION_DELIVERY_REMINDER'
      AND notification.reference_type = 'SCHEDULE'
      AND schedule.subscription_id = (SELECT MIN(id) FROM subscriptions WHERE member_id = @member_id AND status = 'ACTIVE')
      AND schedule.scheduled_date = DATE_ADD(DATE(UTC_TIMESTAMP() + INTERVAL 9 HOUR), INTERVAL 2 DAY)
) = 1 THEN 1 ELSE 0 END;

SELECT 'INTERACTIONS_VERIFIED',
       (SELECT COUNT(*) FROM interaction_events WHERE member_id = @member_id AND event_type = 'PRODUCT_IMPRESSION'),
       (SELECT COUNT(*) FROM interaction_events WHERE member_id = @member_id AND event_type = 'PRODUCT_VIEW'),
       (SELECT COUNT(*) FROM interaction_events WHERE member_id = @member_id AND event_type = 'SEARCH'),
       (SELECT COUNT(*) FROM interaction_events WHERE member_id = @member_id AND event_type = 'FILTER'),
       (SELECT COUNT(*) FROM interaction_events WHERE member_id = @member_id AND event_type = 'RECOMMENDATION_IMPRESSION'),
       (SELECT COUNT(*) FROM interaction_events WHERE member_id = @member_id AND event_type = 'RECOMMENDATION_CLICK');
'@

$sql | docker exec -i $mysql sh -lc 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=TCP -uroot --database="$MYSQL_DATABASE" --batch --skip-column-names'
if ($LASTEXITCODE -ne 0) {
    throw 'Final Product interaction verification failed'
}
