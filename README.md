<p align="middle">
    <img src='./fastlane/metadata/android/en-US/images/featureGraphic.png' alt="Astryxbook banner" width="100%">
</p>

<h1 align="middle">
    Astryxbook
</h1>

<p align="middle">
    <a href='./LICENSE'><img alt='License: GPL-3.0' src='https://img.shields.io/badge/license-GPL--3.0-blue'/></a>
    <a href='https://github.com/ofirc73/AstryxBook/releases/latest'><img alt='Android 8.0+' src='https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white'/></a>
    <a href='https://github.com/ofirc73/AstryxBook/actions/workflows/create-release.yml'><img alt='Release build' src='https://github.com/ofirc73/AstryxBook/actions/workflows/create-release.yml/badge.svg?branch=main'/></a>
</p>

An open-source, ad-free Android app for Facebook's mobile site, written in Kotlin with Jetpack Compose: picture-in-picture video with lock-screen audio, Messages and Messenger calls in desktop mode, sharing photos and videos into Facebook, downloads, AMOLED black, distraction filters, app lock and Facebook notifications. Not affiliated with Meta (see [NOTICE.md](./NOTICE.md)).

> **Kept working as Facebook changes:** a daily check runs the app against the live Facebook site, so when Facebook changes its pages the break shows up on our side within a day and the fix ships in the next release.

<h1 align="middle">
    📱 Download
</h1>

<p align="middle">
    <a href='https://github.com/ofirc73/AstryxBook/releases/latest'><img alt='Download' height='40' src='./assets/download.svg'/></a>
    <a href='https://apps.obtainium.imranr.dev/redirect?r=obtainium%3A%2F%2Fadd%2Fhttps%3A%2F%2Fgithub.com%2Fofirc73%2FAstryxBook'><img alt='Get it on Obtainium' height='48' src='https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png'/></a>
</p>

<p align="middle">
    Official releases are published only at <a href='https://github.com/ofirc73/AstryxBook/releases'>github.com/ofirc73/AstryxBook</a>, signed with the same key every time, so each one installs as an update over the last. <a href='https://github.com/ImranR98/Obtainium'>Obtainium</a> installs them from there and keeps the app up to date.
</p>

<p align="middle">
    <a href='https://grev.shehryar.ae/?owner=ofirc73&repo=AstryxBook'><img alt='Downloads count' height='40' src='https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fofirc73%2FAstryxBook%2Frefs%2Fheads%2Fstats%2Fbadge.json&style=for-the-badge&logo=github&labelColor=24292F'/></a>
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

Most features are toggles in the app's Settings.

On by default:
*  Blocks sponsored ads
*  Keeps the navigation bar at the top
*  Downloads media or copies it to the clipboard

Off by default (opt-in):
*  Uses Material You colors instead of Facebook's blues (Facebook's own blue is the default theme)
*  Makes Facebook AMOLED Black
*  Hides distractions like:
    *  Suggested posts
    *  Reels
    *  Stories
    *  Groups
    *  People you may know
*  Hides the system bars (immersive mode)
*  Allows pinch-to-zoom anywhere, and can wrap text to the screen when zoomed in, like Opera (Settings → *Wrap text when zoomed*)
*  Zooms whole pages, text and pictures together, from 85% to 150% (Settings → *Page zoom*)
*  Opens web links in a browser tab over the app instead of switching apps (Settings → *Open web links in the app*)
*  Lets you access Messenger (Desktop layout only)
*  Opens Messages inside the app on the desktop site, in its own layer over the feed, so Back returns you to where you were; it can turn to landscape for more room (Settings → *Messages in desktop mode*); links to a conversation keep it
*  Makes Messenger voice and video calls from Messages (the camera and microphone are asked for on the first call, for Facebook only)
*  Supports Picture-in-Picture playback with selectable portrait-window ratios
*  Keeps a Picture-in-Picture video's audio playing when the screen locks
*  Keeps the screen on while a video plays, or whenever the app is open (Settings → *Keep screen on*)
*  Locks the app with your fingerprint, face or screen lock, and hides it in Recents (Settings → *App lock*)
*  Notifies you of new Facebook notifications, unread chats and friend requests while the app is closed, checking every 15 minutes (Settings → *Facebook notifications*)

