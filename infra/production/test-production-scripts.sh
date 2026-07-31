#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf -- "$TEST_ROOT"' EXIT

BIN_DIR="$TEST_ROOT/bin"
RUNTIME_DIR="$TEST_ROOT/runtime"
STATE_DIR="$TEST_ROOT/state"
FAKE_DOCKER_STATE="$TEST_ROOT/docker-state"
REAL_FLOCK="$(command -v flock)"
mkdir -p "$BIN_DIR" "$FAKE_DOCKER_STATE"
export FAKE_DOCKER_STATE REAL_FLOCK

cat > "$BIN_DIR/aws" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
name=""
while (( $# > 0 )); do
  case "$1" in
    --name) name="${2:-}"; shift 2 ;;
    *) shift ;;
  esac
done
leaf="${name##*/}"
[[ "${FAKE_MISSING:-}" != "$leaf" ]] || exit 254
if [[ "$leaf" == "MYSQL_DATABASE" && -n "${FAKE_AWS_BLOCK_DIR:-}" ]]; then
  : > "$FAKE_AWS_BLOCK_DIR/started"
  while [[ ! -e "$FAKE_AWS_BLOCK_DIR/release" ]]; do
    sleep 0.1
  done
fi
case "$leaf" in
  MYSQL_DATABASE) printf 'ops010' ;;
  MYSQL_USER) printf 'ops010_user' ;;
  MYSQL_PASSWORD) printf 'local-%%pa$$word#' ;;
  MYSQL_ROOT_PASSWORD) printf 'local-root-%%pa$$word#' ;;
  *) exit 254 ;;
esac
EOF

cat > "$BIN_DIR/curl" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
request="${*: -1}"
active_sha=""
if [[ -f "$FAKE_DOCKER_STATE/active-sha" ]]; then
  active_sha="$(<"$FAKE_DOCKER_STATE/active-sha")"
fi
if [[ "${FAKE_SMOKE_FAIL_SHA:-}" == "$active_sha" && "$request" == *"${FAKE_SMOKE_FAIL_PATH:-}" ]]; then
  exit 22
fi
if [[ "$request" == https://* \
  && "${FAKE_HTTPS_FAIL_SHA:-}" == "$active_sha" \
  && "$request" == *"${FAKE_HTTPS_FAIL_PATH:-}" ]]; then
  exit 22
fi
if [[ "$request" == *"/.well-known/acme-challenge/"* && "${FAKE_CHALLENGE_FAIL:-}" == "1" ]]; then
  exit 22
fi
if [[ "$request" == *"/.well-known/acme-challenge/"* \
  && -n "${FAKE_CHALLENGE_FAIL_AT_COUNT:-}" \
  && -f "$FAKE_DOCKER_STATE/challenge-create-count" \
  && "$(<"$FAKE_DOCKER_STATE/challenge-create-count")" == "$FAKE_CHALLENGE_FAIL_AT_COUNT" ]]; then
    exit 22
fi
if [[ "$*" == *"%{http_code}"* ]]; then
  if [[ "${FAKE_REDIRECT_FAIL_SHA:-}" == "$active_sha" ]]; then
    printf '302'
  else
    printf '301'
  fi
elif [[ "$*" == *"%{redirect_url}"* ]]; then
  printf 'https://%s/products' "${FAKE_DOMAIN:?}"
elif [[ "$request" == *"/.well-known/acme-challenge/"* ]]; then
  printf 'pawcycle-acme-probe'
fi
EOF

cat > "$BIN_DIR/git" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
case "${1:-}" in
  cat-file) exit 0 ;;
  rev-parse) printf '%s\n' "${FAKE_CONTROL_SHA:?}"; exit 0 ;;
  status)
    [[ "${FAKE_CONTROL_DIRTY:-}" != "1" ]] || printf ' M infra/production/deploy.sh\n'
    exit 0
    ;;
  diff)
    [[ "${FAKE_CONTRACT_MISMATCH:-}" != "1" ]] || exit 1
    exit 0
    ;;
esac
exit 0
EOF

cat > "$BIN_DIR/flock" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
if [[ -n "${FAKE_FLOCK_PREVIOUS_STATE:-}" ]]; then
  printf '%s\n' "${FAKE_FLOCK_PREVIOUS_SHA:?}" >"$FAKE_FLOCK_PREVIOUS_STATE"
  chmod 600 "$FAKE_FLOCK_PREVIOUS_STATE"
fi
exec "$REAL_FLOCK" "$@"
EOF

cat > "$BIN_DIR/docker" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail

docker_call_count=0
[[ ! -f "$FAKE_DOCKER_STATE/docker-call-count" ]] || docker_call_count="$(<"$FAKE_DOCKER_STATE/docker-call-count")"
printf '%s' "$((docker_call_count + 1))" > "$FAKE_DOCKER_STATE/docker-call-count"

if [[ "$1" == "compose" ]]; then
  command=""
  service=""
  for argument in "$@"; do
    case "$argument" in
      config|pull|up|ps|stop) command="$argument" ;;
      mysql|backend|frontend|proxy) service="$argument" ;;
    esac
  done
  case "$command" in
    up)
      count=0
      [[ ! -f "$FAKE_DOCKER_STATE/up-count" ]] || count="$(<"$FAKE_DOCKER_STATE/up-count")"
      printf '%s' "$((count + 1))" > "$FAKE_DOCKER_STATE/up-count"
      if [[ "${FAKE_UP_FAIL_AT_COUNT:-}" == "$((count + 1))" ]]; then
        exit 1
      fi
      printf '%s' "$RELEASE_SHA" > "$FAKE_DOCKER_STATE/active-sha"
      printf '%s' "$BACKEND_IMAGE" > "$FAKE_DOCKER_STATE/backend-image"
      printf '%s' "$FRONTEND_IMAGE" > "$FAKE_DOCKER_STATE/frontend-image"
      printf '%s' 'mysql:8.4.10@sha256:c592c15aaf4a1961e15d82eb31ea5987dda862d1c4b1e93424438c0e91dc1f8d' > "$FAKE_DOCKER_STATE/mysql-image"
      printf '%s' "$PAWCYCLE_MYSQL_VOLUME" > "$FAKE_DOCKER_STATE/mysql-volume"
      printf '%s' 'nginx:1.30.3-alpine3.23@sha256:0d3b80406a13a767339fbe2f41406d6c7da727ab89cf8fae399e81f780f814d1' > "$FAKE_DOCKER_STATE/proxy-image"
      ;;
    ps)
      if [[ "$*" == *"--quiet"* ]]; then
        printf 'container-%s\n' "$service"
      elif [[ "${FAKE_PS_FAIL:-}" == "1" ]]; then
        exit 2
      else
        printf 'fake compose services healthy\n'
      fi
      ;;
    stop)
      count=0
      [[ ! -f "$FAKE_DOCKER_STATE/stop-count" ]] || count="$(<"$FAKE_DOCKER_STATE/stop-count")"
      printf '%s' "$((count + 1))" > "$FAKE_DOCKER_STATE/stop-count"
      if [[ "${FAKE_STOP_FAIL_AT_COUNT:-}" == "$((count + 1))" ]]; then
        exit 1
      fi
      ;;
  esac
  exit 0
