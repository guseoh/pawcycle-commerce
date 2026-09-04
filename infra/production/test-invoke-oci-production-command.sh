#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
TEST_ROOT="$(mktemp -d)"; FAKE_BIN="$TEST_ROOT/bin"; STATE_FILE="$TEST_ROOT/state"
mkdir -p "$FAKE_BIN"; trap 'rm -rf -- "$TEST_ROOT"' EXIT
cat > "$FAKE_BIN/oci" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
if [[ "$*" == *'command create'* ]]; then
  count=0; [[ ! -f "$FAKE_STATE.create" ]] || count="$(<"$FAKE_STATE.create")"; count=$((count + 1)); printf '%s' "$count" > "$FAKE_STATE.create"
  content=""; target=""
  for argument in "$@"; do
    case "${argument#file://}" in
      */content.json) content="${argument#file://}" ;;
      */target.json) target="${argument#file://}" ;;
    esac
  done
  [[ -n "$content" && -n "$target" && "$(stat -c '%a' "$content")" == 600 && "$(stat -c '%a' "$target")" == 600 ]]
  python3 - "$content" "$target" <<'PY'
import hashlib, json, sys
content=json.load(open(sys.argv[1])); target=json.load(open(sys.argv[2])); assert target == {"instanceId": "ocid1.instance.oc1.ap-tokyo-1.fake"}
text=content["source"]["text"]
assert text.startswith("sudo -n /usr/bin/env bash /opt/pawcycle/control/infra/production/production-command-dispatch.sh")
assert content["source"]["textSha256"] == hashlib.sha256(text.encode()).hexdigest()
PY
  printf 'ocid1.instance-agent-command.oc1.test%s\n' "$count"
elif [[ "$*" == *'command-execution get'* ]]; then
  poll=0; [[ ! -f "$FAKE_STATE.poll" ]] || poll="$(<"$FAKE_STATE.poll")"; poll=$((poll + 1)); printf '%s' "$poll" > "$FAKE_STATE.poll"
  case "${FAKE_MODE:-success}" in
    failed) printf '{"data":{"lifecycle-state":"FAILED","content":{"exit-code":1,"text":"failed"}}}' ;;
    timed_out) printf '{"data":{"lifecycle-state":"TIMED_OUT","content":{"exit-code":1,"text":"timed out"}}}' ;;
    canceled) printf '{"data":{"lifecycle-state":"CANCELED","content":{"exit-code":1,"text":"canceled"}}}' ;;
    nonzero) printf '{"data":{"lifecycle-state":"SUCCEEDED","content":{"exit-code":2,"text":"nonzero"}}}' ;;
    malformed) printf '{invalid' ;;
    unknown) printf '{"data":{"lifecycle-state":"PAUSED","content":{"exit-code":0,"text":"unknown"}}}' ;;
    *) if (( poll == 1 )); then printf '{"data":{"lifecycle-state":"ACCEPTED","content":{"exit-code":0,"text":""}}}'; elif (( poll == 2 )); then printf '{"data":{"lifecycle-state":"IN_PROGRESS","content":{"exit-code":0,"text":""}}}'; else printf '{"data":{"lifecycle-state":"SUCCEEDED","content":{"exit-code":0,"text":"fake command succeeded\\n"}}}'; fi ;;
  esac
else exit 1; fi
EOF
chmod +x "$FAKE_BIN/oci"
run_wrapper() {
  PATH="$FAKE_BIN:$PATH" FAKE_STATE="$STATE_FILE" "$SCRIPT_DIR/invoke-oci-production-command.sh" --operation "$1" --target-sha 0123456789abcdef0123456789abcdef01234567 --compartment-id ocid1.compartment.oc1.ap-tokyo-1.fake --instance-id ocid1.instance.oc1.ap-tokyo-1.fake --region ap-tokyo-1 >/dev/null
}
run_wrapper preflight; [[ "$(<"$STATE_FILE.create")" == 1 ]]; rm -f -- "$STATE_FILE.create" "$STATE_FILE.poll"
run_wrapper control-adopt; [[ "$(<"$STATE_FILE.create")" == 1 ]]; rm -f -- "$STATE_FILE.create" "$STATE_FILE.poll"
run_wrapper deploy; [[ "$(<"$STATE_FILE.create")" == 2 ]]; rm -f -- "$STATE_FILE.create" "$STATE_FILE.poll"
for mode in failed timed_out canceled nonzero malformed unknown; do
  if PATH="$FAKE_BIN:$PATH" FAKE_STATE="$STATE_FILE" FAKE_MODE="$mode" "$SCRIPT_DIR/invoke-oci-production-command.sh" --operation deploy --target-sha 0123456789abcdef0123456789abcdef01234567 --compartment-id ocid1.compartment.oc1.ap-tokyo-1.fake --instance-id ocid1.instance.oc1.ap-tokyo-1.fake --region ap-tokyo-1 >/dev/null 2>&1; then exit 1; fi
  [[ "$(<"$STATE_FILE.create")" == 1 ]]; rm -f -- "$STATE_FILE.create" "$STATE_FILE.poll"
done
printf 'OCI Run Command wrapper fake lifecycle tests passed\n'
