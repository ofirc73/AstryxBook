package com.astryx.book.utils
import android.net.Uri
import android.webkit.WebResourceRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdsPixelTest {

    @Test
    fun pixelFrameAndLogging_areTheAdsPixel() {
        assertTrue(isAdsPixelUrl("https://www.fbsbx.com/paid_ads_pixel/iframe/?tag_ids%5B0%5D=AW-1&is_remarketing"))
        assertTrue(isAdsPixelUrl("https://www.fbsbx.com/paid_ads_pixel/logging/?funnel_id=x"))
        assertTrue(isAdsPixelUrl("https://fbsbx.com/paid_ads_pixel"))
        assertTrue(isAdsPixelUrl("HTTPS://WWW.FBSBX.COM:443/paid_ads_pixel/iframe/"))
    }

    @Test
    fun otherUrls_areNotTheAdsPixel() {
        // Messenger's proxy page is on the same host and needed.
        assertFalse(isAdsPixelUrl("https://www.fbsbx.com/maw_proxy_page/?__cci=x"))
        assertFalse(isAdsPixelUrl("https://www.fbsbx.com/paid_ads_pixels/"))
        assertFalse(isAdsPixelUrl("https://m.facebook.com/paid_ads_pixel/"))
        assertFalse(isAdsPixelUrl("https://example.com/?u=https://www.fbsbx.com/paid_ads_pixel/"))
        assertFalse(isAdsPixelUrl("https://www.fbsbx.com@example.com/paid_ads_pixel/"))
        assertFalse(isAdsPixelUrl("https://fbsbx.com.example.com/paid_ads_pixel/"))
    }

    @Test
    fun client_answersThePixelWithAnEmpty204() {
        val response = AdsPixelBlockingClient().shouldInterceptRequest(
            null, request("https://www.fbsbx.com/paid_ads_pixel/iframe/?tag_ids%5B0%5D=AW-1")
        )!!
        assertEquals(204, response.statusCode)
        assertEquals(-1, response.data.read())
    }

    private fun request(url: String) = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)
        override fun isForMainFrame() = false
        override fun isRedirect() = false
        override fun hasGesture() = false
        override fun getMethod() = "GET"
        override fun getRequestHeaders() = emptyMap<String, String>()
    }
}
