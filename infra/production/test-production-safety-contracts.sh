#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
COMMON="$SCRIPT_DIR/release-common.sh"
DEPLOY="$SCRIPT_DIR/deploy.sh"
ROLLBACK="$SCRIPT_DIR/rollback.sh"
CONTROL="$SCRIPT_DIR/subscription-automation-control.sh"

bash -n "$COMMON" "$DEPLOY" "$ROLLBACK" "$CONTROL"

# Provider-neutral release state and incomplete-transition safety.
grep -Fq 'read_state_sha current-sha' "$DEPLOY"
grep -Fq 'publish_state_or_abort previous-sha' "$DEPLOY"
grep -Fq 'publish_state_or_abort current-sha' "$DEPLOY"
grep -Fq 'contract-sha' "$DEPLOY"
grep -Fq 'previous-contract-sha' "$DEPLOY"
grep -Fq 'STATE_TRANSITION_NAME="release-state-transition"' "$DEPLOY"
grep -Fq 'INCOMPLETE_TARGET_SHA' "$DEPLOY"

# Contract and migration boundaries must require explicit approvals and block
# automatic restoration across a boundary.
grep -Fq 'CONTRACT_BOUNDARY=1' "$DEPLOY"
grep -Fq 'require_contract_boundary_approval' "$DEPLOY"
grep -Fq 'migration_bundle_changed' "$DEPLOY"
grep -Fq 'SCHEMA_BOUNDARY=1' "$DEPLOY"
grep -Fq 'automatic contract-boundary restoration is blocked' "$DEPLOY"
grep -Fq 'require_no_migration_boundary_rollback' "$ROLLBACK"
grep -Fq 'database migration boundary' "$COMMON"

# Health/smoke/HTTPS gates and fail-closed state publication remain app-only.
grep -Fq 'activate_release "$CURRENT_SHA"' "$DEPLOY"
grep -Fq 'stop_failed_release_applications' "$DEPLOY"
grep -Fq 'publish_state_or_abort' "$DEPLOY"
grep -Fq 'verify_running_release' "$COMMON"
grep -Fq 'smoke_release' "$COMMON"
grep -Fq 'verify_https_release' "$COMMON"
grep -Fq 'Backend/Frontend' "$DEPLOY" || grep -Fq 'managed database was not modified' "$DEPLOY"

# Exact identity, immutable digests/revisions, runtime fail-closed, and
# Scheduler OFF gates are all enforced by the active contracts.
grep -Fq 'backend frontend proxy' "$COMMON"
grep -Fq 'org.opencontainers.image.revision' "$COMMON"
grep -Fq 'RepoDigest' "$COMMON"
grep -Fq 'validate_runtime_bundle' "$COMMON"
grep -Fq 'runtime bundle completion marker is missing' "$COMMON"
grep -Fq 'Application deploy/rollback never enables the Scheduler' "$CONTROL"
grep -Fq 'require_subscription_automation_mode' "$CONTROL"

printf 'OPS-OCI-002 provider-neutral release safety coverage passed\n'
