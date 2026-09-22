#!/usr/bin/env bash
set -Eeuo pipefail

[[ "${EUID:-$(id -u)}" -eq 0 ]] || {
  printf 'test must run as root\n' >&2
  exit 1
}

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
MANAGER="$SCRIPT_DIR/manage-isolated-catalog.sh"
COMPOSE_FILE="$SCRIPT_DIR/compose.yaml"
K6_RUNNER="$SCRIPT_DIR/../k6/run-isolated-capacity.sh"

tmp="$(mktemp -d /tmp/pawcycle-isolated-catalog-test.XXXXXX)"
trap 'rm -rf "$tmp"' EXIT

dataset_id='catalog-core-control-v1'
dataset_dir="$tmp/$dataset_id"
config_file="$tmp/$dataset_id.env"
password_file="$tmp/db-password"
results_dir="$tmp/results"
fake_bin="$tmp/bin"
docker_log="$tmp/docker.log"
k6_log="$tmp/k6.log"
curl_log="$tmp/curl.log"
approved_image='ghcr.io/guseoh/pawcycle-backend@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'

mkdir -m 0700 "$dataset_dir"
mkdir -p "$results_dir" "$fake_bin"

python3 - "$dataset_dir" <<'PY'
import hashlib
import json
import sys
from pathlib import Path

dataset_dir = Path(sys.argv[1])
products = []
for index in range(32):
    products.append({
        "catalogKey": f"CONTROL-{index:04d}",
        "categorySlug": "food",
        "petType": "DOG" if index % 2 == 0 else "CAT",
        "skus": [{
            "skuCode": f"CONTROL-SKU-{index:04d}",
            "status": "ACTIVE",
            "price": 10000 + index,
            "subscribable": index % 2 == 0,
            "initialInventory": 20,
        }],
    })
manifest = {"categories": [{"slug": "food"}], "products": products}
manifest_bytes = (json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode()
(dataset_dir / "manifest.json").write_bytes(manifest_bytes)
report = {
    "schemaVersion": 1,
    "datasetId": "catalog-core-control-v1",
    "seed": 20260826,
    "baseManifestSha256": "b" * 64,
    "generatedManifestSha256": hashlib.sha256(manifest_bytes).hexdigest(),
    "products": {
        "base": 32,
        "synthetic": 0,
        "total": 32,
        "petType": {"CAT": 16, "DOG": 16},
        "category": {"food": 32},
        "withoutSku": 0,
        "duplicateCatalogKeys": 0,
        "unknownCategoryReferences": 0,
    },
    "skus": {
        "total": 32,
        "fanoutByProduct": {"1": 32},
        "status": {"ACTIVE": 32},
        "subscribable": {"false": 16, "true": 16},
        "duplicateSkuCodes": 0,
    },
    "inventory": {
        "total": 32,
        "stockout": 0,
        "low_1_5": 0,
        "normal_gt_5": 32,
    },
    "price": {
        "min": 10000,
        "p50NearestRank": 10015,
        "p95NearestRank": 10030,
        "max": 10031,
    },
}
(dataset_dir / "report.json").write_text(
    json.dumps(report, ensure_ascii=False, indent=2) + "\n",
    encoding="utf-8",
)
PY
chmod 0444 "$dataset_dir/manifest.json" "$dataset_dir/report.json"

cat >"$config_file" <<EOF
PAWCYCLE_PERF_DATASET_ID=catalog-core-control-v1
PAWCYCLE_PERF_SCHEMA=pawcycle_perf_core_control
PAWCYCLE_PERF_BACKEND_IMAGE=$approved_image
PAWCYCLE_PERF_DB_URL=jdbc:mysql://mysql.internal:3306/pawcycle_perf_core_control?sslMode=REQUIRED
PAWCYCLE_PERF_DB_USERNAME=pawcycle_perf_catalog
PAWCYCLE_PERF_HOST_PORT=18080
EOF
chmod 0600 "$config_file"
printf '%s\n' 'fixture-password' >"$password_file"
chmod 0400 "$password_file"

# Real Compose parsing is part of the contract test. It does not start containers.
PAWCYCLE_PERF_DB_PASSWORD='fixture-password' \
PAWCYCLE_PERF_MANIFEST_PATH="$dataset_dir/manifest.json" \
PAWCYCLE_PERF_IMPORT_OPERATION='validate' \
docker compose \
  --project-name pawcycle-performance-catalog \
  --profile tools \
  --env-file "$config_file" \
  -f "$COMPOSE_FILE" \
  config >"$tmp/resolved-compose.yaml"

grep -q 'name: pawcycle-performance-catalog' "$tmp/resolved-compose.yaml"
grep -q 'host_ip: 127.0.0.1' "$tmp/resolved-compose.yaml"
grep -q 'performance-db-egress' "$tmp/resolved-compose.yaml"
grep -q 'read_only: true' "$tmp/resolved-compose.yaml"
grep -q 'no-new-privileges:true' "$tmp/resolved-compose.yaml"
if grep -Eq 'pawcycle-production|app-ingress|0\.0\.0\.0' "$tmp/resolved-compose.yaml"; then
  printf 'resolved Compose unexpectedly references Production/public network state\n' >&2
  exit 1
fi

cat >"$fake_bin/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
log="${FAKE_DOCKER_LOG:?}"
approved_image="${FAKE_APPROVED_IMAGE:?}"
printf 'docker|operation=%s|%s\n' "${PAWCYCLE_PERF_IMPORT_OPERATION:-}" "$*" >>"$log"

if [[ "${1:-}" == "compose" && "${2:-}" == "version" ]]; then
  exit 0
fi
if [[ "${1:-}" == "image" && "${2:-}" == "inspect" ]]; then
  joined=" $* "
  case "$joined" in
    *"{{.Os}}/{{.Architecture}}"*)
      printf 'linux/amd64\n'
      exit 0
      ;;
    *".RepoDigests"*)
      printf '%s\n' "$approved_image"
      exit 0
      ;;
    *)
      exit 0
      ;;
  esac
