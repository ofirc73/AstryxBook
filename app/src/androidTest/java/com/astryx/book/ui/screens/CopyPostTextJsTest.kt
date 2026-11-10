package com.astryx.book.ui.screens
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import com.astryx.book.ui.screens.TextWrapJsTest.Companion.SERVER_STYLE
import com.astryx.book.ui.screens.TextWrapJsTest.Companion.SERVER_TEXT
import com.astryx.book.ui.screens.TextWrapJsTest.Companion.SERVER_TEXT_JOINED
import com.eepiemi.materialbook.R
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * copy_post_text.js: a long press on a post's body copies its text (AstryxPage call `copyText`,
 * caught here). The page copies m.facebook.com's feed as read on the emulator: posts as rows of a
 * data-type="vscroller" list, each with its name above a body as wide as the post, then a photo
 * and a link card under it. A long press is what Chromium sends for one: touchstart, contextmenu,
 * touchend.
 */
@RunWith(AndroidJUnit4::class)
class CopyPostTextJsTest {

    private val page = PageFixture(width = 1080, height = 2000)

    private fun open() {
        page.load("https://m.facebook.com/", PAGE)
        page.eval("document.getElementById('body').textContent = ${JSONObject.quote(SERVER_TEXT)}; 1")
        page.eval(
            "window.__astryxCopyExpandMs = 1500; window.__copied = [];" +
                " window.AstryxPage = { postMessage: function (m) { var c = JSON.parse(m);" +
                " if (c.call === 'copyText') window.__copied.push(c.text); } }; 1"
        )
        page.run(R.raw.server_lines)
        page.run(R.raw.copy_post_text)
    }

    private fun holdDown(id: String) = page.eval(
        """
        (function (el) {
          var box = el.getBoundingClientRect();
          var init = { bubbles: true, cancelable: true, clientX: box.left + box.width / 2, clientY: box.top + box.height / 2 };
          el.dispatchEvent(new Event('touchstart', init));
          el.dispatchEvent(new MouseEvent('contextmenu', init));
          el.dispatchEvent(new Event('touchend', init));
          return 1;
        })(document.getElementById('$id'))
        """.trimIndent()
    )

    private fun copied(): List<String> = JSONArray(page.eval("window.__copied")).let { list -> (0 until list.length()).map { list.getString(it) } }

    @Test
    fun aPostsBody_heldDown_isCopied_withTheServersLineBreaksJoined() {
        open()
        holdDown("body")
        assertTrue("nothing was copied", page.waitFor { copied().isNotEmpty() })
        assertEquals(listOf(SERVER_TEXT_JOINED), copied())
    }

    @Test
    fun theNameAboveIt_andALinkCardUnderItsPhoto_arentCopied() {
        open()
        holdDown("name")
        holdDown("card")
        holdDown("short")
        Thread.sleep(500)
        assertEquals(emptyList<String>(), copied())
    }

    @Test
    fun aPostCutShort_isOpenedFirst_thenCopiedWhole() {
        open()
        holdDown("cut")
        assertTrue("nothing was copied", page.waitFor { copied().isNotEmpty() })
        assertEquals(listOf("The start of a long post, and the rest of it once opened."), copied())
        assertEquals("the post wasn't opened in place", "1", page.eval("window.__opened"))
    }

    @Test
    fun aLongPressFacebookAnswersItself_copiesNothing() {
        open()
        // A comment's long press opens Facebook's own menu, and Facebook prevents the touch's end.
        page.eval("document.getElementById('body').addEventListener('touchend', function (e) { e.preventDefault(); }); 1")
        holdDown("body")
        Thread.sleep(500)
        assertEquals(emptyList<String>(), copied())
    }

    private companion object {
        // Cut short: a tap opens it in place (the server draws the whole text again, a moment later).
        const val OPEN_CUT =
            "<script>document.getElementById('cut').addEventListener('click', function () {" +
                " window.__opened = (window.__opened || 0) + 1; var cut = this; setTimeout(function () {" +
                " cut.innerHTML = '<span>The start of a long post, and the rest of it once opened.</span>'; }, 300); });</script>"
        val PAGE = """
            <html><body style="margin:0"><div data-type="vscroller" style="width:320px">
              <div style="width:320px">
                <div><img style="width:40px;height:40px">
                  <div id="name" data-mcomponent="TextArea" style="width:200px"><span>A Page With A Long Name</span></div></div>
                <div id="body" data-mcomponent="TextArea" style="$SERVER_STYLE"></div>
                <img style="width:320px;height:100px">
                <div id="card" data-mcomponent="TextArea" style="width:320px"><span>A link card's title, under the photo</span></div>
              </div>
              <div style="width:320px">
                <div id="cut" data-mcomponent="TextArea" data-action-id="1" style="width:300px"><span>The start of a long post</span><span>... See more</span></div>
              </div>
              <div style="width:320px">
                <div id="short" data-mcomponent="TextArea" style="width:200px"><span>A short line, not a body</span></div>
              </div>
            </div>$OPEN_CUT</body></html>
        """.trimIndent()
    }
}
