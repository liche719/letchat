$ErrorActionPreference = 'Stop'

$base = 'http://localhost:7070/api'
$redisContainer = if ($env:REDIS_CONTAINER) { $env:REDIS_CONTAINER } else { 'letchat-test-redis' }
$mysqlContainer = if ($env:MYSQL_CONTAINER) { $env:MYSQL_CONTAINER } else { 'letchat-test-mysql' }

$results = New-Object System.Collections.Generic.List[object]

function Invoke-Step {
    param([string]$Name, [scriptblock]$Action)
    $sw = [Diagnostics.Stopwatch]::StartNew()
    try {
        $value = & $Action
        $sw.Stop()
        $results.Add([pscustomobject]@{
            Name = $Name
            Ok = $true
            Ms = [math]::Round($sw.Elapsed.TotalMilliseconds, 2)
            Detail = $value
        })
        return $value
    } catch {
        $sw.Stop()
        $results.Add([pscustomobject]@{
            Name = $Name
            Ok = $false
            Ms = [math]::Round($sw.Elapsed.TotalMilliseconds, 2)
            Detail = $_.Exception.Message
        })
        return $null
    }
}

function Call-Form {
    param(
        [string]$Path,
        [hashtable]$Body = @{},
        [hashtable]$Headers = @{}
    )
    $resp = Invoke-WebRequest -Method POST -Uri "$base$Path" -Body $Body -Headers $Headers -UseBasicParsing
    $json = $resp.Content | ConvertFrom-Json
    return $json
}

function Get-CheckCode {
    $r = Call-Form '/account/checkCode'
    if ($r.code -ne 200 -or -not $r.data.checkCodeKey) {
        throw "checkCode failed: $($r | ConvertTo-Json -Compress -Depth 6)"
    }
    $key = [string]$r.data.checkCodeKey
    $raw = docker exec $redisContainer redis-cli -n 1 GET "letchat:checkcode:$key"
    $rawText = ($raw | Out-String).Trim()
    if ([string]::IsNullOrWhiteSpace($rawText)) {
        throw "cannot read captcha code from Redis, key=$key raw=$raw"
    }
    try {
        $code = [string]($rawText | ConvertFrom-Json)
    } catch {
        $code = $rawText.Trim('"')
    }
    return [pscustomobject]@{ Key=$key; Code=$code }
}

function Login-User {
    param([string]$Email)
    $captcha = Get-CheckCode
    $r = Call-Form '/account/login' @{
        checkCodeKey = $captcha.Key
        email = $Email
        password = 'Test@123456'
        checkCode = $captcha.Code
    }
    if ($r.code -ne 200 -or -not $r.data.token) {
        throw "login failed for $Email : $($r | ConvertTo-Json -Compress -Depth 6)"
    }
    return $r.data
}

function Register-User {
    param(
        [string]$Email,
        [string]$NickName,
        [string]$Password = 'Test@123456'
    )
    $captcha = Get-CheckCode
    $r = Call-Form '/account/register' @{
        checkCodeKey = $captcha.Key
        email = $Email
        password = $Password
        nickName = $NickName
        checkCode = $captcha.Code
    }
    if ($r.code -ne 200) {
        throw "register failed for $Email : $($r | ConvertTo-Json -Compress -Depth 6)"
    }
    return $r
}

function Receive-WsText {
    param(
        [System.Net.WebSockets.ClientWebSocket]$Socket,
        [int]$TimeoutMs = 5000
    )
    $buffer = New-Object byte[] 65536
    $segment = [ArraySegment[byte]]::new($buffer)
    $cts = [Threading.CancellationTokenSource]::new($TimeoutMs)
    try {
        $result = $Socket.ReceiveAsync($segment, $cts.Token).GetAwaiter().GetResult()
        if ($result.MessageType -eq [System.Net.WebSockets.WebSocketMessageType]::Close) {
            return '__CLOSED__'
        }
        return [Text.Encoding]::UTF8.GetString($buffer, 0, $result.Count)
    } finally {
        $cts.Dispose()
    }
}

function Connect-Ws {
    param([string]$Token)
    $socket = [System.Net.WebSockets.ClientWebSocket]::new()
    $null = $socket.ConnectAsync([Uri]"ws://localhost:7071/ws?token=$Token", [Threading.CancellationToken]::None).GetAwaiter().GetResult()
    return $socket
}

Invoke-Step 'api docs' {
    $resp = Invoke-WebRequest -Method GET -Uri "$base/v3/api-docs" -UseBasicParsing
    if ($resp.StatusCode -ne 200 -or $resp.Content -notlike '*openapi*') {
        throw 'api docs unavailable'
    }
    'ok'
} | Out-Null