fi

if [[ "$1" == "pull" ]]; then
  exit 0
fi

if [[ "$1" == "volume" ]]; then
  volume="${*: -1}"
  case "$2" in
    inspect)
      [[ -f "$FAKE_DOCKER_STATE/volume-$volume" ]] || exit 1
      if [[ "$*" == *"com.pawcycle.ops025.scope"* ]]; then
        printf '%s\n' "$(<"$FAKE_DOCKER_STATE/volume-label-scope-$volume")"
      elif [[ "$*" == *"com.pawcycle.ops025.source-volume"* ]]; then
        printf '%s\n' "$(<"$FAKE_DOCKER_STATE/volume-label-source-$volume")"
      elif [[ "$*" == *"com.pawcycle.ops025.backup-sha256"* ]]; then
        printf '%s\n' "$(<"$FAKE_DOCKER_STATE/volume-label-backup-$volume")"
      elif [[ "$*" == *"com.pawcycle.ops025.manifest-sha256"* ]]; then
        printf '%s\n' "$(<"$FAKE_DOCKER_STATE/volume-label-manifest-$volume")"
      fi
      ;;
    create) : > "$FAKE_DOCKER_STATE/volume-$volume"; printf '%s\n' "$volume" ;;
  esac
  exit $?
fi

if [[ "$1" == "ps" && "$*" == *"--filter volume="* ]]; then
  exit 0
fi

if [[ "$1" == "run" ]]; then
  if [[ "$*" == *"printf pawcycle-acme-probe"* ]]; then
    count=0
    [[ ! -f "$FAKE_DOCKER_STATE/challenge-create-count" ]] \
      || count="$(<"$FAKE_DOCKER_STATE/challenge-create-count")"
    printf '%s' "$((count + 1))" > "$FAKE_DOCKER_STATE/challenge-create-count"
    : > "$FAKE_DOCKER_STATE/challenge-probe"
  elif [[ "$*" == *"rm -f -- /var/www/certbot/.well-known/acme-challenge/pawcycle-bootstrap-probe"* ]]; then
    rm -f -- "$FAKE_DOCKER_STATE/challenge-probe"
  fi
  if [[ "$*" == *" certonly "* ]]; then
    count=0
    [[ ! -f "$FAKE_DOCKER_STATE/issue-count" ]] || count="$(<"$FAKE_DOCKER_STATE/issue-count")"
    printf '%s' "$((count + 1))" > "$FAKE_DOCKER_STATE/issue-count"
    [[ "${FAKE_CERTBOT_FAIL:-}" != "1" ]] || exit 1
  fi
  if [[ "$*" == *" renew "* ]]; then
    count=0
    [[ ! -f "$FAKE_DOCKER_STATE/renew-count" ]] || count="$(<"$FAKE_DOCKER_STATE/renew-count")"
    printf '%s' "$((count + 1))" > "$FAKE_DOCKER_STATE/renew-count"
    [[ "${FAKE_RENEW_FAIL:-}" != "1" ]] || exit 1
  fi
  if [[ "$*" == *"from cryptography import x509"* ]]; then
    [[ "${FAKE_CERT_INVALID:-}" != "1" ]] || exit 1
  fi
  exit 0
fi

if [[ "$1" == "exec" ]]; then
  active_sha="$(<"$FAKE_DOCKER_STATE/active-sha")"
  active_volume="$(<"$FAKE_DOCKER_STATE/mysql-volume")"
  request="${*: -1}"
  if [[ "$*" == *"nginx -s reload"* ]]; then
    count=0
    [[ ! -f "$FAKE_DOCKER_STATE/reload-count" ]] || count="$(<"$FAKE_DOCKER_STATE/reload-count")"
    printf '%s' "$((count + 1))" > "$FAKE_DOCKER_STATE/reload-count"
    [[ "${FAKE_RELOAD_FAIL:-}" != "1" ]] || exit 1
  fi
  if [[ "${FAKE_SMOKE_FAIL_SHA:-}" == "$active_sha" && "$request" == *"${FAKE_SMOKE_FAIL_PATH:-}" ]]; then
    exit 1
  fi
  if [[ "$request" == *"'TABLE'"* && "$request" == *"information_schema.TABLES"* ]]; then
    printf 'schema|%s\n' "$active_volume"
  elif [[ "$request" == *"FROM flyway_schema_history"* ]]; then
    printf '1|1|validation|SQL|V1__validation.sql|12345|1\n'
  elif [[ "$request" == *"SELECT COUNT(*) FROM"* ]]; then
    if [[ "$active_volume" == pawcycle-production-mysql-candidate-* ]]; then
      printf '%s\n' "${FAKE_CANDIDATE_TABLE_COUNT:-2}"
    else
      printf '1\n'
    fi
  fi
  exit 0
fi

if [[ "$1" == "image" && "$2" == "inspect" ]]; then
  reference="${*: -1}"
  if [[ "$*" == *"RepoDigests"* ]]; then
    if [[ "$reference" == *@sha256:* ]]; then
      repository="${reference%%:*}"
      digest="${reference##*@}"
      if [[ "${FAKE_BASE_DIGEST_DRIFT:-}" == "1" ]]; then
        digest="sha256:$(printf '%064d' 9)"
      fi
      printf '%s@%s\n' "$repository" "$digest"
    else
      sha="${reference##*:}"
      repository="${reference%:*}"
      digit=0
      [[ "${FAKE_APP_DIGEST_DRIFT_SHA:-}" != "$sha" ]] || digit=1
      printf '%s@sha256:%064d\n' "$repository" "$digit"
    fi
  else
    sha="${reference##*:}"
    printf '%s\n' "$sha"
  fi
  exit 0
fi

if [[ "$1" == "inspect" ]]; then
  container="${*: -1}"
  active_sha="$(<"$FAKE_DOCKER_STATE/active-sha")"
  if [[ "$*" == *".Mounts"* ]]; then
    printf '%s\n' "$(<"$FAKE_DOCKER_STATE/mysql-volume")"
  elif [[ "$*" == *".State.Health"* ]]; then
    if { [[ "${FAKE_FAIL_SHA:-}" == "$active_sha" ]] \
      || [[ "${FAKE_FAIL_MYSQL_VOLUME:-}" == "$(<"$FAKE_DOCKER_STATE/mysql-volume")" ]]; } \
      && [[ "$container" == "container-backend" ]]; then
      printf 'unhealthy\n'
    else
      printf 'healthy\n'
    fi
  elif [[ "$*" == *".Config.Image"* ]]; then
    service="${container#container-}"
    repository="$(<"$FAKE_DOCKER_STATE/${service}-image")"
    if [[ "$service" == "mysql" || "$service" == "proxy" ]]; then
      printf '%s\n' "$repository"
    else
      printf '%s:%s\n' "$repository" "$active_sha"
    fi
  else
    printf '%s\n' "$active_sha"
  fi
  exit 0
fi

exit 0
EOF

chmod +x "$BIN_DIR/aws" "$BIN_DIR/curl" "$BIN_DIR/docker" "$BIN_DIR/flock" "$BIN_DIR/git"
export PATH="$BIN_DIR:$PATH"

