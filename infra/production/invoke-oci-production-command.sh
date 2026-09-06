#!/usr/bin/env bash

set -Eeuo pipefail
set +x

OPERATION=""
TARGET_SHA=""
COMPARTMENT_ID=""
INSTANCE_ID=""
REGION=""
APPROVED_CONTRACT_FROM_SHA=""
APPROVED_CONTROL_SHA=""
APPROVED_MIGRATION_TARGET_SHA=""
COMMAND_ID=""
TEMP_DIR=""
MAX_OUTPUT_BYTES=12288
COMMAND_TIMEOUT_SECONDS=600
POLL_INTERVAL_SECONDS=2
MAX_POLL_ATTEMPTS=$(( (COMMAND_TIMEOUT_SECONDS + POLL_INTERVAL_SECONDS - 1) / POLL_INTERVAL_SECONDS + 1 ))

die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
usage() { printf 'Usage: %s --operation <preflight|deploy|control-adopt> --target-sha <sha> --compartment-id <ocid> --instance-id <ocid> --region <region> [approval SHA options]\n' "${0##*/}" >&2; }
validate_sha() { [[ "$1" =~ ^[0-9a-f]{40}$ ]] || die "SHA must be exactly 40 lowercase hexadecimal characters"; }
validate_ocid() { [[ "$1" =~ ^ocid1\.compartment\.oc1\.[a-z0-9.-]{2,128}$ ]] || die "compartment OCID is invalid"; }

