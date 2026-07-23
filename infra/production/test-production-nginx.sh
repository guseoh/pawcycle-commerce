#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TEST_ROOT="$(mktemp -d)"
CONTAINER_NAME=""

cleanup() {
  if [[ -n "$CONTAINER_NAME" ]]; then
    docker rm --force "$CONTAINER_NAME" >/dev/null 2>&1 || true
  fi
  rm -rf -- "$TEST_ROOT"
}
trap cleanup EXIT

PROXY_IMAGE="nginx:1.30.3-alpine3.23@sha256:0d3b80406a13a767339fbe2f41406d6c7da727ab89cf8fae399e81f780f814d1"
CERTBOT_IMAGE="certbot/certbot:v5.7.0@sha256:d07bd043d61d6bee1114235ac12c2e9a5c54b6931b3ccf5e1174d6c8c4afaa95"
TEST_DOMAIN="ops011-nginx-test.duckdns.org"
LETSENCRYPT_DIR="$TEST_ROOT/letsencrypt"
CERTIFICATE_DIR="$LETSENCRYPT_DIR/live/pawcycle-production"
CHALLENGE_DIR="$TEST_ROOT/challenge"
HTTPS_CONFIG="$TEST_ROOT/nginx.https.conf"
mkdir -p "$CERTIFICATE_DIR" "$CHALLENGE_DIR/.well-known/acme-challenge"

openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout "$CERTIFICATE_DIR/privkey.pem" \
  -out "$CERTIFICATE_DIR/fullchain.pem" \
  -days 2 -subj "/CN=$TEST_DOMAIN" \
  -addext "subjectAltName=DNS:$TEST_DOMAIN" >/dev/null 2>&1
sed "s/__PAWCYCLE_DOMAIN__/$TEST_DOMAIN/g" "$SCRIPT_DIR/nginx.https.conf" > "$HTTPS_CONFIG"
printf 'pawcycle-acme-probe' > "$CHALLENGE_DIR/.well-known/acme-challenge/probe"

validate_config() {
  local config="$1"

  docker run --rm \
    --add-host backend:127.0.0.1 \
    --add-host frontend:127.0.0.1 \
    --volume "$config:/etc/nginx/conf.d/default.conf:ro" \
    --volume "$LETSENCRYPT_DIR:/etc/letsencrypt:ro" \
    --volume "$CHALLENGE_DIR:/var/www/certbot:ro" \
    "$PROXY_IMAGE" nginx -t >/dev/null
}

validate_config "$SCRIPT_DIR/nginx.conf"
validate_config "$HTTPS_CONFIG"

docker run --rm --platform linux/amd64 \
  --entrypoint python \
  --env EXPECTED_DOMAIN="$TEST_DOMAIN" \
  --volume "$LETSENCRYPT_DIR:/etc/letsencrypt:ro" \
  "$CERTBOT_IMAGE" -c \
  'import datetime, os
from cryptography import x509
with open("/etc/letsencrypt/live/pawcycle-production/fullchain.pem", "rb") as source:
    certificate=x509.load_pem_x509_certificate(source.read())
san=certificate.extensions.get_extension_for_class(x509.SubjectAlternativeName).value
assert len(san) == 1
assert set(san.get_values_for_type(x509.DNSName)) == {os.environ["EXPECTED_DOMAIN"]}
assert certificate.not_valid_after_utc > datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=1)'

CONTAINER_NAME="pawcycle-nginx-test-${RANDOM}-$$"
docker run --detach --name "$CONTAINER_NAME" \
  --add-host backend:127.0.0.1 \
  --add-host frontend:127.0.0.1 \
  --publish 127.0.0.1::80 \
  --volume "$HTTPS_CONFIG:/etc/nginx/conf.d/default.conf:ro" \
  --volume "$LETSENCRYPT_DIR:/etc/letsencrypt:ro" \
  --volume "$CHALLENGE_DIR:/var/www/certbot:ro" \
  --read-only \
  --tmpfs /var/cache/nginx:size=16m,mode=0755 \
  --tmpfs /var/run:size=1m,mode=0755 \
  "$PROXY_IMAGE" >/dev/null

HTTP_BINDING="$(docker port "$CONTAINER_NAME" 80/tcp)"
HTTP_PORT="${HTTP_BINDING##*:}"
for (( attempt = 0; attempt < 20; attempt++ )); do
  if curl --silent --output /dev/null --max-time 2 \
    --header "Host: $TEST_DOMAIN" "http://127.0.0.1:$HTTP_PORT/.well-known/acme-challenge/probe"; then
    break
  fi
  sleep 1
done

[[ "$(curl --fail --silent --show-error --max-time 5 \
  --header "Host: $TEST_DOMAIN" "http://127.0.0.1:$HTTP_PORT/.well-known/acme-challenge/probe")" \
  == "pawcycle-acme-probe" ]]
[[ "$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 5 \
  --header "Host: $TEST_DOMAIN" "http://127.0.0.1:$HTTP_PORT/products")" == "301" ]]
[[ "$(curl --silent --output /dev/null --write-out '%{redirect_url}' --max-time 5 \
  --header "Host: $TEST_DOMAIN" "http://127.0.0.1:$HTTP_PORT/products")" \
  == "https://$TEST_DOMAIN/products" ]]
unknown_code="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 5 \
  --header 'Host: unknown.example.invalid' "http://127.0.0.1:$HTTP_PORT/products" || true)"
[[ "$unknown_code" == "000" || "$unknown_code" == "400" || "$unknown_code" == "404" ]]

printf 'OPS-011 Nginx hostname, certificate API, and configuration tests passed\n'
