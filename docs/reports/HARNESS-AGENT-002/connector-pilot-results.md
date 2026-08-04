# HARNESS-AGENT-002 ChatGPT Connector Pilot 결과

> 기록 시각: 2026-08-05T00:50:53+09:00
> 상태: Pilot 측정 완료 — Codex GitHub MCP 비교 전

## 1. 실행 결과

| 시나리오 | 실행 | 전체 중앙값 | 독립 evidence 실행 | 독립 실행 중앙값 | Tool 호출 | 정확도 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| A | 3 | 40.400초 | 3 | 40.400초 | 3 | 3/3 pass |
| B | 3 | 38.390초 | 3 | 38.390초 | 3 | 3/3 pass |
| C | 3 | 37.581초 | 3 | 37.581초 | 3 | 3/3 pass |
| D | 3 | 0.000초 | 1 | 75.241초 | 3 | 3/3 pass |

## 2. 판정

- A·B는 각 회차마다 PR 또는 Issue를 다시 조회했다.
- C는 고정 HEAD의 PS-004·API-004·DATA-003·ARCH-007·루트/Backend AGENTS.md를 앞선 단계에서 직접 확인했지만, 반복 search 자체는 정확한 파일을 반환하지 못해 같은 세션의 검증 원본을 재사용했다.
- D1은 Workflow Run→Job→Log를 새로 조회한 cold evidence path다.
- D2·D3는 D1의 원본을 재사용한 warm-cache 실행이라 0초에 가까운 값이 나왔다. 이 값은 독립 실행 성능이나 MCP 개선률 계산에 사용하지 않는다.
- 전체 12회에서 Tool 실패, 사용자 교정, 범위 이탈, Production·AWS·운영 DB·Secret 접근은 없었다.

## 3. 비교에 사용할 수 있는 값

```text
A, B
→ 3회 중앙값 사용 가능

C
→ 같은 세션 cache가 개입한 Pilot 값
→ 독립 세션 재측정 권장

D
→ D1 cold evidence path 75.241초만 참고 가능
→ D2·D3 warm-cache 값은 정량 비교 제외
```

## 4. 다음 완료 조건

1. Codex GitHub MCP에서 동일 A·B·C·D를 각 3회 실행한다.
2. 가능하면 각 회차를 독립 세션 또는 cache 초기화 환경에서 수행한다.
3. ChatGPT Connector 쪽 C와 D도 독립 실행으로 보충한다.
4. 양쪽 모두 시간·Tool 호출·실패·정확도·사용자 개입을 같은 schema로 기록한다.
5. 독립 실행 표본만으로 중앙값과 개선률을 계산한다.

## 5. 현재 주장 가능 범위

현재 결과는 ChatGPT Connector 작업 절차와 측정 schema를 검증한 Pilot이다. Codex GitHub MCP 대비 성능 개선 또는 Before/After 완료를 주장할 수 없다.
