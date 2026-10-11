# Emulator check
Device: sdk_gphone64_x86_64 / Android 14 (API 34) / WebView Current WebView package (name, version): (com.google.android.webview, 113.0.5672.136)
Commit: 072fa37

## Install

```
$ adb install -r -g app/build/outputs/apk/debug/app-debug.apk
Performing Streamed Install
Success
```

## Launch (logged out)

```
$ adb shell am start -W -n com.astryx.book.test/com.eepiemi.materialbook.MainActivity
Starting: Intent { cmp=com.astryx.book.test/com.eepiemi.materialbook.MainActivity }
Status: ok
LaunchState: COLD
Activity: com.astryx.book.test/com.eepiemi.materialbook.MainActivity
TotalTime: 3054
WaitTime: 3072
Complete
```
- screenshot `01-launch.png`
page: "https://m.facebook.com/?wtsid=rdr_0dJ8DrbeClksPVelR"

## Bundled page scripts run (#33)

```js
({settingsBridge: typeof SettingsBridge, downloadsObject: typeof AstryxDownloads, oldDownloadBridge: typeof DownloadBridge, settingsButton: !!document.getElementById("custom-settings-btn")})
```
{"settingsBridge": "object", "downloadsObject": "object", "oldDownloadBridge": "undefined", "settingsButton": false}
Network requests for scripts outside Facebook (should be none):
```
$ sh -c grep -iE 'githubusercontent|raw\.github|jsdelivr' emulator-check-results/logcat.txt || echo none
none
```

## Launcher shortcuts (#28)

```
$ sh -c adb shell dumpsys shortcut | sed -n '/Package: com.astryx.book.test/,/Package: /p' | grep -E 'ShortcutInfo|shortLabel=|intents=' | head -20
          ShortcutInfo {id=notifications, flags=0x85 [DynIc-rStr]
            shortLabel=Notifications, resId=0[null]
            intents=[Intent { act=android.intent.action.VIEW dat=https://m.facebook.com/... cmp=com.astryx.book.test/com.eepiemi.materialbook.MainActivity }/null]
          ShortcutInfo {id=marketplace, flags=0x85 [DynIc-rStr]
            shortLabel=Marketplace, resId=0[null]
            intents=[Intent { act=android.intent.action.VIEW dat=https://m.facebook.com/... cmp=com.astryx.book.test/com.eepiemi.materialbook.MainActivity }/null]
          ShortcutInfo {id=messages, flags=0x85 [DynIc-rStr]
            shortLabel=Messages, resId=0[null]
            intents=[Intent { act=android.intent.action.VIEW dat=https://m.facebook.com/... cmp=com.astryx.book.test/com.eepiemi.materialbook.MainActivity }/null]
```
longpress 'Astryxbook' at 918,1970
- screenshot `02-shortcut-menu.png`
not found: Notifications

## Share a link in through the share sheet (#28, #34)

```
$ adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT 'Look at this https://www.facebook.com/Meta'
Starting: Intent { act=android.intent.action.SEND typ=text/plain (has extras) }
```
- screenshot `03-share-sheet.png`
Share
Astryxbook Debug
Bluetooth
Chrome
Drive
Copy to clipboard
Drive
Just once
Always
tap 'Astryxbook' at 297,1719
tap 'Just once' at 742,2245
- screenshot `04-shared-link-opened.png`
page: "https://m.facebook.com/?next=https%3A%2F%2Fm.facebook.com%2Fsharer%2Fsharer.php%3Fu%3Dhttps%253A%252F%252Fwww.facebook.com%252FMeta&refsrc=deprecated&wtsid=rdr_0KLB8DGrID5JCxjXG"
Running instances of MainActivity after sharing: 0

## Share text without a link (#28)

