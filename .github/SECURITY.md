# Security policy

## Supported versions

Only the [latest release](https://github.com/ofirc73/AstryxBook/releases/latest) gets fixes.
Releases are frequent: update before reporting, the problem may already be fixed.

## Reporting a vulnerability

Please report security problems **privately**, not in a public issue:

1. Open the repository's [Security tab](https://github.com/ofirc73/AstryxBook/security).
2. Choose **Report a vulnerability**.
3. Describe the problem, the app version, your Android version and device, and the steps to
   reproduce it.

You'll get an answer through the same private report. This is a personal, volunteer project, so
there's no guaranteed response time, but security reports come first. Once a fix is released, the
report can be published as a security advisory, crediting you if you want.

## What counts

Astryxbook is an Android app that shows Facebook's mobile site in a WebView. Problems in the app
itself are in scope, for example:

- the app lock (unlocking without the screen lock, the page showing while locked);
- the bridges between Facebook's pages and the app (`AstryxPage` and the other page bridges),
  and the scripts the app adds to pages;
- links and intents: pages or other apps getting the app to open something it shouldn't, or
  `intent://` links launching apps;
- files shared into the app, downloads and copied images;
- camera and microphone access for web pages;
- leaking the Facebook login (cookies) or other data to anything but Facebook;
- the build and release pipeline (signing, secrets, GitHub Actions workflows).

Out of scope:

- Facebook itself, its site and its accounts: report those to
  [Meta's bug bounty program](https://www.facebook.com/whitehat).
- Problems that need a rooted phone, a modified app or physical access to an unlocked phone.
- Android System WebView bugs: report them to [Chromium](https://issues.chromium.org/).
