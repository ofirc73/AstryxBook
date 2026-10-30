package com.astryx.book.ui.screens
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

private val OPEN_FEEDS_ORIGINS = setOf("https://facebook.com", "https://*.facebook.com")

// Installs the bundled script [id] (with [prefix] before it) for [origins]; null without
// DOCUMENT_START_SCRIPT (Chrome 87+) or when the WebView refuses it (logged under [tag]).
private fun installDocumentStartScript(
    webView: android.webkit.WebView, id: Int, origins: Set<String>, tag: String, prefix: String = ""
): ScriptHandler? {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return null
    val script = webView.resources.openRawResource(id).bufferedReader().use { it.readText() }
    return runCatching { WebViewCompat.addDocumentStartJavaScript(webView, prefix + script, origins) }
        .onFailure { Log.w(tag, "${webView.resources.getResourceEntryName(id)}.js not installed", it) }
        .getOrNull()
}

// Installs pip_visibility.js (see the script) as a document-start script, so it wraps
// IntersectionObserver before Facebook's own scripts create theirs. Needs WebView's
// DOCUMENT_START_SCRIPT (Chrome 87+); without it, desktop-site videos still pause in PiP.
internal fun installPipVisibilityScript(webView: android.webkit.WebView) {
    installDocumentStartScript(webView, R.raw.pip_visibility, PIP_VISIBILITY_ORIGINS, "AstryxbookPiP")
}

// Installs feed_prefetch.js (main view; see the script): a list's next posts are asked for while
// its loading row is still a few screens below, not once it shows. Without DOCUMENT_START_SCRIPT
// (Chrome 87+) they load as Facebook's own page does.
internal fun installFeedPrefetchScript(webView: android.webkit.WebView) {
    installDocumentStartScript(webView, R.raw.feed_prefetch, OPEN_FEEDS_ORIGINS, "Astryxbook")
}

// Installs page_zoom.js (page zoom, see the script) for [percent]; its handler removes it when
// the setting changes. Null at 100% and without DOCUMENT_START_SCRIPT (Chrome 87+).
internal fun installPageZoomScript(webView: android.webkit.WebView, percent: Int): ScriptHandler? {
    if (percent == 100) return null
    return installDocumentStartScript(
        webView, R.raw.page_zoom, OPEN_FEEDS_ORIGINS, "Astryxbook", "window.__astryxPageZoom = ${percent / 100.0};\n"
    )
}

// Installs open_feeds.js ("Start on Feeds", see the script) as a document-start script, so the
// menu it starts from is hidden from the first frame. Returns false without DOCUMENT_START_SCRIPT
// (Chrome 87+); the script is then run once the page has loaded instead.
internal fun installOpenFeedsScript(webView: android.webkit.WebView): Boolean =
    installDocumentStartScript(webView, R.raw.open_feeds, OPEN_FEEDS_ORIGINS, "Astryxbook") != null
