<p align="middle">
    <img src='./fastlane/metadata/android/en-US/images/featureGraphic.png' alt="Astryxbook banner" width="100%">
</p>

<h1 align="middle">
    📱 Download 
</h1>

<p align="middle">
    <a href='https://github.com/ofirc73/AstryxBook/releases/latest'><img alt='Download' height='40' src='./assets/download.svg'/></a>
</p>

<p align="middle">
    <a href='https://grev.shehryar.ae/?owner=ofirc73&repo=AstryxBook'><img alt='Downloads count' height='40' src='https://img.shields.io/github/downloads/ofirc73/AstryxBook/total?style=for-the-badge&logo=github&label=Downloads&color=1877F2&labelColor=24292F'/></a>
</p>

<h2 align="middle">
    🙋 Have issues?
</h2>

<p align="middle">
    <a href='https://github.com/ofirc73/AstryxBook/issues/new/choose'><img alt='Open issue' height='40' src='./assets/open_issue.svg'/></a>
</p>

<h2 align="middle">
    ✏️ This fork:
</h2>

*  Renames the app to **Astryxbook**, with an original "A" monogram launcher icon (Facebook blue, but not Facebook's own trademarked logo — avoids impersonation/trademark issues)
*  Restyles it to match **Facebook's own original Android look** — Material You theming and AMOLED Black now ship **off by default**, using Facebook's native blue instead of your wallpaper colors out of the box
*  Both settings remain available as opt-in toggles for anyone who preferred the upstream [Materialbook](https://github.com/eepiemi/Materialbook) look
*  Everything below still applies whenever those toggles are switched on
*  See [FORK_CHANGES.md](./FORK_CHANGES.md) for the full list of changes in this fork

<h2 align="middle">
    ⚙️ Features
</h2>

If enabled, the app:
*  Uses Material You colors instead of Facebook's blues
*  Makes Facebook AMOLED Black
*  Blocks sponsored ads
*  Hides distractions like:
    *  Suggested posts
    *  Reels
    *  Stories
    *  Groups
    *  People you may know
*  Keeps the navigation bar at the top
*  Downloads media or copies it to the clipboard
*  Supports Picture-in-Picture playback for visible, audible Facebook videos
*  And more!

<h2 align="middle">
    🖼️ Picture-in-Picture
</h2>

To use Picture-in-Picture:

1. Enable **Picture-in-Picture** in the app's Settings.
2. If needed, also allow it in Android under **Settings > Apps > Astryxbook > Picture-in-picture**.
3. Open a Facebook video or Reel and start playback.
4. Keep the video visible and audible, then use the Home gesture or Home button.

On Android 12 and newer, PiP is configured for a smooth automatic transition as you leave
the app. On older supported Android versions, PiP is entered when Android notifies the app
that it is being left.

The app ignores muted feed autoplay and selects the largest visible audible video when
multiple videos are present. Landscape videos use a landscape PiP window; vertical Reels
use a portrait-ish window capped at 2:3 (not the video's true 9:16) — some devices size a
true 9:16 PiP window oversized/clipped off-screen, so this trades exact video shape for a
reliably-sized window, at the cost of cropping the top/bottom of the video slightly to fill
the window with no black bars (the same crop-to-fill approach YouTube/TikTok/Instagram use
when their own players are forced into a non-matching window shape).

The page itself is hidden while in PiP so only the video shows, filling the window.

Android/Chromium auto-pauses the video the moment PiP starts (a platform limitation, not
something an app can override) — tap the Play button on the PiP overlay to resume it.

If PiP does not appear, confirm that the video is actively playing with volume above zero
and that both the app and Android system PiP permissions are enabled.

<h2 align="middle">
    🛠️ Setup
</h2>

1.  **Clone the repository**
    * In Android Studio:
      * File > New > Project from Version Control
      * Paste `https://github.com/ofirc73/AstryxBook.git` and clone.
    * Or via terminal: 
    ```
    git clone https://github.com/ofirc73/AstryxBook.git
    cd AstryxBook
    ``` 
2.  **Open in Android Studio.** (only if cloned via terminal)
    * Select Open an Existing Project and choose the cloned folder.
3.  **Sync the project** to download dependencies.
4.  **Run the app** in a device or emulator.

<h2 align="middle">
    ✅ Testing
</h2>

Covers the rebrand and default-behavior changes in this fork — defaults (Material You/AMOLED off), theme colors, app identity/strings, launcher icon, applicationId, and the pinned scripts source.

*  **Unit tests** (no device/emulator needed): `./gradlew test`
*  **Instrumented tests** (needs a connected device or running emulator): `./gradlew connectedAndroidTest`

<h2 align="middle">
    💗 Acknowledgement:
</h2>

*  This is a fork of [Materialbook](https://github.com/eepiemi/Materialbook) by eepiemi, itself a fork of [Nobook](https://github.com/ycngmn/Nobook) by ycngmn
*  [@KevinnZou/compose-webview-multiplatform](https://github.com/KevinnZou/compose-webview-multiplatform)  
