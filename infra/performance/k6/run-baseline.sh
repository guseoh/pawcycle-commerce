#!/usr/bin/env bash
set -euo pipefail

usage() {
  printf '%s\n' 'Usage: run-baseline.sh --cohort api-products|api-product-detail|products-page [--base-url http://127.0.0.1:8080] [--results-dir <local-dir>]'
}

cohort=''
base_url='http://127.0.0.1:8080'
results_dir=''

while (($#)); do
  case "$1" in
    --cohort) cohort="${2:-}"; shift 2 ;;
    --base-url) base_url="${2:-}"; shift 2 ;;
    --results-dir) results_dir="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) usage; exit 64 ;;
  esac
done

case "$cohort" in
  api-products) script='api-products.js' ;;
  api-product-detail) script='api-product-detail.js' ;;
  products-page) script='products-page.js' ;;
  *) usage; exit 64 ;;
esac

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if [[ -n "$results_dir" ]]; then
  mkdir -p -- "$results_dir"
fi

# Every VU step still runs in the same order; a failed step is recorded and the
# runner exits non-zero at the end so a failed verdict cannot look like success.
failed_steps=()
for vus in 1 5 10 20; do
  printf 'Running cohort=%s vus=%s\n' "$cohort" "$vus"
  set +e
  BASE_URL="$base_url" VUS="$vus" RESULTS_DIR="$results_dir" k6 run "$script_dir/$script"
  step_exit=$?
  set -e
  if ((step_exit != 0)); then
    failed_steps+=("${vus}vu:exit=${step_exit}")
  fi
done

if ((${#failed_steps[@]})); then
  printf 'baseline verdict=FAIL cohort=%s failed_steps=%s\n' "$cohort" "${failed_steps[*]}" >&2
  exit 1
fi
printf 'baseline verdict=PASS cohort=%s steps=1,5,10,20\n' "$cohort"
