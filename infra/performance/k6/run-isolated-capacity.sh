#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  cat >&2 <<'EOF'
Usage:
  bash run-isolated-capacity.sh \
    --source-root /absolute/path/to/<APPROVED_SHA> \
    --target-url http://127.0.0.1:PORT \
    --dataset-id catalog-core-control-v1|catalog-core-10k-v1 \
    --results-dir /absolute/path \
    --acknowledge-isolated-load YES
EOF
  exit 64
}

source_root=''
target_url=''
dataset_id=''
results_dir=''
acknowledgement=''

while (($#)); do
  case "$1" in
    --source-root)
      source_root="${2:-}"
      shift 2
      ;;
    --target-url)
      target_url="${2:-}"
      shift 2
      ;;
    --dataset-id)
      dataset_id="${2:-}"
      shift 2
      ;;
    --results-dir)
      results_dir="${2:-}"
      shift 2
      ;;
    --acknowledge-isolated-load)
      acknowledgement="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      ;;
    *)
      usage
      ;;
  esac
done

[[ "$source_root" == /* ]] || usage
[[ "$target_url" =~ ^http://(127\.0\.0\.1|localhost|\[::1\])(:[0-9]{1,5})?/?$ ]] || usage
case "$dataset_id" in
  catalog-core-control-v1|catalog-core-10k-v1) ;;
  *) usage ;;
esac
[[ "$results_dir" == /* ]] || usage
[[ "$acknowledgement" == 'YES' ]] || usage

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
script_source_root="$(cd -- "$script_dir/../../.." && pwd -P)"
source_root="$(cd -- "$source_root" && pwd -P)"

[[ "$script_source_root" == "$source_root" ]] || {
  printf 'k6 runner must run from the approved source root\n' >&2
  exit 1
}
[[ -f "$source_root/.approved-sha" && ! -L "$source_root/.approved-sha" ]] || {
  printf 'approved source marker is missing or invalid\n' >&2
  exit 1
}
approved_sha="$(tr -d '\r\n' <"$source_root/.approved-sha")"
[[ "$approved_sha" =~ ^[0-9a-f]{40}$ ]] || {
  printf 'approved source marker must contain a 40-character lowercase SHA\n' >&2
  exit 1
}
[[ "$(basename -- "$source_root")" == "$approved_sha" ]] || {
  printf 'approved source directory does not match marker SHA\n' >&2
  exit 1
}

command -v k6 >/dev/null 2>&1 || {
  printf 'k6 is required\n' >&2
  exit 1
}

if [[ -e "$results_dir" ]] && [[ -n "$(find "$results_dir" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
  printf 'results directory must be empty: %s\n' "$results_dir" >&2
  exit 1
fi
mkdir -p "$results_dir"

for target_rps in 25 50 100 150 200 250; do
  k6 run \
    -e "BASE_URL=$target_url" \
    -e "ISOLATED_DATASET_ID=$dataset_id" \
    -e "ISOLATED_LOAD_ACKNOWLEDGEMENT=$acknowledgement" \
    -e "TARGET_RPS=$target_rps" \
    -e "RESULTS_DIR=$results_dir" \
    "$script_dir/isolated-capacity-api-products.js"
done
