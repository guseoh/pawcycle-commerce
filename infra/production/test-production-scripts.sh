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
exit 0
EOF

cat > "$BIN_DIR/docker" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail

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
      printf '%s' "$RELEASE_SHA" > "$FAKE_DOCKER_STATE/active-sha"
      printf '%s' "$BACKEND_IMAGE" > "$FAKE_DOCKER_STATE/backend-image"
      printf '%s' "$FRONTEND_IMAGE" > "$FAKE_DOCKER_STATE/frontend-image"
      ;;
    ps)
      if [[ "$*" == *"--quiet"* ]]; then
        printf 'container-%s\n' "$service"
      else
        printf 'fake compose services healthy\n'
      fi
      ;;
  esac
  exit 0
fi

if [[ "$1" == "pull" ]]; then
  exit 0
fi

if [[ "$1" == "image" && "$2" == "inspect" ]]; then
  reference="${*: -1}"
  sha="${reference##*:}"
  repository="${reference%:*}"
  if [[ "$*" == *"RepoDigests"* ]]; then
    printf '%s@sha256:%064d\n' "$repository" 0
  else
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
    printf '%s:%s\n' "$repository" "$active_sha"
  else
    printf '%s\n' "$active_sha"
  fi
  exit 0
fi

exit 0
EOF

chmod +x "$BIN_DIR/aws" "$BIN_DIR/curl" "$BIN_DIR/docker"
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

BACKEND_IMAGE="ghcr.io/example/pawcycle-commerce-backend"
FRONTEND_IMAGE="ghcr.io/example/pawcycle-commerce-frontend"
SHA_A="1111111111111111111111111111111111111111"
SHA_B="2222222222222222222222222222222222222222"
SHA_C="3333333333333333333333333333333333333333"

deploy() {
  "$SCRIPT_DIR/deploy.sh" \
    --sha "$1" \
    --backend-image "$BACKEND_IMAGE" \
    --frontend-image "$FRONTEND_IMAGE" \
    --runtime-dir "$RUNTIME_DIR" \
    --state-dir "$STATE_DIR" >/dev/null
}

deploy "$SHA_A"
[[ "$(<"$STATE_DIR/current-sha")" == "$SHA_A" ]]
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

export FAKE_FAIL_SHA="$SHA_C"
if deploy "$SHA_C"; then
  printf 'unhealthy target did not fail\n' >&2
  exit 1
fi
unset FAKE_FAIL_SHA
[[ "$(<"$STATE_DIR/current-sha")" == "$SHA_A" ]]
[[ "$(<"$FAKE_DOCKER_STATE/active-sha")" == "$SHA_A" ]]

printf 'OPS-010 release script tests passed\n'
