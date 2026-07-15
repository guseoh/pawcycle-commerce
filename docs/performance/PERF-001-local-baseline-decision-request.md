# PERF-001 로컬 성능 기준선 결정 요청

## 문서 정보

- 작업 ID: `PERF-001`
- 역할: Platform/SRE
- 상태: `Decision Required`
- 기준 commit: `c7179ca28d7b8628635c9e06973aded940eb15ea`
- 기준 기능: 공개 상품 탐색, session 로그인, 구독 생성·목록·상세, 로그아웃
- 목적: 측정 구현이나 최적화 전에 재현 가능한 로컬 기준선의 범위·조건·증거 정책을 승인받는다.

이 문서는 SLI, SLO, 성능 목표, 부하 조건 또는 도구 도입을 승인하지 않는다. D1~D6의 추천안은 모두 `Proposed`이며 사용자/Tech Lead 승인 후에만 실제 측정이나 저장소 도구 구현으로 진행한다.

## 승인 입력

- `docs/product/PS-002-first-mvp-requirements.md`
- `docs/product/PS-003-ux-product-decisions.md`
- `docs/adr/AUTH-003-session-authentication-approved-inputs.md`
- `docs/api/API-002-public-product-api-contract-proposal.md`
- `docs/api/API-003-subscription-api-contract-decision-request.md`
- `docs/reports/FOUNDATION-005/tl-report.md`
- `docs/qa/FOUNDATION-006/first-mvp-follow-up-test-plan.md`
- `docs/reports/FOUNDATION-006/qa-report.md`
- `docs/runbook/FOUNDATION-004-local-integration.md`
- `docs/performance/experiment-template.md`

## 성능 질문

동일 노트북, 동일 Docker Desktop 자원 할당, 동일 commit과 동일 QA fixture에서 첫 MVP HTTP 흐름의 cold start, warm latency, 상태 코드·오류 비율과 컨테이너 자원 사용량은 어느 범위인가?

이 질문은 현재 값을 측정하기 위한 질문이지 목표값을 정하거나 병목을 확정하는 질문이 아니다. 기준선 뒤에만 병목 가설과 최적화 후보를 작성한다.

## 현재 관측 가능 범위

| 표면 | 현재 근거 | 지금 확인 가능한 항목 | 현재 공백 |
| --- | --- | --- | --- |
| Compose lifecycle | `infra/local-integration/compose.yaml` | `up --wait` 전체 경과, service 상태, health 상태·재시작 수 | service별 health convergence 집계 도구 없음 |
| MySQL health | TCP `SELECT 1`, 5초 간격, 5초 timeout | DB 연결 가능 여부 | query latency, slow query, buffer·lock metric 없음 |
| Backend health | `GET /api/products`, 5초 간격 | JVM·DB·공개 조회 경로의 통합 성공 여부 | 전용 liveness/readiness, JVM·HTTP·DB metric 없음 |
| Frontend health | `GET /products`, 5초 간격 | Next.js HTTP 응답 성공 여부 | hydration, Web Vitals, asset별 timing 없음 |
| Nginx health | `GET /products`, 5초 간격 | proxy와 Frontend 경로 성공 여부 | Backend와 Frontend route별 health 분리 없음 |
| Nginx access log | effective 기본 `main` log format | method, URI, protocol, status, response bytes 등 | `$request_time`, upstream timing과 route template 없음 |
| Backend·Frontend log | Docker `json-file` stdout/stderr | startup·오류 메시지와 제한된 실행 상태 | 공통 request ID, 요청별 status·elapsed time, 구조화 log 없음 |
| Smoke | `infra/local-integration/smoke.ps1` | 첫 MVP endpoint 순서와 기능 pass/fail | endpoint latency·status 집계 없음; Full 반복 시 구독 데이터 증가 |
| Container runtime | Docker inspect·stats | health, restart, CPU, memory, network, block I/O, PIDs | Compose resource limit 없음; 단일 snapshot은 peak·평균이 아님 |
| CI | `.github/workflows/validate-conventions.yml` | Backend test/build, Frontend lint/build, MySQL health | runtime baseline, 성능 artifact, 회귀 threshold 없음 |

조사 시 네 서비스가 healthy이고 Docker `json-file` log와 `docker stats` 표면이 동작함을 확인했다. 이때의 단일 자원 snapshot은 background activity와 sampling 시점이 통제되지 않았으므로 PERF-001 기준 측정 결과가 아니며 보존하지 않는다.

## 측정 가능한 항목과 신규 구현 필요 항목

### 현재 도구만으로 측정 가능

