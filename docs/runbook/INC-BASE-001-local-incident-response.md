# INC-BASE-001 Local 장애 감지·복구 기준선

## 목적과 경계

OBS-BASE-001 local stack에서 Backend unavailable, MySQL 연결 실패, reconciliation 실패를 감지하고 원인을 구분한 뒤 정상 복귀를 확인한다. 이 절차는 local-integration 전용이며 Production·Cloud·AWS, alert 임계값, 자동 복구, Scheduler cadence 변경을 정의하지 않는다. Secret은 출력하지 않고 기존 `.env.local`만 사용한다.

기준 환경은 2026-08-09 UTC, Docker Engine 28.5.1, MySQL 8.4.10, Prometheus scrape interval 15초·timeout 10초다. 실행 전 `OBS-BASE-001-local-observability.md`에 따라 전체 stack과 Grafana Dashboard를 준비한다.

```powershell
$ComposeFiles = @('-f', 'compose.yaml', '-f', 'compose.observability.yaml')
$ProxyPort = (docker compose --env-file .env.local @ComposeFiles port proxy 80).Split(':')[-1]
$PrometheusPort = (docker compose --env-file .env.local @ComposeFiles port prometheus 9090).Split(':')[-1]
$ProxyUrl = "http://127.0.0.1:$ProxyPort"
$PrometheusUrl = "http://127.0.0.1:$PrometheusPort"
docker compose --env-file .env.local @ComposeFiles ps
```

정상 기준은 MySQL·Backend가 `healthy`, Backend `/actuator/health`가 `UP`, Prometheus `up{job="pawcycle-backend"}`가 `1`, Proxy 상품 API가 `200`인 상태다. 운영자의 첫 확인 순서는 다음과 같다.

1. `docker compose ps`로 Backend와 MySQL process·health를 구분한다.
2. Prometheus target의 `health`와 `lastError`를 확인한다.
3. Backend container 내부 `/actuator/health`를 10초 이내로 확인한다.
4. Backend scrape가 유지되면 Hikari와 reconciliation failure metric, 관련 Backend log를 확인한다.

## 판정 기준

| 시나리오 | 장애 판정 | 원인 구분 | 정상 복귀 |
| --- | --- | --- | --- |
| Backend unavailable | Backend container가 stopped이고 `up=0`; Proxy API `502` | MySQL은 healthy | Backend healthy, health `UP`, `up=1`, Proxy `200` |
| MySQL 연결 실패 | MySQL container가 stopped; Backend health·scrape·Proxy가 10초 안에 응답하지 않음 | Backend process는 running이고 Prometheus `lastError`는 scrape timeout | MySQL과 Backend healthy, health `UP`, `up=1`, Proxy `200` |
| reconciliation 실패 | health `UP`, `up=1`인 상태에서 `pawcycle_subscription_reconciliation_failures_total` 증가와 실패 log가 함께 존재 | 전체 Backend·MySQL 장애가 아니라 해당 reconciliation transaction 실패 | 원인 제거 후 다음 허용된 실행이 완료되고 새 failure 증가가 없음 |

이 값들은 local 판정 조건이며 Production alert 임계값이 아니다. Grafana의 `Backend scrape availability`, `Hikari connections`, `Reconciliation totals` 패널에서 같은 Prometheus 신호를 확인한다.

## Backend unavailable

