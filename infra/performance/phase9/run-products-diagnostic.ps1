[CmdletBinding()]
param(
    [string]$K6Command = 'k6',
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$PrometheusUrl = 'http://127.0.0.1:9090',
    [string]$ResultsDir = (Join-Path $env:TEMP 'pawcycle-phase9-products'),
    [switch]$ValidateOnly,
    [switch]$ValidateCollectorOnly
)

$ErrorActionPreference = 'Stop'
$TargetRps = 250
$WarmupSeconds = 30
$MeasurementSeconds = 120
$SampleSeconds = 5
$RepoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$ResultsDir = [IO.Path]::GetFullPath($ResultsDir)
$repoRootPrefix = $RepoRoot.TrimEnd([char[]]@([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)) + [IO.Path]::DirectorySeparatorChar
$K6Script = Join-Path $RepoRoot 'infra\performance\k6\capacity-api-products.js'
$MysqlContainer = 'pawcycle-local-integration-mysql-1'

if ($BaseUrl -notmatch '^http://(127\.0\.0\.1|localhost|\[::1\])(?::[0-9]{1,5})?$') {
    throw 'BaseUrl must be an http loopback origin.'
}
if ($ResultsDir.Equals($RepoRoot, [StringComparison]::OrdinalIgnoreCase) -or $ResultsDir.StartsWith($repoRootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'ResultsDir must be outside the repository.'
}
if (-not (Test-Path -LiteralPath $K6Script)) { throw "Missing existing capacity script: $K6Script" }

if ($ValidateOnly) {
    & $K6Command inspect -e "BASE_URL=$BaseUrl" -e "TARGET_RPS=$TargetRps" $K6Script | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'k6 inspect failed.' }
    'Phase 9 local diagnostic k6 inspect passed.'
    exit 0
}

$runDir = Join-Path $ResultsDir (Get-Date -Format 'yyyyMMdd-HHmmss')
New-Item -ItemType Directory -Path $runDir -Force | Out-Null
$runStdout = Join-Path $runDir 'k6.stdout.log'
$runStderr = Join-Path $runDir 'k6.stderr.log'
$summaryPath = Join-Path $runDir 'diagnostic-summary.json'
$samplesPath = Join-Path $runDir 'measurement-samples.json'

function Query-Prometheus([string]$Query) {
    try {
        $response = Invoke-RestMethod -Uri "$PrometheusUrl/api/v1/query" -Body @{ query = $Query }
        if ($response.data.result.Count -eq 0) { return $null }
        return [double]$response.data.result[0].value[1]
    } catch {
        throw 'Prometheus query failed.'
    }
}

function Get-MySqlAggregate([string]$ErrorPath) {
    $sql = @'
SELECT CONCAT(CHAR(100,105,103,101,115,116,95,99,111,117,110,116,61), COALESCE(SUM(COUNT_STAR),0)), CONCAT(CHAR(100,105,103,101,115,116,95,119,97,105,116,95,112,115,61), COALESCE(SUM(SUM_TIMER_WAIT),0)), CONCAT(CHAR(100,105,103,101,115,116,95,114,111,119,115,95,101,120,97,109,105,110,101,100,61), COALESCE(SUM(SUM_ROWS_EXAMINED),0)) FROM performance_schema.events_statements_summary_by_digest WHERE SCHEMA_NAME=DATABASE() AND (LOCATE(CHAR(112,114,111,100,117,99,116,115), DIGEST_TEXT)>0 OR LOCATE(CHAR(115,107,117,115), DIGEST_TEXT)>0);
SELECT CONCAT(CHAR(116,104,114,101,97,100,115,95,99,111,110,110,101,99,116,101,100,61), COALESCE(MAX(CASE WHEN VARIABLE_NAME=CHAR(84,104,114,101,97,100,115,95,99,111,110,110,101,99,116,101,100) THEN VARIABLE_VALUE ELSE 0 END),0)) FROM performance_schema.global_status;
'@
    $raw = $sql | docker exec -i $MysqlContainer sh -lc 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -N -uroot "$MYSQL_DATABASE"' 2> $ErrorPath
    $exitCode = $LASTEXITCODE
    Remove-Item -LiteralPath $ErrorPath -Force -ErrorAction SilentlyContinue
    if ($exitCode -ne 0) { throw "MySQL aggregate collector failed (exit code $exitCode)." }
    $values = [ordered]@{ digestCount = $null; digestWaitPs = $null; digestRowsExamined = $null; threadsConnected = $null }
    foreach ($part in (($raw -join "`n") -split "`t|`r?`n")) {
        if ($part -match '^digest_count=(\d+)$') { $values.digestCount = [double]$Matches[1] }
        elseif ($part -match '^digest_wait_ps=(\d+)$') { $values.digestWaitPs = [double]$Matches[1] }
        elseif ($part -match '^digest_rows_examined=(\d+)$') { $values.digestRowsExamined = [double]$Matches[1] }
        elseif ($part -match '^threads_connected=(\d+)$') { $values.threadsConnected = [double]$Matches[1] }
    }
    if ($null -in @($values.digestCount, $values.digestWaitPs, $values.digestRowsExamined, $values.threadsConnected)) { throw 'MySQL aggregate collector returned an unexpected format.' }
    return $values
}

function Get-ContainerEvidence([string]$ErrorPath) {
    $stats = @(docker stats --no-stream --format '{{.Name}}|{{.CPUPerc}}|{{.MemUsage}}|{{.MemPerc}}|{{.PIDs}}' 2> $ErrorPath | Where-Object { $_ -match 'pawcycle-local-integration-(backend|mysql|frontend|proxy)-1' })
    $statsExitCode = $LASTEXITCODE
    Remove-Item -LiteralPath $ErrorPath -Force -ErrorAction SilentlyContinue
    if ($statsExitCode -ne 0 -or $stats.Count -ne 4) { throw 'Docker stats collection failed.' }
    $health = @(docker inspect --format '{{.Name}}|health={{if .State.Health}}{{.State.Health.Status}}{{else}}no-health{{end}}|restarts={{.RestartCount}}|oom={{.State.OOMKilled}}' pawcycle-local-integration-backend-1 pawcycle-local-integration-mysql-1 pawcycle-local-integration-frontend-1 pawcycle-local-integration-proxy-1 2> $ErrorPath)
    $healthExitCode = $LASTEXITCODE
    Remove-Item -LiteralPath $ErrorPath -Force -ErrorAction SilentlyContinue
    if ($healthExitCode -ne 0 -or $health.Count -ne 4) { throw 'Docker health collection failed.' }
    if ($health | Where-Object { $_ -notmatch 'health=healthy\|restarts=0\|oom=False$' }) { throw 'Docker health state is not healthy.' }
    return [ordered]@{ stats = $stats; health = $health }
}

function Get-Snapshot([string]$Label, [string]$ErrorPath) {
    return [ordered]@{
        label = $Label
        timestampUtc = (Get-Date).ToUniversalTime().ToString('o')
        httpProductsCount = Query-Prometheus 'sum(http_server_requests_seconds_count{method="GET",uri="/api/products"})'
        httpProductsSumSeconds = Query-Prometheus 'sum(http_server_requests_seconds_sum{method="GET",uri="/api/products"})'
        hikariUsageCount = Query-Prometheus 'sum(hikaricp_connections_usage_seconds_count)'
        hikariUsageSeconds = Query-Prometheus 'sum(hikaricp_connections_usage_seconds_sum)'
        hikariAcquireCount = Query-Prometheus 'sum(hikaricp_connections_acquire_seconds_count)'
        hikariAcquireSeconds = Query-Prometheus 'sum(hikaricp_connections_acquire_seconds_sum)'
        hikariActive = Query-Prometheus 'sum(hikaricp_connections_active)'
        hikariIdle = Query-Prometheus 'sum(hikaricp_connections_idle)'
        hikariPending = Query-Prometheus 'sum(hikaricp_connections_pending)'
        hikariMax = Query-Prometheus 'sum(hikaricp_connections_max)'
        jvmHeapUsed = Query-Prometheus 'sum(jvm_memory_used_bytes{area="heap"})'
        jvmHeapCommitted = Query-Prometheus 'sum(jvm_memory_committed_bytes{area="heap"})'
        jvmNonHeapUsed = Query-Prometheus 'sum(jvm_memory_used_bytes{area="nonheap"})'
        jvmLiveThreads = Query-Prometheus 'sum(jvm_threads_live_threads)'
        jvmPeakThreads = Query-Prometheus 'sum(jvm_threads_peak_threads)'
        jvmGcPauseCount = Query-Prometheus 'sum(jvm_gc_pause_seconds_count)'
        jvmGcPauseSeconds = Query-Prometheus 'sum(jvm_gc_pause_seconds_sum)'
        processCpuUsage = Query-Prometheus 'sum(process_cpu_usage)'
        mysql = Get-MySqlAggregate $ErrorPath
        containers = Get-ContainerEvidence $ErrorPath
    }
}

function Delta([object]$Before, [object]$After) {
    if ($null -eq $Before -or $null -eq $After) { return $null }
    return [double]$After - [double]$Before
}

if ($ValidateCollectorOnly) {
    $validationErrorPath = [IO.Path]::GetTempFileName()
    try {
        [void](Get-MySqlAggregate $validationErrorPath)
        'Phase 9 MySQL aggregate collector validation passed.'
        exit 0
    } finally {
        Remove-Item -LiteralPath $validationErrorPath -Force -ErrorAction SilentlyContinue
    }
}

$collectorErrorPath = Join-Path $runDir 'collector-errors.tmp'
$preflight = Get-Snapshot 'preflight' $collectorErrorPath
$process = $null
try {
    $process = Start-Process -FilePath $K6Command -ArgumentList @('run', '-e', "BASE_URL=$BaseUrl", '-e', "TARGET_RPS=$TargetRps", $K6Script) -RedirectStandardOutput $runStdout -RedirectStandardError $runStderr -PassThru -NoNewWindow
    Start-Sleep -Seconds $WarmupSeconds
    $measurementStart = Get-Snapshot 'measurement-start' $collectorErrorPath
    $samples = [System.Collections.Generic.List[object]]::new()
    $deadline = (Get-Date).AddSeconds($MeasurementSeconds)
    while ((Get-Date) -lt $deadline -and -not $process.HasExited) {
        $samples.Add((Get-Snapshot 'measurement-sample' $collectorErrorPath))
        Start-Sleep -Seconds $SampleSeconds
    }
    $measurementEnd = Get-Snapshot 'measurement-end' $collectorErrorPath
    $process.WaitForExit()
    $processExit = $process.ExitCode
    if ($processExit -ne 0) { throw "k6 process failed (exit code $processExit)." }
} finally {
    if ($process -and -not $process.HasExited) {
        $process.Kill()
        $process.WaitForExit()
    }
    if ($process) { $process.Dispose() }
    Remove-Item -LiteralPath $collectorErrorPath -Force -ErrorAction SilentlyContinue
}

$summaryLine = Get-Content -LiteralPath $runStdout | Where-Object { $_ -match '"cohort"' -and $_ -match '"targetRps"' } | Select-Object -Last 1
$k6Summary = $null
if ($summaryLine) {
    $k6Summary = $summaryLine.Substring($summaryLine.IndexOf('{')) | ConvertFrom-Json
}
$completed = if ($k6Summary) { [double]$k6Summary.iterations } else { $null }
$digestDelta = Delta $measurementStart.mysql.digestCount $measurementEnd.mysql.digestCount
$usageDelta = Delta $measurementStart.hikariUsageCount $measurementEnd.hikariUsageCount
$acquireDelta = Delta $measurementStart.hikariAcquireCount $measurementEnd.hikariAcquireCount
$summary = [ordered]@{
    diagnostic = 'phase9-products-local'
    targetRps = $TargetRps
    processExit = $processExit
    k6 = $k6Summary
    preflight = $preflight
    measurementStart = $measurementStart
    measurementEnd = $measurementEnd
    sampleCount = $samples.Count
    queryIntervalSeconds = $SampleSeconds
    prometheusScrapeIntervalSeconds = 15
    peakObservationNote = 'activePeak and pendingPeak are maxima observed in stored Prometheus scrape samples; spikes between 15-second scrapes may be missed.'
    activePeak = (($samples | ForEach-Object { $_.hikariActive } | Where-Object { $null -ne $_ } | Measure-Object -Maximum).Maximum)
    pendingPeak = (($samples | ForEach-Object { $_.hikariPending } | Where-Object { $null -ne $_ } | Measure-Object -Maximum).Maximum)
    requestCountDelta = Delta $measurementStart.httpProductsCount $measurementEnd.httpProductsCount
    hikariUsageCountDelta = $usageDelta
    hikariAcquireCountDelta = $acquireDelta
    relevantSqlExecutionDelta = $digestDelta
    connectionBorrowPerCompletedRequest = if ($completed -and $completed -gt 0) { $usageDelta / $completed } else { $null }
    relevantSqlPerCompletedRequest = if ($completed -and $completed -gt 0) { $digestDelta / $completed } else { $null }
    connectionUsageSecondsDelta = Delta $measurementStart.hikariUsageSeconds $measurementEnd.hikariUsageSeconds
    connectionAcquireSecondsDelta = Delta $measurementStart.hikariAcquireSeconds $measurementEnd.hikariAcquireSeconds
    relevantSqlWaitPsDelta = Delta $measurementStart.mysql.digestWaitPs $measurementEnd.mysql.digestWaitPs
    relevantSqlRowsExaminedDelta = Delta $measurementStart.mysql.digestRowsExamined $measurementEnd.mysql.digestRowsExamined
    backgroundTrafficNote = 'Prometheus scrape, backend healthcheck, and other local traffic share HTTP/Hikari/Performance Schema counters; request-per-query and borrow-per-request are diagnostic estimates, not isolated exact values.'
    productionExecution = 'not run'
}
$summary | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $summaryPath -Encoding utf8
$samples | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $samplesPath -Encoding utf8
"resultsDir=$runDir"
"processExit=$processExit"
if ($k6Summary) { $k6Summary | ConvertTo-Json -Compress }
"activePeak=$($summary.activePeak) pendingPeak=$($summary.pendingPeak) requestCountDelta=$($summary.requestCountDelta) relevantSqlExecutionDelta=$($summary.relevantSqlExecutionDelta)"
