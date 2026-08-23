# PERF-PH9-001 local `/api/products` diagnostic

이 도구는 기존 `infra/performance/k6/capacity-api-products.js`를 그대로 실행하고, Phase 8-C/8-D workload나 애플리케이션 코드를 변경하지 않는다. `BaseUrl`과 `PrometheusUrl` 모두 local loopback origin(`http://127.0.0.1`, `http://localhost`, `http://[::1]`, 선택적 port)만 허용한다. `TARGET_RPS=250`만 허용하며 500/750/1000 RPS 및 Production/Cloud/AWS target은 거부한다.

## 실행

Windows에서는 PowerShell 7+(`pwsh`)로 실행한다. Windows PowerShell 5.1의 native stdin 인코딩은 MySQL collector SQL 앞에 BOM을 전달할 수 있으므로 이 진단 entrypoint에 사용하지 않는다.

먼저 local-integration과 Prometheus가 healthy인지 확인한 뒤 inspect만 수행할 수 있다.

Phase 9 local diagnostic은 `infra/local-integration/compose.yaml`과 secret 없는 `compose.prometheus.yaml`만 사용한다. Alertmanager, Grafana와 Discord webhook secret이 포함된 `compose.observability.yaml` 전체는 이 diagnostic의 runtime 준비에 필요하지 않다. 기존 named volume은 보존한다.

## Production-like envelope baseline

Phase 9 baseline은 전용 `compose.phase9-envelope.yaml` overlay를 사용한다. backend에만 memory 640 MB, CPU 0.75, PID 256, `JAVA_TOOL_OPTIONS`의 `MaxRAMPercentage=65.0`과 OOM 즉시 종료를 적용하며 base/production compose와 DB profile은 변경하지 않는다. Windows에서는 `pwsh`를 사용한다. 이 단계에서는 250 RPS를 실행하지 않는다.

```powershell
Set-Location infra/local-integration
docker compose --env-file .env.local -f compose.yaml -f compose.prometheus.yaml -f compose.phase9-envelope.yaml up --build -d mysql backend frontend proxy prometheus
Set-Location ../..
```

## Tomcat experiments

기존 Tomcat64 결과는 candidate 기각으로 기록한다. Tomcat128은 Production 권장값이 아니라 Production-like local envelope의 midpoint experiment다. `compose.phase9-tomcat128.yaml`은 backend의 `SERVER_TOMCAT_THREADS_MAX=128`과 local observability instrumentation인 `SERVER_TOMCAT_MBEANREGISTRY_ENABLED=true`만 추가하며, Hikari/SQL/index/JVM/accept queue/max-connections 등 다른 성능 변수를 동시에 변경하지 않는다. 이 experiment는 기존 resource-envelope와 Prometheus overlay에 Tomcat128 overlay를 추가한다. 이 준비 단계에서는 250 RPS를 실행하지 않는다.

일반 Phase 9 diagnostic preflight는 기존 HTTP/Hikari/JVM critical metric 계약만 적용한다. Tomcat experiment 경로에서만 `-ExpectedTomcatThreadsMax <expected>`를 전달하며, 이때 Prometheus의 `tomcat_threads_config_max_threads`, `tomcat_threads_current_threads`, `tomcat_threads_busy_threads` 존재와 expected config max를 확인한다. metric이 없거나 config max가 다르면 load를 시작하지 않고 fail-close한다. experiment sample과 summary에는 세 Tomcat metric을 그대로 보존한다.

```powershell
Set-Location infra/local-integration
docker compose --env-file .env.local -f compose.yaml -f compose.prometheus.yaml -f compose.phase9-envelope.yaml -f compose.phase9-tomcat128.yaml up --build -d mysql backend frontend proxy prometheus
Set-Location ../..
```

Tomcat128 experiment 실행 시에만 다음 expected max parameter를 함께 사용한다.

```powershell
pwsh -NoProfile -File infra/performance/phase9/run-products-diagnostic.ps1 -ExpectedTomcatThreadsMax 128
```

Tomcat128 experiment 뒤 baseline 복귀는 Tomcat128 overlay와 expected max parameter를 제거하고 envelope만 유지한다.

```powershell
Set-Location infra/local-integration
docker compose --env-file .env.local -f compose.yaml -f compose.prometheus.yaml -f compose.phase9-envelope.yaml up --build -d mysql backend frontend proxy prometheus
Set-Location ../..
pwsh -NoProfile -File infra/performance/phase9/run-products-diagnostic.ps1
```

## CPU1.5 causality experiment

