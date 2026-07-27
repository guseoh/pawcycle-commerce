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
subscription_count="$(aws sns list-subscriptions-by-topic --region "$AWS_REGION" --topic-arn "$topic_arn" --query 'length(Subscriptions)' --output text)"
email_subscription_count="$(aws sns list-subscriptions-by-topic --region "$AWS_REGION" --topic-arn "$topic_arn" --query "length(Subscriptions[?Protocol=='email' && Endpoint=='${ALERT_EMAIL}'])" --output text)"
[[ "$subscription_count" == 1 && "$email_subscription_count" == 1 ]] || die "SNS topic has unexpected subscriptions; refusing cleanup"
if alarm_exists; then
  verify_alarm_contract "$topic_arn"
  aws cloudwatch delete-alarms --region "$AWS_REGION" --alarm-names "$ALARM_NAME"
fi
aws sns delete-topic --region "$AWS_REGION" --topic-arn "$topic_arn"
printf '%s\n' 'StatusCheckFailed alarm resources were deleted.'
