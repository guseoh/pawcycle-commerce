#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROMETHEUS_IMAGE="prom/prometheus:v3.13.2@sha256:508729e0e2d18e11fd742a5a5ca70e557b940a93948c3c95fd0123a6fd538b69"
GRAFANA_IMAGE="grafana/grafana:13.1.3@sha256:ab5cb380e3ff3172d6c8bd2e7cfd31cce977d2881b260e1f5bc089bf0b759b43"
TEMP_DIR="$(mktemp -d)"
VALIDATION_ID="obs-validation-${RANDOM}-$$"
PROJECT_NAME="pawcycle-$VALIDATION_ID"
PROMETHEUS_VOLUME="pawcycle-$VALIDATION_ID-prometheus-data"
GRAFANA_VOLUME="pawcycle-$VALIDATION_ID-grafana-data"
PROMETHEUS_PORT="$((20000 + RANDOM % 10000))"
GRAFANA_PORT="$((30000 + RANDOM % 10000))"

compose_validation() {
  PAWCYCLE_METRICS_TARGET="metrics-proxy.example.invalid:9464" \
  PAWCYCLE_GRAFANA_ADMIN_USER_FILE="$TEMP_DIR/grafana-admin-user" \
  PAWCYCLE_GRAFANA_ADMIN_PASSWORD_FILE="$TEMP_DIR/grafana-admin-password" \
  PAWCYCLE_OBSERVABILITY_PROMETHEUS_VOLUME="$PROMETHEUS_VOLUME" \
  PAWCYCLE_OBSERVABILITY_GRAFANA_VOLUME="$GRAFANA_VOLUME" \
  PAWCYCLE_OBSERVABILITY_PROMETHEUS_PORT="$PROMETHEUS_PORT" \
  PAWCYCLE_OBSERVABILITY_GRAFANA_PORT="$GRAFANA_PORT" \
    docker compose --project-name "$PROJECT_NAME" --file "$SCRIPT_DIR/compose.yaml" "$@"
}

cleanup() {
  local status=$?
  set +e
  compose_validation down --volumes --remove-orphans >/dev/null 2>&1
  rm -rf -- "$TEMP_DIR"
  return "$status"
}
trap cleanup EXIT

printf 'admin\n' > "$TEMP_DIR/grafana-admin-user"
printf 'validation-only-password\n' > "$TEMP_DIR/grafana-admin-password"
chmod 400 "$TEMP_DIR/grafana-admin-user" "$TEMP_DIR/grafana-admin-password"
docker run --rm --volume "$TEMP_DIR:/run/pawcycle-secrets" alpine:3.22 \
  chown 472:472 /run/pawcycle-secrets/grafana-admin-user /run/pawcycle-secrets/grafana-admin-password
compose_validation config --quiet
compose_validation config --format json > "$TEMP_DIR/compose-model.json"

docker run --rm --entrypoint sh \
  --volume "$SCRIPT_DIR/prometheus/prometheus.yml.tpl:/template:ro" \
  "$PROMETHEUS_IMAGE" -ec \
  'sed "s|__PAWCYCLE_METRICS_TARGET__|metrics-proxy.example.invalid:9464|g" /template >/tmp/prometheus.yml && promtool check config /tmp/prometheus.yml'

if command -v python3 >/dev/null 2>&1; then
  PYTHON=(python3)
else
  PYTHON=(py -3)
fi
"${PYTHON[@]}" - "$SCRIPT_DIR" "$TEMP_DIR/compose-model.json" <<'PY'
import json
import sys
from pathlib import Path

root = Path(sys.argv[1])
compose_model = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
prometheus = compose_model["services"]["prometheus"]
prometheus_entrypoint = prometheus["entrypoint"]
prometheus_command = prometheus["command"]
assert prometheus_entrypoint == ["sh", "-ec"], "Prometheus entrypoint must invoke sh -ec explicitly"
assert len(prometheus_command) == 1, "Prometheus shell script must remain one command argument"
script = prometheus_command[0]
assert "sed " in script, "Prometheus command must render the runtime target"
assert "__PAWCYCLE_METRICS_TARGET__" in script, "Prometheus command must preserve the template placeholder"
assert "PAWCYCLE_METRICS_TARGET" in script, "Prometheus command must preserve runtime target expansion"
assert "exec /bin/prometheus" in script, "Prometheus command must exec the server after rendering config"

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

compose_validation up --detach --wait --wait-timeout 60
compose_validation exec --no-TTY prometheus \
  grep -Fq 'metrics-proxy.example.invalid:9464' /etc/prometheus-runtime/prometheus.yml
compose_validation down --volumes --remove-orphans >/dev/null

printf 'Production observability Compose, Prometheus, Grafana dashboard, and ARM64 image validation passed\n'
