[CmdletBinding()]
param(
    [ValidateRange(1, 1000)]
    [int]$Rows = 100,

    [ValidateRange(0, 20)]
    [int]$Warmup = 2,

    [ValidateRange(1, 20)]
    [int]$Iterations = 7,

    [ValidateRange(1, 10)]
    [int]$LockIterations = 5,

    [ValidateRange(1, 1000)]
    [int]$LockRows = 300,

    [ValidatePattern('^[A-Za-z0-9._-]+$')]
    [string]$RunId = '',

    [string]$OutputPath = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$harnessSource = Join-Path $PSScriptRoot 'fixtures\OpsPerf002MigrationMeasurementTests.java'
$harnessTarget = Join-Path $repoRoot 'backend\src\test\java\com\pawcycle\backend\subscription\v2\OpsPerf002MigrationMeasurementTests.java'
$mysqlImage = 'mysql:8.4.10'
$javaImage = 'eclipse-temurin:25.0.3_9-jdk-noble'
$primaryError = $null
$harnessCopied = $false
$containerStarted = $false
$networkCreated = $false

function Invoke-NativeCommand {
    param(
        [Parameter(Mandatory = $true)]
        [scriptblock]$Command,

        [Parameter(Mandatory = $true)]
        [string]$FailureMessage
    )

    $output = & $Command
    $nativeExit = $LASTEXITCODE
    if ($nativeExit -ne 0) {
        throw "$FailureMessage (exit code $nativeExit)."
    }
    return $output
}

function Add-CleanupFailure {
    param([System.Management.Automation.ErrorRecord]$CleanupError)
    if ($null -eq $script:primaryError) {
        $script:primaryError = $CleanupError
    } else {
        Write-Warning 'Cleanup also failed; preserving the original failure.'
    }
}

try {
    if (Test-Path -LiteralPath $harnessTarget) {
        throw 'Temporary Backend measurement test path already exists; refusing to overwrite it.'
    }
    if (-not (Test-Path -LiteralPath $harnessSource)) {
        throw 'Tracked OPS-PERF-002 measurement harness is missing.'
    }

    $status = Invoke-NativeCommand { git -C $repoRoot status --porcelain } 'git status failed'
    if (@($status).Count -ne 0) {
        throw 'Measurement requires a clean worktree so source_commit identifies the exact source.'
    }
    $sourceCommit = (Invoke-NativeCommand { git -C $repoRoot rev-parse HEAD } 'git rev-parse failed' | Select-Object -Last 1).Trim()

    if ([string]::IsNullOrWhiteSpace($RunId)) {
        $RunId = 'OPS-PERF-002-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
    }
    if ([string]::IsNullOrWhiteSpace($OutputPath)) {
        $OutputPath = "backend/build/ops-perf-002/$RunId.json"
    }
    if ([IO.Path]::IsPathRooted($OutputPath)) {
        throw 'OutputPath must be repository-relative.'
    }
    $outputHostPath = [IO.Path]::GetFullPath((Join-Path $repoRoot $OutputPath))
    $repoPrefix = $repoRoot.TrimEnd('\') + '\'
    if (-not $outputHostPath.StartsWith($repoPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'OutputPath must stay inside the repository.'
    }
    $outputDirectory = Split-Path -Parent $outputHostPath
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    $containerOutputPath = '/repo/' + $OutputPath.Replace('\', '/').TrimStart('/')

    $scriptSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $PSCommandPath).Hash.ToLowerInvariant()
    $harnessSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $harnessSource).Hash.ToLowerInvariant()
    $suffix = ([guid]::NewGuid().ToString('N')).Substring(0, 8)
    $containerName = "ops-perf-002-mysql-$suffix"
    $networkName = "ops-perf-002-network-$suffix"
    $dbName = 'opsperf002'
    $dbUser = 'opsperf002'
    $dbPassword = [guid]::NewGuid().ToString('N')
    $rootPassword = [guid]::NewGuid().ToString('N')

    Invoke-NativeCommand { docker pull $mysqlImage } 'MySQL image pull failed' | Out-Null
    Invoke-NativeCommand { docker pull $javaImage } 'Java image pull failed' | Out-Null
    $mysqlDigest = (Invoke-NativeCommand {
        docker image inspect --format '{{index .RepoDigests 0}}' $mysqlImage
    } 'MySQL image digest inspection failed' | Select-Object -Last 1).Trim()
    $javaDigest = (Invoke-NativeCommand {
        docker image inspect --format '{{index .RepoDigests 0}}' $javaImage
    } 'Java image digest inspection failed' | Select-Object -Last 1).Trim()
    $mysqlVersion = (Invoke-NativeCommand {
        docker run --rm $mysqlDigest mysql --version
    } 'MySQL version inspection failed' | Select-Object -Last 1).Trim()

    Invoke-NativeCommand { docker network create $networkName } 'Temporary Docker network creation failed' | Out-Null
    $networkCreated = $true
    Invoke-NativeCommand {
        docker run --detach --rm --name $containerName --network $networkName --network-alias $containerName `
            -e "MYSQL_DATABASE=$dbName" `
            -e "MYSQL_USER=$dbUser" `
            -e "MYSQL_PASSWORD=$dbPassword" `
            -e "MYSQL_ROOT_PASSWORD=$rootPassword" `
            $mysqlDigest `
            --character-set-server=utf8mb4 `
            --collation-server=utf8mb4_0900_ai_ci
    } 'Temporary MySQL start failed' | Out-Null
    $containerStarted = $true

    $ready = $false
    for ($attempt = 1; $attempt -le 30; $attempt++) {
        docker exec $containerName sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysqladmin --protocol=TCP --host=127.0.0.1 --user="$MYSQL_USER" ping --silent' 2>$null
        if ($LASTEXITCODE -eq 0) {
            $ready = $true
            break
        }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) {
        throw 'Temporary MySQL did not become ready.'
    }

    Invoke-NativeCommand {
        docker exec -e "MYSQL_PWD=$rootPassword" $containerName mysql `
            --protocol=TCP --host=127.0.0.1 --user=root `
            --execute="GRANT SELECT ON performance_schema.* TO '$dbUser'@'%'; FLUSH PRIVILEGES;"
    } 'performance_schema observer grant failed' | Out-Null

    docker exec -e "MYSQL_PWD=$dbPassword" $containerName mysql `
        --protocol=TCP --host=127.0.0.1 --user=$dbUser --database=$dbName `
        --execute='SELECT * FROM ops_perf_002_missing_table' 2>$null
    if ($LASTEXITCODE -eq 0) {
        throw 'Native MySQL non-zero exit check did not fail as expected.'
    }
    $nativeExitCheck = 'pass'

    Copy-Item -LiteralPath $harnessSource -Destination $harnessTarget -ErrorAction Stop
    $harnessCopied = $true
    Invoke-NativeCommand {
        docker run --rm --network $networkName `
            -e "SPRING_DATASOURCE_URL=jdbc:mysql://${containerName}:3306/${dbName}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" `
            -e "SPRING_DATASOURCE_USERNAME=$dbUser" `
            -e "SPRING_DATASOURCE_PASSWORD=$dbPassword" `
            -e 'PAWCYCLE_MVP2_RECONCILIATION_ENABLED=false' `
            -e 'PAWCYCLE_LOCAL_QA_BOOTSTRAP_ENABLED=false' `
            -e "OPS_PERF_002_ROWS=$Rows" `
            -e "OPS_PERF_002_WARMUP=$Warmup" `
            -e "OPS_PERF_002_ITERATIONS=$Iterations" `
            -e "OPS_PERF_002_LOCK_ITERATIONS=$LockIterations" `
            -e "OPS_PERF_002_LOCK_ROWS=$LockRows" `
            -e "OPS_PERF_002_RUN_ID=$RunId" `
            -e "OPS_PERF_002_SOURCE_COMMIT=$sourceCommit" `
            -e "OPS_PERF_002_SCRIPT_SHA256=$scriptSha256" `
            -e "OPS_PERF_002_HARNESS_SHA256=$harnessSha256" `
            -e "OPS_PERF_002_MYSQL_IMAGE=$mysqlImage" `
            -e "OPS_PERF_002_MYSQL_DIGEST=$mysqlDigest" `
            -e "OPS_PERF_002_MYSQL_VERSION=$mysqlVersion" `
            -e "OPS_PERF_002_JAVA_IMAGE=$javaDigest" `
            -e "OPS_PERF_002_NATIVE_EXIT_CHECK=$nativeExitCheck" `
            -e "OPS_PERF_002_OUTPUT=$containerOutputPath" `
            -v "${repoRoot}:/repo" `
            -v 'pawcycle-gradle-cache:/root/.gradle' `
            -w /repo/backend `
            $javaDigest `
            sh gradlew --no-daemon test --tests com.pawcycle.backend.subscription.v2.OpsPerf002MigrationMeasurementTests
    } 'OPS-PERF-002 measurement test failed' | Out-Null

    if (-not (Test-Path -LiteralPath $outputHostPath)) {
        throw 'Measurement evidence JSON was not created.'
    }
    $evidence = Get-Content -Raw -Encoding utf8 -LiteralPath $outputHostPath | ConvertFrom-Json
    if ($evidence.run_id -ne $RunId -or $evidence.source_commit -ne $sourceCommit) {
        throw 'Measurement evidence provenance validation failed.'
    }
    if ($evidence.native_command_nonzero_check -ne 'pass' -or $evidence.final_legacy_rows -ne 0) {
        throw 'Measurement evidence validation failed.'
    }

    Write-Output "run_id=$RunId"
    Write-Output "source_commit=$sourceCommit"
    Write-Output "mysql_digest=$mysqlDigest"
    Write-Output "output=$OutputPath"
    Write-Output 'validation=pass'
} catch {
    $primaryError = $_
} finally {
    if ($harnessCopied -and (Test-Path -LiteralPath $harnessTarget)) {
        try {
            Remove-Item -LiteralPath $harnessTarget -Force -ErrorAction Stop
        } catch {
            Add-CleanupFailure $_
        }
    }
    if ($containerStarted) {
        try {
            Invoke-NativeCommand { docker stop $containerName } 'Temporary MySQL stop failed' | Out-Null
        } catch {
            Add-CleanupFailure $_
        }
    }
    if ($networkCreated) {
        try {
            Invoke-NativeCommand { docker network rm $networkName } 'Temporary Docker network cleanup failed' | Out-Null
        } catch {
            Add-CleanupFailure $_
        }
    }
}

if ($null -ne $primaryError) {
    throw $primaryError
}
