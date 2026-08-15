# OPS-AUTO-010 Production Backend 다중 채널 알림

## 목적과 경계

OPS-AUTO-009의 Observability 최종 진단 결과를 입력으로 받아 비정상 상태를 Discord와 Slack Incoming Webhook으로 전달한다.

- `NORMAL`: 알림 없이 성공 종료
- `BACKEND_DOWN`, `OBSERVABILITY_DEGRADED`, `DEGRADED`, `UNKNOWN`: Discord와 Slack을 독립적으로 모두 시도
- malformed 또는 신뢰할 수 없는 입력: `UNKNOWN`으로 fail-closed 처리
- 알림 전송 결과는 Production 상태 판정을 변경하지 않음
- auto-healing, restart, cron/systemd 활성화, 실제 Secret 등록은 이 저장소 변경 범위가 아님

실제 webhook 생성·Secret 배치·운영 전송은 별도 고위험 사용자 승인 후 진행한다. webhook URL은 명령 출력·문서·payload·Git에 기록하지 않는다.

## 저장소 검증

저장소 root에서 focused 검증만 실행한다.

```bash
bash -n infra/production/dispatch-backend-state-alert.sh
bash infra/production/test-dispatch-backend-state-alert.sh
python -m py_compile infra/production/send-slack-notification.py infra/production/test_send_slack_notification.py
python -m unittest infra.production.test_send_slack_notification
python scripts/test_send_discord_notification.py
git diff --check
```

전체 Production Compose/recovery/auth lifecycle은 로컬에서 반복하지 않고 최종 HEAD의 Repository Validation을 회귀 기준으로 사용한다.

## 운영 artifact 준비

병합 후 실제 운영 승인이 있을 때만 Observability EC2에서 진행한다. `APPROVED_SHA`는 검토·병합이 끝난 승인 commit SHA여야 한다. 실행 중 기존 control checkout HEAD를 변경하지 않는다.

Discord sender는 같은 디렉터리의 contract helper를 import하므로 dispatcher만 단독으로 `/tmp`에 복사하면 안 된다. 승인 commit에서 dispatcher, Slack sender, Discord sender와 두 Discord contract helper를 함께 materialize한다.

아래 운영 명령은 같은 승인된 shell session에서 순서대로 수행한다. 첫 실패에서 즉시 중단하고, 예측 가능한 `/tmp` 디렉터리를 미리 삭제·재생성하지 않는다. `mktemp -d`로 현재 실행 계정이 소유한 private 작업 디렉터리를 원자적으로 만들고, 생성 직후 `EXIT` trap을 등록해 성공·실패와 관계없이 이번 실행의 디렉터리만 정리한다.

```bash
set -Eeuo pipefail
umask 077

APPROVED_SHA='<approved-merge-sha>'
ALERT_ROOT="$(mktemp -d /tmp/pawcycle-backend-alert.XXXXXX)"
cleanup_alert_root() {
  if [[ -n "${ALERT_ROOT:-}" && "$ALERT_ROOT" == /tmp/pawcycle-backend-alert.* ]]; then
    rm -rf -- "$ALERT_ROOT"
  fi
}
trap cleanup_alert_root EXIT

REPO_RAW="https://raw.githubusercontent.com/guseoh/pawcycle-commerce/${APPROVED_SHA}"

chmod 700 "$ALERT_ROOT"

curl --fail --silent --show-error --location --connect-timeout 5 --max-time 20 \
  "$REPO_RAW/infra/production/dispatch-backend-state-alert.sh" \
  -o "$ALERT_ROOT/dispatch-backend-state-alert.sh"
curl --fail --silent --show-error --location --connect-timeout 5 --max-time 20 \
  "$REPO_RAW/infra/production/send-slack-notification.py" \
  -o "$ALERT_ROOT/send-slack-notification.py"
curl --fail --silent --show-error --location --connect-timeout 5 --max-time 20 \
  "$REPO_RAW/.github/scripts/send-discord-notification.py" \
  -o "$ALERT_ROOT/send-discord-notification.py"
curl --fail --silent --show-error --location --connect-timeout 5 --max-time 20 \
  "$REPO_RAW/.github/scripts/discord-message-contract.py" \
  -o "$ALERT_ROOT/discord-message-contract.py"
curl --fail --silent --show-error --location --connect-timeout 5 --max-time 20 \
  "$REPO_RAW/.github/scripts/discord-payload-limits.py" \
  -o "$ALERT_ROOT/discord-payload-limits.py"

chmod 500 \
  "$ALERT_ROOT/dispatch-backend-state-alert.sh" \
  "$ALERT_ROOT/send-slack-notification.py" \
  "$ALERT_ROOT/send-discord-notification.py" \
  "$ALERT_ROOT/discord-message-contract.py" \
  "$ALERT_ROOT/discord-payload-limits.py"

bash -n "$ALERT_ROOT/dispatch-backend-state-alert.sh"
python3 -m py_compile \
  "$ALERT_ROOT/send-slack-notification.py" \
  "$ALERT_ROOT/send-discord-notification.py" \
  "$ALERT_ROOT/discord-message-contract.py" \
  "$ALERT_ROOT/discord-payload-limits.py"
```

