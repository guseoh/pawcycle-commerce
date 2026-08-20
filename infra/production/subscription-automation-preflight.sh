#!/usr/bin/env bash

set -Eeuo pipefail

case "$-" in
  *x*) set +x ;;
esac

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=infra/production/release-common.sh
source "$SCRIPT_DIR/release-common.sh"

usage() {
  cat <<'EOF'
Usage: subscription-automation-preflight.sh --backend-image <ghcr-repository> --frontend-image <ghcr-repository> --expect-bundle-enabled <true|false> [options]

Options:
  --expect-running-enabled <true|false|any>  Expected value in the running Backend (default: any)
  --max-due-candidates <count>              Fail if the aggregate due candidate count is larger
  --runtime-dir <path>                      Materialized runtime bundle root (default: /opt/pawcycle/runtime)
  --state-dir <path>                        Release state directory (default: /opt/pawcycle/state)

This command is read-only. It prints aggregate schema, candidate, invariant, and
metric values only; it never prints customer, subscription, Schedule, or Order IDs.
EOF
}

BACKEND_IMAGE=""
FRONTEND_IMAGE=""
EXPECTED_BUNDLE_ENABLED=""
EXPECTED_RUNNING_ENABLED="any"
MAX_DUE_CANDIDATES=""
PAWCYCLE_RUNTIME_DIR="/opt/pawcycle/runtime"
PAWCYCLE_STATE_DIR="/opt/pawcycle/state"

while (( $# > 0 )); do
  case "$1" in
    --backend-image) BACKEND_IMAGE="${2:-}"; shift 2 ;;
    --frontend-image) FRONTEND_IMAGE="${2:-}"; shift 2 ;;
    --expect-bundle-enabled) EXPECTED_BUNDLE_ENABLED="${2:-}"; shift 2 ;;
    --expect-running-enabled) EXPECTED_RUNNING_ENABLED="${2:-}"; shift 2 ;;
    --max-due-candidates) MAX_DUE_CANDIDATES="${2:-}"; shift 2 ;;
    --runtime-dir) PAWCYCLE_RUNTIME_DIR="${2:-}"; shift 2 ;;
    --state-dir) PAWCYCLE_STATE_DIR="${2:-}"; shift 2 ;;
    --help) usage; exit 0 ;;
    *) usage >&2; die "unknown argument: $1" ;;
  esac
done

[[ "$EXPECTED_BUNDLE_ENABLED" == "true" || "$EXPECTED_BUNDLE_ENABLED" == "false" ]] \
  || die "--expect-bundle-enabled must be exactly true or false"
[[ "$EXPECTED_RUNNING_ENABLED" == "true" \
  || "$EXPECTED_RUNNING_ENABLED" == "false" \
  || "$EXPECTED_RUNNING_ENABLED" == "any" ]] \
  || die "--expect-running-enabled must be true, false, or any"
if [[ -n "$MAX_DUE_CANDIDATES" ]]; then
  [[ "$MAX_DUE_CANDIDATES" =~ ^(0|[1-9][0-9]*)$ ]] \
    || die "--max-due-candidates must be a non-negative integer"
fi

prepare_read_only_release_context
load_active_mysql_volume
CURRENT_SHA="$(read_state_sha current-sha)"
load_runtime_contract
require_subscription_automation_mode "$EXPECTED_BUNDLE_ENABLED"

ACTIVE_SHA="$CURRENT_SHA"
export ACTIVE_SHA
verify_running_release || die "running Production release identity, health, or MySQL volume does not match protected state"

MYSQL_CONTAINER="$(compose ps --quiet mysql)"
BACKEND_CONTAINER="$(compose ps --quiet backend)"
[[ -n "$MYSQL_CONTAINER" && -n "$BACKEND_CONTAINER" ]] \
  || die "running Production MySQL and Backend containers are required"

running_setting() {
  local key="$1"
  local value

  if ! value="$(docker exec "$BACKEND_CONTAINER" printenv "$key" 2>/dev/null)"; then
    die "running Backend automation setting is missing: $key"
  fi
  printf '%s\n' "$value"
}

RUNNING_ENABLED="$(running_setting PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED)"
RUNNING_BATCH_SIZE="$(running_setting PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE)"
RUNNING_FIXED_DELAY_MS="$(running_setting PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS)"
[[ "$RUNNING_ENABLED" == "true" || "$RUNNING_ENABLED" == "false" ]] \
  || die "running Backend automation enabled value is invalid"
[[ "$RUNNING_BATCH_SIZE" =~ ^[1-9][0-9]*$ ]] \
  || die "running Backend automation batch size is invalid"
[[ "$RUNNING_FIXED_DELAY_MS" =~ ^[1-9][0-9]*$ ]] \
  || die "running Backend automation fixed delay is invalid"
