# Install debug APK ke semua gawai terhubung
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$apk = Join-Path $PSScriptRoot "..\app\app\build\outputs\apk\debug\app-debug.apk"

if (-not (Test-Path $apk)) {
    Write-Error "APK not found. Run: cd app; .\gradlew.bat assembleDebug"
    exit 1
}

$devices = & $adb devices | Select-String "device$" | ForEach-Object { ($_ -split "\s+")[0] }
if (-not $devices) {
    Write-Error "No devices connected"
    exit 1
}

foreach ($serial in $devices) {
    Write-Host "Installing to $serial ..."
    $result = & $adb -s $serial install -r $apk 2>&1
    if ($result -match "INSTALL_FAILED_UPDATE_INCOMPATIBLE") {
        Write-Host "  Signature mismatch — uninstalling old app ..."
        & $adb -s $serial uninstall com.example.caraka | Out-Null
        & $adb -s $serial install $apk
    } else {
        $result
    }
}

Write-Host "Done. Launch with: adb shell am start -n com.example.caraka/.MainActivity"
