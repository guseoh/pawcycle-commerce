# PERF-002 Tech Lead → Platform/SRE 인수인계

## 전달 목적

사용자가 승인한 PERF-001 D1-A~D6-A를 변경 없이 적용해 저장소 변경 없는 일회성 로컬 성능 기준선을 측정하도록 전달한다.

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
- 다음 측정 작업의 변경 불가 조건과 중단 조건

## 관련 파일

- `docs/performance/PERF-002-local-baseline-approved-inputs.md`
- `docs/performance/PERF-001-local-baseline-decision-request.md`
- `docs/reports/PERF-002/tl-report.md`

## 인수 조건과 추적성

| 승인 결정 | 다음 측정 적용 |
| --- | --- |
| D1-A | 다섯 cohort 분리, Frontend·proxy와 공개 읽기 분리 |
| D2-A | 읽기·lifecycle 30회 p50·p95·max, 쓰기 10회 p50·max, expected status 오류율, cold health, container 자원·restart, 환경 fingerprint |
| D3-A | volume 미삭제 `down` → `up --detach --wait --wait-timeout 180` cold 3회, 안정화 30초, warm-up 5, endpoint별 읽기 30회, concurrency 1, reset·seed·cardinality 고정 |
| D4-A | allowlist record, PowerShell 5.1 실패 처리, 민감정보·실제 ID 제외 |
| D5-A | 집계 Markdown만 보존, raw 미커밋·미장기보존, 조건부 OS temp 삭제 |
| D6-A | 현재 PowerShell·Docker만 사용하는 저장소 변경 없는 일회성 측정 |

## 확정된 결정

D1-A~D6-A는 `Approved`다. 세부 조건은 PERF-002 승인 원본을 그대로 사용한다.

## 미확정 결정

- SLO, latency 목표, 오류 예산과 regression threshold
- browser Web Vitals와 server 내부 timing
- concurrency 2 이상, arrival rate와 장시간 부하
- benchmark script, k6와 관측성 stack
- 병목과 최적화

## 승인 필요 항목

승인된 일회성 기준선에는 추가 승인이 필요하지 않다. 단, 조건 변경·도구 도입·저장소 수정·고부하·목표 설정이 필요하면 별도 승인을 받아야 한다.

## 다음 역할의 입력

PERF-002 승인 원본, 동일 commit·QA fixture·Docker Desktop 자원·전원·background workload 조건을 측정 입력으로 고정한다.

## 지켜야 할 규칙

- cohort, 횟수, 순서, concurrency, warm-up과 계산법을 변경하지 않는다.
- 인증된 읽기 setup·cleanup을 읽기 latency와 합산하지 않는다.
- 실패 표본을 제거하거나 성공한 재실행으로 대체하지 않는다.
- 상태 변경 결과를 고정 cardinality 읽기 결과와 합산하지 않는다.
- 실제 ID, credential, cookie, session ID, CSRF token, body와 raw log를 보존하지 않는다.

## 적용·실행 방법

1. 동일 commit과 환경 fingerprint를 확인한다.
2. QA fixture와 reset 권한을 사전 확인한다.
3. cold 3회와 각 healthy 후 30초 안정화를 수행한다.
4. run 시작 전 reset 한 번, 즉시 `false` 복원, 측정 제외 seed 한 건을 준비한다.
5. warm-up 5회 후 다섯 cohort를 승인된 횟수로 concurrency 1에서 순차 실행한다.
6. 동일 schema로 성공·HTTP error·transport error·timeout을 집계한다.
7. 각 warm endpoint 또는 lifecycle cohort의 시작 전·중간 iteration 직후·종료 직후 container stats를 수집하고 세 표본의 평균·최대를 집계한다.
8. seed ID는 집계 전에 폐기하고 raw record는 process memory에서 집계 직후 폐기한 뒤 집계 Markdown만 보존한다.

## 다음 역할의 검증 포인트

- Frontend·proxy와 공개 읽기가 별도 30회 표본인가
- 인증 lifecycle iteration마다 새 session과 전후 CSRF 경계를 지키는가
- 인증된 읽기의 setup·cleanup이 latency 표본에서 제외되는가
- cold 3, 안정화 30초, warm-up 5, 각 cohort 30/10회와 concurrency 1이 유지되는가
- p50과 nearest-rank p95 계산 및 쓰기 p95 미보고가 승인 조건과 일치하는가
- expected status 불일치와 transport·timeout 실패가 누락 없이 집계되는가
- D4 record가 `timestamp_utc`, `response_bytes_if_available`, `subscription_count_before_if_state_change`를 포함한 canonical field와 nullable 규칙을 지키는가
- container 자원이 시작 전·중간·종료의 event-based 3개 표본으로 수집되고 관측 스케줄과 평균·최대가 기록되는가
- seed ID, raw record·log와 민감정보가 저장소·보고서에 남지 않는가

## QA 필요 여부

별도 QA 문서는 불필요하다. 기존 QA fixture를 승인된 범위로 사용하지만 FOUNDATION-006 미실행 위험을 재검증하거나 닫지 않는다.

## AI 리뷰에서 남은 확인 항목

PERF-002 PR의 CI와 AI 리뷰가 완료된 뒤 유효한 미해결 항목이 없어야 다음 측정 입력으로 사용한다.

## 알려진 위험

- client elapsed와 container stats로 내부 병목을 분해할 수 없다.
- 상태 변경 10회 동안 cardinality가 증가한다.
- 로컬 노트북 결과는 운영 성능 목표가 아니다.

## 남은 위험과 주의 사항

- SLO·threshold가 없으므로 결과를 pass/fail로 판정하지 않는다.
- 결과가 기대와 달라도 조건을 바꾸거나 표본을 제거하지 않는다.
- instrumentation 공백은 결과 검토 후 별도 승인 요청으로만 다룬다.

## 다음 권장 작업

별도 Platform/SRE 작업 ID로 일회성 기준선을 측정하고 집계 결과, 재현 명령, 환경 fingerprint, 제한과 다음 결정 요청을 작성한다.

## 완료 조건

- 승인 조건 그대로 모든 cohort를 측정하거나 중단 사유를 보고한다.
- raw·민감정보 없이 집계 Markdown과 재현 근거만 남긴다.
- 실제 측정값을 SLO, threshold 또는 운영 capacity로 오표기하지 않는다.

## 중단 조건

- 승인 조건 변경 또는 추가 제품·기술 결정이 필요함
- 제품 코드, 실행 설정, CI, dependency 또는 benchmark script 변경이 필요함
- QA 회원 외 데이터 삭제, volume 삭제 또는 iteration별 reset이 필요함
- 원시 record·log나 민감정보의 커밋·장기 보존이 필요함
- 실제 고부하·장시간 부하나 성능 최적화가 필요함
