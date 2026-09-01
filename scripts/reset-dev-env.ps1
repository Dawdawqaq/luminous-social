param (
    [switch]$NoBuild = $false
)
# scripts/reset-dev-env.ps1

Write-Host "==========================================================" -ForegroundColor Magenta
Write-Host "[RESET] Resetting Luminous Dev/Test Sandbox..." -ForegroundColor Magenta
Write-Host "==========================================================" -ForegroundColor Magenta

$rootPath = Split-Path -Parent $PSScriptRoot
Set-Location $rootPath

Write-Host "1. Teardown old dev containers and volumes..." -ForegroundColor Yellow
docker compose -f docker-compose.yml --env-file .env.dev down -v

Write-Host "2. Starting fresh dev/test infrastructure and services..." -ForegroundColor Yellow
if ($NoBuild) {
    docker compose -f docker-compose.yml --env-file .env.dev up -d
} else {
    docker compose -f docker-compose.yml --env-file .env.dev up -d --build
}

Write-Host "3. Waiting for database and cache to be healthy..." -ForegroundColor Yellow
& "$PSScriptRoot\init-db.ps1" -EnvFile ".env.dev"

Write-Host "==========================================================" -ForegroundColor Green
Write-Host "[SUCCESS] Dev/Test sandbox is fully online!" -ForegroundColor Green
Write-Host "[URL] Frontend URL: http://localhost:3000" -ForegroundColor Green
Write-Host "[URL] Backend API URL: http://localhost:8080/api" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
