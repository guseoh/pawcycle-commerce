# OPS-001 SRE → TL 인수인계

## 전달 목적

OPS-001에서 Discord 협업 알림 실패가 GitHub Actions 실패로 드러나도록 전송 스크립트와 알림 워크플로를 보완한 결과를 Tech Lead에게 전달한다.

## 병합 후 필수 확인

OPS-001 PR이 `main`에 병합된 뒤 다음을 수행한다.

1. GitHub Actions에서 `Collaboration Notification` 워크플로를 연다.
2. 기본 브랜치에서 `Run workflow`로 수동 실행한다.
3. `Send Discord notification` 단계 로그에서 HTTP `2xx` 성공 메시지를 확인한다.
4. Discord 채널에서 연결 테스트 Embed가 실제로 도착했는지 확인한다.
5. 실제 메시지를 확인하기 전에는 Discord 연동 완료로 판단하지 않는다.
6. 연결 검증이 끝난 뒤 UX-001로 진행한다.

예상 성공 로그는 다음과 같다.

```text
Discord 알림 전송 완료: HTTP 204
```

## Repository Validation 알림 확인

OPS-001 병합 후 생성되는 PR에서 다음을 확인한다.

- Repository Validation이 `success`로 끝나면 CI 검증 성공 알림이 전송된다.
- Repository Validation이 성공 외 conclusion으로 끝나면 CI 검증 미통과 알림이 전송된다.
- 알림에는 head branch, head SHA, Actions 실행 URL과 conclusion이 포함된다.
- PR 번호를 확인할 수 있으면 `PR #<번호>`가 포함된다.
- 작업 ID를 확인할 수 없으면 `기록 없음`으로 표시된다.

## 변경된 실패 처리

- `DISCORD_WEBHOOK_URL`이 없으면 알림 전송 단계가 실패한다.
- HTTP `2xx`만 성공으로 처리한다.
- HTTP `400`, `401`, `403`, `404` 등 재시도 불필요 오류는 즉시 실패한다.
- HTTP `429`, `5xx`, 네트워크 오류는 3회까지 재시도한다.
- 재시도 소진 후 실패하면 알림 전송 단계가 실패한다.
- Webhook URL, Secret, 오류 응답 본문은 로그에 출력하지 않는다.

## 실행한 검증 명령

```powershell
git status --short --branch
git diff --check
git diff --stat
py -3 -m unittest scripts.test_send_discord_notification
py -3 scripts/validate-discord-payloads.py
py -3 -m py_compile .github/scripts/build-discord-payload.py .github/scripts/send-discord-notification.py scripts/test_send_discord_notification.py
Write-Output 'OPS-001' | py -3 scripts/validate-task-artifacts.py --from-stdin
C:\Program Files\Git\bin\bash.exe scripts/validate-commit-message.sh --message "fix(discord): 협업 알림 실패 감지와 연결 테스트 추가"
```

검증 결과는 모두 통과했다. PR `#9` 생성 후 Commit and PR conventions와 Discord collaboration notification도 통과했다.

## 남아 있는 제한

- PR 병합 전에는 Repository Secret을 사용하는 실제 Discord 전송 테스트를 완료했다고 주장하지 않는다.
- 실제 Discord 채널 수신 여부는 병합 후 수동 워크플로 실행으로 확인해야 한다.
- Discord 서버, 채널, Webhook 생성이나 Secret 값 변경은 수행하지 않았다.
- Repository Validation 자체의 검증 규칙은 변경하지 않았다.
- 제품·도메인·API 설계, UX-001, DATA-001은 변경하지 않았다.
