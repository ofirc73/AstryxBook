# Contributing to Astryxbook

Thanks for helping! Astryxbook is a small, volunteer-run Android app that shows Facebook's mobile
site in a WebView, with its own features on top (picture-in-picture, sharing into Facebook,
Messages in desktop mode, app lock, and more). Everyone taking part follows the
[Code of Conduct](CODE_OF_CONDUCT.md).

## Reporting a bug or asking for a feature

- Use the [issue forms](https://github.com/ofirc73/AstryxBook/issues/new/choose): they ask for the
  app version, Android version and device, which most bugs depend on.
- Check you're on the [latest release](https://github.com/ofirc73/AstryxBook/releases/latest)
  first.
- Some behaviour is Facebook's own (a reel pausing again after you resume it, a finished video
  moving on to the next). If you can, check whether the same happens in Chrome on the phone.
- Security problems go through [private reporting](SECURITY.md), never a public issue.
- Don't post your Facebook login, cookies, or screenshots showing other people's private messages.

## Building

1. Install [Android Studio](https://developer.android.com/studio) (it brings the Android SDK and
   JDK 17). The app builds against Android SDK 36 and runs on Android 8 (API 26) and newer.
2. Clone the repository and open it in Android Studio, or build from a terminal:
   ```bash
   ./gradlew assembleDebug
   ```
3. The debug build installs as a separate app (`com.astryx.book.test`) next to a released one,
   with its own Facebook login.

## Making a change

- Branch from `main`, keep each pull request to one change, and describe what it fixes and how
  you checked it (on which Android version and device).
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/): `fix:` for
  a bug fix, `feat:` for a new feature, and `test:`, `refactor:`, `docs:`, `ci:` and so on for
  the rest. Releases are versioned and published from them automatically when a change reaches
  `main`.
- User-visible changes get a line in [FORK_CHANGES.md](../FORK_CHANGES.md).
- Some conventions this codebase relies on (they're explained in [CLAUDE.md](../CLAUDE.md), the
  project's working notes):
  - New text the user sees goes in `brand_strings.xml`, `extra_strings.xml` or
    `privacy_strings.xml` (not `strings.xml`, which belongs to upstream Materialbook's
    translations), with a copy in every translated language (`values-ar`, `-bn`, `-de`, `-es`,
    `-fr`, `-in`, `-it`, `-iw`, `-pl`, `-pt`, `-zh-rTW`).
  - A new setting is listed in `data/local/SettingsBackup.kt`, as exported or not.
  - Scripts added to Facebook's pages (`res/raw`) for PiP stay ES5 (no arrow functions or `?.`);
    don't restyle or resize the video's parent elements, Facebook's reels player watches them.
  - Pages talk to the app through the `AstryxPage` message listener (`PageBridge.kt`), not a new
    `addJavascriptInterface`.

## Testing

Run these before opening a pull request; CI runs them on every one:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew connectedDebugAndroidTest
```

The last needs an emulator or a phone, and uninstalls the debug app when it finishes, logging it
out of Facebook. To keep the login, install with
`./gradlew installDebug installDebugAndroidTest` and run the tests with `adb shell am instrument`
(see CLAUDE.md). The app-level tests (`AppHarness`) drive the real app with test pages, without a
Facebook login.

A bug fix comes with a test that fails without it when that's possible. Never post, comment or
share anything on Facebook while testing: the share and story checks stop at the open editor,
and drafts are discarded.

## License

Astryxbook is licensed under the [GNU GPL v3.0](../LICENSE), like Materialbook and Nobook it's
based on. By contributing, you agree that your contribution is licensed the same way. The name
"Astryxbook" and its logo aren't covered by the license: see [NOTICE.md](../NOTICE.md).
