#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
DISPATCHER="$SCRIPT_DIR/dispatch-backend-state-alert.sh"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf -- "$TEST_ROOT"' EXIT

cat >"$TEST_ROOT/sender.py" <<'EOF'
import os
import shutil
import sys
from pathlib import Path

channel = "discord" if "discord" in Path(sys.argv[0]).name else "slack"
payload_file = sys.argv[sys.argv.index("--payload-file") + 1]
capture_dir = Path(os.environ["CAPTURE_DIR"])
shutil.copyfile(payload_file, capture_dir / f"{channel}.json")
with (capture_dir / "calls").open("a", encoding="utf-8") as handle:
    handle.write(channel + "\n")
raise SystemExit(1 if os.environ.get("FAIL_CHANNEL") in {channel, "both"} else 0)
EOF
cp "$TEST_ROOT/sender.py" "$TEST_ROOT/discord-sender.py"
cp "$TEST_ROOT/sender.py" "$TEST_ROOT/slack-sender.py"

run_case() {
  local name="$1" result="$2" expected_code="$3" fail_channel="${4:-}" code output
  mkdir -p "$TEST_ROOT/$name"
  printf '%s' "$result" >"$TEST_ROOT/$name/result"
  if CAPTURE_DIR="$TEST_ROOT/$name" FAIL_CHANNEL="$fail_channel" PAWCYCLE_DISCORD_SENDER="$TEST_ROOT/discord-sender.py" PAWCYCLE_SLACK_SENDER="$TEST_ROOT/slack-sender.py" \
    DISCORD_WEBHOOK_URL='https://example.invalid/private-discord' SLACK_WEBHOOK_URL='https://example.invalid/private-slack' \
    bash "$DISPATCHER" --result "$TEST_ROOT/$name/result" >"$TEST_ROOT/$name/output" 2>&1; then code=0; else code=$?; fi
  [[ "$code" == "$expected_code" ]]
  output="$(<"$TEST_ROOT/$name/output")"
  [[ "$output" != *private-* ]]
}

normal='status=NORMAL
production_assessment=READY
prometheus_target=up
'
run_case normal "$normal" 0
[[ ! -e "$TEST_ROOT/normal/calls" ]]

for status in BACKEND_DOWN OBSERVABILITY_DEGRADED DEGRADED UNKNOWN; do
  result="status=$status
production_assessment=$status
prometheus_target=down
"
  [[ "$status" != UNKNOWN ]] || result='status=UNKNOWN
production_assessment=UNKNOWN
prometheus_target=unknown
'
  run_case "$status" "$result" 0
  [[ "$(sort "$TEST_ROOT/$status/calls" | tr -d '\r' | tr '\n' ' ')" == 'discord slack ' ]]
  grep -q "\"status\",\"value\":\"$status\"" "$TEST_ROOT/$status/discord.json"
  grep -q "status: $status" "$TEST_ROOT/$status/slack.json"
done

run_case malformed 'status=NORMAL
production_assessment=READY
prometheus_target=up
unexpected=value
' 0
[[ "$(sort "$TEST_ROOT/malformed/calls" | tr -d '\r' | tr '\n' ' ')" == 'discord slack ' ]]
grep -q '"value":"UNKNOWN"' "$TEST_ROOT/malformed/discord.json"

run_case inconsistent-normal 'status=NORMAL
production_assessment=UNKNOWN
prometheus_target=down
' 0
[[ "$(sort "$TEST_ROOT/inconsistent-normal/calls" | tr -d '\r' | tr '\n' ' ')" == 'discord slack ' ]]
grep -q '"value":"UNKNOWN"' "$TEST_ROOT/inconsistent-normal/discord.json"

abnormal='status=DEGRADED
production_assessment=DEGRADED
prometheus_target=up
'
for fail_channel in discord slack both; do
  run_case "$fail_channel-failure" "$abnormal" 1 "$fail_channel"
  [[ "$(sort "$TEST_ROOT/$fail_channel-failure/calls" | tr -d '\r' | tr '\n' ' ')" == 'discord slack ' ]]
done

printf 'OPS-AUTO-010 backend state alert dispatcher tests passed\n'
