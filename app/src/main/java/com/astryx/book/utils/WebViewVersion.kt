package com.astryx.book.utils
/**
 * The oldest Android System WebView (Chrome) version the page scripts work on. They use optional
 * chaining (`?.`, Chrome 80), so on an older WebView (Android 8/9 without Play updates, e.g. the
 * stock API 26 emulator image's Chrome 69) most of them fail to parse and the app half-works.
 * Not rewritten for older ones by choice (2026-10-08); the app asks for a WebView update instead.
 */
const val MIN_WEBVIEW_MAJOR = 80

/** The major version from a WebView package's version name ("69.0.3497.100" -> 69), or null. */
fun webViewMajor(versionName: String?): Int? =
    versionName?.substringBefore('.')?.trim()?.toIntOrNull()

/** Whether a WebView of [versionName] is too old for the page scripts (unknown: assumed fine). */
fun isWebViewTooOld(versionName: String?): Boolean =
    webViewMajor(versionName)?.let { it < MIN_WEBVIEW_MAJOR } ?: false
