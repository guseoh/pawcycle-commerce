#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_NAME="pawcycle-metrics-proxy-validation-${RANDOM}-$$"
APP_NETWORK="pawcycle-metrics-proxy-validation-app-${RANDOM}-$$"
EDGE_NETWORK="pawcycle-metrics-proxy-validation-edge-${RANDOM}-$$"
BACKEND_A_NAME="${PROJECT_NAME}-backend-a"
BACKEND_B_NAME="${PROJECT_NAME}-backend-b"
METRICS_PORT="19464"
PROXY_IMAGE="nginx:1.30.3-alpine3.23@sha256:0d3b80406a13a767339fbe2f41406d6c7da727ab89cf8fae399e81f780f814d1"
TEMP_DIR="$(mktemp -d)"

compose_validation() {
  PAWCYCLE_APP_NETWORK="$APP_NETWORK" PAWCYCLE_EDGE_NETWORK="$EDGE_NETWORK" \
    PAWCYCLE_METRICS_PORT="$METRICS_PORT" \
    docker compose --project-name "$PROJECT_NAME" --file "$SCRIPT_DIR/compose.yaml" "$@"
}

cleanup() {
  local status=$?
  set +e
  compose_validation down --remove-orphans >/dev/null 2>&1
  docker rm --force "$BACKEND_A_NAME" "$BACKEND_B_NAME" >/dev/null 2>&1
  docker network inspect "$APP_NETWORK" >/dev/null 2>&1 && docker network rm "$APP_NETWORK" >/dev/null 2>&1
  docker network inspect "$EDGE_NETWORK" >/dev/null 2>&1 && docker network rm "$EDGE_NETWORK" >/dev/null 2>&1
  rm -rf "$TEMP_DIR"
  return "$status"
}
trap cleanup EXIT

start_backend_fixture() {
  local name="$1"
  local metric_value="$2"

  docker run --detach --name "$name" --network "$APP_NETWORK" --network-alias backend \
    --env "FIXTURE_METRIC_VALUE=$metric_value" \
    "$PROXY_IMAGE" sh -ec '
      printf "%s\n" \
        "events { worker_connections 64; }" \
        "http { server { listen 8080; location = /actuator/prometheus { default_type text/plain; return 200 \"fixture_metric ${FIXTURE_METRIC_VALUE}\\n\"; } location / { return 404; } } }" \
        > /tmp/nginx.conf
      exec nginx -c /tmp/nginx.conf -g "daemon off;"
    ' >/dev/null
}

wait_metric() {
  local expected="$1"
  local actual=""
  local attempt

  for attempt in $(seq 1 30); do
    actual="$(curl --fail --silent --show-error "http://127.0.0.1:${METRICS_PORT}/actuator/prometheus" 2>/dev/null || true)"
    if [[ "$actual" == "$expected" ]]; then
      return 0
    fi
    sleep 1
  done
  printf 'expected metric %q but got %q\n' "$expected" "$actual" >&2
  return 1
}

docker network create "$APP_NETWORK" >/dev/null
docker network create "$EDGE_NETWORK" >/dev/null
compose_validation config --quiet
compose_validation config --format json > "$TEMP_DIR/compose-model.json"

python3 - "$TEMP_DIR/compose-model.json" "$APP_NETWORK" "$EDGE_NETWORK" "$METRICS_PORT" "$PROXY_IMAGE" <<'PY'
import json
import sys
from pathlib import Path

model = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
expected_app, expected_edge, expected_port, expected_image = sys.argv[2:6]
services = model["services"]
assert set(services) == {"metrics-proxy"}, "standalone project must own only metrics-proxy"
proxy = services["metrics-proxy"]
assert proxy["image"] == expected_image, "metrics-proxy image must remain pinned"
assert proxy.get("read_only") is True, "metrics-proxy root filesystem must remain read-only"
assert float(proxy["cpus"]) == 0.05, "metrics-proxy CPU limit drifted"
assert int(proxy["mem_limit"]) == 32 * 1024 * 1024, "metrics-proxy memory limit drifted"
assert int(proxy["pids_limit"]) == 32, "metrics-proxy PID limit drifted"
security_opts = {item.replace("=", ":", 1) for item in proxy.get("security_opt", [])}
assert "no-new-privileges:true" in security_opts, "no-new-privileges must remain enabled"
assert not proxy.get("depends_on"), "standalone metrics-proxy must not own Application lifecycle"

ports = proxy.get("ports", [])
assert len(ports) == 1, "metrics-proxy must publish exactly one host port"
assert ports[0].get("target") == 9464, "metrics-proxy container port must remain 9464"
assert str(ports[0].get("published")) == expected_port, "metrics-proxy published test port drifted"

mounts = {mount["target"]: mount for mount in proxy.get("volumes", [])}
config_mount = mounts.get("/etc/nginx/conf.d/default.conf")
assert config_mount and config_mount.get("read_only") is True, "metrics-proxy config mount must remain read-only"

service_networks = proxy.get("networks", {})
assert set(service_networks) == {"app", "edge"}, "metrics-proxy must use only app and edge networks"
networks = model["networks"]
assert networks["app"].get("external") is True and networks["app"]["name"] == expected_app, "app network must remain external"
assert networks["edge"].get("external") is True and networks["edge"]["name"] == expected_edge, "edge network must remain external"

health = " ".join(proxy.get("healthcheck", {}).get("test", []))
assert "/actuator/prometheus" in health, "metrics-proxy healthcheck must use the metrics endpoint"
PY

grep -Fq 'resolver 127.0.0.11' "$SCRIPT_DIR/metrics-proxy.conf"
grep -Fq 'server backend:8080 resolve;' "$SCRIPT_DIR/metrics-proxy.conf"
grep -Fq 'location = /actuator/prometheus' "$SCRIPT_DIR/metrics-proxy.conf"
grep -Fq 'return 404' "$SCRIPT_DIR/metrics-proxy.conf"

start_backend_fixture "$BACKEND_A_NAME" 1
compose_validation up --detach --wait --wait-timeout 60
wait_metric 'fixture_metric 1'
[[ "$(curl --silent --output /dev/null --write-out '%{http_code}' "http://127.0.0.1:${METRICS_PORT}/api/products")" == "404" ]]
PROXY_ID_BEFORE="$(compose_validation ps --quiet metrics-proxy)"
[[ -n "$PROXY_ID_BEFORE" ]]

docker rm --force "$BACKEND_A_NAME" >/dev/null
start_backend_fixture "$BACKEND_B_NAME" 2
wait_metric 'fixture_metric 2'
PROXY_ID_AFTER="$(compose_validation ps --quiet metrics-proxy)"
[[ "$PROXY_ID_AFTER" == "$PROXY_ID_BEFORE" ]]

docker inspect "$BACKEND_B_NAME" --format '{{.State.Running}}' | grep -qx true
compose_validation down --remove-orphans >/dev/null
docker inspect "$BACKEND_B_NAME" --format '{{.State.Running}}' | grep -qx true
docker network inspect "$APP_NETWORK" >/dev/null
docker network inspect "$EDGE_NETWORK" >/dev/null

printf 'Standalone metrics-proxy hardening, dynamic backend DNS, lifecycle, and endpoint contract passed\n'
