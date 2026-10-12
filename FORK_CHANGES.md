# Astryxbook Fork Changes

Changes specific to this fork, kept separate from `CHANGE.md` (upstream's
own changelog) to avoid merge conflicts on a file eepiemi actively maintains
— same reasoning as `brand_strings.xml` being split out from `strings.xml`.

Organized by area rather than by release tag, since auto-versioning bumps a
patch version on every merged commit (`fix:`/`chore:`/etc.) — a per-tag log
would mostly be noise. See GitHub Releases for the actual per-version diffs.

## Rebrand

- Renamed to **Astryxbook**: `applicationId` (`com.astryx.book`), display
  name, and an original "A" monogram launcher icon in Facebook blue — not
  Facebook's own trademarked logo, to avoid impersonation/trademark issues.
- Restyled to match **Facebook's own original Android look**: Material You
  theming and AMOLED Black now ship **off by default** (previously on),
  using Facebook's native blue instead of wallpaper-derived colors. Both
  remain available as opt-in toggles.
- Fixed `-v31`/`-night-v31` resource variants that were pulling Android's
  dynamic `system_accent1_*` colors into both native chrome *and* the
  launcher icon itself, unconditionally — bypassing the above default
  entirely on API 31+ devices.
- Native typography (splash, settings sheet, dialogs) tightened to a
  denser, more Facebook-like scale.
- Removed the Buy Me a Coffee button/link and `FUNDING.yml` entry — not
  relevant to this fork.
- Local badge assets (`download.svg`, `open_issue.svg`) and the store
  listing icon/banner recolored to match; downloads badge swapped from an
  opaque third-party worker to a themed, GitHub-API-backed shields.io badge.
- README banner (the store feature graphic) now shows what sets this fork
  apart: Picture-in-Picture, lock-screen audio and in-app Messages chips, and a
  second row for app lock, Messenger calls and share & shortcuts (with a small
  drop shadow), under the original tagline. The README also gained a License section (GPL-3.0, as
  upstream) with the fork's copyright line.
- `NOTICE.md` adds GPL-3.0 section 7 terms for this fork's own material: no
  trademark rights to the Astryxbook name and logo, and modified versions must
  use their own name, logo and application ID and keep the author notices.
- Weekly download history: `download-stats.yml` appends each release's
  GitHub download count to `downloads.csv` on the data-only `stats` branch
  (Mondays, or on demand from the Actions tab). No tracking in the app.

## Localization

- Added a complete Hebrew (`עברית`) locale, including Astryxbook branding.
- Ported the upstream Italian translation into a fork-safe locale with Astryxbook
  branding and current PiP ratio strings.
- Updated every existing translated locale with the new PiP aspect-ratio
  title and options.
- Pulled the upstream Traditional Chinese (`zh-rTW`) update: clipboard copy and
  Material You strings, without upstream's Materialbook-branded strings.

## Android compatibility

- Raised the minimum supported Android version to 8.0 (API level 26).
- Removed the obsolete pre-API 26 launcher-icon fallback; supported installs
  now resolve the adaptive icon from the base `mipmap-anydpi` resources.
- Backups (Google cloud backup, device-to-device transfer and `adb backup`) now
  carry only the app's settings. The Facebook login (the WebView's cookies) and
  caches stay on the phone, so a restored or new phone starts logged out.

## Default behavior changes

- Phones stay in portrait while browsing; only a video in fullscreen or the
  Messages layer can rotate (following the system auto-rotate setting), and
  leaving them returns to portrait. Facebook's mobile site doesn't handle a real
  landscape page: rotating made it skip the reel, and leaving fullscreen in
  landscape left an oversized still frame instead of the video (both also
  in Chrome). Tablets and other large screens (`smallestScreenWidthDp >=
  600`, the auto-desktop ones) still rotate freely.

- Auto desktop layout now triggers only on genuinely large screens
  (`smallestScreenWidthDp >= 600`), no longer on a phone that happens to be
  in landscape at app start. The automatic decision is computed at runtime
  and never persisted; only the user's own Desktop layout toggle is saved.
  Previously a phone launched once while sideways could get stuck in the
  desktop layout (desktop user agent, garbled Reels, broken PiP) until the
  user turned it off manually. A one-time migration unsticks such installs:
  a desktop setting that was written by the old auto logic is reset to off,
  while one the user chose themselves is kept.

## WebView

