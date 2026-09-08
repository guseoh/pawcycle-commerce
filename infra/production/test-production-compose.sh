#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/../.." && pwd -P)"
VALIDATION_ID="ops-oci-003-${RANDOM}-$$"
PROJECT_NAME="pawcycle-$VALIDATION_ID"
CERTBOT_WEBROOT_VOLUME="pawcycle-$VALIDATION_ID-certbot-webroot"
LETSENCRYPT_VOLUME="pawcycle-$VALIDATION_ID-letsencrypt"
EDGE_NETWORK="pawcycle-$VALIDATION_ID-edge"
APP_NETWORK="pawcycle-$VALIDATION_ID-app"
TEST_DATABASE_NETWORK="pawcycle-$VALIDATION_ID-test-database"
DATABASE_EGRESS_NETWORK="pawcycle-$VALIDATION_ID-database-egress"
UNHEALTHY_PROJECT_NAME="pawcycle-$VALIDATION_ID-unhealthy"
UNHEALTHY_DATABASE_NETWORK="pawcycle-$VALIDATION_ID-unhealthy-database"
UNHEALTHY_EDGE_NETWORK="pawcycle-$VALIDATION_ID-unhealthy-edge"
UNHEALTHY_APP_NETWORK="pawcycle-$VALIDATION_ID-unhealthy-app"
UNHEALTHY_EGRESS_NETWORK="pawcycle-$VALIDATION_ID-unhealthy-egress"
UNHEALTHY_SENTINEL_NETWORK="pawcycle-$VALIDATION_ID-unrelated-sentinel"
UNHEALTHY_MARKER=""
HTTP_PORT="18080"
HTTPS_PORT="18443"
HTTPS_DOMAIN="ops-oci-003-compose-test.duckdns.org"
BOOTSTRAP_DOMAIN="ops-oci-003-unapproved-test.duckdns.org"
TEMP_DIR="$(mktemp -d)"
MYSQL_ENV="$TEMP_DIR/mysql.env"
BACKEND_ENV="$TEMP_DIR/backend.env"
BACKEND_IMAGE="ghcr.io/example/pawcycle-commerce-backend"
FRONTEND_IMAGE="ghcr.io/example/pawcycle-commerce-frontend"
MYSQL_IMAGE="mysql:8.4.10@sha256:c592c15aaf4a1961e15d82eb31ea5987dda862d1c4b1e93424438c0e91dc1f8d"
PROXY_IMAGE="nginx:1.30.3-alpine3.23@sha256:0d3b80406a13a767339fbe2f41406d6c7da727ab89cf8fae399e81f780f814d1"
SHA_A="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
SHA_B="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
NGINX_CONFIG="$SCRIPT_DIR/nginx.conf"
DB_NAME="ops_oci_003_validation"
DB_USER="ops_oci_003_validation"
DB_PASSWORD="$(openssl rand -hex 16)"
DB_ROOT_PASSWORD="$(openssl rand -hex 16)"
ACTIVE_SHA="$SHA_A"
HTTPS_MODE=false

