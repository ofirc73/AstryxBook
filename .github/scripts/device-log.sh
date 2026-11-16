#!/usr/bin/env bash
# The device's log after a failed test run, saved to $1: the app's and the test runner's lines, and
# why a process ended (ActivityManager's kill reasons, lmkd, a native crash). Shared by ci.yml (a
# failed part's artifact) and tools/canary.ps1 (a failed daily check, kept on the PC). $ADB names
# adb when it isn't on PATH; ANDROID_SERIAL picks the device.
set -euo pipefail
"${ADB:-adb}" logcat -d -v time -s AstryxbookPiP:D Astryxbook:D TestRunner:I ActivityTaskManager:I \
  WindowManager:W AndroidRuntime:E ActivityManager:I lmkd:I DEBUG:F libc:F > "$1"
