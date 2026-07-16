# PERF-002 Tech Lead → Platform/SRE 인수인계

## 전달 목적

사용자가 승인한 PERF-001 D1-A~D6-A 범위와 아직 승인하지 않은 측정 세부를 분리해 전달한다. Container sampling 방식과 warm-up 적용 단위가 결정될 때까지 실제 기준선을 측정하지 않는다.

## 대상 역할

- Platform/SRE

## 입력 문서

- `AGENTS.md`
- `infra/AGENTS.md`
- `docs/roles/platform-sre.md`
- `.agents/skills/platform-sre/SKILL.md`
- `docs/performance/PERF-002-local-baseline-approved-inputs.md`
- `docs/performance/PERF-001-local-baseline-decision-request.md`
- `docs/reports/PERF-002/tl-report.md`
- `docs/runbook/FOUNDATION-004-local-integration.md`

## 완료된 작업

- D1-A~D6-A 사용자 승인 기록
- PERF-001 상태와 결정 요약 갱신
- 미승인 대안·SLO·고부하·신규 도구 분리

## 사용 가능한 결과

- 단일 승인 원본의 cohort·SLI candidate·횟수·계산법
- 인증 setup·cleanup과 lifecycle 경계
- reset·seed·cardinality 통제
- 실패 응답 처리와 raw·민감정보 보존 금지 정책
- 승인된 범위, 미결정 세부와 측정 전 중단 조건

## 관련 파일

- `docs/performance/PERF-002-local-baseline-approved-inputs.md`
- `docs/performance/PERF-001-local-baseline-decision-request.md`
- `docs/reports/PERF-002/tl-report.md`

## 인수 조건과 추적성

| 승인 결정 | 다음 측정 적용 |
| --- | --- |
| D1-A | 다섯 cohort 분리, Frontend·proxy와 공개 읽기 분리 |
| D2-A | SLI candidate 범위 승인. Container sampling 방식·지표별 집계는 `Decision Required` |
| D3-A | cold·반복·concurrency·reset 범위 승인. Warm-up 5회의 적용 단위는 `Decision Required` |
| D4-A | allowlist record, PowerShell 5.1 실패 처리, 민감정보·실제 ID 제외 |
| D5-A | 집계 Markdown만 보존, raw 미커밋·미장기보존, 조건부 OS temp 삭제 |
| D6-A | 현재 PowerShell·Docker만 사용하는 저장소 변경 없는 일회성 측정 |

## 확정된 결정

D1-A~D6-A의 추천 범위는 `Approved by PERF-002`다. 이 승인은 container sampling 방식이나 warm-up 적용 단위를 포함하지 않으며 실행 게이트는 `Blocked Pending Detail Approval`이다.

## 미확정 결정

- SLO, latency 목표, 오류 예산과 regression threshold
- browser Web Vitals와 server 내부 timing
- concurrency 2 이상, arrival rate와 장시간 부하
- benchmark script, k6와 관측성 stack
- 병목과 최적화
- container sampling의 periodic/event-based 여부, 시점·횟수·간격과 지표별 집계·optional 규칙
- warm-up 5회의 전체·endpoint별·cohort별 적용 단위

## 승인 필요 항목

실제 기준선 측정 전에 사용자가 container sampling 방식과 warm-up 적용 단위를 승인해야 한다. 조건 변경·도구 도입·저장소 수정·고부하·목표 설정도 별도 승인 대상이다.

## 다음 역할의 입력

PERF-002 승인 원본은 승인 범위 확인에 사용한다. 두 미결정 세부가 승인된 후 동일 commit·QA fixture·Docker Desktop 자원·전원·background workload 조건을 측정 입력으로 고정한다.

## 지켜야 할 규칙

- 승인된 cohort, 반복 횟수, concurrency와 계산법을 변경하지 않는다.
- container sampling과 warm-up 적용 단위를 임의로 정하지 않는다.
- 인증된 읽기 setup·cleanup을 읽기 latency와 합산하지 않는다.
- 실패 표본을 제거하거나 성공한 재실행으로 대체하지 않는다.
- 상태 변경 결과를 고정 cardinality 읽기 결과와 합산하지 않는다.
- 실제 ID, credential, cookie, session ID, CSRF token, body와 raw log를 보존하지 않는다.

## 적용·실행 방법

1. 실제 측정을 시작하지 않고 동일 commit·환경과 QA fixture 사용 가능 여부만 확인한다.
2. Container sampling과 warm-up 적용 단위의 사용자 결정을 요청한다.
3. 두 결정이 승인 문서에 기록된 뒤 별도 Platform/SRE 측정 작업에서 실행 절차를 확정한다.

## 다음 역할의 검증 포인트

- Frontend·proxy와 공개 읽기가 별도 30회 표본인가
- 인증 lifecycle iteration마다 새 session과 전후 CSRF 경계를 지키는가
- 인증된 읽기의 setup·cleanup이 latency 표본에서 제외되는가
- cold 3, 안정화 30초, 측정 제외 warm-up 5회, 각 cohort 30/10회와 concurrency 1의 승인 범위가 유지되는가
- p50과 nearest-rank p95 계산 및 쓰기 p95 미보고가 승인 조건과 일치하는가
- expected status 불일치와 transport·timeout 실패가 누락 없이 집계되는가
- D4 record가 `timestamp_utc`, `response_bytes_if_available`, `subscription_count_before_if_state_change`를 포함한 canonical field와 nullable 규칙을 지키는가
- container sampling 구체 방식과 warm-up 적용 단위가 승인 전 실행 조건으로 오표기되지 않았는가
- seed ID, raw record·log와 민감정보가 저장소·보고서에 남지 않는가

## QA 필요 여부

별도 QA 문서는 불필요하다. 기존 QA fixture를 승인된 범위로 사용하지만 FOUNDATION-006 미실행 위험을 재검증하거나 닫지 않는다.

## AI 리뷰에서 남은 확인 항목

PERF-002 PR의 CI와 AI 리뷰가 완료되고 두 미결정 세부가 사용자 승인된 뒤에만 다음 측정 입력으로 사용한다.

## 알려진 위험

- client elapsed와 container stats로 내부 병목을 분해할 수 없다.
- 상태 변경 10회 동안 cardinality가 증가한다.
- 로컬 노트북 결과는 운영 성능 목표가 아니다.

## 남은 위험과 주의 사항

- SLO·threshold가 없으므로 결과를 pass/fail로 판정하지 않는다.
- 결과가 기대와 달라도 조건을 바꾸거나 표본을 제거하지 않는다.
- instrumentation 공백은 결과 검토 후 별도 승인 요청으로만 다룬다.

## 다음 권장 작업

사용자/Tech Lead가 container sampling 방식과 warm-up 적용 단위를 먼저 결정한다. 그 뒤 별도 Platform/SRE 작업 ID로 일회성 기준선을 측정한다.

## 완료 조건

- 두 미결정 세부가 승인 문서에 기록된다.
- 승인 전에는 실제 측정을 수행하지 않는다.
- 승인 뒤 raw·민감정보 없이 집계 Markdown과 재현 근거만 남긴다.

## 중단 조건

- 승인 조건 변경 또는 추가 제품·기술 결정이 필요함
- container sampling 방식 또는 warm-up 적용 단위가 미승인 상태임
- 제품 코드, 실행 설정, CI, dependency 또는 benchmark script 변경이 필요함
- QA 회원 외 데이터 삭제, volume 삭제 또는 iteration별 reset이 필요함
- 원시 record·log나 민감정보의 커밋·장기 보존이 필요함
- 실제 고부하·장시간 부하나 성능 최적화가 필요함
