# OPS-001 SRE 작업 보고서

## 작업 정보

- 작업 ID: `OPS-001`
- 역할: Platform/SRE
- 기준 브랜치: `main`
- 작업 브랜치: `ops/sre`
- PR 대상: `main`
- 선행 PR: `#8 fix(validation): UX와 DATA 작업 ID 인식 추가`
- 자동 병합: 하지 않음

## 작업 목적

Discord 협업 알림이 실제로 전송되지 않아도 GitHub Actions가 성공으로 처리하는 문제를 수정한다.

Discord Webhook 연결을 수동으로 검증할 수 있는 실행 경로를 추가하고, Runbook에 적힌 Repository Validation 성공·미통과 알림을 실제 워크플로에 구현한다.

## 시작 전 상태

- PR `#8`: 2026-07-03 10:06:11Z 병합 확인
- `origin/main`: `UX`, `DATA` 작업 ID 지원과 PR #8 기록 커밋 존재 확인
- 기존 `ops/sre`: 로컬과 원격에 활성 브랜치 없음 확인
- 새 `ops/sre`: 최신 `origin/main`에서 생성
- 시작 전 작업 트리: clean
- force push: 하지 않음
- reset과 history rewrite: 하지 않음

## 확인한 기존 실패 은폐 동작

`send-discord-notification.py`는 다음 상황에서 종료 코드 `0`을 반환했다.

- `DISCORD_WEBHOOK_URL`이 없음
- HTTP `400`, `401`, `403`, `404` 같은 재시도 불필요 오류
- HTTP `429`, `5xx`, 네트워크 오류가 재시도 후에도 계속됨

그 결과 Discord 메시지가 실제로 도착하지 않아도 `Discord collaboration notification` 체크가 성공할 수 있었다.

## 변경 범위

- `.github/workflows/notify-collaboration.yml`
- `.github/scripts/send-discord-notification.py`
- `.github/scripts/build-discord-payload.py`
- `.github/fixtures/discord/ci-failure.json`
- `.github/fixtures/discord/manual-test.json`
- `scripts/test_send_discord_notification.py`
- `docs/runbook/collaboration-automation.md`
- `docs/reports/OPS-001/sre-report.md`
- `docs/handoffs/OPS-001/sre-to-tl.md`

## 변경하지 않은 범위

- Discord 서버 또는 채널 생성
- Webhook URL 재발급
- Secret 값 조회 또는 출력
- GitHub 브랜치 보호 정책
- Repository Validation의 제품 검증 규칙
- Obsidian 자동 기록 구조
- UX-001 작업
- DATA-001 작업
- 제품·도메인·API 설계
- `backend/**`, `frontend/**`, `infra/**`
- 신규 외부 의존성
- PR #8 변경 재작성

## 종료 코드 정책

- `DISCORD_WEBHOOK_URL`이 없으면 종료 코드 `1`을 반환한다.
- HTTP `2xx`만 성공으로 처리한다.
- HTTP `400`, `401`, `403`, `404` 등 재시도 불필요 `4xx`는 즉시 실패하고 종료 코드 `1`을 반환한다.
- HTTP `429`는 제한된 횟수만 재시도한다.
- HTTP `5xx`는 제한된 횟수만 재시도한다.
- 네트워크 오류는 제한된 횟수만 재시도한다.
- 모든 재시도 소진 후에는 종료 코드 `1`을 반환한다.
- 성공 시 HTTP 상태를 포함한 성공 메시지를 남긴다.
- Webhook URL, Secret, 오류 응답 본문은 출력하지 않는다.

## 재시도 정책

- 기본 재시도 횟수는 기존과 같은 3회다.
- 재시도 간격은 기존 방식과 같은 짧은 선형 대기이며 최대 6초로 제한한다.
- `429`, `5xx`, 네트워크 오류만 재시도한다.
- 재시도로 해결되지 않는 `4xx`는 즉시 실패한다.

## 수동 테스트 방식

`Collaboration Notification` 워크플로에 `workflow_dispatch`를 추가했다.

수동 실행은 다음을 수행한다.

- 기본 브랜치의 승인된 스크립트만 checkout한다.
- 사용자 입력으로 Webhook URL을 받지 않는다.
- `DISCORD_WEBHOOK_URL` Repository Secret을 사용한다.
- `build-discord-payload.py`의 `test` 이벤트로 연결 테스트 Embed를 만든다.
- 저장소, 실행자, 브랜치, 커밋, 실행 URL과 연결 테스트 상태를 포함한다.
- 전송 실패 시 Actions 실행이 실패한다.

PR 병합 전에는 GitHub Repository Secret을 사용하는 실제 수동 전송 테스트를 완료했다고 주장하지 않는다. 이 PR의 워크플로 변경은 병합 뒤 기본 브랜치에서 실행되어야 실제 Secret을 사용하는 수동 테스트 경로가 된다.

## CI 알림 구현 방식

`Collaboration Notification` 워크플로에 `workflow_run` 이벤트를 추가했다.

- 감시 대상 워크플로는 `Repository Validation`이다.
- 완료 상태에서만 실행된다.
- conclusion이 `success`면 `ci_success` 이벤트를 전송한다.
- 성공 이외의 conclusion은 `ci_failure` 이벤트로 CI 검증 미통과 알림을 전송한다.
- PR 번호를 이벤트에서 확인할 수 있으면 `PR #<번호>`로 포함한다.
- head branch, head SHA, Actions 실행 URL과 conclusion을 포함한다.
- 작업 ID는 head branch에서 감지할 수 있을 때만 사용하고, 확인할 수 없으면 `기록 없음`으로 둔다.
- `Collaboration Notification` 자체는 감시하지 않아 재귀 실행을 만들지 않는다.

