param(
    [string]$EnvFile = (Join-Path (Split-Path $PSScriptRoot -Parent) '.env.local')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$LocalIntegrationDirectory = Split-Path $PSScriptRoot -Parent
$ComposeFiles = @(
    '-f', (Join-Path $LocalIntegrationDirectory 'compose.yaml'),
    '-f', (Join-Path $LocalIntegrationDirectory 'compose.observability.yaml'),
    '-f', (Join-Path $LocalIntegrationDirectory 'compose.incident.yaml')
)
$RunId = [guid]::NewGuid().ToString('N').Substring(0, 12)
$ProjectName = "pawcycle-inc-base-001-$RunId"
$ComposeArguments = @('--project-name', $ProjectName, '--env-file', (Resolve-Path -LiteralPath $EnvFile).Path) + $ComposeFiles
$FixturePath = Join-Path $PSScriptRoot 'INC-BASE-001-reconciliation-fixture.sql'
$LockSqlPath = Join-Path $env:TEMP "inc-base-001-lock-$RunId.sql"
$LockContainerPath = "/tmp/inc-base-001-lock-$RunId.sql"
$LockOutputPath = "/tmp/inc-base-001-lock-$RunId.out"
$ScalarSqlPath = Join-Path $env:TEMP "inc-base-001-scalar-$RunId.sql"
$ScalarContainerPath = "/tmp/inc-base-001-scalar-$RunId.sql"
$SharedVolumeNames = @(
    'pawcycle-local-integration-mysql-data',
    'pawcycle-local-integration-prometheus-data',
    'pawcycle-local-integration-grafana-data'
)
$BackendStartupBudgetSeconds = 180
$SchedulerStartBudgetSeconds = 15
$PrometheusScrapeIntervalSeconds = 15
$PrometheusScrapeTimeoutSeconds = 10
$CleanupSafetyMarginSeconds = 20
$LockHoldBudgetSeconds = $BackendStartupBudgetSeconds + $SchedulerStartBudgetSeconds + $PrometheusScrapeIntervalSeconds + $PrometheusScrapeTimeoutSeconds + $CleanupSafetyMarginSeconds
$SessionTerminationTimeoutSeconds = 10
$PreviousPrometheusPort = $env:PAWCYCLE_LOCAL_PROMETHEUS_PORT
$PreviousGrafanaPort = $env:PAWCYCLE_LOCAL_GRAFANA_PORT
$TemporaryEnvironmentNames = @(
    'MYSQL_DATABASE',
    'MYSQL_USER',
    'MYSQL_PASSWORD',
    'MYSQL_ROOT_PASSWORD',
    'PAWCYCLE_LOCAL_QA_BOOTSTRAP_EMAIL',
    'PAWCYCLE_LOCAL_QA_BOOTSTRAP_PASSWORD'
)
$PreviousTemporaryEnvironment = @{}
$TemporaryEnvironmentNames | ForEach-Object {
    $PreviousTemporaryEnvironment[$_] = [Environment]::GetEnvironmentVariable($_, [EnvironmentVariableTarget]::Process)
}
$CleanupRequired = $false
$DisposableVolumeNames = @()
$MySqlContainer = $null
$FixtureConnectionId = $null
$PrometheusPort = $null

function Invoke-Compose([string[]]$Command) {
    & docker compose @ComposeArguments @Command
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose command failed: $($Command[0])" }
}

function Invoke-ComposeCapture([string[]]$Command) {
    $Output = & docker compose @ComposeArguments @Command
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose command failed: $($Command[0])" }
    return ($Output -join "`n").Trim()
}

function Invoke-MySqlScalar([string]$Statement, [switch]$Root) {
    $UserArguments = if ($Root) {
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=TCP --host=127.0.0.1 --user=root --database="$MYSQL_DATABASE" --batch --skip-column-names'
    } else {
        'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP --host=127.0.0.1 --user="$MYSQL_USER" --database="$MYSQL_DATABASE" --batch --skip-column-names'
    }
    [IO.File]::WriteAllText($ScalarSqlPath, $Statement + "`n", [Text.UTF8Encoding]::new($false))
    & docker cp $ScalarSqlPath "${MySqlContainer}:$ScalarContainerPath" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Disposable MySQL scalar SQL copy failed.' }
    $Command = $UserArguments + ' < "' + $ScalarContainerPath + '"'
    $Output = & docker exec $MySqlContainer sh -c $Command
    if ($LASTEXITCODE -ne 0) { throw 'Disposable MySQL query failed.' }
    return ($Output -join "`n").Trim()
}

function Wait-MySqlSessionExit([long]$ConnectionId) {
    $Deadline = (Get-Date).AddSeconds($SessionTerminationTimeoutSeconds)
    do {
        $SessionCount = Invoke-MySqlScalar "SELECT COUNT(*) FROM information_schema.processlist WHERE ID=$ConnectionId;" -Root
        if ($SessionCount -eq '0') { return $true }
        Start-Sleep -Seconds 1
    } until ((Get-Date) -ge $Deadline)
    return $false
}

function Stop-MySqlSession([long]$ConnectionId) {
    $SessionCount = Invoke-MySqlScalar "SELECT COUNT(*) FROM information_schema.processlist WHERE ID=$ConnectionId;" -Root
    if ($SessionCount -eq '0') { return }
    try {
        Invoke-MySqlScalar "KILL $ConnectionId;" -Root | Out-Null
    } catch {
        if ((Invoke-MySqlScalar "SELECT COUNT(*) FROM information_schema.processlist WHERE ID=$ConnectionId;" -Root) -ne '0') { throw }
        return
    }
    if (-not (Wait-MySqlSessionExit $ConnectionId)) { throw "Disposable lock session did not terminate within $SessionTerminationTimeoutSeconds seconds." }
}

function Format-DockerLogSince([datetime]$Timestamp) {
    return $Timestamp.ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ss.fffffffZ')
}

function Get-BackendJson([string]$Url) {
    $Output = & docker exec $BackendContainer curl --fail --silent --show-error --max-time 10 $Url
    if ($LASTEXITCODE -ne 0) { throw "Internal request failed: $Url" }
    return (($Output -join "`n") | ConvertFrom-Json)
}

function New-DisposableSecret {
    $Bytes = [byte[]]::new(32)
    $RandomNumberGenerator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $RandomNumberGenerator.GetBytes($Bytes)
    } finally {
        $RandomNumberGenerator.Dispose()
    }
    return -join ($Bytes | ForEach-Object { $_.ToString('x2') })
}

