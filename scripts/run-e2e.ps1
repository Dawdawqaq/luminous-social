param (
    [string]$TargetApi = "http://localhost:8080/api"
)

$ErrorActionPreference = 'Stop'

Write-Host "Executing Luminous E2E test lifecycle against $TargetApi..." -ForegroundColor Cyan
$env:E2E_TARGET_API = $TargetApi
$rootPath = Split-Path -Parent $PSScriptRoot

try {
    node "$rootPath/scripts/run_e2e_isolated.mjs"
    if ($LASTEXITCODE -ne 0) { throw '真实 API 验收失败' }
} finally {
    # 成功和失败时都清理，保留测试失败结果。
    & "$PSScriptRoot/clean-test-data.ps1"
}
