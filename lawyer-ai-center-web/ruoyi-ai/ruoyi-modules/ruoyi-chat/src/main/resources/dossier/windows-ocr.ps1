param([Parameter(Mandatory=$true)][string]$Image,[Parameter(Mandatory=$true)][string]$Output,[string]$Language='zh-Hans-CN')
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null=[Windows.Storage.StorageFile,Windows.Storage,ContentType=WindowsRuntime]
$null=[Windows.Graphics.Imaging.BitmapDecoder,Windows.Foundation,ContentType=WindowsRuntime]
$null=[Windows.Media.Ocr.OcrEngine,Windows.Foundation,ContentType=WindowsRuntime]
$null=[Windows.Globalization.Language,Windows.Foundation,ContentType=WindowsRuntime]
function Await-WinRt($Operation,[Type]$ResultType) {
 $method=[System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {$_.Name -eq 'AsTask' -and $_.IsGenericMethod -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'} | Select-Object -First 1
 $task=$method.MakeGenericMethod($ResultType).Invoke($null,@($Operation));$task.GetAwaiter().GetResult()
}
$stream=$null;$bitmap=$null
try {
 $file=Await-WinRt ([Windows.Storage.StorageFile]::GetFileFromPathAsync($Image)) ([Windows.Storage.StorageFile])
 $stream=Await-WinRt ($file.OpenAsync([Windows.Storage.FileAccessMode]::Read)) ([Windows.Storage.Streams.IRandomAccessStream])
 $decoder=Await-WinRt ([Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync($stream)) ([Windows.Graphics.Imaging.BitmapDecoder])
 $bitmap=Await-WinRt ($decoder.GetSoftwareBitmapAsync()) ([Windows.Graphics.Imaging.SoftwareBitmap])
 $engine=[Windows.Media.Ocr.OcrEngine]::TryCreateFromLanguage((New-Object Windows.Globalization.Language($Language)))
 if($null -eq $engine){throw 'OCR_LANGUAGE_UNAVAILABLE'}
 $result=Await-WinRt ($engine.RecognizeAsync($bitmap)) ([Windows.Media.Ocr.OcrResult])
 # Preserve recognized glyphs. Reconstruct rows from actual word coordinates;
 # the engine's paragraph order can otherwise turn a table into separate columns.
 $bands=New-Object 'System.Collections.Generic.List[object]'
 $words=@($result.Lines | ForEach-Object {$_.Words} | Sort-Object @{Expression={$_.BoundingRect.Y+$_.BoundingRect.Height/2}},@{Expression={$_.BoundingRect.X}})
 foreach($word in $words){
  $y=$word.BoundingRect.Y+$word.BoundingRect.Height/2
  $band=$bands | Where-Object {[Math]::Abs($_.Y-$y) -le [Math]::Max(3,[Math]::Min($_.Height,$word.BoundingRect.Height)*0.45)} | Sort-Object @{Expression={[Math]::Abs($_.Y-$y)}} | Select-Object -First 1
  if($null -eq $band){$band=[pscustomobject]@{Y=$y;Height=$word.BoundingRect.Height;Words=(New-Object 'System.Collections.Generic.List[object]')};$bands.Add($band)}
  $band.Words.Add($word)
 }
 $lines=foreach($band in ($bands | Sort-Object Y)){
  $text=New-Object Text.StringBuilder;$previous=$null
  foreach($word in ($band.Words | Sort-Object @{Expression={$_.BoundingRect.X}})){
   if($null -ne $previous){$gap=$word.BoundingRect.X-($previous.BoundingRect.X+$previous.BoundingRect.Width);if($gap -gt [Math]::Min($previous.BoundingRect.Height,$word.BoundingRect.Height)*0.25){$null=$text.Append(' ')}}
   $null=$text.Append($word.Text);$previous=$word
  }
  $text.ToString()
 }
 [IO.File]::WriteAllText($Output,($lines -join "`n"),(New-Object Text.UTF8Encoding($false)))
} catch {exit 2} finally {if($bitmap){$bitmap.Dispose()};if($stream){$stream.Dispose()}}
