package com.astryx.book.ui.screens
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * feed_posting.js tells the app (AstryxPage "feedPosting") while a post is still uploading after
 * Post, as read on the emulator (2026-11-03): the composer closes (/composer/ to /), Facebook's
 * snackbar says "Uploading your post..." while its WebSocket still has bytes to send, then
 * "Posted". A fake WebSocket stands in for Facebook's (its bufferedAmount and readyState set by
 * the test); the script wraps it as it wraps the real one at document start.
 */
@RunWith(AndroidJUnit4::class)
class FeedPostingJsTest {

    private val page = PageFixture()

    private fun open(atComposer: Boolean = true) {
        page.load(if (atComposer) "https://m.facebook.com/composer/" else "https://m.facebook.com/", "<html><body></body></html>")
        page.stubPageCalls()
        page.eval(
            "window.WebSocket = function (url) { this.url = url; this.readyState = 1; this.bufferedAmount = 0; };" +
                "window.WebSocket.CONNECTING = 0; window.WebSocket.OPEN = 1; window.WebSocket.CLOSING = 2; window.WebSocket.CLOSED = 3;" +
                "window.WebSocket.prototype.send = function (data) { this.bufferedAmount += data.length; };" +
                "window.__astryxFeedPostingSettleMs = 1500; 1"
        )
        page.run(R.raw.feed_posting)
        page.eval("window.__socket = new WebSocket('wss://kaios-d.facebook.com/ws/1'); 1")
        Thread.sleep(CHECK_MS) // the script sees the composer
    }

    // Post: the composer closes in-page and Facebook's snackbar shows [text].
    private fun post(text: String = "Uploading your post...") = page.eval(
        "history.pushState({}, '', '/');" +
            "var bar = document.createElement('div'); bar.className = 'snackbar-container'; bar.textContent = '$text';" +
            "document.body.appendChild(bar); 1"
    )

    private fun sending(bytes: Int) = page.eval("window.__socket.send(new Array($bytes + 1).join('x')); 1")

    private fun calls(): String = page.pageCalls()

    @Test
    fun aPostStillUploading_isPosting_untilItsSent_andPosted() {
        open()
        sending(300_000)
        assertEquals("the most held unsent, for the live check", "300000", page.eval("window.__astryxFeedPosting.mostUnsent"))
        post()
        assertTrue("posting never reported (${calls()})", page.waitFor { calls() == "\"feedPosting posting\"" })
        // Sent, then Facebook's "Posted".
        page.eval("window.__socket.bufferedAmount = 0; document.querySelector('.snackbar-container').textContent = 'Posted'; 1")
        assertTrue("posted never reported (${calls()})", page.waitFor { calls() == "\"feedPosting posting, feedPosting posted\"" })
    }

    @Test
    fun allSent_withNoPostedSeen_isPosted_aWhileLater() {
        // The page hidden (the app left), its snackbar's "Posted" missed.
        open()
        sending(1000)
        post()
        assertTrue(page.waitFor { calls() == "\"feedPosting posting\"" })
        page.eval("window.__socket.bufferedAmount = 0; 1")
        Thread.sleep(CHECK_MS)
        assertEquals("posted before it settled", "\"feedPosting posting\"", calls())
        assertTrue(page.waitFor { calls() == "\"feedPosting posting, feedPosting posted\"" })
    }

    @Test
    fun theSocketClosingWithBytesUnsent_isAFailure() {
        open()
        sending(5000)
        post()
        assertTrue(page.waitFor { calls() == "\"feedPosting posting\"" })
        page.eval("window.__socket.readyState = 3; 1")
        assertTrue("the failure never reported (${calls()})", page.waitFor { calls() == "\"feedPosting posting, feedPosting failed\"" })
    }

    @Test
    fun aPostWhosePhotosAreAllUp_isNotPosting() {
        // Nothing left to lose: Facebook's server finishes it on its own.
        open()
        post()
        Thread.sleep(QUIET_MS)
        assertEquals("\"\"", calls())
    }

    @Test
    fun aSnackbarAwayFromTheComposer_isNotPosting() {
        // A photo still going up while the page shows another snackbar ("Link copied") elsewhere.
        open(atComposer = false)
        sending(5000)
        post("Link copied")
        Thread.sleep(QUIET_MS)
        assertEquals("\"\"", calls())
    }

    private companion object {
        // The script's check (500 ms), twice.
        const val CHECK_MS = 1000L
        const val QUIET_MS = 2000L
    }
}
