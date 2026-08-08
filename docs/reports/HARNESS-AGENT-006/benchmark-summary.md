# HARNESS-AGENT-006 Codex GitHub MCP 읽기 검증과 Benchmark

## 목적과 실행 경계

- 작업 등급: 고위험
- 실행 구분: 저장소 변경
- 역할: Platform/SRE
- 기준 `main`: `887db103e0f3531706cdd2d662f0c0ba00a7074a`
- 관측 시점: 2026-08-08T13:26:55+09:00
- 대상: `guseoh/pawcycle-commerce`
- 실제 Production·Cloud·운영 DB·Secret·비용 리소스 실행: 없음

Codex의 설치된 GitHub App connector로 대상 저장소의 읽기 연결과 실제 Tool을 확인하고, 고정 Benchmark A·B·C·D를 각각 3회 독립 실행했다. 로컬 credential 파일은 만들지 않았고 credential 원문은 조회·기록하지 않았다.

작업 시작 시 legacy `ops/sre` ref가 task branch 경로와 충돌했다. 더 최신 remote tip `dd0630e192fad41dcedaba4230d1a7a8d9c66f4c`가 local tip을 포함하는 것을 확인한 뒤 `archive/ops-sre-legacy-20260808`에 같은 tip을 보존했다. 원격 archive가 `origin/main`에 없는 legacy commit 3개를 모두 포함함을 검증한 뒤에만 기존 local·remote `ops/sre` ref를 삭제했다.

## 연결과 권한 판정

- 실행 환경에 `mcp__codex_apps__github_*` Tool 89개가 노출됐다.
- `mcp__codex_apps__github_get_repo`로 저장소와 기본 branch `main`을 확인했다.
- PR #89, Issue #88, 열린 PR 목록과 task branch 검색을 대상 저장소 인자로 제한해 읽었다.
- connector 응답상 대상 저장소에는 읽기뿐 아니라 쓰기 권한도 존재하며 쓰기 Tool도 노출된다. 따라서 credential 수준의 강제 read-only가 아니라 아래 Tool·저장소 인자 allowlist로 읽기 단계를 통제했다.
- Benchmark와 연결 검증 중 GitHub 쓰기·병합·Workflow 재실행·권한 변경 Tool 호출은 0건이다.
- commit·push·Draft PR은 Benchmark 이후 사용자가 승인한 조건부 저장소 발행 단계로 분리한다.

## 실제 Tool과 capability Allowlist 매핑

아래 Tool은 실행 시점 catalog와 입력 schema에서 대상 저장소 인자를 확인했다. 검색 Tool도 `guseoh/pawcycle-commerce` 또는 `owner=guseoh`, `repo_name=pawcycle-commerce`를 반드시 지정한다.

### repository metadata read

- `mcp__codex_apps__github_get_repo`

### branch·commit read

- `mcp__codex_apps__github_search_branches`
- `mcp__codex_apps__github_search_commits`
- `mcp__codex_apps__github_fetch_commit`

### file·tree·compare read

- `mcp__codex_apps__github_fetch_file`
- `mcp__codex_apps__github_fetch_blob`
- `mcp__codex_apps__github_compare_commits`

별도 tree 전용 Tool은 확인되지 않았다. file·blob·commit compare로 필요한 권위 원본과 변경 범위를 읽으며, 저장소 범위를 강제할 수 없는 범용 fetch는 allowlist에 넣지 않았다.

### PR metadata·diff·review read

- `mcp__codex_apps__github_get_pr_info`
- `mcp__codex_apps__github_fetch_pr`
- `mcp__codex_apps__github_get_pr_diff`
- `mcp__codex_apps__github_fetch_pr_patch`
- `mcp__codex_apps__github_fetch_pr_file_patch`
- `mcp__codex_apps__github_list_pr_changed_filenames`
- `mcp__codex_apps__github_fetch_pr_comments`
- `mcp__codex_apps__github_list_pull_request_reviews`
- `mcp__codex_apps__github_list_pull_request_review_threads`
- `mcp__codex_apps__github_search_prs`

### Issue read

- `mcp__codex_apps__github_fetch_issue`
- `mcp__codex_apps__github_fetch_issue_comments`
- `mcp__codex_apps__github_search_issues`

### commit status·Workflow Run·Job·Step·Log read

- `mcp__codex_apps__github_get_commit_combined_status`
- `mcp__codex_apps__github_fetch_commit_workflow_runs`
- `mcp__codex_apps__github_fetch_workflow_run_jobs`
- `mcp__codex_apps__github_fetch_workflow_job_steps`
- `mcp__codex_apps__github_fetch_workflow_job_logs`

나머지 노출 Tool은 이번 읽기 allowlist에 포함하지 않았다. 특히 file·branch·PR·Issue·comment·review 쓰기, review thread resolve, Workflow rerun, merge와 permission 변경 Tool은 호출하지 않았다.

## Benchmark 결과

