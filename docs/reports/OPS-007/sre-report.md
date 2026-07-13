# OPS-007 Platform/SRE 작업 보고서

## 작업 목적

Discord 협업 알림을 단순 상태 통지에서 PR 작업 맥락, 처리 과정, 검증·리뷰 결과와 다음 행동을 한 메시지에서 확인할 수 있는 보고서형 알림으로 개선한다. `Repository Validation`을 중복 감시하던 두 workflow를 하나로 통합하고, 신뢰할 수 없는 PR·Issue 입력과 Secret을 안전하게 다룬다.

## 입력 문서

- 사용자 승인 OPS-007 작업 요청
- `AGENTS.md`
- `docs/roles/platform-sre.md`
- `docs/api/API-003-subscription-api-contract-decision-request.md`
- `.github/workflows/notify-collaboration.yml`
- `.github/workflows/notify-ci-result.yml`
- 기존 Discord builder, sender, fixture와 runbook
- PR #39 병합 결과와 최신 `main`

## 변경 전 문제

- 두 notification workflow가 같은 `Repository Validation` 완료를 감시해 결과가 중복 전송될 수 있었다.
- 단일 Embed의 `fields[:8]` 절단 때문에 추가 정보와 다음 행동이 사라질 수 있었다.
- 작업 ID가 PR 본문보다 제목·브랜치에 의존했고 `AUTH-*`, `PRODUCT-*`를 놓쳤다.
- CI job·실패 step, 리뷰어별 최신 상태와 미해결 thread 수가 payload에 없었다.
- PR Ready, Draft, synchronize와 review 요청의 의미가 충분히 분리되지 않았다.

## 변경 범위

- `.github/workflows/notify-collaboration.yml`을 유일한 협업 알림 진입점으로 정리하고 `notify-ci-result.yml`을 제거했다.
- `collect-discord-context.py`가 이벤트 payload와 read-only GitHub API를 정규화된 context JSON으로 변환한다.
- builder를 이벤트별 1~3개 Embed 보고서 구조로 변경하고 `fields[:8]` 절단을 제거했다.
- 작업 ID 우선순위, 역할 브랜치 매핑, PR 본문의 제한된 섹션 추출과 API 실패 fallback을 구현했다.
- PR Ready, Draft, synchronize, review requested, 승인, 변경 요청, CI conclusion별 결과, 병합, Issue와 Preview를 구분했다.
- CI job별 conclusion, 실패 job·step, 리뷰 상태, 미해결 thread, 처리 과정과 다음 행동을 표시한다.
- 정규화 fixture 20개, collector·builder·sender 단위 테스트와 validator를 추가·갱신했다.
- 협업 자동화 runbook에 단일 workflow, 보안 경계, collector 누락 실패와 병합 후 Preview 절차를 반영했다.

## 변경하지 않은 범위

- Backend·Frontend 제품 코드, API-003 승인 계약, DB와 migration
- 배포 pipeline, 브랜치 정책, Discord Bot application과 별도 인프라
- 신규 Python package와 외부 SaaS
- Discord Webhook 생성·교체, Repository Secret 조회·변경
- Discord 개인·역할 mention, 전체 diff·로그·Stack Trace 전송
- 자동 병합과 destructive Git 작업

## 주요 결과

- `Repository Validation` 완료 이벤트의 알림 경로는 `notify-collaboration.yml`의 `workflow_run` 한 곳뿐이다.
- `pull_request_target`, `workflow_run`, `workflow_dispatch`는 모두 기본 브랜치의 신뢰된 script만 실행하며 read 권한만 사용한다.
- 기본 브랜치에 필수 collector가 없으면 성공으로 숨기지 않고 workflow를 실패시킨다.
- 작업 ID는 본문의 명시적 항목을 우선하고 `API-003`, `AUTH-004`, `PRODUCT-001`, `OPS-007`과 미기록 경계를 fixture로 검증한다.
- 상세 이벤트는 작업 요약, 처리·검증·리뷰, 상태와 다음 작업의 세 Embed로 구성한다. 간단 이벤트는 한 Embed를 사용한다.
- API 조회 실패는 `0`이나 성공으로 추측하지 않고 `확인 불가`로 표시한다.
- review thread는 GraphQL cursor로 전체 페이지를 집계하며 페이지 실패·반복 cursor는 `확인 불가`로 처리한다.
- PR fenced code block은 섹션 추출 전에 전체 제거하고 Webhook·token·password·client secret·API/AWS key·PEM private key를 공통 `clean_text()`에서 마스킹한다. Issue 본문은 전달하지 않는다.
- CI conclusion은 `success`, `failure`, `timed_out`, `cancelled`, `neutral`, `skipped`, unknown으로 구분한다.
- `allowed_mentions={"parse":[]}`, mention 무력화, control character 제거와 Discord 실제 embed 텍스트 합계 6000자 제한을 유지한다.

## 검증 결과

