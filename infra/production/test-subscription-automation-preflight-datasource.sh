#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf -- "$TEST_ROOT"' EXIT

BIN_DIR="$TEST_ROOT/bin"
MARKER_DIR="$TEST_ROOT/markers"
mkdir -p "$BIN_DIR" "$MARKER_DIR"

APP_SHA="1111111111111111111111111111111111111111"
CONTROL_SHA="2222222222222222222222222222222222222222"
BACKEND_IMAGE="ghcr.io/example/pawcycle-commerce-backend"
FRONTEND_IMAGE="ghcr.io/example/pawcycle-commerce-frontend"
MYSQL_IMAGE="mysql:8.4.10@sha256:c592c15aaf4a1961e15d82eb31ea5987dda862d1c4b1e93424438c0e91dc1f8d"
PROXY_IMAGE="nginx:1.30.3-alpine3.23@sha256:0d3b80406a13a767339fbe2f41406d6c7da727ab89cf8fae399e81f780f814d1"
RDS_HOST="pawcycle-test.abcdefghijkl.ap-northeast-2.rds.amazonaws.com"

export APP_SHA CONTROL_SHA BACKEND_IMAGE FRONTEND_IMAGE MYSQL_IMAGE PROXY_IMAGE MARKER_DIR RDS_HOST

cat > "$BIN_DIR/git" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
if [[ "${1:-}" == "-C" ]]; then
  shift 2
fi
case "${1:-}" in
  status) exit 0 ;;
  rev-parse) printf '%s\n' "$CONTROL_SHA" ;;
  cat-file) exit 0 ;;
  diff) exit 0 ;;
  *) exit 0 ;;
esac
EOF

cat > "$BIN_DIR/docker" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail

emit_sql_result() {
  local sql="$1"
  local due_count="$2"
  if [[ "$sql" == *"TABLE_SUBSCRIPTION_ORDERS"* ]]; then
    cat <<'SCHEMA'
FLYWAY_V9=SUCCESS
FLYWAY_V10=SUCCESS
FLYWAY_V11=SUCCESS
TABLE_SUBSCRIPTION_ORDERS=PRESENT
TABLE_SUBSCRIPTION_ORDER_ITEMS=PRESENT
UNIQUE_SCHEDULE_ORDER=PRESENT
DUE_INDEX=PRESENT
SCHEMA
  else
    cat <<DATA
DUE_CANDIDATE_COUNT=$due_count
OLDEST_DUE_DATE=2026-08-01
DUPLICATE_ORDER_SCHEDULE_GROUPS=0
ORDERLESS_ADVANCED_SCHEDULES=0
ORDER_SNAPSHOT_CARDINALITY_ANOMALIES=0
PROCESSED_ACTIVE_FUTURE_SCHEDULE_ANOMALIES=0
DATA
  fi
}

if [[ "${1:-}" == "compose" ]]; then
  command=""
  service=""
  for argument in "$@"; do
    case "$argument" in
      ps) command="ps" ;;
      mysql|backend|frontend|proxy) service="$argument" ;;
    esac
  done
  if [[ "$command" == "ps" && "$*" == *"--quiet"* ]]; then
    printf 'container-%s\n' "$service"
    exit 0
  fi
  exit 0
fi

if [[ "${1:-}" == "inspect" ]]; then
  container="${*: -1}"
  if [[ "$*" == *".Config.Image"* ]]; then
    case "$container" in
      container-mysql) printf '%s\n' "$MYSQL_IMAGE" ;;
      container-backend) printf '%s:%s\n' "$BACKEND_IMAGE" "$APP_SHA" ;;
      container-frontend) printf '%s:%s\n' "$FRONTEND_IMAGE" "$APP_SHA" ;;
      container-proxy) printf '%s\n' "$PROXY_IMAGE" ;;
      *) exit 1 ;;
    esac
  elif [[ "$*" == *"org.opencontainers.image.revision"* ]]; then
    printf '%s\n' "$APP_SHA"
  elif [[ "$*" == *".Mounts"* ]]; then
    printf '%s\n' 'pawcycle-production-mysql-data'
  else
    exit 1
  fi
  exit 0
fi

