#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf -- "$TEST_ROOT"' EXIT

make_case() {
  local name="$1" backend="$2" api="$3" metrics="$4" target="$5" expected="$6"
  local case_dir="$TEST_ROOT/$name"
  mkdir -p "$case_dir/bin" "$case_dir/state"
  printf 'current\n' >"$case_dir/state/current-sha"; printf 'previous\n' >"$case_dir/state/previous-sha"; printf 'volume\n' >"$case_dir/state/active-mysql-volume"; printf 'example.test\n' >"$case_dir/state/https-domain"
  cat >"$case_dir/bin/docker" <<EOF
#!/usr/bin/env bash
if [[ "\$*" == *"ps --all"* ]]; then [[ "$backend" == missing ]] || printf 'backend-id\n'; exit 0; fi
printf '%s\n' "$backend"
EOF
  cat >"$case_dir/bin/curl" <<EOF
#!/usr/bin/env bash
case "\$*" in
  *'/api/products'*) printf '$api';;
  *'https://example.test/products'*) printf '200';;
  *'/actuator/prometheus'*) printf '$metrics';;
  *) printf '{"data":{"result":[{"metric":{"job":"pawcycle-production-backend"},"value":[0,"$target"]}]}}';;
esac
EOF
  chmod +x "$case_dir/bin/docker" "$case_dir/bin/curl"
  if PATH="$case_dir/bin:$PATH" PAWCYCLE_PYTHON_BIN=python3 "$SCRIPT_DIR/diagnose-backend-state.sh" --state-dir "$case_dir/state" --prometheus-url http://prometheus.test >"$case_dir/out"; then code=0; else code=$?; fi
  grep -qx "status=$expected" "$case_dir/out"
  if [[ "$expected" == NORMAL ]]; then [[ "$code" == 0 ]]; else [[ "$code" != 0 ]]; fi
}

make_case normal healthy 200 200 1 NORMAL
make_case backend-down stopped 503 200 0 BACKEND_DOWN
make_case observability-degraded healthy 200 503 0 OBSERVABILITY_DEGRADED
make_case contradictory healthy 503 200 1 DEGRADED
make_case frontend-false-positive healthy 503 200 1 DEGRADED
if grep -R -E 'docker (compose|start|stop|restart|rm)|aws |flyway|mysql ' "$SCRIPT_DIR/diagnose-backend-state.sh"; then
  printf 'diagnostic must remain read-only\n' >&2
  exit 1
fi
printf 'OPS-AUTO-009 read-only backend diagnostic fixture tests passed\n'
