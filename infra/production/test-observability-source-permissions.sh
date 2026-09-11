#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
RUNBOOK="$REPO_ROOT/docs/runbook/OPS-OBS-001-production-observability.md"
TEST_ROOT="$(mktemp -d)"
SOURCE_ROOT="$TEST_ROOT/source"
DEFECTIVE_ROOT="$TEST_ROOT/defective"

cleanup() {
  local status=$?
  set +e
  find "$TEST_ROOT" -type d -exec chmod u+rwx {} + >/dev/null 2>&1
  rm -rf -- "$TEST_ROOT"
  exit "$status"
}
trap cleanup EXIT

die() {
  printf '%s\n' "$1" >&2
  exit 1
}

umask 077
APPROVED_SHA="$(git -C "$REPO_ROOT" rev-parse HEAD)"
git -C "$REPO_ROOT" cat-file -e "${APPROVED_SHA}^{commit}"
mkdir -p "$SOURCE_ROOT"
git -C "$REPO_ROOT" archive --format=tar "$APPROVED_SHA" \
  infra/production-metrics-proxy \
  infra/production-observability \
  infra/production/verify-observability-application-identity.sh | \
  tar -x -C "$SOURCE_ROOT"
printf '%s\n' "$APPROVED_SHA" > "$SOURCE_ROOT/.approved-sha"

find "$SOURCE_ROOT" -type d -exec chmod 0555 {} +
find "$SOURCE_ROOT" -type f -exec chmod 0444 {} +

test "$(stat -c '%a' "$SOURCE_ROOT")" = '555' \
  || die 'materialized source root is not mode 0555'
test -z "$(find "$SOURCE_ROOT" -type d ! -perm 0555 -print -quit)" \
  || die 'materialized source contains a directory that is not mode 0555'
test -z "$(find "$SOURCE_ROOT" -type f ! -perm 0444 -print -quit)" \
  || die 'materialized source contains a regular file that is not mode 0444'

for required_path in \
  "$SOURCE_ROOT/infra/production-observability/prometheus" \
  "$SOURCE_ROOT/infra/production-observability/grafana/provisioning" \
  "$SOURCE_ROOT/infra/production-observability/grafana/dashboards" \
  "$SOURCE_ROOT/infra/production-metrics-proxy"; do
  test -d "$required_path" || die "required source directory is missing: $required_path"
done

for required_path in \
  "$SOURCE_ROOT/infra/production-observability/prometheus/prometheus.yml.tpl" \
  "$SOURCE_ROOT/infra/production-observability/grafana/provisioning/dashboards/pawcycle.yaml" \
  "$SOURCE_ROOT/infra/production-observability/grafana/dashboards/production-overview.json" \
  "$SOURCE_ROOT/infra/production-metrics-proxy/compose.yaml"; do
  test -f "$required_path" || die "required source file is missing: $required_path"
done

docker run --rm --user 65534:65534 --volume "$SOURCE_ROOT:/source:ro" alpine:3.22 sh -ec '
  set -eu
  for directory in \
    /source \
    /source/infra/production-observability/prometheus \
    /source/infra/production-observability/grafana/provisioning \
    /source/infra/production-observability/grafana/dashboards \
    /source/infra/production-metrics-proxy; do
    test -d "$directory"
    test -x "$directory"
  done
  find /source -type f -exec test -r {} \;
  test -r /source/infra/production-observability/prometheus/prometheus.yml.tpl
  test -r /source/infra/production-observability/grafana/provisioning/dashboards/pawcycle.yaml
  test -r /source/infra/production-observability/grafana/dashboards/production-overview.json
' || die 'non-owner container user cannot traverse/read the normalized source'

cp -a "$SOURCE_ROOT" "$DEFECTIVE_ROOT"
find "$DEFECTIVE_ROOT" -type d -exec chmod 0500 {} +
find "$DEFECTIVE_ROOT" -type f -exec chmod 0400 {} +
if docker run --rm --user 65534:65534 --volume "$DEFECTIVE_ROOT:/source:ro" alpine:3.22 sh -ec '
  set -eu
  test -x /source/infra/production-observability/prometheus
  test -r /source/infra/production-observability/prometheus/prometheus.yml.tpl
' >/dev/null 2>&1; then
  die 'defective 0500/0400 source unexpectedly passed for a non-owner container user'
fi

grep -Fq 'find "$SOURCE_ROOT" -type d -exec chmod 0555 {} +' "$RUNBOOK" \
  || die 'Runbook does not normalize source directories to mode 0555'
grep -Fq 'find "$SOURCE_ROOT" -type f -exec chmod 0444 {} +' "$RUNBOOK" \
  || die 'Runbook does not normalize source regular files to mode 0444'
if grep -Fq 'chmod -R a-w "$SOURCE_ROOT"' "$RUNBOOK"; then
  die 'Runbook still contains the defective recursive a-w source contract'
fi

printf '%s\n' 'OCI observability archive source permission and non-owner bind-mount regression passed'
