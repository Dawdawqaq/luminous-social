param ([string]$PlaywrightModule = '')
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot/dev-docker.ps1"
$avatarDocker = Resolve-LuminousDocker
foreach ($service in @('frontend','backend','mysql','redis','minio')) {
    $projectLabel = & $avatarDocker inspect "luminous-dev-$service" --format '{{index .Config.Labels "com.docker.compose.project"}}'
    if ($LASTEXITCODE -ne 0 -or $projectLabel.Trim() -ne 'luminous-dev') { throw '头像验收仅允许独立开发容器' }
}
if (-not $PlaywrightModule) {
    $PlaywrightModule = Get-ChildItem -LiteralPath (Join-Path $repoRoot '.local/npm-cache/_npx') -Directory |
        ForEach-Object { Join-Path $_.FullName 'node_modules/playwright/index.mjs' } |
        Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}
if (-not $PlaywrightModule) { throw '需要工作区 Playwright 模块' }
$previousModule = $env:E2E_PLAYWRIGHT_MODULE
$previousMarker = $env:E2E_RUN_MARKER
$runMarker = '[E2E_TEST] avatars-' + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$env:E2E_PLAYWRIGHT_MODULE = (Resolve-Path -LiteralPath $PlaywrightModule).Path
$env:E2E_RUN_MARKER = $runMarker
Push-Location $repoRoot
try {
    node "$PSScriptRoot/verify-profile-avatars.mjs"
    if ($LASTEXITCODE -ne 0) { throw '头像预设真实联调验收失败' }
} finally {
    try { & "$PSScriptRoot/clean-test-data.ps1" -RunMarker $runMarker }
    finally { $env:E2E_PLAYWRIGHT_MODULE = $previousModule; $env:E2E_RUN_MARKER = $previousMarker; Pop-Location }
}
