# PERF-COMMERCE-003 — shared-SKU checkout contention / KEEP

- 작업 ID: PERF-COMMERCE-003
- 작업 등급: 고위험
- 실행 구분: 저장소 변경
- Tracking: [Issue #335](https://github.com/guseoh/pawcycle-commerce/issues/335)
- Issue 작성 시 기준 main: `6ed3cdb09c89ad0db8570621992ebf817ba26497`
- 시작 시 fetch한 `origin/main`: 위 SHA와 일치, main drift 없음
- Branch: `codex/perf-commerce-003`
- 결과: baseline 10 RPS에서 재현한 shared-inventory 충돌을 한 곳에서 수정했다. 동일 경계의 유효한 After 10 RPS가 통과해 **KEEP**한다.

## 목적과 Delta

PERF-COMMERCE-002의 120-VU runner, exact fixture cleanup, local observability collector를 재사용해 독립 SKU control과 shared SKU ladder를 측정했다. 이번 harness Delta는 5 → 10 → 15 → 20 RPS 순차 stage, status별 오류 수집, fixture의 주문·결제·재고 invariant 비교, 5초 nominal Docker stats, MySQL/Prometheus stage JSON 보존이다. Hard gate 첫 실패 즉시 상위 RPS를 중단하고 VU는 120으로 고정했다.

각 stage는 동일한 로컬 Backend/MySQL/Redis/Prometheus runtime과 resource 설정, 120 dedicated member/cart/address, `preAllocatedVUs=maxVUs=120`, 30초 warm-up, 120초 measurement를 사용했다. Shared profile은 member별 cart만 다르고 Product/SKU/Inventory 한 행을 공유하며 stock 10,000, 수량 1, coupon 없음, unique idempotency key, 성공 기대 status 200이다. control은 SKU/Inventory를 분리했다. Toss confirm은 호출하지 않았다.

실행 대상은 local `infra/local-integration`뿐이다. Production/OCI/Cloud/실운영 DB, Redis/cache/async/queue 변경, Hikari/thread/JVM tuning, 근거 없는 SQL/index/schema 변경, 120 초과 VU는 사용하지 않았다. Hikari max는 10으로 유지했다.

## Stage 결과와 capacity 판정

Latency는 k6 `checkout_latency`의 응답 단위 p50/p95/p99/max다. Warm-up에서 hard gate가 깨지면 120초 measurement는 시작하지 않는다. Actual RPS는 exact UTC elapsed time 중 Checkout request 수로 계산한다.

| 구간 | Target / actual RPS | Checkout 수 | 오류 / dropped | p50 / p95 / p99 / max (ms) | 판정 |
| --- | ---: | ---: | ---: | ---: | --- |
| 독립 SKU control | 20 / 19.999 | 2,400 | 0 / 0 | 23.554 / 115.636 / 906.923 / 1,696.710 | stable, 120초 |
| Shared baseline | 5 / 4.9998 | 600 | 0 / 0 | 25.358 / 42.353 / 61.101 / 78.241 | stable, 120초 |
| Shared baseline | 10 / 9.9960 | 300 | 7 / 0 | 27.246 / 58.955 / 474.844 / 809.833 | **hard failure, 30초 warm-up에서 중단** |
| Shared baseline | 15 | — | — | — | 10 RPS 실패로 실행 안 함 |
| Shared baseline | 20 | — | — | — | 10 RPS 실패로 실행 안 함 |
| Shared After | 10 / 10.0036 | 1,201 | 0 / 0 | 36.457 / 84.356 / 134.077 / 282.287 | stable, 120초 |

Prometheus는 exact range에 15초 step과 1분 rolling rate를 적용했다. 아래 rate 평균은 k6 exact RPS가 아니라 scrape roll-up이다.

| Stage | Checkout rate 평균 / peak RPS | HTTP 평균 latency | Prometheus p95 / p99 평균 | HTTP error signal |
| --- | ---: | ---: | ---: | --- |
| Control 20 | 15.680 / 20.003 | 47.15 ms | 148.10 / 572.47 ms | 4xx series 없음, 5xx 0, error ratio 0 |
| Shared 5 | 4.062 / 5.001 | 22.05 ms | 39.32 / 57.94 ms | 4xx series 없음, 5xx 0, error ratio 0 |
| Shared 10 baseline warm-up | 1.808 / 4.378 (3 samples) | 28.59 ms | 50.49 / 314.85 ms | 4xx rate 표본 1개로 0, 5xx series 없음; exact 409 counter delta 7 |
| Shared 10 After | 7.986 / 10.001 | 41.27 ms | 79.69 / 126.76 ms | 4xx/5xx series 및 error-ratio sample 없음; k6 status errors 0 |

- Baseline 마지막 stable stage는 **5 RPS**, 첫 hard-failure stage는 **10 RPS**다. Baseline stable capacity 관측 bracket은 **5 ≤ C < 10 RPS**다.
- Saturation/contention onset과 첫 hard failure는 모두 baseline **10 RPS warm-up**에서 관측했다. 10 RPS에서 300건 중 7건이 409였고 inventory conditional update는 293건만 반영됐다. Prometheus의 exact warm-up 경계 checkout status counter도 409가 0 → 7로 증가했다. 해당 짧은 구간의 15초 Prometheus scrape로는 새 409 series의 `rate()` 표본이 하나뿐이라 dashboard 4xx rate가 0으로 보인다. 따라서 오류 수는 k6와 exact counter delta를 기준으로 판정했다.
- 유효한 경계 After에서는 warm-up 300건과 measurement 1,201건 모두 성공했고, measurement fixture delta가 1,201건과 일치했다. 후보의 입증된 용량은 **10 RPS 이상**이다. 15/20 RPS After를 실행하지 않았으므로 변경 후 상한은 미측정이며, 그 이상 capacity를 주장하지 않는다.
- 잘못된 Backend image를 대상으로 한 첫 After 시도는 [invalid attempt metadata](evidence/invalid-attempt-rps-10.json)에 기록하고 수치를 별도 보존했다. 컨테이너는 baseline image `149428…`를 계속 실행했지만 candidate image는 `54a000…`로 빌드된 사실을 확인해 이 시도를 비교에서 제외했다. Backend를 candidate image로 강제 재생성하고 image SHA와 healthy 상태를 확인한 다음에만 유효한 After 10 RPS를 한 번 측정했다.

Exact UTC measurement/warm-up range와 전체 k6 JSON은 evidence 파일에 있다. Baseline 10 RPS는 warm-up만 수행했으므로 Docker measurement resource 표본은 없다.

| Evidence | Exact UTC range |
| --- | --- |
| control measurement | `2026-10-06T13:46:23.841Z` – `2026-10-06T13:48:23.846Z` |
| shared 5 measurement | `2026-10-06T13:50:30.286Z` – `2026-10-06T13:52:30.290Z` |
| shared 10 baseline warm-up | `2026-10-06T13:53:34.404Z` – `2026-10-06T13:54:04.416Z` |
| shared 10 After warm-up | `2026-10-06T14:20:18.022Z` – `2026-10-06T14:20:48.036Z` |
| shared 10 After measurement | `2026-10-06T14:21:17.438Z` – `2026-10-06T14:23:17.495Z` |

## Resource attribution

Docker stats에는 measurement당 24개 Backend/MySQL 표본과 collector error 0개가 있다. nominal interval은 5초였고 실제 completion gap은 3.452–6.312초, median 약 5초였다. Docker CPU%는 여러 host CPU를 합산하므로 100%를 넘을 수 있다. Prometheus는 15초 scrape로 exact stage 구간을 조회했다.

| Stage | Backend process / system CPU 평균 | Backend / MySQL container CPU 평균 (peak) | Backend / MySQL memory 평균 | Hikari active peak / idle 평균 / pending peak / max | Acquire / connection hold 평균 | COMMIT 평균 (count, total) | MySQL threads C/R; row-lock waits/time; deadlock |
| --- | ---: | ---: | ---: | --- | ---: | --- | --- |
| Control 20 | 2.28% / 27.54% | 27.08% (192.19) / 43.91% (446.11) | 604.9 / 576.3 MiB | 1 / 9.56 / 0 / 10 | 8.506 / 38.122 ms | 21.078 ms (2,427; 51.156 s) | 11/2; 0 / 0 ms; 0 |
| Shared 5 | 0.39% / 24.22% | 6.19% (13.75) / 6.65% (11.35) | 606.8 / 582.4 MiB | 0 / 10 / 0 / 10 | 0.195 / 20.583 ms | 8.217 ms (626; 5.144 s) | 11/2; 0 / 0 ms; 0 |
| Shared 10 baseline warm-up | 2.85% / 28.16% | not sampled (measurement did not start) | not sampled | 0 / 10 / 0 / 10 | 0.147 / 42.199 ms | 9.181 ms (300; 2.754 s) | 11/2; 8 / 1,908 ms; 0 |
| Shared 10 After | 3.27% / 28.46% | 47.24% (202.50) / 19.36% (34.17) | 626.4 / 563.0 MiB | 1 / 9.11 / 0 / 10 | 0.122 / 35.829 ms | 11.247 ms (1,228; 13.811 s) | 11/2; 13 / 550 ms; 0 |

Hikari pending은 control/5/10 warm-up/After의 수집 표본에서 모두 0이며, stable measurement 각 9개 Prometheus 표본에서도 max 0, 연속 positive 표본 0이다. Active peak도 pool max10의 1에 그쳤다. 따라서 pending은 queueing point 또는 pool ceiling의 증거가 아니며 Hikari 증설은 하지 않았다.

Shared 5 → After 10 RPS에서 system CPU는 약 24.22% → 28.46%, Backend process CPU는 0.39% → 3.27%였다. MySQL container CPU 평균은 6.65% → 19.36%였지만 host 전체 CPU 한계나 MySQL commit 병목을 보이지 않았다. MySQL `Threads_connected/running`은 11/2로 유지됐다. Deadlock과 performance_schema digest loss는 모든 유효 stage에서 0이다. Baseline 10의 lock 지표만 row-lock waits 8건/1.908초, 최대 389ms로 급증했고 `SELECT SKU ... FOR UPDATE` digest 평균이 7.369ms, lock time이 1.910초였다. After에서는 13건/0.550초였고 해당 SKU lock digest 평균은 0.930ms/lock time 0.554초다.

MySQL Checkout digest는 COMMIT과 SKU lock, Inventory select/update별 count·total·average·lock time·affected를 JSON에 보존했다. Baseline 실패 stage의 Inventory conditional update는 300회 중 293회만 affected였다. After는 measurement 중 1,201회 중 1,201회 반영됐다. Hikari acquire/usage count와 sum, JVM heap, GC pause count/sum, live/peak threads, Backend scrape `up`도 Prometheus JSON에 보존했다. GC pause count/time은 control 11/0.155s, shared 5는 3/0.013s, After 10은 7/0.107s다. JVM heap mean은 각각 약 132, 118, 126 MiB였으며, live thread는 control 44–56, shared 5 58, After 10 40이다. Backend scrape는 해당 측정 9/9 표본에서 healthy였다.

## 원인, 최소 변경, 판정

Root-cause category는 **2. Inventory optimistic conflict dominated**다. Baseline의 7개 미반영 update는 `InventoryService.reserve`가 SKU lock 이후 non-locking `findById(skuId)`로 Inventory version을 읽고 `reserveIfVersionMatches`에서 0건을 받으면 `INVENTORY_CONFLICT` 409를 발생시키는 코드 경로와 일치한다. Exact status counter 409 증가 7건과 update affected 부족 7건이 일치한다. Baseline의 row-lock wait 증가는 shared-SKU contention이 존재했다는 증거지만, 요청 실패를 직접 만든 메커니즘은 stale Inventory version에 의한 conditional update 실패다. Candidate는 현재 Inventory row를 locking read로 읽어 이 read/update 구간을 직렬화하며, 기존 version 조건과 재고 부족 검증은 유지한다. `INVENTORY_CONFLICT` 오류 계약 자체를 제거하지 않으며, 이번 변경은 충분한 stock이 있는 shared-SKU 동시 예약에서 stale version read 때문에 발생하던 불필요한 충돌을 줄이는 범위다. Warm-up JSON에는 상세 post-fixture table count가 저장되지 않아 다른 table별 shortage를 별도로 주장하지 않는다. 이 연결은 응답 본문이 아니라 코드 경로와 집계 delta에 근거한다.

제품 변경은 [InventoryService.java](../../../backend/src/main/java/com/pawcycle/backend/commerce/InventoryService.java) 한 곳이다. Reservation 전에 같은 SKU의 현재 Inventory 행을 `findLockedBySkuId`로 읽도록 바꾸고 기존 conditional update/version 검증은 유지했다. Hikari pool, transaction duration 설정, SQL/schema/index는 변경하지 않았다. After 10 RPS에서 errors/dropped 0, 주문·ready payment·idempotency·reservation·reserved quantity·inventory version의 measurement delta 1,201 일치, available quantity 차감 1,201, negative inventory 0, deadlock 0, Backend scrape healthy를 확인했다. **KEEP**한다.

주요 MySQL digest 평균/누적 시간은 아래와 같다. SQL digest의 INSERT affected는 해당 stage에서 실행된 statement 수치이며 rollback 이후 최종 committed row 수와 같다고 해석하지 않는다.

| Stage | SKU `SELECT … FOR UPDATE` avg / total (lock total) | Inventory SELECT avg / total | Inventory UPDATE avg / total (affected) | Orders / Payments INSERT 평균 |
| --- | ---: | ---: | ---: | ---: |
| Control 20 | 0.538 ms / 1.292 s (5.18 ms) | 0.225 ms / 0.539 s | 0.450 ms / 1.080 s (2,400) | 0.656 / 0.829 ms |
| Shared 5 | 0.411 ms / 0.247 s (1.87 ms) | 0.207 ms / 0.124 s | 0.305 ms / 0.183 s (600) | 0.384 / 0.369 ms |
| Shared 10 baseline warm-up | 7.369 ms / 2.211 s (1.910 s) | 0.288 ms / 0.086 s | 0.377 ms / 0.113 s (293/300) | 0.508 / 0.446 ms |
| Shared 10 After | 0.930 ms / 1.117 s (0.554 s) | 0.341 ms / 0.410 s | 0.544 ms / 0.654 s (1,201) | 0.648 / 0.705 ms |

실제 MySQL 동시성 회귀 테스트 `concurrentDifferentMembersReserveOneSharedSkuWithoutInventoryConflicts`는 8개 member가 같은 SKU를 동시에 예약하도록 해 각 order/payment/idempotency/reservation 1회, inventory available 0/reserved 8/version 8을 검증한다.

## Grafana evidence와 환경

모든 screenshot은 실제 local Grafana dashboard를 동일 exact UTC range로 렌더링한 이미지다. 처음에는 Grafana에 renderer가 없어 render API가 500을 반환했다. 측정이 끝난 뒤에만 공식 [Grafana image rendering](https://grafana.com/docs/grafana/latest/setup-grafana/image-rendering/) remote renderer를 임시 로컬 service로 구성하고 screenshot 네 장을 생성했다. 임시 Grafana 설정을 제거하고 원래 Grafana runtime으로 복원했으며 renderer container와 임시 token 설정도 제거했다. token 값은 evidence에 저장하지 않았다.

- [Control 20 RPS](evidence/grafana/control-isolated-20.png) — `13:46:23.841Z`–`13:48:23.846Z`
- [Baseline shared 5 RPS, last stable before change](evidence/grafana/shared-rps-5.png) — `13:50:30.286Z`–`13:52:30.290Z`
- [Baseline shared 10 RPS, saturation onset and first hard failure](evidence/grafana/shared-rps-10-hard-failure.png) — `13:53:34.404Z`–`13:54:04.416Z`
- [After shared 10 RPS, last stable stage](evidence/grafana/shared-after-rps-10.png) — `14:21:17.438Z`–`14:23:17.495Z`

Stage별 warm-up/measurement/Prometheus/MySQL/cleanup 수치는 JSON으로 보존했다. [run.json](evidence/run.json), [environment.json](evidence/environment.json), [baseline 10 RPS MySQL/Prometheus](evidence/shared-rps-10-warmup.json), [invalid image attempt](evidence/invalid-attempt-rps-10.json)도 포함된다. Cleanup은 control, shared 5, baseline 10, invalid attempt, After 10 모두 정확한 namespace에서 verified다.

환경은 Docker Desktop 29.8.0, Linux/x86_64 VM 12 CPU / 7.72 GiB, Windows k6 v2.2.0, MySQL 8.4.10 REPEATABLE-READ, Backend Temurin 25.0.3, Prometheus 3.13.2, Grafana 13.1.3이다. Backend/MySQL container limits는 기본 unlimited(0)였고 모든 유효 stage에서 unchanged다. Candidate After는 Backend image SHA `54a000f7d1cbbe3b173c3b2cdee84c662f03c0547a6db57a91a277cede3a71ad`, baseline image SHA `149428b38e1773bcab8176135486c08db38186c22cdf41dcb9fb51b18206b791`이다. MySQL image/container는 유지했다. Renderer와 token 설정은 측정 후 생성·사용·정리했다.

## 검증

- `python infra/performance/k6/test_checkout_runner.py`: 20 tests 통과.
- `python -m py_compile infra/performance/k6/run-checkout.py infra/performance/k6/test_checkout_runner.py`: 통과.
- `git diff --check`: 통과.
- `.\gradlew.bat -p backend test --tests com.pawcycle.backend.commerce.CheckoutIdempotencyIntegrationTests`: MySQL integration 7 tests 통과. 별도 disposable local MySQL에서 실행했고 performance MySQL은 건드리지 않았다.
- Candidate Backend Docker build/bootJar 성공, candidate image 재기동 후 healthy 확인.
- control 20, baseline shared 5, valid After shared 10: exact cleanup 및 stage hard gate 통과. Baseline shared 10: warm-up gate에서 중단 후 exact cleanup. Baseline 15/20은 정책대로 미실행.
- Evidence 검증: 21 JSON parse 통과, cleanup 5/5 verified, Grafana PNG 4/4 signature·1920×2800 확인, credential/cookie/JWT value pattern 없음. 임시 renderer 설정·container·image 정리 후 Grafana `/api/health` 200 (`13.1.3`, database ok).
- `scripts/validate-task-artifacts.py --from-stdin --task-id PERF-COMMERCE-003 --task-grade high-risk --execution-type '저장소 변경'`: Draft PR body contract와 report 필수 섹션 통과.
- `scripts/validate-pr-body-encoding.py --body-file <temporary UTF-8 PR body>`: 통과. PR body 마지막 줄은 `<!-- pawcycle-ai-handoff: review-ready -->`다.
- PR은 Draft로 생성하고 merge 또는 CodeRabbit 직접 요청은 하지 않는다.

## 남은 위험과 제한

- Before의 안정 용량은 5와 10 사이로 좁혀졌고 10에서 원인을 수정했다. After는 boundary 10 RPS 한 번만 실행했으므로 post-change 15/20 RPS 상한은 미측정이다. 120초 local run은 장시간 또는 Production capacity를 보증하지 않는다.
- Baseline 실패는 warm-up gate에서 멈춰 120초 latency/resource 분포가 없다. 30초 warm-up과 15초 scrape에서 Prometheus 409 `rate()` series는 표본이 부족했다. 오류 개수는 k6와 exact counter delta로 교차 확인했다.
- Docker stats 표본은 nominal 5초지만 observed completion interval이 3.452–6.312초이며 Prometheus는 15초 scrape다. 짧은 순간의 peak는 놓칠 수 있다.
- Hikari/GC Prometheus counter delta와 MySQL digest는 scraper/snapshot 경계 및 stage 주변 background 작업을 포함할 수 있고 요청 수와 완전한 1:1 대응을 뜻하지 않는다. SQL digest의 COMMIT count는 보존된 관측값 그대로이며 요청 건수로 해석하지 않는다.
- Docker CPU%는 multi-core 합계라 100% 초과 가능하다. process/system CPU는 Docker Linux VM 시계열이지 Windows 전체 host trace가 아니다. Host scheduler/storage/fsync trace는 수집하지 않았다.
- 이 결과는 local disposable fixture와 local runtime에만 적용된다. Secret/cookie/token/raw DB row는 저장하지 않았고 Production/Cloud 검증은 수행하지 않았다.
