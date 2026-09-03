#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT="infra/production/import-demo-catalog.sh"
bash -n "$SCRIPT"
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
grep -Fq -- 'postflight' backend/src/main/java/com/pawcycle/backend/catalog/application/DemoCatalogManifestImportService.java
grep -Fq -- 'CUSTOMER_CATALOG_IMPORT_RESULT status=PASS' backend/src/main/java/com/pawcycle/backend/catalog/application/CustomerCatalogImportResult.java
printf 'PASS: production catalog import target contract\n'
