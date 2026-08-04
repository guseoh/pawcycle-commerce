# HARNESS-AGENT-002 ChatGPT Connector Pilot 결과

> 기록 시각: 2026-08-05T00:50:53+09:00
> 상태: Pilot 측정 완료 — Codex GitHub MCP 비교 전

## 목적

현재 사용자 + ChatGPT GitHub Connector 방식을 향후 Codex + GitHub MCP 실험군과 비교하기 위한 대조군 Pilot으로 측정한다. 이 문서는 최초 실행 원본의 시간·Tool 호출·정확도와 warm-cache 한계를 기록한다.

## 실행 결과

| 시나리오 | 실행 | 전체 중앙값 | 독립 evidence 실행 | 독립 실행 중앙값 | Tool 호출 | 정확도 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| A | 3 | 40.400초 | 3 | 40.400초 | 3 | 3/3 pass |
| B | 3 | 38.390초 | 3 | 38.390초 | 3 | 3/3 pass |
| C | 3 | 37.581초 | 3 | 37.581초 | 3 | 3/3 pass |
| D | 3 | 0.000초 | 1 | 75.241초 | 3 | 3/3 pass |

## 판정

- A·B는 각 회차마다 PR 또는 Issue를 다시 조회했다.
- C는 고정 HEAD의 PS-004·API-004·DATA-003·ARCH-007·루트/Backend `AGENTS.md`를 앞선 단계에서 확인했지만 반복 search가 정확한 파일을 반환하지 않아 같은 세션 원본을 재사용했다.
- D1은 Workflow Run→Job→Log를 새로 조회한 cold evidence path다.
- D2·D3는 D1 원본을 재사용한 warm-cache 실행이라 0초에 가까운 값이 나왔다.
- 전체 12회에서 Tool 실패, 사용자 교정, 범위 이탈, Production·AWS·운영 DB·Secret 접근은 없었다.

## 비교 사용 범위

```text
A·B
→ 3회 중앙값 사용 가능

C
→ 같은 세션 cache가 개입한 최초 Pilot 값
→ 독립 실행 보충 원본을 함께 사용

D
→ D1 cold evidence path 75.241초만 참고 가능
→ D2·D3 warm-cache 값은 시간 비교 제외
```

C·D 독립 evidence 보충 판정은 `connector-pilot-independent-summary.md`와 `benchmark-results-chatgpt-connector-independent.jsonl`을 따른다.

## 실행한 검증과 미실행 사유

- A·B·C·D 각 3회와 JSONL 12행을 확인했다.
- 정확도 12/12 `pass`, Tool 실패와 범위 이탈 0건을 확인했다.
- Codex GitHub MCP 실험군은 실제 MCP 연결 전이므로 미실행했다.
- Backend·Frontend 변경이 없어 애플리케이션 build·test는 실행하지 않았다.
- Production·AWS·운영 DB·Secret 실행은 범위 밖이라 수행하지 않았다.

## 위험·제한

- C 최초 실행에는 같은 세션 원본 재사용이 포함된다.
- D2·D3 warm-cache 시간은 성능 중앙값이나 개선률에 사용할 수 없다.
- 표본 수가 작아 일반적인 AI Agent 성능으로 확대 해석할 수 없다.
- 네트워크·GitHub 상태·세션 문맥이 시간에 영향을 줄 수 있다.
- Codex 실험군과 실제 작업 3~5건 전에는 MCP 효과 검증 완료로 표현하지 않는다.

## 복구 경계

문서·측정 원본만 추가한 변경이므로 PR 또는 관련 문서 revert로 복구한다. Production·AWS·운영 DB·Secret은 변경하지 않았다.
