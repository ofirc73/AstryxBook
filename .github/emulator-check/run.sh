#!/usr/bin/env bash
# Hands-on check of the debug app on an emulator: drives it like a user (adb input,
# uiautomator, DevTools) and writes screenshots plus a log to emulator-check-results/.
# Run by emulator-check.yml; also works against any adb device with the debug APK built.
# Not a test suite: it never fails the job, the log and screenshots are what to read.
set -u
# Git Bash on Windows would rewrite /sdcard/... arguments into Windows paths.
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")/../.."
OUT=emulator-check-results
rm -rf "$OUT"; mkdir -p "$OUT"
LOG=$OUT/log.md
PKG=com.astryx.book.test
ACT=$PKG/com.eepiemi.materialbook.MainActivity
HERE=.github/emulator-check
PY=${PY:-python3}
n=0

step() { printf '\n## %s\n\n' "$*" >> "$LOG"; echo "== $*"; }
note() { echo "$*" | tee -a "$LOG"; }
shot() {
    n=$((n + 1))
    local f; f=$(printf '%02d-%s.png' "$n" "$1")
    adb exec-out screencap -p > "$OUT/$f"
    echo "- screenshot \`$f\`" >> "$LOG"
}
run() {
    { echo '```'; echo "\$ $*"; } >> "$LOG"
    "$@" 2>&1 | head -60 | tee -a "$LOG"
    echo '```' >> "$LOG"
}
ui() { "$PY" "$HERE/ui.py" "$@" 2>&1 | tee -a "$LOG"; return "${PIPESTATUS[0]}"; }
js() { { echo '```js'; echo "$1"; echo '```'; } >> "$LOG"; "$PY" "$HERE/cdp.py" "$PKG" "$1" 2>&1 | tee -a "$LOG"; }
url() { note "page: $("$PY" "$HERE/cdp.py" "$PKG" 'location.href' 2>&1)"; }
resumed() { note "resumed: $(adb shell dumpsys activity activities | grep -m1 -E 'mResumedActivity|topResumedActivity' | sed 's/^ *//')"; }
# One "Hist #" line per activity record; other dumpsys lines repeat the same record.
instances() { adb shell dumpsys activity activities | grep -cE "Hist #[0-9]+: ActivityRecord\{.*$PKG/com.eepiemi.materialbook.MainActivity"; }
fresh_start() { adb shell am force-stop "$PKG"; sleep 1; run adb shell am start -W -n "$ACT" "$@"; sleep "${WAIT:-25}"; }
back_to_app() { run adb shell am start -W -n "$ACT"; sleep 3; }

echo "# Emulator check" > "$LOG"
note "Device: $(adb shell getprop ro.product.model) / Android $(adb shell getprop ro.build.version.release) (API $(adb shell getprop ro.build.version.sdk)) / WebView $(adb shell dumpsys webviewupdate | grep -m1 -oE 'Current WebView package.*')"
note "Commit: $(git rev-parse --short HEAD)"

adb logcat -c
adb logcat -v time 'AstryxbookPiP:D' 'ActivityTaskManager:I' 'AndroidRuntime:E' 'chromium:W' '*:S' > "$OUT/logcat.txt" &
LOGCAT_PID=$!

step "Install"
run adb install -r -g app/build/outputs/apk/debug/app-debug.apk
# A test photo for the upload check.
adb push .github/emulator-check/test-photo.png /sdcard/Pictures/astryx-test-photo.png > /dev/null
adb shell content call --method scan_volume --uri content://media --arg external_primary > /dev/null 2>&1

step "Launch (logged out)"
fresh_start
shot launch
url

step "Bundled page scripts run (#33)"
# scripts.js only ever comes from the APK now; these show it ran on the page.
js '({settingsBridge: typeof SettingsBridge, downloadsObject: typeof AstryxDownloads, oldDownloadBridge: typeof DownloadBridge, settingsButton: !!document.getElementById("custom-settings-btn")})'
note "Network requests for scripts outside Facebook (should be none):"
run sh -c "grep -iE 'githubusercontent|raw\\.github|jsdelivr' $OUT/logcat.txt || echo none"

step "Launcher shortcuts (#28)"
run sh -c "adb shell dumpsys shortcut | sed -n '/Package: $PKG/,/Package: /p' | grep -E 'ShortcutInfo|shortLabel=|intents=' | head -20"
adb shell input keyevent HOME; sleep 2
adb shell input swipe 540 2100 540 500 300; sleep 3
ui scrollto "Astryxbook" > /dev/null
if ui longpress "Astryxbook"; then
    sleep 2; shot shortcut-menu
    if ui tap "Notifications"; then
        sleep 20; shot shortcut-notifications; url
    fi
