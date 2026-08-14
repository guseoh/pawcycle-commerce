#!/usr/bin/env bash

set -Eeuo pipefail

STATE_DIR="${PAWCYCLE_STATE_DIR:-/opt/pawcycle/state}"
PROJECT_NAME="${PAWCYCLE_PRODUCTION_PROJECT:-pawcycle-production}"
HTTPS_ORIGIN="${PAWCYCLE_HTTPS_ORIGIN:-}"
PROMETHEUS_URL="${PAWCYCLE_PROMETHEUS_URL:-}"
PRODUCTION_RESULT=""
SCOPE=""
METRICS_PORT="${PAWCYCLE_METRICS_PORT:-9464}"
CONNECT_TIMEOUT_SECONDS="${PAWCYCLE_DIAGNOSTIC_CONNECT_TIMEOUT_SECONDS:-5}"
MAX_TIME_SECONDS="${PAWCYCLE_DIAGNOSTIC_MAX_TIME_SECONDS:-10}"
PYTHON_BIN="${PAWCYCLE_PYTHON_BIN:-python3}"

usage() {
  printf 'usage: %s --scope production [--https-origin <https://host>] [--state-dir <path>]\n' "$0" >&2
  printf '       %s --scope observability --prometheus-url <http://127.0.0.1:port> --production-result <path>\n' "$0" >&2
  exit 64
}

