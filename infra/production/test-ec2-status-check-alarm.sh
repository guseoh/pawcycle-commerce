#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
COMMON="$SCRIPT_DIR/ec2-status-check-alarm-common.sh"
CREATE="$SCRIPT_DIR/create-ec2-status-check-alarm.sh"
CLEANUP="$SCRIPT_DIR/cleanup-ec2-status-check-alarm.sh"
bash -n "$COMMON" "$CREATE" "$CLEANUP"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf -- "$TEST_ROOT"' EXIT
mkdir -p "$TEST_ROOT/bin"
printf '%s\n' '#!/usr/bin/env bash' 'exit 99' > "$TEST_ROOT/bin/aws"
chmod +x "$TEST_ROOT/bin/aws"

assert_invalid_input() {
  local variable="$1" value="$2"
  if env PATH="$TEST_ROOT/bin:$PATH" PAWCYCLE_ALERT_REGION=ap-northeast-2 PAWCYCLE_ALERT_INSTANCE_ID=i-12345678 PAWCYCLE_ALERT_EMAIL=ops@example.test PAWCYCLE_ALERT_RESOURCE_PREFIX=pawcycle "${variable}=${value}" bash "$CREATE" verify >/dev/null 2>&1; then
    printf 'invalid input was accepted: %s\n' "$variable" >&2
    exit 1
  fi
}

assert_invalid_input PAWCYCLE_ALERT_REGION us-east-1
assert_invalid_input PAWCYCLE_ALERT_INSTANCE_ID invalid-instance
assert_invalid_input PAWCYCLE_ALERT_EMAIL invalid-email
assert_invalid_input PAWCYCLE_ALERT_RESOURCE_PREFIX InvalidPrefix

grep -Fq 'PAWCYCLE_ALERT_REGION' "$COMMON"
grep -Fq 'PAWCYCLE_ALERT_INSTANCE_ID' "$COMMON"
grep -Fq 'PAWCYCLE_ALERT_EMAIL' "$COMMON"
grep -Fq 'PAWCYCLE_ALERT_RESOURCE_PREFIX' "$COMMON"
grep -Fq 'APPROVED_AWS_REGION="ap-northeast-2"' "$COMMON"
grep -Fq 'StatusCheckFailed' "$CREATE"
grep -Fq -- '--period 60' "$CREATE"
grep -Fq -- '--evaluation-periods 2' "$CREATE"
grep -Fq -- '--threshold 1' "$CREATE"
grep -Fq -- '--comparison-operator GreaterThanOrEqualToThreshold' "$CREATE"
grep -Fq -- '--alarm-actions "$topic_arn"' "$CREATE"
grep -Fq -- '--ok-actions "$topic_arn"' "$CREATE"
grep -Fq 'existing alarm does not match the approved StatusCheckFailed contract' "$COMMON"
grep -Fq 'unexpected subscriptions; refusing cleanup' "$CLEANUP"
grep -Fq 'aws sns delete-topic' "$CLEANUP"
printf '%s\n' 'OPS-015 EC2 StatusCheckFailed alarm static contract tests passed'
