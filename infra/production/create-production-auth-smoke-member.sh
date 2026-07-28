#!/usr/bin/env bash
set -Eeuo pipefail
set +x

CONTAINER_NAME="pawcycle-ops020-auth-smoke-member"
DATA_NETWORK="pawcycle-production-data"
PROJECT_NAME="pawcycle-production"
PASS_MESSAGE="PASS: production auth smoke member created"
RUNTIME_DIR="/opt/pawcycle/runtime"
STATE_DIR="/opt/pawcycle/state"
RELEASE_SHA=""
BACKEND_IMAGE=""
CONTAINER_STARTED=0
CONTAINER_PROCESS_ID=""
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
  trap - EXIT INT TERM
  set +e
  if (( ECHO_DISABLED == 1 )) && [[ -n "$TTY_STATE" ]]; then
    stty "$TTY_STATE" <&3 >/dev/null 2>&1
  fi
  if (( CONTAINER_STARTED == 1 )); then
    container_scope="$(
      docker inspect --format '{{ index .Config.Labels "com.pawcycle.ops020.scope" }}' \
        "$CONTAINER_NAME" 2>/dev/null
    )"
    if [[ "$container_scope" == "auth-smoke-member" ]]; then
      docker rm --force "$CONTAINER_NAME" >/dev/null 2>&1
    fi
  fi
  if [[ -n "$CONTAINER_PROCESS_ID" ]]; then
    wait "$CONTAINER_PROCESS_ID" >/dev/null 2>&1
  fi
  OPERATOR_EMAIL=""
  OPERATOR_PASSWORD=""
  unset OPERATOR_EMAIL OPERATOR_PASSWORD
  exec 3>&-
  exit "$status"
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
for command in docker git stat grep readlink stty; do
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
for key in SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD; do
  [[ "$(grep -Ec "^${key}=.+$" "$BACKEND_ENV_FILE" 2>/dev/null)" == "1" ]] \
    || die "Backend runtime contract is incomplete"
done

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
REPO_DIGESTS="$(docker_value image inspect --format '{{ range .RepoDigests }}{{ println . }}{{ end }}' "$BACKEND_IMAGE:$RELEASE_SHA")"
[[ "$IMAGE_REVISION" == "$RELEASE_SHA" ]] || die "Backend image revision is invalid"
[[ "$IMAGE_USER" == "pawcycle" ]] || die "Backend image user is invalid"
printf '%s\n' "$REPO_DIGESTS" | grep -Fxq "$BACKEND_DIGEST" || die "Backend image digest drift detected"

mapfile -t MYSQL_CONTAINERS < <(
  docker ps \
    --filter "label=com.docker.compose.project=$PROJECT_NAME" \
    --filter 'label=com.docker.compose.service=mysql' \
    --format '{{.ID}}' 2>/dev/null
)
[[ "${#MYSQL_CONTAINERS[@]}" == "1" && -n "${MYSQL_CONTAINERS[0]}" ]] \
  || die "production MySQL identity is invalid"
MYSQL_CONTAINER="${MYSQL_CONTAINERS[0]}"
[[ "$(docker_value inspect --format '{{.State.Status}}' "$MYSQL_CONTAINER")" == "running" ]] \
  || die "production MySQL is not running"
[[ "$(docker_value inspect --format '{{.State.Health.Status}}' "$MYSQL_CONTAINER")" == "healthy" ]] \
  || die "production MySQL is not healthy"
[[ "$(docker_value inspect --format "{{ if index .NetworkSettings.Networks \"$DATA_NETWORK\" }}attached{{ end }}" "$MYSQL_CONTAINER")" == "attached" ]] \
  || die "production MySQL data network is invalid"
[[ "$(docker_value network inspect --format '{{.Internal}}' "$DATA_NETWORK")" == "true" ]] \
  || die "production data network is invalid"
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
  docker run \
      --rm \
      --interactive \
      --name "$CONTAINER_NAME" \
      --label com.pawcycle.ops020.scope=auth-smoke-member \
      --network "$DATA_NETWORK" \
      --env-file "$BACKEND_ENV_FILE" \
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
OPERATOR_EMAIL=""
OPERATOR_PASSWORD=""
mapfile -t CONTAINER_OUTPUT_LINES <&"$MEMBER_OUTPUT_FD"
exec {MEMBER_OUTPUT_FD}>&-
if ! wait "$CONTAINER_PROCESS_ID"; then
  die "member creation Container failed"
fi
CONTAINER_PROCESS_ID=""
CONTAINER_STARTED=0
[[ "${#CONTAINER_OUTPUT_LINES[@]}" == "1" && "${CONTAINER_OUTPUT_LINES[0]}" == "$PASS_MESSAGE" ]] \
  || die "member creation result is invalid"
printf '%s\n' "$PASS_MESSAGE"
