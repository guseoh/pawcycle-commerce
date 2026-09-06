---
name: codex-delta-prompt
description: >-
  PawCycle Commerce의 승인된 작업 명세를 최신 main과 기존 구현 대비 실제 변경 Delta만 남긴 Final Lightweight Delta Prompt 또는 후속 수정 Prompt로 변환할 때 사용한다.
---

# Codex Delta Prompt Skill

이 Skill은 **상세 작업 명세를 실행용 Prompt로 경량화하는 절차**만 정의한다. 공통 안전 규칙과 작업 등급·실행 구분은 루트 `AGENTS.md`와 `docs/runbook/lean-harness.md`를 따른다.

## 핵심 원칙

`Spec ≠ Prompt`다.

상세 분석은 문제·근거·대안·위험을 충분히 포함할 수 있지만, Codex에 전달하는 최종 Prompt에는 **현재 작업에서 실제로 바뀌어야 하는 Delta와 검증 경계**만 남긴다.

## 실행 절차

1. **입력 고정**
   - 현재 사용자 지시, 승인된 작업 명세, 최신 `main`, 실제 구현과 관련 계약을 확인한다.
   - 동적 branch/PR 상태가 필요한 경우 작업 시점에 다시 읽는다.

2. **Delta 추출**
   - 이미 구현된 내용, 변하지 않는 계약, AGENTS/Runbook에 이미 있는 공통 규칙을 제거한다.
   - 이번 작업에서 추가·수정·삭제해야 하는 동작만 남긴다.
   - 보호해야 하는 기존 동작이나 backward compatibility가 있으면 명시한다.

3. **범위 경량화**
   - 프로젝트 전체 배경, 긴 역사, 역할 설명, 문서 원문 복사를 제거한다.
   - 구현을 막지 않는 선택지는 Prompt에 의사결정 과제로 넘기지 않는다.
   - 승인되지 않은 제품·API·DB·보안·아키텍처 결정이 필요하면 Prompt를 만들기 전에 중단 조건으로 올린다.

4. **Acceptance Criteria 정리**
   - 동작 결과, 외부 계약, 데이터/transaction invariant, 필요한 regression을 검증 가능한 문장으로 만든다.
   - 단순 파일 목록보다 사용자 목적이 완료되는 조건을 우선한다.

5. **검증과 중단 조건 정리**
   - 가장 작은 관련 검증과 필요 시 확대 검증을 지정한다.
   - 실패 원인을 모른 채 반복 실행하지 않도록 중단 조건을 남긴다.
   - 실제 운영 실행이 필요한 부분은 저장소 준비 Prompt와 분리한다.

6. **최종 Prompt 생성**
   - 문제/목표
   - Current Delta
   - 포함 범위
   - 제외 범위
   - Acceptance Criteria
   - Validation
   - Stop Conditions
   - Git/PR 결과물

7. **PR handoff 계약 연결**
   - 승인된 작업이 commit/push/Draft PR 생성까지 포함하면 Codex가 구현과 정의된 검증을 마친 뒤 PR 본문 마지막에 `<!-- pawcycle-ai-handoff: review-ready -->`를 추가하도록 최종 Prompt에 넣는다.
   - 이 marker는 **독립 검토를 시작할 수 있다는 handoff 신호**일 뿐 `Verified`, merge-ready 또는 사용자 승인 의미가 아니다.
   - marker가 있는 PR은 `.github/workflows/request-coderabbit-review.yml`이 CodeRabbit review 요청을 자동 관리한다. Codex Prompt에 `@coderabbitai review` 수동 호출을 넣지 않는다.
   - Codex 최종 응답에는 PR 번호/URL, 최신 HEAD SHA, 실행한 검증과 미실행 항목을 짧게 남긴다. 사용자가 이 정보를 다시 ChatGPT에 복사해야 하는 계약으로 만들지는 않는다.

## 후속 수정 모드

리뷰나 CI finding 이후에는 원래 Prompt 전체를 다시 보내지 않는다. 다음만 남긴다.

- 현재 HEAD와 유효 finding
- 왜 finding이 유효한지
- 필요한 동작 변화
- 보호할 기존 계약
- regression test 또는 validator
- 재검증 범위와 중단 조건

후속 수정 후 같은 PR을 계속 사용하는 경우 최신 HEAD까지 검증을 갱신하고 handoff marker를 유지한다. marker가 유지되면 CodeRabbit review 요청 자동화도 최신 HEAD를 다시 평가한다. marker 자체를 성공 판정으로 해석하지 않는다.

## 출력 기준

최종 결과는 **Codex에 그대로 전달할 수 있는 하나의 경량 Prompt**여야 한다. 분석 메모, 모델 추천, 작업 등급 설명, 역할 소개는 Prompt 밖에서 다룬다.
