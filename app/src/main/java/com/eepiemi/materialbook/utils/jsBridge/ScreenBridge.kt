package com.eepiemi.materialbook.utils.jsBridge

import android.webkit.JavascriptInterface

/**
 * Reports whether a video is playing on the page (video_playing.js), for the
 * "Keep screen on: while a video plays" setting. Runs on the JS bridge thread.
 */
class ScreenBridge(private val onVideoPlayingChanged: (Boolean) -> Unit) {
    @JavascriptInterface
    fun setVideoPlaying(isPlaying: Boolean) {
        onVideoPlayingChanged(isPlaying)
    }
}
