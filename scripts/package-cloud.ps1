param([switch]$Offline)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$releaseId = Get-Date -Format 'yyyyMMdd-HHmmss'
$packageRoot = Join-Path $repoRoot ".local\cloud\luminous-cloud-$releaseId"
$archivePath = "$packageRoot.tar.gz"

function Invoke-CheckedCommand {
    param([string]$Program, [string[]]$CommandArguments)
    & $Program @CommandArguments
    if ($LASTEXITCODE -ne 0) { throw "$Program 执行失败，退出码 $LASTEXITCODE。" }
}

# 构建在开发机器完成，云服务器只运行产物和运行时镜像。
Push-Location $repoRoot
try {
    # 先清理旧资源，避免已从源码移除的迁移脚本残留在发布 JAR。
    $mavenArguments = @('-f', 'backend/pom.xml', '-DskipTests', 'clean', 'package')
    if ($Offline) { $mavenArguments = @('-o') + $mavenArguments }
    Invoke-CheckedCommand 'mvn' $mavenArguments
    Invoke-CheckedCommand 'npm.cmd' @('--prefix', 'frontend', 'run', 'build')

    if (Test-Path -LiteralPath $packageRoot) { throw '部署包目录已存在，请稍后重新运行。' }
    New-Item -ItemType Directory -Path $packageRoot | Out-Null
    Copy-Item -LiteralPath (Join-Path $repoRoot 'deploy/cloud/compose.yml') -Destination $packageRoot
    Copy-Item -LiteralPath (Join-Path $repoRoot 'deploy/cloud/broker.conf') -Destination $packageRoot
    Copy-Item -LiteralPath (Join-Path $repoRoot 'deploy/cloud/init-env.sh') -Destination $packageRoot
    Copy-Item -LiteralPath (Join-Path $repoRoot 'deploy/cloud/.env.example') -Destination $packageRoot
    Copy-Item -LiteralPath (Join-Path $repoRoot 'deploy/cloud/nginx.conf') -Destination $packageRoot
    Copy-Item -LiteralPath (Join-Path $repoRoot 'deploy/cloud/nginx-log.conf') -Destination $packageRoot
    Copy-Item -LiteralPath (Join-Path $repoRoot 'deploy/cloud/README.md') -Destination $packageRoot
    New-Item -ItemType Directory -Path (Join-Path $packageRoot 'backend') | Out-Null
    Copy-Item -LiteralPath (Join-Path $repoRoot 'deploy/cloud/backend/Dockerfile') -Destination (Join-Path $packageRoot 'backend/Dockerfile')
    Copy-Item -LiteralPath (Join-Path $repoRoot 'backend/target/luminous-api-0.0.1-SNAPSHOT.jar') -Destination (Join-Path $packageRoot 'backend/app.jar')
    Copy-Item -LiteralPath (Join-Path $repoRoot 'frontend/dist') -Destination (Join-Path $packageRoot 'frontend') -Recurse

    $commit = (& git rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw '无法读取构建提交。' }
    $dirty = @(& git status --porcelain).Count -gt 0
    $manifest = [ordered]@{
        releaseId = $releaseId
        commit = $commit
        hasUncommittedChanges = $dirty
        backendSha256 = (Get-FileHash (Join-Path $packageRoot 'backend/app.jar') -Algorithm SHA256).Hash
        createdAt = (Get-Date).ToString('o')
    } | ConvertTo-Json
    [System.IO.File]::WriteAllText((Join-Path $packageRoot 'manifest.json'), $manifest, [System.Text.UTF8Encoding]::new($false))
    Invoke-CheckedCommand 'tar.exe' @('-czf', $archivePath, '-C', $packageRoot, '.')
    Get-FileHash -LiteralPath $archivePath -Algorithm SHA256
    Write-Host "部署包：$archivePath"
    Write-Host '部署包不包含任何本地真实环境文件或云服务器密码。'
} finally {
    Pop-Location
}
