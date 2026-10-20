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
        // The main view then opens the real reel; kept off the network, so that load ends at once
        // (logged in, on a local emulator, the real reel played and slowed the emulator until the
        // page stopped answering; logged out, Facebook answers differently). The address it
        // opens is what's checked.
        val main = app.mainWebView()
        app.onActivity { main.settings.blockNetworkLoads = true }
        app.eval("window.AstryxPage.postMessage(JSON.stringify({ call: 'layerUrl', url: 'https://www.facebook.com/stories/create' })); 1")
        assertTrue("the Messages layer never opened", app.waitFor(15_000) { app.layerWebView() != null })
        val layer = app.layerWebView()!!
        // The real page the layer opened (the story creator) is heavy and goes on loading; it's
        // kept off the network and stopped, the chat here is a test page anyway.
        app.onActivity { layer.settings.blockNetworkLoads = true; layer.stopLoading() }
        app.loadPage("https://www.facebook.com/messages/t/100/", PAGE, layer)
        // messages_layer_nav.js reports the layer's moves. The layer runs it once a load finishes,
        // which a stopped load can leave out; it's run here then, as the layer would.
        if (!app.waitFor(5_000) { app.eval("!!window.__astryxLayerNavInstalled", layer) == "true" }) {
            val nav = app.context.resources.openRawResource(R.raw.messages_layer_nav).bufferedReader().use { it.readText() }
            app.eval(nav + "; 1", layer)
        }
        assertEquals("true", app.eval("!!window.__astryxLayerNavInstalled", layer))
        // The chat the user is in: an in-page move, as the desktop site makes.
        moveTo(layer, CHAT_PATH)
        return layer
    }

    private fun moveTo(layer: WebView, path: String, settle: Boolean = true) {
        app.eval("history.pushState({}, '', '$path'); 1", layer)
        if (settle) Thread.sleep(1500) // messages_layer_nav.js checks every second besides pushState itself
    }

    private fun layerOpen() = app.webViews().size > 1

    // The layer closed and the main view started loading the reel: let that load finish (the
    // real page, or wherever Facebook sends a logged-out view) before putting a test page there.
    private fun reelOpenedInTheMainView() {
        assertTrue("the layer stayed open over the reel", app.waitFor { !layerOpen() })
        assertTrue("the main view never opened the reel", app.waitFor(15_000) {
            app.url(app.mainWebView()).let { it.startsWith("https://m.facebook.com/") && it.contains(REEL_ID) }
        })
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
        // The main view is at the reel's address (its load kept off the network, see openChat).
        assertTrue(app.url(app.mainWebView()).startsWith("https://m.facebook.com/reel/$REEL_ID"))

        val before = app.url(app.mainWebView())
        app.pressBack()
        assertTrue("Back on the reel never reopened the chat (main view at $before)", app.waitFor { layerOpen() })
        assertTrue("the layer reopened somewhere other than the chat", app.waitFor(15_000) {
            app.url(app.layerWebView()!!).contains(CHAT_ID)
        })
    }

    @Test
    fun aReelFromAShareLink_goesBackToTheChat_notTheLink() {
        // A reel shared in a chat comes as Facebook's share link, which redirects to the reel.
        // Back reopened the link (so the reel) on every press.
        val layer = openChat()
        moveTo(layer, "/share/r/AbCdEf123/")
        assertTrue("the share link left the layer", layerOpen())
        moveTo(layer, "/reel/$REEL_ID")
        reelOpenedInTheMainView()

        app.pressBack()
        assertTrue("Back on the reel never reopened the chat", app.waitFor { layerOpen() })
        assertTrue("Back reopened something other than the chat (the share link?)", app.waitFor(15_000) {
            app.layerWebView()?.let { app.url(it) }.orEmpty().let { it.contains(CHAT_ID) && !it.contains("/share/") }
        })
    }

    @Test
    fun theChatStaysOnScreen_untilTheReelIsInTheMainView() {
        // Closing the layer at once showed the main view's feed for a second before the reel.
        val layer = openChat()
        moveTo(layer, "/reel/$REEL_ID", settle = false)
        var mainUrlWhenClosed: String? = null
        val main = app.mainWebView()
        assertTrue("the layer never closed", app.waitFor(15_000) {
            val open = layerOpen()
            if (!open) mainUrlWhenClosed = app.url(main)
            !open
        })
        assertTrue(
            "the layer closed before the main view had the reel (it showed $mainUrlWhenClosed)",
            mainUrlWhenClosed.orEmpty().startsWith("https://m.facebook.com/reel/$REEL_ID")
        )
    }

    @Test
    fun backAfterLeavingTheReel_doesNotReopenTheChat() {
        val layer = openChat()
        moveTo(layer, "/reel/$REEL_ID")
        reelOpenedInTheMainView()
        // The user went on from the reel to something else in the main view (also kept off the
        // network: only its address matters).
        val main = app.mainWebView()
        app.onActivity { main.loadUrl("https://m.facebook.com/profile.php?id=4") }
        assertTrue(app.waitFor(15_000) { app.url(main).contains("profile.php") && app.onActivity { main.progress } == 100 })

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
