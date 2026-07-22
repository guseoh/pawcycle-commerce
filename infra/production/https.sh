#!/usr/bin/env bash

set -Eeuo pipefail
set +x

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=infra/production/release-common.sh
source "$SCRIPT_DIR/release-common.sh"

CERTBOT_IMAGE="certbot/certbot:v5.7.0@sha256:d07bd043d61d6bee1114235ac12c2e9a5c54b6931b3ccf5e1174d6c8c4afaa95"
CERTIFICATE_NAME="pawcycle-production"
CERTBOT_WEBROOT_VOLUME="pawcycle-production-certbot-webroot"
LETSENCRYPT_VOLUME="pawcycle-production-letsencrypt"
HTTPS_MARKER=""
CERTBOT_CONFIG=""

usage() {
  cat <<'EOF'
Usage: https.sh <bootstrap|issue|renew|status|disable> --domain <single-hostname.duckdns.org> \
  --backend-image <ghcr-repository> --frontend-image <ghcr-repository> [options]

Actions:
  bootstrap  Keep public HTTP available and verify the HTTP-01 challenge path.
  issue      Issue or reuse the certificate, validate it, then enable HTTPS.
  renew      Renew the certificate and reload Nginx only after successful validation.
  status     Validate the certificate, HTTPS application paths, and HTTP redirect.
  disable    Return to bootstrap HTTP without deleting certificates or application data.

Options:
  --email <address>     Required only for issue; used by Certbot and never printed.
  --dry-run             Required modifier for a renewal rehearsal.
  --runtime-dir <path>  Materialized runtime bundle root (default: /opt/pawcycle/runtime)
  --state-dir <path>    Release state directory (default: /opt/pawcycle/state)
EOF
}

validate_domain() {
  [[ "$1" =~ ^([a-z0-9]|[a-z0-9][a-z0-9-]{0,61}[a-z0-9])\.duckdns\.org$ ]] \
    || die "domain must be one lowercase single-label duckdns.org hostname"
}

validate_email() {
  [[ "$1" =~ ^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$ ]] \
    || die "email address format is invalid"
}

cleanup() {
  if [[ -n "$CERTBOT_CONFIG" && -f "$CERTBOT_CONFIG" ]]; then
    rm -f -- "$CERTBOT_CONFIG"
  fi
}
trap cleanup EXIT

ensure_https_volumes() {
  local volume

  for volume in "$CERTBOT_WEBROOT_VOLUME" "$LETSENCRYPT_VOLUME"; do
    if ! docker volume inspect "$volume" >/dev/null 2>&1; then
      docker volume create --label com.pawcycle.production=https "$volume" >/dev/null
    fi
  done
}

write_certbot_config() {
  CERTBOT_CONFIG="$PAWCYCLE_STATE_DIR/certbot-cli.ini.$$"
  umask 077
  {
    printf 'email = %s\n' "$EMAIL"
    printf 'agree-tos = true\n'
    printf 'non-interactive = true\n'
    printf 'quiet = true\n'
  } > "$CERTBOT_CONFIG"
  chmod 600 "$CERTBOT_CONFIG"
}

certbot() {
  docker run --rm --platform linux/amd64 \
    --volume "$LETSENCRYPT_VOLUME:/etc/letsencrypt" \
    --volume "$CERTBOT_WEBROOT_VOLUME:/var/www/certbot" \
    --volume "$CERTBOT_CONFIG:/tmp/certbot-cli.ini:ro" \
    --tmpfs /tmp:size=32m,mode=1777 \
    "$CERTBOT_IMAGE" \
    --config /tmp/certbot-cli.ini \
    --work-dir /tmp/certbot-work \
    --logs-dir /tmp/certbot-logs \
    "$@" >/dev/null 2>&1
}

validate_certificate() {
  docker run --rm --platform linux/amd64 \
    --entrypoint python \
    --env EXPECTED_DOMAIN="$DOMAIN" \
    --volume "$LETSENCRYPT_VOLUME:/etc/letsencrypt:ro" \
    "$CERTBOT_IMAGE" -c \
    'import datetime, os, ssl, sys
p="/etc/letsencrypt/live/pawcycle-production/fullchain.pem"
try:
    info=ssl._ssl._test_decode_cert(p)
    sans={value.lower() for kind, value in info.get("subjectAltName", ()) if kind == "DNS"}
    expiry=datetime.datetime.strptime(info["notAfter"], "%b %d %H:%M:%S %Y %Z").replace(tzinfo=datetime.timezone.utc)
    expected=os.environ["EXPECTED_DOMAIN"].lower()
    valid=sans == {expected} and expiry > datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=1)