if [[ "$EXPECTED_RUNNING_ENABLED" != "any" ]]; then
  [[ "$RUNNING_ENABLED" == "$EXPECTED_RUNNING_ENABLED" ]] \
    || die "running Backend automation mode does not match the expected transition state"
fi

printf 'BUNDLE_AUTOMATION_ENABLED=%s\n' "$PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED"
printf 'BUNDLE_AUTOMATION_BATCH_SIZE=%s\n' "$PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE"
printf 'BUNDLE_AUTOMATION_FIXED_DELAY_MS=%s\n' "$PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS"
printf 'RUNNING_AUTOMATION_ENABLED=%s\n' "$RUNNING_ENABLED"
printf 'RUNNING_AUTOMATION_BATCH_SIZE=%s\n' "$RUNNING_BATCH_SIZE"
printf 'RUNNING_AUTOMATION_FIXED_DELAY_MS=%s\n' "$RUNNING_FIXED_DELAY_MS"

run_mysql_read_only() {
  local sql="$1"
  local result
  local mysql_database=""
  local mysql_user=""
  local mysql_password=""

  if [[ "$PAWCYCLE_DATASOURCE_HOST" == "mysql" && "$PAWCYCLE_DATASOURCE_SSL_MODE" == "DISABLED" ]]; then
    DATABASE_PREFLIGHT_TARGET="DOCKER_MYSQL"
    if ! result="$(printf '%s\n' "$sql" | docker exec --interactive "$MYSQL_CONTAINER" sh -c \
      'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysql --protocol=TCP --host=127.0.0.1 --user="$MYSQL_USER" --database="$MYSQL_DATABASE" --batch --skip-column-names --raw' \
      2>/dev/null)"; then
      die "read-only Production Docker MySQL preflight failed; raw database output was suppressed"
    fi
  elif [[ "$PAWCYCLE_DATASOURCE_HOST" != "mysql" && "$PAWCYCLE_DATASOURCE_SSL_MODE" == "REQUIRED" ]]; then
    DATABASE_PREFLIGHT_TARGET="RDS"
    read_runtime_setting "$PAWCYCLE_RUNTIME_DIR/current/mysql.env" MYSQL_DATABASE mysql_database
    read_runtime_setting "$PAWCYCLE_RUNTIME_DIR/current/mysql.env" MYSQL_USER mysql_user
    read_runtime_setting "$PAWCYCLE_RUNTIME_DIR/current/mysql.env" MYSQL_PASSWORD mysql_password
    if ! result="$(printf '%s\n' "$sql" | MYSQL_PWD="$mysql_password" docker run --rm --pull never \
      --network "container:$BACKEND_CONTAINER" \
      --read-only --tmpfs /tmp:size=16m,mode=1777 \
      --security-opt no-new-privileges:true --cap-drop ALL \
      --env MYSQL_PWD --entrypoint mysql "$MYSQL_IMAGE" \
      --protocol=TCP --host="$PAWCYCLE_DATASOURCE_HOST" --port="$PAWCYCLE_DATASOURCE_PORT" \
      --user="$mysql_user" --database="$mysql_database" --ssl-mode=REQUIRED \
      --batch --skip-column-names --raw 2>/dev/null)"; then
      die "read-only Production RDS preflight failed; raw database output was suppressed"
    fi
  else
    die "active datasource runtime is not approved for subscription automation preflight"
  fi
  printf '%s\n' "$result"
}

