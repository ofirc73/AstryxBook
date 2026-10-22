package com.eepiemi.materialbook.utils

import android.graphics.Bitmap
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView

/**
 * WebView draws pages in a separate renderer process. Android can kill it to reclaim memory
 * (mostly while the app is in the background, with a video in PiP or on the lock screen, say),
 * or it can crash. By default WebView then takes the whole app down with it. This client
 * reports it instead, through [onGone] (on the main thread), and the screen replaces the
 * WebView: the one passed to [onGone] can't be used again, so only read its URL and destroy
 * it once it's out of the layout. Several WebViews can share one renderer (the main view and
 * the Messages layer), and each one that loses it gets its own call. It also blocks Facebook's
 * Google Ads pixel ([AdsPixelBlockingClient]).
 */
class RendererGoneClient(
    private val onPageStarted: () -> Unit = {},
    private val onGone: (WebView) -> Unit,
) : AdsPixelBlockingClient() {
    // A new page (not Facebook's in-page navigation, which doesn't start one); see PageStarts.
    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted()
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        Log.w(TAG, "WebView renderer gone (crashed: ${detail.didCrash()}), replacing the WebView")
        onGone(view)
        return true
    }

    private companion object {
        const val TAG = "Astryxbook"
    }
}

/** A second renderer loss within this long of the last one doesn't reload the same page. */
const val RENDERER_RETRY_WINDOW_MS = 60_000L

/**
 * The page the replacement WebView opens: the one that was showing ([pageUrl]), when it's a
 * Facebook page, else [home]. If the renderer was lost [RENDERER_RETRY_WINDOW_MS] or less after
 * the last time ([lastLossAtMillis], on a monotonic clock), the page itself may be what kills
 * it, so [home] opens instead of reloading it again.
 */
fun rendererRecoveryUrl(pageUrl: String?, home: String, lastLossAtMillis: Long?, nowMillis: Long): String {
    val retriedTooSoon = lastLossAtMillis != null && nowMillis - lastLossAtMillis <= RENDERER_RETRY_WINDOW_MS
    return pageUrl?.takeIf { !retriedTooSoon && isFacebookWebUrl(it) } ?: home
}

/** The URL a WebView whose renderer is gone was showing, or null. */
fun deadWebViewUrl(view: WebView): String? = runCatching { view.url }.getOrNull()
