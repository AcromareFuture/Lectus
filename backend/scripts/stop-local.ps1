$ErrorActionPreference='Stop'
$runtime=Join-Path (Split-Path $PSScriptRoot -Parent) '.runtime/local'
if(!(Test-Path "$runtime/processes.json")){return}
$records=Get-Content "$runtime/processes.json" -Raw | ConvertFrom-Json
foreach($record in $records){
 $process=Get-Process -Id $record.pid -ErrorAction SilentlyContinue
 if($process -and [Math]::Abs(($process.StartTime.ToUniversalTime()-([datetime]$record.startTime).ToUniversalTime()).TotalSeconds) -lt 2){
  & taskkill.exe /PID $record.pid /T /F | Out-Null
 }
}
Remove-Item -LiteralPath "$runtime/processes.json"