- PowerShell `Stopwatch`와 `Invoke-WebRequest`/`Invoke-RestMethod`로 same-origin client-observed elapsed time과 HTTP status 수집
- `WebRequestSession`으로 cookie를 메모리에만 유지한 session·CSRF 흐름 수행
- `docker compose up --wait` 전체 cold start 경과와 inspect health 상태 확인
- `docker stats`로 컨테이너 CPU·memory·network·block I/O·PIDs 표본 수집
- `docker inspect`로 image ID, restart count, health와 Docker log driver 확인
- Nginx access log의 method·request·status를 로컬 진단에 사용
- commit SHA, Docker·Compose·PowerShell 버전과 환경 조건 기록

### 신규 구현 또는 도입이 있어야 측정 가능

- Nginx `$request_time`, `$upstream_response_time` 기반 server-side proxy timing
- Backend request별 latency, JVM, connection pool, JPA·SQL timing과 trace correlation
- Frontend hydration, Core Web Vitals, browser resource timing
- 지속적인 시계열, dashboard, alert와 CI performance regression gate
- 안정적인 동시 사용자·arrival rate 부하 생성과 percentile report

이 공백을 닫으려면 Nginx·Compose·애플리케이션 설정, dependency 또는 신규 도구가 필요하므로 PERF-001에서 구현하지 않는다.

## D1. 측정할 사용자 흐름과 endpoint 범위

### 상태

`Decision Required`

### 추천안 D1-A

same-origin `http://localhost:<port>`의 첫 MVP를 다음 세 cohort로 분리한다.

1. Frontend·proxy 경계: `GET /products`
2. 공개·인증·읽기 흐름:
   - `GET /api/products`
   - `GET /api/products/{productId}`
   - `GET /api/auth/csrf`
   - `POST /api/auth/login`
   - `GET /api/auth/me`
   - `GET /api/subscriptions`
   - `GET /api/subscriptions/{subscriptionId}`
   - `POST /api/auth/logout`
3. 상태 변경 흐름:
   - `POST /api/subscriptions`
   - 생성 결과를 확인하는 목록·상세 GET

Frontend route의 HTML 응답 시간은 proxy 경계 확인으로만 기록한다. 실제 화면 렌더링, hydration과 Web Vitals는 최소 기준선에 포함하지 않는다. 상태 변경 cohort는 읽기 cohort와 합산하지 않고 별도 표로 보고한다.

### 실질적인 대안

- D1-B: 공개 상품 GET만 측정한다. 가장 안전하고 단순하지만 인증·구독 핵심 흐름을 대표하지 못한다.
- D1-C: 실제 브라우저 화면 전환과 asset·Web Vitals까지 포함한다. 사용자 체감에 가깝지만 browser timing 도구와 측정 기준 승인이 추가로 필요하다.
- D1-D: Full smoke 전체 경과만 한 값으로 측정한다. 재현은 쉽지만 endpoint 병목을 구분하지 못하고 반복 시 구독 수가 증가한다.

### 영향

D1-A는 기능 기준선 전체를 다루면서 읽기와 쓰기 데이터 변화를 분리한다. endpoint별 값은 사용자 체감 전체 시간이 아니라 local proxy에서 관찰한 HTTP 기준선이다.

### 필요한 사용자 승인

- D1-A/B/C/D 중 범위 선택
- 상태 변경 cohort에서 QA 구독 생성을 반복하는 것과 기존 reset 경계 사용 여부
- Frontend browser rendering을 이번 기준선에서 제외할지 여부

## D2. SLI 후보

### 상태

`Decision Required`

### 추천안 D2-A

다음 값을 `SLI candidate`로만 기록하고 목표나 pass/fail threshold는 두지 않는다.

- endpoint·cohort별 client elapsed milliseconds: minimum, median(p50), p95, maximum
- method·정규화 route·status별 요청 수
- 전체·cohort별 non-2xx/3xx 수와 오류 비율
- cold start의 `up --wait` 전체 경과와 service별 최초 healthy 시각
- 측정 전·중·후 container CPU·memory·network·block I/O·PIDs 표본의 평균·최대
- container restart count와 health transition 실패 수
- 환경 fingerprint: commit, OS·runtime, Docker·Compose·PowerShell 버전, Docker Desktop CPU·memory 할당, image ID

HTTP redirect가 승인 흐름에 포함되면 3xx를 무조건 오류로 세지 않고 endpoint별 기대 status와 비교한다. p95는 표본 수와 계산법을 함께 기록한다.

### 실질적인 대안

