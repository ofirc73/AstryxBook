# What tools/canary.ps1 decides about a run, apart from the emulator, so it can be tried alone
# (dot-source this file and call the functions).

# The daily check's scheduled task ends a run at 2 hours; a lock older than that (with a margin)
# was left by a run Windows ended, or by a shutdown mid-run.
$LockLimit = [TimeSpan]::FromMinutes(135)

# The process holding the daily check's lock file ("<process id> <time>"), or $null when no run
# holds it: no lock, the lock is this process's own ($self), or it's stale. A lock is stale when
# its process is gone, isn't PowerShell, started after the lock was written (Windows reused the
# id: this PC always runs several pwsh processes, the Claude sessions'), or the lock is older than
# a run can be. Before 2026-11-13 only the first two counted: a reused id skipped the check every
# day, and every session waited on an emulator nothing was using.
function Get-LockHolder([string]$Path, [int]$Self, [datetime]$Now = (Get-Date)) {
    if (-not (Test-Path $Path)) { return $null }
    $parts = (Get-Content $Path -Raw).Trim() -split '\s+'
    $held = 0
    if (-not [int]::TryParse($parts[0], [ref]$held) -or $held -eq $Self) { return $null }
    $since = [datetime]::MinValue
    if ($parts.Count -lt 2 -or -not [datetime]::TryParse($parts[1], [ref]$since)) { return $null }
    if ($Now - $since -gt $LockLimit) { return $null }
    $holder = Get-Process -Id $held -ErrorAction SilentlyContinue
    if (-not $holder -or $holder.ProcessName -notmatch '^(pwsh|powershell)$') { return $null }
    if ($holder.StartTime -gt $since) { return $null }
    return $holder
}

# The check a hung run was in: the last "started:" line the test runner wrote ($Lines, logcat's
# TestRunner lines), or "an unknown check" when the log no longer has one (the app's logging over
# the run can push it out of logcat's buffer). It used to index into nothing there and throw,
# before the app was stopped.
function Get-HungCheck([string[]]$Lines) {
    $last = $Lines | Select-String 'started: (.+)' | Select-Object -Last 1
    if ($last) { $last.Matches[0].Groups[1].Value.Trim() } else { 'an unknown check' }
}
