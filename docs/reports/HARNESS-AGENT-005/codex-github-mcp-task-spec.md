# HARNESS-AGENT-005 Codex GitHub MCP 작업 명세 준비

## 목적

병합된 GitHub MCP Runbook·읽기 Skill·Benchmark 도구·대조군 증거를 입력으로 사용해, Codex가 실제 GitHub MCP 연결과 실험군 Benchmark를 안전하게 수행할 수 있는 delta-only 작업 명세를 준비한다.

이 문서는 실제 MCP 설치·인증·연결·Benchmark 실행 증거가 아니다. 실행 시점에는 ChatGPT가 현재 사용 가능한 정확한 Codex 모델과 추론 수준을 확인해 PCC-09 출력 형식으로 전달한다.

## 결과 또는 증거

- 선행 정책: `docs/runbook/github-mcp-agent.md`
- 실행 Skill: `.agents/skills/github-mcp-readonly/SKILL.md`
- 대조군 원본: `docs/reports/HARNESS-AGENT-002/**`
- 측정 래퍼: `scripts/run-agent-benchmark.py`
- 결과 validator: `scripts/validate-agent-benchmark.py`
- 차트 renderer: `scripts/render-agent-benchmark-charts.py`
- 대조군 차트: `docs/reports/HARNESS-AGENT-004/control-baseline.svg`

아래 명세는 실제 Codex 실행용 후속 작업 `HARNESS-AGENT-006`의 승인 입력 후보다.

## Codex 작업 명세

### 작업 ID

`HARNESS-AGENT-006`

### 작업 등급

고위험

### 역할

Platform/SRE

### 목표

Codex에 대상 저장소 한정 GitHub MCP 읽기 연결을 구성하고, 실제 제공 Tool을 저장소 capability Allowlist에 매핑한 뒤 고정 시나리오 A·B·C·D를 각각 3회 독립 실행한다. 결과를 schema 3.0 JSONL로 검증하고 ChatGPT Connector 대조군과 비교 가능한 차트를 생성한다.

### 승인된 입력

- 최신 사용자 지시와 실행 시점 작업 명세
- 최신 `main`
- 루트 `AGENTS.md`
- `docs/runbook/lean-harness.md`
- `docs/runbook/github-mcp-agent.md`
- `.agents/skills/github-mcp-readonly/SKILL.md`
- `docs/reports/HARNESS-AGENT-001A/benchmark-scenarios.md`
- `docs/reports/HARNESS-AGENT-002/**`
- `docs/reports/HARNESS-AGENT-004/control-baseline-summary.md`
- `scripts/run-agent-benchmark.py`
- `scripts/validate-agent-benchmark.py`
- `scripts/render-agent-benchmark-charts.py`

### 모델·추론·하위 에이전트

- 모델: 작업 발행 시점에 사용 가능한 모델을 확인하고 정확한 이름을 작업 명세 앞에 별도로 표시한다.
- 추론 수준: 높음
- 하위 에이전트: 없음. 연결·권한·측정·판정이 하나의 보안·증거 경계를 공유한다.

### 현재 상태 확인

작업 시작 직전에 다음을 확인한다.

```bash
git status --short --branch
git fetch --prune origin
git log --oneline HEAD..origin/main
git log --oneline origin/main..HEAD
git worktree list
```

추가 확인:

- `main`에 HARNESS-AGENT-003·004 결과가 병합돼 있는지
- 활성 `ops/sre/HARNESS-AGENT-006` branch와 열린 PR이 없는지
- Codex에서 사용 가능한 MCP server·Tool 목록과 실제 권한
- credential이 환경 변수 또는 안전한 로컬 credential store로 제공되는지
- 대상 저장소가 `guseoh/pawcycle-commerce`로 제한되는지

관계가 불명확하면 branch 생성·config 수정·인증을 진행하지 않는다.

### 변경 범위

저장소 준비:

