# Astryxbook

Android wrapper around m.facebook.com (Kotlin, Jetpack Compose,
compose-webview-multiplatform, Media3). Package `com.eepiemi.materialbook`,
applicationId `com.astryx.book`. Fork-specific changes are documented in
`FORK_CHANGES.md` (upstream's own log is `CHANGE.md`); update it with every
user-visible change.

- **Packages (since 2026-10-28):** the fork's own Kotlin files live in `com.astryx.book.*`
  (`app/src/{main,test,androidTest}/java/com/astryx/book/…`, with the same sub-packages: `.utils`,
  `.ui.screens`, …); a new one goes there too. Only upstream's files (the 29 also in
  `upstream/main`, e.g. `MainActivity.kt`, `MaterialbookWV.kt`, `SettingsDataStore.kt`) stay in
  `com.eepiemi.materialbook.*`, and so does the Android `namespace` (`R`, `BuildConfig` and the
  manifest's relative class names come from it), so upstream merges don't conflict and
  `MainActivity` keeps its name (renamed, it would drop the launcher icon from users' home
  screens). A fork activity, service or receiver goes in the manifest by its full name (a
  relative `.Name` resolves to `com.eepiemi.materialbook`); a class something creates by
  reflection needs its keep rule under the new package. A class name saved on the device must
  survive a move: the Facebook notifications job is re-enqueued with `ExistingPeriodicWorkPolicy.UPDATE`,
  which rewrites the worker class WorkManager saved (tried on the emulator: a job saved by v1.36.4
  named `com.eepiemi…FacebookCountsWorker`, the update rewrote it, schedule kept). Paths below like
  `ui/screens/MessagesLayer.kt` are under whichever of the two packages holds the file.

## Build, install, test (Windows / PowerShell)

- Release build for a device: `.\gradlew.bat assembleRelease "-PversionNameOverride=99.99.N"`.
  The quotes are required in PowerShell. A plain local build is `versionCode`
  13, lower than the installed app, so `adb install -r` needs the override
  (bump N for each install).
- Debug builds install as a separate app (`com.astryx.book.test`). Use it
  for device checks when the phone has an official (release-key) build:
  a local release APK won't install over it. `connectedDebugAndroidTest`
  uninstalls that app when it finishes (losing its Facebook login), so run
  instrumented tests before or after a device session, not in the middle.
  To keep the app (and its login), install with `installDebug installDebugAndroidTest` and run
  `adb shell am instrument -w [-e class <fqcn>] com.astryx.book.test.test/androidx.test.runner.AndroidJUnitRunner`.
- `installDebug` installs on every connected device: with the phone plugged in too, set
  `ANDROID_SERIAL` (or use `adb -e`) so the debug app doesn't land on the user's phone.
- `.\gradlew.bat testDebugUnitTest` (use the variant task; `--tests` filtering
  doesn't work on plain `test`), `.\gradlew.bat connectedDebugAndroidTest`
  (needs a device), `.\gradlew.bat lintDebug`. CI (`ci.yml`) gates on
  `test`, `:app:lintDebug` and `connectedAndroidTest`. The instrumented tests run in four parts on
  four emulators (`instrumented-shard`, the runner's `numShards`/`shardIndex`) next to the release
  APK's launch check (`release-launch`, the build job's APK); the required `instrumented-tests`
  check passes when all three do. Gradle's build cache is kept between runs (`setup-gradle`,
  `--build-cache`), written only by main's release runs: entries from a by-hand `ci.yml` run on main
  (workflow "CI" in their key) were what every PR restored first, and once stale they doubled each
  test part's time (2026-10-27; found in the parts' "Build App and Test APKs" step). The emulator's
  AVD cache (`ci.yml`, `facebook-canary.yml`) is saved right after the AVD is made, never at a job's
  end: saved then, a release run's AVD kept v1.36.6 installed and every branch build failed to
  install as a downgrade (2026-10-30). A newer CI run for the same PR (a push, a reopen) cancels the older one; on
  `main` and inside `create-release.yml` nothing is cancelled. Run CI by hand on a branch with
  `gh workflow run ci.yml --ref <branch>`. The coverage report (`coverage.yml`) is its own
  workflow, started after each CI or release run that passed (`workflow_run`); it gates nothing.
