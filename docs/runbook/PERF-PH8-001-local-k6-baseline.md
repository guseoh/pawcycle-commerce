# PERF-PH8-001 local k6 baseline

이 Runbook은 local proxy만 대상으로 public product cohort를 read-only로 반복 측정한다. Production, Cloud, AWS, 운영 DB는 대상이 아니며 `BASE_URL`이 `http://127.0.0.1`·`http://localhost`·`http://[::1]` origin이 아니면 k6 script가 시작 전에 실패한다.

## 실행

local integration stack과 observability override가 healthy인 상태에서 Git Bash 또는 WSL로 각 cohort를 독립 실행한다. 기본 target은 local proxy `http://127.0.0.1:8080`이다.

```bash
infra/performance/k6/run-baseline.sh --cohort api-products
infra/performance/k6/run-baseline.sh --cohort api-product-detail
infra/performance/k6/run-baseline.sh --cohort products-page
```

각 명령은 1 VU 30초 warm-up(결과 제외) 뒤 1·5·10·20 VU를 각각 2분간 실행한다. `api-product-detail`은 매 실행의 `setup`에서 public list 첫 product ID만 메모리에서 얻는다. ID, response body, credential, cookie, session은 출력하거나 artifact에 기록하지 않는다.

기본 출력은 stdout의 cohort별 aggregate JSON이다. 보관이 필요하면 Git 추적 밖의 local directory만 지정한다.

```bash
infra/performance/k6/run-baseline.sh --cohort api-products --results-dir /tmp/pawcycle-k6-results
```

출력의 `throughput`은 warm-up이나 전체 test duration을 포함하지 않고 measurement request count를 고정 measurement 120초로 나눈 requests/s다. `latencyMs`는 p50/p95/p99/max, `expectedStatusErrorRate`는 기대 상태(200)가 아닌 응답의 비율이다. 이는 local baseline 비교용 관측값이며 latency/SLO 판정이나 Production 용량 결론이 아니다.

## 관측과 복구

Grafana `PawCycle Local Observability`에서 HTTP request rate, average, p95/p99, 전체 요청 대비 4xx/5xx error ratio와 기존 JVM·CPU·heap·GC·thread·Hikari panel을 함께 본다. capacity처럼 짧은 측정 구간이 고정 5분 창에 희석되지 않도록 HTTP rate 기반 패널은 Grafana `$__rate_interval`을 사용한다. Prometheus scrape interval(15초)보다 짧은 구간 해석은 피한다.

실행을 중단하면 k6 process를 종료한다. 이 harness는 GET만 사용하고 application, DB, runtime 설정을 바꾸지 않는다. 저장소 변경을 되돌릴 때는 이 작업 PR을 revert한다.

## Capacity envelope

`run-capacity.sh`는 동일 cohort를 250·500·750·1000 RPS로 각각 독립 실행한다. measurement는 `constant-arrival-rate` 2분이며 기존 1 VU/30초 warm-up을 재사용한다. latency SLO는 추가하지 않지만 expected-status error 또는 dropped iteration이 한 건이라도 발생하면 k6 threshold가 실패해 다음 단계가 실행되지 않는다.

`actualRps`와 `droppedIterationsPerSecond`는 warm-up 또는 전체 test duration을 포함하지 않고 measurement 120초만으로 계산한다. `preAllocatedVUs=250`은 250 RPS에서 최대 1초 수준의 응답 지연에도 load-generator VU 부족을 피하기 위한 초기 budget이고, `maxVUs=1000`은 최대 승인 target 1000 RPS에 같은 1초 지연을 적용한 상한이다. 둘 다 성능 목표가 아니다.

```bash
infra/performance/k6/run-capacity.sh --cohort api-products
```

특정 단계만 다시 확인해야 할 때는 runner를 우회하고 승인된 `TARGET_RPS` 하나만 지정한다. 예시는 API list 250 RPS 재측정이다.

```bash
BASE_URL=http://127.0.0.1:8080 \
TARGET_RPS=250 \
k6 run infra/performance/k6/capacity-api-products.js
```

## Local MySQL statement digest 비교

API cohort(`/api/products`, `/api/products/{productId}`)는 필요할 때 capacity 실행 전후 local MySQL `performance_schema.events_statements_summary_by_digest`를 read-only로 snapshot한다. `/products`에는 DB snapshot을 만들지 않는다. 아래 명령은 실제 credential 값을 출력하지 않고 container 내부 환경변수만 사용하며, 결과 파일은 Git 추적 밖 `/tmp`에 둔다.

```bash
cd infra/local-integration

docker compose --env-file .env.local -f compose.yaml -f compose.observability.yaml \
  exec -T mysql sh -lc 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -N -uroot "$MYSQL_DATABASE" -e "
SELECT DIGEST,
       LEFT(DIGEST_TEXT, 160),
       COUNT_STAR,
       ROUND(SUM_TIMER_WAIT / 1000000000000, 6),
       SUM_ROWS_EXAMINED
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME = DATABASE()
  AND DIGEST_TEXT IS NOT NULL
ORDER BY COUNT_STAR DESC
LIMIT 50;"' \
  > /tmp/pawcycle-digest-before.tsv
```

capacity 실행 뒤 같은 query를 `/tmp/pawcycle-digest-after.tsv`로 저장하고 `COUNT_STAR`, total time, rows examined의 delta만 비교한다. `DIGEST_TEXT`는 MySQL이 literal을 정규화한 statement digest 확인용이며, product ID·response body·credential·cookie·session은 Issue나 repository artifact에 기록하지 않는다.

container unhealthy, restart, OOM 또는 local host 포화는 application capacity 결론이 아니라 중단·후속 판단 사유다.