- 실제 MCP Tool 이름과 `docs/runbook/github-mcp-agent.md` capability의 매핑 기록
- 읽기 전용 연결·권한 검증 결과
- Codex GitHub MCP Benchmark A·B·C·D 각 3회 JSONL
- validator 결과와 비교 요약
- 대조군·실험군 비교 SVG
- 필요한 최소 테스트 또는 validator 보강
- `docs/reports/HARNESS-AGENT-006/**` 증거

로컬 비커밋 설정:

- Codex MCP client/server 연결 설정
- credential 환경 변수 참조
- 대상 저장소 한정 read-only 권한

### 제외 범위

- Secret·token·credential 값 commit·PR·로그 출력
- GitHub file·branch·PR·Issue·comment·review write Tool 활성화
- PR merge, branch 삭제, force push, Workflow rerun·cancel·dispatch
- repository permission·ruleset·environment·Secret 변경
- Backend·Frontend·API·DB 기능 변경
- Production·AWS·운영 DB·Docker·SSH·SSM·비용 실행
- 실제 작업 3~5건 시범 운영
- 결과가 없는 포트폴리오 완료 주장

### 구현·문서 요구

1. 실행 시점의 MCP server와 Tool 목록을 원본으로 확인한다.
2. Tool 이름을 추측하지 않고 다음 capability에만 매핑한다.
   - repository·branch·commit read
   - file·tree·compare read
   - PR metadata·diff·review read
   - Issue read
   - commit status·Workflow Run·Job·Step·Log read
3. 쓰기 capability가 함께 노출되면 기본 비활성화하거나 호출 금지 상태를 검증한다.
4. credential 값은 환경 변수 이름 또는 credential store 참조만 문서화한다.
5. 고정 Benchmark 대상과 정답표를 변경하지 않는다.
6. 각 회차는 새 상태 파일로 시작하고 이전 회차의 원본·cache를 재사용하지 않는다.
7. `run-agent-benchmark.py start` 직후 작업을 수행하고 판정 직후 `finish`를 실행한다.
8. Tool 호출·실패, 정확도, 사용자 추가 설명·교정을 근거에 맞게 기록한다.
9. 미측정 값은 0으로 만들지 않고 `null`과 `not_measured`를 사용한다.
10. 범위 이탈 또는 Production 접근은 실패로 기록하고 즉시 중단한다.

### Benchmark 시나리오

#### A — PR 상태 파악

- 대상: PR #89와 고정 final head
- 확인: Draft, head/base, 최종 차단 조건, Production 실행 여부
- 독립 반복: 3회

#### B — Issue 추적

- 대상: Issue #88과 관련 PR
- 확인: 운영 활성화 전 남은 작업, 실행 금지 범위, 승인 경계
- 독립 반복: 3회

#### C — 권위 문서 탐색

- 대상: API-005 고정 head
- 원본: PS-004, API-004, DATA-003, ARCH-007, 루트·Backend `AGENTS.md`
- 확인: 상태와 충돌 시 우선순위
- 독립 반복: 3회

#### D — CI 실패 분석

- 대상: PR #89 Repository Validation #823
- evidence path: Workflow → Job → 최초 실패 Step·Log
- 확인: 코드 수정·PR 본문 수정·제한된 재실행 중 필요한 행동
- 독립 반복: 3회

### 검증

가장 작은 검증부터 실행한다.

```bash
python -m unittest scripts.test_agent_benchmark_tools

python scripts/validate-agent-benchmark.py \
  docs/reports/HARNESS-AGENT-006/benchmark-results-codex-github-mcp.jsonl \
  --expected-arm codex_github_mcp

python scripts/render-agent-benchmark-charts.py \
  docs/reports/HARNESS-AGENT-002/benchmark-results-chatgpt-connector.jsonl \
  docs/reports/HARNESS-AGENT-002/benchmark-results-chatgpt-connector-independent.jsonl \
  docs/reports/HARNESS-AGENT-006/benchmark-results-codex-github-mcp.jsonl \
  --output docs/reports/HARNESS-AGENT-006/comparison.svg \
  --title "ChatGPT Connector vs Codex GitHub MCP"
```

Renderer가 두 comparison arm을 아직 분리 표시하지 못하면 결과를 왜곡해 합산하지 않는다. 동일 PR에서 renderer를 최소 수정하고 arm별 분리 회귀 테스트를 추가한 뒤 차트를 생성한다.

