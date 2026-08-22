# PERF-PH9-001 local `/api/products` diagnostic

이 도구는 기존 `infra/performance/k6/capacity-api-products.js`를 그대로 실행하고, Phase 8-C/8-D workload나 애플리케이션 코드를 변경하지 않는다. local loopback `BASE_URL`과 `TARGET_RPS=250`만 허용한다. 500/750/1000 RPS 및 Production/Cloud/AWS target은 거부한다.

## 실행

먼저 local-integration과 Prometheus가 healthy인지 확인한 뒤 inspect만 수행할 수 있다.

```powershell
powershell -ExecutionPolicy Bypass -File infra/performance/phase9/run-products-diagnostic.ps1 -ValidateOnly
```

진단 실행은 warm-up 30초 후 measurement 시작 snapshot, 250 RPS 2분 동안 5초 간격 sample, measurement 종료 snapshot 순서다. k6 stdout/stderr와 summary는 Git 밖의 임시 결과 디렉터리에 저장하며 credential, cookie, session, CSRF, response body, raw ID와 raw digest text는 저장하지 않는다.

```powershell
powershell -ExecutionPolicy Bypass -File infra/performance/phase9/run-products-diagnostic.ps1
```

## 해석 경계

Prometheus에서 실제 payload로 확인된 HTTP, Hikari, JVM, GC, process metric을 snapshot한다. container CPU/memory와 health/restart/OOM은 허용된 `docker stats` 및 명시적 `docker inspect --format`으로만 수집한다. MySQL은 container 내부의 Performance Schema에서 `products|skus` digest의 count, total wait, rows examined aggregate와 `Threads_connected`만 얻는다.

HTTP `/api/products` counter, Hikari counters, digest counters에는 Prometheus scrape, backend healthcheck 및 기타 local traffic이 섞일 수 있다. 따라서 request당 query/borrow 값은 오염 가능성을 포함한 diagnostic estimate이며 exact per-request claim으로 사용하지 않는다. metric이 없거나 query가 실패하면 null/미확인으로 남기고 보정하지 않는다.

이 작업은 병목 후보를 좁히는 측정만 수행한다. SQL/index, Hikari, JVM, Tomcat, Nginx, Docker, RDS와 제품 코드는 변경하지 않는다.
