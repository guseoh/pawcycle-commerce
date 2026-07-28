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
cat > "$TEST_ROOT/bin/aws" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s %s\n' "${1:-}" "${2:-}" >> "$FAKE_AWS_CALLS"
if [[ "${1:-}" == sts && "${2:-}" == get-caller-identity ]]; then
  region_count=0
  region_value=""
  arguments=("$@")
  for ((index = 0; index < ${#arguments[@]}; index += 1)); do
    if [[ "${arguments[$index]}" == --region ]]; then
      ((region_count += 1))
      if ((index + 1 < ${#arguments[@]})); then
        region_value="${arguments[$((index + 1))]}"
      fi
    fi
  done
  [[ "$region_count" == 1 && "$region_value" == ap-northeast-2 ]] || {
    printf '%s\n' 'STS call did not receive the approved Seoul region' >&2
    exit 45
  }
  printf 'sts-region %s\n' "$region_value" >> "$FAKE_AWS_CALLS"
fi
case "${FAKE_AWS_SCENARIO:?}:${1:-}:${2:-}" in
  sts-failure:sts:get-caller-identity) exit 43 ;;
  account-mismatch:sts:get-caller-identity) printf '%s\n' '111111111111' ;;
  *:sts:get-caller-identity) printf '%s\n' '000000000000' ;;
  ec2-failure:ec2:describe-instances) exit 44 ;;
  instance-missing:ec2:describe-instances) printf 'None\n' ;;
  *:ec2:describe-instances) printf '%s\n' 'i-12345678' ;;
  query-failure:sns:list-topics) exit 42 ;;
  new:sns:list-topics) printf 'None\n' ;;
  new:sns:list-subscriptions-by-topic) printf 'None\n' ;;
  new:cloudwatch:describe-alarms) [[ "$*" == *'MetricAlarms[0].AlarmName'* ]] && printf 'None\n' || printf '%s\n' 'pawcycle-ec2-status-check-failed' ;;
  new:sns:create-topic) printf '%s\n' 'arn:aws:sns:ap-northeast-2:000000000000:pawcycle-ec2-status-check-alerts' ;;
  new:sns:subscribe) printf '%s\n' 'pending confirmation' ;;
  new:cloudwatch:put-metric-alarm) : ;;
  repeat:sns:list-topics|conflict-alarm:sns:list-topics|unexpected-subscriber:sns:list-topics|actions-disabled:sns:list-topics|one-of-two:sns:list-topics|pending:sns:list-topics|cleanup-success:sns:list-topics) printf '%s\n' 'arn:aws:sns:ap-northeast-2:000000000000:pawcycle-ec2-status-check-alerts' ;;
  repeat:cloudwatch:describe-alarms|cleanup-success:cloudwatch:describe-alarms) printf '%s\n' 'pawcycle-ec2-status-check-failed' ;;
  conflict-alarm:cloudwatch:describe-alarms) [[ "$*" == *'MetricAlarms[0].AlarmName'* ]] && printf '%s\n' 'pawcycle-ec2-status-check-failed' || printf 'None\n' ;;
  actions-disabled:cloudwatch:describe-alarms|one-of-two:cloudwatch:describe-alarms) [[ "$*" == *'MetricAlarms[0].AlarmName'* ]] && printf '%s\n' 'pawcycle-ec2-status-check-failed' || printf 'None\n' ;;
  unexpected-subscriber:cloudwatch:describe-alarms) printf '%s\n' 'pawcycle-ec2-status-check-failed' ;;
  repeat:sns:list-subscriptions-by-topic|actions-disabled:sns:list-subscriptions-by-topic|one-of-two:sns:list-subscriptions-by-topic|cleanup-success:sns:list-subscriptions-by-topic) printf '1\n' ;;
  pending:cloudwatch:describe-alarms) printf '%s\n' 'pawcycle-ec2-status-check-failed' ;;
  pending:sns:list-subscriptions-by-topic) [[ "$*" == *'SubscriptionArn'* ]] && printf 'PendingConfirmation\n' || printf '1\n' ;;
  unexpected-subscriber:sns:list-subscriptions-by-topic) [[ "$*" == *'length(Subscriptions)'* ]] && printf '2\n' || printf '1\n' ;;
  cleanup-success:cloudwatch:delete-alarms|cleanup-success:sns:delete-topic) : ;;
  *) printf 'unexpected fake AWS call: %s %s\n' "${1:-}" "${2:-}" >&2; exit 98 ;;