except (KeyError, OSError, ValueError, ssl.SSLError):
    valid=False
sys.exit(0 if valid else 1)' \
    >/dev/null
  printf 'Certificate hostname and validity checks passed\n'
}

validate_https_nginx_config() {
  docker run --rm \
    --network pawcycle-production-app \
    --volume "$SCRIPT_DIR/nginx.https.conf:/etc/nginx/conf.d/default.conf:ro" \
    --volume "$LETSENCRYPT_VOLUME:/etc/letsencrypt:ro" \
    "$PROXY_IMAGE" nginx -t >/dev/null
  printf 'HTTPS Nginx configuration check passed\n'
}

verify_challenge_path() {
  local probe_path=".well-known/acme-challenge/pawcycle-bootstrap-probe"

  docker run --rm --platform linux/amd64 --entrypoint sh \
    --volume "$CERTBOT_WEBROOT_VOLUME:/var/www/certbot" \
    "$CERTBOT_IMAGE" -c \
    "install -d -m 755 /var/www/certbot/.well-known/acme-challenge && printf pawcycle-acme-probe > /var/www/certbot/$probe_path"
  if [[ "$(curl --fail --silent --show-error --max-time 10 --header "Host: $DOMAIN" "http://127.0.0.1/$probe_path")" != "pawcycle-acme-probe" ]]; then
    return 1
  fi
  docker run --rm --platform linux/amd64 --entrypoint sh \
    --volume "$CERTBOT_WEBROOT_VOLUME:/var/www/certbot" \
    "$CERTBOT_IMAGE" -c "rm -f -- /var/www/certbot/$probe_path"
  printf 'HTTP-01 challenge path check passed\n'
}

verify_https_paths() {
  local path
  local code
  local redirect

  for path in /products /api/products; do
    curl --fail --silent --show-error --max-time 10 \
      --resolve "$DOMAIN:443:127.0.0.1" "https://$DOMAIN$path" >/dev/null || return 1
  done
  code="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 10 \
    --header "Host: $DOMAIN" http://127.0.0.1/products)"
  redirect="$(curl --silent --output /dev/null --write-out '%{redirect_url}' --max-time 10 \
    --header "Host: $DOMAIN" http://127.0.0.1/products)"
  [[ "$code" == "301" && "$redirect" == "https://$DOMAIN/products" ]] || return 1
  smoke_release || return 1
  printf 'HTTPS application and HTTP redirect checks passed\n'
}

bootstrap_http() {
  if https_enabled; then
    validate_certificate
    verify_https_paths
    printf 'HTTPS is already enabled; bootstrap did not downgrade the service\n'
    return 0
  fi

  ACTIVE_SHA="$TARGET_SHA"
  compose up --detach --pull never --no-deps --force-recreate proxy
  wait_healthy proxy
  verify_running_release
  smoke_release
  verify_challenge_path
  printf 'Bootstrap HTTP service is ready for certificate issuance\n'
}

enable_https() {
  write_state "$HTTPS_MARKER_NAME" enabled
  if compose up --detach --pull never --no-deps --force-recreate proxy \
    && wait_healthy proxy \
    && verify_running_release \
    && verify_https_paths; then
    printf 'HTTPS service enabled\n'
    return 0
  fi

  rm -f -- "$HTTPS_MARKER"
  printf 'HTTPS activation failed; restoring bootstrap HTTP configuration\n' >&2
  if compose up --detach --pull never --no-deps --force-recreate proxy \
    && wait_healthy proxy \
    && verify_running_release \
    && smoke_release; then
    die "HTTPS activation failed; bootstrap HTTP service was restored"
  fi
  die "HTTPS activation and bootstrap HTTP restoration both failed; release and data volumes were not removed"
}

disable_https() {
  local backup="$PAWCYCLE_STATE_DIR/${HTTPS_MARKER_NAME}.disable-backup"

  [[ -f "$HTTPS_MARKER" ]] || {
    bootstrap_http
    printf 'HTTPS was already disabled\n'
    return 0
  }
  mv "$HTTPS_MARKER" "$backup"
  if compose up --detach --pull never --no-deps --force-recreate proxy \
    && wait_healthy proxy \
    && verify_running_release \
    && smoke_release \
    && verify_challenge_path; then
    rm -f -- "$backup"
    printf 'Bootstrap HTTP restored; certificate volumes were preserved\n'
    return 0
  fi

  mv "$backup" "$HTTPS_MARKER"
  compose up --detach --pull never --no-deps --force-recreate proxy || true
  wait_healthy proxy || true
  die "HTTP recovery failed; HTTPS marker was restored and data volumes were preserved"
}

ACTION="${1:-}"
[[ -n "$ACTION" ]] || { usage >&2; exit 1; }
shift

DOMAIN=""
EMAIL=""
BACKEND_IMAGE=""
FRONTEND_IMAGE=""
PAWCYCLE_RUNTIME_DIR="/opt/pawcycle/runtime"
PAWCYCLE_STATE_DIR="/opt/pawcycle/state"
DRY_RUN=false

while (( $# > 0 )); do
  case "$1" in
    --domain) DOMAIN="${2:-}"; shift 2 ;;
    --email) EMAIL="${2:-}"; shift 2 ;;
    --backend-image) BACKEND_IMAGE="${2:-}"; shift 2 ;;
    --frontend-image) FRONTEND_IMAGE="${2:-}"; shift 2 ;;
    --runtime-dir) PAWCYCLE_RUNTIME_DIR="${2:-}"; shift 2 ;;
    --state-dir) PAWCYCLE_STATE_DIR="${2:-}"; shift 2 ;;
    --dry-run) DRY_RUN=true; shift ;;
    --help) usage; exit 0 ;;
    *) usage >&2; die "unknown argument: $1" ;;
  esac
