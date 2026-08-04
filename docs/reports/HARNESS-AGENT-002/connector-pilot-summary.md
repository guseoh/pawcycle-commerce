# HARNESS-AGENT-002 ChatGPT Connector Pilot 비교 설계와 판정

> 상태: 대조군 Pilot 완료 — Codex GitHub MCP 비교 전
> 최초 설계 시각: 2026-08-05T00:13:00+09:00

## 목적

현재 사용자 + ChatGPT Connector 방식을 향후 Codex + GitHub MCP + Rule·Skill·Tool Allowlist 환경과 비교할 대조군으로 고정한다. 엄밀하게 복원할 수 없는 MCP 도입 전 수치를 `Before`로 소급하지 않는다.

## 비교 축

```text
대조군: 사용자 + ChatGPT GitHub Connector
실험군: 사용자 + Codex GitHub MCP + Rule·Skill·Allowlist
```

검증 질문:

> Codex + GitHub MCP + Rule·Skill 환경이 현재 사용자 + ChatGPT Connector 작업 방식보다 처리 시간, 정확도, Tool 실패와 사용자 개입을 줄이는가?

## 역사적 PR 자료의 역할

PR #84·#85·#86·#87·#89 자료는 Benchmark 실행 결과가 아니다.

- 작업 복잡도와 반복 문제 설명
- 시나리오 대상 선정 근거
- CI·Review·PR 본문 validator 반복 구간 식별
- PR #89의 40분 초과 관찰을 정성적 사례로 유지

Lead Time은 달력 시간이며 실제 노동 시간이나 AI 처리 시간을 의미하지 않는다.

## 공통 측정 계약

- 시나리오 A·B·C·D 각각 3회
- 동일 입력, 고정 대상, 고정 정답표
- 독립 원본 재조회
- Production·AWS·운영 DB·Secret 쓰기 금지
- 범위 밖 쓰기는 실패 처리
- 시간, Tool 호출·실패, 사용자 추가 설명·교정, 정확도와 범위 이탈 기록
- 복원하거나 안정적으로 측정할 수 없는 값은 추정하지 않음

## 대조군 실행 결과

| 항목 | 상태 |
| --- | --- |
| 역사적 PR 상세 지표 | 완료 |
| ChatGPT Connector Pilot A·B·C·D | 12회 완료 |
| 독립 evidence path | 12/12 |
| 정확도 | 12/12 pass |
| Tool 실패·범위 이탈·Production 접근 | 0건 |
| A 시간 중앙값 | 40.400초 |
| B 시간 중앙값 | 38.390초 |
| C·D 동일 기준 end-to-end 시간 | 미수집 |
| Codex GitHub MCP 실행 | 미실행 |

C·D는 정확도·Tool 호출·실패와 사용자 개입 비교에는 사용할 수 있지만, 동일 외부 타이머가 없으므로 처리 시간 개선률 계산에는 사용하지 않는다.

상세 원본:

- `benchmark-results-chatgpt-connector.jsonl`: 최초 Pilot 원본과 warm-cache 한계
- `benchmark-results-chatgpt-connector-independent.jsonl`: C·D 독립 evidence 보충
- `connector-pilot-results.md`: 최초 Pilot 해석
- `connector-pilot-independent-summary.md`: 최종 독립 실행 판정

## 실행한 검증과 미실행 사유

- Pilot 계획 12행, 최초 결과 12행, C·D 보충 결과 6행을 확인했다.
- 전체 독립 evidence path 12/12와 정확도 12/12 `pass`를 확인했다.
- Codex GitHub MCP와 실제 작업 3~5건은 후속 단계이므로 미실행했다.
- C·D 동일 기준 end-to-end 시간은 안정적인 외부 타이머가 없어 추정하지 않았다.
- Backend·Frontend·Production·AWS·운영 DB·Secret은 변경하거나 실행하지 않았다.

## 전체 비교 완료 조건

1. Codex GitHub MCP에서 A·B·C·D 각 3회 실행
2. 동일 schema와 정답표 사용
3. 독립 원본 재조회와 Tool Allowlist 적용
4. 시간·Tool 호출·실패·정확도·사용자 개입 기록
5. 동일 기준으로 수집된 값만 중앙값과 개선률 계산
6. 실제 작업 3~5건으로 Benchmark 결과 보완
7. 범위 이탈·Production 실행 0건 확인

## 위험·제한

- 역사적 PR 수치는 정량 대조군이 아니다.
- 표본 수가 작아 일반적인 AI Agent 성능으로 확대 해석하지 않는다.
- 네트워크, GitHub 상태, 캐시와 세션 문맥이 시간에 영향을 줄 수 있다.
- C·D의 시간 개선률은 현재 계산할 수 없다.
- Codex 실험군과 실제 작업 검증 전에는 MCP 효과 검증 완료로 표현하지 않는다.

## 복구 경계

문서·측정 원본만 추가한 변경이므로 PR 또는 관련 문서 revert로 복구한다. Production·AWS·운영 DB·Secret은 변경하지 않았다.

## 현재 판정

```text
비교 설계
→ 유효

ChatGPT Connector 대조군 Pilot
→ 완료

Codex GitHub MCP 실험군
→ 미실행

전체 비교
→ 미완료
```
