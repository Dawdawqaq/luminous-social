Write-Host "==========================================================" -ForegroundColor Blue
Write-Host "Starting Luminous Production Simulation Lab..." -ForegroundColor Blue
Write-Host "==========================================================" -ForegroundColor Blue

$rootPath = Split-Path -Parent $PSScriptRoot
Set-Location $rootPath

# Port 80 pre-flight check
$port80Occupied = Get-NetTCPConnection -LocalPort 80 -ErrorAction SilentlyContinue | Where-Object { $_.State -eq 'Listen' }
if ($port80Occupied) {
    Write-Host "[WARN] Host port 80 is occupied by another process (e.g. IIS / HTTP.sys)!" -ForegroundColor Yellow
    Write-Host "[WARN] If Docker fails to bind, set PROD_HTTP_PORT=8088 in .env.prod" -ForegroundColor Yellow
}

docker compose -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.prod up -d --build
docker compose -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.prod restart nginx-lb

Write-Host "==========================================================" -ForegroundColor Green
Write-Host "Production Simulation Lab is online!" -ForegroundColor Green
Write-Host "Entry Gateway (Nginx): http://localhost" -ForegroundColor Green
Write-Host "API Cluster Gateway: http://localhost/api/" -ForegroundColor Green
Write-Host "Topology: Nginx:80 -> backend-1 and backend-2 (least_conn) -> MySQL / Redis / MQ" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