function Set-PrometheusPort {
    $PublishedAddress = Invoke-ComposeCapture @('port', 'prometheus', '9090')
    $PortMatch = [regex]::Match($PublishedAddress, ':(\d+)$')
    if (-not $PortMatch.Success) { throw "Disposable Prometheus port is invalid: $PublishedAddress" }
    $script:PrometheusPort = [int]$PortMatch.Groups[1].Value
}

function Get-PrometheusJson([string]$Path) {
    if (-not $PrometheusPort) { throw 'Disposable Prometheus port is not available.' }
    return Invoke-RestMethod -Uri "http://127.0.0.1:$PrometheusPort$Path" -TimeoutSec 10
}

function Get-PrometheusMetricValue([string]$Query) {
    $EncodedQuery = [uri]::EscapeDataString($Query)
    $Response = Get-PrometheusJson "/api/v1/query?query=$EncodedQuery"
    if ($Response.status -ne 'success' -or $Response.data.result.Count -ne 1) { return $null }
    return [double]$Response.data.result[0].value[1]
}

function Test-PrometheusAlertRuleLoaded([string]$AlertName) {
    $Response = Get-PrometheusJson '/api/v1/rules'
    if ($Response.status -ne 'success') { return $false }
    foreach ($Group in @($Response.data.groups)) {
        foreach ($Rule in @($Group.rules)) {
            if ($Rule.name -eq $AlertName) { return $true }
        }
    }
    return $false
}

function Get-PrometheusAlertState([string]$AlertName) {
    $Response = Get-PrometheusJson '/api/v1/alerts'
    if ($Response.status -ne 'success') { throw "Prometheus alert query failed for $AlertName" }
    $Alerts = @($Response.data.alerts | Where-Object { $_.labels.alertname -eq $AlertName })
    if ($Alerts.Count -gt 1) { throw "Prometheus returned multiple active alerts named $AlertName" }
    if ($Alerts.Count -eq 0) { return $null }
    return [string]$Alerts[0].state
}

