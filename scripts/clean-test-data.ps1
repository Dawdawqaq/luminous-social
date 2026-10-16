param ([string]$EnvFile = '.env.dev', [string]$RunMarker = '')
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot/dev-docker.ps1"
$dockerCli = Resolve-LuminousDocker
if ($RunMarker -and $RunMarker -notmatch '^\[E2E_TEST\] [a-zA-Z0-9_-]+$') { throw '测试批次标记格式无效' }
# 只允许独立开发编排，不把任意环境文件变量注入当前进程。
$devSettings = @{}
Get-Content -LiteralPath (Join-Path $repoRoot $EnvFile) | ForEach-Object {
    if ($_ -match '^\s*([A-Z_]+)\s*=\s*(.*?)\s*$') { $devSettings[$matches[1]] = $matches[2].Trim('"', "'") }
}
if ($devSettings['COMPOSE_PROJECT_NAME'] -ne 'luminous-dev') { throw '清理只允许 luminous-dev，禁止生产和共享基础设施' }
foreach ($devService in @('mysql','redis','backend','minio')) {
    $projectLabel = & $dockerCli inspect "luminous-dev-$devService" --format '{{index .Config.Labels "com.docker.compose.project"}}'
    if ($LASTEXITCODE -ne 0 -or $projectLabel.Trim() -ne 'luminous-dev') { throw "开发容器归属核对失败：$devService" }
}
function Invoke-DevSql([string]$Sql) {
    $sqlOutput = $Sql | & $dockerCli exec -i luminous-dev-mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -B -u"$MYSQL_USER" "$MYSQL_DATABASE"'
    if ($LASTEXITCODE -ne 0) { throw '测试 SQL 清理失败' }
    return $sqlOutput
}
$marker = if ($RunMarker) { $RunMarker } else { '[E2E_TEST]' }
$markerSetup = "SET @cleanup_marker = '$marker'; SET @include_benchmark = " + $(if ($RunMarker) { '0' } else { '1' }) + ';'
$selectorSql = Get-Content -Raw -LiteralPath "$PSScriptRoot/test-data-scope.sql"
$scopeRows = @(Invoke-DevSql ($markerSetup + $selectorSql))
$bucket = $devSettings['MINIO_BUCKET']
if (-not $bucket) { $bucket = 'luminous-uploads' }
if ($bucket -notmatch '^[a-z0-9][a-z0-9.-]{1,62}$') { throw '测试存储桶名称无效' }
# 先删除显式标记用户的上传对象，再处理受外键约束的元数据。
foreach ($scopeRow in $scopeRows) {
    $columns = $scopeRow -split "`t"
    if ($columns[0] -ne 'FILE') { continue }
    $objectKey = $columns[2]
    if ($objectKey -notmatch '^[a-zA-Z0-9._-]+$') { throw '测试文件键无效' }
    if ($columns[1] -eq 'minio') {
        $removeObject = 'export MC_HOST_e2e="http://$MINIO_ROOT_USER:$MINIO_ROOT_PASSWORD@localhost:9000"; mc rm "e2e/$1/$2"'
        & $dockerCli exec luminous-dev-minio sh -c $removeObject -- $bucket $objectKey | Out-Null
    } elseif ($columns[1] -eq 'local') {
        & $dockerCli exec luminous-dev-backend sh -c 'rm -f -- "/app/uploads/$1"' -- $objectKey
    } else { throw '测试文件存储类型不受支持' }
    if ($LASTEXITCODE -ne 0) { throw '测试上传对象删除失败' }
}
$cleanupSql = Get-Content -Raw -LiteralPath "$PSScriptRoot/test-data-cleanup.sql"
Invoke-DevSql ($markerSetup + $selectorSql + $cleanupSql) | Out-Null
# 按本次实体标识清理命名空间缓存，保留其他开发用户的限流和数据。
$entityIds = @($scopeRows | ForEach-Object {
    $columns = $_ -split "`t"
    if ($columns[0] -in @('USER','POST','COMMUNITY')) {
        if ($columns[1] -notmatch '^[a-zA-Z0-9_-]+$') { throw '测试实体标识无效' }
        $columns[1]
    }
})
if ($entityIds.Count) {
    # 批量扫描一次，再按本批实体筛选，避免大样本验收反复遍历整个命名空间。
    $entityPattern = ($entityIds | ForEach-Object { [regex]::Escape($_) }) -join '|'
    $entityMatcher = [regex]::new($entityPattern, [System.Text.RegularExpressions.RegexOptions]::CultureInvariant)
    $namespaceKeys = @(& $dockerCli exec luminous-dev-redis redis-cli --scan --pattern 'luminous:*')
    if ($LASTEXITCODE -ne 0) { throw '测试 Redis 扫描失败' }
    $deleteKeys = @($namespaceKeys | Where-Object { $entityMatcher.IsMatch($_) })
    for ($index=0; $index -lt $deleteKeys.Count; $index+=64) {
        $last=[Math]::Min($index+63,$deleteKeys.Count-1)
        & $dockerCli exec luminous-dev-redis redis-cli UNLINK @($deleteKeys[$index..$last]) | Out-Null
        if ($LASTEXITCODE -ne 0) { throw '测试 Redis 清理失败' }
    }
}
# 榜单快照只存编号，按本批用户、社区及帖子识别派生缓存，不清空整个 Redis。
$rankingUsers = [System.Collections.Generic.HashSet[string]]::new()
$rankingPosts = [System.Collections.Generic.HashSet[string]]::new()
$rankingCommunities = [System.Collections.Generic.HashSet[string]]::new()
foreach ($scopeRow in $scopeRows) {
    $scopeColumns = $scopeRow -split "`t"
    if ($scopeColumns[0] -eq 'USER') { [void]$rankingUsers.Add($scopeColumns[1]) }
    if ($scopeColumns[0] -eq 'POST') { [void]$rankingPosts.Add($scopeColumns[1]) }
    if ($scopeColumns[0] -eq 'COMMUNITY') { [void]$rankingCommunities.Add($scopeColumns[1]) }
}
$rankingKeys = @(& $dockerCli exec luminous-dev-redis redis-cli --scan --pattern 'luminous:post-ranking:v1:*')
if ($LASTEXITCODE -ne 0) { throw '榜单测试缓存扫描失败' }
foreach ($rankingKey in $rankingKeys) {
    if ($rankingKey -notmatch '^luminous:post-ranking:v1:(candidates:(all|[0-9]+)|snapshot:[a-f0-9]{32})$') { continue }
    $rankingJson = & $dockerCli exec luminous-dev-redis redis-cli --raw GET $rankingKey
    if ($LASTEXITCODE -ne 0) { throw '榜单测试缓存读取失败' }
    if (-not $rankingJson) { continue }
    try { $rankingEntry = ($rankingJson | Out-String) | ConvertFrom-Json } catch { continue }
    $removeRanking = $rankingUsers.Contains([string]$rankingEntry.viewerId) -or $rankingCommunities.Contains([string]$rankingEntry.community)
    if ($rankingKey -like '*:candidates:*') {
        $removeRanking = @($rankingEntry.candidates | Where-Object { $rankingPosts.Contains([string]$_.id) }).Count -gt 0
    }
    if ($removeRanking) {
        & $dockerCli exec luminous-dev-redis redis-cli UNLINK $rankingKey | Out-Null
        if ($LASTEXITCODE -ne 0) { throw '榜单测试缓存清理失败' }
    }
}
# 搜索快照不含正文，按本批编号交集清理，其他查询快照继续保留。
$searchCommunities = [System.Collections.Generic.HashSet[string]]::new()
foreach ($scopeRow in $scopeRows) { $columns=$scopeRow -split "`t"; if ($columns[0] -eq 'COMMUNITY_ID') { [void]$searchCommunities.Add($columns[1]) } }
$searchKeys = @(& $dockerCli exec luminous-dev-redis redis-cli --scan --pattern 'luminous:search:v1:snapshot:*')
if ($LASTEXITCODE -ne 0) { throw '搜索快照扫描失败' }
foreach ($searchKey in $searchKeys) {
    if ($searchKey -notmatch '^luminous:search:v1:snapshot:[a-f0-9]{32}$') { continue }
    $searchJson = & $dockerCli exec luminous-dev-redis redis-cli --raw GET $searchKey
    if ($LASTEXITCODE -ne 0) { throw '搜索快照读取失败' }
    if (-not $searchJson) { continue }
    try { $entry=($searchJson | Out-String) | ConvertFrom-Json } catch { continue }
    $ids = $null; switch ($entry.kind) { 'posts' { $ids=$rankingPosts } 'users' { $ids=$rankingUsers } 'communities' { $ids=$searchCommunities } }
    if ($null -ne $ids -and @($entry.ids | Where-Object { $ids.Contains([string]$_) }).Count -gt 0) {
        & $dockerCli exec luminous-dev-redis redis-cli UNLINK $searchKey | Out-Null
        if ($LASTEXITCODE -ne 0) { throw '搜索快照清理失败' }
    }
}
$remaining = @(Invoke-DevSql ($markerSetup + $selectorSql))
if ($remaining.Count) { throw '仍存在带标记测试实体，清理未完成' }
Write-Host '独立开发环境测试用户、社区、内容、上传对象及对应缓存已清理。' -ForegroundColor Green
