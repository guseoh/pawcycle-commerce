# HARNESS-AGENT-002 ChatGPT Connector 독립 실행 보충

> 상태: C·D 독립 evidence path 각 3회 보충 완료
> 비교 대상: 향후 Codex GitHub MCP 동일 시나리오

## 목적

최초 Pilot의 C·D warm-cache 한계를 보완한다. 최초 원본은 탐색 기록으로 보존하고, 이 문서와 `benchmark-results-chatgpt-connector-independent.jsonl`을 C·D의 독립 반복 판정 증거로 사용한다.

## 결과

| 시나리오 | 반복 | 독립 원본 재조회 | Tool 호출 | Tool 실패 | 정확도 | 시간 | 사용자 개입 |
| --- | ---: | ---: | ---: | ---: | ---: | --- | --- |
| C — 권위 문서 탐색 | 3 | 3/3 | 18 | 0 | 3/3 pass | 미수집 | 미측정 |
| D — CI 실패 분석 | 3 | 3/3 | 9 | 0 | 3/3 pass | 미수집 | 미측정 |
| 합계 | 6 | 6/6 | 27 | 0 | 6/6 pass | 미수집 | 미측정 |

A·B 최초 독립 Pilot까지 합치면 A=3, B=3, C=3, D=3의 독립 evidence path를 확보한다.

## C 판정

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

## D 판정

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

## 측정 상태

C의 파일 조회와 D의 전체 3단계 실행에 대해 동일 기준의 end-to-end 벽시계 시간을 안정적으로 얻지 못했다. 시간은 `null`과 미수집 상태로 보존했다.

C·D 실행 중 사용자 추가 설명과 교정은 별도 측정하지 않았다. 해당 필드는 `null`, `user_intervention_measurement=not_measured`로 기록하며 0으로 소급하지 않는다.

따라서 C·D는 정확도·Tool 호출·Tool 실패 비교에는 사용할 수 있지만 처리 시간과 사용자 개입 개선률 계산에는 사용할 수 없다.

## 실행한 검증과 미실행 사유

- C·D 각 3회, 총 6행의 schema 3.0 JSONL을 확인했다.
- 모든 행이 `record_type=result`, `counts_toward_independent_repetition=true`, `cache_reuse=false`임을 확인했다.
- 정확도 6/6 `pass`, Tool 실패와 범위 이탈 0건을 확인했다.
- Codex GitHub MCP는 실제 연결 전이므로 미실행했다.
- Backend·Frontend·Production·AWS·운영 DB·Secret은 변경하거나 실행하지 않았다.

## 위험·제한

- 같은 ChatGPT 대화 안에서 수행해 세션 문맥 영향을 완전히 제거할 수 없다.
- C·D 시간과 사용자 개입은 정량 비교할 수 없다.
- 표본 수가 작아 일반적인 AI Agent 성능으로 확대 해석하지 않는다.
- 향후 양쪽 환경에서 같은 외부 타이머와 측정 래퍼를 사용해야 한다.

## 복구 경계

문서·측정 원본만 변경한 작업이므로 PR 또는 관련 문서 revert로 복구한다. Production·AWS·운영 DB·Secret은 변경하지 않았다.

## 현재 판정

```text
C·D 독립 evidence path
→ 각 3회 완료

정확도
→ 6/6 pass

Tool 실패
→ 0

시간·사용자 개입 개선률
→ 계산 불가

Codex GitHub MCP 대비 전체 비교
→ 미실행
```
