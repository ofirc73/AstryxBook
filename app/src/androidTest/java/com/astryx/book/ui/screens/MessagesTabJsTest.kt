package com.astryx.book.ui.screens
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.astryx.book.PageFixture
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import com.astryx.book.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for messages_tab.js (*Messages in desktop mode*, mobile page side): a tap on the Messages
 * tab (found by its English label or its icon) goes straight to m.facebook.com/messages/, which the
 * app opens in the Messages layer, and Facebook's own handler never sees it; other tabs are left
 * alone. Live: theMessagesTab_opensMessagesInTheLayer.
 */
@RunWith(AndroidJUnit4::class)
class MessagesTabJsTest {

    private val page = PageFixture(width = 1080, height = 2000)

    private fun url(): String {
        var url = ""
        InstrumentationRegistry.getInstrumentation().runOnMainSync { url = page.webView.url.orEmpty() }
        return url
    }

    // A tab bar whose tabs record Facebook's own handling of a tap; [messages] is the Messages tab's inside.
    private fun load(messages: String) {
        page.load(
            "https://m.facebook.com/",
            "<html><body><div role='tablist'>" +
                "<div role='tab' id='feed' aria-label='feed, 1 of 5'>Feed</div>" +
                "<div role='tab' id='messages' $messages</div>" +
                "</div><script>window.__facebook = 0; document.querySelectorAll('[role=tab]').forEach(function (t) {" +
                " t.addEventListener('click', function () { window.__facebook++; }); });</script></body></html>"
        )
        page.run(R.raw.messages_tab)
    }

    private fun tap(id: String) = page.eval(
        "var t = document.getElementById('$id'); ['pointerup', 'click'].forEach(function (type) {" +
            " t.dispatchEvent(new MouseEvent(type, { bubbles: true, cancelable: true })); }); 1"
    )

    private fun assertGoesToMessages() {
        assertTrue({ "the tap didn't go to /messages/ (${url()})" }, page.waitFor(10_000) { url() == "https://m.facebook.com/messages/" })
    }

    @Test
    fun theMessagesTab_byItsLabel_goesStraightToMessages() {
        load("aria-label='messages, 3 of 5'>Chats")
        tap("messages")
        assertEquals("Facebook's own handler saw the tap", "0", page.eval("window.__facebook"))
        assertGoesToMessages()
    }

    @Test
    fun theMessagesTab_byItsIcon_inAnyLanguage() {
        load("aria-label='הודעות'><span id='icon'></span>")
        page.eval("document.getElementById('icon').textContent = String.fromCodePoint(0xF0388); 1")
        tap("messages")
        assertGoesToMessages()
    }

    @Test
    fun anotherTab_isLeftToFacebook() {
        load("aria-label='messages, 3 of 5'>Chats")
        tap("feed")
        Thread.sleep(500)
        assertEquals("1", page.eval("window.__facebook"))
        assertFalse(url(), url().contains("/messages"))
    }
}
