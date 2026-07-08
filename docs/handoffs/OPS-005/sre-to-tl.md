# OPS-005 SRE to TL 인수인계

## 전달 목적

OPS-005에서 Tech Lead 역할 문서, 산출물 검증기, QA 독립 검증 기준, PR/보고서/인수인계 템플릿을 강화한 내용을 TL에게 전달한다.

## 대상 역할

- Tech Lead

## 입력 문서

- `AGENTS.md`
- `docs/roles/tech-lead.md`
- `.agents/skills/tech-lead/SKILL.md`
- `docs/qa/README.md`
- `.github/pull_request_template.md`
- `docs/reports/task-report-template.md`
- `docs/handoffs/handoff-template.md`
- `scripts/validate-task-artifacts.py`
- `docs/reports/OPS-005/sre-report.md`

## 완료된 작업

- Tech Lead 역할 문서와 Skill을 추가했다.
- Tech Lead 역할 문서와 Skill에 AI Tech Lead 보조 역할 문서 표현을 추가했다.
- `validate-task-artifacts.py`가 현재 task-id의 마크다운 필수 섹션을 검사하도록 강화했다.
- `scripts/test_validate_task_artifacts.py`에 표준 라이브러리 `unittest` 기반 테스트 14개를 추가했다.
- CodeRabbit 리뷰 thread 8건을 확인하고 반영했다.
- 기존 하이픈 테스트 파일은 언더스코어 테스트 파일을 실행하는 호환 entry point로 유지했다.
- QA 필요 여부, QA 문서 경로, QA 생략 사유 기준을 QA README, PR 템플릿, 보고서 템플릿에 추가했다.
- AI 리뷰 반영 여부와 미반영 이유를 PR 템플릿에 추가했다.
- 보고서와 인수인계 템플릿에 미실행 검증 사유, 남은 위험, 다음 역할 검증 포인트를 추가했다.

## 사용 가능한 결과

- 후속 PR에서 `Write-Output '<TASK-ID>' | py -3 scripts/validate-task-artifacts.py --from-stdin`로 보고서와 인수인계 필수 섹션을 검증할 수 있다.
- 기능 구현 PR은 QA 독립 검증 필요 여부를 PR 또는 보고서에 남겨야 한다.
- Tech Lead는 PR 최종 확인에서 Proposed/Approved 오표기, 검증 미실행 사유, QA 필요 여부, 남은 위험을 확인할 수 있다.
- 검증기는 표 헤더와 구분선만 있는 섹션을 의미 있는 콘텐츠로 인정하지 않는다.

## 관련 파일

- `docs/roles/tech-lead.md`
- `.agents/skills/tech-lead/SKILL.md`
- `scripts/validate-task-artifacts.py`
- `scripts/test_validate_task_artifacts.py`
- `scripts/test-validate-task-artifacts.py`
- `.github/pull_request_template.md`
- `docs/reports/task-report-template.md`
- `docs/handoffs/handoff-template.md`
- `docs/qa/README.md`
- `README.md`
- `docs/reports/OPS-005/sre-report.md`

## 인수 조건과 추적성

| OPS-005 요구 | 확인 파일 |
| --- | --- |
| Tech Lead 역할 목적과 판단 기준 | `docs/roles/tech-lead.md` |
| Tech Lead 실행 절차 | `.agents/skills/tech-lead/SKILL.md` |
| 산출물 섹션 검증 | `scripts/validate-task-artifacts.py` |
| 검증기 테스트 14 tests | `scripts/test_validate_task_artifacts.py` |
| QA 필요/불필요 기준 | `docs/qa/README.md`, `.github/pull_request_template.md` |
| 보고서/인수인계 품질 기준 | `docs/reports/task-report-template.md`, `docs/handoffs/handoff-template.md` |

## 확정된 결정

- 현재 task-id의 보고서와 인수인계만 검증한다.
- `--task-id`, `--from-stdin`, `--root` 인터페이스를 유지한다.
- QA 문서가 필요 없는 작업도 생략 사유를 남긴다.
- AI 리뷰와 AI Tech Lead 보조 역할은 검토와 권고를 돕는다. 최종 승인과 병합 결정은 사용자가 수행한다.

## 미확정 결정

- 없음.

## 승인 필요 항목

