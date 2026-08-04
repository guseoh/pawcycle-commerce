# HARNESS-AGENT-002 ChatGPT Connector Pilot 결과

> 기록 시각: 2026-08-05T00:50:53+09:00
> 상태: 최초 Pilot 원본과 독립 보충 결과 분리 완료

## 목적

현재 사용자 + ChatGPT GitHub Connector 방식을 향후 Codex + GitHub MCP 실험군과 비교하기 위한 대조군 Pilot으로 측정한다. 최초 실행의 warm-cache 기록은 삭제하지 않고 탐색 기록으로 분리하며, 독립 반복 집계에는 독립 원본 재조회만 포함한다.

## 최초 Pilot 원본

| 시나리오 | 독립 실행 | 독립 시간 | 탐색 실행 | 탐색 시간 | 원본 Tool 호출 | 정확도 |
| --- | ---: | --- | ---: | --- | ---: | ---: |
| A | 3 | 중앙값 40.400초 | 0 | 없음 | 3 | 3/3 pass |
| B | 3 | 중앙값 38.390초 | 0 | 없음 | 3 | 3/3 pass |
| C | 0 | 미수집 | 3 | 40.245·37.581·29.110초 | 3 | 3/3 pass |
| D | 1 | 75.241초 | 2 | 0.000003·0.000001초 | 3 | 3/3 pass |

- A·B는 각 회차마다 PR 또는 Issue를 다시 조회했다.
- C 최초 3회는 같은 세션에서 미리 확인한 권위 원본을 재사용했으므로 `exploratory_result`이며 독립 반복 수에 포함하지 않는다.
- D1은 Workflow Run→Job→Log를 새로 조회한 독립 cold path다.
- D2·D3는 D1 원본을 재사용했으므로 `exploratory_result`이며 독립 반복 수와 시간 중앙값에서 제외한다.

## 독립 실행 보충

`benchmark-results-chatgpt-connector-independent.jsonl`에서 다음을 보충했다.

| 시나리오 | 독립 실행 | Tool 호출 | Tool 실패 | 정확도 | 시간 |
| --- | ---: | ---: | ---: | ---: | --- |
| C | 3 | 18 | 0 | 3/3 pass | 미수집 |
| D | 3 | 9 | 0 | 3/3 pass | 미수집 |

따라서 A·B 최초 독립 실행과 C·D 보충 실행을 합친 최종 독립 evidence path는 A=3, B=3, C=3, D=3이다.

## 사용자 개입 측정

- A·B: `user_additional_explanations=0`, `user_corrections=0`으로 측정했다.
- C·D 최초 탐색과 독립 보충: 사용자 개입을 별도 측정하지 않아 `null`, `not_measured`로 기록했다.
- 따라서 사용자 개입 비교는 현재 A·B에만 사용할 수 있다. C·D에 0을 소급하지 않는다.

## 실행한 검증과 미실행 사유

- 계획·최초 결과·독립 보충의 `schema_version=3.0`과 공통 필드를 확인했다.
- 독립 반복 집계에는 `counts_toward_independent_repetition=true`인 레코드만 포함했다.
- 최종 독립 evidence path 12/12와 정확도 12/12 `pass`를 확인했다.
- Tool 실패와 범위 이탈, Production·AWS·운영 DB·Secret 접근은 0건이다.
- Codex GitHub MCP 실험군은 실제 MCP 연결 전이므로 미실행했다.
- C·D 동일 기준 end-to-end 시간과 사용자 개입은 측정하지 않아 추정하지 않았다.

## 위험·제한

- 최초 C와 D2·D3은 탐색 기록이며 성능 비교에 사용할 수 없다.
- C·D는 정확도와 Tool 호출 비교에는 사용할 수 있지만 시간·사용자 개입 개선률 계산에는 사용할 수 없다.
- 표본 수가 작아 일반적인 AI Agent 성능으로 확대 해석할 수 없다.
- 네트워크·GitHub 상태·세션 문맥이 시간에 영향을 줄 수 있다.
- Codex 실험군과 실제 작업 3~5건 전에는 MCP 효과 검증 완료로 표현하지 않는다.

## 복구 경계

문서·측정 원본만 변경한 작업이므로 PR 또는 관련 문서 revert로 복구한다. Production·AWS·운영 DB·Secret은 변경하지 않았다.