Invoke-Step 'auth guard without token' {
    $r = Call-Form '/contact/loadContact' @{ contactType = 'U' }
    if ($r.code -ne 901) {
        throw "expected login timeout code 901, got $($r | ConvertTo-Json -Compress -Depth 6)"
    }
    'ok'
} | Out-Null

$newUserEmail = "runtime-$([Guid]::NewGuid().ToString('N').Substring(0, 8))@example.com"
Invoke-Step 'register new user' {
    Register-User $newUserEmail 'RuntimeUser' | Out-Null
    'ok'
} | Out-Null

Invoke-Step 'login registered user' {
    $registeredUser = Login-User $newUserEmail
    if (-not $registeredUser.userId) {
        throw "registered login returned no userId: $($registeredUser | ConvertTo-Json -Compress -Depth 6)"
    }
    "userId=$($registeredUser.userId)"
} | Out-Null

$alice = Invoke-Step 'login alice' { Login-User 'alice@example.com' }
$bob = Invoke-Step 'login bob' { Login-User 'bob@example.com' }

$aliceHeaders = @{ token = [string]$alice.token }
$bobHeaders = @{ token = [string]$bob.token }

Invoke-Step 'load alice contacts' {
    $r = Call-Form '/contact/loadContact' @{ contactType = 'U' } $aliceHeaders
    if ($r.code -ne 200 -or @($r.data).Count -lt 1) {
        throw "contacts failed: $($r | ConvertTo-Json -Compress -Depth 6)"
    }
    'ok'
} | Out-Null

$aliceWs = $null
$bobWs = $null
try {
    Invoke-Step 'websocket alice init' {
        $socket = Connect-Ws ([string]$alice.token)
        $msg = Receive-WsText $socket
        if ($msg -notlike '*"messageType":0*') {
            throw "init message missing: $msg"
        }
        $script:aliceWs = $socket
        'ok'
    } | Out-Null

    Invoke-Step 'websocket bob init' {
        $socket = Connect-Ws ([string]$bob.token)
        $msg = Receive-WsText $socket
        if ($msg -notlike '*"messageType":0*') {
            throw "init message missing: $msg"
        }
        $script:bobWs = $socket
        'ok'
    } | Out-Null

    Invoke-Step 'send private message' {
        $message = 'runtime hello ' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
        $r = Call-Form '/chat/sendMessage' @{
            contactId = [string]$bob.userId
            messageContent = $message
            messageType = '2'
        } $aliceHeaders
        if ($r.code -ne 200) {
            throw "sendMessage failed: $($r | ConvertTo-Json -Compress -Depth 6)"
        }
        $wsText = Receive-WsText $script:bobWs 8000
        if ($wsText -notlike "*$message*") {
            throw "bob did not receive websocket message: $wsText"
        }
        Start-Sleep -Milliseconds 800
        $count = docker exec $mysqlContainer mysql -uroot -p123456 --batch --skip-column-names letchat -e "SELECT COUNT(*) FROM chat_message WHERE message_content='$message';"
        if ([int]($count | Select-Object -First 1) -lt 1) {
            throw "message was not persisted by MQ consumer"
        }
        "message=$message"
    } | Out-Null
} finally {
    foreach ($socket in @($script:aliceWs, $script:bobWs)) {
        if ($socket -and $socket.State -eq [System.Net.WebSockets.WebSocketState]::Open) {
            $null = $socket.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, 'done', [Threading.CancellationToken]::None).GetAwaiter().GetResult()
            $socket.Dispose()
        }
    }
}

Invoke-Step 'load chat messages' {
    $r = Call-Form '/chat/loadChatMessage' @{ contactId = [string]$bob.userId; pageNo = '1' } $aliceHeaders
    if ($r.code -ne 200 -or @($r.data.list).Count -lt 1) {
        throw "loadChatMessage failed: $($r | ConvertTo-Json -Compress -Depth 8)"
    }
    'ok'
} | Out-Null

Invoke-Step 'queue drained' {
    $queues = docker exec letchat-test-rabbitmq rabbitmqctl list_queues name messages_ready messages_unacknowledged --formatter json
    $queueRows = $queues | ConvertFrom-Json
    $chatQueue = $queueRows | Where-Object { $_.name -eq 'chat.message.queue' } | Select-Object -First 1
    if (-not $chatQueue) {
        throw "chat queue missing: $queues"
    }
    if ($chatQueue.messages_ready -ne 0 -or $chatQueue.messages_unacknowledged -ne 0) {
        throw "chat queue not drained: $queues"
    }
    "ready=$($chatQueue.messages_ready), unacked=$($chatQueue.messages_unacknowledged)"
} | Out-Null

$results | ConvertTo-Json -Depth 8
if (($results | Where-Object { -not $_.Ok }).Count -gt 0) {
    exit 1
}