- Security: page scripts are no longer downloaded from GitHub at launch; the app
  always runs the copies bundled with it. Before, anyone able to push to `main`
  could inject code into every user's logged-in Facebook page on their next
  launch. Script updates now arrive with app updates, and launch no longer waits
  on the network for them.
- Settings while watching reels: the Reels viewer has its own top bar instead of
  Facebook's header, where the app's Settings button lives, so Settings couldn't be
  opened from there. `scripts.js` now adds a Settings button at the right end of that
  bar on reel pages whenever no other one is showing (it's hidden in PiP like the rest
  of the page).
- HTML5 fullscreen video support: the WebView's chrome client now implements
  `onShowCustomView` / `onHideCustomView`, so Facebook's fullscreen button
  shows the video in a real fullscreen overlay (system bars hidden, screen
  kept on, follows the system auto-rotate setting) instead of only enlarging
  it inside the page. Back exits fullscreen; system bars then return to the
  Immersive mode setting. Entering PiP while fullscreen shows the fullscreen
  view directly and skips PiP focus mode.
  Verified on device: rotating to landscape keeps the same video playing,
  letterboxed; PiP from fullscreen shows the video (not black), the PiP
  Play/Pause action works, and expanding PiP returns to fullscreen. File
  upload (photo picker) still works through the extended chrome client.
  Facebook offers its fullscreen button on landscape videos; portrait ones
  open in its own Reels-style viewer instead.
- PiP from fullscreen with the phone in landscape: WebView itself ends
  fullscreen right after PiP starts (the display turns to portrait for the
  home screen, which Chromium treats as rotating out of a fullscreen video).
  Facebook's viewer reacted to that by re-rendering at the PiP window size
  and discarding the video, so the PiP window went black and the app came
  back with the page squeezed to that size. While in PiP the page now
  doesn't see that `fullscreenchange`; focus mode is applied once
  fullscreen has ended, and on return the event is replayed once the window
  has settled, so Facebook lays out at the full size.
- Privacy fix: the upstream file-download hook in `scripts.js` saved every
  blob a page created to the public Downloads folder. On the desktop site
  that included the decrypted photos of every encrypted Messenger chat you
  opened. It now saves a blob only when the page starts a real download (a
  clicked `<a download>` link pointing at it). The app's own media download
  button is unaffected. Covered by `FileDownloadJsTest`.
- Security fix: any frame in the WebView (an ad or third-party iframe
  included) could call the download bridge and save any file type, an APK
  say, to Downloads without the user doing anything. Downloads now go
  through a message listener (`AstryxDownloads`) that only the main frame of
  a facebook.com/messenger.com page can reach, and only photos and videos are
  saved; anything else, or an image that doesn't decode, is refused with
  "Download failed". On a WebView without message listeners (older than 83)
  the old `DownloadBridge` interface remains, with the same photo/video
  limit. The scripts fall back to `DownloadBridge` so older app versions keep
  working with the scripts on `main`. Covered by `DownloadMimeTypeTest` and
  `FileDownloadJsTest`.
- Desktop layout: Back with a Messenger chat window open over the feed closed
  the app, because chat windows aren't marked as dialogs. Back now closes the
  chat window (the right-most visible button of its header, "Close chat" in
  any language). Covered by `BackHandlerJsTest`.
- Profile links opened as a full page (from another page or a link) failed with "Not
  supported": `m.facebook.com` answers them with a redirect to an `intent://` link for the
  Facebook app. The app now opens that link's web fallback (`S.browser_fallback_url`)
  itself when it's a Facebook page, so the profile shows in the app. `intent://` links are
  never used to launch the app they name; one with a non-Facebook web fallback opens that
  page externally. The "is this Facebook" check now matches only facebook.com /
  messenger.com hosts and their subdomains (the old pattern also accepted look-alike
  hosts and URLs that merely contained a Facebook address).
- Security: a link in a post or ad can no longer send you straight into another app.
  Web, email and phone links open as before; any other kind (another app's deep link,
  `market:`, `sms:`…) first asks "Open in another app?" and shows the link. `javascript:`,
  `file:`, `content:` and `data:` links are refused. Links are sent with
  `CATEGORY_BROWSABLE`, so they only reach apps that accept links from a browser. Covered
  by `ExternalLinksTest`.