if [[ "${1:-}" == "exec" ]]; then
  if [[ "$*" == *"container-backend printenv PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED"* ]]; then
    printf 'false\n'
    exit 0
  fi
  if [[ "$*" == *"container-backend printenv PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE"* ]]; then
    printf '7\n'
    exit 0
  fi
  if [[ "$*" == *"container-backend printenv PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS"* ]]; then
    printf '12345\n'
    exit 0
  fi
  if [[ "$*" == *"container-backend curl"* && "$*" == *"/actuator/prometheus"* ]]; then
    cat <<'METRICS'
pawcycle_subscription_automation_executions_total 0
pawcycle_subscription_automation_processed_candidates_total 0
pawcycle_subscription_automation_orders_total 0
pawcycle_subscription_automation_failures_total 0
pawcycle_subscription_automation_duplicate_noop_total 0
METRICS
    exit 0
  fi
  if [[ "$*" == *"--interactive container-mysql"* && "$*" == *"exec mysql"* ]]; then
    [[ "${FAKE_FAIL_DOCKER_QUERY:-0}" != "1" ]] || exit 1
    sql="$(cat)"
    count=0
    [[ ! -f "$MARKER_DIR/docker-query-count" ]] || count="$(<"$MARKER_DIR/docker-query-count")"
    printf '%s\n' "$((count + 1))" > "$MARKER_DIR/docker-query-count"
    emit_sql_result "$sql" "${FAKE_DOCKER_DUE_COUNT:-2}"
    exit 0
  fi
  exit 1
fi

if [[ "${1:-}" == "run" ]]; then
  [[ "${FAKE_FAIL_RDS_QUERY:-0}" != "1" ]] || exit 1
  [[ "$*" == *"--pull never"* ]] || exit 1
  [[ "$*" == *"--interactive"* ]] || exit 1
  [[ "$*" == *"--network container:container-backend"* ]] || exit 1
  [[ "$*" == *"--entrypoint mysql"* ]] || exit 1
  [[ "$*" == *"--host=$RDS_HOST"* ]] || exit 1
  [[ "$*" == *"--ssl-mode=REQUIRED"* ]] || exit 1
  sql="$(cat)"
  count=0
  [[ ! -f "$MARKER_DIR/rds-query-count" ]] || count="$(<"$MARKER_DIR/rds-query-count")"
  printf '%s\n' "$((count + 1))" > "$MARKER_DIR/rds-query-count"
  emit_sql_result "$sql" "${FAKE_RDS_DUE_COUNT:-1}"
  exit 0
fi

exit 1
EOF

chmod +x "$BIN_DIR/git" "$BIN_DIR/docker"
export PATH="$BIN_DIR:$PATH"

make_runtime() {
  local root="$1"
  local mode="$2"
  local host ssl_mode datasource_url

  mkdir -p "$root/current"
  : > "$root/.materialize.lock"
  : > "$root/current/.complete"

  if [[ "$mode" == "docker" ]]; then
    host="mysql"
    ssl_mode="DISABLED"
    datasource_url="jdbc:mysql://mysql:3306/ops010?sslMode=DISABLED&allowPublicKeyRetrieval=true&serverTimezone=UTC"
  else
    host="$RDS_HOST"
    ssl_mode="REQUIRED"
    datasource_url="jdbc:mysql://${RDS_HOST}:3306/ops010?sslMode=REQUIRED&serverTimezone=UTC"
  fi

  cat > "$root/current/mysql.env" <<'EOF'
MYSQL_DATABASE='ops010'
MYSQL_USER='ops010_user'
MYSQL_PASSWORD='local-test-password'
MYSQL_ROOT_PASSWORD='local-test-root-password'
EOF

  cat > "$root/current/backend.env" <<EOF
PAWCYCLE_DATASOURCE_HOST='$host'
PAWCYCLE_DATASOURCE_PORT='3306'
PAWCYCLE_DATASOURCE_SSL_MODE='$ssl_mode'
SPRING_DATASOURCE_URL='$datasource_url'
SPRING_DATASOURCE_USERNAME='ops010_user'
SPRING_DATASOURCE_PASSWORD='local-test-password'
PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED='false'
PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE='7'
PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS='12345'
EOF

  chmod 600 "$root/.materialize.lock" "$root/current/.complete" \
    "$root/current/mysql.env" "$root/current/backend.env"
}

