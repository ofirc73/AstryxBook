package com.astryx.book.ui.screens

import android.util.Log
import com.multiplatform.webview.web.WebViewNavigator
import com.multiplatform.webview.web.WebViewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long Back waits for the page's answer before going on without it. */
internal const val BACK_ANSWER_WAIT_MS = 1500L

/**
 * Asks the page what Back did there ([js], which answers a string: scripts.js `backHandlerNB` in
 * the main view, messages_layer_nav.js `__astryxLayerBack` in the Messages layer) and hands the
 * answer to [onAnswer], or null when there's none within [BACK_ANSWER_WAIT_MS]. A page whose scripts
 * haven't run yet gives none, and a busy renderer answers late or not at all (both on GitHub's
 * emulator, 2026-10-30): Back then did nothing. A late answer is dropped. Asked of the WebView
 * itself (the navigator passes on only its last event), else through [navigator].
 */
internal fun askPageForBack(
    scope: CoroutineScope,
    state: WebViewState,
    navigator: WebViewNavigator,
    js: String,
    onAnswer: (String?) -> Unit,
) {
    var answered = false
    val noAnswer = scope.launch {
        delay(BACK_ANSWER_WAIT_MS)
        answered = true
        Log.d("Astryxbook", "Back: the page didn't answer")
        onAnswer(null)
    }
    val answer = { reply: String? ->
        if (!answered) {
            answered = true
            noAnswer.cancel()
            onAnswer(reply?.removeSurrounding("\""))
        }
    }
    runCatching { state.nativeWebView.evaluateJavascript(js) { answer(it) } }
        .onFailure { navigator.evaluateJavaScript(js) { answer(it) } }
}
