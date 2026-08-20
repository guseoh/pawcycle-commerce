#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf -- "$TEST_ROOT"' EXIT

cp "$SCRIPT_DIR/deploy.sh" "$TEST_ROOT/deploy.sh"

cat > "$TEST_ROOT/release-common.sh" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail

CONTRACT_SHA=""
PENDING_CONTRACT_SHA=""

die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
initialize_read_only_release_context() { :; }
initialize_release_context() { :; }
require_subscription_automation_mode() { [[ "$1" == false ]]; }
read_state_sha() { cat "$PAWCYCLE_STATE_DIR/$1"; }
validate_sha() { [[ "$1" =~ ^[0-9a-f]{40}$ ]]; }
current_control_sha() { printf '%s\n' "$FAKE_CONTROL_SHA"; }
validate_runtime_contract_compatibility() { :; }
load_or_adopt_runtime_contract() { CONTRACT_SHA="$(cat "$PAWCYCLE_STATE_DIR/contract-sha")"; }
release_contract_changed() { return 0; }
require_contract_boundary_approval() {
  [[ -n "$4" && -n "$5" ]] || die 'production release contract boundary requires approved_contract_from_sha and approved_control_sha'
  return 0
}
require_control_only_contract_adoption() { :; }
validate_current_release_for_contract_adoption() { :; }
write_state() { printf '%s\n' "$2" > "$PAWCYCLE_STATE_DIR/$1"; chmod 600 "$PAWCYCLE_STATE_DIR/$1"; }
migration_bundle_changed() { return 1; }
require_migration_boundary_approval() { :; }
preflight_release() { :; }
activate_release() { :; }
compose() { :; }
EOF
chmod +x "$TEST_ROOT/deploy.sh" "$TEST_ROOT/release-common.sh"

BACKEND_IMAGE='ghcr.io/example/pawcycle-commerce-backend'
FRONTEND_IMAGE='ghcr.io/example/pawcycle-commerce-frontend'
CURRENT_SHA='1111111111111111111111111111111111111111'
NEXT_SHA='2222222222222222222222222222222222222222'
CONTROL_SHA='3333333333333333333333333333333333333333'
export FAKE_CONTROL_SHA="$CONTROL_SHA"

new_state() {
  local path="$1"
  mkdir -p "$path"
  printf '%s\n' "$CURRENT_SHA" > "$path/current-sha"
  printf '%s\n' "$CONTROL_SHA" > "$path/contract-sha"
  chmod 600 "$path/current-sha" "$path/contract-sha"
}

same_state="$TEST_ROOT/same-state"
new_state "$same_state"

same_output="$TEST_ROOT/same-output"
if ! "$TEST_ROOT/deploy.sh" \
  --sha "$CURRENT_SHA" \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE" \
  --runtime-dir "$TEST_ROOT/runtime" \
  --state-dir "$same_state" >"$same_output" 2>&1; then
  cat "$same_output" >&2
  printf 'same-SHA runtime activation failed unexpectedly\n' >&2
  exit 1
fi

grep -Fq "Release activated: $CURRENT_SHA" "$same_output" \
  || { cat "$same_output" >&2; printf 'same-SHA runtime activation was not allowed\n' >&2; exit 1; }
[[ "$(cat "$same_state/current-sha")" == "$CURRENT_SHA" ]]

changed_state="$TEST_ROOT/changed-state"
new_state "$changed_state"
changed_output="$TEST_ROOT/changed-output"

if "$TEST_ROOT/deploy.sh" \
  --sha "$NEXT_SHA" \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE" \
  --runtime-dir "$TEST_ROOT/runtime" \
  --state-dir "$changed_state" >"$changed_output" 2>&1; then
  printf 'changed-SHA contract boundary did not fail closed\n' >&2
  exit 1
fi

grep -Fq 'production release contract boundary requires approved_contract_from_sha and approved_control_sha' "$changed_output" \
  || { cat "$changed_output" >&2; printf 'changed-SHA contract boundary failed for the wrong reason\n' >&2; exit 1; }

printf 'same-SHA runtime activation regression passed\n'
