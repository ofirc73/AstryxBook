package com.astryx.book.ui.screens
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Back in the Messages layer (messages_layer_nav.js, `__astryxLayerBack`): in a chat it presses the
 * chat header's own Back button, the icon at the top left with the chat's name beside it; on the
 * chat list it leaves the layer to the app. The fixtures are the desktop site's rows at phone width
 * as read from the emulator (2026-10-24): the Back button 36 px at (32, 84), the name 12 px to its
 * right, the message box at the bottom (laid out with no width while the chat list shows).
 *
 * It also brings a chat back after a page it opened in the layer (a shared post): opened again at
 * its address, an end-to-end encrypted chat shows the chat list at phone width, so the chat's row
 * is tapped then.
 */
@RunWith(AndroidJUnit4::class)
class MessagesLayerBackJsTest {

    private val page = PageFixture(width = 1080, height = 2000, domStorage = true)

    private fun load(html: String, returnChat: String? = null, returnLink: String? = null) {
        page.load("https://www.facebook.com/messages/e2ee/t/1/", html)
        eval("window.LayerBridge = { onUrlChanged: function () {} }; sessionStorage.clear(); 1")
        returnChat?.let { eval("sessionStorage.setItem('astryxReturnChat', '$it'); 1") }
        returnLink?.let { eval("sessionStorage.setItem('astryxReturnLink', '$it'); 1") }
        page.run(R.raw.messages_layer_nav)
    }

    private fun eval(js: String) = page.eval(js)

    private fun back() = eval("window.__astryxLayerBack()")
    private fun pressed() = eval("window.__pressed || ''")
    private fun returnChat() = eval("sessionStorage.getItem('astryxReturnChat')")
    private fun pageGoes() = eval("window.dispatchEvent(new PageTransitionEvent('pagehide')); 1")

    private fun waitFor(ms: Long = 5_000, check: () -> Boolean) = page.waitFor(ms, check = check)

    @Test
    fun aChat_namedByALink_goesBackToTheChatList() {
        load(page(NAME_LINK, BOX_SHOWN))
        assertEquals("\"chat\"", back())
        assertEquals("\"back\"", pressed())
    }

    @Test
    fun anEncryptedChat_namedByAHeading_goesBackToTheChatList() {
        load(page(NAME_HEADING, BOX_SHOWN))
        assertEquals("\"chat\"", back())
        assertEquals("\"back\"", pressed())
    }

    @Test
    fun theChatList_beside_aHeading_leavesTheLayer() {
        // The list's own icon beside its heading, and the chat's box laid out with no width.
        load(page(NAME_HEADING, BOX_HIDDEN))
        assertEquals("\"list\"", back())
        assertEquals("\"\"", pressed())
    }

    @Test
    fun backAtAChatLeftForAPost_opensTheChat_notTheList() {
        // Loaded anew at the chat it left: the chat list shows, the chat's row on it.
        load(page(ROW + NAME_HEADING, BOX_HIDDEN), returnChat = CHAT)
        assertTrue("the chat's row was never tapped", waitFor { pressed() == "\"row\"" })
        assertEquals("the chat to go back to outlived its use", "null", returnChat())
    }

    @Test
    fun backAtAChatLeftForAPost_showsThePostAgain() {
        // Opened anew, the chat shows its latest messages: the post, far up, is scrolled to.
        load(CHAT_FAR_UP_POST, returnChat = CHAT, returnLink = POST)
        assertTrue("the shared post was never scrolled into view", waitFor {
            eval("(function () { var r = document.getElementById('post').getBoundingClientRect();" +
                " return r.top >= 0 && r.bottom <= window.innerHeight; })()") == "true"
        })
        assertEquals("the link to show outlived its use", "null", eval("sessionStorage.getItem('astryxReturnLink')"))
    }

    @Test
    fun aLinkTappedInTheChat_isKeptForTheWayBack_whenThePageGoes() {
        load(page(NAME_HEADING + "<a id='post' href='$POST' onclick='return false'>post</a>", BOX_SHOWN))
        eval("document.getElementById('post').click(); 1")
        // A link that left nothing (a reel, shown in the main view) isn't kept.
        assertEquals("null", eval("sessionStorage.getItem('astryxReturnLink')"))
        pageGoes()
        assertEquals("\"$POST\"", eval("sessionStorage.getItem('astryxReturnLink')"))
    }

    @Test
    fun theChatOnScreen_isKeptWhenThePageGoes() {
        load(page(NAME_HEADING, BOX_SHOWN))
        pageGoes()
        assertEquals("\"$CHAT\"", returnChat())
    }

    @Test
    fun theChatList_isNotKeptWhenThePageGoes() {
        // Only a chat that was on screen comes back: the user had gone back to the list.
        load(page(ROW + NAME_HEADING, BOX_HIDDEN))
        pageGoes()
        assertEquals("null", returnChat())
        assertEquals("\"\"", pressed())
    }

    @Test
    fun anotherChat_isNotOpenedForTheOneLeft() {
        load(page(ROW + NAME_HEADING, BOX_HIDDEN), returnChat = "/messages/e2ee/t/2/")
        Thread.sleep(1500)
        assertEquals("\"\"", pressed())
    }

    private companion object {
        const val CHAT = "/messages/e2ee/t/1/"
        const val POST = "https://www.facebook.com/share/p/1/"
        // The chat list (its row), and the chat its row opens: a message box and, far up the
        // conversation, the shared post (the page starts scrolled to the bottom, like a chat).
        const val CHAT_FAR_UP_POST =
            "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'></head>" +
                "<body style='margin:0;height:6000px'>" +
                "<a href='$CHAT' onclick=\"document.getElementById('box').style.width = '135px'; return false\" " +
                "style='position:absolute;left:24px;top:5800px;width:284px;height:72px'>Name</a>" +
                "<a id='post' href='$POST' style='position:absolute;left:24px;top:200px;width:200px;height:100px'>post</a>" +
                "<div id='box' role='textbox' contenteditable='true' " +
                "style='position:absolute;left:150px;top:5900px;width:0;height:23px'></div>" +
                "<script>window.scrollTo(0, 6000);</script></body></html>"
        // The chat's row on the chat list (the list keeps the chat's address).
        const val ROW = "<a href='$CHAT' onclick=\"window.__pressed = 'row'; return false\" " +
            "style='position:absolute;left:24px;top:165px;width:284px;height:72px'>Name</a>"
        const val BUTTON =
            "<div role='button' onclick=\"window.__pressed = 'back'\" " +
                "style='position:absolute;left:32px;top:84px;width:36px;height:36px'><svg width='20' height='20'></svg></div>"
        const val NAME_LINK = "<a href='/me/' style='position:absolute;left:80px;top:90px;width:120px;height:24px'>Name</a>"
        const val NAME_HEADING = "<h3 style='position:absolute;left:80px;top:90px;width:120px;height:24px;margin:0'>Name</h3>"
        const val BOX_SHOWN =
            "<div role='textbox' contenteditable='true' style='position:absolute;left:150px;top:700px;width:135px;height:23px'></div>"
        const val BOX_HIDDEN =
            "<div role='textbox' contenteditable='true' style='position:absolute;left:150px;top:700px;width:0;height:23px'></div>"

        fun page(name: String, box: String) =
            "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'></head>" +
                "<body style='margin:0'>$BUTTON$name$box</body></html>"
    }
}
