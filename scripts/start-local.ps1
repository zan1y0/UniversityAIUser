param([int]$Port = 8080)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
if (Test-Path -LiteralPath 'C:\Program Files\Java\jdk-17') {
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
}
& mvn.cmd package '-DskipTests'
if ($LASTEXITCODE -ne 0) { throw '后端构建失败' }
& java.exe '-Dfile.encoding=UTF-8' -jar 'target/kaoyan-ai-server-0.0.1-SNAPSHOT.jar' '--spring.profiles.active=local' "--server.port=$Port"