fi
if [[ "${1:-}" == "ps" ]]; then
  exit 0
fi
if [[ "${1:-}" == "inspect" ]]; then
  printf 'healthy\n'
  exit 0
fi

joined=" $* "
case "$joined" in
  *" config "*) exit 0 ;;
  *" ps --status running -q backend "*) exit 0 ;;
  *" ps -q backend "*)
    printf 'fake-backend\n'
    exit 0
    ;;
  *" run --rm --no-deps --pull never catalog-import "*) exit 0 ;;
  *" up -d --pull never backend "*) exit 0 ;;
  *" down --remove-orphans "*) exit 0 ;;
  *" ps "*) exit 0 ;;
esac
exit 0
EOF
chmod +x "$fake_bin/docker"

cat >"$fake_bin/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'curl|%s\n' "$*" >>"${FAKE_CURL_LOG:?}"
exit 0
EOF
chmod +x "$fake_bin/curl"

cat >"$fake_bin/k6" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'k6|%s\n' "$*" >>"${FAKE_K6_LOG:?}"
exit 0
EOF
chmod +x "$fake_bin/k6"

run_manager() {
  PATH="$fake_bin:$PATH" \
  FAKE_DOCKER_LOG="$docker_log" \
  FAKE_CURL_LOG="$curl_log" \
  FAKE_APPROVED_IMAGE="$approved_image" \
  bash "$MANAGER" "$@" \
    --config-file "$config_file" \
    --password-file "$password_file" \
    --dataset-dir "$dataset_dir"
}

run_manager preflight >/dev/null
grep -q 'image inspect' "$docker_log"
grep -q '{{.Os}}/{{.Architecture}}' "$docker_log"
grep -q '.RepoDigests' "$docker_log"

: >"$docker_log"
if run_manager import-apply >/dev/null 2>&1; then
  printf 'import-apply unexpectedly succeeded without acknowledgement\n' >&2
  exit 1
fi
if grep -q 'run --rm --no-deps --pull never catalog-import' "$docker_log"; then
  printf 'import ran before acknowledgement\n' >&2
  exit 1
fi

: >"$docker_log"
run_manager import-apply --acknowledge "APPLY:$dataset_id" >/dev/null
grep -q 'operation=validate|.*run --rm --no-deps --pull never catalog-import' "$docker_log"
grep -q 'operation=apply|.*run --rm --no-deps --pull never catalog-import' "$docker_log"

: >"$docker_log"
if run_manager up >/dev/null 2>&1; then
  printf 'runtime start unexpectedly succeeded without acknowledgement\n' >&2
  exit 1
fi
if grep -q 'up -d --pull never backend' "$docker_log"; then
  printf 'runtime started before acknowledgement\n' >&2
  exit 1
fi

: >"$docker_log"
: >"$curl_log"
run_manager up --acknowledge "START:$dataset_id" >/dev/null
grep -q 'up -d --pull never backend' "$docker_log"
grep -q '/actuator/health/readiness' "$curl_log"
grep -q '/api/products' "$curl_log"

: >"$docker_log"
if run_manager down >/dev/null 2>&1; then
  printf 'runtime cleanup unexpectedly succeeded without acknowledgement\n' >&2
  exit 1
fi
if grep -q 'down --remove-orphans' "$docker_log"; then
  printf 'runtime cleanup ran before acknowledgement\n' >&2
  exit 1
fi

: >"$docker_log"
run_manager down --acknowledge 'DOWN:pawcycle-performance-catalog' >/dev/null
grep -q 'down --remove-orphans' "$docker_log"
if grep -q -- '--volumes' "$docker_log"; then
  printf 'runtime cleanup must not remove volumes\n' >&2
  exit 1
fi

: >"$k6_log"
if PATH="$fake_bin:$PATH" FAKE_K6_LOG="$k6_log" \
  bash "$K6_RUNNER" \
    --target-url 'https://example.com' \
    --dataset-id "$dataset_id" \
    --results-dir "$results_dir" \
    --acknowledge-isolated-load YES >/dev/null 2>&1; then
  printf 'isolated k6 runner accepted a non-loopback target\n' >&2
  exit 1
fi
[[ ! -s "$k6_log" ]]

PATH="$fake_bin:$PATH" FAKE_K6_LOG="$k6_log" \
bash "$K6_RUNNER" \
  --target-url 'http://127.0.0.1:18080' \
  --dataset-id "$dataset_id" \
  --results-dir "$results_dir" \
  --acknowledge-isolated-load YES >/dev/null

[[ "$(wc -l <"$k6_log")" -eq 6 ]]
grep -q 'TARGET_RPS=25' "$k6_log"
grep -q 'TARGET_RPS=250' "$k6_log"
grep -q 'ISOLATED_DATASET_ID=catalog-core-control-v1' "$k6_log"

printf 'isolated_catalog_contract_test=PASS\n'
