# HARNESS-AGENT-002 ChatGPT Connector 독립 실행 보충

> 상태: C·D 독립 evidence path 3회 보충 완료
> 비교 대상: 향후 Codex GitHub MCP 동일 시나리오

## 1. 목적

기존 `benchmark-results-chatgpt-connector.jsonl`의 C·D warm-cache 한계를 보완한다. 기존 원본은 역사적 실행 기록으로 유지하고, 이 문서와 `benchmark-results-chatgpt-connector-independent.jsonl`을 후속 판정 증거로 사용한다.

## 2. 결과

| 시나리오 | 반복 | 독립 원본 재조회 | Tool 호출 | Tool 실패 | 정확도 | 시간 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| C — 권위 문서 탐색 | 3 | 3/3 | 18 | 0 | 3/3 pass | 미수집 |
| D — CI 실패 분석 | 3 | 3/3 | 9 | 0 | 3/3 pass | 미수집 |
| 합계 | 6 | 6/6 | 27 | 0 | 6/6 pass | 미수집 |

A·B 기존 Pilot까지 합치면 전체 12회 모두 독립 원본 재조회와 정확도 `pass`를 확보한다.

## 3. C 판정

각 회차마다 API-005 고정 HEAD `c9563a27076b2e3d34a09fbe07e63dd9978ba613`에서 다음 원본을 다시 조회했다.

- `docs/product/PS-004-second-mvp-requirements.md`
- `docs/api/API-004-second-mvp-api-contract.md`
- `docs/data/DATA-003-second-mvp-subscription-data-design.md`
- `docs/adr/ARCH-007-second-mvp-subscription-consistency.md`
- `AGENTS.md`
- `backend/AGENTS.md`

세 회차 모두 다음을 동일하게 판정했다.

- PS-004는 `Approved Input`
- API-004·DATA-003·ARCH-007은 고정 HEAD에서 `Proposed`
- 현재 사용자 지시와 승인 입력이 우선
- `AGENTS.md`는 절차와 허용 범위를 통제하지만 승인된 제품 계약을 덮어쓰지 않음
- 과거 채팅을 저장소 원본보다 우선하지 않음

## 4. D 판정

각 회차마다 다음 evidence path를 다시 수행했다.

```text
최종 HEAD Workflow 목록
→ Repository Validation #823
→ Run #823 Job 목록
→ Commit and PR conventions
→ Validate task artifacts
→ 실패 Job 로그
```

세 회차 모두 최초 오류를 다음으로 판정했다.

```text
PR 본문 실행한 검증 또는 미실행 이유가 비어 있음
PR 본문 남은 위험 또는 복구 경계가 비어 있음
```

따라서 코드 변경 또는 동일 payload의 단순 재실행이 아니라, PR 본문 수정 후 새 pull request 이벤트 검증이 필요하다. 이후 Repository Validation #825가 성공했다.

## 5. 시간 측정 제한

C의 파일 조회와 D의 전체 3단계 실행에 대해 동일 기준의 end-to-end 벽시계 시간을 안정적으로 얻지 못했다. 값을 추정하지 않고 `null`로 보존했다.

따라서 C·D는 정확도·Tool 호출·Tool 실패·사용자 개입 비교에는 사용할 수 있지만 처리 시간 개선률 계산에는 사용할 수 없다. 향후 Codex MCP 비교에서 C·D 시간까지 필요하면 양쪽 모두 외부 타이머 또는 실행 래퍼를 사용해야 한다.

## 6. 현재 판정

```text
ChatGPT Connector Pilot 12회
→ 완료

독립 evidence path
→ 12/12

정확도
→ 12/12 pass

Tool 실패
→ 0

C·D 시간 개선률
→ 계산 불가

Codex GitHub MCP 대비 전체 비교
→ 미실행
```

## 7. 운영 경계

이 보충 작업에서는 Backend·Frontend·API·DB·Production·AWS·운영 DB·Secret을 변경하거나 실행하지 않았다.
