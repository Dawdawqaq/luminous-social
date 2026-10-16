function Resolve-LuminousDocker {
    $dockerCommand = Get-Command docker.exe -ErrorAction SilentlyContinue
    if ($dockerCommand) { return $dockerCommand.Source }
    # 支持 Docker Desktop 的用户目录安装，不修改用户或机器 PATH。
    $dockerCandidates = @(
        (Join-Path $env:LOCALAPPDATA 'Programs/DockerDesktop/resources/bin/docker.exe'),
        'C:/Program Files/Docker/Docker/resources/bin/docker.exe'
    )
    foreach ($dockerCandidate in $dockerCandidates) {
        if (Test-Path -LiteralPath $dockerCandidate) { return $dockerCandidate }
    }
    throw '未找到 Docker CLI，请启动 Docker Desktop 并确认安装位置'
}
