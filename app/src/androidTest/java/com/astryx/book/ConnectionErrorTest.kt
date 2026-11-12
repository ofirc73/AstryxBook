package com.astryx.book

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.ui.components.connectionRetries
import com.eepiemi.materialbook.R
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A page Facebook can't be reached for, in the main view and in the Messages layer: the app's own
 * screen covers it, with a button that loads the page again, and goes once a page loads. The page
 * stays underneath (the dialog it replaces took the WebView away and could only close the app; the
 * layer showed the browser's own error page).
 */
@RunWith(AndroidJUnit4::class)
class ConnectionErrorTest {

    private val app = AppHarness()
    private val retry get() = app.context.getString(R.string.connection_retry)

    @Before
    fun setUp() {
        File(app.context.filesDir, "datastore").deleteRecursively()
    }

    @After
    fun tearDown() {
        app.finish()
        File(app.context.filesDir, "datastore").deleteRecursively()
    }

    @Test
    fun aPageThatCantBeReached_offersToTryAgain_andAPageThatLoadsClearsIt() {
        app.startOnPage(PAGE_URL, PLAIN_PAGE)
        assertFalse("the error screen shows over a page that loaded", app.textShown(retry))
        offersToTryAgain(app.mainWebView(), PAGE_URL)
    }

    @Test
    fun aLayerPageThatCantBeReached_offersToTryAgain_overTheLayer() {
        app.startOnPage(PAGE_URL, PLAIN_PAGE)
        val layer = app.openLayer(CHAT_URL, PLAIN_PAGE)
        offersToTryAgain(layer, CHAT_URL)
        assertTrue("the layer was closed", app.layerOpen())
    }

    // [webView] sent to an address that can't be reached: Try again shows over it, loads it again
    // (failing again), and a page that loads ([url], another one) clears it.
    private fun offersToTryAgain(webView: WebView, url: String) {
        fun page() = "the WebView at ${app.url(webView)}, ${app.onActivity { webView.progress }}% loaded; ${app.screenState()}"
        // The harness keeps the layer off the network (openLayer), which turns a refused connection
        // into a blocked load: let this one through (a loopback address, nothing leaves the device).
        app.onActivity {
            webView.settings.blockNetworkLoads = false
            webView.loadUrl(UNREACHABLE_URL)
        }
        assertTrue({ "no Try again for a page that can't be reached (${page()})" }, app.waitFor(30_000) { app.textShown(retry) })
        assertTrue("the page was taken away with the error", app.webViews().contains(webView))

        val retries = connectionRetries.get()
        assertTrue(app.tapText(retry))
        assertTrue("Try again didn't load the page again", app.waitFor { connectionRetries.get() == retries + 1 })
        assertTrue({ "the page failed again, without its error screen (${page()})" }, app.waitFor(30_000) { app.textShown(retry) })

        app.loadPage("$url?n=2", PLAIN_PAGE, webView)
        assertTrue("the error screen stayed over a page that loaded", app.waitFor { !app.textShown(retry) })
    }

    companion object {
        private const val PAGE_URL = "https://m.facebook.com/some.page/posts/2/"
        private const val CHAT_URL = "https://www.facebook.com/messages/t/100/"
        // Nothing listens there: the connection is refused at once, with or without a network (a name
        // that doesn't resolve took CI's emulator over 30 s to give up on).
        private const val UNREACHABLE_URL = "https://127.0.0.1:47111/"
    }
}
