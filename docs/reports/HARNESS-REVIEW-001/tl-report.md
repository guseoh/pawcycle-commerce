# HARNESS-REVIEW-001 Tech Lead 작업 보고서

## 작업 정보

- 작업 ID: HARNESS-REVIEW-001
- 작업 등급: 일반
- 역할: Tech Lead
- 작업 브랜치: `ops/tl-harness-review-001`
- 대상 브랜치: `main`

## 작업 목적

PR 작성자가 변경 목록만 나열하지 않고 작업 필요성, 핵심 결정, 대안, 한계와 사람이 판단할 검토 지점을 설명하도록 PR template을 의사결정 중심으로 개선한다.

## 입력 문서

사용자 승인 요청, 루트 `AGENTS.md`, `docs/runbook/lean-harness.md`, Tech Lead 역할 문서와 Skill, 기존 `.github/pull_request_template.md`를 입력으로 사용했다.

## 승인 입력

기존 작업 정보, 도메인, API, 검증, 고위험 증거, QA, AI 리뷰와 병합 전 확인 항목을 보존하면서 의사결정 중심 섹션과 작업 등급별 작성 깊이 안내를 추가하도록 승인됐다.

## 변경 범위

- `.github/pull_request_template.md`에 작업 필요성, 해결 방법과 핵심 결정, 검토한 대안, 한계와 트레이드오프 섹션 추가
- 리뷰어 중점 확인을 사람 판단, 영향·회귀, 자동 검증의 세 구획으로 재구성
- `docs/reports/HARNESS-REVIEW-001/tl-report.md`에 변경 근거와 검증 기록

## 변경하지 않은 범위

`docs/runbook/lean-harness.md`, `AGENTS.md`, workflow·CI 차단 규칙, 기존 PR 본문, application·infra·DB는 변경하지 않는다. PR 라인 수·파일 수 제한이나 `1 subtask = 1 PR` 강제도 추가하지 않는다.

## 주요 결과

기존 필수 항목을 유지하면서 다음 네 의사결정 항목을 중복 없이 추가했다.

1. 왜 지금 해야 하나요?
2. 해결 방법과 핵심 결정
3. 검토한 대안
4. 한계와 트레이드오프

경량 PR에는 불필요한 장문 대신 `해당 없음` 표기를 허용하고, 일반·고위험 작업에는 판단 근거와 위험 경계를 기록하도록 안내했다.

## 기존 필수 항목 보존

작업 정보, 관련 이슈, 목적, 변경 범위, 도메인 규칙, API 변경 여부, 검증, 고위험 검증 증거, QA 검증, AI 리뷰, 병합 전 확인, Tech Lead 최종 확인과 자동 병합 금지 문구를 유지했다.

## 결정 상태

사용자가 승인한 template 개선 범위만 반영했다. AI 리뷰는 보조 검토자이며 사람의 최종 판단과 사용자 직접 병합을 대체하지 않는다.

## API 영향

API 요청·응답, 오류 형식, HTTP status와 인증·인가 동작 변경은 없다.

## DB 영향

DB schema, data, migration 또는 복구 절차 변경은 없다.

## 보안 영향

Secret·개인정보·계정 식별자·운영 credential을 추가하거나 변경하지 않는다.

## 운영 영향

workflow·CI 차단 규칙과 자동 병합 정책은 변경하지 않는다.

## 실행한 검증

- 관련 Markdown과 UTF-8 문자 인코딩 검사
- `py -3 scripts/validate-task-artifacts.py --task-id HARNESS-REVIEW-001 --task-grade 일반`
- PR·commit 제목 규칙 검사
- `git diff --check`
- 변경 경로가 template과 작업 보고서에 한정됐는지 확인

## 실행하지 못한 검증과 이유

문서 template과 작업 보고서만 변경하므로 application 전체 테스트는 로컬에서 반복하지 않는다. 원격 Repository Validation 결과는 GitHub Checks를 권위 원본으로 확인한다.

## QA 필요 여부

별도 QA 문서는 필요하지 않다.

## QA 문서 경로 또는 생략 사유

제품 동작, API·DB 계약, application·infra 실행 코드가 바뀌지 않는 문서 template 변경이므로 별도 QA 문서를 작성하지 않는다.

## AI 리뷰 반영 여부

PR 생성 뒤 CodeRabbit·Codex Review의 유효한 지적을 사용자 승인 범위와 template·보고서 변경에 대조해 선별한다.

## AI 리뷰 미반영 항목과 이유

현재 없음. 범위를 넘어선 정책·workflow·자동화 제안은 이 작업에서 반영하지 않는다.

## 인수인계 생략 사유

이 template 변경을 다음 역할 또는 운영자가 별도 실행 입력으로 즉시 소비하지 않으므로 역할 간 인수인계를 생략한다. PR 작성자는 병합 뒤 갱신된 template을 사용한다.

## 적용 방법

새 PR 작성 시 갱신된 template의 작업 등급 안내와 의사결정 섹션을 채운다. 자동 병합하지 않고 사람이 승인 범위, 위험과 병합 여부를 최종 판단한다.

## 위험과 제한

Template은 판단 근거를 요청할 뿐 내용의 품질이나 승인 적합성을 자동으로 보장하지 않는다. 경량 작업에는 `해당 없음`을 허용해 과도한 문서화를 피한다.

## 남은 위험

PR 작성자가 섹션을 형식적으로 채울 수 있으므로 리뷰어는 사람 판단 구획과 실행한 검증 근거의 실제 내용을 확인해야 한다.

## 다음 작업

사용자 검토 후 병합 여부를 결정한다. 기존 `ops/tl`의 미병합 이력 정리는 별도 사용자 승인 작업으로 남긴다.

## 예외 브랜치 근거

기존 `ops/tl`에는 서로 분기된 미병합 이력이 있어 재사용하지 않았다. 기존 local·remote `ops/tl`은 삭제·reset·rebase·force push 없이 보존했고, HARNESS-REVIEW-001만 최신 `main` 기반 임시 예외 브랜치 `ops/tl-harness-review-001`에서 수행한다. PR 병합 후에는 이 임시 예외 브랜치만 정리하며 기존 `ops/tl` 정리는 별도 사용자 승인 작업으로 남긴다.

## Git 결과

최신 `origin/main`에서 임시 예외 브랜치를 생성했다. commit·push와 원격 PR 상태는 GitHub를 권위 원본으로 확인한다.

## PR 결과

`main` 대상 Draft PR의 head는 `ops/tl-harness-review-001`로 생성한다. 자동 병합하지 않으며 사용자가 직접 병합 여부를 결정한다.
