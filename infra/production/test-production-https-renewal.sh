#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
TEST_ROOT="$(mktemp -d)"
FAKE_BIN="$TEST_ROOT/bin"
FAKE_STATE="$TEST_ROOT/fake-state"
STATE_DIR="$TEST_ROOT/state"
DOMAIN_FILE="$STATE_DIR/https-domain"
ENABLED_FILE="$STATE_DIR/https-enabled"
LOCK_FILE="$TEST_ROOT/renewal.lock"
FAKE_DOCKER_LOG="$TEST_ROOT/docker.log"
mkdir -p "$FAKE_BIN" "$FAKE_STATE" "$STATE_DIR"
printf '%s\n' 'renewal-test.duckdns.org' > "$DOMAIN_FILE"
printf '%s\n' enabled > "$ENABLED_FILE"
chmod 600 "$DOMAIN_FILE" "$ENABLED_FILE"

cleanup() {
  rm -rf -- "$TEST_ROOT"
}
trap cleanup EXIT

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

assert_contains() {
  local needle="$1"
  local file="$2"
  grep -Fq -- "$needle" "$file" || fail "missing contract: $needle"
}

assert_not_contains() {
  local needle="$1"
  local file="$2"
  ! grep -Fq -- "$needle" "$file" || fail "forbidden contract: $needle"
}

cat > "$FAKE_BIN/docker" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail

log_file="${FAKE_DOCKER_LOG:?}"
printf '%s\n' "$*" >> "$log_file"

case "$*" in
  volume*)
    exit 0
    ;;
  ps*)
    printf '%s\n' proxy-fixture
    exit 0
    ;;
  inspect*)
    printf '%s\n' 'pawcycle-production-certbot-webroot|/var/www/certbot|false'
    printf '%s\n' 'pawcycle-production-letsencrypt|/etc/letsencrypt|false'
    printf '%s\n' '|/etc/nginx/conf.d/default.conf|false'
    exit 0
    ;;
  exec*)
    if [[ "$*" == *" nginx -T"* ]]; then
      printf '%s\n' 'server_name renewal-test.duckdns.org;'
      printf '%s\n' 'ssl_certificate /etc/letsencrypt/live/pawcycle-production/fullchain.pem;'
      printf '%s\n' 'ssl_certificate_key /etc/letsencrypt/live/pawcycle-production/privkey.pem;'
      exit 0
    fi
    if [[ "$*" == *" nginx -t"* ]]; then
      exit "${FAKE_NGINX_TEST_STATUS:-0}"
    fi
    if [[ "$*" == *" nginx -s reload"* ]]; then
      printf '%s\n' reload >> "$FAKE_DOCKER_LOG"
      exit "${FAKE_NGINX_RELOAD_STATUS:-0}"
    fi
    exit 99
    ;;
  run*)
    if [[ "$*" == *--entrypoint*python* ]]; then
      if [[ "$*" == *--env*EXPECTED_DOMAIN* ]]; then
        exit "${FAKE_CERTIFICATE_VALIDATION_STATUS:-0}"
      fi
      if [[ -e "${FAKE_RENEWED_MARKER:-}" ]]; then
        printf '%s\n' "${FAKE_AFTER_FINGERPRINT:-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb}"
      else
        printf '%s\n' "${FAKE_BEFORE_FINGERPRINT:-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa}"
      fi
      exit 0
    fi
    if [[ "$*" == *renew* ]]; then
      if [[ "${FAKE_CERTBOT_STATUS:-0}" != 0 ]]; then
        exit "$FAKE_CERTBOT_STATUS"
      fi
      if [[ -n "${FAKE_RENEWED_MARKER:-}" \
        && "${FAKE_NO_RENEW:-0}" != 1 \
        && " $* " != *" --dry-run "* ]]; then
        : > "$FAKE_RENEWED_MARKER"
        for argument in "$@"; do
          if [[ "$argument" == */run/pawcycle-renewal ]]; then
            marker_directory="${argument%:/run/pawcycle-renewal}"
            : > "$marker_directory/renewed"
          fi
        done
      fi
      exit 0
    fi
    exit 0
    ;;
esac

exit 99
EOF
chmod 700 "$FAKE_BIN/docker"

script="$SCRIPT_DIR/renew-production-certificate.sh"
compose="$SCRIPT_DIR/compose.yaml"
https_config="$SCRIPT_DIR/nginx.https.conf"
service="$SCRIPT_DIR/systemd/pawcycle-production-https-renew.service"
timer="$SCRIPT_DIR/systemd/pawcycle-production-https-renew.timer"
installer="$SCRIPT_DIR/install-production-https-renewal.sh"

