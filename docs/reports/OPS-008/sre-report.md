# OPS-008 Platform/SRE 작업 보고서

## 작업 목적

OPS-007 병합 후 수동 `pr_preview`에서 작업 요약 Embed 하나만 보인 현상을 진단하고, HTTP 성공과 Discord 상세 message 계약 성공을 구분한다. 상세 이벤트의 3-Embed 계약과 이모지 표시 정책을 자동 검증한다.

## 입력 문서

- 기준 Actions run: `29257861839`
- 기준 job: `86842913462`
- 입력: `scenario=pr_preview`, `pr_number=40`
- 사용자 화면 관찰: 첫 번째 작업 요약 Embed만 표시되고 `처리·검증·리뷰`, `상태와 다음 작업` Embed는 표시되지 않음
- 기준 로그: collector `event=pr_ready`, sender HTTP 204

## 원인 분석

기준 시점 builder 코드는 상세 이벤트에서 3개 Embed를 구성하도록 작성돼 있었다. 그러나 기준 workflow는 생성한 payload의 Embed 수를 기록하거나 검증하지 않았고, sender도 `wait=true`를 사용하지 않아 Discord가 생성한 message JSON을 받지 않았다. 따라서 과거 run 증거만으로 요청 payload가 실제 몇 개였는지 또는 Discord가 몇 개를 생성했는지는 확정할 수 없다.

코드에서 확인된 직접 결함은 두 가지다.

1. `pr_preview`가 조회한 PR 상태와 무관하게 `pr_ready`로 고정됐다.
2. HTTP 204를 성공으로 처리했지만 요청 payload와 생성 message의 Embed 수를 검증할 경로가 없었다.

화면에서 한 개만 표시된 최종 원인은 기존 로그에 필요한 증거가 없어 추정하지 않는다. 수정 후에는 요청 전 payload 계약과 응답 message 계약을 분리해 어느 구간에서 불일치했는지 확인할 수 있다.

## 주요 결과

- 상세 이벤트 공통 계약: `pr_ready`, `pr_merged`, review 상세 이벤트와 모든 `ci_*`는 정확히 3개 Embed
- 상세 이벤트의 두 고정 title 순서를 message contract 한 곳에서 관리
- 비문자열·`None`·빈 title을 traceback 없이 검증 오류로 수집
- 공통 helper에서 기대 수, payload 수, title 목록, 전체 텍스트 길이 검증
- 전송 전 GitHub Step Summary에 안전한 metadata만 출력
- sender `wait=true` URL 처리와 Discord message JSON 검증
- HTTP 성공이어도 message 식별자·embeds·요청/생성 개수 계약 불충족 시 실패
- PR 상태 기반 Preview 판정: merged, draft, open-ready, API 실패와 미병합 종료 fallback
- Preview 제목 `🧪 [PREVIEW]`, 상세 제목 `🔍`, `🚦`와 승인된 field label 이모지 적용
- Webhook URL, response body 전체, field value 전체와 PR 본문 전체 비출력 유지

## 변경 범위

- `.github/scripts/collect-discord-context.py`
- `.github/scripts/build-discord-payload.py`
- `.github/scripts/discord-message-contract.py`
- `.github/scripts/send-discord-notification.py`
- `.github/workflows/notify-collaboration.yml`
- `scripts/validate-discord-payloads.py`
- `scripts/test_discord_context.py`
- `scripts/test_discord_payload.py`
- `scripts/test_send_discord_notification.py`
- `docs/runbook/collaboration-automation.md`
- `docs/reports/OPS-008/sre-report.md`
- `docs/handoffs/OPS-008/sre-to-tl.md`

제품 코드, API 계약, DB, dependency와 Secret은 변경하지 않았다.

## 변경하지 않은 범위

- Backend·Frontend 제품 코드와 API 계약
- DB, migration, JPA와 신규 dependency
- Webhook Secret 생성·조회·교체
- Discord Bot application과 외부 서비스
- 자동 병합, reset, rebase와 force push

## 검증 결과

- `python scripts/validate-discord-payloads.py` → 통과, fixture 20개
- `python -m unittest discover -s scripts -p "test_*discord*.py"` → 통과, 테스트 44개
- `py scripts\validate-task-artifacts.py --task-id OPS-008` → 통과
- `git diff --check` → 통과
- Python 문법 검사 통과
- 상세 이벤트 3개 Embed와 고유 title
- list·dict·`None`·빈 문자열·공백 title 형식 오류와 중복·순서 불일치 오류
- 병합 PR #40 Preview의 `pr_merged` 판정과 `🧪 [PREVIEW] PR 병합 완료 · #40`
- wait mode message Embed 3개 성공, 1개·JSON 없음·HTTP 204 실패 mock
- 429·5xx 재시도와 4xx 비재시도
- 6000자 제한, mention 보호와 민감정보 비출력