```
$ adb shell am start -n com.astryx.book.test/com.eepiemi.materialbook.MainActivity -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT 'just some words'
Starting: Intent { act=android.intent.action.SEND typ=text/plain cmp=com.astryx.book.test/com.eepiemi.materialbook.MainActivity (has extras) }
Warning: Activity not started, intent has been delivered to currently running top-most instance.
```
- screenshot `05-share-text-only-toast.png`
page: "https://m.facebook.com/?next=https%3A%2F%2Fm.facebook.com%2Fsharer%2Fsharer.php%3Fu%3Dhttps%253A%252F%252Fwww.facebook.com%252FMeta&refsrc=deprecated&wtsid=rdr_0KLB8DGrID5JCxjXG"

## Start links: only Facebook pages are loaded (#34)


Fresh start with data=https://example.com/
```
$ adb shell am start -W -n com.astryx.book.test/com.eepiemi.materialbook.MainActivity -a android.intent.action.VIEW -d 'https://example.com/'
Starting: Intent { act=android.intent.action.VIEW dat=https://example.com/... cmp=com.astryx.book.test/com.eepiemi.materialbook.MainActivity }
Status: ok
LaunchState: COLD
Activity: com.astryx.book.test/com.eepiemi.materialbook.MainActivity
TotalTime: 2394
WaitTime: 2402
Complete
```
page: "https://m.facebook.com/?wtsid=rdr_0dJ8DrbeClksPVelR"

Fresh start with data=javascript:alert(document.cookie)
```
$ adb shell am start -W -n com.astryx.book.test/com.eepiemi.materialbook.MainActivity -a android.intent.action.VIEW -d 'javascript:alert(document.cookie)'
Starting: Intent { act=android.intent.action.VIEW dat=javascript: cmp=com.astryx.book.test/com.eepiemi.materialbook.MainActivity }
Status: ok
LaunchState: COLD
Activity: com.astryx.book.test/com.eepiemi.materialbook.MainActivity
TotalTime: 2374
WaitTime: 2384
Complete
```
page: "https://m.facebook.com/?wtsid=rdr_0dJ8DrbeClksPVelR"

Fresh start with data=file:///sdcard/Download/x.html
```
$ adb shell am start -W -n com.astryx.book.test/com.eepiemi.materialbook.MainActivity -a android.intent.action.VIEW -d 'file:///sdcard/Download/x.html'
Starting: Intent { act=android.intent.action.VIEW dat=file:///... cmp=com.astryx.book.test/com.eepiemi.materialbook.MainActivity }
Status: ok
LaunchState: COLD
Activity: com.astryx.book.test/com.eepiemi.materialbook.MainActivity
TotalTime: 2212
WaitTime: 2244
Complete
```
page: "https://m.facebook.com/?wtsid=rdr_0dJ8DrbeClksPVelR"

Fresh start with data=https://facebook.com:443@example.com/
```
$ adb shell am start -W -n com.astryx.book.test/com.eepiemi.materialbook.MainActivity -a android.intent.action.VIEW -d 'https://facebook.com:443@example.com/'
Starting: Intent { act=android.intent.action.VIEW dat=https://example.com/... cmp=com.astryx.book.test/com.eepiemi.materialbook.MainActivity }
Status: ok
LaunchState: COLD
Activity: com.astryx.book.test/com.eepiemi.materialbook.MainActivity
TotalTime: 2194
WaitTime: 2219
Complete
```
page: "https://m.facebook.com/?wtsid=rdr_0dJ8DrbeClksPVelR"

Fresh start with data=https://m.facebook.com/marketplace/
```
$ adb shell am start -W -n com.astryx.book.test/com.eepiemi.materialbook.MainActivity -a android.intent.action.VIEW -d 'https://m.facebook.com/marketplace/'
Starting: Intent { act=android.intent.action.VIEW dat=https://m.facebook.com/... cmp=com.astryx.book.test/com.eepiemi.materialbook.MainActivity }
Status: ok
LaunchState: COLD
Activity: com.astryx.book.test/com.eepiemi.materialbook.MainActivity
TotalTime: 2195
WaitTime: 2217
Complete
```
page: "https://m.facebook.com/marketplace/"
- screenshot `06-start-link-marketplace.png`

