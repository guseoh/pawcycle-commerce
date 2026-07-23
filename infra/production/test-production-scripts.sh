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

if [[ "$1" == "volume" ]]; then
  volume="${*: -1}"
  case "$2" in
    inspect) [[ -f "$FAKE_DOCKER_STATE/volume-$volume" ]] ;;
    create) : > "$FAKE_DOCKER_STATE/volume-$volume"; printf '%s\n' "$volume" ;;
  esac
  exit $?
fi

if [[ "$1" == "run" ]]; then
  if [[ "$*" == *"printf pawcycle-acme-probe"* ]]; then
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
  [[ "${FAKE_CERT_INVALID:-}" != "1" ]] || exit 1
  exit 0
fi

if [[ "$1" == "exec" ]]; then
  active_sha="$(<"$FAKE_DOCKER_STATE/active-sha")"
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

FAKE_DOMAIN="ops011-test.duckdns.org"
FAKE_EMAIL="operator${FAKE_AT_SIGN:-@}example.invalid"
export FAKE_DOMAIN

https_command() {
  "$SCRIPT_DIR/https.sh" "$@" \
    --domain "$FAKE_DOMAIN" \
    --backend-image "$BACKEND_IMAGE" \
    --frontend-image "$FRONTEND_IMAGE" \
    --runtime-dir "$RUNTIME_DIR" \
    --state-dir "$STATE_DIR" >/dev/null
}

export FAKE_CHALLENGE_FAIL=1
if https_command bootstrap; then
  printf 'failed challenge validation was reported as bootstrap success\n' >&2
  exit 1
fi
unset FAKE_CHALLENGE_FAIL
[[ ! -e "$FAKE_DOCKER_STATE/challenge-probe" ]] || {
  printf 'challenge probe remained after failed validation\n' >&2
  exit 1
}

https_command bootstrap
[[ ! -e "$STATE_DIR/https-enabled" ]]
[[ "$(<"$STATE_DIR/current-sha")" == "$SHA_A" ]]
[[ "$(<"$STATE_DIR/https-domain")" == "$FAKE_DOMAIN" ]]
[[ "$(stat -c '%a' "$STATE_DIR/https-domain")" == "600" ]]

mv "$STATE_DIR/https-domain" "$STATE_DIR/https-domain.saved"
ln -s "$STATE_DIR/https-domain.saved" "$STATE_DIR/https-domain"
if https_command bootstrap; then
  printf 'HTTPS domain symlink did not fail closed\n' >&2
  exit 1
fi
rm -f -- "$STATE_DIR/https-domain"
mv "$STATE_DIR/https-domain.saved" "$STATE_DIR/https-domain"

chmod 644 "$STATE_DIR/https-domain"
if https_command bootstrap; then
  printf 'HTTPS domain mode violation did not fail closed\n' >&2
  exit 1
fi
chmod 600 "$STATE_DIR/https-domain"

printf 'invalid.example.invalid\n' > "$STATE_DIR/https-domain"
if https_command bootstrap; then
  printf 'invalid HTTPS domain state did not fail closed\n' >&2
  exit 1
fi
printf '%s\n' "$FAKE_DOMAIN" > "$STATE_DIR/https-domain"
chmod 600 "$STATE_DIR/https-domain"

export FAKE_CERTBOT_FAIL=1
if https_command issue --email "$FAKE_EMAIL"; then
  printf 'certificate issuance failure enabled HTTPS\n' >&2
  exit 1
fi
unset FAKE_CERTBOT_FAIL
[[ ! -e "$STATE_DIR/https-enabled" ]]
[[ "$(<"$STATE_DIR/current-sha")" == "$SHA_A" ]]

export FAKE_HTTPS_FAIL_SHA="$SHA_A"
export FAKE_HTTPS_FAIL_PATH="/products"
if https_command issue --email "$FAKE_EMAIL"; then
  printf 'HTTPS activation failure did not restore bootstrap\n' >&2
  exit 1
fi
unset FAKE_HTTPS_FAIL_SHA FAKE_HTTPS_FAIL_PATH
[[ ! -e "$STATE_DIR/https-enabled" ]]
[[ ! -e "$STATE_DIR/nginx.https.conf" ]]
[[ ! -e "$STATE_DIR/nginx.https.conf.candidate" ]]
[[ ! -e "$FAKE_DOCKER_STATE/challenge-probe" ]]
[[ "$(<"$STATE_DIR/current-sha")" == "$SHA_A" ]]
[[ -f "$FAKE_DOCKER_STATE/volume-pawcycle-production-letsencrypt" ]]

https_command issue --email "$FAKE_EMAIL"
[[ "$(<"$STATE_DIR/https-enabled")" == "enabled" ]]
[[ "$(stat -c '%a' "$STATE_DIR/https-enabled")" == "600" ]]
[[ "$(stat -c '%a' "$STATE_DIR/nginx.https.conf")" == "600" ]]
grep -Fq "server_name $FAKE_DOMAIN;" "$STATE_DIR/nginx.https.conf"
grep -Fq "return 301 https://$FAKE_DOMAIN\$request_uri;" "$STATE_DIR/nginx.https.conf"
[[ "$(<"$STATE_DIR/current-sha")" == "$SHA_A" ]]

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

printf 'OPS-011 production script tests passed\n'