SCHEMA_SQL=$(cat <<'SQL'
SELECT CONCAT('FLYWAY_V9=', IF(COUNT(*) = 1 AND MIN(success) = 1, 'SUCCESS', 'NOT_SUCCESS')) FROM flyway_schema_history WHERE version = '9';
SELECT CONCAT('FLYWAY_V10=', IF(COUNT(*) = 1 AND MIN(success) = 1, 'SUCCESS', 'NOT_SUCCESS')) FROM flyway_schema_history WHERE version = '10';
SELECT CONCAT('FLYWAY_V11=', IF(COUNT(*) = 1 AND MIN(success) = 1, 'SUCCESS', 'NOT_SUCCESS')) FROM flyway_schema_history WHERE version = '11';
SELECT CONCAT('TABLE_SUBSCRIPTION_ORDERS=', IF(COUNT(*) = 1, 'PRESENT', 'MISSING')) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription_orders';
SELECT CONCAT('TABLE_SUBSCRIPTION_ORDER_ITEMS=', IF(COUNT(*) = 1, 'PRESENT', 'MISSING')) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription_order_items';
SELECT CONCAT('UNIQUE_SCHEDULE_ORDER=', IF(COUNT(*) = 1, 'PRESENT', 'MISSING')) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription_orders' AND CONSTRAINT_NAME = 'uk_subscription_orders_schedule' AND CONSTRAINT_TYPE = 'UNIQUE';
SELECT CONCAT('DUE_INDEX=', IF(COALESCE(GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX), '') = 'status,scheduled_date,id,subscription_id', 'PRESENT', 'MISSING')) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription_schedules' AND INDEX_NAME = 'idx_schedules_due_automation';
SQL
)
SCHEMA_RESULT="$(run_mysql_read_only "$SCHEMA_SQL")"
printf 'DATABASE_PREFLIGHT_TARGET=%s\n' "$DATABASE_PREFLIGHT_TARGET"
printf '%s\n' "$SCHEMA_RESULT"
for expected in \
  FLYWAY_V9=SUCCESS \
  FLYWAY_V10=SUCCESS \
  FLYWAY_V11=SUCCESS \
  TABLE_SUBSCRIPTION_ORDERS=PRESENT \
  TABLE_SUBSCRIPTION_ORDER_ITEMS=PRESENT \
  UNIQUE_SCHEDULE_ORDER=PRESENT \
  DUE_INDEX=PRESENT; do
  grep -Fxq "$expected" <<< "$SCHEMA_RESULT" \
    || die "V9-V11 Flyway or required schema contract is incomplete; Scheduler activation is blocked"
done

