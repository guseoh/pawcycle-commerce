---
name: pr-readiness-review
description: >-
  PawCycle Commerce의 Pull Request를 병합하기 전에 최신 HEAD, main drift, CI, review와 의미상 위험을 확인해 READY 또는 CHANGES REQUIRED로 판정할 때 사용한다.
---

# PR Readiness Review Skill

이 Skill은 **병합 준비도 검토 절차**만 정의한다. 공통 안전·Git write·운영 경계는 루트 `AGENTS.md`, Harness 완료 기준은 `docs/runbook/lean-harness.md`, GitHub evidence 수집 방법은 `docs/runbook/github-mcp-agent.md`를 따른다.

## 실행 절차

1. **최신 상태 고정**
   - PR이 open인지, Draft인지, base/head branch와 최신 head SHA를 확인한다.
   - 최신 `main`과 비교해 behind/base drift, conflict, mergeable 상태를 확인한다.
   - 이후 모든 검증과 review evidence가 이 head SHA를 대상으로 하는지 확인한다.

2. **자동 검증 확인**
   - Repository Validation, metadata validation과 작업 위험에 필요한 추가 workflow를 확인한다.
   - aggregate 실패가 있으면 원인 job을 먼저 찾고 `ci-failure-triage` Skill로 넘긴다.
   - 미실행·skipped·cancelled 검증을 success로 취급하지 않는다.

3. **리뷰 확인**
   - review submission, inline thread, issue comment의 실제 상태를 최신 HEAD와 대조한다.
   - 외부 AI reviewer가 미실행·stale이면 그 한계를 명시하고 독립 검토로 보완한다.
   - reviewer 지적은 그대로 수용하지 않고 현재 계약·코드·테스트와 대조한다.

4. **의미상 diff 검토**
   - 작업 목적과 Acceptance Criteria 대비 실제 변경이 충분한지 본다.
   - 데이터·transaction·보안·release/운영 side effect처럼 CI Green만으로 보장되지 않는 경계를 우선 검토한다.
   - 관련 없는 변경, 숨은 scope expansion, rollback 불가능한 변경이 없는지 확인한다.

5. **Evidence 정합성 확인**
   - PR 본문의 검증 결과, 미실행 항목, review 한계와 남은 위험이 실제 최신 상태와 일치하는지 확인한다.
   - 오래된 run 번호, stale HEAD, 이미 해결된 finding을 현재 evidence처럼 남기지 않는다.

6. **최종 판정**
   - `READY`: blocker 없음 + 필수 검증 완료 + 남은 위험이 수용 가능하고 명시됨.
   - `CHANGES REQUIRED`: 코드·계약·검증·review blocker 또는 불명확한 안전 경계가 남음.
   - 실제 운영 검증을 수행하지 않았다면 `Production Verified`를 사용하지 않는다.

## 출력

- Latest State
- Blocking Findings
- Validation Evidence
- Review Limitations
- Remaining Risk
- Decision: `READY` 또는 `CHANGES REQUIRED`

## 병합 경계

이 Skill의 기본 산출물은 **판정**이다. 사용자가 해당 PR의 병합까지 명시적으로 위임한 경우에만 루트 `AGENTS.md`의 병합 전 재확인 조건을 적용해 병합 단계로 진행한다.
