# PERF-PH8-003 isolated Commerce k6 harness

이 Runbook은 Phase 8-D의 local-integration 전용 저장소 준비 절차다. Production, Cloud, AWS, 운영 DB와 비용 리소스에는 실행하지 않는다. runner와 모든 k6 script는 `http://127.0.0.1`·`http://localhost`·`http://[::1]`만 허용하며, seed/reset은 local Compose의 `.env.local`과 명시 acknowledgement가 없으면 실패한다.

## Fixture와 실행 경계

local-integration을 기존 QA bootstrap credential으로 기동한 뒤 `qa-foundation-004@<local-domain>` 형식의 전용 synthetic 회원만 사용한다. seed script, runner, k6 setup이 모두 이 형식이 아니면 실행을 거부한다. password는 command line·결과 artifact가 아니라 `PERF_PHASE8D_MEMBER_PASSWORD` 환경 변수로만 제공한다.

seed는 정확히 `[QA FOUNDATION-004] 정기배송 사료` / `[QA FOUNDATION-004] 2kg` fixture를 기준으로 marker subscription과 `PERF-PH8-003-ORDER` 주문을 멱등 생성한다. raw row/ID, credential, cookie, session, CSRF token을 출력하지 않는다.

```bash
infra/performance/k6/seed-phase8d-fixture.sh \
  --member-email qa-foundation-004@<local-domain> \
  --acknowledge-local-fixture YES
```

각 k6 test의 `setup`은 CSRF 획득 → login → session rotation 후 CSRF 재획득 → 정확한 product/SKU 확인 → marker subscription/order 존재 확인을 정상 API로 수행한다. seed를 먼저 실행하지 않았거나 다른 SKU가 선택되면 measurement 전에 실패한다. setup 요청은 custom measurement metric에 포함되지 않으며 session·CSRF·raw ID는 VU 메모리에만 보관하고 summary에 기록하지 않는다.

```bash
export PERF_PHASE8D_MEMBER_PASSWORD='<local environment secret>'
infra/performance/k6/run-phase8d.sh --profile mixed-steady --member-email qa-foundation-004@<local-domain>
infra/performance/k6/run-phase8d.sh --profile burst --member-email qa-foundation-004@<local-domain>
infra/performance/k6/run-phase8d.sh --profile sustained --member-email qa-foundation-004@<local-domain>
infra/performance/k6/run-phase8d.sh --profile bounded-write --member-email qa-foundation-004@<local-domain>
```

mixed steady, burst, sustained는 40% product list, 25% product detail, 10% subscription read, 5% cart read, 5% wishlist read, 10% orders read, 5% member read로 고정 arrival-rate scenario를 분리한다. burst만 target RPS의 2배를 30초, steady는 2분, sustained는 동일 target RPS를 10분 실행한다.

bounded-write는 cart add/update/delete와 wishlist add/delete 5개 요청을 한 cycle로 순서대로 실행한다. 동일 synthetic cart/wishlist 상태의 cycle이 겹치지 않도록 `constant-arrival-rate`의 `preAllocatedVUs`와 `maxVUs`를 모두 `1`로 고정한다. target RPS는 HTTP 요청 기준이며 cycle rate는 `target RPS / 5`다. single VU가 목표 속도를 따라가지 못하면 `dropped_iterations`가 발생해 테스트가 실패한다. 이 profile은 데이터 정합성을 지키는 **직렬 bounded-write 기준선**이며 동시 write capacity를 대표하지 않는다. 동시 write capacity가 필요하면 회원·cart·wishlist identity를 VU별로 격리하는 별도 workload를 설계해야 한다.

checkout, payment/billing complete, subscription create/command, cancellation, return 및 다른 mutation은 포함하지 않는다.

stdout aggregate는 target/actual RPS, operation별 RPS·비율, dropped iterations, expected-status error rate, 전체 p50/p95/p99/max, allocated/active VUs만 포함한다. 3xx를 따르지 않고 expected status가 아닌 응답이나 dropped iteration은 threshold 실패다.

## Sustained 관측과 복구

sustained 시작 직전, 5분 시점, 종료 직후에 Grafana `PawCycle Local Observability`의 HTTP request rate·p95/p99·4xx/5xx ratio, JVM heap/GC/thread, CPU, Hikari와 MySQL connection/CPU를 같은 dashboard time range로 수치 기록한다. container restart, OOM, health 악화, error 또는 dropped iteration이 생기면 k6 process를 종료하고 다음 profile을 실행하지 않는다. 결과는 local stack과 load generator의 합성 관측값이며 Production capacity 결론이 아니다.

실행 후 marker fixture만 정리하려면 다음을 실행한다. 이 reset은 해당 synthetic member의 marker subscription/order 및 정확한 fixture SKU/product의 cart/wishlist row만 삭제하며 다른 회원·schema·runtime을 변경하지 않는다.

```bash
infra/performance/k6/seed-phase8d-fixture.sh \
  --member-email qa-foundation-004@<local-domain> \
  --acknowledge-local-fixture YES \
  --reset-only
```

저장소 변경의 복구는 이 PR을 revert한다. 실제 load 실행은 이번 작업 범위 밖이다.
