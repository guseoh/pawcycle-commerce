#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT="infra/production/import-demo-catalog.sh"
AUTH_SCRIPT="infra/production/create-production-auth-smoke-member.sh"
bash -n "$SCRIPT"
bash -n "$AUTH_SCRIPT"
help_output="$(bash "$SCRIPT" --help)"
grep -Fq -- '--operation <validate|apply>' <<<"$help_output"
grep -Fq -- '--target <demo|customer>' <<<"$help_output"
grep -Fq -- '--identity-mode <release-state|running-container>' <<<"$help_output"
grep -Fq -- 'The default target is demo.' <<<"$help_output"
grep -Fq -- 'The default identity mode is release-state.' <<<"$help_output"
grep -Fq -- 'running-container is an explicit OCI-compatible identity path and never falls back automatically.' <<<"$help_output"
grep -Fq -- '--confirm-apply' <<<"$help_output"
grep -Fq -- '[[ "$TARGET" == "demo" || "$TARGET" == "customer" ]]' "$SCRIPT"
grep -Fq -- '[[ "$IDENTITY_MODE" == "release-state" || "$IDENTITY_MODE" == "running-container" ]]' "$SCRIPT"
grep -Fq -- '--pawcycle.catalog.manifest-import.enabled=true' "$SCRIPT"
grep -Fq -- '--pawcycle.catalog.manifest-import.target="$TARGET"' "$SCRIPT"
grep -Fq -- '--pawcycle.catalog.manifest-import.mode=' "$SCRIPT"
grep -Fq -- '--pawcycle.catalog.manifest-import.confirm-apply=true' "$SCRIPT"
grep -Fq -- 'CUSTOMER_CATALOG_IMPORT_RESULT\ status=PASS' "$SCRIPT"
grep -Fq -- 'IMPORT_ARGUMENTS+=(--pawcycle.catalog.manifest-import.manifest=classpath:catalog/demo-catalog.json)' "$SCRIPT"
! grep -Fq -- 'timeout --signal=' "$SCRIPT"

# release-state remains the default and keeps the existing registry/state/revision checks.
grep -Fq -- 'IDENTITY_MODE="release-state"' "$SCRIPT"
grep -Fq -- 'CURRENT_SHA_FILE="$STATE_DIR/current-sha"' "$SCRIPT"
grep -Fq -- 'IMAGE_STATE_FILE="$STATE_DIR/$RELEASE_SHA.images"' "$SCRIPT"
grep -Fq -- 'RUNTIME_CURRENT="$(readlink -f -- "$RUNTIME_DIR/current"' "$SCRIPT"
grep -Fq -- 'BACKEND_DIGEST_LINE=' "$SCRIPT"
grep -Fq -- '--format '\''{{ index .Config.Labels "org.opencontainers.image.revision" }}'\''' "$SCRIPT"
grep -Fq -- 'RUN_IMAGE="$BACKEND_DIGEST"' "$SCRIPT"

# running-container is explicit: it uses the direct OCI runtime file and verifies Git/Compose/image identity.
grep -Fq -- 'BACKEND_ENV_FILE="$RUNTIME_DIR/backend.env"' "$SCRIPT"
grep -Fq -- 'git -C "$CONTROL_ROOT" cat-file -e "${RELEASE_SHA}^{commit}"' "$SCRIPT"
grep -Fq -- 'git -C "$CONTROL_ROOT" merge-base --is-ancestor "$RELEASE_SHA" HEAD' "$SCRIPT"
grep -Fq -- 'com.docker.compose.project.working_dir' "$SCRIPT"
grep -Fq -- 'com.docker.compose.project.config_files' "$SCRIPT"
grep -Fq -- 'LOCAL_IMAGE_ID="$(docker_value image inspect --format '\''{{.Id}}'\'' "$EXPECTED_IMAGE_REF")"' "$SCRIPT"
grep -Fq -- 'RUNNING_IMAGE_ID="$(docker_value inspect --format '\''{{.Image}}'\'' "$BACKEND_CONTAINER")"' "$SCRIPT"
grep -Fq -- '[[ "$RUNNING_IMAGE_ID" == "$LOCAL_IMAGE_ID" ]]' "$SCRIPT"
grep -Fq -- 'RUN_IMAGE="$RUNNING_IMAGE_ID"' "$SCRIPT"
grep -Fq -- '"$RUN_IMAGE" "${IMPORT_ARGUMENTS[@]}"' "$SCRIPT"

# The running-container path must not silently synthesize legacy release state.
! grep -Fq -- 'printf '\''%s\n'\'' "$RELEASE_SHA" > "$STATE_DIR/current-sha"' "$SCRIPT"
! grep -Fq -- 'touch "$STATE_DIR/$RELEASE_SHA.images"' "$SCRIPT"
! grep -Fq -- 'ln -s' "$SCRIPT"

for wrapper in "$SCRIPT" "$AUTH_SCRIPT"; do
  ! grep -Eq -- 'DATA_NETWORK|pawcycle-production-data|com\.docker\.compose\.service=mysql|MYSQL_(CONTAINER|CONTAINERS)' "$wrapper"
  grep -Fq -- 'NetworkSettings.Networks' "$wrapper"
  grep -Fq -- "network inspect --format '{{.Internal}}'" "$wrapper"
  grep -Fq -- 'production Backend database network is ambiguous' "$wrapper"
  grep -Fq -- 'DATABASE_EGRESS_NETWORK' "$wrapper"
  grep -Fq -- '--network "$DATABASE_EGRESS_NETWORK"' "$wrapper"
  grep -Fq -- 'database egress network membership is invalid' "$wrapper"
done
grep -Fq -- 'database-egress' infra/production/compose.yaml
! grep -Eq -- '^  mysql:' infra/production/compose.yaml
grep -Fq -- 'postflight' backend/src/main/java/com/pawcycle/backend/catalog/application/DemoCatalogImportResult.java
grep -Fq -- 'CUSTOMER_CATALOG_IMPORT_RESULT status=PASS' backend/src/main/java/com/pawcycle/backend/catalog/application/CustomerCatalogImportResult.java
printf 'PASS: production catalog import target and runtime identity contract\n'
