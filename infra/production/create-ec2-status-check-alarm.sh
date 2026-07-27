#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=ec2-status-check-alarm-common.sh
source "$SCRIPT_DIR/ec2-status-check-alarm-common.sh"

usage() {
  cat <<'EOF'
Usage: create-ec2-status-check-alarm.sh [create|verify]
Required environment variables: PAWCYCLE_ALERT_REGION, PAWCYCLE_ALERT_INSTANCE_ID,
PAWCYCLE_ALERT_EMAIL, PAWCYCLE_ALERT_RESOURCE_PREFIX
EOF
}

command="${1:-create}"
[[ $# -le 1 && ( "$command" == create || "$command" == verify ) ]] || { usage >&2; exit 2; }
require_command aws
validate_inputs
topic_arn="$(find_topic_arn)"
if [[ "$command" == verify ]]; then
  [[ "$topic_arn" != None ]] || die "SNS topic is missing"
  verify_alarm_contract "$topic_arn"
  printf '%s\n' 'StatusCheckFailed alarm contract is configured.'
  exit 0
fi
if [[ "$topic_arn" == None ]]; then
  topic_arn="$(aws sns create-topic --region "$AWS_REGION" --name "$TOPIC_NAME" --query 'TopicArn' --output text)"
fi
ensure_email_subscription "$topic_arn"
if alarm_exists; then
  verify_alarm_contract "$topic_arn"
  printf '%s\n' 'Existing StatusCheckFailed alarm contract is unchanged.'
  exit 0
fi
aws cloudwatch put-metric-alarm --region "$AWS_REGION" --alarm-name "$ALARM_NAME" --alarm-description 'EC2 StatusCheckFailed alert; ALARM and OK notify the dedicated SNS topic.' --namespace AWS/EC2 --metric-name StatusCheckFailed --statistic Maximum --period 60 --evaluation-periods 2 --threshold 1 --comparison-operator GreaterThanOrEqualToThreshold --dimensions "Name=InstanceId,Value=${INSTANCE_ID}" --alarm-actions "$topic_arn" --ok-actions "$topic_arn"
verify_alarm_contract "$topic_arn"
printf '%s\n' 'StatusCheckFailed alarm contract was created.'
