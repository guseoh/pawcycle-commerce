#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=infra/production/release-common.sh
source "$SCRIPT_DIR/release-common.sh"

usage() {
  cat <<'EOF'
Usage: deploy.sh --sha <40-char-sha> --backend-image <ghcr-repository> --frontend-image <ghcr-repository> [options]

Options:
  --runtime-dir <path>  Materialized runtime bundle root (default: /opt/pawcycle/runtime)
  --state-dir <path>    Release state directory (default: /opt/pawcycle/state)
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

initialize_release_context

CURRENT_SHA=""
if [[ -f "$PAWCYCLE_STATE_DIR/current-sha" ]]; then
  CURRENT_SHA="$(<"$PAWCYCLE_STATE_DIR/current-sha")"
  validate_sha "$CURRENT_SHA"
fi

if [[ -n "$CURRENT_SHA" && "$CURRENT_SHA" != "$TARGET_SHA" ]]; then
  validate_release_contract_compatibility "$CURRENT_SHA" "$TARGET_SHA"
  printf 'Preflighting rollback release before activation: %s\n' "$CURRENT_SHA"
  preflight_release "$CURRENT_SHA"
fi

printf 'Preflighting target release without changing running containers: %s\n' "$TARGET_SHA"
preflight_release "$TARGET_SHA"

if ! activate_release "$TARGET_SHA"; then
  printf 'Target release failed health or smoke validation: %s\n' "$TARGET_SHA" >&2
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
fi
write_state current-sha "$TARGET_SHA"

ACTIVE_SHA="$TARGET_SHA"
compose ps || printf 'WARNING: release succeeded, but final compose ps failed\n' >&2
printf 'Release activated: %s\n' "$TARGET_SHA"
