#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TEST_ROOT="$(mktemp -d)"
CONTAINER_NAME=""
BACKEND_CONTAINER=""
FRONTEND_CONTAINER=""
NETWORK_NAME=""

cleanup() {
  if [[ -n "$CONTAINER_NAME" ]]; then
    docker rm --force "$CONTAINER_NAME" >/dev/null 2>&1 || true
  fi
  if [[ -n "$BACKEND_CONTAINER" ]]; then
    docker rm --force "$BACKEND_CONTAINER" >/dev/null 2>&1 || true
  fi
  if [[ -n "$FRONTEND_CONTAINER" ]]; then
    docker rm --force "$FRONTEND_CONTAINER" >/dev/null 2>&1 || true
  fi
  if [[ -n "$NETWORK_NAME" ]]; then
    docker network rm "$NETWORK_NAME" >/dev/null 2>&1 || true
  fi
  rm -rf -- "$TEST_ROOT"
}
trap cleanup EXIT
trap 'status=$?; printf "AUTH-005 Nginx contract failed at line %s: %s (public_success=%s public_rate_limited=%s internal_success=%s)\n" "$LINENO" "$BASH_COMMAND" "${public_success_count:-n/a}" "${public_rate_limited_count:-n/a}" "${internal_success_count:-n/a}" >&2; exit "$status"' ERR

PROXY_IMAGE="nginx:1.30.3-alpine3.23@sha256:0d3b80406a13a767339fbe2f41406d6c7da727ab89cf8fae399e81f780f814d1"
CERTBOT_IMAGE="certbot/certbot:v5.8.0@sha256:398c47284a6d6782825be71685f677ef3a1e65b8b5c278a8b1e99f6da84b4eb9"
TEST_DOMAIN="ops011-nginx-test.duckdns.org"
LETSENCRYPT_DIR="$TEST_ROOT/letsencrypt"
CERTIFICATE_DIR="$LETSENCRYPT_DIR/live/pawcycle.duckdns.org"
CHALLENGE_DIR="$TEST_ROOT/challenge"
HTTPS_CONFIG="$TEST_ROOT/nginx.https.conf"
BACKEND_CONFIG="$TEST_ROOT/backend.conf"
FRONTEND_CONFIG="$TEST_ROOT/frontend.conf"
mkdir -p "$CERTIFICATE_DIR" "$CHALLENGE_DIR/.well-known/acme-challenge"

openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout "$CERTIFICATE_DIR/privkey.pem" \
  -out "$CERTIFICATE_DIR/fullchain.pem" \
  -days 2 -subj "/CN=$TEST_DOMAIN" \
  -addext "subjectAltName=DNS:$TEST_DOMAIN" >/dev/null 2>&1
sed "s/__PAWCYCLE_DOMAIN__/$TEST_DOMAIN/g" "$SCRIPT_DIR/nginx.https.conf" > "$HTTPS_CONFIG"
printf 'pawcycle-acme-probe' > "$CHALLENGE_DIR/.well-known/acme-challenge/probe"
printf '%s\n' \
  'server {' \
  '    listen 8080;' \
  '    access_log /dev/stdout main;' \
  '    add_header Strict-Transport-Security "upstream-hsts" always;' \
  '    add_header X-Content-Type-Options "upstream-nosniff" always;' \
  '    add_header X-Frame-Options "upstream-frame" always;' \
  '    add_header X-XSS-Protection "upstream-xss" always;' \
  '    add_header Referrer-Policy "upstream-referrer" always;' \
  '    location / {' \
  '        default_type application/json;' \
  '        return 200 '\''{"source":"backend"}'\'';' \
  '    }' \
  '}' > "$BACKEND_CONFIG"
printf '%s\n' \
  'server {' \
  '    listen 3000;' \
  '    add_header Strict-Transport-Security "upstream-hsts" always;' \
  '    add_header X-Content-Type-Options "upstream-nosniff" always;' \
  '    add_header X-Frame-Options "upstream-frame" always;' \
  '    add_header X-XSS-Protection "upstream-xss" always;' \
  '    add_header Referrer-Policy "upstream-referrer" always;' \
  '    location = /upstream-error {' \
  '        default_type text/plain;' \
  '        return 503 '\''mock upstream error'\'';' \
  '    }' \
  '    location / {' \
  '        default_type text/plain;' \
  '        return 200 '\''mock frontend'\'';' \
  '    }' \
  '}' > "$FRONTEND_CONFIG"

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
with open("/etc/letsencrypt/live/pawcycle.duckdns.org/fullchain.pem", "rb") as source:
    certificate=x509.load_pem_x509_certificate(source.read())
