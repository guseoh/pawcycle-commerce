#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf -- "$TEST_ROOT"' EXIT

BIN_DIR="$TEST_ROOT/bin"
RUNTIME_DIR="$TEST_ROOT/runtime"
STATE_DIR="$TEST_ROOT/state"
FAKE_DOCKER_STATE="$TEST_ROOT/docker-state"
mkdir -p "$BIN_DIR" "$FAKE_DOCKER_STATE"
export FAKE_DOCKER_STATE

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
EOF

cat > "$BIN_DIR/git" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
case "${1:-}" in
  cat-file) exit 0 ;;
  diff)
    [[ "${FAKE_CONTRACT_MISMATCH:-}" != "1" ]] || exit 1
    exit 0
    ;;
esac
exit 0
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
      printf '%s' "$RELEASE_SHA" > "$FAKE_DOCKER_STATE/active-sha"
      printf '%s' "$BACKEND_IMAGE" > "$FAKE_DOCKER_STATE/backend-image"
      printf '%s' "$FRONTEND_IMAGE" > "$FAKE_DOCKER_STATE/frontend-image"
      printf '%s' 'mysql:8.4.10@sha256:c592c15aaf4a1961e15d82eb31ea5987dda862d1c4b1e93424438c0e91dc1f8d' > "$FAKE_DOCKER_STATE/mysql-image"
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
      ;;
  esac
  exit 0
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
  if [[ "$*" == *".State.Health"* ]]; then
    if [[ "${FAKE_FAIL_SHA:-}" == "$active_sha" && "$container" == "container-backend" ]]; then
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

chmod +x "$BIN_DIR/aws" "$BIN_DIR/curl" "$BIN_DIR/docker" "$BIN_DIR/git"
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

BACKEND_IMAGE="ghcr.io/example/pawcycle-commerce-backend"
FRONTEND_IMAGE="ghcr.io/example/pawcycle-commerce-frontend"
SHA_A="1111111111111111111111111111111111111111"
SHA_B="2222222222222222222222222222222222222222"
SHA_C="3333333333333333333333333333333333333333"

deploy() {
  local state_dir="${2:-$STATE_DIR}"
  "$SCRIPT_DIR/deploy.sh" \
    --sha "$1" \
    --backend-image "$BACKEND_IMAGE" \
    --frontend-image "$FRONTEND_IMAGE" \
    --runtime-dir "$RUNTIME_DIR" \
    --state-dir "$state_dir" >/dev/null
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

deploy "$SHA_A"
[[ "$(<"$STATE_DIR/current-sha")" == "$SHA_A" ]]
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

"$SCRIPT_DIR/rollback.sh" \
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE" \
  --runtime-dir "$RUNTIME_DIR" \
  --state-dir "$STATE_DIR" >/dev/null
[[ "$(<"$STATE_DIR/current-sha")" == "$SHA_A" ]]
[[ "$(<"$STATE_DIR/previous-sha")" == "$SHA_B" ]]

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
  printf 'incompatible infra/production contract did not fail closed\n' >&2
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
  --backend-image "$BACKEND_IMAGE" \
  --frontend-image "$FRONTEND_IMAGE" \
  --runtime-dir "$RUNTIME_DIR" \
  --state-dir "$STATE_DIR" >/dev/null; then
  printf 'rollback with incompatible infra/production contract did not fail closed\n' >&2
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

printf 'OPS-010 release script tests passed\n'