done

case "$ACTION" in
  bootstrap|issue|renew|status|disable) ;;
  *) usage >&2; die "unknown action: $ACTION" ;;
esac
validate_domain "$DOMAIN"
if [[ "$ACTION" == "issue" ]]; then
  validate_email "$EMAIL"
elif [[ -n "$EMAIL" ]]; then
  die "--email is accepted only by the issue action"
fi
if [[ "$ACTION" != "renew" && "$DRY_RUN" == true ]]; then
  die "--dry-run is accepted only by the renew action"
fi

prepare_state_directory
[[ -f "$PAWCYCLE_STATE_DIR/current-sha" ]] || die "current release state is missing"
TARGET_SHA="$(<"$PAWCYCLE_STATE_DIR/current-sha")"
initialize_release_context
ACTIVE_SHA="$TARGET_SHA"
HTTPS_MARKER="$PAWCYCLE_STATE_DIR/$HTTPS_MARKER_NAME"
ensure_https_volumes

case "$ACTION" in
  bootstrap)
    bootstrap_http
    ;;
  issue)
    if https_enabled; then
      validate_certificate
      verify_https_paths
      printf 'HTTPS is already enabled; certificate issuance was not repeated\n'
      exit 0
    fi
    bootstrap_http
    write_certbot_config
    if ! certbot certonly --webroot --webroot-path /var/www/certbot \
      --domains "$DOMAIN" --cert-name "$CERTIFICATE_NAME" --keep-until-expiring; then
      die "certificate issuance failed; the current service and release remain active"
    fi
    validate_certificate
    validate_https_nginx_config
    enable_https
    ;;
  renew)
    https_enabled || die "HTTPS is not enabled; renewal is unavailable in bootstrap mode"
    CERTBOT_CONFIG="$PAWCYCLE_STATE_DIR/certbot-cli.ini.$$"
    umask 077
    printf 'non-interactive = true\nquiet = true\n' > "$CERTBOT_CONFIG"
    chmod 600 "$CERTBOT_CONFIG"
    renew_args=(renew --cert-name "$CERTIFICATE_NAME" --webroot-path /var/www/certbot --no-random-sleep-on-renew)
    if [[ "$DRY_RUN" == true ]]; then
      renew_args+=(--dry-run)
    fi
    if ! certbot "${renew_args[@]}"; then
      die "certificate renewal failed; Nginx was not reloaded and the current certificate remains active"
    fi
    validate_certificate
    if [[ "$DRY_RUN" == true ]]; then
      verify_https_paths
      printf 'Certificate renewal dry-run passed without reloading Nginx\n'
      exit 0
    fi
    proxy_id="$(compose ps --quiet proxy)"
    [[ -n "$proxy_id" ]] || die "proxy container is missing; certificate remains preserved"
    docker exec "$proxy_id" nginx -t >/dev/null \
      || die "running Nginx configuration check failed; certificate remains preserved"
    docker exec "$proxy_id" nginx -s reload \
      || die "Nginx reload failed; the running worker keeps the previously loaded certificate"
    verify_https_paths
    printf 'Certificate renewal validation and Nginx reload passed\n'
    ;;
  status)
    https_enabled || die "HTTPS is not enabled"
    validate_certificate
    verify_https_paths
    ;;
  disable)
    disable_https
    ;;
esac
