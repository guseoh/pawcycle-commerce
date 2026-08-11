#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=infra/production/release-common.sh
source "$SCRIPT_DIR/release-common.sh"

usage() {
  cat <<'EOF'
Usage: deploy.sh --sha <40-char-sha> --backend-image <ghcr-repository> --frontend-image <ghcr-repository> [options]

Options:
  --runtime-dir <path>        Materialized runtime bundle root (default: /opt/pawcycle/runtime)
  --state-dir <path>          Release state directory (default: /opt/pawcycle/state)
  --adopt-contract-sha <sha>  When contract state is absent: approved prior baseline; when it differs: current clean control HEAD
EOF
}

TARGET_SHA=""
BACKEND_IMAGE=""
FRONTEND_IMAGE=""
ADOPT_CONTRACT_SHA=""
PAWCYCLE_RUNTIME_DIR="/opt/pawcycle/runtime"
PAWCYCLE_STATE_DIR="/opt/pawcycle/state"

while (( $# > 0 )); do
  case "$1" in
    --sha) TARGET_SHA="${2:-}"; shift 2 ;;
    --backend-image) BACKEND_IMAGE="${2:-}"; shift 2 ;;
    --frontend-image) FRONTEND_IMAGE="${2:-}"; shift 2 ;;
    --adopt-contract-sha) ADOPT_CONTRACT_SHA="${2:-}"; shift 2 ;;
    --runtime-dir) PAWCYCLE_RUNTIME_DIR="${2:-}"; shift 2 ;;
    --state-dir) PAWCYCLE_STATE_DIR="${2:-}"; shift 2 ;;
    --help) usage; exit 0 ;;
    *) usage >&2; die "unknown argument: $1" ;;
  esac
done

initialize_release_context
require_subscription_automation_mode false

CURRENT_SHA=""
if [[ -e "$PAWCYCLE_STATE_DIR/current-sha" || -L "$PAWCYCLE_STATE_DIR/current-sha" ]]; then
  CURRENT_SHA="$(read_state_sha current-sha)"
fi

CONTRACT_STATE_PATH="$PAWCYCLE_STATE_DIR/contract-sha"
if [[ ! -e "$CONTRACT_STATE_PATH" && ! -L "$CONTRACT_STATE_PATH" ]]; then
  [[ -n "$ADOPT_CONTRACT_SHA" ]] \
    || die "production runtime contract state is missing; --adopt-contract-sha with the approved prior baseline is required"
  validate_sha "$ADOPT_CONTRACT_SHA"
  CONTROL_SHA="$(current_control_sha)"
  validate_runtime_contract_compatibility "$ADOPT_CONTRACT_SHA" "$CONTROL_SHA"
  load_or_adopt_runtime_contract "$CONTROL_SHA" "$CURRENT_SHA"
else
  load_or_adopt_runtime_contract "$ADOPT_CONTRACT_SHA" "$CURRENT_SHA"
fi

if [[ "$CURRENT_SHA" != "$TARGET_SHA" ]]; then
  validate_runtime_contract_compatibility "$CONTRACT_SHA" "$TARGET_SHA"
fi

if [[ -n "$CURRENT_SHA" && "$CURRENT_SHA" != "$TARGET_SHA" ]]; then
  printf 'Preflighting rollback release before activation: %s\n' "$CURRENT_SHA"
  preflight_release "$CURRENT_SHA"
fi

SCHEMA_BOUNDARY=0
if [[ -n "$CURRENT_SHA" && "$CURRENT_SHA" != "$TARGET_SHA" ]] \
  && migration_bundle_changed "$CURRENT_SHA" "$TARGET_SHA"; then
  SCHEMA_BOUNDARY=1
  printf 'Database migration boundary detected; automatic pre-migration release restoration is disabled\n'
fi

printf 'Preflighting target release without changing running containers: %s\n' "$TARGET_SHA"
preflight_release "$TARGET_SHA"

if ! activate_release "$TARGET_SHA"; then
  printf 'Target release failed health or smoke validation: %s\n' "$TARGET_SHA" >&2
  if [[ "$SCHEMA_BOUNDARY" == "1" ]]; then
    stop_application_services
    die "target release failed across a database migration boundary; automatic pre-migration release restoration is blocked, Scheduler remains OFF, and MySQL was preserved"
  fi
  if [[ -n "$CURRENT_SHA" && "$CURRENT_SHA" != "$TARGET_SHA" ]]; then
    printf 'Restoring previous healthy release: %s\n' "$CURRENT_SHA" >&2
    if activate_release "$CURRENT_SHA"; then
      die "target release failed; previous release was restored"
    fi
    die "target release and automatic restoration both failed; MySQL volume was not removed"
  fi
  stop_application_services
  die "initial release failed; application services were stopped and MySQL was preserved"
fi

if [[ -n "$CURRENT_SHA" && "$CURRENT_SHA" != "$TARGET_SHA" ]]; then
  write_state previous-sha "$CURRENT_SHA"
  write_state previous-contract-sha "$CONTRACT_SHA"
fi
write_state current-sha "$TARGET_SHA"

ACTIVE_SHA="$TARGET_SHA"
compose ps || printf 'WARNING: release succeeded, but final compose ps failed\n' >&2
printf 'Release activated: %s\n' "$TARGET_SHA"
