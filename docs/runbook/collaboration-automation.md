# 협업 자동화 런북(Collaboration Automation Runbook)

## 1. 목적

이 문서는 PawCycle Commerce의 역할 브랜치, 커밋 메시지 규칙, Git Hook, GitHub Actions 검증, Discord 알림, Obsidian PR 기록 자동화를 설명한다.

자동화의 목표는 다음과 같다.

- `main`에 들어가는 커밋과 PR 제목의 형식을 일관되게 유지한다.
- 작업 보고서와 역할 인수인계가 PR 산출물로 남도록 확인한다.
- 협업 이벤트를 Discord에 구조화된 Embed로 알린다.
- 병합된 PR을 Obsidian에서 읽기 쉬운 Markdown 기록으로 남긴다.
- Secret과 Webhook URL이 저장소, 로그, 보고서에 노출되지 않게 한다.

## 2. 역할 브랜치

작업마다 긴 브랜치 이름을 만들지 않고 다음 역할 브랜치만 사용한다.

| 브랜치 | 담당 역할 |
| --- | --- |
| `main` | 기준 브랜치 |
| `spec/po` | Product Planner |
| `design/ux` | UX/UI Designer |
| `feat/be` | Backend Engineer |
| `feat/fe` | Frontend Engineer |
| `test/qa` | QA Engineer |
| `ops/sre` | Platform/SRE |
| `ops/tl` | Tech Lead와 공통 저장소 작업 |

역할 브랜치는 영구 통합 브랜치가 아니다.

```text
최신 main
→ 역할 브랜치 생성
→ 역할의 한 작업 수행
→ commit
→ push
→ PR
→ main 병합
→ 역할 브랜치 삭제
→ 다음 작업에서 같은 이름 재생성
```

하나의 역할 브랜치에는 하나의 활성 작업만 둔다. Squash Merge 이후에는 기존 역할 브랜치를 계속 사용하지 않는다.

## 3. commit·push 정책

필수 검증을 통과하고 작업 보고서와 인수인계가 작성되면 Codex는 commit과 push를 수행한다.

다음 상황에서는 commit과 push를 중단한다.

- 필수 검증 실패
- 해결되지 않은 충돌
- 다른 작업 변경 혼입
- Secret 노출 의심
- 원격 브랜치 상태 불명확
- destructive Git 작업 필요
- 사용자의 명시적 금지

Pull Request 병합은 사용자가 최종 검토 후 수행한다. Codex는 자동 병합하지 않는다.

## 4. 커밋 메시지와 PR 제목 규칙

커밋 메시지와 Pull Request 제목은 Conventional Commits 1.0.0을 기반으로 한다.

기본 형식은 다음과 같다.

```text
<type>(<scope>): <한국어 명사형 설명>
```

`scope`는 선택 사항이다.

허용 타입은 다음이다.

- `feat`
- `fix`
- `docs`
- `style`
- `refactor`
- `test`
- `build`
- `ci`
- `chore`
- `perf`
- `revert`

규칙은 다음과 같다.

- `type`과 `scope`는 영문 소문자로 작성한다.
- 설명에는 한글이 최소 한 글자 이상 포함되어야 한다.
- 설명은 명사형으로 끝난다.
- `~한다`, `~했다`, `~합니다`, `~하였다`, `~됩니다` 같은 서술형 종결을 쓰지 않는다.
- 콜론 뒤에는 공백 한 칸을 둔다.
- 제목 끝에는 마침표를 쓰지 않는다.
- 제목은 가능하면 72자 이내로 작성한다.

권장 예시는 다음과 같다.

```text
docs: 프로젝트 문서 정리
docs(harness): 역할별 보고 체계 추가
ci(discord): 협업 알림 워크플로 구성
fix(obsidian): PR 기록 감지 오류 수정
```

사용하지 않을 예시는 다음과 같다.

```text
docs: 프로젝트 문서를 정리한다
ci(discord): 알림을 추가합니다
fix(obsidian): 오류를 수정했다
feat: 기능을 구현하였다
```

자동화가 생성하는 커밋도 가능한 한 같은 규칙을 따른다. 예외적으로 Git이 생성하는 Merge commit과 Revert commit은 검증기가 허용한다.

## 5. 로컬 Git Hook 설치

로컬 커밋 전에 메시지를 검증하려면 다음을 실행한다.

```bash
sh scripts/setup-git-hooks.sh
```

Windows PowerShell에서는 다음을 사용할 수 있다.

