#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf -- "$TEST_ROOT"' EXIT

PROXY_IMAGE="nginx:1.30.3-alpine3.23@sha256:0d3b80406a13a767339fbe2f41406d6c7da727ab89cf8fae399e81f780f814d1"
LETSENCRYPT_DIR="$TEST_ROOT/letsencrypt"
CERTIFICATE_DIR="$LETSENCRYPT_DIR/live/pawcycle-production"
CHALLENGE_DIR="$TEST_ROOT/challenge"
mkdir -p "$CERTIFICATE_DIR" "$CHALLENGE_DIR"

openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout "$CERTIFICATE_DIR/privkey.pem" \
  -out "$CERTIFICATE_DIR/fullchain.pem" \
  -days 1 -subj '/CN=localhost' >/dev/null 2>&1

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
validate_config "$SCRIPT_DIR/nginx.https.conf"

printf 'OPS-011 Nginx configuration tests passed\n'
