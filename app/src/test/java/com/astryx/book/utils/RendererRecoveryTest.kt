package com.astryx.book.utils
import org.junit.Assert.assertEquals
import org.junit.Test

class RendererRecoveryTest {

    private val home = "https://facebook.com/"

    @Test
    fun firstLoss_reloadsTheFacebookPageThatWasShowing() {
        val page = "https://m.facebook.com/groups/123/permalink/456/"
        assertEquals(page, rendererRecoveryUrl(page, home, lastLossAtMillis = null, nowMillis = 1_000))
    }

    @Test
    fun lossLongAfterTheLastOne_reloadsThePageAgain() {
        val page = "https://m.facebook.com/reel/1"
        val last = 5_000L
        assertEquals(page, rendererRecoveryUrl(page, home, last, last + RENDERER_RETRY_WINDOW_MS + 1))
    }

    @Test
    fun lossSoonAfterTheLastOne_goesHome() {
        val page = "https://m.facebook.com/reel/1"
        val last = 5_000L
        assertEquals(home, rendererRecoveryUrl(page, home, last, last))
        assertEquals(home, rendererRecoveryUrl(page, home, last, last + RENDERER_RETRY_WINDOW_MS))
    }

    @Test
    fun unknownOrNonFacebookPage_goesHome() {
        assertEquals(home, rendererRecoveryUrl(null, home, null, 1_000))
        assertEquals(home, rendererRecoveryUrl("about:blank", home, null, 1_000))
        assertEquals(home, rendererRecoveryUrl("https://example.com/", home, null, 1_000))
        assertEquals(home, rendererRecoveryUrl("https://facebook.com:443@example.com/", home, null, 1_000))
        assertEquals(home, rendererRecoveryUrl("https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com", home, null, 1_000))
    }

    @Test
    fun messagesLayer_reopensItsOwnPage() {
        val chat = "https://www.facebook.com/messages/t/123"
        val layerStart = "https://www.facebook.com/messages/"
        assertEquals(chat, rendererRecoveryUrl(chat, layerStart, null, 1_000))
        assertEquals(layerStart, rendererRecoveryUrl(chat, layerStart, 900, 1_000))
    }
}
