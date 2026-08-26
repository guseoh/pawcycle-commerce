#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT="infra/production/import-demo-catalog.sh"
bash -n "$SCRIPT"
help_output="$(bash "$SCRIPT" --help)"
grep -Fq -- '--operation <validate|apply>' <<<"$help_output"
grep -Fq -- '--confirm-apply' <<<"$help_output"
grep -Fq -- '--pawcycle.catalog.manifest-import.enabled=true' "$SCRIPT"
grep -Fq -- '--pawcycle.catalog.manifest-import.mode=' "$SCRIPT"
grep -Fq -- 'postflight' backend/src/main/java/com/pawcycle/backend/catalog/application/DemoCatalogManifestImportService.java
printf 'PASS: production demo catalog import contract\n'
