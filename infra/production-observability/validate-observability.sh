#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROMETHEUS_IMAGE="prom/prometheus:v3.13.2@sha256:508729e0e2d18e11fd742a5a5ca70e557b940a93948c3c95fd0123a6fd538b69"
GRAFANA_IMAGE="grafana/grafana:13.1.3@sha256:ab5cb380e3ff3172d6c8bd2e7cfd31cce977d2881b260e1f5bc089bf0b759b43"
ALERTMANAGER_IMAGE="prom/alertmanager:v0.28.1@sha256:27c475db5fb156cab31d5c18a4251ac7ed567746a2483ff264516437a39b15ba"
# Validation-only fake Discord receiver and fake metrics-proxy target (ARCH-016). Never used in Production.
FAKE_HTTP_IMAGE="python:3.13-alpine@sha256:9ef1a599f863231c3d91e941389bfdc4a2d6070557243d54df1a4e57015b960e"
TEMP_DIR="$(mktemp -d)"
VALIDATION_ID="obs-validation-${RANDOM}-$$"
PROJECT_NAME="pawcycle-$VALIDATION_ID"
APP_NETWORK="pawcycle-$VALIDATION_ID-app"
PROMETHEUS_VOLUME="pawcycle-$VALIDATION_ID-prometheus-data"
GRAFANA_VOLUME="pawcycle-$VALIDATION_ID-grafana-data"
ALERTMANAGER_VOLUME="pawcycle-$VALIDATION_ID-alertmanager-data"
FAKE_DISCORD_CONTAINER="pawcycle-$VALIDATION_ID-fake-discord"
FAKE_TARGET_CONTAINER="pawcycle-$VALIDATION_ID-fake-metrics-proxy"
PROMETHEUS_PORT="$((20000 + RANDOM % 10000))"
GRAFANA_PORT="$((30000 + RANDOM % 10000))"
METRICS_TARGET="metrics-proxy:9464"
CURL_CONNECT_TIMEOUT_SECONDS=2
CURL_MAX_TIME_SECONDS=3
STALL_PID=""

compose_validation() {
  PAWCYCLE_METRICS_TARGET="$METRICS_TARGET" \
  PAWCYCLE_APP_NETWORK="$APP_NETWORK" \
  PAWCYCLE_GRAFANA_ADMIN_USER_FILE="$TEMP_DIR/grafana-admin-user" \
  PAWCYCLE_GRAFANA_ADMIN_PASSWORD_FILE="$TEMP_DIR/grafana-admin-password" \
  PAWCYCLE_OBSERVABILITY_PROMETHEUS_VOLUME="$PROMETHEUS_VOLUME" \
  PAWCYCLE_OBSERVABILITY_GRAFANA_VOLUME="$GRAFANA_VOLUME" \
  PAWCYCLE_ALERTMANAGER_DISCORD_WEBHOOK_FILE="$TEMP_DIR/discord-webhook" \
  PAWCYCLE_OBSERVABILITY_ALERTMANAGER_VOLUME="$ALERTMANAGER_VOLUME" \
  PAWCYCLE_OBSERVABILITY_PROMETHEUS_PORT="$PROMETHEUS_PORT" \
  PAWCYCLE_OBSERVABILITY_GRAFANA_PORT="$GRAFANA_PORT" \
    docker compose --project-name "$PROJECT_NAME" --file "$SCRIPT_DIR/compose.yaml" "$@"
}

bounded_curl() {
  curl --fail --silent --output /dev/null \
    --connect-timeout "$CURL_CONNECT_TIMEOUT_SECONDS" \
    --max-time "$CURL_MAX_TIME_SECONDS" \
    "$1"
}

cleanup() {
  local status=$?
  set +e
  if [[ -n "$STALL_PID" ]]; then
    kill "$STALL_PID" >/dev/null 2>&1
    wait "$STALL_PID" >/dev/null 2>&1
  fi
  docker rm --force "$FAKE_DISCORD_CONTAINER" "$FAKE_TARGET_CONTAINER" >/dev/null 2>&1
  compose_validation down --volumes --remove-orphans >/dev/null 2>&1
  docker network rm "$APP_NETWORK" >/dev/null 2>&1
  rm -rf -- "$TEMP_DIR"
  return "$status"
}
trap cleanup EXIT

