param (
    [string]$TargetApi = "http://localhost:8080/api"
)

Write-Host "Executing Luminous E2E test lifecycle against $TargetApi..." -ForegroundColor Cyan
$env:E2E_TARGET_API = $TargetApi
$rootPath = Split-Path -Parent $PSScriptRoot

node "$rootPath/scripts/run_e2e_isolated.mjs"

Write-Host "Executing precision test data cleanup..." -ForegroundColor Yellow
$cleanScript = "$PSScriptRoot/clean-test-data.ps1"
& $cleanScript
