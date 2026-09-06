#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
TEST_ROOT="$(mktemp -d)"
BACKEND_ENV="$TEST_ROOT/backend.env"
trap 'rm -rf -- "$TEST_ROOT"' EXIT

cat >"$BACKEND_ENV" <<'EOF'
PAWCYCLE_DATASOURCE_HOST='db.example.com'
PAWCYCLE_DATASOURCE_PORT='3306'
PAWCYCLE_DATASOURCE_DATABASE='pawcycle'
PAWCYCLE_DATASOURCE_SSL_MODE='REQUIRED'
SPRING_DATASOURCE_URL='jdbc:mysql://db.example.com:3306/pawcycle?sslMode=REQUIRED&serverTimezone=UTC'
SPRING_DATASOURCE_USERNAME='pawcycle_app'
SPRING_DATASOURCE_PASSWORD='local-validation-only'
PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED='false'
PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE='7'
PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS='12345'
EOF
chmod 600 "$BACKEND_ENV"

CONFIG_JSON="$TEST_ROOT/compose.json"
RELEASE_SHA="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" \
BACKEND_IMAGE="ghcr.io/example/pawcycle-commerce-backend" \
FRONTEND_IMAGE="ghcr.io/example/pawcycle-commerce-frontend" \
PAWCYCLE_BACKEND_ENV_FILE="$BACKEND_ENV" \
PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED=false \
PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE=7 \
PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS=12345 \
PAWCYCLE_EDGE_NETWORK="pawcycle-test-edge" \
PAWCYCLE_APP_NETWORK="pawcycle-test-app" \
PAWCYCLE_DATABASE_EGRESS_NETWORK="pawcycle-test-database-egress" \
PAWCYCLE_CERTBOT_WEBROOT_VOLUME="pawcycle-test-certbot-webroot" \
PAWCYCLE_LETSENCRYPT_VOLUME="pawcycle-test-letsencrypt" \
PAWCYCLE_NGINX_CONFIG="$SCRIPT_DIR/nginx.conf" \
  docker compose --project-name pawcycle-compose-contract --file "$SCRIPT_DIR/compose.yaml" config --format json >"$CONFIG_JSON"

python3 - "$CONFIG_JSON" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    config = json.load(handle)
services = config.get("services", {})
assert set(services) == {"backend", "frontend", "proxy"}
assert "mysql" not in services
assert "mysql-data" not in config.get("volumes", {})
assert "data" not in config.get("networks", {})
assert set(services["backend"].get("networks", {})) == {"app", "database-egress"}
assert set(services["frontend"].get("networks", {})) == {"app"}
assert set(services["proxy"].get("networks", {})) == {"edge", "app"}
assert config["networks"]["database-egress"].get("internal") is not True
for name, service in services.items():
    assert service.get("healthcheck"), name
    assert service.get("read_only") is True, name
    assert "no-new-privileges:true" in service.get("security_opt", []), name
    assert service.get("logging", {}).get("driver") == "json-file", name
assert not services["backend"].get("ports")
assert not services["frontend"].get("ports")
published = {(item.get("published"), item.get("target")) for item in services["proxy"]["ports"]}
assert published == {("80", 80), ("443", 443)}
PY

if grep -nE 'mysql-data|PAWCYCLE_MYSQL_|depends_on:.*mysql|^[[:space:]]+mysql:' "$SCRIPT_DIR/compose.yaml"; then
  printf 'Production Compose retained a local MySQL ownership contract\n' >&2
  exit 1
fi

printf 'OPS-OCI-002 Production Compose service, network, security, and external-DB contract passed\n'
