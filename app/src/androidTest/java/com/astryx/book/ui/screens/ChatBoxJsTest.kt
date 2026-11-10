package com.astryx.book.ui.screens
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import com.astryx.book.assertTrue
import com.eepiemi.materialbook.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * chat_box.js keeps what's typed and not sent in a chat's message box (Messages layer) and puts it
 * back when the chat opens again on a new page: Messenger keeps it only while its page lives, and
 * Back on the chat list closes the layer. The chat is a test page at a chat's address with a
 * contenteditable box, as Messenger's (role=textbox); Enter empties it without an input event, as
 * Messenger's Send does. A test WebView isn't on screen, so the page's focus is stubbed.
 */
@RunWith(AndroidJUnit4::class)
class ChatBoxJsTest {

    private val page = PageFixture(width = 1080, height = 2000, domStorage = true)

    private fun open(path: String, html: String = CHAT, n: Int = 1) {
        page.load("https://www.facebook.com/messages/$path/?n=$n", html)
        page.eval("document.hasFocus = function () { return true; }; 1")
        page.run(R.raw.chat_box)
    }

    private fun box() = page.eval("document.getElementById('box').innerText.replace(/\\n+$/, '')").removeSurrounding("\"")

    private fun type(text: String) {
        page.eval("var b = document.getElementById('box'); b.focus(); document.execCommand('insertText', false, '$text'); 1")
        Thread.sleep(600) // kept 0.3 s after the input
    }

    // The test WebViews share the app's storage: the kept messages are dropped before and after.
    @After
    fun freshStorage() {
        page.load("https://www.facebook.com/messages/", "<html><body></body></html>")
        page.eval("localStorage.removeItem('astryxChatDrafts'); 1")
    }

    @Test
    fun anUnsentMessage_isBackInItsChat_onANewPage() {
        freshStorage()
        open(CHAT_PATH)
        type("see you at 8")
        open(CHAT_PATH, n = 2)
        assertTrue({ "the unsent message wasn't put back (box: ${box()})" }, page.waitFor { box() == "see you at 8" })
        Thread.sleep(500)
        assertEquals("the message went in more than once", "see you at 8", box())
    }

    @Test
    fun aSentMessage_isntPutBack() {
        freshStorage()
        open(CHAT_PATH)
        type("on my way")
        // Enter sends: the box empties without an input event, then the key comes up.
        page.eval(
            "var b = document.getElementById('box');" +
                " b.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));" +
                " b.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter', bubbles: true })); 1"
        )
        Thread.sleep(600)
        open(CHAT_PATH, n = 2)
        Thread.sleep(800)
        assertEquals("a sent message came back", "", box())
    }

    @Test
    fun anotherChat_andABoxAlreadyHoldingText_areLeftAsTheyAre() {
        freshStorage()
        open(CHAT_PATH)
        type("for chat one")
        open("t/2", n = 2)
        Thread.sleep(800)
        assertEquals("another chat got the message", "", box())
        open(CHAT_PATH, CHAT_WITH_TEXT, n = 3)
        Thread.sleep(800)
        assertEquals("a box with text in it got the kept message too", "already here", box())
    }

    @Test
    fun aMessageKeptOverAMonth_isDropped() {
        freshStorage()
        page.eval(
            "localStorage.setItem('astryxChatDrafts', JSON.stringify({ '$CHAT_PATH':" +
                " { text: 'old', at: Date.now() - 31 * 24 * 3600 * 1000 } })); 1"
        )
        open(CHAT_PATH)
        Thread.sleep(800)
        assertEquals("a month-old message came back", "", box())
    }

    private companion object {
        const val CHAT_PATH = "e2ee/t/1"
        // Enter empties the box, as Messenger's Send does (no input event).
        const val ENTER_SENDS =
            "<script>document.getElementById('box').addEventListener('keydown', function (e) {" +
                " if (e.key === 'Enter') { e.preventDefault(); this.innerHTML = ''; } });</script>"
        const val CHAT =
            "<html><body><div id='box' role='textbox' contenteditable='true' style='height:40px;width:300px'></div>" +
                ENTER_SENDS + "</body></html>"
        const val CHAT_WITH_TEXT =
            "<html><body><div id='box' role='textbox' contenteditable='true' style='height:40px;width:300px'>already here</div>" +
                ENTER_SENDS + "</body></html>"
    }
}
