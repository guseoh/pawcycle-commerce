# HARNESS-AGENT-004 Benchmark 도구와 대조군 차트

## 목적

ChatGPT Connector 대조군과 향후 Codex GitHub MCP 실험군을 같은 schema·외부 벽시계·독립 반복 기준으로 측정하고 검증·시각화할 수 있는 저장소 도구를 준비한다.

## 결과 또는 증거

### 실행 래퍼

`scripts/run-agent-benchmark.py`는 Benchmark 작업을 직접 실행하지 않는다.

```text
start
→ 시작 시각과 시나리오 상태 파일 기록
→ 사용자가 별도 환경에서 Benchmark 수행
→ finish
→ 종료 시각·Tool 호출·정확도·사용자 개입을 JSONL에 추가
```

따라서 래퍼 자체에는 GitHub 쓰기, shell command, AWS, SSH, Docker와 DB 실행 권한이 없다.

예시:

```bash
python scripts/run-agent-benchmark.py start \
  --state .tmp/benchmark-C-1.json \
  --task-id HARNESS-AGENT-005 \
  --arm codex_github_mcp \
  --scenario C \
  --run 1 \
  --target "API-005 fixed ref" \
  --prompt "권위 문서와 우선순위를 설명한다"

# 별도 Codex 세션에서 작업 수행

python scripts/run-agent-benchmark.py finish \
  --state .tmp/benchmark-C-1.json \
  --output docs/reports/HARNESS-AGENT-005/results.jsonl \
  --tool-calls 6 \
  --failed-tool-calls 0 \
  --accuracy pass \
  --user-intervention-measurement measured \
  --user-additional-explanations 0 \
  --user-corrections 0 \
  --independent
```

### 결과 validator

`scripts/validate-agent-benchmark.py`는 다음을 차단한다.

- schema 3.0 필수 필드 누락
- A·B·C·D 독립 반복 3회 미충족
- warm-cache 결과의 독립 반복 혼입
- 사용자 개입 측정 상태와 값 불일치
- 중복 scenario/run
- 범위 이탈 또는 Production 실행 기록
- 음수 시간·Tool 호출

대조군 검증 명령:

```bash
python scripts/validate-agent-benchmark.py \
  docs/reports/HARNESS-AGENT-002/benchmark-results-chatgpt-connector.jsonl \
  docs/reports/HARNESS-AGENT-002/benchmark-results-chatgpt-connector-independent.jsonl \
  --expected-arm chatgpt_connector_pilot
```

### SVG renderer

`scripts/render-agent-benchmark-charts.py`는 외부 Python 의존성 없이 독립 실행만 집계한다.

- 시간 중앙값
- Tool 호출 중앙값
- 정확도 pass 수
- 미수집 시간은 0이 아니라 `N/A`

생성 명령:

```bash
python scripts/render-agent-benchmark-charts.py \
  docs/reports/HARNESS-AGENT-002/benchmark-results-chatgpt-connector.jsonl \
  docs/reports/HARNESS-AGENT-002/benchmark-results-chatgpt-connector-independent.jsonl \
  --output docs/reports/HARNESS-AGENT-004/control-baseline.svg \
  --title "ChatGPT Connector Control Baseline"
```

### 대조군 차트 판정

| 시나리오 | 시간 중앙값 | Tool 호출 중앙값 | 정확도 |
| --- | ---: | ---: | ---: |
| A | 40.400초 | 1 | 3/3 |
| B | 38.390초 | 1 | 3/3 |
| C | N/A | 6 | 3/3 |
| D | N/A | 3 | 3/3 |

차트: `control-baseline.svg`

## 실행한 검증과 미실행 사유

로컬 격리 디렉터리에서 Python 표준 라이브러리만 사용해 다음을 실행했다.

```text
python -m unittest discover -s scripts -p test_agent_benchmark_tools.py -v
→ 3 tests passed

validate-agent-benchmark.py control.jsonl
→ A·B·C·D 독립 반복 검증 성공

render-agent-benchmark-charts.py control.jsonl
→ SVG 생성 성공
```

테스트 범위:

- runner의 start·finish와 양수 벽시계 기록
- 유효한 A·B·C·D 결과 검증
- warm-cache 독립 레코드 거부
- C·D 미수집 시간의 `N/A` 렌더링

실제 Codex GitHub MCP Benchmark는 연결 전이므로 실행하지 않았다. Backend·Frontend·Production·AWS·운영 DB·Secret 실행은 범위 밖이라 수행하지 않았다.

## 위험·제한

- 벽시계는 네트워크·GitHub 상태와 사용자 전환 시간을 포함하므로 양쪽 실험군에서 같은 절차를 사용해야 한다.
- runner는 Tool 호출 수와 정확도를 자동 관측하지 않으므로 실행자가 근거에 맞게 입력해야 한다.
- validator는 결과 schema와 반복 계약을 확인하지만 답안의 기술적 진위를 자동 판정하지 않는다.
- C·D 대조군 시간은 기존 측정에 없으므로 차트에서 `N/A`다.
- 이 차트는 대조군 현황이며 Codex MCP 개선 효과를 보여주지 않는다.

## 복구 경계

스크립트·테스트·문서·SVG만 추가한 저장소 준비 작업이다. PR 또는 관련 commit revert로 복구하며 실제 credential·GitHub 권한·Production 리소스는 변경하지 않았다.
