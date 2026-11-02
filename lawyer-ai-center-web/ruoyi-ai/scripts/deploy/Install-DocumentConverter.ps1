param([string]$RuntimeRoot=(Join-Path $PSScriptRoot '../../../data/document-converter'))
$ErrorActionPreference='Stop'
$taskRoot=[IO.Path]::GetFullPath($RuntimeRoot)
New-Item -ItemType Directory -Force -Path $taskRoot | Out-Null
$taskMsi=Join-Path $taskRoot 'LibreOffice_26.8.1_Win_x86-64.msi'
$taskHash='c6298b10cfa1748bcbedad94c62ce771e240e5021bfd6d7a967cd571b04b5312'
if(!(Test-Path -LiteralPath $taskMsi)) {
 & curl.exe -L --fail --connect-timeout 20 --max-time 600 -o $taskMsi 'https://download.documentfoundation.org/libreoffice/stable/26.8.1/win/x86_64/LibreOffice_26.8.1_Win_x86-64.msi'
 if($LASTEXITCODE -ne 0){throw 'Official converter download failed'}
}
if((Get-FileHash -LiteralPath $taskMsi -Algorithm SHA256).Hash.ToLower() -ne $taskHash){throw 'Converter checksum mismatch'}
if((Get-AuthenticodeSignature -LiteralPath $taskMsi).Status -ne 'Valid'){throw 'Converter signature invalid'}
$taskRuntime=Join-Path $taskRoot 'runtime'
# Administrative image extraction only; no default applications or system file associations are changed.
$taskProcess=Start-Process -FilePath msiexec.exe -ArgumentList @('/a',('"'+$taskMsi+'"'),'/qn',('TARGETDIR="'+$taskRuntime+'"')) -WindowStyle Hidden -PassThru
$taskProcess.WaitForExit()
if($taskProcess.ExitCode -ne 0){throw "Converter extraction failed: $($taskProcess.ExitCode)"}
$taskOffice=Get-ChildItem -LiteralPath $taskRuntime -Recurse -Filter soffice.exe | Select-Object -First 1
if(!$taskOffice){throw 'Converter executable missing'}
Write-Output ('legal.documents.soffice='+$taskOffice.FullName)