```powershell
.\scripts\setup-git-hooks.ps1
```

설정 스크립트는 다음 Git 설정을 적용한다.

```bash
git config core.hooksPath .githooks
```

검증 기준은 `scripts/validate-commit-message.sh` 하나로 유지한다. 로컬 Hook과 GitHub Actions는 같은 스크립트를 호출한다.

## 6. GitHub Actions 검증

`Repository Validation` 워크플로는 Pull Request에서 다음을 검증한다.

- PR 제목
- PR에 포함된 커밋 메시지
- PR 본문에서 작업 ID 확인
- `docs/reports/<작업 ID>/`의 작업 보고서 확인
- `docs/handoffs/<작업 ID>/`의 역할 인수인계 확인
- 변경 diff의 공백 오류

Discord Secret은 검증 워크플로에서 사용하지 않는다.

검증 실패 시 실패한 커밋 SHA, 현재 메시지, 위반 규칙, 올바른 예시가 Actions 로그에 출력된다.

`Collaboration Notification` 워크플로는 `Repository Validation` 완료 이벤트를 감시한다.

- 결론이 `success`이면 CI 검증 성공 알림을 보낸다.
- 성공 이외의 완료 결론은 CI 검증 미통과 알림을 보낸다.
- 알림에는 head branch, head SHA, Actions 실행 URL과 conclusion을 포함한다.
- 작업 ID를 확인할 수 없으면 `기록 없음`으로 표시하며 임의로 추측하지 않는다.
- `Collaboration Notification` 자신은 감시하지 않는다.

## 7. Discord Webhook 설정

Codex와 자동화는 Discord Webhook URL을 생성하거나 추측하지 않는다.

사용자는 다음 순서로 GitHub Actions Secret을 등록한다.

1. Discord 서버에 협업 알림용 채널을 만든다.
2. 채널 설정에서 Incoming Webhook을 만든다.
3. Webhook URL을 복사한다.
4. GitHub 저장소로 이동한다.
5. `Settings`를 연다.
6. `Secrets and variables`를 연다.
7. `Actions`를 선택한다.
8. `New repository secret`을 선택한다.
9. 이름에 `DISCORD_WEBHOOK_URL`을 입력한다.
10. 값에 Discord Webhook URL을 입력한다.

Webhook 이름은 Discord 설정에서 `PawCycle Bot`을 권장한다.

Webhook URL은 채팅, Issue, PR, 커밋, 로그, 문서에 출력하지 않는다.

## 8. Discord 알림 이벤트

Discord 알림은 단순 텍스트가 아니라 Rich Embed로 전송한다.

알림 대상은 다음이다.

- PR 생성
- Draft에서 리뷰 가능 상태로 전환
- 리뷰어 요청
- 새 커밋 반영
- 리뷰 승인
- 변경 요청
- 일반 리뷰 코멘트
- PR 병합
- 병합 없는 PR 종료
- Issue 생성
- Issue 재오픈
- 담당자 지정
- Issue 종료
- CI 검증 성공
- CI 검증 미통과
- `main` 반영 성공

모든 작업 브랜치의 단순 push는 알리지 않는다.

## 9. Discord Embed 디자인

공통 필드는 다음이다.

- 저장소
- 작업 ID
- 작업자 또는 리뷰어
- 브랜치
- 대상
- 짧은 커밋 SHA
- 상태
- 검증 결과

공통 설정은 다음이다.

- `username`: `PawCycle Bot`
- `allowed_mentions`: `{"parse":[]}`
- Footer: `PawCycle Commerce · GitHub Actions` 또는 이벤트 유형별 Footer
- Timestamp: GitHub 이벤트 발생 시각 또는 생성 시각

상태별 색상은 `.github/scripts/build-discord-payload.py`에서 한곳에 관리한다.

Discord에는 전체 diff, 긴 로그, Stack Trace, Secret 값, 개인정보를 넣지 않는다.

## 10. Discord 알림 테스트

Secret이 설정되지 않은 상태에서는 실제 전송을 하지 않는다.

로컬에서는 fixture 기반 payload 검증만 수행한다.

```bash
python scripts/validate-discord-payloads.py
```

`DISCORD_WEBHOOK_URL` Secret이 없거나 Discord가 오류를 반환하면 알림 전송 단계는 실패한다. 전송 스크립트는 Webhook URL과 오류 응답 본문을 출력하지 않는다.

Secret 설정 후 실제 연결 테스트가 필요하면 GitHub Actions에서 `Collaboration Notification` 워크플로를 수동 실행한다.

