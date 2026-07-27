param(
    [string]$BaseUrl = 'http://localhost:7070/api',
    [string]$RedisContainer = 'letchat-test-redis'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

$http = [System.Net.Http.HttpClient]::new()

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
    $response = Invoke-WebRequest -Method POST -Uri "$BaseUrl$Path" -Body $Body -Headers $headers -UseBasicParsing
    return $response.Content | ConvertFrom-Json
}

function Get-Captcha {
    $captcha = Invoke-Form '/account/checkCode'
    Assert-Ok $captcha 'captcha'
    $raw = (docker exec $RedisContainer redis-cli -n 1 GET "letchat:checkcode:$($captcha.data.checkCodeKey)" | Out-String).Trim()
    if (-not $raw) {
        throw 'captcha code was not found in Redis'
    }
    try {
        $code = [string]($raw | ConvertFrom-Json)
    } catch {
        $code = $raw.Trim('"')
    }
    return @{ Key = [string]$captcha.data.checkCodeKey; Code = $code }
}

function Login-TestUser {
    param([string]$Email)

    $captcha = Get-Captcha
    $response = Invoke-Form '/account/login' @{
        checkCodeKey = $captcha.Key
        email = $Email
        password = 'Test@123456'
        checkCode = $captcha.Code
    }
    Assert-Ok $response "login $Email"
    return $response.data
}

function Invoke-CreateGroup {
    param([string]$Token, [string]$GroupName)

    $form = [System.Net.Http.MultipartFormDataContent]::new()
    $form.Add([System.Net.Http.StringContent]::new($GroupName), 'groupName')
    $form.Add([System.Net.Http.StringContent]::new('Disposable automated smoke-test group. It is dissolved after verification.'), 'groupNotice')
    $form.Add([System.Net.Http.StringContent]::new('0'), 'joinType')
    $png = [Convert]::FromBase64String('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL0TAAAAABJRU5ErkJggg==')
    foreach ($field in @('avatarFile', 'avatarCover')) {
        $image = [System.Net.Http.ByteArrayContent]::new($png)
        $image.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('image/png')
        $form.Add($image, $field, 'group.png')
    }
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post, "$BaseUrl/group/saveGroup")
    $request.Headers.Add('token', $Token)
    $request.Content = $form
    $response = $http.SendAsync($request).GetAwaiter().GetResult()
    $payload = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
    Assert-Ok $payload 'create group'
}

$alice = Login-TestUser 'alice@example.com'
$bob = Login-TestUser 'bob@example.com'
$groupName = "smoke-group-$([guid]::NewGuid().ToString('N').Substring(0, 8))"
$groupId = $null
$steps = New-Object System.Collections.Generic.List[string]

try {
    Invoke-CreateGroup $alice.token $groupName
    $groups = Invoke-Form '/group/loadMyGroup' @{} $alice.token
    Assert-Ok $groups 'load owned groups'
    $created = @($groups.data | Where-Object { $_.groupName -eq $groupName }) | Select-Object -First 1
    if (-not $created.groupId) {
        throw 'created group not found in owner group list'
    }
    $groupId = [string]$created.groupId
    $steps.Add('create group')

    $initial = Invoke-Form '/group/getGroupInfo4chat' @{ groupId = $groupId } $alice.token
    Assert-Ok $initial 'read initial group detail'
    if (@($initial.data.userContactList).Count -ne 1) {
        throw 'new group should contain only its owner'
    }
    $steps.Add('owner detail')

    $add = Invoke-Form '/group/addOrRemoveGroupUser' @{ groupId = $groupId; selectContacts = [string]$bob.userId; opType = '0' } $alice.token
    Assert-Ok $add 'add group member'
    $afterAdd = Invoke-Form '/group/getGroupInfo4chat' @{ groupId = $groupId } $alice.token
    Assert-Ok $afterAdd 'read group after add'
    if (@($afterAdd.data.userContactList).Count -ne 2) {
        throw 'group member count should be 2 after adding Bob'
    }
    $steps.Add('add member')

    $messageText = "group lifecycle $([guid]::NewGuid().ToString('N').Substring(0, 8))"
    $sent = Invoke-Form '/chat/sendMessage' @{ contactId = $groupId; messageContent = $messageText; messageType = '2' } $alice.token
    Assert-Ok $sent 'send group message'
    $saved = $false
    foreach ($attempt in 1..12) {
        Start-Sleep -Milliseconds 500
        $history = Invoke-Form '/chat/loadChatMessage' @{ contactId = $groupId; pageNo = '1' } $bob.token
        Assert-Ok $history 'load member group history'
        if (@($history.data.list | Where-Object { $_.messageContent -eq $messageText }).Count -gt 0) {
            $saved = $true
            break
        }
    }
    if (-not $saved) {
        throw 'group message was not persisted for member history'
    }
    $steps.Add('group message and history')

    $remove = Invoke-Form '/group/addOrRemoveGroupUser' @{ groupId = $groupId; selectContacts = [string]$bob.userId; opType = '1' } $alice.token
    Assert-Ok $remove 'remove group member'
    $denied = Invoke-Form '/group/getGroupInfo' @{ groupId = $groupId } $bob.token
    if ($denied.code -eq 200) {
        throw 'removed member can still read group details'
    }
    $steps.Add('remove member authorization')
} finally {
    if ($groupId) {
        $dissolve = Invoke-Form '/group/dissolutionGroup' @{ groupId = $groupId } $alice.token
        Assert-Ok $dissolve 'dissolve disposable group'
        $steps.Add('dissolve group cleanup')
    }
    $http.Dispose()
}

[pscustomobject]@{
    Result = 'passed'
    Steps = $steps
    TestGroup = $groupName
}
