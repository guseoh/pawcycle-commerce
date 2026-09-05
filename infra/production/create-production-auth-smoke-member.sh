#!/usr/bin/env bash
set -Eeuo pipefail
set +x

CONTAINER_NAME="pawcycle-ops020-auth-smoke-member"
DATA_NETWORK="pawcycle-production-database-egress"
PROJECT_NAME="pawcycle-production"
PASS_MESSAGE="PASS: production auth smoke member created"
MEMBER_COMMAND_TIMEOUT_SECONDS=180
MEMBER_CLEANUP_GRACE_ATTEMPTS=50
RUNTIME_DIR="/opt/pawcycle/runtime"
STATE_DIR="/opt/pawcycle/state"
RELEASE_SHA=""
BACKEND_IMAGE=""
CONTAINER_STARTED=0
CONTAINER_PROCESS_ID=""
MEMBER_INPUT_FD=""
MEMBER_OUTPUT_FD=""
ECHO_DISABLED=0
TTY_STATE=""
OPERATOR_EMAIL=""
OPERATOR_PASSWORD=""

usage() {
  cat <<'EOF'
Usage: create-production-auth-smoke-member.sh \
  --sha <current-40-char-sha> \
  --backend-image <lowercase-ghcr-repository> \
  [--runtime-dir /opt/pawcycle/runtime] \
  [--state-dir /opt/pawcycle/state]

This high-risk command requires root and an interactive /dev/tty.
EOF
}

die() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

cleanup() {
  local status=$?
  local attempt
  local container_scope
  trap - EXIT INT TERM
  set +e
  if (( ECHO_DISABLED == 1 )) && [[ -n "$TTY_STATE" ]]; then
    stty "$TTY_STATE" <&3 >/dev/null 2>&1
  fi
  if [[ -n "$MEMBER_INPUT_FD" ]]; then
    exec {MEMBER_INPUT_FD}>&-
    MEMBER_INPUT_FD=""
  fi
  if [[ -n "$MEMBER_OUTPUT_FD" ]]; then
    exec {MEMBER_OUTPUT_FD}>&-
    MEMBER_OUTPUT_FD=""
  fi
  terminate_member_process
  if (( CONTAINER_STARTED == 1 )); then
    for (( attempt = 0; attempt < 30; attempt += 1 )); do
      container_scope="$(
        docker inspect --format '{{ index .Config.Labels "com.pawcycle.ops020.scope" }}' \
          "$CONTAINER_NAME" 2>/dev/null
      )"
      if [[ "$container_scope" == "auth-smoke-member" ]]; then
        docker rm --force "$CONTAINER_NAME" >/dev/null 2>&1
        break
      fi
      if docker container inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
        break
      fi
      sleep 0.1
    done
  fi
  OPERATOR_EMAIL=""
  OPERATOR_PASSWORD=""
  unset OPERATOR_EMAIL OPERATOR_PASSWORD
  exec 3>&-
  exit "$status"
}

terminate_member_process() {
  local attempt

  [[ -n "$CONTAINER_PROCESS_ID" ]] || return 0
  if kill -0 "$CONTAINER_PROCESS_ID" >/dev/null 2>&1; then
    kill -TERM "$CONTAINER_PROCESS_ID" >/dev/null 2>&1
    for (( attempt = 0; attempt < MEMBER_CLEANUP_GRACE_ATTEMPTS; attempt += 1 )); do
      kill -0 "$CONTAINER_PROCESS_ID" >/dev/null 2>&1 || break
      sleep 0.1
    done
    if kill -0 "$CONTAINER_PROCESS_ID" >/dev/null 2>&1; then
      kill -KILL "$CONTAINER_PROCESS_ID" >/dev/null 2>&1
    fi
  fi
  wait "$CONTAINER_PROCESS_ID" >/dev/null 2>&1
  CONTAINER_PROCESS_ID=""
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "a required local command is unavailable"
}