## 기존 동작 보존

- PR 생성
- Ready for review 전환
- PR 재오픈
- 리뷰어 요청
- PR 새 커밋 반영
- 리뷰 승인
- 변경 요청
- 일반 리뷰 제출
- PR 병합
- 병합 없는 PR 종료
- Issue 생성
- Issue 재오픈
- Issue 담당자 지정
- Issue 종료
- `main` push
- PR #8의 `UX`, `DATA` 작업 ID 감지 패턴

일반 역할 브랜치의 단순 push는 계속 알리지 않는다.

## 검증 결과

| 검증 | 명령 | 결과 |
| --- | --- | --- |
| 브랜치와 작업 트리 확인 | `git status --short --branch` | 통과 |
| 공백 오류 확인 | `git diff --check` | 통과 |
| 변경 통계 확인 | `git diff --stat` | 통과 |
| Discord 전송 단위 테스트 | `py -3 -m unittest scripts.test_send_discord_notification` | 통과 |
| Discord payload fixture 회귀 | `py -3 scripts/validate-discord-payloads.py` | 통과 |
| Python 문법 검사 | `py -3 -m py_compile .github/scripts/build-discord-payload.py .github/scripts/send-discord-notification.py scripts/test_send_discord_notification.py` | 통과 |
| OPS-001 산출물 확인 | `Write-Output 'OPS-001' \| py -3 scripts/validate-task-artifacts.py --from-stdin` | 통과 |
| 커밋 메시지 확인 | `C:\Program Files\Git\bin\bash.exe scripts/validate-commit-message.sh --message "fix(discord): 협업 알림 실패 감지와 연결 테스트 추가"` | 통과 |
| workflow_dispatch 확인 | `Select-String`으로 `workflow_dispatch` 존재 확인 | 통과 |
| Repository Validation workflow_run 확인 | `Select-String`으로 `workflow_run`과 `Repository Validation` 존재 확인 | 통과 |
| Collaboration Notification 재귀 방지 확인 | `workflow_run.workflows` 값이 `Repository Validation`임을 확인 | 통과 |
| Secret 출력 패턴 확인 | OPS-001 변경 파일에서 Webhook URL literal과 Secret 출력 의심 패턴 확인 | 통과 |
| PR #8 작업 ID 패턴 유지 | `UX`, `DATA` 포함 확인 | 통과 |
| 미완성 표기 확인 | 미완성 placeholder 검색 | 통과 |
| PR 생성 후 CI | `gh pr checks 9 --watch --interval 5` | 통과. Commit and PR conventions, Discord collaboration notification 성공 |

## 실제 Discord 연결 테스트 상태

PR 병합 전에는 실제 Repository Secret을 사용하는 수동 전송 테스트를 완료할 수 없다.

병합 전 로컬 검증은 네트워크 없는 단위 테스트와 payload fixture 검증으로 제한했다. 실제 Discord 채널 수신 여부는 OPS-001 PR 병합 후 기본 브랜치의 `Collaboration Notification` 워크플로를 수동 실행해 확인해야 한다.

## PR 병합 후 필요한 검증 절차

1. GitHub Actions에서 `Collaboration Notification` 워크플로를 연다.
2. `Run workflow`로 기본 브랜치에서 수동 실행한다.
3. `Send Discord notification` 로그에서 HTTP `2xx` 성공 메시지를 확인한다.
4. Discord 채널에서 연결 테스트 Embed가 실제 수신됐는지 확인한다.
5. 이후 PR에서 `Repository Validation` 성공 알림이 수신되는지 확인한다.
6. 실패하는 검증을 가진 테스트 PR 또는 안전한 재실행으로 CI 검증 미통과 알림이 수신되는지 확인한다.

## 위험과 제한

- 이 PR만으로 실제 Discord 채널 수신을 보장했다고 판단하지 않는다.
- Repository Secret이 잘못 등록됐거나 Discord Webhook이 폐기된 경우 병합 후 수동 테스트가 실패한다.
- `workflow_run` 이벤트에서 PR 번호가 없는 실행은 PR 번호를 표시하지 않는다.
- 작업 ID는 workflow_run 이벤트에서 확인 가능한 값만 사용하며, 알 수 없으면 `기록 없음`으로 표시한다.
- Discord 전송 단계가 실패하면 협업 알림 체크가 실패하므로 Secret 설정 문제는 즉시 드러난다.

## Git 결과

- 브랜치: `ops/sre`
- 주요 변경 커밋: `b332b28aeb4d299a66ee395cda264acaf1f83f53`
- 주요 변경 커밋 메시지: `fix(discord): 협업 알림 실패 감지와 연결 테스트 추가`
- push: `origin/ops/sre` 반영 완료
- PR: `#9` 생성 완료
- 자동 병합: 하지 않음

보고서 자신을 최종 갱신하는 커밋 SHA는 재귀적으로 기록하지 않는다.

## PR 상태

- PR 번호: `#9`
- PR 제목: `fix(discord): 협업 알림 실패 감지와 연결 테스트 추가`
- PR 방향: `ops/sre` → `main`
- PR 상태: Open, Ready for review
- mergeable: MERGEABLE
- Commit and PR conventions: 통과
- Discord collaboration notification: 통과
- 자동 병합: 하지 않음
