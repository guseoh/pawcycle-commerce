# HARNESS-AGENT-002 ChatGPT Connector Pilot 준비 계획

## 상태

- 작업 ID: `HARNESS-AGENT-002`
- 작업 등급: 일반
- 실행 구분: 저장소 변경 + 읽기 전용 GitHub 측정
- 역할: Tech Lead
- 시작 기준 `main`: `f6a50b5eb032355a6596bbb7c85da0fe6f17a273`
- 선행 기준선: `docs/reports/HARNESS-AGENT-001A/**`

## 목적

현재 사용자 + ChatGPT GitHub Connector 작업 방식을 대조군 Pilot으로 측정하고, 향후 Codex + GitHub MCP + Rule·Skill·Tool Allowlist 환경과 동일 조건으로 비교한다.

`HARNESS-AGENT-001A`의 역사적 Before 자료는 문제 사례와 복잡도 설명에만 사용하고 정량 Benchmark 기준값으로 소급 사용하지 않는다.

## 포함 범위

- PR #84·#85·#86·#87·#89 역사적 GitHub 상세 지표
- 고정 시나리오 A·B·C·D의 ChatGPT Connector Pilot 각 3회
- 시간, Tool 호출, 실패 호출, 중복 읽기, 사용자 개입과 정확도 기록
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
└── connector-pilot-summary.md
```

## 측정 계약

- 대조군: 사용자 + ChatGPT GitHub Connector
- 실험군: 사용자 + Codex GitHub MCP + Rule·Skill·Tool Allowlist
- A·B·C·D 각각 3회
- 동일 입력·고정 대상·고정 정답표
- 독립 세션 또는 문맥 초기화 환경
- 시간 비교는 중앙값 우선
- Production·AWS·운영 DB·Secret 쓰기는 실패 처리

## 실행 순서

1. 역사적 PR 상세 지표를 보조 자료로 확정한다.
2. ChatGPT Connector Pilot 12개 실행 슬롯을 고정한다.
3. 독립 세션 조건으로 A~D를 각 3회 실행한다.
4. 정답표로 정확도와 누락·범위 이탈을 판정한다.
5. 시나리오별 중앙값과 사용자 개입을 집계한다.
6. Codex 토큰 복구 후 동일 조건의 MCP 실험군을 실행한다.
7. 실제 작업 3~5건으로 Benchmark 결과를 보완한다.

## 결과 또는 증거

현재 확보:

- 역사적 PR 상세 지표 5건
- ChatGPT Connector Pilot 실행 슬롯 12건
- 비교 대조군·실험군과 공통 측정 계약

현재 미실행:

- ChatGPT Connector Pilot 12회
- Codex GitHub MCP 12회
- 실제 작업 3~5건
- 중앙값·개선률 계산

## 완료 조건

- ChatGPT Connector Pilot 12회와 Codex MCP 12회 모두 실행됨
- 각 실행에 시간·Tool 호출·실패·정확도·사용자 개입·범위 이탈 기록
- 동일 정답표로 판정됨
- 시나리오별 중앙값과 전체 중앙값 계산됨
- 실제 작업 3~5건의 운영 결과가 보조 증거로 있음
- Production·AWS·운영 DB·Secret 실행 0건

## 위험·제한

- 역사적 PR Lead Time은 노동 시간이 아니다.
- 현재 Review thread 상태는 병합 당시 상태와 다를 수 있다.
- 네트워크·GitHub 상태·캐시·세션 문맥이 시간에 영향을 준다.
- ChatGPT Connector Pilot 완료 전 개선률을 계산하지 않는다.
- 표본 수가 작으므로 일반적인 AI Agent 성능으로 과장하지 않는다.
