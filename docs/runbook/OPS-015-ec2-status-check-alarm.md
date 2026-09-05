# OPS-015 EC2 StatusCheckFailed SNS 이메일 알림 Runbook

> Superseded by `ARCH-016` for active OCI operations. 본문은 historical evidence로 보존하며 현재 알림 절차로 사용하지 않는다.

## 범위

이 Runbook은 `ap-northeast-2`의 단일 EC2 instance에서 `AWS/EC2` `StatusCheckFailed >= 1`이 60초 period로 2회 연속 평가될 때, 전용 SNS topic으로 ALARM과 OK 전이를 모두 전송하도록 준비한다. 저장소 스크립트는 AWS CLI 명령을 구성할 뿐이며, 이 문서와 CI는 AWS 리소스를 생성·변경·삭제하거나 이메일 구독을 승인하지 않는다.

CloudWatch Agent, CPU·메모리·디스크·custom metric, 앱·컨테이너 상태, 로그·dashboard, Lambda·Discord, 자동 복구와 다른 알림은 범위 밖이다.

## 입력과 생성

사용자는 실제 값이 shell history·공유 로그·저장소에 남지 않는 환경에서 다음 값을 제공한다. 이메일, 계정 ID, ARN, instance ID를 PR·이슈·Runbook 실행 로그에 복사하지 않는다.

```bash
export PAWCYCLE_ALERT_REGION=ap-northeast-2
export PAWCYCLE_ALERT_INSTANCE_ID=<EC2 instance ID>
export PAWCYCLE_ALERT_EMAIL=<alert recipient email>
export PAWCYCLE_ALERT_ACCOUNT_ID=<12-digit approved AWS account ID>
export PAWCYCLE_ALERT_RESOURCE_PREFIX=<dedicated lowercase prefix>
bash infra/production/create-ec2-status-check-alarm.sh create
```

입력 검증은 서울 region, EC2 instance ID·12자리 account ID·email 형식, 전용 lowercase resource prefix를 강제한다. 모든 create·verify·cleanup은 전역 AWS CLI region 설정에 의존하지 않고 `PAWCYCLE_ALERT_REGION`에서 검증한 서울 region을 STS를 포함한 AWS CLI 호출에 명시적으로 전달한다. 변경 전에 `sts get-caller-identity`의 Account와 입력 account ID가 일치하는지, 지정 region에 입력 EC2 instance가 존재하는지 확인한다. 조회 실패·불일치는 어떤 리소스 변경도 하지 않고 중단한다. 생성은 이어서 기존 alarm·topic·subscription을 읽기 전용으로 모두 확인한다. 동일 계약이면 변경하지 않고 성공하며, alarm 불일치, alarm만 있고 topic 없음, topic만 있거나 예상 밖·중복 subscription이 있으면 어떤 생성·구독·alarm 변경도 하지 않고 중단한다. 세 리소스가 모두 없을 때만 topic → email subscription → `DatapointsToAlarm=2` alarm 순서로 생성한다.

SNS confirmation email을 수신한 사용자는 AWS가 제공한 확인 절차를 완료한다. 확인 전에는 구독이 pending 상태이며 알림 수신을 성공으로 판단하지 않는다. 확인 후 다음 명령으로 alarm 계약만 다시 확인한다.

```bash
bash infra/production/create-ec2-status-check-alarm.sh verify
```

## 상태 확인과 수동 ALARM·OK 검증

AWS Console에서 해당 alarm의 metric이 `AWS/EC2` `StatusCheckFailed`, threshold `>= 1`, period `60`, evaluation periods·DatapointsToAlarm 모두 `2`, `ActionsEnabled=true`, InstanceId dimension인지 확인한다. ALARM action과 OK action이 모두 같은 전용 SNS topic인지 확인한다. `verify`는 단일 승인 email subscription이 confirmed일 때만 성공하며 pending은 성공으로 처리하지 않는다. 화면·CLI 출력에 실제 ARN, account ID, email을 남기지 않는다.

