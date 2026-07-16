# PERF-002 로컬 성능 기준선 승인 입력

## 문서 정보

- 작업 ID: `PERF-002`
- 역할: Tech Lead
- 상태: `Approved`
- 승인 주체: 사용자/Product Owner 겸 Tech Lead
- 승인 근거: PERF-002 작업 요청에서 PERF-001의 D1-A~D6-A를 모두 명시 승인
- 적용 대상: 다음 Platform/SRE의 저장소 변경 없는 일회성 로컬 기준선 측정
- 기준 문서: `docs/performance/PERF-001-local-baseline-decision-request.md`

이 문서가 다음 기준선 측정 작업의 단일 승인 원본이다. 승인 범위는 D1-A~D6-A에 한정되며 SLO, 목표값, 회귀 threshold, 고부하, 신규 도구 또는 제품·실행 설정 변경을 승인하지 않는다.

## D1-A 승인: 측정 범위

다음 다섯 cohort를 서로 분리해 측정한다.

1. Frontend·proxy 경계: `GET /products`
2. 공개 읽기: `GET /api/products`, `GET /api/products/{productId}`
3. 인증된 읽기: `GET /api/subscriptions`, `GET /api/subscriptions/{subscriptionId}`
4. 인증 lifecycle: CSRF 획득, 로그인, CSRF 갱신, 인증 확인, 로그아웃
5. 구독 상태 변경: `POST /api/subscriptions`와 생성 후 목록·상세 확인

- Frontend·proxy 경계는 공개 읽기와 분리한다.
- 실제 browser rendering, hydration과 Web Vitals는 제외한다.
- 인증된 읽기의 session·CSRF·login setup과 logout cleanup은 읽기 latency 표본에서 제외해 별도 기록한다.
- 인증 lifecycle은 매 iteration 새 `WebRequestSession` → 로그인 전 CSRF → 로그인 → 로그인 후 CSRF → 인증 확인 → 로그아웃 → session 폐기 순서를 사용한다.

## D2-A 승인: SLI candidate

- Frontend·proxy, 공개 읽기, 인증된 읽기와 인증 lifecycle: 표본 수, p50, p95, max
- 구독 상태 변경 10회: 표본 수, p50, max
- p50: 정렬된 표본의 중앙값. 짝수 표본은 가운데 두 값의 산술평균
- p95: nearest-rank `ceil(0.95 × n)`
- method·normalized route·expected status·actual status별 요청 수와 기대 status 불일치 오류 비율
- cold start 경과, service별 health convergence, container CPU·memory·network·block I/O·PIDs, restart와 환경 fingerprint

Container 자원은 periodic sampling을 사용하지 않고 각 warm endpoint 또는 lifecycle cohort의 첫 요청 전, 중간 iteration 직후, 마지막 iteration 직후에 총 3회 수집한다. 30회 cohort의 중간은 15번째 iteration 직후, 10회 cohort의 중간은 5번째 iteration 직후다. 각 표본의 관측점과 `timestamp_utc`를 기록하며 다음처럼 지표별로 집계한다.

- CPU percentage: 숫자 표본의 평균·최대
- memory usage bytes: 숫자 표본의 평균·최대
- network RX/TX bytes: 방향별 누적 counter 세 표본의 평균·최대. 처리량으로 해석하지 않음
- block read/write bytes: 방향별 누적 counter 세 표본의 평균·최대. 처리량으로 해석하지 않음
- PIDs: 제공된 정수 표본의 평균·최대와 가용 표본 수. Windows에서 미제공 시 `null`로 유지하고 `0`으로 대체하지 않음

이 event-based sampling schedule과 지표별 단위·가용 표본 수를 결과에 남기며 단일 snapshot은 평균·최대로 표현하지 않는다.

기대한 3xx는 정상이고, 기대하지 않은 3xx를 포함한 expected status 불일치만 오류다. 모든 elapsed는 client-observed 값이며 Nginx·Backend·DB server processing time으로 표현하지 않는다. SLO, latency 목표, 오류 예산과 regression threshold는 미결정이다.

## D3-A 승인: 고정 측정 조건

| 항목 | 승인 조건 |
| --- | --- |
| cold start | volume을 삭제하지 않는 `down` → `up --detach --wait --wait-timeout 180` 3회, 전체 경과와 service별 health convergence 기록 |
| 안정화 | service healthy 후 30초 |
| warm-up | 측정 제외 5회 |
| Frontend·proxy | 순차 30회 |
| 공개 읽기 | endpoint별 순차 30회 |
| 인증된 읽기 | endpoint별 순차 30회 |
| 인증 lifecycle | 순차 30회 |
| 구독 상태 변경 | 순차 10회 |
| concurrency | 1 |
| think time | 없음 |

- 동일 commit, QA fixture, Docker Desktop 자원 할당, 전원 모드와 background workload 조건을 유지한다.
- run 시작 전에 QA 회원 구독 reset을 한 번 수행하고 reset 설정을 즉시 `false`로 복원한다.
- 읽기 측정에서 제외되는 seed 구독 1건을 생성한다. seed `subscriptionId`는 측정 중 process memory에만 보관하고 집계 전에 폐기한다.
- 상태 변경 iteration마다 `iteration`과 `subscription_count_before`를 기록한다.
- 증가하는 cardinality 결과를 고정 cardinality 읽기 기준선이나 일반 읽기 cohort와 합산하지 않는다.
- 고정 cardinality 비교가 필요하면 iteration 사이가 아니라 별도 실험 run 사이에서만 reset한다.

