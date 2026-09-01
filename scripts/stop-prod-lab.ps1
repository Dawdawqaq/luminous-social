Write-Host "==========================================================" -ForegroundColor Yellow
Write-Host "Stopping Luminous Production Simulation Lab..." -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Yellow

$rootPath = Split-Path -Parent $PSScriptRoot
Set-Location $rootPath

docker compose -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.prod stop

Write-Host "==========================================================" -ForegroundColor Green
Write-Host "Production Simulation Lab containers safely stopped." -ForegroundColor Green
Write-Host "Persistent data volumes are preserved." -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
