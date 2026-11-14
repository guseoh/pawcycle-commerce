# Toss Test verification helper for docs/runbook/PAYMENT-TOSS-TEST-001.
#
#   ./verify-toss-test.ps1 -Preflight
#   ./verify-toss-test.ps1 -OrderNumber O-... -Expect Paid
#
# Preflight checks only key *prefixes* and never prints key values. State checks read the
# local-integration MySQL container; they never call Toss and never write to the database.
param(
    [switch]$Preflight,
    [string]$OrderNumber,
    [ValidateSet("Paid", "Failed", "Expired", "Pending", "Processing")]
    [string]$Expect,
    [string]$Container
)

$ErrorActionPreference = "Stop"

function Test-TossTestKeys {
    param([string]$SecretKey, [string]$ClientKey)
    $problems = @()
    if ([string]::IsNullOrWhiteSpace($SecretKey)) { $problems += "PAWCYCLE_TOSS_TEST_SECRET_KEY is not set" }
    elseif (-not $SecretKey.StartsWith("test_sk_")) { $problems += "PAWCYCLE_TOSS_TEST_SECRET_KEY is not a test_sk_ key" }
    if ([string]::IsNullOrWhiteSpace($ClientKey)) { $problems += "PAWCYCLE_TOSS_TEST_CLIENT_KEY is not set" }
    elseif (-not $ClientKey.StartsWith("test_ck_")) { $problems += "PAWCYCLE_TOSS_TEST_CLIENT_KEY is not a test_ck_ key" }
    return $problems
}

function Get-ExpectedState {
    param([string]$Name)
    switch ($Name) {
        "Paid"       { @{ Payment = "SUCCEEDED";  Order = "PAID";            Movements = "DEDUCT,RESERVE";  Deliveries = 1 } }
        "Failed"     { @{ Payment = "FAILED";     Order = "PAYMENT_FAILED";  Movements = "RELEASE,RESERVE"; Deliveries = 0 } }
        "Expired"    { @{ Payment = "FAILED";     Order = "EXPIRED";         Movements = "RELEASE,RESERVE"; Deliveries = 0 } }
        "Pending"    { @{ Payment = "READY";      Order = "PAYMENT_PENDING"; Movements = "RESERVE";         Deliveries = 0 } }
        "Processing" { @{ Payment = "PROCESSING"; Order = "PAYMENT_PENDING"; Movements = "RESERVE";         Deliveries = 0 } }
    }
}

function Invoke-LocalSql {
    param([string]$TargetContainer, [string]$Sql)
    foreach ($name in "MYSQL_DATABASE", "MYSQL_USER", "MYSQL_PASSWORD") {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
            throw "$name is required (load infra/local-integration/.env.local into this shell)."
        }
    }
    $output = docker exec -e "MYSQL_PWD=$env:MYSQL_PASSWORD" $TargetContainer `
        mysql --batch --skip-column-names --user=$env:MYSQL_USER $env:MYSQL_DATABASE --execute=$Sql
    if ($LASTEXITCODE -ne 0) { throw "MySQL query failed." }
    return $output
}

if ($Preflight) {
    $problems = Test-TossTestKeys $env:PAWCYCLE_TOSS_TEST_SECRET_KEY $env:PAWCYCLE_TOSS_TEST_CLIENT_KEY
    if ($problems.Count -gt 0) {
        $problems | ForEach-Object { Write-Output "PREFLIGHT FAIL: $_" }
        exit 2
    }
    Write-Output "PREFLIGHT OK: both Toss keys are test keys (values not shown)."
    exit 0
}

if ([string]::IsNullOrWhiteSpace($OrderNumber) -or [string]::IsNullOrWhiteSpace($Expect)) {
    throw "Use -Preflight, or both -OrderNumber and -Expect."
}
if ($OrderNumber -notmatch '^[A-Za-z0-9-]{1,80}$') { throw "OrderNumber has unexpected characters." }

if ([string]::IsNullOrWhiteSpace($Container)) {
    $Container = (docker compose -f (Join-Path $PSScriptRoot "..\compose.yaml") ps -q mysql)
    if ([string]::IsNullOrWhiteSpace($Container)) { throw "local-integration mysql container is not running." }
}

$row = Invoke-LocalSql $Container @"
SELECT p.id, p.status, p.provider_status, o.status,
  COALESCE((SELECT GROUP_CONCAT(DISTINCT m.type ORDER BY m.type) FROM inventory_movements m WHERE m.payment_id=p.id), ''),
  (SELECT COUNT(*) FROM deliveries d WHERE d.order_id=o.id),
  COALESCE((SELECT GROUP_CONCAT(c.status) FROM member_coupons c WHERE c.reserved_order_id=o.id), '-')
FROM orders o JOIN payments p ON p.order_id=o.id
WHERE o.order_number='$OrderNumber' AND p.type='NORMAL'
ORDER BY p.attempt_no DESC LIMIT 1;
"@
if ([string]::IsNullOrWhiteSpace($row)) { throw "No NORMAL payment found for the order." }
$cols = ($row -split "`t")
$actual = [ordered]@{
    PaymentId = $cols[0]; Payment = $cols[1]; ProviderStatus = $cols[2]; Order = $cols[3]
    Movements = $cols[4]; Deliveries = [int]$cols[5]; Coupon = $cols[6]
}
$expected = Get-ExpectedState $Expect

$failures = @()
foreach ($key in "Payment", "Order", "Movements", "Deliveries") {
    if ("$($actual[$key])" -ne "$($expected[$key])") {
        $failures += "$key expected=$($expected[$key]) actual=$($actual[$key])"
    }
}

$actual.GetEnumerator() | ForEach-Object { Write-Output ("{0}={1}" -f $_.Key, $_.Value) }
if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Output "STATE FAIL: $_" }
    exit 1
}
Write-Output "STATE OK: order matches '$Expect'."
exit 0