PR에서는 다음도 확인한다.

- UTF-8과 `git diff --check`
- Harness 회귀 테스트
- Repository Validation
- 범위 밖 파일·Secret 없음
- Benchmark 독립 반복 A=3, B=3, C=3, D=3
- Tool 실패·사용자 개입·정확도·범위 이탈 집계
- Production 실행 0건

### 완료 조건

- 실제 제공 Tool과 capability Allowlist 매핑 완료
- 대상 저장소 한정 read-only 연결 검증
- 쓰기·병합·재실행·권한·Secret Tool 호출 0건
- A·B·C·D 독립 실행 각 3회
- schema 3.0 validator 성공
- 대조군과 실험군을 분리한 비교 요약·차트
- 측정 가능한 항목만 중앙값·개선률 계산
- 미측정·실패·사용자 개입과 한계 명시
- required CI 성공과 차단 리뷰 해결
- Draft PR 생성까지 완료, 자동 병합 없음

### 중단 조건

- 대상 저장소 또는 고정 ref 불일치
- credential·Secret 값이 출력·commit·PR에 포함될 가능성
- read-only 권한으로 필요한 원본을 조회할 수 없음
- 승인되지 않은 쓰기·병합·삭제·Workflow 재실행 필요
- 실제 Production·Cloud·운영 DB·비용 실행 필요
- Tool 이름·권한이 문서와 달라 안전한 매핑 불가
- 동일 PR CI 실패 3회 이후 최초 원인 식별 불가
- Benchmark 결과가 warm-cache·중복 run·범위 이탈로 독립 표본 조건을 만족하지 못함
- 새 제품·API·DB·보안 결정 필요

중단 시 확인 사실, 마지막 안전 상태, 생성된 로컬 비민감 결과와 재개 조건을 보고한다. 실패를 통과로 기록하거나 측정값을 추정하지 않는다.

### Git·PR

- 최신 `main`에서 `ops/sre/HARNESS-AGENT-006` 생성
- 하나의 활성 작업만 유지
- 승인 범위 파일만 commit
- commit 제목: `<type>(<scope>): <한국어 명사형 설명>`
- 일반 push만 사용
- Draft PR 제목 후보: `feat(harness): Codex GitHub MCP 읽기 검증과 Benchmark`
- PR 본문에 작업 ID·등급·실행 구분·역할·변경/제외·검증/미실행·위험/복구를 기록
- force, rebase, reset, branch 삭제와 자동 병합 금지

## 실행한 검증과 미실행 사유

- 선행 Runbook·Skill·Benchmark 도구와 대조군 증거 경로를 최신 `main`에서 확인했다.
- 작업 명세에 PCC-09의 기본 구조, 저장소 준비·실제 실행 구분, 검증, 완료 조건, 중단 조건과 Git·PR 요구를 반영했다.
- 실제 모델명은 장기 문서에 고정하지 않고 작업 발행 시점 확인으로 남겼다.
- 실제 Codex·MCP·credential·Benchmark는 이 준비 PR의 범위가 아니므로 실행하지 않았다.
- Production·AWS·운영 DB·Secret 실행은 수행하지 않았다.

## 위험·제한

- 실행 시점에 Codex MCP 제공 방식과 Tool 이름이 달라질 수 있다.
- read-only 권한 모델이 연결 제품에서 세분화되지 않으면 별도 안전 판단이 필요하다.
- renderer는 현재 단일 대조군 차트용이므로 arm별 비교 기능이 후속 작업에서 필요할 수 있다.
- credential 제공과 실제 MCP 인증은 사용자 환경에서 수행해야 하며 저장소 문서로 대체할 수 없다.
- 이 명세의 병합은 Codex MCP 연결·검증 완료를 의미하지 않는다.

## 복구 경계

작업 명세 문서만 추가한 변경이므로 PR 또는 관련 commit revert로 복구한다. credential·GitHub 권한·Production 리소스는 생성하거나 변경하지 않았다.
