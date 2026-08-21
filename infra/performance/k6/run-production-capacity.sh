#!/usr/bin/env bash
set -euo pipefail

usage() {
  printf '%s\n' 'Usage: run-production-capacity.sh --target-url https://host --confirm-target-host host --acknowledge-production-load YES' >&2
  exit 64
}

target_url=''
confirmed_host=''
acknowledgement=''
while (($#)); do
  case "$1" in
    --target-url) target_url="${2:-}"; shift 2 ;;
    --confirm-target-host) confirmed_host="${2:-}"; shift 2 ;;
    --acknowledge-production-load) acknowledgement="${2:-}"; shift 2 ;;
    -h|--help) usage ;;
    *) usage ;;
  esac
done

[[ "$target_url" =~ ^https://([^/?#:@]+)(:[0-9]{1,5})?/?$ ]] || usage
target_host="${BASH_REMATCH[1]}"
[[ -n "$confirmed_host" && "${target_host,,}" == "${confirmed_host,,}" ]] || usage
[[ "$acknowledgement" == 'YES' ]] || usage

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
for target_rps in 25 50 100 150 200 250; do
  k6 run \
    -e "PRODUCTION_TARGET_URL=$target_url" \
    -e "PRODUCTION_TARGET_HOST=$confirmed_host" \
    -e "PRODUCTION_LOAD_ACKNOWLEDGEMENT=$acknowledgement" \
    -e "TARGET_RPS=$target_rps" \
    "$script_dir/production-capacity-api-products.js"
done
