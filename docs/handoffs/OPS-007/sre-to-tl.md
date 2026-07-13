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
- 정규화 Discord fixture 16개와 collector·builder·sender 테스트
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
2. `pull_request_target`과 `workflow_run`이 기본 브랜치 script만 checkout하는지 확인한다.
3. 권한이 `contents`, `pull-requests`, `issues`, `actions` read로 제한됐는지 확인한다.
4. API 실패 시 thread와 job을 성공 또는 0으로 오인하지 않고 `확인 불가`로 표시하는지 확인한다.
5. PR Ready, CI failure, changes requested fixture에서 처리 과정, 실패 step, review 상태와 다음 작업이 남는지 확인한다.
6. `allowed_mentions`, 6000자 경계와 Secret 비노출 검증을 확인한다.
7. 수동 Preview는 `scenario=pr_preview`, 양의 정수 PR 번호로만 API를 조회하는지 확인한다.

로컬에서 정규화 fixture 16개와 Discord 단위 테스트 21개가 통과했다. Repository Validation과 실제 Preview 결과는 PR checks에서 최종 확인한다.

## 중단 조건

- write 권한 확대, PR head script의 Secret 권한 실행 또는 신규 dependency가 필요함
- Webhook URL·token·응답 본문 전체가 로그나 payload에 노출됨
- 동일한 Repository Validation 실행이 둘 이상의 경로로 알림을 생성함
- 필수 fixture, Repository Validation 또는 실제 전송이 실패함
- 제품·API·DB 계약 변경이나 destructive Git 작업이 필요함

## 남은 위험 또는 주의 사항

- 실제 Discord 채널 수신은 HTTP 성공만으로 단정하지 않는다.
- API 일시 장애 시 알림은 전송하되 일부 상세 값은 `확인 불가`다.
- review thread 집계는 API 첫 100개 범위다.
- 후속 구독 Backend PR부터 새 알림이 적용되며, 첫 운영 이벤트에서 중복 여부와 가독성을 다시 확인한다.
