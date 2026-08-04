# HARNESS-AGENT-002 ChatGPT Connector Pilot 준비·실행 기록

## 상태

- 작업 ID: `HARNESS-AGENT-002`
- 작업 등급: 일반
- 실행 구분: 저장소 변경
- 역할: Tech Lead
- 시작 기준 `main`: `f6a50b5eb032355a6596bbb7c85da0fe6f17a273`
- 선행 기준선: `docs/reports/HARNESS-AGENT-001A/**`

## 목적

현재 사용자 + ChatGPT GitHub Connector 작업 방식을 대조군 Pilot으로 측정하고, 향후 Codex + GitHub MCP + Rule·Skill·Tool Allowlist 환경과 동일 조건으로 비교한다.

`HARNESS-AGENT-001A`의 역사적 Before 자료는 문제 사례와 복잡도 설명에만 사용하고 정량 Benchmark 기준값으로 소급 사용하지 않는다.

## 포함 범위

- PR #84·#85·#86·#87·#89 역사적 GitHub 상세 지표
- 고정 시나리오 A·B·C·D의 ChatGPT Connector Pilot 각 3회
- Tool 호출, 실패 호출, 사용자 개입, 정확도와 수집 가능한 시간 기록
- 향후 Codex GitHub MCP와 동일 입력·대상·정답표 비교 계약
- 수집 불가능한 증거의 명시적 누락 처리

## 제외 범위

- GitHub MCP 설치·인증·Tool Allowlist 실제 적용
- Codex MCP 실험군 실행
- 실제 작업 3~5건 시범 운영
- `AGENTS.md`, Skill, PR template, validator 변경
- Backend·Frontend·API·DB·Production·AWS·Secret 변경

## 산출물

```text
docs/reports/HARNESS-AGENT-002/
├── preparation-plan.md
├── historical-pr-details.csv
├── benchmark-plan-chatgpt-connector.jsonl
├── benchmark-results-chatgpt-connector.jsonl
├── benchmark-results-chatgpt-connector-independent.jsonl
├── connector-pilot-summary.md
├── connector-pilot-results.md
└── connector-pilot-independent-summary.md
```

## 측정 계약

- 대조군: 사용자 + ChatGPT GitHub Connector
- 실험군: 사용자 + Codex GitHub MCP + Rule·Skill·Tool Allowlist
- A·B·C·D 각각 3회
- 동일 입력·고정 대상·고정 정답표
- Production·AWS·운영 DB·Secret 쓰기는 실패 처리
- 복원하거나 안정적으로 측정할 수 없는 값은 추정하지 않음

## 실행 결과

- 역사적 PR 상세 지표 5건 수집
- ChatGPT Connector Pilot A·B·C·D 총 12회 기록
- A·B 독립 원본 재조회와 시간 중앙값 확보
- C·D 독립 evidence path 각 3회 보충
- 전체 독립 evidence path 12/12
- 정확도 12/12 `pass`
- Tool 실패·범위 이탈·Production 접근 0건
- C·D의 동일 기준 end-to-end 시간은 미수집으로 보존

## 미실행과 이유

- Codex GitHub MCP 12회: 실제 MCP 연결 이후 후속 작업에서 실행
- 실제 작업 3~5건: Codex MCP 준비와 통제 시나리오 검증 이후 실행
- C·D 개선률: 대조군과 실험군 양쪽의 동일 외부 타이머가 없어 계산하지 않음

## 완료 판정

```text
ChatGPT Connector 대조군 Pilot
→ 완료

Codex GitHub MCP 실험군
→ 미실행

전체 비교
→ 미완료
```

이번 작업은 대조군 증거를 확정하는 범위로 완료한다. 전체 MCP 효과 검증 완료로 표현하지 않는다.

## 위험·제한과 복구

- 같은 ChatGPT 대화 문맥 영향을 완전히 제거할 수 없다.
- A·B만 시간 중앙값을 비교 기준으로 사용할 수 있다.
- 표본 수가 작아 일반적인 AI Agent 성능으로 확대 해석하지 않는다.
- 문서·측정 원본만 추가한 변경이므로 PR 또는 관련 문서 revert로 복구한다.
- Production·AWS·운영 DB·Secret은 변경하지 않았다.
