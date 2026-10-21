package com.eepiemi.materialbook

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eepiemi.materialbook.data.local.SettingsDataStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Facebook's Google Ads pixel (www.fbsbx.com/paid_ads_pixel/, a hidden frame on every
 * m.facebook.com page) is answered with an empty 204 by the WebViews' own client, in the main
 * view and in the Messages layer.
 */
@RunWith(AndroidJUnit4::class)
class AdsPixelBlockTest {

    private val app = AppHarness()
    private val pixel = "https://www.fbsbx.com/paid_ads_pixel/iframe/?tag_ids%5B0%5D=AW-1&is_remarketing"

    @Before
    fun setUp() {
        File(app.context.filesDir, "datastore").deleteRecursively()
    }

    @After
    fun tearDown() {
        app.finish()
        runBlocking { SettingsDataStore(app.context).setMessagesDesktop(false) }
        File(app.context.filesDir, "datastore").deleteRecursively()
    }

    private fun request(url: String) = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)
        override fun isForMainFrame() = false
        override fun isRedirect() = false
        override fun hasGesture() = false
        override fun getMethod() = "GET"
        override fun getRequestHeaders() = emptyMap<String, String>()
    }

    private fun assertBlocks(webView: WebView) {
        val response = app.onActivity { webView.webViewClient.shouldInterceptRequest(webView, request(pixel)) }
        assertNotNull("the pixel was let through", response)
        assertEquals(204, response!!.statusCode)
        assertEquals(-1, response.data.read())
    }

    @Test
    fun mainView_blocksThePixel() {
        app.start()
        assertBlocks(app.mainWebView())
    }

    @Test
    fun messagesLayer_blocksThePixel() {
        runBlocking { SettingsDataStore(app.context).setMessagesDesktop(true) }
        app.start(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/messages/t/4567/"))
                .setClass(app.context, MainActivity::class.java)
        )
        assertTrue("the Messages layer never opened", app.waitFor(20_000) { app.layerWebView() != null })
        assertBlocks(app.layerWebView()!!)
    }
}