esac
EOF
chmod +x "$TEST_ROOT/bin/aws"

assert_fake_sts_region_rejection() {
  local expected_case="$1"
  shift
  local output status
  : > "$TEST_ROOT/calls"
  set +e
  output="$(env FAKE_AWS_CALLS="$TEST_ROOT/calls" FAKE_AWS_SCENARIO=new "$TEST_ROOT/bin/aws" sts get-caller-identity "$@" 2>&1)"
  status=$?
  set -e
  [[ "$status" == 45 && "$output" == *'STS call did not receive the approved Seoul region'* ]] \
    || { printf 'invalid STS region arguments were accepted: %s\n' "$expected_case" >&2; exit 1; }
  [[ "$(grep -c '^sts-region ' "$TEST_ROOT/calls" || true)" == 0 ]] \
    || { printf 'invalid STS region produced a success marker: %s\n' "$expected_case" >&2; exit 1; }
}

assert_fake_sts_region_rejection missing --query Account --output text
assert_fake_sts_region_rejection prefix --region ap-northeast-20 --query Account --output text
assert_fake_sts_region_rejection duplicate --region ap-northeast-2 --region ap-northeast-2 --query Account --output text

assert_sts_region_marker() {
  local scenario="$1"
  [[ "$(grep -c '^sts-region ap-northeast-2$' "$TEST_ROOT/calls" || true)" == 1 ]] \
    || { printf 'STS region was not verified exactly once for %s\n' "$scenario" >&2; exit 1; }
}

assert_invalid_input() {
  local variable="$1" value="$2" expected_message="$3"
  local output status
  : > "$TEST_ROOT/calls"
  set +e
  output="$(env PATH="$TEST_ROOT/bin:$PATH" FAKE_AWS_CALLS="$TEST_ROOT/calls" FAKE_AWS_SCENARIO=new PAWCYCLE_ALERT_REGION=ap-northeast-2 PAWCYCLE_ALERT_INSTANCE_ID=i-12345678 PAWCYCLE_ALERT_EMAIL=ops@example.test PAWCYCLE_ALERT_ACCOUNT_ID=000000000000 PAWCYCLE_ALERT_RESOURCE_PREFIX=pawcycle "${variable}=${value}" bash "$CREATE" verify 2>&1)"
  status=$?
  set -e
  if [[ "$status" != 1 || "$output" != *"$expected_message"* ]]; then
    printf 'invalid input was accepted: %s\n' "$variable" >&2
    exit 1
  fi
  [[ ! -s "$TEST_ROOT/calls" ]] || { printf 'invalid input reached AWS: %s\n' "$variable" >&2; exit 1; }
}

assert_invalid_input PAWCYCLE_ALERT_REGION us-east-1 'AWS region must be the approved Seoul region'
assert_invalid_input PAWCYCLE_ALERT_INSTANCE_ID invalid-instance 'EC2 instance ID format is invalid'
assert_invalid_input PAWCYCLE_ALERT_EMAIL invalid-email 'alert email format is invalid'
assert_invalid_input PAWCYCLE_ALERT_RESOURCE_PREFIX InvalidPrefix 'resource prefix must be 3-31 lowercase letters'

