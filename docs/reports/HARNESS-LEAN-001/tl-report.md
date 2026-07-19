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
| 기존 산출물 호환 | 명시적 legacy 옵션에서만 등급 없는 기존 산출물에 일반 규칙 적용 |
| 프롬프트 예산 비차단 | 권장 길이를 권위 문서에만 기록하고 validator 조건에서 제외 |

## 주요 결과

- validator, Discord와 병합 PR 기록이 승인된 prefix 집합과 `HARNESS-...-###` 형식을 동일하게 인식한다.
- 명시된 작업 등급과 보고서의 작업 등급 불일치를 차단한다.
- 고위험 증거는 heading 존재뿐 아니라 비어 있지 않은 내용까지 검사한다.
- CI workflow 호출 형태와 기존 작업 디렉터리는 변경하지 않는다. 새 PR의 등급 누락은 기본 실패한다.

## 변경 파일

- 운영 권위·요약: `AGENTS.md`, `docs/runbook/lean-harness.md`, 관련 역할·QA·협업 문서
- template: `.github/pull_request_template.md`, 보고서·인수인계 template
- validator: `scripts/validate-task-artifacts.py`
- 자동화: Discord 컨텍스트와 병합 PR 기록의 일치된 작업 ID 추출
- 회귀 테스트: validator, Discord 컨텍스트와 병합 PR 기록 fixture
- 현재 작업 보고서

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
- 기존 등급 없는 산출물은 명시적 `--allow-legacy-without-grade` 옵션에서만 하위 호환 일반 규칙과 경고를 사용한다.

## 성능 영향

- validator의 소규모 Markdown 구조 검사만 추가한다. 성능 최적화나 기준값 변경은 없다.

## 적용 전 검증

- `origin/main`, 로컬 `main`, 새 로컬·원격 `ops/tl` 시작 SHA 일치를 확인했다.
- 삭제 직전 clean worktree, 열린 `ops/tl` PR 부재와 다른 worktree의 `ops/tl` 미사용을 확인했다.
- 기존 validator 회귀 테스트 기준을 읽고 하위 호환 경로를 유지했다.

## 실행한 검증

| 명령 | 결과 |
| --- | --- |
| 관련 Python 문법 검사 | 통과 |
| `py -3 scripts/test_validate_task_artifacts.py` | 통과, 35개 테스트 |
| `py -3 scripts/validate-task-artifacts.py --task-id HARNESS-LEAN-001 --task-grade 고위험` | 통과 |
| `py -3 scripts/test_discord_context.py` | 통과, 17개 테스트 |
| `py -3 scripts/validate-obsidian-record.py` | 통과, `HARNESS-LEAN-001` 기록 확인 |
| `bash scripts/validate-commit-message.sh --message "fix(harness): 작업 ID와 소비자 계약 정렬"` | 통과 |
| `bash scripts/validate-commit-message.sh --message "fix(harness): 실제 소비 기준 정렬"` | 통과 |
| `bash scripts/validate-commit-message.sh --message "fix(harness): 인수인계 트리거 분리"` | 통과 |
| `git diff --check` | 통과 |
| 변경 파일 개인 절대 경로·Secret 패턴 검사 | 일치 항목 없음 |

## 적용 후 검증

- 변경 파일 자기 리뷰와 targeted diff 검사를 수행했다.
- 실제 고위험 산출물 validator, commit 제목과 공백 검사를 통과했다.
- 원격 검증 상태는 보고서에 실행 ID나 중간 SHA를 복제하지 않고 최신 GitHub Checks를 권위 원본으로 확인한다.

## 독립 검증

- GitHub-hosted runner의 최신 `Repository Validation`을 독립 자동 검증 경로로 사용하며, 결과의 권위 원본은 GitHub Checks다.
- 인증·결제·데이터·제품 동작 변경이 없는 하네스 변경이므로 별도 제품 QA 문서는 만들지 않는다. 사용자가 diff와 CI 결과를 최종 검토한다.

## 실행하지 못한 검증과 이유

- 없음.

## QA 필요 여부

- 별도 제품 QA 문서 불필요. 제품 동작을 바꾸지 않는다.
- validator 회귀 테스트와 독립 CI 검증은 필수다.

## QA 문서 경로 또는 생략 사유

- 생략. 하네스 문서·validator·template 변경이며 제품 사용자 흐름이 없다.

## AI 리뷰 반영 여부

- 리뷰 지적과 해결 상태는 GitHub Review Threads를 권위 원본으로 확인한다. 등급 누락, placeholder, QA 내용, 작업 ID prefix 정렬, 인수인계 완료 경로와 소비자 용어 지적은 현재 코드에서 유효성을 검증해 최소 반영했다.

## AI 리뷰 미반영 항목과 이유

- docstring·Ruff 경고는 요청된 동작 결함과 무관하고 기존 함수 전반의 범위 밖 정리이므로 반영하지 않았다.

## 적용 방법

- PR을 사용자 검토 후 `main`에 병합한다.
- 병합 뒤 시작하는 작업부터 `docs/runbook/lean-harness.md`와 새 template을 사용한다.
- 기존 작업 기록과 열린 과거 산출물에는 새 등급 필드를 소급 추가하지 않는다.

## 복구·롤백 증거

- 문제 발생 시 `HARNESS-LEAN-001` 변경 커밋을 새 PR에서 일반 `git revert`로 되돌릴 수 있다.
- reset, rebase, force push, history rewrite와 기존 산출물 수정은 필요하지 않다.
- 되돌린 뒤 기존 등급 없는 일반 검증 경로와 회귀 테스트를 다시 실행해 복구를 확인한다.

## 위험과 제한

- 등급 없는 기존 산출물의 일반 규칙 적용은 명시적 legacy 옵션으로 제한되며, 명시된 등급 자체의 의미적 적절성은 사람이 검토해야 한다.
- 독립 검증의 충분성과 롤백의 실제 안전성은 구조 검사만으로 보장할 수 없어 사용자와 Tech Lead 판단이 남는다.

## 남은 위험

- 검증 상태는 GitHub Checks, 리뷰 지적과 해결 상태는 GitHub Review Threads를 권위 원본으로 확인하며 사용자 최종 검토가 필요하다.
- prompt 권장 길이는 저장소에 prompt 원문이 없으므로 문서 경고로만 운영한다.

## 다음 작업

- 다음 작업자는 새 등급 필드와 delta-only 명세를 사용하고, 위험 발견 시 등급을 승격한다.

## 인수인계 생략

- 확정된 후속 역할이나 실제 운영자가 없고, 다음 작업은 루트 규칙·권위 런북·template을 직접 입력으로 사용하므로 범용 인수인계를 작성하지 않는다.

## Git 결과

- 역할 브랜치의 commit·push 이력은 Git을 권위 원본으로 확인한다.

## PR 결과

- PR #56은 `ops/tl`에서 `main`을 대상으로 하며 자동 병합하지 않는다. PR 상태와 검증은 GitHub Checks, 리뷰는 GitHub Review Threads를 권위 원본으로 확인한다.
