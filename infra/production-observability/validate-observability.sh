#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROMETHEUS_IMAGE="prom/prometheus:v3.13.2@sha256:508729e0e2d18e11fd742a5a5ca70e557b940a93948c3c95fd0123a6fd538b69"
GRAFANA_IMAGE="grafana/grafana:13.1.3@sha256:ab5cb380e3ff3172d6c8bd2e7cfd31cce977d2881b260e1f5bc089bf0b759b43"
TEMP_DIR="$(mktemp -d)"
VALIDATION_ID="obs-validation-${RANDOM}-$$"
PROJECT_NAME="pawcycle-$VALIDATION_ID"
APP_NETWORK="pawcycle-$VALIDATION_ID-app"
PROMETHEUS_VOLUME="pawcycle-$VALIDATION_ID-prometheus-data"
GRAFANA_VOLUME="pawcycle-$VALIDATION_ID-grafana-data"
PROMETHEUS_PORT="$((20000 + RANDOM % 10000))"
GRAFANA_PORT="$((30000 + RANDOM % 10000))"
METRICS_TARGET="metrics-proxy:9464"

compose_validation() {
  PAWCYCLE_METRICS_TARGET="$METRICS_TARGET" \
  PAWCYCLE_APP_NETWORK="$APP_NETWORK" \
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
  docker network rm "$APP_NETWORK" >/dev/null 2>&1
  rm -rf -- "$TEMP_DIR"
  return "$status"
}
trap cleanup EXIT

printf 'admin\n' > "$TEMP_DIR/grafana-admin-user"
printf 'validation-only-password\n' > "$TEMP_DIR/grafana-admin-password"
chmod 400 "$TEMP_DIR/grafana-admin-user" "$TEMP_DIR/grafana-admin-password"
docker run --rm --volume "$TEMP_DIR:/run/pawcycle-secrets" alpine:3.22 \
  chown 472:472 /run/pawcycle-secrets/grafana-admin-user /run/pawcycle-secrets/grafana-admin-password
docker network create "$APP_NETWORK" >/dev/null
compose_validation config --quiet
compose_validation config --format json > "$TEMP_DIR/compose-model.json"

docker run --rm --entrypoint sh \
  --volume "$SCRIPT_DIR/prometheus/prometheus.yml.tpl:/template:ro" \
  "$PROMETHEUS_IMAGE" -ec \
  'sed "s|__PAWCYCLE_METRICS_TARGET__|metrics-proxy:9464|g" /template >/tmp/prometheus.yml && promtool check config /tmp/prometheus.yml'

if command -v python3 >/dev/null 2>&1; then
  PYTHON=(python3)
else
  PYTHON=(py -3)
fi
"${PYTHON[@]}" - "$SCRIPT_DIR" "$TEMP_DIR/compose-model.json" "$APP_NETWORK" "$PROMETHEUS_PORT" "$GRAFANA_PORT" <<'PY'
import json
import sys
from pathlib import Path

root = Path(sys.argv[1])
compose_model = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
prometheus = compose_model["services"]["prometheus"]
grafana = compose_model["services"]["grafana"]
prometheus_entrypoint = prometheus["entrypoint"]
prometheus_command = prometheus["command"]
assert set(compose_model["services"]) == {"prometheus", "grafana"}, "observability must own only Prometheus and Grafana"
assert "mysql" not in compose_model["services"], "observability must not own a database container"
assert prometheus_entrypoint == ["sh", "-ec"], "Prometheus entrypoint must invoke sh -ec explicitly"
assert len(prometheus_command) == 1, "Prometheus shell script must remain one command argument"
script = prometheus_command[0]
assert "sed " in script, "Prometheus command must render the runtime target"
assert "__PAWCYCLE_METRICS_TARGET__" in script, "Prometheus command must preserve the template placeholder"
assert "PAWCYCLE_METRICS_TARGET" in script, "Prometheus command must preserve runtime target expansion"
assert "exec /bin/prometheus" in script, "Prometheus command must exec the server after rendering config"
assert set(prometheus["networks"]) == {"app", "observability"}, "Prometheus must bridge app and observability networks only"
assert set(grafana["networks"]) == {"observability"}, "Grafana must stay isolated from the Application network"
assert compose_model["networks"]["app"]["external"] is True, "Application Docker network must remain external"
assert compose_model["networks"]["app"]["name"] == sys.argv[3], "Application Docker network name drifted"
assert compose_model["networks"]["observability"].get("internal") is True, "Observability service network must remain internal"
assert prometheus["cpus"] == 0.25 and prometheus["mem_limit"] == str(384 * 1024 * 1024), "Prometheus lean resource cap drifted"
assert grafana["cpus"] == 0.15 and grafana["mem_limit"] == str(256 * 1024 * 1024), "Grafana lean resource cap drifted"
assert len(prometheus["ports"]) == 1 and str(prometheus["ports"][0]["published"]) == sys.argv[4], "Prometheus UI port drifted"
assert len(grafana["ports"]) == 1 and str(grafana["ports"][0]["published"]) == sys.argv[5], "Grafana UI port drifted"
assert all(port.get("host_ip") == "127.0.0.1" for port in prometheus["ports"] + grafana["ports"]), "observability UI must not bind publicly"
assert "PAWCYCLE_MYSQL" not in json.dumps(compose_model), "observability must not depend on local MySQL state"

datasource = (root / "grafana" / "provisioning" / "datasources" / "prometheus.yaml").read_text(encoding="utf-8")
assert "url: http://prometheus:9090" in datasource, "Grafana datasource must use the shared observability network service name"

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
  grep -Eq 'Platform:[[:space:]]+linux/amd64' <<<"$manifest" || {
    printf 'linux/amd64 manifest missing for %s\n' "$image" >&2
    exit 1
  }
done

compose_validation up --detach --wait --wait-timeout 60
compose_validation exec --no-TTY prometheus \
  grep -Fq "$METRICS_TARGET" /etc/prometheus-runtime/prometheus.yml

OBSERVABILITY_NETWORK="${PROJECT_NAME}_observability"
docker network inspect "$OBSERVABILITY_NETWORK" >/dev/null
docker run --rm --network "$OBSERVABILITY_NETWORK" alpine:3.22 \
  wget --quiet --output-document=/dev/null http://prometheus:9090/-/ready

compose_validation down --volumes --remove-orphans >/dev/null

printf 'Production observability Compose, shared-network Prometheus reachability, dashboards, and linux/amd64 image validation passed\n'
