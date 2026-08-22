#!/usr/bin/env bash
set -euo pipefail
usage() { printf '%s\n' 'Usage: run-phase8d.sh --profile mixed-steady|burst|sustained|bounded-write --member-email <local QA bootstrap email>'; }
profile=''; member_email=''; base_url='http://127.0.0.1:8080'; target_rps='20'
while (($#)); do case "$1" in --profile) profile="${2:-}"; shift 2;; --member-email) member_email="${2:-}"; shift 2;; --base-url) base_url="${2:-}"; shift 2;; --target-rps) target_rps="${2:-}"; shift 2;; -h|--help) usage; exit 0;; *) usage; exit 64;; esac; done
case "$profile" in mixed-steady) script='phase8d-mixed-steady.js';; burst) script='phase8d-burst.js';; sustained) script='phase8d-sustained.js';; bounded-write) script='phase8d-bounded-write.js';; *) usage; exit 64;; esac
if [[ ! "$base_url" =~ ^http://(127\.0\.0\.1|localhost|\[::1\])(:[0-9]{1,5})?$ ]]; then printf '%s\n' 'Phase 8-D runner refuses non-loopback targets.' >&2; exit 64; fi
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if [[ -z "${PERF_PHASE8D_MEMBER_PASSWORD:-}" ]]; then printf '%s\n' 'PERF_PHASE8D_MEMBER_PASSWORD must be supplied through the environment.' >&2; exit 64; fi
BASE_URL="$base_url" TARGET_RPS="$target_rps" PERF_PHASE8D_MEMBER_EMAIL="$member_email" k6 run "$script_dir/$script"
