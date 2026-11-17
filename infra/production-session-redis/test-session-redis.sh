#!/usr/bin/env bash

# Disposable validation of the AUTH-005 session Redis sibling project.
# Uses its own internal network and volume; never touches Production names.

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_NAME="pawcycle-session-redis-validation-${RANDOM}-$$"
APP_NETWORK="pawcycle-session-redis-validation-app-${RANDOM}-$$"
OTHER_NETWORK="pawcycle-session-redis-validation-other-${RANDOM}-$$"
DATA_VOLUME="pawcycle-session-redis-validation-data-${RANDOM}-$$"
APP_SENTINEL="${PROJECT_NAME}-app-sentinel"
REDIS_IMAGE="redis:8.10.0-alpine3.23@sha256:978f0e01593e65eed801f2402944efcd936d43b5027e4908a7897baf88ed6241"
TEMP_DIR="$(mktemp -d)"

compose_validation() {
  PAWCYCLE_APP_NETWORK="$APP_NETWORK" PAWCYCLE_SESSION_REDIS_VOLUME="$DATA_VOLUME" \
    docker compose --project-name "$PROJECT_NAME" --file "$SCRIPT_DIR/compose.yaml" "$@"
}

cleanup() {
  local status=$?
  set +e
  compose_validation down --remove-orphans >/dev/null 2>&1
  docker rm --force "$APP_SENTINEL" >/dev/null 2>&1
  docker volume rm "$DATA_VOLUME" >/dev/null 2>&1
  for network in "$APP_NETWORK" "$OTHER_NETWORK"; do
    docker network inspect "$network" >/dev/null 2>&1 && docker network rm "$network" >/dev/null 2>&1
  done
  rm -rf "$TEMP_DIR"
  return "$status"
}
trap cleanup EXIT

# Client on the simulated Application network, the way Backend reaches Redis.
app_redis() {
  docker run --rm --network "$1" --entrypoint redis-cli "$REDIS_IMAGE" -h session-redis "${@:2}"
}

started_at() {
  docker inspect --format '{{.State.StartedAt}}' "$1"
}

docker network create --internal "$APP_NETWORK" >/dev/null
docker network create --internal "$OTHER_NETWORK" >/dev/null
compose_validation config --quiet
compose_validation config --format json > "$TEMP_DIR/compose-model.json"

python3 - "$TEMP_DIR/compose-model.json" "$APP_NETWORK" "$DATA_VOLUME" "$REDIS_IMAGE" <<'PY'
import json
import sys
from pathlib import Path

model = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
expected_app, expected_volume, expected_image = sys.argv[2:5]
assert model["name"].startswith("pawcycle-session-redis-validation-")
services = model["services"]
assert set(services) == {"session-redis"}, "sibling project must own only session-redis"
redis = services["session-redis"]
assert redis["image"] == expected_image, "session-redis image must remain digest-pinned"
assert redis.get("read_only") is True, "session-redis root filesystem must remain read-only"
assert redis.get("restart") == "unless-stopped", "session-redis restart policy drifted"
assert float(redis["cpus"]) == 0.25, "session-redis CPU limit drifted"
assert int(redis["mem_limit"]) == 128 * 1024 * 1024, "session-redis memory limit drifted"
assert int(redis["pids_limit"]) == 64, "session-redis PID limit drifted"
security_opts = {item.replace("=", ":", 1) for item in redis.get("security_opt", [])}
assert "no-new-privileges:true" in security_opts, "no-new-privileges must remain enabled"
assert not redis.get("depends_on"), "session-redis must not own Application lifecycle"
assert not redis.get("ports"), "session-redis must not publish a host port"
assert not redis.get("environment"), "session-redis takes no credentials or environment"

command = redis["command"]
options = dict(zip(command[1::2], command[2::2]))
assert command[0] == "redis-server"
assert options == {"--appendonly": "yes", "--appendfsync": "everysec", "--save": "",
                   "--maxmemory": "64mb", "--maxmemory-policy": "noeviction"}, "session-redis server options drifted"
assert not any("requirepass" in item or "port" == item.lstrip("-") for item in command)

mounts = redis.get("volumes", [])
assert len(mounts) == 1 and mounts[0]["type"] == "volume" and mounts[0]["target"] == "/data"
assert mounts[0]["source"] == "session-redis-data"
assert model["volumes"]["session-redis-data"]["name"] == expected_volume