```powershell
docker compose --env-file .env.local @ComposeFiles stop --timeout 10 backend
# 15초 scrape 1회 이후 container, Prometheus target과 10초 이내 Proxy 502를 확인한다.
curl.exe --max-time 10 --output NUL --write-out "%{http_code}`n" "$ProxyUrl/api/products"
docker compose --env-file .env.local @ComposeFiles start backend
docker compose --env-file .env.local @ComposeFiles up --detach --wait --wait-timeout 180 backend
$BackendContainer = docker compose --env-file .env.local @ComposeFiles ps -q backend
docker exec $BackendContainer curl --fail --silent --show-error --max-time 10 http://127.0.0.1:8080/actuator/health
curl.exe --max-time 10 --output NUL --write-out "%{http_code}`n" "$ProxyUrl/api/products"
```

실측에서는 `2026-08-09T05:40:37Z`에 target `down`, Proxy `502`, Backend stopped, MySQL healthy를 확인했다. `2026-08-09T05:41:25Z`에 health `UP`, `up=1`, Proxy `200`으로 복귀했다.

## MySQL 연결 실패

```powershell
docker compose --env-file .env.local @ComposeFiles stop --timeout 10 mysql
$BackendContainer = docker compose --env-file .env.local @ComposeFiles ps -q backend
docker exec $BackendContainer curl --fail --silent --show-error --max-time 10 http://127.0.0.1:8080/actuator/health
curl.exe --max-time 10 --output NUL --write-out "%{http_code}`n" "$ProxyUrl/api/products"
docker compose --env-file .env.local @ComposeFiles up --detach --wait --wait-timeout 180 mysql backend
$BackendContainer = docker compose --env-file .env.local @ComposeFiles ps -q backend
docker exec $BackendContainer curl --fail --silent --show-error --max-time 10 http://127.0.0.1:8080/actuator/health
curl.exe --max-time 10 --output NUL --write-out "%{http_code}`n" "$ProxyUrl/api/products"
```

실측에서는 `2026-08-09T05:42:30Z`에 Backend process가 running인 상태에서 Prometheus target `down`, `lastError=context deadline exceeded`, health와 Proxy timeout, MySQL stopped를 확인했다. `2026-08-09T05:43:16Z`에 기존 Backend process가 DB 연결을 회복해 health `UP`, `up=1`, Proxy `200`으로 복귀했다.

## Reconciliation 실패

active local fixture가 있을 때만 수행한다. 다음 SQL은 현재 MySQL session의 `innodb_lock_wait_timeout`을 읽고 40초의 Backend 재시작 여유를 더한 시간 동안 한 row를 조회 lock으로 보유한 뒤 `ROLLBACK`하며 데이터를 변경하지 않는다. 기존 즉시 reconciliation 실행을 사용하고 Scheduler cadence를 바꾸지 않는다. named lock과 실제 row lock, 적용 timeout을 함께 확인한 뒤 Backend를 재시작한다.

```powershell
$MySqlContainer = docker compose --env-file .env.local @ComposeFiles ps -q mysql
if (-not $MySqlContainer) { throw 'MySQL Compose service를 찾지 못했습니다.' }
$RunId = [guid]::NewGuid().ToString('N')
$SqlPath = Join-Path $env:TEMP "inc-base-001-reconciliation-lock-$RunId.sql"
$ContainerSqlPath = "/tmp/inc-base-001-reconciliation-lock-$RunId.sql"
$ContainerOutputPath = "/tmp/inc-base-001-reconciliation-lock-$RunId.out"
$MySqlClient = 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP --host=127.0.0.1 --user="$MYSQL_USER" --database="$MYSQL_DATABASE" --batch --skip-column-names'
function Invoke-MySqlScalar([string]$Statement) {
    $Command = $MySqlClient + ' --execute="' + $Statement + '"'
    return ((docker exec $MySqlContainer sh -c $Command) -join "`n").Trim()
}
function Remove-ReconciliationFixtureArtifacts {
    if (Test-Path -LiteralPath $SqlPath) { Remove-Item -LiteralPath $SqlPath }
    docker exec $MySqlContainer rm -f $ContainerSqlPath $ContainerOutputPath
}
$Sql = @"
SELECT CONCAT('CONNECTION_ID:', CONNECTION_ID());
SELECT GET_LOCK('inc-base-001-reconciliation',0) INTO @get_lock_result;
SELECT @@SESSION.innodb_lock_wait_timeout INTO @lock_wait_timeout;
SET @hold_seconds := @lock_wait_timeout + 40;
SET @subscription_id := NULL;
START TRANSACTION;
SELECT id INTO @subscription_id FROM subscriptions WHERE @get_lock_result=1 AND mvp2_managed=true AND status='ACTIVE' ORDER BY id LIMIT 1 FOR UPDATE;
SELECT CASE
    WHEN @get_lock_result IS NULL OR @get_lock_result<>1 THEN CONCAT('NAMED_LOCK_FAILED:', COALESCE(@get_lock_result, 'NULL'))
    WHEN @subscription_id IS NULL THEN 'NO_ACTIVE_FIXTURE'
    ELSE CONCAT('LOCK_ACQUIRED:', @subscription_id, ':WAIT_TIMEOUT:', @lock_wait_timeout, ':HOLD_SECONDS:', @hold_seconds)
