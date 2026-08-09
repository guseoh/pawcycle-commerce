# OBS-BASE-001 Local Observability 실행

## 목적과 경계

Prometheus와 Grafana를 기존 local-integration stack에 선택적으로 추가해 Phase A metric을 수집하고 시각화한다. 기본 `compose.yaml`만 실행하면 기존 MySQL, Backend, Frontend, Proxy 구성은 바뀌지 않는다. 관측성 구성은 `compose.observability.yaml`을 명시한 로컬 실행에서만 활성화되며 Production, Cloud, AWS 계약을 정의하지 않는다.

Prometheus와 Grafana의 host 포트는 `127.0.0.1`에만 바인딩한다. Prometheus는 Docker internal network의 `backend:8080/actuator/prometheus`를 직접 scrape하며 internet-facing Proxy에는 `/actuator/**` route를 추가하지 않는다. Grafana는 로컬 read-only 조회를 위해 anonymous Viewer만 허용하고 로그인과 초기 admin 생성을 비활성화한다.

## 시작

`infra/local-integration/.env.local`은 기존 local-integration 절차대로 준비하며 Secret 값을 명령이나 문서에 출력하지 않는다. 기본 포트가 사용 중이면 다음 로컬 변수만 바꾼다.

```text
PAWCYCLE_LOCAL_PROMETHEUS_PORT=9090
PAWCYCLE_LOCAL_GRAFANA_PORT=3001
```

다음 명령은 `infra/local-integration`에서 실행한다.

```powershell
$ComposeFiles = @('-f', 'compose.yaml', '-f', 'compose.observability.yaml')
docker compose --env-file .env.local @ComposeFiles config --quiet
docker compose --env-file .env.local @ComposeFiles pull mysql proxy prometheus grafana
docker compose --env-file .env.local @ComposeFiles build backend frontend
docker compose --env-file .env.local @ComposeFiles up --detach --wait --wait-timeout 180
docker compose --env-file .env.local @ComposeFiles ps

$PrometheusPort = (docker compose --env-file .env.local @ComposeFiles port prometheus 9090).Split(':')[-1]
$GrafanaPort = (docker compose --env-file .env.local @ComposeFiles port grafana 3000).Split(':')[-1]
$PrometheusUrl = "http://127.0.0.1:$PrometheusPort"
$GrafanaUrl = "http://127.0.0.1:$GrafanaPort"
$ReadinessDeadline = (Get-Date).AddSeconds(180)

do {
    $PrometheusReady = $false
    $GrafanaReady = $false
    try { $PrometheusReady = (Invoke-WebRequest -UseBasicParsing "$PrometheusUrl/-/ready").StatusCode -eq 200 } catch {}
    try { $GrafanaReady = (Invoke-RestMethod "$GrafanaUrl/api/health").database -eq 'ok' } catch {}
    if (-not ($PrometheusReady -and $GrafanaReady)) { Start-Sleep -Seconds 2 }
} until (($PrometheusReady -and $GrafanaReady) -or (Get-Date) -ge $ReadinessDeadline)

if (-not ($PrometheusReady -and $GrafanaReady)) {
    throw 'Prometheus 또는 Grafana가 180초 안에 준비되지 않았습니다.'
}
```

- Prometheus: `$PrometheusUrl` (`.env.local` 기본값은 `http://127.0.0.1:9090`)
- Grafana: `$GrafanaUrl/d/pawcycle-local-observability` (`.env.local` 기본값은 `http://127.0.0.1:3001/d/pawcycle-local-observability`)
- scrape interval: 15초
- scrape timeout: 10초

Grafana datasource와 PawCycle Dashboard는 provisioning 파일에서 자동 생성되며 UI 저장으로 수정하지 않는다.

## 검증

Prometheus API에서 `pawcycle-backend` target의 `health`가 `up`인지 확인한다. Grafana API에서는 datasource UID `pawcycle-prometheus`와 dashboard UID `pawcycle-local-observability`가 조회되어야 한다.

테스트 트래픽은 기존 `smoke.ps1` 또는 공개 상품 API 호출을 사용한다. 트래픽 전후 Prometheus query 결과를 비교해 HTTP request count, JVM heap/GC/thread, process/system CPU와 Hikari active/idle/pending을 확인한다. reconciliation은 기존 조건부 Scheduler 실행 결과만 관측하며 cadence를 변경하거나 새 trigger를 추가하지 않는다. idempotency cleanup은 승인된 runtime trigger와 운영 batch size가 없으므로 Phase A integration test 증거를 사용한다.

Proxy 공개 경계는 `http://127.0.0.1:8080/actuator/prometheus` 응답이 Backend Prometheus payload가 아님을 확인한다. Backend container 내부 direct endpoint는 Prometheus scrape에만 사용한다.

resource baseline은 UTC 시각, Docker Engine/Compose 버전, 15초 scrape interval, 측정 전후 traffic 조건과 함께 기록한다. `docker stats --no-stream`으로 container CPU·memory를, `docker system df --verbose`와 named volume 정보를 사용해 local storage를 확인한다. 연속 scrape 전후 Hikari active/idle/pending과 MySQL connection 수를 비교해 gauge의 네 개 indexed `COUNT(*)` query가 local DB connection에 미치는 영향을 기록한다. 이 결과로 Production cache, refresh cadence, query timeout 또는 배치 정책을 정하지 않는다.

## 종료와 복구

```powershell
$ComposeFiles = @('-f', 'compose.yaml', '-f', 'compose.observability.yaml')
docker compose --env-file .env.local @ComposeFiles down
```

일반 종료에서는 MySQL, Prometheus, Grafana named volume을 삭제하지 않는다. 관측성 변경을 되돌리기 전에도 반드시 두 Compose 파일을 함께 지정한 위 `down`을 먼저 실행해 Prometheus와 Grafana container를 제거한다. 이후 이 작업의 저장소 변경만 revert하며 Production 설정이나 Backend 제품 코드를 수정하지 않는다.
