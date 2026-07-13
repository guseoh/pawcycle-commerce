# OPS-007 Platform/SRE → Tech Lead 인수인계

## 전달 목적

단일 Discord 협업 알림 workflow와 보고서형 multi-embed 구조의 보안·운영 검토 포인트를 전달한다. 이 변경은 제품 또는 API 계약을 수정하지 않는다.

## 다음 역할

Tech Lead가 PR 구조와 권한, 이벤트별 중복 억제, payload 예시와 실제 Preview 결과를 검토한다. 최종 병합은 사용자가 결정한다.

## 입력 문서

- 사용자 승인 OPS-007 작업 요청
- `docs/reports/OPS-007/sre-report.md`
- `docs/runbook/collaboration-automation.md`
- API-003 승인 계약이 포함된 최신 `main`

## 사용 가능한 결과

- 단일 진입점 `.github/workflows/notify-collaboration.yml`
- 정규화 수집기 `.github/scripts/collect-discord-context.py`
- 보고서 builder `.github/scripts/build-discord-payload.py`
- 정규화 Discord fixture 20개와 collector·builder·sender 테스트
- 전체 review thread 페이지네이션, CI conclusion별 상태와 실제 embed 텍스트 길이 검증
- 제거된 중복 `.github/workflows/notify-ci-result.yml`
- 갱신된 협업 자동화 runbook

payload는 상세 이벤트에서 다음 세 부분을 한 Discord 메시지에 담는다.

1. 작업 요약: 작업 ID, 역할, 작업자, PR, branch, SHA, 변경 규모와 목적
2. 처리·검증·리뷰: 변경·처리 과정, CI job과 실패 step, 리뷰와 미해결 thread
3. 상태와 다음 작업: mergeable, 위험, 다음 담당 작업과 GitHub 링크

## 미결정 사항 또는 승인 필요 항목

- 실제 Discord 채널의 수신 여부와 카드 가독성은 사용자가 확인해야 한다.
- PR 병합과 후속 운영 적용 여부는 사용자 최종 승인이 필요하다.
- 새로운 mention, cookie, 제품, API 또는 인프라 정책 결정은 없다.

## 검증 포인트

1. notification workflow 전체에서 `Repository Validation`용 `workflow_run`이 한 경로인지 확인한다.
2. `pull_request_target`, `workflow_run`, `workflow_dispatch`가 모두 기본 브랜치 script만 checkout하는지 확인한다.
3. 권한이 `contents`, `pull-requests`, `issues`, `actions` read로 제한됐는지 확인한다.
4. API 실패 시 thread와 job을 성공 또는 0으로 오인하지 않고 `확인 불가`로 표시하는지 확인한다.
5. PR Ready, CI failure, changes requested fixture에서 처리 과정, 실패 step, review 상태와 다음 작업이 남는지 확인한다.
6. fenced code, Secret·AWS credential·private key 비노출과 Issue 본문 미전달을 확인한다.
7. 실제 embed 텍스트 합계 6000자 경계와 malformed payload 오류 수집을 확인한다.
8. collector 누락이 성공으로 숨겨지지 않고 CI conclusion별 다음 행동이 분리되는지 확인한다.
9. 병합 후 수동 Preview는 `scenario=pr_preview`, 양의 정수 PR 번호로만 API를 조회하는지 확인한다.

로컬에서 정규화 fixture 20개와 Discord 단위 테스트 35개가 통과했다. 기존 `ops/sre`의 `pr_preview` run `29246535424` HTTP 204는 수정 전 참고 기록이며 최종 보안 검증 증거가 아니다. 수정 후 실제 Webhook Preview는 PR #40 병합 후 기본 브랜치 workflow에서 실행하고, Discord 채널 화면 수신 여부는 사용자가 확인한다.

head `ab83d5a`의 Repository Validation run `29255002315`는 conventions와 application validation 전체가 성공했다. 이어진 collaboration report run `29254995061`은 기본 브랜치 collector 부재를 `exit 1`로 드러내고 전송 단계 전에 종료했다. 기존 review thread 10개는 모두 근거 답변 또는 수정 후 resolved 상태다.

## 중단 조건

- write 권한 확대, PR head script의 Secret 권한 실행 또는 신규 dependency가 필요함
- Webhook URL·token·응답 본문 전체가 로그나 payload에 노출됨
- 동일한 Repository Validation 실행이 둘 이상의 경로로 알림을 생성함
- 필수 fixture 또는 Repository Validation이 실패함
- 제품·API·DB 계약 변경이나 destructive Git 작업이 필요함

## 남은 위험 또는 주의 사항

- 실제 Discord 채널 수신은 HTTP 성공만으로 단정하지 않는다.
- API 일시 장애 시 알림은 전송하되 일부 상세 값은 `확인 불가`다.
- review thread 페이지 조회가 하나라도 실패하면 부분 합계 대신 `확인 불가`가 표시된다.
- 실제 전송은 병합 후 기본 브랜치에서만 검증하며, 첫 운영 이벤트에서 중복 여부와 가독성을 다시 확인한다.
