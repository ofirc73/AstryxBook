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

# Asks the emulator [Serial] to quit, waits for its qemu processes ([Ids]) to end, and stops them
# if they're still there a minute on (a headless qemu was found left behind after the daily check).
# Only those: another emulator open at the time was stopped too, mid-write.
function Stop-Emulator([string]$Serial, [int[]]$Ids) {
    & $adb -s $Serial emu kill 2>$null | Out-Null
    function Running { if ($Ids) { Get-Process -Id $Ids -ErrorAction SilentlyContinue } }
    $until = (Get-Date).AddMinutes(1)
    while ((Running) -and (Get-Date) -lt $until) { Start-Sleep 2 }
    Running | Stop-Process -Force
}
