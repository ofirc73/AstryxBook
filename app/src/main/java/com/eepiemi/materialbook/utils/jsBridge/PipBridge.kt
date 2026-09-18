package com.eepiemi.materialbook.utils.jsBridge

import android.webkit.JavascriptInterface

/**
 * Reports the currently active video state so the activity can decide when to
 * enter PiP and how to size the floating window.
 */
class PipBridge(private val onVideoStateChanged: (Boolean, Int, Int) -> Unit) {
    @JavascriptInterface
    fun setVideoPlaying(isPlaying: Boolean) {
        onVideoStateChanged(isPlaying, 0, 0)
    }

    @JavascriptInterface
    fun setVideoState(isPlaying: Boolean, videoWidth: Int, videoHeight: Int) {
        onVideoStateChanged(isPlaying, videoWidth, videoHeight)
    }
}
