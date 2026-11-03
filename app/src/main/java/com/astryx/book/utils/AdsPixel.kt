package com.astryx.book.utils
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import com.multiplatform.webview.web.AccompanistWebViewClient
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

// Facebook's own Google Ads remarketing tag: every full m.facebook.com page load adds two hidden
// www.fbsbx.com/paid_ads_pixel/iframe/ frames, which load Google's gtag.js and report the visit
// to doubleclick.net and google.com (rmkt/collect, ccm/collect, pagead/1p-user-list), then log
// that to fbsbx.com/paid_ads_pixel/logging/. Nothing on Facebook's pages depends on it.
private val ADS_PIXEL_URL =
    Regex("""^https?://(?:[a-z0-9-]+\.)*fbsbx\.com(?::\d+)?/paid_ads_pixel(?:[/?#]|$)""", RegexOption.IGNORE_CASE)

/** Whether [url] is part of Facebook's Google Ads remarketing pixel (frame or logging). */
fun isAdsPixelUrl(url: String): Boolean = ADS_PIXEL_URL.containsMatchIn(url)

/** The ads pixel's requests answered empty since the app started (the live Facebook check reads it). */
internal val adsPixelBlocks = AtomicInteger()

/**
 * Answers requests the pages don't need with an empty 204 instead of loading them: the ads
 * pixel's, so its frame stays empty and Google's scripts never load, and the story creator's
 * text-story backgrounds during a story share ([StoryBackgrounds]). Everything else goes on to the
 * library's client.
 */
open class RequestBlockingClient : AccompanistWebViewClient() {
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url?.toString()
        if (url != null && isAdsPixelUrl(url)) {
            adsPixelBlocks.incrementAndGet()
            return emptyResponse()
        }
        if (url != null && StoryBackgrounds.skips(view, url)) {
            StoryBackgrounds.skipped.incrementAndGet()
            return emptyResponse()
        }
        return super.shouldInterceptRequest(view, request)
    }

    private fun emptyResponse() =
        WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))
}
