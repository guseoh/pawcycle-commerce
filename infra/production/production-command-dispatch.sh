#!/usr/bin/env bash

set -Eeuo pipefail
set +x

CONTROL_DIR="/opt/pawcycle/control"
STATE_DIR="/opt/pawcycle/state"
OPERATION=""
TARGET_SHA=""
APPROVED_CONTRACT_FROM_SHA=""
APPROVED_CONTROL_SHA=""
APPROVED_MIGRATION_TARGET_SHA=""

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

usage() {
  printf 'Usage: %s --operation <preflight|deploy|control-adopt> --target-sha <sha> [approval SHA options]\n' "${0##*/}" >&2
}

validate_sha() {
  [[ "$1" =~ ^[0-9a-f]{40}$ ]] || die "SHA must be exactly 40 lowercase hexadecimal characters"
}

while (($#)); do
  case "$1" in
    --operation) [[ $# -gt 1 ]] || die "--operation requires a value"; OPERATION="$2"; shift 2 ;;
    --target-sha) [[ $# -gt 1 ]] || die "--target-sha requires a value"; TARGET_SHA="$2"; shift 2 ;;
    --approved-contract-from-sha) [[ $# -gt 1 ]] || die "--approved-contract-from-sha requires a value"; APPROVED_CONTRACT_FROM_SHA="$2"; shift 2 ;;
    --approved-control-sha) [[ $# -gt 1 ]] || die "--approved-control-sha requires a value"; APPROVED_CONTROL_SHA="$2"; shift 2 ;;
    --approved-migration-target-sha) [[ $# -gt 1 ]] || die "--approved-migration-target-sha requires a value"; APPROVED_MIGRATION_TARGET_SHA="$2"; shift 2 ;;
    --help) usage; exit 0 ;;
    *) usage; die "unknown argument" ;;
  esac
done

[[ "$EUID" == 0 ]] || die "root execution is required"
[[ "$OPERATION" == preflight || "$OPERATION" == deploy || "$OPERATION" == control-adopt ]] || die "operation is invalid"
validate_sha "$TARGET_SHA"
for approved_sha in "$APPROVED_CONTRACT_FROM_SHA" "$APPROVED_CONTROL_SHA" "$APPROVED_MIGRATION_TARGET_SHA"; do
  [[ -z "$approved_sha" ]] || validate_sha "$approved_sha"
done

[[ -d "$CONTROL_DIR/.git" ]] || die "control worktree is unavailable"
[[ -d "$STATE_DIR" && ! -L "$STATE_DIR" ]] || die "state directory is unavailable or unsafe"
ORIGIN_URL="$(git -C "$CONTROL_DIR" config --get remote.origin.url 2>/dev/null || true)"
[[ "$ORIGIN_URL" =~ ^https://github\.com/[a-z0-9][a-z0-9._-]*/[a-z0-9][a-z0-9._-]*\.git$ ]] \
  || die "control origin must use the approved HTTPS GitHub repository form"
REPOSITORY="${ORIGIN_URL#https://github.com/}"
REPOSITORY="${REPOSITORY%.git}"

git -C "$CONTROL_DIR" fetch --prune origin main >/dev/null
CONTROL_SHA="$(git -C "$CONTROL_DIR" rev-parse --verify HEAD)"
validate_sha "$CONTROL_SHA"
git -C "$CONTROL_DIR" cat-file -e "${CONTROL_SHA}^{commit}"
git -C "$CONTROL_DIR" merge-base --is-ancestor "$CONTROL_SHA" refs/remotes/origin/main \
  || die "current control HEAD is not contained in fetched origin/main"
git -C "$CONTROL_DIR" cat-file -e "${TARGET_SHA}^{commit}"
git -C "$CONTROL_DIR" merge-base --is-ancestor "$TARGET_SHA" refs/remotes/origin/main \
  || die "target SHA is not contained in fetched origin/main"

CURRENT_SHA=""
CURRENT_SHA_FILE="$STATE_DIR/current-sha"
if [[ -e "$CURRENT_SHA_FILE" || -L "$CURRENT_SHA_FILE" ]]; then
  [[ -f "$CURRENT_SHA_FILE" && ! -L "$CURRENT_SHA_FILE" && "$(stat -c '%a' "$CURRENT_SHA_FILE")" == 600 ]] \
    || die "current-sha state must be a regular mode-600 file"
  CURRENT_SHA="$(<"$CURRENT_SHA_FILE")"
  validate_sha "$CURRENT_SHA"
  git -C "$CONTROL_DIR" cat-file -e "${CURRENT_SHA}^{commit}"
  git -C "$CONTROL_DIR" merge-base --is-ancestor "$CURRENT_SHA" refs/remotes/origin/main \
    || die "current release SHA is not contained in fetched origin/main"
  if [[ "$OPERATION" == deploy && "$TARGET_SHA" != "$CURRENT_SHA" ]]; then
    git -C "$CONTROL_DIR" merge-base --is-ancestor "$CURRENT_SHA" "$TARGET_SHA" \
      || die "deploy refuses an older or divergent release"
  fi
fi

BACKEND_IMAGE="ghcr.io/${REPOSITORY}-backend"
FRONTEND_IMAGE="ghcr.io/${REPOSITORY}-frontend"
exec /usr/bin/env bash "$CONTROL_DIR/infra/production/deploy.sh" \
  --operation "$OPERATION" \
  --sha "$TARGET_SHA" \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE" \
  --approved-contract-from-sha "$APPROVED_CONTRACT_FROM_SHA" \
  --approved-control-sha "$APPROVED_CONTROL_SHA" \
  --approved-migration-target-sha "$APPROVED_MIGRATION_TARGET_SHA"