output="$("$SCRIPT_DIR/materialize-ssm-env.sh" \
  --ssm-prefix /pawcycle/production \
  --output-dir "$RUNTIME_DIR" \
  --region ap-northeast-2)"
[[ "$output" != *"local-"* ]]
[[ "$(stat -c '%a' "$RUNTIME_DIR/current/mysql.env")" == "600" ]]
[[ "$(stat -c '%a' "$RUNTIME_DIR/current/backend.env")" == "600" ]]
[[ "$(stat -c '%a' "$RUNTIME_DIR/current/.complete")" == "600" ]]
original_bundle="$(readlink "$RUNTIME_DIR/current")"

export FAKE_MISSING="MYSQL_PASSWORD"
if "$SCRIPT_DIR/materialize-ssm-env.sh" \
  --ssm-prefix /pawcycle/production \
  --output-dir "$RUNTIME_DIR" \
  --region ap-northeast-2 >/dev/null 2>&1; then
  printf 'missing SSM parameter did not fail closed\n' >&2
  exit 1
fi
unset FAKE_MISSING
[[ "$(readlink "$RUNTIME_DIR/current")" == "$original_bundle" ]]

"$SCRIPT_DIR/materialize-ssm-env.sh" \
  --ssm-prefix /pawcycle/production \
  --output-dir "$RUNTIME_DIR" \
  --region ap-northeast-2 >/dev/null
[[ "$(find "$RUNTIME_DIR" -mindepth 1 -maxdepth 1 -type d -name '.bundle.*' | wc -l)" == "1" ]]
[[ ! -e "$RUNTIME_DIR/$original_bundle" ]]

FAKE_AWS_BLOCK_DIR="$TEST_ROOT/aws-block"
mkdir -p "$FAKE_AWS_BLOCK_DIR"
export FAKE_AWS_BLOCK_DIR
"$SCRIPT_DIR/materialize-ssm-env.sh" \
  --ssm-prefix /pawcycle/production \
  --output-dir "$RUNTIME_DIR" \
  --region ap-northeast-2 >/dev/null &
materialize_pid=$!
for (( attempt = 0; attempt < 100; attempt++ )); do
  [[ ! -e "$FAKE_AWS_BLOCK_DIR/started" ]] || break
  sleep 0.1
done
[[ -e "$FAKE_AWS_BLOCK_DIR/started" ]]
if "$SCRIPT_DIR/materialize-ssm-env.sh" \
  --ssm-prefix /pawcycle/production \
  --output-dir "$RUNTIME_DIR" \
  --region ap-northeast-2 >/dev/null 2>&1; then
  printf 'concurrent runtime materialization did not fail closed\n' >&2
  exit 1
fi
: > "$FAKE_AWS_BLOCK_DIR/release"
wait "$materialize_pid"
unset FAKE_AWS_BLOCK_DIR
[[ "$(find "$RUNTIME_DIR" -mindepth 1 -maxdepth 1 -type d -name '.bundle.*' | wc -l)" == "1" ]]

BACKEND_IMAGE="ghcr.io/example/pawcycle-commerce-backend"
FRONTEND_IMAGE="ghcr.io/example/pawcycle-commerce-frontend"
SHA_A="1111111111111111111111111111111111111111"
SHA_B="2222222222222222222222222222222222222222"
SHA_C="3333333333333333333333333333333333333333"
export FAKE_CONTROL_SHA="$SHA_A"

deploy() {
  local state_dir="${2:-$STATE_DIR}"
  local arguments=(
    --sha "$1"
    --backend-image "$BACKEND_IMAGE"
    --frontend-image "$FRONTEND_IMAGE"
    --runtime-dir "$RUNTIME_DIR"
    --state-dir "$state_dir"
  )
  mkdir -p "$state_dir"
  if [[ ! -e "$state_dir/active-mysql-volume" ]]; then
    printf '%s\n' 'pawcycle-production-mysql-data' >"$state_dir/active-mysql-volume"
    chmod 600 "$state_dir/active-mysql-volume"
  fi
  if [[ ! -e "$state_dir/contract-sha" \
    || "$(<"$state_dir/contract-sha")" != "$FAKE_CONTROL_SHA" ]]; then
    arguments+=(--adopt-contract-sha "$FAKE_CONTROL_SHA")
  fi
  "$SCRIPT_DIR/deploy.sh" "${arguments[@]}" >/dev/null
}

rollback_without_sha() {
  "$SCRIPT_DIR/rollback.sh" \
    --backend-image "$BACKEND_IMAGE" \
    --frontend-image "$FRONTEND_IMAGE" \
    --runtime-dir "$RUNTIME_DIR" \
    --state-dir "$1"
}

for smoke_path in /products /api/products; do
  initial_state="$TEST_ROOT/initial-${smoke_path//\//-}"
  stop_count_before=0
  if [[ -f "$FAKE_DOCKER_STATE/stop-count" ]]; then
    stop_count_before="$(<"$FAKE_DOCKER_STATE/stop-count")"
  fi
  export FAKE_SMOKE_FAIL_SHA="$SHA_C"
  export FAKE_SMOKE_FAIL_PATH="$smoke_path"
  if deploy "$SHA_C" "$initial_state"; then
    printf 'initial release did not fail when smoke failed: %s\n' "$smoke_path" >&2
    exit 1
  fi
  unset FAKE_SMOKE_FAIL_SHA FAKE_SMOKE_FAIL_PATH
  [[ ! -e "$initial_state/current-sha" ]]
  [[ "$(<"$FAKE_DOCKER_STATE/stop-count")" -gt "$stop_count_before" ]]
done

missing_contract_state="$TEST_ROOT/missing-contract-state"
missing_contract_output="$TEST_ROOT/missing-contract-output"
mkdir -p "$missing_contract_state"
printf '%s\n' 'pawcycle-production-mysql-data' >"$missing_contract_state/active-mysql-volume"
chmod 600 "$missing_contract_state/active-mysql-volume"
docker_call_count_before="$(<"$FAKE_DOCKER_STATE/docker-call-count")"
if "$SCRIPT_DIR/deploy.sh" \
  --sha "$SHA_A" \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE" \
  --runtime-dir "$RUNTIME_DIR" \
  --state-dir "$missing_contract_state" >/dev/null 2>"$missing_contract_output"; then
  printf 'missing runtime contract state did not fail closed\n' >&2
  exit 1
fi
grep -Fq 'production runtime contract state is missing' "$missing_contract_output" \
  || { printf 'missing runtime contract state failed for the wrong reason\n' >&2; exit 1; }
[[ "$(<"$FAKE_DOCKER_STATE/docker-call-count")" == "$docker_call_count_before" ]]

control_state="$TEST_ROOT/control-state"
deploy "$SHA_A" "$control_state"
[[ "$(<"$control_state/contract-sha")" == "$SHA_A" ]]
export FAKE_CONTROL_SHA="$SHA_B"

docker_call_count_before="$(<"$FAKE_DOCKER_STATE/docker-call-count")"
control_drift_output="$TEST_ROOT/control-drift-output"
if "$SCRIPT_DIR/deploy.sh" \
  --sha "$SHA_A" \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE" \
  --runtime-dir "$RUNTIME_DIR" \
  --state-dir "$control_state" >/dev/null 2>"$control_drift_output"; then
  printf 'control SHA drift without explicit adoption did not fail closed\n' >&2
  exit 1