최종 명령별 결과와 Repository Validation 상태는 PR의 GitHub checks를 기준으로 확인한다.

## 적용 방법

- 상세 이벤트는 공통 계약에 따라 정확히 3개 Embed를 생성한다.
- 전송 전에 payload의 기대·실제 Embed 수, title과 길이를 검증한다.
- Actions sender는 Webhook에 `wait=true`를 추가한다.
- Discord 생성 message의 식별자와 Embed 수가 요청·기대 수와 일치해야 최종 완료로 기록한다.
- Preview는 `merged`, 미병합 종료, open Draft, open Ready 순서로 PR 상태를 판정한다.
- 이모지는 Discord에 표시되는 title과 field label에만 적용한다.
- 병합 후 `main`의 `Collaboration Notification`에서 `scenario=pr_preview`, `pr_number=40`으로 실제 전송을 확인한다.

## 실패 후 수정

첫 sender 테스트에서 JSON 파싱 실패 경로가 초기화되지 않은 `message` 변수를 참조했다. 실패 경로를 한 번 수정하고 sender 대상 테스트를 재실행해 통과시켰다.

## 병합 전 미실행 검증

PR branch script를 Repository Secret과 함께 실행하지 않는 신뢰 경계를 유지하므로 실제 Discord Webhook 전송은 실행하지 않는다. 병합 전 검증은 Secret 없는 fixture와 mock, Repository Validation로 제한한다.

## 병합 후 수동 Preview

1. 사용자가 OPS-008 PR 병합을 결정한다.
2. 기본 브랜치 `main`에서 `Collaboration Notification`을 수동 실행한다.
3. `scenario=pr_preview`, `pr_number=40`을 입력한다.
4. Actions에서 `pr_merged`, 기대·payload·created Embed 수 3과 message contract success를 확인한다.
5. Discord에서 `🧪 [PREVIEW] PR 병합 완료 · #40`, `🔍 처리·검증·리뷰`, `🚦 상태와 다음 작업`을 확인한다.
6. 사용자가 PC·모바일 가독성, 링크, 이모지, mention 비활성화를 직접 확인한다.

## 위험과 제한

- Discord가 향후 message 응답 계약을 변경하면 HTTP 성공 후 contract failure가 발생할 수 있다. 이 경우 Secret이나 response body를 출력하지 말고 API 계약과 안전한 개수 metadata만 확인한다.
- 롤백은 OPS-008 PR을 병합하지 않거나, 병합 후 별도 revert PR로 workflow·sender·builder 변경을 되돌린다. 자동 병합은 사용하지 않는다.

## 다음 작업

1. PR #41의 최신 Repository Validation과 review 상태를 GitHub에서 확인한다.
2. 사용자가 병합 여부를 결정한다.
3. 병합 후 기본 브랜치에서 수동 Preview를 실행한다.
4. Expected/Payload/Created Embed 수가 모두 3인지 확인한다.
5. Discord의 실제 세 카드와 PC·모바일 가독성을 확인한다.

## Git 결과

- 작업 브랜치: `ops/sre`
- 구현 커밋:
  - `55233f6` `fix(discord): 상세 카드 검증과 이모지 개선`
  - `b45c55e` `fix(discord): 상세 알림 검증 마무리`
  - `cb090d2` `fix(discord): 상세 알림 검증 마무리`
- 이번 최종 수정 커밋은 자기참조를 피하고 PR #41의 GitHub 커밋 이력에서 확인한다.
- `origin/ops/sre` 일반 push 완료
- PR #41 생성 및 갱신 완료
- reset, rebase, force push와 자동 병합 미실행

## PR 상태

- PR: #41
- base: `main`
- head: `ops/sre`
- 제목: `fix(discord): 상세 카드 검증과 이모지 개선`
- 최초 Draft 생성 후 필수 검증을 거쳐 Ready로 전환했다.
- 최신 Repository Validation과 review 상태는 GitHub를 기준으로 확인한다.
- 자동 병합하지 않는다.
- 실제 Discord Webhook Preview는 병합 후 기본 브랜치에서 수행한다.
