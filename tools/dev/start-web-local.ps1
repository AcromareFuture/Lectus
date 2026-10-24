param([ValidateSet('storefront','merchant','platform')][string]$App='storefront')
$ErrorActionPreference='Stop'
$frontendRoot=Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) 'frontend'
Push-Location $frontendRoot
try {
 & npm.cmd run "dev:$App"
 if($LASTEXITCODE -ne 0){throw "Frontend exited with code $LASTEXITCODE"}
} finally {Pop-Location}
