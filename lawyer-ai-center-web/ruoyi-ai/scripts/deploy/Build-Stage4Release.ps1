param(
 [Parameter(Mandatory=$true)][string]$EvidenceDir,
 [string]$JavaHome='D:\devoloper_tools\Java\jdk-17',
 [string]$Maven='D:\devoloper_tools\apache-maven-3.9.9\bin\mvn.cmd',
 [switch]$UseJustBuiltJar
)
$ErrorActionPreference='Stop'
$taskRoot=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$taskEvidence=[IO.Path]::GetFullPath($EvidenceDir)
New-Item -ItemType Directory -Force -Path $taskEvidence | Out-Null
if(!$UseJustBuiltJar) {
 $env:JAVA_HOME=$JavaHome
 Push-Location $taskRoot
 try {
  $ErrorActionPreference='Continue'
  & $Maven -o -pl ruoyi-admin -am package '-DskipTests' *> (Join-Path $taskEvidence 'backend-package.log')
  $taskExit=$LASTEXITCODE
  $ErrorActionPreference='Stop'
  if($taskExit -ne 0){throw 'Backend package failed; service not replaced.'}
 } finally {Pop-Location}
}
if(!(Select-String -LiteralPath (Join-Path $taskEvidence 'backend-package.log') -Pattern 'BUILD SUCCESS' -Quiet)){throw 'A successful build receipt is required.'}
$taskRelease=Join-Path $taskRoot ('.codex-runtime/stage4-releases/'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force -Path $taskRelease | Out-Null
$taskJar=Join-Path $taskRelease 'ruoyi-admin.jar'
Copy-Item -LiteralPath (Join-Path $taskRoot 'ruoyi-admin/target/ruoyi-admin.jar') -Destination $taskJar
$taskFiles=@(
 'ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/chat/ContractReviewReport.java',
 'ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/chat/ContractReviewSources.java',
 'ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/chat/impl/ChatServiceFacade.java',
 'ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/controller/legal/SearchReportController.java',
 'ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/lawyer/CaseTaskService.java',
 'ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/controller/legal/LegalOpinionController.java'
)
$taskSources=@($taskFiles | ForEach-Object {@{path=$_;sha256=(Get-FileHash -LiteralPath (Join-Path $taskRoot $_) -Algorithm SHA256).Hash.ToLower()}})
$taskReceipt=@{time=(Get-Date).ToString('o');operator=$env:STAGE4_OPERATOR;version='3.1.0 + stage4 / instructions 50-51';jar=$taskJar;sha256=(Get-FileHash -LiteralPath $taskJar -Algorithm SHA256).Hash.ToLower();sourceFiles=$taskSources;mobile52='Formal ruoyi-uniapp sources delivered separately; not embedded into the backend jar'}
$taskReceipt | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $taskEvidence 'release.json') -Encoding utf8
$taskReceipt | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $taskRelease 'release.json') -Encoding utf8
Write-Output "Stage4 immutable release prepared: $taskJar"
