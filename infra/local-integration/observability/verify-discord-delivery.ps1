# Verifies real Discord delivery of the local Alertmanager routes (OBS-002, stage 2).
# Starts a disposable Alertmanager with this checkout's config, sends one critical and one warning
# test alert, then resolves them, and prints Alertmanager's own delivery counters.
# Local only. Webhook URLs are read without echo, stored only under -SecretDir with a user-only ACL,
# and never printed. Works in Windows PowerShell 5.1 and PowerShell 7.
param(
    [string]$SecretDir = (Join-Path $HOME '.pawcycle\secrets'),
    # Use previously saved webhook files instead of prompting.
    [switch]$ReuseSavedWebhook,
    [int]$HostPort = 19095,
    [int]$WaitSeconds = 40
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$Utf8 = [Text.UTF8Encoding]::new($false)
$Observability = $PSScriptRoot
$ContainerName = 'pawcycle-discord-check'
$CriticalFile = Join-Path $SecretDir 'discord-critical.url'
$WarningFile = Join-Path $SecretDir 'discord-warning.url'

function Save-WebhookFile([string]$Path, [string]$Label) {
    $secure = Read-Host "$Label Discord webhook URL (입력은 화면에 보이지 않음)" -AsSecureString
    $plain = [Net.NetworkCredential]::new('', $secure).Password
    if ($plain -notmatch '^https://(discord\.com|discordapp\.com)/api/webhooks/') {
        throw "$Label URL is not a Discord webhook URL."
    }
    [IO.File]::WriteAllText($Path, $plain, $Utf8)
    Remove-Variable plain
    icacls $Path /inheritance:r /grant:r "$($env:USERNAME):(R,W)" | Out-Null
}

New-Item -ItemType Directory -Force $SecretDir | Out-Null
if ($ReuseSavedWebhook) {
    if (-not (Test-Path -LiteralPath $CriticalFile)) { throw "Saved critical webhook file not found: $CriticalFile" }
    if (-not (Test-Path -LiteralPath $WarningFile)) { $WarningFile = $CriticalFile }
} else {
    Save-WebhookFile $CriticalFile 'critical'
    if ((Read-Host 'warning 채널을 따로 쓰나요? (y/N)') -eq 'y') {
        Save-WebhookFile $WarningFile 'warning'
    } else {
        $WarningFile = $CriticalFile
    }
}

$ComposeFile = Join-Path (Split-Path $Observability -Parent) 'compose.observability.yaml'
$ImageMatch = Select-String -LiteralPath $ComposeFile -Pattern 'image: (prom/alertmanager:\S+)' | Select-Object -First 1
if (-not $ImageMatch) { throw 'Alertmanager image is not pinned in compose.observability.yaml.' }
$Image = $ImageMatch.Matches[0].Groups[1].Value
$AlertmanagerDir = Join-Path $Observability 'alertmanager'
$AM = "http://127.0.0.1:$HostPort"

function Send-TestAlert([string]$Severity, [bool]$Resolved) {
    $now = (Get-Date).ToUniversalTime()
    $alert = @{
        labels = @{ alertname = "PawCycleDiscordDeliveryCheck$Severity"; scope = 'local'; severity = $Severity.ToLower() }
        annotations = @{
            summary = "Discord 전달 확인($Severity)"
            description = '로컬 알림 경로의 실제 Discord 전달 확인용 시험 알림이다.'
            impact = '없음(시험 알림)'
            dashboard = 'http://127.0.0.1:3001/'
            runbook = 'https://github.com/guseoh/pawcycle-commerce/blob/main/docs/runbook/OBS-002-local-alert-response.md'
        }
        startsAt = $now.AddMinutes(-1).ToString('o')
    }
    if ($Resolved) { $alert.endsAt = $now.AddSeconds(-1).ToString('o') }
    $json = ConvertTo-Json -InputObject @($alert) -Depth 5
    Invoke-RestMethod -Method Post "$AM/api/v2/alerts" -ContentType 'application/json; charset=utf-8' -Body $Utf8.GetBytes($json) | Out-Null
}

try {
    $ErrorActionPreference = 'Continue'; docker rm -f $ContainerName *> $null; $ErrorActionPreference = 'Stop'
    docker run -d --name $ContainerName -p "127.0.0.1:${HostPort}:9093" `
        -v "${AlertmanagerDir}\alertmanager.yml:/etc/alertmanager/alertmanager.yml:ro" `
        -v "${AlertmanagerDir}\templates:/etc/alertmanager/templates:ro" `
        -v "${CriticalFile}:/run/secrets/discord-webhook:ro" `
        -v "${WarningFile}:/run/secrets/discord-warning-webhook:ro" `
        $Image --config.file=/etc/alertmanager/alertmanager.yml --storage.path=/alertmanager | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Alertmanager container failed to start.' }
    $ready = $false
    foreach ($i in 1..30) {
        try { Invoke-RestMethod "$AM/-/ready" -TimeoutSec 2 | Out-Null; $ready = $true; break } catch { Start-Sleep 1 }
    }
    if (-not $ready) { throw 'Alertmanager did not become ready.' }

    Send-TestAlert 'Critical' $false
    Send-TestAlert 'Warning' $false
    Write-Host "firing 2건 전송. Discord에서 [CRITICAL]·[WARNING]을 확인하세요. ${WaitSeconds}초 대기..."
    Start-Sleep $WaitSeconds
    Send-TestAlert 'Critical' $true
    Send-TestAlert 'Warning' $true
    Write-Host "resolved 2건 전송. Discord에서 [RESOLVED] 2건을 확인하세요. ${WaitSeconds}초 대기..."
    Start-Sleep $WaitSeconds

    $metrics = (Invoke-WebRequest "$AM/metrics" -UseBasicParsing).Content -split "`n"
    $metrics | Where-Object { $_ -match '^alertmanager_notifications_(total|failed_total)\{.*integration="discord"' }
    $ErrorActionPreference = 'Continue'
    $logs = docker logs $ContainerName 2>&1 | ForEach-Object { "$_" }
    $ErrorActionPreference = 'Stop'
    $logs | Where-Object { $_ -match 'level=(error|warn)' } | ForEach-Object { $_ -replace 'https://\S+', '<redacted>' }
    Write-Host '성공 기준: alertmanager_notifications_total{integration="discord"} 4, failed_total 모두 0, Discord에 메시지 4건.'
} finally {
    $ErrorActionPreference = 'Continue'
    docker rm -f $ContainerName *> $null
    Write-Host "webhook 파일은 $SecretDir 에 남아 있다. 지우려면: Remove-Item -Recurse -Force '$SecretDir'"
}