- Security: a link sent to the app (from another app or an explicit intent) opens only if
  it's a Facebook page or an fb.watch link, else the app opens at Home. Before, any URL
  could be loaded in the logged-in WebView, because the start page doesn't pass through
  the request interceptor. The "is this Facebook" check also no longer accepts a Facebook
  address in the user-info part (`https://facebook.com:443@other.example/` is
  other.example). Covered by `AppLinkUrlTest`.
- A Facebook link tapped in another app while Astryxbook is running now opens in the
  running app (`singleTask`, `onNewIntent`) instead of starting a second copy with its own
  WebView, page load and PiP state. Links opened this way go through the same Messages
  routing as at launch. Covered by `LinkHandlingManifestTest`.
- Android 12+: Facebook links open in the browser until the app is allowed to open them,
  since facebook.com can't verify for a third-party app (the useless `autoVerify` is
  gone). Settings has a new *Open Facebook links here* row that opens the system page
  where you turn them on.
- A download that Android refuses to save now says "Failed to save file" instead of
  showing nothing.
- Video and reel downloads work again. Facebook plays videos through Media Source
  Extensions, so the `<video>`'s source is a `blob:` URL the page can't fetch, and the
  download button failed silently. The button now downloads the player's own MP4
  (`data-video-url` on the `MVideo` container: one file with video and audio) and saves it
  as `video/mp4`. When a video has no such file, or fetching it fails, the app says "This
  video can't be downloaded".
- Two cold-start crashes ("WebView is not initialized"): the user-agent effect and the PiP
  layer refresh touched the WebView before it existed, which happens when the app starts
  with the screen off. Both now wait for it.
- Desktop layout: Back on the feed closed the app right away instead of
  scrolling to the top first. The check expected one dialog element on the
  feed at rest, which Facebook's desktop feed no longer has. Back now scrolls
  a scrolled feed to the top, and leaves the app from the top, as on mobile.

## CI/CD

- `./gradlew test`, `:app:lintDebug`, and `connectedAndroidTest` now gate
  every build — previously nothing ran tests or static Android checks before
  signing/releasing.
- Android lint runs as its own CI job and uploads HTML, XML, and text reports
  as a workflow artifact, including on failure. Dependency-update notices are
  advisory because the current pinned libraries target `compileSdk 36`; the
  latest available versions require the newer compile SDK.
- Split into `ci.yml` (PR validation) and `create-release.yml` (tag/dispatch
  only, calls `ci.yml` as a reusable workflow) — signing secrets no longer
  touch PR runs, and PR checks show correctly instead of "Create Release".
- AVD image + boot snapshot cached — first run populates it, later runs
  skip re-downloading the emulator/system-image and the cold boot. The cache
  key includes the installed emulator and system-image versions: a runner
  update had made the cached snapshot unusable, so every run cold-booted.
- Faster releases (about 11 min to about 7): the instrumented-test job
  compiles the app and test APKs before it starts the emulator (compiling
  next to the software-rendered emulator took most of the job), and the
  coverage report (`coverage.yml`) runs next to the release job instead of
  before it. `ci.yml` can also be run by hand on a branch (`workflow_dispatch`).
- Releases now auto-version from the merge commit's conventional-commit
  prefix (`feat:` → minor, `fix:`/`chore:`/etc → patch, `feat!:`/
  `BREAKING CHANGE` → major) instead of manual tagging.
- A push to `main` that only changes docs (Markdown, `LICENSE`, the
  `fastlane/` store listing, README images in `assets/`) no longer publishes
  a release; its commits roll into the next code release's version bump.
- Manual releases (running *Create Release* by hand with a tag, or pushing a
  `v*.*.*` tag) work again: every job was skipped because the version step
  only runs on a push to `main`, and a skipped job skips everything
  downstream of it.
- APK's actual `versionName`/`versionCode` now baked in from the resolved
  release tag at build time — previously hardcoded to a permanent `1.0.0`
  regardless of the real release version.
- Release APK filename cleaned up to `Astryxbook-<version>.apk`.
- Combined test coverage report (JaCoCo, unit + instrumented merged) added
  as a downloadable CI artifact.

## Testing

Added test coverage for the rebrand and default-behavior changes: settings
defaults, theme colors, app identity/strings, launcher icon, applicationId,
none of which existed upstream.

Also covers the PiP focus-mode/toggle/freeze JS (`PipFocusModeJsTest`, 24
tests driven against a real `WebView` with synthetic DOM fixtures rather
than live Facebook):

