param([string]$MavenRepository = '')
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
if (Test-Path -LiteralPath 'C:\Program Files\Java\jdk-17') {
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
}
$mavenArgs = @('test', '-Dmysql.integration=true', '-Dtest=MybatisMysqlIntegrationTests,MybatisMysqlApplicationTests,RbacMysqlIntegrationTests')
if ($MavenRepository) { $mavenArgs += "-Dmaven.repo.local=$MavenRepository" }
& mvn.cmd @mavenArgs
if ($LASTEXITCODE -ne 0) { throw 'MyBatis/MySQL验证失败，请检查 target/surefire-reports' }
