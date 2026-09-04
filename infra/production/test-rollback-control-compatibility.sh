#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
COMMON="$SCRIPT_DIR/release-common.sh"
ROLLBACK="$SCRIPT_DIR/rollback.sh"
DEPLOY="$SCRIPT_DIR/deploy.sh"

bash -n "$COMMON" "$ROLLBACK" "$DEPLOY"
! grep -Eq 'active-mysql-volume|PAWCYCLE_MYSQL_ENV_FILE|PAWCYCLE_MYSQL_VOLUME|MYSQL_DIGEST|compose (start|stop|rm).*mysql' "$COMMON" "$ROLLBACK" "$DEPLOY"
grep -Fq 'acquire_release_lock' "$ROLLBACK"
grep -Fq 'TARGET_SHA="$(read_state_sha previous-sha)"' "$ROLLBACK"
grep -Fq 'require_no_migration_boundary_rollback' "$ROLLBACK"
grep -Fq 'managed database was not modified by the Application release lifecycle' "$ROLLBACK"
grep -Fq 'previous-contract-sha' "$ROLLBACK"
grep -Fq 'compose stop proxy frontend backend' "$DEPLOY"

printf 'OPS-OCI-002 rollback control compatibility and managed-DB boundary tests passed\n'