PERF-PH9-004의 Tomcat128 + 0.75 CPU first-result를 control로 재사용한다. control을 재실행하지 않고, `compose.phase9-cpu15.yaml`으로 backend CPU limit만 1.5로 변경한다. Tomcat max 128, MBean instrumentation, memory 640MiB, PID 256, `MaxRAMPercentage=65.0`은 유지한다. CPU1.5는 local-only causality experiment이며 Production 권장값이 아니다. Hikari/SQL/index/JVM/memory/PID/accept queue/max-connections는 변경하지 않는다.

이것만 실행해주세요.

```powershell
Set-Location infra/local-integration
docker compose --env-file .env.local -f compose.yaml -f compose.prometheus.yaml -f compose.phase9-envelope.yaml -f compose.phase9-tomcat128.yaml -f compose.phase9-cpu15.yaml up --build -d mysql backend frontend proxy prometheus
Set-Location ../..
pwsh -NoProfile -File infra/performance/phase9/run-products-diagnostic.ps1 -ExpectedTomcatThreadsMax 128
```

CPU1.5 rollback은 `compose.phase9-cpu15.yaml`만 제거하고, Tomcat128 overlay와 envelope를 유지한다. compose 파일을 제외하는 것만으로 실행 중인 backend 설정이 바뀌지는 않으므로, 아래 명령으로 CPU1.5 overlay를 제외한 동일 backend 구성을 강제로 재생성한다. baseline 복귀는 별도로 Tomcat128 overlay와 expected max parameter까지 제거한다. 이 preparation 단계에서는 250 RPS를 실행하지 않는다.

이것만 실행해주세요.

```powershell
Set-Location infra/local-integration
docker compose --env-file .env.local -f compose.yaml -f compose.prometheus.yaml -f compose.phase9-envelope.yaml -f compose.phase9-tomcat128.yaml up -d --no-deps --force-recreate backend
docker inspect --format 'health={{if .State.Health}}{{.State.Health.Status}}{{else}}no-health{{end}} cpu={{.HostConfig.NanoCpus}} memory={{.HostConfig.Memory}} pids={{.HostConfig.PidsLimit}}' pawcycle-local-integration-backend-1
Set-Location ../..
```

rollback 확인값은 backend `health=healthy`, `cpu=750000000`, `memory=671088640`, `pids=256`이다. 이 확인은 credential이나 전체 container 설정을 출력하지 않는 좁은 상태 조회만 사용한다.

## Memory1GiB causality experiment

PERF-PH9-005의 Tomcat128 + CPU1.5 + memory640MiB first-result를 control로 재사용한다. control과 PERF-PH9-005를 재실행하지 않고, `compose.phase9-memory1g.yaml`으로 backend memory limit만 1GiB로 변경한다. overlay 순서는 `compose.phase9-envelope.yaml` → `compose.phase9-tomcat128.yaml` → `compose.phase9-cpu15.yaml` → `compose.phase9-memory1g.yaml`이다. CPU1.5, PID256, Tomcat max 128, MBean instrumentation, `MaxRAMPercentage=65.0`은 유지한다. 이 candidate는 local-only causality experiment이며 Production/Cloud/AWS 실행과 실제 250 RPS를 포함하지 않는다.

이것만 실행해주세요.

```powershell
Set-Location infra/local-integration
$composeArgs = @(
    '--env-file', '.env.local',
    '-f', 'compose.yaml',
    '-f', 'compose.prometheus.yaml',
    '-f', 'compose.phase9-envelope.yaml',
    '-f', 'compose.phase9-tomcat128.yaml',
    '-f', 'compose.phase9-cpu15.yaml',
    '-f', 'compose.phase9-memory1g.yaml'
)
docker compose @composeArgs up --build -d --wait --wait-timeout 120 mysql backend frontend proxy prometheus
if ($LASTEXITCODE -ne 0) { throw 'Memory1GiB candidate services did not become healthy.' }
docker inspect --format 'health={{if .State.Health}}{{.State.Health.Status}}{{else}}no-health{{end}} cpu={{.HostConfig.NanoCpus}} memory={{.HostConfig.Memory}} pids={{.HostConfig.PidsLimit}}' pawcycle-local-integration-backend-1
$portOutput = docker compose @composeArgs port prometheus 9090
if ($LASTEXITCODE -ne 0) { throw 'Prometheus published port lookup failed.' }
$portMatch = [regex]::Match(($portOutput | Select-Object -First 1), ':(?<port>[0-9]+)$')
if (-not $portMatch.Success) { throw 'Prometheus published port is unavailable.' }
$prometheusUrl = "http://127.0.0.1:$($portMatch.Groups['port'].Value)"
$freshAfter = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$deadline = (Get-Date).AddSeconds(45)
$tomcatValue = $null
do {
    Start-Sleep -Seconds 2
    try {
        $evaluationTime = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
        $tomcat = Invoke-RestMethod -Uri "$prometheusUrl/api/v1/query?query=sum%28tomcat_threads_config_max_threads%29&time=$evaluationTime"
        $tomcatTimestamp = Invoke-RestMethod -Uri "$prometheusUrl/api/v1/query?query=max%28timestamp%28tomcat_threads_config_max_threads%29%29&time=$evaluationTime"
        if ($tomcat.status -eq 'success' -and $tomcat.data.result.Count -eq 1 -and $tomcatTimestamp.status -eq 'success' -and $tomcatTimestamp.data.result.Count -eq 1) {
            $candidateValue = [int]$tomcat.data.result[0].value[1]
            $scrapeTimestamp = [double]$tomcatTimestamp.data.result[0].value[1]
            if ($candidateValue -eq 128 -and $scrapeTimestamp -ge $freshAfter) {
                $tomcatValue = $candidateValue
                break
            }
        }
    } catch {
    }
} while ((Get-Date) -lt $deadline)
if ($null -eq $tomcatValue) { throw 'Fresh Tomcat runtime max 128 was not observed.' }
$tomcatValue
Set-Location ../..
```

