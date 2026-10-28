package com.astryx.book.ui.screens
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Settings gear scripts.js puts in a page's top bar (a Page, Comments...) makes the title
 * narrower. m.facebook.com cuts the title to fit the full width on its server (adding its own
 * "..."), so the narrower box cut it again mid-word ("Comments" became "Comr"). The fixture is
 * built like Facebook's bar: Back, the title (a fixed-width box holding the text spans) and the
 * bar's buttons, stacked with negative top margins.
 */
@RunWith(AndroidJUnit4::class)
class PageTitleJsTest {

    private class Harness {
        private val page = PageFixture(width = 1080, height = 2000)

        fun load(body: String) {
            page.load(
                "https://m.facebook.com/story.php",
                "<html><head><meta name='viewport' content='width=device-width'></head>" +
                    "<body style='margin:0'>$body</body></html>"
            )
            page.run(R.raw.scripts)
        }

        fun eval(js: String) = page.eval(js)

        fun waitFor(js: String, ms: Long = 5000) = page.waitFor(ms) { eval(js) == "true" }
    }

    @Test
    fun theTitle_endsWithItsOwnEllipsis_besideTheGear() {
        val h = Harness()
        h.load(PAGE_BAR)
        assertTrue("no gear", h.waitFor("!!document.getElementById('custom-settings-btn-page')", 6000))
        val text = "document.getElementById('title-text')"
        assertEquals("\"ellipsis\"", h.eval("getComputedStyle($text).textOverflow"))
        assertEquals("\"hidden\"", h.eval("getComputedStyle($text).overflowX"))
        // In the title's own font, so the ellipsis looks like the rest of it.
        assertEquals(
            h.eval("getComputedStyle(document.getElementById('title-span')).fontWeight"),
            h.eval("getComputedStyle($text).fontWeight")
        )
        // The title ends before the gear.
        assertTrue(h.eval(
            "document.getElementById('title').getBoundingClientRect().right <= " +
                "document.getElementById('custom-settings-btn-page').getBoundingClientRect().left + 1"
        ) == "true")
    }

    private companion object {
        // Back (its icon put in by script: a private-use glyph), the title "Comments" in a
        // 263 px box sized for the full width, and the bar's buttons.
        const val PAGE_BAR =
            "<div id='bar' style='display:flex;flex-direction:column;width:411px'>" +
                "<div style='height:45px;width:48px'><div role='button'><span id='back'></span></div></div>" +
                "<h1 id='title' style='margin-top:-45px;height:45px;width:315px;margin-left:48px;overflow:hidden;font-size:16px'>" +
                "<div style='margin-top:12px;height:21px;width:315px'>" +
                "<div id='title-text' style='white-space:pre'><span id='title-span' style='font-weight:700'>Comments</span></div>" +
                "</div></h1>" +
                "<div id='buttons' style='margin-top:-45px;height:45px;margin-left:363px;width:48px'>" +
                "<div role='button'>S</div></div>" +
                "</div>" +
                "<script>document.getElementById('back').textContent = String.fromCodePoint(0xF30DF);</script>"
    }
}