- A local release APK is signed with the **debug** key (`app/build.gradle.kts`): it's for testing
  only and never distributable. The published APK is re-signed with the release keystore by
  `create-release.yml` (GitHub secrets); that's also why a local release build can't update a phone
  that has an official one.
- Fixed-page tests (`*JsTest`) load their test page through `PageFixture` (one WebView setup:
  load, eval, run a bundled script, wait; laid out only when given a size). `AppHarness` also holds
  what the app-level tests and the live checks share: Messages on screen (`layerOpen`) and a real
  touch on an element (`tapOn` with a JS expression for it; `tapIn` with its box). Keep test code
  free of repeats: reuse or generalize those, and the live checks' `shownJs(selector)` (elements
  with a size) and `MESSAGE_BOX_FIND_JS`, before writing a new snippet; check through the app's own
  behaviour (its log lines, its state) rather than re-reading Facebook's page where that's enough.
- App-level tests drive the real `MainActivity` through `AppHarness` (androidTest; one class per
  feature, listed in FORK_CHANGES' Testing). Test pages loaded with `loadPage` have no WebView
  history (`asHistory` gives them one, but a reload then fetches the real page). Loading a second
  test page at the same address leaves `evaluateJavascript` unanswered: give each its own
  (`?n=2`). The lock-screen
  and app-lock tests need the screen lock's PIN: `-e pin 1234` on the emulator. An emulator stuck
  on its lock screen (plain `input swipe` and `input text` ignored): `input touchscreen swipe`
  brings up the PIN pad. Don't `adb reboot` it (tests then failed at random, "evaluateJavascript
  never returned"); `adb emu kill` and start it again (minimized: a windowed start may not come
  up), which quick-boots the saved snapshot with older APKs, so reinstall both before testing. The live
  checks against real Facebook (`LiveFacebookCanaryTest`; "canary" in file, class and argument names
  means these, and docs call them live checks) are skipped unless run with
  `-e canary true` on a device where the debug app is logged in; `tools/canary.ps1` builds main
  in `../astryx-canary` and runs them on the emulator (`-NoBuild` for the installed app).
  Every test added or changed goes with the live checks: add or adjust the live check for what it
  covers, or say in FORK_CHANGES' Testing why it has none (it doesn't depend on Facebook's page, or
  can't be checked live). The fixed-page tests keep passing when Facebook changes. A change to the
  Messages layer or to Back runs the whole live suite on the local emulator, with the chat
  (`-e fbChat`), before it's merged: each check starts a fresh app, so only the sequence check
  (`aPostThenAReelFromAChat_backEndsOnTheFeed_withNothingLeftBehind`) catches one flow breaking the
  next (v1.35.16 shipped a reel that replayed on every Back that way). `facebook-canary.yml` runs them daily on GitHub (from 08:37 Israel; GitHub drops many scheduled
  runs, so it's scheduled every 2 hours until 17:37 UTC, and the later runs stand down once one
  has run),
  logged in to a Facebook test account through the `FB_TEST_COOKIES` secret (`-e fbCookies`, base64; the Messages
  layer checks open the chat in `-e fbChat`: its address, `e2ee/t/<id>` or a bare id; skipped without
  it), and reports failures
  in one "Daily Facebook check failed" issue; a check that passed only after a workaround (the reel
  check's second Home) prints a `CANARY NOTE:` line (`note()`), shown as a warning on the run. A part
  Facebook gave nothing to check this run (no ad came) is a `gap(key, …)`; the same key 7 runs in a
  row opens a "parts unchecked" issue. A part no run can check (the test account never sees it) is
  `untestable(…)`: in the output, not a warning. A check that finds none of what it checks fails: never a note
  or gap for that (the feed check's "no feed posts" note hid Remove ads breaking for days). Never put a
  personal account's cookies there. At most 2 full runs a UTC day (`MAX_A_DAY`, any branch, by
  hand too): Facebook ended the test account's session the night after each day of 10+ runs, so
  check a branch on the local emulator (`tools/canary.ps1`), not with `gh workflow run`. When the
  issue says the session expired, the user renews it with `tools/renew-fb-cookies.ps1` (from the
  emulator's debug app login; it never shows the values, and starts one run past the limit).
  The Messages layer checks run only on the local emulator
  (`tools/canary.ps1 -Chat e2ee/t/<id>`): the test account's chat is end-to-end encrypted, and
  GitHub's emulator, logged in by cookies alone, gets its restore prompt instead of its messages
  (tried 2026-10-24 with an `FB_TEST_CHAT` secret: all three failed). Leave that secret unset.
- Release builds are minified (R8 full mode); debug builds and the tests aren't. A class or
  constructor something creates by reflection needs a keep rule in `proguard-rules.pro` (v1.25.1
  and v1.25.2 crashed at launch: WorkManager's Room database). The instrumented-tests job also
  launches the release APK (`.github/scripts/release-smoke-test.sh`); locally, check a release
  build with `assembleRelease` and the same script, passing the APK path.
- Kotlin sources: write bidirectional marks and icon-font glyphs as `\u` escapes, never as the
  characters themselves (lint `BidiSpoofing` fails the build on literal bidi marks).
- Pushing to `main` runs `create-release.yml` (which calls `ci.yml`); it
  auto-versions from conventional commits (`feat:` = minor bump) and
  publishes a release (every commit type bumps at least the patch, `test:` too). A push
  touching only docs, tests or repo tooling (`*.md`, `LICENSE`, `fastlane/`, `assets/`,
  `app/src/test/`, `app/src/androidTest/`, `.github/`, `.gitignore`, `lint.xml`, `tools/`)
  skips it via `paths-ignore`; those commits go out with the next release. So does a push whose commits since the last release are all
  Dependabot's (`compute-version`'s check; its security updates touch the Gradle files): they go
  out with the next real release.

## WebView and PiP code (`ui/screens/MaterialbookWV.kt`)

- compose-webview's navigator replays only its *last* event to a WebView that
  isn't attached yet. Never call `navigator.evaluateJavaScript` unconditionally
  at composition: guard on `state.loadingState is LoadingState.Finished` or a
  trigger counter `> 0`, or it replaces the initial `loadUrl` and the page
  never loads.
- Native -> JS uses Compose trigger counters/state passed from `MainActivity`
  into `MaterialbookWebView`; JS -> native goes through the `AstryxPage` message listener
  (`PageBridge.kt`, main Facebook frame only), with `PipBridge` etc. as the fallback on
  WebViews older than 83. New page -> app calls go there, not in a new `addJavascriptInterface`.
- PiP focus mode (`PIP_FOCUS_MODE_JS` / `PIP_RESTORE_MODE_JS`) was tuned
  through live DevTools sessions and is covered by `PipFocusModeJsTest`. Never
  restyle or resize the video's ancestor elements: Facebook's reels controller
  watches them and pauses the reel. Add new PiP behaviour as separate scripts
  and effects instead of editing focus mode.
- The page scripts in `res/raw` always run from the copy bundled in the APK
  (`bundledScripts.kt`); nothing is fetched at runtime, so script changes ship
  only with an app release.
- Messages in desktop mode opens `MessagesLayer` (`ui/screens/MessagesLayer.kt`), a
  second WebView with the desktop user agent over the main one. The main view never
  switches user agent for Messages; Facebook pages opened from a chat stay in the layer
  (`messagesLayerRoute`), but Facebook's own sections (Home, Friends, the Reels tab…) and a
  reel's or a post's own page (`/reel/<id>`, `/share/p/<id>`, `/<page>/posts/<id>`, as a chat
  opens them; the layer is then kept hidden behind it and Back shows it again,
  `MessagesLayerState`; m.facebook.com shows a post as Home at Home's address, so the post counts
  by the main view's history position) close the layer and open in the main view (`messagesLayerExit`; the desktop site navigates
  in-page, so `messages_layer_nav.js` reports URL changes too). Each WebView is a separate
  DevTools target. The layer pinch-zooms (not in the creators) and runs the page scripts with
  `window.__astryxMessagesLayer` set (`pinch_to_zoom.js` leaves it zoomable); `layer_photo_viewer.js`
  widens the desktop photo viewer's photo over its arrow columns, `layer_story_viewer.js` hides the
  stories viewer's Stories column and top bar at phone width. After Share to story the creator
  moves in-page to `www.facebook.com/stories`, which already has the new story (the feed's tray
  gets it ~30 s later): `open_own_story.js` opens the user's own card there (found by its Add to
  story link), and Back closes the layer. PiP effects act on
  `pipNavigator`/`pipState` (the layer's while it's open, see `PipTarget`), never on
  `navigator` directly.
- `m.facebook.com` redirects full loads of some pages (profiles) to
  `intent://…;package=com.facebook.katana;S.browser_fallback_url=…`; the interceptors open
  the web fallback (`intentFallbackUrl`). Never launch the app an `intent://` link names. For some
  accounts it redirects full reel loads too (2026-10-30; not the test account): a Back step that
  bounces is cancelled and the fallback lands on top, so `BackHistory` counts it as that step. To
  try it on the emulator, answer the reel's document load over DevTools (`Fetch.fulfillRequest`, a
  302 to the `intent://` link). Pages a script opens by `location.href` without a tap are skipped
  by Back (Chromium's history intervention): in tests open them with `loadUrl` or an app link.
  m.facebook.com answers a desktop user agent with a 301 to www.facebook.com that the WebView
  caches; the mobile view then followed it to the desktop site (after Desktop layout): the app
  clears the cache and goes back to the mobile page (`mobileSiteUrl`). The same `wtsid` on every
  redirect means a cached one. A post's
  Share > Send as a message opens `intent://share/?link=<post>…;package=com.facebook.orca`:
  `messengerShareLink` reads the post, the Messages layer opens the chat list, and the chat the user
  opens gets the link in its message box (`put_link_in_chat.js`: `execCommand('insertText')` into
  Messenger's Lexical editor, which needs the page focused, as a real tap gives it; a
  MutationObserver puts it in as the box appears, and an insert made before Lexical takes the box
  over is lost, so it's tried again 0.2 s on). Don't use the
  desktop Send in Messenger dialog (`share_as_message`, `dialog/send`): in end-to-end encrypted
  chats it sends an empty message (2026-10-28). Never send from the test account except to the
  user's own account, with their OK.
- `MainActivity` is `singleTask`: a link sent while the app runs arrives in `onNewIntent`
  and reaches the WebView as `openLinkRequest`. Start links are checked with `appLinkUrl`
  (the start page skips the request interceptor); outgoing links with `externalLinkAction`.
- Both WebViews share one `WebViewClient` (`RendererGoneClient`, built on `AdsPixelBlockingClient`
  in `utils/AdsPixel.kt`), the only place requests are seen below navigations
  (`shouldInterceptRequest`; it can't see WebSocket traffic, which carries the main view's feed
  data). It answers Facebook's Google Ads pixel frames (`www.fbsbx.com/paid_ads_pixel/`) with an
  empty 204. To see what a page requests, record `Network.*` events over DevTools from every
  target (the page, its service worker, the layer's workers).
- Effects that act on each new page key on `pageStarts` (`PageStarts`, counted by the WebView
  client) next to `loadingState`: a page that starts and finishes within one frame leaves the
  loading state at Finished, and an effect keyed on it alone never runs on that page.
- `state.nativeWebView` throws until the WebView exists (it's created during layout, which
  doesn't happen with the screen off): guard it in effects.
- A lost renderer process (`RendererGoneClient`, `utils/RendererRecovery.kt`) replaces the
  WebView: the main view re-keys its `WebView` and reloads through the navigator, the Messages
  layer is re-keyed at its page. Per-WebView setup belongs in `onCreated` (it runs again for
  the replacement), not in effects that hold on to the first WebView. To try it on a debug
  build, send `Page.crash` over DevTools.
- Instrumented tests reset the debug app's settings (not its Facebook login).
- On Android 12+ PiP auto-enters and `onUserLeaveHint` is NOT called (verified on API 34
  and 36), so anything that must run on every PiP entry goes on
  `onPictureInPictureModeChanged`/`isInPipMode`, not `pipEnteringTrigger`. Facebook's
  players pause the video on each resize; the keep-playing guard
  (`pipKeepPlayingActivateJs`) undoes that while in PiP.
- Page scripts use optional chaining (`?.`, Chrome 80+), so on a WebView older than
  that (e.g. the stock API 26 emulator image, Chrome 69) `scripts.js` and most others
  fail to parse. Keep new PiP scripts ES5-style; they also run there. On such a WebView the app
  asks the user to update it at start (`utils/WebViewVersion.kt`, `MIN_WEBVIEW_MAJOR`).
- All PiP, lock-screen audio and fullscreen logs use the tag `AstryxbookPiP`:
  `adb logcat AstryxbookPiP:D *:S`.
- WebView debugging is always on in debug builds; on a release build turn it on in
  Settings (tap the version at the bottom seven times, then *WebView debugging*). Keep the
  setting: live DevTools sessions against the release build are how most PiP fixes were
  found. Live debugging on the device: forward `localabstract:webview_devtools_remote_<pid>`
  and use the DevTools protocol (`/json`, `Runtime.evaluate`).

- Facebook's desktop player pauses videos less than 50% inside the viewport (an
  `IntersectionObserver` rule that outranks a user's Play). The PiP window is smaller
  than its player, so `pip_visibility.js` (a document-start script, both WebViews) reports
  the PiP video's player as fully visible while PiP is engaged. To see why Facebook
  pauses/plays, hook `require('CoreVideoPlayerAutoplayClientUtils').log` in DevTools: it
  names the deciding autoplay rule.
- Remove ads (`adblock.js`): a reel is an ad when one of its spans *is* the label, e.g.
  `<span class="f19">Ad</span>` at the reel's bottom left (English "Ad" even with Facebook in
  Hebrew, read on a phone). Feed ads are found by their label's colour instead. To read a real
  reel ad, turn *Remove ads* off (otherwise the reel is emptied first).
- Facebook plays most videos through Media Source Extensions, so a `<video>`'s
  `currentSrc` is `blob:`. The mobile site's player (`data-mcomponent="MVideo"`) carries
  the whole MP4 in `data-video-url`; downloads (`download_content.js`) and the lock-screen
  audio hand-off (`PIP_HANDOFF_ARM_JS`) use that. Its sound is quieter than what the player streams
  (8.7 dB on the emulator's test reel, read 2026-10-31): the stream's own tracks are in the player's
  `data-extra` (`dash_prefetch_representations`; a track's `base_url` without byte ranges is its
  whole fragmented MP4, which `decodeAudioData` reads).
- A reel's comments (mobile site) are a screen of their own at "/" (pushState): Facebook removes the
  reel's `<video>` (about a second after the tap, set back to 0 on the way out), and Back draws it again from 0, passing through `/watch/` (the new
  reel's `play` fires there, its `playing` at the reel's path, the query comes back later).
  `reel_comments_audio.js` plays the stream's audio track meanwhile through Web Audio (an `<audio>` took
  seconds to play after a seek). Facebook's player ignores a seek outside what it has loaded, and
  stalls loading a reel set `muted`: the returning reel is silenced through a Web Audio gain
  instead, and moved only once, as it starts.
- *Wrap text when zoomed* (`text_wrap.js`, main view only, with pinch to zoom on) narrows text
  blocks to the visual viewport after a pinch settles. m.facebook.com is laid out on the
  server: text is `white-space: pre` with the line breaks in the text, and every box has a
  fixed inline `height` (overlays use negative margins as tall as what they cover, so
  `height: auto` breaks them). The script joins the server's breaks (canvas-measured),
  switches to `pre-wrap` and adds each block's growth to the fixed heights around it, up to
  the first content-sized box. A test WebView can't pinch, so `TextWrapJsTest` fakes
  `visualViewport`; on the emulator, CDP `Input.synthesizePinchGesture` on the WebView target
  does a real pinch (its x/y must be inside the visual viewport, in CSS px).
- m.facebook.com cancels WebView's `textZoom` on every load (it divides its font-size variables
  by it). *Page zoom* (`page_zoom.js`, document-start, main view) zooms whole pages instead: it
  rewrites the viewport `<meta>`s and Facebook's `vpd` cookie (the viewport its server lays pages
  out for). Page scripts that read `visualViewport.scale` must count from
  `__astryxPageZoomScale()`, not 1. Zooming out needs `useWideViewPort`.
