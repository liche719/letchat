param(
    [string]$BaseUrl = 'http://localhost:7070/api',
    [string]$RedisContainer = 'letchat-test-redis',
    [int]$RequestTimeoutSeconds = 15
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

function Assert-Ok {
    param($Response, [string]$Step)
    if ($Response.code -ne 200) {
        throw "$Step failed: $($Response | ConvertTo-Json -Compress -Depth 8)"
    }
}

function Invoke-Form {
    param(
        [string]$Path,
        [hashtable]$Body = @{},
        [string]$Token = ''
    )

    $headers = @{}
    if ($Token) {
        $headers.token = $Token
    }
    $response = Invoke-WebRequest -Method POST -Uri "$BaseUrl$Path" -Body $Body -Headers $headers -UseBasicParsing -TimeoutSec $RequestTimeoutSeconds
    return $response.Content | ConvertFrom-Json
}

function Login-TestUser {
    param([string]$Email)

    $captcha = Invoke-Form '/account/checkCode'
    Assert-Ok $captcha 'captcha'
    $key = [string]$captcha.data.checkCodeKey
    $raw = (docker exec $RedisContainer redis-cli -n 1 GET "letchat:checkcode:$key" | Out-String).Trim()
    if (-not $raw) {
        throw 'captcha code was not found in Redis'
    }
    try {
        $code = [string]($raw | ConvertFrom-Json)
    } catch {
        $code = $raw.Trim('"')
    }
    $login = Invoke-Form '/account/login' @{
        checkCodeKey = $key
        email = $Email
        password = 'Test@123456'
        checkCode = $code
    }
    Assert-Ok $login "login $Email"
    return $login.data
}

$alice = Login-TestUser 'alice@example.com'
$bob = Login-TestUser 'bob@example.com'
$content = [Convert]::FromBase64String('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL0TAAAAABJRU5ErkJggg==')
$fileName = "file-smoke-$([Guid]::NewGuid().ToString('N').Substring(0, 8)).png"
$clientMessageId = [Guid]::NewGuid().ToString()

$message = Invoke-Form '/chat/sendMessage' @{
    contactId = [string]$bob.userId
    messageContent = $fileName
    messageType = '5'
    clientMessageId = $clientMessageId
    fileSize = [string]$content.Length
    fileName = $fileName
    fileType = '5'
} $alice.token
Assert-Ok $message 'create file message'
$messageId = [string]$message.data.messageId

$http = [System.Net.Http.HttpClient]::new()
$http.Timeout = [TimeSpan]::FromSeconds($RequestTimeoutSeconds)
try {
    $form = [System.Net.Http.MultipartFormDataContent]::new()
    foreach ($field in @('file', 'coverFile')) {
        $part = [System.Net.Http.ByteArrayContent]::new($content)
        $part.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('image/png')
        $form.Add($part, $field, $fileName)
    }
    $form.Add([System.Net.Http.StringContent]::new($messageId), 'messageId')
    $uploadRequest = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, "$BaseUrl/chat/uploadFile")
    $uploadRequest.Headers.Add('token', [string]$alice.token)
    $uploadRequest.Content = $form
    $uploadResponse = $http.SendAsync($uploadRequest).GetAwaiter().GetResult()
    $uploadPayload = $uploadResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
    Assert-Ok $uploadPayload 'upload file'

    $found = $false
    foreach ($attempt in 1..12) {
        Start-Sleep -Milliseconds 500
        $history = Invoke-Form '/chat/loadChatMessage' @{ contactId = [string]$alice.userId; pageNo = '1' } $bob.token
        Assert-Ok $history 'load recipient history'
        if (@($history.data.list | Where-Object { [string]$_.messageId -eq $messageId -and $_.fileName -eq $fileName }).Count -eq 1) {
            $found = $true
            break
        }
    }
    if (-not $found) {
        throw 'recipient history does not include the uploaded media message'
    }

    $downloadRequest = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Get, "$BaseUrl/chat/downloadFile?fileId=$messageId&showCover=false")
    $downloadRequest.Headers.Add('token', [string]$bob.token)
    $downloadResponse = $http.SendAsync($downloadRequest).GetAwaiter().GetResult()
    if (-not $downloadResponse.IsSuccessStatusCode) {
        throw "recipient download failed: $($downloadResponse.StatusCode)"
    }
    $downloaded = $downloadResponse.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
    $contentMatches = [BitConverter]::ToString($downloaded) -eq [BitConverter]::ToString($content)
    if ($downloaded.Length -ne $content.Length -or -not $contentMatches) {
        throw 'downloaded file content differs from uploaded content'
    }
} finally {
    $http.Dispose()
}

[pscustomobject]@{
    Result = 'passed'
    MessageId = $messageId
    FileName = $fileName
    Bytes = $content.Length
    RecipientDownload = 'verified'
}
