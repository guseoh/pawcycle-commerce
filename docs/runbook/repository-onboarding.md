# 저장소 온보딩 런북

## 목적

새 환경에서 PawCycle Commerce의 **저장소 상태, Git 안전장치, Harness 검증과 협업 알림**을 준비하는 최소 절차다.

공통 작업 규칙은 `AGENTS.md`, 위험 기반 Harness는 `docs/runbook/lean-harness.md`, branch 관례는 `CONTRIBUTING.md`를 따른다.

## 저장소 확인

```bash
git remote -v
git status --short --branch
git branch --show-current
git fetch --prune origin
```

기본 브랜치는 `main`이고 실제 작업은 최신 `main`에서 새 task branch로 시작한다.

```bash
git switch main
git pull --ff-only origin main
git switch -c <role-prefix>/<TASK-ID>
```

역할 prefix 예시는 `CONTRIBUTING.md`에서 확인한다. 한 사용자 목적이 여러 영역을 가로지르면 역할 수 때문에 branch나 PR을 분리하지 않는다.

## Git Hook

선택적으로 로컬 commit message 검증을 설치한다.

```bash
sh scripts/setup-git-hooks.sh
```

PowerShell:

```powershell
.\scripts\setup-git-hooks.ps1
```

설치 후 `git config core.hooksPath` 값이 `.githooks`인지 확인한다.

## Discord

GitHub Actions Secret 이름은 `DISCORD_WEBHOOK_URL`이다.

- 실제 Webhook URL을 채팅·문서·Issue·PR·로그에 기록하지 않는다.
- 로컬에서는 실제 전송보다 payload와 redaction 검증을 우선한다.

```bash
python scripts/validate-discord-payloads.py
```

Discord는 보조 알림 채널이며 CI·Review·병합 판정을 대신하지 않는다.

## 병합 PR evidence

과거 `docs/learning/pull-requests/**`는 역사 자료로 유지한다.

새 병합 PR은 GitHub Pull Request 자체를 권위 evidence로 사용한다. 병합 직후 bot이 `main`에 별도 Markdown commit을 추가하는 자동화는 사용하지 않는다.

장기 보존이 필요한 운영·복구·측정 결과만 Harness 조건에 따라 `docs/reports/**`에 기록한다.

## 작업 완료 전 최소 확인

```bash
git status --short --branch
git diff --check
git diff
```

그 다음 현재 변경 영향에 맞는 검사만 선택한다.

Harness 예시:

```bash
python -m py_compile \
  scripts/validate-task-artifacts.py \
  scripts/classify-validation-changes.py \
  .github/scripts/collect-discord-context.py

python -m unittest \
  scripts.test_validate_task_artifacts \
  scripts.test_validate_conventions_workflow \
  scripts.test_discord_context
```

Backend, Frontend, MySQL, Production 계약 검증은 해당 동작을 바꾸는 작업에서만 실행한다. workflow/classifier처럼 CI 선택 의미 자체를 바꾸는 변경은 Repository Validation의 전체 영향 lane을 확인한다.

## PR

PR 본문은 `.github/pull_request_template.md`의 최소 계약을 채운다.

- 작업 ID
- 작업 등급
- 실행 구분
- 목적·포함·제외 범위
- 실행 결과 또는 미실행 이유
- 남은 위험과 복구 경계

보고서·QA·Runbook·ADR·Handoff는 `docs/runbook/lean-harness.md`의 조건을 충족할 때만 만든다.

## GitHub Tool write preflight

GitHub Connector/MCP로 파일을 쓸 때는 매 write 직전에 다음을 다시 확인한다.

1. repository
2. target task branch
3. expected branch HEAD/file SHA
4. create/update/delete 호출의 명시적 `branch`
5. target이 `main`이 아닌지

세부 절차는 `docs/runbook/github-mcp-agent.md`를 따른다.

## Secret 확인

Secret 의심 문자열을 발견하면 값을 복사하거나 출력하지 않고 작업을 중단한다. 저장소 전체 탐색이 필요할 때도 raw Secret을 결과에 노출하지 않는 방식을 사용한다.