- D2-B: elapsed 평균과 성공 여부만 기록한다. 계산은 단순하지만 tail latency와 오류 분포를 숨긴다.
- D2-C: Actuator/Micrometer·Prometheus metric과 browser Web Vitals까지 포함한다. 병목 근거가 풍부하지만 신규 dependency·설정·도구 승인이 필요하다.
- D2-D: 즉시 SLO와 regression threshold를 정한다. 기준 분포와 사용자 기대가 없어 현재는 추천하지 않는다.

### 영향

D2-A는 현재 도구로 재현 가능한 client·container 관측을 제공하지만 server 내부 구간과 브라우저 렌더링 병목은 설명하지 못한다. 낮은 표본 수의 percentile은 변동성이 크므로 표본 수와 원자료 정책이 함께 승인돼야 한다.

### 필요한 사용자 승인

- SLI candidate 목록과 percentile 통계 승인
- 기대 status mapping 승인
- SLO·성능 목표는 이번 작업에서 미결정으로 유지한다는 승인

## D3. cold/warm 조건, warm-up, 반복과 동시성

### 상태

`Decision Required`

### 추천안 D3-A

- cold start: volume을 삭제하지 않는 `down` → `up --detach --wait --wait-timeout 180`을 3회, 각 회차 전체 경과와 health convergence 기록
- warm 조건: 네 서비스 healthy 후 30초 안정화, 측정하지 않는 읽기 warm-up 5회
- warm 읽기 cohort: endpoint별 순차 30회
- 상태 변경 cohort: QA 구독을 승인된 reset 절차로 한 번 초기화한 뒤 순차 10회, iteration 순서를 보존하고 읽기 cohort와 분리
- 동시 요청: 1, think time 없음, 한 측정 process만 실행
- 각 비교 실험은 같은 commit, 같은 Docker Desktop CPU·memory 할당, 같은 전원 모드와 같은 background workload 조건 사용
- 실패 요청도 제거하지 않고 status·elapsed time에 포함

`p95`는 nearest-rank 방식 등 계산법을 실험 문서에 고정한다. cold start와 warm request latency를 한 분포에 합치지 않는다. reset은 QA 회원의 구독만 대상으로 하고 즉시 `false`로 복원한다.

### 실질적인 대안

- D3-B: warm-up 3회, 측정 10회, cold start 1회. 빠르지만 변동과 p95를 판단하기 어렵다.
- D3-C: 동시성 3의 짧은 cohort를 추가한다. 약한 경쟁 상태를 볼 수 있지만 baseline과 load test 경계가 넓어지고 조건 승인이 필요하다.
- D3-D: arrival rate·장시간 부하를 사용한다. 현재 제외 범위이며 k6 같은 전용 도구와 안전 한도가 필요하다.

### 영향

D3-A는 로컬 단일 사용자 기준선으로 비교 가능성을 우선한다. 상태 변경 10회는 각 실험에서 같은 순서로 데이터가 증가하므로 iteration별 결과를 함께 보존해야 하며, 대규모 데이터 성능을 대표하지 않는다.

### 필요한 사용자 승인

- cold 3회, warm-up 5회, 읽기 30회, 상태 변경 10회와 동시성 1 승인
- QA 구독 reset과 반복 생성 승인
- Docker Desktop 자원 할당·전원·background process 통제 수준 승인

## D4. 로그·측정 record 수집과 민감정보 제외

### 상태

`Decision Required`

### 추천안 D4-A

측정 record는 다음 allowlist만 사용한다.

```text
timestamp_utc, run_id, cohort, iteration, method,
normalized_route, expected_status, actual_status, elapsed_ms,
response_bytes_if_available, outcome
```

- 숫자 ID는 `/api/products/{productId}`, `/api/subscriptions/{subscriptionId}`로 정규화한다.
- query string은 record에 저장하지 않는다.
- email, password, 요청·응답 body, cookie, session ID, CSRF token과 전체 header를 수집하지 않는다.
- credential과 token은 PowerShell process memory의 `WebRequestSession`과 변수에만 둔다.
- service log는 실패 진단 때 `--tail`과 route·status 중심 filter로만 확인하며 전체 log를 복사하지 않는다.
- Nginx 기본 log에는 elapsed time이 없으므로 client Stopwatch 값을 elapsed evidence로 사용하고 이를 server processing time으로 오표기하지 않는다.

### 실질적인 대안

- D4-B: Nginx log format에 `$request_time`과 upstream timing을 추가한다. server-side timing이 생기지만 Nginx 설정 변경과 redaction 검토가 필요하다.
- D4-C: Backend 구조화 access log·request ID를 추가한다. 상관관계가 좋아지지만 애플리케이션 코드·설정 변경 승인이 필요하다.
- D4-D: 전체 request·response와 raw service log를 보존한다. 민감정보 위험 때문에 선택하지 않는다.

