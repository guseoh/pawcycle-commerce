[CmdletBinding()]
param(
    [string] $EnvFile,
    [switch] $RemoveVolume
)

$ErrorActionPreference = 'Stop'
$project = 'pawcycle-mvp4-final-qa'
$volume = 'pawcycle-mvp4-final-qa-mysql-data'

if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $EnvFile = Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) 'infra/local-integration/.env.local'
}
if (-not (Test-Path -LiteralPath $EnvFile -PathType Leaf)) {
    throw "Local env input is required but was not found: $EnvFile"
}

$baseCompose = (Resolve-Path (Join-Path $PSScriptRoot '../../infra/local-integration/compose.yaml')).Path
$qaCompose = (Resolve-Path (Join-Path $PSScriptRoot 'compose.final-product-qa.yaml')).Path
$composeArgs = @('-p', $project, '--env-file', $EnvFile, '-f', $baseCompose, '-f', $qaCompose)

docker compose @composeArgs down
if ($LASTEXITCODE -ne 0) {
    throw 'Final Product QA Compose cleanup failed'
}

if ($RemoveVolume) {
    $owner = docker volume inspect $volume --format '{{index .Labels "com.docker.compose.project"}}' 2>$null
    if ($LASTEXITCODE -ne 0 -or $owner -ne $project) {
        throw 'Refusing to remove a missing or foreign QA volume'
    }
    docker volume rm $volume
    if ($LASTEXITCODE -ne 0) {
        throw 'Final Product QA volume cleanup failed'
    }
    Write-Output "Removed disposable QA volume: $volume"
} else {
    Write-Output "Preserved disposable QA volume: $volume"
}
