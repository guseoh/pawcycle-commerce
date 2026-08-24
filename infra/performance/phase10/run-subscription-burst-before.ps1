[CmdletBinding()]
param(
    [ValidateSet(100, 500, 1000, 2500, 5000, 10000)]
    [int]$CohortSize = 100,
    [string]$HttpCommand = 'curl.exe',
    [string]$ResultsDir = (Join-Path $env:TEMP 'pawcycle-phase10-subscription-burst-before'),
    [string]$EvidenceSourceSummaryPath = '',
    [string]$EvidenceMarkerPath = '',
    [switch]$ValidateOnly,
    [switch]$ValidateRuntimeCapability,
    [switch]$InspectEvidenceState,
    [switch]$PromoteEvidence,
    [switch]$RunBeforeFirstResult,
    [switch]$CleanupIsolatedRuntime
)

$ErrorActionPreference = 'Stop'
$RepoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$ResultsDir = [IO.Path]::GetFullPath($ResultsDir)
$TempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$repoRootPrefix = $RepoRoot.TrimEnd([char[]]@([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)) + [IO.Path]::DirectorySeparatorChar
$tempRootPrefix = $TempRoot.TrimEnd([char[]]@([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)) + [IO.Path]::DirectorySeparatorChar
$LocalIntegrationDir = Join-Path $RepoRoot 'infra\local-integration'
$MeasurementServiceSource = Join-Path $RepoRoot 'backend\src\main\java\com\pawcycle\backend\subscription\v2\performance\SubscriptionBurstMeasurementService.java'
$OverlayPath = Join-Path $LocalIntegrationDir 'compose.phase10-subscription-burst.yaml'
$MarkerDir = Join-Path $TempRoot 'pawcycle-phase10-subscription-burst-before-marker'
$FirstResultMarker = Join-Path $MarkerDir 'workload-started.json'
$SummaryPath = Join-Path $ResultsDir 'subscription-burst-before-summary.json'
$DurableEvidenceDir = Join-Path $RepoRoot 'docs\reports\PERF-PH10-003\evidence-candidates'
$HistoricalFirstResultContract = [ordered]@{
    workloadIdentity = 'phase10-subscription-burst-before-local'
    cohort = 5000
    sourceSha = '3f11a5cc6489d3096d024290008a1b91fabe634c'
}
$WorkloadIdentity = [string]$HistoricalFirstResultContract.workloadIdentity
$FirstResultAuthoritativelyConsumed = $true
$EvidenceSourceSummaryPath = if ([string]::IsNullOrWhiteSpace($EvidenceSourceSummaryPath)) { $SummaryPath } else { [IO.Path]::GetFullPath($EvidenceSourceSummaryPath) }
$EvidenceMarkerPath = if ([string]::IsNullOrWhiteSpace($EvidenceMarkerPath)) { $FirstResultMarker } else { [IO.Path]::GetFullPath($EvidenceMarkerPath) }
$DriverStdout = Join-Path $ResultsDir 'driver.stdout.json'
$DriverStderr = Join-Path $ResultsDir 'driver.stderr.log'
$BackendContainer = 'pawcycle-phase10-subscription-burst-backend-1'
$MysqlContainer = 'pawcycle-phase10-subscription-burst-mysql-1'
$ExpectedMysqlVolume = 'pawcycle-phase10-subscription-burst-mysql-data'
$DefaultBatchSize = 100
$DefaultFixedDelayMs = 60000
$env:PAWCYCLE_PHASE10_SUBSCRIPTION_BURST_MARKER_DIR = $MarkerDir
$env:PAWCYCLE_LOCAL_PROMETHEUS_PORT = '0'

function Assert-SafeHostTempPath([string]$Path, [string]$Label) {
    $normalized = [IO.Path]::GetFullPath($Path)
    if ($normalized.Equals($RepoRoot, [StringComparison]::OrdinalIgnoreCase) -or $normalized.StartsWith($repoRootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "$Label must be outside the repository."
    }
    if (-not ($normalized.Equals($TempRoot, [StringComparison]::OrdinalIgnoreCase) -or $normalized.StartsWith($tempRootPrefix, [StringComparison]::OrdinalIgnoreCase))) {
        throw "$Label must be the host local temp directory or one of its descendants."
    }
}

function Get-ComposeArgs {
    return @(
        '--env-file', '.env.local',
        '-f', 'compose.yaml',
        '-f', 'compose.prometheus.yaml',
        '-f', 'compose.phase9-envelope.yaml',
        '-f', 'compose.phase9-tomcat128.yaml',
        '-f', 'compose.phase9-cpu15.yaml',
        '-f', 'compose.phase9-memory1g.yaml',
        '-f', 'compose.phase9-cpu20.yaml',
        '-f', 'compose.phase10-subscription-burst.yaml'
    )
}

function Invoke-Compose([object[]]$Arguments) {
    Push-Location $LocalIntegrationDir
    try {
        & docker compose @(Get-ComposeArgs) @Arguments
        if ($LASTEXITCODE -ne 0) { throw "docker compose failed: $($Arguments -join ' ')" }
    } finally {
        Pop-Location
    }
}

function Get-PublishedLoopbackUrl([string]$Service, [int]$ContainerPort) {
    Push-Location $LocalIntegrationDir
    try {
        $output = docker compose @(Get-ComposeArgs) port $Service $ContainerPort
        if ($LASTEXITCODE -ne 0) { throw "$Service published port lookup failed." }
        $match = [regex]::Match(($output | Select-Object -First 1), ':(?<port>[0-9]+)$')
        if (-not $match.Success) { throw "$Service published port is unavailable." }
        return "http://127.0.0.1:$($match.Groups['port'].Value)"
    } finally {
        Pop-Location
    }
}

function Reset-IsolatedRuntime {
    Invoke-Compose @('down', '--volumes')
}

function Assert-IsolatedRuntime {
    $backend = @(docker inspect --format 'health={{if .State.Health}}{{.State.Health.Status}}{{else}}no-health{{end}}|cpu={{.HostConfig.NanoCpus}}|memory={{.HostConfig.Memory}}|pids={{.HostConfig.PidsLimit}}|restart={{.RestartCount}}|oom={{.State.OOMKilled}}' $BackendContainer)
    if ($LASTEXITCODE -ne 0 -or $backend.Count -ne 1 -or $backend[0] -ne 'health=healthy|cpu=2000000000|memory=1073741824|pids=256|restart=0|oom=false') {
        throw 'Backend is not the expected isolated CPU2.0/memory1GiB/PID256 runtime.'
    }
    $mysqlVolume = @(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/mysql"}}{{.Name}}{{end}}{{end}}' $MysqlContainer)
    if ($LASTEXITCODE -ne 0 -or $mysqlVolume.Count -ne 1 -or $mysqlVolume[0] -ne $ExpectedMysqlVolume) {
        throw 'MySQL is not using the dedicated disposable Subscription Burst volume.'
    }
    $jvm = @(docker exec $BackendContainer sh -lc 'java -XX:+PrintFlagsFinal -version 2>&1')
    $exitCode = $LASTEXITCODE
    $flags = @($jvm | Where-Object { [string]$_ -match '^\s*double\s+MaxRAMPercentage\s*=' })
    if ($exitCode -ne 0 -or $flags.Count -ne 1 -or [string]$flags[0] -notmatch 'MaxRAMPercentage\s*=\s*65(?:\.0+)?\s') {
        throw 'Effective MaxRAMPercentage=65 was not observed exactly once.'
    }
}

function Query-Prometheus([string]$PrometheusUrl, [string]$Query) {
    $response = Invoke-RestMethod -TimeoutSec 3 -Uri "$PrometheusUrl/api/v1/query" -Body @{ query = $Query }
    if ($response.status -ne 'success' -or $response.data.result.Count -ne 1) { throw "Prometheus metric unavailable: $Query" }
    return [double]$response.data.result[0].value[1]
}

function Get-PrometheusSnapshot([string]$PrometheusUrl, [string]$Label) {
    return [ordered]@{
        label = $Label
        capturedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
        automationExecutions = Query-Prometheus $PrometheusUrl 'sum(pawcycle_subscription_automation_executions_total)'
        automationProcessed = Query-Prometheus $PrometheusUrl 'sum(pawcycle_subscription_automation_processed_candidates_total)'
        automationCreated = Query-Prometheus $PrometheusUrl 'sum(pawcycle_subscription_automation_orders_total)'
        automationFailures = Query-Prometheus $PrometheusUrl 'sum(pawcycle_subscription_automation_failures_total)'
        automationDuplicateNoOp = Query-Prometheus $PrometheusUrl 'sum(pawcycle_subscription_automation_duplicate_noop_total)'
        automationDurationCount = Query-Prometheus $PrometheusUrl 'sum(pawcycle_subscription_automation_duration_seconds_count)'
        automationDurationSeconds = Query-Prometheus $PrometheusUrl 'sum(pawcycle_subscription_automation_duration_seconds_sum)'
        processCpuUsage = Query-Prometheus $PrometheusUrl 'sum(process_cpu_usage)'
        jvmHeapUsed = Query-Prometheus $PrometheusUrl 'sum(jvm_memory_used_bytes{area="heap"})'
        jvmNonHeapUsed = Query-Prometheus $PrometheusUrl 'sum(jvm_memory_used_bytes{area="nonheap"})'
        jvmGcPauseCount = Query-Prometheus $PrometheusUrl 'sum(jvm_gc_pause_seconds_count)'
        jvmGcPauseSeconds = Query-Prometheus $PrometheusUrl 'sum(jvm_gc_pause_seconds_sum)'
        jvmLiveThreads = Query-Prometheus $PrometheusUrl 'sum(jvm_threads_live_threads)'
        jvmPeakThreads = Query-Prometheus $PrometheusUrl 'sum(jvm_threads_peak_threads)'
        hikariActive = Query-Prometheus $PrometheusUrl 'sum(hikaricp_connections_active)'
        hikariPending = Query-Prometheus $PrometheusUrl 'sum(hikaricp_connections_pending)'
        hikariMax = Query-Prometheus $PrometheusUrl 'sum(hikaricp_connections_max)'
        hikariAcquireCount = Query-Prometheus $PrometheusUrl 'sum(hikaricp_connections_acquire_seconds_count)'
        hikariAcquireSeconds = Query-Prometheus $PrometheusUrl 'sum(hikaricp_connections_acquire_seconds_sum)'
        hikariUsageCount = Query-Prometheus $PrometheusUrl 'sum(hikaricp_connections_usage_seconds_count)'
        hikariUsageSeconds = Query-Prometheus $PrometheusUrl 'sum(hikaricp_connections_usage_seconds_sum)'
        freshnessTimestamp = Query-Prometheus $PrometheusUrl 'min(timestamp({__name__=~"tomcat_threads_config_max_threads|hikaricp_connections_max|pawcycle_subscription_automation_executions_total"}))'
    }
}

function Assert-RuntimeMetrics([string]$PrometheusUrl, [double]$FreshAfter) {
    $deadline = (Get-Date).AddSeconds(60)
    do {
        try {
            $snapshot = Get-PrometheusSnapshot $PrometheusUrl 'runtime-capability'
            $tomcatMax = Query-Prometheus $PrometheusUrl 'sum(tomcat_threads_config_max_threads)'
            if ($tomcatMax -eq 128 -and $snapshot.hikariMax -eq 10 -and $snapshot.freshnessTimestamp -ge $FreshAfter) {
                return $snapshot
            }
        } catch {
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw 'Fresh Tomcat128/Hikari10/automation runtime evidence was not observed.'
}

function Assert-MeasurementEndMetrics([string]$PrometheusUrl, [double]$FinishedAfter) {
    $deadline = (Get-Date).AddSeconds(60)
    do {
        try {
            $snapshot = Get-PrometheusSnapshot $PrometheusUrl 'measurement-end'
            if ($snapshot.freshnessTimestamp -ge $FinishedAfter) {
                return $snapshot
            }
        } catch {
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw 'Measurement-end Prometheus scrape was not fresh after the workload finished.'
}

function Assert-MeasurementEndpointsDisarmed([string]$BackendUrl) {
    $response = Invoke-WebRequest -TimeoutSec 5 -SkipHttpErrorCheck -Method Post -Uri "$BackendUrl/internal/performance/subscription-burst/setup?cohortSize=100"
    if ($response.StatusCode -lt 400 -or $response.StatusCode -in @(404, 405)) {
        throw 'Measurement setup endpoint did not demonstrate the expected disarmed runtime boundary.'
    }
    if (Test-Path -LiteralPath $FirstResultMarker) {
        throw 'Disarmed runtime capability validation unexpectedly created a workload-start marker.'
    }
}

function Get-MySqlSnapshot([string]$Label) {
    $command = @'
MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=TCP --host=127.0.0.1 --user=root --database="$MYSQL_DATABASE" --batch --skip-column-names --execute="
SELECT CONCAT('relevant_statements=',COALESCE(SUM(COUNT_STAR),0)) FROM performance_schema.events_statements_summary_by_digest WHERE SCHEMA_NAME=DATABASE() AND (DIGEST_TEXT LIKE '%SUBSCRIPTION%' OR DIGEST_TEXT LIKE '%ORDERS%' OR DIGEST_TEXT LIKE '%PAYMENTS%' OR DIGEST_TEXT LIKE '%INVENTOR%');
SELECT CONCAT('threads_connected=',COALESCE(MAX(CASE WHEN VARIABLE_NAME='Threads_connected' THEN VARIABLE_VALUE END),0)) FROM performance_schema.global_status;
SELECT CONCAT('row_lock_waits=',COALESCE(MAX(CASE WHEN VARIABLE_NAME='Innodb_row_lock_waits' THEN VARIABLE_VALUE END),0)) FROM performance_schema.global_status;
SELECT CONCAT('row_lock_time_ms=',COALESCE(MAX(CASE WHEN VARIABLE_NAME='Innodb_row_lock_time' THEN VARIABLE_VALUE END),0)) FROM performance_schema.global_status;
SELECT CONCAT('current_lock_waits=',COUNT(*)) FROM performance_schema.data_lock_waits;"
'@
    $lines = @(docker exec $MysqlContainer sh -lc $command)
    if ($LASTEXITCODE -ne 0) { throw 'MySQL performance aggregate collection failed.' }
    $values = [ordered]@{ label = $Label; capturedAtUtc = (Get-Date).ToUniversalTime().ToString('o') }
    foreach ($line in $lines) {
        if ([string]$line -match '^(?<key>[a-z_]+)=(?<value>[0-9]+)$') {
            $values[$matches.key] = [long]$matches.value
        }
    }
    foreach ($required in @('relevant_statements', 'threads_connected', 'row_lock_waits', 'row_lock_time_ms', 'current_lock_waits')) {
        if (-not $values.Contains($required)) { throw "MySQL aggregate is missing: $required" }
    }
    return $values
}

function Get-ContainerSample {
    $raw = docker stats --no-stream --format '{{json .}}' $BackendContainer
    if ($LASTEXITCODE -ne 0) { throw 'Backend container stats collection failed.' }
    $stats = [string]($raw | Select-Object -First 1) | ConvertFrom-Json
    return [ordered]@{
        capturedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
        cpuPercent = [double](([string]$stats.CPUPerc).TrimEnd('%'))
        memoryUsage = [string]$stats.MemUsage
        memoryPercent = [double](([string]$stats.MemPerc).TrimEnd('%'))
        pids = [int]$stats.PIDs
    }
}

function Get-BackendMetricsPayload([string]$BackendUrl) {
    return (Invoke-WebRequest -TimeoutSec 3 -Uri "$BackendUrl/actuator/prometheus").Content
}

function Query-BackendGauge([string]$MetricsPayload, [string]$Metric) {
    $pattern = '(?m)^' + [regex]::Escape($Metric) + '(?:\{[^}]*\})?\s+(?<value>[-+0-9.eE]+)\s*$'
    $metricMatches = [regex]::Matches($MetricsPayload, $pattern)
    if ($metricMatches.Count -lt 1) { throw "Backend actuator gauge unavailable: $Metric" }
    return [double](($metricMatches | ForEach-Object { [double]$_.Groups['value'].Value } | Measure-Object -Sum).Sum)
}

function Get-MeasurementSample([string]$BackendUrl) {
    $container = Get-ContainerSample
    $metricsPayload = Get-BackendMetricsPayload $BackendUrl
    $container['processCpuUsage'] = Query-BackendGauge $metricsPayload 'process_cpu_usage'
    $container['jvmLiveThreads'] = Query-BackendGauge $metricsPayload 'jvm_threads_live_threads'
    $container['hikariActive'] = Query-BackendGauge $metricsPayload 'hikaricp_connections_active'
    $container['hikariPending'] = Query-BackendGauge $metricsPayload 'hikaricp_connections_pending'
    return $container
}

function Get-MeasurementPeaks([object[]]$Samples) {
    $peaks = [ordered]@{}
    foreach ($property in @('cpuPercent', 'memoryPercent', 'pids', 'processCpuUsage', 'jvmLiveThreads', 'hikariActive', 'hikariPending')) {
        $values = @($Samples | ForEach-Object { $_.$property } | Where-Object { $null -ne $_ })
        $peaks[$property] = if ($values.Count -gt 0) { ($values | Measure-Object -Maximum).Maximum } else { $null }
    }
    return $peaks
}

function Delta([object]$Before, [object]$After) {
    if ($null -eq $Before -or $null -eq $After) { return $null }
    return [double]$After - [double]$Before
}

function Get-DeltaSnapshot([object]$Before, [object]$After) {
    $delta = [ordered]@{}
    foreach ($property in @(
        'automationExecutions', 'automationProcessed', 'automationCreated', 'automationFailures',
        'automationDuplicateNoOp', 'automationDurationCount', 'automationDurationSeconds',
        'jvmGcPauseCount', 'jvmGcPauseSeconds', 'hikariAcquireCount', 'hikariAcquireSeconds',
        'hikariUsageCount', 'hikariUsageSeconds')) {
        $delta[$property] = Delta $Before.$property $After.$property
    }
    return $delta
}

function Get-MySqlDelta([object]$Before, [object]$After) {
    return [ordered]@{
        relevantStatements = Delta $Before.relevant_statements $After.relevant_statements
        rowLockWaits = Delta $Before.row_lock_waits $After.row_lock_waits
        rowLockTimeMs = Delta $Before.row_lock_time_ms $After.row_lock_time_ms
        finalThreadsConnected = $After.threads_connected
        finalCurrentLockWaits = $After.current_lock_waits
    }
}

function Write-Json([string]$Path, [object]$Value) {
    $json = $Value | ConvertTo-Json -Depth 12
    if ($json -match '(?i)memberId|subscriptionId|orderId|recipient|address(?:Line)?|billing(?:Key)?|payment(?:Id|Key)?|customerKey|email|phone|postalCode|password|credential|secret|token|cookie|session|csrf|authorization|driverStdout|driverStderr|rawStdout|rawStderr') {
        throw 'Artifact privacy boundary rejected a sensitive field.'
    }
    $json | Set-Content -LiteralPath $Path -Encoding utf8
}

function Get-RequiredProperty([object]$Value, [string]$Name, [string]$Context) {
    if ($null -eq $Value) {
        throw "$Context is missing required field: $Name."
    }
    if ($Value -is [System.Collections.IDictionary]) {
        if (-not $Value.Contains($Name) -or $null -eq $Value[$Name]) { throw "$Context is missing required field: $Name." }
        return $Value[$Name]
    }
    if ($null -eq $Value.PSObject.Properties[$Name] -or $null -eq $Value.$Name) { throw "$Context is missing required field: $Name." }
    return $Value.$Name
}

function Assert-AuthoritativeEvidenceIdentity([string]$Identity, [int]$Cohort, [string]$SourceSha, [object]$AuthoritativeContract, [string]$Context) {
    if ($Identity -ne [string](Get-RequiredProperty $AuthoritativeContract 'workloadIdentity' 'Authoritative first-result contract') -or
            $Cohort -ne [int](Get-RequiredProperty $AuthoritativeContract 'cohort' 'Authoritative first-result contract') -or
            -not $SourceSha.Equals([string](Get-RequiredProperty $AuthoritativeContract 'sourceSha' 'Authoritative first-result contract'), [StringComparison]::OrdinalIgnoreCase)) {
        throw "$Context does not match the authoritative historical first-result identity."
    }
}

function Assert-SourceSummaryPrivacy([object]$Summary) {
    $json = $Summary | ConvertTo-Json -Depth 12
    if ($json -match '(?i)memberId|subscriptionId|orderId|recipient|address(?:Line)?|billing(?:Key)?|payment(?:Id|Key)?|customerKey|email|phone|postalCode|password|credential|secret|token|cookie|session|csrf|authorization|driverStdout|driverStderr|rawStdout|rawStderr') {
        throw 'Evidence promotion rejected a source summary outside the privacy boundary.'
    }
}

function ConvertTo-UtcTimestamp([object]$Value, [string]$Context) {
    try {
        return [DateTimeOffset]::Parse([string]$Value, [Globalization.CultureInfo]::InvariantCulture).ToUniversalTime()
    } catch {
        throw "$Context is not a valid timestamp."
    }
}

function Get-ValidatedMarker([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw 'Authoritative workload marker is unavailable.' }
    $marker = Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
    if ((Get-RequiredProperty $marker 'workloadInvocationStarted' 'Authoritative workload marker') -ne $true) {
        throw 'Authoritative workload marker does not record a consumed workload.'
    }
    $null = ConvertTo-UtcTimestamp (Get-RequiredProperty $marker 'workloadStartedAtUtc' 'Authoritative workload marker') 'Authoritative workload marker timestamp'
    return $marker
}

function Select-Aggregate([object]$Value, [string[]]$Names, [string]$Context) {
    $projection = [ordered]@{}
    foreach ($name in $Names) { $projection[$name] = Get-RequiredProperty $Value $name $Context }
    return $projection
}

function New-DurableEvidenceProjection([object]$Summary, [object]$Marker, [object]$AuthoritativeContract = $HistoricalFirstResultContract) {
    Assert-SourceSummaryPrivacy $Summary
    $sourceCommit = [string](Get-RequiredProperty $Summary 'sourceCommit' 'Source summary')
    if ($sourceCommit -notmatch '^[0-9a-fA-F]{40}$') { throw 'Source summary SHA is malformed.' }
    $commitSpec = "$sourceCommit`^{commit}"
    git -C $RepoRoot cat-file -e $commitSpec 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'Source summary SHA is not available as a repository commit.' }

    $identity = [string](Get-RequiredProperty $Summary 'diagnostic' 'Source summary')
    if ((Get-RequiredProperty $Summary 'actualPerformanceWorkload' 'Source summary') -ne $true -or
            (Get-RequiredProperty $Summary 'workloadInvocationStarted' 'Source summary') -ne $true) {
        throw 'Source summary does not record a consumed performance workload.'
    }

    $cohort = [int](Get-RequiredProperty $Summary 'syntheticCohortSize' 'Source summary')
    if ($cohort -notin @(100, 500, 1000, 2500, 5000, 10000)) { throw 'Source summary cohort is outside the approved contract.' }
    Assert-AuthoritativeEvidenceIdentity $identity $cohort $sourceCommit $AuthoritativeContract 'Source summary'
    $driver = Get-RequiredProperty $Summary 'driver' 'Source summary'
    if ([int](Get-RequiredProperty $driver 'initialBacklog' 'Driver aggregate') -ne $cohort) { throw 'Source summary cohort does not match the driver initial backlog.' }
    $batchSize = [int](Get-RequiredProperty $Summary 'batchSize' 'Source summary')
    $fixedDelayMs = [long](Get-RequiredProperty $Summary 'fixedDelayMs' 'Source summary')
    if ($batchSize -ne $DefaultBatchSize -or $fixedDelayMs -ne $DefaultFixedDelayMs -or
            [int](Get-RequiredProperty $driver 'defaultSchedulerBatchSize' 'Driver aggregate') -ne $batchSize -or
            [long](Get-RequiredProperty $driver 'defaultSchedulerFixedDelayMs' 'Driver aggregate') -ne $fixedDelayMs) {
        throw 'Source summary batch/fixed-delay contract is inconsistent.'
    }

    $markerStarted = ConvertTo-UtcTimestamp (Get-RequiredProperty $Marker 'workloadStartedAtUtc' 'Authoritative workload marker') 'Authoritative workload marker timestamp'
    $summaryStarted = ConvertTo-UtcTimestamp (Get-RequiredProperty $Summary 'workloadStartedAtUtc' 'Source summary') 'Source summary workload timestamp'
    $completed = ConvertTo-UtcTimestamp (Get-RequiredProperty $Summary 'completedAtUtc' 'Source summary') 'Source summary completion timestamp'
    if ($markerStarted.UtcTicks -ne $summaryStarted.UtcTicks -or $completed -lt $summaryStarted) { throw 'Marker and source summary timestamps are inconsistent.' }

    $runtime = Get-RequiredProperty $Summary 'runtimeCapability' 'Source summary'
    $automationDelta = Get-RequiredProperty $Summary 'automationAndRuntimeDelta' 'Source summary'
    $measurementPeaks = Get-RequiredProperty $Summary 'measurementPeaks' 'Source summary'
    $mysql = Get-RequiredProperty $Summary 'mysql' 'Source summary'
    $mysqlDelta = Get-RequiredProperty $mysql 'delta' 'MySQL aggregate'
    $reconciliation = Get-RequiredProperty $Summary 'automationMetricReconciliation' 'Source summary'

    return [ordered]@{
        evidenceSchema = 'pawcycle.subscription-burst.redacted.v1'
        sourceSha = $sourceCommit.ToLowerInvariant()
        workloadIdentity = $identity
        cohort = $cohort
        contract = [ordered]@{ batchSize = $batchSize; fixedDelayMs = $fixedDelayMs }
        timestamps = [ordered]@{ workloadStartedAtUtc = $summaryStarted.ToString('o'); workloadCompletedAtUtc = $completed.ToString('o'); markerStartedAtUtc = $markerStarted.ToString('o') }
        workloadAggregate = Select-Aggregate $driver @('initialBacklog', 'finalBacklog', 'processed', 'created', 'failures', 'duplicateOrNoOp') 'Driver aggregate'
        rawPerformanceAggregate = Select-Aggregate $driver @('rawDrainElapsedMs', 'ordersPerSecond') 'Driver aggregate'
        batchAggregate = Select-Aggregate $driver @('batchCount', 'batchDurationP50Ms', 'batchDurationP95Ms', 'batchDurationMaxMs') 'Driver aggregate'
        schedulerProjection = Select-Aggregate $driver @('defaultSchedulerProjectedTicks', 'defaultSchedulerProjectedCompletionMs', 'projectionBasis') 'Driver aggregate'
        runtimeAggregate = Select-Aggregate $runtime @('processCpuUsage', 'jvmHeapUsed', 'jvmNonHeapUsed', 'jvmGcPauseCount', 'jvmGcPauseSeconds', 'jvmLiveThreads', 'jvmPeakThreads', 'hikariActive', 'hikariPending', 'hikariMax', 'hikariAcquireCount', 'hikariAcquireSeconds', 'hikariUsageCount', 'hikariUsageSeconds') 'Runtime aggregate'
        runtimeDeltaAggregate = Select-Aggregate $automationDelta @('automationExecutions', 'automationProcessed', 'automationCreated', 'automationFailures', 'automationDuplicateNoOp', 'automationDurationCount', 'automationDurationSeconds', 'jvmGcPauseCount', 'jvmGcPauseSeconds', 'hikariAcquireCount', 'hikariAcquireSeconds', 'hikariUsageCount', 'hikariUsageSeconds') 'Runtime delta aggregate'
        runtimePeakAggregate = Select-Aggregate $measurementPeaks @('cpuPercent', 'memoryPercent', 'pids', 'processCpuUsage', 'jvmLiveThreads', 'hikariActive', 'hikariPending') 'Runtime peak aggregate'
        mysqlAggregate = Select-Aggregate $mysqlDelta @('relevantStatements', 'rowLockWaits', 'rowLockTimeMs', 'finalThreadsConnected', 'finalCurrentLockWaits') 'MySQL aggregate'
        harnessCollectorState = [ordered]@{
            harnessFailure = [bool](Get-RequiredProperty $Summary 'harnessFailure' 'Source summary')
            collectorFailure = [bool](Get-RequiredProperty $Summary 'collectorFailure' 'Source summary')
            driverExitCode = [int](Get-RequiredProperty $Summary 'driverExitCode' 'Source summary')
            automationMetricReconciliationMatched = [bool](Get-RequiredProperty $reconciliation 'matched' 'Automation reconciliation')
        }
    }
}

function Write-DurableEvidenceCandidate([object]$Projection, [string]$Directory) {
    Assert-SourceSummaryPrivacy $Projection
    $json = $Projection | ConvertTo-Json -Depth 12
    $started = ConvertTo-UtcTimestamp $Projection.timestamps.workloadStartedAtUtc 'Durable evidence workload timestamp'
    $fileName = 'subscription-burst-before-{0}-{1}.json' -f $Projection.sourceSha.Substring(0, 12), $started.ToString('yyyyMMddTHHmmssfffffffZ')
    New-Item -ItemType Directory -Path $Directory -Force | Out-Null
    $path = Join-Path $Directory $fileName
    try {
        $stream = [IO.File]::Open($path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
        try {
            $writer = [IO.StreamWriter]::new($stream, [Text.UTF8Encoding]::new($false))
            try { $writer.Write($json) } finally { $writer.Dispose() }
        } finally { if ($stream) { $stream.Dispose() } }
    } catch [System.IO.IOException] {
        throw 'Durable evidence candidate already exists; overwrite is forbidden.'
    }
    return $path
}

function Export-DurableEvidence([string]$SourceSummaryPath, [string]$MarkerPath, [string]$DestinationDirectory, [object]$AuthoritativeContract = $HistoricalFirstResultContract) {
    Assert-SafeHostTempPath $SourceSummaryPath 'Evidence source summary'
    Assert-SafeHostTempPath $MarkerPath 'Evidence marker'
    if (-not (Test-Path -LiteralPath $SourceSummaryPath -PathType Leaf)) { throw 'Evidence source summary is unavailable.' }
    $marker = Get-ValidatedMarker $MarkerPath
    $summary = Get-Content -Raw -LiteralPath $SourceSummaryPath | ConvertFrom-Json
    return Write-DurableEvidenceCandidate (New-DurableEvidenceProjection $summary $marker $AuthoritativeContract) $DestinationDirectory
}

function Test-DurableEvidenceCandidate([string]$Path, [object]$AuthoritativeContract = $HistoricalFirstResultContract) {
    try {
        $candidate = Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
        Assert-SourceSummaryPrivacy $candidate
        if ((Get-RequiredProperty $candidate 'evidenceSchema' 'Durable evidence') -ne 'pawcycle.subscription-burst.redacted.v1') { return $false }
        $sourceSha = [string](Get-RequiredProperty $candidate 'sourceSha' 'Durable evidence')
        if ($sourceSha -notmatch '^[0-9a-f]{40}$') { return $false }
        git -C $RepoRoot cat-file -e "$sourceSha`^{commit}" 2>$null
        if ($LASTEXITCODE -ne 0) { return $false }
        $cohort = [int](Get-RequiredProperty $candidate 'cohort' 'Durable evidence')
        if ($cohort -notin @(100, 500, 1000, 2500, 5000, 10000)) { return $false }
        Assert-AuthoritativeEvidenceIdentity ([string](Get-RequiredProperty $candidate 'workloadIdentity' 'Durable evidence')) $cohort $sourceSha $AuthoritativeContract 'Durable evidence'
        $contract = Get-RequiredProperty $candidate 'contract' 'Durable evidence'
        if ([int](Get-RequiredProperty $contract 'batchSize' 'Durable evidence contract') -ne $DefaultBatchSize -or
                [long](Get-RequiredProperty $contract 'fixedDelayMs' 'Durable evidence contract') -ne $DefaultFixedDelayMs) { return $false }
        $timestamps = Get-RequiredProperty $candidate 'timestamps' 'Durable evidence'
        $started = ConvertTo-UtcTimestamp (Get-RequiredProperty $timestamps 'workloadStartedAtUtc' 'Durable evidence timestamps') 'Durable evidence workload timestamp'
        $markerStarted = ConvertTo-UtcTimestamp (Get-RequiredProperty $timestamps 'markerStartedAtUtc' 'Durable evidence timestamps') 'Durable evidence marker timestamp'
        $completed = ConvertTo-UtcTimestamp (Get-RequiredProperty $timestamps 'workloadCompletedAtUtc' 'Durable evidence timestamps') 'Durable evidence completion timestamp'
        if ($started.UtcTicks -ne $markerStarted.UtcTicks -or $completed -lt $started) { return $false }
        $workloadAggregate = Get-RequiredProperty $candidate 'workloadAggregate' 'Durable evidence'
        if ([int](Get-RequiredProperty $workloadAggregate 'initialBacklog' 'Durable workload aggregate') -ne $cohort) { return $false }
        $null = Select-Aggregate $workloadAggregate @('finalBacklog', 'processed', 'created', 'failures', 'duplicateOrNoOp') 'Durable workload aggregate'
        $null = Select-Aggregate (Get-RequiredProperty $candidate 'rawPerformanceAggregate' 'Durable evidence') @('rawDrainElapsedMs', 'ordersPerSecond') 'Durable raw performance aggregate'
        $null = Select-Aggregate (Get-RequiredProperty $candidate 'batchAggregate' 'Durable evidence') @('batchCount', 'batchDurationP50Ms', 'batchDurationP95Ms', 'batchDurationMaxMs') 'Durable batch aggregate'
        $null = Select-Aggregate (Get-RequiredProperty $candidate 'schedulerProjection' 'Durable evidence') @('defaultSchedulerProjectedTicks', 'defaultSchedulerProjectedCompletionMs', 'projectionBasis') 'Durable scheduler projection'
        $null = Select-Aggregate (Get-RequiredProperty $candidate 'runtimeAggregate' 'Durable evidence') @('processCpuUsage', 'jvmHeapUsed', 'jvmNonHeapUsed', 'jvmGcPauseCount', 'jvmGcPauseSeconds', 'jvmLiveThreads', 'jvmPeakThreads', 'hikariActive', 'hikariPending', 'hikariMax', 'hikariAcquireCount', 'hikariAcquireSeconds', 'hikariUsageCount', 'hikariUsageSeconds') 'Durable runtime aggregate'
        $null = Select-Aggregate (Get-RequiredProperty $candidate 'runtimeDeltaAggregate' 'Durable evidence') @('automationExecutions', 'automationProcessed', 'automationCreated', 'automationFailures', 'automationDuplicateNoOp', 'automationDurationCount', 'automationDurationSeconds', 'jvmGcPauseCount', 'jvmGcPauseSeconds', 'hikariAcquireCount', 'hikariAcquireSeconds', 'hikariUsageCount', 'hikariUsageSeconds') 'Durable runtime delta aggregate'
        $null = Select-Aggregate (Get-RequiredProperty $candidate 'runtimePeakAggregate' 'Durable evidence') @('cpuPercent', 'memoryPercent', 'pids', 'processCpuUsage', 'jvmLiveThreads', 'hikariActive', 'hikariPending') 'Durable runtime peak aggregate'
        $null = Select-Aggregate (Get-RequiredProperty $candidate 'mysqlAggregate' 'Durable evidence') @('relevantStatements', 'rowLockWaits', 'rowLockTimeMs', 'finalThreadsConnected', 'finalCurrentLockWaits') 'Durable MySQL aggregate'
        $null = Select-Aggregate (Get-RequiredProperty $candidate 'harnessCollectorState' 'Durable evidence') @('harnessFailure', 'collectorFailure', 'driverExitCode', 'automationMetricReconciliationMatched') 'Durable harness/collector state'
        return $true
    } catch {
        return $false
    }
}

function Get-EvidenceState([string]$MarkerPath, [string]$DurableDirectory, [bool]$AuthoritativelyConsumed, [object]$AuthoritativeContract = $HistoricalFirstResultContract) {
    $candidates = if (Test-Path -LiteralPath $DurableDirectory -PathType Container) { @(Get-ChildItem -LiteralPath $DurableDirectory -Filter 'subscription-burst-before-*.json' -File) } else { @() }
    if (@($candidates | Where-Object { Test-DurableEvidenceCandidate $_.FullName $AuthoritativeContract }).Count -gt 0) { return 'CONSUMED_SUMMARY_AVAILABLE' }
    if ($AuthoritativelyConsumed -or (Test-Path -LiteralPath $MarkerPath -PathType Leaf)) {
        if (Test-Path -LiteralPath $MarkerPath -PathType Leaf) { $null = Get-ValidatedMarker $MarkerPath }
        return 'CONSUMED_SUMMARY_MISSING'
    }
    return 'NOT_STARTED'
}

function Write-MinimalRedactedFailureArtifact([string]$Path, [object]$Summary) {
    $artifact = [ordered]@{
        diagnostic = [string]$Summary.diagnostic
        sourceCommit = [string]$Summary.sourceCommit
        syntheticCohortSize = [int]$Summary.syntheticCohortSize
        batchSize = [int]$Summary.batchSize
        fixedDelayMs = [long]$Summary.fixedDelayMs
        actualPerformanceWorkload = [bool]$Summary.actualPerformanceWorkload
        workloadInvocationStarted = [bool]$Summary.workloadInvocationStarted
        workloadStartedAtUtc = if ($Summary.Contains('workloadStartedAtUtc')) { [string]$Summary.workloadStartedAtUtc } else { $null }
        harnessFailure = $true
        collectorFailure = [bool]$Summary.collectorFailure
        artifactWriteFailure = 'Full summary was discarded because the artifact privacy boundary rejected or could not serialize it.'
        completedAtUtc = [string]$Summary.completedAtUtc
    }
    $artifact | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $Path -Encoding utf8
}

function Validate-SyntheticContracts {
    if ($HistoricalFirstResultContract.workloadIdentity -ne 'phase10-subscription-burst-before-local' -or
            $HistoricalFirstResultContract.cohort -ne 5000 -or
            $HistoricalFirstResultContract.sourceSha -ne '3f11a5cc6489d3096d024290008a1b91fabe634c') {
        throw 'Historical first-result authoritative identity contract is invalid.'
    }
    foreach ($value in @(100, 500, 1000, 2500, 5000, 10000)) {
        if ($value -lt 1 -or $value -gt 10000) { throw 'Approved synthetic cohort contract is invalid.' }
    }
    $serviceSource = Get-Content -Raw -LiteralPath $MeasurementServiceSource
    $markerIndex = $serviceSource.IndexOf('writeWorkloadStartMarker();', [StringComparison]::Ordinal)
    $workloadIndex = $serviceSource.IndexOf('automation.processDueSchedules(DEFAULT_BATCH_SIZE)', [StringComparison]::Ordinal)
    if ($markerIndex -lt 0 -or $workloadIndex -lt 0 -or $markerIndex -gt $workloadIndex) {
        throw 'Backend workload-start marker is not authoritative.'
    }

    if ($serviceSource -notmatch 'assertRunArmed\(\)' -or $serviceSource -notmatch 'assertEligibleCandidateScope\(initialBacklog\)') {
        throw 'Backend run-arm or synthetic scope guard is missing.'
    }
    $harnessSource = Get-Content -Raw -LiteralPath $PSCommandPath
    $permanentGateIndex = $harnessSource.IndexOf('if ($RunBeforeFirstResult -and $FirstResultAuthoritativelyConsumed)', [StringComparison]::Ordinal)
    $runtimeStartIndex = $harnessSource.IndexOf("Invoke-Compose @('up', '--build'", [StringComparison]::Ordinal)
    if (-not $FirstResultAuthoritativelyConsumed -or $permanentGateIndex -lt 0 -or $runtimeStartIndex -lt 0 -or $permanentGateIndex -gt $runtimeStartIndex) {
        throw 'Authoritatively consumed first-result is not fail-closed before runtime startup.'
    }
    $sampleBlock = [regex]::Match($harnessSource, '(?s)function Get-MeasurementSample.*?function Get-MeasurementPeaks').Value
    if (([regex]::Matches($sampleBlock, 'Get-BackendMetricsPayload')).Count -ne 1 -or
            ([regex]::Matches($sampleBlock, 'Query-BackendGauge')).Count -ne 4) {
        throw 'Measurement sample must fetch actuator metrics once and parse four gauges from that payload.'
    }
    $overlay = Get-Content -Raw -LiteralPath $OverlayPath
    if ($overlay -notmatch 'pawcycle-phase10-subscription-burst-mysql-data' -or
            $overlay -notmatch 'PAWCYCLE_PHASE10_SUBSCRIPTION_BURST_MARKER_DIR' -or
            $overlay -match 'remove-orphans') {
        throw 'Subscription Burst isolated compose boundary is invalid.'
    }
    $syntheticRoot = Join-Path $TempRoot "pawcycle-phase10-subscription-burst-evidence-validation-$([guid]::NewGuid())"
    $syntheticMarker = Join-Path $syntheticRoot 'workload-started.json'
    $syntheticArtifact = Join-Path $syntheticRoot 'source-summary.json'
    $syntheticDurable = Join-Path $syntheticRoot 'durable'
    try {
        New-Item -ItemType Directory -Path $syntheticRoot -Force | Out-Null
        if ((Get-EvidenceState $syntheticMarker $syntheticDurable $false) -ne 'NOT_STARTED') {
            throw 'Synthetic NOT_STARTED evidence state validation failed.'
        }
        if ((Get-EvidenceState $syntheticMarker $syntheticDurable $true) -ne 'CONSUMED_SUMMARY_MISSING') {
            throw 'Synthetic authoritative consumed state validation failed.'
        }
        if (Test-Path -LiteralPath $syntheticMarker) { throw 'Synthetic pre-workload marker unexpectedly exists.' }
        [ordered]@{ workloadInvocationStarted = $true; workloadStartedAtUtc = '2026-01-01T00:00:00Z' } |
            ConvertTo-Json | Set-Content -LiteralPath $syntheticMarker -Encoding utf8
        try { throw 'synthetic post-start collector failure' } catch { }
        $retained = Get-Content -Raw -LiteralPath $syntheticMarker | ConvertFrom-Json
        if (-not $retained.workloadInvocationStarted) { throw 'Synthetic post-start marker was not retained.' }
        if ((Get-EvidenceState $syntheticMarker $syntheticDurable $false) -ne 'CONSUMED_SUMMARY_MISSING') {
            throw 'Synthetic CONSUMED_SUMMARY_MISSING evidence state validation failed.'
        }

        $sourceCommit = (git -C $RepoRoot rev-parse HEAD)
        if ($LASTEXITCODE -ne 0) { throw 'Synthetic source commit could not be resolved.' }
        $syntheticAuthoritativeContract = [ordered]@{
            workloadIdentity = $WorkloadIdentity
            cohort = 5000
            sourceSha = [string]$sourceCommit
        }
        $safeSummary = [ordered]@{
            diagnostic = $WorkloadIdentity
            sourceCommit = [string]$sourceCommit
            syntheticCohortSize = [int]$syntheticAuthoritativeContract.cohort
            batchSize = 100
            fixedDelayMs = 60000
            actualPerformanceWorkload = $true
            workloadInvocationStarted = $true
            workloadStartedAtUtc = '2026-01-01T00:00:00Z'
            driver = [ordered]@{
                initialBacklog = [int]$syntheticAuthoritativeContract.cohort; finalBacklog = 0; processed = [int]$syntheticAuthoritativeContract.cohort; created = [int]$syntheticAuthoritativeContract.cohort; failures = 0; duplicateOrNoOp = 0
                rawDrainElapsedMs = 1; ordersPerSecond = [int]$syntheticAuthoritativeContract.cohort; batchCount = 1
                batchDurationP50Ms = 1; batchDurationP95Ms = 1; batchDurationMaxMs = 1
                defaultSchedulerBatchSize = 100; defaultSchedulerFixedDelayMs = 60000
                defaultSchedulerProjectedTicks = 1; defaultSchedulerProjectedCompletionMs = 1
                projectionBasis = 'synthetic validation projection'
            }
            runtimeCapability = [ordered]@{
                processCpuUsage = 0; jvmHeapUsed = 0; jvmNonHeapUsed = 0; jvmGcPauseCount = 0; jvmGcPauseSeconds = 0
                jvmLiveThreads = 1; jvmPeakThreads = 1; hikariActive = 0; hikariPending = 0; hikariMax = 10
                hikariAcquireCount = 0; hikariAcquireSeconds = 0; hikariUsageCount = 0; hikariUsageSeconds = 0
            }
            automationAndRuntimeDelta = [ordered]@{
                automationExecutions = 1; automationProcessed = [int]$syntheticAuthoritativeContract.cohort; automationCreated = [int]$syntheticAuthoritativeContract.cohort; automationFailures = 0
                automationDuplicateNoOp = 0; automationDurationCount = 1; automationDurationSeconds = 0
                jvmGcPauseCount = 0; jvmGcPauseSeconds = 0; hikariAcquireCount = 0; hikariAcquireSeconds = 0
                hikariUsageCount = 0; hikariUsageSeconds = 0
            }
            measurementPeaks = [ordered]@{ cpuPercent = 0; memoryPercent = 0; pids = 1; processCpuUsage = 0; jvmLiveThreads = 1; hikariActive = 0; hikariPending = 0 }
            mysql = [ordered]@{ delta = [ordered]@{ relevantStatements = 1; rowLockWaits = 0; rowLockTimeMs = 0; finalThreadsConnected = 1; finalCurrentLockWaits = 0 } }
            automationMetricReconciliation = [ordered]@{ matched = $true }
            harnessFailure = $false
            collectorFailure = $false
            driverExitCode = 0
            completedAtUtc = '2026-01-01T00:00:00Z'
        }

        $wrongCohortSummary = $safeSummary | ConvertTo-Json -Depth 12 | ConvertFrom-Json
        $wrongCohortSummary.syntheticCohortSize = 100
        $wrongCohortSummary.driver.initialBacklog = 100
        $wrongCohortSummary.driver.processed = 100
        $wrongCohortSummary.driver.created = 100
        $wrongCohortSummary.driver.ordersPerSecond = 100
        $wrongCohortSummary.automationAndRuntimeDelta.automationProcessed = 100
        $wrongCohortSummary.automationAndRuntimeDelta.automationCreated = 100
        $wrongCohortSummary.workloadStartedAtUtc = '2026-01-01T00:01:00Z'
        $wrongCohortSummary.completedAtUtc = '2026-01-01T00:01:00Z'
        $wrongCohortSummary | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $syntheticArtifact -Encoding utf8
        [ordered]@{ workloadInvocationStarted = $true; workloadStartedAtUtc = '2026-01-01T00:01:00Z' } | ConvertTo-Json | Set-Content -LiteralPath $syntheticMarker -Encoding utf8
        $wrongCohortRejected = $false
        try { $null = Export-DurableEvidence $syntheticArtifact $syntheticMarker $syntheticDurable $syntheticAuthoritativeContract } catch { $wrongCohortRejected = $true }
        if (-not $wrongCohortRejected) { throw 'Synthetic wrong cohort promotion was not rejected.' }
        $wrongCohortContract = [ordered]@{ workloadIdentity = $WorkloadIdentity; cohort = 100; sourceSha = [string]$sourceCommit }
        $null = Export-DurableEvidence $syntheticArtifact $syntheticMarker $syntheticDurable $wrongCohortContract
        if ((Get-EvidenceState $syntheticMarker $syntheticDurable $false $syntheticAuthoritativeContract) -ne 'CONSUMED_SUMMARY_MISSING') {
            throw 'Synthetic wrong cohort candidate was accepted as available evidence.'
        }

        $wrongSourceSummary = $safeSummary | ConvertTo-Json -Depth 12 | ConvertFrom-Json
        $wrongSourceSummary.sourceCommit = [string]$HistoricalFirstResultContract.sourceSha
        $wrongSourceSummary.workloadStartedAtUtc = '2026-01-01T00:02:00Z'
        $wrongSourceSummary.completedAtUtc = '2026-01-01T00:02:00Z'
        $wrongSourceSummary | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $syntheticArtifact -Encoding utf8
        [ordered]@{ workloadInvocationStarted = $true; workloadStartedAtUtc = '2026-01-01T00:02:00Z' } | ConvertTo-Json | Set-Content -LiteralPath $syntheticMarker -Encoding utf8
        $wrongSourceRejected = $false
        try { $null = Export-DurableEvidence $syntheticArtifact $syntheticMarker $syntheticDurable $syntheticAuthoritativeContract } catch { $wrongSourceRejected = $true }
        if (-not $wrongSourceRejected) { throw 'Synthetic wrong source SHA promotion was not rejected.' }
        $wrongSourceContract = [ordered]@{ workloadIdentity = $WorkloadIdentity; cohort = [int]$syntheticAuthoritativeContract.cohort; sourceSha = [string]$HistoricalFirstResultContract.sourceSha }
        $null = Export-DurableEvidence $syntheticArtifact $syntheticMarker $syntheticDurable $wrongSourceContract
        if ((Get-EvidenceState $syntheticMarker $syntheticDurable $false $syntheticAuthoritativeContract) -ne 'CONSUMED_SUMMARY_MISSING') {
            throw 'Synthetic wrong source candidate was accepted as available evidence.'
        }

        $safeSummary | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $syntheticArtifact -Encoding utf8
        [ordered]@{ workloadInvocationStarted = $true; workloadStartedAtUtc = '2026-01-01T00:00:00Z' } | ConvertTo-Json | Set-Content -LiteralPath $syntheticMarker -Encoding utf8
        $promoted = Export-DurableEvidence $syntheticArtifact $syntheticMarker $syntheticDurable $syntheticAuthoritativeContract
        if ((Get-EvidenceState $syntheticMarker $syntheticDurable $false $syntheticAuthoritativeContract) -ne 'CONSUMED_SUMMARY_AVAILABLE') {
            throw 'Synthetic CONSUMED_SUMMARY_AVAILABLE evidence state validation failed.'
        }
        $beforeCollision = Get-Content -Raw -LiteralPath $promoted
        $collisionRejected = $false
        try { $null = Export-DurableEvidence $syntheticArtifact $syntheticMarker $syntheticDurable $syntheticAuthoritativeContract } catch { $collisionRejected = $true }
        if (-not $collisionRejected -or (Get-Content -Raw -LiteralPath $promoted) -ne $beforeCollision) {
            throw 'Synthetic durable evidence collision protection validation failed.'
        }

        $unsafeSummary = $safeSummary | ConvertTo-Json -Depth 12 | ConvertFrom-Json
        $unsafeSummary | Add-Member -NotePropertyName customerKey -NotePropertyValue 'must-not-persist'
        $privacyRejected = $false
        try { $null = New-DurableEvidenceProjection $unsafeSummary (Get-ValidatedMarker $syntheticMarker) $syntheticAuthoritativeContract } catch { $privacyRejected = $true }
        if (-not $privacyRejected) { throw 'Synthetic privacy validation did not reject the unsafe summary.' }
    } finally {
        Remove-Item -LiteralPath $syntheticRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Assert-SafeHostTempPath $ResultsDir 'ResultsDir'
Assert-SafeHostTempPath $MarkerDir 'first-result marker directory'
$modes = @($ValidateOnly, $ValidateRuntimeCapability, $InspectEvidenceState, $PromoteEvidence, $RunBeforeFirstResult, $CleanupIsolatedRuntime) | Where-Object { $_ }
if ($modes.Count -ne 1) {
    throw 'Specify exactly one mode: ValidateOnly, ValidateRuntimeCapability, InspectEvidenceState, PromoteEvidence, RunBeforeFirstResult, or CleanupIsolatedRuntime.'
}
if ($RunBeforeFirstResult -and -not $PSBoundParameters.ContainsKey('CohortSize')) {
    throw 'RunBeforeFirstResult requires an explicit approved CohortSize before any workload preparation.'
}
$env:PAWCYCLE_PHASE10_SUBSCRIPTION_BURST_RUN_ARMED = if ($RunBeforeFirstResult) { 'true' } else { 'false' }

if ($ValidateOnly) {
    Validate-SyntheticContracts
    'Phase 10 Subscription Burst Before harness validation passed without starting the performance workload.'
    exit 0
}

if ($InspectEvidenceState) {
    Get-EvidenceState $EvidenceMarkerPath $DurableEvidenceDir $FirstResultAuthoritativelyConsumed
    exit 0
}

if ($PromoteEvidence) {
    $candidate = Export-DurableEvidence $EvidenceSourceSummaryPath $EvidenceMarkerPath $DurableEvidenceDir
    "Durable redacted evidence candidate created for review without Git commit or push: $candidate"
    exit 0
}

if ($RunBeforeFirstResult -and $FirstResultAuthoritativelyConsumed) {
    $state = Get-EvidenceState $EvidenceMarkerPath $DurableEvidenceDir $true
    throw "PERF-PH10-002 first-result is authoritatively consumed (evidenceState=$state); NEVER RERUN."
}

New-Item -ItemType Directory -Path $MarkerDir -Force | Out-Null
if ($CleanupIsolatedRuntime) {
    Reset-IsolatedRuntime
    'Isolated Subscription Burst containers and disposable volumes were removed; first-result marker and artifacts were preserved.'
    exit 0
}

if ($RunBeforeFirstResult -and (Test-Path -LiteralPath $FirstResultMarker)) {
    throw 'Subscription Burst Before first-result was already started; NEVER RERUN.'
}

$freshAfter = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() / 1000.0
Invoke-Compose @('up', '--build', '-d', '--wait', '--wait-timeout', '180', 'mysql', 'redis', 'backend', 'prometheus')
Assert-IsolatedRuntime
$backendUrl = Get-PublishedLoopbackUrl 'backend' 8080
$prometheusUrl = Get-PublishedLoopbackUrl 'prometheus' 9090
$runtime = Assert-RuntimeMetrics $prometheusUrl $freshAfter
$runtimeMySql = Get-MySqlSnapshot 'runtime-capability'

if ($ValidateRuntimeCapability) {
    Assert-MeasurementEndpointsDisarmed $backendUrl
    "Phase 10 Subscription Burst isolated runtime capability passed (backend=$backendUrl, prometheus=$prometheusUrl)."
    exit 0
}

New-Item -ItemType Directory -Path $ResultsDir -Force | Out-Null
$sourceCommit = (git -C $RepoRoot rev-parse HEAD)
if ($LASTEXITCODE -ne 0) { throw 'Source commit could not be resolved.' }
$dirty = @(git -C $RepoRoot status --porcelain)
if ($LASTEXITCODE -ne 0 -or $dirty.Count -ne 0) { throw 'First-result requires a clean reviewed worktree.' }

$consumed = $false
$process = $null
$summary = [ordered]@{
    diagnostic = 'phase10-subscription-burst-before-local'
    sourceCommit = [string]$sourceCommit
    syntheticCohortSize = $CohortSize
    batchSize = $DefaultBatchSize
    fixedDelayMs = $DefaultFixedDelayMs
    actualPerformanceWorkload = $true
    workloadInvocationStarted = $false
    harnessFailure = $false
    collectorFailure = $false
    error = $null
    runtimeCapability = $runtime
    runtimeCapabilityMySql = $runtimeMySql
}
try {
    $fixture = Invoke-RestMethod -Method Post -TimeoutSec 900 -Uri "$backendUrl/internal/performance/subscription-burst/setup?cohortSize=$CohortSize"
    if ($fixture.initialBacklog -ne $CohortSize -or $fixture.batchSize -ne $DefaultBatchSize) {
        throw 'Synthetic fixture setup did not produce the approved backlog.'
    }
    $baselinePrometheus = Get-PrometheusSnapshot $prometheusUrl 'baseline'
    $baselineMySql = Get-MySqlSnapshot 'baseline'
    $samples = [System.Collections.Generic.List[object]]::new()

    $process = Start-Process -FilePath $HttpCommand `
        -ArgumentList @('--fail-with-body', '--silent', '--show-error', '--request', 'POST', "$backendUrl/internal/performance/subscription-burst/drain") `
        -RedirectStandardOutput $DriverStdout `
        -RedirectStandardError $DriverStderr `
        -PassThru `
        -NoNewWindow

    do {
        if (Test-Path -LiteralPath $FirstResultMarker) {
            $started = Get-Content -Raw -LiteralPath $FirstResultMarker | ConvertFrom-Json
            if (-not $started.workloadInvocationStarted) { throw 'Authoritative workload-start marker is invalid.' }
            $consumed = $true
            $summary.workloadInvocationStarted = $true
            $summary['workloadStartedAtUtc'] = $started.workloadStartedAtUtc
        }
        if ($consumed) {
            try {
                $samples.Add((Get-MeasurementSample $backendUrl))
            } catch {
                $summary.collectorFailure = $true
            }
        }
        if (-not $process.HasExited) { Start-Sleep -Milliseconds 200 }
    } while (-not $process.HasExited)
    $process.WaitForExit()
    $workloadFinishedAfter = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() / 1000.0
    if (-not $consumed -and (Test-Path -LiteralPath $FirstResultMarker)) {
        $started = Get-Content -Raw -LiteralPath $FirstResultMarker | ConvertFrom-Json
        $consumed = [bool]$started.workloadInvocationStarted
        $summary.workloadInvocationStarted = $consumed
        $summary['workloadStartedAtUtc'] = $started.workloadStartedAtUtc
    }
    if (-not $consumed) { throw 'Backend rejected the driver before the workload-start boundary.' }

    $driver = Get-Content -Raw -LiteralPath $DriverStdout | ConvertFrom-Json
    $endPrometheus = Assert-MeasurementEndMetrics $prometheusUrl $workloadFinishedAfter
    $endMySql = Get-MySqlSnapshot 'measurement-end'
    $automationDelta = Get-DeltaSnapshot $baselinePrometheus $endPrometheus
    $automationMetricsMatch =
        $automationDelta.automationExecutions -eq [double]$driver.batchCount -and
        $automationDelta.automationProcessed -eq [double]$driver.processed -and
        $automationDelta.automationCreated -eq [double]$driver.created -and
        $automationDelta.automationFailures -eq [double]$driver.failures -and
        $automationDelta.automationDuplicateNoOp -eq [double]$driver.duplicateOrNoOp
    $summary['driver'] = $driver
    $summary['automationAndRuntimeDelta'] = $automationDelta
    $summary['automationMetricReconciliation'] = [ordered]@{
        matched = $automationMetricsMatch
        expectedBatchCount = [int]$driver.batchCount
        expectedProcessed = [int]$driver.processed
        expectedCreated = [int]$driver.created
        expectedFailures = [int]$driver.failures
        expectedDuplicateNoOp = [int]$driver.duplicateOrNoOp
    }
    $summary['prometheus'] = [ordered]@{ baseline = $baselinePrometheus; measurementEnd = $endPrometheus }
    $summary['mysql'] = [ordered]@{ baseline = $baselineMySql; measurementEnd = $endMySql; delta = Get-MySqlDelta $baselineMySql $endMySql }
    $summary['containerSamples'] = @($samples)
    $summary['measurementPeaks'] = Get-MeasurementPeaks @($samples)
    $summary['backendFinalState'] = @(docker inspect --format 'health={{if .State.Health}}{{.State.Health.Status}}{{else}}no-health{{end}}|restart={{.RestartCount}}|oom={{.State.OOMKilled}}' $BackendContainer | Select-Object -First 1)
    $summary.harnessFailure = [bool]$driver.harnessFailure -or $process.ExitCode -ne 0 -or -not $automationMetricsMatch
    if (-not $automationMetricsMatch) { $summary.error = 'Automation metric delta did not match the driver aggregate.' }
    $summary['driverExitCode'] = $process.ExitCode
} catch {
    $summary.harnessFailure = $true
    $summary.error = 'Subscription Burst Before harness execution failed.'
    if (-not $consumed -and (Test-Path -LiteralPath $FirstResultMarker)) {
        $started = Get-Content -Raw -LiteralPath $FirstResultMarker | ConvertFrom-Json
        $consumed = [bool]$started.workloadInvocationStarted
        $summary.workloadInvocationStarted = $consumed
        $summary['workloadStartedAtUtc'] = $started.workloadStartedAtUtc
    }
    if (-not $consumed) {
        try { Reset-IsolatedRuntime } catch { }
        throw
    }
} finally {
    if ($process) { $process.Dispose() }
    if ($consumed) {
        $summary['completedAtUtc'] = (Get-Date).ToUniversalTime().ToString('o')
        $fullSummaryWritten = $false
        try {
            Write-Json $SummaryPath $summary
            $fullSummaryWritten = $true
        } catch {
            $summary.harnessFailure = $true
            $summary.error = 'Full summary artifact was discarded after a privacy or serialization failure.'
            Write-MinimalRedactedFailureArtifact $SummaryPath $summary
        }
        if ($fullSummaryWritten) {
            try {
                $null = Export-DurableEvidence $SummaryPath $FirstResultMarker $DurableEvidenceDir
            } catch {
                $summary.harnessFailure = $true
                $summary.error = 'Durable redacted evidence promotion failed; the workload remains consumed and must never be rerun.'
                Write-Json $SummaryPath $summary
            }
        }
    }
}

if ($summary.harnessFailure) {
    throw "Subscription Burst Before first-result was consumed with a harness failure; NEVER RERUN. Summary: $SummaryPath"
}
"Subscription Burst Before first-result completed once: $SummaryPath"
