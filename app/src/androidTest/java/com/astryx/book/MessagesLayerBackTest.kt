package com.astryx.book

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Back in the Messages layer when its page doesn't answer in time (a busy renderer, scripts not run
 * yet): it did nothing there, as it once did in the main view (askPageForBack, shared by both now).
 * The layer's Back on a real chat is the live check's (`backInAChat_goesToTheChatList_andThenClosesTheLayer`).
 */
@RunWith(AndroidJUnit4::class)
class MessagesLayerBackTest {

    private val app = AppHarness()

    @Before
    fun setUp() {
        File(app.context.filesDir, "datastore").deleteRecursively()
        app.startOnPage("https://m.facebook.com/home.php", PLAIN_PAGE)
    }

    @After
    fun tearDown() {
        app.finish()
        File(app.context.filesDir, "datastore").deleteRecursively()
    }

    // A page busy for 4 s, which would then answer "chat" (Back handled by the page itself).
    private fun keepBusy(layer: WebView) = app.eval(
        "window.__astryxLayerBack = function () { var t = Date.now(); while (Date.now() - t < 4000) {} return 'chat'; }; 1", layer
    )

    // Where the layer is in its history (its address doesn't tell: test pages all report about:blank).
    private fun historyIndex(layer: WebView) = app.onActivity { layer.copyBackForwardList().currentIndex }

    @Test
    fun backInTheLayer_whenItsPageDoesntAnswerInTime_stillGoesBack() {
        val layer = app.openLayer(CHAT_URL, PLAIN_PAGE)
        keepBusy(layer)
        val at = historyIndex(layer)
        app.pressBack()
        // Without an answer in 1.5 s Back steps back a page, or closes the layer when there's none.
        // Either here: the page the layer first opened (the story creator, stopped) is in its
        // history when its load got far enough, as on CI's emulator, and Back then goes to it.
        assertTrue("Back did nothing while the layer's page was busy", app.waitFor(3_000) {
            !app.layerOpen() || historyIndex(layer) < at
        })
    }

    @Test
    fun backInTheLayer_whenItsPageDoesntAnswerInTime_stepsBackAPage_whenThereIsOne() {
        val layer = app.openLayer(CHAT_URL, PLAIN_PAGE)
        app.loadPage("$CHAT_URL?n=2", PLAIN_PAGE, layer)
        keepBusy(layer)
        val at = historyIndex(layer)
        app.pressBack()
        assertTrue("Back didn't step back from the layer's busy page", app.waitFor(3_000) { historyIndex(layer) == at - 1 })
        assertTrue("Back closed the layer with a page to go back to", app.layerOpen())
    }

    private companion object {
        const val CHAT_URL = "https://www.facebook.com/messages/t/100/"
    }
}
