package com.astryx.book.utils.jsBridge
import android.util.Log
import android.webkit.JavascriptInterface

private const val TAG = "AstryxbookPiP"

/**
 * Reports the currently active video state so the activity can decide when to
 * enter PiP and how to size the floating window.
 */
class PipBridge(
    private val onVideoStateChanged: (Boolean, Int, Int) -> Unit,
    private val onHandoffEventCallback: (event: String, data: String) -> Unit = { _, _ -> },
    private val onVideoGoneCallback: () -> Unit = {},
) {
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

    // Permanent diagnostic, not debug-only scaffolding: PIP_FOCUS_MODE_JS's
    // own after-the-fact sanity check calls this only when it finds an
    // element still visibly on-screen after hiding everything it knows to
    // hide. Silent/free in the normal case; when it does fire, it gives the
    // exact tag/id/class of the leaking element from an ordinary field
    // logcat capture, no live DevTools session required - useful for
    // intermittent, hard-to-reproduce leaks (page chrome, a stray control)
    // that vary by which video/page layout Facebook happens to render.
    @JavascriptInterface
    fun logPipAnomaly(message: String) {
        Log.w(TAG, "PiP focus mode: unexpected visible element(s): $message")
    }

    // Lock-screen audio handoff events from PIP_HANDOFF_ARM_JS and pipHandbackJs, with their
    // [data] (JSON): 'sync' (armed, every second: the PiP video's sound, time and whether it
    // plays), 'hidden' (the screen going off), 'visible' (unlocked, a handback signal),
    // 'handbackDone' (the page plays in step with the native player). Runs on the JS bridge
    // thread, not the main thread.
    @JavascriptInterface
    fun onHandoffEvent(event: String, data: String) {
        if (event != "sync") Log.d(TAG, "PipBridge.onHandoffEvent: $event")
        onHandoffEventCallback(event, data)
    }

    // Called by PIP_VIDEO_GONE_WATCH_JS when the PiP video has been gone from the page for a
    // while: the app closes the empty PiP window. Runs on the JS bridge thread on old WebViews.
    @JavascriptInterface
    fun onPipVideoGone() {
        Log.d(TAG, "PipBridge.onPipVideoGone")
        onVideoGoneCallback()
    }
}
