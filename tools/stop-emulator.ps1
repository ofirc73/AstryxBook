# Stops the running local emulator (the AVD's own qemu processes only) and deletes its RAM file
# (`snapshots\default_boot`, 4 GB; see Remove-RamSnapshot). Use it instead of `adb emu kill`.
# Start it again with: emulator -avd astryx_api36 -no-snapshot-load -no-snapshot-save
# Check the daily check's `running.lock` and `ActiveInstrumentation` first (CLAUDE.md).
param([string]$Avd = 'astryx_api36')
. "$PSScriptRoot\emulator.ps1" # $adb, Get-EmulatorSerial, Stop-Emulator, Remove-RamSnapshot
$serial = Get-EmulatorSerial
if (-not $serial) { Write-Host 'No emulator running'; exit 0 }
$ids = @(Get-CimInstance Win32_Process -Filter "Name like 'qemu-system%'" |
    Where-Object { $_.CommandLine -match "-avd $Avd(\s|$)" }).ProcessId
Stop-Emulator $serial $ids $Avd