fi
grep -Fq 'production control SHA differs from contract state' "$control_drift_output"
[[ "$(<"$FAKE_DOCKER_STATE/docker-call-count")" == "$docker_call_count_before" ]]

export FAKE_CONTRACT_MISMATCH=1
if "$SCRIPT_DIR/deploy.sh" \
  --sha "$SHA_A" \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE" \
  --adopt-contract-sha "$SHA_B" \
  --runtime-dir "$RUNTIME_DIR" \
  --state-dir "$control_state" >/dev/null 2>&1; then
  printf 'incompatible control contract transition did not fail closed\n' >&2
  exit 1
fi
unset FAKE_CONTRACT_MISMATCH
[[ "$(<"$control_state/contract-sha")" == "$SHA_A" ]]

deploy "$SHA_A" "$control_state"
[[ "$(<"$control_state/contract-sha")" == "$SHA_B" ]]
[[ "$(stat -c '%a' "$control_state/contract-sha")" == "600" ]]

export FAKE_CONTROL_DIRTY=1
docker_call_count_before="$(<"$FAKE_DOCKER_STATE/docker-call-count")"
dirty_control_output="$TEST_ROOT/dirty-control-output"
if "$SCRIPT_DIR/deploy.sh" \
  --sha "$SHA_A" \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE" \
  --runtime-dir "$RUNTIME_DIR" \
  --state-dir "$control_state" >/dev/null 2>"$dirty_control_output"; then
  printf 'dirty production control worktree did not fail closed\n' >&2
  exit 1
fi
grep -Fq 'production control contract worktree is not clean' "$dirty_control_output"
[[ "$(<"$FAKE_DOCKER_STATE/docker-call-count")" == "$docker_call_count_before" ]]
unset FAKE_CONTROL_DIRTY

export FAKE_CONTROL_SHA="$SHA_C"
docker_call_count_before="$(<"$FAKE_DOCKER_STATE/docker-call-count")"
rollback_control_output="$TEST_ROOT/rollback-control-output"
if "$SCRIPT_DIR/rollback.sh" \
  --sha "$SHA_C" \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE" \
  --runtime-dir "$RUNTIME_DIR" \
  --state-dir "$control_state" >/dev/null 2>"$rollback_control_output"; then
  printf 'rollback with control SHA drift did not fail closed\n' >&2
  exit 1
fi
grep -Fq 'production control SHA differs from contract state' "$rollback_control_output"
[[ "$(<"$FAKE_DOCKER_STATE/docker-call-count")" == "$docker_call_count_before" ]]
export FAKE_CONTROL_SHA="$SHA_A"

deploy "$SHA_A"
[[ "$(<"$STATE_DIR/current-sha")" == "$SHA_A" ]]
[[ "$(<"$STATE_DIR/contract-sha")" == "$SHA_A" ]]
[[ "$(stat -c '%a' "$STATE_DIR/contract-sha")" == "600" ]]
[[ "$(<"$STATE_DIR/active-mysql-volume")" == "pawcycle-production-mysql-data" ]]
[[ "$(<"$FAKE_DOCKER_STATE/mysql-volume")" == "pawcycle-production-mysql-data" ]]

missing_volume_state="$TEST_ROOT/missing-volume-state"
mkdir -p "$missing_volume_state"
docker_call_count_before="$(<"$FAKE_DOCKER_STATE/docker-call-count")"
if "$SCRIPT_DIR/deploy.sh" \
  --sha "$SHA_A" \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE" \
  --runtime-dir "$RUNTIME_DIR" \
  --state-dir "$missing_volume_state" >/dev/null 2>"$TEST_ROOT/missing-volume-output"; then
  printf 'missing active MySQL volume state did not fail closed\n' >&2
  exit 1
fi
grep -Fq 'active MySQL volume state is missing' "$TEST_ROOT/missing-volume-output"
[[ "$(<"$FAKE_DOCKER_STATE/docker-call-count")" == "$docker_call_count_before" ]]

rm -f -- "$STATE_DIR/contract-sha"
deploy "$SHA_A"
[[ "$(<"$STATE_DIR/contract-sha")" == "$SHA_A" ]]
[[ "$(<"$FAKE_DOCKER_STATE/active-sha")" == "$SHA_A" ]]

export FAKE_PS_FAIL=1
deploy "$SHA_A"
unset FAKE_PS_FAIL

for smoke_path in /products /api/products; do
  export FAKE_SMOKE_FAIL_SHA="$SHA_B"
  export FAKE_SMOKE_FAIL_PATH="$smoke_path"
  if deploy "$SHA_B"; then
    printf 'target release did not fail when smoke failed: %s\n' "$smoke_path" >&2
    exit 1
  fi
  unset FAKE_SMOKE_FAIL_SHA FAKE_SMOKE_FAIL_PATH
  [[ "$(<"$STATE_DIR/current-sha")" == "$SHA_A" ]]
  [[ "$(<"$FAKE_DOCKER_STATE/active-sha")" == "$SHA_A" ]]
done

deploy "$SHA_B"
[[ "$(<"$STATE_DIR/current-sha")" == "$SHA_B" ]]
[[ "$(<"$STATE_DIR/previous-sha")" == "$SHA_A" ]]
[[ "$(<"$STATE_DIR/previous-contract-sha")" == "$SHA_A" ]]

rollback_symlink_state="$TEST_ROOT/rollback-previous-symlink-state"
mkdir -p "$rollback_symlink_state"
cp -a -- "$STATE_DIR/." "$rollback_symlink_state/"
rm -f -- "$rollback_symlink_state/previous-sha"
printf '%s\n' "$SHA_A" >"$TEST_ROOT/rollback-previous-symlink-target"
ln -s "$TEST_ROOT/rollback-previous-symlink-target" "$rollback_symlink_state/previous-sha"
docker_call_count_before="$(<"$FAKE_DOCKER_STATE/docker-call-count")"
if rollback_without_sha "$rollback_symlink_state" >/dev/null 2>"$TEST_ROOT/rollback-previous-symlink-output"; then
  printf 'rollback previous-sha symlink did not fail closed\n' >&2
  exit 1
fi
grep -Fq 'previous-sha state must be a regular non-symlink file' \
  "$TEST_ROOT/rollback-previous-symlink-output"
[[ "$(<"$FAKE_DOCKER_STATE/docker-call-count")" == "$docker_call_count_before" ]]

rollback_mode_state="$TEST_ROOT/rollback-previous-mode-state"
mkdir -p "$rollback_mode_state"
cp -a -- "$STATE_DIR/." "$rollback_mode_state/"
chmod 644 "$rollback_mode_state/previous-sha"
docker_call_count_before="$(<"$FAKE_DOCKER_STATE/docker-call-count")"
if rollback_without_sha "$rollback_mode_state" >/dev/null 2>"$TEST_ROOT/rollback-previous-mode-output"; then
  printf 'rollback previous-sha mode violation did not fail closed\n' >&2
  exit 1
