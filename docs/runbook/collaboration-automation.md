# 협업 자동화 런북

## 목적

이 문서는 PawCycle Commerce의 GitHub 협업 자동화가 **어떤 사건을 자동화하고 어떤 판단은 자동화하지 않는지** 설명한다.

공통 작업 등급, PR evidence, AI Prompt, 실제 운영 실행 관문은 `docs/runbook/lean-harness.md`가 권위 원본이다. branch 관례와 로컬 Git 절차는 `CONTRIBUTING.md`, GitHub Tool 쓰기 안전은 `docs/runbook/github-mcp-agent.md`를 따른다.

## Git과 Pull Request

- 새 저장소 작업은 최신 `main`에서 task branch를 만든다.
- 일반 파일 create/update/delete에서 target branch를 생략하지 않는다.
- direct `main` write는 일반 작업 경로가 아니다.
- commit과 PR 제목은 Conventional Commit 구조를 사용한다. 한국어·명사형 문체는 권장사항이지 safety gate가 아니다.
- PR 본문은 `.github/pull_request_template.md`의 최소 4구획을 사용한다.
- 사용자가 특정 PR 병합을 명시적으로 위임한 경우에만 최신 HEAD·CI·review·mergeable 상태를 다시 확인하고 병합한다.

## Repository Validation

`Repository Validation`은 code event에서 다음 두 축을 병렬로 확인한다.

1. `Commit and PR conventions`
   - PR title/body encoding
   - 작업 ID·등급·실행 구분과 최소 PR evidence
   - commit message 구조
   - whitespace
2. changed-path classifier와 선택된 component validation
   - Harness
   - Backend + MySQL
   - Frontend
   - Production contract

classifier와 component lane은 PR metadata 검증이 끝날 때까지 기다리지 않는다. metadata 오류는 최종 `Application validation`을 실패시킬 수 있지만 관련 제품 테스트 자체를 막지 않는다.

GitHub Ruleset/Branch Protection에서 required check를 지정할 때는 workflow 이름이 아니라 최종 집계 check 이름인 **`Application validation`**을 사용한다.

변경 분류 원칙은 다음과 같다.

- Backend 내부 → Backend
- Backend 외부 HTTP contract → Backend + Frontend
- Frontend → Frontend
- Production runtime/contract → Production
- Harness 문서·역할·Skill·task validator → Harness
- classifier 또는 workflow처럼 lane 선택 의미를 바꾸는 파일 → fail-safe 전체
- 미분류 → fail-safe 전체

`edited` event는 별도 `PR Metadata Validation`에서 title/body만 다시 검사하며 component CI를 재실행하지 않는다.

## Production release side effect

Production image publish workflow는 모든 `main` push event를 받되, 첫 job에서 push 전후 commit의 전체 changed path를 직접 판정한다. 실제 image build/push는 다음 runtime 경로가 하나라도 바뀐 경우에만 진행한다.

- `backend/**`
- `frontend/**`
- `infra/production/**`

README, AGENTS, Runbook, workflow 자체 같은 non-runtime 변경은 Production image build/push와 자동 deploy chain을 시작하지 않아야 한다. event-level `paths` 필터에만 의존하지 않고 checkout된 commit diff를 job 수준에서 판정해 대규모 diff에서도 runtime 변경 누락 위험을 줄인다.

`workflow_dispatch`는 별도 명시적 image publish 경로이며 실제 Production 실행 승인을 자동으로 의미하지 않는다.

## Discord 알림

Discord는 GitHub 상태를 알리는 보조 채널이다.

- PR/Review/Issue/Repository Validation 이벤트를 제한된 context로 요약한다.
- `DISCORD_WEBHOOK_URL`은 GitHub Actions Secret으로만 관리한다.
- `pull_request_target` 또는 `workflow_run`에서 PR head의 임의 코드를 Secret과 함께 실행하지 않는다.
- task ID를 확인할 수 없으면 추측하지 않고 `기록 없음`으로 표시한다.
- 알림 성공 여부가 CI·Review·병합 판정을 대신하지 않는다.

로컬에서는 실제 Webhook 전송 대신 payload와 redaction 회귀만 검증한다.

## 병합 PR 기록

GitHub Pull Request 자체를 병합 이력과 리뷰·CI evidence의 권위 원본으로 사용한다.

과거 `docs/learning/pull-requests/**` 기록은 역사 자료로 보존하지만, **병합 직후 GitHub Actions가 새 Markdown을 만들고 `main`에 직접 commit/push하는 자동화와 전용 exporter/fixture/validator는 retire한다.**

이유는 다음과 같다.

- GitHub PR과 별도 Markdown에 같은 사실을 중복 저장했다.
- merge 뒤 bot commit이 `main` HEAD를 다시 움직였다.
- 문서 commit이 다른 `main push` workflow의 side effect를 만들 수 있었다.
- 새 task-ID family가 생길 때 별도 parser까지 갱신해야 했다.

새 병합 기록은 PR 본문, Review, CI run과 merge commit을 그대로 evidence로 사용한다. 장기 보존이 필요한 별도 실행 증거만 `docs/reports/**`에 남긴다.

## 검증 명령

Harness 변경에서 관련된 최소 회귀를 선택한다.

```bash
python -m py_compile \
  scripts/validate-task-artifacts.py \
  scripts/classify-validation-changes.py \
  .github/scripts/collect-discord-context.py

python -m unittest \
  scripts.test_validate_task_artifacts \
  scripts.test_validate_conventions_workflow \
  scripts.test_discord_context

python scripts/validate-discord-payloads.py
```

workflow 또는 classifier 자체를 바꾼 경우 Repository Validation에서 영향 lane 전체가 다시 선택되는지 확인한다.

## Secret과 실패 처리

- Secret, token, private key, Webhook URL과 원시 운영 값을 문서·PR·로그에 넣지 않는다.
- 자동화가 실패하면 같은 명령을 반복하기보다 첫 실패 원인을 분류한다.
- 외부 서비스 제한은 `미실행/제한`으로 기록하고 성공으로 해석하지 않는다.
- 자동화 때문에 예상하지 않은 Cloud/Production side effect 가능성이 생기면 추가 write를 멈추고 실제 실행 여부부터 확인한다.
