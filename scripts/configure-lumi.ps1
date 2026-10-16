param([Parameter(Mandatory=$true)][string]$ApiKeyFile)
$ErrorActionPreference='Stop'
$lumiRoot=Split-Path -Parent $PSScriptRoot
Push-Location $lumiRoot
try {
    & git check-ignore -q .env.lumi.local
    if($LASTEXITCODE -ne 0){throw 'Lumi 本地配置未被版本库忽略，停止写入'}
    $secretText=Get-Content -LiteralPath $ApiKeyFile -Raw
    $matchesFound=@([regex]::Matches($secretText,'(?<![A-Za-z0-9])sk-[A-Za-z0-9_-]{16,}') | ForEach-Object {$_.Value} | Select-Object -Unique)
    if($matchesFound.Count -ne 1){throw '密钥文件应只包含一个可识别的 DeepSeek 密钥'}
    $settings=[ordered]@{}
    $localFile=Join-Path $lumiRoot '.env.lumi.local'
    if(Test-Path -LiteralPath $localFile){Get-Content -LiteralPath $localFile | ForEach-Object {if($_ -match '^([A-Z_]+)=(.*)$'){$settings[$matches[1]]=$matches[2]}}}
    $settings['LUMI_API_KEY']=$matchesFound[0]
    $settings['LUMI_ENABLED']='true'
    if(-not $settings.Contains('LUMI_MODEL')){$settings['LUMI_MODEL']='deepseek-flash'}
    if(-not $settings.Contains('LUMI_DAILY_BUDGET_MICROS')){$settings['LUMI_DAILY_BUDGET_MICROS']='100000'}
    $lines=@('# 仅供后端容器读取，不提交版本库。金额单位为百万分之一美元。')
    $lines+=@($settings.GetEnumerator() | ForEach-Object {$_.Key+'='+$_.Value})
    [System.IO.File]::WriteAllLines($localFile,$lines,[System.Text.UTF8Encoding]::new($false))
    Write-Host 'Lumi 本地配置已写入，密钥未输出；当前默认每日应用预算上限为0.10美元。'
} finally {Pop-Location}
