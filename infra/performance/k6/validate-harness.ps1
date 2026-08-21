[CmdletBinding()]
param(
    [string]$K6Command = 'k6',
    [switch]$SkipK6Inspect
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$Scripts = @('api-products.js', 'api-product-detail.js', 'products-page.js')
$CapacityScripts = @('capacity-api-products.js', 'capacity-api-product-detail.js', 'capacity-products-page.js')
$ProductionCapacityScript = Join-Path $PSScriptRoot 'production-capacity-api-products.js'
$ProductionCapacityShared = Join-Path $PSScriptRoot 'lib\production-capacity.js'
$DashboardPath = Join-Path $Root '..\local-integration\observability\grafana\dashboards\pawcycle-observability.json'

foreach ($Script in $Scripts) {
    $ScriptPath = Join-Path $PSScriptRoot $Script
    if (-not (Test-Path -LiteralPath $ScriptPath)) { throw "Missing k6 cohort script: $Script" }
    $Content = Get-Content -LiteralPath $ScriptPath -Raw
    if ($Content -notmatch 'export function warmup' -or $Content -notmatch 'export function measure' -or $Content -notmatch 'handleSummary') {
        throw "Cohort script does not expose the warm-up, measurement, and safe summary contract: $Script"
    }
    if (-not $SkipK6Inspect) {
        & $K6Command inspect $ScriptPath | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "k6 inspect failed: $Script" }
    }
}

foreach ($Script in $CapacityScripts) {
    $ScriptPath = Join-Path $PSScriptRoot $Script
    $Content = Get-Content -LiteralPath $ScriptPath -Raw
    if ($Content -notmatch 'optionsForCapacity' -or $Content -notmatch 'export function warmup' -or $Content -notmatch 'export function measure') { throw "Capacity scenario contract missing: $Script" }
    if (-not $SkipK6Inspect) { & $K6Command inspect $ScriptPath | Out-Null; if ($LASTEXITCODE -ne 0) { throw "k6 inspect failed: $Script" } }
}
$CapacityShared = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'lib\capacity.js') -Raw
$CapacityRedirectCount = ([regex]::Matches($CapacityShared, 'redirects:\s*0')).Count
$CapacityGracefulStopCount = ([regex]::Matches($CapacityShared, 'gracefulStop:\s*"0s"')).Count
if ($CapacityShared -notmatch 'constant-arrival-rate' -or $CapacityShared -notmatch 'MEASUREMENT_SECONDS = 120' -or $CapacityShared -notmatch 'droppedIterationsPerSecond' -or $CapacityShared -notmatch 'rate==0' -or $CapacityRedirectCount -lt 2 -or $CapacityGracefulStopCount -lt 2) {
    throw 'Capacity arrival-rate, measurement-window, redirect, graceful-stop, or fail-closed contract is missing.'
}

foreach ($ProductionScriptPath in @($ProductionCapacityScript, $ProductionCapacityShared, (Join-Path $PSScriptRoot 'run-production-capacity.sh'))) {
    if (-not (Test-Path -LiteralPath $ProductionScriptPath)) { throw "Missing Production capacity artifact: $ProductionScriptPath" }
}
if (-not $SkipK6Inspect) {
    & $K6Command inspect -e 'PRODUCTION_TARGET_URL=https://validator.invalid' -e 'PRODUCTION_TARGET_HOST=validator.invalid' -e 'PRODUCTION_LOAD_ACKNOWLEDGEMENT=YES' -e 'TARGET_RPS=25' $ProductionCapacityScript | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'k6 inspect failed: production-capacity-api-products.js' }
}
$ProductionCapacityContent = Get-Content -LiteralPath $ProductionCapacityShared -Raw
$ProductionRunnerContent = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'run-production-capacity.sh') -Raw
$ProductionRedirectCount = ([regex]::Matches($ProductionCapacityContent, 'redirects:\s*0')).Count
if ($ProductionCapacityContent -notmatch 'SUPPORTED_RATES = \[25, 50, 100, 150, 200, 250\]' -or $ProductionCapacityContent -notmatch 'PRODUCTION_TARGET_URL' -or $ProductionCapacityContent -notmatch 'PRODUCTION_TARGET_HOST' -or $ProductionCapacityContent -notmatch 'PRODUCTION_LOAD_ACKNOWLEDGEMENT' -or $ProductionCapacityContent -notmatch 'rate==0' -or $ProductionCapacityContent -notmatch 'dropped_iterations' -or $ProductionCapacityContent -notmatch 'discardResponseBodies: true' -or $ProductionCapacityContent -notmatch 'responseType: "none"' -or $ProductionRedirectCount -ne 1 -or $ProductionCapacityContent -notmatch '\$\{productionTargetUrl\(\)\}/api/products') {
    throw 'Production capacity target guard, read-only endpoint, redirect refusal, rate envelope, or fail-closed contract is missing.'
}
if ($ProductionRunnerContent -notmatch 'for target_rps in 25 50 100 150 200 250' -or $ProductionRunnerContent -notmatch 'set -euo pipefail' -or $ProductionRunnerContent -notmatch '-e "PRODUCTION_TARGET_URL=' -or $ProductionRunnerContent -notmatch '--confirm-target-host' -or $ProductionRunnerContent -notmatch '--acknowledge-production-load') {
    throw 'Production capacity runner acknowledgement, target confirmation, step order, or stop boundary is missing.'
}

