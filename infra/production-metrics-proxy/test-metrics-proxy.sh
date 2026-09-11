#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_NAME="pawcycle-metrics-proxy-validation-${RANDOM}-$$"
APP_NETWORK="pawcycle-metrics-proxy-validation-app-${RANDOM}-$$"
BACKEND_A_NAME="${PROJECT_NAME}-backend-a"
BACKEND_B_NAME="${PROJECT_NAME}-backend-b"
PROXY_IMAGE="nginx:1.30.3-alpine3.23@sha256:0d3b80406a13a767339fbe2f41406d6c7da727ab89cf8fae399e81f780f814d1"
TEMP_DIR="$(mktemp -d)"

compose_validation() {
  PAWCYCLE_APP_NETWORK="$APP_NETWORK" \
    docker compose --project-name "$PROJECT_NAME" --file "$SCRIPT_DIR/compose.yaml" "$@"
}

cleanup() {
  local status=$?
  set +e
  compose_validation down --remove-orphans >/dev/null 2>&1
  docker rm --force "$BACKEND_A_NAME" "$BACKEND_B_NAME" >/dev/null 2>&1
  docker network inspect "$APP_NETWORK" >/dev/null 2>&1 && docker network rm "$APP_NETWORK" >/dev/null 2>&1
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
    actual="$(docker run --rm --network "$APP_NETWORK" --entrypoint wget "$PROXY_IMAGE" \
      --quiet --output-document=- http://metrics-proxy:9464/actuator/prometheus 2>/dev/null || true)"
    if [[ "$actual" == "$expected" ]]; then
      return 0
    fi
    sleep 1
  done
  printf 'expected metric %q but got %q\n' "$expected" "$actual" >&2
  return 1
}

internal_http_code() {
  local path="$1"
  docker run --rm --network "$APP_NETWORK" --entrypoint sh "$PROXY_IMAGE" -ec '
    set +e
    wget -S -O /dev/null "http://metrics-proxy:9464$1" 2>&1 |
      awk '\''$1 == "HTTP/1.1" { code=$2 } END { print code == "" ? "000" : code }'\''
  ' sh "$path" | tail -n 1
}

docker network create "$APP_NETWORK" >/dev/null
compose_validation config --quiet
compose_validation config --format json > "$TEMP_DIR/compose-model.json"

python3 - "$TEMP_DIR/compose-model.json" "$APP_NETWORK" "$PROXY_IMAGE" <<'PY'
import json
import sys
from pathlib import Path

model = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
expected_app, expected_image = sys.argv[2:4]
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
assert not ports, "metrics-proxy must not publish a host port"

mounts = {mount["target"]: mount for mount in proxy.get("volumes", [])}
config_mount = mounts.get("/etc/nginx/conf.d/default.conf")
assert config_mount and config_mount.get("read_only") is True, "metrics-proxy config mount must remain read-only"

service_networks = proxy.get("networks", {})
assert set(service_networks) == {"app"}, "metrics-proxy must use only the Application network"
networks = model["networks"]
assert networks["app"].get("external") is True and networks["app"]["name"] == expected_app, "app network must remain external"

health = " ".join(proxy.get("healthcheck", {}).get("test", []))
assert "/actuator/prometheus" in health, "metrics-proxy healthcheck must use the metrics endpoint"
PY

grep -Fq 'resolver 127.0.0.11' "$SCRIPT_DIR/metrics-proxy.conf"
grep -Fq 'server backend:8080 resolve;' "$SCRIPT_DIR/metrics-proxy.conf"
grep -Fq 'location = /actuator/prometheus' "$SCRIPT_DIR/metrics-proxy.conf"
grep -Fq 'return 404' "$SCRIPT_DIR/metrics-proxy.conf"

manifest="$(docker buildx imagetools inspect "$PROXY_IMAGE")"
grep -Eq 'Platform:[[:space:]]+linux/amd64' <<<"$manifest" || {
  printf 'linux/amd64 manifest missing for %s\n' "$PROXY_IMAGE" >&2
  exit 1
}

start_backend_fixture "$BACKEND_A_NAME" 1
compose_validation up --detach --wait --wait-timeout 60
wait_metric 'fixture_metric 1'
[[ "$(internal_http_code /api/products)" == "404" ]]
PROXY_ID_BEFORE="$(compose_validation ps --quiet metrics-proxy)"
[[ -n "$PROXY_ID_BEFORE" ]]
[[ -z "$(docker port "$PROXY_ID_BEFORE" 9464/tcp 2>/dev/null)" ]]

docker rm --force "$BACKEND_A_NAME" >/dev/null
start_backend_fixture "$BACKEND_B_NAME" 2
wait_metric 'fixture_metric 2'
PROXY_ID_AFTER="$(compose_validation ps --quiet metrics-proxy)"
[[ "$PROXY_ID_AFTER" == "$PROXY_ID_BEFORE" ]]

docker inspect "$BACKEND_B_NAME" --format '{{.State.Running}}' | grep -qx true
compose_validation down --remove-orphans >/dev/null
docker inspect "$BACKEND_B_NAME" --format '{{.State.Running}}' | grep -qx true
docker network inspect "$APP_NETWORK" >/dev/null

printf 'Same-host internal metrics-proxy hardening, dynamic backend DNS, lifecycle, and endpoint contract passed\n'
