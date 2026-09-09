#!/usr/bin/env bash

set -Eeuo pipefail
set +x

PROJECT_NAME="pawcycle-production"
ACTIVE_DOMAIN="pawcycle.duckdns.org"
STATE_DIR="${PAWCYCLE_STATE_DIR:-/opt/pawcycle/state}"
APPROVED_DOMAIN_FILE="$STATE_DIR/https-domain"
HTTPS_ENABLED_FILE="$STATE_DIR/https-enabled"
CERTBOT_WEBROOT_VOLUME="pawcycle-production-certbot-webroot"
LETSENCRYPT_VOLUME="pawcycle-production-letsencrypt"
CERTIFICATE_NAME="$ACTIVE_DOMAIN"
CERTBOT_IMAGE="certbot/certbot:v5.8.0@sha256:398c47284a6d6782825be71685f677ef3a1e65b8b5c278a8b1e99f6da84b4eb9"
MIN_CERT_VALIDITY_SECONDS="86400"
LOCK_FILE="${PAWCYCLE_HTTPS_RENEWAL_LOCK_FILE:-/run/lock/pawcycle-production-https-renewal.lock}"
DOCKER_BIN="docker"
DRY_RUN=false
ACTION="renew"
ADOPT_CONFIRM=false
APPROVED_DOMAIN=""
PROXY_CONTAINER=""
RENEWAL_MARKER_DIR=""

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage: renew-production-certificate.sh [preflight|adopt|renew] [--confirm-adopt] [--dry-run]

preflight validates the live HTTPS runtime without creating state files.
adopt --confirm-adopt records the already verified active HTTPS state; it does
not issue, renew, replace certificates, or reload Nginx.
renew checks the adopted lineage. A live Nginx reload is performed only when a
non-dry renewal succeeds, the certificate changed, the approved hostname and
validity are valid, and the running Nginx config passes.
EOF
}

cleanup() {
  if [[ -n "$RENEWAL_MARKER_DIR" && -d "$RENEWAL_MARKER_DIR" ]]; then
    rm -rf -- "$RENEWAL_MARKER_DIR"
  fi
}
trap cleanup EXIT

validate_domain() {
  [[ "$1" == "$ACTIVE_DOMAIN" ]] || die "approved HTTPS domain is not the active OCI lineage"
}

require_state_dir() {
  [[ -d "$STATE_DIR" && ! -L "$STATE_DIR" ]] \
    || die "HTTPS state directory is missing; create and approve it explicitly"
  [[ "$(stat -c '%a' "$STATE_DIR" 2>/dev/null)" == "700" ]] \
    || die "HTTPS state directory permissions are invalid"
}

state_path_exists() {
  [[ -e "$1" || -L "$1" ]]
}

require_regular_state_file() {
  local path="$1"
  local description="$2"

  [[ -f "$path" && ! -L "$path" ]] || die "$description is missing or not a regular file"
  [[ "$(stat -c '%a' "$path" 2>/dev/null)" == "600" ]] \
    || die "$description permissions are invalid"
}

load_approved_domain() {
  require_state_dir
  require_regular_state_file "$HTTPS_ENABLED_FILE" "HTTPS enabled state"
  [[ "$(<"$HTTPS_ENABLED_FILE")" == enabled ]] || die "HTTPS enabled state is invalid"

  require_regular_state_file "$APPROVED_DOMAIN_FILE" "approved HTTPS domain state"
  APPROVED_DOMAIN="$(<"$APPROVED_DOMAIN_FILE")"
  validate_domain "$APPROVED_DOMAIN"
}

load_optional_adoption_state() {
  require_state_dir
  if state_path_exists "$APPROVED_DOMAIN_FILE" || state_path_exists "$HTTPS_ENABLED_FILE"; then
    load_approved_domain
  else
    APPROVED_DOMAIN="$ACTIVE_DOMAIN"
  fi
}