## D4-A 승인: 측정 record

HTTP 측정 record의 canonical allowlist는 다음 PERF-001 D4-A 필드명으로 고정한다.

```text
timestamp_utc, run_id, cohort, iteration, method,
normalized_route, expected_status, actual_status, elapsed_ms,
response_bytes_if_available, subscription_count_before_if_state_change,
outcome
```

- `timestamp_utc`는 UTC 시각, elapsed와 response bytes·cardinality는 정수로 process memory의 PowerShell object에 유지한다.
- `actual_status`는 transport error·timeout일 때만 `null`이다.
- `response_bytes_if_available`은 확인할 수 없을 때 `null`이다.
- `subscription_count_before_if_state_change`는 구독 상태 변경 cohort에서만 정수이고 다른 cohort에서는 `null`이다.
- raw record는 직렬화하거나 파일로 저장하지 않는다. 승인된 직렬화 결과는 집계 Markdown뿐이다.

- email, password, 요청·응답 body, 전체 header, cookie, session ID와 CSRF token은 수집하지 않는다.
- 실제 상품·구독 ID와 query string은 보존하지 않는다.
- PowerShell 5.1 호환 `try/catch/finally`를 기본안으로 사용한다.
- 성공, HTTP error, transport error와 timeout을 동일 schema에 기록한다.
- 실패 표본을 제거하거나 재실행 결과로 대체하지 않는다.
- 기대한 3xx는 정상으로, expected status 불일치는 `unexpected_status` 오류로 집계한다.

## D5-A 승인: 증거 보존

저장소에는 집계 Markdown, 재현 명령, 환경 fingerprint, 계산법, 제한과 결과 해석만 보존한다.

- 원시 request record와 Docker·Nginx·Backend·MySQL raw log는 커밋하거나 장기 보존하지 않는다.
- 원시 record는 process memory에서 집계 후 폐기한다.
- 실패 진단용 OS temp 파일은 민감정보 점검, 집계 후 삭제와 비커밋 조건을 모두 충족할 때만 허용한다.
- 임시 파일의 내용과 경로는 보고서나 저장소에 복사하지 않는다.

## D6-A 승인: 현재 도구 최소안

- PowerShell 5.1 이상의 `Stopwatch`, `Invoke-WebRequest`, `Invoke-RestMethod`, `WebRequestSession`
- Docker와 Docker Compose CLI의 `up`, `inspect`, `stats`, 제한된 `logs` tail
- 기존 local-integration Compose, QA fixture와 smoke
- 저장소 변경 없는 일회성 기준선 측정

repository benchmark script, k6, Actuator, Micrometer, Prometheus, Grafana와 OpenTelemetry는 승인하지 않는다. 재사용 script 필요 여부는 일회성 결과 검토 후 별도 작업에서 결정한다.

## 선택되지 않은 대안과 미승인 항목

- PERF-001의 선택되지 않은 모든 대안: `Deferred` 또는 미승인
- browser rendering, hydration, Web Vitals
- SLO, latency 목표, 오류 예산, regression threshold
- concurrency 2 이상, arrival rate, 장시간·고부하 실행
- repository benchmark script와 신규 dependency·외부 service
- Nginx·Compose·Dockerfile·CI·Backend·Frontend 변경
- metric·trace·dashboard·alert stack
- 성능 병목 확정과 최적화
- FOUNDATION-006의 GET 오류 재시도 keyboard 접근성과 session 만료 미실행 위험 해소

## 다음 측정 작업의 변경 불가 조건

다음 Platform/SRE 작업은 D1-A~D6-A의 cohort, 횟수, 순서, concurrency, warm-up, reset·seed, 통계 계산, 실패 처리와 보존 정책을 임의로 변경하지 않는다. 조건 변경이 필요하면 측정을 시작하거나 계속하지 않고 사용자/Tech Lead에게 별도 승인을 요청한다.

## 중단 조건

- 실제 제품 코드, 테스트, Compose, Nginx, Dockerfile 또는 CI 변경이 필요함
- benchmark script, 신규 dependency·도구·외부 service·Secret이 필요함
- QA 회원 외 데이터 삭제, volume 삭제 또는 iteration별 reset이 필요함
- 원시 record·log나 민감정보를 커밋·장기 보존해야 함
- 승인된 횟수, cohort, 통계, 데이터 경계 또는 증거 정책을 바꿔야 함
- 결과 개선을 위해 표본을 제거·대체하거나 조건을 변경해야 함
- SLO·threshold·병목 또는 최적화를 함께 결정해야 함

## 승인 결과

D1-A~D6-A는 `Approved`다. 이 승인은 동일 조건의 일회성 로컬 기준선 측정을 허용하지만 측정 결과, 성능 목표, 병목 가설, 최적화와 확장 도구를 미리 승인하지 않는다.
