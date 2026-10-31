<#
.SYNOPSIS
  Renews the FB_TEST_COOKIES secret (the daily GitHub live checks' Facebook login) from the debug
  app's login on the emulator, then starts the checks once to confirm it.

.DESCRIPTION
  When the "Daily Facebook check failed" issue says "Facebook session expired":
    1. log in to the Facebook TEST account in the debug app (com.astryx.book.test) on the
       emulator, if it isn't already (never a personal account),
    2. run this script.
  It copies the app's WebView cookie store off the emulator (adb run-as), reads c_user, xs, datr
  and sb for .facebook.com from it, and hands them to `gh secret set FB_TEST_COOKIES` on its input.
  The values are never printed, and the copy is deleted. The emulator and GitHub then share that
  login: if Facebook ends it, log in on the emulator again and run this again.

  Needs Python 3 (its sqlite3 module reads the cookie store) and gh logged in as the repo's owner.

.PARAMETER NoRun
  Only set the secret; don't start the checks.
#>
param(
    [switch]$NoRun
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\emulator.ps1" # $adb, Get-EmulatorSerial
$ghRepo = 'ofirc73/AstryxBook'
$package = 'com.astryx.book.test'

$serial = Get-EmulatorSerial
if (-not $serial) { throw 'No emulator running: start it and log in to the test account in the debug app' }

# WebView writes its cookies to disk every 30 s or so: the app in the background flushes them now.
& $adb -s $serial shell input keyevent KEYCODE_HOME
Start-Sleep 3

$dir = Join-Path ([IO.Path]::GetTempPath()) ([IO.Path]::GetRandomFileName())
New-Item -ItemType Directory $dir | Out-Null
try {
    $db = Join-Path $dir 'Cookies'
    # Through cmd: PowerShell's redirection would re-encode the binary file.
    cmd /c "`"$adb`" -s $serial exec-out run-as $package cat app_webview/Default/Cookies > `"$db`""
    if ($LASTEXITCODE -ne 0 -or (Get-Item $db).Length -eq 0) { throw "Couldn't read the debug app's cookies ($package installed?)" }

    # The four cookies as "c_user=...; xs=...; datr=...; sb=..." on the first line, and when xs
    # expires on the second; nothing at all when one is missing. Chrome's times count microseconds
    # from 1601.
    $read = @'
import sqlite3, sys, datetime
names = ['c_user', 'xs', 'datr', 'sb']
rows = dict((n, (v, x)) for n, v, x in sqlite3.connect(sys.argv[1]).execute(
    "select name, value, expires_utc from cookies where host_key = '.facebook.com'") if n in names)
if all(rows.get(n, ('', 0))[0] for n in names):
    print('; '.join(n + '=' + rows[n][0] for n in names))
    print(datetime.datetime(1601, 1, 1) + datetime.timedelta(microseconds=rows['xs'][1]))
'@
    $out = @(python -I -c $read $db)
    if ($LASTEXITCODE -ne 0) { throw "Couldn't read the cookie store (Python 3 installed?)" }
    if ($out.Count -lt 2) { throw "The debug app isn't logged in to Facebook on the emulator: log in to the test account, then run this again" }

    $out[0] | gh secret set FB_TEST_COOKIES -R $ghRepo
    if ($LASTEXITCODE -ne 0) { throw 'gh secret set failed' }
    Write-Host "FB_TEST_COOKIES renewed. Facebook gives this login (xs) until $(([datetime]$out[1]).ToString('yyyy-MM-dd')), unless it ends it sooner."
    $out = $null
} finally {
    Remove-Item -Recurse -Force $dir
}

if (-not $NoRun) {
    # Past the daily limit too (force): it's the run that checks the new login.
    gh workflow run facebook-canary.yml -R $ghRepo -f force=true
    if ($LASTEXITCODE -ne 0) { throw "Couldn't start the live checks" }
    Write-Host "The live checks are starting: gh run list -R $ghRepo --workflow facebook-canary.yml"
    Write-Host 'A passing run closes the "Daily Facebook check failed" issue.'
}
