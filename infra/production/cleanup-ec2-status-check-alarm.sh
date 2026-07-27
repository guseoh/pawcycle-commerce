#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=ec2-status-check-alarm-common.sh
source "$SCRIPT_DIR/ec2-status-check-alarm-common.sh"

require_command aws
validate_inputs
topic_arn="$(find_topic_arn)"
if [[ "$topic_arn" == None ]]; then
  alarm_exists && die "alarm exists but its dedicated SNS topic is missing; refusing cleanup"
  printf '%s\n' 'No matching StatusCheckFailed alarm resources were found.'
  exit 0
fi
verify_subscription_contract "$topic_arn"
if alarm_exists; then
  verify_alarm_contract "$topic_arn"
  aws cloudwatch delete-alarms --region "$AWS_REGION" --alarm-names "$ALARM_NAME"
fi
aws sns delete-topic --region "$AWS_REGION" --topic-arn "$topic_arn"
printf '%s\n' 'StatusCheckFailed alarm resources were deleted.'