function Get-SharedVolumeState {
    $State = @{}
    foreach ($VolumeName in $SharedVolumeNames) {
        $Value = & docker volume inspect --format '{{.Name}}|{{.CreatedAt}}' $VolumeName 2>$null
        if ($LASTEXITCODE -eq 0) { $State[$VolumeName] = ($Value -join "`n").Trim() }
    }
    return $State
}

function Assert-SharedVolumesUnchanged([hashtable]$Before, [hashtable]$After) {
    foreach ($VolumeName in $SharedVolumeNames) {
        if ($Before.ContainsKey($VolumeName) -ne $After.ContainsKey($VolumeName)) {
            throw "Shared volume presence changed: $VolumeName"
        }
        if ($Before.ContainsKey($VolumeName) -and $Before[$VolumeName] -ne $After[$VolumeName]) {
            throw "Shared volume metadata changed: $VolumeName"
        }
    }
}

if (-not (Test-Path -LiteralPath $EnvFile)) { throw "Local env file not found: $EnvFile" }
if (-not (Test-Path -LiteralPath $FixturePath)) { throw "Fixture file not found: $FixturePath" }

$SharedVolumesBefore = Get-SharedVolumeState
$env:MYSQL_DATABASE = "pawcycle_inc_$RunId"
$env:MYSQL_USER = 'pawcycle'
$env:MYSQL_PASSWORD = New-DisposableSecret
$env:MYSQL_ROOT_PASSWORD = New-DisposableSecret
$env:PAWCYCLE_LOCAL_QA_BOOTSTRAP_EMAIL = "qa-foundation-004@alert-$RunId.test"
$env:PAWCYCLE_LOCAL_QA_BOOTSTRAP_PASSWORD = New-DisposableSecret
$env:PAWCYCLE_LOCAL_PROMETHEUS_PORT = '0'
$env:PAWCYCLE_LOCAL_GRAFANA_PORT = '0'
$ExecutionError = $null

