package com.eepiemi.materialbook.utils.jsBridge

import android.webkit.JavascriptInterface

/**
 * The Messages layer's in-page URL changes (messages_layer_nav.js). Runs on the JS bridge
 * thread.
 */
class LayerBridge(private val onUrlChangedCallback: (String) -> Unit) {
    @JavascriptInterface
    fun onUrlChanged(url: String) {
        onUrlChangedCallback(url)
    }
}
