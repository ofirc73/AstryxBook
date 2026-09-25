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

## Localization

- Added a complete Hebrew (`עברית`) locale, including Astryxbook branding.
- Ported the upstream Italian translation into a fork-safe locale with Astryxbook
  branding and current PiP ratio strings.
- Updated every existing translated locale with the new PiP aspect-ratio
  title and options.

## Android compatibility

- Raised the minimum supported Android version to 8.0 (API level 26).
- Removed the obsolete pre-API 26 launcher-icon fallback; supported installs
  now resolve the adaptive icon from the base `mipmap-anydpi` resources.

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
  skip re-downloading the emulator/system-image and the cold boot.
- Releases now auto-version from the merge commit's conventional-commit
  prefix (`feat:` → minor, `fix:`/`chore:`/etc → patch, `feat!:`/
  `BREAKING CHANGE` → major) instead of manual tagging.
- APK's actual `versionName`/`versionCode` now baked in from the resolved
  release tag at build time — previously hardcoded to a permanent `1.0.0`
  regardless of the real release version.
- Release APK filename cleaned up to `Astryxbook-<version>.apk`.
- Combined test coverage report (JaCoCo, unit + instrumented merged) added
  as a downloadable CI artifact.

## Testing

Added test coverage for the rebrand and default-behavior changes: settings
defaults, theme colors, app identity/strings, launcher icon, applicationId,
and the pinned external script source — none of which existed upstream.

Also covers the PiP focus-mode/toggle/freeze JS (`PipFocusModeJsTest`, driven
against a real `WebView` with synthetic DOM fixtures rather than live
Facebook): non-kept siblings hidden at every ancestor level (not just
`body`'s direct children), the video's own ancestor chain staying untouched,
the download button hidden via inline override (beating its own
higher-specificity stylesheet rule) and restored on exit, toggle targeting
the locked-in video instead of re-deriving "the active video" by area, and
the active-video freeze installed at PiP-entry blocking writes until
unfrozen. Facebook's own player behavior (the re-pause limitation above) is
deliberately out of scope — external, unfixable from here.

## Picture-in-Picture

New feature, opt-in (off by default): shrinks into a floating window when
leaving the app while a Facebook video or Reel is playing.

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

## Known limitations

- On Reels specifically, Facebook's own web player can re-pause a video
  shortly after it's resumed from the PiP overlay's Play button. Facebook's
  player enforces which single video is allowed to play at a time on its own
  side, independent of anything this app does, so this isn't something we
  can override from here.
- Store listing screenshots (`fastlane/metadata/.../phoneScreenshots/`)
  removed as stale; not replaced yet (need real device captures).

## Round 2 – 15 tests and fixes

- **15 tests** now cover the full PiP focus‑mode behaviour:
  - Nested‑DOM chrome hiding at every ancestor level (not just `body`'s direct children).
  - Download‑button hide/restore, including the specificity‑beating case.
  - Clipboard‑copy button hide/restore – same root cause and fix as the download button (`copy_to_clipboard.js`), with regression coverage.
  - Toggle target selection – ensures the locked‑in video is focused, not re‑derived by area.
  - Freeze mechanism – writes are blocked until unfrozen on PiP exit.
  - Restore cleanup – all temporary attributes and styles are cleared on PiP exit.
  - Anomaly scan (clean case) – reports any unexpectedly‑visible element via the existing `AstryxbookPiP` logcat channel.
  - Anomaly scan (“something slipped through”) – a dedicated test that a deliberately‑inserted hidden element is caught by the scan.

### Summary of changes beyond the tests

- **Fixed:** clipboard‑copy button leaking into PiP – same root cause and same fix as the download button (`copy_to_clipboard.js`), identified by reading the source rather than live DevTools.
- **Added:** a permanent, silent‑unless‑triggered anomaly scan in `PIP_FOCUS_MODE_JS` that reports any unexpectedly‑visible element via the existing `AstryxbookPiP` logcat channel – so the next leak (from the navbar script, Facebook's own DOM, or anything else) shows up in an ordinary `adb logcat` capture without needing a reproducible live DevTools session.
