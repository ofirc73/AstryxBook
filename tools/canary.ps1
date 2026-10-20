<#
.SYNOPSIS
  Live-Facebook check: runs LiveFacebookCanaryTest (the app against today's real
  m.facebook.com) on the emulator, from the latest main, and reports a failure as a GitHub issue.

.DESCRIPTION
  Facebook changes its pages without notice; the tests in CI run on fixed test pages and can't
  see that. This script:
    1. starts the emulator headless if none is running (and stops it again afterwards),
    2. wakes and unlocks it (the PIN in $env:ASTRYX_EMULATOR_PIN, if it has one),
    3. builds the debug app and its tests from origin/main in a separate worktree
       (../astryx-canary; the main checkout is left alone) and installs them,
    4. runs the canary with `am instrument` (keeps the debug app's Facebook login),
    5. on failure opens, or comments on, one "Daily Facebook check failed" issue; when the checks
       pass again it closes that issue.
  The debug app must be logged in to Facebook on the emulator (log in by hand once). Nothing is
  posted: the checks stop at open editors and leave drafts unsent.

  Logs: %LOCALAPPDATA%\Astryxbook\canary\<date>.log.

.PARAMETER NoBuild
  Test the debug app already installed instead of building origin/main.
.PARAMETER NoIssue
  Only log; don't open, comment on or close the GitHub issue.
#>
param(
    [string]$Avd = 'astryx_api36',
    [switch]$NoBuild,
    [switch]$NoIssue
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path "$PSScriptRoot\..").Path
$worktree = Join-Path (Split-Path $repo -Parent) 'astryx-canary'
$ghRepo = 'ofirc73/AstryxBook'
$issueTitle = 'Daily Facebook check failed'
$sdk = $env:ANDROID_HOME
if (-not $sdk) {
    $sdk = ((Get-Content "$repo\local.properties" | Select-String '^sdk.dir=') -replace '^sdk.dir=', '' -replace '\\:', ':').Trim()
}
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$emulatorExe = Join-Path $sdk 'emulator\emulator.exe'
$logDir = Join-Path $env:LOCALAPPDATA 'Astryxbook\canary'
New-Item -ItemType Directory -Force $logDir | Out-Null
$log = Join-Path $logDir ((Get-Date -Format 'yyyy-MM-dd_HHmm') + '.log')
Start-Transcript -Path $log | Out-Null

function Get-EmulatorSerial {
    (& $adb devices) -match '^emulator-\d+\s+device$' | ForEach-Object { ($_ -split '\s+')[0] } | Select-Object -First 1
}

$startedEmulator = $false
$failure = $null
$commit = ''
try {
    # 1. The emulator.
    $serial = Get-EmulatorSerial
    if (-not $serial) {
        Write-Host "Starting emulator $Avd (headless)"
        Start-Process -FilePath $emulatorExe -ArgumentList "-avd $Avd -no-window -no-audio -no-boot-anim" -WindowStyle Hidden
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

    # 2. Awake and unlocked (WebView fails in tests behind the lock screen).
    & $adb shell input keyevent KEYCODE_WAKEUP
    & $adb shell wm dismiss-keyguard
    Start-Sleep 2
    if ((& $adb shell 'dumpsys window | grep isKeyguardShowing=true') -and $env:ASTRYX_EMULATOR_PIN) {
        & $adb shell input text $env:ASTRYX_EMULATOR_PIN
        & $adb shell input keyevent KEYCODE_ENTER
        Start-Sleep 3
    }
    if (& $adb shell 'dumpsys window | grep isKeyguardShowing=true') { throw 'The emulator is locked (set ASTRYX_EMULATOR_PIN)' }

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
    $output = & $adb shell am instrument -w -e canary true -e class com.eepiemi.materialbook.LiveFacebookCanaryTest `
        com.astryx.book.test.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | Out-String
    Write-Host $output
    if ($output -notmatch 'OK \(\d+ tests?\)') {
        # The failing checks and their messages, without stack traces.
        $failure = ($output -split "`r?`n" | Where-Object {
            $_ -match '^(Error in |\d+\) |java\.lang\.\w+(Error|Exception)|Process crashed|INSTRUMENTATION_RESULT: shortMsg|Tests run:)'
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
if (-not $NoIssue) {
    try {
        $open = gh issue list -R $ghRepo --state open --search "in:title `"$issueTitle`"" --json number,title `
            -q ".[] | select(.title == `"$issueTitle`") | .number" | Select-Object -First 1
        if ($failure) {
            $body = "**$date** on the emulator$(if ($commit) { ", main at $commit" }):`n`n``````n$failure`n```````n`n" +
                "LiveFacebookCanaryTest against today's m.facebook.com. Full log on the PC: ``$log``."
            if ($open) { gh issue comment $open -R $ghRepo --body $body | Out-Null }
            else { gh issue create -R $ghRepo --title $issueTitle --body $body | Out-Null }
        } elseif ($open) {
            gh issue close $open -R $ghRepo --comment "Passing again on $date$(if ($commit) { " (main at $commit)" })." | Out-Null
        }
    } catch {
        Write-Host "Couldn't report on GitHub: $($_.Exception.Message)"
    }
}

if ($failure) { Write-Host "FAILED:`n$failure" } else { Write-Host 'All live checks passed' }
Stop-Transcript | Out-Null
# Keep a month of logs.
Get-ChildItem $logDir -Filter *.log | Where-Object LastWriteTime -lt (Get-Date).AddDays(-30) | Remove-Item -Force
exit ([int][bool]$failure)