assert_scenario() {
  local scenario="$1" expected_status="$2" expected_message="$3" expected_changes="$4"
  local output status changes
  : > "$TEST_ROOT/calls"
  set +e
  output="$(env PATH="$TEST_ROOT/bin:$PATH" FAKE_AWS_CALLS="$TEST_ROOT/calls" FAKE_AWS_SCENARIO="$scenario" PAWCYCLE_ALERT_REGION=ap-northeast-2 PAWCYCLE_ALERT_INSTANCE_ID=i-12345678 PAWCYCLE_ALERT_EMAIL=ops@example.test PAWCYCLE_ALERT_ACCOUNT_ID=000000000000 PAWCYCLE_ALERT_RESOURCE_PREFIX=pawcycle bash "$CREATE" create 2>&1)"
  status=$?
  set -e
  [[ "$status" == "$expected_status" ]] || { printf 'unexpected status for %s: %s (%s)\n' "$scenario" "$status" "$output" >&2; exit 1; }
  [[ "$output" == *"$expected_message"* ]] || { printf 'unexpected output for %s\n' "$scenario" >&2; exit 1; }
  assert_sts_region_marker "$scenario"
  changes="$(grep -E '^(sns create-topic|sns subscribe|cloudwatch put-metric-alarm)$' "$TEST_ROOT/calls" || true)"
  [[ "$changes" == "$expected_changes" ]] || { printf 'unexpected AWS changes for %s: %s\n' "$scenario" "$changes" >&2; exit 1; }
}

assert_scenario new 0 'StatusCheckFailed alarm contract was created.' $'sns create-topic\nsns subscribe\ncloudwatch put-metric-alarm'
assert_scenario repeat 0 'Existing StatusCheckFailed alarm contract is unchanged.' ''
assert_scenario conflict-alarm 1 'existing alarm does not match the approved StatusCheckFailed contract' ''
assert_scenario unexpected-subscriber 1 'SNS topic does not have exactly one approved email subscription' ''
assert_scenario query-failure 42 '' ''
assert_scenario account-mismatch 1 'AWS caller account does not match the approved account' ''
assert_scenario instance-missing 1 'EC2 instance does not exist in the approved region' ''
assert_scenario sts-failure 43 '' ''
assert_scenario ec2-failure 44 '' ''
assert_scenario actions-disabled 1 'existing alarm does not match the approved StatusCheckFailed contract' ''
assert_scenario one-of-two 1 'existing alarm does not match the approved StatusCheckFailed contract' ''

assert_verify_confirmed() {
  local output status changes
  : > "$TEST_ROOT/calls"
  set +e
  output="$(env PATH="$TEST_ROOT/bin:$PATH" FAKE_AWS_CALLS="$TEST_ROOT/calls" FAKE_AWS_SCENARIO=repeat PAWCYCLE_ALERT_REGION=ap-northeast-2 PAWCYCLE_ALERT_INSTANCE_ID=i-12345678 PAWCYCLE_ALERT_EMAIL=ops@example.test PAWCYCLE_ALERT_ACCOUNT_ID=000000000000 PAWCYCLE_ALERT_RESOURCE_PREFIX=pawcycle bash "$CREATE" verify 2>&1)"
  status=$?
  set -e
  [[ "$status" == 0 && "$output" == *'Existing StatusCheckFailed alarm contract is unchanged.'* ]] || exit 1
  assert_sts_region_marker verify-confirmed
  changes="$(grep -E '^(sns create-topic|sns subscribe|cloudwatch put-metric-alarm|cloudwatch delete-alarms|sns delete-topic)$' "$TEST_ROOT/calls" || true)"
  [[ -z "$changes" ]] || exit 1
}
assert_verify_confirmed

assert_verify_pending() {
  local output status changes
  : > "$TEST_ROOT/calls"
  set +e
  output="$(env PATH="$TEST_ROOT/bin:$PATH" FAKE_AWS_CALLS="$TEST_ROOT/calls" FAKE_AWS_SCENARIO=pending PAWCYCLE_ALERT_REGION=ap-northeast-2 PAWCYCLE_ALERT_INSTANCE_ID=i-12345678 PAWCYCLE_ALERT_EMAIL=ops@example.test PAWCYCLE_ALERT_ACCOUNT_ID=000000000000 PAWCYCLE_ALERT_RESOURCE_PREFIX=pawcycle bash "$CREATE" verify 2>&1)"
  status=$?
  set -e
  [[ "$status" == 1 && "$output" == *'SNS email subscription is pending confirmation'* ]] || exit 1
  assert_sts_region_marker verify-pending
  changes="$(grep -E '^(sns create-topic|sns subscribe|cloudwatch put-metric-alarm|cloudwatch delete-alarms|sns delete-topic)$' "$TEST_ROOT/calls" || true)"
  [[ -z "$changes" ]] || exit 1
}
assert_verify_pending

