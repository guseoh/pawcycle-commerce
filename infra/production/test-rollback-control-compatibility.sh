#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf -- "$TEST_ROOT"' EXIT

BIN_DIR="$TEST_ROOT/bin"
RUNTIME_DIR="$TEST_ROOT/runtime"
DOCKER_STATE="$TEST_ROOT/docker-state"
REAL_FLOCK="$(command -v flock)"
mkdir -p "$BIN_DIR" "$RUNTIME_DIR/current" "$DOCKER_STATE"

cat >"$RUNTIME_DIR/current/mysql.env" <<'EOF'
MYSQL_DATABASE='pawcycle'
MYSQL_USER='pawcycle'
MYSQL_PASSWORD='test-password'
MYSQL_ROOT_PASSWORD='test-root-password'
EOF
cat >"$RUNTIME_DIR/current/backend.env" <<'EOF'
PAWCYCLE_DATASOURCE_HOST='mysql'
PAWCYCLE_DATASOURCE_PORT='3306'
PAWCYCLE_DATASOURCE_SSL_MODE='DISABLED'
SPRING_DATASOURCE_URL='jdbc:mysql://mysql:3306/pawcycle?sslMode=DISABLED&allowPublicKeyRetrieval=true&serverTimezone=UTC'
SPRING_DATASOURCE_USERNAME='pawcycle'
SPRING_DATASOURCE_PASSWORD='test-password'
PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED='false'
PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE='7'
PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS='12345'
EOF
: >"$RUNTIME_DIR/current/.complete"
printf '%s\n' 'lock' >"$RUNTIME_DIR/.materialize.lock"
chmod 600 \
  "$RUNTIME_DIR/.materialize.lock" \
  "$RUNTIME_DIR/current/mysql.env" \
  "$RUNTIME_DIR/current/backend.env" \
  "$RUNTIME_DIR/current/.complete"

cat >"$BIN_DIR/flock" <<EOF
#!/usr/bin/env bash
exec "$REAL_FLOCK" "\$@"
EOF

cat >"$BIN_DIR/git" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s\n' "$*" >>"$FAKE_GIT_LOG"
case "${1:-}" in
  status) exit 0 ;;
  rev-parse) printf '%s\n' "$FAKE_CONTROL_SHA" ;;
  cat-file) exit 0 ;;
  diff)
    [[ "${FAKE_CONTRACT_MISMATCH:-0}" != "1" ]]
    ;;
esac
EOF

cat >"$BIN_DIR/docker" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail

count=0
[[ ! -f "$FAKE_DOCKER_STATE/calls" ]] || count="$(<"$FAKE_DOCKER_STATE/calls")"
printf '%s\n' "$((count + 1))" >"$FAKE_DOCKER_STATE/calls"

if [[ "$1" == "compose" ]]; then
  command=""
  service=""
  for argument in "$@"; do
    case "$argument" in
      config|up|ps|stop) command="$argument" ;;
      mysql|backend|frontend|proxy) service="$argument" ;;
    esac
  done
  case "$command" in
    config) exit 0 ;;
    up)
      printf '%s\n' "$RELEASE_SHA" >"$FAKE_DOCKER_STATE/active-sha"
      printf '%s\n' "$BACKEND_IMAGE" >"$FAKE_DOCKER_STATE/backend-repository"
      printf '%s\n' "$FRONTEND_IMAGE" >"$FAKE_DOCKER_STATE/frontend-repository"
      printf '%s\n' "$PAWCYCLE_MYSQL_VOLUME" >"$FAKE_DOCKER_STATE/mysql-volume"
      exit 0
      ;;
    ps)
      if [[ "$*" == *"--quiet"* ]]; then
        printf 'container-%s\n' "$service"
      else
        printf 'fake services healthy\n'
      fi
      exit 0
      ;;
    stop) exit 0 ;;
  esac
fi

if [[ "$1" == "pull" ]]; then
  exit 0
fi

if [[ "$1" == "image" && "$2" == "inspect" ]]; then
  reference="${*: -1}"
  if [[ "$*" == *"RepoDigests"* ]]; then
    if [[ "$reference" == *@sha256:* ]]; then
      repository="${reference%%:*}"
      digest="${reference##*@}"
      printf '%s@%s\n' "$repository" "$digest"
    else
      repository="${reference%:*}"
      printf '%s@sha256:%064d\n' "$repository" 1
    fi
  else
    printf '%s\n' "${reference##*:}"
  fi
  exit 0
