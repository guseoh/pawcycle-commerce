#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=infra/production/release-common.sh
source "$SCRIPT_DIR/release-common.sh"

usage() {
  cat <<'EOF'
Usage: deploy.sh --sha <40-char-sha> --backend-image <ghcr-repository> --frontend-image <ghcr-repository> [options]

Options:
  --operation <preflight|deploy|control-adopt>
                               Read-only approval preflight, activation, or control-only adoption (default: deploy)
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
STATE_TRANSITION_NAME="release-state-transition"

stop_failed_release_applications() {
  local running_backend_ids

  compose stop proxy frontend backend \
    || die "Application stop command failed after release failure; manual intervention is required and MySQL was preserved"
  running_backend_ids="$(compose ps --status running --quiet backend)" \
    || die "Application stop verification failed after release failure; manual intervention is required and MySQL was preserved"
  [[ -z "$running_backend_ids" ]] \
    || die "Backend remains running after release failure; manual intervention is required and MySQL was preserved"
}

abort_state_publication() {
  stop_failed_release_applications
  die "release state publication failed after target activation; the transition marker was preserved, Application services were stopped, and MySQL was preserved"
}

publish_state_or_abort() {
  local name="$1"
  local value="$2"

  write_state "$name" "$value" || abort_state_publication
}

require_quiesced_same_sha_control_transition() {
  local stored_contract_sha="$1"
  local current_release_sha="$2"
  local target_sha="$3"
  local approved_contract_from_sha="$4"
  local approved_control_sha="$5"
  local running_backend_ids

  [[ -z "$ADOPT_CONTRACT_SHA" ]] \
    || die "--adopt-contract-sha cannot approve a quiesced same-SHA control transition"

  require_control_only_contract_adoption \
    "$stored_contract_sha" "$current_release_sha" "$target_sha" \
    "$approved_contract_from_sha" "$approved_control_sha"

  ACTIVE_SHA="$current_release_sha"
  export ACTIVE_SHA
  running_backend_ids="$(compose ps --status running --quiet backend)" \
    || die "unable to verify Backend quiesce before same-SHA control transition"
  [[ -z "$running_backend_ids" ]] \
    || die "same-SHA control transition requires Backend to be quiesced when Control HEAD differs from contract-sha"

  CONTRACT_SHA="$stored_contract_sha"
  CONTRACT_BOUNDARY=1
  printf 'Approved quiesced same-SHA control transition: %s -> %s\n' \
    "$stored_contract_sha" "$PENDING_CONTRACT_SHA"
}

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

[[ "$OPERATION" == "preflight" || "$OPERATION" == "deploy" || "$OPERATION" == "control-adopt" ]] \
  || die "operation must be preflight, deploy, or control-adopt"
if [[ "$OPERATION" == "preflight" ]]; then
  PAWCYCLE_PREFLIGHT_RECORD_IMAGES=false
  initialize_read_only_release_context
elif [[ "$OPERATION" == "control-adopt" ]]; then
  PAWCYCLE_PREFLIGHT_RECORD_IMAGES=false
  initialize_release_context
else
  PAWCYCLE_PREFLIGHT_RECORD_IMAGES=true
  initialize_release_context
fi
# Control-only adoption preserves the current Scheduler mode because it does not activate Application containers.
if [[ "$OPERATION" != "control-adopt" ]]; then
  require_subscription_automation_mode false
fi

if [[ -e "$PAWCYCLE_STATE_DIR/$STATE_TRANSITION_NAME" \
  || -L "$PAWCYCLE_STATE_DIR/$STATE_TRANSITION_NAME" ]]; then
  INCOMPLETE_TARGET_SHA="$(read_state_sha "$STATE_TRANSITION_NAME")"
  die "incomplete release state transition detected for target $INCOMPLETE_TARGET_SHA; explicit recovery is required before another deploy"
fi

CURRENT_SHA=""
if [[ -e "$PAWCYCLE_STATE_DIR/current-sha" || -L "$PAWCYCLE_STATE_DIR/current-sha" ]]; then
  CURRENT_SHA="$(read_state_sha current-sha)"
fi

if [[ "$OPERATION" == "control-adopt" ]]; then
  [[ -z "$ADOPT_CONTRACT_SHA" ]] \
    || die "--adopt-contract-sha is not accepted for control-only contract adoption"
  [[ -z "$APPROVED_MIGRATION_TARGET_SHA" ]] \
    || die "approved_migration_target_sha is not accepted for control-only contract adoption"
  [[ -n "$CURRENT_SHA" ]] \
    || die "control-only contract adoption requires current-sha state"

  STORED_CONTRACT_SHA="$(read_state_sha contract-sha)"
  require_control_only_contract_adoption \
    "$STORED_CONTRACT_SHA" "$CURRENT_SHA" "$TARGET_SHA" \
    "$APPROVED_CONTRACT_FROM_SHA" "$APPROVED_CONTROL_SHA"
  validate_current_release_for_contract_adoption "$CURRENT_SHA"
  if ! write_state contract-sha "$PENDING_CONTRACT_SHA"; then
    die "control-only contract state publication failed; existing Application containers and release state were preserved"
  fi
  printf 'Production control contract adopted without Application activation: %s\n' "$PENDING_CONTRACT_SHA"
  exit 0
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
  if [[ -n "$CURRENT_SHA" && "$CURRENT_SHA" == "$TARGET_SHA" ]]; then
    CONTROL_SHA="$(current_control_sha)"
    if [[ "$STORED_CONTRACT_SHA" == "$CONTROL_SHA" ]]; then
      CONTRACT_BOUNDARY=0
      load_or_adopt_runtime_contract "$ADOPT_CONTRACT_SHA" "$CURRENT_SHA"
    else
      require_quiesced_same_sha_control_transition \
        "$STORED_CONTRACT_SHA" "$CURRENT_SHA" "$TARGET_SHA" \
        "$APPROVED_CONTRACT_FROM_SHA" "$APPROVED_CONTROL_SHA"
    fi
  elif release_contract_changed "$STORED_CONTRACT_SHA" "$TARGET_SHA"; then
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
    stop_failed_release_applications
    die "target release failed across an approved contract or database migration boundary; automatic pre-migration release restoration is blocked, and automatic contract-boundary restoration is blocked, Scheduler remains OFF, and MySQL was preserved"
  fi
  if [[ -n "$CURRENT_SHA" && "$CURRENT_SHA" != "$TARGET_SHA" ]]; then
    printf 'Restoring previous healthy release: %s\n' "$CURRENT_SHA" >&2
    if activate_release "$CURRENT_SHA"; then
      die "target release failed; previous release was restored"
    fi
    die "target release and automatic restoration both failed; MySQL volume was not removed"
  fi
  stop_failed_release_applications
  die "initial release failed; application services were stopped and MySQL was preserved"
fi

PREVIOUS_CONTRACT_SHA="$CONTRACT_SHA"
if ! write_state "$STATE_TRANSITION_NAME" "$TARGET_SHA"; then
  stop_failed_release_applications
  die "unable to start release state publication after target activation; Application services were stopped and MySQL was preserved"
fi

if [[ -n "$CURRENT_SHA" && "$CURRENT_SHA" != "$TARGET_SHA" ]]; then
  publish_state_or_abort previous-sha "$CURRENT_SHA"
  publish_state_or_abort previous-contract-sha "$PREVIOUS_CONTRACT_SHA"
fi
publish_state_or_abort current-sha "$TARGET_SHA"

if [[ -n "$PENDING_CONTRACT_SHA" ]]; then
  if ! write_state contract-sha "$PENDING_CONTRACT_SHA"; then
    abort_state_publication
  fi
  CONTRACT_SHA="$PENDING_CONTRACT_SHA"
  printf 'Production control contract adopted after target activation: %s\n' "$CONTRACT_SHA"
fi

if ! rm -f -- "$PAWCYCLE_STATE_DIR/$STATE_TRANSITION_NAME"; then
  stop_failed_release_applications
  die "release state transition marker cleanup failed; Application services were stopped and MySQL was preserved"
fi

ACTIVE_SHA="$TARGET_SHA"
compose ps || printf 'WARNING: release succeeded, but final compose ps failed\n' >&2
printf 'Release activated: %s\n' "$TARGET_SHA"