san=certificate.extensions.get_extension_for_class(x509.SubjectAlternativeName).value
assert len(san) == 1
assert set(san.get_values_for_type(x509.DNSName)) == {os.environ["EXPECTED_DOMAIN"]}
assert certificate.not_valid_after_utc > datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=1)'

NETWORK_NAME="pawcycle-nginx-test-${RANDOM}-$$"
BACKEND_CONTAINER="pawcycle-nginx-backend-${RANDOM}-$$"
FRONTEND_CONTAINER="pawcycle-nginx-frontend-${RANDOM}-$$"
docker network create "$NETWORK_NAME" >/dev/null
docker run --detach --name "$BACKEND_CONTAINER" \
  --network "$NETWORK_NAME" \
  --network-alias backend \
  --volume "$BACKEND_CONFIG:/etc/nginx/conf.d/default.conf:ro" \
  --read-only \
  --tmpfs /var/cache/nginx:size=16m,mode=0755 \
  --tmpfs /var/run:size=1m,mode=0755 \
  "$PROXY_IMAGE" >/dev/null
docker run --detach --name "$FRONTEND_CONTAINER" \
  --network "$NETWORK_NAME" \
  --network-alias frontend \
  --volume "$FRONTEND_CONFIG:/etc/nginx/conf.d/default.conf:ro" \
  --read-only \
  --tmpfs /var/cache/nginx:size=16m,mode=0755 \
  --tmpfs /var/run:size=1m,mode=0755 \
  "$PROXY_IMAGE" >/dev/null

CONTAINER_NAME="pawcycle-nginx-test-${RANDOM}-$$"
docker run --detach --name "$CONTAINER_NAME" \
  --network "$NETWORK_NAME" \
  --publish 127.0.0.1::80 \
  --publish 127.0.0.1::443 \
  --publish 127.0.0.1::8081 \
  --volume "$HTTPS_CONFIG:/etc/nginx/conf.d/default.conf:ro" \
  --volume "$LETSENCRYPT_DIR:/etc/letsencrypt:ro" \
  --volume "$CHALLENGE_DIR:/var/www/certbot:ro" \
  --read-only \
  --tmpfs /var/cache/nginx:size=16m,mode=0755 \
  --tmpfs /var/run:size=1m,mode=0755 \
  "$PROXY_IMAGE" >/dev/null

HTTP_BINDING="$(docker port "$CONTAINER_NAME" 80/tcp)"
HTTP_PORT="${HTTP_BINDING##*:}"
HTTPS_BINDING="$(docker port "$CONTAINER_NAME" 443/tcp)"
HTTPS_PORT="${HTTPS_BINDING##*:}"
INTERNAL_BINDING="$(docker port "$CONTAINER_NAME" 8081/tcp)"
INTERNAL_PORT="${INTERNAL_BINDING##*:}"
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

assert_public_header() {
  local header_file="$1"
  local header_name="$2"
  local expected_value="$3"
  local count
  local actual_value

  count="$(grep -i -c "^${header_name}:" "$header_file" || true)"
  [[ "$count" == "1" ]]
  actual_value="$(grep -i "^${header_name}:" "$header_file" | head -n 1 | cut -d: -f2- | sed 's/^ *//')"
  [[ "$actual_value" == "$expected_value" ]]
}

PUBLIC_HEADERS="$TEST_ROOT/public.headers"
PUBLIC_CODE="$(curl --silent --show-error --insecure --max-time 5 \
  --resolve "$TEST_DOMAIN:$HTTPS_PORT:127.0.0.1" \
  --dump-header "$PUBLIC_HEADERS" --output "$TEST_ROOT/public.body" \
  --write-out '%{http_code}' "https://$TEST_DOMAIN:$HTTPS_PORT/products")"
[[ "$PUBLIC_CODE" == "200" ]]
assert_public_header "$PUBLIC_HEADERS" 'Strict-Transport-Security' 'max-age=31536000; includeSubDomains'
assert_public_header "$PUBLIC_HEADERS" 'X-Content-Type-Options' 'nosniff'
assert_public_header "$PUBLIC_HEADERS" 'X-Frame-Options' 'DENY'
assert_public_header "$PUBLIC_HEADERS" 'X-XSS-Protection' '0'
assert_public_header "$PUBLIC_HEADERS" 'Referrer-Policy' 'strict-origin-when-cross-origin'