fi

if [[ "$1" == "inspect" ]]; then
  container="${*: -1}"
  active_sha="$(<"$FAKE_DOCKER_STATE/active-sha")"
  case "$*" in
    *'.State.Health'*) printf 'healthy\n' ;;
    *'.Mounts'*) printf '%s\n' "$(<"$FAKE_DOCKER_STATE/mysql-volume")" ;;
    *'.Config.Image'*)
      case "$container" in
        container-mysql) printf '%s\n' 'mysql:8.4.10@sha256:c592c15aaf4a1961e15d82eb31ea5987dda862d1c4b1e93424438c0e91dc1f8d' ;;
        container-proxy) printf '%s\n' 'nginx:1.30.3-alpine3.23@sha256:0d3b80406a13a767339fbe2f41406d6c7da727ab89cf8fae399e81f780f814d1' ;;
        container-backend) printf '%s:%s\n' "$(<"$FAKE_DOCKER_STATE/backend-repository")" "$active_sha" ;;
        container-frontend) printf '%s:%s\n' "$(<"$FAKE_DOCKER_STATE/frontend-repository")" "$active_sha" ;;
      esac
      ;;
    *) printf '%s\n' "$active_sha" ;;
  esac
  exit 0
fi

if [[ "$1" == "exec" ]]; then
  exit 0
fi

exit 0
EOF

chmod +x "$BIN_DIR/docker" "$BIN_DIR/flock" "$BIN_DIR/git"
export PATH="$BIN_DIR:$PATH"
export FAKE_DOCKER_STATE="$DOCKER_STATE"
export FAKE_GIT_LOG="$TEST_ROOT/git.log"

BACKEND_IMAGE="ghcr.io/example/pawcycle-commerce-backend"
FRONTEND_IMAGE="ghcr.io/example/pawcycle-commerce-frontend"
APP_PREVIOUS="1111111111111111111111111111111111111111"
APP_CURRENT="2222222222222222222222222222222222222222"
APP_OTHER="3333333333333333333333333333333333333333"
CONTROL_PREVIOUS="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
CONTROL_CURRENT="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
CONTROL_INCOMPATIBLE="cccccccccccccccccccccccccccccccccccccccc"
VOLUME="pawcycle-production-mysql-data"

write_state() {
  local state_dir="$1"
  local name="$2"
  local value="$3"
  printf '%s\n' "$value" >"$state_dir/$name"
  chmod 600 "$state_dir/$name"
}

prepare_case() {
  local state_dir="$1"
  local current_sha="$2"
  local previous_sha="$3"
  local previous_contract_sha="$4"
  local contract_sha="$5"

  rm -rf -- "$state_dir"
  mkdir -p "$state_dir"
  write_state "$state_dir" current-sha "$current_sha"
  write_state "$state_dir" previous-sha "$previous_sha"
  write_state "$state_dir" previous-contract-sha "$previous_contract_sha"
  write_state "$state_dir" contract-sha "$contract_sha"
  write_state "$state_dir" active-mysql-volume "$VOLUME"
  printf '%s\n' "$current_sha" >"$DOCKER_STATE/active-sha"
  printf '%s\n' "$BACKEND_IMAGE" >"$DOCKER_STATE/backend-repository"
  printf '%s\n' "$FRONTEND_IMAGE" >"$DOCKER_STATE/frontend-repository"
  printf '%s\n' "$VOLUME" >"$DOCKER_STATE/mysql-volume"
  : >"$DOCKER_STATE/calls"
  : >"$FAKE_GIT_LOG"
}

rollback() {
  local state_dir="$1"
  local target_sha="$2"
  "$SCRIPT_DIR/rollback.sh" \
    --sha "$target_sha" \
    --backend-image "$BACKEND_IMAGE" \
    --frontend-image "$FRONTEND_IMAGE" \
    --runtime-dir "$RUNTIME_DIR" \
    --state-dir "$state_dir" >/dev/null
}

