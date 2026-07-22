#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
VALIDATION_ID="ops011-${RANDOM}-$$"
PROJECT_NAME="pawcycle-$VALIDATION_ID"
VALIDATION_VOLUME="pawcycle-$VALIDATION_ID-mysql-data"
CERTBOT_WEBROOT_VOLUME="pawcycle-$VALIDATION_ID-certbot-webroot"
LETSENCRYPT_VOLUME="pawcycle-$VALIDATION_ID-letsencrypt"
EDGE_NETWORK="pawcycle-$VALIDATION_ID-edge"
APP_NETWORK="pawcycle-$VALIDATION_ID-app"
DATA_NETWORK="pawcycle-$VALIDATION_ID-data"
HTTP_PORT="18080"
HTTPS_PORT="18443"
TEMP_DIR="$(mktemp -d)"
MYSQL_ENV="$TEMP_DIR/mysql.env"
BACKEND_ENV="$TEMP_DIR/backend.env"
BACKEND_IMAGE="ghcr.io/example/pawcycle-commerce-backend"
FRONTEND_IMAGE="ghcr.io/example/pawcycle-commerce-frontend"
MYSQL_IMAGE="mysql:8.4.10@sha256:c592c15aaf4a1961e15d82eb31ea5987dda862d1c4b1e93424438c0e91dc1f8d"
PROXY_IMAGE="nginx:1.30.3-alpine3.23@sha256:0d3b80406a13a767339fbe2f41406d6c7da727ab89cf8fae399e81f780f814d1"
SHA_A="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
SHA_B="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

cleanup() {
  local status=$?
  set +e
  if (( status != 0 )); then
    printf 'OPS-011 validation failed; recent non-secret service logs follow\n' >&2
    ACTIVE_SHA="$SHA_A" compose ps >&2
    ACTIVE_SHA="$SHA_A" compose logs --tail 100 mysql backend frontend proxy >&2
  fi
  ACTIVE_SHA="$SHA_A" compose down --remove-orphans >/dev/null 2>&1
  if [[ "$VALIDATION_VOLUME" == pawcycle-ops011-* \
    && "$CERTBOT_WEBROOT_VOLUME" == pawcycle-ops011-* \
    && "$LETSENCRYPT_VOLUME" == pawcycle-ops011-* ]]; then
    docker volume rm "$VALIDATION_VOLUME" "$CERTBOT_WEBROOT_VOLUME" "$LETSENCRYPT_VOLUME" >/dev/null 2>&1
  fi
  docker image rm "${BACKEND_IMAGE}:${SHA_A}" "${BACKEND_IMAGE}:${SHA_B}" \
    "${FRONTEND_IMAGE}:${SHA_A}" "${FRONTEND_IMAGE}:${SHA_B}" >/dev/null 2>&1
  rm -rf -- "$TEMP_DIR"
  return "$status"
}
trap cleanup EXIT

cat > "$MYSQL_ENV" <<'EOF'
MYSQL_DATABASE=ops010_validation
MYSQL_USER=ops010_validation
MYSQL_PASSWORD=local-validation-only
MYSQL_ROOT_PASSWORD=local-validation-root-only
EOF

cat > "$BACKEND_ENV" <<'EOF'
SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/ops010_validation?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
SPRING_DATASOURCE_USERNAME=ops010_validation
SPRING_DATASOURCE_PASSWORD=local-validation-only
EOF

compose() {
  RELEASE_SHA="$ACTIVE_SHA" \
  BACKEND_IMAGE="$BACKEND_IMAGE" \
  FRONTEND_IMAGE="$FRONTEND_IMAGE" \
  PAWCYCLE_MYSQL_ENV_FILE="$MYSQL_ENV" \
  PAWCYCLE_BACKEND_ENV_FILE="$BACKEND_ENV" \
  PAWCYCLE_MYSQL_VOLUME="$VALIDATION_VOLUME" \
  PAWCYCLE_EDGE_NETWORK="$EDGE_NETWORK" \
  PAWCYCLE_APP_NETWORK="$APP_NETWORK" \
  PAWCYCLE_DATA_NETWORK="$DATA_NETWORK" \
  PAWCYCLE_CERTBOT_WEBROOT_VOLUME="$CERTBOT_WEBROOT_VOLUME" \
  PAWCYCLE_LETSENCRYPT_VOLUME="$LETSENCRYPT_VOLUME" \
  PAWCYCLE_HTTP_PORT="$HTTP_PORT" \
  PAWCYCLE_HTTPS_PORT="$HTTPS_PORT" \
    docker compose --project-name "$PROJECT_NAME" --file "$SCRIPT_DIR/compose.yaml" "$@"
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
  compose up --detach --pull never --remove-orphans mysql backend frontend
  for service in mysql backend frontend; do
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
  curl --fail --silent --show-error "http://127.0.0.1:${HTTP_PORT}/products" >/dev/null
  curl --fail --silent --show-error "http://127.0.0.1:${HTTP_PORT}/api/products" >/dev/null
}

build_release "$SHA_A"
build_release "$SHA_B"
docker pull "$MYSQL_IMAGE" >/dev/null
docker pull "$PROXY_IMAGE" >/dev/null
activate_and_check "$SHA_A"

MYSQL_CONTAINER="$(ACTIVE_SHA="$SHA_A" compose ps --quiet mysql)"
docker exec --env MYSQL_PWD=local-validation-only "$MYSQL_CONTAINER" \
  mysql --user=ops010_validation ops010_validation \
  --execute='CREATE TABLE IF NOT EXISTS ops010_volume_probe (id INT PRIMARY KEY); INSERT IGNORE INTO ops010_volume_probe VALUES (1);'

ACTIVE_SHA="$SHA_A" compose stop
activate_and_check "$SHA_A"
docker exec --env MYSQL_PWD=local-validation-only "$MYSQL_CONTAINER" \
  mysql --batch --skip-column-names --user=ops010_validation ops010_validation \
  --execute='SELECT COUNT(*) FROM ops010_volume_probe WHERE id = 1;' | grep -qx '1'

activate_and_check "$SHA_B"
activate_and_check "$SHA_A"
MYSQL_CONTAINER="$(ACTIVE_SHA="$SHA_A" compose ps --quiet mysql)"
docker exec --env MYSQL_PWD=local-validation-only "$MYSQL_CONTAINER" \
  mysql --batch --skip-column-names --user=ops010_validation ops010_validation \
  --execute='SELECT COUNT(*) FROM ops010_volume_probe WHERE id = 1;' | grep -qx '1'

ACTIVE_SHA="$SHA_A" compose down --remove-orphans
docker volume inspect "$VALIDATION_VOLUME" --format '{{.Name}}' | grep -qx "$VALIDATION_VOLUME"
printf 'OPS-011 bootstrap Compose start, health, smoke, restart, rollback, and volume preservation passed\n'
