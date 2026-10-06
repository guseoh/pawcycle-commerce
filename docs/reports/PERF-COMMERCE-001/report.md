# PERF-COMMERCE-001 — Checkout deadlock correction KEEP / Hikari16 REVERT

- Slice: 기존 `PERF-COMMERCE-001` continuation (새 Slice 아님)
- 기준 main: `a3787d8cd0437b40c4aa6817996d70d2168610b7`
- Branch: `codex/perf-commerce-001`
- 실행 범위: `infra/local-integration` 전용
- correctness decision: **KEEP** — missing-row idempotency replay 조회의 `PESSIMISTIC_WRITE` 제거가 실제 MySQL 동시성 회귀와 load에서 deadlock/status error 재발 없이 검증됨.
- capacity result: **20 RPS capacity-failure candidate** — 120 VU 측정에서 status error 0이었지만 dropped 18로 valid Before는 확보하지 못함.
- tuning decision: **REVERT (Hikari max 10 → 16 후보만 해당)** — 후보 After는 dropped 35와 fixture-to-request 불일치 77건으로 invalid이며, 기본 max 10으로 복구함. 이 REVERT는 deadlock correctness correction을 되돌린다는 의미가 아님.
- Production/Cloud 실행 및 Production Verified 근거 없음.

## 초기 STOP 근거

수정 후 50 VU correctness warm-up은 30초 동안 601 Checkout을 처리했고 status error 0, dropped 0으로 통과했다. 이어진 120초 measurement는 2,367 Checkout, status error 0, k6 `http_req_failed` rate 0이었으나 dropped iteration 34건으로 종료 코드 99를 반환했다. 따라서 이 실행은 performance baseline으로 사용할 수 없다. 사용자가 정한 invalid-baseline STOP을 적용해 재실행, After, 성능 코드 변경을 중단했다.

20 VU로 시작한 수정 후 warm-up에서는 status error 0이었지만 160 iteration이 dropped됐다. 그 실행도 결과에 포함하지 않고 `evidence/warmup-dropped-20-vus.json`으로 보존했다. 응답시간 tail에 비해 VU 상한이 부족하다고 판단해 50 VU 전용 pool로 조정한 뒤 한 번 더 실행했다. 50 VU warm-up은 통과했지만 120초 measurement에서 34건이 다시 dropped되어 중단했다. 추가 재시도는 하지 않는다.

최초 clean-main warm-up failure는 별도 진단이다. `2026-10-06T05:47:13.620Z`–`2026-10-06T05:52:07.780Z`에 HTTP 200 219건, 500 48건, Hikari pending max 7이 발생했고 measurement는 시작하지 않았다. 그 evidence는 삭제하지 않았다.

## Deadlock graph와 원인

실제 local MySQL 8.4.10 / `REPEATABLE-READ`에서 서로 다른 synthetic member와 서로 다른 누락 idempotency key로 production-shaped `SELECT ... FOR UPDATE`와 결과 INSERT를 재현했다. `SHOW ENGINE INNODB STATUS`, `performance_schema.data_locks`, `data_lock_waits`에서 다음을 확인했다.

- Transaction A와 B는 각자 `checkout_idempotency_results`의 `PRIMARY`에 table IX와 supremum pseudo-record의 record X lock을 보유했다. 해당 레코드는 비어 있는 key 공간의 supremum gap이다.
- 각 transaction의 idempotency lookup은 서로 다른 `(member_id, idempotency_key)`를 찾았지만, 누락 row locking read가 같은 PRIMARY gap을 잠갔다.
- INSERT 단계에서 A는 같은 PRIMARY gap에 `X,INSERT_INTENTION`을 요청해 B가 보유한 `X` supremum lock을 기다렸고, B도 A가 보유한 lock을 기다렸다. 즉 양쪽 모두 table IX 및 supremum `X`를 보유하고 서로의 insert-intention 요청이 대기하는 cycle이었다. InnoDB가 한 transaction을 victim으로 골라 rollback하고 다른 쪽을 진행시켰다.

따라서 원인은 서로 다른 회원 간의 member lock 경합이 아니라, 누락 idempotency row에 대한 pessimistic locking lookup이 만든 gap lock과 뒤이은 insert-intention lock의 cycle이다. 기존 failure의 48개 HTTP 500 및 실패 idempotency INSERT digest와 연결된다. sanitized lock summary만 저장했으며 transaction/회원/주문/키 ID와 raw DB dump는 보관하지 않았다.

