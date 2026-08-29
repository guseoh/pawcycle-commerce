[CmdletBinding()]
param(
    [string] $EnvFile
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

$existingContainers = @(docker ps -a --filter "label=com.docker.compose.project=$project" --format '{{.Names}}')
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to inspect existing Docker containers'
}
if ($existingContainers | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) {
    throw "Compose project already exists: $project"
}

$existingVolume = docker volume inspect $volume --format '{{.Name}}' 2>$null
$volumeExit = $LASTEXITCODE
if ($volumeExit -eq 0 -and -not [string]::IsNullOrWhiteSpace($existingVolume)) {
    throw "Disposable QA volume already exists: $volume"
}

docker compose @composeArgs config --quiet
if ($LASTEXITCODE -ne 0) {
    throw 'Final Product QA Compose config failed'
}

docker compose @composeArgs build backend frontend
if ($LASTEXITCODE -ne 0) {
    throw 'Final Product QA image build failed'
}

docker compose @composeArgs up --detach --wait --wait-timeout 240
if ($LASTEXITCODE -ne 0) {
    throw 'Final Product QA stack readiness failed'
}

$backendContainer = docker compose @composeArgs ps -q backend
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($backendContainer)) {
    throw 'Backend container lookup failed'
}

$projectLabel = docker inspect $backendContainer --format '{{index .Config.Labels "com.docker.compose.project"}}'

function Get-ContainerEnvValue {
    param(
        [string] $Container,
        [string] $Key
    )

    $template = '{{range .Config.Env}}{{ $parts := split . "=" }}{{if eq (index $parts 0) "' + $Key + '"}}{{println (index $parts 1)}}{{end}}{{end}}'
    return (docker inspect $Container --format $template 2>$null).Trim()
}

function Test-ContainerEnvKey {
    param(
        [string] $Container,
        [string] $Key
    )

    $template = '{{range .Config.Env}}{{ $parts := split . "=" }}{{if eq (index $parts 0) "' + $Key + '"}}present{{end}}{{end}}'
    return -not [string]::IsNullOrWhiteSpace((docker inspect $Container --format $template 2>$null).Trim())
}

$expectedEnv = @{
    'SPRING_PROFILES_ACTIVE' = 'local-integration'
    'PAWCYCLE_LOCAL_CUSTOMER_CATALOG_V3_ENABLED' = 'true'
    'PAWCYCLE_LOCAL_QA_BOOTSTRAP_ENABLED' = 'true'
    'PAWCYCLE_LOCAL_QA_BOOTSTRAP_RESET_SUBSCRIPTIONS' = 'false'
    'PAWCYCLE_SUBSCRIPTION_DELIVERY_REMINDER_ENABLED' = 'true'
}

if ($projectLabel -ne $project) {
    throw 'Final Product QA Compose project label assertion failed'
}
foreach ($key in $expectedEnv.Keys) {
    if ((Get-ContainerEnvValue -Container $backendContainer -Key $key) -ne $expectedEnv[$key]) {
        throw "Final Product QA environment assertion failed for $key"
    }
}

$credentialKeys = @(
    'PAWCYCLE_LOCAL_QA_BOOTSTRAP_EMAIL',
    'PAWCYCLE_LOCAL_QA_BOOTSTRAP_PASSWORD'
)
foreach ($key in $credentialKeys) {
    if (-not (Test-ContainerEnvKey -Container $backendContainer -Key $key)) {
        throw "Final Product QA credential presence assertion failed for $key"
    }
}

$volumeOwner = docker volume inspect $volume --format '{{index .Labels "com.docker.compose.project"}}'
if ($LASTEXITCODE -ne 0 -or $volumeOwner -ne $project) {
    throw 'Final Product QA volume ownership assertion failed'
}

Write-Output "Final Product QA stack ready: project=$project http=http://localhost:8083 volume=$volume"
Write-Output 'Non-secret environment, credential presence, and disposable volume assertions passed.'
docker compose @composeArgs ps