- m.facebook.com's client sends the device's details (screen size included) once per session and
  notes when in `sessionStorage` (`sharedprefs.web.last_device_info`); pages loaded while the app is
  in PiP leave Facebook laying pages out for the PiP window's width, even after reloads. Removing that
  key and reloading makes it send them again (`RELAYOUT_PAGE_JS`, after an empty PiP window closes).
- A feed memory ("5 years ago") opens each photo alone (`photo.php?fbid=…`, "1 of 1"; even with
  `set=`, a full load is always 1 of 1). `memory_photos.js` (main view) catches a photo opened from
  a card with several photo tiles (the tap is kept in `sessionStorage`: soon after the feed loads,
  Facebook may open the photo as a new page, which then asks), asks the app (`AstryxPage` call `photoPost`, answered through the
  listener's reply) and opens the post (`m.facebook.com/<owner>/posts/<id>/`, shown at the top of the
  feed as "From your link"), then taps the same tile. `PhotoPost.kt` reads the post from the desktop
  photo page's tracking data (`top_level_post_id`, `photo_attachments_list`). Memories show only now
  and then (often after an app update) and only in the app's feed, not the sites'.
- m.facebook.com is drawn by its server over a WebSocket (screens as HTML, toasts as text): a post's
  id never reaches the page, and its posts have no links (a post's time, a `data-action-id`, opens
  it). After a post from the composer (`/composer/`) the server shows a `.snackbar-container`
  ("Uploading your post...", then "Posted"); from Home it then opens the new post, from elsewhere it
  goes back. `open_new_post.js` opens the profile's newest post then. Since 2026-10-30 Home draws a
  post as several rows of its feed list (`data-type="vscroller"`; header, text, actions) sharing a
  `data-tracking-duration-id`, where a post opened from a link is still one card
  (`data-dcm-id="1"`): hide a post through `scripts.js` `hideFeedPost`, never one box. A list asks
  for more when its loading row (last row, `data-on-first-visibility-action-id`) fires Facebook's
  plain IntersectionObserver; `feed_prefetch.js` (document start) fires it thirty screens early, and once more if
  nothing came 9 s later. The
  page's code is GWT-compiled, in `rsrc.php` scripts: a stack trace from a hooked DOM getter names
  the file to fetch and read. Posting tests: the emulator's
  test account, audience Only me, deleted right after, only with the user's OK for each.