Always on:
*  Shares links from other apps to Facebook, and the page you're on to other apps (the share button in Settings), without tracking parameters
*  Shares photos and videos from other apps into Facebook, with the Facebook app's own entries under Astryxbook in the share sheet: *Your story*, *Feed*, *Set as profile picture*, *Your groups* and *Reels*. You caption and post in Facebook's own composer
*  Adapts Facebook's Story creator for phones: *Create story* on the feed and *Add story* from a Story open the creator in Astryxbook; the music sticker stays hidden while the song remains attached; Facebook's AI-label control and disclosure stay hidden if moved outside the sidebar while posting
*  Launcher shortcuts for Messages, Notifications and Marketplace (long-press the app icon)
*  Exports your settings to a file and imports them on another phone (Settings → *Export / Import settings*)
*  Supports real fullscreen video, including landscape, from Facebook's fullscreen button
   (on phones the app otherwise stays in portrait, apart from Messages, since Facebook's mobile site breaks in landscape)
*  One-tap *Log out and clear data* in Settings
*  Supports English, Arabic, Bengali, German, Hebrew (`עברית`), Italian, Spanish, French, Portuguese, Indonesian, Polish, and Traditional Chinese
*  And more!

<h2 align="middle">
    📱 Screenshots
</h2>

<p align="middle">
    <img src='./assets/screenshots/page.png' alt="A Facebook Page in the app" width="19%">
    <img src='./assets/screenshots/page_zoom_130.png' alt="The same Page at 130% page zoom" width="19%">
    <img src='./assets/screenshots/pip.jpg' alt="A video in Picture-in-Picture over the home screen" width="19%">
    <img src='./assets/screenshots/settings.png' alt="Astryxbook settings" width="19%">
    <img src='./assets/screenshots/settings_notifications.png' alt="Facebook notifications and privacy settings" width="19%">
</p>
<p align="middle"><sub>A Page at 100% and 130% page zoom · Picture-in-Picture · Settings</sub></p>

<h2 align="middle">
    🖼️ Picture-in-Picture
</h2>

To use Picture-in-Picture:

1. Enable **Picture-in-Picture** in the app's Settings.
2. Tap **PiP aspect ratio** (shown when PiP is enabled) and choose the window ratio for portrait videos:
   * **4:7** (recommended; default)
   * **2:3**
   * **3:4**
   * **9:16** (may overflow on some devices)
3. If needed, also allow PiP in Android under **Settings > Apps > Astryxbook > Picture-in-picture**.
4. Open a Facebook video or Reel and start playback.
5. Keep the video visible and audible, then use the Home gesture or Home button.

On Android 12 and newer, PiP is configured for a smooth automatic transition as you leave
the app. On older supported Android versions, PiP is entered when Android notifies the app
that it is being left.

The app ignores muted feed autoplay and selects the largest visible audible video when
multiple videos are present. Landscape videos use their detected source ratio when available,
including ratios such as 7:4, 4:3, and 16:9, with 16:9 fallback when dimensions are unavailable.
Portrait videos use the ratio selected in Settings, and a changed selection is applied immediately
to the active video without restarting the app.

The default 4:7 ratio is a reliable compromise: it stays close to the video's true 9:16
shape while avoiding the oversized or off-screen window seen on some devices. The wider
4:7, 2:3, and 3:4 windows crop the top and bottom as needed to fill the PiP window without
black bars. Selecting 9:16 preserves the exact video shape, but the window may overflow on
some devices. Extreme detected landscape ratios are clamped to Android's supported PiP range.

The page itself is hidden while in PiP so only the video shows, filling the window.
Rotating the phone while a video is in PiP keeps the same video playing.

The PiP window itself is Android's (Android 12 and newer):

* one tap shows its controls (Play/Pause, settings, close);
* a double tap switches between its current size and its largest size;
* a pinch resizes it;
* dragging it to the screen's edge tucks it away.

The first PiP after the app opens starts at Android's default size, which is too small for the
controls: a tap enlarges the window while they show, and it shrinks back a few seconds later.
Android remembers a bigger size for the next videos. To keep the first one bigger too, double-tap
or pinch it once. Apps can only choose the window's shape (the aspect ratio above), not its size.

To keep listening with the screen locked, also turn on **Keep audio when screen locks**
(shown under the PiP settings). Locking the screen while a video is in PiP then hands its
audio to a native player, with media controls on the lock screen. Unlocking hands it back to
the video at the same position. Facebook's video links expire after a while, so audio during
a very long lock can stop; the video is still restored where it left off.

A video that was playing keeps playing when PiP starts, on the mobile and the desktop site.
If it ever stops, tap the Play button on the PiP overlay to resume it.

The PiP window closes by itself when there's nothing left in it to play, and the app stays in the
background where it was. That happens when Facebook takes the video off the page or reloads the
whole page (for example after a reel was left paused for a while with the screen off), or when
Android closed the app during a long lock and starts it again inside the PiP window. PiP doesn't
move on to the next reel.

If PiP does not appear, confirm that the video is actively playing with volume above zero
and that both the app and Android system PiP permissions are enabled.

<h2 align="middle">
    🛠️ Setup
</h2>

Astryxbook requires Android 8.0 (API level 26) or newer.

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

Unit tests, tests of the scripts the app adds to Facebook's pages, and app-level tests that drive the real app (picture-in-picture, sharing, the Messages layer, app lock, links). A daily check also runs the app against the live Facebook site to catch changes on Facebook's side.

*  **Unit tests** (no device/emulator needed): `./gradlew test`
*  **Android lint** (no device/emulator needed): `./gradlew :app:lintDebug`
*  **Instrumented tests** (needs a connected device or running emulator): `./gradlew connectedAndroidTest`

GitHub Actions runs all three checks. Android lint reports are uploaded as a workflow artifact for every run. The lint gate treats dependency-update notices as advisory while still failing on actionable Android findings.

<h2 align="middle">
    🤝 Contributing
</h2>

Bug reports, ideas and pull requests are welcome: see [CONTRIBUTING.md](.github/CONTRIBUTING.md) and the [Code of Conduct](.github/CODE_OF_CONDUCT.md). Security problems: please report them privately, as described in [SECURITY.md](.github/SECURITY.md).

<h2 align="middle">
    💗 Acknowledgement
</h2>

*  This is a fork of [Materialbook](https://github.com/eepiemi/Materialbook) by eepiemi, itself a fork of [Nobook](https://github.com/ycngmn/Nobook) by ycngmn
*  [@KevinnZou/compose-webview-multiplatform](https://github.com/KevinnZou/compose-webview-multiplatform)

<h2 align="middle">
    ⚖️ License
</h2>

Astryxbook is free software, licensed under the [GNU General Public License v3.0](./LICENSE), the same license as Materialbook and Nobook, which it's based on.

*  Copyright (C) 2026 Ofir Cohen, for the changes made in this fork (see [FORK_CHANGES.md](./FORK_CHANGES.md))
*  The original code remains copyright of its authors: eepiemi (Materialbook) and ycngmn (Nobook)

You may use, change and share it under the GPL-3.0's terms: copies and modified versions must stay under the same license, with their source code available and these copyright notices kept.

The name "Astryxbook" and its logo aren't covered by the license: modified versions need their own name, logo and application ID. See [NOTICE.md](./NOTICE.md) for these additional terms (GPL-3.0, section 7).

**Official source.** The only official Astryxbook is this repository and the [releases](https://github.com/ofirc73/AstryxBook/releases) published here. Forks and other copies aren't affiliated with or endorsed by Astryxbook or its author, and neither are their owners or what they publish, say or support.
