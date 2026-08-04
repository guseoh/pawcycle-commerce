# HARNESS-AGENT-002 ChatGPT Connector Pilot 비교 설계

> 상태: 대조군 설계 완료 — Pilot 실행 전  
> 관측 시각: 2026-08-05T00:13:00+09:00  
> 비교 목적: 현재 사용자 + ChatGPT Connector 방식과 향후 Codex + GitHub MCP 방식 비교

## 1. 목적 수정

기존 계획은 GitHub MCP 도입 전 `Before`를 소급 구성하려 했으나, 현재 세션이 이미 GitHub Connector를 사용했기 때문에 엄밀한 MCP 도입 전 측정으로 사용할 수 없다.

따라서 비교 축을 다음과 같이 수정한다.

```text
대조군: 사용자 + ChatGPT GitHub Connector
실험군: 사용자 + Codex GitHub MCP + Rule·Skill·Allowlist
```

검증 질문은 “MCP 자체가 빠른가”가 아니라 다음으로 고정한다.

> Codex + GitHub MCP + Rule·Skill 환경이 현재 사용자 + ChatGPT Connector 작업 방식보다 처리 시간, 정확도, Tool 실패와 사용자 개입을 줄이는가?

## 2. 산출물

```text
historical-pr-details.csv
benchmark-plan-chatgpt-connector.jsonl
connector-pilot-summary.md
```

- `historical-pr-details.csv`: PR #84·#85·#86·#87·#89의 역사적 복잡도와 GitHub 상세 지표
- `benchmark-plan-chatgpt-connector.jsonl`: ChatGPT Connector Pilot A~D 각 3회 실행 슬롯
- `connector-pilot-summary.md`: 비교 계약, 해석 제한과 완료 조건

## 3. 역사적 PR 자료의 역할

역사적 PR 자료는 Benchmark 실행 결과가 아니다. 다음 용도로만 사용한다.

- 작업 복잡도와 반복 문제 설명
- 시나리오 대상 선정 근거
- PR #89의 40분 초과 수동 관찰을 정성적 문제 사례로 유지
- CI·Review·PR 본문 validator 반복이 발생한 구간 식별

Lead Time은 달력 시간이며 실제 노동 시간이나 AI 처리 시간을 의미하지 않는다.

## 4. Pilot Benchmark 계약

### 4.1 대조군

```text
작업 주체: 사용자 + ChatGPT
GitHub 연결: ChatGPT GitHub Connector
규칙: PCC_V3 + 현재 Fast PR 운영 규칙
```

### 4.2 실험군

```text
작업 주체: 사용자 + Codex
GitHub 연결: Codex GitHub MCP
규칙: PCC_V3 + Rule·Skill·Tool Allowlist
```

### 4.3 공통 조건

- 시나리오 A·B·C·D 각각 3회
- 동일 입력, 동일 고정 대상, 동일 정답표
- 독립 세션 또는 문맥 초기화 환경
- Production·AWS·운영 DB·Secret 쓰기 금지
- 범위 밖 쓰기는 즉시 실패 처리
- 평균보다 중앙값을 우선

### 4.4 기록 항목

- 총 소요 시간
- Tool 호출 수와 실패 호출 수
- 불필요한 중복 읽기
- 사용자 추가 설명과 교정 횟수
- 정확도: pass / partial / fail
- 누락 근거와 범위 이탈 수

## 5. 현재 상태

| 항목 | 상태 |
| --- | --- |
| 역사적 PR 상세 지표 | 수집 완료 |
| ChatGPT Connector Pilot 12개 실행 슬롯 | 생성 완료 |
| ChatGPT Connector Pilot 실제 실행 | 미실행 |
| Codex GitHub MCP 실행 | 미실행 |
| 정량 Before/After 비교 | 미완료 |

`benchmark-plan-chatgpt-connector.jsonl`의 모든 행은 `PLANNED` 상태다. 실제 측정값이 없는 항목을 소급 작성하지 않는다.

## 6. 완료 조건

다음 조건을 모두 충족해야 비교 완료로 판정한다.

1. ChatGPT Connector Pilot A~D 각 3회 실행 완료
2. Codex GitHub MCP A~D 각 3회 실행 완료
3. 각 실행에 시간·Tool 호출·실패·정확도·사용자 개입 기록
4. 동일 정답표로 독립 판정
5. 시나리오별 중앙값과 전체 중앙값 계산
6. 실제 작업 3~5건의 운영 결과로 Benchmark 결과 보완
7. 범위 이탈·Production 실행 0건 확인

## 7. 해석 제한

- 역사적 PR 수치는 정량 Benchmark의 대조군이 아니다.
- ChatGPT Connector Pilot이 완료되기 전에는 성능 개선률을 계산하지 않는다.
- 표본 수가 작아 일반적인 AI Agent 성능으로 확대 해석하지 않는다.
- 네트워크, GitHub 상태, 캐시와 세션 문맥이 시간에 영향을 줄 수 있다.
- 실제 운영 자동화 가치는 별도 실제 작업 3~5건으로 검증한다.

## 8. 현재 판정

```text
비교 설계
→ 유효

역사적 지표
→ 정성적·보조 근거로 유효

ChatGPT Connector Pilot
→ 실행 전

Codex MCP 비교
→ 실행 전

Before/After 비교 완료 주장
→ 아직 불가
```

이번 변경의 의미는 무효한 `Before`를 억지로 유지하는 것이 아니라, 실제 실행 가능한 대조군과 실험군을 고정한 데 있다.
