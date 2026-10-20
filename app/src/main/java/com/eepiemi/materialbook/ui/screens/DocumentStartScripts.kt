package com.eepiemi.materialbook.ui.screens

import android.util.Log
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.eepiemi.materialbook.R

// Page scripts installed as document-start scripts (before Facebook's own scripts run), each
// falling back gracefully on a WebView without DOCUMENT_START_SCRIPT (Chrome 87+).

private val PIP_VISIBILITY_ORIGINS = setOf(
    "https://facebook.com", "https://*.facebook.com",
    "https://messenger.com", "https://*.messenger.com"
)

// Installs pip_visibility.js (see the script) as a document-start script, so it wraps
// IntersectionObserver before Facebook's own scripts create theirs. Needs WebView's
// DOCUMENT_START_SCRIPT (Chrome 87+); without it, desktop-site videos still pause in PiP.
internal fun installPipVisibilityScript(webView: android.webkit.WebView) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
    val script = webView.resources.openRawResource(R.raw.pip_visibility)
        .bufferedReader().use { it.readText() }
    runCatching { WebViewCompat.addDocumentStartJavaScript(webView, script, PIP_VISIBILITY_ORIGINS) }
        .onFailure { Log.w("AstryxbookPiP", "pip_visibility.js not installed", it) }
}

private val OPEN_FEEDS_ORIGINS = setOf("https://facebook.com", "https://*.facebook.com")

// Installs page_zoom.js (page zoom, see the script) for [percent]; its handler removes it when
// the setting changes. Null at 100% and without DOCUMENT_START_SCRIPT (Chrome 87+).
internal fun installPageZoomScript(webView: android.webkit.WebView, percent: Int): ScriptHandler? {
    if (percent == 100 || !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return null
    val script = webView.resources.openRawResource(R.raw.page_zoom)
        .bufferedReader().use { it.readText() }
    return runCatching {
        WebViewCompat.addDocumentStartJavaScript(
            webView, "window.__astryxPageZoom = ${percent / 100.0};\n$script", OPEN_FEEDS_ORIGINS
        )
    }.onFailure { Log.w("Astryxbook", "page_zoom.js not installed", it) }.getOrNull()
}

// Installs open_feeds.js ("Start on Feeds", see the script) as a document-start script, so the
// menu it starts from is hidden from the first frame. Returns false without DOCUMENT_START_SCRIPT
// (Chrome 87+); the script is then run once the page has loaded instead.
internal fun installOpenFeedsScript(webView: android.webkit.WebView): Boolean {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return false
    val script = webView.resources.openRawResource(R.raw.open_feeds)
        .bufferedReader().use { it.readText() }
    return runCatching { WebViewCompat.addDocumentStartJavaScript(webView, script, OPEN_FEEDS_ORIGINS) }
        .onFailure { Log.w("Astryxbook", "open_feeds.js not installed", it) }
        .isSuccess
}
