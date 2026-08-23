[CmdletBinding()]
param(
    [string]$K6Command = 'k6',
    [string]$ResultsDir = (Join-Path $env:TEMP 'pawcycle-phase10-redis-after'),
    [switch]$ValidateOnly,
    [switch]$ValidateRuntimeCapability,
    [switch]$RunAfterFirstResult
)

$ErrorActionPreference = 'Stop'
$RepoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$ResultsDir = [IO.Path]::GetFullPath($ResultsDir)
$TempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$repoRootPrefix = $RepoRoot.TrimEnd([char[]]@([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)) + [IO.Path]::DirectorySeparatorChar
$tempRootPrefix = $TempRoot.TrimEnd([char[]]@([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)) + [IO.Path]::DirectorySeparatorChar
$LocalIntegrationDir = Join-Path $RepoRoot 'infra\local-integration'
$DiagnosticScript = Join-Path $RepoRoot 'infra\performance\phase9\run-products-diagnostic.ps1'
$BackendContainer = 'pawcycle-local-integration-backend-1'
$RedisContainer = 'pawcycle-local-integration-redis-1'
$FirstResultMarker = Join-Path $TempRoot 'pawcycle-phase10-redis-after-first-result.started.json'

function Assert-SafeResultsDir([string]$Path) {
    $normalized = [IO.Path]::GetFullPath($Path)
    if ($normalized.Equals($RepoRoot, [StringComparison]::OrdinalIgnoreCase) -or $normalized.StartsWith($repoRootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'ResultsDir must be outside the repository.'
    }
    if (-not ($normalized.Equals($TempRoot, [StringComparison]::OrdinalIgnoreCase) -or $normalized.StartsWith($tempRootPrefix, [StringComparison]::OrdinalIgnoreCase))) {
        throw 'ResultsDir must be the host local temp directory or one of its descendants.'
    }
}

function Get-Cpu2ComposeArgs {
    return @('--env-file', '.env.local', '-f', 'compose.yaml', '-f', 'compose.prometheus.yaml', '-f', 'compose.phase9-envelope.yaml', '-f', 'compose.phase9-tomcat128.yaml', '-f', 'compose.phase9-cpu15.yaml', '-f', 'compose.phase9-memory1g.yaml', '-f', 'compose.phase9-cpu20.yaml')
}

function Get-PublishedLoopbackUrl([object[]]$ComposeArgs, [string]$Service, [int]$ContainerPort) {
    Push-Location $LocalIntegrationDir
    try {
        $portOutput = docker compose @ComposeArgs port $Service $ContainerPort
        if ($LASTEXITCODE -ne 0) { throw "$Service published port lookup failed." }
        $portMatch = [regex]::Match(($portOutput | Select-Object -First 1), ':(?<port>[0-9]+)$')
        if (-not $portMatch.Success) { throw "$Service published port is unavailable." }
        return "http://127.0.0.1:$($portMatch.Groups['port'].Value)"
    } finally {
        Pop-Location
    }
}

function Assert-Cpu2BackendState {
    $state = @(docker inspect --format 'health={{if .State.Health}}{{.State.Health.Status}}{{else}}no-health{{end}}|cpu={{.HostConfig.NanoCpus}}|memory={{.HostConfig.Memory}}|pids={{.HostConfig.PidsLimit}}|restart={{.RestartCount}}|oom={{.State.OOMKilled}}' $BackendContainer)
    if ($LASTEXITCODE -ne 0 -or $state.Count -ne 1 -or $state[0] -ne 'health=healthy|cpu=2000000000|memory=1073741824|pids=256|restart=0|oom=false') {
        throw 'Backend is not the expected CPU2.0 local envelope.'
    }
}

function Assert-RedisState {
    $state = @(docker inspect --format 'health={{if .State.Health}}{{.State.Health.Status}}{{else}}no-health{{end}}|cpu={{.HostConfig.NanoCpus}}|memory={{.HostConfig.Memory}}|pids={{.HostConfig.PidsLimit}}|restart={{.RestartCount}}|oom={{.State.OOMKilled}}' $RedisContainer)
    if ($LASTEXITCODE -ne 0 -or $state.Count -ne 1 -or $state[0] -ne 'health=healthy|cpu=500000000|memory=201326592|pids=64|restart=0|oom=false') {
        throw 'Redis is not the expected bounded local runtime.'
    }
}

function Assert-EffectiveMaxRamPercentage {
    $jvmOutput = @(docker exec $BackendContainer sh -lc 'java -XX:+PrintFlagsFinal -version 2>&1')
    $dockerExitCode = $LASTEXITCODE
    $flagLines = @($jvmOutput | Where-Object { [string]$_ -match '^\s*double\s+MaxRAMPercentage\s*=' })
    if ($dockerExitCode -ne 0 -or $flagLines.Count -ne 1) { throw 'Effective MaxRAMPercentage flag line was not observed exactly once.' }
    $match = [regex]::Match([string]$flagLines[0], 'MaxRAMPercentage\s*=\s*(?<value>[0-9]+(?:\.[0-9]+)?)')
    if (-not $match.Success) { throw 'Effective MaxRAMPercentage value could not be parsed.' }
    $value = [double]::Parse($match.Groups['value'].Value, [Globalization.CultureInfo]::InvariantCulture)
    if ([Math]::Abs($value - 65.0) -gt 0.000001) { throw "Effective MaxRAMPercentage mismatch: $value" }
}

function Query-Prometheus([string]$PrometheusUrl, [string]$Query) {
    $response = Invoke-RestMethod -TimeoutSec 2 -Uri "$PrometheusUrl/api/v1/query" -Body @{ query = $Query }
    if ($response.status -ne 'success' -or $response.data.result.Count -ne 1) { throw "Prometheus metric unavailable: $Query" }
    return [double]$response.data.result[0].value[1]
}

function Assert-RuntimeMetrics([string]$PrometheusUrl, [double]$FreshAfter) {
    $deadline = (Get-Date).AddSeconds(45)
    do {
        Start-Sleep -Seconds 2
        try {
            $tomcatMax = Query-Prometheus $PrometheusUrl 'sum(tomcat_threads_config_max_threads)'
            $hikariMax = Query-Prometheus $PrometheusUrl 'sum(hikaricp_connections_max)'
            $cacheHit = Query-Prometheus $PrometheusUrl 'sum(pawcycle_catalog_product_list_cache_operations_total{result="hit"})'
            $cacheMiss = Query-Prometheus $PrometheusUrl 'sum(pawcycle_catalog_product_list_cache_operations_total{result="miss"})'
            $cacheError = Query-Prometheus $PrometheusUrl 'sum(pawcycle_catalog_product_list_cache_operations_total{result="error"})'
            $freshness = Query-Prometheus $PrometheusUrl 'min(timestamp({__name__=~"tomcat_threads_config_max_threads|hikaricp_connections_max|pawcycle_catalog_product_list_cache_operations_total"}))'
            if ($tomcatMax -eq 128 -and $hikariMax -eq 10 -and $cacheHit -gt 0 -and $cacheMiss -ge 0 -and $cacheError -ge 0 -and $freshness -ge $FreshAfter) {
                return [ordered]@{ tomcatMax = $tomcatMax; hikariMax = $hikariMax; cacheHit = $cacheHit; cacheMiss = $cacheMiss; cacheError = $cacheError; freshnessTimestamp = $freshness }
            }
        } catch {
        }
    } while ((Get-Date) -lt $deadline)
    throw 'Fresh Tomcat128/Hikari10/cache-hit runtime evidence was not observed.'
}

function Assert-RuntimeCapability([object[]]$ComposeArgs, [double]$FreshAfter) {
    Assert-Cpu2BackendState
    Assert-RedisState
    Assert-EffectiveMaxRamPercentage
    $prometheusUrl = Get-PublishedLoopbackUrl $ComposeArgs 'prometheus' 9090
    $metrics = Assert-RuntimeMetrics $prometheusUrl $FreshAfter
    return [ordered]@{ prometheusUrl = $prometheusUrl; metrics = $metrics }
}

function Save-Marker([object]$Marker) {
    $Marker | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $FirstResultMarker -Encoding utf8
}

function Get-ValidWorkloadStartedMarker([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        throw 'Redis After diagnostic did not record an authoritative workload-start marker.'
    }
    $marker = Get-Content -Raw -LiteralPath $Path | ConvertFrom-Json
    if (-not $marker.workloadInvocationStarted -or $null -eq $marker.k6ProcessId) {
        throw 'Redis After workload-start marker is invalid.'
    }
    return $marker
}

Assert-SafeResultsDir $ResultsDir
$modes = @($ValidateOnly, $ValidateRuntimeCapability, $RunAfterFirstResult) | Where-Object { $_ }
if ($modes.Count -ne 1) { throw 'Specify exactly one mode: ValidateOnly, ValidateRuntimeCapability, or RunAfterFirstResult.' }

if ($ValidateOnly) {
    if (-not (Test-Path -LiteralPath $DiagnosticScript)) { throw 'Existing products diagnostic is unavailable.' }
    $composeArgs = Get-Cpu2ComposeArgs
    foreach ($required in @('compose.phase9-envelope.yaml', 'compose.phase9-tomcat128.yaml', 'compose.phase9-memory1g.yaml', 'compose.phase9-cpu20.yaml')) {
        if ($composeArgs -notcontains $required) { throw "CPU2.0 compose contract is missing: $required" }
    }
    & $DiagnosticScript -ValidateProductListCacheOnly
    if ($LASTEXITCODE -ne 0) { throw 'Product list cache diagnostic validation failed.' }
    $syntheticMarker = Join-Path $TempRoot ("pawcycle-phase10-redis-after-marker-validation-$([guid]::NewGuid()).json")
    try {
        $preWorkloadRejected = $false
        try {
            & $DiagnosticScript -ValidateProductListCacheOnly -BaseUrl 'https://invalid.example' -WorkloadStartedMarkerPath $syntheticMarker
        } catch {
            $preWorkloadRejected = $true
        }
        if (-not $preWorkloadRejected -or (Test-Path -LiteralPath $syntheticMarker)) {
            throw 'Synthetic pre-workload failure unexpectedly consumed the first-result marker.'
        }

        & $DiagnosticScript -ValidateWorkloadStartMarkerOnly -WorkloadStartedMarkerPath $syntheticMarker
        $startedMarker = Get-ValidWorkloadStartedMarker $syntheticMarker
        try { throw 'synthetic post-workload-start failure' } catch { }
        $retainedMarker = Get-ValidWorkloadStartedMarker $syntheticMarker
        if (-not $retainedMarker.workloadInvocationStarted -or $retainedMarker.k6ProcessId -ne $startedMarker.k6ProcessId) {
            throw 'Synthetic post-workload-start failure did not preserve the consumed marker.'
        }
    } finally {
        Remove-Item -LiteralPath $syntheticMarker -Force -ErrorAction SilentlyContinue
    }
    'Phase 10 Redis After harness validation passed without starting k6.'
    exit 0
}

$composeArgs = Get-Cpu2ComposeArgs
if ($ValidateRuntimeCapability) {
    $runtime = Assert-RuntimeCapability $composeArgs ([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())
    "Phase 10 Redis runtime capability passed (Prometheus: $($runtime.prometheusUrl))."
    exit 0
}

if (Test-Path -LiteralPath $FirstResultMarker) {
    throw 'Redis After first-result was already started; NEVER RERUN.'
}
New-Item -ItemType Directory -Path $ResultsDir -Force | Out-Null

$freshAfter = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
Push-Location $LocalIntegrationDir
try {
    docker compose @composeArgs up --build -d --wait --wait-timeout 120 mysql redis backend frontend proxy prometheus
    if ($LASTEXITCODE -ne 0) { throw 'Redis After candidate services did not become healthy.' }
} finally {
    Pop-Location
}

$runtime = Assert-RuntimeCapability $composeArgs $freshAfter
$baseUrl = Get-PublishedLoopbackUrl $composeArgs 'proxy' 80
& $DiagnosticScript -K6Command $K6Command -BaseUrl $baseUrl -PrometheusUrl $runtime.prometheusUrl -ResultsDir (Join-Path $ResultsDir 'diagnostic') -ExpectedTomcatThreadsMax 128 -ExpectedHikariPoolMax 10 -RequireProductListCache -WorkloadStartedMarkerPath $FirstResultMarker

$startedMarker = Get-ValidWorkloadStartedMarker $FirstResultMarker
$marker = [ordered]@{
    diagnostic = 'phase10-products-redis-after-local'
    beforeEvidence = 'PERF-PH9-010'
    startedAtUtc = $startedMarker.workloadStartedAtUtc
    workloadInvocationStarted = $true
    k6ProcessId = $startedMarker.k6ProcessId
    completed = $false
    outcome = $null
    runtime = $runtime
}
Save-Marker $marker

$summaries = @(Get-ChildItem -LiteralPath (Join-Path $ResultsDir 'diagnostic') -Filter 'diagnostic-summary.json' -Recurse -File)
if ($summaries.Count -ne 1) { throw 'Redis After diagnostic summary was not produced exactly once.' }
$summary = Get-Content -Raw -LiteralPath $summaries[0].FullName | ConvertFrom-Json
$marker.completed = $true
$marker.outcome = $summary.outcome
$marker['summaryPath'] = $summaries[0].FullName
Save-Marker $marker
"Redis After first-result completed once: $($summary.outcome)"