while (($#)); do
  case "$1" in
    --scope) [[ $# -ge 2 ]] || usage; SCOPE="$2"; shift 2 ;;
    --prometheus-url) [[ $# -ge 2 ]] || usage; PROMETHEUS_URL="$2"; shift 2 ;;
    --https-origin) [[ $# -ge 2 ]] || usage; HTTPS_ORIGIN="$2"; shift 2 ;;
    --state-dir) [[ $# -ge 2 ]] || usage; STATE_DIR="$2"; shift 2 ;;
    --production-result) [[ $# -ge 2 ]] || usage; PRODUCTION_RESULT="$2"; shift 2 ;;
    *) usage ;;
  esac
done

http_code() {
  curl --silent --output /dev/null --write-out '%{http_code}' \
    --connect-timeout "$CONNECT_TIMEOUT_SECONDS" --max-time "$MAX_TIME_SECONDS" "$1" || true
}

valid_sha() {
  [[ "$1" =~ ^[0-9a-f]{40}$ ]]
}

valid_mysql_volume() {
  [[ "$1" == pawcycle-production-mysql-data || "$1" =~ ^pawcycle-production-mysql-candidate-[0-9a-f]{16}$ ]]
}

read_required_state() {
  local name="$1" kind="$2" path value
  path="$STATE_DIR/$name"
  if [[ ! -e "$path" && ! -L "$path" ]]; then
    printf 'invalid'
    return
  fi
  if [[ -L "$path" || ! -f "$path" || "$(stat -c '%a' "$path" 2>/dev/null || true)" != 600 ]]; then
    printf 'invalid'
    return
  fi
  value="$(<"$path")"
  if [[ "$kind" == sha ]] && ! valid_sha "$value"; then
    printf 'invalid'
  elif [[ "$kind" == volume ]] && ! valid_mysql_volume "$value"; then
    printf 'invalid'
  else
    printf '%s' "$value"
  fi
}

read_previous_sha() {
  local path="$STATE_DIR/previous-sha" value
  if [[ ! -e "$path" && ! -L "$path" ]]; then
    printf 'none'
    return
  fi
  if [[ -L "$path" || ! -f "$path" || "$(stat -c '%a' "$path" 2>/dev/null || true)" != 600 ]]; then
    printf 'invalid'
    return
  fi
  value="$(<"$path")"
  if valid_sha "$value"; then printf '%s' "$value"; else printf 'invalid'; fi
}

production_assessment() {
  local docker_query="$1" backend="$2" api="$3" metrics="$4" current="$5" previous="$6" volume="$7"
  local api_ok=false metrics_ok=false release_ok=false
  [[ "$api" =~ ^2[0-9][0-9]$ ]] && api_ok=true
  [[ "$metrics" =~ ^2[0-9][0-9]$ ]] && metrics_ok=true
  if valid_sha "$current" && { [[ "$previous" == none ]] || valid_sha "$previous"; } && valid_mysql_volume "$volume"; then
    release_ok=true
  fi

  if [[ "$docker_query" != ok || "$backend" == unknown || "$backend" == ambiguous || "$release_ok" != true ]]; then
    printf 'UNKNOWN'
  elif [[ "$backend" == healthy && "$api_ok" == true && "$metrics_ok" == true ]]; then
    printf 'READY'
  elif [[ "$backend" =~ ^(missing|stopped|unhealthy)$ && "$api_ok" == false ]]; then
    printf 'BACKEND_DOWN'
  elif [[ "$backend" == healthy && "$api_ok" == true && "$metrics_ok" == false ]]; then
    printf 'OBSERVABILITY_DEGRADED'
  else
    printf 'DEGRADED'
  fi
}

run_production() {
  local backend_ids backend_status docker_query api_status metrics_status
  local current_sha previous_sha active_mysql_volume assessment

  [[ -z "$PROMETHEUS_URL" && -z "$PRODUCTION_RESULT" ]] || usage
  if [[ -z "$HTTPS_ORIGIN" && -f "$STATE_DIR/https-domain" && ! -L "$STATE_DIR/https-domain" ]]; then
    HTTPS_ORIGIN="https://$(<"$STATE_DIR/https-domain")"
  fi
  [[ "$HTTPS_ORIGIN" =~ ^https://[^[:space:]]+$ ]] || usage

  docker_query=ok
  if ! backend_ids="$(docker ps --all --quiet --filter "label=com.docker.compose.project=$PROJECT_NAME" --filter 'label=com.docker.compose.service=backend' 2>/dev/null)"; then
    docker_query=failed
    backend_status=unknown
  else
    mapfile -t backend_id_list < <(printf '%s\n' "$backend_ids" | sed '/^$/d')
    if ((${#backend_id_list[@]} == 0)); then
      backend_status=missing
    elif ((${#backend_id_list[@]} > 1)); then
      backend_status=ambiguous
    elif ! backend_status="$(docker inspect --format '{{if .State.Running}}{{if .State.Health}}{{.State.Health.Status}}{{else}}unknown{{end}}{{else}}stopped{{end}}' "${backend_id_list[0]}" 2>/dev/null)"; then
      docker_query=failed
      backend_status=unknown
    elif [[ ! "$backend_status" =~ ^(healthy|unhealthy|stopped)$ ]]; then
      backend_status=unknown
    fi
  fi

  api_status="$(http_code "$HTTPS_ORIGIN/api/products")"
  metrics_status="$(http_code "http://127.0.0.1:${METRICS_PORT}/actuator/prometheus")"
  current_sha="$(read_required_state current-sha sha)"
  previous_sha="$(read_previous_sha)"
  active_mysql_volume="$(read_required_state active-mysql-volume volume)"
  assessment="$(production_assessment "$docker_query" "$backend_status" "$api_status" "$metrics_status" "$current_sha" "$previous_sha" "$active_mysql_volume")"

  printf 'scope=production\nproduction_assessment=%s\ndocker_query=%s\nbackend=%s\napi_products_http=%s\nmetrics_proxy_http=%s\ncurrent_sha=%s\nprevious_sha=%s\nactive_mysql_volume=%s\n' \
    "$assessment" "$docker_query" "$backend_status" "$api_status" "$metrics_status" \
    "$current_sha" "$previous_sha" "$active_mysql_volume"
  [[ "$assessment" == READY ]]
}

declare -A SNAPSHOT=()

load_production_result() {
  local key value
  [[ -f "$PRODUCTION_RESULT" && ! -L "$PRODUCTION_RESULT" ]] || return 1
  while IFS='=' read -r key value; do
    [[ -n "$key" && -n "$value" ]] || return 1
    case "$key" in
      scope|production_assessment|docker_query|backend|api_products_http|metrics_proxy_http|current_sha|previous_sha|active_mysql_volume) ;;
      *) return 1 ;;
    esac
    [[ ! -v "SNAPSHOT[$key]" ]] || return 1
    SNAPSHOT["$key"]="$value"
  done <"$PRODUCTION_RESULT"
  ((${#SNAPSHOT[@]} == 9)) && [[ "${SNAPSHOT[scope]:-}" == production ]]
}

validate_production_result() {
  local calculated
  [[ "${SNAPSHOT[docker_query]:-}" =~ ^(ok|failed)$ ]] || return 1
  [[ "${SNAPSHOT[backend]:-}" =~ ^(healthy|unhealthy|stopped|missing|unknown|ambiguous)$ ]] || return 1
  [[ "${SNAPSHOT[api_products_http]:-}" =~ ^[0-9]{3}$ ]] || return 1
  [[ "${SNAPSHOT[metrics_proxy_http]:-}" =~ ^[0-9]{3}$ ]] || return 1
  calculated="$(production_assessment "${SNAPSHOT[docker_query]}" "${SNAPSHOT[backend]}" \
    "${SNAPSHOT[api_products_http]}" "${SNAPSHOT[metrics_proxy_http]}" \
    "${SNAPSHOT[current_sha]:-}" "${SNAPSHOT[previous_sha]:-}" "${SNAPSHOT[active_mysql_volume]:-}")"
  [[ "$calculated" == "${SNAPSHOT[production_assessment]:-}" ]]
}

prometheus_target_health() {
  local payload
  if ! payload="$(curl --silent --show-error --connect-timeout "$CONNECT_TIMEOUT_SECONDS" --max-time "$MAX_TIME_SECONDS" \
    --get --data-urlencode 'state=active' "$PROMETHEUS_URL/api/v1/targets" 2>/dev/null)"; then
    printf 'unknown'
    return
  fi
  "$PYTHON_BIN" -c '
import json, sys
try:
    payload = json.loads(sys.stdin.read())
    if payload.get("status") != "success":
        raise ValueError
    targets = [target for target in payload["data"]["activeTargets"]
               if target.get("labels", {}).get("job") == "pawcycle-production-backend"]
    if len(targets) != 1:
        raise ValueError
    health = targets[0].get("health")
    print(health if health in {"up", "down"} else "unknown")
except Exception:
    print("unknown")
' <<<"$payload" 2>/dev/null || printf 'unknown'
}

run_observability() {
  local prometheus_target assessment state
  [[ -z "$HTTPS_ORIGIN" && "$STATE_DIR" == "${PAWCYCLE_STATE_DIR:-/opt/pawcycle/state}" ]] || usage
  [[ "$PROMETHEUS_URL" =~ ^http://(127\.0\.0\.1|localhost)(:[0-9]+)?/?$ ]] || usage
  [[ -n "$PRODUCTION_RESULT" ]] || usage

  prometheus_target="$(prometheus_target_health)"
  if ! load_production_result || ! validate_production_result; then
    state=UNKNOWN
    assessment=UNKNOWN
  else
    assessment="${SNAPSHOT[production_assessment]}"
    if [[ "$prometheus_target" == unknown || "$assessment" == UNKNOWN ]]; then
      state=UNKNOWN
    elif [[ "$assessment" == READY && "$prometheus_target" == up ]]; then
      state=NORMAL
    elif [[ "$assessment" == BACKEND_DOWN && "$prometheus_target" == down ]]; then
      state=BACKEND_DOWN
    elif [[ "$assessment" =~ ^(READY|OBSERVABILITY_DEGRADED)$ && "$prometheus_target" == down ]]; then
      state=OBSERVABILITY_DEGRADED
    else
      state=DEGRADED
    fi
  fi

  printf 'status=%s\nproduction_assessment=%s\nprometheus_target=%s\n' "$state" "$assessment" "$prometheus_target"
  [[ "$state" == NORMAL ]]
}

case "$SCOPE" in
  production) run_production ;;
  observability) run_observability ;;
  *) usage ;;
esac