fi
grep -Fq 'previous-sha state mode must be 600' "$TEST_ROOT/rollback-previous-mode-output"
[[ "$(<"$FAKE_DOCKER_STATE/docker-call-count")" == "$docker_call_count_before" ]]

rollback_stale_state="$TEST_ROOT/rollback-previous-stale-state"
mkdir -p "$rollback_stale_state"
cp -a -- "$STATE_DIR/." "$rollback_stale_state/"
export FAKE_FLOCK_PREVIOUS_STATE="$rollback_stale_state/previous-sha"
export FAKE_FLOCK_PREVIOUS_SHA="$SHA_C"
rollback_without_sha "$rollback_stale_state" >/dev/null
unset FAKE_FLOCK_PREVIOUS_STATE FAKE_FLOCK_PREVIOUS_SHA
[[ "$(<"$rollback_stale_state/current-sha")" == "$SHA_C" ]]
[[ "$(<"$rollback_stale_state/previous-sha")" == "$SHA_B" ]]
[[ "$(<"$FAKE_DOCKER_STATE/active-sha")" == "$SHA_C" ]]
printf '%s' "$SHA_B" >"$FAKE_DOCKER_STATE/active-sha"

printf '%s\n' 'pawcycle-production-mysql-candidate-0123456789abcdef' >"$STATE_DIR/active-mysql-volume"
chmod 600 "$STATE_DIR/active-mysql-volume"
deploy "$SHA_A"
[[ "$(<"$FAKE_DOCKER_STATE/mysql-volume")" == "pawcycle-production-mysql-candidate-0123456789abcdef" ]]

"$SCRIPT_DIR/rollback.sh" \
  --sha "$SHA_B" \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE" \
  --runtime-dir "$RUNTIME_DIR" \
  --state-dir "$STATE_DIR" >/dev/null
[[ "$(<"$STATE_DIR/current-sha")" == "$SHA_B" ]]
[[ "$(<"$STATE_DIR/previous-sha")" == "$SHA_A" ]]
[[ "$(<"$STATE_DIR/previous-contract-sha")" == "$SHA_A" ]]
[[ "$(<"$FAKE_DOCKER_STATE/mysql-volume")" == "pawcycle-production-mysql-candidate-0123456789abcdef" ]]
deploy "$SHA_A"
[[ "$(<"$STATE_DIR/current-sha")" == "$SHA_A" ]]
[[ "$(<"$FAKE_DOCKER_STATE/mysql-volume")" == "pawcycle-production-mysql-candidate-0123456789abcdef" ]]

record_before="$(<"$STATE_DIR/${SHA_A}.images")"
up_count_before="$(<"$FAKE_DOCKER_STATE/up-count")"
export FAKE_APP_DIGEST_DRIFT_SHA="$SHA_A"
if deploy "$SHA_A"; then
  printf 'same-SHA application digest drift did not fail closed\n' >&2
  exit 1
fi
unset FAKE_APP_DIGEST_DRIFT_SHA
[[ "$(<"$STATE_DIR/${SHA_A}.images")" == "$record_before" ]]
[[ "$(<"$FAKE_DOCKER_STATE/up-count")" == "$up_count_before" ]]

export FAKE_BASE_DIGEST_DRIFT=1
if deploy "$SHA_A"; then
  printf 'pinned base image digest drift did not fail closed\n' >&2
  exit 1
fi
unset FAKE_BASE_DIGEST_DRIFT
[[ "$(<"$STATE_DIR/${SHA_A}.images")" == "$record_before" ]]
[[ "$(<"$FAKE_DOCKER_STATE/up-count")" == "$up_count_before" ]]

sha_b_record_before="$(<"$STATE_DIR/${SHA_B}.images")"
docker_call_count_before="$(<"$FAKE_DOCKER_STATE/docker-call-count")"
export FAKE_CONTRACT_MISMATCH=1
if deploy "$SHA_B"; then
  printf 'incompatible production runtime contract did not fail closed\n' >&2
  exit 1
fi
unset FAKE_CONTRACT_MISMATCH
[[ "$(<"$STATE_DIR/current-sha")" == "$SHA_A" ]]
[[ "$(<"$FAKE_DOCKER_STATE/active-sha")" == "$SHA_A" ]]
[[ "$(<"$STATE_DIR/${SHA_B}.images")" == "$sha_b_record_before" ]]
[[ "$(<"$FAKE_DOCKER_STATE/up-count")" == "$up_count_before" ]]
[[ "$(<"$FAKE_DOCKER_STATE/docker-call-count")" == "$docker_call_count_before" ]]

export FAKE_CONTRACT_MISMATCH=1
if "$SCRIPT_DIR/rollback.sh" \
  --sha "$SHA_C" \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE" \
  --runtime-dir "$RUNTIME_DIR" \
  --state-dir "$STATE_DIR" >/dev/null; then
  printf 'rollback with incompatible production runtime contract did not fail closed\n' >&2
  exit 1
fi
unset FAKE_CONTRACT_MISMATCH
[[ "$(<"$STATE_DIR/current-sha")" == "$SHA_A" ]]
[[ "$(<"$STATE_DIR/previous-sha")" == "$SHA_B" ]]
[[ "$(<"$FAKE_DOCKER_STATE/docker-call-count")" == "$docker_call_count_before" ]]

export FAKE_FAIL_SHA="$SHA_C"
if deploy "$SHA_C"; then
  printf 'unhealthy target did not fail\n' >&2
  exit 1
fi
unset FAKE_FAIL_SHA
[[ "$(<"$STATE_DIR/current-sha")" == "$SHA_A" ]]
[[ "$(<"$FAKE_DOCKER_STATE/active-sha")" == "$SHA_A" ]]

FAKE_DOMAIN="ops011-test.duckdns.org"
BAD_DOMAIN="ops011-wrong.duckdns.org"
FAKE_EMAIL="operator${FAKE_AT_SIGN:-@}example.invalid"
export FAKE_DOMAIN

https_command() {
  local domain="${HTTPS_COMMAND_DOMAIN:-$FAKE_DOMAIN}"

  "$SCRIPT_DIR/https.sh" "$@" \
    --domain "$domain" \
    --backend-image "$BACKEND_IMAGE" \
    --frontend-image "$FRONTEND_IMAGE" \
    --runtime-dir "$RUNTIME_DIR" \
    --state-dir "$STATE_DIR" >/dev/null
}

assert_unapproved_https_state() {
  [[ ! -e "$STATE_DIR/https-domain" ]] || {
    printf 'failed pre-approval flow persisted HTTPS domain\n' >&2
    exit 1
  }
  [[ ! -e "$STATE_DIR/https-domain.candidate" ]] || {
    printf 'failed pre-approval flow left HTTPS domain candidate\n' >&2
    exit 1
  }
  [[ ! -e "$STATE_DIR/nginx.https.conf" ]]
  [[ ! -e "$STATE_DIR/nginx.https.conf.candidate" ]]
  [[ ! -e "$STATE_DIR/https-enabled" ]]
  [[ ! -e "$FAKE_DOCKER_STATE/challenge-probe" ]] || {
    printf 'challenge probe remained after failed validation\n' >&2
    exit 1
  }
  [[ "$(<"$STATE_DIR/current-sha")" == "$SHA_A" ]]
  [[ "$(<"$FAKE_DOCKER_STATE/active-sha")" == "$SHA_A" ]]
  [[ -f "$FAKE_DOCKER_STATE/volume-pawcycle-production-letsencrypt" ]]
}