## Correctness correction과 회귀 검증

`CheckoutIdempotencyRepository`의 replay 조회에서 `PESSIMISTIC_WRITE`를 제거하고 `findResult`로 이름을 바꿨으며 `CheckoutPersistenceAdapter`가 이를 호출하게 했다. `CheckoutApplicationService.checkout()` 선행 흐름은 계속 `cart.lockForAdd(memberId)` → member row `FOR UPDATE` → idempotency replay lookup이다. 같은 회원 Checkout 직렬화, key unique constraint, transaction 경계, idempotency replay 계약은 유지된다. isolation, schema/index, retry, timeout, pool 설정은 변경하지 않았다.

새 MySQL integration regression은 같은 회원·같은 key 동시 Checkout이 Order/Payment/idempotency result 및 inventory reservation을 한 번만 생성하고 두 응답이 같은 결과를 가리키는지, 서로 다른 회원 4개의 독립 SKU Checkout이 deadlock 없이 완료되는지 검증한다. 기존 replay, conflict, ownership, reservation, rollback, legacy fail-closed 테스트를 포함한 `CheckoutIdempotencyIntegrationTests`와 `CommercePurchaseIntegrationTests`가 실제 local Compose MySQL에서 모두 `BUILD SUCCESSFUL`이었다. 수정된 Backend `bootJar`도 성공했다.

## 환경과 workload/fixture

Docker Desktop의 기존 `infra/local-integration` stack만 사용했다. MySQL/Redis/Backend/Prometheus/Grafana는 기존 volume과 설정을 유지했다. Backend는 수정 branch 코드로 한 번 재빌드했으며 127.0.0.1:8080 loopback publish만 사용했다. Prometheus scrape interval은 15초, dashboard UID는 `pawcycle-local-observability`, Grafana anonymous Viewer다. Toss test enablement는 false다. Production/OCI/Cloud/외부 Provider/Grafana/credential에 접근하지 않았다.

Workload는 `POST /api/checkout`, target 20 RPS, warm-up 30초, measurement 120초, 예상 status 200, coupon 없음, cart item 1개, unique `Idempotency-Key`, Toss confirm 없음이다. 50 VU에 각기 고유 member/cart/address/Product/SKU/Inventory를 할당했다. Product는 PUBLIC, SKU는 ACTIVE, stock은 SKU별 10,000, 현재 가격은 19,900원이다. fixture 수가 요청 동시성을 수용하며 hot SKU 실험을 하지 않는다. QA bootstrap의 password hash는 DB 안에서 복사했고 plaintext/hash/cookie/token을 source/log/evidence에 저장하지 않았다.

각 시도의 fixture namespace만 transaction으로 만들고 정확한 marker email/catalog key/SKU code 범위의 Checkout 하위 row와 Brand/Category/Product/SKU/Inventory를 FK 순서로 제거했다. 마지막 50-member measurement 후 `before-cleanup.json`에서 cleanup boundary와 잔여 namespace 0 검증을 확인했다. 다른 QA data/reset/schema/volume은 변경하지 않았다.

첫 20 VU 시도의 160 dropped를 보존한 뒤 `POOL_SIZE=50`, k6 preAllocated/max VUs 50으로 맞췄다. 이 50-member 설정은 이번 Slice의 fixture/workload 조건이며 기존 Phase 8-D historical profile은 바꾸지 않았다.

## Warm-up 및 measurement 집계

| 지표 | 20 VU 수정 후 warm-up (실패) | 50 VU correctness warm-up (통과) | 50 VU 120초 measurement (무효) |
| --- | ---: | ---: | ---: |
| Checkout request count | 441 | 601 | 2,367 |
| k6 Checkout rate | 10.734 RPS | 16.677 RPS* | 18.611 RPS* |
| p50 | 526.17 ms | 50.02 ms | 50.23 ms |
| p95 | 2,025.58 ms | 488.44 ms | 650.16 ms |
| p99 | 6,573.99 ms | 711.71 ms | 3,723.83 ms |
| max | 7,011.25 ms | 915.92 ms | 4,862.24 ms |
| expected status errors | 0 | 0 | 0 |
| dropped iterations | 160 | 0 | 34 |
| max VUs | 20 | 50 | 50 |

