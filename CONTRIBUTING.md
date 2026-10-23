# Contributing

## 기준

작업 전에는 루트와 수정 경로의 `AGENTS.md`, 필요한 승인 계약, `docs/runbook/lean-harness.md`를 확인한다. 역할 책임이 필요한 경우 `docs/roles/**`, 실행 절차가 필요한 경우 `.agents/skills/**`를 참고한다.

## Task branch

상시 유지 브랜치는 `main`(안정 버전)과 `dev`(개발 통합) 두 개다. 저장소 변경은 최신 `dev`에서 별도 임시 task branch를 생성하고, **임시 branch → `dev` PR**로 독립 검증·리뷰·승인 병합한다. 여러 작업은 서로 다른 임시 브랜치/PR에서 병렬 수행한다. 통합 `dev`를 `main`에 반영할 때는 **`dev → main` 별도 PR**로 검증하고 사용자의 병합 승인을 받는다. `main`과 `dev`에 작업 파일을 직접 수정하지 않는다.

**병합 후 정리:** `dev`로 병합되고, 같은 저장소의 PR HEAD SHA가 그대로이며, 브랜치가 보호되지 않고 다른 열린 PR이 참조하지 않는 **임시 브랜치만** `.github/workflows/delete-merged-task-branch.yml`로 자동 삭제한다. 단순 종료(미병합)·Draft·열린 PR에는 적용하지 않는다. `dev → main` PR에서는 `dev`를 절대 자동 삭제하지 않으며 `main`도 유지한다. 삭제 자동화가 실패하거나 실행되지 않았다면 브랜치 상태와 검증 로그를 확인해 필요한 경우 별도 조치한다. GitHub 저장소의 전역 **Automatically delete head branches** 옵션은 `dev`까지 삭제할 수 있으므로 사용하지 않는다.

역할이 하나로 명확할 때는 다음 prefix를 권장한다.

| 역할 | 권장 prefix |
| --- | --- |
| Product Planner | `spec/po/` |
| UX/UI Designer | `design/ux/` |
| Backend Engineer | `feat/be/` |
| Frontend Engineer | `feat/fe/` |
| QA Engineer | `test/qa/` |
| Platform/SRE | `ops/sre/` |
| Tech Lead / Harness | `ops/tl/` |

여러 영역을 가로지르는 coherent work unit은 목적을 잘 드러내는 별도 branch 이름을 사용할 수 있다. branch 이름 자체보다 **명시적인 non-main target과 승인된 범위**가 우선이다.

GitHub file write Tool에서는 branch 인자를 생략하지 않는다. 일반 작업의 direct `main` write는 금지하며 PR merge를 사용한다.

## Commit / PR 제목

Conventional Commit 형태를 사용한다.

```text
<type>(<scope>): <설명>
<type>: <설명>
```

허용 type은 `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `build`, `ci`, `chore`, `perf`, `revert`다. 한국어의 짧고 구체적인 설명을 권장하지만 언어와 문장 종결 형태는 CI safety gate로 강제하지 않는다. 제목 끝에는 마침표를 붙이지 않는다.

## PR

필수 정보는 네 구획으로 제한한다.

```text
작업
- 작업 ID / 등급 / 실행 구분

변경
- 목적 / 포함 범위 / 제외 범위

검증
- 실행 결과 / 실패·미실행

위험
- 남은 위험 / 복구 경계
```

중요한 결정, 외부 계약 영향, QA, migration, reviewer 한계는 실제로 필요한 경우에만 추가한다. 보고서·Handoff·Runbook·ADR도 `lean-harness.md`의 조건을 만족할 때만 작성한다.

## 검증

가장 작은 관련 검사에서 시작해 변경 영향에 따라 확대한다. metadata 형식 오류는 병합을 막을 수 있지만 관련 code test가 불필요하게 실행되지 못하도록 만들지 않는 것을 원칙으로 한다.

실행하지 못한 검증, reviewer 미실행, 남은 위험은 명시한다.

## Secret / Production / Merge

Secret, 인증 정보, Webhook URL, private key, 개인정보와 원시 운영 값을 저장소·PR·로그에 넣지 않는다.

저장소 변경과 실제 운영 실행은 별도 승인 경계다. 특정 PR 병합은 사용자가 명시적으로 위임한 경우 최신 HEAD·CI·review를 확인한 뒤 수행할 수 있지만 자동 병합 정책으로 확대하지 않는다.