## A link tapped while the app runs reuses it (#34)

MainActivity instances before: 0
```
$ adb shell am start -W -n com.astryx.book.test/com.eepiemi.materialbook.MainActivity -a android.intent.action.VIEW -d https://m.facebook.com/groups/
Starting: Intent { act=android.intent.action.VIEW dat=https://m.facebook.com/... cmp=com.astryx.book.test/com.eepiemi.materialbook.MainActivity }
Warning: Activity not started, intent has been delivered to currently running top-most instance.
Status: ok
LaunchState: UNKNOWN (0)
Activity: com.astryx.book.test/com.eepiemi.materialbook.MainActivity
TotalTime: 0
WaitTime: 8
Complete
```
page: "https://m.facebook.com/groups_browse/"
MainActivity instances after: 0
```
$ adb shell am start -W -n com.astryx.book.test/com.eepiemi.materialbook.MainActivity -a android.intent.action.VIEW -d https://example.com/
Starting: Intent { act=android.intent.action.VIEW dat=https://example.com/... cmp=com.astryx.book.test/com.eepiemi.materialbook.MainActivity }
Warning: Activity not started, intent has been delivered to currently running top-most instance.
Status: ok
LaunchState: UNKNOWN (0)
Activity: com.astryx.book.test/com.eepiemi.materialbook.MainActivity
TotalTime: 0
WaitTime: 5
Complete
```
page: "https://m.facebook.com/groups_browse/"
MainActivity instances after a non-Facebook link: 0

## Facebook links from other apps, Android 12+ (#34)

```
$ adb shell pm get-app-links com.astryx.book.test
```
Before allowing links:
```
$ adb shell am start -W -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d https://m.facebook.com/watch/
Starting: Intent { act=android.intent.action.VIEW cat=[android.intent.category.BROWSABLE] dat=https://m.facebook.com/... }
Status: ok
LaunchState: WARM
Activity: com.android.chrome/org.chromium.chrome.browser.firstrun.FirstRunActivity
TotalTime: 628
WaitTime: 723
Complete
```
resumed: topResumedActivity=ActivityRecord{2a5d7d4 u0 com.android.chrome/org.chromium.chrome.browser.firstrun.FirstRunActivity t15}
- screenshot `07-facebook-link-before-allowing.png`
```
$ adb shell am start -W -n com.astryx.book.test/com.eepiemi.materialbook.MainActivity
Starting: Intent { cmp=com.astryx.book.test/com.eepiemi.materialbook.MainActivity }
Warning: Activity not started, its current task has been brought to the front
Status: ok
LaunchState: HOT
Activity: com.astryx.book.test/com.eepiemi.materialbook.MainActivity
TotalTime: 194
WaitTime: 408
Complete
```

## Settings: share button, text size, Open Facebook links here (#28, #34)

```js
SettingsBridge.onSettingsToggle(); "opened"
```
"opened"
- screenshot `08-settings.png`
Remove ads
Hide sponsored ads from feed
Messages in desktop mode
Open Messages inside the app using the desktop site (only for that section)
Download contents
Enable download button on media view
Copy to clipboard
Enable copy to clipboard button on media view
Customize feed
Show or hide items
Pinch to zoom
Use two fingers to zoom in or out
Desktop layout
Force desktop layout. Not suitable for small display.
Immersive mode
Hide system bars for a fullscreen experience
Keep screen on
Off
Text size
back button
Share this page
Astryxbook settings
tap 'Share this page' at 1009,189
- screenshot `09-share-this-page.png`
not found: Text size
not found: Open Facebook links here
- screenshot `10-page-at-150-percent.png`
```js
getComputedStyle(document.body).fontSize
```
"13px"
Allowing the links (what the system page does):
```
$ adb shell pm set-app-links-user-selection --user 0 --package com.astryx.book.test true all
```
```
$ adb shell pm get-app-links com.astryx.book.test
```
```
$ adb shell am start -W -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d https://m.facebook.com/watch/
Starting: Intent { act=android.intent.action.VIEW cat=[android.intent.category.BROWSABLE] dat=https://m.facebook.com/... }
Warning: Activity not started, intent has been delivered to currently running top-most instance.
Status: ok
LaunchState: UNKNOWN (0)
Activity: com.astryx.book.test/com.eepiemi.materialbook.MainActivity
TotalTime: 0
WaitTime: 10
Complete
```
resumed: topResumedActivity=ActivityRecord{1eb2b28 u0 com.astryx.book.test/com.eepiemi.materialbook.MainActivity t14}
page: "https://m.facebook.com/watch/"
- screenshot `11-facebook-link-after-allowing.png`
MainActivity instances: 0