while (($#)); do
  case "$1" in
    --operation) [[ $# -gt 1 ]] || die "--operation requires a value"; OPERATION="$2"; shift 2 ;;
    --target-sha) [[ $# -gt 1 ]] || die "--target-sha requires a value"; TARGET_SHA="$2"; shift 2 ;;
    --compartment-id) [[ $# -gt 1 ]] || die "--compartment-id requires a value"; COMPARTMENT_ID="$2"; shift 2 ;;
    --instance-id) [[ $# -gt 1 ]] || die "--instance-id requires a value"; INSTANCE_ID="$2"; shift 2 ;;
    --region) [[ $# -gt 1 ]] || die "--region requires a value"; REGION="$2"; shift 2 ;;
    --approved-contract-from-sha) [[ $# -gt 1 ]] || die "--approved-contract-from-sha requires a value"; APPROVED_CONTRACT_FROM_SHA="$2"; shift 2 ;;
    --approved-control-sha) [[ $# -gt 1 ]] || die "--approved-control-sha requires a value"; APPROVED_CONTROL_SHA="$2"; shift 2 ;;
    --approved-migration-target-sha) [[ $# -gt 1 ]] || die "--approved-migration-target-sha requires a value"; APPROVED_MIGRATION_TARGET_SHA="$2"; shift 2 ;;
    --help) usage; exit 0 ;;
    *) usage; die "unknown argument" ;;
  esac
done

[[ "$OPERATION" == preflight || "$OPERATION" == deploy || "$OPERATION" == control-adopt ]] || die "operation is invalid"
validate_sha "$TARGET_SHA"
validate_ocid "$COMPARTMENT_ID"
[[ "$INSTANCE_ID" =~ ^ocid1\.instance\.oc1\.[a-z0-9.-]{2,128}$ ]] || die "instance OCID is invalid"
[[ "$REGION" =~ ^[a-z0-9]([a-z0-9-]{0,30}[a-z0-9])?$ ]] || die "region is invalid"
for approved_sha in "$APPROVED_CONTRACT_FROM_SHA" "$APPROVED_CONTROL_SHA" "$APPROVED_MIGRATION_TARGET_SHA"; do
  [[ -z "$approved_sha" ]] || validate_sha "$approved_sha"
done
command -v oci >/dev/null 2>&1 || die "OCI CLI is unavailable"
command -v python3 >/dev/null 2>&1 || die "python3 is unavailable"

cleanup() {
  local status=$? cleanup_failed=0
  if [[ -n "$TEMP_DIR" && -d "$TEMP_DIR" ]]; then rm -rf -- "$TEMP_DIR"; fi
  [[ -z "$TEMP_DIR" || ! -e "$TEMP_DIR" ]] || cleanup_failed=1
  if (( status == 0 && cleanup_failed == 1 )); then status=1; fi
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

TEMP_DIR="$(mktemp -d)"
chmod 700 "$TEMP_DIR"
TARGET_JSON="$TEMP_DIR/target.json"
CONTENT_JSON="$TEMP_DIR/content.json"

create_payload() {
  local operation="$1"
  local command_text="sudo -n /usr/bin/env bash /opt/pawcycle/control/infra/production/production-command-dispatch.sh --operation $operation --target-sha $TARGET_SHA"
  [[ -z "$APPROVED_CONTRACT_FROM_SHA" ]] || command_text+=" --approved-contract-from-sha $APPROVED_CONTRACT_FROM_SHA"
  [[ -z "$APPROVED_CONTROL_SHA" ]] || command_text+=" --approved-control-sha $APPROVED_CONTROL_SHA"
  [[ -z "$APPROVED_MIGRATION_TARGET_SHA" ]] || command_text+=" --approved-migration-target-sha $APPROVED_MIGRATION_TARGET_SHA"
  python3 - "$command_text" "$INSTANCE_ID" "$TARGET_JSON" > "$CONTENT_JSON.tmp" <<'PY'
import hashlib, json, sys
text = sys.argv[1]
instance_id = sys.argv[2]
target_path = sys.argv[3]
payload = {"source": {"sourceType": "TEXT", "text": text}, "output": {"outputType": "TEXT"}}
payload["source"]["textSha256"] = hashlib.sha256(text.encode()).hexdigest()
with open(target_path, "w", encoding="utf-8") as target:
    json.dump({"instanceId": instance_id}, target, separators=(",", ":"))
print(json.dumps(payload, separators=(",", ":")))
PY
  mv -f "$CONTENT_JSON.tmp" "$CONTENT_JSON"
  chmod 600 "$CONTENT_JSON" "$TARGET_JSON"
}

parse_execution() {
  local payload="$1"
  MAX_OUTPUT_BYTES="$MAX_OUTPUT_BYTES" EXECUTION_JSON="$payload" python3 -c 'import base64, json, os
try:
    data=json.loads(os.environ["EXECUTION_JSON"])
    content=data["data"]["content"]
    state=data["data"]["lifecycle-state"]
    code=content.get("exit-code")
    text=content.get("text", "")
    if not isinstance(state, str) or not isinstance(code, int) or not isinstance(text, str): raise ValueError
    if len(text.encode()) > int(os.environ["MAX_OUTPUT_BYTES"]): raise ValueError
    print(state + "\t" + str(code) + "\t" + base64.b64encode(text.encode()).decode())
except Exception as error:
    raise SystemExit("malformed OCI command execution response") from error'
}

run_command() {
  local operation="$1"
  local execution state exit_code encoded
  create_payload "$operation"
  COMMAND_ID="$(oci instance-agent command create --compartment-id "$COMPARTMENT_ID" --content "file://$CONTENT_JSON" --target "file://$TARGET_JSON" --timeout-in-seconds "$COMMAND_TIMEOUT_SECONDS" --region "$REGION" --query data.id --raw-output)"
  [[ "$COMMAND_ID" =~ ^ocid1\.instance-agent-command\.oc1\.[a-z0-9.-]{2,128}$ ]] || die "OCI command id is invalid"
  for ((attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++)); do
    execution="$(oci instance-agent command-execution get --command-id "$COMMAND_ID" --instance-id "$INSTANCE_ID" --region "$REGION" --output json)"
    IFS=$'\t' read -r state exit_code encoded < <(parse_execution "$execution") || die "OCI command execution response is malformed"
    case "$state" in
      ACCEPTED|IN_PROGRESS)
        if (( attempt + 1 < MAX_POLL_ATTEMPTS )); then
          sleep "$POLL_INTERVAL_SECONDS"
        fi
        ;;
      SUCCEEDED)
        [[ "$exit_code" == 0 ]] || die "OCI command succeeded with a nonzero exit code"
        [[ -z "$encoded" ]] || printf '%s' "$encoded" | base64 -d
        return 0
        ;;
      FAILED|TIMED_OUT|CANCELED) die "OCI command execution failed: $state" ;;
      *) die "OCI command execution returned an unknown lifecycle state" ;;
    esac
  done
  die "OCI command execution polling timed out"
}

if [[ "$OPERATION" == deploy ]]; then run_command preflight; fi
run_command "$OPERATION"