각 회차는 새로운 runner state로 시작하고 원본 MCP Tool을 다시 호출했다. 12개 레코드는 모두 schema 3.0 `result`, `cache_reuse=false`, `independent_evidence_read=true`, `counts_toward_independent_repetition=true`다.

| 시나리오 | 독립 실행 | 시간 중앙값 | Tool 호출 중앙값 | 정확도 | Tool 실패 |
| --- | ---: | ---: | ---: | ---: | ---: |
| A — PR 상태 파악 | 3 | 7.280초 | 3 | 3/3 pass | 0 |
| B — Issue 추적 | 3 | 5.489초 | 2 | 3/3 pass | 0 |
| C — 권위 문서 탐색 | 3 | 8.482초 | 6 | 3/3 pass | 0 |
| D — CI 실패 분석 | 3 | 6.747초 | 3 | 3/3 pass | 0 |

전체 GitHub MCP 호출은 42건이며 실패·범위 이탈·Production 접근은 0건이다. 사용자 추가 설명과 교정은 모든 실험군 회차에서 측정했고 각각 0건이었다.

- A: PR #89 고정 HEAD, Draft=false, head `ops/tl/OPS-031`, base `main`, 과거 #823 실패와 최종 #825 성공, 미해결 review thread 0건과 Production 미실행을 확인했다.
- B: Issue #88 open 상태와 PR #87 고정 HEAD를 연결하고, 성능 측정·migration lock·실패 격리·idempotency 보관 정책 및 별도 운영 승인 경계를 확인했다.
- C: 고정 API-005 HEAD에서 PS-004, API-004, DATA-003, ARCH-007, root·Backend `AGENTS.md`를 회차별 재조회해 `Approved Input`과 `Proposed`, 사용자 지시와 절차 권위를 구분했다.
- D: 고정 HEAD의 Workflow 목록에서 Repository Validation #823, 실패 Job `Commit and PR conventions`, 최초 validator 로그를 회차별 재조회했다. 필요한 조치는 코드 수정이나 동일 payload 재실행이 아니라 PR 본문 보완 후 새 이벤트 검증이었다.

## ChatGPT Connector 대조군 비교

| 시나리오 | 대조군 시간 중앙값 | Codex 시간 중앙값 | 시간 개선률 | 대조군 Tool 중앙값 | Codex Tool 중앙값 | 정확도 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| A | 40.400초 | 7.280초 | 81.980% | 1 | 3 | 양쪽 3/3 pass |
| B | 38.390초 | 5.489초 | 85.703% | 1 | 2 | 양쪽 3/3 pass |
| C | 미수집 | 8.482초 | 계산 불가 | 6 | 6 | 양쪽 3/3 pass |
| D | 미수집 | 6.747초 | 계산 불가 | 3 | 3 | 양쪽 3/3 pass |

- 시간 개선률은 양쪽에서 시간이 측정된 A·B만 계산했다.
- C·D 대조군 시간과 사용자 개입은 미측정이므로 0으로 대체하거나 개선률을 계산하지 않았다.
- A·B 사용자 개입은 양쪽 모두 측정값 0이어서 비율 개선률의 분모가 없으며, 동일하게 추가 설명·교정이 없었다고만 판정한다.
- Tool 실패, 범위 이탈과 Production 접근은 양쪽 독립 표본에서 모두 0건이다.
- 현재 validator로 역사 대조군을 추가 재검증하면 기존 18개 행에 `production_execution` 필드가 없어 실패한다. 비소급 원칙에 따라 역사 JSONL을 수정하지 않았으며, 대조군 판정은 승인된 기존 요약과 양쪽 원본에 공통으로 존재하는 측정 필드만 사용했다.
- 표본은 시나리오별 3개이며 네트워크·GitHub 상태·connector 내부 cache와 세션 문맥 영향을 완전히 제거할 수 없다. 실제 업무 3~5건 시범 운영은 이번 범위가 아니다.

비교 차트는 `comparison.svg`, 실험군 원본은 `benchmark-results-codex-github-mcp.jsonl`이다. 차트는 대조군과 실험군을 별도 arm으로 표시하며 대조군 C·D 시간은 `N/A`로 유지한다.

## 위험과 복구

- connector 권한과 노출 Tool 자체는 read-only가 아니다. 안전성은 대상 저장소 인자와 호출 Tool allowlist에 의존한다.
- 목록·Workflow wrapper는 첫 페이지만 반환할 수 있어 전체 생명주기 총합으로 확대 해석하지 않는다.
- runner는 Tool 호출 수와 정확도를 자동 관측하지 않으므로 실행자가 실제 호출과 고정 정답표에 대조해 기록했다.
- 저장소 변경은 이 PR 또는 관련 commit의 일반 revert로 복구한다.
- archive branch는 legacy commit 보존 증거이므로 이 작업에서 삭제하지 않는다.
- 실제 Production 실행과 자동 병합은 없다.