- Non-kept siblings hidden at every ancestor level, not just `body`'s direct
  children; the video's own ancestor chain staying untouched.
- The download button (`download_content.js`) hidden via inline override
  in PiP and restored on exit, including the case where its own
  higher-specificity `#id.visible` stylesheet rule would otherwise win.
- The clipboard-copy button (`copy_to_clipboard.js`) hidden/restored the
  same way — same root cause, same fix, found by reading its source rather
  than live DevTools.
- Toggle targeting the locked-in video instead of re-deriving "the active
  video" by area, with both a marked-video case and a fallback case.
- The active-video freeze installed at PiP-entry: blocks writes while
  frozen, preserves whatever was already tracked at install time, resumes
  on unfreeze.
- Restore cleanup — all PiP-mode DOM markers actually removed on exit.
- The video sized to the visual viewport (wide ancestors left untouched),
  re-fitted on viewport resize, and the resize listener removing itself
  after restore.
- The screen pin: values held from pin time, idempotent, native getters
  restored on unpin with a single `resize` fired.
- Holding `fullscreenchange` back during PiP and replaying exactly one
  after PiP, only once resize events have settled.
- The anomaly scan (below): a clean-page case reporting nothing, and a case
  where a deliberately-unhideable element (inline `!important`, the same
  trick that caused the two button leaks) is correctly caught and named.

Lock-screen audio, fullscreen and auto-desktop have their own suites:

- `PipHandoffJsTest` (12, real `WebView`): the snapshot taken at page hide
  even when the video is paused right after, mute/pause and restore of the
  prior mute state, the visible-again signal only while a handoff is active,
  handback position/play behaviour, and listener cleanup/re-arming.
- `LockScreenHandoffTest` (8): handoff eligibility (`https://` only, not
  `blob:`/empty, not already paused) and parsing of the page's answer.
- `FullscreenControllerTest` (6): the show/hide contract (second show
  dismissed, hide without show is a no-op, WebView told exactly once).
- `AutoDesktopTest` (11): the effective-desktop rule, the one-time
  migration of the old persisted auto decision, and the orientation rule
  (phones portrait except fullscreen video and the Messages layer, large
  screens unlocked).
- `SettingsDefaultsTest` and `PipManifestTest` extended: lock-screen audio
  off by default; the service declared with `foregroundServiceType`
  mediaPlayback and exported.

Facebook's own player behavior (the re-pause limitation below) is
deliberately out of scope — external, unfixable from here.

## Messages in desktop mode

Facebook's mobile site (`m.facebook.com`) no longer has Messages: its Messages tab sends
you to the Messenger app, then to an "Open Messenger" page whose button leads to Google
Play. When the setting is on, Messages opens inside the app instead, on the desktop
site, in its own layer over the page you were on. Contributed by Ufoex (PR 6); the
separate layer was added on top.

- **Messages layer** (`MessagesLayer.kt`): a second WebView with the desktop user agent,
  drawn over the main one. The main view keeps its mobile page and scroll position
  underneath, and its videos are paused while the layer is open. Back steps through the
  layer's own history (a reel back to the chat, a conversation back to the chat list),
  then closes the layer and you're back exactly where you were in the feed. The layer's
  WebView is destroyed when it closes.
- Every Messages/Messenger entry point (the Messages tab, `facebook.com/messages`,
  `m.me`, `messenger.com`, `fb-messenger://`, `intent://`) opens the layer. Links that
  point at a conversation keep it (`/messages/t/<id>`, `m.me/<name>`,
  `messenger.com/t/<id>`, `fb-messenger://user/<id>`); anything else opens the inbox.
- Facebook pages opened from a chat (a shared reel, a profile) open in the layer too, on
  the desktop site, which plays shared reels and opens profiles without pushing the
  Facebook app; Back returns to the chat. Handing them to the mobile view instead lost the
  feed position and broke on Facebook's mobile redirects (shared reels landed on the home
  feed, profiles redirected to the Facebook app). Routing is in `messagesLayerRoute`;
  non-Facebook links go to the system as in the main view.