HTTPS_COMMAND_DOMAIN="$BAD_DOMAIN" https_command bootstrap
assert_unapproved_https_state

export FAKE_CERTBOT_FAIL=1
if HTTPS_COMMAND_DOMAIN="$BAD_DOMAIN" https_command issue --email "$FAKE_EMAIL"; then
  printf 'certificate issuance failure approved HTTPS domain\n' >&2
  exit 1
fi
unset FAKE_CERTBOT_FAIL
assert_unapproved_https_state

export FAKE_CERT_INVALID=1
if https_command issue --email "$FAKE_EMAIL"; then
  printf 'certificate validation failure approved HTTPS domain\n' >&2
  exit 1
fi
unset FAKE_CERT_INVALID
assert_unapproved_https_state

mkdir "$STATE_DIR/https-domain.candidate"
if https_command issue --email "$FAKE_EMAIL" 2>/dev/null; then
  printf 'domain candidate cleanup failure approved HTTPS domain\n' >&2
  exit 1
fi
[[ ! -e "$STATE_DIR/https-domain" ]]
[[ ! -e "$STATE_DIR/nginx.https.conf" ]]
[[ ! -e "$STATE_DIR/nginx.https.conf.candidate" ]]
[[ ! -e "$STATE_DIR/https-enabled" ]]
[[ ! -e "$FAKE_DOCKER_STATE/challenge-probe" ]]
[[ "$(<"$STATE_DIR/current-sha")" == "$SHA_A" ]]
[[ "$(<"$FAKE_DOCKER_STATE/active-sha")" == "$SHA_A" ]]
[[ -f "$FAKE_DOCKER_STATE/volume-pawcycle-production-letsencrypt" ]]
rmdir "$STATE_DIR/https-domain.candidate"

https_command bootstrap
assert_unapproved_https_state

challenge_count_before="$(<"$FAKE_DOCKER_STATE/challenge-create-count")"
export FAKE_HTTPS_FAIL_SHA="$SHA_A"
export FAKE_HTTPS_FAIL_PATH="/products"
export FAKE_CHALLENGE_FAIL_AT_COUNT="$((challenge_count_before + 2))"
if recovery_error="$(https_command issue --email "$FAKE_EMAIL" 2>&1)"; then
  printf 'HTTPS activation failure did not fail after challenge recovery error\n' >&2
  exit 1
fi
unset FAKE_HTTPS_FAIL_SHA FAKE_HTTPS_FAIL_PATH FAKE_CHALLENGE_FAIL_AT_COUNT
[[ "$recovery_error" == *"bootstrap HTTP service was restored, but HTTP-01 challenge path validation failed"* ]]
[[ "$(<"$STATE_DIR/https-domain")" == "$FAKE_DOMAIN" ]]
[[ "$(stat -c '%a' "$STATE_DIR/https-domain")" == "600" ]]
[[ ! -e "$STATE_DIR/https-enabled" ]]
[[ ! -e "$STATE_DIR/nginx.https.conf" ]]
[[ ! -e "$STATE_DIR/nginx.https.conf.candidate" ]]
[[ ! -e "$FAKE_DOCKER_STATE/challenge-probe" ]]
[[ "$(<"$STATE_DIR/current-sha")" == "$SHA_A" ]]
[[ -f "$FAKE_DOCKER_STATE/volume-pawcycle-production-letsencrypt" ]]

if HTTPS_COMMAND_DOMAIN="$BAD_DOMAIN" https_command bootstrap; then
  printf 'different HTTPS domain was accepted after certificate approval\n' >&2
  exit 1
fi
[[ "$(<"$STATE_DIR/https-domain")" == "$FAKE_DOMAIN" ]]

up_count_before="$(<"$FAKE_DOCKER_STATE/up-count")"
export FAKE_HTTPS_FAIL_SHA="$SHA_A"
export FAKE_HTTPS_FAIL_PATH="/products"
export FAKE_UP_FAIL_AT_COUNT="$((up_count_before + 3))"
if recovery_error="$(https_command issue --email "$FAKE_EMAIL" 2>&1)"; then
  printf 'HTTPS activation failure did not fail after bootstrap recovery failure\n' >&2
  exit 1
fi
unset FAKE_HTTPS_FAIL_SHA FAKE_HTTPS_FAIL_PATH FAKE_UP_FAIL_AT_COUNT
[[ "$recovery_error" == *"HTTPS activation and bootstrap HTTP restoration both failed"* ]]
[[ ! -e "$STATE_DIR/https-enabled" ]]
[[ ! -e "$STATE_DIR/nginx.https.conf" ]]
[[ ! -e "$STATE_DIR/nginx.https.conf.candidate" ]]
[[ ! -e "$FAKE_DOCKER_STATE/challenge-probe" ]]
[[ "$(<"$STATE_DIR/current-sha")" == "$SHA_A" ]]
[[ -f "$FAKE_DOCKER_STATE/volume-pawcycle-production-letsencrypt" ]]

https_command issue --email "$FAKE_EMAIL"
[[ "$(<"$STATE_DIR/https-domain")" == "$FAKE_DOMAIN" ]]
[[ "$(stat -c '%a' "$STATE_DIR/https-domain")" == "600" ]]
[[ "$(<"$STATE_DIR/https-enabled")" == "enabled" ]]
[[ "$(stat -c '%a' "$STATE_DIR/https-enabled")" == "600" ]]
[[ "$(stat -c '%a' "$STATE_DIR/nginx.https.conf")" == "600" ]]
grep -Fq "server_name $FAKE_DOMAIN;" "$STATE_DIR/nginx.https.conf"
grep -Fq "return 301 https://$FAKE_DOMAIN\$request_uri;" "$STATE_DIR/nginx.https.conf"
[[ "$(<"$STATE_DIR/current-sha")" == "$SHA_A" ]]

if HTTPS_COMMAND_DOMAIN="$BAD_DOMAIN" https_command status; then
  printf 'different HTTPS domain was accepted after HTTPS activation\n' >&2
  exit 1
fi
[[ "$(<"$STATE_DIR/https-domain")" == "$FAKE_DOMAIN" ]]
[[ "$(<"$STATE_DIR/https-enabled")" == "enabled" ]]

mv "$STATE_DIR/https-domain" "$STATE_DIR/https-domain.saved"
ln -s "$STATE_DIR/https-domain.saved" "$STATE_DIR/https-domain"
if https_command status; then
  printf 'HTTPS domain symlink did not fail closed\n' >&2
  exit 1
fi
rm -f -- "$STATE_DIR/https-domain"
mv "$STATE_DIR/https-domain.saved" "$STATE_DIR/https-domain"

chmod 644 "$STATE_DIR/https-domain"
if https_command status; then
  printf 'HTTPS domain mode violation did not fail closed\n' >&2
  exit 1
fi
chmod 600 "$STATE_DIR/https-domain"

printf 'invalid.example.invalid\n' > "$STATE_DIR/https-domain"
if https_command status; then
  printf 'invalid HTTPS domain state did not fail closed\n' >&2
  exit 1