check_image_presence() {
  "$DOCKER_BIN" image inspect "$CERTBOT_IMAGE" >/dev/null 2>&1 \
    || die "approved Certbot image is not present; install it in a separate approved step before renewal"
}

check_certificate_volumes() {
  "$DOCKER_BIN" volume inspect "$CERTBOT_WEBROOT_VOLUME" >/dev/null 2>&1 \
    || die "configured Certbot webroot volume is missing"
  "$DOCKER_BIN" volume inspect "$LETSENCRYPT_VOLUME" >/dev/null 2>&1 \
    || die "configured Let's Encrypt volume is missing"
}

find_running_proxy() {
  local -a proxy_containers=()

  mapfile -t proxy_containers < <(
    "$DOCKER_BIN" ps \
      --filter "label=com.docker.compose.project=$PROJECT_NAME" \
      --filter 'label=com.docker.compose.service=proxy' \
      --filter 'status=running' \
      --format '{{.ID}}' 2>/dev/null
  )
  (( ${#proxy_containers[@]} == 1 )) || die "exactly one running Compose proxy is required"
  PROXY_CONTAINER="${proxy_containers[0]}"
}

validate_proxy_mounts() {
  local mounts

  mounts="$("$DOCKER_BIN" inspect "$PROXY_CONTAINER" \
    --format '{{range .Mounts}}{{printf "%s|%s|%t\n" .Name .Destination .RW}}{{end}}' \
    2>/dev/null)" || die "running Compose proxy could not be inspected"

  grep -Fqx "$CERTBOT_WEBROOT_VOLUME|/var/www/certbot|false" <<<"$mounts" \
    || die "running proxy does not use the approved Certbot webroot volume"
  grep -Fqx "$LETSENCRYPT_VOLUME|/etc/letsencrypt|false" <<<"$mounts" \
    || die "running proxy does not use the approved Let's Encrypt volume"
  grep -Fqx '|/etc/nginx/conf.d/default.conf|false' <<<"$mounts" \
    || die "running proxy does not use a read-only runtime Nginx config"
}

validate_runtime_https_config() {
  local runtime_config

  runtime_config="$("$DOCKER_BIN" exec "$PROXY_CONTAINER" nginx -T 2>/dev/null)" \
    || die "running proxy HTTPS config could not be inspected"
  grep -Fq "server_name $APPROVED_DOMAIN;" <<<"$runtime_config" \
    || die "running proxy HTTPS config does not use the approved hostname"
  grep -Fq "ssl_certificate /etc/letsencrypt/live/$CERTIFICATE_NAME/fullchain.pem;" \
    <<<"$runtime_config" \
    || die "running proxy HTTPS config does not use the approved certificate lineage"
  grep -Fq "ssl_certificate_key /etc/letsencrypt/live/$CERTIFICATE_NAME/privkey.pem;" \
    <<<"$runtime_config" \
    || die "running proxy HTTPS config does not use the approved certificate key path"
}

certificate_fingerprint() {
  "$DOCKER_BIN" run --rm --pull never --platform linux/amd64 \
    --entrypoint python \
    --env "CERTIFICATE_PATH=/etc/letsencrypt/live/$CERTIFICATE_NAME/fullchain.pem" \
    --volume "$LETSENCRYPT_VOLUME:/etc/letsencrypt:ro" \
    "$CERTBOT_IMAGE" -c 'import os
from cryptography import x509
from cryptography.hazmat.primitives import hashes

path = os.environ["CERTIFICATE_PATH"]
with open(path, "rb") as source:
    certificate = x509.load_pem_x509_certificate(source.read())
print(certificate.fingerprint(hashes.SHA256()).hex())' 2>/dev/null
}

validate_certificate() {
  local minimum_validity_seconds="${1:-$MIN_CERT_VALIDITY_SECONDS}"

  "$DOCKER_BIN" run --rm --pull never --platform linux/amd64 \
    --entrypoint python \
    --env "CERTIFICATE_PATH=/etc/letsencrypt/live/$CERTIFICATE_NAME/fullchain.pem" \
    --env "EXPECTED_DOMAIN=$APPROVED_DOMAIN" \
    --env "MIN_VALIDITY_SECONDS=$minimum_validity_seconds" \
    --volume "$LETSENCRYPT_VOLUME:/etc/letsencrypt:ro" \
    "$CERTBOT_IMAGE" -c 'import datetime
import os
import sys
from cryptography import x509

path = os.environ["CERTIFICATE_PATH"]
try:
    with open(path, "rb") as source:
        certificate = x509.load_pem_x509_certificate(source.read())
    san = certificate.extensions.get_extension_for_class(x509.SubjectAlternativeName).value
    dns_names = set(san.get_values_for_type(x509.DNSName))
    expected = os.environ["EXPECTED_DOMAIN"].lower()
    now = datetime.datetime.now(datetime.timezone.utc)
    minimum = now + datetime.timedelta(seconds=int(os.environ["MIN_VALIDITY_SECONDS"]))
    valid = (
        len(san) == 1
        and dns_names == {expected}
        and certificate.not_valid_before_utc <= now
        and certificate.not_valid_after_utc >= minimum
    )
except (OSError, ValueError, x509.ExtensionNotFound, KeyError):
    valid = False
sys.exit(0 if valid else 1)' >/dev/null 2>&1 \
    || die "renewed certificate hostname or validity validation failed"
}

run_certbot() {
  local -a renewal_args=(
    renew
    --cert-name "$CERTIFICATE_NAME"
    --webroot-path /var/www/certbot
    --config-dir /etc/letsencrypt
    --work-dir /tmp/certbot-work
    --logs-dir /tmp/certbot-logs
    --non-interactive
    --no-random-sleep-on-renew
    --quiet
  )

  if [[ "$DRY_RUN" == true ]]; then
    renewal_args+=(--dry-run)
  else
    renewal_args+=(--deploy-hook 'touch /run/pawcycle-renewal/renewed')
  fi

  check_image_presence
  "$DOCKER_BIN" run --rm --pull never --platform linux/amd64 \
    --volume "$LETSENCRYPT_VOLUME:/etc/letsencrypt" \
    --volume "$CERTBOT_WEBROOT_VOLUME:/var/www/certbot" \
    --volume "$RENEWAL_MARKER_DIR:/run/pawcycle-renewal" \
    --read-only \
    --tmpfs /tmp:size=32m,mode=1777 \
    --security-opt no-new-privileges:true \
    "$CERTBOT_IMAGE" "${renewal_args[@]}" >/dev/null 2>&1 \
    || die "Certbot renewal command failed; Nginx was not reloaded"
}

validate_nginx_config() {
  "$DOCKER_BIN" exec "$PROXY_CONTAINER" nginx -t >/dev/null 2>&1 \
    || die "Nginx configuration validation failed; Nginx was not reloaded"
}

reload_nginx() {
  "$DOCKER_BIN" exec "$PROXY_CONTAINER" nginx -s reload >/dev/null 2>&1 \
    || die "Nginx reload failed; application services were not restarted"
}

write_state_file() {
  local target="$1"
  local value="$2"
  local temporary

  temporary="$(mktemp "$STATE_DIR/.https-state.XXXXXX")" \
    || die "HTTPS adoption state file could not be staged"
  chmod 600 "$temporary"
  if ! printf '%s\n' "$value" >"$temporary"; then
    rm -f -- "$temporary"
    die "HTTPS adoption state file could not be written"
  fi
  mv -- "$temporary" "$target" \
    || die "HTTPS adoption state file could not be installed"
}

prepare_commands() {
  command -v "$DOCKER_BIN" >/dev/null 2>&1 || die "Docker CLI is unavailable"
  command -v stat >/dev/null 2>&1 || die "stat is unavailable"
  command -v mktemp >/dev/null 2>&1 || die "mktemp is unavailable"
}

acquire_lock() {
  command -v flock >/dev/null 2>&1 || die "flock is unavailable"
  exec 9>"$LOCK_FILE" || die "renewal lock cannot be opened"
  flock -n 9 || die "another certificate renewal is already running"
}

run_preflight() {
  load_optional_adoption_state
  check_image_presence
  check_certificate_volumes
  find_running_proxy
  validate_proxy_mounts
  validate_runtime_https_config
  validate_certificate 0
  validate_nginx_config
}

while (( $# > 0 )); do
  case "$1" in
    preflight|adopt|renew)
      [[ "$ACTION" == renew ]] || die "only one action may be specified"
      ACTION="$1"
      shift
      ;;
    --confirm-adopt)
      ADOPT_CONFIRM=true
      shift
      ;;
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    --help)
      usage
      exit 0
      ;;
    *)
      usage >&2
      die "unknown argument"
      ;;
  esac
done

case "$ACTION" in
  preflight|adopt|renew) ;;
  *) die "unsupported action" ;;
