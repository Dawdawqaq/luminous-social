$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $repoRoot 'frontend')
try {
    # 使用仓库已安装的 TypeScript 编译器，不下载依赖。
    & node node_modules/typescript/bin/tsc src/api/operationCoordinator.ts src/notification/UnreadCounter.ts --rootDir src --target ES2022 --module ES2022 --lib ES2022,DOM,DOM.Iterable --skipLibCheck --outDir ../backend/target/client-tests
    if ($LASTEXITCODE -ne 0) { throw '客户端幂等模块编译失败' }
    [System.IO.File]::WriteAllText((Join-Path (Resolve-Path ../backend/target/client-tests) 'package.json'), '{"type":"module"}')
    & node --test (Join-Path $repoRoot 'scripts/client-idempotency.test.mjs') (Join-Path $repoRoot 'scripts/notification-unread.test.mjs')
    if ($LASTEXITCODE -ne 0) { throw '客户端幂等测试失败' }
    & node --test (Join-Path $repoRoot 'scripts/test-api-auth.test.mjs')
    if ($LASTEXITCODE -ne 0) { throw '测试认证夹具验证失败' }
} finally { Pop-Location }