cleanup() {
  local status=$?
  set +e
  if (( status != 0 )); then
    printf 'OPS-OCI-003 validation failed; recent non-secret service logs follow\n' >&2
    if [[ -f "$BACKEND_ENV" && -f "$MYSQL_ENV" ]]; then
      ACTIVE_SHA="$SHA_A" compose ps >&2
      ACTIVE_SHA="$SHA_A" compose logs --tail 100 mysql backend frontend proxy >&2
    fi
  fi
  if [[ -f "$BACKEND_ENV" && -f "$MYSQL_ENV" ]]; then
    ACTIVE_SHA="$SHA_A" compose down --remove-orphans >/dev/null 2>&1
  fi
  if [[ -f "$BACKEND_ENV" && -f "$MYSQL_ENV" ]]; then
    compose_for_project \
      "$UNHEALTHY_PROJECT_NAME" \
      "$UNHEALTHY_EDGE_NETWORK" \
      "$UNHEALTHY_APP_NETWORK" \
      "$UNHEALTHY_DATABASE_NETWORK" \
      "$UNHEALTHY_EGRESS_NETWORK" \
      "$SCRIPT_DIR/compose.test-unhealthy.yaml" \
      down --remove-orphans >/dev/null 2>&1
  fi
  if [[ "$UNHEALTHY_SENTINEL_NETWORK" == pawcycle-ops-oci-003-* ]]; then
    docker network rm "$UNHEALTHY_SENTINEL_NETWORK" >/dev/null 2>&1
  fi
  if [[ "$CERTBOT_WEBROOT_VOLUME" == pawcycle-ops-oci-003-* \
    && "$LETSENCRYPT_VOLUME" == pawcycle-ops-oci-003-* ]]; then
    docker volume rm "$CERTBOT_WEBROOT_VOLUME" "$LETSENCRYPT_VOLUME" >/dev/null 2>&1
  fi
  docker image rm "${BACKEND_IMAGE}:${SHA_A}" "${BACKEND_IMAGE}:${SHA_B}" \
    "${FRONTEND_IMAGE}:${SHA_A}" "${FRONTEND_IMAGE}:${SHA_B}" >/dev/null 2>&1
  rm -rf -- "$TEMP_DIR"
  return "$status"
}
trap cleanup EXIT

cat >"$MYSQL_ENV" <<EOF
MYSQL_DATABASE=$DB_NAME
MYSQL_USER=$DB_USER
MYSQL_PASSWORD=$DB_PASSWORD
MYSQL_ROOT_PASSWORD=$DB_ROOT_PASSWORD
EOF

cat >"$BACKEND_ENV" <<EOF
PAWCYCLE_DATASOURCE_HOST=mysql
PAWCYCLE_DATASOURCE_PORT=3306
PAWCYCLE_DATASOURCE_SSL_MODE=DISABLED
SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/$DB_NAME?sslMode=DISABLED&allowPublicKeyRetrieval=true&serverTimezone=UTC
SPRING_DATASOURCE_USERNAME=$DB_USER
SPRING_DATASOURCE_PASSWORD=$DB_PASSWORD
PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED=false
PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE=7
PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS=12345
EOF
chmod 600 "$MYSQL_ENV" "$BACKEND_ENV"

compose_for_project() {
  local release_sha="$1"
  local project_name="$2"
  local edge_network="$3"
  local app_network="$4"
  local test_database_network="$5"
  local database_egress_network="$6"
  local extra_file="$7"
  local -a compose_files=(
    --file "$SCRIPT_DIR/compose.yaml"
    --file "$SCRIPT_DIR/compose.test.yaml"
  )
  shift 7
  if [[ -n "$extra_file" ]]; then
    compose_files+=(--file "$extra_file")
  fi
  RELEASE_SHA="$release_sha" \
  BACKEND_IMAGE="$BACKEND_IMAGE" \
  FRONTEND_IMAGE="$FRONTEND_IMAGE" \
  PAWCYCLE_TEST_MYSQL_ENV_FILE="$MYSQL_ENV" \
  PAWCYCLE_BACKEND_ENV_FILE="$BACKEND_ENV" \
  PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED="false" \
  PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE="7" \
  PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS="12345" \
  PAWCYCLE_EDGE_NETWORK="$edge_network" \
  PAWCYCLE_APP_NETWORK="$app_network" \
  PAWCYCLE_TEST_DATABASE_NETWORK="$test_database_network" \
  PAWCYCLE_DATABASE_EGRESS_NETWORK="$database_egress_network" \
  PAWCYCLE_CERTBOT_WEBROOT_VOLUME="$CERTBOT_WEBROOT_VOLUME" \
  PAWCYCLE_LETSENCRYPT_VOLUME="$LETSENCRYPT_VOLUME" \
  PAWCYCLE_NGINX_CONFIG="$NGINX_CONFIG" \
  PAWCYCLE_HTTP_PORT="$HTTP_PORT" \
  PAWCYCLE_HTTPS_PORT="$HTTPS_PORT" \
    docker compose --project-name "$project_name" "${compose_files[@]}" "$@"
}