validate_absolute_directory() {
  [[ "$1" == /* && "$1" != "/" ]] || die "$2 is invalid"
}

validate_root_directory() {
  local path="$1"
  local label="$2"
  validate_absolute_directory "$path" "$label"
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
  if ! value="$(docker "$@" 2>/dev/null)"; then
    die "Docker preflight failed"
  fi
  printf '%s' "$value"
}

validate_backend_env_contract() {
  local line
  local key
  local encoded_value
  local value_without_escaped_quotes
  local url_count=0
  local username_count=0
  local password_count=0
  local automation_enabled_count=0
  local automation_batch_size_count=0
  local automation_fixed_delay_count=0
  local datasource_host_count=0
  local datasource_port_count=0
  local datasource_database_count=0
  local datasource_ssl_mode_count=0

  while IFS= read -r line || [[ -n "$line" ]]; do
    key="${line%%=*}"
    encoded_value="${line#*=}"
    [[ "$line" == *=* && "$encoded_value" == \'*\' && "${#encoded_value}" -gt 2 ]] \
      || return 1
    encoded_value="${encoded_value:1:${#encoded_value}-2}"
    value_without_escaped_quotes="${encoded_value//\\\'/}"
    [[ "$value_without_escaped_quotes" != *"'"* ]] || return 1
    case "$key" in
      SPRING_DATASOURCE_URL) (( url_count += 1 )) ;;
      SPRING_DATASOURCE_USERNAME) (( username_count += 1 )) ;;
      SPRING_DATASOURCE_PASSWORD) (( password_count += 1 )) ;;
      PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED)
        [[ "$encoded_value" == "true" || "$encoded_value" == "false" ]] \
          || return 1
        (( automation_enabled_count += 1 ))
        ;;
      PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE|PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS)
        [[ "$encoded_value" =~ ^[1-9][0-9]*$ ]] || return 1
        if [[ "$key" == "PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE" ]]; then
          (( automation_batch_size_count += 1 ))
        else
          (( automation_fixed_delay_count += 1 ))
        fi
        ;;
      PAWCYCLE_DATASOURCE_HOST) (( datasource_host_count += 1 )) ;;
      PAWCYCLE_DATASOURCE_PORT) [[ "$encoded_value" == 3306 ]] || return 1; (( datasource_port_count += 1 )) ;;
      PAWCYCLE_DATASOURCE_DATABASE) [[ "$encoded_value" =~ ^[A-Za-z0-9_]{1,64}$ ]] || return 1; (( datasource_database_count += 1 )) ;;
      PAWCYCLE_DATASOURCE_SSL_MODE) [[ "$encoded_value" == REQUIRED ]] || return 1; (( datasource_ssl_mode_count += 1 )) ;;
      *) return 1 ;;
    esac
  done < "$BACKEND_ENV_FILE"

  (( datasource_host_count == 1 && datasource_port_count == 1 && datasource_database_count == 1 \
    && datasource_ssl_mode_count == 1 && url_count == 1 && username_count == 1 && password_count == 1 \
    && automation_enabled_count == 1 && automation_batch_size_count == 1 && automation_fixed_delay_count == 1 ))
}

stream_backend_env() {
  local line
  local key
  local encoded_value

  while IFS= read -r line || [[ -n "$line" ]]; do
    key="${line%%=*}"
    encoded_value="${line#*=}"
    encoded_value="${encoded_value:1:${#encoded_value}-2}"
    encoded_value="${encoded_value//\\\'/\'}"
    case "$key" in
      PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED|PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE|PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS) ;;
      *) printf '%s=%s\n' "$key" "$encoded_value" ;;
    esac
  done < "$BACKEND_ENV_FILE"
  printf '%s\n' 'PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED=false'
}

while (( $# > 0 )); do
  case "$1" in
    --sha) RELEASE_SHA="${2:-}"; shift 2 ;;
    --backend-image) BACKEND_IMAGE="${2:-}"; shift 2 ;;
    --runtime-dir) RUNTIME_DIR="${2:-}"; shift 2 ;;
    --state-dir) STATE_DIR="${2:-}"; shift 2 ;;
    --help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
  esac
done

[[ "$EUID" == "0" ]] || die "root execution is required"
[[ "$RELEASE_SHA" =~ ^[0-9a-f]{40}$ ]] || die "approved release SHA is invalid"
[[ "$BACKEND_IMAGE" =~ ^ghcr\.io/[a-z0-9][a-z0-9._-]*/[a-z0-9][a-z0-9._/-]*$ ]] \
  || die "Backend image repository is invalid"
for command in docker flock git grep readlink sleep stat stty timeout; do
  require_command "$command"
done
if ! { exec 3<>/dev/tty; } 2>/dev/null || [[ ! -t 3 ]]; then
  die "an interactive terminal is required"
fi
TTY_STATE="$(stty -g <&3 2>/dev/null)" || die "terminal state is unavailable"
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

validate_root_directory "$RUNTIME_DIR" "runtime directory"
validate_root_directory "$STATE_DIR" "state directory"
DEPLOY_LOCK_FILE="$STATE_DIR/deploy.lock"
[[ ! -e "$DEPLOY_LOCK_FILE" || ( -f "$DEPLOY_LOCK_FILE" && ! -L "$DEPLOY_LOCK_FILE" ) ]] \
  || die "production release lock is unsafe"
exec 9>>"$DEPLOY_LOCK_FILE"
flock --nonblock 9 || die "another production release command is running"
CURRENT_SHA_FILE="$STATE_DIR/current-sha"
IMAGE_STATE_FILE="$STATE_DIR/$RELEASE_SHA.images"
validate_protected_file "$CURRENT_SHA_FILE" "current release state"
validate_protected_file "$IMAGE_STATE_FILE" "release image state"
[[ "$(<"$CURRENT_SHA_FILE")" == "$RELEASE_SHA" ]] || die "approved SHA is not the current production release"

RUNTIME_CURRENT="$(readlink -f -- "$RUNTIME_DIR/current" 2>/dev/null || true)"
[[ "$RUNTIME_CURRENT" == "$RUNTIME_DIR"/.bundle.* && -d "$RUNTIME_CURRENT" ]] \
  || die "runtime bundle target is unavailable or unsafe"
[[ "$(stat -c '%a' "$RUNTIME_CURRENT" 2>/dev/null)" == "700" ]] \
  || die "runtime bundle permissions are invalid"
BACKEND_ENV_FILE="$RUNTIME_CURRENT/backend.env"
COMPLETE_FILE="$RUNTIME_CURRENT/.complete"
validate_protected_file "$BACKEND_ENV_FILE" "Backend runtime file"
validate_protected_file "$COMPLETE_FILE" "runtime completion marker"
validate_backend_env_contract || die "Backend runtime contract is incomplete"

git cat-file -e "${RELEASE_SHA}^{commit}" 2>/dev/null || die "approved release commit is unavailable"
git grep -Fq 'spring.flyway.enabled' "$RELEASE_SHA" -- \
  backend/src/main/java/com/pawcycle/backend/member/maintenance/ProductionAuthSmokeMemberBootstrap.java \
  2>/dev/null \
  || die "approved release does not contain the OPS-019 maintenance gate"

BACKEND_DIGEST_LINE="$(grep -E '^BACKEND_DIGEST=ghcr\.io/.+@sha256:[0-9a-f]{64}$' "$IMAGE_STATE_FILE" 2>/dev/null || true)"
[[ "$(grep -c '^BACKEND_DIGEST=' "$IMAGE_STATE_FILE" 2>/dev/null)" == "1" \
  && "$(printf '%s\n' "$BACKEND_DIGEST_LINE" | grep -c '^BACKEND_DIGEST=')" == "1" ]] \
  || die "Backend digest state is invalid"
BACKEND_DIGEST="${BACKEND_DIGEST_LINE#BACKEND_DIGEST=}"
[[ "$BACKEND_DIGEST" == "$BACKEND_IMAGE@sha256:"* ]] || die "Backend digest repository does not match"

if ! docker pull "$BACKEND_IMAGE:$RELEASE_SHA" >/dev/null 2>&1; then
  die "approved Backend image is unavailable"
fi
IMAGE_REVISION="$(docker_value image inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$BACKEND_DIGEST")"
IMAGE_USER="$(docker_value image inspect --format '{{.Config.User}}' "$BACKEND_DIGEST")"
APPROVED_IMAGE_ID="$(docker_value image inspect --format '{{.Id}}' "$BACKEND_DIGEST")"
REPO_DIGESTS="$(docker_value image inspect --format '{{ range .RepoDigests }}{{ println . }}{{ end }}' "$BACKEND_IMAGE:$RELEASE_SHA")"
[[ "$IMAGE_REVISION" == "$RELEASE_SHA" ]] || die "Backend image revision is invalid"
[[ "$IMAGE_USER" == "pawcycle" ]] || die "Backend image user is invalid"
printf '%s\n' "$REPO_DIGESTS" | grep -Fxq "$BACKEND_DIGEST" || die "Backend image digest drift detected"

