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
current_clean_control_sha() { printf '%s\n' "$FAKE_CONTROL_SHA"; }
validate_runtime_contract_compatibility() { :; }
load_or_adopt_runtime_contract() { CONTRACT_SHA="$(cat "$PAWCYCLE_STATE_DIR/contract-sha")"; }
release_contract_changed() { return 0; }
require_contract_boundary_approval() {
  [[ -n "$4" && -n "$5" ]] || die 'production release contract boundary requires approved_contract_from_sha and approved_control_sha'
  return 0
}
require_control_only_contract_adoption() {
  local stored_contract_sha="$1"
  local current_release_sha="$2"
  local target_sha="$3"
  local approved_contract_from_sha="$4"
  local approved_control_sha="$5"
  local control_sha

  [[ -n "$approved_contract_from_sha" && -n "$approved_control_sha" ]] \
    || die 'control-only contract adoption requires approved_contract_from_sha and approved_control_sha'
  [[ "$stored_contract_sha" == "$approved_contract_from_sha" ]] \
    || die 'approved_contract_from_sha does not match stored contract-sha'
  [[ "$target_sha" == "$current_release_sha" ]] \
    || die 'control-only contract adoption requires target SHA to match current-sha'
  control_sha="$(current_clean_control_sha)"
  [[ "$control_sha" == "$approved_control_sha" ]] \
    || die 'approved_control_sha does not match the current clean Control HEAD'
  PENDING_CONTRACT_SHA="$control_sha"
}
validate_current_release_for_contract_adoption() { :; }
write_state() { printf '%s\n' "$2" > "$PAWCYCLE_STATE_DIR/$1"; chmod 600 "$PAWCYCLE_STATE_DIR/$1"; }
migration_bundle_changed() { return 1; }
require_migration_boundary_approval() { :; }
preflight_release() { :; }
activate_release() { :; }
compose() {
  if [[ "$*" == *"ps --status running --quiet backend"* && "${FAKE_BACKEND_RUNNING:-0}" == "1" ]]; then
    printf 'container-backend\n'
  fi
}
EOF
chmod +x "$TEST_ROOT/deploy.sh" "$TEST_ROOT/release-common.sh"

BACKEND_IMAGE='ghcr.io/example/pawcycle-commerce-backend'
FRONTEND_IMAGE='ghcr.io/example/pawcycle-commerce-frontend'
CURRENT_SHA='1111111111111111111111111111111111111111'
NEXT_SHA='2222222222222222222222222222222222222222'
CONTROL_SHA='3333333333333333333333333333333333333333'
OLD_CONTROL_SHA='4444444444444444444444444444444444444444'
export FAKE_CONTROL_SHA="$CONTROL_SHA"

new_state() {
  local path="$1"
  local contract_sha="${2:-$CONTROL_SHA}"
  mkdir -p "$path"
  printf '%s\n' "$CURRENT_SHA" > "$path/current-sha"
  printf '%s\n' "$contract_sha" > "$path/contract-sha"
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

unapproved_state="$TEST_ROOT/unapproved-control-state"
new_state "$unapproved_state" "$OLD_CONTROL_SHA"
unapproved_output="$TEST_ROOT/unapproved-control-output"
if "$TEST_ROOT/deploy.sh" \
  --sha "$CURRENT_SHA" \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE" \
  --runtime-dir "$TEST_ROOT/runtime" \
  --state-dir "$unapproved_state" >"$unapproved_output" 2>&1; then
  printf 'unapproved same-SHA Control transition did not fail closed\n' >&2
  exit 1
fi
grep -Fq 'control-only contract adoption requires approved_contract_from_sha and approved_control_sha' "$unapproved_output" \
  || { cat "$unapproved_output" >&2; printf 'unapproved same-SHA Control transition failed for the wrong reason\n' >&2; exit 1; }
[[ "$(cat "$unapproved_state/contract-sha")" == "$OLD_CONTROL_SHA" ]]

quiesced_state="$TEST_ROOT/quiesced-control-state"
new_state "$quiesced_state" "$OLD_CONTROL_SHA"
quiesced_output="$TEST_ROOT/quiesced-control-output"
if ! "$TEST_ROOT/deploy.sh" \
  --sha "$CURRENT_SHA" \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE" \
  --runtime-dir "$TEST_ROOT/runtime" \
  --state-dir "$quiesced_state" \
  --approved-contract-from-sha "$OLD_CONTROL_SHA" \
  --approved-control-sha "$CONTROL_SHA" >"$quiesced_output" 2>&1; then
  cat "$quiesced_output" >&2
  printf 'approved quiesced same-SHA Control transition failed unexpectedly\n' >&2
  exit 1
fi
grep -Fq 'Approved quiesced same-SHA control transition' "$quiesced_output" \
  || { cat "$quiesced_output" >&2; printf 'quiesced transition approval was not recorded\n' >&2; exit 1; }
grep -Fq "Release activated: $CURRENT_SHA" "$quiesced_output"
[[ "$(cat "$quiesced_state/contract-sha")" == "$CONTROL_SHA" ]]

running_state="$TEST_ROOT/running-control-state"
new_state "$running_state" "$OLD_CONTROL_SHA"
running_output="$TEST_ROOT/running-control-output"
export FAKE_BACKEND_RUNNING=1
if "$TEST_ROOT/deploy.sh" \
  --sha "$CURRENT_SHA" \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE" \
  --runtime-dir "$TEST_ROOT/runtime" \
  --state-dir "$running_state" \
  --approved-contract-from-sha "$OLD_CONTROL_SHA" \
  --approved-control-sha "$CONTROL_SHA" >"$running_output" 2>&1; then
  printf 'same-SHA Control transition with running Backend did not fail closed\n' >&2
  exit 1
fi
unset FAKE_BACKEND_RUNNING
grep -Fq 'same-SHA control transition requires Backend to be quiesced when Control HEAD differs from contract-sha' "$running_output" \
  || { cat "$running_output" >&2; printf 'running Backend transition failed for the wrong reason\n' >&2; exit 1; }
[[ "$(cat "$running_state/contract-sha")" == "$OLD_CONTROL_SHA" ]]

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

printf 'same-SHA runtime activation contract regression passed\n'
