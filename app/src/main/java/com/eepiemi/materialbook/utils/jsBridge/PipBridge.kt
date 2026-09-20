package com.eepiemi.materialbook.utils.jsBridge

import android.util.Log
import android.webkit.JavascriptInterface

private const val TAG = "AstryxbookPiP"

/**
 * Reports the currently active video state so the activity can decide when to
 * enter PiP and how to size the floating window.
 */
class PipBridge(private val onVideoStateChanged: (Boolean, Int, Int) -> Unit) {
    @JavascriptInterface
    fun setVideoPlaying(isPlaying: Boolean) {
        Log.d(TAG, "PipBridge.setVideoPlaying: isPlaying=$isPlaying")
        onVideoStateChanged(isPlaying, 0, 0)
    }

    @JavascriptInterface
    fun setVideoState(isPlaying: Boolean, videoWidth: Int, videoHeight: Int) {
        Log.d(TAG, "PipBridge.setVideoState: isPlaying=$isPlaying, ${videoWidth}x$videoHeight")
        onVideoStateChanged(isPlaying, videoWidth, videoHeight)
    }
}