compatible_state="$TEST_ROOT/compatible-state"
prepare_case \
  "$compatible_state" \
  "$APP_CURRENT" \
  "$APP_PREVIOUS" \
  "$CONTROL_PREVIOUS" \
  "$CONTROL_CURRENT"
export FAKE_CONTROL_SHA="$CONTROL_CURRENT"
unset FAKE_CONTRACT_MISMATCH
rollback "$compatible_state" "$APP_PREVIOUS"
grep -Fq "diff --quiet $CONTROL_PREVIOUS $CONTROL_CURRENT" "$FAKE_GIT_LOG"
[[ "$(<"$compatible_state/current-sha")" == "$APP_PREVIOUS" ]]
[[ "$(<"$compatible_state/previous-sha")" == "$APP_CURRENT" ]]
[[ "$(<"$compatible_state/previous-contract-sha")" == "$CONTROL_CURRENT" ]]
[[ "$(<"$compatible_state/active-mysql-volume")" == "$VOLUME" ]]
[[ "$(<"$DOCKER_STATE/mysql-volume")" == "$VOLUME" ]]

incompatible_state="$TEST_ROOT/incompatible-state"
prepare_case \
  "$incompatible_state" \
  "$APP_CURRENT" \
  "$APP_PREVIOUS" \
  "$CONTROL_PREVIOUS" \
  "$CONTROL_INCOMPATIBLE"
export FAKE_CONTROL_SHA="$CONTROL_INCOMPATIBLE"
export FAKE_CONTRACT_MISMATCH=1
docker_calls_before="$(<"$DOCKER_STATE/calls")"
if rollback "$incompatible_state" "$APP_PREVIOUS" 2>"$TEST_ROOT/incompatible-error"; then
  printf 'incompatible recorded previous Control was accepted\n' >&2
  exit 1
fi
grep -Fq 'production release contract differs from the approved contract SHA' \
  "$TEST_ROOT/incompatible-error"
grep -Fq "diff --quiet $CONTROL_PREVIOUS $CONTROL_INCOMPATIBLE" "$FAKE_GIT_LOG"
[[ "$(<"$DOCKER_STATE/calls")" == "$docker_calls_before" ]]
[[ "$(<"$incompatible_state/current-sha")" == "$APP_CURRENT" ]]
[[ "$(<"$incompatible_state/previous-sha")" == "$APP_PREVIOUS" ]]
[[ "$(<"$incompatible_state/previous-contract-sha")" == "$CONTROL_PREVIOUS" ]]
[[ "$(<"$incompatible_state/active-mysql-volume")" == "$VOLUME" ]]

partial_state="$TEST_ROOT/partial-state"
prepare_case \
  "$partial_state" \
  "$APP_CURRENT" \
  "$APP_CURRENT" \
  "$CONTROL_PREVIOUS" \
  "$CONTROL_INCOMPATIBLE"
export FAKE_CONTROL_SHA="$CONTROL_INCOMPATIBLE"
export FAKE_CONTRACT_MISMATCH=1
docker_calls_before="$(<"$DOCKER_STATE/calls")"
if rollback "$partial_state" "$APP_OTHER" 2>"$TEST_ROOT/partial-error"; then
  printf 'partial state write used recorded previous Control fast-path\n' >&2
  exit 1
fi
grep -Fq "diff --quiet $CONTROL_INCOMPATIBLE $APP_OTHER" "$FAKE_GIT_LOG"
if grep -Fq "diff --quiet $CONTROL_PREVIOUS $CONTROL_INCOMPATIBLE" "$FAKE_GIT_LOG"; then
  printf 'partial state write compared the stale previous Control\n' >&2
  exit 1
fi
[[ "$(<"$DOCKER_STATE/calls")" == "$docker_calls_before" ]]
[[ "$(<"$partial_state/current-sha")" == "$APP_CURRENT" ]]
[[ "$(<"$partial_state/previous-sha")" == "$APP_CURRENT" ]]
[[ "$(<"$partial_state/previous-contract-sha")" == "$CONTROL_PREVIOUS" ]]
[[ "$(<"$partial_state/active-mysql-volume")" == "$VOLUME" ]]

printf 'OPS-027 rollback Control compatibility regression tests passed\n'
