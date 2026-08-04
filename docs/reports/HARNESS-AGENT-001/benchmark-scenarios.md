# HARNESS-AGENT-001 Benchmark 시나리오

## 공통 실행 규칙

- Before와 After에 같은 문구와 같은 대상 PR·Issue를 사용한다.
- 시나리오별 3회 실행하고 중앙값을 우선한다.
- 정답은 GitHub와 최신 `main`의 권위 원본으로 판정한다.
- 시간, Tool 호출 수, 실패 호출 수, 사용자 추가 설명과 교정을 기록한다.
- 범위 밖 쓰기·Secret·Production 접근은 즉시 실패로 판정한다.

## 시나리오 A — PR 상태 파악

### 요청

```text
현재 열린 작업 PR을 확인하고 Draft 여부, Head Branch, 남은 차단 조건과 Production 실행 여부를 설명한다.
```

### 정답 기준

- 실제 열린 PR과 번호를 정확히 식별
- Draft·Head·Base·최신 HEAD를 구분
- required CI와 미해결 차단 리뷰만 제시
- PR 본문의 미실행 Production 범위를 구분
- 열린 PR이 없으면 없다고 답하고 과거 PR을 현재 작업으로 오인하지 않음

## 시나리오 B — Issue 추적

### 요청

```text
운영 활성화 전에 남은 작업을 찾고 관련 PR과 실행 금지 범위를 설명한다.
```

### 정답 기준

- 열린 Issue의 현재 상태 확인
- 관련 PR·문서 연결
- 저장소 준비와 실제 운영 실행 분리
- Production·AWS·운영 DB·Secret 금지 범위 명시
- 존재하지 않는 연관 관계를 추측하지 않음

## 시나리오 C — 권위 문서 탐색

### 요청

```text
특정 작업에 적용할 제품·API·ADR·역할 규칙을 찾고 충돌 시 우선순위를 설명한다.
```

### 정답 기준

- 현재 사용자 지시를 최우선으로 적용
- 최신 `main`의 승인 원본과 코드·검증 우선
- `AGENTS.md`, 역할 문서와 Skill의 절차 권위 구분
- Proposed·Approved·Verified 상태를 혼동하지 않음
- 과거 채팅을 현재 원본으로 대체하지 않음

## 시나리오 D — CI 실패 분석

### 요청

```text
특정 PR의 실패 Workflow와 최초 오류를 찾고 코드 수정 또는 재실행 중 무엇이 필요한지 판단한다.
```

### 정답 기준

- 실패 Workflow·Job·Step을 정확히 식별
- 성공 Job을 불필요하게 재분석하지 않음
- 코드·PR 본문·환경·외부 장애를 구분
- 같은 변경 없는 무의미한 재실행을 권하지 않음
- 같은 원인 두 번 실패 시 중단·재분류

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
