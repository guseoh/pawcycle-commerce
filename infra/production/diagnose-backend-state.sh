#!/usr/bin/env bash

set -Eeuo pipefail

STATE_DIR="${PAWCYCLE_STATE_DIR:-/opt/pawcycle/state}"
PROJECT_NAME="${PAWCYCLE_PRODUCTION_PROJECT:-pawcycle-production}"
HTTPS_ORIGIN="${PAWCYCLE_HTTPS_ORIGIN:-}"
PROMETHEUS_URL="${PAWCYCLE_PROMETHEUS_URL:-}"
METRICS_PORT="${PAWCYCLE_METRICS_PORT:-9464}"
CONNECT_TIMEOUT_SECONDS="${PAWCYCLE_DIAGNOSTIC_CONNECT_TIMEOUT_SECONDS:-5}"
MAX_TIME_SECONDS="${PAWCYCLE_DIAGNOSTIC_MAX_TIME_SECONDS:-10}"
PYTHON_BIN="${PAWCYCLE_PYTHON_BIN:-python3}"

usage() {
  printf 'usage: %s --prometheus-url <http(s)://host[:port]> [--https-origin <https://host>] [--state-dir <path>]\n' "$0" >&2
  exit 64
}

while (($#)); do
  case "$1" in
    --prometheus-url) PROMETHEUS_URL="${2:-}"; shift 2 ;;
    --https-origin) HTTPS_ORIGIN="${2:-}"; shift 2 ;;
    --state-dir) STATE_DIR="${2:-}"; shift 2 ;;
    *) usage ;;
  esac
done

[[ "$PROMETHEUS_URL" =~ ^https?://[^[:space:]]+$ ]] || usage
if [[ -z "$HTTPS_ORIGIN" && -f "$STATE_DIR/https-domain" && ! -L "$STATE_DIR/https-domain" ]]; then
  HTTPS_ORIGIN="https://$(<"$STATE_DIR/https-domain")"
fi
[[ "$HTTPS_ORIGIN" =~ ^https://[^[:space:]]+$ ]] || usage

read_state() {
  local name="$1"
  local path="$STATE_DIR/$name"
  if [[ -f "$path" && ! -L "$path" ]]; then
    tr -d '\r\n' <"$path"
  else
    printf 'unavailable'
  fi
}

http_code() {
  curl --silent --output /dev/null --write-out '%{http_code}' \
    --connect-timeout "$CONNECT_TIMEOUT_SECONDS" --max-time "$MAX_TIME_SECONDS" "$1" || true
}

backend_ids="$(docker ps --all --quiet --filter "label=com.docker.compose.project=$PROJECT_NAME" --filter 'label=com.docker.compose.service=backend' 2>/dev/null || true)"
backend_status="missing"
if [[ "$(wc -w <<<"$backend_ids")" == 1 ]]; then
  backend_status="$(docker inspect --format '{{if .State.Running}}{{if .State.Health}}{{.State.Health.Status}}{{else}}unknown{{end}}{{else}}stopped{{end}}' $backend_ids 2>/dev/null || printf 'unknown')"
elif [[ -n "$backend_ids" ]]; then
  backend_status="ambiguous"
fi

api_status="$(http_code "$HTTPS_ORIGIN/api/products")"
metrics_status="$(http_code "http://127.0.0.1:${METRICS_PORT}/actuator/prometheus")"
prometheus_payload="$(curl --silent --show-error --connect-timeout "$CONNECT_TIMEOUT_SECONDS" --max-time "$MAX_TIME_SECONDS" --get --data-urlencode 'query=up{job="pawcycle-production-backend"}' "$PROMETHEUS_URL/api/v1/query" 2>/dev/null || true)"
prometheus_target="$($PYTHON_BIN -c '
import json, sys
try:
    rows=json.loads(sys.stdin.read())["data"]["result"]
    rows=[row for row in rows if row.get("metric", {}).get("job") == "pawcycle-production-backend"]
    print("up" if len(rows) == 1 and str(rows[0].get("value", ["", ""])[1]) == "1" else "down" if len(rows) == 1 else "unknown")
except Exception:
    print("unknown")
' <<<"$prometheus_payload" 2>/dev/null || printf 'unknown')"

api_ok=false; [[ "$api_status" =~ ^2[0-9][0-9]$ ]] && api_ok=true
metrics_ok=false; [[ "$metrics_status" =~ ^2[0-9][0-9]$ ]] && metrics_ok=true
state="UNKNOWN"
if [[ "$backend_status" == healthy && "$api_ok" == true && "$metrics_ok" == true && "$prometheus_target" == up ]]; then
  state="NORMAL"
elif [[ "$backend_status" =~ ^(missing|stopped|unhealthy)$ && "$api_ok" == false && "$prometheus_target" == down ]]; then
  state="BACKEND_DOWN"
elif [[ "$backend_status" == healthy && "$api_ok" == true && ( "$metrics_ok" == false || "$prometheus_target" != up ) ]]; then
  state="OBSERVABILITY_DEGRADED"
elif [[ "$backend_status" == unknown || "$backend_status" == ambiguous || "$prometheus_target" == unknown ]]; then
  state="UNKNOWN"
else
  state="DEGRADED"
fi

printf 'status=%s\nbackend=%s\napi_products_http=%s\nmetrics_proxy_http=%s\nprometheus_target=%s\ncurrent_sha=%s\nprevious_sha=%s\nactive_mysql_volume=%s\n' \
  "$state" "$backend_status" "$api_status" "$metrics_status" "$prometheus_target" \
  "$(read_state current-sha)" "$(read_state previous-sha)" "$(read_state active-mysql-volume)"

[[ "$state" == NORMAL ]]
