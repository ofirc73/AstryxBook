package com.astryx.book.ui.screens
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * feed_posting.js tells the app (AstryxPage "feedPosting") from Post until Facebook has the post,
 * as read on the emulator (2026-11-03): Post tapped (the composer's top row, right of back) with
 * the photo's preview in the composer, which then closes (/composer/ to /); Facebook's snackbar
 * says "Uploading your post...", has no text a while as the photo goes up a piece at a time over
 * its WebSocket, then says "Posted"; Discard closes the composer after its "Discard post?" dialog.
 * A fake WebSocket stands in for Facebook's; the script wraps it as it wraps the real one at
 * document start.
 */
@RunWith(AndroidJUnit4::class)
class FeedPostingJsTest {

    private val page = PageFixture()

    private fun open(atComposer: Boolean = true, withPhoto: Boolean = true) {
        val preview = if (withPhoto) "<img src='data:image/gif;base64,R0lGODlhAQABAAAAACw='>" else ""
        page.load(if (atComposer) "https://m.facebook.com/composer/" else "https://m.facebook.com/", "<html><body>$COMPOSER_TOP$preview</body></html>")
        page.stubPageCalls()
        page.eval(
            "window.WebSocket = function (url) { this.url = url; this.readyState = 1; this.bufferedAmount = 0; };" +
                "window.WebSocket.CONNECTING = 0; window.WebSocket.OPEN = 1; window.WebSocket.CLOSING = 2; window.WebSocket.CLOSED = 3;" +
                "window.WebSocket.prototype.send = function (data) { this.bufferedAmount += data.length; };" +
                "window.__astryxFeedPostingSettleMs = 3000; 1"
        )
        page.run(R.raw.feed_posting)
        page.eval("window.__socket = new WebSocket('wss://kaios-d.facebook.com/ws/1'); 1")
        Thread.sleep(CHECK_MS) // the script sees the composer
    }

    // Post: the composer closes in-page, and Facebook's snackbar shows [text] (none yet: null).
    private fun post(text: String? = "Uploading your post...") = page.eval(
        "history.pushState({}, '', '/');" +
            (if (text == null) "" else "var bar = document.createElement('div'); bar.className = 'snackbar-container';" +
                " bar.textContent = '$text'; document.body.appendChild(bar);") + " 1"
    )

    private fun tap(id: String) = page.eval("document.querySelector('#$id div').click(); 1")

    private fun snackbar(text: String) = page.eval("document.querySelector('.snackbar-container').textContent = '$text'; 1")

    private fun sending(bytes: Int) = page.eval("window.__socket.send(new Array($bytes + 1).join('x')); 1")

    private fun calls(): String = page.pageCalls()

    private fun awaitPosting() = assertTrue("posting never reported (${calls()})", page.waitFor { calls() == "\"feedPosting posting\"" })

    private fun awaitPosted() =
        assertTrue("posted never reported (${calls()})", page.waitFor { calls() == "\"feedPosting posting, feedPosting posted\"" })

    @Test
    fun aPost_isPosting_untilFacebookSaysPosted() {
        // "Uploading your post...", a while with no text, then "Posted" (2026-11-03, 128 kbps up).
        open()
        sending(300_000)
        assertEquals("the most held unsent, for the live check", "300000", page.eval("window.__astryxFeedPosting.mostUnsent"))
        post()
        awaitPosting()
        snackbar(" ")
        Thread.sleep(CHECK_MS)
        assertEquals("posted before Facebook said so", "\"feedPosting posting\"", calls())
        snackbar("Posted")
        awaitPosted()
    }

    @Test
    fun aPostedShownOnlyAMoment_isSeen() {
        // With the page hidden its timers ran seconds apart, and "Posted" was missed (on the phone).
        open()
        post()
        awaitPosting()
        page.eval(
            "var bar = document.querySelector('.snackbar-container'); bar.textContent = 'Posted';" +
                " setTimeout(function () { bar.remove(); }, 50); 1"
        )
        awaitPosted()
    }

    @Test
    fun aTapOnPost_isPosting_beforeTheComposerCloses() {
        // Over a slow link the composer stayed open for seconds after Post.
        open()
        sending(5000)
        tap("post")
        awaitPosting()
        assertEquals("/composer/", page.eval("location.pathname").trim('"'))
    }

    @Test
    fun aTapOnAnotherButton_orOnPostWithoutAPhoto_isNotPosting() {
        open()
        tap("audience")
        tap("back")
        Thread.sleep(QUIET_MS)
        assertEquals("\"\"", calls())
        open(withPhoto = false)
        tap("post")
        Thread.sleep(QUIET_MS)
        assertEquals("\"\"", calls())
    }

    @Test
    fun closingTheComposer_isPosting_beforeFacebooksSnackbarShows() {
        // Over a slow link "Uploading your post..." came seconds after Post.
        open()
        post(text = null)
        awaitPosting()
        page.eval(
            "var bar = document.createElement('div'); bar.className = 'snackbar-container'; bar.textContent = 'Uploading your post...';" +
                " document.body.appendChild(bar); 1"
        )
        Thread.sleep(CHECK_MS)
        assertEquals("posted at Facebook's first text", "\"feedPosting posting\"", calls())
        snackbar("Posted")
        awaitPosted()
    }

    @Test
    fun noPostedSeen_endsWithoutAWord_aWhileAfterThePhotosLastPiece() {
        // The page hidden (the app left), its snackbar's "Posted" missed.
        open()
        post()
        awaitPosting()
        repeat(4) { sending(30_000); Thread.sleep(500) }
        assertEquals("ended while the photo went up", "\"feedPosting posting\"", calls())
        assertTrue("never ended (${calls()})", page.waitFor { calls() == "\"feedPosting posting, feedPosting ended\"" })
    }

    @Test
    fun aDiscardedPost_isNotPosting() {
        // Facebook's "Discard post?" dialog, then the composer closes.
        open()
        sending(5000)
        page.eval("var d = document.createElement('div'); d.setAttribute('role', 'dialog'); d.id = 'sheet'; document.body.appendChild(d); 1")
        Thread.sleep(CHECK_MS)
        page.eval("document.getElementById('sheet').remove(); 1")
        post(text = null)
        Thread.sleep(QUIET_MS)
        assertEquals("\"\"", calls())
    }

    @Test
    fun aComposerClosedWithoutAPhoto_isNotPosting() {
        // An empty composer closed by Back (no dialog asks then).
        open(withPhoto = false)
        post(text = null)
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
        // The composer's top row (its back button left, Post right, neither labelled by text the
        // script reads) and its audience button below, as on m.facebook.com/composer/.
        const val COMPOSER_TOP =
            "<div style='display:flex;justify-content:space-between;height:44px'>" +
                "<div role='button' id='back' aria-label='back' style='width:40px'><div>&lt;</div></div>" +
                "<div role='button' id='post' style='width:160px'><div>Post</div></div></div>" +
                "<div role='button' id='audience' style='width:114px;height:28px'><div>Only me</div></div>"
        // The script's check (500 ms), twice.
        const val CHECK_MS = 1000L
        const val QUIET_MS = 2000L
    }
}
