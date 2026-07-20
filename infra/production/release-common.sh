#!/usr/bin/env bash

set -Eeuo pipefail

PRODUCTION_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$PRODUCTION_DIR/compose.yaml"
PROJECT_NAME="pawcycle-production"
HEALTH_TIMEOUT_SECONDS="${PAWCYCLE_HEALTH_TIMEOUT_SECONDS:-240}"

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

validate_sha() {
  [[ "$1" =~ ^[0-9a-f]{40}$ ]] || die "release SHA must be 40 lowercase hexadecimal characters"
}

validate_image_repository() {
  [[ "$1" =~ ^ghcr\.io/[a-z0-9][a-z0-9._-]*/[a-z0-9][a-z0-9._/-]*$ ]] \
    || die "image repository must be an untagged lowercase ghcr.io path"
}

validate_absolute_directory() {
  [[ "$1" == /* && "$1" != "/" ]] || die "$2 must be an absolute directory other than /"
}

validate_runtime_bundle() {
  local runtime_dir="$1"
  local current="$runtime_dir/current"
  local file

  validate_absolute_directory "$runtime_dir" "runtime directory"
  [[ -d "$current" ]] || die "materialized runtime bundle is missing: $current"
  [[ -f "$current/.complete" ]] || die "runtime bundle completion marker is missing"

  for file in "$current/mysql.env" "$current/backend.env" "$current/.complete"; do
    [[ ! -L "$file" && -f "$file" ]] || die "runtime file must be a regular non-symlink file: $file"
    [[ "$(stat -c '%a' "$file")" == "600" ]] || die "runtime file mode must be 600: $file"
  done

  for key in MYSQL_DATABASE MYSQL_USER MYSQL_PASSWORD MYSQL_ROOT_PASSWORD; do
    grep -Eq "^${key}=.+$" "$current/mysql.env" || die "required runtime key is missing: $key"
  done
  for key in SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD; do
    grep -Eq "^${key}=.+$" "$current/backend.env" || die "required runtime key is missing: $key"
  done
  if grep -Eq '^MYSQL_ROOT_PASSWORD=' "$current/backend.env"; then
    die "Backend runtime file must not contain the MySQL root password"
  fi

  PAWCYCLE_MYSQL_ENV_FILE="$current/mysql.env"
  PAWCYCLE_BACKEND_ENV_FILE="$current/backend.env"
  export PAWCYCLE_MYSQL_ENV_FILE PAWCYCLE_BACKEND_ENV_FILE
}

prepare_state_directory() {
  validate_absolute_directory "$PAWCYCLE_STATE_DIR" "state directory"
  install -d -m 700 "$PAWCYCLE_STATE_DIR"
}

compose() {
  RELEASE_SHA="$ACTIVE_SHA" \
  BACKEND_IMAGE="$BACKEND_IMAGE" \
  FRONTEND_IMAGE="$FRONTEND_IMAGE" \
  PAWCYCLE_MYSQL_ENV_FILE="$PAWCYCLE_MYSQL_ENV_FILE" \
  PAWCYCLE_BACKEND_ENV_FILE="$PAWCYCLE_BACKEND_ENV_FILE" \
  PAWCYCLE_MYSQL_VOLUME="pawcycle-production-mysql-data" \
  PAWCYCLE_EDGE_NETWORK="pawcycle-production-edge" \
  PAWCYCLE_APP_NETWORK="pawcycle-production-app" \
  PAWCYCLE_DATA_NETWORK="pawcycle-production-data" \
  PAWCYCLE_HTTP_PORT="80" \
    docker compose --project-name "$PROJECT_NAME" --file "$COMPOSE_FILE" "$@"
}

image_digest() {
  local repository="$1"
  local sha="$2"
  local reference="${repository}:${sha}"
  local revision
  local digest

  docker pull "$reference" >/dev/null
  revision="$(docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$reference")"
  [[ "$revision" == "$sha" ]] || die "image revision label does not match release SHA: $reference"

  digest="$(docker image inspect --format '{{ range .RepoDigests }}{{ println . }}{{ end }}' "$reference" \
    | grep -F "${repository}@sha256:" | head -n 1)"
  [[ -n "$digest" ]] || die "registry digest is missing: $reference"
  printf '%s\n' "$digest"
}

preflight_release() {
  local sha="$1"
  local record="$PAWCYCLE_STATE_DIR/${sha}.images"
  local backend_digest
  local frontend_digest

  validate_sha "$sha"
  ACTIVE_SHA="$sha"
  export ACTIVE_SHA

  compose config --quiet
  compose pull mysql proxy >/dev/null
  backend_digest="$(image_digest "$BACKEND_IMAGE" "$sha")"
  frontend_digest="$(image_digest "$FRONTEND_IMAGE" "$sha")"

  {
    printf 'RELEASE_SHA=%s\n' "$sha"
    printf 'BACKEND_DIGEST=%s\n' "$backend_digest"
    printf 'FRONTEND_DIGEST=%s\n' "$frontend_digest"
  } > "${record}.tmp"
  chmod 600 "${record}.tmp"
  mv -f "${record}.tmp" "$record"

  printf 'Verified Backend digest: %s\n' "$backend_digest"
  printf 'Verified Frontend digest: %s\n' "$frontend_digest"
}

wait_healthy() {
  local service="$1"
  local elapsed=0
  local container_id
  local status

  while (( elapsed < HEALTH_TIMEOUT_SECONDS )); do
    container_id="$(compose ps --quiet "$service")"
    if [[ -n "$container_id" ]]; then
      status="$(docker inspect --format '{{ if .State.Health }}{{ .State.Health.Status }}{{ else }}{{ .State.Status }}{{ end }}' "$container_id")"
      case "$status" in
        healthy)
          printf '%s is healthy\n' "$service"
          return 0
          ;;
        unhealthy|exited|dead)
          printf '%s entered terminal status: %s\n' "$service" "$status" >&2
          return 1
          ;;
      esac
    fi
    sleep 5
    elapsed=$((elapsed + 5))
  done

  printf '%s did not become healthy within %ss\n' "$service" "$HEALTH_TIMEOUT_SECONDS" >&2
  return 1
}

smoke_release() {
  curl --fail --silent --show-error --max-time 10 http://127.0.0.1/products >/dev/null
  curl --fail --silent --show-error --max-time 10 http://127.0.0.1/api/products >/dev/null
  printf 'HTTP smoke checks passed\n'
}

verify_running_release() {
  local service
  local expected_reference
  local container_id
  local configured_reference
  local revision

  for service in backend frontend; do
    if [[ "$service" == "backend" ]]; then
      expected_reference="${BACKEND_IMAGE}:${ACTIVE_SHA}"
    else
      expected_reference="${FRONTEND_IMAGE}:${ACTIVE_SHA}"
    fi

    container_id="$(compose ps --quiet "$service")"
    [[ -n "$container_id" ]] || return 1
    configured_reference="$(docker inspect --format '{{ .Config.Image }}' "$container_id")"
    revision="$(docker inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$container_id")"
    [[ "$configured_reference" == "$expected_reference" && "$revision" == "$ACTIVE_SHA" ]] || {
      printf '%s is not running the requested immutable image\n' "$service" >&2
      return 1
    }
  done
}

activate_release() {
  local sha="$1"
  local service

  ACTIVE_SHA="$sha"
  export ACTIVE_SHA
  compose up --detach --pull never --remove-orphans mysql backend frontend || return 1
  for service in mysql backend frontend; do
    wait_healthy "$service" || return 1
  done
  compose up --detach --pull never --no-deps --force-recreate proxy || return 1
  wait_healthy proxy || return 1
  verify_running_release || return 1
  smoke_release || return 1
}

write_state() {
  local name="$1"
  local value="$2"
  local target="$PAWCYCLE_STATE_DIR/$name"

  printf '%s\n' "$value" > "${target}.tmp"
  chmod 600 "${target}.tmp"
  mv -f "${target}.tmp" "$target"
}

stop_application_services() {
  compose stop proxy frontend backend || true
}

initialize_release_context() {
  require_command curl
  require_command docker
  require_command flock
  require_command grep
  require_command stat

  validate_sha "$TARGET_SHA"
  validate_image_repository "$BACKEND_IMAGE"
  validate_image_repository "$FRONTEND_IMAGE"
  validate_runtime_bundle "$PAWCYCLE_RUNTIME_DIR"
  prepare_state_directory

  exec 9>"$PAWCYCLE_STATE_DIR/deploy.lock"
  flock --nonblock 9 || die "another production release command is running"
}