## Links leaving the app (#34)

market: link (another app's link) should ask first:
```js
(function(){var a=document.createElement('a');a.href='market://details?id=com.android.chrome';a.textContent='x';document.body.appendChild(a);a.click();return 'clicked';})()
```
"clicked"
- screenshot `12-market-link-asks.png`
exists 'Open in another app?' at 521,1093
tap 'Cancel' at 580,1351
resumed: topResumedActivity=ActivityRecord{1eb2b28 u0 com.astryx.book.test/com.eepiemi.materialbook.MainActivity t14}
file: link should be refused:
```js
(function(){var a=document.createElement('a');a.href='file:///sdcard/Download/x.html';a.textContent='x';document.body.appendChild(a);a.click();return 'clicked';})()
```
"clicked"
- screenshot `13-file-link-refused.png`
resumed: topResumedActivity=ActivityRecord{1eb2b28 u0 com.astryx.book.test/com.eepiemi.materialbook.MainActivity t14}
content: link should be refused:
```js
(function(){var a=document.createElement('a');a.href='content://media/external/images/media/1';a.textContent='x';document.body.appendChild(a);a.click();return 'clicked';})()
```
"clicked"
- screenshot `14-content-link-refused.png`
resumed: topResumedActivity=ActivityRecord{1eb2b28 u0 com.astryx.book.test/com.eepiemi.materialbook.MainActivity t14}
javascript: link (the page runs it itself, as in any browser; it never reaches the app):
```js
(function(){var a=document.createElement('a');a.href='javascript:void(document.title=\"ran\")';a.textContent='x';document.body.appendChild(a);a.click();return 'clicked';})()
```
"clicked"
```js
document.title
```
"Discover Popular Videos | Facebook"
intent:// link naming another app should open only its web fallback:
```js
(function(){var a=document.createElement('a');a.href='intent://profile#Intent;scheme=fb;package=com.facebook.katana;S.browser_fallback_url=https%3A%2F%2Fexample.org%2F;end';a.textContent='x';document.body.appendChild(a);a.click();return 'clicked';})()
```
"clicked"
resumed: topResumedActivity=ActivityRecord{cc50463 u0 com.android.chrome/org.chromium.chrome.browser.firstrun.FirstRunActivity t16}
- screenshot `15-intent-link-fallback.png`
```
$ adb shell am start -W -n com.astryx.book.test/com.eepiemi.materialbook.MainActivity
Starting: Intent { cmp=com.astryx.book.test/com.eepiemi.materialbook.MainActivity }
Warning: Activity not started, its current task has been brought to the front
Status: ok
LaunchState: HOT
Activity: com.astryx.book.test/com.eepiemi.materialbook.MainActivity
TotalTime: 368
WaitTime: 387
Complete
```
Plain web link opens directly in the browser, without trackers (#28):
```js
(function(){var a=document.createElement('a');a.href='https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2F%3Fkeep%3D1%26utm_source%3Dfb%26fbclid%3DIwAR123&h=AT0';a.textContent='x';document.body.appendChild(a);a.click();return 'clicked';})()
```
"clicked"
resumed: topResumedActivity=ActivityRecord{1437f68 u0 com.android.chrome/org.chromium.chrome.browser.firstrun.FirstRunActivity t17}
- screenshot `16-web-link-browser.png`
Link the browser was sent: 
```
$ adb shell am start -W -n com.astryx.book.test/com.eepiemi.materialbook.MainActivity
Starting: Intent { cmp=com.astryx.book.test/com.eepiemi.materialbook.MainActivity }
Warning: Activity not started, its current task has been brought to the front
Status: ok
LaunchState: HOT
Activity: com.astryx.book.test/com.eepiemi.materialbook.MainActivity
TotalTime: 266
WaitTime: 275
Complete
```

## WebView hardening: geolocation off, no file access (#28)

```js
new Promise(function(r){navigator.geolocation.getCurrentPosition(function(){r("got a position")},function(e){r("refused: code "+e.code+" "+e.message)},{timeout:8000})})
```
"refused: code 1 User denied Geolocation"
```js
fetch("file:///sdcard/Pictures/astryx-test-photo.png").then(function(){return "file read"},function(e){return "refused: "+e})
```
"refused: TypeError: Failed to fetch"

## Share this page sends a clean www link (#28)

```
$ adb shell am start -W -n com.astryx.book.test/com.eepiemi.materialbook.MainActivity -a android.intent.action.VIEW -d 'https://m.facebook.com/Meta?fbclid=IwAR123&utm_source=test&mibextid=abc'
Starting: Intent { act=android.intent.action.VIEW dat=https://m.facebook.com/... cmp=com.astryx.book.test/com.eepiemi.materialbook.MainActivity }
Status: ok
LaunchState: COLD
Activity: com.astryx.book.test/com.eepiemi.materialbook.MainActivity
TotalTime: 2153
WaitTime: 2166
Complete
```
page: "https://m.facebook.com/?next=https%3A%2F%2Fm.facebook.com%2FMeta%3Ffbclid%3DIwAR123%26utm_source%3Dtest%26mibextid%3Dabc&wtsid=rdr_069UMlTTSPVDwLdBN"
```js
SettingsBridge.onSettingsToggle(); "opened"
```
"opened"
tap 'Share this page' at 1009,189
- screenshot `17-share-this-page-clean-link.png`
Sharing link
https://www.facebook.com/?next=https%3A%2F%2Fm.facebook.com%2FMeta%3Ffbclid%3DIwAR123%26utm_source%3Dtest%26mibextid%3Dabc&wtsid=rdr_069UMlTTSPVDwLdBN
Copy
No recommended people to share with
Nearby Share
Astryxbook Debug
Chrome
Drive
Copy to clipboard
Drive
```
$ adb shell am start -W -n com.astryx.book.test/com.eepiemi.materialbook.MainActivity
Starting: Intent { cmp=com.astryx.book.test/com.eepiemi.materialbook.MainActivity }
Warning: Activity not started, intent has been delivered to currently running top-most instance.
Status: ok
LaunchState: UNKNOWN (0)
Activity: com.astryx.book.test/com.eepiemi.materialbook.MainActivity
TotalTime: 0
WaitTime: 5
Complete
```

## Downloads: only photos and videos, only from Facebook's main frame (#33, #34)

Files in Downloads: 0
```js
AstryxDownloads.postMessage(JSON.stringify({data:'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',mimeType:'image/png'}));'posted photo'
```
"posted photo"
- screenshot `18-download-photo-toast.png`
Files in Downloads after a photo: 1
```js
AstryxDownloads.postMessage(JSON.stringify({data:'data:application/vnd.android.package-archive;base64,UEsDBBQAAAA=',mimeType:'application/vnd.android.package-archive'}));'posted apk'
```
"posted apk"
- screenshot `19-download-apk-refused.png`
Files in Downloads after an APK: 1
```js
(function(){var f=document.createElement('iframe');f.srcdoc='<script>try{AstryxDownloads.postMessage(JSON.stringify({data:"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",mimeType:"image/png"}));parent.__frameSent=typeof AstryxDownloads}catch(e){parent.__frameSent=String(e)}<\/script>';document.body.appendChild(f);return new Promise(function(r){setTimeout(function(){r(window.__frameSent)},1500)})})()
```
null
Files in Downloads after an iframe tried: 1
```
$ adb shell ls -l /sdcard/Download
total 8
-rwxrwx--- 1 u0_a173 media_rw 99 2026-10-11 23:16 1791760596246.png
```

## Photo upload: the page's file chooser opens the picker (#28)

```js
(function(){var i=document.createElement('input');i.type='file';i.accept='image/*';i.id='astryx-test-file';i.onchange=function(){window.__picked=Array.prototype.map.call(i.files,function(f){return f.name+' '+f.size})};document.body.appendChild(i);i.click();return 'chooser requested'})()
```
"chooser requested"
- screenshot `20-photo-picker.png`
This app can only access the photos you select
Recent
Photo taken on Oct 11, 2026, 11:16:36 PM
Photo taken on Oct 11, 2026, 11:04:10 PM
Cancel
Photos
Photos
Albums
Albums
More options
tap 'Photo taken' at 177,1586
- screenshot `21-photo-picked.png`
```js
window.__picked || null
```
["1000000020.png 99"]
```
$ adb shell am start -W -n com.astryx.book.test/com.eepiemi.materialbook.MainActivity
Starting: Intent { cmp=com.astryx.book.test/com.eepiemi.materialbook.MainActivity }
Status: ok
LaunchState: WARM
Activity: com.astryx.book.test/com.eepiemi.materialbook.MainActivity
TotalTime: 756
WaitTime: 761
Complete
```

## Backups leave the Facebook session out (#33)

```
$ timeout 60 adb root
restarting adbd as root
```
```
$ adb shell bmgr enable true
Backup Manager now enabled
```
```
$ adb shell bmgr transport com.android.localtransport/.LocalTransport
Selected transport com.android.localtransport/.LocalTransport (formerly com.google.android.gms/.backup.BackupTransportService)
```
```
$ adb shell settings put secure backup_local_transport_parameters fake_encryption_flag=true
```
```
$ timeout 180 adb shell bmgr backupnow --non-incremental com.astryx.book.test
Running non-incremental backup for 1 requested packages.
Package com.astryx.book.test with result: Transport rejected package because it wasn't able to process it at the time
Backup finished with result: Success
```
```
$ sh -c adb logcat -d -s BackupManagerService:* LocalTransport:* FullBackupEngine:* PFTBT:* | grep -iE 'reject|exclud|include|com.astryx.book.test|size' | head -30
10-11 23:17:12.117   523  9219 I PFTBT   : Initiating full-data transport backup of com.astryx.book.test token: 1035173378
10-11 23:17:12.119   523  9220 D BackupManagerService: [UserID:0] awaiting agent for ApplicationInfo{ab22a09 com.astryx.book.test}
10-11 23:17:12.121   523  2302 D BackupManagerService: [UserID:0] agentConnected pkg=com.astryx.book.test agent=android.os.BinderProxy@ea756e6
10-11 23:17:12.130   523  9219 W PFTBT   : Error -1002 backing up com.astryx.book.test
10-11 23:17:12.130   523  9219 I PFTBT   : Transport rejected backup of com.astryx.book.test, skipping
10-11 23:17:12.131   523  9219 I PFTBT   : Unbinding agent in com.astryx.book.test
```
Backed-up files (should be only the settings DataStore):
```
$ adb shell find /data/cache/backup -path '*com.astryx.book.test*' -type f 2>/dev/null | head
```
Cookies the app holds on the device (what must not be in the backup):
```
$ adb shell ls -l /data/data/com.astryx.book.test/app_webview/Default/ 2>/dev/null | grep -i cookie
-rw------- 1 u0_a192 u0_a192 24576 2026-10-11 23:17 Cookies
-rw------- 1 u0_a192 u0_a192     0 2026-10-11 23:17 Cookies-journal
```

## Errors in logcat

```
$ sh -c grep -E 'FATAL|AndroidRuntime' emulator-check-results/logcat.txt || echo none
none
```

Done.
