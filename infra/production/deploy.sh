#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=infra/production/release-common.sh
source "$SCRIPT_DIR/release-common.sh"

usage() {
  cat <<'EOF'
Usage: deploy.sh --sha <40-char-sha> --backend-image <ghcr-repository> --frontend-image <ghcr-repository> [options]

Options:
  --operation <preflight|deploy>
                               Read-only approval preflight or activation (default: deploy)
  --runtime-dir <path>        Materialized runtime bundle root (default: /opt/pawcycle/runtime)
  --state-dir <path>          Release state directory (default: /opt/pawcycle/state)
  --adopt-contract-sha <sha>  When contract state is absent: approved prior baseline; when it differs: current clean control HEAD
  --approved-contract-from-sha <sha>
                               Approved stored contract SHA for a Release contract boundary
  --approved-control-sha <sha> Approved clean Control HEAD for a Release contract boundary
  --approved-migration-target-sha <sha>
                               Approved target SHA for a Flyway migration boundary
EOF
}

TARGET_SHA=""
BACKEND_IMAGE=""
FRONTEND_IMAGE=""
ADOPT_CONTRACT_SHA=""
APPROVED_CONTRACT_FROM_SHA=""
APPROVED_CONTROL_SHA=""
APPROVED_MIGRATION_TARGET_SHA=""
OPERATION="deploy"
PAWCYCLE_RUNTIME_DIR="/opt/pawcycle/runtime"
PAWCYCLE_STATE_DIR="/opt/pawcycle/state"

while (( $# > 0 )); do
  case "$1" in
    --sha) TARGET_SHA="${2:-}"; shift 2 ;;
    --backend-image) BACKEND_IMAGE="${2:-}"; shift 2 ;;
    --frontend-image) FRONTEND_IMAGE="${2:-}"; shift 2 ;;
    --operation) OPERATION="${2:-}"; shift 2 ;;
    --adopt-contract-sha) ADOPT_CONTRACT_SHA="${2:-}"; shift 2 ;;
    --approved-contract-from-sha) APPROVED_CONTRACT_FROM_SHA="${2:-}"; shift 2 ;;
    --approved-control-sha) APPROVED_CONTROL_SHA="${2:-}"; shift 2 ;;
    --approved-migration-target-sha) APPROVED_MIGRATION_TARGET_SHA="${2:-}"; shift 2 ;;
    --runtime-dir) PAWCYCLE_RUNTIME_DIR="${2:-}"; shift 2 ;;
    --state-dir) PAWCYCLE_STATE_DIR="${2:-}"; shift 2 ;;
    --help) usage; exit 0 ;;
    *) usage >&2; die "unknown argument: $1" ;;
  esac
done

[[ "$OPERATION" == "preflight" || "$OPERATION" == "deploy" ]] \
  || die "operation must be preflight or deploy"
if [[ "$OPERATION" == "preflight" ]]; then
  PAWCYCLE_PREFLIGHT_RECORD_IMAGES=false
  initialize_read_only_release_context
else
  initialize_release_context
fi
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
  STORED_CONTRACT_SHA="$(read_state_sha contract-sha)"
  if release_contract_changed "$STORED_CONTRACT_SHA" "$TARGET_SHA"; then
    [[ -z "$ADOPT_CONTRACT_SHA" ]] \
      || die "--adopt-contract-sha cannot approve a production release contract boundary"
    require_contract_boundary_approval \
      "$STORED_CONTRACT_SHA" "$CURRENT_SHA" "$TARGET_SHA" \
      "$APPROVED_CONTRACT_FROM_SHA" "$APPROVED_CONTROL_SHA"
    CONTRACT_SHA="$STORED_CONTRACT_SHA"
    CONTRACT_BOUNDARY=1
  else
    CONTRACT_BOUNDARY=0
    load_or_adopt_runtime_contract "$ADOPT_CONTRACT_SHA" "$CURRENT_SHA"
  fi
fi

CONTRACT_BOUNDARY="${CONTRACT_BOUNDARY:-0}"

if [[ "$CURRENT_SHA" != "$TARGET_SHA" && "$CONTRACT_BOUNDARY" == "0" ]]; then
  validate_runtime_contract_compatibility "$CONTRACT_SHA" "$TARGET_SHA"
fi

SCHEMA_BOUNDARY=0
if [[ -n "$CURRENT_SHA" && "$CURRENT_SHA" != "$TARGET_SHA" ]] \
  && migration_bundle_changed "$CURRENT_SHA" "$TARGET_SHA"; then
  SCHEMA_BOUNDARY=1
  require_migration_boundary_approval "$TARGET_SHA" "$APPROVED_MIGRATION_TARGET_SHA"
  printf 'Database migration boundary detected; automatic pre-migration release restoration is disabled\n'
fi

if [[ -n "$CURRENT_SHA" && "$CURRENT_SHA" != "$TARGET_SHA" ]]; then
  printf 'Preflighting rollback release before activation: %s\n' "$CURRENT_SHA"
  preflight_release "$CURRENT_SHA"
fi

printf 'Preflighting target release without changing running containers: %s\n' "$TARGET_SHA"
preflight_release "$TARGET_SHA"

if [[ "$OPERATION" == "preflight" ]]; then
  printf 'Production deploy approval preflight passed without changing containers, DB, or state\n'
  exit 0
fi

if ! activate_release "$TARGET_SHA"; then
  printf 'Target release failed health or smoke validation: %s\n' "$TARGET_SHA" >&2
  if [[ "$CONTRACT_BOUNDARY" == "1" || "$SCHEMA_BOUNDARY" == "1" ]]; then
    stop_application_services
    die "target release failed across an approved contract or database migration boundary; automatic pre-migration release restoration is blocked, and automatic contract-boundary restoration is blocked, Scheduler remains OFF, and MySQL was preserved"
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

PREVIOUS_CONTRACT_SHA="$CONTRACT_SHA"
if [[ -n "$PENDING_CONTRACT_SHA" ]]; then
  write_state contract-sha "$PENDING_CONTRACT_SHA"
  CONTRACT_SHA="$PENDING_CONTRACT_SHA"
  printf 'Production control contract adopted after target activation: %s\n' "$CONTRACT_SHA"
fi

if [[ -n "$CURRENT_SHA" && "$CURRENT_SHA" != "$TARGET_SHA" ]]; then
  write_state previous-sha "$CURRENT_SHA"
  write_state previous-contract-sha "$PREVIOUS_CONTRACT_SHA"
fi
write_state current-sha "$TARGET_SHA"

ACTIVE_SHA="$TARGET_SHA"
compose ps || printf 'WARNING: release succeeded, but final compose ps failed\n' >&2
printf 'Release activated: %s\n' "$TARGET_SHA"