| 검증 | 명령 | 결과 |
| --- | --- | --- |
| Python 문법 | `python -m py_compile`로 collector, builder, limit helper, sender, validator와 테스트 검사 | 통과 |
| 정규화 payload fixture | `python scripts/validate-discord-payloads.py` | 통과, 20개 |
| Discord 전체 단위 테스트 | `python -m unittest discover -s scripts -p "test_*discord*.py"` | 통과, 35개 |
| 중복 CI 알림 경로 | payload validator의 workflow 검사 | 통과, `workflow_run` 1개 |
| Workflow 신뢰 경계 | indentation-aware validator | 통과: default branch checkout, collector 누락 `exit 1`, 중복 trigger 없음 |
| 작업 산출물 | `py scripts\\validate-task-artifacts.py --task-id OPS-007` | 통과 |
| Diff 형식 | `git diff --check` | 통과 |
| 수정 후 Discord PR Preview | 병합 전에는 실행하지 않음 | 미실행: PR branch script와 Repository Secret을 함께 실행하지 않음 |

기존 `ops/sre` script로 실행한 run `29246535424`의 HTTP 204는 수정 전 참고 기록이다. 이번 보안 보완 후의 최종 Webhook 전송 증거로 재사용하지 않으며, 수정 후 실제 Preview는 병합되어 기본 브랜치 코드가 신뢰 경계 안에 들어온 뒤 수행한다.

Ready 전환 후 CodeRabbit review 제출 알림 run `29247302229`는 기본 브랜치에 collector가 없어 실패했다. collector 누락을 `notify=false`와 종료 코드 0으로 숨기던 호환 fallback은 제거했고, 필수 collector가 없으면 명시적으로 실패하도록 변경했다. QA heading 중복 분류 방지와 10분 job timeout은 유지한다.

추가 리뷰에서 이전 CI run의 SHA가 최신 PR SHA로 덮일 수 있는 문제, API 실패 시 event의 PR 번호가 사라지는 문제와 Issue 자유 본문의 Secret 전파 가능성을 확인했다. run SHA·PR 번호를 event 기준으로 보존하고 stale CI의 변경 정보를 `확인 불가`로 전환했으며, 공통 Secret redaction과 Issue 본문 비전송을 추가했다.

## 실행하지 못한 검증과 이유

- 실제 Discord 채널 화면 수신: Codex가 Discord 화면 수신을 추측하지 않으며 사용자가 확인해야 한다.

## 적용 방법

PR·review·Issue 이벤트, `Repository Validation` 완료와 수동 dispatch 모두 `notify-collaboration.yml`이 기본 브랜치의 collector와 builder를 실행한다. PR branch의 script는 Secret과 함께 실행하지 않는다. 병합 후 수동 연결은 Actions의 `Collaboration Notification`에서 `connection_test`, PR 보고서 확인은 `pr_preview`와 양의 정수 `pr_number`를 사용한다.

롤백 시 이번 보완 커밋 전체를 PR에서 되돌리고 이전 신뢰 경계로 실제 Webhook Preview를 실행하지 않는다. 기본 브랜치 collector 배포 전 알림 누락은 workflow 실패로 관측한다.

## 남은 위험과 제한

- GitHub API가 일시적으로 실패하면 job, review 또는 thread 정보가 `확인 불가`로 표시될 수 있다.
- review 시 CI 상태는 해당 head branch의 최신 `Repository Validation` 실행을 사용하므로 새 실행이 아직 생성되지 않은 짧은 구간에는 이전 상태 또는 `확인 불가`가 보일 수 있다.
- 실제 Webhook과 Discord 채널 권한은 로컬 fixture로 검증할 수 없다.
- GraphQL 어느 페이지에서든 조회가 실패하거나 cursor가 반복되면 전체 thread 수를 `확인 불가`로 표시한다.
- 수정 후 실제 Discord 수신과 카드 가독성은 병합 후 사용자가 확인해야 한다.

## 다음 작업

1. PR #40의 Repository Validation 전체 성공과 unresolved review 0건을 확인한다.
2. 사용자가 PR #40 병합 여부를 결정한다.
3. 병합 후 기본 브랜치의 `Collaboration Notification`에서 `scenario=pr_preview`, 안전한 PR 번호로 실행해 HTTP 2xx/204를 확인한다.
4. 사용자가 Discord 채널의 카드 수신과 가독성을 확인한다.

## Git 결과

- 작업 시작 원격 기준 `main`: `d56686426300683ce97aa92ae472ff0775e2890f`
- 기존 로컬 `ops/sre` 보존: `backup/ops-sre-before-OPS-007-eec524b` → `eec524b`
- 작업 브랜치: `ops/sre`
- 구현 커밋: `08118de` `ci(discord): 협업 상세 알림 개선`
- 원격 검증 기록 커밋: `bae555a` `docs(ops): OPS-007 원격 검증 결과 기록`
- 호환 보완 커밋: `501b15f` `fix(ci): 병합 전 Discord 알림 호환 보완`
- 컨텍스트 보완 커밋: `cefa15d` `fix(discord): CI 컨텍스트와 민감정보 경계 보완`
- 작업 시작 PR head: `5b68105` `fix(discord): JSON 시크릿 마스킹 보완`
- force push, reset, rebase, 자동 병합: 수행하지 않음

## PR 상태

- base: `main`
- head: `ops/sre`
- 제목: `ci(discord): 협업 상세 알림 개선`
- PR: #40
- 최초 상태: Draft
- 현재 Open, Ready for review이며 자동 병합하지 않는다.
- 수정 후 실제 Webhook Preview는 병합 후 기본 브랜치에서 수행한다.
