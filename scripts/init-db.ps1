param (
    [string]$EnvFile = ".env.dev"
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "[DATABASE] Luminous Database Readiness Check" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

if (Test-Path $EnvFile) {
    Get-Content $EnvFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#")) {
            $parts = $line.Split("=", 2)
            if ($parts.Length -eq 2) {
                [System.Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), "Process")
            }
        }
    }
}

$containerName = if ($env:COMPOSE_PROJECT_NAME) { "$($env:COMPOSE_PROJECT_NAME)-mysql" } else { "luminous-dev-mysql" }
$dbUser = if ($env:DB_USER) { $env:DB_USER } else { "luminous" }
$dbPassword = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { "luminous_password" }
$dbName = if ($env:DB_NAME) { $env:DB_NAME } else { "luminous" }

Write-Host "[CHECK] Checking database container health: $containerName ..." -ForegroundColor Yellow

$retries = 30
$ready = $false

for ($i = 1; $i -le $retries; $i++) {
    $health = docker inspect --format="{{json .State.Health.Status}}" $containerName 2>$null
    if ($health -match "healthy") {
        $ready = $true
        break
    }
    Write-Host "[WAIT] Waiting for MySQL container to become healthy ($i/$retries)..." -ForegroundColor Gray
    Start-Sleep -Seconds 2
}

if ($ready) {
    Write-Host "[SUCCESS] MySQL container is healthy!" -ForegroundColor Green
    
    # Check if core tables exist
    $tableCheckCmd = "mysql -u$dbUser -p$dbPassword -e `"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$dbName' AND table_name IN ('users', 'posts', 'notifications');`""
    $tableOutput = docker exec -i $containerName sh -c $tableCheckCmd 2>$null
    if ($tableOutput -match "3") {
        Write-Host "[SUCCESS] Core tables (users, posts, notifications) confirmed ready!" -ForegroundColor Green
    } else {
        Write-Host "[INFO] Core tables will be auto-migrated by Flyway on Spring Boot startup." -ForegroundColor Gray
    }
} else {
    Write-Host "[WARN] MySQL container did not become healthy in time. Check: docker logs $containerName" -ForegroundColor Red
}
