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
- PR Ready, Draft, synchronize, review requested, 승인, 변경 요청, CI 결과, 병합, Issue와 Preview를 구분했다.
- CI job별 conclusion, 실패 job·step, 리뷰 상태, 미해결 thread, 처리 과정과 다음 행동을 표시한다.
- 정규화 fixture 16개, collector·builder 단위 테스트와 validator를 추가·갱신했다.
- 협업 자동화 runbook에 단일 workflow, 보안 경계, fallback과 Preview 절차를 반영했다.

## 변경하지 않은 범위

- Backend·Frontend 제품 코드, API-003 승인 계약, DB와 migration
- 배포 pipeline, 브랜치 정책, Discord Bot application과 별도 인프라
- 신규 Python package와 외부 SaaS
- Discord Webhook 생성·교체, Repository Secret 조회·변경
- Discord 개인·역할 mention, 전체 diff·로그·Stack Trace 전송
- 자동 병합과 destructive Git 작업

## 주요 결과

- `Repository Validation` 완료 이벤트의 알림 경로는 `notify-collaboration.yml`의 `workflow_run` 한 곳뿐이다.
- `pull_request_target`과 `workflow_run`은 기본 브랜치의 신뢰된 script만 실행하며 read 권한만 사용한다.
- 작업 ID는 본문의 명시적 항목을 우선하고 `API-003`, `AUTH-004`, `PRODUCT-001`, `OPS-007`과 미기록 경계를 fixture로 검증한다.
- 상세 이벤트는 작업 요약, 처리·검증·리뷰, 상태와 다음 작업의 세 Embed로 구성한다. 간단 이벤트는 한 Embed를 사용한다.
- API 조회 실패는 `0`이나 성공으로 추측하지 않고 `확인 불가`로 표시한다.
- `allowed_mentions={"parse":[]}`, mention 무력화, control character 제거, 필드·전체 6000자 제한과 낮은 우선순위 목록의 생략 건수 표시를 유지한다.

## 검증 결과

| 검증 | 명령 | 결과 |
| --- | --- | --- |
| Python 문법 | workspace Python `-m py_compile`로 변경된 6개 Python 파일 검사 | 통과 |
| 정규화 payload fixture | `python scripts/validate-discord-payloads.py` | 통과, 16개 |
| Discord 전체 단위 테스트 | `python -m unittest discover -s scripts -p "test_*discord*.py"` | 통과, 21개 |
| 중복 CI 알림 경로 | payload validator의 workflow 검사 | 통과, `workflow_run` 1개 |
| Diff 형식 | `git diff --check` | 통과 |
| Repository Validation | PR #40 run `29246521901` | 통과: conventions, Java 25·MySQL 8.4 Backend test/build, Node.js 24 Frontend install/lint/build |
| Discord PR Preview | `ops/sre`, `scenario=pr_preview`, `pr_number=40`, run `29246535424` | 통과: collector·builder·sender 성공, HTTP 204 |

첫 실행에서 WindowsApps `python.exe`가 `Python`만 출력하고 종료 코드 1을 반환했다. 코드 실패가 아니므로 workspace dependency의 Python 3.12 runtime으로 같은 명령을 다시 실행했고 위 결과처럼 통과했다.

task artifact validator, Secret 의심 패턴 검사, Repository Validation 전체와 실제 Discord Preview가 통과했다. Actions의 Node.js 20 deprecation 안내는 기존 pinned `actions/checkout`이 runner에서 Node.js 24로 강제 실행된다는 warning이며 이번 작업의 실패는 아니다.

Ready 전환 후 첫 실제 `workflow_run` 알림 run `29247302229`는 기본 브랜치에 아직 신규 collector가 없어 실패했다. 보안 결정대로 PR head script로 우회하지 않고, 기본 브랜치에 collector가 없는 병합 전에는 `notify=false`로 안전하게 생략하는 호환 fallback을 추가했다. 또한 CodeRabbit의 유효 의견에 따라 QA heading 중복 분류 방지, 10분 job timeout과 fixture 계약을 보강했다.

## 실행하지 못한 검증과 이유

- 실제 Discord 채널 화면 수신: Codex가 Discord 화면 수신을 추측하지 않으며 사용자가 확인해야 한다.

## 적용 방법

PR·review·Issue 이벤트와 `Repository Validation` 완료 시 `notify-collaboration.yml`이 기본 브랜치의 collector와 builder를 실행한다. 수동 연결은 Actions의 `Collaboration Notification`에서 `connection_test`, PR 보고서 확인은 `pr_preview`와 양의 정수 `pr_number`를 사용한다.

## 남은 위험과 제한

- GitHub API가 일시적으로 실패하면 job, review 또는 thread 정보가 `확인 불가`로 표시될 수 있다.
- review 시 CI 상태는 해당 head branch의 최신 `Repository Validation` 실행을 사용하므로 새 실행이 아직 생성되지 않은 짧은 구간에는 이전 상태 또는 `확인 불가`가 보일 수 있다.
- 실제 Webhook과 Discord 채널 권한은 로컬 fixture로 검증할 수 없다.
- 하나의 PR에 review thread가 100개를 넘으면 현재 GraphQL 첫 100개 범위만 집계한다.

## 다음 작업

1. Draft PR에서 Repository Validation 전체 결과를 확인한다.
2. 가능하면 `ops/sre` ref의 `pr_preview`를 실행해 HTTP 2xx를 확인한다.
3. 사용자가 Discord 채널의 카드 가독성과 실제 수신을 확인한다.
4. 병합 후 후속 구독 Backend PR부터 새 보고서형 알림을 운영 확인한다.

## Git 결과

- 기준 `main`: `d56686426300683ce97aa92ae472ff0775e2890f`
- PR #39 merge commit: `79e0cbc277093d4f4469342747979ec08eb528e7`
- 기존 `ops/sre` 보존: `backup/ops-sre-before-OPS-007` → `0ced626947...`
- 작업 브랜치: `ops/sre`
- 구현 커밋: `08118de` `ci(discord): 협업 상세 알림 개선`
- 원격 검증 기록 커밋: `bae555a` `docs(ops): OPS-007 원격 검증 결과 기록`
- force push, reset, rebase, 자동 병합: 수행하지 않음

## PR 상태

- base: `main`
- head: `ops/sre`
- 제목: `ci(discord): 협업 상세 알림 개선`
- PR: #40
- 최초 상태: Draft
- Repository Validation과 HTTP 204 Preview 통과 후 Ready for review로 전환하며 자동 병합하지 않는다.
