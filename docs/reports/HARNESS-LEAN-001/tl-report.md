# HARNESS-LEAN-001 Tech Lead 작업 보고서

## 작업 정보

- 작업 ID: `HARNESS-LEAN-001`
- 작업 등급: 고위험
- 역할: Tech Lead
- 브랜치: `ops/tl`
- 대상 브랜치: `main`

## 작업 목적

저장소 하네스를 승인된 위험 기반 Lean Harness와 delta-only Codex 명세 규칙에 맞추고, 기존 작업 기록을 바꾸지 않은 채 다음 작업부터 적용한다.

## 입력 문서

- 사용자가 `HARNESS-LEAN-001` 요청과 후속 역할 브랜치 재생성 승인을 통해 확정한 등급·산출물·검증·비소급 기준
- 루트와 경로별 `AGENTS.md`
- `docs/roles/tech-lead.md`
- `.agents/skills/tech-lead/SKILL.md`
- PR·보고서·인수인계 template
- `docs/runbook/collaboration-automation.md`
- `scripts/validate-task-artifacts.py`, 회귀 테스트와 `Repository Validation` 연결부

## 승인 입력

- 경량·일반·고위험 정의와 불명확 시 일반 시작·위험 발견 시 승격 기준
- 프롬프트 권장 길이를 경고 기준으로만 사용하는 delta-only 규칙
- validator가 구조적 필수 항목만 차단하고 기존 산출물을 소급 변경하지 않는 기준
- 고위험 작업의 승인, 적용 전후 검증, 독립 검증과 복구·롤백 증거 유지

## 명시적 승인 근거

- 사용자가 현재 요청에서 작업 등급을 고위험으로 지정하고 변경 범위·제외 범위·완료 조건을 승인했다.
- 사용자는 PR #53의 squash merge를 확인한 뒤 기존 `ops/tl` 로컬·원격 브랜치 삭제와 최신 `origin/main` 기준 재생성을 별도로 승인했다.

## 변경 범위

- 위험 기반 Lean Harness 상세 권위 원본과 상위 요약 관계
- 루트 운영 규칙, Tech Lead 역할 문서·Skill, QA·협업 Runbook 요약
- PR·보고서·인수인계 template
- 작업 등급별 산출물 validator와 회귀 테스트
- 기존 CI 호출 방식을 유지하는 비소급 전환

## 변경하지 않은 범위

- 제품 요구사항, 도메인, API, DB schema, 인증 동작과 배포 구성
- 기존 보고서·인수인계·학습 기록
- 역할 브랜치 체계와 자동 병합 정책
- dependency, 외부 서비스와 의미적 위험 자동 판정

## 인수 조건 매핑

| 조건 | 결과 |
| --- | --- |
| 단일 상세 권위 원본 | `docs/runbook/lean-harness.md`에 등급·승격·산출물·QA·검증·delta-only 규칙 통합 |
| 경량 무산출물 허용 | 명시적 경량이면 보고서·인수인계 없이 통과하는 회귀 테스트 추가 |
| 일반 조건부 인수인계 | 보고서 필수, 인수인계 또는 보고서의 명시적 생략 사유 검사 |
| 고위험 증거 유지 | 승인·적용 전후·독립 검증·복구 및 롤백 heading과 내용 검사 |
| 기존 산출물 호환 | 등급이 없으면 경고와 함께 기존 일반 규칙 적용 |
| 프롬프트 예산 비차단 | 권장 길이를 권위 문서에만 기록하고 validator 조건에서 제외 |

## 주요 결과

- `HARNESS-LEAN-001` 형식의 작업 ID를 PR 본문에서 인식한다.
- 명시된 작업 등급과 보고서의 작업 등급 불일치를 차단한다.
- 고위험 증거는 heading 존재뿐 아니라 비어 있지 않은 내용까지 검사한다.
- CI workflow 호출 형태와 기존 작업 디렉터리는 변경하지 않는다.

## 변경 파일

- 운영 권위·요약: `AGENTS.md`, `docs/runbook/lean-harness.md`, 관련 역할·QA·협업 문서
- template: `.github/pull_request_template.md`, 보고서·인수인계 template
- validator: `scripts/validate-task-artifacts.py`
- 회귀 테스트: `scripts/test_validate_task_artifacts.py`
- 현재 작업 보고서와 다음 작업 인수인계

## 결정 상태

- Product Decision: 없음
- Technical Decision: 사용자 승인 입력 범위에서 Approved
- 의미적 위험 판단은 validator에 추가하지 않고 사용자와 Tech Lead 검토로 유지

## API 영향

- 없음.

## DB 영향

- 없음. migration이 없고 기존 데이터·산출물을 수정하지 않는다.

## 보안 영향

- Secret 처리나 인증 동작을 변경하지 않는다.
- 고위험 분류에 보안을 포함하고 사람의 최종 판단을 유지한다.