\* k6 summary `rate`는 summary lifetime 기준이다. 유효 후보 measurement의 exact UTC 구간 길이는 120.164초이고, Checkout count/구간은 19.698 RPS다. 다만 dropped 34로 gate를 실패했으므로 이 값과 latency는 진단값이며 valid Before 수치로 판정하지 않는다. measurement 중 Backend restart 0, OOM false, fixture에는 warm-up+measurement 합계 2,968개 order/payment/reservation이 생성된 뒤 전용 namespace를 정리했다.

## Grafana / Prometheus 진단 evidence

유효 Before가 아니라 dropped가 발생한 measurement 진단 구간이다. Grafana 및 Prometheus는 같은 exact UTC range를 사용했다.

- UTC: `2026-10-06T06:33:31.656Z`–`2026-10-06T06:35:31.820Z`
- Dashboard UID: `pawcycle-local-observability`; query range step 15초, 9 samples
- `evidence/before-dropped-prometheus.json`: dashboard PromQL, panel ID/title, query_range series와 통계
- 실제 브라우저 screenshot: `before-dropped-grafana-runtime.png` (HTTP, heap, GC/threads, CPU), `before-dropped-grafana-hikari.png` 및 `before-dropped-grafana-hikari-detail.png` (Hikari), `before-dropped-grafana-scrape.png` (Backend availability), `before-dropped-grafana-latency-error.png` (latency percentiles/error ratio)
- 화면의 UTC time selector도 동일 구간을 표시했다. screenshot은 실제 local Grafana 브라우저 capture다.
- 확인 panel: 1 HTTP requests and errors, 2 HTTP latency, 3 JVM heap, 4 JVM GC and threads, 5 Process and system CPU, 6 Hikari connections, 13 Backend scrape availability, 17 HTTP latency percentiles, 18 HTTP request error ratio.

| Prometheus 항목 | 구간 통계 |
| --- | ---: |
| `/api/checkout` 1m rate | mean 18.381, max 20.009 RPS |
| `/api/checkout` p95 / p99 1m histogram | mean 481.85 / 760.05 ms; max 724.78 / 1,264.87 ms |
| 4xx / 5xx | 해당 status 시계열 없음; k6 expected status error 0 |
| Process CPU / system CPU | mean 23.56% / 55.22%; max 35.30% / 64.53% |
| JVM heap used | mean 136.4 MiB; max 182.2 MiB; committed 200 MiB; max 728 MiB |
| GC pause sum/count delta | 0.975 s / 11 pauses; 5m rolling pause rate mean 0.0363 s/s |
| Live / peak threads | max 63 / 64 |
| Hikari active / idle / pending / max | max 10 / 10 / 15 / 10; mean 2.89 / 7.11 / 1.89 |
| Hikari acquire count / sum delta | 2,393 / 45.025 s; average 18.82 ms per acquire |
| Backend scrape availability | 9/9 samples were 1 |

Hikari는 10개 pool 상한에 닿고 pending이 15까지 올라간 sample이 있어 pool queue가 dropped와 연관될 가능성이 있다. 이는 인과를 입증하지 않으며, valid Before가 아니므로 pool tuning을 적용하지 않았다. HTTP request error ratio panel에는 error series가 없어 Grafana에 `No data`로 표시된다. CPU/heap/GC 수치는 15초 scrape 표본이라 순간 peak를 놓칠 수 있다.

## MySQL performance_schema 진단 delta

120초 exact interval의 DB-wide statement digest before/after delta다. Checkout 전용으로 각 statement에 tag할 수 없으므로 count가 요청과 일치하지 않는 일부 background/local statement는 Checkout 전용 비용으로 단정하지 않는다. digest collector의 performance_schema SQL은 집계에서 제외했다.

| SQL digest 형태 | 실행 | total / 평균 | SUM_LOCK_TIME | rows examined / affected |
| --- | ---: | ---: | ---: | ---: |
| `COMMIT` | 2,389 | 100.258 s / 41.967 ms | 0 ms | 0 / 0 |
| idempotency result `INSERT` | 2,367 | 8.537 s / 3.607 ms | 6.018 ms | 0 / 2,366 |
| order `INSERT` | 2,367 | 6.761 s / 2.856 ms | 9.366 ms | 0 / 2,367 |
| payment `INSERT` | 2,367 | 6.503 s / 2.747 ms | 5.344 ms | 0 / 2,367 |
| inventory movement `INSERT` | 2,367 | 4.659 s / 1.968 ms | 4.834 ms | 0 / 2,367 |
| order item `INSERT` | 2,367 | 3.416 s / 1.443 ms | 4.845 ms | 0 / 2,367 |
| inventory reserve `UPDATE` | 2,366 | 2.293 s / 0.969 ms | 5.659 ms | 2,367 / 2,367 |
| idempotency replay `SELECT` | 2,367 | 1.603 s / 0.677 ms | 5.298 ms | 2,367 / 0 |