fi

step "Share a link in through the share sheet (#28, #34)"
# What Chrome's Share does: an ACTION_SEND text/plain intent the system offers to the apps
# that take it.
run adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "'Look at this https://www.facebook.com/Meta'"
sleep 4; shot share-sheet
ui texts | head -30 > /dev/null
if ui tap "Astryxbook"; then
    sleep 2
    ui tap "Just once" > /dev/null || ui tap "Always" > /dev/null
    sleep 20; shot shared-link-opened; url
fi
note "Running instances of MainActivity after sharing: $(instances)"

step "Share text without a link (#28)"
run adb shell am start -n "$ACT" -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "'just some words'"
sleep 1; shot share-text-only-toast
sleep 3; url

step "Start links: only Facebook pages are loaded (#34)"
for link in "https://example.com/" "javascript:alert(document.cookie)" "file:///sdcard/Download/x.html" "https://facebook.com:443@example.com/" "https://m.facebook.com/marketplace/"; do
    note ""
    note "Fresh start with data=$link"
    WAIT=20 fresh_start -a android.intent.action.VIEW -d "'$link'"
    url
done
shot start-link-marketplace

step "A link tapped while the app runs reuses it (#34)"
note "MainActivity instances before: $(instances)"
run adb shell am start -W -n "$ACT" -a android.intent.action.VIEW -d https://m.facebook.com/groups/
sleep 15; url
note "MainActivity instances after: $(instances)"
run adb shell am start -W -n "$ACT" -a android.intent.action.VIEW -d https://example.com/
sleep 5; url
note "MainActivity instances after a non-Facebook link: $(instances)"

step "Facebook links from other apps, Android 12+ (#34)"
run adb shell pm get-app-links "$PKG"
note "Before allowing links:"
run adb shell am start -W -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d https://m.facebook.com/watch/
sleep 5; resumed; shot facebook-link-before-allowing
back_to_app

step "Settings: share button, text size, Open Facebook links here (#28, #34)"
js 'SettingsBridge.onSettingsToggle(); "opened"'
sleep 3; shot settings
ui texts > /dev/null
if ui tap "Share this page"; then
    sleep 3; shot share-this-page; adb shell input keyevent BACK; sleep 2
fi
if ui scrollto "Text size" && ui tap "Text size"; then
    sleep 2; shot text-size-dialog
    ui tap "150%" && sleep 1
fi
if ui scrollto "Open Facebook links here"; then
    shot settings-open-links-row
    ui tap "Open Facebook links here"; sleep 4; shot system-open-by-default
    ui tap "Add link" > /dev/null && { sleep 2; shot system-add-links; adb shell input keyevent BACK; sleep 1; }
    adb shell input keyevent BACK; sleep 2
fi
adb shell input keyevent BACK; sleep 5
shot page-at-150-percent
js 'getComputedStyle(document.body).fontSize'
note "Allowing the links (what the system page does):"
run adb shell pm set-app-links-user-selection --user 0 --package "$PKG" true all
run adb shell pm get-app-links "$PKG"
run adb shell am start -W -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d https://m.facebook.com/watch/
sleep 10; resumed; url; shot facebook-link-after-allowing
note "MainActivity instances: $(instances)"

step "Links leaving the app (#34)"
click_link() {
    js "(function(){var a=document.createElement('a');a.href='$1';a.textContent='x';document.body.appendChild(a);a.click();return 'clicked';})()"
}
note "market: link (another app's link) should ask first:"
click_link "market://details?id=com.android.chrome"
sleep 3; shot market-link-asks
ui exists "Open in another app?"
ui tap "Cancel"; sleep 1; resumed
note "file: link should be refused:"
click_link "file:///sdcard/Download/x.html"
sleep 1; shot file-link-refused; resumed
note "content: link should be refused:"
click_link "content://media/external/images/media/1"
sleep 1; shot content-link-refused; resumed
note "javascript: link (the page runs it itself, as in any browser; it never reaches the app):"
click_link 'javascript:void(document.title=\"ran\")'
sleep 1; js 'document.title'
note "intent:// link naming another app should open only its web fallback:"
click_link "intent://profile#Intent;scheme=fb;package=com.facebook.katana;S.browser_fallback_url=https%3A%2F%2Fexample.org%2F;end"
sleep 6; resumed; shot intent-link-fallback
back_to_app
note "Plain web link opens directly in the browser, without trackers (#28):"
click_link "https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2F%3Fkeep%3D1%26utm_source%3Dfb%26fbclid%3DIwAR123&h=AT0"
sleep 6; resumed; shot web-link-browser
note "Link the browser was sent: $(adb shell dumpsys activity activities | grep -m1 -oE 'dat=[^ ]*example[^ ]*')"
back_to_app

