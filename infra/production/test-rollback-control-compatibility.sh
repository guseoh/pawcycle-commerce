#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
COMMON="$SCRIPT_DIR/release-common.sh"
ROLLBACK="$SCRIPT_DIR/rollback.sh"
DEPLOY="$SCRIPT_DIR/deploy.sh"

bash -n "$COMMON" "$ROLLBACK" "$DEPLOY"
if grep -Eq 'active-mysql-volume|PAWCYCLE_MYSQL_ENV_FILE|PAWCYCLE_MYSQL_VOLUME|MYSQL_DIGEST|compose (start|stop|rm).*mysql' "$COMMON" "$ROLLBACK" "$DEPLOY"; then
  printf 'obsolete local MySQL rollback contract remains\n' >&2
  exit 1
fi
grep -Fq 'acquire_release_lock' "$ROLLBACK"
grep -Fq 'TARGET_SHA="$(read_state_sha previous-sha)"' "$ROLLBACK"
grep -Fq 'require_no_migration_boundary_rollback' "$ROLLBACK"
grep -Fq 'managed database was not modified by the Application release lifecycle' "$ROLLBACK"
grep -Fq 'previous-contract-sha' "$ROLLBACK"
grep -Fq 'compose stop proxy frontend backend' "$DEPLOY"

TEST_ROOT="$(mktemp -d)"
BIN_DIR="$TEST_ROOT/bin"
RUNTIME_DIR="$TEST_ROOT/runtime"
DOCKER_STATE="$TEST_ROOT/docker-state"
FAKE_DOCKER_LOG="$TEST_ROOT/docker.log"
mkdir -p "$BIN_DIR" "$RUNTIME_DIR/.bundle.fixture" "$DOCKER_STATE"
chmod 700 "$RUNTIME_DIR" "$RUNTIME_DIR/.bundle.fixture"
trap 'rm -rf -- "$TEST_ROOT"' EXIT

APP_PREVIOUS="1111111111111111111111111111111111111111"
APP_CURRENT="2222222222222222222222222222222222222222"
CONTROL_CURRENT="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
BACKEND_IMAGE="ghcr.io/example/pawcycle-commerce-backend"
FRONTEND_IMAGE="ghcr.io/example/pawcycle-commerce-frontend"
export BACKEND_IMAGE FRONTEND_IMAGE FAKE_DOCKER_LOG

ln -s .bundle.fixture "$RUNTIME_DIR/current"
: >"$RUNTIME_DIR/.materialize.lock"
chmod 600 "$RUNTIME_DIR/.materialize.lock"
cat >"$RUNTIME_DIR/.bundle.fixture/backend.env" <<'EOF'
PAWCYCLE_DATASOURCE_HOST='db.example.com'
PAWCYCLE_DATASOURCE_PORT='3306'
PAWCYCLE_DATASOURCE_DATABASE='pawcycle'
PAWCYCLE_DATASOURCE_SSL_MODE='REQUIRED'
SPRING_DATASOURCE_URL='jdbc:mysql://db.example.com:3306/pawcycle?sslMode=REQUIRED&serverTimezone=UTC'
SPRING_DATASOURCE_USERNAME='rollback_fixture_user'
SPRING_DATASOURCE_PASSWORD='rollback_fixture_password'
PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED='false'
PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE='7'
PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS='12345'
EOF
printf 'RUNTIME_ENV_FORMAT=1\n' >"$RUNTIME_DIR/.bundle.fixture/.complete"
chmod 600 "$RUNTIME_DIR/.bundle.fixture/backend.env" "$RUNTIME_DIR/.bundle.fixture/.complete"

cat >"$BIN_DIR/git" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
if [[ "${1:-}" == -C ]]; then shift 2; fi
case "${1:-}" in
  status) exit 0 ;;
  rev-parse) printf '%s\n' "$FAKE_CONTROL_SHA" ;;
  cat-file) exit 0 ;;
  diff)
    if [[ "${FAKE_MIGRATION_CHANGED:-0}" == 1 && "$*" == *'backend/src/main/resources/db/migration'* ]]; then
      exit 1
    fi
    exit 0
    ;;
  *) exit 0 ;;
