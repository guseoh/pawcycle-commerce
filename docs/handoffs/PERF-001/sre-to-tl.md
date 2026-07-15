# PERF-001 Platform/SRE → Tech Lead 인수인계

## 전달 목적

첫 MVP 로컬 성능 기준선을 실제 측정하기 전에 D1~D6의 범위, SLI candidate, 반복 조건, 비민감 record, 증거 보존과 도구 경계를 사용자/Tech Lead가 결정하도록 전달한다.

## 다음 역할

사용자/Tech Lead가 추천안과 대안을 검토하고 각 결정의 승인 또는 수정 조건을 기록한다. 승인 전에는 실제 baseline, 부하 실행, 측정 도구 구현과 최적화를 시작하지 않는다.

## 입력 문서

- `docs/performance/PERF-001-local-baseline-decision-request.md`
- `docs/reports/PERF-001/sre-report.md`
- `docs/performance/experiment-template.md`
- `docs/runbook/FOUNDATION-004-local-integration.md`
- `docs/reports/FOUNDATION-005/tl-report.md`
- `docs/reports/FOUNDATION-006/qa-report.md`

## 사용 가능한 결과

- 현재 Compose·Nginx·Backend·Frontend·smoke·CI의 관측 가능 범위와 공백
- 현재 도구만으로 가능한 client elapsed, health와 container 자원 측정 경계
- D1~D6별 추천안, 실질적인 대안, 영향과 승인 필요 항목
- cold/warm, warm-up, 반복·동시성, 상태 변경 데이터 통제 초안
- credential·cookie·session·CSRF·body·raw log 제외 정책
- 집계 Markdown과 재현 명령만 commit하는 증거 정책
- 승인 후 동일 조건으로 실행할 단계별 실험 절차

## 미결정 사항 또는 승인 필요 항목

### D1 범위

- 세 cohort 추천안을 사용할지
- 상태 변경 반복과 QA reset을 허용할지
- browser rendering을 최소 기준선에서 제외할지

### D2 SLI candidate

- client latency p50·p95·max, status·오류 비율, cold health, container 자원과 환경 fingerprint를 사용할지
- SLO와 threshold를 이번 단계에서 계속 미결정으로 둘지

### D3 조건

- cold 3회, warm-up 5회, 읽기 30회, 상태 변경 10회, concurrency 1
- Docker Desktop 자원·전원·background workload 통제 조건

### D4 record

- method, 정규화 route, expected·actual status, elapsed와 outcome allowlist
- client elapsed를 server processing time과 구분해 최소 기준선으로 사용할지

### D5 증거

- raw record·log 미커밋과 process-memory 집계
- 진단용 OS temp 예외와 집계 완료 후 삭제

### D6 도구

- PowerShell·Docker 현재 도구 최소안
- repository PowerShell script, k6, Actuator/Micrometer와 metric stack은 별도 승인으로 미룰지

## 검증 포인트

- D1~D6가 모두 `Decision Required`이며 승인으로 오표기되지 않았는가
- 첫 MVP 기능 endpoint가 공개·인증·읽기·상태 변경으로 추적되는가
- cold start와 warm latency가 한 분포로 합쳐지지 않는가
- 상태 변경 cohort가 읽기 cohort와 분리되고 데이터 증가가 기록되는가
- latency가 client-observed 값이며 Nginx·Backend·DB 구간으로 오해되지 않는가
- credential, header, body, cookie, session ID와 CSRF token이 record·log·문서에서 제외되는가
- raw log·record를 저장소에 커밋하지 않는가
- SLO, threshold와 병목이 근거 없이 확정되지 않는가
- 신규 dependency·설정·외부 service가 PERF-001에 포함되지 않았는가
- FOUNDATION-006 미실행 위험을 닫았다고 오표기하지 않는가

## 중단 조건

- 사용자가 D1~D6을 결정하지 않은 상태에서 실제 측정 또는 부하 실행이 필요함
- 신규 dependency, 외부 service, 비용, Secret 또는 운영 접근 권한이 필요함
- Nginx·Compose·CI나 Backend·Frontend 제품 코드 변경이 필요함
- QA 회원 외 데이터 삭제 또는 volume 삭제가 필요함
- 결과를 좋게 만들기 위해 반복·동시성·warm-up·자원 조건을 바꿔야 함
- 원시 log, credential, cookie, session 또는 CSRF token을 저장해야 함

## 남은 위험 또는 주의 사항

- local baseline은 운영 capacity가 아니라 동일 노트북에서의 비교 기준이다.
- 현재 최소안은 server 내부와 browser rendering 병목을 분해하지 못한다.
- container stats와 percentile은 sampling·표본 수·background workload에 민감하다.
- 오류 재시도 keyboard 접근성과 session 만료는 FOUNDATION-006의 미실행 위험으로 남는다.

## 승인 후 다음 절차

1. Tech Lead가 D1~D6 결정 결과를 PERF-001 문서 또는 후속 승인 문서에 기록한다.
2. 별도 baseline 측정 작업에서 승인된 조건을 고정한다.
3. 기능 smoke를 통과시킨 뒤 cold와 warm cohort를 분리 측정한다.
4. 집계·재현 명령·환경 fingerprint와 제한만 보존한다.
5. 병목 가설과 변경안은 기준 증거 이후 별도 역할 인수인계와 재측정 계획으로 제안한다.
