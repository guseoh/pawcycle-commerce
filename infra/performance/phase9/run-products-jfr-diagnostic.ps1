[CmdletBinding()]
param(
    [string]$ResultsDir = (Join-Path $env:TEMP 'pawcycle-phase9-jfr'),
    [string]$JfrCommand = 'jfr',
    [switch]$ValidateOnly,
    [switch]$ValidateRuntimeCapability,
    [switch]$RunProfiling
)

$ErrorActionPreference = 'Stop'
$RepoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
$ResultsDir = [IO.Path]::GetFullPath($ResultsDir)
$TempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$repoRootPrefix = $RepoRoot.TrimEnd([char[]]@([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)) + [IO.Path]::DirectorySeparatorChar
$tempRootPrefix = $TempRoot.TrimEnd([char[]]@([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)) + [IO.Path]::DirectorySeparatorChar
$BackendContainer = 'pawcycle-local-integration-backend-1'
$DiagnosticScript = Join-Path $PSScriptRoot 'run-products-diagnostic.ps1'
$JfrDurationSeconds = 300

function Assert-SafeResultsDir([string]$Path) {
    $normalized = [IO.Path]::GetFullPath($Path)
    if ($normalized.Equals($RepoRoot, [StringComparison]::OrdinalIgnoreCase) -or $normalized.StartsWith($repoRootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'ResultsDir must be outside the repository.'
    }
    if (-not ($normalized.Equals($TempRoot, [StringComparison]::OrdinalIgnoreCase) -or $normalized.StartsWith($tempRootPrefix, [StringComparison]::OrdinalIgnoreCase))) {
        throw 'ResultsDir must be the host local temp directory or one of its descendants.'
    }
}

function New-JfrRunPaths([string]$Root, [string]$RunId) {
    $runDir = Join-Path $Root "phase9-products-jfr-$RunId"
    if (Test-Path -LiteralPath $runDir) { throw 'JFR result directory already exists; refusing to overwrite evidence.' }
    [ordered]@{
        runDir = $runDir
        artifactPath = Join-Path $runDir 'products-profile.jfr'
        summaryPath = Join-Path $runDir 'jfr-summary.txt'
        metadataPath = Join-Path $runDir 'profiling-metadata.json'
        containerArtifactPath = "/tmp/phase9-products-$RunId.jfr"
    }
}

function Get-JfrStartupOption([string]$ContainerArtifactPath) {
    if ($ContainerArtifactPath -notmatch '^/tmp/phase9-products-[0-9a-f-]+\.jfr$') { throw 'JFR container artifact path is invalid.' }
    return "-XX:StartFlightRecording=name=phase9-products,settings=profile,filename=$ContainerArtifactPath,duration=$($JfrDurationSeconds)s"
}

function Assert-Cpu2BackendState {
    $state = @(docker inspect --format 'health={{if .State.Health}}{{.State.Health.Status}}{{else}}no-health{{end}}|cpu={{.HostConfig.NanoCpus}}|memory={{.HostConfig.Memory}}|pids={{.HostConfig.PidsLimit}}|restart={{.RestartCount}}|oom={{.State.OOMKilled}}' $BackendContainer)
    if ($LASTEXITCODE -ne 0 -or $state.Count -ne 1 -or $state[0] -ne 'health=healthy|cpu=2000000000|memory=1073741824|pids=256|restart=0|oom=false') {
        throw 'CPU2.0 candidate backend state is not the expected local envelope.'
    }
}

function Assert-EffectiveMaxRamPercentage {
    $flagLines = @(docker exec $BackendContainer java -XX:+PrintFlagsFinal -version 2>&1 | Where-Object { [string]$_ -match '^\s*double\s+MaxRAMPercentage\s*=' })
    if ($LASTEXITCODE -ne 0 -or $flagLines.Count -ne 1) {
        throw 'Effective MaxRAMPercentage flag line was not observed exactly once.'
    }
    $match = [regex]::Match([string]$flagLines[0], 'MaxRAMPercentage\s*=\s*(?<value>[0-9]+(?:\.[0-9]+)?)')
    if (-not $match.Success) { throw 'Effective MaxRAMPercentage value could not be parsed.' }
    $value = [double]::Parse($match.Groups['value'].Value, [Globalization.CultureInfo]::InvariantCulture)
    if ([Math]::Abs($value - 65.0) -gt 0.000001) { throw "Effective MaxRAMPercentage mismatch: $value" }
    return $value
}

function Get-JfrRuntimeTools {
    $tools = [ordered]@{}
    foreach ($tool in @('java', 'jfr', 'jcmd')) {
        docker exec $BackendContainer sh -c "command -v $tool >/dev/null 2>&1"
        $tools[$tool] = ($LASTEXITCODE -eq 0)
    }
    return $tools
}

function Assert-JfrRuntimeCapability([object]$Tools) {
    if (-not $Tools.java -or -not $Tools.jfr) { throw 'Current backend runtime lacks required java or jfr command.' }
}

function Save-Metadata([string]$Path, [object]$Metadata) {
    $Metadata | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $Path -Encoding utf8
}

function Test-ExpectedFreshRuntimeMetrics(
        [int]$HikariValue,
        [int]$TomcatValue,
        [double]$HikariTimestamp,
        [double]$TomcatTimestamp,
        [double]$FreshAfter) {
    return (
        $HikariValue -eq 10 -and
        $TomcatValue -eq 128 -and
        $HikariTimestamp -ge $FreshAfter -and
        $TomcatTimestamp -ge $FreshAfter
    )
}

function Get-Cpu2ComposeArgs {
    return @('--env-file', '.env.local', '-f', 'compose.yaml', '-f', 'compose.prometheus.yaml', '-f', 'compose.phase9-envelope.yaml', '-f', 'compose.phase9-tomcat128.yaml', '-f', 'compose.phase9-cpu15.yaml', '-f', 'compose.phase9-memory1g.yaml', '-f', 'compose.phase9-cpu20.yaml')
}

function Get-JfrComposeArgs([object[]]$Cpu2ComposeArgs) {
    return @($Cpu2ComposeArgs + @('-f', 'compose.phase9-jfr.yaml'))
}

function Get-PrometheusUrl([object[]]$ComposeArgs) {
    Push-Location (Join-Path $RepoRoot 'infra\local-integration')
    try {
        $portOutput = docker compose @ComposeArgs port prometheus 9090
        if ($LASTEXITCODE -ne 0) { throw 'Prometheus published port lookup failed.' }
        $portMatch = [regex]::Match(($portOutput | Select-Object -First 1), ':(?<port>[0-9]+)$')
        if (-not $portMatch.Success) { throw 'Prometheus published port is unavailable.' }
        return "http://127.0.0.1:$($portMatch.Groups['port'].Value)"
    } finally {
        Pop-Location
    }
}

function Assert-FreshJfrBackendRuntime([object[]]$Cpu2ComposeArgs) {
    Assert-Cpu2BackendState
    $maxRamPercentage = Assert-EffectiveMaxRamPercentage
    $prometheusUrl = Get-PrometheusUrl $Cpu2ComposeArgs

    $freshAfter = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $deadline = (Get-Date).AddSeconds(45)
    do {
        Start-Sleep -Seconds 2
        try {
            $evaluationTime = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
            $hikari = Invoke-RestMethod -TimeoutSec 2 -Uri "$prometheusUrl/api/v1/query?query=sum%28hikaricp_connections_max%29&time=$evaluationTime"
            $hikariTimestamp = Invoke-RestMethod -TimeoutSec 2 -Uri "$prometheusUrl/api/v1/query?query=max%28timestamp%28hikaricp_connections_max%29%29&time=$evaluationTime"
            $tomcat = Invoke-RestMethod -TimeoutSec 2 -Uri "$prometheusUrl/api/v1/query?query=sum%28tomcat_threads_config_max_threads%29&time=$evaluationTime"
            $tomcatTimestamp = Invoke-RestMethod -TimeoutSec 2 -Uri "$prometheusUrl/api/v1/query?query=max%28timestamp%28tomcat_threads_config_max_threads%29%29&time=$evaluationTime"
            if (
                $hikari.status -eq 'success' -and $hikari.data.result.Count -eq 1 -and
                $hikariTimestamp.status -eq 'success' -and $hikariTimestamp.data.result.Count -eq 1 -and
                $tomcat.status -eq 'success' -and $tomcat.data.result.Count -eq 1 -and
                $tomcatTimestamp.status -eq 'success' -and $tomcatTimestamp.data.result.Count -eq 1
            ) {
                $hikariValue = [int]$hikari.data.result[0].value[1]
                $tomcatValue = [int]$tomcat.data.result[0].value[1]
                $hikariScrapeTimestamp = [double]$hikariTimestamp.data.result[0].value[1]
                $tomcatScrapeTimestamp = [double]$tomcatTimestamp.data.result[0].value[1]
                if (Test-ExpectedFreshRuntimeMetrics $hikariValue $tomcatValue $hikariScrapeTimestamp $tomcatScrapeTimestamp $freshAfter) {
                    return [ordered]@{
                        maxRamPercentage = $maxRamPercentage
                        prometheusUrl = $prometheusUrl
                        hikariMax = $hikariValue
                        tomcatMax = $tomcatValue
                        hikariFreshTimestamp = $hikariScrapeTimestamp
                        tomcatFreshTimestamp = $tomcatScrapeTimestamp
                    }
                }
            }
        } catch {
        }
    } while ((Get-Date) -lt $deadline)

    throw 'Fresh JFR backend Hikari10/Tomcat128 runtime evidence was not observed.'
}

function Wait-JfrArtifact([string]$ContainerArtifactPath, [int]$TimeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        docker exec $BackendContainer sh -c "test -s '$ContainerArtifactPath'"
        if ($LASTEXITCODE -eq 0) { return $true }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    return $false
}

function Copy-JfrArtifact([object]$Paths) {
    if (Test-Path -LiteralPath $Paths.artifactPath) { throw 'JFR artifact path already exists; refusing to overwrite evidence.' }
    docker cp "${BackendContainer}:$($Paths.containerArtifactPath)" $Paths.artifactPath
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $Paths.artifactPath) -or (Get-Item -LiteralPath $Paths.artifactPath).Length -le 0) {
        throw 'JFR artifact copy failed.'
    }
}

function Write-JfrSummary([object]$Paths) {
    & $JfrCommand summary $Paths.artifactPath | Set-Content -LiteralPath $Paths.summaryPath -Encoding utf8
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $Paths.summaryPath)) { throw 'Host JFR summary failed.' }
}

