#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_NAME="pawcycle-metrics-proxy-validation-${RANDOM}-$$"
APP_NETWORK="pawcycle-metrics-proxy-validation-app-${RANDOM}-$$"
EDGE_NETWORK="pawcycle-metrics-proxy-validation-edge-${RANDOM}-$$"
BACKEND_NAME="${PROJECT_NAME}-backend-fixture"
METRICS_PORT="19464"
PROXY_IMAGE="nginx:1.30.3-alpine3.23@sha256:0d3b80406a13a767339fbe2f41406d6c7da727ab89cf8fae399e81f780f814d1"

cleanup() {
  local status=$?
  set +e
  PAWCYCLE_APP_NETWORK="$APP_NETWORK" PAWCYCLE_EDGE_NETWORK="$EDGE_NETWORK" \
    PAWCYCLE_METRICS_PORT="$METRICS_PORT" \
    docker compose --project-name "$PROJECT_NAME" --file "$SCRIPT_DIR/compose.yaml" down --remove-orphans >/dev/null 2>&1
  docker rm --force "$BACKEND_NAME" >/dev/null 2>&1
  docker network inspect "$APP_NETWORK" >/dev/null 2>&1 && docker network rm "$APP_NETWORK" >/dev/null 2>&1
  docker network inspect "$EDGE_NETWORK" >/dev/null 2>&1 && docker network rm "$EDGE_NETWORK" >/dev/null 2>&1
  return "$status"
}
trap cleanup EXIT

docker network create "$APP_NETWORK" >/dev/null
docker network create "$EDGE_NETWORK" >/dev/null
PAWCYCLE_APP_NETWORK="$APP_NETWORK" PAWCYCLE_EDGE_NETWORK="$EDGE_NETWORK" \
  PAWCYCLE_METRICS_PORT="$METRICS_PORT" \
  docker compose --project-name "$PROJECT_NAME" --file "$SCRIPT_DIR/compose.yaml" config --quiet

docker run --detach --name "$BACKEND_NAME" --network "$APP_NETWORK" --network-alias backend \
  "$PROXY_IMAGE" sh -ec \
  'printf "%s\n" "events { worker_connections 64; }" "http { server { listen 8080; location = /actuator/prometheus { default_type text/plain; return 200 \"fixture_metric 1\\n\"; } location / { return 404; } } }" > /tmp/nginx.conf && nginx -c /tmp/nginx.conf -g "daemon off;"' >/dev/null

PAWCYCLE_APP_NETWORK="$APP_NETWORK" PAWCYCLE_EDGE_NETWORK="$EDGE_NETWORK" \
  PAWCYCLE_METRICS_PORT="$METRICS_PORT" \
  docker compose --project-name "$PROJECT_NAME" --file "$SCRIPT_DIR/compose.yaml" up --detach --wait --wait-timeout 60

curl --fail --silent --show-error "http://127.0.0.1:${METRICS_PORT}/actuator/prometheus" | grep -qx 'fixture_metric 1'
[[ "$(curl --silent --output /dev/null --write-out '%{http_code}' "http://127.0.0.1:${METRICS_PORT}/api/products")" == "404" ]]
[[ "$(docker compose --project-name "$PROJECT_NAME" --file "$SCRIPT_DIR/compose.yaml" port metrics-proxy 9464)" == *":${METRICS_PORT}"* ]]
[[ "$(docker compose --project-name "$PROJECT_NAME" --file "$SCRIPT_DIR/compose.yaml" config --format json | grep -o 'published[^,]*' | wc -l)" -eq 1 ]]

docker inspect "$BACKEND_NAME" --format '{{.State.Running}}' | grep -qx true
docker compose --project-name "$PROJECT_NAME" --file "$SCRIPT_DIR/compose.yaml" down --remove-orphans >/dev/null
docker inspect "$BACKEND_NAME" --format '{{.State.Running}}' | grep -qx true
docker network inspect "$APP_NETWORK" >/dev/null
docker network inspect "$EDGE_NETWORK" >/dev/null

printf 'Standalone metrics-proxy external-network lifecycle and endpoint contract passed\n'
