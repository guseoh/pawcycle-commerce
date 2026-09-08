#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT="infra/production/import-demo-catalog.sh"
AUTH_SCRIPT="infra/production/create-production-auth-smoke-member.sh"
bash -n "$SCRIPT"
bash -n "$AUTH_SCRIPT"
help_output="$(bash "$SCRIPT" --help)"
grep -Fq -- '--operation <validate|apply>' <<<"$help_output"
grep -Fq -- '--target <demo|customer>' <<<"$help_output"
grep -Fq -- 'The default target is demo.' <<<"$help_output"
grep -Fq -- '--confirm-apply' <<<"$help_output"
grep -Fq -- '[[ "$TARGET" == "demo" || "$TARGET" == "customer" ]]' "$SCRIPT"
grep -Fq -- '--pawcycle.catalog.manifest-import.enabled=true' "$SCRIPT"
grep -Fq -- '--pawcycle.catalog.manifest-import.target="$TARGET"' "$SCRIPT"
grep -Fq -- '--pawcycle.catalog.manifest-import.mode=' "$SCRIPT"
grep -Fq -- '--pawcycle.catalog.manifest-import.confirm-apply=true' "$SCRIPT"
grep -Fq -- 'CUSTOMER_CATALOG_IMPORT_RESULT\ status=PASS' "$SCRIPT"
grep -Fq -- 'IMPORT_ARGUMENTS+=(--pawcycle.catalog.manifest-import.manifest=classpath:catalog/demo-catalog.json)' "$SCRIPT"
grep -Fq -- '--format '\''{{ index .Config.Labels "org.opencontainers.image.revision" }}'\''' "$SCRIPT"
! grep -Fq -- 'org.opencontainers.image.revision\"' "$SCRIPT"
! grep -Fq -- 'timeout --signal=' "$SCRIPT"

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
printf 'PASS: production catalog import target contract\n'
