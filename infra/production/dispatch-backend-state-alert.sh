#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
DISCORD_SENDER="${PAWCYCLE_DISCORD_SENDER:-$SCRIPT_DIR/../../.github/scripts/send-discord-notification.py}"
SLACK_SENDER="${PAWCYCLE_SLACK_SENDER:-$SCRIPT_DIR/send-slack-notification.py}"
PYTHON_BIN="${PAWCYCLE_PYTHON_BIN:-python3}"
RESULT_FILE=""

usage() {
  printf 'usage: %s --result <observability-result-path>\n' "$0" >&2
  exit 64
}

while (($#)); do
  case "$1" in
    --result) [[ $# -ge 2 ]] || usage; RESULT_FILE="$2"; shift 2 ;;
    *) usage ;;
  esac
done

[[ -n "$RESULT_FILE" ]] || usage

status=UNKNOWN
assessment=UNKNOWN
prometheus_target=unknown
trusted=true
declare -A RESULT=()

if [[ ! -f "$RESULT_FILE" || -L "$RESULT_FILE" ]]; then
  trusted=false
else
  while IFS='=' read -r key value; do
    if [[ -z "$key" || -z "$value" || ! "$key" =~ ^(status|production_assessment|prometheus_target)$ || -v "RESULT[$key]" ]]; then
      trusted=false
      break
    fi
    RESULT["$key"]="$value"
  done <"$RESULT_FILE"
  if ((${#RESULT[@]} != 3)) \
    || [[ ! "${RESULT[status]:-}" =~ ^(NORMAL|BACKEND_DOWN|OBSERVABILITY_DEGRADED|DEGRADED|UNKNOWN)$ ]] \
    || [[ ! "${RESULT[production_assessment]:-}" =~ ^(READY|BACKEND_DOWN|OBSERVABILITY_DEGRADED|DEGRADED|UNKNOWN)$ ]] \
    || [[ ! "${RESULT[prometheus_target]:-}" =~ ^(up|down|unknown)$ ]]; then
    trusted=false
  fi
fi

if [[ "$trusted" == true ]]; then
  status="${RESULT[status]}"
  assessment="${RESULT[production_assessment]}"
  prometheus_target="${RESULT[prometheus_target]}"

  if [[ "$status" == NORMAL && ( "$assessment" != READY || "$prometheus_target" != up ) ]]; then
    trusted=false
    status=UNKNOWN
    assessment=UNKNOWN
    prometheus_target=unknown
  fi
fi

if [[ "$status" == NORMAL ]]; then
  printf 'Backend state alert skipped: status=NORMAL\n'
  exit 0
fi

work_dir="$(mktemp -d)"
trap 'rm -rf -- "$work_dir"' EXIT
discord_payload="$work_dir/discord-payload.json"
discord_context="$work_dir/discord-context.json"
slack_payload="$work_dir/slack-payload.json"

case "$status" in
  BACKEND_DOWN) color=15158332 ;;
  OBSERVABILITY_DEGRADED|DEGRADED) color=15105570 ;;
  *) color=9807270 ;;
esac

printf '{"allowed_mentions":{"parse":[]},"embeds":[{"title":"PawCycle Backend State Alert","color":%s,"fields":[{"name":"status","value":"%s","inline":true},{"name":"production_assessment","value":"%s","inline":true},{"name":"prometheus_target","value":"%s","inline":true}]}]}' \
  "$color" "$status" "$assessment" "$prometheus_target" >"$discord_payload"
printf '{"event":"backend_state_alert"}' >"$discord_context"
printf '{"text":"PawCycle Backend State Alert\\nstatus: %s\\nproduction_assessment: %s\\nprometheus_target: %s"}' \
  "$status" "$assessment" "$prometheus_target" >"$slack_payload"

set +e
"$PYTHON_BIN" "$DISCORD_SENDER" --payload-file "$discord_payload" --context-file "$discord_context"
discord_code=$?
"$PYTHON_BIN" "$SLACK_SENDER" --payload-file "$slack_payload"
slack_code=$?
set -e

printf 'Backend state alert dispatch: status=%s discord=%s slack=%s\n' "$status" "$discord_code" "$slack_code"
if ((discord_code == 0 && slack_code == 0)); then
  exit 0
fi
exit 1