- TL은 새 Tech Lead 문서가 사용자 승인을 대체하지 않는다는 경계를 확인해야 한다.
- TL은 후속 PR에서 새 필수 섹션이 과도하거나 누락되는 경우 alias 또는 템플릿 조정 필요 여부를 판단해야 한다.

## 다음 역할의 입력

- 이 PR의 diff
- `docs/reports/OPS-005/sre-report.md`
- GitHub Actions `Repository Validation` 결과
- CodeRabbit/Codex Review 결과가 있다면 해당 미반영 항목과 이유

## 지켜야 할 규칙

- Proposed 문서를 사용자 승인 없이 Approved로 표시하지 않는다.
- 검증 실패 또는 미실행 검증을 이유 없이 통과 처리하지 않는다.
- QA 독립 검증 대상 기능은 `docs/qa/<TASK-ID>/test-plan.md` 또는 `retest-result.md`를 남긴다.
- 자동 병합하지 않는다.

## 적용·실행 방법

```powershell
Write-Output 'OPS-005' | py -3 scripts/validate-task-artifacts.py --from-stdin
py -3 scripts/test_validate_task_artifacts.py
```

검증기는 다음을 확인한다.

- 보고서 Markdown 파일 존재
- 인수인계 Markdown 파일 존재
- 보고서와 인수인계 필수 섹션 존재
- 검증 결과 섹션의 실제 내용
- 미실행 검증 섹션의 사유
- 위험, 제한, 차단 사유, 남은 위험 섹션

## 다음 역할의 검증 포인트

- PR 본문에 `OPS-005`가 있어 task-id 감지가 되는지 확인한다.
- 보고서와 인수인계가 강화된 필수 섹션을 통과하는지 확인한다.
- QA 문서 불필요 사유가 OPS-005 범위와 맞는지 확인한다.
- `scripts/test-validate-task-artifacts.py` 호환 entry point 유지가 허용 가능한지 확인한다.
- `.github/workflows/**` 변경 없이 기존 Validate task artifacts 단계와 호환되는지 확인한다.
- AI Tech Lead 보조 역할 표현이 사용자 최종 승인권을 대체하지 않는지 확인한다.

## QA 필요 여부

- QA 문서 불필요.
- 이유: 제품 기능, 인증/인가, 결제, 주문 상태, 정기배송 상태, 개인정보, 재고, 멱등성, 데이터 손실 위험을 변경하지 않았다.

## AI 리뷰에서 남은 확인 항목

- CodeRabbit 리뷰 thread 8건은 이번 리뷰 반영 작업에서 모두 반영했다.
- Codex Review는 사용량 제한으로 실행되지 않아 CodeRabbit 리뷰와 로컬/CI 검증 결과를 기준으로 반영했다.

## 알려진 위험

- 새 검증 기준 때문에 후속 PR은 보고서/인수인계 섹션 누락 시 실패한다.
- 섹션명 표현이 새 alias에 없으면 사람이 보기에는 맞아도 검증 실패가 날 수 있다.
- `risk`, `limit` 같은 짧은 영어 alias는 제거되어 영어 위험 섹션 제목은 구체적 표현을 사용해야 한다.

## 남은 위험과 주의 사항

- 후속 역할은 템플릿을 기준으로 보고서를 작성해야 한다.
- AI 리뷰가 검증 기준을 완화하라고 제안하더라도, 사용자 승인 없는 품질 기준 약화는 하지 않는다.
- PR #26의 CodeRabbit thread가 push 후 outdated 처리되는지 확인해야 한다.

## 다음 권장 작업

- TL은 PR 검토에서 Tech Lead 문서와 Skill의 경계가 적절한지 확인한다.
- 후속 기능 구현 작업은 QA 필요 여부와 QA 문서 경로 또는 생략 사유를 PR/보고서에 남긴다.

## 완료 조건

- OPS-005 PR의 Repository Validation 통과
- TL이 새 역할 문서, 검증 스크립트, QA 기준, PR 템플릿을 확인
- 사용자가 직접 병합 여부 결정

## 중단 조건

- Secret 또는 민감정보 노출 의심
- 검증 스크립트가 OPS-005 산출물 자체를 통과하지 못함
- 기존 GitHub Actions의 `--from-stdin` 호출과 호환되지 않음
- 제품 기능 코드 수정이 필요해짐
- reset, rebase, force push, history rewrite가 필요해짐
