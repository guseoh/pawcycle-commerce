# HARNESS-AGENT-002 Before 상세 지표·Benchmark 준비 계획

## 상태

- 작업 ID: `HARNESS-AGENT-002`
- 작업 등급: 일반
- 실행 구분: 저장소 변경 + 읽기 전용 GitHub 측정
- 역할: Tech Lead
- 시작 기준 `main`: `f6a50b5eb032355a6596bbb7c85da0fe6f17a273`
- 선행 기준선: `docs/reports/HARNESS-AGENT-001A/**`

## 목적

HARNESS-AGENT-001A에서 고정한 GitHub MCP 도입 전 기준선을 바꾸지 않고, PR·CI·Review 상세 지표와 고정 Benchmark의 Before 반복 실행 결과를 재현 가능한 증거로 남긴다.

## 범위

포함:

- PR #84·#85·#86·#87·#89의 Workflow Run 수, 실패 Run 수와 First-pass CI 여부
- PR별 Review thread 수, 해결 상태와 PR 본문 validator 실패 횟수
- 고정 시나리오 A·B·C·D의 Before 3회 반복 실행
- 실행 시간, Tool 호출 수, 실패 호출 수, 사용자 추가 설명·교정 횟수 기록
- 시나리오별 정확도 판정과 중앙값·표본 수 집계
- 수집 불가능한 증거를 `미수집` 또는 `evidence_missing=true`로 보존

제외:

- GitHub MCP 설치·인증·Tool Allowlist 적용
- After Benchmark
- 실제 작업 3~5건 시범 운영
- `AGENTS.md`, Skill, PR template, validator 변경
- Backend·Frontend·API·DB·Production·AWS·Secret 변경
- Grafana·Prometheus·상시 수집기 구축

## 산출물 계획

```text
docs/reports/HARNESS-AGENT-002/
├── preparation-plan.md
├── before-github-details.csv
├── benchmark-results-before.jsonl
└── before-summary.md
```

- `before-github-details.csv`: PR별 GitHub 상세 지표 원본
- `benchmark-results-before.jsonl`: 시나리오별 3회 실행 원본
- `before-summary.md`: 계산법, 중앙값, 판정과 제한

## 측정 계약

### GitHub 상세 지표

- 표본: PR #84·#85·#86·#87·#89
- 기준: 각 PR의 병합 전 최종 HEAD와 연결된 Pull Request Workflow
- Workflow Run 수: 대상 PR의 생성부터 병합까지 발생한 관련 Run 수
- 실패 Run 수: 결론이 `failure`인 Run 수
- First-pass CI: 첫 필수 Repository Validation과 PR Metadata Validation이 모두 성공했는지 여부
- Review thread: GitHub에서 확인 가능한 inline review thread만 집계
- validator 실패: Repository 또는 Metadata Validation에서 문서·PR 본문 계약 때문에 실패한 Run만 별도 집계

CI·Review 원본을 Connector에서 확인할 수 없거나 로그가 보존되지 않았으면 추측하지 않는다.

### Benchmark 실행

- 입력·대상·정답은 `HARNESS-AGENT-001A/benchmark-scenarios.md`를 그대로 사용한다.
- 시나리오 A·B·C·D를 각각 3회 실행한다.
- 실행 1회는 요청 확인 직전부터 최종 답안·판정 기록 완료까지로 본다.
- 정확도는 `pass`, `partial`, `fail` 중 하나로 기록한다.
- 범위 밖 쓰기, Secret 또는 Production 접근은 `scope_violation=true`로 실패 처리한다.
- 시간 비교는 평균보다 중앙값을 우선한다.

## 실행 순서

1. PR #84·#85·#86·#87·#89의 고정 HEAD와 Workflow·Review 원본을 수집한다.
2. `before-github-details.csv`를 작성하고 누락 증거를 표시한다.
3. Benchmark A·B·C·D를 각 3회 실행해 JSONL로 기록한다.
4. 정답 기준으로 각 실행의 정확도를 판정한다.
5. 시나리오별 중앙값, Tool 실패율과 사용자 개입 횟수를 집계한다.
6. `before-summary.md`에 결과·한계·After 비교 경계를 기록한다.
7. 최종 diff와 보고서 validator 조건을 확인한 뒤 PR을 생성한다.

## 결과 또는 증거

현재 확보된 증거:

- 기준 `main`: `f6a50b5eb032355a6596bbb7c85da0fe6f17a273`
- HARNESS-AGENT-001A 기준선과 고정 Benchmark 시나리오가 `main`에 병합됨
- 작업 브랜치: `ops/tl/HARNESS-AGENT-002`

현재 미실행:

- PR 상세 Workflow·Review 지표 수집
- Before Benchmark 12회 실행
- 중앙값·정확도 집계

## 완료 조건

- 5개 PR의 상세 지표가 원본 근거 또는 명시적 미수집 상태와 함께 기록됨
- A·B·C·D 각각 3회, 총 12개 Benchmark 결과가 기록됨
- 각 실행에 시간·Tool 호출·실패·정확도·사용자 개입·범위 이탈 여부가 있음
- 시나리오별 중앙값과 표본 수가 계산됨
- 실제 MCP 연결 전 단계라는 제한이 명확히 유지됨
- Repository Validation과 PR Metadata Validation 통과

## 위험·제한

- 과거 Workflow·Review 원본 일부가 Connector 또는 보존 기간 때문에 조회되지 않을 수 있다.
- Tool 캐시와 네트워크 상태가 실행 시간에 영향을 줄 수 있다.
- 같은 ChatGPT 세션의 문맥 캐시가 반복 실행 난이도를 낮출 수 있으므로 결과에 이를 명시해야 한다.
- 표본 수가 작으므로 일반적인 AI Agent 성능으로 과장하지 않는다.
- 이 작업은 GitHub MCP 효과를 증명하지 않는다. After 측정은 Codex 토큰 복구와 실제 MCP 연결 이후에만 수행한다.
