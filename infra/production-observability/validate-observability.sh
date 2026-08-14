#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROMETHEUS_IMAGE="prom/prometheus:v3.13.2@sha256:508729e0e2d18e11fd742a5a5ca70e557b940a93948c3c95fd0123a6fd538b69"
GRAFANA_IMAGE="grafana/grafana:13.1.3@sha256:ab5cb380e3ff3172d6c8bd2e7cfd31cce977d2881b260e1f5bc089bf0b759b43"
TEMP_DIR="$(mktemp -d)"

cleanup() {
  rm -rf -- "$TEMP_DIR"
}
trap cleanup EXIT

touch "$TEMP_DIR/grafana-admin-user" "$TEMP_DIR/grafana-admin-password"
PAWCYCLE_METRICS_TARGET="metrics-proxy.example.invalid:9464" \
PAWCYCLE_GRAFANA_ADMIN_USER_FILE="$TEMP_DIR/grafana-admin-user" \
PAWCYCLE_GRAFANA_ADMIN_PASSWORD_FILE="$TEMP_DIR/grafana-admin-password" \
  docker compose --file "$SCRIPT_DIR/compose.yaml" config --quiet

docker run --rm --entrypoint sh \
  --volume "$SCRIPT_DIR/prometheus/prometheus.yml.tpl:/template:ro" \
  "$PROMETHEUS_IMAGE" -ec \
  'sed "s|__PAWCYCLE_METRICS_TARGET__|metrics-proxy.example.invalid:9464|g" /template >/tmp/prometheus.yml && promtool check config /tmp/prometheus.yml'

if command -v python3 >/dev/null 2>&1; then
  PYTHON=(python3)
else
  PYTHON=(py -3)
fi
"${PYTHON[@]}" - "$SCRIPT_DIR" <<'PY'
import json
import sys
from pathlib import Path

root = Path(sys.argv[1])
dashboards = sorted((root / "grafana" / "dashboards").glob("*.json"))
assert len(dashboards) == 3, "exactly three Grafana dashboards must be provisioned"
titles = {json.loads(path.read_text(encoding="utf-8"))["title"] for path in dashboards}
assert titles == {"Production Overview", "Runtime", "PawCycle Operations"}, "unexpected Grafana dashboard titles"
for path in dashboards:
    dashboard = json.loads(path.read_text(encoding="utf-8"))
    assert dashboard["editable"] is False, f"{path.name} must be provisioned read-only"
PY

for image in "$PROMETHEUS_IMAGE" "$GRAFANA_IMAGE"; do
  manifest="$(docker buildx imagetools inspect "$image")"
  grep -Fq 'Platform:  linux/arm64' <<<"$manifest" || {
    printf 'linux/arm64 manifest missing for %s\n' "$image" >&2
    exit 1
  }
done

printf 'Production observability Compose, Prometheus, Grafana dashboard, and ARM64 image validation passed\n'
