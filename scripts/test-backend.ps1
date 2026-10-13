$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
if (Test-Path -LiteralPath 'C:\Program Files\Java\jdk-17') {
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
}
& mvn.cmd test
if ($LASTEXITCODE -ne 0) { throw '后端测试失败，请检查 target/surefire-reports' }
