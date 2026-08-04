---
name: github-mcp-readonly
description: >-
  PawCycle Commerce에서 GitHub MCP의 읽기 전용 capability로 PR·Issue·권위 문서·Workflow 실패를 조사하고, 확인 사실·추론·미확인 항목과 안전한 다음 행동을 판정할 때 사용한다.
---

# GitHub MCP 읽기 전용 Skill

## 1. Skill 이름

`github-mcp-readonly`

## 2. Skill 설명

GitHub 동적 상태가 필요한 작업에서 최소 읽기 Tool만 사용해 증거 경로를 구성한다. 이 Skill은 GitHub 쓰기, PR 병합, Workflow 재실행과 실제 운영 실행을 허용하지 않는다.

업무 흐름, Tool Allowlist, 보안·중단 기준은 `docs/runbook/github-mcp-agent.md`를 따른다.

## 3. 사용하는 상황

- PR의 Draft·head/base·최종 CI·review thread를 확인한다.
- Issue의 열린 후속 작업과 실행 금지 범위를 추적한다.
- 고정 ref에서 제품·API·ADR·데이터·AGENTS 원본을 찾는다.
- 실패 Workflow의 최초 오류와 수정·재실행 필요성을 판단한다.
- branch·commit·PR 관계를 원격 상태와 대조한다.

## 4. 사용하지 않는 상황

- 로컬 파일과 테스트만으로 답할 수 있다.
- GitHub 쓰기·병합·삭제·Workflow 재실행이 목적이다.
- Production·Cloud·운영 DB·Secret 접근이 필요하다.
- 대상 저장소나 ref가 확정되지 않았다.

## 5. 입력

최소 입력:

- 저장소 전체 이름
- 작업 ID·등급·실행 구분·역할
- 대상 PR·Issue·commit 또는 고정 ref
- 확인할 질문과 완료 기준

입력이 불명확하면 검색 범위를 임의로 넓히지 않고 중단 조건을 적용한다.

## 6. 실행 절차

1. 현재 사용자 지시, 작업 명세, `AGENTS.md`와 `docs/runbook/github-mcp-agent.md`를 확인한다.
2. 대상 저장소와 고정 ref를 확인하고 다른 저장소 결과를 배제한다.
3. 질문에 필요한 최소 capability만 선택한다.
4. 원본을 다음 순서로 읽는다.
   - PR: metadata → head CI → review thread → 본문 경계
   - Issue: metadata·본문 → 관련 원본·PR
   - 권위: 제품 → API·ADR·데이터 → 경로별 AGENTS → 역할 Skill
   - CI: Workflow → 실패 Run → 실패 Job → 최초 실패 Step·Log
5. 확인 사실, 근거 기반 추론, 미확인 항목을 분리한다.
6. 코드·문서 수정, 제한된 재실행, 사용자 결정 필요 중 하나로 다음 행동을 분류한다.
7. 저장소·ref·관측 시점·페이지 제한과 Production 실행 여부를 보고한다.

## 7. Tool Allowlist

허용:

- repository·branch·commit metadata read
- file·tree·compare read
- PR metadata·diff·review read
- Issue read
- commit status·Workflow Run·Job·Step·Log read

금지:

- file·branch·PR·Issue·comment·review write
- review thread resolve
- Workflow rerun·cancel·dispatch
- merge·delete·force·permission·Secret·environment 변경
- Production·AWS·운영 DB·Docker·SSH·SSM 실행

Tool 이름은 실행 시점에 확인하며, 이름이 비슷하다는 이유로 쓰기 Tool을 대체 호출하지 않는다.

## 8. 출력 형식

```text
대상: <repository / PR·Issue·ref>
관측 시점: <시각>
확인 방법: <evidence path>

확인됨
- <원본으로 확인한 사실>

판정
- <근거 기반 결론>

미확인·제한
- <페이지·권한·로그·시간 측정 한계>

다음 행동
- <수정 / 제한된 재실행 / 사용자 결정 / 없음>

실제 Production 실행
- 없음
```

동적 Workflow ID·mergeable·thread 수를 장기 권위 문서에 고정하지 않는다. 역사적 실행 증거에 필요한 경우 관측 시점과 역할을 명시한다.

## 9. CI 실패 분석 규칙

- Aggregate Job보다 최초 실패 Job·Step을 우선한다.
- 코드·문서·PR metadata·환경·flaky를 구분한다.
- 같은 payload의 재실행은 transient 근거가 있을 때만 제한적으로 제안한다.
- 동일 PR에서 실패가 3회 이상이면 재실행을 멈추고 공통 최초 원인을 분석한다.
- 실패를 해결하지 못하면 통과로 기록하지 않는다.

## 10. 중단 조건

- 저장소·PR·Issue·ref 불일치
- branch·worktree·고유 commit 관계 불명확
- Secret·개인정보·운영 원시 값 노출 의심
- 읽기 요청 중 쓰기 Tool 또는 권한 확대 필요
- 승인되지 않은 제품·API·DB·보안 결정 필요
- Production·Cloud·운영 DB·비용 실행 필요
- 최신 `main`과 GitHub 원격 증거 충돌
- 필요한 페이지·로그·권한 부족
- CI 3회 실패 후 최초 원인 식별 불가

## 11. 검증

- 같은 입력에서 저장소와 고정 ref가 유지되는지 확인한다.
- A·B·C·D Benchmark에서 독립 원본 재조회와 Tool 호출을 기록한다.
- 사용자 개입·시간을 측정하지 않았으면 `null`과 미측정 상태로 남긴다.
- 범위 밖 쓰기와 Production 접근은 0건이어야 한다.

## 12. 공통 운영 기준

- 공통 Git·PR·권위 규칙은 루트 `AGENTS.md`를 따른다.
- 작업 등급과 저장소 준비 경계는 `docs/runbook/lean-harness.md`를 따른다.
- GitHub MCP 운영 계약은 `docs/runbook/github-mcp-agent.md`를 따른다.
