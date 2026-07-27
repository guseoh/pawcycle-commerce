#!/usr/bin/env bash
set -Eeuo pipefail
set +x

APPROVED_AWS_REGION="ap-northeast-2"
AWS_REGION="${PAWCYCLE_ALERT_REGION:-}"
INSTANCE_ID="${PAWCYCLE_ALERT_INSTANCE_ID:-}"
ALERT_EMAIL="${PAWCYCLE_ALERT_EMAIL:-}"
RESOURCE_PREFIX="${PAWCYCLE_ALERT_RESOURCE_PREFIX:-}"
TOPIC_NAME=""
ALARM_NAME=""

die() { printf '%s\n' "ERROR: $*" >&2; exit 1; }
require_command() { command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"; }

validate_inputs() {
  [[ "$AWS_REGION" == "$APPROVED_AWS_REGION" ]] || die "AWS region must be the approved Seoul region"
  [[ "$INSTANCE_ID" =~ ^i-[0-9a-f]{8,17}$ ]] || die "EC2 instance ID format is invalid"
  [[ "$ALERT_EMAIL" =~ ^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,63}$ ]] || die "alert email format is invalid"
  [[ "$RESOURCE_PREFIX" =~ ^[a-z][a-z0-9-]{2,30}$ ]] || die "resource prefix must be 3-31 lowercase letters, digits, or hyphens and start with a letter"
  TOPIC_NAME="${RESOURCE_PREFIX}-ec2-status-check-alerts"
  ALARM_NAME="${RESOURCE_PREFIX}-ec2-status-check-failed"
}

find_topic_arn() {
  aws sns list-topics --region "$AWS_REGION" --query "Topics[?ends_with(TopicArn, ':${TOPIC_NAME}')].TopicArn | [0]" --output text
}

alarm_exists() {
  [[ "$(aws cloudwatch describe-alarms --region "$AWS_REGION" --alarm-names "$ALARM_NAME" --query 'MetricAlarms[0].AlarmName' --output text)" != "None" ]]
}

verify_alarm_contract() {
  local topic_arn="$1"
  local alarm
  alarm="$(aws cloudwatch describe-alarms --region "$AWS_REGION" --alarm-names "$ALARM_NAME" --query "MetricAlarms[?Namespace=='AWS/EC2' && MetricName=='StatusCheckFailed' && Statistic=='Maximum' && Period==\`60\` && EvaluationPeriods==\`2\` && Threshold==\`1\` && ComparisonOperator=='GreaterThanOrEqualToThreshold' && length(AlarmActions)==\`1\` && AlarmActions[0]=='${topic_arn}' && length(OKActions)==\`1\` && OKActions[0]=='${topic_arn}' && length(Dimensions[?Name=='InstanceId' && Value=='${INSTANCE_ID}'])==\`1\`].AlarmName | [0]" --output text)"
  [[ "$alarm" == "$ALARM_NAME" ]] || die "existing alarm does not match the approved StatusCheckFailed contract"
}

ensure_email_subscription() {
  local topic_arn="$1" subscription_arn
  subscription_arn="$(aws sns list-subscriptions-by-topic --region "$AWS_REGION" --topic-arn "$topic_arn" --query "Subscriptions[?Protocol=='email' && Endpoint=='${ALERT_EMAIL}'].SubscriptionArn | [0]" --output text)"
  if [[ "$subscription_arn" == "None" ]]; then
    aws sns subscribe --region "$AWS_REGION" --topic-arn "$topic_arn" --protocol email --notification-endpoint "$ALERT_EMAIL" --return-subscription-arn --query 'SubscriptionArn' --output text >/dev/null
    printf '%s\n' 'SNS email subscription was requested; confirm it before manual alarm-state verification.'
  fi
}
