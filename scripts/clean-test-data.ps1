param (
    [string]$EnvFile = ".env.dev"
)

Write-Host "Cleaning [E2E_TEST] and benchmark artifacts..." -ForegroundColor Cyan

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

$mysqlContainer = if ($env:COMPOSE_PROJECT_NAME) { "$($env:COMPOSE_PROJECT_NAME)-mysql" } else { "luminous-dev-mysql" }
$redisContainer = if ($env:COMPOSE_PROJECT_NAME) { "$($env:COMPOSE_PROJECT_NAME)-redis" } else { "luminous-dev-redis" }
$dbName = if ($env:DB_NAME) { $env:DB_NAME } else { "luminous" }
$dbPassword = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { "luminous_password" }
$dbUser = if ($env:DB_USER) { $env:DB_USER } else { "luminous" }

$cleanSql = "USE $dbName; DELETE FROM notifications WHERE receiver_id IN (SELECT id FROM users WHERE username LIKE 'e2e_%' OR username LIKE 'bench_%') OR actor_id IN (SELECT id FROM users WHERE username LIKE 'e2e_%' OR username LIKE 'bench_%') OR content_preview LIKE '%[E2E_TEST]%' OR content_preview LIKE '%[BENCHMARK]%'; DELETE FROM user_follows WHERE follower_id IN (SELECT id FROM users WHERE username LIKE 'e2e_%' OR username LIKE 'bench_%') OR following_id IN (SELECT id FROM users WHERE username LIKE 'e2e_%' OR username LIKE 'bench_%'); DELETE FROM comments WHERE content LIKE '%[E2E_TEST]%' OR content LIKE '%[BENCHMARK]%'; DELETE FROM post_votes WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'e2e_%' OR username LIKE 'bench_%'); DELETE FROM bookmarks WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'e2e_%' OR username LIKE 'bench_%'); DELETE FROM posts WHERE title LIKE '%[E2E_TEST]%' OR title LIKE '%[BENCHMARK]%'; DELETE FROM communities WHERE name LIKE 'e2e-%' OR name LIKE 'benchmark-%'; DELETE FROM users WHERE username LIKE 'e2e_%' OR username LIKE 'bench_%';"

Write-Host "1. Executing precision SQL cleanup (including notifications and user_follows)..." -ForegroundColor Yellow
$mysqlCmd = "mysql -u$dbUser -p$dbPassword -e `"$cleanSql`""
docker exec -i $mysqlContainer sh -c $mysqlCmd 2>$null

Write-Host "2. Cleaning Redis test keys in luminous namespace..." -ForegroundColor Yellow
$redisScript = "for key in `$(redis-cli --scan --pattern 'luminous:*e2e*'); do redis-cli del `"`$key`"; done; for key in `$(redis-cli --scan --pattern 'luminous:*bench*'); do redis-cli del `"`$key`"; done; for key in `$(redis-cli --scan --pattern 'luminous:ratelimit:*'); do redis-cli del `"`$key`"; done"
docker exec -i $redisContainer sh -c $redisScript 2>$null

Write-Host "Cleanup completed successfully!" -ForegroundColor Green
