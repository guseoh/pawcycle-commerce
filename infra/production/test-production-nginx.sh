#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TEST_ROOT="$(mktemp -d)"
CONTAINER_NAME=""
BACKEND_CONTAINER=""
FRONTEND_CONTAINER=""
NETWORK_NAME=""
REPLACEMENT_BACKEND=""
REPLACEMENT_FRONTEND=""

cleanup() {
  for replacement in "$REPLACEMENT_BACKEND" "$REPLACEMENT_FRONTEND"; do
    if [[ -n "$replacement" ]]; then
      docker rm --force "$replacement" >/dev/null 2>&1 || true
    fi
  done
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
  '    add_header X-Seen-Request-Id $http_x_request_id always;' \
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

# Exercise the Release CLI's stricter, capability-free nginx -t on root-owned
# disposable fixtures, generated certificates and unique test volumes.
if (( EUID == 0 )); then
  python3 "$SCRIPT_DIR/test-oci-release-nginx.py"
else
  sudo "$(command -v python3)" "$SCRIPT_DIR/test-oci-release-nginx.py"
fi

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
# Explicit addresses below require an explicitly configured subnet (Engine 28).
# A private disposable /24 avoids reusing either old service address.
docker network create --subnet "172.30.$(( RANDOM % 200 + 20 )).0/24" "$NETWORK_NAME" >/dev/null
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
  actual_value="$(grep -i "^${header_name}:" "$header_file" | head -n 1 | cut -d: -f2- | tr -d '\r' | sed 's/^ *//')"
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

GENERIC_RATE_LIMITED_BODY='{"code":"RATE_LIMITED","message":"요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.","fieldErrors":[]}'

public_code() {
  local method="$1"
  local path="$2"
  local output="$3"
  local -a body=()
  if [[ "$method" == POST ]]; then
    body=(--header 'Content-Type: application/json' --data '{}')
  fi
  curl --silent --show-error --insecure --max-time 5 \
    --resolve "$TEST_DOMAIN:$HTTPS_PORT:127.0.0.1" \
    --request "$method" "${body[@]}" \
    --dump-header "$output.headers" --output "$output.body" --write-out '%{http_code}' \
    "https://$TEST_DOMAIN:$HTTPS_PORT$path"
}

assert_generic_rate_limited() {
  local output="$1"
  [[ "$(<"$output.body")" == "$GENERIC_RATE_LIMITED_BODY" ]]
  grep -qi '^Content-Type: application/json' "$output.headers"
  assert_public_header "$output.headers" 'Strict-Transport-Security' 'max-age=31536000; includeSubDomains'
  assert_public_header "$output.headers" 'X-Content-Type-Options' 'nosniff'
  assert_public_header "$output.headers" 'X-Frame-Options' 'DENY'
}

# Sends attempts as fast as possible from one client address. nodelay admits
# burst+1 at once; a few more may pass as the bucket refills during the loop,
# bounded by the elapsed time. Below the limit every request reaches backend.
probe_rate_limit() {
  local zone="$1" method="$2" path="$3" attempts="$4" burst="$5" per_minute="$6"
  local success=0 limited=0 first_limited=0 code started elapsed refill attempt output
  started="$(date +%s.%N)"
  for (( attempt = 1; attempt <= attempts; attempt++ )); do
    output="$TEST_ROOT/rate-$zone-$attempt"
    code="$(public_code "$method" "$path" "$output")"
    case "$code" in
      200)
        [[ "$(<"$output.body")" == '{"source":"backend"}' ]]
        success=$((success + 1))
        ;;
      429)
        assert_generic_rate_limited "$output"
        limited=$((limited + 1))
        if (( first_limited == 0 )); then first_limited="$attempt"; fi
        ;;
      *)
        false
        ;;
    esac
  done
  elapsed="$(awk -v s="$started" -v e="$(date +%s.%N)" 'BEGIN { printf "%.2f", e - s }')"
  refill="$(awk -v t="$elapsed" -v r="$per_minute" 'BEGIN { printf "%d", t * r / 60 + 1 }')"
  (( first_limited >= burst + 2 ))
  (( success >= burst + 1 ))
  (( success <= burst + 1 + refill ))
  (( limited > 0 ))
  printf 'rate-limit %s: %s %s attempts=%s passed=%s limited=%s first_429_at=%s elapsed=%ss\n' \
    "$zone" "$method" "$path" "$attempts" "$success" "$limited" "$first_limited" "$elapsed"
}

