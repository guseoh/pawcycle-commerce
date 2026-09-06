---
name: github-mcp-readonly
description: >-
  PawCycle Commerce의 PR·Issue·branch·commit·CI·review 같은 GitHub 동적 상태를 읽기 전용으로 확인할 때 사용한다.
---

# GitHub MCP Read-only Skill

상세 Tool/보안 계약은 `docs/runbook/github-mcp-agent.md`가 권위 원본이다. 이 Skill은 **최소 읽기 evidence path**만 정의한다.

## 사용하는 경우

- PR head/base/Draft/mergeable/CI/review 확인
- Issue와 관련 PR 추적
- 고정 ref의 권위 파일 조회
- 실패 Workflow의 최초 실패 Job·Step·Log 분석
- branch/commit/PR 관계 확인

로컬 코드와 테스트만으로 답할 수 있으면 사용하지 않는다.

## 실행 절차

1. 대상 repository와 PR/Issue/ref를 고정한다.
2. 질문에 필요한 최소 읽기 capability만 선택한다.
3. 원본 순서로 확인한다.
   - PR: metadata → head CI → review thread → body
   - CI: run → job → 최초 실패 step/log
   - 권위 문서: 현재 사용자 입력 → latest main 계약 → 실제 코드/테스트
4. 확인 사실, 추론, 미확인 항목을 구분한다.
5. 수정·재실행·사용자 결정 중 다음 행동을 판정한다.

## 금지

- file/ref/PR/Issue/review write
- merge, workflow rerun/cancel/dispatch
- permission/Secret/environment 변경
- Production·Cloud·운영 DB 실행

읽기 중 쓰기가 필요해지면 이 Skill의 범위를 벗어났다고 판단하고 `github-mcp-agent` Runbook의 조건부 쓰기 경계를 다시 확인한다.

## CI 실패 규칙

Aggregate failure보다 최초 실패 원인을 우선한다. 같은 payload를 원인 없이 반복 실행하지 않는다. 외부 reviewer나 API의 페이지·rate limit은 검증 한계로 명시한다.