esac
EOF

cat >"$BIN_DIR/docker" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s\n' "$*" >>"$FAKE_DOCKER_LOG"
if [[ "${1:-}" == compose ]]; then
  if [[ "$*" == *' config --quiet'* ]]; then exit 0; fi
  if [[ "$*" == *' up '* ]]; then
    if [[ -n "${FAKE_ACTIVATION_FAIL_SHA:-}" && "${RELEASE_SHA:-}" == "$FAKE_ACTIVATION_FAIL_SHA" ]]; then exit 1; fi
    exit 0
  fi
  if [[ "$*" == *' stop '* ]]; then exit 0; fi
  if [[ "$*" == *' ps '* ]]; then
    service=""
    for argument in "$@"; do
      case "$argument" in backend|frontend|proxy) service="$argument" ;; esac
    done
    if [[ "$*" == *'--status running'* && "$service" == backend && "${FAKE_BACKEND_STOPPED:-0}" == 1 ]]; then exit 0; fi
    if [[ "$*" == *'--quiet'* ]]; then printf 'container-%s\n' "$service"; else printf 'fake services healthy\n'; fi
    exit 0
  fi
  exit 0
fi
if [[ "${1:-}" == pull ]]; then exit 0; fi
if [[ "${1:-}" == image && "${2:-}" == inspect ]]; then
  reference="${*: -1}"
  if [[ "$*" == *RepoDigests* ]]; then
    if [[ "$reference" == *@sha256:* ]]; then
      printf '%s@%s\n' "${reference%%:*}" "${reference##*@}"
    else
      printf '%s@sha256:%064d\n' "${reference%:*}" 1
    fi
  elif [[ "$*" == *'org.opencontainers.image.revision'* ]]; then
    printf '%s\n' "${reference##*:}"
  else
    printf '%s\n' "${reference##*:}"
  fi
  exit 0
fi
if [[ "${1:-}" == inspect ]]; then
  container="${*: -1}"
  case "$*" in
    *'.State.Health'*) printf 'healthy\n' ;;
    *'.Config.Image'*)
      case "$container" in
        container-backend) printf '%s:%s\n' "$BACKEND_IMAGE" "$ACTIVE_SHA" ;;
        container-frontend) printf '%s:%s\n' "$FRONTEND_IMAGE" "$ACTIVE_SHA" ;;
        container-proxy) printf '%s\n' 'nginx:1.30.3-alpine3.23@sha256:0d3b80406a13a767339fbe2f41406d6c7da727ab89cf8fae399e81f780f814d1' ;;
      esac
      ;;
    *'NetworkSettings.Networks'*) printf 'attached\n' ;;
    *) printf '%s\n' "$ACTIVE_SHA" ;;
  esac
  exit 0
fi
if [[ "${1:-}" == exec ]]; then exit 0; fi
exit 0
EOF
chmod +x "$BIN_DIR/git" "$BIN_DIR/docker"
export PATH="$BIN_DIR:$PATH"
export FAKE_CONTROL_SHA="$CONTROL_CURRENT"

write_state() {
  local state_dir="$1" name="$2" value="$3"
  printf '%s\n' "$value" >"$state_dir/$name"
  chmod 600 "$state_dir/$name"
}

prepare_case() {
  local state_dir="$1" current_sha="$2" previous_sha="$3" previous_contract_sha="$4" contract_sha="$5"
  rm -rf -- "$state_dir"
  mkdir -p "$state_dir"
  chmod 700 "$state_dir"
  write_state "$state_dir" current-sha "$current_sha"
  write_state "$state_dir" previous-sha "$previous_sha"
  write_state "$state_dir" previous-contract-sha "$previous_contract_sha"
  write_state "$state_dir" contract-sha "$contract_sha"
  : >"$state_dir/deploy.lock"
  chmod 600 "$state_dir/deploy.lock"
  : >"$FAKE_DOCKER_LOG"
}

