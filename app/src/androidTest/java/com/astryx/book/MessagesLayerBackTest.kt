package com.astryx.book

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

    @Test
    fun backInTheLayer_whenItsPageDoesntAnswerInTime_stillGoesBack() {
        val layer = app.openLayer("https://www.facebook.com/messages/t/100/", PLAIN_PAGE)
        // A page busy for 4 s, which would then answer "chat" (Back handled by the page itself).
        app.eval("window.__astryxLayerBack = function () { var t = Date.now(); while (Date.now() - t < 4000) {} return 'chat'; }; 1", layer)
        app.pressBack()
        // Without an answer in 1.5 s Back steps back a page; with none (a test page has no history)
        // the layer closes.
        assertTrue("Back did nothing while the layer's page was busy", app.waitFor(3_000) { !app.layerOpen() })
    }
}
