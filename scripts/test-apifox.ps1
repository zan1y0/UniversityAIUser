param([string]$BaseUrl = 'http://127.0.0.1:8080', [string]$LoginName = 'student', [string]$Password = $env:LOCAL_STUDENT_PASSWORD, [ValidateSet('business', 'auth')][string]$Suite = 'business')
$ErrorActionPreference = 'Stop'
if ($Suite -eq 'business' -and [string]::IsNullOrWhiteSpace($Password)) {
    throw '请设置 LOCAL_STUDENT_PASSWORD 环境变量，或通过 -Password 传入现有账号的密码'
}
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$toolsRoot = Join-Path $projectRoot '.tools/apifox'
$cliPath = Join-Path $toolsRoot 'node_modules/.bin/apifox.cmd'
if (-not (Test-Path -LiteralPath $cliPath)) {
    & npm.cmd install --prefix $toolsRoot --cache (Join-Path $projectRoot '.tools/npm-cache') 'apifox-cli@2.2.12' '--ignore-scripts'
    if ($LASTEXITCODE -ne 0) { throw 'Apifox CLI 安装失败' }
}
$collection = if ($Suite -eq 'auth') { 'apifox/auth-jwt.apifox-cli.json' } else { 'apifox/backend-local.apifox-cli.json' }
$cliArguments = @('run', $collection, '-r', 'cli,html,json,junit', '--out-dir', 'apifox-reports', '--delay-request', '250', '--timeout-request', '10000', '--on-error', 'end', '--env-var', "baseUrl=$BaseUrl", '--lang', 'zh')
if ($Suite -eq 'business') { $cliArguments += @('--env-var', "loginName=$LoginName", '--env-var', "password=$Password") }
& $cliPath @cliArguments
if ($LASTEXITCODE -ne 0) { throw 'Apifox 验证未通过，请检查 apifox-reports 中的报告' }
