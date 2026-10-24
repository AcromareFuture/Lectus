param([switch]$Test,[switch]$Package,[ValidateSet('gateway','identity-service','catalog-service','trade-service','member-service')][string]$Service='gateway',[ValidateSet('local','postgres')][string]$Profile='local')
$ErrorActionPreference='Stop'
$javaCandidates=@($env:JAVA_HOME,"$env:USERPROFILE\.jdks\jdk21")
$selectedJavaHome=$null
foreach($candidate in $javaCandidates){
 if($candidate -and (Test-Path "$candidate\bin\javac.exe")){
  $version=& "$candidate\bin\javac.exe" -version | Out-String
  if($LASTEXITCODE -eq 0 -and $version -match 'javac (21|2[2-9]|[3-9]\d)(?:\.|\s|$)'){$selectedJavaHome=$candidate;break}
 }
}
if(!$selectedJavaHome){throw 'JDK 21+ required'}
$env:JAVA_HOME=$selectedJavaHome
$maven=Get-Command mvn.cmd -ErrorAction SilentlyContinue
$mavenPath=if($maven){$maven.Source}else{"$env:USERPROFILE\.jdks\apache-maven-3.9.9\bin\mvn.cmd"}
Push-Location $PSScriptRoot
try {
 if($Test){& $mavenPath -B -ntp test}
 elseif($Package){& $mavenPath -B -ntp verify}
 else {
  $module=if($Service -eq 'gateway'){'gateway'}else{"services/$Service"}
  $jar=Get-ChildItem "$module/target/*.jar" -ErrorAction SilentlyContinue | Where-Object {$_.Name -notmatch 'original'} | Select-Object -First 1
  if(!$jar){throw 'Run .\run.ps1 -Package first'}
  & "$env:JAVA_HOME\bin\java.exe" -jar $jar.FullName "--spring.profiles.active=$Profile"
 }
 if($LASTEXITCODE){throw "Backend command failed: $LASTEXITCODE"}
} finally {Pop-Location}
