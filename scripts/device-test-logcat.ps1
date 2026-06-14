# CARAKA device test — logcat filter (sesuai rencana A0)
# Usage: .\scripts\device-test-logcat.ps1 [-DeviceSerial <serial>] [-LogFile <path>]

param(
    [string]$DeviceSerial = "",
    [string]$LogFile = ""
)

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    $adb = "$env:USERPROFILE\AppData\Local\Android\Sdk\platform-tools\adb.exe"
}

$tags = "WifiDirect:*", "MeshRouter:*", "MeshFGS:*", "MeshSocket:*", "CarakaDB:*", "DBPassphrase:*", "MeshManager:*", "Room:*"
$filter = ($tags -join " ") + " *:S"

$args = @("logcat", "-v", "threadtime", $filter)
if ($DeviceSerial) { $args = @("-s", $DeviceSerial) + $args }

Write-Host "CARAKA logcat — tags: WifiDirect, MeshRouter, MeshFGS, MeshSocket, CarakaDB, DBPassphrase"
Write-Host "Device: $(if ($DeviceSerial) { $DeviceSerial } else { 'all' })"
Write-Host "Ctrl+C to stop"
Write-Host "---"

if ($LogFile) {
    & $adb @args | Tee-Object -FilePath $LogFile
} else {
    & $adb @args
}