DATA_SQL=$(cat <<'SQL'
SELECT CONCAT('DUE_CANDIDATE_COUNT=', COUNT(*)) FROM (
  SELECT schedule.scheduled_date
  FROM subscription_schedules schedule
  JOIN subscriptions subscription ON subscription.id = schedule.subscription_id
  LEFT JOIN subscription_orders existing_order ON existing_order.schedule_id = schedule.id
  WHERE subscription.mvp2_managed = true
    AND subscription.status = 'ACTIVE'
    AND schedule.status = 'SCHEDULED'
    AND schedule.scheduled_date <= DATE(CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+09:00'))
    AND existing_order.id IS NULL
    AND NOT EXISTS (
      SELECT 1
      FROM subscription_schedules earlier
      LEFT JOIN subscription_orders earlier_order ON earlier_order.schedule_id = earlier.id
      WHERE earlier.subscription_id = schedule.subscription_id
        AND earlier.status = 'SCHEDULED'
        AND earlier.scheduled_date <= DATE(CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+09:00'))
        AND earlier_order.id IS NULL
        AND (earlier.scheduled_date < schedule.scheduled_date
          OR (earlier.scheduled_date = schedule.scheduled_date AND earlier.id < schedule.id))
    )
) candidates;
SELECT CONCAT('OLDEST_DUE_DATE=', COALESCE(DATE_FORMAT(MIN(scheduled_date), '%Y-%m-%d'), 'NONE')) FROM (
  SELECT schedule.scheduled_date
  FROM subscription_schedules schedule
  JOIN subscriptions subscription ON subscription.id = schedule.subscription_id
  LEFT JOIN subscription_orders existing_order ON existing_order.schedule_id = schedule.id
  WHERE subscription.mvp2_managed = true
    AND subscription.status = 'ACTIVE'
    AND schedule.status = 'SCHEDULED'
    AND schedule.scheduled_date <= DATE(CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+09:00'))
    AND existing_order.id IS NULL
    AND NOT EXISTS (
      SELECT 1
      FROM subscription_schedules earlier
      LEFT JOIN subscription_orders earlier_order ON earlier_order.schedule_id = earlier.id
      WHERE earlier.subscription_id = schedule.subscription_id
        AND earlier.status = 'SCHEDULED'
        AND earlier.scheduled_date <= DATE(CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+09:00'))
        AND earlier_order.id IS NULL
        AND (earlier.scheduled_date < schedule.scheduled_date
          OR (earlier.scheduled_date = schedule.scheduled_date AND earlier.id < schedule.id))
    )
) candidates;
SELECT CONCAT('DUPLICATE_ORDER_SCHEDULE_GROUPS=', COUNT(*)) FROM (SELECT schedule_id FROM subscription_orders GROUP BY schedule_id HAVING COUNT(*) > 1) duplicates;
SELECT CONCAT('ORDERLESS_ADVANCED_SCHEDULES=', COUNT(*)) FROM subscription_schedules schedule LEFT JOIN subscription_orders orders ON orders.schedule_id = schedule.id WHERE schedule.effective_snapshot_id IS NOT NULL AND orders.id IS NULL;
SELECT CONCAT('ORDER_SNAPSHOT_CARDINALITY_ANOMALIES=', COUNT(DISTINCT orders.id))
FROM subscription_orders orders
JOIN subscription_schedules schedule ON schedule.id = orders.schedule_id
WHERE schedule.subscription_id <> orders.subscription_id
  OR schedule.effective_snapshot_id IS NULL
  OR schedule.effective_snapshot_id <> orders.effective_snapshot_id
  OR EXISTS (
    SELECT 1 FROM subscription_snapshot_items snapshot_item
    LEFT JOIN subscription_order_items order_item ON order_item.order_id = orders.id AND order_item.sku_id = snapshot_item.sku_id
    WHERE snapshot_item.snapshot_id = orders.effective_snapshot_id
      AND (order_item.sku_id IS NULL OR order_item.quantity <> snapshot_item.quantity)
  )
  OR EXISTS (
    SELECT 1 FROM subscription_order_items order_item
    LEFT JOIN subscription_snapshot_items snapshot_item ON snapshot_item.snapshot_id = orders.effective_snapshot_id AND snapshot_item.sku_id = order_item.sku_id
    WHERE order_item.order_id = orders.id AND snapshot_item.sku_id IS NULL
  );
SELECT CONCAT('PROCESSED_ACTIVE_FUTURE_SCHEDULE_ANOMALIES=', COUNT(*)) FROM (
  SELECT subscription.id
  FROM subscriptions subscription
  LEFT JOIN subscription_schedules future_schedule ON future_schedule.subscription_id = subscription.id
    AND future_schedule.status = 'SCHEDULED'
    AND future_schedule.scheduled_date > DATE(CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+09:00'))
  LEFT JOIN subscription_orders future_order ON future_order.schedule_id = future_schedule.id
  WHERE subscription.mvp2_managed = true
    AND subscription.status = 'ACTIVE'
    AND EXISTS (SELECT 1 FROM subscription_orders processed_order WHERE processed_order.subscription_id = subscription.id)
  GROUP BY subscription.id
  HAVING SUM(CASE WHEN future_schedule.id IS NOT NULL AND future_order.id IS NULL THEN 1 ELSE 0 END) <> 1
) anomalies;
SQL
)
DATA_RESULT="$(run_mysql_read_only "$DATA_SQL")"
printf '%s\n' "$DATA_RESULT"

DUE_CANDIDATE_COUNT="$(sed -n 's/^DUE_CANDIDATE_COUNT=//p' <<< "$DATA_RESULT")"
[[ "$DUE_CANDIDATE_COUNT" =~ ^(0|[1-9][0-9]*)$ ]] \
  || die "aggregate due candidate count is invalid"
if [[ -n "$MAX_DUE_CANDIDATES" && "$DUE_CANDIDATE_COUNT" -gt "$MAX_DUE_CANDIDATES" ]]; then
  die "due candidate count exceeds the explicitly approved activation maximum"
fi
for invariant in \
  DUPLICATE_ORDER_SCHEDULE_GROUPS \
  ORDERLESS_ADVANCED_SCHEDULES \
  ORDER_SNAPSHOT_CARDINALITY_ANOMALIES \
  PROCESSED_ACTIVE_FUTURE_SCHEDULE_ANOMALIES; do
  grep -Fxq "${invariant}=0" <<< "$DATA_RESULT" \
    || die "subscription automation aggregate invariant failed: $invariant"
done

if ! METRICS="$(docker exec "$BACKEND_CONTAINER" curl --fail --silent --show-error \
  http://127.0.0.1:8080/actuator/prometheus 2>/dev/null)"; then
  die "Backend automation aggregate metrics could not be read; raw output was suppressed"
fi

metric_value() {
  local name="$1"
  local value

  value="$(awk -v metric="$name" '$1 == metric { print $2; exit }' <<< "$METRICS")"
  [[ "$value" =~ ^[0-9]+([.][0-9]+)?$ ]] || die "required aggregate metric is missing: $name"
  printf '%s\n' "$value"
}

printf 'AUTOMATION_EXECUTIONS_TOTAL=%s\n' "$(metric_value pawcycle_subscription_automation_executions_total)"
printf 'AUTOMATION_PROCESSED_CANDIDATES_TOTAL=%s\n' "$(metric_value pawcycle_subscription_automation_processed_candidates_total)"
printf 'AUTOMATION_ORDERS_CREATED_TOTAL=%s\n' "$(metric_value pawcycle_subscription_automation_orders_total)"
printf 'AUTOMATION_FAILURES_TOTAL=%s\n' "$(metric_value pawcycle_subscription_automation_failures_total)"
printf 'AUTOMATION_DUPLICATE_NOOP_TOTAL=%s\n' "$(metric_value pawcycle_subscription_automation_duplicate_noop_total)"
printf 'SUBSCRIPTION_AUTOMATION_PREFLIGHT=PASS\n'
