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
# Only those: another emulator open at the time was stopped too, mid-write. With [Avd], once they
# have ended, its RAM file goes (Remove-RamSnapshot).
function Stop-Emulator([string]$Serial, [int[]]$Ids, [string]$Avd) {
    & $adb -s $Serial emu kill 2>$null | Out-Null
    function Running { if ($Ids) { Get-Process -Id $Ids -ErrorAction SilentlyContinue } }
    $until = (Get-Date).AddMinutes(1)
    while ((Running) -and (Get-Date) -lt $until) { Start-Sleep 2 }
    Running | Stop-Process -Force
    if ($Avd -and -not (Running)) { Remove-RamSnapshot $Avd }
}

# Deletes the AVD's `snapshots\default_boot`, nothing else of it (`logged_in` and the data image
# hold the test account's login). Even with -no-snapshot-save the emulator keeps the guest RAM's
# backing file there (`ram.img`, 4 GB, no `snapshot.pb`: 2026-11-14, emulator 37.1.11), and every
# cold boot makes it again. Call it only with that AVD's emulator stopped.
function Remove-RamSnapshot([string]$Avd) {
    $avdHome = if ($env:ANDROID_AVD_HOME) { $env:ANDROID_AVD_HOME }
        elseif ($env:ANDROID_USER_HOME) { Join-Path $env:ANDROID_USER_HOME 'avd' }
        else { Join-Path $env:USERPROFILE '.android\avd' }
    $dir = Join-Path $avdHome "$Avd.avd\snapshots\default_boot"
    if (Test-Path -LiteralPath $dir) {
        Remove-Item -LiteralPath $dir -Recurse -Force -ErrorAction SilentlyContinue
        Write-Host "Removed $dir"
    }
}
