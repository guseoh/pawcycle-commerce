#!/usr/bin/env bash

set -Eeuo pipefail
set +x

PROJECT_NAME="pawcycle-production"
STATE_DIR="${PAWCYCLE_STATE_DIR:-/opt/pawcycle/state}"
APPROVED_DOMAIN_FILE="$STATE_DIR/https-domain"
HTTPS_ENABLED_FILE="$STATE_DIR/https-enabled"
CERTBOT_WEBROOT_VOLUME="pawcycle-production-certbot-webroot"
LETSENCRYPT_VOLUME="pawcycle-production-letsencrypt"
CERTIFICATE_NAME="pawcycle-production"
CERTBOT_IMAGE="certbot/certbot:v5.7.0@sha256:d07bd043d61d6bee1114235ac12c2e9a5c54b6931b3ccf5e1174d6c8c4afaa95"
MIN_CERT_VALIDITY_SECONDS="86400"
LOCK_FILE="${PAWCYCLE_HTTPS_RENEWAL_LOCK_FILE:-/run/lock/pawcycle-production-https-renewal.lock}"
DRY_RUN=false
APPROVED_DOMAIN=""
PROXY_CONTAINER=""
RENEWAL_MARKER_DIR=""

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage: renew-production-certificate.sh [--dry-run]

Checks the existing Production Let's Encrypt lineage. A live Nginx reload is
performed only when a non-dry renewal succeeds, the certificate changed, the
approved hostname and validity are valid, and the running Nginx config passes.
EOF
}

cleanup() {
  if [[ -n "$RENEWAL_MARKER_DIR" && -d "$RENEWAL_MARKER_DIR" ]]; then
    rm -rf -- "$RENEWAL_MARKER_DIR"
  fi
}
trap cleanup EXIT

validate_domain() {
  [[ "$1" =~ ^([a-z0-9]|[a-z0-9][a-z0-9-]{0,61}[a-z0-9])\.duckdns\.org$ ]] \
    || die "approved HTTPS domain is invalid"
}

require_regular_state_file() {
  local path="$1"
  local description="$2"

  [[ -f "$path" && ! -L "$path" ]] || die "$description is missing or not a regular file"
  [[ "$(stat -c '%a' "$path" 2>/dev/null)" == "600" ]] \
    || die "$description permissions are invalid"
}

load_approved_domain() {
  require_regular_state_file "$HTTPS_ENABLED_FILE" "HTTPS enabled state"
  [[ "$(<"$HTTPS_ENABLED_FILE")" == enabled ]] || die "HTTPS enabled state is invalid"

  require_regular_state_file "$APPROVED_DOMAIN_FILE" "approved HTTPS domain state"
  APPROVED_DOMAIN="$(<"$APPROVED_DOMAIN_FILE")"
  validate_domain "$APPROVED_DOMAIN"
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
    --volume "$LETSENCRYPT_VOLUME:/etc/letsencrypt:ro" \
    "$CERTBOT_IMAGE" -c 'from cryptography import x509
from cryptography.hazmat.primitives import hashes

with open("/etc/letsencrypt/live/pawcycle-production/fullchain.pem", "rb") as source:
    certificate = x509.load_pem_x509_certificate(source.read())
print(certificate.fingerprint(hashes.SHA256()).hex())' 2>/dev/null
}

validate_certificate() {
  "$DOCKER_BIN" run --rm --pull never --platform linux/amd64 \
    --entrypoint python \
    --env "EXPECTED_DOMAIN=$APPROVED_DOMAIN" \
    --env "MIN_VALIDITY_SECONDS=$MIN_CERT_VALIDITY_SECONDS" \
    --volume "$LETSENCRYPT_VOLUME:/etc/letsencrypt:ro" \
    "$CERTBOT_IMAGE" -c 'import datetime
import os
import sys
from cryptography import x509

path = "/etc/letsencrypt/live/pawcycle-production/fullchain.pem"
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

validate_and_reload_nginx() {
  "$DOCKER_BIN" exec "$PROXY_CONTAINER" nginx -t >/dev/null 2>&1 \
    || die "Nginx configuration validation failed; Nginx was not reloaded"
  "$DOCKER_BIN" exec "$PROXY_CONTAINER" nginx -s reload >/dev/null 2>&1 \
    || die "Nginx reload failed; application services were not restarted"
}

DOCKER_BIN="docker"
while (( $# > 0 )); do
  case "$1" in
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

require_regular_state_file "$APPROVED_DOMAIN_FILE" "approved HTTPS domain state"
command -v "$DOCKER_BIN" >/dev/null 2>&1 || die "Docker CLI is unavailable"
command -v stat >/dev/null 2>&1 || die "stat is unavailable"
command -v flock >/dev/null 2>&1 || die "flock is unavailable"
command -v mktemp >/dev/null 2>&1 || die "mktemp is unavailable"

exec 9>"$LOCK_FILE" || die "renewal lock cannot be opened"
flock -n 9 || die "another certificate renewal is already running"

load_approved_domain
check_certificate_volumes
find_running_proxy
validate_proxy_mounts
validate_runtime_https_config

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
validate_and_reload_nginx
printf 'Certificate renewal, validation, and Nginx reload passed\n'
