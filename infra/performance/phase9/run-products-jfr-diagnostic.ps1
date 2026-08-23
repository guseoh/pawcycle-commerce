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
$repoRootPrefix = $RepoRoot.TrimEnd([char[]]@([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)) + [IO.Path]::DirectorySeparatorChar
$BackendContainer = 'pawcycle-local-integration-backend-1'
$DiagnosticScript = Join-Path $PSScriptRoot 'run-products-diagnostic.ps1'
$JfrDurationSeconds = 300

function Assert-ExternalResultsDir([string]$Path) {
    if ($Path.Equals($RepoRoot, [StringComparison]::OrdinalIgnoreCase) -or $Path.StartsWith($repoRootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'ResultsDir must be outside the repository.'
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
    $Metadata | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $Path -Encoding utf8
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
    & $JfrCommand summary $Paths.artifactPath | Set-Content -LiteralPath $Paths.summaryPath -Encoding utf8
    if ($LASTEXITCODE -ne 0) { throw 'Host JFR summary failed.' }
}

function Invoke-Cpu2Rollback {
    $composeArgs = @('--env-file', '.env.local', '-f', 'compose.yaml', '-f', 'compose.prometheus.yaml', '-f', 'compose.phase9-envelope.yaml', '-f', 'compose.phase9-tomcat128.yaml', '-f', 'compose.phase9-cpu15.yaml', '-f', 'compose.phase9-memory1g.yaml', '-f', 'compose.phase9-cpu20.yaml')
    Push-Location (Join-Path $RepoRoot 'infra\local-integration')
    try {
        docker compose @composeArgs up -d --no-deps --force-recreate --wait --wait-timeout 120 backend
        if ($LASTEXITCODE -ne 0) { throw 'CPU2.0 rollback backend did not become healthy.' }
    } finally {
        Pop-Location
    }
}

Assert-ExternalResultsDir $ResultsDir
$modes = @($ValidateOnly, $ValidateRuntimeCapability, $RunProfiling) | Where-Object { $_ }
if ($modes.Count -ne 1) { throw 'Specify exactly one mode: ValidateOnly, ValidateRuntimeCapability, or RunProfiling.' }

if ($ValidateOnly) {
    $valid = New-JfrRunPaths ([IO.Path]::GetTempPath()) '11111111-1111-1111-1111-111111111111'
    if ((Get-JfrStartupOption $valid.containerArtifactPath) -notmatch 'StartFlightRecording=.*duration=300s' -or (Get-JfrStartupOption $valid.containerArtifactPath) -match 'jcmd') {
        throw 'JFR lifecycle command generation validation failed.'
    }
    $rejectedPath = $false
    try { [void](Get-JfrStartupOption '/unsafe.jfr') } catch { $rejectedPath = $true }
    if (-not $rejectedPath) { throw 'Invalid JFR container artifact path unexpectedly passed.' }
    $repositoryPathRejected = $false
    try { Assert-ExternalResultsDir $RepoRoot } catch { $repositoryPathRejected = $true }
    if (-not $repositoryPathRejected) { throw 'Repository results directory unexpectedly passed.' }
    $overwriteRoot = Join-Path ([IO.Path]::GetTempPath()) ("pawcycle-phase9-jfr-overwrite-$([guid]::NewGuid())")
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
    'Phase 9 JFR lifecycle validation passed without starting k6.'
    exit 0
}

if ($ValidateRuntimeCapability) {
    Assert-Cpu2BackendState
    $tools = Get-JfrRuntimeTools
    Assert-JfrRuntimeCapability $tools
    & $JfrCommand --version | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Host JFR command is unavailable.' }
    "Phase 9 JFR runtime capability passed (jcmd available: $($tools.jcmd))."
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
    jfrArtifactCopied = $false
    diagnosticStarted = $false
    diagnosticExitCode = $null
    diagnosticOutcome = $null
    rollbackCompleted = $false
}
Save-Metadata $paths.metadataPath $metadata
$jfrBackendStarted = $false

try {
    Assert-Cpu2BackendState
    $tools = Get-JfrRuntimeTools
    Assert-JfrRuntimeCapability $tools
    & $JfrCommand --version | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Host JFR command is unavailable.' }

    $env:PAWCYCLE_PHASE9_JFR_RECORDING_PATH = $paths.containerArtifactPath
    Push-Location (Join-Path $RepoRoot 'infra\local-integration')
    try {
        $composeArgs = @('--env-file', '.env.local', '-f', 'compose.yaml', '-f', 'compose.prometheus.yaml', '-f', 'compose.phase9-envelope.yaml', '-f', 'compose.phase9-tomcat128.yaml', '-f', 'compose.phase9-cpu15.yaml', '-f', 'compose.phase9-memory1g.yaml', '-f', 'compose.phase9-cpu20.yaml', '-f', 'compose.phase9-jfr.yaml')
        docker compose @composeArgs up -d --no-deps --force-recreate --wait --wait-timeout 120 backend
        if ($LASTEXITCODE -ne 0) { throw 'JFR backend start did not become healthy.' }
        $jfrBackendStarted = $true
    } finally {
        Pop-Location
        Remove-Item Env:PAWCYCLE_PHASE9_JFR_RECORDING_PATH -ErrorAction SilentlyContinue
    }

    Assert-Cpu2BackendState
    $metadata.diagnosticStarted = $true
    Save-Metadata $paths.metadataPath $metadata
    & $DiagnosticScript -ResultsDir (Join-Path $paths.runDir 'diagnostic') -ExpectedTomcatThreadsMax 128 -ExpectedHikariPoolMax 10
    $summaries = @(Get-ChildItem -LiteralPath (Join-Path $paths.runDir 'diagnostic') -Filter 'diagnostic-summary.json' -Recurse -File)
    if ($summaries.Count -ne 1) { throw 'Phase 9 diagnostic summary was not produced exactly once.' }
    $diagnosticSummary = Get-Content -Raw -LiteralPath $summaries[0].FullName | ConvertFrom-Json
    $metadata.diagnosticExitCode = $diagnosticSummary.processExit
    $metadata.diagnosticOutcome = $diagnosticSummary.outcome
} finally {
    if ($jfrBackendStarted -and (Wait-JfrArtifact $paths.containerArtifactPath 150)) {
        Copy-JfrArtifact $paths
        $metadata.jfrArtifactCopied = $true
        Invoke-Cpu2Rollback
        $metadata.rollbackCompleted = $true
    }
    $metadata.finishedAtUtc = (Get-Date).ToUniversalTime().ToString('o')
    Save-Metadata $paths.metadataPath $metadata
}

if (-not $metadata.jfrArtifactCopied) { throw 'JFR artifact was not available before the bounded recovery wait; backend was left unchanged for manual evidence recovery.' }
if ($metadata.diagnosticOutcome -ne 'success') { throw "Phase 9 diagnostic outcome was $($metadata.diagnosticOutcome); first-result evidence was preserved." }
"Phase 9 JFR profiling completed: $($paths.runDir)"
