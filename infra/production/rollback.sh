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

if [[ -z "$TARGET_SHA" && -f "$PAWCYCLE_STATE_DIR/previous-sha" ]]; then
  TARGET_SHA="$(<"$PAWCYCLE_STATE_DIR/previous-sha")"
fi

initialize_release_context

[[ -f "$PAWCYCLE_STATE_DIR/current-sha" ]] || die "current release state is missing"
CURRENT_SHA="$(<"$PAWCYCLE_STATE_DIR/current-sha")"
validate_sha "$CURRENT_SHA"
[[ "$TARGET_SHA" != "$CURRENT_SHA" ]] || die "rollback target equals current release"

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
write_state current-sha "$TARGET_SHA"

ACTIVE_SHA="$TARGET_SHA"
compose ps
printf 'Rollback activated without database restoration or volume deletion: %s\n' "$TARGET_SHA"