assert set(redis.get("networks", {})) == {"app"}, "session-redis must use only the Application network"
assert model["networks"]["app"].get("external") is True and model["networks"]["app"]["name"] == expected_app

assert redis["healthcheck"]["test"] == ["CMD", "redis-cli", "ping"]
PY

manifest="$(docker buildx imagetools inspect "$REDIS_IMAGE")"
grep -Eq 'Platform:[[:space:]]+linux/amd64' <<<"$manifest" || {
  printf 'linux/amd64 manifest missing for %s\n' "$REDIS_IMAGE" >&2
  exit 1
}

# An Application-owned container already attached to the network must survive
# every sibling lifecycle step below.
docker run --detach --name "$APP_SENTINEL" --network "$APP_NETWORK" --network-alias backend \
  --entrypoint sh "$REDIS_IMAGE" -c 'while :; do sleep 3600; done' >/dev/null
SENTINEL_STARTED="$(started_at "$APP_SENTINEL")"

compose_validation up --detach --wait --wait-timeout 60
REDIS_ID="$(compose_validation ps --quiet session-redis)"
[[ -n "$REDIS_ID" ]]

[[ "$(app_redis "$APP_NETWORK" ping)" == "PONG" ]]
[[ "$(app_redis "$APP_NETWORK" config get maxmemory | tail -n 1)" == "67108864" ]]
[[ "$(app_redis "$APP_NETWORK" config get maxmemory-policy | tail -n 1)" == "noeviction" ]]
[[ "$(app_redis "$APP_NETWORK" config get appendonly | tail -n 1)" == "yes" ]]
[[ "$(app_redis "$APP_NETWORK" config get appendfsync | tail -n 1)" == "everysec" ]]

# No host port, no network other than the Application network.
[[ -z "$(docker port "$REDIS_ID")" ]]
[[ "$(docker inspect --format '{{json .HostConfig.PortBindings}}' "$REDIS_ID")" =~ ^(\{\}|null)$ ]]
[[ "$(docker inspect --format '{{range $name, $_ := .NetworkSettings.Networks}}{{$name}} {{end}}' "$REDIS_ID")" == "$APP_NETWORK " ]]
[[ "$(docker inspect --format '{{.HostConfig.ReadonlyRootfs}}' "$REDIS_ID")" == "true" ]]
if docker run --rm --network "$OTHER_NETWORK" --entrypoint redis-cli "$REDIS_IMAGE" \
    -h session-redis ping >/dev/null 2>&1; then
  printf 'session-redis must not be reachable outside the Application network\n' >&2
  exit 1
fi

# AOF persistence across container recreation (named volume survives).
app_redis "$APP_NETWORK" set pawcycle:session:validation present >/dev/null
sleep 2
compose_validation up --detach --wait --wait-timeout 60 --force-recreate
[[ "$(compose_validation ps --quiet session-redis)" != "$REDIS_ID" ]]
[[ "$(app_redis "$APP_NETWORK" get pawcycle:session:validation)" == "present" ]]

# noeviction: writes are refused at the limit instead of evicting sessions.
# (A temporary 1-byte limit; an evicting policy would drop the existing key.)
app_redis "$APP_NETWORK" config set maxmemory 1 >/dev/null
write_result="$(app_redis "$APP_NETWORK" set pawcycle:session:filler value 2>&1 || true)"
if [[ "$write_result" != *OOM* ]]; then
  printf 'session-redis accepted or mishandled a write beyond maxmemory\n' >&2
  exit 1
fi
[[ "$(app_redis "$APP_NETWORK" get pawcycle:session:validation)" == "present" ]]
app_redis "$APP_NETWORK" config set maxmemory 64mb >/dev/null

# Sibling stop/down keeps the data volume, the Application network and the
# Application container untouched.
compose_validation down >/dev/null
docker volume inspect "$DATA_VOLUME" >/dev/null
docker network inspect "$APP_NETWORK" >/dev/null
[[ "$(started_at "$APP_SENTINEL")" == "$SENTINEL_STARTED" ]]
compose_validation up --detach --wait --wait-timeout 60
[[ "$(app_redis "$APP_NETWORK" get pawcycle:session:validation)" == "present" ]]
[[ "$(started_at "$APP_SENTINEL")" == "$SENTINEL_STARTED" ]]

printf 'Session Redis sibling hardening, internal-only reachability, AOF persistence, noeviction and lifecycle passed\n'
