#!/usr/bin/env bash
set -euo pipefail

usage() { printf '%s\n' 'Usage: run-capacity.sh --cohort api-products|api-product-detail|products-page [--base-url http://127.0.0.1:8080]'; }
cohort=''; base_url='http://127.0.0.1:8080'
while (($#)); do case "$1" in --cohort) cohort="${2:-}"; shift 2;; --base-url) base_url="${2:-}"; shift 2;; -h|--help) usage; exit 0;; *) usage; exit 64;; esac; done
case "$cohort" in api-products) script='capacity-api-products.js';; api-product-detail) script='capacity-api-product-detail.js';; products-page) script='capacity-products-page.js';; *) usage; exit 64;; esac
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# The ladder still stops at the first failed stage; the stop is now reported
# with the stages that were not run, and the k6 exit code is preserved.
stages=(250 500 750 1000)
for index in "${!stages[@]}"; do
  target_rps="${stages[$index]}"
  printf 'Running capacity cohort=%s target_rps=%s\n' "$cohort" "$target_rps"
  set +e
  BASE_URL="$base_url" TARGET_RPS="$target_rps" k6 run "$script_dir/$script"
  stage_exit=$?
  set -e
  if ((stage_exit != 0)); then
    not_run="${stages[*]:index+1}"
    printf 'capacity verdict=FAIL cohort=%s failed_rps=%s exit=%s not_run_rps=%s\n' \
      "$cohort" "$target_rps" "$stage_exit" "${not_run:-none}" >&2
    exit "$stage_exit"
  fi
done
printf 'capacity verdict=PASS cohort=%s stages=250,500,750,1000\n' "$cohort"
