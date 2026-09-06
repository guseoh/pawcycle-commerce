---
name: pr-readiness-review
description: >-
  PawCycle Commerce의 Pull Request를 병합하기 전에 최신 HEAD, main drift, CI, review와 의미상 위험을 확인해 READY 또는 CHANGES REQUIRED로 판정할 때 사용한다.
---

# PR Readiness Review Skill

이 Skill은 **병합 준비도 검토 절차**만 정의한다. 공통 안전·Git write·운영 경계는 루트 `AGENTS.md`, Harness 완료 기준은 `docs/runbook/lean-harness.md`, GitHub evidence 수집 방법은 `docs/runbook/github-mcp-agent.md`를 따른다.

## 대상 PR 자동 탐색

사용자가 PR 번호를 명시하지 않았더라도 Codex/다른 AI 구현 직후 `검토해줘`, `이어서 검토`, `최종 검토해줘`처럼 요청하면 PR 번호를 다시 요구하는 것을 기본 동작으로 삼지 않는다.

다음 순서로 관련 open PR을 찾는다.

1. 현재 요청이나 대화에 명시된 PR 번호가 있으면 그것을 사용한다.
2. 현재 작업의 Task ID 또는 branch가 알려져 있으면 정확히 일치하는 open PR을 우선한다.
3. 그렇지 않으면 base가 `main`이고 PR 본문에 `<!-- pawcycle-ai-handoff: review-ready -->`가 있는 최신 open PR을 찾는다.
4. 여러 후보가 있으면 현재 Task ID, branch, 최근 작업 맥락과 head SHA를 이용해 좁힌다.
5. 그래도 동률인 후보가 남으면 임의 선택하지 않고 후보를 명시한다.

handoff marker는 **검토 시작 신호**일 뿐 merge-ready, `Verified` 또는 사용자 승인으로 해석하지 않는다.

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
   - CodeRabbit review 요청은 `.github/workflows/request-coderabbit-review.yml` 자동화를 기본 경로로 사용한다.
   - 현재 저장소가 native auto-review 대상이 아닌 동안에는 handoff marker가 있는 최신 HEAD를 자동 요청하고, 저장소 전체 65분 cooldown과 주기적 retry로 rate-limit 중복 요청을 피한다.
   - 저장소가 native auto-review 조건을 충족하면 자동 요청 workflow가 수동 명령을 보내지 않고 native review에 맡긴다.
   - 최신 HEAD가 실제 CodeRabbit review `commit_id`에 포함되는지 확인한다. 자동 요청 comment나 passing status만으로 review 완료를 주장하지 않는다.
   - rate limit, 파일 수, 서비스 제한으로 최신 HEAD review가 없으면 그 한계를 기록하고 독립 diff 검토로 보완한다.
   - `@coderabbitai review` 수동 입력은 자동 요청 workflow 자체가 실패하거나 사용자가 명시적으로 요구한 예외 상황에서만 사용한다.
   - review submission, inline thread, issue comment의 실제 상태를 최신 HEAD와 대조한다.
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