Runtime 확인값은 Tomcat max `128`, CPU `1500000000`, memory `1073741824`, PID `256`이다. Prometheus published port는 동일 compose stack에서 조회하며, Tomcat 값과 scrape timestamp는 동일 evaluation time에서 검증하고 candidate가 healthy가 된 뒤의 새 scrape timestamp까지 확인한다. `-ValidateTomcatOnly`는 synthetic fixture 검증용이므로 이 runtime 확인에 사용하지 않는다. 실제 candidate load에서는 기존 `-ExpectedTomcatThreadsMax 128` preflight가 실제 Prometheus Tomcat metric을 fail-close로 검증한 뒤 k6를 시작한다. rollback은 `compose.phase9-memory1g.yaml`만 제거하고 envelope + Tomcat128 + CPU1.5 overlay를 유지한 채 backend를 재생성한다. 그러면 PERF-PH9-005 조건인 Tomcat128 + CPU1.5 + memory640MiB로 복귀한다.

이것만 실행해주세요.

```powershell
Set-Location infra/local-integration
$rollbackArgs = @(
    '--env-file', '.env.local',
    '-f', 'compose.yaml',
    '-f', 'compose.prometheus.yaml',
    '-f', 'compose.phase9-envelope.yaml',
    '-f', 'compose.phase9-tomcat128.yaml',
    '-f', 'compose.phase9-cpu15.yaml'
)
docker compose @rollbackArgs up -d --no-deps --force-recreate --wait --wait-timeout 120 backend
if ($LASTEXITCODE -ne 0) { throw 'Memory1GiB rollback backend did not become healthy.' }
docker inspect --format 'health={{if .State.Health}}{{.State.Health.Status}}{{else}}no-health{{end}} cpu={{.HostConfig.NanoCpus}} memory={{.HostConfig.Memory}} pids={{.HostConfig.PidsLimit}}' pawcycle-local-integration-backend-1
Set-Location ../..
```

rollback 확인값은 backend `health=healthy`, `cpu=1500000000`, `memory=671088640`, `pids=256`이다. 이 확인은 credential이나 전체 container 설정을 출력하지 않는 좁은 상태 조회만 사용한다.

## 일반 local diagnostic

일반 local diagnostic은 Tomcat128 overlay 없이 baseline 또는 기본 local compose를 사용한다. Tomcat metric이 없어도 일반 resilient snapshot의 collector failure로 처리하지 않으며, Tomcat metric 검증은 `-ExpectedTomcatThreadsMax`를 명시한 experiment 경로에서만 fail-close한다. 현재 checkout 소스와 runtime image가 일치하도록 backend/frontend를 다시 build한 뒤 필요한 service만 시작한다.

```powershell
Set-Location infra/local-integration
docker compose --env-file .env.local -f compose.yaml -f compose.prometheus.yaml up --build -d mysql backend frontend proxy prometheus
Set-Location ../..
```

```powershell
pwsh -NoProfile -File infra/performance/phase9/run-products-diagnostic.ps1 -ValidateOnly
```