Cart item locking read, member `FOR UPDATE`, address lookup, SKU locking read, purchasability check, inventory lookup도 각각 약 0.52–0.59 ms 평균 digest latency였다. `Innodb_row_lock_waits`, row lock time/max 증가와 digest loss는 0이었다. `COMMIT`의 누적/평균 latency 및 Hikari pending은 관찰된 후보 지표지만, invalid baseline으로 원인을 확정하지 않았다. DML `EXPLAIN ANALYZE`나 추정 기반 query 최적화는 하지 않았다.

## 변경, 검증, 남은 상태

변경 파일은 idempotency correctness correction 및 두 MySQL regression, Checkout k6/fixture runner와 그 regression, 기존 report 및 evidence다. runner Before gate는 pinned main SHA와 task branch를 확인하며, 이 continuation에서 승인된 correctness correction 세 Backend 파일만 허용하고 다른 Backend 변경/untracked Backend 파일/main drift를 거부한다.

- `python infra/performance/k6/test_checkout_runner.py`: 8 tests 통과
- k6 JS `node --input-type=module --check`: 통과
- `git diff --check`: 통과
- 수정된 source로 local Docker `bootJar`: 통과
- 실제 local MySQL Checkout/payment integration 두 클래스: `BUILD SUCCESSFUL`
- 50 VU correctness warm-up: status error 0 / dropped 0
- 120초 measurement: dropped 34; valid Before 아님. After/최적화/최종 KEEP·REVERT·NO_CHANGE 판정 없음.

이 시점의 50 VU 실행은 invalid-baseline STOP으로 보존한다. 이후 같은 Slice에서 120 VU capacity candidate와 Hikari16 단일 후보까지 추가 검증했으며, 최종 결과는 아래 continuation 절에 기록한다. 기존 failure/measurement/Grafana/Prometheus evidence는 삭제하지 않는다. 프로덕션 실행이나 merge는 없다.

## 120 VU continuation 및 pool 변경 판정 (2026-10-06)

이 절은 사용자가 같은 Slice의 재개 조건을 지정한 뒤 수행한 후속 실행을 기록한다. 위 50 VU STOP, deadlock graph, 기존 report 내용과 evidence 파일은 보존했다. 기준 `HEAD`와 fetch한 `origin/main`은 계속 `a3787d8cd0437b40c4aa6817996d70d2168610b7`이며 branch는 `codex/perf-commerce-001`이다. 측정 시 Backend에는 기존 idempotency correctness correction만 적용돼 있었다.

### 50 VU dropped 원인과 120 VU workload

