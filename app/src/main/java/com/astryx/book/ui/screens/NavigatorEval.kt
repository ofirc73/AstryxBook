package com.astryx.book.ui.screens
import com.multiplatform.webview.web.WebViewNavigator
import kotlinx.coroutines.CompletableDeferred

/**
 * [script]'s result in the navigator's page, as evaluateJavaScript gives it (a JSON value as text),
 * once the page answers. Only for a page that's loaded: the navigator replays only its last event
 * to a WebView not attached yet (CLAUDE.md), so an early call may never be answered.
 */
internal suspend fun WebViewNavigator.evaluated(script: String): String {
    val result = CompletableDeferred<String>()
    evaluateJavaScript(script) { result.complete(it) }
    return result.await()
}
