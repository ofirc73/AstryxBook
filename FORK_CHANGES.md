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
  recommended default), 2:3, 3:4, or 9:16. Changes are applied immediately;
  landscape videos remain 16:9.
- The 4:7 default trades a small top/bottom crop for reliable sizing on
  devices that render a true 9:16 window oversized or clipped off-screen.
- On Android 12 and newer, the WebView bounds are supplied as the PiP source
  rectangle to preserve smooth system transitions.

## Known limitations

- Store listing screenshots (`fastlane/metadata/.../phoneScreenshots/`)
  removed as stale; not replaced yet (need real device captures).
