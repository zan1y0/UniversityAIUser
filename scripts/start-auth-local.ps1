param([int]$Port = 8080)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
if (Test-Path -LiteralPath 'C:\Program Files\Java\jdk-17') {
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
}
& mvn.cmd package '-DskipTests'
if ($LASTEXITCODE -ne 0) { throw '后端构建失败' }
# 注册会自动创建用户和档案；首次启动不依赖内置演示账号或演示密码。
& java.exe '-Dfile.encoding=UTF-8' -jar 'target/kaoyan-ai-server-0.0.1-SNAPSHOT.jar' '--spring.profiles.active=local' '--app.seed-local=false' "--server.port=$Port"
