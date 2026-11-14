# SMTP self-test wrapper -> scripts/SmtpProbe.java (Java/JSSE, same TLS stack as the app).
#
# Why Java and not PowerShell: on this machine Windows PowerShell 5.1's SslStream
# fails the handshake with "No credentials are available in the security package"
# (Schannel), while Java/JSSE connects fine. Testing with the JVM reproduces what
# the backend actually does, so a PowerShell-native SMTP test would be misleading.
#
# Usage:
#   ./scripts/test-smtp.ps1 -SmtpHost smtp.qq.com -Port 587 -User 1069168265@qq.com
#   ./scripts/test-smtp.ps1 -SmtpHost smtp.qq.com -Port 465 -User you@qq.com -To someone@163.com
# Omit -Password to be prompted (keeps the authorization code out of your shell history).
param(
    [string]$SmtpHost = 'smtp.qq.com',
    [int]$Port = 587,
    [Parameter(Mandatory = $true)][string]$User,
    [string]$To = '',
    [string]$From = '',
    [string]$Password = ''
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

$java = $null
foreach ($candidate in @(
        (Join-Path $env:JAVA_HOME 'bin\java.exe'),
        'C:\Program Files\Java\jdk-17\bin\java.exe',
        'C:\Program Files\Java\jdk-21\bin\java.exe')) {
    if ($candidate -and (Test-Path -LiteralPath $candidate)) { $java = $candidate; break }
}
if (-not $java) {
    $cmd = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($cmd) { $java = $cmd.Source }
}
if (-not $java) { throw 'java.exe not found; set JAVA_HOME or run scripts/SmtpProbe.java manually' }

$arguments = @((Join-Path $root 'scripts\SmtpProbe.java'), '-host', $SmtpHost, '-port', "$Port", '-user', $User)
if ($To) { $arguments += @('-to', $To) }
if ($From) { $arguments += @('-from', $From) }
if ($Password) { $arguments += @('-pass', $Password) }

Write-Host ("running: {0} {1}" -f $java, ($arguments -join ' ')) -ForegroundColor DarkGray
# JVM writes UTF-8; without this the Chinese output turns into mojibake in a CP936 console.
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }
& $java @arguments
exit $LASTEXITCODE
