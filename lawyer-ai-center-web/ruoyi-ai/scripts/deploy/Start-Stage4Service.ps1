param(
 [Parameter(Mandatory=$true)][string]$ReleaseJar,
 [Parameter(Mandatory=$true)][string]$EvidenceDir,
 [int]$Port=6038,
 [int]$ReplaceProcessId=0,
 [string]$Java='D:\devoloper_tools\Java\jdk-17\bin\java.exe'
)
$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$taskJar=(Resolve-Path -LiteralPath $ReleaseJar).Path
$taskEvidence=[IO.Path]::GetFullPath($EvidenceDir)
New-Item -ItemType Directory -Force -Path $taskEvidence | Out-Null
if($Port -eq 6039) {
 $taskCandidateSmoke=Get-Content -LiteralPath (Join-Path $taskEvidence 'deploy-smoke-6038.json') -Raw | ConvertFrom-Json
 $taskCandidate=Get-Content -LiteralPath (Join-Path $taskEvidence 'service-6038.json') -Raw | ConvertFrom-Json
 if(!$taskCandidateSmoke.pass -or $taskCandidateSmoke.database -ne $env:STAGE4_EXPECTED_DATABASE -or $taskCandidate.sha256 -ne (Get-FileHash -LiteralPath $taskJar -Algorithm SHA256).Hash.ToLower()) {throw 'Successful candidate smoke for the same database and exact JAR is required before replacing 6039.'}
}
$taskListeners=@(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
if($taskListeners.Count -gt 0) {
 if($ReplaceProcessId -eq 0 -or @($taskListeners | Where-Object {$_.OwningProcess -ne $ReplaceProcessId}).Count -gt 0) {throw 'Port owner differs from explicit replacement PID; no process stopped.'}
 $taskOld=Get-CimInstance Win32_Process -Filter "ProcessId=$ReplaceProcessId"
 if($taskOld.Name -ne 'java.exe' -or $taskOld.CommandLine -notmatch 'RuoYiAIApplication|stage4-releases') {throw 'Port owner is not a recognized RuoYi backend; no process stopped.'}
 # Capture the launch description for operators; never include datasource/password arguments.
 $taskDescription=$taskOld.CommandLine -replace '(?i)(password|secret|token)=\S+','$1=***'
 @{pid=$ReplaceProcessId;executable=$taskOld.ExecutablePath;command=$taskDescription;time=(Get-Date).ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskEvidence 'previous-process.json') -Encoding utf8
 Stop-Process -Id $ReplaceProcessId
 for($taskN=0;$taskN -lt 30;$taskN++){if(!(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)){break};Start-Sleep -Milliseconds 500}
 if(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue){throw 'Old port still occupied; new process not started.'}
}
$taskArgs=@('-Xms128m','-Xmx768m','-Dfile.encoding=UTF-8','-jar',('"'+$taskJar+'"'),"--server.port=$Port")
if($Port -ne 6039){$taskArgs+=@('--server.address=127.0.0.1','--snail-job.enabled=false','--spring.boot.admin.client.enabled=false')}
$taskProcess=Start-Process -FilePath $Java -ArgumentList $taskArgs -WorkingDirectory $taskRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $taskEvidence "service-$Port.log") -RedirectStandardError (Join-Path $taskEvidence "service-$Port-error.log") -PassThru
@{pid=$taskProcess.Id;port=$Port;jar=$taskJar;sha256=(Get-FileHash -LiteralPath $taskJar -Algorithm SHA256).Hash.ToLower();startedAt=(Get-Date).ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskEvidence "service-$Port.json") -Encoding utf8
Write-Output "Stage4 service starting: port=$Port PID=$($taskProcess.Id)"
