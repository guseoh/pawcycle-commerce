#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
TEST_ROOT="$(mktemp -d)"
BIN_DIR="$TEST_ROOT/bin"
MARKER_DIR="$TEST_ROOT/markers"
mkdir -p "$BIN_DIR" "$MARKER_DIR"
trap 'rm -rf -- "$TEST_ROOT"' EXIT

APP_SHA="1111111111111111111111111111111111111111"
CONTROL_SHA="2222222222222222222222222222222222222222"
BACKEND_IMAGE="ghcr.io/example/pawcycle-commerce-backend"
FRONTEND_IMAGE="ghcr.io/example/pawcycle-commerce-frontend"
PROXY_IMAGE="nginx:1.30.3-alpine3.23@sha256:0d3b80406a13a767339fbe2f41406d6c7da727ab89cf8fae399e81f780f814d1"
export APP_SHA CONTROL_SHA BACKEND_IMAGE FRONTEND_IMAGE PROXY_IMAGE MARKER_DIR

cat >"$BIN_DIR/git" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
case "${1:-}" in
  -C) shift 2; case "${1:-}" in status) exit 0 ;; rev-parse) printf '%s\n' "$CONTROL_SHA" ;; cat-file|diff) exit 0 ;; esac ;;
  status) exit 0 ;;
  rev-parse) printf '%s\n' "$CONTROL_SHA" ;;
  cat-file|diff) exit 0 ;;
  *) exit 0 ;;
esac
EOF

cat >"$BIN_DIR/docker" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
if [[ "${1:-}" == compose ]]; then
  if [[ "$*" == *' ps --quiet '* || "$*" == *' ps --quiet' ]]; then
    case "$*" in *backend*) printf 'container-backend\n' ;; *frontend*) printf 'container-frontend\n' ;; *proxy*) printf 'container-proxy\n' ;; esac
  fi
  exit 0
fi
if [[ "${1:-}" == inspect ]]; then
  if [[ "$*" == *'.Config.Image'* ]]; then
    case "$*" in *container-backend*) printf '%s:%s\n' "$BACKEND_IMAGE" "$APP_SHA" ;; *container-frontend*) printf '%s:%s\n' "$FRONTEND_IMAGE" "$APP_SHA" ;; *) printf '%s\n' "$PROXY_IMAGE" ;; esac
  elif [[ "$*" == *'org.opencontainers.image.revision'* ]]; then printf '%s\n' "$APP_SHA"
  elif [[ "$*" == *'NetworkSettings.Networks'* ]]; then printf 'attached\n'
  else printf 'healthy\n'; fi
  exit 0
fi
if [[ "${1:-}" == exec ]]; then
  case "$*" in
    *'printenv PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED'*) printf 'false\n' ;;
    *'printenv PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE'*) printf '7\n' ;;
    *'printenv PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS'*) printf '12345\n' ;;
    *'/actuator/prometheus'*) cat <<'METRICS'
pawcycle_subscription_automation_executions_total 0
pawcycle_subscription_automation_processed_candidates_total 0
pawcycle_subscription_automation_orders_total 0
pawcycle_subscription_automation_failures_total 0
pawcycle_subscription_automation_duplicate_noop_total 0
METRICS
      ;;
  esac
  exit 0
fi
if [[ "${1:-}" == run ]]; then
  [[ "$*" == *'--network pawcycle-production-database-egress'* ]]
  [[ "$*" == *'--entrypoint mysql'* ]]
  [[ "$*" == *'--defaults-extra-file=/run/pawcycle/mysql-client.cnf'* ]]
  [[ "$*" != *'MYSQL_PWD'* && "$*" != *'--password'* ]]
  if [[ "${FAKE_MYSQL_MODE:-success}" == failure ]]; then
    printf 'synthetic query failure with password=must-not-leak\n' >&2
    exit 17
  fi
  count=0; [[ ! -f "$MARKER_DIR/query-count" ]] || count="$(<"$MARKER_DIR/query-count")"; printf '%s\n' "$((count + 1))" >"$MARKER_DIR/query-count"
  sql="$(cat)"
  if [[ "$sql" == *TABLE_SUBSCRIPTION_ORDERS* ]]; then
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
    printf 'DUE_CANDIDATE_COUNT=%s\nOLDEST_DUE_DATE=2026-08-01\nDUPLICATE_ORDER_SCHEDULE_GROUPS=0\nORDERLESS_ADVANCED_SCHEDULES=0\nORDER_SNAPSHOT_CARDINALITY_ANOMALIES=0\nPROCESSED_ACTIVE_FUTURE_SCHEDULE_ANOMALIES=0\n' "${FAKE_DUE_COUNT:-2}"
  fi
  exit 0
fi
exit 0
EOF
cat >"$BIN_DIR/timeout" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
[[ "${1:-}" == --signal=TERM ]] && shift
[[ "${1:-}" == --kill-after=* ]] && shift
[[ -n "${1:-}" ]] && shift
if [[ "${FAKE_TIMEOUT_MODE:-success}" == timeout ]]; then
  exit 124