fi
printf '%s\n' "$FAKE_DOMAIN" > "$STATE_DIR/https-domain"
chmod 600 "$STATE_DIR/https-domain"

issue_count_before="$(<"$FAKE_DOCKER_STATE/issue-count")"
https_command issue --email "$FAKE_EMAIL"
[[ "$(<"$FAKE_DOCKER_STATE/issue-count")" == "$issue_count_before" ]]

for https_path in /products /api/products; do
  export FAKE_HTTPS_FAIL_SHA="$SHA_B"
  export FAKE_HTTPS_FAIL_PATH="$https_path"
  if deploy "$SHA_B"; then
    printf 'HTTPS release gate failure changed current SHA: %s\n' "$https_path" >&2
    exit 1
  fi
  unset FAKE_HTTPS_FAIL_SHA FAKE_HTTPS_FAIL_PATH
  [[ "$(<"$STATE_DIR/current-sha")" == "$SHA_A" ]]
  [[ "$(<"$FAKE_DOCKER_STATE/active-sha")" == "$SHA_A" ]]
  [[ -f "$FAKE_DOCKER_STATE/volume-pawcycle-production-letsencrypt" ]]
done

export FAKE_REDIRECT_FAIL_SHA="$SHA_B"
if deploy "$SHA_B"; then
  printf 'HTTPS redirect gate failure changed current SHA\n' >&2
  exit 1
fi
unset FAKE_REDIRECT_FAIL_SHA
[[ "$(<"$STATE_DIR/current-sha")" == "$SHA_A" ]]
[[ "$(<"$FAKE_DOCKER_STATE/active-sha")" == "$SHA_A" ]]

export FAKE_HTTPS_FAIL_SHA="$SHA_B"
export FAKE_HTTPS_FAIL_PATH="/products"
if "$SCRIPT_DIR/rollback.sh" \
  --sha "$SHA_B" \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE" \
  --runtime-dir "$RUNTIME_DIR" \
  --state-dir "$STATE_DIR" >/dev/null; then
  printf 'HTTPS rollback gate failure changed current SHA\n' >&2
  exit 1
fi
unset FAKE_HTTPS_FAIL_SHA FAKE_HTTPS_FAIL_PATH
[[ "$(<"$STATE_DIR/current-sha")" == "$SHA_A" ]]
[[ "$(<"$FAKE_DOCKER_STATE/active-sha")" == "$SHA_A" ]]

deploy "$SHA_B"
[[ "$(<"$STATE_DIR/current-sha")" == "$SHA_B" ]]
"$SCRIPT_DIR/rollback.sh" \
  --sha "$SHA_A" \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE" \
  --runtime-dir "$RUNTIME_DIR" \
  --state-dir "$STATE_DIR" >/dev/null
[[ "$(<"$STATE_DIR/current-sha")" == "$SHA_A" ]]
[[ "$(<"$FAKE_DOCKER_STATE/active-sha")" == "$SHA_A" ]]

reload_before=0
[[ ! -f "$FAKE_DOCKER_STATE/reload-count" ]] || reload_before="$(<"$FAKE_DOCKER_STATE/reload-count")"
https_command renew --dry-run
reload_after=0
[[ ! -f "$FAKE_DOCKER_STATE/reload-count" ]] || reload_after="$(<"$FAKE_DOCKER_STATE/reload-count")"
[[ "$reload_after" == "$reload_before" ]]

export FAKE_RENEW_FAIL=1
if https_command renew; then
  printf 'certificate renewal failure reloaded Nginx\n' >&2
  exit 1
fi
unset FAKE_RENEW_FAIL
reload_after_failure=0
[[ ! -f "$FAKE_DOCKER_STATE/reload-count" ]] || reload_after_failure="$(<"$FAKE_DOCKER_STATE/reload-count")"
[[ "$reload_after_failure" == "$reload_before" ]]
[[ "$(<"$STATE_DIR/current-sha")" == "$SHA_A" ]]

https_command renew
[[ "$(<"$FAKE_DOCKER_STATE/reload-count")" -eq $((reload_before + 1)) ]]

export FAKE_RELOAD_FAIL=1
if https_command renew; then
  printf 'Nginx reload failure was reported as success\n' >&2
  exit 1
fi
unset FAKE_RELOAD_FAIL
[[ -f "$STATE_DIR/https-enabled" ]]
[[ "$(<"$STATE_DIR/current-sha")" == "$SHA_A" ]]

https_command disable
[[ ! -e "$STATE_DIR/https-enabled" ]]
[[ -f "$FAKE_DOCKER_STATE/volume-pawcycle-production-letsencrypt" ]]
[[ "$(<"$STATE_DIR/current-sha")" == "$SHA_A" ]]

CANDIDATE_VOLUME="pawcycle-production-mysql-candidate-fedcba9876543210"
BACKUP_HASH="$(printf '%064d' 7)"
MANIFEST_HASH="$(printf '%064d' 8)"
SCHEMA_HASH="$(printf 'schema|%s\n' "$CANDIDATE_VOLUME" | sha256sum | awk '{print $1}')"
FLYWAY_HASH="$(printf '1|1|validation|SQL|V1__validation.sql|12345|1\n' | sha256sum | awk '{print $1}')"
printf '%s\n' 'pawcycle-production-mysql-data' >"$STATE_DIR/active-mysql-volume"
chmod 600 "$STATE_DIR/active-mysql-volume"
printf '%s' 'pawcycle-production-mysql-data' >"$FAKE_DOCKER_STATE/mysql-volume"
for volume in pawcycle-production-mysql-data "$CANDIDATE_VOLUME"; do
  : >"$FAKE_DOCKER_STATE/volume-$volume"
done
printf '%s' 'candidate' >"$FAKE_DOCKER_STATE/volume-label-scope-$CANDIDATE_VOLUME"
printf '%s' 'pawcycle-production-mysql-data' >"$FAKE_DOCKER_STATE/volume-label-source-$CANDIDATE_VOLUME"
printf '%s' "$BACKUP_HASH" >"$FAKE_DOCKER_STATE/volume-label-backup-$CANDIDATE_VOLUME"
printf '%s' "$MANIFEST_HASH" >"$FAKE_DOCKER_STATE/volume-label-manifest-$CANDIDATE_VOLUME"
cat >"$STATE_DIR/db-restore-candidate" <<EOF
FORMAT_VERSION=1
RECORD_KIND=candidate
BACKUP_ID_SHA256=$BACKUP_HASH
MANIFEST_SHA256=$MANIFEST_HASH
MYSQL_IMAGE=mysql:8.4.10@sha256:c592c15aaf4a1961e15d82eb31ea5987dda862d1c4b1e93424438c0e91dc1f8d
SOURCE_VOLUME=pawcycle-production-mysql-data
CANDIDATE_VOLUME=$CANDIDATE_VOLUME
SCHEMA_SHA256=$SCHEMA_HASH
FLYWAY_SHA256=$FLYWAY_HASH
FLYWAY_COUNT=1
TABLE_members=2
TABLE_products=2
TABLE_skus=2
TABLE_subscriptions=2
EOF
chmod 600 "$STATE_DIR/db-restore-candidate"