- **Leaving Messages for Facebook's own sections** closes the layer: Home (or the logo)
  returns to the main feed where you left it, and Friends, Groups, Watch, Marketplace, the
  Reels tab and the like open their mobile page in the main view
  (`messagesLayerExit`). Before, they stayed in the layer: its desktop feed looked like
  the main one, so reels picked from there played in the desktop layout and its gear did
  nothing. The desktop site often changes pages in-page rather than loading them, so
  `messages_layer_nav.js` reports those URL changes (`LayerBridge`) as well. Pages a chat
  links to (a reel, a video, a group, a profile, a post) still stay in the layer.
- The gear the page scripts add to the desktop header opens the app's Settings from the
  layer too (it had no `SettingsBridge`).
- The layer gets the same page scripts as the main view (download hook, theme and so on).
- **PiP:** videos in the layer (a shared reel, a video in a chat) go to Picture-in-Picture
  like feed videos: the layer gets the same detector (`pip_video_detector.js`, `PipBridge`),
  and while it's open every PiP action (focus mode, the Play/Pause button, lock-screen
  audio, the fullscreen hand-off) acts on the layer's page instead of the feed's
  (`PipTarget`). Closing the layer reports "no video playing", so leaving the app
  afterwards doesn't start PiP. The PiP scripts themselves are unchanged.
- **Landscape:** while the layer is open, phones follow the system auto-rotate setting, so
  the desktop Messages page can use the full width (chat list beside the conversation,
  all composer buttons). Closing the layer returns to portrait. The page underneath
  reflows when the screen turns, so its scroll position is saved when the layer opens and
  put back after the rotation back. Leaving a fullscreen video inside the layer keeps it
  rotatable.
- `messages_tab.js` hooks the Messages tab so it opens the layer directly. It's injected
  on every page load while the setting is on (bundled copy, like the PiP detector), so
  it works right after the setting is switched on, not only after "Apply immediately?" or
  a restart; otherwise Facebook left its "Get the Messenger app" page on the feed under
  the layer. It recognises the tab by its English label or its icon glyph. There is no position check on purpose:
  if Facebook changed both, matching by position could hijack another tab, and a miss
  still ends in the layer through the tab's intercepted `fb-messenger://threads` link.
- Setting **Messages in desktop mode** (off by default, opt-in). Off keeps the previous
  behavior, including `fb-messenger://` links reaching the Messenger app.
  Not used when the whole app is already on the desktop site (Desktop layout, large
  screens), which shows Messages by itself; there, web Messages links (`m.me`,
  `messenger.com`, mobile `/messages`) now open as the desktop Messages page in the app
  instead of the browser.
- `MessagesDesktopTest` covers the URL helpers and the layer's routing.

## Messenger calls

- **Voice and video calls** in the Messages layer (Messages in desktop mode): the call
  opens in the layer, and the first call asks Android for the camera and microphone. Only
  Facebook's and Messenger's own `https` pages get them, and only those two (other requests,
  such as protected media IDs or MIDI, are still denied); compose-webview's own handler
  would have granted them to any page once the app held the permissions, so the app
  replaces it. The camera and microphone aren't required hardware, so the app still
  installs on devices without them.

## Picture-in-Picture

New feature, opt-in (off by default): shrinks into a floating window when
leaving the app while a Facebook video or Reel is playing.

- Keeps playing in PiP: Facebook's mobile players pause the video right after
  every resize of the page, so entering PiP always paused it, and later resizes
  paused it again (traced with DevTools: the pause comes from Facebook's player
  code, not Chromium). A
  keep-playing guard, separate from focus mode, now undoes those pauses while
  PiP is engaged if the video was playing when PiP started, at most 6 times
  per 10 s; pausing from the PiP button, the lock-screen hand-off and a hidden
  page are respected. It's switched on from `onPictureInPictureModeChanged`,
  because Android 12+ doesn't call `onUserLeaveHint` when PiP auto-enters.
  Verified on emulators running Android 16, 14, 11 and 8.0 (`PipKeepPlayingJsTest`).
- Desktop site (Desktop layout, or a reel opened in the Messages layer): reels
  paused in PiP and every Play was undone within a second, with the view
  jumping between sizes. Facebook's desktop player pauses any video whose
  player is less than 50% inside the viewport (its
  `evaluateVideoAutoplayPauseOnInvisibleRule:50%`, which outranks a user's
  Play), and in the small PiP window the page is laid out at window size, so
  the player never is. `pip_visibility.js`, installed as a document-start
  script in both WebViews, wraps `IntersectionObserver` so that while PiP is
  engaged the PiP video's player reads as fully visible, and re-sends that on
  PiP entry; Facebook then resumes the reel itself. Needs a WebView with
  document-start scripts (Chrome 87+). Verified on the Android 16 emulator, in
  Desktop layout and in the Messages layer (`PipVisibilityJsTest`).

