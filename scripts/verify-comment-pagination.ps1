param ([string]$PlaywrightModule = '')
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot/dev-docker.ps1"
$commentDocker = Resolve-LuminousDocker
foreach ($service in @('frontend', 'backend', 'mysql', 'redis', 'minio')) {
    $label = & $commentDocker inspect "luminous-dev-$service" --format '{{index .Config.Labels "com.docker.compose.project"}}'
    if ($LASTEXITCODE -ne 0 -or $label.Trim() -ne 'luminous-dev') { throw '评论分页测试只允许独立开发容器' }
}
if (-not $PlaywrightModule) {
    $PlaywrightModule = Get-ChildItem -LiteralPath (Join-Path $repoRoot '.local/npm-cache/_npx') -Directory |
        ForEach-Object { Join-Path $_.FullName 'node_modules/playwright/index.mjs' } |
        Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}
if (-not $PlaywrightModule -or -not (Test-Path -LiteralPath $PlaywrightModule)) { throw '需要工作区 Playwright 模块或传入 -PlaywrightModule' }
$previousModule = $env:E2E_PLAYWRIGHT_MODULE
$previousMarker = $env:E2E_RUN_MARKER
$runMarker = '[E2E_TEST] comment-pagination-' + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$env:E2E_PLAYWRIGHT_MODULE = (Resolve-Path -LiteralPath $PlaywrightModule).Path
$env:E2E_RUN_MARKER = $runMarker
Push-Location $repoRoot
try {
    node "$PSScriptRoot/verify-comment-pagination.mjs"
    if ($LASTEXITCODE -ne 0) { throw '评论分页端到端验收失败' }
} finally {
    try { & "$PSScriptRoot/clean-test-data.ps1" -RunMarker $runMarker }
    finally {
        $env:E2E_PLAYWRIGHT_MODULE = $previousModule
        $env:E2E_RUN_MARKER = $previousMarker
        Pop-Location
    }
}
