param(
    [switch]$RunAggregationBenchmark,
    [switch]$AggregationOnly,
    [ValidateRange(10,120)][int]$BenchmarkSeconds = 15,
    [string]$RedisServerPath = 'D:\Redis\redis-server.exe'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$backendRoot = Join-Path $repoRoot 'backend'
$runRoot = Join-Path $backendRoot ('target\issue-redis-' + [guid]::NewGuid().ToString('N'))
$previousPort = $env:VOTE_TEST_REDIS_PORT
$previousRedisServer = $env:VOTE_TEST_REDIS_SERVER
$redisProcess = $null
$portProbe = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)

if ($AggregationOnly -and -not $RunAggregationBenchmark) { throw '-AggregationOnly 必须与 -RunAggregationBenchmark 一起使用。' }

if (-not (Test-Path -LiteralPath $RedisServerPath -PathType Leaf)) {
    throw '找不到 Redis 服务程序，请通过 -RedisServerPath 指定本机路径。'
}

try {
    # 分配独立测试端口，只启动本次测试拥有的 Redis，不复用共享实例。
    $portProbe.Start()
    $testPort = $portProbe.LocalEndpoint.Port
    $portProbe.Stop()
    New-Item -ItemType Directory -Path $runRoot -Force | Out-Null
    [System.IO.File]::WriteAllText((Join-Path $runRoot 'redis-test.conf'),
        "bind 127.0.0.1`nport $testPort`nsave `"`"`nappendonly no`n",
        [System.Text.UTF8Encoding]::new($false))
    $redisProcess = Start-Process -FilePath $RedisServerPath -ArgumentList 'redis-test.conf' `
        -WorkingDirectory $runRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $runRoot 'redis.stdout.log') `
        -RedirectStandardError (Join-Path $runRoot 'redis.stderr.log')

    $ready = $false
    for ($attempt = 0; $attempt -lt 50; $attempt++) {
        $redisProcess.Refresh()
        if ($redisProcess.HasExited) {
            throw "测试 Redis 启动失败，请查看 $runRoot 中的日志。"
        }
        $client = [System.Net.Sockets.TcpClient]::new()
        try {
            $client.Connect('127.0.0.1', $testPort)
            $ready = $true
            break
        } catch {
            Start-Sleep -Milliseconds 200
        } finally {
            $client.Dispose()
        }
    }
    if (-not $ready) {
        throw '测试 Redis 在 10 秒内未就绪。'
    }

    # 所有单元测试及本次隔离集成验收；不运行依赖共享基础设施的其他集成测试。
    $testNames = (Get-ChildItem (Join-Path $backendRoot 'src\test\java') -Recurse -Filter '*Test.java' |
        Where-Object { $_.FullName -notmatch '\\integration\\' } |
        ForEach-Object { $_.BaseName }) -join ','
    $testNames += ',VoteStateIntegrationTest,HighConcurrencyVoteBenchmarkTest,SnowflakeNodeGuardRedisTest,ReliableInteractionIntegrationTest,VoteCrashRecoveryTest,FeedCacheCheckInIntegrationTest,SecurityStorageIntegrationTest,ListPaginationBatchIntegrationTest,VoteMySqlIsolationIntegrationTest'
    if ($RunAggregationBenchmark) { $testNames += ',VoteAggregationBenchmarkTest' }
    if ($AggregationOnly) { $testNames = 'VoteAggregationBenchmarkTest' }
    $env:VOTE_TEST_REDIS_PORT = [string]$testPort
    $env:VOTE_TEST_REDIS_SERVER = $RedisServerPath
    Push-Location $backendRoot
    try {
        $mavenArguments = @('-o','test',"-Dtest=$testNames",'-Dluminous.vote-test.isolated=true')
        if ($RunAggregationBenchmark) { $mavenArguments += @('-Dluminous.aggregation-benchmark.enabled=true',"-Dluminous.aggregation-benchmark.seconds=$BenchmarkSeconds") }
        & mvn @mavenArguments
        if ($LASTEXITCODE -ne 0) {
            throw "验收失败，Maven 退出码：$LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
} finally {
    $portProbe.Stop()
    $env:VOTE_TEST_REDIS_PORT = $previousPort
    $env:VOTE_TEST_REDIS_SERVER = $previousRedisServer
    if ($null -ne $redisProcess) {
        $redisProcess.Refresh()
        if (-not $redisProcess.HasExited) {
            # 仅停止由当前脚本创建的进程，禁止操作其他 Redis 或 Docker 服务。
            Stop-Process -Id $redisProcess.Id -ErrorAction SilentlyContinue
            $redisProcess.WaitForExit(5000) | Out-Null
        }
        $redisProcess.Dispose()
    }
}