bash -n "$script" "$installer" "$SCRIPT_DIR/test-production-https-renewal.sh"
assert_contains 'pawcycle-production-certbot-webroot' "$script"
assert_contains 'pawcycle-production-letsencrypt' "$script"
assert_contains 'name: ${PAWCYCLE_CERTBOT_WEBROOT_VOLUME:-pawcycle-production-certbot-webroot}' "$compose"
assert_contains 'name: ${PAWCYCLE_LETSENCRYPT_VOLUME:-pawcycle-production-letsencrypt}' "$compose"
assert_contains 'certbot-webroot:/var/www/certbot:ro' "$compose"
assert_contains 'letsencrypt:/etc/letsencrypt:ro' "$compose"
assert_contains '/etc/letsencrypt/live/pawcycle-production/fullchain.pem' "$https_config"
assert_contains '/etc/letsencrypt/live/pawcycle-production/privkey.pem' "$https_config"
assert_contains 'nginx -t' "$script"
assert_contains 'nginx -s reload' "$script"
assert_contains '--deploy-hook' "$script"
assert_contains 'runtime Nginx config' "$script"
assert_not_contains 'docker compose up' "$script"
assert_not_contains 'docker compose restart' "$script"
assert_not_contains 'docker compose down' "$script"
assert_not_contains 'docker volume rm' "$script"
assert_not_contains 'docker volume create' "$script"
assert_not_contains 'docker restart' "$script"
assert_not_contains 'docker stop' "$script"
assert_not_contains 'docker start' "$script"
assert_not_contains 'backend' "$script"
assert_not_contains 'frontend' "$script"
assert_contains 'Type=oneshot' "$service"
assert_contains 'ExecStart=/usr/local/libexec/pawcycle-production-https-renew' "$service"
assert_contains 'TimeoutStartSec=15min' "$service"
assert_contains 'ProtectSystem=full' "$service"
assert_contains 'OnCalendar=*-*-* 03:00:00' "$timer"
assert_contains 'OnCalendar=*-*-* 15:00:00' "$timer"
assert_contains 'Persistent=true' "$timer"
assert_contains 'Unit=pawcycle-production-https-renew.service' "$timer"
assert_contains 'install -m 0755' "$installer"
assert_contains 'systemctl daemon-reload' "$installer"
assert_not_contains 'systemctl enable' "$installer"
assert_not_contains 'systemctl start' "$installer"

run_renewal() {
  local expected_status="$1"
  local expected_reload_count="$2"
  local fake_no_renew="$3"
  local fake_certbot_status="$4"
  local fake_nginx_test_status="$5"
  local fake_nginx_reload_status="$6"
  local fake_certificate_validation_status="$7"
  shift 7
  local output=""
  local status=0
  : > "$FAKE_DOCKER_LOG"
  rm -f -- "$FAKE_STATE/renewed"
  output="$(
    PATH="$FAKE_BIN:$PATH" \
      FAKE_DOCKER_LOG="$FAKE_DOCKER_LOG" \
      FAKE_RENEWED_MARKER="$FAKE_STATE/renewed" \
      FAKE_NO_RENEW="$fake_no_renew" \
      FAKE_CERTBOT_STATUS="$fake_certbot_status" \
      FAKE_NGINX_TEST_STATUS="$fake_nginx_test_status" \
      FAKE_NGINX_RELOAD_STATUS="$fake_nginx_reload_status" \
      FAKE_CERTIFICATE_VALIDATION_STATUS="$fake_certificate_validation_status" \
      PAWCYCLE_STATE_DIR="$STATE_DIR" \
      PAWCYCLE_HTTPS_RENEWAL_LOCK_FILE="$LOCK_FILE" \
      bash "$script" "$@" 2>&1
  )" || status=$?
  [[ "$status" == "$expected_status" ]] || fail "unexpected status $status: $output"
  actual_reload_count="$(grep -Fxc reload "$FAKE_DOCKER_LOG" || true)"
  [[ "$actual_reload_count" == "$expected_reload_count" ]] \
    || fail "unexpected reload count $actual_reload_count"
}

run_renewal 0 0 0 0 0 0 0 --dry-run
run_renewal 0 1 0 0 0 0 0
run_renewal 0 0 1 0 0 0 0
run_renewal 1 0 0 1 0 0 0
run_renewal 1 0 0 0 1 0 0
run_renewal 1 1 0 0 0 1 0
run_renewal 1 0 0 0 0 0 1

printf 'OPS-OCI-004 HTTPS renewal service, timer, fail-closed, and no-restart contracts passed\n'
