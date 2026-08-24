[CmdletBinding()]
param(
    [ValidateSet(100, 500, 1000, 2500, 5000, 10000)]
    [int]$CohortSize = 100,
    [string]$HttpCommand = 'curl.exe',
    [string]$ResultsDir = (Join-Path $env:TEMP 'pawcycle-phase10-subscription-burst-before'),
    [switch]$ValidateOnly,
    [switch]$ValidateRuntimeCapability,
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
    if ($json -match '(?i)memberId|subscriptionId|orderId|recipient|addressLine|billingKey|customerKey|password|credential|secret') {
        throw 'Artifact privacy boundary rejected a sensitive field.'
    }
    $json | Set-Content -LiteralPath $Path -Encoding utf8
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
    $syntheticMarker = Join-Path $TempRoot ("pawcycle-phase10-subscription-burst-marker-validation-$([guid]::NewGuid()).json")
    $syntheticArtifact = Join-Path $TempRoot ("pawcycle-phase10-subscription-burst-artifact-validation-$([guid]::NewGuid()).json")
    try {
        if (Test-Path -LiteralPath $syntheticMarker) { throw 'Synthetic pre-workload marker unexpectedly exists.' }
        [ordered]@{ workloadInvocationStarted = $true; workloadStartedAtUtc = '2026-01-01T00:00:00Z' } |
            ConvertTo-Json | Set-Content -LiteralPath $syntheticMarker -Encoding utf8
        try { throw 'synthetic post-start collector failure' } catch { }
        $retained = Get-Content -Raw -LiteralPath $syntheticMarker | ConvertFrom-Json
        if (-not $retained.workloadInvocationStarted) { throw 'Synthetic post-start marker was not retained.' }

        $unsafeSummary = [ordered]@{
            diagnostic = 'synthetic'
            sourceCommit = 'synthetic'
            syntheticCohortSize = 100
            batchSize = 100
            fixedDelayMs = 60000
            actualPerformanceWorkload = $true
            workloadInvocationStarted = $true
            collectorFailure = $false
            completedAtUtc = '2026-01-01T00:00:00Z'
            customerKey = 'must-not-persist'
        }
        $privacyRejected = $false
        try { Write-Json $syntheticArtifact $unsafeSummary } catch { $privacyRejected = $true }
        if (-not $privacyRejected) { throw 'Synthetic privacy validation did not reject the unsafe summary.' }
        Write-MinimalRedactedFailureArtifact $syntheticArtifact $unsafeSummary
        $artifactJson = Get-Content -Raw -LiteralPath $syntheticArtifact
        if ($artifactJson -match 'customerKey' -or $artifactJson -notmatch '"harnessFailure":\s*true') {
            throw 'Synthetic redacted failure artifact contract is invalid.'
        }
    } finally {
        Remove-Item -LiteralPath $syntheticMarker -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $syntheticArtifact -Force -ErrorAction SilentlyContinue
    }
}

Assert-SafeHostTempPath $ResultsDir 'ResultsDir'
Assert-SafeHostTempPath $MarkerDir 'first-result marker directory'
$modes = @($ValidateOnly, $ValidateRuntimeCapability, $RunBeforeFirstResult, $CleanupIsolatedRuntime) | Where-Object { $_ }
if ($modes.Count -ne 1) {
    throw 'Specify exactly one mode: ValidateOnly, ValidateRuntimeCapability, RunBeforeFirstResult, or CleanupIsolatedRuntime.'
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
        try {
            Write-Json $SummaryPath $summary
        } catch {
            $summary.harnessFailure = $true
            $summary.error = 'Full summary artifact was discarded after a privacy or serialization failure.'
            Write-MinimalRedactedFailureArtifact $SummaryPath $summary
        }
    }
}

if ($summary.harnessFailure) {
    throw "Subscription Burst Before first-result was consumed with a harness failure; NEVER RERUN. Summary: $SummaryPath"
}
"Subscription Burst Before first-result completed once: $SummaryPath"