END;
SELECT IF(@get_lock_result=1 AND @subscription_id IS NOT NULL, SLEEP(@hold_seconds), NULL) INTO @sleep_result;
ROLLBACK;
SELECT IF(@get_lock_result=1, RELEASE_LOCK('inc-base-001-reconciliation'), NULL) INTO @release_lock_result;
SELECT CONCAT('FIXTURE_RESULTS:GET_LOCK=', COALESCE(@get_lock_result, 'NULL'), ':SLEEP=', COALESCE(@sleep_result, 'NULL'), ':RELEASE_LOCK=', COALESCE(@release_lock_result, 'NULL'));
"@
[IO.File]::WriteAllText($SqlPath, $Sql, [Text.UTF8Encoding]::new($false))
docker exec $MySqlContainer sh -c "rm -f '$ContainerOutputPath'"
docker cp $SqlPath "${MySqlContainer}:$ContainerSqlPath"
$MySqlCommand = $MySqlClient + ' --unbuffered < "' + $ContainerSqlPath + '" > "' + $ContainerOutputPath + '" 2>&1'
docker exec -d $MySqlContainer sh -c $MySqlCommand
$LockDeadline = (Get-Date).AddSeconds(10)
do {
    $LockOutput = (docker exec $MySqlContainer sh -c "test -f '$ContainerOutputPath' && cat '$ContainerOutputPath' || true") -join "`n"
    $ConnectionMatch = [regex]::Match($LockOutput, '(?m)^CONNECTION_ID:([0-9]+)$')
    $LockMatch = [regex]::Match($LockOutput, '(?m)^LOCK_ACQUIRED:([0-9]+):WAIT_TIMEOUT:([0-9]+):HOLD_SECONDS:([0-9]+)$')
    $LockAcquired = $LockMatch.Success
    $NoActiveFixture = $LockOutput -match '(?m)^NO_ACTIVE_FIXTURE$'
    $NamedLockFailed = $LockOutput -match '(?m)^NAMED_LOCK_FAILED:'
    if (-not $LockAcquired -and -not $NoActiveFixture -and -not $NamedLockFailed) { Start-Sleep -Seconds 1 }
} until ($LockAcquired -or $NoActiveFixture -or $NamedLockFailed -or (Get-Date) -ge $LockDeadline)
$LockOutput
if ($NoActiveFixture -or $NamedLockFailed) {
    $TerminalDeadline = (Get-Date).AddSeconds(10)
    do {
        $LockOutput = (docker exec $MySqlContainer sh -c "cat '$ContainerOutputPath'") -join "`n"
        $FixtureTerminated = $LockOutput -match '(?m)^FIXTURE_RESULTS:'
        if (-not $FixtureTerminated) { Start-Sleep -Seconds 1 }
    } until ($FixtureTerminated -or (Get-Date) -ge $TerminalDeadline)
    Remove-ReconciliationFixtureArtifacts
    if (-not $FixtureTerminated) { throw 'fixture 종료와 named lock release를 확인하지 못했습니다.' }
    throw 'ACTIVE fixture row lock을 확인하지 못했습니다.'
}
if (-not $LockAcquired) {
    if (-not $ConnectionMatch.Success) {
        Remove-ReconciliationFixtureArtifacts
        throw 'timeout된 MySQL fixture connection을 식별하지 못했습니다.'
    }
    $FixtureConnectionId = [int]$ConnectionMatch.Groups[1].Value
    if ((Invoke-MySqlScalar "SELECT COUNT(*) FROM information_schema.processlist WHERE ID=$FixtureConnectionId;") -eq '1') {
        Invoke-MySqlScalar "KILL $FixtureConnectionId;" | Out-Null
    }
    $AbortDeadline = (Get-Date).AddSeconds(10)
    do {
        $AbortStatus = Invoke-MySqlScalar "SELECT CONCAT('SESSION_COUNT=', (SELECT COUNT(*) FROM information_schema.processlist WHERE ID=$FixtureConnectionId), ':NAMED_LOCK_FREE=', IS_FREE_LOCK('inc-base-001-reconciliation'));"
        $FixtureAborted = $AbortStatus -eq 'SESSION_COUNT=0:NAMED_LOCK_FREE=1'
        if (-not $FixtureAborted) { Start-Sleep -Seconds 1 }
    } until ($FixtureAborted -or (Get-Date) -ge $AbortDeadline)
    Remove-ReconciliationFixtureArtifacts
    if (-not $FixtureAborted) { throw 'timeout된 fixture session의 rollback과 named lock release를 확인하지 못했습니다.' }
    throw 'ACTIVE fixture row lock을 10초 안에 확인하지 못해 fixture session을 종료했습니다.'
}
$HoldSeconds = [int]$LockMatch.Groups[3].Value
docker compose --env-file .env.local @ComposeFiles restart --timeout 10 backend
```

`LOCK_ACQUIRED:<subscription_id>:WAIT_TIMEOUT:<seconds>:HOLD_SECONDS:<seconds>`가 출력된 경우에만 Backend를 재시작한다. `NO_ACTIVE_FIXTURE` 또는 `NAMED_LOCK_FAILED`가 출력되면 실제 row lock이 없으므로 Backend를 재시작하지 않고 중단한다. failure metric과 `Lock wait timeout exceeded` log를 확인한 뒤 다음처럼 fixture 완료를 기다린다.

```powershell
function Get-PrometheusMetricValue([string]$Query) {
    $EncodedQuery = [uri]::EscapeDataString($Query)
    $Response = Invoke-RestMethod -TimeoutSec 10 "$PrometheusUrl/api/v1/query?query=$EncodedQuery"
    if ($Response.status -ne 'success' -or $Response.data.result.Count -ne 1) { return $null }
    return [double]$Response.data.result[0].value[1]
}
$ExecutionsBeforeRestart = Get-PrometheusMetricValue 'pawcycle_subscription_reconciliation_executions_total'
$FailuresBeforeRestart = Get-PrometheusMetricValue 'pawcycle_subscription_reconciliation_failures_total'
$FixtureDeadline = (Get-Date).AddSeconds($HoldSeconds + 10)
do {
    $LockOutput = (docker exec $MySqlContainer sh -c "cat '$ContainerOutputPath'") -join "`n"
    $FixtureCompleted = $LockOutput -match '(?m)^FIXTURE_RESULTS:GET_LOCK=1:SLEEP=0:RELEASE_LOCK=1$'
    if (-not $FixtureCompleted) { Start-Sleep -Seconds 1 }
} until ($FixtureCompleted -or (Get-Date) -ge $FixtureDeadline)
$LockOutput
if (-not $FixtureCompleted) { throw 'row lock rollback 또는 named lock release를 확인하지 못했습니다.' }
$RecoveryStartedAt = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
Remove-ReconciliationFixtureArtifacts
docker compose --env-file .env.local @ComposeFiles restart --timeout 10 backend
docker compose --env-file .env.local @ComposeFiles up --detach --wait --wait-timeout 180 backend
$RecoveryDeadline = (Get-Date).AddSeconds(180)
do {
    $RecoveryExecutions = Get-PrometheusMetricValue 'pawcycle_subscription_reconciliation_executions_total'
    $RecoveryFailures = Get-PrometheusMetricValue 'pawcycle_subscription_reconciliation_failures_total'
    $RecoveryLogs = (docker compose --env-file .env.local @ComposeFiles logs --since $RecoveryStartedAt backend) -join "`n"
    $RecoveryVerified = $null -ne $RecoveryExecutions -and $RecoveryExecutions -ge 1 -and $RecoveryFailures -eq 0 -and $RecoveryLogs -notmatch 'Lock wait timeout exceeded'
    if (-not $RecoveryVerified) { Start-Sleep -Seconds 1 }
} until ($RecoveryVerified -or (Get-Date) -ge $RecoveryDeadline)
"RECOVERY_METRICS:BEFORE_RESTART_EXECUTIONS=$ExecutionsBeforeRestart`:BEFORE_RESTART_FAILURES=$FailuresBeforeRestart`:AFTER_RESTART_EXECUTIONS=$RecoveryExecutions`:AFTER_RESTART_FAILURES=$RecoveryFailures"
if (-not $RecoveryVerified) { throw '원인 제거 후 reconciliation 실행 완료와 failure 비증가를 확인하지 못했습니다.' }
```

실측에서는 `2026-08-09T05:43:58Z` 기준 executions `1`, failures `0`에서 시작했다. `2026-08-09T05:45:41Z`에 health `UP`, `up=1`, executions `1`, processed `4`, failures `1`과 subscription `3`의 `Lock wait timeout exceeded` log를 확인했다. lock holder는 `ROLLBACK`과 named lock release를 완료했다. Backend를 재시작해 기존 즉시 실행을 사용한 `2026-08-09T05:46:30Z`에는 health `UP`, `up=1`, executions `1`, processed `4`, failures `0`이었다. 재시작으로 counter가 reset되므로 복구 판정은 과거 failure 값의 감소가 아니라 원인 제거 후 새 failure 증가가 없는지로 판단한다.

## 복구와 중단

모든 시나리오 후 MySQL·Backend·Prometheus·Grafana·Proxy가 정상인지 다시 확인한다. lock fixture는 marker로 확인한 `$HoldSeconds` 동안만 보유된 뒤 스스로 `ROLLBACK`·release되며 데이터 변경이 없어야 한다. row lock 확인이 10초를 넘기면 connection ID로 session을 종료하고 rollback·named lock release를 확인한다. active fixture가 없거나 lock release를 확인할 수 없거나 데이터 변경이 필요하면 reconciliation 재현을 수행하지 않고 중단한다.

최종 검증에서 Grafana health `ok`, Dashboard UID `pawcycle-local-observability`, 13개 panel과 `Backend scrape availability`의 `up{job="pawcycle-backend"}` query를 확인했다. Java 25와 격리된 MySQL 8.4에서 `ObservabilityIntegrationTests` 2개가 통과했다. 최종 local stack은 MySQL·Backend·Proxy healthy, Prometheus·Grafana running 상태였다.

저장소 변경의 rollback은 Dashboard와 이 Runbook을 일반 revert하는 것이다. local stack 종료는 `docker compose --env-file .env.local -f compose.yaml -f compose.observability.yaml down`을 사용하고 named volume은 삭제하지 않는다.