printf 'admin\n' > "$TEMP_DIR/grafana-admin-user"
printf 'validation-only-password\n' > "$TEMP_DIR/grafana-admin-password"
# Points at the validation-only fake receiver. Production uses a real webhook file owned 65534:65534 mode 0400.
printf 'http://fake-discord:8080/webhook\n' > "$TEMP_DIR/discord-webhook"
chmod 400 "$TEMP_DIR/grafana-admin-user" "$TEMP_DIR/grafana-admin-password" "$TEMP_DIR/discord-webhook"
docker run --rm --volume "$TEMP_DIR:/run/pawcycle-secrets" alpine:3.22 \
  chown 472:472 /run/pawcycle-secrets/grafana-admin-user /run/pawcycle-secrets/grafana-admin-password
docker run --rm --volume "$TEMP_DIR:/run/pawcycle-secrets" alpine:3.22 \
  chown 65534:65534 /run/pawcycle-secrets/discord-webhook
# Production의 pawcycle-production-app network와 같은 조건을 재현한다.
# app network 자체는 internal로 유지하고, localhost publish 경로는 observability bridge가 제공해야 한다.
docker network create --internal "$APP_NETWORK" >/dev/null
test "$(docker network inspect "$APP_NETWORK" --format '{{.Internal}}')" = 'true'
compose_validation config --quiet
compose_validation config --format json > "$TEMP_DIR/compose-model.json"

# The rendered config loads the production rule files from the same path the container mounts.
docker run --rm --entrypoint sh \
  --volume "$SCRIPT_DIR/prometheus/prometheus.yml.tpl:/template:ro" \
  --volume "$SCRIPT_DIR/prometheus/rules:/etc/prometheus/rules:ro" \
  "$PROMETHEUS_IMAGE" -ec \
  'sed "s|__PAWCYCLE_METRICS_TARGET__|metrics-proxy:9464|g" /template >/tmp/prometheus.yml && promtool check config /tmp/prometheus.yml'
bash "$SCRIPT_DIR/test-alerting-config.sh"

if command -v python3 >/dev/null 2>&1; then
  PYTHON=(python3)
else
  PYTHON=(py -3)
fi

# TCP 연결 후 응답을 보내지 않는 endpoint에서도 bounded_curl이 제한 시간 안에 실패해야 한다.
STALL_PORT_FILE="$TEMP_DIR/stall-port"
"${PYTHON[@]}" - "$STALL_PORT_FILE" <<'PY' &
import socket
import sys
import time
from pathlib import Path

with socket.socket() as server:
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("127.0.0.1", 0))
    server.listen(1)
    Path(sys.argv[1]).write_text(str(server.getsockname()[1]), encoding="utf-8")
    connection, _ = server.accept()
    with connection:
        time.sleep(30)
PY
STALL_PID=$!
for attempt in $(seq 1 20); do
  if [[ -s "$STALL_PORT_FILE" ]]; then
    break
  fi
  if [[ "$attempt" -eq 20 ]]; then
    printf 'Stalled HTTP fixture did not start\n' >&2
    exit 1
  fi
  sleep 0.1
done
STALL_PORT="$(cat "$STALL_PORT_FILE")"
STALL_STARTED_AT="$(date +%s)"
if bounded_curl "http://127.0.0.1:${STALL_PORT}/"; then
  printf 'Bounded curl unexpectedly succeeded against a stalled endpoint\n' >&2
  exit 1
fi
STALL_ELAPSED="$(( $(date +%s) - STALL_STARTED_AT ))"
kill "$STALL_PID" >/dev/null 2>&1 || true
wait "$STALL_PID" >/dev/null 2>&1 || true
STALL_PID=""
if (( STALL_ELAPSED > CURL_MAX_TIME_SECONDS + 2 )); then
  printf 'Bounded curl exceeded the expected failure window: %ss\n' "$STALL_ELAPSED" >&2
  exit 1
fi

"${PYTHON[@]}" - "$SCRIPT_DIR" "$TEMP_DIR/compose-model.json" "$APP_NETWORK" "$PROMETHEUS_PORT" "$GRAFANA_PORT" <<'PY'
import json
import sys
from pathlib import Path

