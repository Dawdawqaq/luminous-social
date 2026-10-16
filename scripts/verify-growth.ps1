$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot/dev-docker.ps1"
$growthDocker = Resolve-LuminousDocker
foreach ($service in @('mysql', 'redis', 'backend', 'minio')) {
    $label = & $growthDocker inspect "luminous-dev-$service" --format '{{index .Config.Labels "com.docker.compose.project"}}'
    if ($LASTEXITCODE -ne 0 -or $label.Trim() -ne 'luminous-dev') { throw '成长验收只允许 luminous-dev 容器' }
}
$settings = @{}
Get-Content -LiteralPath (Join-Path $repoRoot '.env.dev') | ForEach-Object {
    if ($_ -match '^\s*([A-Z_]+)\s*=\s*(.*?)\s*$') { $settings[$matches[1]] = $matches[2].Trim('"', "'") }
}
if ($settings['COMPOSE_PROJECT_NAME'] -ne 'luminous-dev' -or $settings['MYSQL_PORT'] -ne '33067' -or $settings['REDIS_PORT'] -ne '63797') {
    throw '成长测试要求独立开发项目及固定 MySQL 33067、Redis 63797 端口'
}
$names = @('GROWTH_TEST_DB_USER', 'GROWTH_TEST_DB_PASSWORD', 'GROWTH_TEST_DB_NAME', 'RUN_GROWTH_MYSQL_TESTS', 'GROWTH_TEST_MARKER')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$runMarker = '[E2E_TEST] growth-' + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$env:GROWTH_TEST_DB_USER = $settings['DB_USER']
$env:GROWTH_TEST_DB_PASSWORD = $settings['DB_PASSWORD']
$env:GROWTH_TEST_DB_NAME = $settings['DB_NAME']
$env:RUN_GROWTH_MYSQL_TESTS = 'true'
$env:GROWTH_TEST_MARKER = $runMarker
Push-Location $repoRoot
try {
    mvn -o -f backend/pom.xml '-Dtest=GrowthMySqlIntegrationTest' test
    if ($LASTEXITCODE -ne 0) { throw '成长 MySQL 集成测试失败' }
} finally {
    try { & "$PSScriptRoot/clean-test-data.ps1" -RunMarker $runMarker }
    finally {
        foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
        Pop-Location
    }
}
