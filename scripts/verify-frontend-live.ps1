param ([string]$PlaywrightModule = '')
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot/dev-docker.ps1"
$dockerCli = Resolve-LuminousDocker
foreach ($liveService in @('frontend','backend')) {
    $projectLabel = & $dockerCli inspect "luminous-dev-$liveService" --format '{{index .Config.Labels "com.docker.compose.project"}}'
    if ($LASTEXITCODE -ne 0 -or $projectLabel.Trim() -ne 'luminous-dev') { throw '浏览器验收只允许独立开发容器' }
}
if (-not $PlaywrightModule) {
    $PlaywrightModule = Join-Path $repoRoot '.local/browser-tools/node_modules/playwright/index.mjs'
    if (-not (Test-Path -LiteralPath $PlaywrightModule)) {
        # 复用工作区的既有工具缓存，不写入全局 npm 或浏览器目录。
        $cacheRoots = Get-ChildItem -LiteralPath (Join-Path $repoRoot '.local/npm-cache/_npx') -Directory -ErrorAction SilentlyContinue
        $PlaywrightModule = $cacheRoots | ForEach-Object { Join-Path $_.FullName 'node_modules/playwright/index.mjs' } |
            Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    }
}
if (-not $PlaywrightModule -or -not (Test-Path -LiteralPath $PlaywrightModule)) {
    throw '请先执行 npm install --prefix .local/browser-tools playwright，或传入 -PlaywrightModule 完整路径；验收使用已安装的 Chrome'
}
$previousModule = $env:E2E_PLAYWRIGHT_MODULE
$previousMarker = $env:E2E_RUN_MARKER
$runMarker = '[E2E_TEST] ui-' + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$env:E2E_PLAYWRIGHT_MODULE = (Resolve-Path -LiteralPath $PlaywrightModule).Path
$env:E2E_RUN_MARKER = $runMarker
Push-Location $repoRoot
try {
    node "$PSScriptRoot/verify-frontend-live.mjs"
    if ($LASTEXITCODE -ne 0) { throw '真实浏览器验收失败，查看 output/playwright/luminous-integration/failure.txt' }
} finally {
    try {
        # 成功与失败都清理本批次，保留其他开发数据和并行批次。
        & "$PSScriptRoot/clean-test-data.ps1" -RunMarker $runMarker
    } finally {
        $env:E2E_PLAYWRIGHT_MODULE = $previousModule
        $env:E2E_RUN_MARKER = $previousMarker
        Pop-Location
    }
}
