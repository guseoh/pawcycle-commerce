param(
    [string]$ComposeDirectory = "infra/local-integration",
    [int[]]$PageSizes = @(10, 20, 100),
    [int]$Warmup = 3,
    [int]$Iterations = 9
)

$ErrorActionPreference = "Stop"
if ($Warmup -lt 0) { throw "Warmup must be zero or greater" }
if ($Iterations -lt 1) { throw "Iterations must be one or greater" }
$prefix = "[OPS-PERF-001]"
$measurementEmail = "ops-perf-001-local@example.test"
$envFile = Join-Path $ComposeDirectory ".env.local"
if (-not (Test-Path $envFile)) { throw "local integration .env.local is required" }
Get-Content $envFile | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { Set-Item -Path "Env:$($matches[1].Trim())" -Value $matches[2].Trim('"') }
}
Push-Location $ComposeDirectory

function Invoke-Mysql([string]$Sql) {
    $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Sql))
    $command = "echo $encoded | base64 -d | MYSQL_PWD=`"`$MYSQL_PASSWORD`" mysql --protocol=TCP --host=127.0.0.1 --user=`"`$MYSQL_USER`" --database=`"`$MYSQL_DATABASE`" --batch --skip-column-names"
    return & docker compose --env-file .env.local exec -T mysql sh -lc $command
}
function Questions() { [long](Invoke-Mysql "SHOW GLOBAL STATUS LIKE 'Questions';" | Select-Object -Last 1 | ForEach-Object { ($_ -split "`t")[-1] }) }
function Median([long[]]$Values) { $s = $Values | Sort-Object; if ($s.Count % 2) { return $s[[int]($s.Count / 2)] }; return [long](($s[$s.Count / 2 - 1] + $s[$s.Count / 2]) / 2) }

$cleanup = @"
DELETE si FROM subscription_snapshot_items si JOIN subscription_snapshots ss ON ss.id=si.snapshot_id JOIN subscriptions s ON s.id=ss.subscription_id WHERE s.member_id=(SELECT id FROM members WHERE email='$measurementEmail');
DELETE ss FROM subscription_schedules ss JOIN subscriptions s ON s.id=ss.subscription_id WHERE s.member_id=(SELECT id FROM members WHERE email='$measurementEmail');
UPDATE subscriptions SET current_snapshot_id=NULL WHERE member_id=(SELECT id FROM members WHERE email='$measurementEmail');
DELETE sn FROM subscription_snapshots sn JOIN subscriptions s ON s.id=sn.subscription_id WHERE s.member_id=(SELECT id FROM members WHERE email='$measurementEmail');
DELETE FROM subscriptions WHERE member_id=(SELECT id FROM members WHERE email='$measurementEmail');
DELETE FROM pets WHERE member_id=(SELECT id FROM members WHERE email='$measurementEmail');
DELETE FROM members WHERE email='$measurementEmail';
UPDATE subscription_plans SET current_plan_version_id=NULL WHERE name LIKE '$prefix%';
DELETE FROM plan_version_delivery_cycles WHERE plan_version_id IN (SELECT id FROM plan_versions WHERE plan_id IN (SELECT id FROM subscription_plans WHERE name LIKE '$prefix%'));
DELETE FROM plan_items WHERE plan_version_id IN (SELECT id FROM plan_versions WHERE plan_id IN (SELECT id FROM subscription_plans WHERE name LIKE '$prefix%'));
DELETE FROM plan_versions WHERE plan_id IN (SELECT id FROM subscription_plans WHERE name LIKE '$prefix%');
DELETE FROM subscription_plans WHERE name LIKE '$prefix%';
"@