assert_cleanup_success() {
  local output status changes
  : > "$TEST_ROOT/calls"
  set +e
  output="$(env PATH="$TEST_ROOT/bin:$PATH" FAKE_AWS_CALLS="$TEST_ROOT/calls" FAKE_AWS_SCENARIO=cleanup-success PAWCYCLE_ALERT_REGION=ap-northeast-2 PAWCYCLE_ALERT_INSTANCE_ID=i-12345678 PAWCYCLE_ALERT_EMAIL=ops@example.test PAWCYCLE_ALERT_ACCOUNT_ID=000000000000 PAWCYCLE_ALERT_RESOURCE_PREFIX=pawcycle bash "$CLEANUP" 2>&1)"
  status=$?
  set -e
  [[ "$status" == 0 && "$output" == *'StatusCheckFailed alarm resources were deleted.'* ]] || exit 1
  assert_sts_region_marker cleanup-success
  changes="$(grep -E '^(cloudwatch delete-alarms|sns delete-topic)$' "$TEST_ROOT/calls" || true)"
  [[ "$changes" == $'cloudwatch delete-alarms\nsns delete-topic' ]] || exit 1
}
assert_cleanup_success

assert_cleanup_stops_before_delete() {
  local output status changes
  : > "$TEST_ROOT/calls"
  set +e
  output="$(env PATH="$TEST_ROOT/bin:$PATH" FAKE_AWS_CALLS="$TEST_ROOT/calls" FAKE_AWS_SCENARIO=unexpected-subscriber PAWCYCLE_ALERT_REGION=ap-northeast-2 PAWCYCLE_ALERT_INSTANCE_ID=i-12345678 PAWCYCLE_ALERT_EMAIL=ops@example.test PAWCYCLE_ALERT_ACCOUNT_ID=000000000000 PAWCYCLE_ALERT_RESOURCE_PREFIX=pawcycle bash "$CLEANUP" 2>&1)"
  status=$?
  set -e
  [[ "$status" == 1 && "$output" == *'SNS topic does not have exactly one approved email subscription'* ]] || exit 1
  assert_sts_region_marker cleanup-stops-before-delete
  changes="$(grep -E '^(cloudwatch delete-alarms|sns delete-topic)$' "$TEST_ROOT/calls" || true)"
  [[ -z "$changes" ]] || exit 1
}
assert_cleanup_stops_before_delete

grep -Fq 'PAWCYCLE_ALERT_REGION' "$COMMON"
grep -Fq 'PAWCYCLE_ALERT_INSTANCE_ID' "$COMMON"
grep -Fq 'PAWCYCLE_ALERT_EMAIL' "$COMMON"
grep -Fq 'PAWCYCLE_ALERT_ACCOUNT_ID' "$COMMON"
grep -Fq 'PAWCYCLE_ALERT_RESOURCE_PREFIX' "$COMMON"
grep -Fq 'APPROVED_AWS_REGION="ap-northeast-2"' "$COMMON"
grep -Fq 'StatusCheckFailed' "$CREATE"
grep -Fq -- '--period 60' "$CREATE"
grep -Fq -- '--evaluation-periods 2' "$CREATE"
grep -Fq -- '--datapoints-to-alarm 2' "$CREATE"
grep -Fq -- '--threshold 1' "$CREATE"
grep -Fq -- '--comparison-operator GreaterThanOrEqualToThreshold' "$CREATE"
grep -Fq -- '--alarm-actions "$topic_arn"' "$CREATE"
grep -Fq -- '--ok-actions "$topic_arn"' "$CREATE"
grep -Fq 'existing alarm does not match the approved StatusCheckFailed contract' "$COMMON"
grep -Fq 'does not have exactly one approved email subscription' "$COMMON"
grep -Fq 'aws sns delete-topic' "$CLEANUP"
printf '%s\n' 'OPS-015 EC2 StatusCheckFailed alarm static contract tests passed'