compose() {
  compose_for_project \
    "$ACTIVE_SHA" \
    "$PROJECT_NAME" \
    "$EDGE_NETWORK" \
    "$APP_NETWORK" \
    "$TEST_DATABASE_NETWORK" \
    "$DATABASE_EGRESS_NETWORK" \
    "" \
    "$@"
}

unhealthy_compose() {
  compose_for_project \
    "$SHA_A" \
    "$UNHEALTHY_PROJECT_NAME" \
    "$UNHEALTHY_EDGE_NETWORK" \
    "$UNHEALTHY_APP_NETWORK" \
    "$UNHEALTHY_DATABASE_NETWORK" \
    "$UNHEALTHY_EGRESS_NETWORK" \
    "$SCRIPT_DIR/compose.test-unhealthy.yaml" \
    "$@"
}

validate_active_production_contract() {
  local config_json="$TEMP_DIR/active-compose-config.json"
  env -u PAWCYCLE_MYSQL_ENV_FILE \
    RELEASE_SHA="$SHA_A" \
    BACKEND_IMAGE="$BACKEND_IMAGE" \
    FRONTEND_IMAGE="$FRONTEND_IMAGE" \
    PAWCYCLE_BACKEND_ENV_FILE="$BACKEND_ENV" \
    PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED=false \
    PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE=7 \
    PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS=12345 \
    PAWCYCLE_EDGE_NETWORK="$EDGE_NETWORK" \
    PAWCYCLE_APP_NETWORK="$APP_NETWORK" \
    PAWCYCLE_DATABASE_EGRESS_NETWORK="$DATABASE_EGRESS_NETWORK" \
    PAWCYCLE_CERTBOT_WEBROOT_VOLUME="$CERTBOT_WEBROOT_VOLUME" \
    PAWCYCLE_LETSENCRYPT_VOLUME="$LETSENCRYPT_VOLUME" \
    PAWCYCLE_NGINX_CONFIG="$SCRIPT_DIR/nginx.conf" \
    PAWCYCLE_HTTP_PORT="$HTTP_PORT" \
    PAWCYCLE_HTTPS_PORT="$HTTPS_PORT" \
      docker compose --project-name "$PROJECT_NAME" \
        --file "$SCRIPT_DIR/compose.yaml" config --format json >"$config_json"

  python3 - "$config_json" "$SCRIPT_DIR/compose.yaml" "$ROOT_DIR/backend/src/main/resources/application.properties" <<'PY'
import json
import re
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    config = json.load(handle)
with open(sys.argv[2], encoding="utf-8") as handle:
    compose_source = handle.read()
with open(sys.argv[3], encoding="utf-8") as handle:
    application_properties = handle.read()

services = config.get("services", {})
assert set(services) == {"backend", "frontend", "proxy"}
assert "mysql" not in services
assert "mysql-data" not in config.get("volumes", {})
assert set(config.get("volumes", {})) == {"certbot-webroot", "letsencrypt"}
assert set(config.get("networks", {})) == {"edge", "app", "database-egress"}
assert not services["backend"].get("depends_on")
assert set(services["backend"].get("networks", {})) == {"app", "database-egress"}
assert set(services["frontend"].get("networks", {})) == {"app"}
assert set(services["proxy"].get("networks", {})) == {"edge", "app"}

database_egress_members = {
    name
    for name, service in services.items()
    if "database-egress" in service.get("networks", {})
}
assert database_egress_members == {"backend"}
assert config["networks"]["database-egress"].get("internal") is not True

assert "${PAWCYCLE_BACKEND_ENV_FILE:?PAWCYCLE_BACKEND_ENV_FILE is required}" in compose_source
assert "PAWCYCLE_MYSQL_" not in compose_source
assert not re.search(r"SPRING_DATASOURCE_(URL|USERNAME|PASSWORD)\s*[:=]", compose_source)

for key in ("URL", "USERNAME", "PASSWORD"):
    assert f"spring.datasource.{key.lower()}=${{SPRING_DATASOURCE_{key}}}" in application_properties
assert not re.search(
    r"(?im)^\s*spring\.datasource\.(url|username|password)\s*=\s*(?!\$\{SPRING_DATASOURCE_(URL|USERNAME|PASSWORD)\})",
    application_properties,
)
PY
}

