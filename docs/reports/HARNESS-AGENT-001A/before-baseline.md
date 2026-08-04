# HARNESS-AGENT-001A Before 기준선

## 상태

- 작업 ID: `HARNESS-AGENT-001A`
- 작업 등급: 일반
- 실행 구분: 저장소 변경
- 역할: Tech Lead
- 기준 시점: 2026-08-04
- 기준 `main`: `fea45fa24f813db225e7c7831b5889d59e327a91`

## 목적

GitHub MCP 도입 전 Lean Harness의 PR·CI·Review 업무 기준선을 고정한다. MCP 효과를 미리 결론 내리지 않고, 향후 After 측정에 동일한 계산 기준을 적용하기 위한 역사적 원본으로 사용한다.

## 범위

포함:

- PR #84, #85, #86, #87, #89의 GitHub 메타데이터
- PR 유형별 달력 Lead Time과 병합 전 commit 수
- 자동 수집 지표와 수동 관찰 지표의 구분
- 고정 Benchmark 시나리오와 정답 기준
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

Lean Harness 도입 전 PR은 비교 대상에서 제외한다. Harness 경량화 효과와 MCP 효과가 섞이기 때문이다.

## 결과 또는 증거

### 기준 원본

- 기준 `main`: `fea45fa24f813db225e7c7831b5889d59e327a91`
- 수집 파일: `before-pr-metrics.csv`
- 계산식: `merged_at - created_at`
- commit 수: GitHub PR의 병합 전 commit 수
- 수집 대상: PR #84, #85, #86, #87, #89

### 비교 대상

| PR | 유형 | 작업 | 생성→병합 달력 시간 | Commit 수 |
|---:|---|---|---:|---:|
| #84 | 제품·도메인 문서 | PS-004 | 91분 44초 | 5 |
| #85 | API·데이터·ADR 문서 | API-004 | 44분 18초 | 2 |
| #86 | Frontend 구현 | FRONTEND-002 | 204분 19초 | 3 |
| #87 | Backend 구현 | API-005 | 154분 53초 | 16 |
| #89 | QA·통합·PR 후속 수정 | OPS-031 | 1,384분 15초 | 16 |

Lead Time은 실제 노동 시간이 아니라 사용자 대기, 리뷰 제한, 외부 장애와 다음 날 재개를 포함한 달력 시간이다. 서로 다른 유형을 단순 평균하지 않는다.

### PR #89 수동 관찰

- 사용자 직접 확인: ChatGPT와 사용자가 코드 수정·리뷰 정리·CI 재검증·병합을 처리한 구간이 `40분 초과` 소요됐다.
- 정확한 시작·종료 시각은 수집하지 않았다.
- 이 값은 정밀한 작업 시간이 아니라 하한값으로만 사용한다.
- 출처: 2026-08-04 프로젝트 대화에서 사용자가 직접 확인한 관찰값

## 측정 지표

### GitHub에서 재현 가능한 지표

- PR Lead Time
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

## 위험·제한

- 표본이 5개 PR로 작아 통계적 일반화에 사용하지 않는다.
- Lead Time은 실제 노동 시간이 아니다.
- GitHub Review limit과 Codex usage limit은 MCP 효과와 분리해 해석해야 한다.
- 과거 실제 작업 시간, 사용자 교정과 Tool 호출 수는 자동 복원할 수 없다.
- MCP 실제 연결·Allowlist·After Benchmark·시범 운영은 Codex 토큰 복구 후 별도 단계에서 수행한다.

## 다음 단계

1. 고정 시나리오로 Before 3회 실행 기록
2. MCP 운영 계약과 Tool Allowlist 설계
3. Codex 토큰 복구 후 실제 MCP 연결
4. 동일 시나리오 After Benchmark
5. 실제 작업 3~5건 시범 운영