- m.facebook.com has no URL for Feeds (`?sk=h_chr` redirects to Home). *Start on Feeds* opens the
  menu at `/bookmarks/#astryx-feeds` and `open_feeds.js` presses its Feeds entry, found by its icon.
- *Facebook notifications* (`notifications/FacebookCountsWorker.kt`, WorkManager, 15 min) fetches
  `m.facebook.com/notifications/` with the WebView's cookies and reads the tab bar's English
  `aria-label`s ("notifications, 3 new, 4 of 5"). Without WebView's navigation headers
  (`Sec-Fetch-*` etc.) Facebook answers 400. A periodic job can't be forced early (WorkManager
  delays it); turning the setting off and on runs it at once. Logs `Facebook counts:` under
  `Astryxbook`.
- Sharing photos/videos into Facebook (`utils/ShareMedia.kt`, `share_media.js`): the `ShareTo...`
  activity-aliases (Your story, Feed, Set as profile picture, Your groups, Reels, in the Facebook app's
  order); files are copied to the cache and handed to the page through the `AstryxShare` listener.
  Feed, group and profile picture: a mobile button's click on its hidden file input is answered with
  the files. Story and reel: the desktop creators in the Messages layer (m.facebook.com sends them to
  the app). The story editor is laid out like the app's by `story_layout.js` (phone width, tools on
  the story, a bottom bar; the frame sized through a reported `innerHeight`; finger drags replayed
  as mouse drags, which is all Facebook's editor follows); the reel creator is laid out wide
  (`useWideViewPort` + a viewport rewrite). A reel share keeps `/reel/` in the layer. To iterate on
  story_layout.js live, `window.__astryxStoryLayout.stop()` undoes it, then evaluate the new copy;
  after a few rounds Facebook's page keeps stale sizes, so check a fresh share before trusting it.
  The emulator account joined the public group "Android Developer With Android Studio" (2026-10-15)
  for testing group shares; never post there, discard drafts. A group page's Photo button has no
  `role="button"` (a `data-action-id` container), so composer buttons are looked up by both.
  Nothing ever presses Post/Share: when testing, discard drafts. `am start --eu` can't grant a gallery
  item or pass several files; serve test files through the app's own FileProvider instead.
