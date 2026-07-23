#!/usr/bin/env bash

set -Eeuo pipefail
set +x

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=infra/production/release-common.sh
source "$SCRIPT_DIR/release-common.sh"

HTTPS_MARKER=""
CERTBOT_CONFIG=""
HTTPS_DOMAIN_CANDIDATE=""
HTTPS_CONFIG_CANDIDATE=""

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

validate_email() {
  [[ "$1" =~ ^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$ ]] \
    || die "email address format is invalid"
}

cleanup() {
  if [[ -n "$CERTBOT_CONFIG" && -f "$CERTBOT_CONFIG" ]]; then
    rm -f -- "$CERTBOT_CONFIG"
  fi
  if [[ -n "$HTTPS_DOMAIN_CANDIDATE" \
    && ( -e "$HTTPS_DOMAIN_CANDIDATE" || -L "$HTTPS_DOMAIN_CANDIDATE" ) ]]; then
    rm -f -- "$HTTPS_DOMAIN_CANDIDATE"
  fi
  if [[ -n "$HTTPS_CONFIG_CANDIDATE" && -f "$HTTPS_CONFIG_CANDIDATE" ]]; then
    rm -f -- "$HTTPS_CONFIG_CANDIDATE"
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

select_https_domain() {
  local domain_file="$PAWCYCLE_STATE_DIR/$HTTPS_DOMAIN_NAME"

  if [[ -e "$domain_file" || -L "$domain_file" ]]; then
    load_https_domain
    [[ "$HTTPS_DOMAIN" == "$DOMAIN" ]] \
      || die "requested domain does not match the approved HTTPS domain state"
    return 0
  fi
  HTTPS_DOMAIN="$DOMAIN"
}

approve_https_domain() {
  local domain_file="$PAWCYCLE_STATE_DIR/$HTTPS_DOMAIN_NAME"

  if [[ -e "$domain_file" || -L "$domain_file" ]]; then
    load_https_domain
    [[ "$HTTPS_DOMAIN" == "$DOMAIN" ]] \
      || die "requested domain does not match the approved HTTPS domain state"
    return 0
  fi

  HTTPS_DOMAIN_CANDIDATE="$PAWCYCLE_STATE_DIR/${HTTPS_DOMAIN_NAME}.candidate"
  rm -f -- "$HTTPS_DOMAIN_CANDIDATE"
  umask 077
  printf '%s\n' "$HTTPS_DOMAIN" > "$HTTPS_DOMAIN_CANDIDATE"
  chmod 600 "$HTTPS_DOMAIN_CANDIDATE"
  mv -f "$HTTPS_DOMAIN_CANDIDATE" "$domain_file"
  HTTPS_DOMAIN_CANDIDATE=""
  load_https_domain
}

render_https_nginx_candidate() {
  local target="$PAWCYCLE_STATE_DIR/$HTTPS_NGINX_CONFIG_NAME"

  if [[ -L "$target" ]]; then
    die "generated HTTPS Nginx configuration must not be a symlink"
  fi
  HTTPS_CONFIG_CANDIDATE="$PAWCYCLE_STATE_DIR/${HTTPS_NGINX_CONFIG_NAME}.candidate"
  rm -f -- "$HTTPS_CONFIG_CANDIDATE"
  sed "s/__PAWCYCLE_DOMAIN__/$HTTPS_DOMAIN/g" \
    "$SCRIPT_DIR/nginx.https.conf" > "$HTTPS_CONFIG_CANDIDATE"
  chmod 600 "$HTTPS_CONFIG_CANDIDATE"
}

promote_https_nginx_config() {
  local target="$PAWCYCLE_STATE_DIR/$HTTPS_NGINX_CONFIG_NAME"

  [[ -n "$HTTPS_CONFIG_CANDIDATE" && -f "$HTTPS_CONFIG_CANDIDATE" ]] \
    || die "validated HTTPS Nginx configuration candidate is missing"
  mv -f "$HTTPS_CONFIG_CANDIDATE" "$target"
  HTTPS_CONFIG_CANDIDATE=""
  validate_https_nginx_state
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

validate_https_nginx_config() {
  local config="$1"

  docker run --rm \
    --network pawcycle-production-app \
    --volume "$config:/etc/nginx/conf.d/default.conf:ro" \
    --volume "$LETSENCRYPT_VOLUME:/etc/letsencrypt:ro" \
    "$PROXY_IMAGE" nginx -t >/dev/null
  printf 'HTTPS Nginx configuration check passed\n'
}

verify_challenge_path() {
  local probe_path=".well-known/acme-challenge/pawcycle-bootstrap-probe"
  local probe_ok=false

  docker run --rm --platform linux/amd64 --entrypoint sh \
    --volume "$CERTBOT_WEBROOT_VOLUME:/var/www/certbot" \
    "$CERTBOT_IMAGE" -c \
    "install -d -m 755 /var/www/certbot/.well-known/acme-challenge && printf pawcycle-acme-probe > /var/www/certbot/$probe_path"
  if [[ "$(curl --fail --silent --show-error --max-time 10 \
    --header "Host: $HTTPS_DOMAIN" "http://127.0.0.1/$probe_path")" == "pawcycle-acme-probe" ]]; then
    probe_ok=true
  fi
  docker run --rm --platform linux/amd64 --entrypoint sh \
    --volume "$CERTBOT_WEBROOT_VOLUME:/var/www/certbot" \
    "$CERTBOT_IMAGE" -c "rm -f -- /var/www/certbot/$probe_path"
  [[ "$probe_ok" == true ]] || return 1
  printf 'HTTP-01 challenge path check passed\n'
}

bootstrap_http() {
  if https_enabled; then
    verify_https_release
    printf 'HTTPS is already enabled; bootstrap did not downgrade the service\n'
    return 0
  fi

  ACTIVE_SHA="$TARGET_SHA"
  compose up --detach --pull never --no-deps --force-recreate proxy
  wait_healthy proxy
  verify_running_release
  smoke_release
  verify_challenge_path
  approve_https_domain
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
  rm -f -- "$PAWCYCLE_STATE_DIR/$HTTPS_NGINX_CONFIG_NAME"
  printf 'HTTPS activation failed; restoring bootstrap HTTP configuration\n' >&2
  if compose up --detach --pull never --no-deps --force-recreate proxy \
    && wait_healthy proxy \
    && verify_running_release \
    && smoke_release; then
    if verify_challenge_path; then
      die "HTTPS activation failed; bootstrap HTTP service was restored"
    fi
  fi
  die "HTTPS activation and bootstrap HTTP restoration both failed; release and data volumes were not removed"
}

disable_https() {
  local backup="$PAWCYCLE_STATE_DIR/${HTTPS_MARKER_NAME}.disable-backup"

  if [[ ! -e "$HTTPS_MARKER" && ! -L "$HTTPS_MARKER" ]]; then
    bootstrap_http
    printf 'HTTPS was already disabled\n'
    return 0
  fi
  https_enabled
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
validate_https_domain "$DOMAIN"
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
require_command sed
ACTIVE_SHA="$TARGET_SHA"
HTTPS_MARKER="$PAWCYCLE_STATE_DIR/$HTTPS_MARKER_NAME"
ensure_https_volumes
select_https_domain

case "$ACTION" in
  bootstrap)
    bootstrap_http
    ;;
  issue)
    if https_enabled; then
      verify_https_release
      printf 'HTTPS is already enabled; certificate issuance was not repeated\n'
      exit 0
    fi
    bootstrap_http
    write_certbot_config
    if ! certbot certonly --webroot --webroot-path /var/www/certbot \
      --domains "$HTTPS_DOMAIN" --cert-name "$CERTIFICATE_NAME" --keep-until-expiring; then
      die "certificate issuance failed; the current service and release remain active"
    fi
    validate_https_certificate
    render_https_nginx_candidate
    validate_https_nginx_config "$HTTPS_CONFIG_CANDIDATE"
    promote_https_nginx_config
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
    validate_https_certificate
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
    validate_https_certificate
    verify_https_paths
    ;;
  disable)
    disable_https
    ;;
esac