expect_code() {
  local expected="$1" method="$2" path="$3" output
  output="$TEST_ROOT/expect-$RANDOM$RANDOM"
  [[ "$(public_code "$method" "$path" "$output")" == "$expected" ]]
  if [[ "$expected" == 429 ]]; then
    assert_generic_rate_limited "$output"
  fi
}

probe_rate_limit csrf GET /api/auth/csrf 40 20 30
probe_rate_limit search GET '/api/products?q=probe' 50 30 60
# Plain browsing and an empty term are never keyed; the %71 encoding of q is.
expect_code 200 GET /api/products
expect_code 200 GET '/api/products?q=&page=1'
expect_code 429 GET '/api/products?page=0&%71=probe'
probe_rate_limit checkout POST /api/checkout 20 10 10
probe_rate_limit payment POST /api/payments/toss/confirm 20 10 10
# Billing prepare/complete share the exhausted payment budget; status reads do not.
expect_code 429 POST /api/payment-methods/toss/billing/prepare
expect_code 429 POST /api/payment-methods/toss/billing/complete
expect_code 200 GET /api/payment-methods/toss/billing
probe_rate_limit interaction POST /api/interactions 90 60 120
probe_rate_limit ai GET '/api/products/compare?productIds=1,2' 45 30 30
expect_code 429 GET /api/products/7/reviews/summary
expect_code 429 GET '/api/recommendations/products?petId=1'
expect_code 200 GET /api/products/7
expect_code 200 GET /api/products/7/reviews
expect_code 200 GET /api/cart

