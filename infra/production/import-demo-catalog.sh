#!/usr/bin/env bash
set -Eeuo pipefail
set +x

PROJECT_NAME="pawcycle-production"
DATA_NETWORK="pawcycle-production-database-egress"
CONTAINER_NAME="pawcycle-mvp4-data-002-demo-catalog-import"
RUNTIME_DIR="/opt/pawcycle/runtime"
STATE_DIR="/opt/pawcycle/state"
RELEASE_SHA=""
BACKEND_IMAGE=""
TARGET="demo"
OPERATION=""
CONFIRM_APPLY=0

usage() {
  cat <<'EOF'
Usage: import-demo-catalog.sh \
  --operation <validate|apply> \
  --sha <current-40-char-sha> \
  --backend-image <lowercase-ghcr-repository> \
  [--target <demo|customer>] \
  [--confirm-apply] \
  [--runtime-dir /opt/pawcycle/runtime] \
  [--state-dir /opt/pawcycle/state]

The default target is demo. validate is a dry-run. apply requires --confirm-apply and is never automatic.
EOF
}

die() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "a required local command is unavailable"
}

validate_root_directory() {
  local path="$1"
  local label="$2"
  [[ "$path" == /* && "$path" != "/" ]] || die "$label is invalid"
  [[ -d "$path" && ! -L "$path" ]] || die "$label is unavailable or unsafe"
  [[ "$(stat -c '%a' "$path" 2>/dev/null)" == "700" ]] || die "$label permissions are invalid"
}

validate_protected_file() {
  local path="$1"
  local label="$2"
  [[ -f "$path" && ! -L "$path" ]] || die "$label is unavailable or unsafe"
  [[ "$(stat -c '%a' "$path" 2>/dev/null)" == "600" ]] || die "$label permissions are invalid"
}

docker_value() {
  local value
  value="$(docker "$@" 2>/dev/null)" || die "Docker preflight failed"
  printf '%s' "$value"
}

validate_backend_env() {
  local line key
  local -A count=()
  local keys=(
    PAWCYCLE_DATASOURCE_HOST PAWCYCLE_DATASOURCE_PORT PAWCYCLE_DATASOURCE_DATABASE PAWCYCLE_DATASOURCE_SSL_MODE
    SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD
    PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE
    PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS
  )
  for key in "${keys[@]}"; do count[$key]=0; done
  while IFS= read -r line || [[ -n "$line" ]]; do
    key="${line%%=*}"
    [[ "$line" == *=* && "${line#*=}" == \'*\' ]] || die "Backend runtime contract is invalid"
    [[ -n "${count[$key]+present}" ]] || die "Backend runtime contract contains an unknown key"
    count[$key]=$((count[$key] + 1))
  done < "$BACKEND_ENV_FILE"
  for key in "${keys[@]}"; do [[ "${count[$key]}" == "1" ]] || die "Backend runtime contract is incomplete"; done
}

stream_backend_env() {
  local line key value
  while IFS= read -r line || [[ -n "$line" ]]; do
    key="${line%%=*}"
    value="${line#*=}"
    value="${value:1:${#value}-2}"
    value="${value//\\\'/\'}"
    case "$key" in
      PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED|PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE|PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS) ;;
      *) printf '%s=%s\n' "$key" "$value" ;;
    esac
  done < "$BACKEND_ENV_FILE"
  printf '%s\n' 'PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED=false'
}

while (( $# > 0 )); do
  case "$1" in
    --operation) OPERATION="${2:-}"; shift 2 ;;
    --sha) RELEASE_SHA="${2:-}"; shift 2 ;;
    --backend-image) BACKEND_IMAGE="${2:-}"; shift 2 ;;
    --target) TARGET="${2:-}"; shift 2 ;;
    --confirm-apply) CONFIRM_APPLY=1; shift ;;
    --runtime-dir) RUNTIME_DIR="${2:-}"; shift 2 ;;
    --state-dir) STATE_DIR="${2:-}"; shift 2 ;;
    --help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
  esac
done

[[ "$EUID" == "0" ]] || die "root execution is required"
[[ "$TARGET" == "demo" || "$TARGET" == "customer" ]] || die "target must be demo or customer"
[[ "$OPERATION" == "validate" || "$OPERATION" == "apply" ]] || die "operation must be validate or apply"
[[ "$OPERATION" != "apply" || "$CONFIRM_APPLY" == "1" ]] || die "apply requires --confirm-apply"
[[ "$RELEASE_SHA" =~ ^[0-9a-f]{40}$ ]] || die "approved release SHA is invalid"
[[ "$BACKEND_IMAGE" =~ ^ghcr\.io/[a-z0-9][a-z0-9._-]*/[a-z0-9][a-z0-9._/-]*$ ]] || die "Backend image repository is invalid"
for command in docker flock grep readlink stat; do require_command "$command"; done

validate_root_directory "$RUNTIME_DIR" "runtime directory"
validate_root_directory "$STATE_DIR" "state directory"
DEPLOY_LOCK_FILE="$STATE_DIR/deploy.lock"
[[ ! -e "$DEPLOY_LOCK_FILE" || ( -f "$DEPLOY_LOCK_FILE" && ! -L "$DEPLOY_LOCK_FILE" ) ]] || die "production release lock is unsafe"
exec 9>>"$DEPLOY_LOCK_FILE"
flock --nonblock 9 || die "another production release command is running"

CURRENT_SHA_FILE="$STATE_DIR/current-sha"
IMAGE_STATE_FILE="$STATE_DIR/$RELEASE_SHA.images"
validate_protected_file "$CURRENT_SHA_FILE" "current release state"
validate_protected_file "$IMAGE_STATE_FILE" "release image state"
[[ "$(<"$CURRENT_SHA_FILE")" == "$RELEASE_SHA" ]] || die "approved SHA is not the current production release"

RUNTIME_CURRENT="$(readlink -f -- "$RUNTIME_DIR/current" 2>/dev/null || true)"
[[ "$RUNTIME_CURRENT" == "$RUNTIME_DIR"/.bundle.* && -d "$RUNTIME_CURRENT" ]] || die "runtime bundle target is unavailable or unsafe"
[[ "$(stat -c '%a' "$RUNTIME_CURRENT" 2>/dev/null)" == "700" ]] || die "runtime bundle permissions are invalid"
BACKEND_ENV_FILE="$RUNTIME_CURRENT/backend.env"
validate_protected_file "$BACKEND_ENV_FILE" "Backend runtime file"
validate_backend_env

BACKEND_DIGEST_LINE="$(grep -E '^BACKEND_DIGEST=ghcr\.io/.+@sha256:[0-9a-f]{64}$' "$IMAGE_STATE_FILE" 2>/dev/null || true)"
[[ "$(grep -c '^BACKEND_DIGEST=' "$IMAGE_STATE_FILE" 2>/dev/null)" == "1" && -n "$BACKEND_DIGEST_LINE" ]] || die "Backend digest state is invalid"
BACKEND_DIGEST="${BACKEND_DIGEST_LINE#BACKEND_DIGEST=}"
[[ "$BACKEND_DIGEST" == "$BACKEND_IMAGE@sha256:"* ]] || die "Backend digest repository does not match"

BACKEND_CONTAINERS="$(docker ps --filter "label=com.docker.compose.project=$PROJECT_NAME" --filter 'label=com.docker.compose.service=backend' --format '{{.ID}}' 2>/dev/null)"
[[ "$(printf '%s\n' "$BACKEND_CONTAINERS" | grep -c .)" == "1" ]] || die "production Backend identity is invalid"
BACKEND_CONTAINER="$BACKEND_CONTAINERS"
[[ "$(docker_value inspect --format '{{.State.Status}}' "$BACKEND_CONTAINER")" == "running" ]] || die "production Backend is not running"
[[ "$(docker_value inspect --format '{{.State.Health.Status}}' "$BACKEND_CONTAINER")" == "healthy" ]] || die "production Backend is not healthy"
[[ "$(docker_value inspect --format '{{.Config.Image}}' "$BACKEND_CONTAINER")" == "$BACKEND_IMAGE:$RELEASE_SHA" ]] || die "production Backend image reference is invalid"
[[ "$(docker_value inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$BACKEND_CONTAINER")" == "$RELEASE_SHA" ]] || die "production Backend revision is invalid"
[[ "$(docker_value inspect --format "{{ if index .NetworkSettings.Networks \"$DATA_NETWORK\" }}attached{{ end }}" "$BACKEND_CONTAINER")" == "attached" ]] || die "Backend database-egress network is invalid"
docker_value network inspect "$DATA_NETWORK" >/dev/null
docker container inspect "$CONTAINER_NAME" >/dev/null 2>&1 && die "one-shot Container already exists" || true
APPROVED_IMAGE_ID="$(docker_value image inspect --format '{{.Id}}' "$BACKEND_DIGEST")"
[[ "$(docker_value inspect --format '{{.Image}}' "$BACKEND_CONTAINER")" == "$APPROVED_IMAGE_ID" ]] || die "production Backend image identity is invalid"

IMPORT_ARGUMENTS=(
  --spring.main.web-application-type=none
  --pawcycle.catalog.manifest-import.enabled=true
  --pawcycle.catalog.manifest-import.target="$TARGET"
  --pawcycle.catalog.manifest-import.mode="$OPERATION"
)
if [[ "$TARGET" == "demo" ]]; then
  IMPORT_ARGUMENTS+=(--pawcycle.catalog.manifest-import.manifest=classpath:catalog/demo-catalog.json)
fi
if [[ "$OPERATION" == "apply" ]]; then
  IMPORT_ARGUMENTS+=(--pawcycle.catalog.manifest-import.confirm-apply=true)
fi

set +e
COMMAND_OUTPUT="$(docker run --rm --name "$CONTAINER_NAME" --network "$DATA_NETWORK" --env-file <(stream_backend_env) --env JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=65.0 --env SPRING_PROFILES_ACTIVE=production --read-only --tmpfs /tmp:size=64m,mode=1777 --user pawcycle --security-opt no-new-privileges:true --cap-drop ALL --memory 640m --cpus 0.75 --pids-limit 256 --log-driver none "$BACKEND_DIGEST" "${IMPORT_ARGUMENTS[@]}" 2>/dev/null)"
COMMAND_STATUS=$?
set -e
[[ "$COMMAND_STATUS" == "0" ]] || die "catalog import command failed"
case "$TARGET" in
  demo)
    [[ "$COMMAND_OUTPUT" == CATALOG_IMPORT_RESULT\ operation=* ]] || die "catalog import command failed"
    ;;
  customer)
    [[ "$COMMAND_OUTPUT" == CUSTOMER_CATALOG_IMPORT_RESULT\ status=PASS\ baseline=\{CATALOG_IMPORT_RESULT\ operation=* ]] || die "catalog import command failed"
    ;;
esac
printf '%s\n' "$COMMAND_OUTPUT"
