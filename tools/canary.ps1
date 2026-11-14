<#
.SYNOPSIS
  Live-Facebook check: runs LiveFacebookCanaryTest (the app against today's real
  m.facebook.com) on the emulator, from the latest main, and reports to GitHub issues. This is the
  daily check: tools/schedule-canary.ps1 runs it every day on this PC.

.DESCRIPTION
  Facebook changes its pages without notice; the tests in CI run on fixed test pages and can't
  see that. This script:
    1. starts the emulator headless (cold boot) if none is running, and stops it again afterwards,
    2. wakes and unlocks it (its screen lock PIN: -Pin, or $env:ASTRYX_EMULATOR_PIN),
    3. builds the debug app and its tests from origin/main in a separate worktree
       (../astryx-canary; the main checkout is left alone) and installs them,
    4. runs the live checks with `am instrument` (keeps the debug app's Facebook login),
    5. reports as facebook-canary.yml did (.github/scripts/canary-issue.sh): failures in one "Daily
       Facebook check failed" issue, closed when the checks pass again; a part unchecked in this
       run and the 6 before it (LiveFacebookCanaryTest's gap()) in a "parts unchecked" issue.
  The debug app must be logged in to Facebook on the emulator (log in by hand once). It runs here
  rather than on GitHub: GitHub's emulators replayed this login's cookies from a new data-centre
  machine each run, and Facebook ended the session every few days. Nothing is posted: the checks
  stop at open editors and leave drafts unsent.

  Logs: %LOCALAPPDATA%\Astryxbook\canary\<date>.log; each run's gaps in its gaps\ folder.

  The emulator is shared with the work on the app: a run killed by another session's install or
  force-stop said "Process crashed" (issue 147, 2026-11-03). So a run holds
  %LOCALAPPDATA%\Astryxbook\canary\running.lock (its PowerShell process's id) from start to end,
  which sessions check before they touch the emulator (CLAUDE.md), and it waits, up to half an
  hour, while another test run is on the emulator; still busy then, it skips the day without a
  report.

  A run must end inside the scheduled task's 2-hour limit: a task Windows ends there runs none of
  this script's cleanup (the 2026-11-08 and -09 runs hung in the checks; they reported nothing, kept
  the lock and left the emulator's qemu running). So the checks get -ChecksMinutes, their output
  goes to <date>.checks.txt as they run, and a run that hangs is stopped and reported with the check
  it hung in (the test runner's "started:" line in logcat). The emulator it started is shut down
  when it's done: asked to quit, then its qemu process stopped if it's still there a minute on.

.PARAMETER NoBuild
  Test the debug app already installed instead of building origin/main.
.PARAMETER NoIssue
  Only log; don't open, comment on or close the GitHub issues.
.PARAMETER Chat
  The test account's chat for the Messages layer checks (-e fbChat): its address, e2ee/t/<id> or a
  bare id. Without it they're skipped.
.PARAMETER Pin
  The emulator's screen lock PIN, to unlock it.
.PARAMETER ChecksMinutes
  How long the checks may run before they're stopped as hung (60: with the build, the boot and a
  busy emulator's half hour, inside the scheduled task's 2 hours).
#>
param(
    [string]$Avd = 'astryx_api36',
    [string]$Chat = '',
    [string]$Pin = $env:ASTRYX_EMULATOR_PIN,
    [int]$ChecksMinutes = 60,
    [switch]$NoBuild,
    [switch]$NoIssue
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path "$PSScriptRoot\..").Path
$worktree = Join-Path (Split-Path $repo -Parent) 'astryx-canary'
$env:GH_REPO = 'ofirc73/AstryxBook'
$issueTitle = 'Daily Facebook check failed'
$gapsTitle = 'Daily Facebook check: parts unchecked for 7 runs'
$gapRuns = 7
. "$PSScriptRoot\emulator.ps1" # $adb, $emulatorExe, Get-EmulatorSerial, Stop-Emulator, Remove-RamSnapshot
. "$PSScriptRoot\canary-run.ps1" # Get-LockHolder, Get-HungCheck
$logDir = Join-Path $env:LOCALAPPDATA 'Astryxbook\canary'
$gapsDir = Join-Path $logDir 'gaps'
New-Item -ItemType Directory -Force $gapsDir | Out-Null
$stamp = Get-Date -Format 'yyyy-MM-dd_HHmm'
$log = Join-Path $logDir "$stamp.log"
Start-Transcript -Path $log | Out-Null

# This run holds the lock until it's done; a stale one (a run that was stopped) is taken over.
$lock = Join-Path $logDir 'running.lock'
$holder = Get-LockHolder -Path $lock -Self $PID
if ($holder) {
    Write-Host "Another daily check is running (process $($holder.Id)): skipped"
    Stop-Transcript | Out-Null
    exit 0
}
Set-Content -Path $lock -Value "$PID $(Get-Date -Format o)"

# The issues are opened, commented on and closed by the script facebook-canary.yml uses, through
# Git's own bash (a plain `bash` can be WSL's).
$gitBash = Join-Path (Split-Path (Split-Path (Get-Command git).Source -Parent) -Parent) 'bin\bash.exe'
# They all carry the label the daily check's issues are listed under (the script reads it).
$env:ISSUE_LABEL = 'daily-facebook-check'
# A failed report (gh logged out, no network) throws: it ended unseen, the issue saying nothing.
function Update-Issue([string]$action, [string]$title, [string]$text) {
    $said = & $gitBash "$repo/.github/scripts/canary-issue.sh" $action $title $text 2>&1
    if ($LASTEXITCODE -ne 0) { throw "canary-issue.sh $action failed (exit $LASTEXITCODE): $($said -join ' ')" }
}

$startedEmulator = $null # the qemu processes of the emulator this run started
$reportFailed = $false
$skipped = $null
$failure = $null
$output = ''
$commit = ''
try {
    # 1. The emulator, cold-booted: a quick-boot snapshot brought older APKs and flaky tests back.
    #    -no-snapshot-save too: nothing loads that snapshot, and writing it took 4 GB at every exit.
    $serial = Get-EmulatorSerial
    if (-not $serial) {
        Write-Host "Starting emulator $Avd (headless, cold boot)"
        $before = @(Get-Process -Name 'qemu-system-*' -ErrorAction SilentlyContinue).Id
        Start-Process -FilePath $emulatorExe -ArgumentList "-avd $Avd -no-window -no-audio -no-boot-anim -no-snapshot-load -no-snapshot-save" -WindowStyle Hidden
        $deadline = (Get-Date).AddMinutes(6)
        while (-not ($serial = Get-EmulatorSerial)) {
            if ((Get-Date) -gt $deadline) { throw 'The emulator never came up' }
            Start-Sleep 5
        }
        $startedEmulator = @{ Serial = $serial; Ids = @(Get-Process -Name 'qemu-system-*' -ErrorAction SilentlyContinue | Where-Object Id -notin $before).Id }
        while ((& $adb -s $serial shell getprop sys.boot_completed 2>$null) -ne '1') {
            if ((Get-Date) -gt $deadline) { throw 'The emulator never finished booting' }
            Start-Sleep 5
        }
        Start-Sleep 20 # the launcher and Play services settle after boot
    }
    $env:ANDROID_SERIAL = $serial
    Write-Host "Device: $serial"

    # Another test run on the emulator (a session's `am instrument`, listed by the activity manager
    # only while it runs): installing over it would end it, and its installs would end this run.
    $busyUntil = (Get-Date).AddMinutes(30)
    while (& $adb shell 'dumpsys activity processes | grep -q ActiveInstrumentation && echo busy') {
        if ((Get-Date) -gt $busyUntil) { $skipped = 'another test run kept the emulator busy for half an hour'; break }
        Write-Host 'Another test run is on the emulator: waiting'
        Start-Sleep 30
    }
    if ($skipped) { throw $skipped }

    # 2. Awake and unlocked (WebView fails in tests behind the lock screen). The PIN pad comes up
    # for a swipe through the touchscreen source (a plain swipe was ignored at times).
    & $adb shell input keyevent KEYCODE_WAKEUP
    & $adb shell wm dismiss-keyguard
    Start-Sleep 2
    for ($try = 0; $try -lt 3 -and $Pin -and (& $adb shell 'dumpsys window | grep isKeyguardShowing=true'); $try++) {
        & $adb shell input touchscreen swipe 540 1800 540 400 300
        Start-Sleep 2
        & $adb shell input text $Pin
        & $adb shell input keyevent KEYCODE_ENTER
        Start-Sleep 3
    }
    if (& $adb shell 'dumpsys window | grep isKeyguardShowing=true') { throw 'The emulator is locked (pass -Pin)' }

    # 3. The app and its tests, from origin/main.
    if (-not $NoBuild) {
        git -C $repo fetch -q origin main
        if (-not (Test-Path $worktree)) { git -C $repo worktree add -q --detach $worktree origin/main }
        git -C $worktree checkout -q --detach origin/main
        Copy-Item "$repo\local.properties" $worktree -Force
        $commit = (git -C $worktree rev-parse --short HEAD).Trim()
        Write-Host "Building $commit"
        Push-Location $worktree
        try {
            & .\gradlew.bat installDebug installDebugAndroidTest -q
            if ($LASTEXITCODE -ne 0) { throw "The build failed (exit $LASTEXITCODE)" }
        } finally { Pop-Location }
    }

    # 4. The checks.
    $chatArgs = if ($Chat) { @('-e', 'fbChat', $Chat) } else { @() }
    $checks = Join-Path $logDir "$stamp.checks.txt"
    # A bigger log buffer for the run (16 MB; the default rolled over a hung run's lines), so a
    # hung run's last "started:" line is still there to name the check.
    & $adb logcat -G 16M 2>$null
    & $adb logcat -c
    $run = Start-Process -FilePath $adb -PassThru -WindowStyle Hidden -RedirectStandardOutput $checks -RedirectStandardError "$checks.err" `
        -ArgumentList (@('shell', 'am', 'instrument', '-w', '-e', 'canary', 'true') + $chatArgs + @('-e', 'class',
            'com.astryx.book.LiveFacebookCanaryTest', 'com.astryx.book.test.test/androidx.test.runner.AndroidJUnitRunner'))
    if (-not $run.WaitForExit($ChecksMinutes * 60 * 1000)) {
        $run.Kill()
        # Stopped first: the test run left going keeps the emulator busy, and the next day's run
        # waits half an hour for it, then skips.
        & $adb shell am force-stop com.astryx.book.test
        $hung = Get-HungCheck (& $adb logcat -d -s TestRunner:I)
        $failure = "The checks didn't finish in $ChecksMinutes minutes: stopped in $hung"
    }
    $output = (Get-Content $checks -Raw) + (Get-Content "$checks.err" -Raw)
    Write-Host $output
    if (-not $failure -and $output -notmatch 'OK \(\d+ tests?\)') {
        # The failing checks and their messages, without stack traces (a crash's line follows the
        # passed checks' dots, and the class's name when it crashed in the first check).
        $failure = ($output -split "`r?`n" | Where-Object {
            $_ -match '^(\S+:)?\.*(Error in|\d+\) |[a-z][a-z0-9_]*(\.[A-Za-z0-9_$]+)+(Error|Exception)|Process crashed|INSTRUMENTATION_RESULT: shortMsg|Tests run:)'
        }) -join "`n"
        if (-not $failure) { $failure = 'No test result (see the log on the PC)' }
    }
} catch {
    if (-not $skipped) { $failure = "The check couldn't run: $($_.Exception.Message)" }
} finally {
    if ($startedEmulator) { Stop-Emulator $startedEmulator.Serial $startedEmulator.Ids $Avd }
    Remove-Item $lock -Force -ErrorAction SilentlyContinue
}

# 5. Report.
$date = Get-Date -Format 'yyyy-MM-dd HH:mm'
$at = if ($commit) { ", main at $commit" } else { '' }
$lines = $output -split "`r?`n"
$notes = ($lines | Select-String '^CANARY NOTE: (.*)' | ForEach-Object { $_.Matches[0].Groups[1].Value }) -join "`n"
# The parts with nothing to check this run ("[key] message"), kept for the runs after it; only for
# a run where the checks ran (a run that broke before them says nothing about them).
$ran = $output -match 'Tests run:|OK \(\d+ tests?\)'
$gaps = @($lines | Select-String '^CANARY GAP: (.*)' | ForEach-Object { $_.Matches[0].Groups[1].Value })
if ($ran) { Set-Content -Path (Join-Path $gapsDir "$stamp.txt") -Value $gaps }
if (-not $NoIssue -and -not $skipped) {
    try {
        if ($failure) {
            $body = "**$date** on the PC's emulator${at}:`n`n```````n$failure`n``````" +
                $(if ($notes) { "`n`nNotes:`n```````n$notes`n``````" } else { '' }) +
                "`n`nLiveFacebookCanaryTest against today's m.facebook.com. Full log on the PC: ``$log``."
            Update-Issue comment $issueTitle $body
        } else {
            Update-Issue close $issueTitle "Passing again on $date$at."
        }
        # A part unchecked in this run and the runs before it: one issue, its text rewritten; closed
        # once every such part was checked again.
        $earlier = @(Get-ChildItem $gapsDir -Filter *.txt | Where-Object Name -ne "$stamp.txt" |
            Sort-Object Name -Descending | Select-Object -First ($gapRuns - 1))
        if ($ran -and $earlier.Count -ge $gapRuns - 1) {
            $long = @($gaps | Where-Object {
                $key = ($_ -split '\]')[0] + ']'
                @($earlier | Where-Object { ([string](Get-Content $_.FullName -Raw)).Contains($key) }).Count -ge $gapRuns - 1
            })
            if ($long.Count) {
                Update-Issue edit $gapsTitle ("**$date**${at}: not checked in this run nor the $($gapRuns - 1) before it, so " +
                    "these parts may be broken without a check failing:`n```````n$($long -join "`n")`n``````")
            } else {
                Update-Issue close $gapsTitle "Checked again on $date$at."
            }
        }
    } catch {
        Write-Host "Couldn't report on GitHub: $($_.Exception.Message)"
        $reportFailed = $true
    }
}

if ($notes) { Write-Host "Notes:`n$notes" }
if ($skipped) { Write-Host "Skipped: $skipped" }
elseif ($failure) { Write-Host "FAILED:`n$failure" } else { Write-Host 'All live checks passed' }
Stop-Transcript | Out-Null
# Keep a month of logs and gaps.
Get-ChildItem $logDir, $gapsDir -File | Where-Object LastWriteTime -lt (Get-Date).AddDays(-30) | Remove-Item -Force
# Shown as the scheduled task's Last Run Result: nothing on GitHub says this run happened.
if ($reportFailed) { exit 2 }
exit ([int][bool]$failure)
