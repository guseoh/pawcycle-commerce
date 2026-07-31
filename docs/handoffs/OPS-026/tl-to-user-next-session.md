# OPS-026 Tech Lead → 사용자·다음 채팅 인수인계

## 전달 목적

현재 채팅에서 PR #77 검토와 수정까지 완료한 상태를 다음 채팅으로 전달한다. 이 문서는 세션 연속성을 위한 보조 문서이며 제품·운영 계약, GitHub 동적 상태와 실제 Production 상태의 권위 원본을 대체하지 않는다.

## 대상 역할 또는 운영자

- 사용자: PM·Product Owner·Tech Lead·실제 운영 명령 실행자
- 다음 채팅의 ChatGPT: 최신 `main`, PR #77, 활성 PCC_V3와 실제 운영 증거를 다시 확인하고 단계별 명령·중단 조건·검증 기준을 제공
- Codex: 다음 Control 실제 검증 작업에는 사용하지 않음

## 후속 작업 수행 방식

다음 작업은 Codex 구현 작업이 아니라 **사용자와 ChatGPT가 함께 수행하는 고위험 실제 운영 검증**으로 진행한다.

- ChatGPT는 각 단계 직전에 최신 GitHub·저장소·운영 증거를 확인한다.
- ChatGPT는 한 번에 필요한 최소 명령, 예상 결과, 중단 조건과 복구 기준을 제시한다.
- 사용자는 자신의 로컬 또는 Production 환경에서 명령을 직접 실행한다.
- 사용자는 Secret·개인정보·원시 DB 값을 제외한 결과만 채팅에 전달한다.
- ChatGPT는 전달된 결과를 판독하고 다음 단계 진행·중단·복구 여부를 결정한다.
- 실제 AWS·Production Docker·DB·Secret·비용 변경은 사용자의 단계별 명시 승인 없이는 실행하지 않는다.
- 저장소 문서 보완이 필요하면 ChatGPT가 GitHub에서 최소 변경을 작성·검증할 수 있지만, 운영 명령 자체는 사용자가 실행한다.
- Codex prompt 작성, Codex 구현, Codex 하위 에이전트 사용은 이번 후속 작업 범위에서 제외한다.

## 입력 문서

- `docs/reports/OPS-024/tl-report.md`
- `docs/reports/OPS-021/production-execution-report.md`
- `docs/reports/OPS-025/sre-report.md`
- `docs/runbook/OPS-025-production-db-restore.md`
- `docs/reports/OPS-026/tl-report.md`
- `docs/architecture/production-operations-overview.md`
- PR #77의 diff, CI와 review thread
- 활성 Source Set `PCC_V3`

## 사용 가능한 결과

- PR #77의 잘못된 `OPS-VERIFY-001 = Verified` 판정을 철회했다.
- OPS-024 보고서와 Production 운영 개요는 `main` 버전으로 복원해 역사적 원본을 보존했다.
- OPS-026 보고서는 `OPS-VERIFY-001 = Decision Required`로 재작성했다.
- 일곱 최소 기준의 현재 판정은 `충족 6`, `부분 충족 1`이다.
- 부분 충족 항목은 OPS-025 이후 현재 Control의 실제 Production 적용·deploy·rollback 검증이다.
- 외부 unknown Host, HTTPS 자동 갱신 schedule·certificate backup과 다른 잔여 위험을 보고서에 유지했다.
- 판정 철회·재확정은 기본적으로 고위험 검토·검증을 유지하도록 정리했다.
- PR #77의 최종 diff는 OPS-026 보고서와 이 인수인계서로 제한한다.

## 현재 위치

```text
1차 MVP·기본 Production 운영 기반
→ OPS-021 실제 Application rollback·원래 Release 재배포
→ OPS-025 Actual Production DB restore 저장소 준비
→ OPS-026 재평가
→ 현재: OPS-VERIFY-001 Decision Required
```

PCC_V3는 활성화됐지만, OPS-026은 활성화 전에 승인된 기존 작업 명세의 범위를 유지했다. RDS·MVP2·새 Harness 정렬은 PR #77에 혼입하지 않았다.

## 미결정 사항 또는 승인 필요 항목

1. OPS-025 이후 현재 Control을 실제 Production에 적용하고 deploy·rollback·원래 Release 재배포를 검증할지
2. 위 실제 검증의 새 작업 ID와 실행 범위를 무엇으로 정할지
3. OPS-VERIFY-001 재판정을 같은 실행 작업의 완료 판정으로 할지 별도 Tech Lead 검토로 분리할지
4. 현재 Control 검증 전 PR #77을 `Decision Required` 기록으로 병합할지
5. 저장소 `AGENTS.md`, 역할 문서, Skill, PR template, validator를 PCC_V3와 정렬할 시점
6. RDS ADR과 MVP2 계획의 우선순위

