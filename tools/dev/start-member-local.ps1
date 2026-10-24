param([switch]$Build)
# Compatibility entry point; all implementation is inside backend/.
& "$PSScriptRoot/../../backend/scripts/start-local.ps1" -Build:$Build