### 영향

D4-A는 최소 민감정보 원칙을 지키지만 client·proxy·Backend·DB 구간을 분해하지 못한다. route 정규화로 개별 QA 데이터 식별자도 결과에서 제거한다.

### 필요한 사용자 승인

- allowlist record schema와 route 정규화 승인
- client elapsed를 최소 기준선으로 사용할지 승인
- 실패 진단 log의 로컬 tail·filter 범위 승인

## D5. 증거 보존 정책

### 상태

`Decision Required`

### 추천안 D5-A

저장소에는 다음 집계·재현 정보만 Markdown으로 보존한다.

- 질문, commit과 환경 fingerprint
- 승인된 cohort·반복·동시성·warm-up 조건
- endpoint·cohort별 표본 수, p50·p95·max, status·오류 집계
- container 자원 사용 평균·최대와 sampling 간격
- 실행 명령 template와 성공·실패 요약
- 알려진 제한, 병목 증거 유무와 다음 가설

원시 request record, raw Docker/Nginx/Backend/MySQL log, credential·cookie·session·CSRF·body는 커밋하지 않는다. 최소안은 원시 record를 process memory에서 집계하고 종료 시 폐기한다. 실패 진단으로 임시 파일이 꼭 필요하면 OS temp에 두고 Secret 검사를 거친 집계 완료 후 삭제하며 파일 경로나 내용을 문서에 복사하지 않는다.

### 실질적인 대안

- D5-B: 비민감 raw CSV를 저장소에 커밋한다. 재계산은 쉽지만 ID·query·환경 정보 누출과 저장소 증가 위험이 있어 추천하지 않는다.
- D5-C: GitHub artifact에 raw 결과를 제한 보존한다. repository workflow 변경, 보존 기간과 접근 정책 승인이 필요하다.
- D5-D: 외부 metric·log service에 전송한다. 비용·Secret·운영 접근 승인이 필요해 현재 제외한다.

### 영향

D5-A는 민감정보와 저장소 크기를 최소화하지만 사후 통계 재계산이 어렵다. 이를 보완하기 위해 계산법, 표본 수와 명령 template를 반드시 남긴다.

### 필요한 사용자 승인

- 집계 Markdown만 commit하는 정책 승인
- 원시 record memory-only와 진단용 OS temp 예외 승인
- 향후 artifact 또는 외부 저장소 필요 여부는 별도 결정으로 유지

## D6. 최소 도구안과 확장안

### 상태

`Decision Required`

### 추천안 D6-A: 현재 도구 최소안

- PowerShell 7 `Stopwatch`, `Invoke-WebRequest`/`Invoke-RestMethod`, `WebRequestSession`
- Docker·Docker Compose CLI의 `up --wait`, `inspect`, `stats`, `logs --tail`
- 기존 local-integration Compose와 QA fixture
- 기존 smoke는 기능 사전 검증에만 사용하고 측정값 수집에는 사용하지 않음
- 승인 후 먼저 저장소 변경 없는 일회성 측정 절차를 검증하고, 재사용 script를 commit할지는 별도 승인

### 실질적인 대안

- D6-B: repository PowerShell benchmark script를 추가한다. 신규 dependency는 없지만 유지할 운영 코드와 테스트가 생긴다.
- D6-C: k6를 도입한다. 반복·동시성·percentile은 안정적이지만 신규 도구와 scenario 코드, version pinning이 필요하다.
- D6-D: Actuator/Micrometer, Prometheus·Grafana 또는 OpenTelemetry를 도입한다. 내부 병목·시계열 관측이 가능하지만 애플리케이션 dependency, Compose, 포트, 보존·보안 정책과 운영 비용 승인이 필요하다.

### 영향

D6-A는 비용과 변경이 없고 첫 기준선에 적합하지만 정밀한 동시 부하, server 내부 metric과 browser timing은 제공하지 않는다. 확장안은 기준선에서 공백이 실제 판단을 막는다는 증거가 생긴 뒤 검토한다.

### 필요한 사용자 승인

- D6-A 최소안 승인
- 승인 후 일회성 측정과 재사용 script 작업을 분리할지 승인
- D6-B/C/D는 현재 미승인으로 유지

## 결정 요약