$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$base = "http://localhost:8080"
$loggedIn = $false
$scriptFailed = $true
try {
    Invoke-Mysql $cleanup | Out-Null
    Invoke-Mysql @"
INSERT INTO members(email,password_hash) SELECT '$measurementEmail',password_hash FROM members WHERE email='$($env:PAWCYCLE_LOCAL_QA_BOOTSTRAP_EMAIL)';
SET @member_id=(SELECT id FROM members WHERE email='$measurementEmail');
SET @sku_id=(SELECT id FROM skus ORDER BY id LIMIT 1);
INSERT INTO pets(member_id,name,pet_type) VALUES(@member_id,'$prefix pet','DOG'); SET @pet_id=LAST_INSERT_ID();
INSERT INTO subscription_plans(name,target_pet_type,on_sale) SELECT CONCAT('$prefix plan ', n),'DOG',true FROM (WITH RECURSIVE seq AS (SELECT 1 n UNION ALL SELECT n+1 FROM seq WHERE n<100) SELECT n FROM seq) q;
INSERT INTO plan_versions(plan_id,package_price_krw,is_migration_only) SELECT id,19900,false FROM subscription_plans WHERE name LIKE '$prefix%';
UPDATE subscription_plans p JOIN plan_versions v ON v.plan_id=p.id SET p.current_plan_version_id=v.id WHERE p.name LIKE '$prefix%';
INSERT INTO plan_items(plan_version_id,sku_id,quantity) SELECT v.id,@sku_id,1 FROM plan_versions v JOIN subscription_plans p ON p.id=v.plan_id WHERE p.name LIKE '$prefix%';
INSERT INTO plan_version_delivery_cycles(plan_version_id,delivery_cycle_weeks) SELECT v.id,4 FROM plan_versions v JOIN subscription_plans p ON p.id=v.plan_id WHERE p.name LIKE '$prefix%';
INSERT INTO subscriptions(member_id,sku_id,quantity,delivery_cycle_weeks,created_date,next_order_date,pet_id,status,version,mvp2_managed) SELECT @member_id,@sku_id,1,4,CURDATE(),DATE_ADD(CURDATE(),INTERVAL 4 WEEK),@pet_id,'ACTIVE',0,true FROM (WITH RECURSIVE seq AS (SELECT 1 n UNION ALL SELECT n+1 FROM seq WHERE n<100) SELECT n FROM seq) q;
INSERT INTO subscription_snapshots(subscription_id,source_plan_version_id,package_total_krw,delivery_cycle_weeks) SELECT s.id,(SELECT current_plan_version_id FROM subscription_plans WHERE name LIKE '$prefix%' ORDER BY id LIMIT 1),19900,4 FROM subscriptions s WHERE s.member_id=@member_id AND s.mvp2_managed=true;
UPDATE subscriptions s JOIN subscription_snapshots sn ON sn.subscription_id=s.id SET s.current_snapshot_id=sn.id WHERE s.member_id=@member_id AND s.mvp2_managed=true;
INSERT INTO subscription_snapshot_items(snapshot_id,sku_id,quantity) SELECT id,@sku_id,1 FROM subscription_snapshots WHERE subscription_id IN (SELECT id FROM subscriptions WHERE member_id=@member_id AND mvp2_managed=true);
INSERT INTO subscription_schedules(subscription_id,scheduled_date,status) SELECT id,DATE_ADD(CURDATE(),INTERVAL 4 WEEK),'SCHEDULED' FROM subscriptions WHERE member_id=@member_id AND mvp2_managed=true;
"@ | Out-Null
    $csrf = (Invoke-RestMethod "$base/api/auth/csrf" -WebSession $session).token
    $login = @{ email=$measurementEmail; password=$env:PAWCYCLE_LOCAL_QA_BOOTSTRAP_PASSWORD } | ConvertTo-Json -Compress
    Invoke-RestMethod "$base/api/auth/login" -Method Post -WebSession $session -Headers @{"X-CSRF-TOKEN"=$csrf} -ContentType "application/json" -Body $login | Out-Null
    $loggedIn = $true
    $petId = Invoke-Mysql "SELECT id FROM pets WHERE name='$prefix pet' ORDER BY id DESC LIMIT 1;" | Select-Object -Last 1
    $results = foreach ($size in $PageSizes) {
        foreach ($route in @("/api/v2/subscription-plans?petId=$petId&page=0&size=$size", "/api/v2/subscriptions?page=0&size=$size")) {
            if ($Warmup -gt 0) { 1..$Warmup | ForEach-Object { Invoke-WebRequest "$base$route" -WebSession $session -UseBasicParsing | Out-Null } }
            $samples = foreach ($i in 1..$Iterations) { $before=Questions; $watch=[Diagnostics.Stopwatch]::StartNew(); $response=Invoke-WebRequest "$base$route" -WebSession $session -UseBasicParsing; $watch.Stop(); $after=Questions; [pscustomobject]@{latency_ms=$watch.ElapsedMilliseconds; query_count=$after-$before; status=$response.StatusCode} }
            [pscustomobject]@{route=$route.Split('?')[0]; page_size=$size; warmup=$Warmup; iterations=$Iterations; query_count_median=(Median @($samples.query_count)); latency_median_ms=(Median @($samples.latency_ms)); samples=$samples}
        }
    }
    $csrf = (Invoke-RestMethod "$base/api/auth/csrf" -WebSession $session).token
    Invoke-RestMethod "$base/api/auth/logout" -Method Post -WebSession $session -Headers @{"X-CSRF-TOKEN"=$csrf} | Out-Null
    $loggedIn = $false
    $scriptFailed = $false
    $results | ConvertTo-Json -Depth 4
}
finally {
    if ($loggedIn) {
        try {
            $csrf = (Invoke-RestMethod "$base/api/auth/csrf" -WebSession $session).token
            Invoke-RestMethod "$base/api/auth/logout" -Method Post -WebSession $session -Headers @{"X-CSRF-TOKEN"=$csrf} | Out-Null
        } catch {
            if ($scriptFailed) { Write-Warning "measurement logout failed during cleanup: $($_.Exception.Message)" } else { throw }
        }
    }
    try { Invoke-Mysql $cleanup | Out-Null }
    catch {
        if ($scriptFailed) { Write-Warning "fixture cleanup failed after measurement error: $($_.Exception.Message)" } else { throw }
    }
    Pop-Location
}
