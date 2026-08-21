[CmdletBinding()]
param(
    [string]$K6Command = 'k6',
    [switch]$SkipK6Inspect
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$Scripts = @('api-products.js', 'api-product-detail.js', 'products-page.js')
$CapacityScripts = @('capacity-api-products.js', 'capacity-api-product-detail.js', 'capacity-products-page.js')
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
if ($CapacityShared -notmatch 'constant-arrival-rate' -or $CapacityShared -notmatch 'MEASUREMENT_SECONDS = 120' -or $CapacityShared -notmatch 'droppedIterationsPerSecond' -or $CapacityShared -notmatch 'rate==0') { throw 'Capacity arrival-rate, measurement-window, or fail-closed contract is missing.' }

$SharedScript = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'lib\baseline.js') -Raw
if ($SharedScript -notmatch 'BASE_URL must be an http loopback origin' -or $SharedScript -notmatch 'baseline_measurement_latency' -or $SharedScript -notmatch 'baseline_expected_status_error_rate' -or $SharedScript -notmatch 'MEASUREMENT_SECONDS = 120' -or $SharedScript -notmatch 'count / MEASUREMENT_SECONDS') {
    throw 'Local-only target guard or measurement-window aggregate metrics are missing.'
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
if ($PercentileExpr -notmatch 'histogram_quantile\(0\.95' -or $PercentileExpr -notmatch 'histogram_quantile\(0\.99' -or $PercentileExpr -notmatch '\$__rate_interval') {
    throw 'HTTP latency percentile panel must include p95/p99 and use Grafana rate interval.'
}
$ErrorExpr = $HttpError[0].targets.expr -join "`n"
if ($ErrorExpr -notmatch 'http_server_requests_seconds_count' -or $ErrorExpr -notmatch '\$__rate_interval') { throw 'HTTP error ratio panel must use HTTP request totals and Grafana rate interval.' }

'k6 baseline harness and local observability dashboard validation passed.'