root = Path(sys.argv[1])
compose_model = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
prometheus = compose_model["services"]["prometheus"]
grafana = compose_model["services"]["grafana"]
alertmanager = compose_model["services"]["alertmanager"]
prometheus_entrypoint = prometheus["entrypoint"]
prometheus_command = prometheus["command"]
assert set(compose_model["services"]) == {"prometheus", "grafana", "alertmanager"}, "observability must own only Prometheus, Grafana and Alertmanager"
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
assert compose_model["networks"]["observability"].get("driver") == "bridge", "Observability service network must remain a normal bridge"
assert compose_model["networks"]["observability"].get("internal") is not True, "Observability bridge must allow localhost port publishing"
assert set(alertmanager["networks"]) == {"observability"}, "Alertmanager must stay off the Application network"
assert "ports" not in alertmanager, "Alertmanager must not publish a host port (ARCH-016)"
assert alertmanager["cpus"] == 0.05 and alertmanager["mem_limit"] == str(64 * 1024 * 1024), "Alertmanager lean resource cap drifted"
assert alertmanager["image"].startswith("prom/alertmanager:") and "@sha256:" in alertmanager["image"], "Alertmanager image must be pinned by digest"
assert alertmanager.get("read_only") is True, "Alertmanager root filesystem must be read-only"
assert "--cluster.listen-address=" in alertmanager["command"], "single Alertmanager must not open a gossip listener"
assert alertmanager["healthcheck"]["test"][-1] == "http://127.0.0.1:9093/-/ready", "Alertmanager readiness healthcheck drifted"
alertmanager_mounts = {mount["target"]: mount for mount in alertmanager["volumes"]}
assert alertmanager_mounts["/run/pawcycle-secrets/discord-webhook"].get("read_only") is True, "Discord webhook must be a read-only runtime secret file"
assert alertmanager_mounts["/alertmanager"]["type"] == "volume", "Alertmanager silences and notification log need a named volume"
assert "DISCORD" not in json.dumps(alertmanager.get("environment", {})).upper(), "Discord webhook must not be passed through the environment"
assert any(mount["target"] == "/etc/prometheus/rules" for mount in prometheus["volumes"]), "Prometheus must mount the production alert rules"
alertmanager_config = (root / "alertmanager" / "alertmanager.yml").read_text(encoding="utf-8")
assert "webhook_url_file: /run/pawcycle-secrets/discord-webhook" in alertmanager_config, "Discord webhook must come from webhook_url_file"
assert "webhook_url:" not in alertmanager_config, "inline Discord webhook URL is forbidden"
prometheus_template = (root / "prometheus" / "prometheus.yml.tpl").read_text(encoding="utf-8")
assert "alertmanager:9093" in prometheus_template and "/etc/prometheus/rules/*.yml" in prometheus_template, "Prometheus alerting wiring drifted"
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

for image in "$PROMETHEUS_IMAGE" "$GRAFANA_IMAGE" "$ALERTMANAGER_IMAGE" "$FAKE_HTTP_IMAGE"; do
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
test "$(docker network inspect "$OBSERVABILITY_NETWORK" --format '{{.Internal}}')" = 'false'
for attempt in $(seq 1 12); do
  if docker run --rm --network "$OBSERVABILITY_NETWORK" alpine:3.22 \
    wget --quiet --output-document=/dev/null http://prometheus:9090/-/ready; then
    break
  fi
  if [[ "$attempt" -eq 12 ]]; then
    printf 'Prometheus was not reachable on the shared observability network\n' >&2
    exit 1
  fi
  sleep 1
done

PROMETHEUS_ID="$(compose_validation ps --quiet prometheus)"
GRAFANA_ID="$(compose_validation ps --quiet grafana)"
test -n "$PROMETHEUS_ID" && test -n "$GRAFANA_ID"
test "$(docker port "$PROMETHEUS_ID" 9090/tcp)" = "127.0.0.1:${PROMETHEUS_PORT}"
test "$(docker port "$GRAFANA_ID" 3000/tcp)" = "127.0.0.1:${GRAFANA_PORT}"

for attempt in $(seq 1 12); do
  if bounded_curl "http://127.0.0.1:${PROMETHEUS_PORT}/-/ready"; then
    break
  fi
  if [[ "$attempt" -eq 12 ]]; then
    printf 'Prometheus localhost endpoint was not ready\n' >&2
    exit 1
  fi
  sleep 1
done

for attempt in $(seq 1 60); do
  if bounded_curl "http://127.0.0.1:${GRAFANA_PORT}/api/health"; then
    break
  fi
  if [[ "$attempt" -eq 60 ]]; then
    printf 'Grafana localhost endpoint was not ready\n' >&2
    exit 1
  fi
  sleep 1
done

# ---- Alert delivery (ARCH-016): Prometheus -> Alertmanager -> fake Discord, firing then resolved ----
# No metrics-proxy exists yet on the validation app network, so up == 0 and the production rule
# PawCycleBackendScrapeUnavailable fires after its 1m `for`. A fake metrics-proxy then resolves it.
ALERTMANAGER_ID="$(compose_validation ps --quiet alertmanager)"
test -n "$ALERTMANAGER_ID"
test -z "$(docker port "$ALERTMANAGER_ID")"
test "$(docker inspect --format '{{.State.Health.Status}}' "$ALERTMANAGER_ID")" = 'healthy'
if docker inspect --format '{{json .NetworkSettings.Networks}}' "$ALERTMANAGER_ID" | grep -Fq "$APP_NETWORK"; then
  printf 'Alertmanager must not attach to the Application network\n' >&2
  exit 1
