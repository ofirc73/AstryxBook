<#
.SYNOPSIS
  Registers the daily live Facebook check on this PC: a Windows scheduled task, "Astryxbook daily
  Facebook check", that runs tools/canary.ps1 every day (-Remove takes it off).

.DESCRIPTION
  The check runs on this PC's emulator, where the debug app is logged in to the test account
  (log in by hand once): no cookies to copy, so nothing to renew. GitHub's emulators replayed that
  login's cookies from a new machine each run, and Facebook ended the session every few days.
  The task runs while you're logged on, hidden; if the PC is off or asleep at that time, it runs
  as soon as it can afterwards. A run takes about 30-40 minutes; it starts the emulator headless
  and stops it after (an emulator already running is used and left running).

.PARAMETER At
  The time of day, local (default 09:00).
.PARAMETER Chat
  The test account's chat for the Messages layer checks (canary.ps1 -Chat).
.PARAMETER Pin
  The emulator's screen lock PIN (canary.ps1 -Pin).
.PARAMETER Remove
  Takes the task off.
#>
param(
    [string]$At = '09:00',
    [string]$Chat = '',
    [string]$Pin = $env:ASTRYX_EMULATOR_PIN,
    [switch]$Remove
)

$ErrorActionPreference = 'Stop'
$name = 'Astryxbook daily Facebook check'

if ($Remove) {
    Unregister-ScheduledTask -TaskName $name -Confirm:$false
    Write-Host "Removed the task '$name'"
    return
}

$canary = Join-Path $PSScriptRoot 'canary.ps1'
$arguments = "-NoProfile -WindowStyle Hidden -File `"$canary`"" +
    $(if ($Chat) { " -Chat `"$Chat`"" } else { '' }) +
    $(if ($Pin) { " -Pin `"$Pin`"" } else { '' })
$action = New-ScheduledTaskAction -Execute (Get-Command pwsh).Source -Argument $arguments -WorkingDirectory $PSScriptRoot
$trigger = New-ScheduledTaskTrigger -Daily -At $At
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -RunOnlyIfNetworkAvailable -MultipleInstances IgnoreNew `
    -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit (New-TimeSpan -Hours 2)
$principal = New-ScheduledTaskPrincipal -UserId "$env:USERDOMAIN\$env:USERNAME" -LogonType Interactive
Register-ScheduledTask -TaskName $name -Action $action -Trigger $trigger -Settings $settings -Principal $principal `
    -Description 'Runs Astryxbook''s live checks against Facebook on the emulator and reports to GitHub (tools/canary.ps1).' -Force | Out-Null
Write-Host "Registered '$name': daily at $At (or as soon as it can after that). Logs: $env:LOCALAPPDATA\Astryxbook\canary"
Write-Host "Run it now: Start-ScheduledTask -TaskName '$name'"
