$ErrorActionPreference = 'Stop'

$base = 'http://localhost:7070/api'
$redisContainer = if ($env:REDIS_CONTAINER) { $env:REDIS_CONTAINER } else { 'letchat-test-redis' }

function Call-Form {
    param(
        [string]$Path,
        [hashtable]$Body = @{},
        [hashtable]$Headers = @{}
    )
    $resp = Invoke-WebRequest -Method POST -Uri "$base$Path" -Body $Body -Headers $Headers -UseBasicParsing
    return $resp.Content | ConvertFrom-Json
}

function Login-User {
    param([string]$Email)
    $r = Call-Form '/account/checkCode'
    $key = [string]$r.data.checkCodeKey
    $raw = docker exec $redisContainer redis-cli -n 1 GET "letchat:checkcode:$key"
    $rawText = ($raw | Out-String).Trim()
    try {
        $code = [string]($rawText | ConvertFrom-Json)
    } catch {
        $code = $rawText.Trim('"')
    }
    $login = Call-Form '/account/login' @{
        checkCodeKey = $key
        email = $Email
        password = 'Test@123456'
        checkCode = $code
    }
    if ($login.code -ne 200 -or -not $login.data.token) {
        throw "login failed: $($login | ConvertTo-Json -Compress -Depth 6)"
    }
    return $login.data
}

function Percentile {
    param([double[]]$Values, [double]$P)
    $sorted = $Values | Sort-Object
    if ($sorted.Count -eq 0) { return 0 }
    $idx = [Math]::Ceiling(($P / 100.0) * $sorted.Count) - 1
    $idx = [Math]::Max(0, [Math]::Min($idx, $sorted.Count - 1))
    return [math]::Round([double]$sorted[$idx], 2)
}

function Start-RequestJob {
    param([string]$Token, [string]$Name, [string]$Path, [hashtable]$Body)
    Start-Job -ArgumentList $Token,$Name,$Path,($Body | ConvertTo-Json -Compress) -ScriptBlock {
        param($token, $name, $path, $bodyJson)
        $body = $bodyJson | ConvertFrom-Json
        $hash = @{}
        $body.PSObject.Properties | ForEach-Object { $hash[$_.Name] = [string]$_.Value }
        $sw = [Diagnostics.Stopwatch]::StartNew()
        try {
            $resp = Invoke-WebRequest -Method POST -Uri "http://localhost:7070/api$path" -Body $hash -Headers @{ token=$token } -UseBasicParsing
            $sw.Stop()
            $json = $resp.Content | ConvertFrom-Json
            [pscustomobject]@{
                Endpoint = $name
                Ok = ($json.code -eq 200)
                Ms = $sw.Elapsed.TotalMilliseconds
                Detail = $resp.Content
            }
        } catch {
            $sw.Stop()
            [pscustomobject]@{
                Endpoint = $name
                Ok = $false
                Ms = $sw.Elapsed.TotalMilliseconds
                Detail = $_.Exception.Message
            }
        }
    }
}

$alice = Login-User 'alice@example.com'
$token = [string]$alice.token

$requests = @()
1..50 | ForEach-Object { $requests += @{ Name='load contacts'; Path='/contact/loadContact'; Body=@{ contactType='U' } } }
1..50 | ForEach-Object { $requests += @{ Name='load messages'; Path='/chat/loadChatMessage'; Body=@{ contactId='U10000100002'; pageNo='1' } } }

$allResults = @()
$queue = New-Object System.Collections.Queue
$requests | ForEach-Object { $queue.Enqueue($_) }

while ($queue.Count -gt 0) {
    $batch = @()
    while ($queue.Count -gt 0 -and $batch.Count -lt 20) {
        $batch += $queue.Dequeue()
    }
    $jobs = foreach ($item in $batch) {
        Start-RequestJob $token $item.Name $item.Path $item.Body
    }
    $batchResults = $jobs | Receive-Job -Wait
    $jobs | Remove-Job -Force
    $allResults += $batchResults
}

$latencies = @($allResults | ForEach-Object { [double]$_.Ms })
$summary = [pscustomobject]@{
    Total = $allResults.Count
    Concurrency = 20
    Success = @($allResults | Where-Object { $_.Ok }).Count
    Failure = @($allResults | Where-Object { -not $_.Ok }).Count
    AvgMs = [math]::Round(($latencies | Measure-Object -Average).Average, 2)
    P50Ms = Percentile $latencies 50
    P95Ms = Percentile $latencies 95
    MaxMs = [math]::Round(($latencies | Measure-Object -Maximum).Maximum, 2)
    EndpointSummary = @($allResults | Group-Object Endpoint | ForEach-Object {
        $items = @($_.Group)
        $endpointLatencies = @($items | ForEach-Object { [double]$_.Ms })
        [pscustomobject]@{
            Endpoint = $_.Name
            Total = $items.Count
            Success = @($items | Where-Object { $_.Ok }).Count
            Failure = @($items | Where-Object { -not $_.Ok }).Count
            AvgMs = [math]::Round(($endpointLatencies | Measure-Object -Average).Average, 2)
            P95Ms = Percentile $endpointLatencies 95
        }
    })
}

$summary | ConvertTo-Json -Depth 8
if ($summary.Failure -gt 0) {
    exit 1
}
