param(
    [ValidateSet("Full", "Preserved", "Empty")]
    [string]$Scenario = "Full",
    [string]$BaseUri = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$fixtureProductPrefix = "[QA FOUNDATION-004] "
$fixtureSkuName = "[QA FOUNDATION-004] 2kg"

function Assert-Condition {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

if ([string]::IsNullOrWhiteSpace($env:PAWCYCLE_LOCAL_QA_BOOTSTRAP_EMAIL) -or
    [string]::IsNullOrWhiteSpace($env:PAWCYCLE_LOCAL_QA_BOOTSTRAP_PASSWORD)) {
    throw "QA bootstrap credential environment variables are required."
}

$base = $BaseUri.TrimEnd("/")
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$loggedIn = $false
$csrfToken = $null

try {
    $frontend = Invoke-WebRequest -Uri "$base/products" -UseBasicParsing -TimeoutSec 30
    Assert-Condition ($frontend.StatusCode -eq 200) "Frontend readiness check failed."

    $products = Invoke-RestMethod -Uri "$base/api/products" -Method Get -WebSession $session -TimeoutSec 30
    $fixtureProducts = @($products.products | Where-Object {
        $_.hasSubscribableSku -and $_.name.StartsWith($fixtureProductPrefix)
    })
    Assert-Condition ($fixtureProducts.Count -eq 1) "Expected exactly one FOUNDATION-004 product fixture."

    $product = Invoke-RestMethod -Uri "$base/api/products/$($fixtureProducts[0].productId)" -Method Get -WebSession $session -TimeoutSec 30
    $fixtureSkus = @($product.skus | Where-Object { $_.skuName -eq $fixtureSkuName -and $_.subscribable })
    Assert-Condition ($fixtureSkus.Count -eq 1) "Expected exactly one subscribable FOUNDATION-004 SKU fixture."
    Assert-Condition (@($fixtureSkus[0].availableDeliveryCycles).Count -gt 0) "Fixture SKU has no delivery cycle."

    $csrf = Invoke-RestMethod -Uri "$base/api/auth/csrf" -Method Get -WebSession $session -TimeoutSec 30
    $csrfToken = $csrf.token
    Assert-Condition (-not [string]::IsNullOrWhiteSpace($csrfToken)) "CSRF token acquisition failed."

    $loginBody = @{
        email = $env:PAWCYCLE_LOCAL_QA_BOOTSTRAP_EMAIL
        password = $env:PAWCYCLE_LOCAL_QA_BOOTSTRAP_PASSWORD
    } | ConvertTo-Json -Compress
    $login = Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post -WebSession $session -Headers @{ "X-CSRF-TOKEN" = $csrfToken } -ContentType "application/json" -Body $loginBody -TimeoutSec 30
    $loggedIn = $true

    $csrf = Invoke-RestMethod -Uri "$base/api/auth/csrf" -Method Get -WebSession $session -TimeoutSec 30
    $csrfToken = $csrf.token
    $me = Invoke-RestMethod -Uri "$base/api/auth/me" -Method Get -WebSession $session -TimeoutSec 30
    Assert-Condition ($me.memberId -eq $login.memberId) "Current-member response does not match the login response."

    if ($Scenario -eq "Full") {
        $createBody = @{
            skuId = $fixtureSkus[0].skuId
            quantity = 1
            deliveryCycleWeeks = $fixtureSkus[0].availableDeliveryCycles[0]
        } | ConvertTo-Json -Compress
        $created = Invoke-RestMethod -Uri "$base/api/subscriptions" -Method Post -WebSession $session -Headers @{ "X-CSRF-TOKEN" = $csrfToken } -ContentType "application/json" -Body $createBody -TimeoutSec 30

        $subscriptions = Invoke-RestMethod -Uri "$base/api/subscriptions" -Method Get -WebSession $session -TimeoutSec 30
        $createdInList = @($subscriptions.subscriptions | Where-Object { $_.subscriptionId -eq $created.subscriptionId })
        Assert-Condition ($createdInList.Count -eq 1) "Created subscription was not found in the list."

        $detail = Invoke-RestMethod -Uri "$base/api/subscriptions/$($created.subscriptionId)" -Method Get -WebSession $session -TimeoutSec 30
        Assert-Condition ($detail.subscriptionId -eq $created.subscriptionId) "Subscription detail does not match the created subscription."
    }
    elseif ($Scenario -eq "Preserved") {
        $subscriptions = Invoke-RestMethod -Uri "$base/api/subscriptions" -Method Get -WebSession $session -TimeoutSec 30
        Assert-Condition (@($subscriptions.subscriptions).Count -gt 0) "Expected a subscription to survive the restart."
        $detail = Invoke-RestMethod -Uri "$base/api/subscriptions/$($subscriptions.subscriptions[0].subscriptionId)" -Method Get -WebSession $session -TimeoutSec 30
        Assert-Condition ($null -ne $detail.subscriptionId) "Preserved subscription detail could not be read."
    }
    else {
        $subscriptions = Invoke-RestMethod -Uri "$base/api/subscriptions" -Method Get -WebSession $session -TimeoutSec 30
        Assert-Condition (@($subscriptions.subscriptions).Count -eq 0) "Expected an empty QA subscription list after reset."
    }

    Invoke-RestMethod -Uri "$base/api/auth/logout" -Method Post -WebSession $session -Headers @{ "X-CSRF-TOKEN" = $csrfToken } -TimeoutSec 30 | Out-Null
    $loggedIn = $false
    Write-Output "FOUNDATION-004 smoke scenario passed: $Scenario"
}
finally {
    if ($loggedIn -and -not [string]::IsNullOrWhiteSpace($csrfToken)) {
        try {
            Invoke-RestMethod -Uri "$base/api/auth/logout" -Method Post -WebSession $session -Headers @{ "X-CSRF-TOKEN" = $csrfToken } -TimeoutSec 10 | Out-Null
        }
        catch {
            Write-Warning "Smoke cleanup logout failed; restart the local stack before retrying."
        }
    }
}
