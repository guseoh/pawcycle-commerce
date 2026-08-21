#!/usr/bin/env bash
set -euo pipefail

usage() { printf '%s\n' 'Usage: run-capacity.sh --cohort api-products|api-product-detail|products-page [--base-url http://127.0.0.1:8080]'; }
cohort=''; base_url='http://127.0.0.1:8080'
while (($#)); do case "$1" in --cohort) cohort="${2:-}"; shift 2;; --base-url) base_url="${2:-}"; shift 2;; -h|--help) usage; exit 0;; *) usage; exit 64;; esac; done
case "$cohort" in api-products) script='capacity-api-products.js';; api-product-detail) script='capacity-api-product-detail.js';; products-page) script='capacity-products-page.js';; *) usage; exit 64;; esac
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
for target_rps in 250 500 750 1000; do
  printf 'Running capacity cohort=%s target_rps=%s\n' "$cohort" "$target_rps"
  BASE_URL="$base_url" TARGET_RPS="$target_rps" k6 run "$script_dir/$script"
done