# The container-only listener carries no request limits.
for (( attempt = 1; attempt <= 25; attempt++ )); do
  [[ "$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 5 \
    "http://127.0.0.1:$INTERNAL_PORT/api/auth/csrf")" == "200" ]]
done

# R11: a payment provider redirect keeps its query out of every log stream, the
# access log is one JSON object per request and the request id is forwarded.
PAYMENT_KEY_PROBE="r11_payment_key_$RANDOM$RANDOM"
[[ "$(curl --silent --show-error --insecure --max-time 5 \
  --resolve "$TEST_DOMAIN:$HTTPS_PORT:127.0.0.1" --header 'X-Request-ID: r11-probe.1' \
  --output /dev/null --write-out '%{http_code}' \
  "https://$TEST_DOMAIN:$HTTPS_PORT/checkout/success?paymentKey=$PAYMENT_KEY_PROBE&orderId=r11-order&amount=1000")" \
  == "200" ]]
REQUEST_ID_HEADERS="$TEST_ROOT/request-id.headers"
curl --silent --show-error --insecure --max-time 5 \
  --resolve "$TEST_DOMAIN:$HTTPS_PORT:127.0.0.1" --header 'X-Request-ID: r11-probe.2' \
  --dump-header "$REQUEST_ID_HEADERS" --output /dev/null \
  "https://$TEST_DOMAIN:$HTTPS_PORT/api/orders/1?token=$PAYMENT_KEY_PROBE"
assert_public_header "$REQUEST_ID_HEADERS" 'X-Seen-Request-Id' 'r11-probe.2'
curl --silent --show-error --insecure --max-time 5 \
  --resolve "$TEST_DOMAIN:$HTTPS_PORT:127.0.0.1" --header 'X-Request-ID: unsafe id;drop' \
  --dump-header "$REQUEST_ID_HEADERS" --output /dev/null \
  "https://$TEST_DOMAIN:$HTTPS_PORT/api/orders/2"
[[ "$(grep -i '^X-Seen-Request-Id:' "$REQUEST_ID_HEADERS" | tr -d '\r' | cut -d' ' -f2)" =~ ^[0-9a-f]{32}$ ]]
docker logs "$CONTAINER_NAME" > "$TEST_ROOT/proxy.stdout" 2> "$TEST_ROOT/proxy.stderr"
if grep -q -e "$PAYMENT_KEY_PROBE" -e 'paymentKey' "$TEST_ROOT/proxy.stdout" "$TEST_ROOT/proxy.stderr"; then
  printf 'payment key query reached a proxy log stream\n' >&2
  false
fi
python3 - "$TEST_ROOT/proxy.stdout" <<'PY'
import json, sys
lines = [line for line in open(sys.argv[1], encoding="utf-8").read().splitlines() if line.strip()]
# The image entrypoint also prints start-up notices to stdout; no request line
# may appear in any other (e.g. combined) format.
assert not [line for line in lines if not line.startswith("{") and " HTTP/" in line]
records = [json.loads(line) for line in lines if line.startswith("{")]
assert len(records) > 100, len(records)
keys = {"time", "request_id", "method", "uri", "status", "bytes", "request_time", "upstream_time", "remote_addr"}
assert all(set(record) == keys for record in records)
assert all("?" not in record["uri"] and record["request_id"] for record in records)
assert any(record["uri"] == "/checkout/success" and record["request_id"] == "r11-probe.1" and
           record["status"] == 200 for record in records)
assert any(record["uri"] == "/api/orders/1" and record["request_id"] == "r11-probe.2" for record in records)
assert any(record["uri"] == "/api/products" and record["status"] == 429 for record in records)
PY

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

# Keep the original processes alive but remove their DNS aliases/IPs. Explicit
# different IPs make this a DNS regression, not an accidental address reuse.
old_backend_ip="$(docker inspect --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$BACKEND_CONTAINER")"
old_frontend_ip="$(docker inspect --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$FRONTEND_CONTAINER")"
new_backend_ip="${old_backend_ip%.*}.200"
new_frontend_ip="${old_frontend_ip%.*}.201"
[[ "$new_backend_ip" != "$old_backend_ip" && "$new_frontend_ip" != "$old_frontend_ip" ]]
docker network disconnect "$NETWORK_NAME" "$BACKEND_CONTAINER"
docker network disconnect "$NETWORK_NAME" "$FRONTEND_CONTAINER"
sed 's/"source":"backend"/"source":"backend-replaced"/' "$BACKEND_CONFIG" > "$TEST_ROOT/backend-replaced.conf"
sed 's/mock frontend/frontend-replaced/' "$FRONTEND_CONFIG" > "$TEST_ROOT/frontend-replaced.conf"
REPLACEMENT_BACKEND="${BACKEND_CONTAINER}-replacement"
REPLACEMENT_FRONTEND="${FRONTEND_CONTAINER}-replacement"
docker run --detach --name "$REPLACEMENT_BACKEND" --network "$NETWORK_NAME" \
  --ip "$new_backend_ip" --network-alias backend \
  --volume "$TEST_ROOT/backend-replaced.conf:/etc/nginx/conf.d/default.conf:ro" "$PROXY_IMAGE" >/dev/null
docker run --detach --name "$REPLACEMENT_FRONTEND" --network "$NETWORK_NAME" \
  --ip "$new_frontend_ip" --network-alias frontend \
  --volume "$TEST_ROOT/frontend-replaced.conf:/etc/nginx/conf.d/default.conf:ro" "$PROXY_IMAGE" >/dev/null
resolved=false
for (( attempt = 0; attempt < 30; attempt++ )); do
  backend_body="$(curl --silent --max-time 2 "http://127.0.0.1:$INTERNAL_PORT/api/products" || true)"
  frontend_body="$(curl --silent --max-time 2 "http://127.0.0.1:$INTERNAL_PORT/products" || true)"
  if [[ "$backend_body" == '{"source":"backend-replaced"}' && "$frontend_body" == frontend-replaced ]]; then
    resolved=true
    break
  fi
  sleep 1
done
[[ "$resolved" == true ]]
[[ "$(curl --silent --show-error --insecure --max-time 5 \
  --resolve "$TEST_DOMAIN:$HTTPS_PORT:127.0.0.1" "https://$TEST_DOMAIN:$HTTPS_PORT/api/products")" \
  == '{"source":"backend-replaced"}' ]]
docker exec "$CONTAINER_NAME" nginx -t >/dev/null

printf 'Nginx hostname, TLS, security, rate-limit, access-log and changed-IP DNS regressions passed\n'
