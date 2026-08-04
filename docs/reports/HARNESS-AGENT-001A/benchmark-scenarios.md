# HARNESS-AGENT-001A Benchmark 시나리오

## 목적

GitHub MCP 도입 전과 도입 후에 같은 입력·대상·정답 기준을 사용해 PR·Issue·권위 문서·CI 분석 작업의 정확도와 처리 비용을 비교한다.

## 공통 실행 규칙

- Before와 After에 같은 요청 문구와 고정 대상을 사용한다.
- 시나리오별 3회 실행하고 중앙값을 우선한다.
- 정답은 고정한 GitHub PR·Issue·commit·workflow와 해당 시점의 `main` 원본으로 판정한다.
- 시간, Tool 호출 수, 실패 호출 수, 사용자 추가 설명과 교정을 기록한다.
- 범위 밖 쓰기·Secret·Production 접근은 즉시 실패로 판정한다.
- 동적 최신 상태 조회는 실전 검증으로 별도 수행하며 정량 Benchmark와 섞지 않는다.

## 결과 또는 증거

- 현재 결과: 비교용 시나리오 4개와 고정 대상·정답 기준 정의 완료
- 미실행: 시나리오별 Before 3회 반복 측정
- 실행 결과 위치: 후속 `benchmark-results-before.jsonl`
- After 실행: Codex 토큰 복구와 실제 MCP 연결 이후 동일 입력으로 수행

## 시나리오 A — PR 상태 파악

### 고정 대상

- PR: `#89`
- 기준 HEAD: `a243e76c6b3824e764f75c64154c8ffe794bc921`
- 기준 상태: 2026-08-03 병합 직전 최종 검증 상태

### 요청

```text
PR #89의 최종 HEAD, Draft 여부, Head·Base Branch, 남은 차단 조건과 Production 실행 여부를 설명한다.
```

### 정답 기준

- PR #89와 기준 HEAD를 정확히 식별
- Head `ops/tl/OPS-031`, Base `main` 구분
- 최종 검증 시점의 CI와 차단 리뷰 상태 제시
- Production·AWS·운영 DB·Secret 미실행 구분
- 과거 실패와 최종 성공 상태를 혼동하지 않음

## 시나리오 B — Issue 추적

### 고정 대상

- Issue: `#88`
- 연결 기준: PR #87 본문과 후속 운영 활성화 조건

### 요청

```text
Issue #88에서 운영 활성화 전에 남은 작업을 찾고 관련 PR·문서와 실제 운영 실행 금지 범위를 설명한다.
```

### 정답 기준

- Issue #88의 상태와 목적 식별
- PR #87의 후속 측정·정책·관측성 항목 연결
- 저장소 준비와 실제 운영 실행 분리
- Production·AWS·운영 DB·Secret 금지 범위 명시
- 존재하지 않는 완료 증거를 추측하지 않음

## 시나리오 C — 권위 문서 탐색

### 고정 대상

- 구현 작업: `API-005`
- 기준 HEAD: `c9563a27076b2e3d34a09fbe07e63dd9978ba613`
- 계약 원본: `API-004`, `DATA-003`, `ARCH-007`, 루트·Backend `AGENTS.md`

### 요청

```text
API-005 구현에 적용된 제품·API·데이터·ADR·역할 규칙을 찾고 충돌 시 우선순위를 설명한다.
```

### 정답 기준

- 현재 사용자 지시를 최우선으로 적용
- 승인된 API-004·DATA-003·ARCH-007과 최신 코드·검증 우선
- `AGENTS.md`의 절차 권위와 제품 계약 권위 구분
- Proposed·Approved·Verified 상태를 혼동하지 않음
- 과거 채팅을 현재 저장소 원본으로 대체하지 않음

## 시나리오 D — CI 실패 분석

### 고정 대상

- PR: `#89`
- Workflow: `Repository Validation #823`
- 기준 실패: PR 본문 validator 고정 항목 누락

### 요청

```text
PR #89의 Repository Validation #823 실패 Job과 최초 오류를 찾고 코드 수정 또는 PR 본문 수정·새 실행 중 무엇이 필요한지 판단한다.
```

### 정답 기준

- 실패 Workflow·Job·Step을 정확히 식별
- 코드가 아니라 PR 본문 validator 형식 문제로 분류
- 성공 Job을 불필요하게 재분석하지 않음
- 같은 payload를 쓰는 무의미한 재실행을 권하지 않음
- PR 본문 수정 후 새 이벤트로 검증해야 함을 제시

## 기록 schema

```json
{
  "phase": "before|after",
  "scenario": "A|B|C|D",
  "run": 1,
  "started_at": "ISO-8601",
  "duration_seconds": 0,
  "tool_calls": 0,
  "failed_tool_calls": 0,
  "user_explanations": 0,
  "user_corrections": 0,
  "accuracy": "pass|partial|fail",
  "scope_violation": false,
  "evidence_missing": false,
  "notes": ""
}
```

## 완료 조건

- 시나리오별 Before 3회 기록
- 정답 기준과 판정 이유 보존
- After에서도 같은 입력·대상·계산법 사용
- 평균뿐 아니라 중앙값과 표본 수 표시

## 위험·제한

- 고정 시나리오는 재현성을 높이지만 실제 최신 상태 조회 난이도를 완전히 대표하지 않는다.
- GitHub 데이터나 삭제된 로그에 접근할 수 없으면 해당 실행은 `evidence_missing=true`로 기록한다.
- 도구 캐시와 네트워크 상태가 시간에 영향을 줄 수 있다.
- 표본 수가 작으므로 결과를 일반적인 Agent 성능으로 과장하지 않는다.
- 실제 MCP 연결, Allowlist 적용, After Benchmark와 실전 3~5건은 Codex 토큰 복구 후 수행한다.
