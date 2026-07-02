# 협업 자동화 런북(Collaboration Automation Runbook)

## 1. 목적

이 문서는 PawCycle Commerce의 커밋 메시지 규칙, Git Hook, GitHub Actions 검증, Discord 알림, Obsidian PR 기록 자동화를 설명한다.

자동화의 목표는 다음과 같다.

- `main`에 들어가는 커밋과 PR 제목의 형식을 일관되게 유지한다.
- 협업 이벤트를 Discord에 구조화된 Embed로 알린다.
- 병합된 PR을 Obsidian에서 읽기 쉬운 Markdown 기록으로 남긴다.
- Secret과 Webhook URL이 저장소, 로그, 보고서에 노출되지 않게 한다.

## 2. 커밋 메시지 규칙

커밋 메시지와 Pull Request 제목은 Conventional Commits 1.0.0을 기반으로 한다.

기본 형식은 다음과 같다.

```text
<type>(<scope>): <한국어 설명>
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
- 콜론 뒤에는 공백 한 칸을 둔다.
- 제목 끝에는 마침표를 쓰지 않는다.
- 제목은 가능하면 72자 이내로 작성한다.
- 본문과 설명은 기본적으로 한국어로 작성한다.
- `BREAKING CHANGE`, `Refs`, `Closes` 같은 표준 Footer 식별자는 영문을 허용한다.

권장 예시는 다음과 같다.

```text
docs: 프로젝트 문서를 정리한다
ci(discord): PR 알림을 추가한다
fix(workflow): 중복 전송 조건을 수정한다
```

사용하지 않을 예시는 다음과 같다.

```text
update
docs: update docs
CI: 알림 추가
feat 기능 추가
```

## 3. 로컬 Git Hook 설치

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

## 4. GitHub Actions 검증

`Repository Validation` 워크플로는 Pull Request에서 다음을 검증한다.

- PR 제목
- PR에 포함된 커밋 메시지
- 변경 diff의 공백 오류

Discord Secret은 검증 워크플로에서 사용하지 않는다.

검증 실패 시 실패한 커밋 SHA, 현재 메시지, 위반 규칙, 올바른 예시가 Actions 로그에 출력된다.

## 5. Discord Webhook 설정

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

## 6. Discord 알림 이벤트

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
- CI 검증 실패
- `main` 반영 성공

모든 작업 브랜치의 단순 push는 알리지 않는다.

## 7. Discord Embed 디자인

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

| 이벤트 | 색상 |
| --- | --- |
| PR 생성·리뷰 요청 | 파란색 `#3498DB` |
| 리뷰 승인 | 초록색 `#2ECC71` |
| 변경 요청 | 주황색 `#E67E22` |
| CI 성공 | 초록색 `#2ECC71` |
| CI 실패 | 빨간색 `#E74C3C` |
| PR 병합 | 보라색 `#9B59B6` |
| Issue 생성 | 청록색 `#1ABC9C` |
| Issue 종료 | 회색 `#95A5A6` |
| 경고·설정 필요 | 노란색 `#F1C40F` |

Discord에는 전체 diff, 긴 로그, Stack Trace, Secret 값, 개인정보를 넣지 않는다.

## 8. Discord 알림 테스트

Secret이 설정되지 않은 상태에서는 실제 전송을 하지 않는다.

로컬에서는 fixture 기반 payload 검증만 수행한다.

```bash
python scripts/validate-discord-payloads.py
```

Secret 설정 후 실제 테스트가 필요하면 실제 PR이나 CI 성공으로 오해되지 않는 제목을 사용한다.

```text
🧪 PawCycle Commerce 알림 연결 테스트
```

## 9. Obsidian PR 기록

PR이 병합되면 Markdown 기록을 생성한다.

기본 저장 위치는 다음이다.

```text
docs/learning/pull-requests/<연도>/PR-<번호>-<짧은-slug>.md
```

사용자는 저장소 루트 또는 `docs/learning`을 Obsidian Vault로 열 수 있다.

`.obsidian/` 개인 설정은 저장소에 커밋하지 않는다.

## 10. Obsidian 기록 생성 방식

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
docs(obsidian): PR #<번호> 기록을 추가한다
```

자동 커밋 사용자는 `github-actions[bot]`이다.

## 11. 브랜치 보호와 실패 복구

현재 `main` 브랜치 보호 규칙이 자동 커밋을 막는다면 보호 규칙을 약화하지 않는다.

가능한 복구 방법은 다음이다.

1. Actions 로그에서 생성된 파일 경로를 확인한다.
2. 필요하면 워크플로를 Artifact 업로드 방식으로 변경한다.
3. 사용자가 로컬에서 기록 파일을 생성해 PR로 반영한다.
4. 추후 전용 GitHub App 또는 별도 노트 저장소를 검토한다.

자동 커밋 실패는 제품 빌드나 테스트 결과를 성공으로 바꾸거나 실패로 바꾸지 않는다.

## 12. 보안 주의사항

- Secret 값은 저장소에 커밋하지 않는다.
- Webhook URL은 로그와 완료 보고에 출력하지 않는다.
- `.gitignore`는 앞으로 추적되지 않을 파일을 보호하지만, 이미 Git에 추적 중인 파일을 자동 제거하지 않는다.
- 이미 추적 중인 민감 파일이 발견되면 사용자 승인 없이 삭제하거나 기록을 재작성하지 않는다.
- Git 기록에 Secret이 들어갔다면 `.gitignore` 추가만으로 해결되지 않는다.
- 노출된 Secret은 폐기하고 새 값으로 교체해야 한다.

## 13. 로컬 검증 명령

커밋 메시지 예시 검증:

```bash
sh scripts/validate-commit-message.sh --message "docs: 프로젝트 문서를 정리한다"
sh scripts/validate-commit-message.sh --message "ci(discord): PR 알림을 추가한다"
sh scripts/validate-commit-message.sh --message "fix(workflow): 중복 전송 조건을 수정한다"
```

Discord payload 검증:

```bash
python scripts/validate-discord-payloads.py
```

Obsidian 기록 생성 검증:

```bash
python scripts/validate-obsidian-record.py
```

보안 검색:

```bash
git grep -ni -E '(discord\.com/api/webhooks|BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|password[[:space:]]*[:=]|secret[[:space:]]*[:=]|token[[:space:]]*[:=])'
```
