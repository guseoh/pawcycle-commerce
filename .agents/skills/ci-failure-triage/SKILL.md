---
name: ci-failure-triage
description: >-
  PawCycle Commerce의 PR이나 branch CI가 실패했을 때 최초 원인 job과 실제 오류를 찾고, 이번 변경과의 인과관계를 판정해 최소 후속 수정과 재검증 범위를 정할 때 사용한다.
---

# CI Failure Triage Skill

이 Skill은 **CI 실패 원인 분석 절차**만 정의한다. 공통 안전 규칙은 루트 `AGENTS.md`, GitHub evidence 수집 방법은 `docs/runbook/github-mcp-agent.md`를 따른다.

## 실행 절차

1. **대상 HEAD 확인**
   - 실패한 workflow가 현재 PR/branch의 최신 head SHA를 대상으로 하는지 확인한다.
   - stale run이면 현재 상태의 blocker로 취급하지 않는다.

2. **최초 원인 job 찾기**
   - aggregate/final gate 실패보다 먼저 실패한 실제 job과 step을 찾는다.
   - `Application validation` 같은 aggregate 결과만 보고 수정하지 않는다.
   - metadata, Harness, Backend, Frontend, Production contract 등 failure domain을 분류한다.

3. **실제 오류 읽기**
   - 실패 job log에서 최초 의미 있는 error와 관련 stack/message를 확인한다.
   - warning, 후속 cascading failure, 취소된 step을 root cause로 오인하지 않는다.

4. **인과관계 판정**
   - 이번 diff로 생긴 regression인지, stale contract/test인지, 환경/서비스 제한인지 구분한다.
   - 현재 코드와 계약을 확인해 테스트를 고칠지 구현을 고칠지 결정한다.
   - 테스트 통과만을 위해 제품/계약 정보를 버리는 후속 수정을 만들지 않는다.

5. **최소 후속 수정 정의**
   - root cause를 닫는 최소 Delta를 정한다.
   - 보호할 기존 동작과 필요한 regression을 함께 지정한다.
   - 문제와 무관한 dependency·리팩터링·workflow 변경을 섞지 않는다.

6. **재검증**
   - 가장 가까운 regression부터 실행하고, classifier가 요구하는 component lane과 최종 aggregate까지 확인한다.
   - 같은 실패를 원인 분석 없이 반복 실행하지 않는다.

## 출력

- Failed Head / Workflow
- First Causal Job & Step
- Root Cause
- Relation to Current Diff
- Minimal Follow-up Change
- Regression / Validation
- Remaining Risk

## 다른 Skill과의 연결

- 후속 수정 구현을 Codex에 넘길 때는 `codex-delta-prompt` Skill로 작은 후속 수정 Prompt를 만든다.
- 모든 검증이 끝난 뒤 병합 준비도를 판단할 때는 `pr-readiness-review` Skill을 사용한다.