$SharedScript = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'lib\baseline.js') -Raw
$SharedRedirectCount = ([regex]::Matches($SharedScript, 'redirects:\s*0')).Count
$SharedGracefulStopCount = ([regex]::Matches($SharedScript, 'gracefulStop:\s*"0s"')).Count
if ($SharedScript -notmatch 'BASE_URL must be an http loopback origin' -or $SharedScript -notmatch 'baseline_measurement_latency' -or $SharedScript -notmatch 'baseline_expected_status_error_rate' -or $SharedScript -notmatch 'MEASUREMENT_SECONDS = 120' -or $SharedScript -notmatch 'count / MEASUREMENT_SECONDS' -or $SharedRedirectCount -lt 2 -or $SharedGracefulStopCount -lt 2) {
    throw 'Local-only target guard, redirect refusal, graceful-stop boundary, or measurement-window aggregate metrics are missing.'
}

$Dashboard = Get-Content -LiteralPath $DashboardPath -Raw | ConvertFrom-Json
$HttpAverage = @($Dashboard.panels | Where-Object { $_.title -eq 'HTTP latency' })
$HttpPercentiles = @($Dashboard.panels | Where-Object { $_.title -eq 'HTTP latency percentiles' })
$HttpError = @($Dashboard.panels | Where-Object { $_.title -eq 'HTTP request error ratio' })
$HttpRequests = @($Dashboard.panels | Where-Object { $_.title -eq 'HTTP requests and errors' })
if ($HttpAverage.Count -ne 1 -or $HttpPercentiles.Count -ne 1 -or $HttpError.Count -ne 1 -or $HttpRequests.Count -ne 1) { throw 'Required HTTP request, latency, percentile, or error-ratio panels are missing.' }
if (($HttpRequests[0].targets.expr -join "`n") -notmatch '\$__rate_interval') { throw 'HTTP request rate panel must use Grafana rate interval.' }
if (($HttpAverage[0].targets.expr -join "`n") -notmatch '\$__rate_interval') { throw 'HTTP average latency panel must use Grafana rate interval.' }
$PercentileExpr = $HttpPercentiles[0].targets.expr -join "`n"
if ($PercentileExpr -notmatch 'histogram_quantile\(0\.95' -or $PercentileExpr -notmatch 'histogram_quantile\(0\.99' -or $PercentileExpr -notmatch 'http_server_requests_seconds_bucket' -or $PercentileExpr -notmatch '\$__rate_interval') {
    throw 'HTTP latency percentile panel must use the request histogram buckets, include p95/p99, and use Grafana rate interval.'
}
$ErrorExpr = $HttpError[0].targets.expr -join "`n"
if ($ErrorExpr -notmatch 'http_server_requests_seconds_count\{status=~"4\.\.\|5\.\."\}\[\$__rate_interval\]' -or $ErrorExpr -notmatch 'http_server_requests_seconds_count\[\$__rate_interval\]' -or $ErrorExpr -notmatch '\)\s*/\s*clamp_min\(' -or $ErrorExpr -notmatch '\$__rate_interval') {
    throw 'HTTP error ratio panel must divide filtered 4xx/5xx request rate by the unfiltered total request rate using Grafana rate interval.'
}

'k6 baseline harness and local observability dashboard validation passed.'
