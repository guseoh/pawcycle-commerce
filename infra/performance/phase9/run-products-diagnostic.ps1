[CmdletBinding()]
param(
    [string]$K6Command = 'k6',
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$PrometheusUrl = 'http://127.0.0.1:9090',
    [string]$ResultsDir = (Join-Path $env:TEMP 'pawcycle-phase9-products'),
    [switch]$ValidateOnly,
    [switch]$ValidateCollectorOnly,
    [switch]$ValidateK6AggregateOnly,
    [switch]$ValidateFailureHandlingOnly,
    [switch]$ValidateTomcatOnly,
    [int]$ExpectedTomcatThreadsMax = 0
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
if ($PrometheusUrl -notmatch '^http://(127\.0\.0\.1|localhost|\[::1\])(?::[0-9]{1,5})?$') {
    throw 'PrometheusUrl must be an http loopback origin.'
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
$backendFinalStatePath = Join-Path $runDir 'backend-final-state.json'

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
        tomcatThreadsConfigMax = Query-Prometheus 'sum(tomcat_threads_config_max_threads)'
        tomcatThreadsCurrent = Query-Prometheus 'sum(tomcat_threads_current_threads)'
        tomcatThreadsBusy = Query-Prometheus 'sum(tomcat_threads_busy_threads)'
        jvmHeapUsed = Query-Prometheus 'sum(jvm_memory_used_bytes{area="heap"})'
        jvmHeapCommitted = Query-Prometheus 'sum(jvm_memory_committed_bytes{area="heap"})'
        jvmHeapMax = Query-Prometheus 'sum(jvm_memory_max_bytes{area="heap"})'
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

function Save-MeasurementSamples([string]$Path, [System.Collections.Generic.List[object]]$Samples) {
    $Samples | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $Path -Encoding utf8
}

function Save-BackendFinalState([string]$Path) {
    try {
        $line = @(docker inspect --format '{{.Name}}|health={{if .State.Health}}{{.State.Health.Status}}{{else}}no-health{{end}}|restart={{.RestartCount}}|oom={{.State.OOMKilled}}|exit={{.State.ExitCode}}|status={{.State.Status}}|memory={{.HostConfig.Memory}}|cpus={{.HostConfig.NanoCpus}}|pids={{.HostConfig.PidsLimit}}' pawcycle-local-integration-backend-1 2> $null)
        if ($LASTEXITCODE -ne 0 -or $line.Count -ne 1) { throw 'backend final state unavailable.' }
        $parts = $line[0] -split '\|'
        $evidence = [ordered]@{
            health = $parts[1] -replace '^health=', ''
            restartCount = [int]($parts[2] -replace '^restart=', '')
            oomKilled = [bool]::Parse(($parts[3] -replace '^oom=', ''))
            exitCode = [int]($parts[4] -replace '^exit=', '')
            status = $parts[5] -replace '^status=', ''
            memoryLimitBytes = [int64]($parts[6] -replace '^memory=', '')
            cpuLimitNanoCpus = [int64]($parts[7] -replace '^cpus=', '')
            pidsLimit = [int64]($parts[8] -replace '^pids=', '')
        }
    } catch {
        $evidence = [ordered]@{ available = $false; message = 'backend final state unavailable.' }
    }
    $evidence | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $Path -Encoding utf8
    return $evidence
}

function Write-DiagnosticSummaryArtifact([string]$Path, [object]$Summary) {
    $Summary | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $Path -Encoding utf8
}

function Add-CollectionError([string]$Stage, [string]$Source, [string]$Reason) {
    $script:CollectionErrors.Add([ordered]@{
        stage = $Stage
        source = $Source
        reason = $Reason
    })
}

function New-EmptyMySqlAggregate() {
    return [ordered]@{
        digestCount = $null
        digestWaitPs = $null
        digestRowsExamined = $null
        threadsConnected = $null
    }
}

function New-EmptySnapshot([string]$Label) {
    return [ordered]@{
        label = $Label
        timestampUtc = (Get-Date).ToUniversalTime().ToString('o')
        httpProductsCount = $null
        httpProductsSumSeconds = $null
        hikariUsageCount = $null
        hikariUsageSeconds = $null
        hikariAcquireCount = $null
        hikariAcquireSeconds = $null
        hikariActive = $null
        hikariIdle = $null
        hikariPending = $null
        hikariMax = $null
        tomcatThreadsConfigMax = $null
        tomcatThreadsCurrent = $null
        tomcatThreadsBusy = $null
        jvmHeapUsed = $null
        jvmHeapCommitted = $null
        jvmHeapMax = $null
        jvmNonHeapUsed = $null
        jvmLiveThreads = $null
        jvmPeakThreads = $null
        jvmGcPauseCount = $null
        jvmGcPauseSeconds = $null
        processCpuUsage = $null
        mysql = New-EmptyMySqlAggregate
        containers = $null
    }
}

function Should-RecordMetricCollectionError([string]$MetricKey, [int]$ExpectedTomcatMax) {
    return $ExpectedTomcatMax -gt 0 -or $MetricKey -notlike 'tomcatThreads*'
}

function Get-ResilientSnapshot([string]$Label, [string]$ErrorPath, [int]$ExpectedTomcatMax = 0) {
    $snapshot = New-EmptySnapshot $Label
    $metrics = [ordered]@{
        httpProductsCount = 'sum(http_server_requests_seconds_count{method="GET",uri="/api/products"})'
        httpProductsSumSeconds = 'sum(http_server_requests_seconds_sum{method="GET",uri="/api/products"})'
        hikariUsageCount = 'sum(hikaricp_connections_usage_seconds_count)'
        hikariUsageSeconds = 'sum(hikaricp_connections_usage_seconds_sum)'
        hikariAcquireCount = 'sum(hikaricp_connections_acquire_seconds_count)'
        hikariAcquireSeconds = 'sum(hikaricp_connections_acquire_seconds_sum)'
        hikariActive = 'sum(hikaricp_connections_active)'
        hikariIdle = 'sum(hikaricp_connections_idle)'
        hikariPending = 'sum(hikaricp_connections_pending)'
        hikariMax = 'sum(hikaricp_connections_max)'
        tomcatThreadsConfigMax = 'sum(tomcat_threads_config_max_threads)'
        tomcatThreadsCurrent = 'sum(tomcat_threads_current_threads)'
        tomcatThreadsBusy = 'sum(tomcat_threads_busy_threads)'
        jvmHeapUsed = 'sum(jvm_memory_used_bytes{area="heap"})'
        jvmHeapCommitted = 'sum(jvm_memory_committed_bytes{area="heap"})'
        jvmHeapMax = 'sum(jvm_memory_max_bytes{area="heap"})'
        jvmNonHeapUsed = 'sum(jvm_memory_used_bytes{area="nonheap"})'
        jvmLiveThreads = 'sum(jvm_threads_live_threads)'
        jvmPeakThreads = 'sum(jvm_threads_peak_threads)'
        jvmGcPauseCount = 'sum(jvm_gc_pause_seconds_count)'
        jvmGcPauseSeconds = 'sum(jvm_gc_pause_seconds_sum)'
        processCpuUsage = 'sum(process_cpu_usage)'
    }
    foreach ($metric in $metrics.GetEnumerator()) {
        try {
            $snapshot[$metric.Key] = Query-Prometheus $metric.Value
            if ($null -eq $snapshot[$metric.Key] -and (Should-RecordMetricCollectionError $metric.Key $ExpectedTomcatMax)) {
                Add-CollectionError $Label 'Prometheus' "$($metric.Key) unavailable."
            }
        } catch {
            $snapshot[$metric.Key] = $null
            if (Should-RecordMetricCollectionError $metric.Key $ExpectedTomcatMax) {
                Add-CollectionError $Label 'Prometheus' "$($metric.Key) collection unavailable."
            }
        }
    }
    try {
        $snapshot.mysql = Get-MySqlAggregate $ErrorPath
    } catch {
        $snapshot.mysql = New-EmptyMySqlAggregate
        Add-CollectionError $Label 'MySQL' 'aggregate collection unavailable.'
    }
    try {
        $snapshot.containers = Get-ContainerEvidence $ErrorPath
    } catch {
        $snapshot.containers = $null
        Add-CollectionError $Label 'Docker' 'container evidence unavailable.'
    }
    return $snapshot
}

function Get-DiagnosticOutcome([object]$K6Summary, [object]$ProcessExit, [bool]$CollectorFailure, [bool]$HarnessFailure) {
    $aggregateAvailable = $null -ne $K6Summary
    $thresholdFailure = $false
    if ($aggregateAvailable -and (([double]$K6Summary.droppedIterations -gt 0) -or ([double]$K6Summary.expectedStatusErrorRate -gt 0))) {
        $thresholdFailure = $true
    }
    $harnessFailure = $HarnessFailure -or $CollectorFailure -or (-not $aggregateAvailable) -or $null -eq $ProcessExit -or ([int]$ProcessExit -ne 0 -and -not $thresholdFailure)
    $outcome = if ($thresholdFailure -and $harnessFailure) { 'threshold-and-harness-failure' } elseif ($thresholdFailure) { 'threshold-failure' } elseif ($harnessFailure) { 'harness-or-collector-failure' } else { 'success' }
    return [ordered]@{
        outcome = $outcome
        aggregateAvailable = $aggregateAvailable
        thresholdFailure = $thresholdFailure
        harnessFailure = $harnessFailure
        collectorFailure = $CollectorFailure
        processExit = $ProcessExit
    }
}

function Parse-K6Aggregate([string[]]$Lines) {
    $summaryLine = $Lines | Where-Object { $_ -match '"cohort"' -and $_ -match '"targetRps"' } | Select-Object -Last 1
    if (-not $summaryLine -or $summaryLine.IndexOf('{') -lt 0) { throw 'k6 aggregate summary is missing.' }
    try {
        $aggregate = $summaryLine.Substring($summaryLine.IndexOf('{')) | ConvertFrom-Json
    } catch {
        throw 'k6 aggregate summary is malformed.'
    }
    $requiredProperties = @('cohort', 'targetRps', 'actualRps', 'iterations', 'droppedIterations', 'expectedStatusErrorRate', 'latencyMs')
    foreach ($property in $requiredProperties) {
        if (-not ($aggregate.PSObject.Properties.Name -contains $property) -or $null -eq $aggregate.$property) {
            throw "k6 aggregate summary is missing required field: $property."
        }
    }
    if ($aggregate.cohort -ne 'capacity-api-products' -or [double]$aggregate.targetRps -ne $TargetRps) {
        throw 'k6 aggregate summary does not match the expected capacity cohort.'
    }
    if ([double]$aggregate.iterations -le 0) { throw 'k6 aggregate summary has no completed iterations.' }
    foreach ($property in @('actualRps', 'droppedIterations', 'expectedStatusErrorRate')) {
        if ($null -eq $aggregate.$property -or -not ($aggregate.$property -is [ValueType])) {
            throw "k6 aggregate summary field is malformed: $property."
        }
    }
    foreach ($property in @('p50', 'p95', 'p99', 'max')) {
        if (-not ($aggregate.latencyMs.PSObject.Properties.Name -contains $property) -or $null -eq $aggregate.latencyMs.$property) {
            throw "k6 aggregate latency field is missing: $property."
        }
    }
    return $aggregate
}

function Assert-CriticalMetrics([object]$Snapshot, [int]$ExpectedTomcatMax = 0) {
    $categories = [ordered]@{
        'http products' = @('httpProductsCount')
        'hikari usage' = @('hikariUsageCount', 'hikariUsageSeconds')
        'hikari acquire' = @('hikariAcquireCount', 'hikariAcquireSeconds')
        'hikari pool' = @('hikariActive', 'hikariPending', 'hikariMax')
        'jvm memory' = @('jvmHeapUsed', 'jvmNonHeapUsed')
        'jvm threads' = @('jvmLiveThreads', 'jvmPeakThreads')
    }
    if ($ExpectedTomcatMax -gt 0) {
        $categories['tomcat threads'] = @('tomcatThreadsConfigMax', 'tomcatThreadsCurrent', 'tomcatThreadsBusy')
    }
    $unavailable = @(
        foreach ($category in $categories.Keys) {
            if ($categories[$category] | Where-Object { $null -eq $Snapshot.$_ }) { $category }
        }
    )
    if ($unavailable.Count -gt 0) {
        throw "Critical Prometheus metric categories unavailable: $($unavailable -join ', ')."
    }
    if ($ExpectedTomcatMax -gt 0) {
        Assert-TomcatExperimentMetrics $Snapshot $ExpectedTomcatMax
    }
}

function Assert-TomcatExperimentMetrics([object]$Snapshot, [int]$ExpectedTomcatMax) {
    if ($null -eq $Snapshot.tomcatThreadsConfigMax -or $null -eq $Snapshot.tomcatThreadsCurrent -or $null -eq $Snapshot.tomcatThreadsBusy) {
        throw 'Tomcat experiment requires all three thread metrics.'
    }
    if ([double]$Snapshot.tomcatThreadsConfigMax -ne $ExpectedTomcatMax) {
        throw "Tomcat experiment config max must be $ExpectedTomcatMax."
    }
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

if ($ValidateK6AggregateOnly) {
    $validFixture = '{"cohort":"capacity-api-products","targetRps":250,"actualRps":250,"droppedIterations":0,"iterations":30000,"latencyMs":{"p50":1,"p95":2,"p99":3,"max":4},"expectedStatusErrorRate":0}'
    [void](Parse-K6Aggregate @($validFixture))
    foreach ($invalidFixture in @(
        '',
        '{"cohort":"capacity-api-products","targetRps":250',
        '{"cohort":"capacity-api-products","targetRps":250,"iterations":1}',
        '{"cohort":"capacity-api-products","targetRps":250,"actualRps":250,"droppedIterations":0,"iterations":1,"latencyMs":{"p50":1,"p95":2,"p99":3},"expectedStatusErrorRate":0}',
        '{"cohort":"other","targetRps":250,"actualRps":250,"droppedIterations":0,"iterations":1,"latencyMs":{"p50":1,"p95":2,"p99":3,"max":4},"expectedStatusErrorRate":0}'
    )) {
        $rejected = $false
        try { [void](Parse-K6Aggregate @($invalidFixture)) } catch { $rejected = $true }
        if (-not $rejected) { throw 'k6 aggregate negative fixture unexpectedly passed.' }
    }
    'Phase 9 k6 aggregate validation passed.'
    exit 0
}

if ($ValidateFailureHandlingOnly) {
    $validFixture = '{"cohort":"capacity-api-products","targetRps":250,"actualRps":250,"droppedIterations":0,"iterations":30000,"latencyMs":{"p50":1,"p95":2,"p99":3,"max":4},"expectedStatusErrorRate":0}' | ConvertFrom-Json
    $validNonZeroOutcome = Get-DiagnosticOutcome $validFixture 7 $false $false
    if (-not $validNonZeroOutcome.aggregateAvailable -or $validNonZeroOutcome.processExit -ne 7 -or $validNonZeroOutcome.outcome -ne 'harness-or-collector-failure') {
        throw 'valid aggregate/non-zero exit synthetic validation failed.'
    }
    if (Should-RecordMetricCollectionError 'tomcatThreadsBusy' 0 -or -not (Should-RecordMetricCollectionError 'httpProductsCount' 0)) {
        throw 'general diagnostic Tomcat optional metric synthetic validation failed.'
    }
    $missingOutcome = Get-DiagnosticOutcome $null $null $true $false
    $fixturePath = Join-Path $env:TEMP ("pawcycle-phase9-failure-fixture-$([guid]::NewGuid()).json")
    try {
        Write-DiagnosticSummaryArtifact $fixturePath ([ordered]@{
            diagnostic = 'phase9-products-local'
            k6 = $null
            processExit = $null
            outcome = $missingOutcome.outcome
            collectorFailure = $true
            collectionErrors = @([ordered]@{ stage = 'measurement-sample'; source = 'Prometheus'; reason = 'metric unavailable.' })
            backendFinalState = [ordered]@{ available = $true; health = 'healthy'; restartCount = 0; oomKilled = $false }
        })
        $fixture = Get-Content -Raw -LiteralPath $fixturePath | ConvertFrom-Json
        if (-not (Test-Path -LiteralPath $fixturePath) -or $fixture.outcome -ne 'harness-or-collector-failure' -or $null -ne $fixture.k6 -or $null -eq $fixture.collectionErrors -or $null -eq $fixture.backendFinalState) {
            throw 'missing aggregate/failure summary synthetic validation failed.'
        }
    } finally {
        Remove-Item -LiteralPath $fixturePath -Force -ErrorAction SilentlyContinue
    }
    'Phase 9 failure handling synthetic validation passed.'
    exit 0
}

if ($ValidateTomcatOnly) {
    if ($ExpectedTomcatThreadsMax -le 0) {
        throw 'ValidateTomcatOnly requires ExpectedTomcatThreadsMax.'
    }
    $base = New-EmptySnapshot 'tomcat-base'
    foreach ($property in @('httpProductsCount', 'hikariUsageCount', 'hikariUsageSeconds', 'hikariAcquireCount', 'hikariAcquireSeconds', 'hikariActive', 'hikariPending', 'hikariMax', 'jvmHeapUsed', 'jvmNonHeapUsed', 'jvmLiveThreads', 'jvmPeakThreads')) {
        $base[$property] = 1
    }
    $missing = $base.PSObject.Copy()
    $missing.label = 'tomcat-missing'
    $wrongMax = New-EmptySnapshot 'tomcat-wrong-max'
    foreach ($property in @('httpProductsCount', 'hikariUsageCount', 'hikariUsageSeconds', 'hikariAcquireCount', 'hikariAcquireSeconds', 'hikariActive', 'hikariPending', 'hikariMax', 'jvmHeapUsed', 'jvmNonHeapUsed', 'jvmLiveThreads', 'jvmPeakThreads')) {
        $wrongMax[$property] = 1
    }
    $wrongMax.tomcatThreadsConfigMax = $ExpectedTomcatThreadsMax - 1
    $wrongMax.tomcatThreadsCurrent = 4
    $wrongMax.tomcatThreadsBusy = 1
    $valid = New-EmptySnapshot 'tomcat-valid'
    foreach ($property in @('httpProductsCount', 'hikariUsageCount', 'hikariUsageSeconds', 'hikariAcquireCount', 'hikariAcquireSeconds', 'hikariActive', 'hikariPending', 'hikariMax', 'jvmHeapUsed', 'jvmNonHeapUsed', 'jvmLiveThreads', 'jvmPeakThreads')) {
        $valid[$property] = 1
    }
    $valid.tomcatThreadsConfigMax = $ExpectedTomcatThreadsMax
    $valid.tomcatThreadsCurrent = 4
    $valid.tomcatThreadsBusy = 1
    foreach ($fixture in @($missing, $wrongMax)) {
        $rejected = $false
        try { Assert-CriticalMetrics $fixture $ExpectedTomcatThreadsMax } catch { $rejected = $true }
        if (-not $rejected) { throw 'Tomcat experiment negative fixture unexpectedly passed.' }
    }
    Assert-CriticalMetrics $valid $ExpectedTomcatThreadsMax
    'Phase 9 Tomcat experiment metric validation passed without starting k6.'
    exit 0
}

if ($ExpectedTomcatThreadsMax -lt 0) {
    throw 'ExpectedTomcatThreadsMax must be zero or positive.'
}

$collectorErrorPath = Join-Path $runDir 'collector-errors.tmp'
$script:CollectionErrors = [System.Collections.Generic.List[object]]::new()
$samples = [System.Collections.Generic.List[object]]::new()
Save-MeasurementSamples $samplesPath $samples
$backendFinalState = $null
$preflight = Get-Snapshot 'preflight' $collectorErrorPath
Assert-CriticalMetrics $preflight $ExpectedTomcatThreadsMax
$measurementStart = New-EmptySnapshot 'measurement-start'
$measurementEnd = New-EmptySnapshot 'measurement-end'
$processExit = $null
$harnessError = $null
$process = $null
try {
    $process = Start-Process -FilePath $K6Command -ArgumentList @('run', '-e', "BASE_URL=$BaseUrl", '-e', "TARGET_RPS=$TargetRps", $K6Script) -RedirectStandardOutput $runStdout -RedirectStandardError $runStderr -PassThru -NoNewWindow
    Start-Sleep -Seconds $WarmupSeconds
    $measurementStart = Get-ResilientSnapshot 'measurement-start' $collectorErrorPath $ExpectedTomcatThreadsMax
    $deadline = (Get-Date).AddSeconds($MeasurementSeconds)
    while ((Get-Date) -lt $deadline -and -not $process.HasExited) {
        $samples.Add((Get-ResilientSnapshot 'measurement-sample' $collectorErrorPath $ExpectedTomcatThreadsMax))
        Save-MeasurementSamples $samplesPath $samples
        Start-Sleep -Seconds $SampleSeconds
    }
    $measurementEnd = Get-ResilientSnapshot 'measurement-end' $collectorErrorPath $ExpectedTomcatThreadsMax
    $process.WaitForExit()
    $processExit = $process.ExitCode
} catch {
    $harnessError = 'harness execution failed.'
    Add-CollectionError 'harness' 'script' $harnessError
} finally {
    try {
        if ($process -and -not $process.HasExited) {
            $process.Kill()
            $process.WaitForExit()
        }
    } catch {
        Add-CollectionError 'cleanup' 'script' 'k6 cleanup failed.'
    }
    if ($process) { $process.Dispose() }
    try {
        Save-MeasurementSamples $samplesPath $samples
    } catch {
        Add-CollectionError 'cleanup' 'samples' 'measurement sample persistence failed.'
    }
    $backendFinalState = Save-BackendFinalState $backendFinalStatePath
    Remove-Item -LiteralPath $collectorErrorPath -Force -ErrorAction SilentlyContinue
}

$k6Summary = $null
$aggregateParseError = $null
try {
    $k6Summary = Parse-K6Aggregate (Get-Content -LiteralPath $runStdout)
} catch {
    $aggregateParseError = 'k6 aggregate is missing or malformed.'
    Add-CollectionError 'post-run' 'k6 aggregate' $aggregateParseError
}
$outcome = Get-DiagnosticOutcome $k6Summary $processExit ($CollectionErrors.Count -gt 0) ($null -ne $harnessError)
$completed = if ($k6Summary) { [double]$k6Summary.iterations } else { $null }
$digestDelta = Delta $measurementStart.mysql.digestCount $measurementEnd.mysql.digestCount
$usageDelta = Delta $measurementStart.hikariUsageCount $measurementEnd.hikariUsageCount
$acquireDelta = Delta $measurementStart.hikariAcquireCount $measurementEnd.hikariAcquireCount
$usageSecondsDelta = Delta $measurementStart.hikariUsageSeconds $measurementEnd.hikariUsageSeconds
$acquireSecondsDelta = Delta $measurementStart.hikariAcquireSeconds $measurementEnd.hikariAcquireSeconds
$relevantSqlWaitPsDelta = Delta $measurementStart.mysql.digestWaitPs $measurementEnd.mysql.digestWaitPs
$relevantSqlRowsExaminedDelta = Delta $measurementStart.mysql.digestRowsExamined $measurementEnd.mysql.digestRowsExamined
$summary = [ordered]@{
    diagnostic = 'phase9-products-local'
    targetRps = $TargetRps
    processExit = $processExit
    outcome = $outcome.outcome
    thresholdFailure = $outcome.thresholdFailure
    harnessFailure = $outcome.harnessFailure
    collectorFailure = $outcome.collectorFailure
    k6AggregateAvailable = $outcome.aggregateAvailable
    k6AggregateError = $aggregateParseError
    harnessError = $harnessError
    collectionErrors = @($CollectionErrors)
    k6 = $k6Summary
    preflight = $preflight
    measurementStart = $measurementStart
    measurementEnd = $measurementEnd
    sampleCount = $samples.Count
    backendFinalState = $backendFinalState
    queryIntervalSeconds = $SampleSeconds
    prometheusScrapeIntervalSeconds = 15
    peakObservationNote = 'activePeak and pendingPeak are maxima observed in stored Prometheus scrape samples; spikes between 15-second scrapes may be missed.'
    activePeak = (($samples | ForEach-Object { $_.hikariActive } | Where-Object { $null -ne $_ } | Measure-Object -Maximum).Maximum)
    pendingPeak = (($samples | ForEach-Object { $_.hikariPending } | Where-Object { $null -ne $_ } | Measure-Object -Maximum).Maximum)
    requestCountDelta = Delta $measurementStart.httpProductsCount $measurementEnd.httpProductsCount
    hikariUsageCountDelta = $usageDelta
    hikariAcquireCountDelta = $acquireDelta
    relevantSqlExecutionDelta = $digestDelta
    connectionBorrowPerCompletedRequest = if ($completed -and $completed -gt 0) { $acquireDelta / $completed } else { $null }
    connectionUsageReturnPerCompletedRequest = if ($completed -and $completed -gt 0) { $usageDelta / $completed } else { $null }
    relevantSqlPerCompletedRequest = if ($completed -and $completed -gt 0) { $digestDelta / $completed } else { $null }
    connectionUsageSecondsDelta = $usageSecondsDelta
    connectionAcquireSecondsDelta = $acquireSecondsDelta
    connectionAcquireMeanMs = if ($null -ne $acquireDelta -and $acquireDelta -ne 0) { $acquireSecondsDelta / $acquireDelta * 1000 } else { $null }
    connectionUsageMeanMs = if ($null -ne $usageDelta -and $usageDelta -ne 0) { $usageSecondsDelta / $usageDelta * 1000 } else { $null }
    relevantSqlWaitPsDelta = $relevantSqlWaitPsDelta
    relevantSqlRowsExaminedDelta = $relevantSqlRowsExaminedDelta
    relevantSqlMeanMs = if ($null -ne $digestDelta -and $digestDelta -ne 0) { $relevantSqlWaitPsDelta / $digestDelta / 1000000000 } else { $null }
    rowsExaminedPerRelevantSql = if ($null -ne $digestDelta -and $digestDelta -ne 0) { $relevantSqlRowsExaminedDelta / $digestDelta } else { $null }
    backgroundTrafficNote = 'Prometheus scrape, backend healthcheck, and other local traffic share HTTP/Hikari/Performance Schema counters; request-per-query and borrow-per-request are diagnostic estimates, not isolated exact values.'
    productionExecution = 'not run'
}
Write-DiagnosticSummaryArtifact $summaryPath $summary
Save-MeasurementSamples $samplesPath $samples
"resultsDir=$runDir"
"processExit=$processExit"
if ($k6Summary) { $k6Summary | ConvertTo-Json -Compress }
"activePeak=$($summary.activePeak) pendingPeak=$($summary.pendingPeak) requestCountDelta=$($summary.requestCountDelta) relevantSqlExecutionDelta=$($summary.relevantSqlExecutionDelta)"
