#!/usr/bin/env bash
# Launches the minified release build (assembleRelease; signed with the debug key here) on the
# running emulator and fails if it crashes or isn't running a few seconds later.
#
# Debug builds and the instrumented tests aren't minified, so a class or constructor R8 strips
# only shows in a release: v1.25.1 and v1.25.2 crashed at launch that way (WorkManager's
# database constructor). This catches it before a release instead.
set -euo pipefail

APK=${1:-app/build/outputs/apk/release/app-release.apk}
PKG=com.astryx.book

# A copy already on the emulator (a higher versionCode left in a cached AVD) would refuse the
# install as a downgrade.
adb uninstall "$PKG" > /dev/null 2>&1 || true
adb install -r "$APK"
adb logcat -b crash -c
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 > /dev/null
# Long enough for startup initializers, the activity and the first page load to begin.
sleep 20

if adb logcat -b crash -d | grep -q "Process: $PKG"; then
  adb logcat -b crash -d
  echo "::error::The release build crashed at launch (see the crash log above)."
  exit 1
fi
if ! adb shell pidof "$PKG" > /dev/null; then
  echo "::error::The release build isn't running after launch."
  exit 1
fi
echo "The release build launched and is still running."
