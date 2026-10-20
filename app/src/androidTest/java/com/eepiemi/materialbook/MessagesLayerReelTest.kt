package com.eepiemi.materialbook

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A reel shared in a chat, opened from the Messages layer: it opens in the main view's mobile reel
 * player (the desktop reel page in the layer was tiny, couldn't be enlarged and had no sound), and
 * Back on it goes back to the chat. Driven through the real app, with test pages at Facebook
 * addresses in its WebViews (no login needed); the layer's in-page moves are reported by
 * messages_layer_nav.js, as on the desktop site.
 */
@RunWith(AndroidJUnit4::class)
class MessagesLayerReelTest {

    private val app = AppHarness()

    @Before
    fun setUp() {
        File(app.context.filesDir, "datastore").deleteRecursively()
        app.start()
        assertTrue("the app's WebView never appeared", app.waitFor { app.webViews().isNotEmpty() })
        app.loadPage(MAIN_URL, PAGE)
    }

    @After
    fun tearDown() {
        app.finish()
        File(app.context.filesDir, "datastore").deleteRecursively()
    }

    // Opens the layer (as Create story does) and puts a chat in it.
    private fun openChat(): WebView {
        app.eval("window.AstryxPage.postMessage(JSON.stringify({ call: 'layerUrl', url: 'https://www.facebook.com/stories/create' })); 1")
        assertTrue("the Messages layer never opened", app.waitFor(15_000) { app.layerWebView() != null })
        val layer = app.layerWebView()!!
        app.loadPage("https://www.facebook.com/messages/t/100/", PAGE, layer)
        // The chat the user is in: an in-page move, as the desktop site makes.
        moveTo(layer, CHAT_PATH)
        return layer
    }

    private fun moveTo(layer: WebView, path: String) {
        app.eval("history.pushState({}, '', '$path'); 1", layer)
        Thread.sleep(1500) // messages_layer_nav.js checks every second besides pushState itself
    }

    private fun layerOpen() = app.webViews().size > 1

    // The layer closed and the main view started loading the reel: let that load finish (the
    // real page, or wherever Facebook sends a logged-out view) before putting a test page there.
    private fun reelOpenedInTheMainView() {
        assertTrue("the layer stayed open over the reel", app.waitFor { !layerOpen() })
        assertTrue("the main view never opened the reel", app.waitFor(15_000) {
            app.url(app.mainWebView()).let { it.startsWith("https://m.facebook.com/") && it.contains(REEL_ID) }
        })
        // Not asked of the page itself: a real reel playing can keep it too busy to answer.
        val webView = app.mainWebView()
        app.waitFor(30_000) { app.onActivity { webView.progress } == 100 }
    }

    @Test
    fun aReelOpenedFromAChat_closesTheLayer_andOpensInTheMainView() {
        val layer = openChat()
        moveTo(layer, "/reel/$REEL_ID")
        reelOpenedInTheMainView()
    }

    @Test
    fun backOnTheReel_goesBackToTheChat() {
        val layer = openChat()
        moveTo(layer, "/reel/$REEL_ID")
        reelOpenedInTheMainView()
        // The reel's page in the main view (a test page at its address: no login here).
        app.loadPage("https://m.facebook.com/reel/$REEL_ID/", PAGE, asHistory = true)

        val before = app.url(app.mainWebView())
        app.pressBack()
        assertTrue("Back on the reel never reopened the chat (main view at $before)", app.waitFor { layerOpen() })
        assertTrue("the layer reopened somewhere other than the chat", app.waitFor(15_000) {
            app.url(app.layerWebView()!!).contains(CHAT_ID)
        })
    }

    @Test
    fun backAfterLeavingTheReel_doesNotReopenTheChat() {
        val layer = openChat()
        moveTo(layer, "/reel/$REEL_ID")
        reelOpenedInTheMainView()
        // The user went on from the reel to something else in the main view.
        app.loadPage("https://m.facebook.com/profile.php?id=4", PAGE)

        app.pressBack()
        Thread.sleep(2000)
        assertTrue("Back reopened the chat from a page that isn't the reel", !layerOpen())
    }

    @Test
    fun otherPagesOpenedFromAChat_stayInTheLayer() {
        val layer = openChat()
        moveTo(layer, "/profile.php?id=4")
        Thread.sleep(1000)
        assertTrue("a profile opened from a chat left the layer", layerOpen())
        assertEquals("the main view moved under the layer", "\"$MAIN_URL\"", app.eval("location.href"))
    }

    companion object {
        private const val MAIN_URL = "https://m.facebook.com/home.php"
        private const val CHAT_ID = "4567"
        private const val CHAT_PATH = "/messages/t/$CHAT_ID/"
        private const val REEL_ID = "1360854416127071"
        private const val PAGE =
            "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'></head>" +
                "<body><p>test page</p><script>window.__testPage = true;</script></body></html>"
    }
}