진단 실행은 warm-up 30초 후 measurement 시작 snapshot과 250 RPS 2분 동안 5초 간격 sample을 수집한다. measurement loop가 끝난 UTC 시각을 boundary로 고정하고 k6 종료를 기다린 뒤, 해당 boundary 이상 timestamp의 fresh Prometheus measurement-end evidence를 우선 수집한다. fresh evidence가 아직 없으면 총 4회, 5초 간격으로 bounded retry하며, 계속 unavailable이면 해당 metric은 null과 sanitized collector failure로 보존한다. retry 횟수·간격·boundary·최종 evidence timestamp는 summary의 `measurementEndPrometheusRetry`에서 확인한다. query interval은 5초지만 local Prometheus의 underlying scrape interval은 15초다. 따라서 `activePeak`/`pendingPeak`은 retry snapshot이 아닌 실행 중 저장된 scrape sample 범위의 관측 max이며 scrape 사이의 짧은 spike는 놓칠 수 있다. k6 stdout/stderr와 summary는 Git 밖의 임시 결과 디렉터리에만 저장하며 repository 내부 `ResultsDir` 입력은 fail-closed로 거부한다. credential, cookie, session, CSRF, response body, raw ID와 raw digest text는 저장하지 않는다.

```powershell
pwsh -NoProfile -File infra/performance/phase9/run-products-diagnostic.ps1
```

실제 k6를 시작하지 않고 MySQL aggregate collector만 검증하려면 다음을 사용한다.

```powershell
pwsh -NoProfile -File infra/performance/phase9/run-products-diagnostic.ps1 -ValidateCollectorOnly
```

k6를 시작하지 않고 aggregate summary의 필수 필드, cohort와 target 검증 및 malformed/missing fail-close 경로를 검증하려면 다음을 사용한다.

```powershell
pwsh -NoProfile -File infra/performance/phase9/run-products-diagnostic.ps1 -ValidateK6AggregateOnly
```

실제 k6를 시작하지 않고 non-zero process exit와 aggregate 보존, aggregate 누락 및 collector error에서도 failure summary가 생성되는 경로를 검증하려면 다음을 사용한다.

```powershell
pwsh -NoProfile -File infra/performance/phase9/run-products-diagnostic.ps1 -ValidateFailureHandlingOnly
```

실제 k6를 시작하지 않고 measurement-end Prometheus transient recovery와 persistent unavailable의 bounded retry, null 및 collector failure 보존을 검증하려면 다음을 사용한다.

```powershell
pwsh -NoProfile -File infra/performance/phase9/run-products-diagnostic.ps1 -ValidateMeasurementEndRetryOnly
```

Tomcat experiment metric preflight의 missing, wrong-max, valid fixture를 expected=128로 실제 k6 없이 검증하려면 다음을 사용한다.

```powershell
pwsh -NoProfile -File infra/performance/phase9/run-products-diagnostic.ps1 -ValidateTomcatOnly -ExpectedTomcatThreadsMax 128
```

## 해석 경계

Prometheus에서 실제 payload로 확인된 HTTP, Hikari, JVM, GC, process metric을 snapshot한다. k6 시작 전 HTTP products, Hikari usage/acquire/pool, JVM memory/thread critical metric category를 preflight하고 unavailable이면 부하를 시작하지 않고 fail-close한다. container CPU/memory와 health/restart/OOM은 허용된 `docker stats` 및 명시적 `docker inspect --format`으로만 수집한다. MySQL은 container 내부의 Performance Schema에서 `products|skus` digest의 count, total wait, rows examined aggregate와 `Threads_connected`만 얻는다.

HTTP `/api/products` counter, Hikari counters, digest counters에는 Prometheus scrape, backend healthcheck 및 기타 local traffic이 섞일 수 있다. 따라서 request당 query/borrow 값은 오염 가능성을 포함한 diagnostic estimate이며 exact per-request claim으로 사용하지 않는다. `connectionBorrowPerCompletedRequest`는 acquire count 기준이고 usage return count는 `connectionUsageReturnPerCompletedRequest`로 별도 보존한다. 0 또는 null denominator의 평균·비율은 null이다. k6 시작 전 critical category unavailable은 fail-close하지만, workload 시작 후 Prometheus/MySQL/Docker collector unavailable은 해당 metric을 null로 남기고 sanitized `collectionErrors`에 기록한 뒤 k6 실행을 계속한다.

preflight의 collector와 Docker 필수 조회는 native exit code와 기대 형식을 확인하고 실패 시 fail-close한다. workload 시작 후 collector failure는 fail-soft evidence로 보존하며 running k6를 collector failure 때문에 조기 종료하지 않는다. k6 process가 non-zero로 끝나도 stdout aggregate를 먼저 parse하고, aggregate가 없거나 malformed여도 available samples, collection errors, narrow backend final state를 포함한 `diagnostic-summary.json`을 생성한다. threshold failure와 harness/collector failure는 outcome에서 구분한다. 외부/script abort 시 실행 중 child는 `finally`에서 종료하고 wait/cleanup한다. 이 작업은 병목 후보를 좁히는 측정만 수행한다. SQL/index, Hikari, JVM, Tomcat, Nginx, Docker, RDS와 제품 코드는 변경하지 않는다.