- Detects playback via a lightweight JS bridge (ignores muted feed autoplay).
- Native resume control (Play/Pause) on the PiP overlay, since
  Android/Chromium auto-pauses WebView video on PiP entry and there's no
  public API to override that.
- Page chrome hidden while in PiP so only the video shows, filling the
  window — not just a shrunk copy of the whole page.
- Portrait-video window ratio is selectable from Settings: 4:7 (the
  recommended default), 2:3, 3:4, or 9:16. Changes are applied immediately.
- Landscape videos auto-match their detected source ratio (including 7:4, 4:3,
  and 16:9), clamped to Android's supported PiP range, with 16:9 fallback
  when dimensions are unavailable.
- The 4:7 default trades a small top/bottom crop for reliable sizing on
  devices that render a true 9:16 window oversized or clipped off-screen.
- On Android 12 and newer, the WebView bounds are supplied as the PiP source
  rectangle to preserve smooth system transitions.
- Two other injected UI elements (the download button, the clipboard-copy
  button — see Testing above) are force-hidden while in PiP and restored on
  exit, since they're wanted controls in the normal view but obstructive in
  a small floating window.
- A permanent, silent-unless-triggered sanity check runs after PiP's page
  chrome is hidden: if anything is still unexpectedly visible, it's reported
  by tag/id/class through the existing `AstryxbookPiP` logcat channel. This
  class of bug (an element leaking into the PiP window past known hiding
  rules) has come up three times — the page toolbar, the download button,
  the clipboard-copy button — each only found by live DevTools or reading
  the offending script's source. This lets the *next* one be diagnosed from
  an ordinary field `adb logcat` capture instead of needing a reproducible
  live session.
- The video fills the PiP window exactly: it's sized to the visual viewport
  (the visible area) in px and re-fitted whenever that resizes. Neither CSS
  unit worked everywhere: on the Reels tab Facebook's reel wrappers (the
  video's own ancestors, deliberately left untouched) keep their full-screen
  width, Chromium zooms the page out and a `100vw` video covered only the
  window's top-left corner; for a video playing inline in the feed the
  fixed-position containing block keeps its full-screen size, so `100%` made
  the PiP window a zoomed-in crop. Resizing the wrappers also fixed the zoom
  but made Facebook pause the reel.
- Rotating the phone during PiP keeps the reel: the PiP window doesn't
  change size, but Facebook read the rotated screen on `resize` and dropped
  the reel for `/watch/`, laid out at PiP size. While in PiP the page now
  reads the screen values from PiP entry (`screen` size, orientation,
  `window.orientation`); the real ones are restored, with one `resize`, on
  exit.
- "Keep audio when screen locks" (opt-in, off by default, shown under the
  PiP settings): locking the screen while a video is in PiP hands its audio
  off from the WebView to a native Media3 player in a `mediaPlayback`
  foreground service, with lock-screen media controls. Unlocking (or
  returning to the app) hands playback back to the WebView video at the same
  position, resuming only if it was still playing. The page video stays
  muted in between, since Facebook restarts it on its own after unlock. The
  service only accepts this app's own controller (trusted system controllers
  get transport controls only). It is bound idle at PiP entry and only goes
  to the foreground at screen-off, so no notification shows during ordinary
  PiP use.

## Sharing, text size, shortcuts and developer options

- **Share into the app:** a link shared from another app (the share sheet's Astryxbook
  entry) opens Facebook's share dialog for it. Shared text without a link can't be posted
  through Facebook's web share, so the app just opens and says so.
- **Share this page:** the share button at the top of Settings sends the page you're on
  (the Messages layer's while it's open) through the system share sheet, as a
  `www.facebook.com` link without tracking parameters.
- **Text size** (Settings, 100% by default): 85% to 150%, for the feed and the Messages
  layer alike.
- **Launcher shortcuts:** long-press the app icon for Messages, Notifications and
  Marketplace. Messages opens where the Messages tab would (the Messages layer when
  *Messages in desktop mode* is on); this also applies to Messages links from other apps,
  which used to land on Facebook's "Open Messenger" page.
