$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$dataDirectory = Join-Path $projectRoot '.local-data'
$configPath = Join-Path $dataDirectory 'auth-local.properties'
New-Item -ItemType Directory -Path $dataDirectory -Force | Out-Null
if (Test-Path -LiteralPath $configPath) {
    Write-Output '本地JWT配置已存在，保持原密钥。'
    exit 0
}
$keyBytes = New-Object byte[] 32
$random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $random.GetBytes($keyBytes) } finally { $random.Dispose() }
$encodedKey = [Convert]::ToBase64String($keyBytes)
$content = "# Local development only. Do not commit this file.`nAPP_JWT_SECRET=$encodedKey`n"
# CreateNew 避免同时初始化时覆盖另一进程刚生成的密钥。
$stream = [IO.File]::Open($configPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
try {
    $bytes = [Text.Encoding]::UTF8.GetBytes($content)
    $stream.Write($bytes, 0, $bytes.Length)
} finally { $stream.Dispose() }
Write-Output '本地JWT配置已生成。IDEA的MySQL运行配置会自动读取，无需手填密钥。'