"$SCRIPT_DIR/production-db-restore.sh" cutover \
  --candidate-volume "$CANDIDATE_VOLUME" \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE" \
  --runtime-dir "$RUNTIME_DIR" \
  --state-dir "$STATE_DIR" >/dev/null
[[ "$(<"$STATE_DIR/active-mysql-volume")" == "$CANDIDATE_VOLUME" ]]
[[ "$(<"$FAKE_DOCKER_STATE/mysql-volume")" == "$CANDIDATE_VOLUME" ]]
[[ "$(<"$STATE_DIR/previous-mysql-volume")" == "pawcycle-production-mysql-data" ]]
[[ "$(stat -c '%a' "$STATE_DIR/db-restore-source")" == "600" ]]

export FAKE_CANDIDATE_TABLE_COUNT=3
export FAKE_FAIL_MYSQL_VOLUME="pawcycle-production-mysql-data"
if revert_error="$("$SCRIPT_DIR/production-db-restore.sh" revert \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE" \
  --runtime-dir "$RUNTIME_DIR" \
  --state-dir "$STATE_DIR" 2>&1 >/dev/null)"; then
  printf 'source revert activation failure was reported as success\n' >&2
  exit 1
fi
unset FAKE_FAIL_MYSQL_VOLUME
[[ "$revert_error" == *"source database revert failed; candidate volume and application state were restored"* ]]
[[ "$(<"$STATE_DIR/active-mysql-volume")" == "$CANDIDATE_VOLUME" ]]
[[ "$(<"$FAKE_DOCKER_STATE/mysql-volume")" == "$CANDIDATE_VOLUME" ]]
[[ "$(<"$STATE_DIR/previous-mysql-volume")" == "pawcycle-production-mysql-data" ]]
[[ -f "$STATE_DIR/db-restore-source" ]]
[[ -f "$STATE_DIR/db-restore-candidate" ]]
grep -Fq 'TABLE_members=2' "$STATE_DIR/db-restore-candidate"
[[ "$(stat -c '%a' "$STATE_DIR/db-restore-revert-candidate")" == "600" ]]
grep -Fq 'RECORD_KIND=candidate-current' "$STATE_DIR/db-restore-revert-candidate"
grep -Fq "CANDIDATE_VOLUME=$CANDIDATE_VOLUME" "$STATE_DIR/db-restore-revert-candidate"
grep -Fq "APPLICATION_SHA=$SHA_A" "$STATE_DIR/db-restore-revert-candidate"
for table in members products skus subscriptions; do
  grep -Fq "TABLE_${table}=3" "$STATE_DIR/db-restore-revert-candidate"
done
[[ -f "$FAKE_DOCKER_STATE/volume-pawcycle-production-mysql-data" ]]
[[ -f "$FAKE_DOCKER_STATE/volume-$CANDIDATE_VOLUME" ]]

"$SCRIPT_DIR/production-db-restore.sh" revert \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE" \
  --runtime-dir "$RUNTIME_DIR" \
  --state-dir "$STATE_DIR" >/dev/null
unset FAKE_CANDIDATE_TABLE_COUNT
[[ "$(<"$STATE_DIR/active-mysql-volume")" == "pawcycle-production-mysql-data" ]]
[[ "$(<"$FAKE_DOCKER_STATE/mysql-volume")" == "pawcycle-production-mysql-data" ]]
[[ -f "$FAKE_DOCKER_STATE/volume-pawcycle-production-mysql-data" ]]
[[ -f "$FAKE_DOCKER_STATE/volume-$CANDIDATE_VOLUME" ]]

rm -f -- "$STATE_DIR/db-restore-source" "$STATE_DIR/previous-mysql-volume" "$STATE_DIR/db-restore-application-sha"
stop_count_before="$(<"$FAKE_DOCKER_STATE/stop-count")"
export FAKE_STOP_FAIL_AT_COUNT="$((stop_count_before + 1))"
if stop_error="$("$SCRIPT_DIR/production-db-restore.sh" cutover \
  --candidate-volume "$CANDIDATE_VOLUME" \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE" \
  --runtime-dir "$RUNTIME_DIR" \
  --state-dir "$STATE_DIR" 2>&1 >/dev/null)"; then
  printf 'application write-path stop failure was reported as success\n' >&2
  exit 1
fi
unset FAKE_STOP_FAIL_AT_COUNT
[[ "$stop_error" == *"application write-path stop failed; source release reactivation was attempted without cutover"* ]]
[[ "$(<"$STATE_DIR/active-mysql-volume")" == "pawcycle-production-mysql-data" ]]
[[ "$(<"$FAKE_DOCKER_STATE/mysql-volume")" == "pawcycle-production-mysql-data" ]]
[[ ! -e "$STATE_DIR/db-restore-source" ]]

export FAKE_FAIL_MYSQL_VOLUME="$CANDIDATE_VOLUME"
if cutover_error="$("$SCRIPT_DIR/production-db-restore.sh" cutover \
  --candidate-volume "$CANDIDATE_VOLUME" \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE" \
  --runtime-dir "$RUNTIME_DIR" \
  --state-dir "$STATE_DIR" 2>&1 >/dev/null)"; then
  printf 'candidate cutover failure was reported as success\n' >&2
  exit 1
fi
unset FAKE_FAIL_MYSQL_VOLUME
[[ "$cutover_error" == *"candidate cutover failed; source volume and application state were restored"* ]]
[[ "$(<"$STATE_DIR/active-mysql-volume")" == "pawcycle-production-mysql-data" ]]
[[ "$(<"$FAKE_DOCKER_STATE/mysql-volume")" == "pawcycle-production-mysql-data" ]]
[[ -f "$FAKE_DOCKER_STATE/volume-pawcycle-production-mysql-data" ]]
[[ -f "$FAKE_DOCKER_STATE/volume-$CANDIDATE_VOLUME" ]]

rm -f -- "$STATE_DIR/db-restore-source" "$STATE_DIR/previous-mysql-volume" "$STATE_DIR/db-restore-application-sha"
printf '%s' "$(printf '%064d' 9)" >"$FAKE_DOCKER_STATE/volume-label-manifest-$CANDIDATE_VOLUME"
stop_count_before="$(<"$FAKE_DOCKER_STATE/stop-count")"
if "$SCRIPT_DIR/production-db-restore.sh" cutover \
  --candidate-volume "$CANDIDATE_VOLUME" \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE" \
  --runtime-dir "$RUNTIME_DIR" \
  --state-dir "$STATE_DIR" >/dev/null 2>&1; then
  printf 'candidate manifest label mismatch was reported as success\n' >&2
  exit 1
fi
printf '%s' "$MANIFEST_HASH" >"$FAKE_DOCKER_STATE/volume-label-manifest-$CANDIDATE_VOLUME"
[[ "$(<"$FAKE_DOCKER_STATE/stop-count")" == "$stop_count_before" ]]
[[ "$(<"$STATE_DIR/active-mysql-volume")" == "pawcycle-production-mysql-data" ]]

printf 'OPS-025 production DB restore fake success, failure, cutover, and revert lifecycle tests passed\n'
printf 'OPS-011 production script tests passed\n'