- "Keep screen on" uses the host view's `keepScreenOn`, not the window flag the fullscreen
  host adds and clears.
- A PiP window stops growing at Android's largest PiP size (longest side about the screen's width
  less margins, every app alike): a 4:7 reel's window at about half the screen's width. The app's
  `setPictureInPictureParams` updates don't undo a user's resize (One UI 9 logs, `PipBoundsState`
  in SystemUI's `setBounds` lines). On the phone `sendevent` is denied, so pinch the PiP window in
  tests with `UiAutomation.injectInputEvent` (`MainActivityPipTest.resizePipWindow`: smaller, or
  bigger when it can't shrink, since Android remembers the last size); on the emulator
  `adb emu event send` with `ABS_MT_*` codes pinches too (positions 0-32767).
- A zoom the page takes in PiP outlives it: Chromium keeps the width the window showed when the
  window grows back. Leaving PiP resets the zoom (`PipEffects`, `pipExitZoomBy`). Facebook's
  `/videos/` pages (the phone's Reels tab) show zoomed out in PiP; the emulator account's open as
  `/reel/` and don't, so that case was only seen on the phone.
- In PiP Facebook can write the PiP window's size into a host-only session `vpd` cookie on
  `m.facebook.com`, which shadows the `.facebook.com` one, so later pages are laid out PiP-narrow
  (seen on the emulator after the PiP tests; WebView keeps session cookies across restarts).
  Since v1.35.19 the app expires that host-only cookie when PiP ends and whenever it comes back out
  of PiP (`MainActivity.dropPipViewportCookie`; a PiP window entered and left within a moment can
  end with no PiP callback). By hand: `Network.deleteCookies` over DevTools.
- Facebook's video viewer re-lays out only on a real fullscreen transition
  (`fullscreenchange`), not on `resize`/`orientationchange`, and writes its
  widths into inline styles. Chromium ends HTML5 fullscreen by itself when
  PiP starts from landscape (see `PIP_HOLD_FULLSCREENCHANGE_JS`).

## Settings, permissions and strings

- Every new settings key must be listed in `data/local/SettingsBackup.kt`, either as exported
  or in `NOT_EXPORTED` (developer options, device-specific state). `SettingsBackupTest`
  fails on a key in neither list.
- Camera and microphone go to web pages only through `webMediaGrant`
  (`utils/MediaPermissions.kt`, used by `onPermissionRequest` in `FilePicker.kt`): Facebook
  origins only, after the Android permission. Don't fall back to the library's grant-all.
- `MainActivity` is a `FragmentActivity` because app lock uses `BiometricPrompt`.
- New fork strings go in `brand_strings.xml`, `extra_strings.xml` or `privacy_strings.xml`
  (with a copy in every translated locale: `values-iw`, `-ar`, `-bn`, `-de`, `-es`, `-fr`,
  `-in`, `-it`, `-pl`, `-pt`, `-zh-rTW`), not upstream's `strings.xml`.

## Facebook behaviour, not ours

Verify against Chrome/Opera on the phone before "fixing" these: Reels can
re-pause a resumed video; finished videos auto-advance (and leave
fullscreen). A real landscape page is broken on Facebook's side (reel skip
on rotation, oversized still frame after leaving fullscreen), which is why
phones are held in portrait except during fullscreen video and while the
Messages layer is open (`appOrientation`). See Known
limitations in `FORK_CHANGES.md`.

## Repo housekeeping

- No duplicated code anywhere: app code, tests and live checks, workflows and scripts. Reuse or
  generalize what's there (`AppHarness`: `tapOn`, `pinch`, `screenBox`; the live checks' helpers
  such as `openFeed`, `loadInMain`, `scrollDown`; `.github/actions/avd` for the emulator setup)
  and read each diff for repeats before committing.

- Plan files (`*_PLAN.md`) and `spike.log` are working notes; don't commit them.
- The `stats` branch is data-only (weekly release download counts written by
  `download-stats.yml`, and `badge.json`, the README's downloads total, by `download-badge.yml`);
  never merge it into `main`.
- On Windows, `git worktree remove` can fail with "Filename too long"; delete
  the folder with PowerShell, then `git worktree prune`.