step "WebView hardening: geolocation off, no file access (#28)"
js 'new Promise(function(r){navigator.geolocation.getCurrentPosition(function(){r("got a position")},function(e){r("refused: code "+e.code+" "+e.message)},{timeout:8000})})'
js 'fetch("file:///sdcard/Pictures/astryx-test-photo.png").then(function(){return "file read"},function(e){return "refused: "+e})'

step "Share this page sends a clean www link (#28)"
WAIT=20 fresh_start -a android.intent.action.VIEW -d "'https://m.facebook.com/Meta?fbclid=IwAR123&utm_source=test&mibextid=abc'"
url
js 'SettingsBridge.onSettingsToggle(); "opened"'
sleep 3
if ui tap "Share this page"; then
    sleep 3; shot share-this-page-clean-link
    ui texts | grep -i "facebook" | head -5
    adb shell input keyevent BACK; sleep 1
fi
back_to_app

step "Downloads: only photos and videos, only from Facebook's main frame (#33, #34)"
PNG='iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='
dl_count() { adb shell ls /sdcard/Download 2>/dev/null | wc -l; }
note "Files in Downloads: $(dl_count)"
js "AstryxDownloads.postMessage(JSON.stringify({data:'data:image/png;base64,$PNG',mimeType:'image/png'}));'posted photo'"
sleep 1; shot download-photo-toast; sleep 2
note "Files in Downloads after a photo: $(dl_count)"
js "AstryxDownloads.postMessage(JSON.stringify({data:'data:application/vnd.android.package-archive;base64,UEsDBBQAAAA=',mimeType:'application/vnd.android.package-archive'}));'posted apk'"
sleep 1; shot download-apk-refused; sleep 2
note "Files in Downloads after an APK: $(dl_count)"
js "(function(){var f=document.createElement('iframe');f.srcdoc='<script>try{AstryxDownloads.postMessage(JSON.stringify({data:\"data:image/png;base64,$PNG\",mimeType:\"image/png\"}));parent.__frameSent=typeof AstryxDownloads}catch(e){parent.__frameSent=String(e)}<\/script>';document.body.appendChild(f);return new Promise(function(r){setTimeout(function(){r(window.__frameSent)},1500)})})()"
sleep 2
note "Files in Downloads after an iframe tried: $(dl_count)"
run adb shell ls -l /sdcard/Download

step "Photo upload: the page's file chooser opens the picker (#28)"
js "(function(){var i=document.createElement('input');i.type='file';i.accept='image/*';i.id='astryx-test-file';i.onchange=function(){window.__picked=Array.prototype.map.call(i.files,function(f){return f.name+' '+f.size})};document.body.appendChild(i);i.click();return 'chooser requested'})()"
sleep 4; shot photo-picker
ui texts | head -20 > /dev/null
# The photo picker lists photos by description ("Photo taken on …").
ui tap "Photo taken" || ui tap "astryx-test-photo" || ui tap "Image"
sleep 3; shot photo-picked
js 'window.__picked || null'
adb shell input keyevent BACK; sleep 1; back_to_app

step "Backups leave the Facebook session out (#33)"
run timeout 60 adb root; sleep 5; adb wait-for-device
run adb shell bmgr enable true
run adb shell bmgr transport com.android.localtransport/.LocalTransport
run timeout 180 adb shell bmgr backupnow "$PKG"
sleep 3
note "Backed-up files (should be only the settings DataStore):"
run adb shell "find /data/cache/backup -path '*$PKG*' -type f 2>/dev/null | head"
for f in $(adb shell "find /data/cache/backup -path '*$PKG*' -type f 2>/dev/null"); do
    run adb shell "tar -tvf '$f' 2>/dev/null || ls -l '$f'"
done
note "Cookies the app holds on the device (what must not be in the backup):"
run adb shell "ls -l /data/data/$PKG/app_webview/Default/ 2>/dev/null | grep -i cookie"

step "Errors in logcat"
kill "$LOGCAT_PID" 2>/dev/null
run sh -c "grep -E 'FATAL|AndroidRuntime' $OUT/logcat.txt || echo none"
note ""
note "Done."
exit 0
