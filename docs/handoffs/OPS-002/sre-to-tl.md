# OPS-002 SRE → TL 인수인계

## 전달 목적

OPS-002에서 Discord Webhook 요청에 명시적 `User-Agent`를 추가한 결과와 PR 병합 후 운영 확인 절차를 Tech Lead에게 전달한다.

## 변경 요약

Discord 전송 스크립트의 `build_request()`가 다음 헤더를 포함한다.

```text
Content-Type: application/json
User-Agent: DiscordBot (https://github.com/guseoh/pawcycle-commerce, 1.0)
```

`User-Agent`는 공개 저장소 URL과 정적 버전만 포함한다. Webhook URL, Secret, 사용자 개인정보, 실행별 동적 값은 포함하지 않는다.

## 확인한 실패 증거

GitHub Actions `Collaboration Notification` 실행에서 다음 실패를 확인했다.

- run ID: `28654703785`
- event: `push`
- branch: `main`
- head SHA: `28ea9d8e54c7bb623078e8b860f46a85484da498`
- conclusion: `failure`
- failed job: `Discord collaboration notification`

확인한 로그:

```text
Discord 알림 전송 실패: HTTP 403
Process completed with exit code 1.
```

Webhook URL, 오류 응답 본문, Secret 값은 확인하거나 기록하지 않았다.

## 병합 후 필수 확인

PR이 `main`에 병합된 뒤 다음을 수행한다.

1. GitHub 저장소에서 `Actions`를 연다.
2. `Collaboration Notification` 워크플로를 선택한다.
3. `Run workflow`를 선택한다.
4. `Use workflow from: main`을 유지한다.
5. `Run workflow`를 실행한다.
6. 실행 로그에서 `Send Discord notification` 단계를 연다.
7. HTTP `2xx` 성공 메시지를 확인한다.
8. Discord 채널에서 연결 테스트 Embed가 실제로 도착했는지 확인한다.

성공 조건은 다음 두 가지를 모두 만족하는 것이다.

```text
GitHub Actions:
Discord 알림 전송 완료: HTTP 2xx

Discord 채널:
PawCycle Commerce 알림 연결 테스트 Embed 수신
```

두 조건을 모두 확인하기 전에는 Discord 운영 확인 완료로 판단하지 않는다.

## 403 재발 시 확인 순서

수정 후에도 HTTP `403`이 발생하면 Secret 값이나 Webhook URL을 로그 또는 문서에 출력하지 말고 다음 순서로 확인한다.

1. Discord Webhook이 삭제되거나 비활성화됐는지 확인한다.
2. `DISCORD_WEBHOOK_URL` Secret이 현재 Webhook URL로 갱신돼 있는지 확인한다.
3. Webhook 대상 채널이 변경되거나 삭제됐는지 확인한다.
4. Discord 서버와 채널 권한이 변경됐는지 확인한다.
5. 필요한 경우 Webhook을 재발급하고 GitHub Repository Secret을 갱신한다.

## 실행한 로컬 검증

```powershell
git status --short --branch
git diff --check
git diff --stat
py -3 -m unittest scripts.test_send_discord_notification
py -3 scripts/validate-discord-payloads.py
py -3 -m py_compile .github/scripts/build-discord-payload.py .github/scripts/send-discord-notification.py scripts/test_send_discord_notification.py
Write-Output 'OPS-002' | py -3 scripts/validate-task-artifacts.py --from-stdin
C:\Program Files\Git\bin\bash.exe scripts/validate-commit-message.sh --message "fix(discord): Webhook 요청 User-Agent 추가"
```

검증 결과는 모두 통과했다.

## 남아 있는 제한

- PR 병합 전에는 실제 Discord 채널 수신을 완료했다고 판단하지 않는다.
- Secret 값 조회와 직접 수정은 수행하지 않았다.
- Discord 서버, 채널, Webhook 생성 또는 재발급은 수행하지 않았다.
- Discord payload 디자인과 알림 이벤트 종류는 변경하지 않았다.
- GitHub Actions 전체 구조와 Obsidian 기록 구조는 변경하지 않았다.
