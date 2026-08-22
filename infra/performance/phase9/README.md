# PERF-PH9-001 local `/api/products` diagnostic

이 도구는 기존 `infra/performance/k6/capacity-api-products.js`를 그대로 실행하고, Phase 8-C/8-D workload나 애플리케이션 코드를 변경하지 않는다. `BaseUrl`과 `PrometheusUrl` 모두 local loopback origin(`http://127.0.0.1`, `http://localhost`, `http://[::1]`, 선택적 port)만 허용한다. `TARGET_RPS=250`만 허용하며 500/750/1000 RPS 및 Production/Cloud/AWS target은 거부한다.

## 실행

먼저 local-integration과 Prometheus가 healthy인지 확인한 뒤 inspect만 수행할 수 있다.

Phase 9 local diagnostic은 `infra/local-integration/compose.yaml`과 secret 없는 `compose.prometheus.yaml`만 사용한다. Alertmanager, Grafana와 Discord webhook secret이 포함된 `compose.observability.yaml` 전체는 이 diagnostic의 runtime 준비에 필요하지 않다. 기존 named volume은 보존한다.

현재 checkout 소스와 runtime image가 일치하도록 backend/frontend를 다시 build한 뒤 필요한 service만 시작한다.

```powershell
Set-Location infra/local-integration
docker compose --env-file .env.local -f compose.yaml -f compose.prometheus.yaml up --build -d mysql backend frontend proxy prometheus
Set-Location ../..
```

```powershell
powershell -ExecutionPolicy Bypass -File infra/performance/phase9/run-products-diagnostic.ps1 -ValidateOnly
```

진단 실행은 warm-up 30초 후 measurement 시작 snapshot, 250 RPS 2분 동안 5초 간격 sample, measurement 종료 snapshot 순서다. query interval은 5초지만 local Prometheus의 underlying scrape interval은 15초다. 따라서 `activePeak`/`pendingPeak`은 저장된 scrape sample 범위의 관측 max이며 scrape 사이의 짧은 spike는 놓칠 수 있다. k6 stdout/stderr와 summary는 Git 밖의 임시 결과 디렉터리에만 저장하며 repository 내부 `ResultsDir` 입력은 fail-closed로 거부한다. credential, cookie, session, CSRF, response body, raw ID와 raw digest text는 저장하지 않는다.

```powershell
powershell -ExecutionPolicy Bypass -File infra/performance/phase9/run-products-diagnostic.ps1
```

실제 k6를 시작하지 않고 MySQL aggregate collector만 검증하려면 다음을 사용한다.

```powershell
powershell -ExecutionPolicy Bypass -File infra/performance/phase9/run-products-diagnostic.ps1 -ValidateCollectorOnly
```

k6를 시작하지 않고 aggregate summary의 필수 필드, cohort와 target 검증 및 malformed/missing fail-close 경로를 검증하려면 다음을 사용한다.

```powershell
powershell -ExecutionPolicy Bypass -File infra/performance/phase9/run-products-diagnostic.ps1 -ValidateK6AggregateOnly
```

## 해석 경계

Prometheus에서 실제 payload로 확인된 HTTP, Hikari, JVM, GC, process metric을 snapshot한다. k6 시작 전 HTTP products, Hikari usage/acquire/pool, JVM memory/thread critical metric category를 preflight하고 unavailable이면 부하를 시작하지 않고 fail-close한다. container CPU/memory와 health/restart/OOM은 허용된 `docker stats` 및 명시적 `docker inspect --format`으로만 수집한다. MySQL은 container 내부의 Performance Schema에서 `products|skus` digest의 count, total wait, rows examined aggregate와 `Threads_connected`만 얻는다.

HTTP `/api/products` counter, Hikari counters, digest counters에는 Prometheus scrape, backend healthcheck 및 기타 local traffic이 섞일 수 있다. 따라서 request당 query/borrow 값은 오염 가능성을 포함한 diagnostic estimate이며 exact per-request claim으로 사용하지 않는다. `connectionBorrowPerCompletedRequest`는 acquire count 기준이고 usage return count는 `connectionUsageReturnPerCompletedRequest`로 별도 보존한다. 0 또는 null denominator의 평균·비율은 null이다. metric이 없으면 critical category는 부하 시작 전에 fail-close하고 그 외 metric은 null/미확인으로 남길 수 있지만 Prometheus query, Docker 필수 조회, MySQL collector가 실패하면 diagnostic은 fail-close한다.

collector와 Docker 필수 조회는 native exit code와 기대 형식을 확인하고 실패 시 fail-close한다. k6는 snapshot 예외가 발생해도 `finally`에서 실행 중 child를 종료하고 wait/cleanup한다. 이 작업은 병목 후보를 좁히는 측정만 수행한다. SQL/index, Hikari, JVM, Tomcat, Nginx, Docker, RDS와 제품 코드는 변경하지 않는다.