| 결정 | 추천 | 대안 | 현재 상태 | 사용자 승인 필요 |
| --- | --- | --- | --- | --- |
| D1 범위 | D1-A 세 cohort | 공개만, browser 포함, Full 총경과 | Decision Required | 예 |
| D2 SLI 후보 | D2-A latency·status·오류·자원·환경 | 평균만, metric 확장, 즉시 SLO | Decision Required | 예 |
| D3 조건 | D3-A cold 3, warm-up 5, read 30, write 10, concurrency 1 | 축소안, concurrency 3, 장시간 부하 | Decision Required | 예 |
| D4 record | D4-A allowlist·정규화·client elapsed | Nginx timing, Backend log, raw 보존 | Decision Required | 예 |
| D5 증거 | D5-A 집계 Markdown·raw 미커밋 | raw CSV, Actions artifact, 외부 저장 | Decision Required | 예 |
| D6 도구 | D6-A 현재 PowerShell·Docker | repository script, k6, metric stack | Decision Required | 예 |

## 승인 후 실험 절차 초안

### 1. 환경 고정

1. 승인된 commit을 checkout하고 작업 트리가 깨끗한지 확인한다.
2. `.env.local`의 placeholder 없음과 reset=`false`를 값 출력 없이 확인한다.
3. OS, CPU·memory, 전원 모드, Docker Desktop CPU·memory 할당, Docker·Compose·PowerShell 버전과 image ID를 기록한다.
4. 같은 origin, 같은 QA fixture와 같은 named volume을 사용한다.
5. 다른 benchmark, build와 대용량 download를 중단하고 조건을 기록한다.

### 2. 기능 사전 검증

1. Compose config를 검사한다.
2. 네 service를 healthy로 기동한다.
3. 기존 Full smoke를 한 번 실행해 기능 기준선을 확인한다.
4. smoke 결과는 성능 표본에 넣지 않는다.
5. reset 사용이 승인됐다면 QA 회원 구독만 초기화하고 즉시 reset=`false`로 복원한다.

### 3. cold start cohort

1. volume을 삭제하지 않고 `down`한다.
2. Stopwatch를 시작하고 `up --detach --wait --wait-timeout 180`을 실행한다.
3. 전체 경과, service health 시각, 실패·restart를 기록한다.
4. 승인 횟수만 반복하며 각 회차 사이 조건을 바꾸지 않는다.

### 4. warm-up과 측정

1. 네 service healthy 뒤 승인된 안정화 시간을 기다린다.
2. 승인된 warm-up 횟수만큼 읽기 cohort를 실행하되 결과에 포함하지 않는다.
3. 공개·인증·읽기 cohort를 concurrency 1로 승인 횟수 실행한다.
4. 상태 변경 cohort는 별도 session과 별도 결과 표에서 실행한다.
5. 각 요청에서 allowlist record만 process memory에 저장한다.
6. container stats는 승인된 sampling 간격으로 수집하고 집계값만 남긴다.
7. 실패 요청을 삭제하거나 재실행해 대체하지 않는다.

### 5. 집계와 안전 확인

1. endpoint·cohort별 표본 수, p50·p95·max, status·오류 비율을 계산한다.
2. container CPU·memory·I/O 표본의 평균·최대와 sampling 간격을 기록한다.
3. credential, header, body, cookie, session ID, CSRF token, 원시 log와 실제 데이터 ID가 없는지 확인한다.
4. 단일 snapshot이나 smoke 총경과를 endpoint latency로 오표기하지 않는다.
5. raw record를 보존하지 않고 집계와 재현 명령만 성능 실험 문서에 기록한다.

### 6. 판정과 다음 작업

1. 기준선은 pass/fail 목표 없이 분포와 제한을 설명한다.
2. client·container 증거만으로 server·DB 병목을 단정하지 않는다.
3. 병목 가설이 생기면 증거, 대안, 제품·인프라 영향과 동일 조건 재측정 계획을 별도 승인 요청으로 작성한다.
4. 최적화, dependency, Nginx·Compose·CI·제품 코드 변경은 별도 승인 후 진행한다.

## 명시적 미결정과 제외

- SLO, latency 목표, 오류 예산과 regression threshold
- browser Web Vitals와 실제 사용자 체감 목표
- 동시성 2 이상, arrival rate와 장시간 부하
- k6, Actuator, Micrometer, Prometheus, Grafana, OpenTelemetry
- Nginx log format, Compose, CI와 애플리케이션 변경
- FOUNDATION-006의 GET 오류 재시도와 session 만료 미실행 위험 해소

## 사용자 결정 요청

사용자/Tech Lead는 D1~D6 각각에 대해 추천안 승인, 대안 선택 또는 수정 조건을 기록해야 한다. 모두 승인되기 전에는 실제 기준선 측정, 부하 실행, 측정 script 구현과 최적화를 시작하지 않는다.