validate_test_overlay_contract() {
  local config_json="$TEMP_DIR/test-compose-config.json"
  ACTIVE_SHA="$SHA_A" compose config --format json >"$config_json"
  python3 - "$config_json" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    config = json.load(handle)

services = config.get("services", {})
assert set(services) == {"backend", "frontend", "proxy", "mysql"}
assert set(services["backend"].get("networks", {})) == {"app", "database-egress", "test-database"}
assert set(services["mysql"].get("networks", {})) == {"test-database"}
assert set(services["frontend"].get("networks", {})) == {"app"}
assert set(services["proxy"].get("networks", {})) == {"edge", "app"}
assert not services["backend"].get("depends_on")
assert config["networks"]["test-database"].get("internal") is True

database_egress_members = {
    name
    for name, service in services.items()
    if "database-egress" in service.get("networks", {})
}
assert database_egress_members == {"backend"}
PY
}

wait_healthy() {
  local service="$1"
  local container_id
  local status
  local attempt
  for (( attempt = 0; attempt < 60; attempt++ )); do
    container_id="$(compose ps --quiet "$service")"
    if [[ -n "$container_id" ]]; then
      status="$(docker inspect --format '{{.State.Health.Status}}' "$container_id")"
      [[ "$status" == "healthy" ]] && return 0
      [[ "$status" == "unhealthy" ]] && return 1
    fi
    sleep 5
  done
  return 1
}

wait_healthy_unhealthy_mysql() {
  local container_id
  local status
  local attempt
  for (( attempt = 0; attempt < 30; attempt++ )); do
    container_id="$(unhealthy_compose ps --quiet mysql 2>/dev/null || true)"
    if [[ -n "$container_id" ]]; then
      status="$(docker inspect --format '{{.State.Health.Status}}' "$container_id" 2>/dev/null || true)"
      [[ "$status" == "healthy" ]] && return 0
      [[ "$status" == "unhealthy" ]] && return 1
    fi
    sleep 1
  done
  return 1
}

