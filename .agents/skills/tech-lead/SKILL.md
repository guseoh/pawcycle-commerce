---
name: tech-lead
description: >-
  PawCycle Commerce에서 승인 상태, 작업 범위와 위험, AI 실행 명세, PR 검증과 병합 준비도를 판단할 때 사용한다.
---

# Tech Lead Skill

이 Skill은 **어떻게 판단하고 전달할지**만 정의한다. 지속 책임은 `docs/roles/tech-lead.md`, 공통 안전 규칙은 루트 `AGENTS.md`, Harness 계약은 `docs/runbook/lean-harness.md`를 따른다.

## 실행 절차

1. **상태 확인**
   - 현재 사용자 지시와 최신 `main`의 승인 원본을 확인한다.
   - PR·HEAD·CI·review 같은 동적 상태가 필요하면 `docs/runbook/github-mcp-agent.md`의 최소 evidence path로 다시 읽는다.

2. **작업 정의**
   - 문제, 사용자 목적, 포함·제외 범위, 작업 등급과 실행 구분을 정한다.
   - 하나의 목적을 파일 수나 역할 수만으로 쪼개지 않는다.
   - 구현을 막는 승인 결정만 사용자에게 남긴다.

3. **검증 설계**
   - Acceptance Criteria, 관련 test, 중단 조건과 rollback/revert 경계를 정한다.
   - 고위험이면 데이터·transaction·보안·운영 실패 모드를 명시한다.

4. **AI 실행 전달**
   - 상세 분석과 실행 Prompt를 구분한다.
   - 최종 Prompt에는 현재 작업의 Delta, AC, 검증, 중단 조건만 남긴다.
   - AGENTS/Runbook의 공통 규칙을 Prompt에 다시 복사하지 않는다.
   - 리뷰 후에는 유효 finding과 필요한 회귀 검증만 담은 **후속 수정 Prompt**를 사용한다.

5. **리뷰와 판정**
   - diff, CI, 실제 test evidence와 reviewer finding을 최신 HEAD에 대조한다.
   - 외부 AI review가 미실행이면 그 한계를 기록하고 작업 위험에 맞는 독립 검토로 보완한다.
   - `Implemented / Verified / Production Verified`를 실제 증거 수준에 맞게 구분한다.

## 중단 조건

- 승인되지 않은 제품·API·DB·보안·비용 결정이 필요함
- Secret 또는 운영 원시 값 노출 가능성
- 기준 branch/HEAD가 불명확함
- 실제 운영 실행이 필요한데 별도 승인이 없음
- 필수 검증이 실패했거나 미실행 상태인데 `Verified` 또는 병합 권고를 요구함
- 검증 실패 원인을 모른 채 반복 실행해야 함