필요하면 실행 전에 각 파일의 `git hash-object` 결과를 승인 commit의 Git blob SHA와 대조한다. Secret 값은 artifact 검증과 무관하므로 이 단계에서 출력하거나 파일에 저장하지 않는다.

## 최종 진단 결과 파일 생성

Production snapshot을 Observability EC2로 전달한 뒤 localhost Prometheus와 결합한 **최종 결과를 fresh file로 보존**한다. 이전 실행의 고정 경로를 재사용하지 않는다. 결과 파일은 위에서 만든 private `ALERT_ROOT` 내부에 `mktemp`로 새로 만들고 mode `0600`으로 제한한다.

진단의 non-zero exit도 별도로 보존한다. 비정상 상태 자체의 non-zero와 결과 파일 생성·쓰기 실패를 같은 의미로 해석하지 않는다. fresh file이 비어 있거나 부분적으로만 기록되면 dispatcher가 입력 검증에서 `UNKNOWN`으로 fail-closed 처리한다.

```bash
DIAG_SCRIPT=/tmp/pawcycle-diagnose-backend-state.sh
PRODUCTION_RESULT=/tmp/pawcycle-production-diagnostic
FINAL_RESULT="$(mktemp "$ALERT_ROOT/diagnostic-result.XXXXXX")"
chmod 600 "$FINAL_RESULT"

if bash "$DIAG_SCRIPT" \
  --scope observability \
  --prometheus-url http://127.0.0.1:9090 \
  --production-result "$PRODUCTION_RESULT" \
  > "$FINAL_RESULT"; then
  DIAG_RC=0
else
  DIAG_RC=$?
fi

cat "$FINAL_RESULT"
printf 'diagnostic_exit=%s\n' "$DIAG_RC"
```

`NORMAL`만 diagnostic exit `0`이다. 비정상 상태의 diagnostic non-zero는 알림 dispatcher 실행을 생략할 이유가 아니다.

## Discord + Slack dispatch

`DISCORD_WEBHOOK_URL`, `SLACK_WEBHOOK_URL`은 승인된 runtime Secret 경계에서 환경 변수로 주입되어 있어야 한다. 값을 명령에 직접 쓰거나 `echo`, `env`, shell trace 등으로 출력하지 않는다. dispatcher의 non-zero는 알림 전달 실패를 뜻할 수 있으므로 `set -e`에 의해 종료되기 전에 명시적으로 보존한다.

```bash
if PAWCYCLE_DISCORD_SENDER="$ALERT_ROOT/send-discord-notification.py" \
  PAWCYCLE_SLACK_SENDER="$ALERT_ROOT/send-slack-notification.py" \
  bash "$ALERT_ROOT/dispatch-backend-state-alert.sh" \
    --result "$FINAL_RESULT"; then
  ALERT_RC=0
else
  ALERT_RC=$?
fi

printf 'alert_dispatch_exit=%s\n' "$ALERT_RC"
```

판정 계약은 다음과 같다.

- `NORMAL`: 진단기의 결정표와 정확히 일치하는 `READY/up` 조합일 때만 두 sender를 호출하지 않고 dispatcher exit `0`
- 비정상 상태: 진단기의 전체 상태 결정표와 입력 세 필드가 일치할 때 해당 상태로 Discord와 Slack 모두 시도
- NUL, symlink, unreadable/non-regular file, 형식 손상, 허용 값만으로 구성됐더라도 상태 결정표와 모순되는 입력: `UNKNOWN`으로 fail-closed
- 두 채널 전달 성공: dispatcher exit `0`
- 한 채널 이상 전달 실패: dispatcher non-zero

따라서 `diagnostic_exit`은 Production/Observability 상태를, `alert_dispatch_exit`은 Notification delivery 결과를 뜻한다. 둘을 같은 상태로 해석하지 않는다.

## 정리와 후속 경계

`ALERT_ROOT` 생성 직후 등록한 `EXIT` trap이 정상 종료와 중간 실패 모두에서 이번 실행의 private alert 디렉터리를 제거한다. `FINAL_RESULT`도 그 안에 있으므로 별도 고정 경로를 삭제하지 않는다. 수동 정리를 추가로 실행할 필요가 없다.

Production snapshot과 diagnostic script 정리는 OPS-AUTO-009 Runbook 경계를 따른다. 실제 Slack App/Incoming Webhook 생성, Discord/Slack Secret 등록, cron/systemd timer 활성화, 중복 알림 억제는 별도 승인·후속 작업으로 판단한다.
