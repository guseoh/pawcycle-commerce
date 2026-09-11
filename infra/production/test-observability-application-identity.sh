#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/../.." && pwd -P)"
RUNBOOK="$ROOT_DIR/docs/runbook/OPS-OBS-001-production-observability.md"
TEST_ROOT="$(mktemp -d)"
cleanup() {
  chmod -R u+w "$TEST_ROOT" 2>/dev/null || true
  rm -rf -- "$TEST_ROOT"
}
trap cleanup EXIT

assert_contains() {
  local needle="$1"
  grep -Fq -- "$needle" "$RUNBOOK" || {
    printf 'Runbook is missing required contract: %s\n' "$needle" >&2
    exit 1
  }
}

assert_not_contains() {
  local needle="$1"
  if grep -Fq -- "$needle" "$RUNBOOK"; then
    printf 'Runbook contains retired unsafe contract: %s\n' "$needle" >&2
    exit 1
  fi
}

assert_contains 'SOURCE_ROOT="/opt/pawcycle/observability-source/$APPROVED_SHA"'
assert_contains 'archive --format=tar'
assert_contains 'tar -x -C "$SOURCE_ROOT"'
assert_contains 'install -d -o opc -g opc -m 0750'
assert_contains 'chmod -R a-w "$SOURCE_ROOT"'
assert_contains 'APP_CONTAINER_IDS_BEFORE='
assert_contains 'APP_CONTAINER_IDS_AFTER='
assert_contains 'APP_IMAGE_IDS_BEFORE='
assert_contains 'APP_IMAGE_IDS_AFTER='
assert_contains 'test "$APP_CONTAINER_IDS_AFTER" = "$APP_CONTAINER_IDS_BEFORE"'
assert_contains 'test "$APP_IMAGE_IDS_AFTER" = "$APP_IMAGE_IDS_BEFORE"'
assert_contains '/opt/pawcycle/runtime/observability/grafana-admin-user'
assert_contains '/opt/pawcycle/runtime/observability/grafana-admin-password'
assert_contains "'linux/amd64'"

assert_not_contains 'git worktree add'
assert_not_contains '/opt/pawcycle/metrics-proxy-control'
assert_not_contains '/opt/pawcycle/observability-control'
assert_not_contains 'chown -R'

# Reproduce the safe materialization contract with a normal user-owned source.
# The fixture ensures the source remains traversable after it is made read-only;
# a root-owned 0700 worktree would fail this exact access boundary.
FIXTURE_REPO="$TEST_ROOT/control"
FIXTURE_SOURCE="$TEST_ROOT/source"
mkdir -p "$FIXTURE_REPO/infra/production-metrics-proxy" \
  "$FIXTURE_REPO/infra/production-observability"
git -C "$FIXTURE_REPO" init -q
git -C "$FIXTURE_REPO" config user.name validation
git -C "$FIXTURE_REPO" config user.email validation@example.invalid
printf '%s\n' 'metrics' > "$FIXTURE_REPO/infra/production-metrics-proxy/compose.yaml"
printf '%s\n' 'observability' > "$FIXTURE_REPO/infra/production-observability/compose.yaml"
git -C "$FIXTURE_REPO" add infra
git -C "$FIXTURE_REPO" commit -qm fixture

mkdir -m 700 "$FIXTURE_SOURCE"
git -C "$FIXTURE_REPO" archive --format=tar HEAD \
  infra/production-metrics-proxy infra/production-observability | \
  tar -x -C "$FIXTURE_SOURCE"
chmod -R a-w "$FIXTURE_SOURCE"
test -r "$FIXTURE_SOURCE/infra/production-metrics-proxy/compose.yaml"
test -r "$FIXTURE_SOURCE/infra/production-observability/compose.yaml"
test ! -w "$FIXTURE_SOURCE"

printf 'Observability source ownership and Application identity regression passed\n'
