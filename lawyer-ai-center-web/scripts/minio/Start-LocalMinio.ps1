param([string]$InstallDirectory = 'D:\Program Files\minio')
$ErrorActionPreference = 'Stop'
$server = Join-Path $InstallDirectory 'server\minio.exe'
$runtime = Join-Path $InstallDirectory 'local-runtime'
$existing = Get-NetTCPConnection -State Listen -LocalPort 9000 -ErrorAction SilentlyContinue
if ($existing) {
$owner = Get-Process -Id ($existing | Select-Object -First 1).OwningProcess
if ($owner.Path -ne $server) { throw 'Port 9000 belongs to another program.' }
Write-Output "MinIO already running (PID $($owner.Id))."; return
}
$credential = Import-Clixml -LiteralPath (Join-Path $runtime 'root-credential.xml')
$priorUser = $env:MINIO_ROOT_USER; $priorPassword = $env:MINIO_ROOT_PASSWORD
try {
$env:MINIO_ROOT_USER = $credential.UserName
$env:MINIO_ROOT_PASSWORD = $credential.GetNetworkCredential().Password
& $server server (Join-Path $InstallDirectory 'data') --address 127.0.0.1:9000 --console-address 127.0.0.1:9001
} finally {
$env:MINIO_ROOT_USER = $priorUser; $env:MINIO_ROOT_PASSWORD = $priorPassword
}