## 운영 영향

- 병합 후 시작하는 작업은 작업 등급을 명시하고 등급별 산출물·QA·검증 기준을 사용한다.
- 기존 등급 없는 작업은 하위 호환 일반 규칙과 경고를 사용한다.

## 성능 영향

- validator의 소규모 Markdown 구조 검사만 추가한다. 성능 최적화나 기준값 변경은 없다.

## 적용 전 검증

- `origin/main`, 로컬 `main`, 새 로컬·원격 `ops/tl` 시작 SHA 일치를 확인했다.
- 삭제 직전 clean worktree, 열린 `ops/tl` PR 부재와 다른 worktree의 `ops/tl` 미사용을 확인했다.
- 기존 validator 회귀 테스트 기준을 읽고 하위 호환 경로를 유지했다.

## 실행한 검증

| 명령 | 결과 |
| --- | --- |
| `py -3 -m py_compile scripts/validate-task-artifacts.py scripts/test_validate_task_artifacts.py` | 통과 |
| `py -3 scripts/test_validate_task_artifacts.py` | 통과, 30개 테스트 |
| `py -3 scripts/validate-task-artifacts.py --task-id HARNESS-LEAN-001 --task-grade 고위험` | 통과 |
| `bash scripts/validate-commit-message.sh --message "chore(harness): 위험 기반 Lean Harness 정렬"` | 통과 |
| `git diff --check` | 통과 |
| 변경 파일 개인 절대 경로·Secret 패턴 검사 | 일치 항목 없음 |

## 적용 후 검증

- 변경 파일 자기 리뷰와 targeted diff 검사를 수행했다.
- 실제 고위험 산출물 validator, commit 제목과 공백 검사를 통과했다.
- PR 게시 후 독립된 GitHub Actions `Repository Validation` 결과를 확인한다.

## 독립 검증

- GitHub-hosted runner의 `Repository Validation` run `29681262065`에서 `Commit and PR conventions`와 `Application validation`이 모두 통과했다.
- 별도 `Discord collaboration report`도 통과했다.
- 인증·결제·데이터·제품 동작 변경이 없는 하네스 변경이므로 별도 제품 QA 문서는 만들지 않는다. 사용자가 diff와 CI 결과를 최종 검토한다.

## 실행하지 못한 검증과 이유

- 없음.

## QA 필요 여부

- 별도 제품 QA 문서 불필요. 제품 동작을 바꾸지 않는다.
- validator 회귀 테스트와 독립 CI 검증은 필수다.

## QA 문서 경로 또는 생략 사유

- 생략. 하네스 문서·validator·template 변경이며 제품 사용자 흐름이 없다.

## AI 리뷰 반영 여부

- Draft PR이라 CodeRabbit은 검토를 건너뛰었다. Ready 전환 후 생성되는 AI 리뷰는 실제 버그·보안·검증 누락만 선별 반영한다.

## AI 리뷰 미반영 항목과 이유

- 현재 없음.

## 적용 방법

- PR을 사용자 검토 후 `main`에 병합한다.
- 병합 뒤 시작하는 작업부터 `docs/runbook/lean-harness.md`와 새 template을 사용한다.
- 기존 작업 기록과 열린 과거 산출물에는 새 등급 필드를 소급 추가하지 않는다.

## 복구·롤백 증거

- 문제 발생 시 `HARNESS-LEAN-001` 변경 커밋을 새 PR에서 일반 `git revert`로 되돌릴 수 있다.
- reset, rebase, force push, history rewrite와 기존 산출물 수정은 필요하지 않다.
- 되돌린 뒤 기존 등급 없는 일반 검증 경로와 회귀 테스트를 다시 실행해 복구를 확인한다.

## 위험과 제한

- 등급이 없는 입력은 호환성을 위해 일반으로 처리하므로 실제 고위험 작업의 잘못된 미분류까지 validator가 의미적으로 찾아내지는 못한다.
- 독립 검증의 충분성과 롤백의 실제 안전성은 구조 검사만으로 보장할 수 없어 사용자와 Tech Lead 판단이 남는다.

## 남은 위험

- Draft PR의 AI 리뷰와 사용자 최종 검토가 남아 있다.
- prompt 권장 길이는 저장소에 prompt 원문이 없으므로 문서 경고로만 운영한다.

## 다음 작업

- 다음 작업자는 새 등급 필드와 delta-only 명세를 사용하고, 위험 발견 시 등급을 승격한다.

## Git 결과

- 역할 브랜치 재생성·게시와 구현 commit `496ad1a` push를 완료했다. 이 보고서의 독립 검증 결과도 후속 문서 commit으로 push한다.

## PR 결과

- Draft PR #56을 `ops/tl`에서 `main` 대상으로 생성했다. 자동 병합하지 않는다.
