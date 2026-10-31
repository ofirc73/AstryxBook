# The Android SDK's adb and emulator, and the running emulator's serial: shared by the scripts in
# tools/ (dot-source it). The SDK is ANDROID_HOME's, or local.properties' sdk.dir.
$sdk = $env:ANDROID_HOME
if (-not $sdk) {
    $sdk = ((Get-Content "$PSScriptRoot\..\local.properties" | Select-String '^sdk.dir=') -replace '^sdk.dir=', '' -replace '\\:', ':').Trim()
}
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$emulatorExe = Join-Path $sdk 'emulator\emulator.exe'

function Get-EmulatorSerial {
    (& $adb devices) -match '^emulator-\d+\s+device$' | ForEach-Object { ($_ -split '\s+')[0] } | Select-Object -First 1
}