기존 50 VU 실행은 `preAllocatedVUs=50`, `maxVUs=50`, 관측된 max VU 50, dropped 34였다. k6의 constant-arrival-rate executor는 예정 시각에 시작할 free VU가 없으면 iteration을 drop한다. 따라서 50 VU ceiling은 해당 dropped의 직접적인 load-generator scheduling 원인이었다. 느린 Checkout iteration이 VU를 점유한 상위 원인은 별도 SUT evidence로 판단했다. 근거: [k6 dropped iterations 문서](https://grafana.com/docs/k6/latest/using-k6/scenarios/concepts/dropped-iterations/).

이후 attempt `v120`은 전용 synthetic identity/fixture pool 120개, `preAllocatedVUs=120`, `maxVUs=120`, target 20 RPS로 실행했다. 각 VU에는 별도 member/cart/address/Product/SKU/Inventory와 stock 10,000을 할당했다. Coupon은 없고 cart item은 1개, 요청마다 unique Idempotency-Key를 썼으며 실제 Toss confirm은 실행하지 않았다. QA bootstrap hash를 DB 안에서 재사용했고 plaintext password, cookie/token 및 raw identity/order/payment/address/SKU ID는 evidence에 저장하지 않았다. Fixture는 run marker namespace만 exact cleanup했으며 cleanup 검증을 통과했다.

30초 correctness warm-up은 504 Checkout, status error 0, dropped 96이었다. MySQL lock-wait delta는 0, Backend restart/OOM은 없었고, MySQL error log에서 warm-up/measurement UTC 구간의 deadlock 문구는 0건이었다. correctness 기준은 통과했지만 warm-up부터 120 VU ceiling에서 예정 load를 온전히 전달하지 못한 점을 따로 기록했다.

### 120 VU Before와 병목 판정

Before measurement는 **2026-10-06T07:44:10.832Z–2026-10-06T07:46:11.680Z** (120.848초)다. 2,390 request, 실제 구간 기준 19.777 RPS, k6 expected-status error 0, dropped 18, max VU 120이었다. p50 84.48ms, p95 5,324.81ms, p99 7,038.39ms, max 14,813.08ms였다. Backend image/start time/restart count는 측정 중 변하지 않았고 OOM도 없었다. Fixture 전후 row 수는 2,894개 warm-up+measurement 성공 Checkout과 일치했다. 따라서 이 결과는 **valid Before가 아니라 20 RPS capacity-failure candidate**다. VU pool을 더 키우지 않았다.

Grafana UID `pawcycle-local-observability`와 Prometheus range query는 같은 exact UTC 범위, 15초 step, Grafana의 기존 15초 scrape를 사용했다. 9개 Backend 표본 모두 `up{job="pawcycle-backend"}=1`이었다. HTTP request-error panel은 error series가 없어 Grafana에 `No data`로 표시됐고, k6 expected-status error rate는 0이었다.

Hikari/Checkout 시간 관계는 pool max10에서 직접 관찰됐다. `07:44:55.832Z` scrape에서 active 10, idle 0, pending 108이었고 Checkout 1분 histogram p95/p99는 각각 4.73초/6.20초였다. 같은 약 15초 scrape interval의 Hikari acquire는 205회에 합계 350.175초, 평균 1,708.2ms였고 connection usage는 203회에 합계 134.446초, 평균 hold 662.3ms였다. 이 구간을 포함한 Performance Schema COMMIT sample 평균은 49.92ms로 measurement 전체 COMMIT 평균 67.21ms보다 낮았다. 표본 시각이 완전히 일치하지 않는 점은 남지만, queue/acquire 급증이 COMMIT 지연 급증과 함께 움직이지는 않았다. 주된 관측 병목은 COMMIT 단독 비용보다 Hikari connection acquisition 대기다.

전체 구간 Hikari acquire는 2,258회/1,201.220초, 평균 531.98ms였다. usage는 2,257회/438.864초, 평균 connection hold 194.45ms였다. active 평균/최대 2.78/10, idle 평균/최대 7.22/10, pending 평균/최대 12/108, max 10이었다. process CPU 평균/최대 29.34%/49.12%, system CPU 71.13%/98.96%였다. Heap used 평균/최대 172.5/224.6 MB, GC 12회/합계 572ms/평균 47.67ms, live threads 평균/최대 141.9/143, peak 143이었다.

Performance Schema COMMIT digest는 2,412회, 합계 162.107초, 평균 67.209ms였다. 주문 INSERT 2,390회/평균 5.641ms, payment INSERT 2,390회/2.984ms, inventory movement INSERT 2,390회/2.904ms, idempotency INSERT 2,390회/2.543ms, order item INSERT 2,390회/1.855ms, inventory reserve UPDATE 2,390회/1.844ms였다. `Innodb_row_lock_waits`, lock time/max, digest loss delta는 모두 0이었다. local Compose healthcheck가 `/api/products`를 호출하므로 해당 catalog SELECT digest는 Checkout 비용으로 귀속하지 않았다.

### 단일 pool16 후보와 After

앞선 interval에서 20 RPS × 662.3ms observed hold는 약 13.2개 동시 connection 점유량이었다. 이를 근거로 local-integration에 한해서 maximum pool size를 10에서 16으로 한 가지만 시험했다. 임시 16 설정은 현재 Compose에서 제거했고, 기본 max10 및 loopback binding을 복구했다. Production/config/credential은 접근하지 않았다.

첫 After preflight 호출은 기존 loopback overlay를 Compose에 포함하지 않아 Backend port gate에서 측정 전 중단됐다. Fixture 생성이나 workload/evidence 생성은 없었다. 기존 overlay를 다시 적용해 `127.0.0.1:8080`을 복구한 뒤 실제 After attempt는 한 번만 수행했다.

After range는 **2026-10-06T07:58:57.901Z–2026-10-06T08:00:57.978Z** (120.077초)다. 2,366 Checkout, 구간 기준 19.704 RPS, status error 0, dropped 35, max VU 120, p50 315.50ms, p95 5,450.41ms, p99 9,665.70ms, max 12,667.02ms였다. fixture에는 2,803 orders/payments/reservations가 있었지만 warm-up+measurement request metric 합계는 2,726으로 77건 불일치했다. cleanup boundary 자체는 exact marker 기준으로 통과했으나 fixture-to-request gate 때문에 이 After는 **invalid_measurement_with_drops**이며 유효한 Before/After 비교로 승인하지 않았다.

진단 참고로 After Hikari max16, active 평균/최대 6.11/16, idle 평균/최대 9.89/16, pending 평균/최대 6.56/55였다. acquire 2,203회/1,400.007초, 평균 635.50ms, usage 2,200회/872.170초, 평균 hold 396.44ms였다. process CPU 평균/최대 44.77%/55.44%, system CPU 91.29%/99.58%, heap used 평균/최대 145.9/171.0 MB, GC 13회/합계 689ms/평균 53.0ms, live threads 평균/최대 141.6/142, peak 142였다. COMMIT 2,384회/합계 146.201초/평균 61.326ms, InnoDB row lock wait/time/max와 digest loss는 0, Backend scrape는 9개 표본 모두 1이었다.

측정 지표상 pending peak는 낮아졌지만 계속 55였고, acquire/hold 평균, Checkout 평균·p95·p99, dropped, process/system CPU는 나빠졌다. 게다가 fixture consistency gate도 실패해 pool16은 KEEP 근거가 없다. 이 단일 후보 설정을 되돌렸으며 추가 튜닝·VU 확대·재측정은 하지 않았다. 이 Slice의 tuning 판정은 **REVERT**다. 기존 idempotency correctness correction은 그대로 남겼다.

### Deadlock 관측 한계와 보존 파일

이 실행 당시 `SHOW GLOBAL STATUS` 질의에서 `Innodb_deadlocks` 항목이 반환되지 않아 warm-up/measurement Performance Schema JSON에는 deadlock counter delta가 없다. 사후 확인한 `INFORMATION_SCHEMA.INNODB_METRICS.lock_deadlocks` 값 49는 이전 실행까지 포함한 cumulative count라 이번 구간 delta로 사용할 수 없다. k6 status error 0, row-lock wait/time delta 0, 두 workload 구간 MySQL deadlock log match 0건은 남겼지만, 이를 counter delta가 검증됐다고 표현하지 않는다. Runner는 앞으로 `INNODB_METRICS.lock_deadlocks` before/after delta가 unavailable이면 correctness warm-up을 fail-closed 하도록 보강했다.

Grafana 브라우저 screenshot은 실제로 저장했다. Before: `before-v120-grafana-runtime.jpg`, `before-v120-grafana-hikari-detail.jpg`, `before-v120-grafana-latency-errors.jpg`. After: `after-hikari16-grafana-runtime.jpg`, `after-hikari16-grafana-hikari-detail.jpg`, `after-hikari16-grafana-latency-errors.jpg`. 각 화면은 위 exact UTC range를 표시한다. Panel 1/2/3/4/5/6/13/17/18과 동일 PromQL query_range series/stats 및 Hikari acquire/usage, GC counter delta는 각각 `before-v120-prometheus.json`, `after-hikari16-prometheus.json`에 보존했다. k6 요약, MySQL digest, lock/transaction snapshot, fixture gate는 attempt별 `*-measurement.json`, warm-up은 `*-warmup.json`, exact cleanup은 `*-cleanup.json`에 있다.

검증: `python infra/performance/k6/test_checkout_runner.py` 12 tests 통과, Python compile 통과, `git diff --check` 통과. 기존 correctness correction에 대한 BootJar 및 실제 MySQL Checkout/idempotency integration 검증도 앞선 실행에서 통과했고, 이 continuation에서는 Backend 제품 성능 코드를 수정하지 않았다. 남은 한계는 measurement dropped, After fixture count mismatch, 같은 host에 load generator와 local runtime가 있어 system CPU에 외부 부하가 섞일 수 있음, 그리고 이번 실행의 deadlock counter baseline 부재다. local pool은 기본 max10, Backend healthy, `127.0.0.1:8080` loopback 상태로 복구했다.
