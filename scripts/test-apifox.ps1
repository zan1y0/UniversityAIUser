param([string]$BaseUrl = 'http://127.0.0.1:8080', [string]$LoginName = 'student', [string]$Password = 'LocalStudy2026!')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$toolsRoot = Join-Path $projectRoot '.tools/apifox'
$cliPath = Join-Path $toolsRoot 'node_modules/.bin/apifox.cmd'
if (-not (Test-Path -LiteralPath $cliPath)) {
    & npm.cmd install --prefix $toolsRoot 'apifox-cli@2.2.12' '--ignore-scripts'
    if ($LASTEXITCODE -ne 0) { throw 'Apifox CLI 安装失败' }
}
& $cliPath run 'apifox/backend-local.apifox-cli.json' '-r' 'cli,html,json,junit' '--out-dir' 'apifox-reports' '--delay-request' '250' '--timeout-request' '10000' '--on-error' 'end' '--env-var' "baseUrl=$BaseUrl" '--env-var' "loginName=$LoginName" '--env-var' "password=$Password" '--lang' 'zh'
if ($LASTEXITCODE -ne 0) { throw 'Apifox 验证未通过，请检查 apifox-reports 中的报告' }