function Invoke-Cpu2Rollback {
    $composeArgs = Get-Cpu2ComposeArgs
    Push-Location (Join-Path $RepoRoot 'infra\local-integration')
    try {
        docker compose @composeArgs up -d --no-deps --force-recreate --wait --wait-timeout 120 backend
        if ($LASTEXITCODE -ne 0) { throw 'CPU2.0 rollback backend did not become healthy.' }
    } finally {
        Pop-Location
    }
}

function Assert-ProfilingDiagnosticSummary([object]$Summary) {
    if ($null -eq $Summary) { throw 'Phase 9 diagnostic summary is unavailable.' }
    if (-not $Summary.k6AggregateAvailable) { throw 'Phase 9 diagnostic aggregate is unavailable; first-result evidence is incomplete.' }
    if ($Summary.harnessFailure) { throw 'Phase 9 diagnostic reported a harness failure; first-result evidence was preserved.' }
}

Assert-SafeResultsDir $ResultsDir
$modes = @($ValidateOnly, $ValidateRuntimeCapability, $RunProfiling) | Where-Object { $_ }
if ($modes.Count -ne 1) { throw 'Specify exactly one mode: ValidateOnly, ValidateRuntimeCapability, or RunProfiling.' }

if ($ValidateOnly) {
    $valid = New-JfrRunPaths $TempRoot '11111111-1111-1111-1111-111111111111'
    if ((Get-JfrStartupOption $valid.containerArtifactPath) -notmatch 'StartFlightRecording=.*duration=300s' -or (Get-JfrStartupOption $valid.containerArtifactPath) -match 'jcmd') {
        throw 'JFR lifecycle command generation validation failed.'
    }
    $rejectedPath = $false
    try { [void](Get-JfrStartupOption '/unsafe.jfr') } catch { $rejectedPath = $true }
    if (-not $rejectedPath) { throw 'Invalid JFR container artifact path unexpectedly passed.' }

    $repositoryPathRejected = $false
    try { Assert-SafeResultsDir $RepoRoot } catch { $repositoryPathRejected = $true }
    if (-not $repositoryPathRejected) { throw 'Repository results directory unexpectedly passed.' }

    Assert-SafeResultsDir (Join-Path $TempRoot 'pawcycle-phase9-jfr-validation')
    $outsideTempRejected = $false
    $outsideTempPath = Join-Path ([IO.Path]::GetPathRoot($TempRoot)) 'pawcycle-phase9-jfr-outside-temp'
    if (-not [IO.Path]::GetFullPath($outsideTempPath).StartsWith($tempRootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        try { Assert-SafeResultsDir $outsideTempPath } catch { $outsideTempRejected = $true }
        if (-not $outsideTempRejected) { throw 'Path outside host local temp unexpectedly passed.' }
    }

    $overwriteRoot = Join-Path $TempRoot ("pawcycle-phase9-jfr-overwrite-$([guid]::NewGuid())")
    try {
        $overwritePaths = New-JfrRunPaths $overwriteRoot '22222222-2222-2222-2222-222222222222'
        New-Item -ItemType Directory -Path $overwritePaths.runDir -ErrorAction Stop | Out-Null
        $overwriteRejected = $false
        try { [void](New-JfrRunPaths $overwriteRoot '22222222-2222-2222-2222-222222222222') } catch { $overwriteRejected = $true }
        if (-not $overwriteRejected) { throw 'Existing JFR result directory unexpectedly passed.' }
    } finally {
        Remove-Item -LiteralPath $overwriteRoot -Recurse -Force -ErrorAction SilentlyContinue
    }

    $missingToolsRejected = $false
    try { Assert-JfrRuntimeCapability ([pscustomobject]@{ java = $true; jfr = $false; jcmd = $false }) } catch { $missingToolsRejected = $true }
    if (-not $missingToolsRejected) { throw 'Unavailable JFR tool fixture unexpectedly passed.' }
    Assert-JfrRuntimeCapability ([pscustomobject]@{ java = $true; jfr = $true; jcmd = $false })

    $freshAfter = 1000.0
    if (-not (Test-ExpectedFreshRuntimeMetrics 10 128 1001.0 1001.0 $freshAfter)) { throw 'Fresh expected runtime fixture failed.' }
    if (Test-ExpectedFreshRuntimeMetrics 10 128 999.0 1001.0 $freshAfter) { throw 'Stale Hikari runtime fixture unexpectedly passed.' }
    if (Test-ExpectedFreshRuntimeMetrics 20 128 1001.0 1001.0 $freshAfter) { throw 'Wrong Hikari runtime fixture unexpectedly passed.' }

    $cpu2ComposeArgs = Get-Cpu2ComposeArgs
    $jfrComposeArgs = Get-JfrComposeArgs $cpu2ComposeArgs
    if ($cpu2ComposeArgs -contains 'compose.phase9-jfr.yaml' -or -not ($jfrComposeArgs -contains 'compose.phase9-jfr.yaml')) {
        throw 'JFR compose overlay separation validation failed.'
    }

    Assert-ProfilingDiagnosticSummary ([pscustomobject]@{ k6AggregateAvailable = $true; harnessFailure = $false; thresholdFailure = $true; collectorFailure = $false; outcome = 'threshold-failure' })
    $harnessFailureRejected = $false
    try { Assert-ProfilingDiagnosticSummary ([pscustomobject]@{ k6AggregateAvailable = $true; harnessFailure = $true; thresholdFailure = $false; collectorFailure = $false; outcome = 'harness-failure' }) } catch { $harnessFailureRejected = $true }
    if (-not $harnessFailureRejected) { throw 'Harness failure fixture unexpectedly passed.' }

    'Phase 9 JFR lifecycle validation passed without starting k6.'
    exit 0
}

if ($ValidateRuntimeCapability) {
    Assert-Cpu2BackendState
    $tools = Get-JfrRuntimeTools
    Assert-JfrRuntimeCapability $tools
    & $JfrCommand --version | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Host JFR command is unavailable.' }
    $previousRecordingPath = [Environment]::GetEnvironmentVariable('PAWCYCLE_PHASE9_JFR_RECORDING_PATH', 'Process')
    try {
        Remove-Item Env:PAWCYCLE_PHASE9_JFR_RECORDING_PATH -ErrorAction SilentlyContinue
        $prometheusUrl = Get-PrometheusUrl (Get-Cpu2ComposeArgs)
    } finally {
        if ($null -eq $previousRecordingPath) {
            Remove-Item Env:PAWCYCLE_PHASE9_JFR_RECORDING_PATH -ErrorAction SilentlyContinue
        } else {
            $env:PAWCYCLE_PHASE9_JFR_RECORDING_PATH = $previousRecordingPath
        }
    }
    "Phase 9 JFR runtime capability passed (jcmd available: $($tools.jcmd); fresh-runtime Prometheus lookup: $prometheusUrl)."
    exit 0
}

$runId = [guid]::NewGuid().ToString()
$paths = New-JfrRunPaths $ResultsDir $runId
New-Item -ItemType Directory -Path $paths.runDir -ErrorAction Stop | Out-Null
$metadata = [ordered]@{
    diagnostic = 'phase9-products-jfr-local'
    runId = $runId
    startedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
    jfrDurationSeconds = $JfrDurationSeconds
    containerArtifactPath = $paths.containerArtifactPath
    jcmdRequired = $false
    jfrBackendRuntimeGuardPassed = $false
    maxRamPercentage = $null
    hikariMax = $null
    tomcatMax = $null
    hikariFreshTimestamp = $null
    tomcatFreshTimestamp = $null
    jfrArtifactCopied = $false
    jfrSummaryCreated = $false
    jfrSummaryError = $null
    diagnosticInvocationStarted = $false
    workloadEvidenceAvailable = $false
    diagnosticExitCode = $null
    diagnosticOutcome = $null
    thresholdFailure = $null
    harnessFailure = $null
    collectorFailure = $null
    k6AggregateAvailable = $null
    rollbackCompleted = $false
}
Save-Metadata $paths.metadataPath $metadata
$jfrBackendStarted = $false
$postRunError = $null
$diagnosticSummary = $null

try {
    Assert-Cpu2BackendState
    $tools = Get-JfrRuntimeTools
    Assert-JfrRuntimeCapability $tools
    & $JfrCommand --version | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Host JFR command is unavailable.' }

    $env:PAWCYCLE_PHASE9_JFR_RECORDING_PATH = $paths.containerArtifactPath
    Push-Location (Join-Path $RepoRoot 'infra\local-integration')
    try {
        $cpu2ComposeArgs = Get-Cpu2ComposeArgs
        $jfrComposeArgs = Get-JfrComposeArgs $cpu2ComposeArgs
        docker compose @jfrComposeArgs up -d --no-deps --force-recreate --wait --wait-timeout 120 backend
        if ($LASTEXITCODE -ne 0) { throw 'JFR backend start did not become healthy.' }
        $jfrBackendStarted = $true
    } finally {
        Pop-Location
        Remove-Item Env:PAWCYCLE_PHASE9_JFR_RECORDING_PATH -ErrorAction SilentlyContinue
    }

    $runtimeEvidence = Assert-FreshJfrBackendRuntime $cpu2ComposeArgs
    $metadata.jfrBackendRuntimeGuardPassed = $true
    $metadata.maxRamPercentage = $runtimeEvidence.maxRamPercentage
    $metadata.hikariMax = $runtimeEvidence.hikariMax
    $metadata.tomcatMax = $runtimeEvidence.tomcatMax
    $metadata.hikariFreshTimestamp = $runtimeEvidence.hikariFreshTimestamp
    $metadata.tomcatFreshTimestamp = $runtimeEvidence.tomcatFreshTimestamp
    Save-Metadata $paths.metadataPath $metadata

    $metadata.diagnosticInvocationStarted = $true
    Save-Metadata $paths.metadataPath $metadata
    & $DiagnosticScript -ResultsDir (Join-Path $paths.runDir 'diagnostic') -ExpectedTomcatThreadsMax 128 -ExpectedHikariPoolMax 10
    $summaries = @(Get-ChildItem -LiteralPath (Join-Path $paths.runDir 'diagnostic') -Filter 'diagnostic-summary.json' -Recurse -File)
    if ($summaries.Count -ne 1) { throw 'Phase 9 diagnostic summary was not produced exactly once.' }
    $diagnosticSummary = Get-Content -Raw -LiteralPath $summaries[0].FullName | ConvertFrom-Json
    $metadata.diagnosticExitCode = $diagnosticSummary.processExit
    $metadata.diagnosticOutcome = $diagnosticSummary.outcome
    $metadata.thresholdFailure = $diagnosticSummary.thresholdFailure
    $metadata.harnessFailure = $diagnosticSummary.harnessFailure
    $metadata.collectorFailure = $diagnosticSummary.collectorFailure
    $metadata.k6AggregateAvailable = $diagnosticSummary.k6AggregateAvailable
    $metadata.workloadEvidenceAvailable = ($null -ne $diagnosticSummary.processExit -or $diagnosticSummary.k6AggregateAvailable)
    Save-Metadata $paths.metadataPath $metadata
} finally {
    if ($jfrBackendStarted -and (Wait-JfrArtifact $paths.containerArtifactPath 150)) {
        try {
            Copy-JfrArtifact $paths
            $metadata.jfrArtifactCopied = $true
        } catch {
            $postRunError = 'JFR artifact copy failed; backend was left unchanged for manual evidence recovery.'
        }

        if ($metadata.jfrArtifactCopied) {
            try {
                Write-JfrSummary $paths
                $metadata.jfrSummaryCreated = $true
            } catch {
                $metadata.jfrSummaryError = 'Host JFR summary failed.'
                $postRunError = $metadata.jfrSummaryError
            }

            try {
                Invoke-Cpu2Rollback
                $metadata.rollbackCompleted = $true
            } catch {
                if ($null -eq $postRunError) { $postRunError = 'CPU2.0 rollback failed after JFR artifact copy.' }
            }
        }
    } elseif ($jfrBackendStarted) {
        $postRunError = 'JFR artifact was not available before the bounded recovery wait; backend was left unchanged for manual evidence recovery.'
    }

    $metadata.finishedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
    Save-Metadata $paths.metadataPath $metadata
}

if (-not $metadata.jfrArtifactCopied) { throw $postRunError }
if (-not $metadata.rollbackCompleted) { throw $(if ($postRunError) { $postRunError } else { 'CPU2.0 rollback was not completed after JFR artifact copy.' }) }
if ($postRunError) { throw $postRunError }
Assert-ProfilingDiagnosticSummary $diagnosticSummary
"Phase 9 JFR profiling completed: $($paths.runDir) outcome=$($metadata.diagnosticOutcome) thresholdFailure=$($metadata.thresholdFailure) collectorFailure=$($metadata.collectorFailure)"