구독 확인 후 사용자만 다음 순서로 수동 state 전이를 검증한다. 이는 실제 장애를 만들지 않고 CloudWatch alarm state만 수동 변경한다. 실행 전후 CloudTrail과 SNS delivery를 사용자가 안전한 운영 기록에서 확인한다.

```bash
aws cloudwatch set-alarm-state --region "$PAWCYCLE_ALERT_REGION" \
  --alarm-name "${PAWCYCLE_ALERT_RESOURCE_PREFIX}-ec2-status-check-failed" \
  --state-value ALARM --state-reason 'OPS-015 manual ALARM notification verification'

aws cloudwatch set-alarm-state --region "$PAWCYCLE_ALERT_REGION" \
  --alarm-name "${PAWCYCLE_ALERT_RESOURCE_PREFIX}-ec2-status-check-failed" \
  --state-value OK --state-reason 'OPS-015 manual OK notification verification'
```

각 전이 뒤 수신 이메일의 상태와 SNS delivery를 확인한다. 실제 `StatusCheckFailed` metric이 다음 평가에서 다시 상태를 결정하므로, 수동 state는 장애 원인 해결 증거나 지속 상태로 해석하지 않는다.

## 증상과 첫 확인 절차

알림이 없거나 예상과 다른 알림이면 다음 순서로 확인한다.

1. region·instance ID·전용 prefix가 의도한 대상인지 확인한다.
2. SNS subscription이 confirmed이고 대상 email이 맞는지 AWS Console에서 확인한다.
3. alarm의 metric, threshold, period, evaluation count, ALARM·OK action을 확인한다.
4. EC2 Status Checks와 CloudWatch alarm history를 확인한다.
5. 계정·ARN·email을 제거한 오류 요약만 운영 기록에 남긴다.

## 완화 조치

실제 `StatusCheckFailed` ALARM은 instance·EBS·네트워크 상태를 별도 운영 절차로 진단한다. 이 스크립트는 자동 복구를 수행하지 않는다. 계약 불일치나 예상 밖 SNS 구독이 있으면 삭제·덮어쓰지 말고 사용자/Tech Lead에 에스컬레이션한다.

## 정리와 롤백

더 이상 필요 없고 전용 topic에 확인된 입력 email 하나만 연결돼 있으며 다른 workload가 사용하지 않음을 사용자가 확인한 경우에만 실행한다.

```bash
bash infra/production/cleanup-ec2-status-check-alarm.sh
```

정리 스크립트는 account·instance 검증 뒤 SNS email subscription 하나만 확인한다. alarm이 있으면 정확한 승인 계약을 확인한 뒤 alarm을 먼저 삭제하고 topic을 삭제한다. alarm 없이 전용 topic과 단일 승인 subscription만 남은 부분 생성 상태도 topic을 삭제해 정리한다. topic에 다른 subscription이 있거나 alarm만 남아 topic이 없거나 alarm 계약이 불일치하면 중단한다. 삭제 후 Console에서 alarm, topic, subscription이 남지 않았는지 확인한다. 삭제는 이메일 알림을 복구하지 못할 수 있으므로 재생성이 필요하면 생성 절차와 email confirmation을 처음부터 다시 수행한다.

## 에스컬레이션과 보존할 증거

추가 IAM, Agent, Lambda, custom metric, 자동 복구 또는 다른 알림 채널이 필요하면 이 작업 범위를 확장하지 말고 별도 승인을 받는다. 실제 적용 기록에는 시간, ALARM·OK 전이 수신 여부, subscription confirmation 여부, 정리 여부만 비민감하게 남긴다.

## 저장소 롤백

AWS 리소스를 실행하지 않은 저장소 변경의 결함은 PR 미병합 또는 일반 revert PR로 되돌린다. `reset`, `rebase`, force push는 사용하지 않는다.