ERROR_HEADERS="$TEST_ROOT/error.headers"
ERROR_CODE="$(curl --silent --show-error --insecure --max-time 5 \
  --resolve "$TEST_DOMAIN:$HTTPS_PORT:127.0.0.1" \
  --dump-header "$ERROR_HEADERS" --output "$TEST_ROOT/error.body" \
  --write-out '%{http_code}' "https://$TEST_DOMAIN:$HTTPS_PORT/upstream-error")"
[[ "$ERROR_CODE" == "503" ]]
assert_public_header "$ERROR_HEADERS" 'Strict-Transport-Security' 'max-age=31536000; includeSubDomains'
assert_public_header "$ERROR_HEADERS" 'X-Content-Type-Options' 'nosniff'
assert_public_header "$ERROR_HEADERS" 'X-Frame-Options' 'DENY'
assert_public_header "$ERROR_HEADERS" 'X-XSS-Protection' '0'
assert_public_header "$ERROR_HEADERS" 'Referrer-Policy' 'strict-origin-when-cross-origin'

RATE_LIMITED_BODY='{"code":"RATE_LIMITED","message":"로그인 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.","fieldErrors":[]}'
public_success_count=0
public_rate_limited_count=0

for (( attempt = 1; attempt <= 15; attempt++ )); do
  login_code="$(curl --silent --show-error --insecure --max-time 5 \
    --resolve "$TEST_DOMAIN:$HTTPS_PORT:127.0.0.1" \
    --request POST --header 'Content-Type: application/json' --data '{}' \
    --dump-header "$TEST_ROOT/login-$attempt.headers" \
    --output "$TEST_ROOT/login-$attempt.body" --write-out '%{http_code}' \
    "https://$TEST_DOMAIN:$HTTPS_PORT/api/auth/login")"
  case "$login_code" in
    200)
      [[ "$(<"$TEST_ROOT/login-$attempt.body")" == '{"source":"backend"}' ]]
      public_success_count=$((public_success_count + 1))
      ;;
    429)
      [[ "$(<"$TEST_ROOT/login-$attempt.body")" == "$RATE_LIMITED_BODY" ]]
      grep -qi '^Content-Type: application/json' "$TEST_ROOT/login-$attempt.headers"
      assert_public_header "$TEST_ROOT/login-$attempt.headers" 'Strict-Transport-Security' 'max-age=31536000; includeSubDomains'
      assert_public_header "$TEST_ROOT/login-$attempt.headers" 'X-Content-Type-Options' 'nosniff'
      assert_public_header "$TEST_ROOT/login-$attempt.headers" 'X-Frame-Options' 'DENY'
      assert_public_header "$TEST_ROOT/login-$attempt.headers" 'X-XSS-Protection' '0'
      assert_public_header "$TEST_ROOT/login-$attempt.headers" 'Referrer-Policy' 'strict-origin-when-cross-origin'
      public_rate_limited_count=$((public_rate_limited_count + 1))
      ;;
    *)
      false
      ;;
  esac
done

(( public_success_count > 0 ))
(( public_rate_limited_count > 0 ))

API_CODE="$(curl --silent --show-error --insecure --max-time 5 \
  --resolve "$TEST_DOMAIN:$HTTPS_PORT:127.0.0.1" \
  --output "$TEST_ROOT/api.body" --write-out '%{http_code}' \
  "https://$TEST_DOMAIN:$HTTPS_PORT/api/products")"
[[ "$API_CODE" == "200" ]]
[[ "$(<"$TEST_ROOT/api.body")" == '{"source":"backend"}' ]]

internal_success_count=0
for (( attempt = 1; attempt <= 16; attempt++ )); do
  internal_login_code="$(curl --silent --show-error --max-time 5 \
    --request POST --header 'Content-Type: application/json' --data '{}' \
    --output "$TEST_ROOT/internal-login-$attempt.body" --write-out '%{http_code}' \
    "http://127.0.0.1:$INTERNAL_PORT/api/auth/login")"
  [[ "$internal_login_code" == "200" ]]
  [[ "$(<"$TEST_ROOT/internal-login-$attempt.body")" == '{"source":"backend"}' ]]
  internal_success_count=$((internal_success_count + 1))
done

backend_login_count="$(docker logs "$BACKEND_CONTAINER" 2>&1 | grep -c 'POST /api/auth/login' || true)"
expected_backend_login_count=$((public_success_count + internal_success_count))
[[ "$backend_login_count" == "$expected_backend_login_count" ]]

printf 'AUTH-005 Nginx hostname, certificate, edge security, and login rate-limit tests passed\n'