make_state() {
  local root="$1"
  mkdir -p "$root"
  printf '%s\n' "$APP_SHA" > "$root/current-sha"
  printf '%s\n' "$CONTROL_SHA" > "$root/contract-sha"
  printf '%s\n' 'pawcycle-production-mysql-data' > "$root/active-mysql-volume"
  chmod 600 "$root/current-sha" "$root/contract-sha" "$root/active-mysql-volume"
}

run_preflight() {
  local runtime="$1"
  local state="$2"
  local max_due="$3"
  local output="$4"

  bash "$SCRIPT_DIR/subscription-automation-preflight.sh" \
    --backend-image "$BACKEND_IMAGE" \
    --frontend-image "$FRONTEND_IMAGE" \
    --expect-bundle-enabled false \
    --expect-running-enabled false \
    --max-due-candidates "$max_due" \
    --runtime-dir "$runtime" \
    --state-dir "$state" > "$output" 2>&1
}

DOCKER_RUNTIME="$TEST_ROOT/runtime-docker"
DOCKER_STATE="$TEST_ROOT/state-docker"
make_runtime "$DOCKER_RUNTIME" docker
make_state "$DOCKER_STATE"
rm -f "$MARKER_DIR"/*
export FAKE_FAIL_RDS_QUERY=1 FAKE_FAIL_DOCKER_QUERY=0 FAKE_DOCKER_DUE_COUNT=2
run_preflight "$DOCKER_RUNTIME" "$DOCKER_STATE" 2 "$TEST_ROOT/docker-output"
grep -Fxq 'DATABASE_PREFLIGHT_TARGET=DOCKER_MYSQL' "$TEST_ROOT/docker-output"
grep -Fxq 'SUBSCRIPTION_AUTOMATION_PREFLIGHT=PASS' "$TEST_ROOT/docker-output"
[[ "$(<"$MARKER_DIR/docker-query-count")" == "2" ]]
[[ ! -e "$MARKER_DIR/rds-query-count" ]]
unset FAKE_FAIL_RDS_QUERY FAKE_FAIL_DOCKER_QUERY FAKE_DOCKER_DUE_COUNT

RDS_RUNTIME="$TEST_ROOT/runtime-rds"
RDS_STATE="$TEST_ROOT/state-rds"
make_runtime "$RDS_RUNTIME" rds
make_state "$RDS_STATE"
rm -f "$MARKER_DIR"/*
export FAKE_FAIL_DOCKER_QUERY=1 FAKE_FAIL_RDS_QUERY=0 FAKE_DOCKER_DUE_COUNT=99 FAKE_RDS_DUE_COUNT=1
run_preflight "$RDS_RUNTIME" "$RDS_STATE" 1 "$TEST_ROOT/rds-output"
grep -Fxq 'DATABASE_PREFLIGHT_TARGET=RDS' "$TEST_ROOT/rds-output"
grep -Fxq 'DUE_CANDIDATE_COUNT=1' "$TEST_ROOT/rds-output"
grep -Fxq 'SUBSCRIPTION_AUTOMATION_PREFLIGHT=PASS' "$TEST_ROOT/rds-output"
[[ "$(<"$MARKER_DIR/rds-query-count")" == "2" ]]
[[ ! -e "$MARKER_DIR/docker-query-count" ]]
unset FAKE_FAIL_DOCKER_QUERY FAKE_FAIL_RDS_QUERY FAKE_DOCKER_DUE_COUNT FAKE_RDS_DUE_COUNT

rm -f "$MARKER_DIR"/*
export FAKE_FAIL_DOCKER_QUERY=1 FAKE_FAIL_RDS_QUERY=0 FAKE_DOCKER_DUE_COUNT=0 FAKE_RDS_DUE_COUNT=2
if run_preflight "$RDS_RUNTIME" "$RDS_STATE" 1 "$TEST_ROOT/rds-limit-output"; then
  printf 'RDS due-candidate limit did not fail closed\n' >&2
  exit 1
fi
grep -Fq 'due candidate count exceeds the explicitly approved activation maximum' "$TEST_ROOT/rds-limit-output"
[[ "$(<"$MARKER_DIR/rds-query-count")" == "2" ]]
[[ ! -e "$MARKER_DIR/docker-query-count" ]]
unset FAKE_FAIL_DOCKER_QUERY FAKE_FAIL_RDS_QUERY FAKE_DOCKER_DUE_COUNT FAKE_RDS_DUE_COUNT

printf 'Subscription automation datasource preflight regression passed\n'
