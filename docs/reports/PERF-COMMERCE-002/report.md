# PERF-COMMERCE-002 — local Checkout capacity / NO_CHANGE · INCONCLUSIVE

- 작업 ID: PERF-COMMERCE-002
- 작업 등급: 고위험
- 실행 구분: 저장소 변경
- 로컬 실행: infra/local-integration 전용 performance execution
- Tracking: [Issue #333](https://github.com/guseoh/pawcycle-commerce/issues/333)
- 기준 main / 측정 Backend source: cb4e9b46031b42c46f2f4a4dfbab0871acb82b4e
- Branch: codex/perf-commerce-002
- 결과: local measurement와 cleanup 완료. 제품 변경 NO_CHANGE, 원인 판정 INCONCLUSIVE.

## 목적과 Delta

120 VU ceiling을 유지하고 5 → 10 → 15 → 20 RPS를 한 번씩 측정해 stable capacity 하한과 resource attribution을 확보한다. 시작 시 git fetch origin 후 최신 origin/main은 Issue 작성 기준과 동일해 main drift가 없었다. PERF-COMMERCE-001의 idempotency correction, 120 dedicated fixture, collector, exact cleanup을 재사용했다. 변경은 stage 제어, gates, Docker/MySQL resource 수집과 파생 통계뿐이다.

제품 코드, Hikari max10, thread/JVM/SQL/schema/index 설정은 수정하지 않았다. Production/OCI/Cloud, 실제 Toss confirm, Redis/cache/async/queue 성능 변경, 120 초과 VU는 실행하지 않았다.

## 주요 결과와 capacity 판정

- 마지막 stable stage: **20 RPS**.
- 첫 hard-failure stage: **관측 없음 (20 RPS까지)**.
- Stable capacity bracket: 측정점 5/10/15/20 RPS가 모두 stable. **C_stable ≥ 20 RPS**, 상한은 이번 ladder 범위 밖이라 미확인이다. capacity=20 또는 20 초과 안정성을 주장하지 않는다.
- Saturation onset: **관측 없음 (5–20 RPS)**. 모든 stage의 pending peak와 연속 positive 표본 수가 0이며 acquire/hold, latency, COMMIT 또는 CPU의 인접 stage 악화가 없다.
- Root-cause category: **5. Mixed / INCONCLUSIVE**. 포화가 재현되지 않아 primary cause를 귀속할 수 없다는 뜻이며, 복합 병목을 입증했다는 뜻은 아니다.
- Decision: **NO_CHANGE / INCONCLUSIVE**. 최소 원인과 변경 하나가 확인되지 않아 제품 tuning 및 After를 실행하지 않았다. KEEP/REVERT 대상으로 적용한 제품 후보는 없다.

## 환경 fingerprint와 workload

Docker Desktop 29.8.0, Linux/x86_64, 12 CPU, 7.71 GiB. k6: k6.exe v2.2.0 (commit/00a9a1b7f5, go1.26.5, windows/amd64). MySQL: 8.4.10, REPEATABLE-READ, innodb_flush_log_at_trx_commit=1, sync_binlog=1, performance_schema=1, digest slots=10000. Backend JRE는 repository Dockerfile의 Temurin 25.0.3이다. image SHA, kernel, 시작 시각, service image tag는 [environment.json](evidence/environment.json)에 보존했다.

현재 Docker daemon에는 기존 PawCycle image/container/volume이 없었다. 저장소의 local Compose와 observability 설정으로 새 local stack을 시작하고 Backend를 최신 main 코드로 build했다. 별도 limit 없이 Backend/MySQL memory_bytes=0, nano_cpus=0, pids=null을 유지했다. 8080은 다른 로컬 프로젝트가 사용하고 있어 Backend만 127.0.0.1:18080에 publish했다. 다른 프로젝트는 중단하거나 변경하지 않았다. JVM/Hikari/Toss 설정은 기존 기본값이고 Hikari max10, Toss test=false, reset_subscriptions=false를 확인했다. Prometheus는 15초 scrape, Grafana UID는 pawcycle-local-observability, anonymous Viewer다. Alertmanager/외부 webhook service는 시작하지 않았다. port/Grafana service의 임시 local override는 저장소 밖에 두었다.

각 stage는 같은 Backend/MySQL image, 시작 시각, resource limits를 확인했다. 같은 process/JVM을 유지한 오름차순 실행이며 각 stage에 동일 모양의 새 dedicated fixture를 생성했다. 120 member/cart/address/Product/SKU/Inventory, VU별 독립 SKU, SKU stock 10000, cart item 1개, coupon 없음, unique Idempotency-Key, POST /api/checkout, expected status 200이다. preAllocatedVUs=maxVUs=120, warm-up 30s, measurement 120s, gracefulStop 5s를 유지했다. login과 fixture 생성은 측정 밖에서 수행하고 stage별 exact cleanup 뒤 다음 stage를 시작했다. 기존 pc001 marker 형식과 cleanup boundary를 그대로 재사용했다.

이 환경을 PERF-COMMERCE-001의 runtime/host와 동등하다고 입증하지 않았으므로 이전 20 RPS dropped 18과의 차이를 코드 개선 효과로 주장하지 않는다.

## Stage table

| Target RPS | Actual RPS | Requests | p50 ms | p95 ms | p99 ms | max ms | Errors / dropped | 판정 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 5 | 5.007 | 601 | 35.46 | 98.39 | 207.50 | 312.93 | 0 / 0 | stable |
| 10 | 10.000 | 1200 | 27.56 | 43.40 | 71.52 | 217.62 | 0 / 0 | stable |
| 15 | 15.003 | 1801 | 26.30 | 45.11 | 70.35 | 182.85 | 0 / 0 | stable |
| 20 | 20.000 | 2401 | 24.14 | 41.80 | 64.07 | 115.49 | 0 / 0 | stable |

Actual RPS는 Checkout request count / exact UTC measurement elapsed seconds다. k6 summary rate의 setup/teardown lifetime을 사용하지 않는다. p95/p99에는 별도 SLA를 만들지 않았다.

각 warm-up의 status error/dropped/deadlock/fixture/runtime/scrape/digest gate도 통과했다. warm-up은 correctness gate이고 98% capacity gate는 120초 measurement에 적용했다. 각 measurement의 Orders, READY Payments, RESERVE movements, reserved_quantity 증가가 해당 request count와 각각 일치했다. 누적 warm-up+measurement delta도 일치하고 cart/version/stock 계약을 보존했다. Backend/MySQL restart와 OOM은 없고 Backend up은 stage마다 9/9 표본에서 1이다. row-lock waits/time, deadlock, digest loss delta는 모두 0이다. stage scheduler는 하나라도 실패하면 다음 RPS를 호출하지 않는 회귀로 보호했다. 이번 ladder에는 hard failure가 없었으며 20 RPS 다음 부하를 추가하지 않았다.

## Stage별 resource attribution

CPU는 평균 / 최대다. process/system은 JVM gauge ×100, Docker CPU는 docker stats CPUPerc 원값이다. 두 지표의 CPU 분모/측정 방식이 같다고 가정하지 않는다.

| RPS | Process CPU % | System CPU % | Backend Docker CPU % | MySQL Docker CPU % | Backend memory MiB | MySQL memory MiB |
| --- | --- | --- | --- | --- | --- | --- |
| 5 | 1.80 / 2.77 | 24.81 / 28.48 | 26.91 / 80.42 | 9.88 / 20.88 | 505.45 / 510.90 | 484.79 / 486.10 |
| 10 | 1.81 / 2.95 | 24.75 / 31.95 | 20.09 / 59.59 | 14.03 / 23.79 | 536.50 / 543.80 | 500.77 / 505.00 |
| 15 | 1.54 / 2.10 | 24.50 / 32.13 | 21.60 / 56.28 | 18.72 / 35.32 | 546.06 / 546.90 | 525.17 / 545.20 |
| 20 | 1.81 / 2.55 | 25.23 / 32.06 | 24.37 / 45.54 | 21.63 / 30.82 | 547.36 / 547.50 | 552.79 / 555.30 |

Hikari acquire/usage는 raw counter delta의 sum/count 평균이다. usage는 connection hold이며 transaction의 각 application phase를 분해한 값은 아니다. active/idle/pending/max는 15초 scrape gauge여서 짧은 active burst를 놓칠 수 있다. pending=0은 모든 순간 queue가 없다는 증명은 아니지만, acquire 평균 0.026–0.108 ms 및 낮은 hold/latency와 함께 보면 sustained queueing/ceiling 근거가 없다.

| RPS | Acquire avg ms | Hold avg ms | Active peak | Idle min/max | Pending peak / 연속 positive | Pool max | Heap mean/max MiB | GC count / total s | Live / peak threads max |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 5 | 0.108217 | 36.775374 | 0.0 | 10/10 | 0/0 | 10 | 121.98 / 159.12 | 3/0.039 | 40/40 |
| 10 | 0.025752 | 24.066485 | 0.0 | 10/10 | 0/0 | 10 | 119.13 / 168.75 | 6/0.062 | 43/43 |
| 15 | 0.025641 | 22.246691 | 1.0 | 9/10 | 0/0 | 10 | 125.93 / 167.88 | 9/0.046 | 43/43 |
| 20 | 0.028178 | 21.502499 | 1.0 | 9/10 | 0/0 | 10 | 123.46 / 161.76 | 11/0.064 | 43/43 |

Heap committed/max, Hikari acquire/usage count와 sum, GC 평균과 series는 각 stage JSON에 보존했다. Backend와 system CPU에는 인접 stage의 포화 추세가 없다. MySQL CPU는 부하에 따라 완만히 증가하지만 ceiling 신호는 없다. GC total은 120초당 39–64 ms이며 heap/thread와 tail latency가 함께 악화되는 현상도 없다.

## MySQL evidence

COMMIT은 schema 전체의 global digest delta이며 Checkout 전용 평균이 아니다. healthcheck와 read transaction 등의 COMMIT을 포함한다. Checkout request count보다 각각 26/28/26/27건 많으므로 이 background 비용을 Checkout에 전량 귀속하지 않는다. Threads_connected/running은 gauge여서 before/after와 measurement 중 min/mean/max를 보존했다. collector 자신의 connection과 running thread도 포함한다.

| RPS | COMMIT count | Avg ms | Total ms | Threads_connected mean/max | Threads_running mean/max | Row lock waits/time ms | Deadlock / digest loss |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 5 | 627 | 12.180 | 7636.806 | 11.00 / 11.00 | 2.17 / 3.00 | 0 / 0 | 0 / 0 |
| 10 | 1228 | 8.042 | 9874.986 | 11.00 / 11.00 | 2.17 / 3.00 | 0 / 0 | 0 / 0 |
| 15 | 1827 | 9.324 | 17035.439 | 11.04 / 12.00 | 2.29 / 3.00 | 0 / 0 | 0 / 0 |
| 20 | 2428 | 7.085 | 17203.231 | 11.04 / 12.00 | 2.38 / 3.00 | 0 / 0 | 0 / 0 |

아래 표는 Checkout SQL digest의 평균 ms다. 17개 digest의 count/total/평균/lock 시간/rows examined/affected/sent/errors와 global transaction aggregate를 measurement JSON에 보존했다. 대부분의 digest count는 601/1200/1801/2401로 Checkout request count와 일치했다. 예외로 20 RPS의 order_items INSERT digest는 2400건으로 request 2401건보다 1건 적었다. fixture gate의 Orders/READY Payments/RESERVE/reserved_quantity 증가는 모두 2401건이다. digest count 차이의 원인은 미확인이며 raw row 부재로 이를 order-item 누락 또는 집계 경계 오차로 확정하지 않는다. catalog healthcheck, payment expiry scan, Micrometer aggregate와 collector SQL은 background로 분리했다. DML에 EXPLAIN ANALYZE를 실행하지 않았다.

| Digest | SQL boundary | 5 RPS | 10 RPS | 15 RPS | 20 RPS |
| --- | --- | --- | --- | --- | --- |
| 093741d0 | INSERT payments | 0.635 | 0.453 | 0.388 | 0.470 |
| 44fb1466 | INSERT checkout_idempotency_results | 0.555 | 0.382 | 0.390 | 0.308 |
| 53eae3ba | INSERT orders | 0.462 | 0.356 | 0.349 | 0.487 |
| c43b42fd | INSERT inventory_movements | 0.462 | 0.366 | 0.317 | 0.358 |
| 2ad118df | UPDATE inventories | 0.414 | 0.324 | 0.286 | 0.340 |
| ed28ee38 | INSERT order_items | 0.404 | 0.283 | 0.256 | 0.287 |
| 9cff5eb8 | SELECT members FOR UPDATE | 0.403 | 0.342 | 0.312 | 0.260 |
| 40a845f4 | UPDATE checkout_idempotency_results | 0.383 | 0.352 | 0.328 | 0.290 |
| 67f3aa00 | SELECT skus FOR UPDATE | 0.383 | 0.357 | 0.333 | 0.434 |
| f6619187 | SELECT carts FOR UPDATE | 0.363 | 0.301 | 0.260 | 0.225 |
| eb0645ea | SELECT checkout_idempotency_results | 0.342 | 0.279 | 0.245 | 0.254 |
| 1b7106b7 | SELECT skus | 0.336 | 0.314 | 0.301 | 0.389 |
| fce1c6ec | SELECT cart_items FOR UPDATE | 0.313 | 0.252 | 0.236 | 0.317 |
| 1e76c363 | SELECT member_addresses | 0.302 | 0.245 | 0.226 | 0.271 |
| 27b935c3 | SELECT payments | 0.298 | 0.269 | 0.278 | 0.248 |
| 814178f1 | SELECT checkout_idempotency_results composite key | 0.277 | 0.231 | 0.219 | 0.233 |
| 3c91306c | SELECT inventories | 0.267 | 0.219 | 0.200 | 0.238 |

Digest 분류는 수집 후 read-only 파생 계산에서 identity SELECT, composite idempotency key와 table-scoped DML을 기준으로 보강했다. MySQL 원본 counter, k6, Docker 표본, exact window를 보존하고 attribution만 재계산했다.

COMMIT 평균은 12.18 → 8.04 → 9.32 → 7.09 ms이고 connection hold는 36.78 → 24.07 → 22.25 → 21.50 ms다. Application/transaction hold dominated 또는 MySQL/commit dominated를 선택할 포화 근거가 없다. system CPU도 평균 24–25%, 최대 약 32%여서 Host/system CPU dominated를 선택하지 않는다. Hikari pending은 queueing point와 원인을 구분해야 하며 이번에는 sustained queueing 자체가 관측되지 않았다.

## Grafana exact UTC range와 evidence

| RPS | Start UTC | End UTC | 수치 evidence prefix |
| --- | --- | --- | --- |
| 5 | 2026-10-06T12:46:32.076Z | 2026-10-06T12:48:32.117Z | evidence/ladder-rps-5-* |
| 10 | 2026-10-06T12:50:38.811Z | 2026-10-06T12:52:38.816Z | evidence/ladder-rps-10-* |
| 15 | 2026-10-06T12:54:36.516Z | 2026-10-06T12:56:36.560Z | evidence/ladder-rps-15-* |
| 20 | 2026-10-06T12:58:36.668Z | 2026-10-06T13:00:36.719Z | evidence/ladder-rps-20-* |

모든 stage의 warm-up, k6 measurement, MySQL digest/lock delta, COMMIT timeline, Docker 표본, Prometheus query_range/series/counter delta와 cleanup JSON을 보존했다. Prometheus는 위 exact UTC range, 15s step, 1m rate interval을 사용했다. Checkout 전용 rate/p95/p99/4xx/5xx와 dashboard panel 1/2/3/4/5/6/13/17/18을 포함한다.

실제 local Grafana browser screenshot은 마지막 stable 20 RPS에만 저장했다.

- [전체 dashboard](evidence/last-stable-20-grafana.jpg): HTTP requests/errors, latency, heap, CPU, Hikari, Backend scrape, p95/p99, error ratio.
- [GC/threads 상세](evidence/last-stable-20-grafana-gc-threads.jpg): 같은 exact UTC의 panel 4. 전체 capture에서 blank였던 GC panel을 실제 visible view에서 확인했다.

URL from/to는 1791291516668/1791291636719이고 timezone은 UTC다. saturation onset과 hard-failure stage는 발생하지 않아 해당 screenshot 대상이 없다. Dashboard의 rolling HTTP histogram/rate를 k6의 Checkout exact latency와 동일하게 취급하지 않는다.

## NO_CHANGE와 복구 경계

이번 ladder에서 Backend/MySQL/host/pool 중 먼저 포화되는 경계가 드러나지 않았다. 명확한 원인과 최소 변경 하나의 조건이 성립하지 않아 제품 tuning, After 재측정, KEEP/REVERT 후보를 만들지 않았다. Hikari는 max10이다. 각 stage의 exact namespace cleanup과 잔여 0 검증을 마쳤고 공유 QA나 schema/volume reset을 실행하지 않았다. 저장소 변경의 복구는 일반 revert PR이다. local stack은 Hikari10/healthy/loopback 설정으로 유지했다.

## 검증 결과

- Backend Docker build/bootJar: 성공. 제품 source 수정 없음.
- `python infra/performance/k6/test_checkout_runner.py`: 18 tests 통과. 첫 실패 뒤 상위 stage 중단, 98% 경계, deadlock/scrape/fixture/runtime gates, resource sanitization, background digest 제외와 composite identity를 검증했다.
- `python -m py_compile infra/performance/k6/run-checkout.py infra/performance/k6/test_checkout_runner.py`: 성공.
- 실제 local k6 warm-up 4회 + measurement 4회: 지정 gate 모두 통과. Docker 각 24개 표본, collector error 0, COMMIT timeline error 0, cleanup 4회 verified.
- 보조 `k6 inspect` assertion: 처음에는 system env를 전달하지 않아 실패했다. 환경을 포함한 focused retry에서 CLI는 exit 0이었으나 옵션 equality assertion은 미통과였다. duration 직렬화의 정규화 형식과 expectation 차이로 추정한다. 같은 보조 검사를 반복하지 않고 실제 8회 k6 실행과 exact elapsed evidence를 주검증으로 사용했다.
- PR/report metadata와 diff 검증 결과는 final validation에 기록한다.
- 외부 AI reviewer와 CodeRabbit 직접 요청은 실행하지 않았다. Draft PR을 만들고 merge하지 않는다.

## 위험과 제한

- 허용된 최고 stage가 20 RPS여서 capacity 상한, saturation knee, first failure는 미확인이다. 성공한 측정을 반복하거나 더 높은 RPS/VU로 확대하지 않았다.
- stage당 120초 한 번의 관측으로 장시간 또는 Production capacity를 입증하지 않는다.
- fresh local volumes, 다른 local workload, Docker Desktop과 Windows k6의 공유 resource, JIT/cache warming 차이가 있다. JVM system CPU만으로 전체 Windows host 부하를 설명할 수 없다.
- Docker 수집은 명목 5초 start cadence이고 완료 timestamp 간격은 3.80–6.074초였다. command jitter와 15초 Prometheus scrape가 순간 peak를 놓칠 수 있다. 모든 완료 표본 간격이 엄격히 5초 이내라는 증거는 아니다.
- Hikari/GC delta는 비동기 scrape에 기반한 추정이며 Checkout request count와 일치하는 값이 아니다. MySQL before/after는 collector event 주변의 순차 snapshot으로 background SQL도 포함한다.
- 20 RPS order_items INSERT digest의 1건 count 차이는 미확인이다. 기존 fixture gate에는 order_items 별도 count가 없어 이 값의 실제 row와 digest 동등성은 검증하지 못했다. SQL 귀속의 추가 제한으로 남긴다.
- application phase timer, fsync/storage wait, Windows host trace는 추가하지 않았다. COMMIT/hold 비용 비율만으로 primary root cause를 확정하지 않는다.
- Secret/cookie/token/hash/raw DB row를 보관하지 않았다. normalized SQL digest의 column 이름은 실제 column 값이 아니다.
- Production/Cloud 실행과 Production Verified 근거는 없다. 후속 실험에는 별도 범위와 환경 동등성 확인이 필요하다.

## Final validation

- `scripts/validate-pr-body-encoding.py --from-stdin`: 통과 (명시적 UTF-8 입력).
- `scripts/validate-task-artifacts.py --from-stdin`: PERF-COMMERCE-002 / 고위험 / 저장소 변경 통과.
- `git diff --check`: 통과.
- JSON parse, stage gate 재계산, 네 stage runtime 동일성, fixture/cleanup, resource 수집 오류 부재, 인증 값 패턴 검사: 통과. order_items digest 1건 차이는 위 제한으로 명시했다.