fi

FAKE_HTTP_SOURCE="$(cat "$SCRIPT_DIR/validation-fake-http.py")"
docker run --detach --name "$FAKE_DISCORD_CONTAINER" \
  --network "$OBSERVABILITY_NETWORK" --network-alias fake-discord \
  "$FAKE_HTTP_IMAGE" python -u -c "$FAKE_HTTP_SOURCE" discord >/dev/null

until_delivered() {
  local pattern="$1" label="$2"
  for attempt in $(seq 1 60); do
    if docker logs "$FAKE_DISCORD_CONTAINER" 2>&1 | grep -Fq "$pattern"; then
      printf 'delivered: %s (within ~%ss of polling)\n' "$label" "$((attempt * 5))"
      return 0
    fi
    sleep 5
  done
  printf 'Alertmanager did not deliver %s to the fake receiver within 300s\n' "$label" >&2
  docker logs "$FAKE_DISCORD_CONTAINER" >&2 || true
  compose_validation logs --no-color alertmanager prometheus 2>&1 | tail -n 40 >&2 || true
  return 1
}

for attempt in $(seq 1 12); do
  if curl --fail --silent --connect-timeout "$CURL_CONNECT_TIMEOUT_SECONDS" --max-time "$CURL_MAX_TIME_SECONDS" \
    "http://127.0.0.1:${PROMETHEUS_PORT}/api/v1/alertmanagers" |
    "${PYTHON[@]}" -c 'import json,sys; d=json.load(sys.stdin)["data"]["activeAlertmanagers"]; sys.exit(0 if len(d) == 1 and "alertmanager:9093" in d[0]["url"] else 1)'; then
    break
  fi
  if [[ "$attempt" -eq 12 ]]; then
    printf 'Prometheus did not discover exactly one Alertmanager at alertmanager:9093\n' >&2
    exit 1
  fi
  sleep 5
done

until_delivered '[운영] [CRITICAL] PawCycleBackendScrapeUnavailable' 'critical firing'

# Watchdog reaches Alertmanager (rule evaluation -> Alertmanager path) but is never sent to the channel.
compose_validation exec --no-TTY alertmanager \
  amtool --alertmanager.url=http://127.0.0.1:9093 alert query alertname=Watchdog | grep -Fq Watchdog

docker run --detach --name "$FAKE_TARGET_CONTAINER" \
  --network "$APP_NETWORK" --network-alias metrics-proxy \
  "$FAKE_HTTP_IMAGE" python -u -c "$FAKE_HTTP_SOURCE" metrics >/dev/null

until_delivered '[운영] [RESOLVED] PawCycleBackendScrapeUnavailable' 'resolved'
if docker logs "$FAKE_DISCORD_CONTAINER" 2>&1 | grep -Fq Watchdog; then
  printf 'Watchdog must be routed to the blackhole receiver, not the Discord channel\n' >&2
  exit 1
fi
NOTIFY_METRICS="$(compose_validation exec --no-TTY alertmanager \
  wget --quiet --output-document=- http://127.0.0.1:9093/metrics)"
grep -E '^alertmanager_notifications_(total|failed_total)\{integration="discord"' <<<"$NOTIFY_METRICS"
grep -Eq '^alertmanager_notifications_total\{integration="discord"\} [1-9]' <<<"$NOTIFY_METRICS" || {
  printf 'Alertmanager reported no Discord notifications\n' >&2
  exit 1
}
! grep -E '^alertmanager_notifications_failed_total\{integration="discord"' <<<"$NOTIFY_METRICS" | grep -Evq ' 0$' || {
  printf 'Alertmanager reported failed Discord notifications\n' >&2
  exit 1
}
docker stats --no-stream --format 'resource sample: {{.Name}} {{.MemUsage}} {{.CPUPerc}}' \
  "$ALERTMANAGER_ID" "$PROMETHEUS_ID" "$GRAFANA_ID"

docker rm --force "$FAKE_DISCORD_CONTAINER" "$FAKE_TARGET_CONTAINER" >/dev/null
compose_validation down --volumes --remove-orphans >/dev/null

printf 'Production observability Compose, bounded localhost publishing, shared-network Prometheus reachability, dashboards, alert delivery (firing and resolved through a fake receiver), and linux/amd64 image validation passed\n'