try {
    $ConfigText = Invoke-ComposeCapture @('config', '--format', 'json')
    $Config = $ConfigText | ConvertFrom-Json
    $ExpectedMounts = @{
        mysql = 'inc-base-001-mysql-data'
        prometheus = 'inc-base-001-prometheus-data'
        grafana = 'inc-base-001-grafana-data'
    }
    foreach ($ServiceName in $ExpectedMounts.Keys) {
        $DataMount = @($Config.services.$ServiceName.volumes | Where-Object { $_.type -eq 'volume' })
        if ($DataMount.Count -ne 1 -or $DataMount[0].source -ne $ExpectedMounts[$ServiceName]) {
            throw "Disposable volume isolation is invalid for service: $ServiceName"
        }
    }
    $RuleMount = @($Config.services.prometheus.volumes | Where-Object { $_.target -eq '/etc/prometheus/rules' })
    if ($RuleMount.Count -ne 1 -or -not $RuleMount[0].read_only) {
        throw 'Disposable Prometheus alert rule mount is invalid.'
    }
    $ConfiguredVolumeKeys = @($Config.volumes.PSObject.Properties.Name | Sort-Object)
    $ExpectedVolumeKeys = @($ExpectedMounts.Values | Sort-Object)
    if (Compare-Object $ExpectedVolumeKeys $ConfiguredVolumeKeys) {
        throw 'Merged Compose model contains a non-disposable volume declaration.'
    }
    $DisposableVolumeNames = @($ExpectedVolumeKeys | ForEach-Object {
        $VolumeName = $Config.volumes.PSObject.Properties[$_].Value.name
        if (-not $VolumeName.StartsWith("${ProjectName}_") -or $SharedVolumeNames -contains $VolumeName) {
            throw "Disposable volume name is unsafe: $VolumeName"
        }
        $VolumeName
    })

    Invoke-Compose @('config', '--quiet')
    $CleanupRequired = $true
    Invoke-Compose @('up', '--detach', '--build', '--wait', '--wait-timeout', "$BackendStartupBudgetSeconds", 'mysql', 'backend', 'prometheus')
    $BackendContainer = Invoke-ComposeCapture @('ps', '--quiet', 'backend')
    if (-not $BackendContainer) { throw 'Disposable Backend container not found.' }
    Set-PrometheusPort
    $AlertLoadDeadline = (Get-Date).AddSeconds($PrometheusScrapeIntervalSeconds + $PrometheusScrapeTimeoutSeconds + 20)
    do {
        try {
            $InitialTargetUp = Get-PrometheusMetricValue 'up{job="pawcycle-backend"}'
            $InitialBackendAlert = Get-PrometheusAlertState 'PawCycleBackendScrapeUnavailable'
            $InitialReconciliationAlert = Get-PrometheusAlertState 'PawCycleReconciliationFailure'
            $AlertRulesLoaded = (Test-PrometheusAlertRuleLoaded 'PawCycleBackendScrapeUnavailable') -and (Test-PrometheusAlertRuleLoaded 'PawCycleReconciliationFailure')
            $AlertsReady = $AlertRulesLoaded -and $InitialTargetUp -eq 1 -and -not $InitialBackendAlert -and -not $InitialReconciliationAlert
        } catch {
            $AlertsReady = $false
        }
        if (-not $AlertsReady) { Start-Sleep -Seconds 2 }
    } until ($AlertsReady -or (Get-Date) -ge $AlertLoadDeadline)
    if (-not $AlertsReady) { throw 'Disposable Prometheus alert rules did not load in a normal state.' }

    Invoke-Compose @('stop', '--timeout', '10', 'backend')
    $BackendAlertDeadline = (Get-Date).AddSeconds((3 * $PrometheusScrapeIntervalSeconds) + $PrometheusScrapeTimeoutSeconds + 20)
    $BackendAlertPendingObserved = $false
    do {
        try {
            $BackendAlertState = Get-PrometheusAlertState 'PawCycleBackendScrapeUnavailable'
            if ($BackendAlertState -eq 'pending') { $BackendAlertPendingObserved = $true }
            $BackendAlertFiringObserved = $BackendAlertPendingObserved -and $BackendAlertState -eq 'firing'
        } catch {
            $BackendAlertFiringObserved = $false
        }
        if (-not $BackendAlertFiringObserved) { Start-Sleep -Seconds 2 }
    } until ($BackendAlertFiringObserved -or (Get-Date) -ge $BackendAlertDeadline)
    if (-not $BackendAlertFiringObserved) { throw 'Disposable Backend scrape unavailable alert did not transition from pending to firing.' }

    Invoke-Compose @('up', '--detach', '--wait', '--wait-timeout', "$BackendStartupBudgetSeconds", 'backend')
    $BackendContainer = Invoke-ComposeCapture @('ps', '--quiet', 'backend')
    $BackendAlertRecoveryDeadline = (Get-Date).AddSeconds($PrometheusScrapeIntervalSeconds + $PrometheusScrapeTimeoutSeconds + 20)
    do {
        try {
            $BackendAlertRecovered = (Get-PrometheusMetricValue 'up{job="pawcycle-backend"}') -eq 1 -and -not (Get-PrometheusAlertState 'PawCycleBackendScrapeUnavailable')
        } catch {
            $BackendAlertRecovered = $false
        }
        if (-not $BackendAlertRecovered) { Start-Sleep -Seconds 2 }
    } until ($BackendAlertRecovered -or (Get-Date) -ge $BackendAlertRecoveryDeadline)
    if (-not $BackendAlertRecovered) { throw 'Disposable Backend scrape unavailable alert did not resolve after recovery.' }

    Invoke-Compose @('stop', '--timeout', '10', 'backend')

    $MySqlContainer = Invoke-ComposeCapture @('ps', '--quiet', 'mysql')
    if (-not $MySqlContainer) { throw 'Disposable MySQL container not found.' }
    & docker cp $FixturePath "${MySqlContainer}:/tmp/inc-base-001-fixture.sql" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Disposable fixture copy failed.' }
    $FixtureCommand = 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP --host=127.0.0.1 --user="$MYSQL_USER" --database="$MYSQL_DATABASE" --batch --skip-column-names < /tmp/inc-base-001-fixture.sql'
    $FixtureOutput = (& docker exec $MySqlContainer sh -c $FixtureCommand) -join "`n"
    if ($LASTEXITCODE -ne 0 -or $FixtureOutput -notmatch '(?m)^FIXTURE_READY:SUBSCRIPTION_ID=([0-9]+)$') {
        throw 'Disposable fixture creation failed.'
    }
    $FixtureSubscriptionId = [long]$Matches[1]
    $FixtureFingerprintQuery = "SELECT CONCAT_WS(':', status, version, current_snapshot_id, (SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=$FixtureSubscriptionId), (SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=$FixtureSubscriptionId AND scheduled_date<CURRENT_DATE)) FROM subscriptions WHERE id=$FixtureSubscriptionId;"
    $FixtureFingerprintBefore = Invoke-MySqlScalar $FixtureFingerprintQuery

    $LockSql = @"
SELECT CONCAT('CONNECTION_ID:', CONNECTION_ID());
SELECT @@GLOBAL.innodb_lock_wait_timeout INTO @lock_wait_timeout;
SET @hold_seconds := @lock_wait_timeout + $LockHoldBudgetSeconds;
START TRANSACTION;
SELECT id FROM subscriptions WHERE id=$FixtureSubscriptionId FOR UPDATE;
SELECT CONCAT('LOCK_ACQUIRED:${FixtureSubscriptionId}:WAIT_TIMEOUT:', @lock_wait_timeout, ':HOLD_SECONDS:', @hold_seconds);
SELECT SLEEP(@hold_seconds);
ROLLBACK;
SELECT 'LOCK_RELEASED';
"@
    [IO.File]::WriteAllText($LockSqlPath, $LockSql, [Text.UTF8Encoding]::new($false))
    & docker cp $LockSqlPath "${MySqlContainer}:$LockContainerPath" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Disposable lock SQL copy failed.' }
    $LockCommand = 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP --host=127.0.0.1 --user="$MYSQL_USER" --database="$MYSQL_DATABASE" --batch --skip-column-names --unbuffered < "' + $LockContainerPath + '" > "' + $LockOutputPath + '" 2>&1'
    & docker exec --detach $MySqlContainer sh -c $LockCommand
    if ($LASTEXITCODE -ne 0) { throw 'Disposable lock session start failed.' }

    $LockDeadline = (Get-Date).AddSeconds(15)
    do {
        $LockOutput = (& docker exec $MySqlContainer sh -c "test -f '$LockOutputPath' && cat '$LockOutputPath' || true") -join "`n"
        $LockMatch = [regex]::Match($LockOutput, '(?m)^LOCK_ACQUIRED:([0-9]+):WAIT_TIMEOUT:([0-9]+):HOLD_SECONDS:([0-9]+)$')
        $ConnectionMatch = [regex]::Match($LockOutput, '(?m)^CONNECTION_ID:([0-9]+)$')
        if (-not $LockMatch.Success -or -not $ConnectionMatch.Success) { Start-Sleep -Seconds 1 }
    } until (($LockMatch.Success -and $ConnectionMatch.Success) -or (Get-Date) -ge $LockDeadline)
    if (-not $LockMatch.Success -or -not $ConnectionMatch.Success) {
        throw "Disposable row lock was not acquired. MySQL output: $LockOutput"
    }
    $FixtureConnectionId = [long]$ConnectionMatch.Groups[1].Value
    $LockWaitTimeout = [int]$LockMatch.Groups[2].Value
    $HoldSeconds = [int]$LockMatch.Groups[3].Value

    $FailureStartedAt = (Get-Date).ToUniversalTime()
    Invoke-Compose @('up', '--detach', '--wait', '--wait-timeout', "$BackendStartupBudgetSeconds", 'backend', 'prometheus', 'grafana')
    $BackendContainer = Invoke-ComposeCapture @('ps', '--quiet', 'backend')
    if (-not $BackendContainer) { throw 'Disposable Backend container not found.' }

    $FailureDeadline = $FailureStartedAt.AddSeconds($BackendStartupBudgetSeconds + $SchedulerStartBudgetSeconds + $LockWaitTimeout + $PrometheusScrapeIntervalSeconds + $PrometheusScrapeTimeoutSeconds)
    do {
        try {
            $FailureCount = Get-PrometheusMetricValue 'pawcycle_subscription_reconciliation_failures_total'
            $TargetUp = Get-PrometheusMetricValue 'up{job="pawcycle-backend"}'
            $FailureLogs = (& docker logs --since (Format-DockerLogSince $FailureStartedAt) $BackendContainer) -join "`n"
            $FailureObserved = $FailureCount -ge 1 -and $TargetUp -eq 1 -and $FailureLogs -match "(?s)Subscription reconciliation failed; subscriptionId=$FixtureSubscriptionId.*Lock wait timeout exceeded"
        } catch {
            $FailureObserved = $false
        }
        if (-not $FailureObserved) { Start-Sleep -Seconds 2 }
    } until ($FailureObserved -or (Get-Date).ToUniversalTime() -ge $FailureDeadline)
    if (-not $FailureObserved) { throw 'Disposable reconciliation failure signals were not observed.' }

    $ReconciliationAlertDeadline = (Get-Date).AddSeconds($PrometheusScrapeIntervalSeconds + $PrometheusScrapeTimeoutSeconds + 20)
    do {
        try {
            $ReconciliationAlertFiringObserved = (Get-PrometheusAlertState 'PawCycleReconciliationFailure') -eq 'firing'
        } catch {
            $ReconciliationAlertFiringObserved = $false
        }
        if (-not $ReconciliationAlertFiringObserved) { Start-Sleep -Seconds 2 }
    } until ($ReconciliationAlertFiringObserved -or (Get-Date) -ge $ReconciliationAlertDeadline)
    if (-not $ReconciliationAlertFiringObserved) { throw 'Disposable reconciliation failure alert did not fire.' }

    Stop-MySqlSession $FixtureConnectionId
    $FixtureConnectionId = $null
    $FixtureFingerprintAfterFailure = Invoke-MySqlScalar $FixtureFingerprintQuery
    if ($FixtureFingerprintAfterFailure -ne $FixtureFingerprintBefore) { throw 'Disposable fixture changed during failure reproduction.' }

    $RecoveryStartedAt = (Get-Date).ToUniversalTime()
    Invoke-Compose @('restart', '--timeout', '10', 'backend')
    Invoke-Compose @('up', '--detach', '--wait', '--wait-timeout', "$BackendStartupBudgetSeconds", 'backend')
    $BackendContainer = Invoke-ComposeCapture @('ps', '--quiet', 'backend')
    $RecoveryDeadline = $RecoveryStartedAt.AddSeconds($BackendStartupBudgetSeconds + $SchedulerStartBudgetSeconds + (3 * $PrometheusScrapeIntervalSeconds) + $PrometheusScrapeTimeoutSeconds)
    do {
        try {
            $RecoveryExecutions = Get-PrometheusMetricValue 'pawcycle_subscription_reconciliation_executions_total'
            $RecoveryFailures = Get-PrometheusMetricValue 'pawcycle_subscription_reconciliation_failures_total'
            $RecoveryUp = Get-PrometheusMetricValue 'up{job="pawcycle-backend"}'
            $ReconciliationAlertResolved = -not (Get-PrometheusAlertState 'PawCycleReconciliationFailure')
            $RecoveryLogs = (& docker logs --since (Format-DockerLogSince $RecoveryStartedAt) $BackendContainer) -join "`n"
            $RecoveryObserved = $RecoveryExecutions -ge 1 -and $RecoveryFailures -eq 0 -and $RecoveryUp -eq 1 -and $ReconciliationAlertResolved -and $RecoveryLogs -notmatch 'Subscription reconciliation failed'
        } catch {
            $RecoveryObserved = $false
        }
        if (-not $RecoveryObserved) { Start-Sleep -Seconds 2 }
    } until ($RecoveryObserved -or (Get-Date).ToUniversalTime() -ge $RecoveryDeadline)
    if (-not $RecoveryObserved) { throw 'Disposable reconciliation recovery signals were not observed.' }

    $GrafanaHealth = Get-BackendJson 'http://grafana:3000/api/health'
    $GrafanaDatasource = Get-BackendJson 'http://grafana:3000/api/datasources/uid/pawcycle-prometheus'
    $GrafanaDashboard = Get-BackendJson 'http://grafana:3000/api/dashboards/uid/pawcycle-local-observability'
    if ($GrafanaHealth.database -ne 'ok' -or $GrafanaDatasource.uid -ne 'pawcycle-prometheus' -or $GrafanaDashboard.dashboard.panels.Count -ne 18) {
        throw 'Disposable Grafana provisioning validation failed.'
    }
    $FixtureFingerprintAfterRecovery = Invoke-MySqlScalar $FixtureFingerprintQuery
    if ($FixtureFingerprintAfterRecovery -ne $FixtureFingerprintBefore) { throw 'Disposable fixture changed after recovery.' }

    "DISPOSABLE_PROJECT=$ProjectName"
    'BACKEND_SCRAPE_ALERT:INITIAL=NORMAL:PENDING=OBSERVED:FIRING=OBSERVED:RESOLVED=PASS'
    "FAILURE_EVIDENCE:SUBSCRIPTION_ID=$FixtureSubscriptionId`:FAILURES=$FailureCount`:TARGET_UP=$TargetUp`:LOCK_WAIT_SECONDS=$LockWaitTimeout`:HOLD_SECONDS=$HoldSeconds"
    'RECONCILIATION_FAILURE_ALERT:FIRING=OBSERVED:RESOLVED=PASS'
    "RECOVERY_EVIDENCE:EXECUTIONS=$RecoveryExecutions`:FAILURES=$RecoveryFailures`:TARGET_UP=$RecoveryUp"
    'GRAFANA_EVIDENCE:DATASOURCE=pawcycle-prometheus:DASHBOARD=pawcycle-local-observability:PANELS=18'
    'FIXTURE_DATA_UNCHANGED=PASS'
} catch {
    $ExecutionError = $_
} finally {
    $CleanupErrors = [Collections.Generic.List[Exception]]::new()
    if ($MySqlContainer -and $FixtureConnectionId) {
        try { Stop-MySqlSession $FixtureConnectionId } catch { [void]$CleanupErrors.Add($_.Exception) }
    }
    if ($CleanupRequired) {
        try { Invoke-Compose @('down', '--volumes', '--remove-orphans', '--rmi', 'local') } catch { [void]$CleanupErrors.Add($_.Exception) }
        try {
            $RemainingContainers = @(& docker ps --all --filter "label=com.docker.compose.project=$ProjectName" --format '{{.ID}}')
            if ($LASTEXITCODE -ne 0 -or $RemainingContainers.Count -ne 0) { [void]$CleanupErrors.Add([InvalidOperationException]::new('disposable containers remain')) }
        } catch { [void]$CleanupErrors.Add($_.Exception) }
        try {
            $RemainingNetworks = @(& docker network ls --filter "label=com.docker.compose.project=$ProjectName" --format '{{.ID}}')
            if ($LASTEXITCODE -ne 0 -or $RemainingNetworks.Count -ne 0) { [void]$CleanupErrors.Add([InvalidOperationException]::new('disposable networks remain')) }
        } catch { [void]$CleanupErrors.Add($_.Exception) }
        try {
            $AvailableVolumes = @(& docker volume ls --format '{{.Name}}')
            if ($LASTEXITCODE -ne 0) { [void]$CleanupErrors.Add([InvalidOperationException]::new('disposable volume list failed')) }
            foreach ($VolumeName in $DisposableVolumeNames) {
                if ($AvailableVolumes -contains $VolumeName) { [void]$CleanupErrors.Add([InvalidOperationException]::new("disposable volume remains: $VolumeName")) }
            }
        } catch { [void]$CleanupErrors.Add($_.Exception) }
    }
    try { if (Test-Path -LiteralPath $LockSqlPath) { Remove-Item -LiteralPath $LockSqlPath } } catch { [void]$CleanupErrors.Add($_.Exception) }
    try { if (Test-Path -LiteralPath $ScalarSqlPath) { Remove-Item -LiteralPath $ScalarSqlPath } } catch { [void]$CleanupErrors.Add($_.Exception) }
    $env:PAWCYCLE_LOCAL_PROMETHEUS_PORT = $PreviousPrometheusPort
    $env:PAWCYCLE_LOCAL_GRAFANA_PORT = $PreviousGrafanaPort
    foreach ($EnvironmentName in $TemporaryEnvironmentNames) {
        [Environment]::SetEnvironmentVariable($EnvironmentName, $PreviousTemporaryEnvironment[$EnvironmentName], [EnvironmentVariableTarget]::Process)
    }
    try {
        $SharedVolumesAfter = Get-SharedVolumeState
        Assert-SharedVolumesUnchanged $SharedVolumesBefore $SharedVolumesAfter
    } catch { [void]$CleanupErrors.Add($_.Exception) }
    if ($CleanupErrors.Count -ne 0) {
        $AllErrors = [Collections.Generic.List[Exception]]::new()
        if ($ExecutionError) { [void]$AllErrors.Add($ExecutionError.Exception) }
        foreach ($CleanupError in $CleanupErrors) { [void]$AllErrors.Add($CleanupError) }
        throw [AggregateException]::new('Disposable verification and cleanup failed.', [Exception[]]$AllErrors.ToArray())
    }
}

if ($ExecutionError) { throw $ExecutionError }
'DISPOSABLE_CLEANUP=PASS'