mapfile -t BACKEND_CONTAINERS < <(
  docker ps \
    --filter "label=com.docker.compose.project=$PROJECT_NAME" \
    --filter 'label=com.docker.compose.service=backend' \
    --format '{{.ID}}' 2>/dev/null
)
[[ "${#BACKEND_CONTAINERS[@]}" == "1" && -n "${BACKEND_CONTAINERS[0]}" ]] \
  || die "production Backend identity is invalid"
BACKEND_CONTAINER="${BACKEND_CONTAINERS[0]}"
[[ "$(docker_value inspect --format '{{.State.Status}}' "$BACKEND_CONTAINER")" == "running" ]] \
  || die "production Backend is not running"
[[ "$(docker_value inspect --format '{{.State.Health.Status}}' "$BACKEND_CONTAINER")" == "healthy" ]] \
  || die "production Backend is not healthy"
[[ "$(docker_value inspect --format '{{.Config.Image}}' "$BACKEND_CONTAINER")" == "$BACKEND_IMAGE:$RELEASE_SHA" ]] \
  || die "production Backend image reference is invalid"
[[ "$(docker_value inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$BACKEND_CONTAINER")" == "$RELEASE_SHA" ]] \
  || die "production Backend revision is invalid"
[[ "$(docker_value inspect --format '{{.Image}}' "$BACKEND_CONTAINER")" == "$APPROVED_IMAGE_ID" ]] \
  || die "production Backend image identity is invalid"

[[ "$(docker_value inspect --format "{{ if index .NetworkSettings.Networks \"$DATA_NETWORK\" }}attached{{ end }}" "$BACKEND_CONTAINER")" == "attached" ]] \
  || die "Backend database-egress network is invalid"
docker_value network inspect "$DATA_NETWORK" >/dev/null
[[ "$(docker_value network inspect --format '{{.Internal}}' "$DATA_NETWORK")" != "true" ]] \
  || die "database-egress network must remain non-internal"
if docker container inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
  die "OPS-020 one-shot Container already exists"
fi

printf 'Email: ' >&3
IFS= read -r OPERATOR_EMAIL <&3 || die "credential input failed"
printf 'Password: ' >&3
stty -echo <&3 || die "password echo could not be disabled"
ECHO_DISABLED=1
IFS= read -r OPERATOR_PASSWORD <&3 || die "credential input failed"
stty "$TTY_STATE" <&3 || die "terminal echo could not be restored"
ECHO_DISABLED=0
printf '\n' >&3

CONTAINER_STARTED=1
coproc MEMBER_CONTAINER {
  exec timeout \
      --signal=TERM \
      --kill-after=10s \
      "${MEMBER_COMMAND_TIMEOUT_SECONDS}s" \
    docker run \
      --rm \
      --interactive \
      --name "$CONTAINER_NAME" \
      --label com.pawcycle.ops020.scope=auth-smoke-member \
      --network "$DATA_NETWORK" \
      --env-file <(stream_backend_env) \
      --env 'JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=65.0 -XX:+ExitOnOutOfMemoryError' \
      --env SPRING_PROFILES_ACTIVE=production \
      --read-only \
      --tmpfs /tmp:size=64m,mode=1777 \
      --user pawcycle \
      --security-opt no-new-privileges:true \
      --cap-drop ALL \
      --memory 640m \
      --cpus 0.75 \
      --pids-limit 256 \
      --log-driver none \
      "$BACKEND_DIGEST" \
      --spring.main.web-application-type=none \
      --pawcycle.maintenance.create-auth-smoke-member.enabled=true \
      --spring.flyway.enabled=false \
      2>/dev/null
}
CONTAINER_PROCESS_ID="$MEMBER_CONTAINER_PID"
MEMBER_INPUT_FD="${MEMBER_CONTAINER[1]}"
MEMBER_OUTPUT_FD="${MEMBER_CONTAINER[0]}"
printf '%s\n%s\n' "$OPERATOR_EMAIL" "$OPERATOR_PASSWORD" >&"$MEMBER_INPUT_FD"
exec {MEMBER_INPUT_FD}>&-
MEMBER_INPUT_FD=""
OPERATOR_EMAIL=""
OPERATOR_PASSWORD=""
mapfile -t CONTAINER_OUTPUT_LINES <&"$MEMBER_OUTPUT_FD"
exec {MEMBER_OUTPUT_FD}>&-
MEMBER_OUTPUT_FD=""
if ! wait "$CONTAINER_PROCESS_ID"; then
  CONTAINER_PROCESS_ID=""
  die "member creation Container failed"
fi
CONTAINER_PROCESS_ID=""
CONTAINER_STARTED=0
[[ "${#CONTAINER_OUTPUT_LINES[@]}" == "1" && "${CONTAINER_OUTPUT_LINES[0]}" == "$PASS_MESSAGE" ]] \
  || die "member creation result is invalid"
printf '%s\n' "$PASS_MESSAGE"