- **Tracking parameters:** links leaving the app (to the browser, another app, the share
  sheet) lose Facebook's trackers (`fbclid`, `__cft__`, `__tn__`, `mibextid`, `rdid` …),
  campaign tags (`utm_*`) and the usual ad click IDs, and `lm.facebook.com` redirects are
  unwrapped like `l.facebook.com` ones. Fixed along the way: query values were
  percent-encoded twice (`%20` became `%2520`), `#fragments` were dropped, and a parameter
  without `=` left the whole link uncleaned.
- **WebView debugging is now a developer option** (off by default): tap the version at the
  bottom of Settings seven times, then turn on *WebView debugging* to use `chrome://inspect`
  on a release build. Debug builds always allow it. Before, it was always on, so anything
  with USB debugging access could drive the logged-in page.
- Both WebViews pin file access off and Safe Browsing on (compose-webview's defaults today)
  and switch geolocation off.
- CI: third-party GitHub Actions are pinned to commit SHAs (the release job holds the
  signing keystore); Dependabot keeps the pins current.

## Custom Tabs and settings backup

- **Open web links in the app** (Settings, off by default): web links from posts and chats
  open in a Custom Tab, the default browser's tab drawn over Astryxbook, instead of
  switching to the browser app. The tab uses the browser's own cookies, never this app's
  Facebook login. Other links (email, phone, other apps) behave as before.
- **Export and import settings** (Settings): saves every setting to a small JSON file
  through the system file picker, and restores them from one, on this phone or another.
  Only known settings with the right type are imported. Developer options and WebView
  debugging never travel, so a shared settings file can't turn on remote debugging.

## Keep screen on

New setting (Settings → *Keep screen on*, off by default): *While a video
plays* keeps the screen from timing out while any video on the page is
playing and shown (muted feed videos included, in the feed and the Messages
layer); *Always* keeps it on whenever the app is on screen. It sets the view's
`keepScreenOn`, so it never applies in the background and doesn't interfere
with the window flag fullscreen video already sets.

## App lock, log out and frame restrictions

- **App lock** (Settings, off by default): asks for your fingerprint, face or screen lock
  when the app opens, and again after it's been out of sight for the time you pick (right
  away, 1, 5 or 15 minutes). A video in PiP or playing on the lock screen keeps going
  underneath; locking leaves fullscreen video and closes Settings, which draw above it.
  While it's on, Recents shows no preview of the page (Android 13+). Needs a screen lock on
  the device; it can't be turned on without one.
- **Log out and clear data** (Settings): removes the Facebook login, cookies, cache, site
  storage and copied images from the app, keeping your settings.
- **Copy to clipboard only for Facebook itself**, like downloads: the clipboard bridge
  takes messages only from the main frame of a Facebook or Messenger page
  (`AstryxClipboard`, a web message listener), so an ad or embed can't fill the clipboard.
  WebViews without message listeners keep the old interface.
- **The rest of the page bridges are for Facebook itself too.** Opening Settings, the
  status bar colour, the video state behind PiP and "Keep screen on", and the Messages
  layer's navigation now go through one message listener (`AstryxPage`) that only the main
  frame of a Facebook or Messenger page can reach. Before, any ad or embed could open
  Settings, change the bar colour, keep the screen on by faking a playing video, or reshape
  the PiP window. WebViews without message listeners keep the old interfaces. The Material
  You colours stay a plain interface: they're read-only and only give out the theme colour.
- **Readable status bar in Settings and over Messages**: the Settings screen now gets dark
  status and navigation bar icons on its light background (it's its own window, so it never
  picked up the app's setting), and with Messages in desktop mode open, the system bars take
  the Messages page's colour (dark when Facebook's desktop dark mode is on) instead of staying
  the main page's white.
- **No third-party cookies**: embeds from other sites (ads, link previews) can't set or read
  their cookies inside the app, so they can't track you across pages. Facebook and
  Messenger themselves are unaffected.
- **Iframes can't open other apps.** Only the page itself can send a link out of the app;
  a navigation inside an ad or embed frame stays in that frame, so it can't open the
  browser or another app without a tap.

## UX pass (October 2026)

From a hands-on pass through the app in light and dark mode on an emulator.

- Material You works: the theme now follows the setting (it never got `dynamicColor`, so
  the page script swapped Facebook blue for Facebook blue). The Facebook-blue schemes are
  filled in with Facebook's own greys, so Settings, switches and dialogs no longer mix in
  Material's default purple, and the dark switches are white on blue.