run_unhealthy_mysql_gate_regression() {
  local gate_status
  local backend_containers
  local frontend_containers
  UNHEALTHY_MARKER="$TEMP_DIR/unhealthy-mysql-gate.marker"
  : >"$UNHEALTHY_MARKER"
  docker network create --label "com.pawcycle.ops-oci-003.sentinel=$VALIDATION_ID" \
    "$UNHEALTHY_SENTINEL_NETWORK" >/dev/null

  set +e
  (
    set -Eeuo pipefail
    unhealthy_cleanup() {
      local status=$?
      set +e
      if unhealthy_compose down --remove-orphans >/dev/null 2>&1; then
        printf '%s\n' 'compose-down-remove-orphans=PASS' >>"$UNHEALTHY_MARKER"
      else
        printf '%s\n' 'compose-down-remove-orphans=FAIL' >>"$UNHEALTHY_MARKER"
      fi
      exit "$status"
    }
    trap unhealthy_cleanup EXIT

    unhealthy_compose up --detach --pull never --remove-orphans mysql >/dev/null 2>&1
    if wait_healthy_unhealthy_mysql; then
      printf '%s\n' 'health-gate=UNEXPECTED_PASS' >>"$UNHEALTHY_MARKER"
      gate_status=99
    else
      gate_status=$?
      printf '%s\n' 'health-gate=failed' >>"$UNHEALTHY_MARKER"
    fi
    backend_containers="$(docker ps --all --quiet \
      --filter "label=com.docker.compose.project=$UNHEALTHY_PROJECT_NAME" \
      --filter 'label=com.docker.compose.service=backend' 2>/dev/null || true)"
    frontend_containers="$(docker ps --all --quiet \
      --filter "label=com.docker.compose.project=$UNHEALTHY_PROJECT_NAME" \
      --filter 'label=com.docker.compose.service=frontend' 2>/dev/null || true)"
    if [[ -n "$backend_containers" || -n "$frontend_containers" ]]; then
      printf '%s\n' 'backend-frontend=STARTED' >>"$UNHEALTHY_MARKER"
    else
      printf '%s\n' 'backend-frontend=NOT_STARTED' >>"$UNHEALTHY_MARKER"
    fi
    [[ "$gate_status" != "0" ]] || gate_status=98
    exit "$gate_status"
  )
  gate_status=$?
  set -e

  [[ "$gate_status" != "0" ]] || die "unhealthy MySQL gate unexpectedly succeeded"
  grep -Fxq 'health-gate=failed' "$UNHEALTHY_MARKER" \
    || die "unhealthy MySQL gate did not fail closed"
  grep -Fxq 'backend-frontend=NOT_STARTED' "$UNHEALTHY_MARKER" \
    || die "Backend or Frontend started after unhealthy MySQL gate"
  grep -Fxq 'compose-down-remove-orphans=PASS' "$UNHEALTHY_MARKER" \
    || die "unhealthy MySQL cleanup did not run compose down"
  if docker ps --all --quiet \
    --filter "label=com.docker.compose.project=$UNHEALTHY_PROJECT_NAME" | grep -q .; then
    die "unhealthy MySQL validation containers remained"
  fi
  if docker network inspect "$UNHEALTHY_DATABASE_NETWORK" >/dev/null 2>&1; then
    die "unhealthy MySQL validation network remained"
  fi
  docker network inspect "$UNHEALTHY_SENTINEL_NETWORK" >/dev/null 2>&1 \
    || die "unrelated host network was removed"
  docker network rm "$UNHEALTHY_SENTINEL_NETWORK" >/dev/null \
    || die "unrelated host network cleanup failed"
  UNHEALTHY_SENTINEL_NETWORK=""
}

build_release() {
  local sha="$1"
  docker build --file "$SCRIPT_DIR/backend.Dockerfile" --label "org.opencontainers.image.revision=$sha" \
    --tag "${BACKEND_IMAGE}:${sha}" "$ROOT_DIR"
  docker build --file "$SCRIPT_DIR/frontend.Dockerfile" --label "org.opencontainers.image.revision=$sha" \
    --tag "${FRONTEND_IMAGE}:${sha}" "$ROOT_DIR"
}

