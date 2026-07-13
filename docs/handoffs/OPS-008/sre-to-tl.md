# OPS-008 Platform/SRE → Tech Lead 인수인계

## 전달 목적

Discord 상세 알림이 HTTP 성공만으로 완료되지 않고 요청 payload와 Discord 생성 message의 3-Embed 계약까지 검증하는지 확인한다.

## 다음 역할

Tech Lead가 구현 diff, 신뢰 경계, Repository Validation과 병합 후 Preview 절차를 최종 검토한다.

## 입력 문서

- OPS-008 승인 요구사항
- 기준 Actions run `29257861839`, job `86842913462`
- `docs/runbook/collaboration-automation.md`
- OPS-007 collector·builder·sender와 협업 workflow

## 사용 가능한 결과

- 상세 이벤트 3-Embed 공통 계약
- payload 안전 metadata와 전송 전 검증
- wait mode Discord 생성 message 검증
- 실제 PR 상태 기반 Preview와 이모지 표시 정책
- collector·builder·sender 테스트와 fixture validator
- OPS-008 작업 보고서와 갱신된 Runbook

## 검증 포인트

- `pull_request_target`, `workflow_run`, `workflow_dispatch`가 계속 기본 브랜치 script를 checkout하는가
- `DISCORD_WEBHOOK_URL`이 sender step에만 전달되는가
- `pr_ready`, `pr_merged`, review 상세 이벤트와 모든 `ci_*`가 정확히 3개 Embed인가
- payload 계약 검증이 Discord 전송 전에 실행되는가
- sender가 `wait=true`를 사용하고 message 식별자와 응답 `embeds` 수를 검증하는가
- wait mode가 message 계약 성공 전에는 `Discord 알림 전송 완료`를 기록하지 않는가
- HTTP 204, JSON 파싱 실패, 생성 Embed 1개가 성공으로 처리되지 않는가
- 로그와 Step Summary에 Webhook URL, response body, field value와 PR 본문 전체가 없는가
- title과 주요 field label의 이모지가 정보를 과도하게 복잡하게 만들지 않는가
- 제품 코드, API 계약, dependency와 Secret 변경이 없는가

## 미결정 사항 또는 승인 필요 항목

- OPS-008 PR의 Ready 상태 최종 검토와 병합 여부
- 병합 후 Discord PC·모바일 화면의 세 카드 가독성 승인
- Discord 응답 계약 변경이 관찰될 경우 후속 대응 여부

## 계약 요약

상세 이벤트는 다음 3개 Embed를 사용한다.

1. 이벤트별 작업 요약
2. `🔍 처리·검증·리뷰`
3. `🚦 상태와 다음 작업`

Preview는 실제 PR 상태를 반영한다. PR #40은 병합된 PR이므로 기대 첫 제목은 `🧪 [PREVIEW] PR 병합 완료 · #40`이다. API 조회 실패와 미병합 종료 PR은 `pr_ready`로 표시하지 않는다.

## 병합 후 확인 절차

1. OPS-008 PR을 사용자가 병합한 뒤 `main`의 `Collaboration Notification`을 연다.
2. `scenario=pr_preview`, `pr_number=40`으로 수동 실행한다.
3. Actions에서 다음을 확인한다.
   - `Discord event: pr_merged`
   - Expected/Payload/Created embed count: 모두 3
   - `Discord message contract: success`
   - 계약 성공 뒤 `Discord 알림 전송 완료`
4. Discord 화면에서 세 카드, 이모지 렌더링, PC·모바일 가독성, PR 링크를 확인한다.
5. Secret·전체 로그가 없고 `@everyone`, `@here`가 mention으로 동작하지 않는지 확인한다.

## 제한과 결정

- 과거 run에는 payload 또는 생성 message Embed 수 증거가 없어 한 개 Embed 현상의 최종 발생 구간을 확정하지 않았다.
- 실제 Webhook 검증은 기본 브랜치 신뢰 경계 때문에 병합 후에만 가능하다.
- 병합과 화면 가독성 승인 여부는 사용자가 결정한다. 자동 병합은 설정하지 않는다.

## 중단 조건

- Repository Validation 또는 필수 Discord 단위 테스트 실패
- PR branch script와 Repository Secret을 함께 실행해야 하는 상황
- Webhook URL이나 response body 전체를 출력해야만 진단 가능한 상황
- 제품·API·DB·보안 정책의 새 결정이 필요한 상황
- destructive Git 작업이 필요한 상황
