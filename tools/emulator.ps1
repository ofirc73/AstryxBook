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

# Whether the emulator [Serial] is still there for adb (get-state answers at once either way). Asked
# before reading its log after a run: adb's logcat waits for a device that's gone ("- waiting for
# device -"), and when the emulator died during a run (2026-11-17) the run's device log waited for
# it for hours, holding the lock. adb's shell commands fail at once instead.
function Test-Emulator([string]$Serial) {
    $Serial -and ((& $adb -s $Serial get-state 2>$null) -eq 'device')
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
