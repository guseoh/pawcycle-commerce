#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=infra/production/release-common.sh
source "$SCRIPT_DIR/release-common.sh"

usage() {
  cat <<'EOF'
Usage: rollback.sh [--sha <previous-40-char-sha>] --backend-image <ghcr-repository> --frontend-image <ghcr-repository> [options]

If --sha is omitted, the state directory's previous-sha value is used.
This command never deletes the MySQL volume and never restores database schema or data.
EOF
}

TARGET_SHA=""
BACKEND_IMAGE=""
FRONTEND_IMAGE=""
PAWCYCLE_RUNTIME_DIR="/opt/pawcycle/runtime"
PAWCYCLE_STATE_DIR="/opt/pawcycle/state"

while (( $# > 0 )); do
  case "$1" in
    --sha) TARGET_SHA="${2:-}"; shift 2 ;;
    --backend-image) BACKEND_IMAGE="${2:-}"; shift 2 ;;
    --frontend-image) FRONTEND_IMAGE="${2:-}"; shift 2 ;;
    --runtime-dir) PAWCYCLE_RUNTIME_DIR="${2:-}"; shift 2 ;;
    --state-dir) PAWCYCLE_STATE_DIR="${2:-}"; shift 2 ;;
    --help) usage; exit 0 ;;
    *) usage >&2; die "unknown argument: $1" ;;
  esac
done

initialize_rollback_context() {
  if [[ -n "$TARGET_SHA" ]]; then
    validate_sha "$TARGET_SHA"
  fi
  prepare_release_context
  acquire_release_lock
  if [[ -z "$TARGET_SHA" ]]; then
    TARGET_SHA="$(read_state_sha previous-sha)"
  fi
  load_active_mysql_volume
}

validate_recorded_previous_release_contract() {
  local current_sha="$1"
  local target_sha="$2"
  local previous_sha=""
  local previous_contract_sha=""

  if [[ -e "$PAWCYCLE_STATE_DIR/previous-sha" || -L "$PAWCYCLE_STATE_DIR/previous-sha" ]]; then
    previous_sha="$(read_state_sha previous-sha)"
  fi
  if [[ -e "$PAWCYCLE_STATE_DIR/previous-contract-sha" || -L "$PAWCYCLE_STATE_DIR/previous-contract-sha" ]]; then
    previous_contract_sha="$(read_state_sha previous-contract-sha)"
  fi

  # deploy.sh and rollback.sh write previous-sha and previous-contract-sha under
  # the shared deploy.lock, then write current-sha last as the successful
  # transition commit marker. A partial write therefore leaves previous-sha
  # equal to current-sha and must not use the recorded previous Control.
  if [[ "$target_sha" == "$previous_sha" \
    && "$previous_sha" != "$current_sha" \
    && -n "$previous_contract_sha" ]]; then
    validate_runtime_contract_compatibility "$previous_contract_sha" "$CONTRACT_SHA"
    return 0
  fi

  validate_rollback_contract_compatibility "$target_sha"
}

initialize_rollback_context
if [[ "$PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED" != "false" ]]; then
  die "subscription automation runtime must be explicitly false for rollback; first run subscription-automation-control.sh deactivate, and if deactivation fails stop Backend then escalate to the user"
fi

CURRENT_SHA="$(read_state_sha current-sha)"
load_runtime_contract
[[ "$TARGET_SHA" != "$CURRENT_SHA" ]] || die "rollback target equals current release"

validate_recorded_previous_release_contract "$CURRENT_SHA" "$TARGET_SHA"
require_no_migration_boundary_rollback "$CURRENT_SHA" "$TARGET_SHA"

printf 'Preflighting current recovery release: %s\n' "$CURRENT_SHA"
preflight_release "$CURRENT_SHA"
printf 'Preflighting rollback target: %s\n' "$TARGET_SHA"
preflight_release "$TARGET_SHA"

if ! activate_release "$TARGET_SHA"; then
  printf 'Rollback target failed; attempting to restore current release: %s\n' "$CURRENT_SHA" >&2
  if activate_release "$CURRENT_SHA"; then
    die "rollback target failed; current release was restored"
  fi
  die "rollback target and current release restoration both failed; MySQL volume was not removed"
fi

write_state previous-sha "$CURRENT_SHA"
write_state previous-contract-sha "$CONTRACT_SHA"
write_state current-sha "$TARGET_SHA"

ACTIVE_SHA="$TARGET_SHA"
compose ps || printf 'WARNING: rollback succeeded, but final compose ps failed\n' >&2
printf 'Rollback activated without database restoration or volume deletion: %s\n' "$TARGET_SHA"