fi
exec "$@"
EOF
chmod +x "$BIN_DIR/git" "$BIN_DIR/docker" "$BIN_DIR/timeout"
export PATH="$BIN_DIR:$PATH"

make_runtime() {
  local root="$1" bundle="$1/.bundle.fixture"
  mkdir -p "$bundle"; chmod 700 "$root" "$bundle"; ln -s .bundle.fixture "$root/current"
  : >"$root/.materialize.lock"; chmod 600 "$root/.materialize.lock"
  cat >"$bundle/backend.env" <<'EOF'
PAWCYCLE_DATASOURCE_HOST='db.example.com'
PAWCYCLE_DATASOURCE_PORT='3306'
PAWCYCLE_DATASOURCE_DATABASE='pawcycle'
PAWCYCLE_DATASOURCE_SSL_MODE='REQUIRED'
SPRING_DATASOURCE_URL='jdbc:mysql://db.example.com:3306/pawcycle?sslMode=REQUIRED&serverTimezone=UTC'
SPRING_DATASOURCE_USERNAME='pawcycle_app'
SPRING_DATASOURCE_PASSWORD='local-validation-only'
PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED='false'
PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE='7'
PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS='12345'
EOF
  printf 'RUNTIME_ENV_FORMAT=1\n' >"$bundle/.complete"; chmod 600 "$bundle/backend.env" "$bundle/.complete"
}

make_state() {
  local root="$1"; mkdir -p "$root"
  printf '%s\n' "$APP_SHA" >"$root/current-sha"; printf '%s\n' "$CONTROL_SHA" >"$root/contract-sha"; : >"$root/deploy.lock"
  chmod 600 "$root/current-sha" "$root/contract-sha" "$root/deploy.lock"
}

run_preflight() {
  local runtime="$1" state="$2" max_due="$3" output="$4"
  if ! bash "$SCRIPT_DIR/subscription-automation-preflight.sh" --backend-image "$BACKEND_IMAGE" --frontend-image "$FRONTEND_IMAGE" \
    --expect-bundle-enabled false --expect-running-enabled false --max-due-candidates "$max_due" \
    --runtime-dir "$runtime" --state-dir "$state" >"$output" 2>&1; then
    cat "$output" >&2
    return 1
  fi
}

RUNTIME="$TEST_ROOT/runtime"; STATE="$TEST_ROOT/state"; make_runtime "$RUNTIME"; make_state "$STATE"
FAKE_DUE_COUNT=2 run_preflight "$RUNTIME" "$STATE" 2 "$TEST_ROOT/pass-output"
grep -Fxq 'DATABASE_PREFLIGHT_TARGET=EXTERNAL_MYSQL' "$TEST_ROOT/pass-output"
grep -Fxq 'SUBSCRIPTION_AUTOMATION_PREFLIGHT=PASS' "$TEST_ROOT/pass-output"
[[ "$(<"$MARKER_DIR/query-count")" == 2 ]]

rm -f "$MARKER_DIR/query-count"
if FAKE_DUE_COUNT=3 run_preflight "$RUNTIME" "$STATE" 2 "$TEST_ROOT/limit-output"; then
  printf 'external due-candidate limit did not fail closed\n' >&2
  exit 1
fi
grep -Fq 'due candidate count exceeds the explicitly approved activation maximum' "$TEST_ROOT/limit-output"

rm -f "$MARKER_DIR/query-count"
if FAKE_MYSQL_MODE=failure run_preflight "$RUNTIME" "$STATE" 2 "$TEST_ROOT/failure-output"; then
  printf 'mysql failure was reported as success\n' >&2
  exit 1
fi
grep -Fq 'read-only external MySQL preflight failed; raw database output was suppressed' "$TEST_ROOT/failure-output"
if grep -Fq 'SELECT ' "$TEST_ROOT/failure-output"; then
  printf 'raw SQL leaked from failed external MySQL preflight\n' >&2
  exit 1
fi
if grep -Fq 'must-not-leak' "$TEST_ROOT/failure-output"; then
  printf 'raw database failure output leaked from external MySQL preflight\n' >&2
  exit 1
fi

rm -f "$MARKER_DIR/query-count"
if FAKE_TIMEOUT_MODE=timeout run_preflight "$RUNTIME" "$STATE" 2 "$TEST_ROOT/timeout-output"; then
  printf 'mysql timeout was reported as success\n' >&2
  exit 1
fi
grep -Fq 'read-only external MySQL preflight timed out after 60s; raw database output was suppressed' "$TEST_ROOT/timeout-output"
if grep -Fq 'SELECT ' "$TEST_ROOT/timeout-output"; then
  printf 'raw SQL leaked from timed out external MySQL preflight\n' >&2
  exit 1
fi
if grep -Fq 'local-validation-only' "$TEST_ROOT/timeout-output"; then
  printf 'database credential leaked from timed out external MySQL preflight\n' >&2
  exit 1
fi

printf 'External MySQL subscription preflight regression passed\n'
