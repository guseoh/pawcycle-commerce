#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
TEST_ROOT="$(mktemp -d)"
SOURCE_FILE="$TEST_ROOT/source.env"
RUNTIME_DIR="$TEST_ROOT/runtime"
trap 'rm -rf -- "$TEST_ROOT"' EXIT

cat >"$SOURCE_FILE" <<'EOF'
PAWCYCLE_DATASOURCE_HOST='10.20.30.40'
PAWCYCLE_DATASOURCE_PORT='3306'
PAWCYCLE_DATASOURCE_DATABASE='pawcycle'
PAWCYCLE_DATASOURCE_SSL_MODE='REQUIRED'
SPRING_DATASOURCE_USERNAME='pawcycle_app'
SPRING_DATASOURCE_PASSWORD='local-validation-only'
PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED='false'
PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE='7'
PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS='12345'
EOF
chmod 600 "$SOURCE_FILE"
"$SCRIPT_DIR/materialize-runtime-env.sh" --source-file "$SOURCE_FILE" --output-dir "$RUNTIME_DIR" >/dev/null

BUNDLE="$(readlink -f "$RUNTIME_DIR/current")"
[[ "$(find "$BUNDLE" -mindepth 1 -maxdepth 1 -printf '%f\n' | sort | tr '\n' ' ')" == '.complete backend.env ' ]]
[[ "$(stat -c '%a' "$RUNTIME_DIR")" == 700 ]]
[[ "$(stat -c '%a' "$BUNDLE")" == 700 ]]
[[ "$(stat -c '%a' "$BUNDLE/backend.env")" == 600 ]]
[[ "$(stat -c '%a' "$BUNDLE/.complete")" == 600 ]]
[[ "$(stat -c '%a' "$RUNTIME_DIR/.materialize.lock")" == 600 ]]
grep -Fxq "SPRING_DATASOURCE_URL='jdbc:mysql://10.20.30.40:3306/pawcycle?sslMode=REQUIRED&serverTimezone=UTC'" "$BUNDLE/backend.env"
! grep -Eq '(^|/)mysql\.env|MYSQL_ROOT_PASSWORD' "$RUNTIME_DIR"/* "$BUNDLE"/* 2>/dev/null

ACTIVE_SCRIPTS=(
  "$SCRIPT_DIR/release-common.sh"
  "$SCRIPT_DIR/deploy.sh"
  "$SCRIPT_DIR/rollback.sh"
  "$SCRIPT_DIR/subscription-automation-control.sh"
  "$SCRIPT_DIR/subscription-automation-preflight.sh"
  "$SCRIPT_DIR/import-demo-catalog.sh"
  "$SCRIPT_DIR/create-production-auth-smoke-member.sh"
)
for file in "${ACTIVE_SCRIPTS[@]}"; do
  ! grep -Eq 'active-mysql-volume|PAWCYCLE_MYSQL_ENV_FILE|PAWCYCLE_MYSQL_VOLUME|MYSQL_DIGEST' "$file"
done
grep -Fq 'RELEASE_SHA=' "$SCRIPT_DIR/release-common.sh"
grep -Fq 'BACKEND_DIGEST=' "$SCRIPT_DIR/release-common.sh"
grep -Fq 'FRONTEND_DIGEST=' "$SCRIPT_DIR/release-common.sh"
grep -Fq 'PROXY_DIGEST=' "$SCRIPT_DIR/release-common.sh"
! grep -Fq 'MYSQL_DIGEST=' "$SCRIPT_DIR/release-common.sh"
grep -Fq 'managed database was not modified by the Application release lifecycle' "$SCRIPT_DIR/deploy.sh"
grep -Fq 'managed database was not modified by the Application release lifecycle' "$SCRIPT_DIR/rollback.sh"

grep -Fq 'DATABASE_PREFLIGHT_TARGET="EXTERNAL_MYSQL"' "$SCRIPT_DIR/subscription-automation-preflight.sh"
grep -Fq -- '--network pawcycle-production-database-egress' "$SCRIPT_DIR/subscription-automation-preflight.sh"
grep -Fq -- '--defaults-extra-file=/run/pawcycle/mysql-client.cnf' "$SCRIPT_DIR/subscription-automation-preflight.sh"
! grep -Eq -- '--env MYSQL_PWD|--password[= ]' "$SCRIPT_DIR/subscription-automation-preflight.sh"
grep -Fq 'external_tls_required' "$SCRIPT_DIR/diagnose-backend-state.sh"
grep -Fq 'DATA_NETWORK="pawcycle-production-database-egress"' "$SCRIPT_DIR/import-demo-catalog.sh"
grep -Fq 'DATA_NETWORK="pawcycle-production-database-egress"' "$SCRIPT_DIR/create-production-auth-smoke-member.sh"

printf 'OPS-OCI-002 application release, rollback, helper, and external-runtime contracts passed\n'