run_rollback() {
  local state_dir="$1" target_sha="$2" output="$3"
  if ! "$ROLLBACK" --sha "$target_sha" --backend-image "$BACKEND_IMAGE" --frontend-image "$FRONTEND_IMAGE" \
    --runtime-dir "$RUNTIME_DIR" --state-dir "$state_dir" >"$output" 2>&1; then
    return 1
  fi
}

state_snapshot() {
  local state_dir="$1" name
  for name in current-sha previous-sha previous-contract-sha contract-sha; do
    sha256sum "$state_dir/$name"
  done
  if [[ -e "$state_dir/release-state-transition" || -L "$state_dir/release-state-transition" ]]; then
    printf 'transition-present\n'
  else
    printf 'transition-absent\n'
  fi
}

COMPATIBLE_STATE="$TEST_ROOT/compatible-state"
prepare_case "$COMPATIBLE_STATE" "$APP_CURRENT" "$APP_PREVIOUS" "$CONTROL_CURRENT" "$CONTROL_CURRENT"
unset FAKE_MIGRATION_CHANGED FAKE_ACTIVATION_FAIL_SHA
run_rollback "$COMPATIBLE_STATE" "$APP_PREVIOUS" "$TEST_ROOT/compatible-output"
[[ "$(<"$COMPATIBLE_STATE/current-sha")" == "$APP_PREVIOUS" ]]
[[ "$(<"$COMPATIBLE_STATE/previous-sha")" == "$APP_CURRENT" ]]
[[ "$(<"$COMPATIBLE_STATE/previous-contract-sha")" == "$CONTROL_CURRENT" ]]
[[ "$(<"$COMPATIBLE_STATE/contract-sha")" == "$CONTROL_CURRENT" ]]
[[ ! -e "$COMPATIBLE_STATE/release-state-transition" ]]
grep -Fq 'up --detach --pull never --remove-orphans backend frontend' "$FAKE_DOCKER_LOG"
grep -Fq 'up --detach --pull never --no-deps --force-recreate proxy' "$FAKE_DOCKER_LOG"
if grep -Eq '(^|[[:space:]])mysql([[:space:]]|$)' "$FAKE_DOCKER_LOG"; then
  printf 'rollback activated an obsolete local MySQL service\n' >&2
  exit 1
fi
(
  exec 9>"$COMPATIBLE_STATE/deploy.lock"
  flock -n 9
)

MIGRATION_STATE="$TEST_ROOT/migration-state"
prepare_case "$MIGRATION_STATE" "$APP_CURRENT" "$APP_PREVIOUS" "$CONTROL_CURRENT" "$CONTROL_CURRENT"
before_snapshot="$(state_snapshot "$MIGRATION_STATE")"
export FAKE_MIGRATION_CHANGED=1
if run_rollback "$MIGRATION_STATE" "$APP_PREVIOUS" "$TEST_ROOT/migration-output"; then
  printf 'migration-boundary rollback was accepted\n' >&2
  exit 1
fi
[[ "$(state_snapshot "$MIGRATION_STATE")" == "$before_snapshot" ]]
[[ ! -s "$FAKE_DOCKER_LOG" ]]
! grep -Fq 'compose' "$FAKE_DOCKER_LOG"
unset FAKE_MIGRATION_CHANGED

FAILED_STATE="$TEST_ROOT/failed-state"
prepare_case "$FAILED_STATE" "$APP_CURRENT" "$APP_PREVIOUS" "$CONTROL_CURRENT" "$CONTROL_CURRENT"
before_snapshot="$(state_snapshot "$FAILED_STATE")"
export FAKE_ACTIVATION_FAIL_SHA="$APP_PREVIOUS"
if run_rollback "$FAILED_STATE" "$APP_PREVIOUS" "$TEST_ROOT/failed-output"; then
  printf 'failed activation was reported as rollback success\n' >&2
  exit 1
fi
grep -Fq 'rollback target failed; current release was restored' "$TEST_ROOT/failed-output"
[[ "$(state_snapshot "$FAILED_STATE")" == "$before_snapshot" ]]
[[ ! -e "$FAILED_STATE/release-state-transition" ]]
unset FAKE_ACTIVATION_FAIL_SHA

printf 'OPS-OCI-002 rollback behavior, migration boundary, lock, and managed-DB contract tests passed\n'
