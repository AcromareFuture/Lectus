param([switch]$Build)
$ErrorActionPreference='Stop'
$backendRoot=Split-Path $PSScriptRoot -Parent
if($Build){& "$backendRoot/run.ps1" -Package}
$runtime=Join-Path $backendRoot '.runtime/local'
New-Item -ItemType Directory -Force $runtime | Out-Null
if(Test-Path "$runtime/processes.json"){throw 'Local run manifest exists. Run stop-local.ps1 first.'}
$ports=@(8080,8081,8082,8083,8084)
foreach($port in $ports){if(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue){throw "Port $port already occupied; no process was stopped"}}
$records=@()
try {
 foreach($name in @('identity-service','catalog-service','trade-service','member-service','gateway')){
  $process=Start-Process powershell.exe -WindowStyle Hidden -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-File',"`"$backendRoot/run.ps1`"",'-Service',$name,'-Profile','local' -WorkingDirectory $backendRoot -RedirectStandardOutput "$runtime/$name.log" -RedirectStandardError "$runtime/$name.err.log" -PassThru
  $records+=@{service=$name;pid=$process.Id;startTime=$process.StartTime.ToUniversalTime().ToString('o')}
  $records | ConvertTo-Json -Depth 3 | Set-Content "$runtime/processes.json"
 }
 $servicePorts=@{'gateway'=8080;'member-service'=8081;'identity-service'=8082;'catalog-service'=8083;'trade-service'=8084}
 $deadline=[datetime]::UtcNow.AddSeconds(120)
 $pending=@($records)
 while($pending.Count -gt 0){
  $remaining=@()
  foreach($record in $pending){
   $process=Get-Process -Id $record.pid -ErrorAction SilentlyContinue
   if(!$process){throw "$($record.service) exited. See $runtime/$($record.service).log and .err.log"}
   try {
    $response=Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$($servicePorts[$record.service])/actuator/health/readiness" -TimeoutSec 2
    if($response.StatusCode -ne 200){$remaining+=$record}
   } catch {$remaining+=$record}
  }
  $pending=@($remaining)
  if($pending.Count -gt 0){
   if([datetime]::UtcNow -ge $deadline){throw "Services not ready: $($pending.service -join ', '). See $runtime"}
   Start-Sleep -Milliseconds 500
  }
 }
 Write-Output 'Five local services are ready. Gateway: http://127.0.0.1:8080/api/health ; logs: backend/.runtime/local'
} catch {
 $startupError=$_
 & "$PSScriptRoot/stop-local.ps1"
 throw $startupError
}