activate_and_check() {
  local sha="$1"
  local service
  local container_id
  local configured_image
  ACTIVE_SHA="$sha"
  compose config --quiet
  compose up --detach --pull never --remove-orphans mysql
  wait_healthy mysql
  compose up --detach --pull never --remove-orphans backend frontend
  for service in backend frontend; do
    wait_healthy "$service"
  done
  compose up --detach --pull never --no-deps --force-recreate proxy
  wait_healthy proxy
  for service in mysql proxy; do
    container_id="$(compose ps --quiet "$service")"
    configured_image="$(docker inspect --format '{{.Config.Image}}' "$container_id")"
    if [[ "$service" == "mysql" ]]; then
      [[ "$configured_image" == "$MYSQL_IMAGE" ]]
    else
      [[ "$configured_image" == "$PROXY_IMAGE" ]]
    fi
  done
  if [[ "$HTTPS_MODE" == true ]]; then
    curl --cacert "$CERTIFICATE_SOURCE/fullchain.pem" --fail --silent --show-error \
      --resolve "$HTTPS_DOMAIN:$HTTPS_PORT:127.0.0.1" \
      "https://$HTTPS_DOMAIN:$HTTPS_PORT/products" >/dev/null
    curl --cacert "$CERTIFICATE_SOURCE/fullchain.pem" --fail --silent --show-error \
      --resolve "$HTTPS_DOMAIN:$HTTPS_PORT:127.0.0.1" \
      "https://$HTTPS_DOMAIN:$HTTPS_PORT/api/products" >/dev/null
    [[ "$(curl --silent --output /dev/null --write-out '%{http_code}' \
      --header "Host: $HTTPS_DOMAIN" "http://127.0.0.1:${HTTP_PORT}/products")" == "301" ]]
    [[ "$(curl --silent --output /dev/null --write-out '%{redirect_url}' \
      --header "Host: $HTTPS_DOMAIN" "http://127.0.0.1:${HTTP_PORT}/products")" \
      == "https://$HTTPS_DOMAIN/products" ]]
    unknown_code="$(curl --silent --output /dev/null --write-out '%{http_code}' \
      --header 'Host: unknown.example.invalid' "http://127.0.0.1:${HTTP_PORT}/products" || true)"
    [[ "$unknown_code" == "000" || "$unknown_code" == "400" || "$unknown_code" == "404" ]]
  else
    curl --fail --silent --show-error "http://127.0.0.1:${HTTP_PORT}/products" >/dev/null
    curl --fail --silent --show-error "http://127.0.0.1:${HTTP_PORT}/api/products" >/dev/null
  fi
}

validate_active_production_contract
validate_test_overlay_contract
build_release "$SHA_A"
build_release "$SHA_B"
docker pull "$MYSQL_IMAGE" >/dev/null
run_unhealthy_mysql_gate_regression
docker pull "$PROXY_IMAGE" >/dev/null
activate_and_check "$SHA_A"

docker run --rm \
  --entrypoint sh \
  --volume "$CERTBOT_WEBROOT_VOLUME:/var/www/certbot" \
  "$PROXY_IMAGE" -c \
  'mkdir -p /var/www/certbot/.well-known/acme-challenge && printf pawcycle-acme-probe > /var/www/certbot/.well-known/acme-challenge/probe'
[[ "$(curl --fail --silent --show-error \
  --header "Host: $BOOTSTRAP_DOMAIN" \
  "http://127.0.0.1:${HTTP_PORT}/.well-known/acme-challenge/probe")" == "pawcycle-acme-probe" ]]
docker run --rm \
  --entrypoint sh \
  --volume "$CERTBOT_WEBROOT_VOLUME:/var/www/certbot" \
  "$PROXY_IMAGE" -c 'rm -f -- /var/www/certbot/.well-known/acme-challenge/probe'

ACTIVE_SHA="$SHA_A" compose stop
activate_and_check "$SHA_A"

activate_and_check "$SHA_B"
activate_and_check "$SHA_A"

CERTIFICATE_SOURCE="$TEMP_DIR/letsencrypt/live/pawcycle-production"
mkdir -p "$CERTIFICATE_SOURCE"
openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout "$CERTIFICATE_SOURCE/privkey.pem" \
  -out "$CERTIFICATE_SOURCE/fullchain.pem" \
  -days 2 -subj "/CN=$HTTPS_DOMAIN" \
  -addext "subjectAltName=DNS:$HTTPS_DOMAIN" >/dev/null 2>&1
docker run --rm \
  --volume "$LETSENCRYPT_VOLUME:/target" \
  --volume "$TEMP_DIR/letsencrypt:/source:ro" \
  "$PROXY_IMAGE" sh -c 'cp -a /source/. /target/'
NGINX_CONFIG="$TEMP_DIR/nginx.https.conf"
sed "s/__PAWCYCLE_DOMAIN__/$HTTPS_DOMAIN/g" "$SCRIPT_DIR/nginx.https.conf" > "$NGINX_CONFIG"
HTTPS_MODE=true
activate_and_check "$SHA_A"

ACTIVE_SHA="$SHA_A" compose down --remove-orphans
docker volume inspect "$CERTBOT_WEBROOT_VOLUME" --format '{{.Name}}' | grep -qx "$CERTBOT_WEBROOT_VOLUME"
docker volume inspect "$LETSENCRYPT_VOLUME" --format '{{.Name}}' | grep -qx "$LETSENCRYPT_VOLUME"
printf 'OPS-OCI-003 external-DB Production Compose, lifecycle, rollback, HTTPS, and certificate-volume contract passed\n'
