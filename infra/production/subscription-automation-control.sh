#!/usr/bin/env bash

set -Eeuo pipefail

case "$-" in
  *x*) set +x ;;
esac

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=infra/production/release-common.sh
source "$SCRIPT_DIR/release-common.sh"

usage() {
  cat <<'EOF'
Usage: subscription-automation-control.sh <activate|deactivate> --backend-image <ghcr-repository> --frontend-image <ghcr-repository> [options]

Options:
  --max-due-candidates <count>  Required for activate; user-approved maximum from the first-activation review
  --runtime-dir <path>          Materialized runtime bundle root (default: /opt/pawcycle/runtime)
  --state-dir <path>            Release state directory (default: /opt/pawcycle/state)

The runtime bundle must contain explicit automation enabled, batch-size, and
fixed-delay-ms values. Application deploy/rollback never enables the Scheduler;
this command only recreates the current Release Backend after read-only preflight.
EOF
}

ACTION="${1:-}"
if [[ "$ACTION" == "activate" || "$ACTION" == "deactivate" ]]; then
  shift
else
  usage >&2
  die "command must be activate or deactivate"
fi

BACKEND_IMAGE=""
FRONTEND_IMAGE=""
MAX_DUE_CANDIDATES=""
PAWCYCLE_RUNTIME_DIR="/opt/pawcycle/runtime"
PAWCYCLE_STATE_DIR="/opt/pawcycle/state"

while (( $# > 0 )); do
  case "$1" in
    --backend-image) BACKEND_IMAGE="${2:-}"; shift 2 ;;
    --frontend-image) FRONTEND_IMAGE="${2:-}"; shift 2 ;;
    --max-due-candidates) MAX_DUE_CANDIDATES="${2:-}"; shift 2 ;;
    --runtime-dir) PAWCYCLE_RUNTIME_DIR="${2:-}"; shift 2 ;;
    --state-dir) PAWCYCLE_STATE_DIR="${2:-}"; shift 2 ;;
    --help) usage; exit 0 ;;
    *) usage >&2; die "unknown argument: $1" ;;
  esac
done

if [[ "$ACTION" == "activate" ]]; then
  [[ "$MAX_DUE_CANDIDATES" =~ ^(0|[1-9][0-9]*)$ ]] \
    || die "activate requires --max-due-candidates with an explicitly approved non-negative integer"
elif [[ -n "$MAX_DUE_CANDIDATES" ]]; then
  die "--max-due-candidates is accepted only for activate"
fi

prepare_release_context
acquire_release_lock
CURRENT_SHA="$(read_state_sha current-sha)"
load_runtime_contract

EXPECTED_BUNDLE_ENABLED=false
EXPECTED_RUNNING_ENABLED=any
PREFLIGHT_ARGS=()
if [[ "$ACTION" == "activate" ]]; then
  EXPECTED_BUNDLE_ENABLED=true
  EXPECTED_RUNNING_ENABLED=false
  PREFLIGHT_ARGS+=(--max-due-candidates "$MAX_DUE_CANDIDATES")
fi
require_subscription_automation_mode "$EXPECTED_BUNDLE_ENABLED"

run_automation_preflight() {
  local expected_running_enabled="$1"

  "$SCRIPT_DIR/subscription-automation-preflight.sh" \
    --backend-image "$BACKEND_IMAGE" \
    --frontend-image "$FRONTEND_IMAGE" \
    --expect-bundle-enabled "$EXPECTED_BUNDLE_ENABLED" \
    --expect-running-enabled "$expected_running_enabled" \
    --runtime-dir "$PAWCYCLE_RUNTIME_DIR" \
    --state-dir "$PAWCYCLE_STATE_DIR" \
    "${PREFLIGHT_ARGS[@]}"
}

if [[ "$ACTION" == "activate" ]]; then
  printf 'Preflighting current application release before Scheduler activation: %s\n' "$CURRENT_SHA"
  if ! preflight_release "$CURRENT_SHA" || ! run_automation_preflight false; then
    stop_backend_service
    die "Scheduler activation preflight failed; Backend was stopped so automation cannot continue, and the managed database was not modified by the Application release lifecycle"
  fi
else
  printf 'Recreating current application Backend with Scheduler OFF before postflight: %s\n' "$CURRENT_SHA"
fi

if ! activate_backend_runtime "$CURRENT_SHA"; then
  stop_backend_service
  die "Scheduler $ACTION failed; Backend was stopped so automation cannot continue, and the managed database was not modified by the Application release lifecycle"
fi

if ! run_automation_preflight "$EXPECTED_BUNDLE_ENABLED"; then
  if [[ "$ACTION" == "activate" ]]; then
    stop_backend_service
    die "Scheduler activation postflight failed; Backend was stopped so automation cannot continue, and the managed database was not modified by the Application release lifecycle"
  fi
  die "Scheduler deactivation postflight failed; Scheduler remains OFF and the managed database was not modified by the Application release lifecycle"
fi

if [[ "$ACTION" == "activate" ]]; then
  printf 'Subscription automation activated for the current Release; continue aggregate observation before declaring success\n'
else
  printf 'Subscription automation deactivated for the current Release; Backend health and smoke checks passed\n'
fi
