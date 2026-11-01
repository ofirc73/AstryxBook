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

.PARAMETER NoBuild
  Test the debug app already installed instead of building origin/main.
.PARAMETER NoIssue
  Only log; don't open, comment on or close the GitHub issues.
.PARAMETER Chat
  The test account's chat for the Messages layer checks (-e fbChat): its address, e2ee/t/<id> or a
  bare id. Without it they're skipped.
.PARAMETER Pin
  The emulator's screen lock PIN, to unlock it.
#>
param(
    [string]$Avd = 'astryx_api36',
    [string]$Chat = '',
    [string]$Pin = $env:ASTRYX_EMULATOR_PIN,
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
. "$PSScriptRoot\emulator.ps1" # $adb, $emulatorExe, Get-EmulatorSerial
$logDir = Join-Path $env:LOCALAPPDATA 'Astryxbook\canary'
$gapsDir = Join-Path $logDir 'gaps'
New-Item -ItemType Directory -Force $gapsDir | Out-Null
$stamp = Get-Date -Format 'yyyy-MM-dd_HHmm'
$log = Join-Path $logDir "$stamp.log"
Start-Transcript -Path $log | Out-Null

# The issues are opened, commented on and closed by the script facebook-canary.yml uses, through
# Git's own bash (a plain `bash` can be WSL's).
$gitBash = Join-Path (Split-Path (Split-Path (Get-Command git).Source -Parent) -Parent) 'bin\bash.exe'
function Update-Issue([string]$action, [string]$title, [string]$text) {
    & $gitBash "$repo/.github/scripts/canary-issue.sh" $action $title $text | Out-Null
}

$startedEmulator = $false
$failure = $null
$output = ''
$commit = ''
try {
    # 1. The emulator, cold-booted: a quick-boot snapshot brought older APKs and flaky tests back.
    $serial = Get-EmulatorSerial
    if (-not $serial) {
        Write-Host "Starting emulator $Avd (headless, cold boot)"
        Start-Process -FilePath $emulatorExe -ArgumentList "-avd $Avd -no-window -no-audio -no-boot-anim -no-snapshot-load" -WindowStyle Hidden
        $startedEmulator = $true
        $deadline = (Get-Date).AddMinutes(6)
        while (-not ($serial = Get-EmulatorSerial)) {
            if ((Get-Date) -gt $deadline) { throw 'The emulator never came up' }
            Start-Sleep 5
        }
        while ((& $adb -s $serial shell getprop sys.boot_completed 2>$null) -ne '1') {
            if ((Get-Date) -gt $deadline) { throw 'The emulator never finished booting' }
            Start-Sleep 5
        }
        Start-Sleep 20 # the launcher and Play services settle after boot
    }
    $env:ANDROID_SERIAL = $serial
    Write-Host "Device: $serial"

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
    $output = & $adb shell am instrument -w -e canary true @chatArgs -e class com.astryx.book.LiveFacebookCanaryTest `
        com.astryx.book.test.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | Out-String
    Write-Host $output
    if ($output -notmatch 'OK \(\d+ tests?\)') {
        # The failing checks and their messages, without stack traces.
        $failure = ($output -split "`r?`n" | Where-Object {
            $_ -match '^(Error in |\d+\) |[a-z][a-z0-9_]*(\.[A-Za-z0-9_$]+)+(Error|Exception)|Process crashed|INSTRUMENTATION_RESULT: shortMsg|Tests run:)'
        }) -join "`n"
        if (-not $failure) { $failure = 'No test result (see the log on the PC)' }
    }
} catch {
    $failure = "The check couldn't run: $($_.Exception.Message)"
} finally {
    if ($startedEmulator -and $env:ANDROID_SERIAL) { & $adb emu kill 2>$null | Out-Null }
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
if (-not $NoIssue) {
    try {
        if ($failure) {
            $body = "**$date** on the PC's emulator${at}:`n`n``````n$failure`n``````" +
                $(if ($notes) { "`n`nNotes:`n``````n$notes`n``````" } else { '' }) +
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
                    "these parts may be broken without a check failing:`n``````n$($long -join "`n")`n``````")
            } else {
                Update-Issue close $gapsTitle "Checked again on $date$at."
            }
        }
    } catch {
        Write-Host "Couldn't report on GitHub: $($_.Exception.Message)"
    }
}

if ($notes) { Write-Host "Notes:`n$notes" }
if ($failure) { Write-Host "FAILED:`n$failure" } else { Write-Host 'All live checks passed' }
Stop-Transcript | Out-Null
# Keep a month of logs and gaps.
Get-ChildItem $logDir, $gapsDir -File | Where-Object LastWriteTime -lt (Get-Date).AddDays(-30) | Remove-Item -Force
exit ([int][bool]$failure)
