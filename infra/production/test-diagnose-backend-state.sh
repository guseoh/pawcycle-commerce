#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
DIAGNOSTIC="$SCRIPT_DIR/diagnose-backend-state.sh"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf -- "$TEST_ROOT"' EXIT
SHA_CURRENT="1111111111111111111111111111111111111111"
SHA_PREVIOUS="2222222222222222222222222222222222222222"

mkdir -p "$TEST_ROOT/bin"
cat >"$TEST_ROOT/bin/docker" <<'EOF'
#!/usr/bin/env bash
if [[ "$1" == ps ]]; then
  [[ "${FAKE_DOCKER_QUERY:-ok}" == fail ]] && exit 1
  [[ "${FAKE_BACKEND_STATUS:-healthy}" == missing ]] || printf 'backend-id\n'
  exit 0
fi
[[ "${FAKE_DOCKER_QUERY:-ok}" == inspect-fail ]] && exit 1
printf '%s\n' "${FAKE_BACKEND_STATUS:-healthy}"
EOF
cat >"$TEST_ROOT/bin/curl" <<'EOF'
#!/usr/bin/env bash
arguments="$*"
case "$arguments" in
  *'/api/products'*) printf '%s' "${FAKE_API_STATUS:-200}" ;;
  *'/products'*) printf '200' ;;
  *'/actuator/prometheus'*) printf '%s' "${FAKE_METRICS_STATUS:-200}" ;;
  *'/api/v1/targets'*)
    case "${FAKE_PROMETHEUS_CASE:-up}" in
      request-fail) exit 7 ;;
      parse-fail) printf '{invalid' ;;
      missing) printf '{"status":"success","data":{"activeTargets":[]}}' ;;
      duplicate) printf '{"status":"success","data":{"activeTargets":[{"labels":{"job":"pawcycle-production-backend"},"health":"up"},{"labels":{"job":"pawcycle-production-backend"},"health":"down"}]}}' ;;
      down) printf '{"status":"success","data":{"activeTargets":[{"labels":{"job":"pawcycle-production-backend"},"health":"down"}]}}' ;;
      *) printf '{"status":"success","data":{"activeTargets":[{"labels":{"job":"another-job"},"health":"down"},{"labels":{"job":"pawcycle-production-backend"},"health":"up"}]}}' ;;
    esac ;;
  *) exit 22 ;;
esac
EOF
chmod +x "$TEST_ROOT/bin/docker" "$TEST_ROOT/bin/curl"

make_state() {
  local directory="$1"
  mkdir -p "$directory"
  printf '%s\n' "$SHA_CURRENT" >"$directory/current-sha"
  printf '%s\n' "$SHA_PREVIOUS" >"$directory/previous-sha"
  printf '%s\n' 'pawcycle-production-mysql-data' >"$directory/active-mysql-volume"
  printf '%s\n' 'example.test' >"$directory/https-domain"
  chmod 600 "$directory/current-sha" "$directory/previous-sha" "$directory/active-mysql-volume"
}

run_case() {
  local name="$1" backend="$2" docker_query="$3" api="$4" metrics="$5" prometheus="$6" expected="$7"
  local case_dir production_code final_code
  case_dir="$TEST_ROOT/$name"
  mkdir -p "$case_dir"
  make_state "$case_dir/state"
  case "$name" in
    invalid-current) printf 'invalid\n' >"$case_dir/state/current-sha" ;;
    invalid-previous) printf 'invalid\n' >"$case_dir/state/previous-sha" ;;
    missing-volume) rm -f -- "$case_dir/state/active-mysql-volume" ;;
  esac

  if PATH="$TEST_ROOT/bin:$PATH" FAKE_BACKEND_STATUS="$backend" FAKE_DOCKER_QUERY="$docker_query" \
    FAKE_API_STATUS="$api" FAKE_METRICS_STATUS="$metrics" \
    bash "$DIAGNOSTIC" --scope production --state-dir "$case_dir/state" >"$case_dir/production"; then
    production_code=0
  else
    production_code=$?
  fi
  [[ "$production_code" == 0 || "$production_code" == 1 ]]

  if PATH="$TEST_ROOT/bin:$PATH" PAWCYCLE_PYTHON_BIN=python3 FAKE_PROMETHEUS_CASE="$prometheus" \
    bash "$DIAGNOSTIC" --scope observability --prometheus-url http://127.0.0.1:9090 \
      --production-result "$case_dir/production" >"$case_dir/final"; then
    final_code=0
  else
    final_code=$?
  fi
  grep -qx "status=$expected" "$case_dir/final"
  if [[ "$expected" == NORMAL ]]; then [[ "$final_code" == 0 ]]; else [[ "$final_code" != 0 ]]; fi
}

run_case normal healthy ok 200 200 up NORMAL
run_case backend-down missing ok 503 502 down BACKEND_DOWN
run_case observability-degraded healthy ok 200 503 down OBSERVABILITY_DEGRADED
run_case degraded healthy ok 503 200 up DEGRADED
run_case unknown-docker-status unknown ok 503 502 down UNKNOWN
run_case docker-query-failure healthy fail 503 502 down UNKNOWN
run_case prometheus-parse-failure healthy ok 200 200 parse-fail UNKNOWN
run_case prometheus-request-failure healthy ok 200 200 request-fail UNKNOWN
run_case prometheus-target-missing healthy ok 200 200 missing UNKNOWN
run_case prometheus-target-duplicate healthy ok 200 200 duplicate UNKNOWN
run_case invalid-current healthy ok 200 200 up UNKNOWN
run_case invalid-previous healthy ok 200 200 up UNKNOWN
run_case missing-volume healthy ok 200 200 up UNKNOWN
run_case frontend-false-positive healthy ok 503 200 up DEGRADED

for option in --prometheus-url --https-origin --state-dir; do
  if bash "$DIAGNOSTIC" --scope production "$option" >"$TEST_ROOT/usage-out" 2>"$TEST_ROOT/usage-error"; then
    printf 'missing %s value unexpectedly succeeded\n' "$option" >&2
    exit 1
  else
    code=$?
  fi
  [[ "$code" == 64 ]]
  grep -q '^usage:' "$TEST_ROOT/usage-error"
done

if grep -E 'docker (compose|start|stop|restart|rm)|aws |flyway|mysql ' "$DIAGNOSTIC"; then
  printf 'diagnostic must remain read-only\n' >&2
  exit 1
fi
printf 'OPS-AUTO-009 two-host read-only backend diagnostic fixture tests passed\n'
