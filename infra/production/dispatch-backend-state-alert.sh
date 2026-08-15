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
parsed_result=""

if parsed_result="$("$PYTHON_BIN" - "$RESULT_FILE" <<'PY'
import os
import stat
import sys

path = sys.argv[1]
flags = (
    os.O_RDONLY
    | getattr(os, "O_CLOEXEC", 0)
    | getattr(os, "O_NOFOLLOW", 0)
    | getattr(os, "O_NONBLOCK", 0)
)
try:
    fd = os.open(path, flags)
    info = os.fstat(fd)
    if not stat.S_ISREG(info.st_mode) or info.st_size > 4096:
        os.close(fd)
        raise OSError
    with os.fdopen(fd, "rb") as handle:
        raw = handle.read(4097)
    if len(raw) > 4096:
        raise OSError
except OSError:
    raise SystemExit(1)

if b"\x00" in raw or b"\r" in raw:
    raise SystemExit(1)
try:
    text = raw.decode("utf-8")
except UnicodeDecodeError:
    raise SystemExit(1)

lines = text.split("\n")
if lines and lines[-1] == "":
    lines.pop()
if len(lines) != 3:
    raise SystemExit(1)

result = {}
for line in lines:
    key, separator, value = line.partition("=")
    if not separator or not key or not value or "=" in value or key in result:
        raise SystemExit(1)
    if key not in {"status", "production_assessment", "prometheus_target"}:
        raise SystemExit(1)
    result[key] = value

statuses = {"NORMAL", "BACKEND_DOWN", "OBSERVABILITY_DEGRADED", "DEGRADED", "UNKNOWN"}
assessments = {"READY", "BACKEND_DOWN", "OBSERVABILITY_DEGRADED", "DEGRADED", "UNKNOWN"}
targets = {"up", "down", "unknown"}
status = result.get("status", "")
assessment = result.get("production_assessment", "")
target = result.get("prometheus_target", "")
if status not in statuses or assessment not in assessments or target not in targets:
    raise SystemExit(1)

if target == "unknown" or assessment == "UNKNOWN":
    expected = "UNKNOWN"
elif assessment == "READY" and target == "up":
    expected = "NORMAL"
elif assessment == "BACKEND_DOWN" and target == "down":
    expected = "BACKEND_DOWN"
elif assessment in {"READY", "OBSERVABILITY_DEGRADED"} and target == "down":
    expected = "OBSERVABILITY_DEGRADED"
else:
    expected = "DEGRADED"
if status != expected:
    raise SystemExit(1)

print(status)
print(assessment)
print(target)
PY
)"; then
  mapfile -t parsed_fields <<<"$parsed_result"
  if ((${#parsed_fields[@]} == 3)); then
    status="${parsed_fields[0]}"
    assessment="${parsed_fields[1]}"
    prometheus_target="${parsed_fields[2]}"
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