- Night themes have a dark parent, so the page sees `prefers-color-scheme: dark` in dark mode.
  m.facebook.com still keeps its own Dark mode switch (Facebook menu); the AMOLED Black text
  now says so.
- The status bar icons are re-applied after a system light/dark switch (they went white on
  Facebook's white page), and the bar is black over Facebook's photo, video, reel and story
  viewers. The page reports the colour under the bar once a second, not only when the
  `theme-color` meta changes.
- Back on the feed sends the app to the background instead of finishing it, so coming back
  keeps the page instead of reloading the feed.
- The download and copy buttons show only on a viewer's own URL, not on a Page's Videos or
  Photos tab, where the download button floated over the header.
- Settings opens from every page: a gear in the top bar of Pages, profiles, Notifications…,
  and the feed's gear has the round backdrop of Facebook's header buttons. The feed's gear no
  longer shows over the tab bar on another page loaded directly.
- Settings: sections with headings (Feed, Appearance, Video, Messages and links, Privacy and
  security, Backup); Log out in red; "Reload to apply" (was "Apply Immediately?") shows only
  after a change that needs the page reloaded; Customize feed has a "Hide from your feed"
  title; Text size warns that Facebook's layout can overlap above 115%; App lock without a
  screen lock opens the security settings; "Open Facebook links here" explains what to pick
  first; the header buttons are 48dp and sharing the page keeps Settings open.
- Facebook's Messages tab, with Messages in desktop mode off, offers desktop mode instead of
  a raw `fb-messenger://` link; other app links name only their scheme, and a link no app can
  open says so.
- "Share via…" in Facebook's share sheet hands the post's link to the Android share sheet.
- Downloads are named `Facebook_<date>_<time>`, saving the same file again points at the
  first copy, and a snackbar offers Open.

## Known limitations

- On Reels specifically, Facebook's own web player can re-pause a video
  shortly after it's resumed from the PiP overlay's Play button. Facebook's
  player enforces which single video is allowed to play at a time on its own
  side, independent of anything this app does, so this isn't something we
  can override from here.
- Opening a video from the feed wall via Facebook's own tap-to-fullscreen
  viewer (as opposed to a Reel, or a video playing inline in the feed) can
  show a black PiP window: Facebook's own screen-navigation system hides an
  ancestor of the video (`display:none`) when the app backgrounds, treating
  it the same as the user leaving that screen. Forcing that ancestor back to
  a renderable display works only briefly - the video shows correctly for a
  moment, then goes black again, most likely because Facebook replaces the
  element with a fresh DOM node during its own teardown rather than mutating
  the one we're holding onto, which a style/class-attribute-watching
  `MutationObserver` can't catch. Chasing DOM node replacement itself would
  mean broad `childList`/subtree observation and re-running detection on
  every hit, with no guarantee of actually winning against Facebook's own
  process - not attempted, for the same reason as the Reels re-pause above.
  Reels playing from the Reels tab, and videos playing inline in the feed
  wall, are unaffected. Real fullscreen (Facebook's fullscreen button on a
  landscape video, see WebView above) is unaffected too: its PiP shows the
  video.
- On large screens (tablets, which still rotate freely, see Default
  behavior changes) Facebook's mobile site handles a real landscape page
  badly: rotating while watching Reels makes it skip to another video and
  switch to `/watch/`, and leaving fullscreen in landscape leaves its viewer
  showing an oversized still frame instead of the video (both also happen
  in Chrome on m.facebook.com). Phones avoid both by staying portrait.
- When a reel or fullscreen video ends, Facebook moves on by itself: in PiP
  the window can go empty (the finished video collapsed, or the page went
  back to the feed), and a fullscreen video auto-advances and leaves
  fullscreen.
- After lock-screen audio hands back on unlock, the page video is told to
  resume, but on Reels Facebook can pause it again right away (the same
  re-pause behavior as the first item).
- Lock-screen audio plays Facebook's signed video URL directly, and those
  URLs expire. If one expires during a long lock, native playback stops
  (no refresh); on unlock the WebView video is restored at the last known
  position. It also only covers videos with a plain `https://` source, not
  `blob:` (MSE) streams.
- Store listing screenshots (`fastlane/metadata/.../phoneScreenshots/`)
  removed as stale; not replaced yet (need real device captures).