esac
[[ "$ACTION" == adopt || "$ADOPT_CONFIRM" == false ]] \
  || die "--confirm-adopt is valid only for adopt"
[[ "$ACTION" == renew || "$DRY_RUN" == false ]] \
  || die "--dry-run is valid only for renew"
[[ "$ACTION" == adopt && "$ADOPT_CONFIRM" == true ]] \
  || [[ "$ACTION" != adopt ]] \
  || die "adoption requires --confirm-adopt"

prepare_commands
require_state_dir

case "$ACTION" in
  preflight)
    run_preflight
    printf 'HTTPS adoption preflight passed; state files were not changed\n'
    ;;
  adopt)
    acquire_lock
    run_preflight
    if state_path_exists "$APPROVED_DOMAIN_FILE" || state_path_exists "$HTTPS_ENABLED_FILE"; then
      load_approved_domain
      printf 'HTTPS adoption state is already present; no files were changed\n'
      exit 0
    fi
    write_state_file "$APPROVED_DOMAIN_FILE" "$ACTIVE_DOMAIN"
    write_state_file "$HTTPS_ENABLED_FILE" enabled
    load_approved_domain
    printf 'HTTPS adoption state recorded; certificates and Nginx were not changed\n'
    ;;
  renew)
    acquire_lock
    load_approved_domain
    run_preflight

    RENEWAL_MARKER_DIR="$(mktemp -d)" || die "renewal marker directory could not be created"
    chmod 700 "$RENEWAL_MARKER_DIR"
    BEFORE_FINGERPRINT="$(certificate_fingerprint)" || die "current certificate could not be inspected"
    [[ "$BEFORE_FINGERPRINT" =~ ^[0-9a-f]{64}$ ]] || die "current certificate fingerprint is invalid"

    run_certbot

    if [[ "$DRY_RUN" == true ]]; then
      printf 'Certificate renewal dry-run passed; Nginx was not reloaded\n'
      exit 0
    fi

    AFTER_FINGERPRINT="$(certificate_fingerprint)" || die "renewed certificate could not be inspected"
    [[ "$AFTER_FINGERPRINT" =~ ^[0-9a-f]{64}$ ]] || die "renewed certificate fingerprint is invalid"

    if [[ ! -e "$RENEWAL_MARKER_DIR/renewed" ]]; then
      if [[ "$BEFORE_FINGERPRINT" == "$AFTER_FINGERPRINT" ]]; then
        printf 'Certificate is not due for renewal; Nginx was not reloaded\n'
        exit 0
      fi
      die "certificate changed without a successful renewal marker; Nginx was not reloaded"
    fi

    [[ "$BEFORE_FINGERPRINT" != "$AFTER_FINGERPRINT" ]] \
      || die "renewal marker was present but the certificate did not change; Nginx was not reloaded"
    validate_certificate
    validate_nginx_config
    reload_nginx
    printf 'Certificate renewal, validation, and Nginx reload passed\n'
    ;;
esac
