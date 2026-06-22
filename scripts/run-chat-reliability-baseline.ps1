$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$javaRoot = Join-Path $repoRoot 'letchat_java'
$baselinePath = Join-Path $javaRoot 'target\letchat-baseline\chat-reliability-baseline.json'

Push-Location $javaRoot
try {
    mvn -q -Dtest=ChatReliabilityBaselineTest test
    if (-not (Test-Path $baselinePath)) {
        throw "baseline output was not generated: $baselinePath"
    }
    Get-Content -LiteralPath $baselinePath -Raw
} finally {
    Pop-Location
}
