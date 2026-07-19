# 저장소 온보딩 런북

## 목적

새 환경에서 PawCycle Commerce 저장소를 확인하고, Git Hook, Obsidian, Discord, 역할 브랜치, 검증 명령을 준비하는 절차다.

## 로컬 경로

기준 로컬 경로:

```text
C:\Users\guseo\IdeaProjects\pawcycle-commerce
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
C:\Users\guseo\IdeaProjects\pawcycle-commerce\docs
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

## 역할 브랜치 시작

최신 `main`에서 역할 브랜치를 만든다.

```bash
git switch main
git pull --ff-only
git switch -c ops/tl
```

역할별 브랜치:

```text
spec/po
design/ux
feat/be
feat/fe
test/qa
ops/sre
ops/tl
```

하나의 역할 브랜치에는 하나의 활성 작업만 둔다.

## 역할 브랜치 완료

```bash
git status
git diff --check
git push -u origin <role-branch>
```

PR이 `main`에 병합되면 역할 브랜치를 삭제하고 다음 작업에서 최신 `main` 기준으로 같은 이름을 다시 만든다.

## 검증 명령

현재 존재하는 검증:

```bash
sh scripts/test-commit-message-convention.sh
python -m py_compile .github/scripts/*.py scripts/validate-task-artifacts.py
python scripts/validate-discord-payloads.py
python scripts/validate-obsidian-record.py
```

작업 산출물 검증:

```bash
python scripts/validate-task-artifacts.py --task-id BOOTSTRAP-004 --allow-legacy-without-grade
```

백엔드와 프론트엔드 애플리케이션 프로젝트는 아직 없으므로 Gradle, Next.js, Docker Compose 실행 명령은 없다.

## 보안 확인

```bash
git grep -ni -E '(discord\.com/api/webhooks|BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|password[[:space:]]*[:=]|secret[[:space:]]*[:=]|token[[:space:]]*[:=])'
```

실제 Secret 의심 값이 있으면 값을 출력하거나 복사하지 말고 즉시 중단해 보고한다.