기본 권고는 PR #77을 안전한 중단·재평가 기록으로 병합한 뒤, 사용자와 ChatGPT가 별도 고위험 Control 실제 검증 작업을 진행하는 것이다.

## 검증 포인트

다음 채팅 시작 시 반드시 다시 확인한다.

- PR #77의 최신 head·Draft/Ready·mergeable
- Repository Validation과 Application validation
- unresolved·outdated review thread
- `main`이 PR #77 병합 전인지 후인지
- `ops/tl` 브랜치와 worktree 관계
- Production Control·Application·active MySQL volume의 실제 상태
- 실제 운영 실행 전 사용자 승인과 중단·복구 조건

PR #77 변경 내용 자체는 다음을 확인한다.

- `docs/reports/OPS-026/tl-report.md`가 `Decision Required`로 일관됨
- `docs/reports/OPS-024/tl-report.md`가 PR diff에 없음
- `docs/architecture/production-operations-overview.md`가 PR diff에 없음
- `Verified` 확정과 PERF-OPS-001 이동 가능 주장이 제거됨
- HTTPS와 Control 검증 잔여 위험이 유지됨

## 다음 작업 권고

권장 후속 흐름:

```text
사용자·ChatGPT 실행 방식과 작업 ID 확정
→ 작업 등급 고위험
→ 최신 main·PR·현재 Production state 재확인
→ 저장소 준비와 실제 Production 실행 경계 확인
→ 적용 전 release·Control·volume·복귀 가능성 확인
→ 사용자 단계별 명시 승인
→ 사용자가 현재 Control 적용
→ 사용자가 deploy 실행
→ 사용자가 이전 Application rollback 실행
→ 사용자가 원래 Release 재배포
→ ChatGPT와 함께 health·내부 Smoke·외부 HTTPS 결과 판독
→ active-mysql-volume·Application·Control 상태·volume 보존 확인
→ 비민감 실행 증거 기록
→ OPS-VERIFY-001 재판정
```

실제 Production DB restore는 위 Application Control 검증과 별도 위험 경계다. RDS 전환도 별도 ADR·비용·migration 작업으로 다룬다.

## 중단 조건

- 최신 `main`, 역할 브랜치, PR 또는 worktree 관계가 불명확함
- 현재 Production Control과 저장소 Control의 차이를 설명할 수 없음
- active MySQL volume 또는 복귀 release 상태가 불명확함
- Secret·개인정보·원시 DB 값이 출력될 가능성
- 승인되지 않은 AWS·Docker·DB·Secret·비용 작업 필요
- deploy·rollback 전 복귀 가능성 또는 중단 조건을 검증할 수 없음
- 새 데이터·보안·복구 계약 충돌 발견
- RDS·MVP2·PCC_V3 Harness 정렬을 같은 고위험 실행에 혼입하려는 경우

## 남은 위험 또는 주의 사항

- OPS-025 이후 현재 Control의 실제 Production 적용·deploy·rollback·재배포 미검증
- Actual Production DB restore·복귀 훈련 미실행
- 외부 unknown Host 검증 미실행
- HTTPS 자동 갱신 schedule·certificate backup 부재
- source physical volume, EBS·Instance·filesystem 장애 복구 미정
- RPO/RTO, 자동 backup schedule·실패 알림·장기 보존 미정
- 장기 Application·JVM·HTTP·DB·Batch 관측성 미구축
- Blue/Green·무중단·다중 Instance·고가용성 미구현
- RDS는 목표 후보이며 현재 Production DB는 Docker MySQL named volume

## 새 채팅 시작 문구

```text
PawCycle Commerce 프로젝트를 이어간다.

활성 Source Set은 PCC_V3다.
PR #77과 docs/handoffs/OPS-026/tl-to-user-next-session.md를 먼저 확인하고,
최신 main·PR·CI·review·역할 브랜치 상태를 다시 검증해라.

다음 작업은 Codex 없이 사용자와 ChatGPT가 함께 실행한다.
ChatGPT는 단계별 최소 명령·예상 결과·중단 조건·복구 기준을 제시하고,
사용자는 로컬 또는 Production 환경에서 명령을 직접 실행한 뒤 비민감 결과만 전달한다.

OPS-026 결과는 OPS-VERIFY-001 = Decision Required이며,
OPS-025 이후 현재 Control의 실제 Production 적용·deploy·rollback 검증이 남아 있다.
실제 AWS·Production Docker·DB·Secret·비용 작업은 사용자의 단계별 명시 승인 전 금지한다.
```
