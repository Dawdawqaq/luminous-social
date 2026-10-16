$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/dev-docker.ps1"
$dockerCli = Resolve-LuminousDocker
$brokerContainer = 'luminous-dev-rocketmq-broker'
foreach ($mqContainer in @($brokerContainer,'luminous-dev-rocketmq-namesrv')) {
    $projectLabel = & $dockerCli inspect $mqContainer --format '{{index .Config.Labels "com.docker.compose.project"}}'
    if ($LASTEXITCODE -ne 0 -or $projectLabel.Trim() -ne 'luminous-dev') { throw '主题初始化只允许独立开发消息队列' }
}
function Invoke-DevMqAdmin([string[]]$AdminArguments) {
    $adminOutput = & $dockerCli exec $brokerContainer sh /home/rocketmq/rocketmq-5.3.1/bin/mqadmin @AdminArguments 2>&1
    $adminText = $adminOutput | Out-String
    if ($LASTEXITCODE -ne 0 -or $adminText -match 'SubCommandException|Caused by:') { throw $adminText }
    return $adminText
}
$registered = $false
for ($mqAttempt = 0; $mqAttempt -lt 8; $mqAttempt++) {
    try {
        $clusterState = Invoke-DevMqAdmin @('clusterList','-n','namesrv:9876')
        $registered = $clusterState -match 'broker-a\s+0\s+(?!127\.0\.0\.1:)(\d{1,3}\.){3}\d{1,3}:10911'
    } catch { $registered = $false }
    if ($registered) { break }
    Start-Sleep -Seconds 1
}
if (-not $registered) { throw '开发 Broker 未以容器网络地址注册，检查 broker.dev.conf 和 Broker 日志' }
# 显式初始化应用主题，避免用通知降级成功代替真实 MQ 验收。
foreach ($devTopic in @('luminous-notifications','luminous-votes-topic')) {
    $topicResult = Invoke-DevMqAdmin @('updateTopic','-n','namesrv:9876','-c','DefaultCluster','-t',$devTopic,'-r','4','-w','4')
    if ($topicResult -notmatch 'success') { throw "开发主题初始化未确认：$devTopic" }
    Write-Host "开发消息主题已就绪：$devTopic"
}
