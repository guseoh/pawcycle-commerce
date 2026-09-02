# 저장소 온보딩 런북

## 목적

새 환경에서 PawCycle Commerce 저장소를 확인하고, Git Hook, Obsidian, Discord, 역할 브랜치, 검증 명령을 준비하는 절차다.

## 로컬 경로

기준 로컬 경로:

```text
<repository-root>
```

현재 Codex 작업은 위 저장소에서 수행한다.

## Clone 또는 기존 저장소 확인

```bash
git remote -v
git status
git branch --show-current
```

기본 브랜치는 `main`이어야 한다.

## Git Hook 설치

Git Bash:

```bash
sh scripts/setup-git-hooks.sh
```

PowerShell:

```powershell
.\scripts\setup-git-hooks.ps1
```

설정 후 `git config core.hooksPath` 값이 `.githooks`인지 확인한다.

## Obsidian

Vault 경로:

```text
<repository-root>\docs
```

- 데스크톱과 노트북에서 각각 저장소를 clone한다.
- 각 기기에서 `docs`를 Vault로 연다.
- `.obsidian/`은 기기별 설정이므로 저장소에 커밋하지 않는다.
- 문서 동기화는 Git pull과 push로 수행한다.

## Discord

- GitHub Actions Secret 이름은 `DISCORD_WEBHOOK_URL`이다.
- 실제 Webhook URL은 문서, Issue, PR, 로그에 기록하지 않는다.
- 로컬에서는 payload 생성만 검증한다.

```bash
python scripts/validate-discord-payloads.py
```

Secret 설정 후 실제 전송은 PR 생성, 리뷰, CI 결과 같은 GitHub 이벤트로 확인한다. 전송 실패 시 Actions 로그를 확인한다.

## task branch 시작

최신 `main`에서 역할 prefix와 작업 ID를 결합한 task branch를 만든다.

```bash
git switch main
git pull --ff-only
git switch -c <role-prefix>/<TASK-ID>
```

위 명령의 placeholder를 실제 값으로 교체한다. 예:

```bash
git switch -c ops/tl/HARNESS-LEAN-003
```

역할별 브랜치:

```text
spec/po/<TASK-ID>
design/ux/<TASK-ID>
feat/be/<TASK-ID>
feat/fe/<TASK-ID>
test/qa/<TASK-ID>
ops/sre/<TASK-ID>
ops/tl/<TASK-ID>
```

하나의 task branch에는 하나의 활성 작업만 둔다.

## task branch 완료

```bash
git status
<작업 등급에 맞는 필수 검증>
python scripts/validate-task-artifacts.py \
  --task-id <TASK-ID> \
  --task-grade <경량|일반|고위험> \
  --execution-type "저장소 변경"
git diff --check
git diff
git add <선택한 경로>
git commit -m "<type>(<scope>): <한국어 명사형 설명>"
git push -u origin <role-prefix>/<TASK-ID>
gh pr create --base main --head <role-prefix>/<TASK-ID> --body-file <UTF-8-PR-body.md>
```

순서는 상태 확인 → 작업 등급에 맞는 필수 검증 → 산출물 validator → diff 확인 → 선택한 변경만 add → commit → 일반 push → PR이다.

PR이 `main`에 병합되면 열린 PR·고유 commit·사용 중인 worktree가 모두 없고 사용자가 명시 승인한 경우에만 task branch를 삭제한다. 하나라도 충족하지 않으면 삭제하지 않는다. 다음 작업은 최신 `main`에서 새 작업 ID branch를 만든다.

## 검증 명령

현재 존재하는 검증:

```bash
sh scripts/test-commit-message-convention.sh
python -m py_compile .github/scripts/*.py scripts/validate-task-artifacts.py scripts/classify-validation-changes.py
python -m unittest scripts.test_validate_task_artifacts scripts.test_validate_conventions_workflow
python scripts/validate-discord-payloads.py
python scripts/validate-obsidian-record.py
```

작업 산출물 검증:

```bash
python scripts/validate-task-artifacts.py --task-id BOOTSTRAP-004 --allow-legacy-without-grade
```

Backend·Frontend·MySQL·로컬 통합·Production 계약 파일이 존재한다. 현재 작업과 변경 영향에 맞는 실제 검증 명령만 선택해 실행한다.

## 보안 확인

```bash
git grep -ni -E '(discord\.com/api/webhooks|BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|password[[:space:]]*[:=]|secret[[:space:]]*[:=]|token[[:space:]]*[:=])'
```

실제 Secret 의심 값이 있으면 값을 출력하거나 복사하지 말고 즉시 중단해 보고한다.
