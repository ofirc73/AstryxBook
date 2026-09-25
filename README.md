# Astryxbook

[![GitHub stars][shields-stars]][github-repos]
[![GitHub license][shield-license]][github-repos]
[![GitHub downloads][shield-download]][github-repos]
[![Android SDK][shield-sdk]][github-repos]

## Overview

**Astryxbook** is an ad‑free, privacy‑focused Android client for Facebook (Lite) that adopts the Astryx visual language. It offers full‑featured Picture‑in‑PiP support for reels, selective portrait‑ratio windows, and a long list of customization options while keeping the original Facebook look and feel.

> 📱 **Download:** latest release – [GitHub Releases][github-repos/releases]
> 🛠️ **Source:** <https://github.com/ofirc73/AstryxBook>
> 🐛 **Issues / Feature requests:** <https://github.com/ofirc73/AstryxBook/issues>

---

## Features

- **Ad‑free** – blocks sponsored posts and video ads
- **Material You theming** – optional palette derived from your wallpaper
- **AMOLED Black** – optional dark mode that saves battery
- **Sponsored‑ad & distraction blocking** – hide suggested posts, reels, stories, groups, “People you may know”, etc.
- **Navigation‑bar keep‑at‑top** – optional persistent top bar
- **Media download & clipboard copy** – long‑press any video or image to save or copy
- **Picture‑in‑PiP playback** – floating window with selectable portrait ratios (4:7, 2:3, 3:4, 9:16) and auto‑matching landscape ratios
- **Multi‑language** – Arabic, Bengali, German, Hebrew (`עברית`), Italian, Spanish, French, Portuguese, Traditional Chinese, and more
- **Open‑source & fully fork‑able** – built on Materialbook, fully rebranded

---

## Picture‑in‑PiP

1. **Enable PiP** in the app’s Settings.
2. **Choose a portrait‑window ratio** (shown when PiP is enabled):
   * **4:7** – recommended default; close to 9:16 but crops top/bottom to fit most devices
   * **2:3**
   * **3:4**
   * **9:16** – exact video shape; may overflow on some screens
3. **Allow PiP** in Android: **Settings → Apps → Astryxbook → Picture‑in‑picture**.
4. **Open a Facebook video or Reel** and start playback.
5. **Leave the app** (Home gesture or Home button) – the video will shrink into the PiP overlay.
6. **Resume / Pause** using the PiP overlay controls.
   *On Android 12+ the transition is smooth; older versions rely on the Android notification‑based entry.*

### Landscape video handling

- Detected source ratios (7:4, 4:3, 16:9, etc.) are auto‑matched.
- Extreme landscape ratios are clamped to Android‑supported PiP ranges.
- Fallback to 16:9 when dimensions are unavailable.
- The 4:7 default ratio provides a reliable compromise between true 9:16 shape and device‑safe sizing.

### Landscape‑ratio auto‑matching details

- `MIN_PIP_ASPECT_RATIO = 100/239` and `MAX_PIP_ASPECT_RATIO = 239/100` bound the valid PiP range.
- Detected source ratio is clamped to these bounds; if unavailable, 16:9 is used.

### Page chrome while in PiP

- The entire page is hidden; only the video fills the PiP window, giving a clean native‑player feel.

### Pause/resume note

- Android/Chromium auto‑pauses the WebView video when PiP starts – a platform limitation. Tap **Play** on the PiP overlay to resume.

---

## Localization

All translated locales include Astryxbook branding and the latest PiP ratio strings:

| Language | Code |
|----------|------|
| Arabic     | `ar` |
| Bengali    | `bn` |
| German     | `de` |
| Hebrew     | `iw` |
| Italian    | `it` |
| Spanish    | `es` |
| French     | `fr` |
| Portuguese | `pt` (Brazil) |
| Traditional Chinese | `zh` |

---

## Setup / Building

| Step | Description |
|------|-------------|
| **1. Clone** | ```bash git clone https://github.com/ofirc73/AstryxBook.git``` |
| **2. Open** | Android Studio → **Open an Existing Project** → select the cloned folder |
| **3. Sync** | Let Gradle download dependencies |
| **4. Run** | Connect a device or launch an emulator and press **Run > App** |

**Minimum SDK:** Android 8.0 (API 26)
**Target SDK:** API 36 (compile‑SDK 36)

---

## Testing

| Check | Command |
|-------|---------|
| **Unit tests** (no device) | `./gradlew test` |
| **Android lint** (no device) | `./gradlew :app:lintDebug` |
| **Instrumented tests** (device/emulator) | `./gradlew connectedAndroidTest` |
| **All checks via GitHub Actions** – lint reports uploaded as artifacts; CI gates every PR.

---

## Acknowledgement

- This fork builds on **[Materialbook](https://github.com/eepiemi/Materialbook)** by *eepiemi*.
- Original idea & some code from **[Nobook](https://github.com/ycngmn/Nobook)** by *ycngmn*.
- UI‑multiplatform foundation by **[KevinnZou/compose-webview-multiplatform](https://github.com/KevinnZou/compose-webview-multiplatform)**.
- Icons and branding assets released under the **MIT** license.

---

## License

```
MIT License

Copyright (c) 2026 Astryxbook contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

...
```
(The full MIT license text is included in the repository’s `LICENSE` file.)

---

**[↩ Return to top**](#astrixbook)

---
**Shield URLs** (for markdown rendering)

```markdown
[shield-stars]: https://img.shields.io/github/stars/ofirc73/AstryxBook?style=for-the-badge&label=Stars&color=1877F2&labelColor=24292F
[shield-license]: https://img.shields.io/github/license/ofirc73/AstryxBook?style=for-the-badge&label=License&color=1877F2&labelColor=24292F
[shield-download]: https://img.shields.io/github/downloaders/ofirc73/AstryxBook/total?style=for-the-badge&label=Downloads&color=1877F2&labelColor=24292F
[shield-sdk]: https://img.shields.io/badge/SDK-Android%208.0%20%28API%2026%29-brightgreen?style=for-the-badge
[github-repos]: https://github.com/ofirc73/AstryxBook
[github-repos/releases]: https://github.com/ofirc73/AstryxBook/releases/latest
```