```text
Actions → Collaboration Notification → Run workflow
```

수동 실행은 `test` 이벤트 payload를 만들고 실제 `DISCORD_WEBHOOK_URL` Secret으로 전송한다. 성공 로그에는 HTTP 2xx 상태가 표시되어야 하며 Discord 채널에서 연결 테스트 Embed가 실제로 수신되어야 한다.

```text
Discord 알림 전송 완료: HTTP 204
```

다음 로그는 실패로 처리된다.

```text
Discord 알림 실패: DISCORD_WEBHOOK_URL Secret이 설정되지 않음
Discord 알림 전송 실패: HTTP 404
Discord 알림 전송 실패: 제한된 재시도 후 포기
```

## 11. Obsidian PR 기록

PR이 병합되면 Markdown 기록을 생성한다.

기본 저장 위치는 다음이다.

```text
docs/learning/pull-requests/<연도>/PR-<번호>-<짧은-slug>.md
```

파일명 slug는 저장소 파일명 규칙을 따르기 위해 영문 소문자, 숫자, 하이픈만 사용한다.

사용자는 저장소 루트 또는 `docs/learning`을 Obsidian Vault로 열 수 있다. `.obsidian/` 개인 설정은 저장소에 커밋하지 않는다.

## 12. Obsidian 기록 생성 방식

`Record Merged Pull Request` 워크플로는 PR이 실제 병합된 경우에만 실행된다.

보안 원칙은 다음이다.

- PR head 코드를 checkout하지 않는다.
- base 브랜치인 `main`만 checkout한다.
- PR에서 변경된 스크립트를 실행하지 않는다.
- PR 본문과 제목은 Markdown에 안전하게 기록한다.
- 파일명에 사용할 수 없는 문자는 제거한다.
- 같은 PR 기록이 이미 존재하면 중복 생성하지 않는다.

자동 기록 커밋 메시지는 다음 형식이다.

```text
docs(obsidian): PR #<번호> 기록 추가
```

자동 커밋 사용자는 `github-actions[bot]`이다.

## 13. 브랜치 보호와 실패 복구

현재 `main` 브랜치 보호 규칙이 자동 커밋을 막는다면 보호 규칙을 약화하지 않는다.

가능한 복구 방법은 다음이다.

1. Actions 로그에서 생성된 파일 경로를 확인한다.
2. 필요하면 워크플로를 Artifact 업로드 방식으로 변경한다.
3. 사용자가 로컬에서 기록 파일을 생성해 PR로 반영한다.
4. 추후 전용 GitHub App 또는 별도 노트 저장소를 검토한다.

자동 커밋 실패는 제품 빌드나 테스트 결과를 성공으로 바꾸거나 실패로 바꾸지 않는다.

## 14. 보안 주의사항

- Secret 값은 저장소에 커밋하지 않는다.
- Webhook URL은 로그와 완료 보고에 출력하지 않는다.
- `.gitignore`는 앞으로 추적되지 않을 파일을 보호하지만, 이미 Git에 추적 중인 파일을 자동 제거하지 않는다.
- 이미 추적 중인 민감 파일이 발견되면 사용자 승인 없이 삭제하거나 기록을 재작성하지 않는다.
- Git 기록에 Secret이 들어갔다면 `.gitignore` 추가만으로 해결되지 않는다.
- 노출된 Secret은 폐기하고 새 값으로 교체해야 한다.

## 15. 로컬 검증 명령

커밋 메시지 예시 검증:

```bash
sh scripts/test-commit-message-convention.sh
```

개별 메시지 검증:

```bash
sh scripts/validate-commit-message.sh --message "docs: 프로젝트 문서 정리"
sh scripts/validate-commit-message.sh --message "ci(harness): 역할별 산출물 검증 추가"
```

작업 산출물 검증:

```bash
printf '%s\n' "BOOTSTRAP-004" | python scripts/validate-task-artifacts.py --from-stdin
```

Discord payload 검증:

```bash
python scripts/validate-discord-payloads.py
```

Discord 전송 동작 검증:

```bash
python -m unittest scripts.test_send_discord_notification
```

Obsidian 기록 생성 검증:

```bash
python scripts/validate-obsidian-record.py
```

보안 검색:

```bash
git grep -ni -E '(discord\.com/api/webhooks|BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|password[[:space:]]*[:=]|secret[[:space:]]*[:=]|token[[:space:]]*[:=])'
```
