#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
TEST_ROOT="$(mktemp -d)"
CONTROL_DIR="$TEST_ROOT/control"
STATE_DIR="$TEST_ROOT/state"
FAKE_BIN="$TEST_ROOT/bin"
CURRENT_SHA="1111111111111111111111111111111111111111"
TARGET_SHA="2222222222222222222222222222222222222222"
trap 'rm -rf -- "$TEST_ROOT"' EXIT

mkdir -p "$CONTROL_DIR/.git" "$STATE_DIR" "$FAKE_BIN"
printf '%s\n' "$CURRENT_SHA" >"$STATE_DIR/current-sha"
chmod 600 "$STATE_DIR/current-sha"
cat >"$CONTROL_DIR/infra-production-deploy.sh" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >"$FAKE_DEPLOY_OUTPUT"
EOF
chmod +x "$CONTROL_DIR/infra-production-deploy.sh"

cat >"$FAKE_BIN/git" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
if [[ "$*" == *'config --get remote.origin.url'* ]]; then
  origin="${FAKE_ORIGIN:-valid}"
  [[ "$origin" == valid ]] && printf '%s\n' 'https://github.com/example/pawcycle-commerce.git' || printf '%s\n' "$origin"
elif [[ "$*" == *' fetch --prune origin main'* ]]; then
  exit 0
elif [[ "$*" == *'rev-parse --verify HEAD'* ]]; then
  printf '%s\n' "$FAKE_CONTROL_SHA"
elif [[ "$*" == *'cat-file -e'* ]]; then
  exit 0
elif [[ "$*" == *'merge-base --is-ancestor'* ]]; then
  if [[ "${FAKE_ANCESTOR:-yes}" == no && "$*" == *"$FAKE_CURRENT_SHA $FAKE_TARGET_SHA"* ]]; then exit 1; fi
  exit 0
else
  printf 'unexpected fake git call: %s\n' "$*" >&2
  exit 1
fi
EOF
chmod +x "$FAKE_BIN/git"

PATCHED="$TEST_ROOT/production-command-dispatch.sh"
sed \
  -e "s#CONTROL_DIR=\"/opt/pawcycle/control\"#CONTROL_DIR=\"$CONTROL_DIR\"#" \
  -e "s#STATE_DIR=\"/opt/pawcycle/state\"#STATE_DIR=\"$STATE_DIR\"#" \
  -e "s#\"\$CONTROL_DIR/infra/production/deploy.sh\"#\"\$CONTROL_DIR/infra-production-deploy.sh\"#" \
  "$SCRIPT_DIR/production-command-dispatch.sh" >"$PATCHED"
chmod +x "$PATCHED"

if (( EUID != 0 )); then
  grep -Fq 'CONTROL_DIR="/opt/pawcycle/control"' "$SCRIPT_DIR/production-command-dispatch.sh"
  grep -Fq 'STATE_DIR="/opt/pawcycle/state"' "$SCRIPT_DIR/production-command-dispatch.sh"
  grep -Fq '[[ "$EUID" == 0 ]]' "$SCRIPT_DIR/production-command-dispatch.sh"
  printf 'Production command dispatcher static contract tests passed (root execution unavailable)\n'
  exit 0
fi

PATH="$FAKE_BIN:$PATH" FAKE_CONTROL_SHA="$CURRENT_SHA" FAKE_CURRENT_SHA="$CURRENT_SHA" FAKE_TARGET_SHA="$TARGET_SHA" \
  FAKE_DEPLOY_OUTPUT="$TEST_ROOT/deploy-output" "$PATCHED" --operation deploy --target-sha "$TARGET_SHA" >/dev/null
grep -Fq -- "--operation deploy --sha $TARGET_SHA" "$TEST_ROOT/deploy-output"
grep -Fq -- 'ghcr.io/example/pawcycle-commerce-backend' "$TEST_ROOT/deploy-output"
grep -Fq -- 'ghcr.io/example/pawcycle-commerce-frontend' "$TEST_ROOT/deploy-output"

rm -f -- "$TEST_ROOT/deploy-output"
PATH="$FAKE_BIN:$PATH" FAKE_CONTROL_SHA="$CURRENT_SHA" FAKE_CURRENT_SHA="$CURRENT_SHA" FAKE_TARGET_SHA="$CURRENT_SHA" \
  FAKE_DEPLOY_OUTPUT="$TEST_ROOT/deploy-output" "$PATCHED" --operation control-adopt --target-sha "$CURRENT_SHA" >/dev/null
grep -Fq -- '--operation control-adopt --sha' "$TEST_ROOT/deploy-output"

if PATH="$FAKE_BIN:$PATH" FAKE_CONTROL_SHA="$CURRENT_SHA" FAKE_CURRENT_SHA="$CURRENT_SHA" FAKE_TARGET_SHA="$TARGET_SHA" FAKE_ANCESTOR=no \
  FAKE_DEPLOY_OUTPUT="$TEST_ROOT/deploy-output" "$PATCHED" --operation deploy --target-sha "$TARGET_SHA" >/dev/null 2>&1; then
  printf 'older or divergent deploy was accepted\n' >&2
  exit 1
fi
if PATH="$FAKE_BIN:$PATH" FAKE_ORIGIN='git@github.com:example/pawcycle-commerce.git' FAKE_CONTROL_SHA="$CURRENT_SHA" \
  FAKE_CURRENT_SHA="$CURRENT_SHA" FAKE_TARGET_SHA="$TARGET_SHA" "$PATCHED" --operation preflight --target-sha "$TARGET_SHA" >/dev/null 2>&1; then
  printf 'non-HTTPS origin was accepted\n' >&2
  exit 1
fi
if PATH="$FAKE_BIN:$PATH" FAKE_CONTROL_SHA="$CURRENT_SHA" FAKE_CURRENT_SHA="$CURRENT_SHA" FAKE_TARGET_SHA="$TARGET_SHA" \
  "$PATCHED" --operation deploy --target-sha bad >/dev/null 2>&1; then
  printf 'invalid SHA was accepted\n' >&2
  exit 1
fi

printf 'Production command dispatcher fake contract tests passed\n'
