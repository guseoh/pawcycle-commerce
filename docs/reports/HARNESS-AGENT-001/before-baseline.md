# HARNESS-AGENT-001A Before 기준선

## 상태

- 작업 ID: `HARNESS-AGENT-001A`
- 작업 등급: 일반
- 실행 구분: 저장소 변경
- 역할: Tech Lead
- 기준 시점: 2026-08-04
- 기준 `main`: `fea45fa24f813db225e7c7831b5889d59e327a91`

## 목적

GitHub MCP 도입 전 Lean Harness의 PR·CI·Review 업무 기준선을 고정한다. 이 문서는 MCP 효과를 미리 결론 내리지 않으며, After 측정과 같은 계산 기준으로 비교하기 위한 역사적 원본이다.

## 범위

포함:

- PR #84, #85, #86, #87, #89의 권위 있는 GitHub 메타데이터
- PR 유형별 Lead Time과 commit 수
- Review·CI·사용자 교정 지표의 정의
- MCP 적용 전 Benchmark 시나리오와 정답 기준
- PR #89 Fast PR 개선 가설

제외:

- GitHub MCP 설치·인증·Tool 실행
- `AGENTS.md`, Skill, PR template, validator 변경
- Backend·Frontend·API·DB·Production 변경
- Grafana·Prometheus·상시 수집기 구축

## 기준선 경계

```text
Before
HARNESS-LEAN-002 적용 이후
→ GitHub MCP 적용 직전

After
동일 Lean Harness
+ 선택적 GitHub MCP 운영
+ 승인된 MCP Rule·Skill
```

Lean Harness 도입 전 PR과 비교하지 않는다. Harness 경량화 효과와 MCP 효과가 섞이기 때문이다.

## 비교 대상

| PR | 유형 | 작업 | 생성→병합 Lead Time | Commit 수 |
|---:|---|---|---:|---:|
| #84 | 제품·도메인 문서 | PS-004 | 91분 44초 | 5 |
| #85 | API·데이터·ADR 문서 | API-004 | 44분 18초 | 2 |
| #86 | Frontend 구현 | FRONTEND-002 | 204분 19초 | 3 |
| #87 | Backend 구현 | API-005 | 154분 53초 | 16 |
| #89 | QA·통합·PR 후속 수정 | OPS-031 | 1,384분 15초 | 16 |

Lead Time은 실제 노동 시간이 아니라 사용자 대기, 리뷰 제한, 외부 장애와 다음 날 재개를 포함한 달력 시간이다. 서로 다른 유형을 단순 평균하지 않는다.

## 측정 지표

### GitHub에서 재현 가능한 지표

- PR Lead Time: `merged_at - created_at`
- Commit 수
- Workflow Run 수
- 실패한 Workflow Run 수
- First-pass CI 성공 여부
- Review thread 수와 해결 상태
- PR 본문 validator 실패 횟수

### 수동 기록이 필요한 지표

- 실제 작업 시간
- GitHub 상태 파악 시간
- 사용자 추가 설명 횟수
- 사용자 교정 횟수
- 승인 범위 이탈 횟수
- Tool 호출 수와 실패 수
- Codex Token 또는 Credit 사용량

수동 기록이 없는 과거 값은 추측하지 않고 `미수집`으로 남긴다.

## PR #89 개선 가설

PR #89는 Fast PR의 Before 사례로 별도 취급한다.

관찰된 지연 요인:

- 리뷰 의견을 여러 번 나누어 수정
- 전체 파일 복사·포맷 변경으로 diff 확대
- PR 본문 validator 조건을 CI 전에 확인하지 못함
- 같은 HEAD에서 과거 이벤트 payload를 사용한 CI 재실행
- 여러 파일 수정이 여러 commit으로 분산
- 성공 Job까지 반복 확인

개선 가설:

```text
현재 HEAD·리뷰·실패 CI 일괄 조회
→ 유효 항목 일괄 판정
→ 최소 diff 단일 commit
→ PR 본문 validator 사전 확인
→ 최종 HEAD에서 필수 CI 한 번
→ 차단 리뷰만 정리
```

Fast PR은 Codex 토큰이 없고 사용자와 ChatGPT가 기존 PR의 제한된 오류를 직접 처리할 때만 적용 후보로 둔다. 이 단계에서는 저장소 공통 규칙으로 활성화하지 않는다.

## 다음 단계

1. GitHub API 수집 스크립트와 CSV·JSON schema 작성
2. PR별 Workflow·Review 상세 데이터 보강
3. Benchmark 시나리오 4개의 정답표 확정
4. Before 정적 차트 생성
5. 사용자 검토 후 HARNESS-AGENT-001B MCP 운영 계약으로 전환

## 미실행과 남은 위험

- 과거 PR의 실제 노동 시간, 사용자 교정과 Tool 호출은 자동 복원할 수 없어 미수집이다.
- GitHub Review limit과 Codex usage limit은 PR 시간에 영향을 주지만 MCP 효과와 분리해 해석해야 한다.
- 작은 표본이므로 통계적 일반화에 사용하지 않는다.
- MCP 실제 연결·Allowlist·After Benchmark·시범 운영은 Codex 토큰 복구 후 별도 단계에서 수행한다.
