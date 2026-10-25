package com.eepiemi.materialbook.ui.screens

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eepiemi.materialbook.PageFixture
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for text_wrap.js (Wrap text when zoomed) against a real WebView, same harness
 * approach as PipHandoffJsTest. A test WebView can't pinch, so the visual viewport's
 * scale, size and offset are overridden and the script's update() is called directly.
 * Lining the text up with the screen after a pinch needs a real zoom and isn't covered.
 *
 * The `server` blocks copy how m.facebook.com lays text out: `white-space: pre` with the
 * line breaks already in the text, inside boxes with fixed heights.
 */
@RunWith(AndroidJUnit4::class)
class TextWrapJsTest {

    private class Harness {
        private val page = PageFixture()

        fun load() {
            val long = "A long line of post text that is well over the minimum length. ".repeat(3)
            val hebrew = "שורה ארוכה של טקסט בעברית שעוברת את האורך המינימלי. ".repeat(3)
            // 29 monospace characters, then a word that wouldn't have fit on the line in 300px:
            // a break the server made. The others are the author's (a blank line, a short line).
            val serverText = "abcd abcd abcd abcd abcd abcd\\nefghijk rest\\n\\nShort\\nNext line text"
            val serverStyle = "white-space:pre;font:16px monospace;width:300px"
            val html = """
                <html><body>
                  <div id="box" style="height:60px;width:320px">
                    <div id="server" style="$serverStyle"></div>
                  </div>
                  <div id="videoBox" style="height:60px;width:400px">
                    <div id="besideVideo" style="white-space:pre;width:400px">$long</div>
                    <video></video>
                  </div>
                  <div style="position:fixed;top:0;width:400px"><div id="inBar">$long</div></div>
                  <div id="hebrewBox" style="height:60px;width:400px">
                    <div id="hebrew" dir="rtl" style="white-space:pre">$hebrew</div>
                  </div>
                  <div id="post" style="width:400px">$long</div>
                  <div id="narrow" style="width:150px">$long</div>
                  <div id="short" style="width:400px">Like</div>
                  <div id="withVideo" style="width:400px">$long<video></video></div>
                  <div id="styled" style="width:400px;max-width:390px"><span>$long</span></div>
                  <div id="far" style="width:400px;margin-top:3000px">$long</div>
                </body></html>
            """.trimIndent()
            page.load("https://example.com/", html)
            eval("document.getElementById('server').textContent = '$serverText';")
            page.run(R.raw.text_wrap)
            eval(
                """
                window.__z = { scale: 1, width: 300, height: 600, offsetTop: 0, offsetLeft: 0, pageTop: 0 };
                Object.keys(window.__z).forEach(function(k) {
                  Object.defineProperty(visualViewport, k, { configurable: true, get: function() { return window.__z[k]; } });
                });
                """.trimIndent()
            )
        }

        fun zoom(scale: Double, width: Int) {
            eval("window.__z.scale = $scale; window.__z.width = $width; window.__astryxTextWrap.update(false);")
        }

        fun maxWidth(id: String) = style(id, "maxWidth")

        fun style(id: String, property: String) =
            eval("document.getElementById('$id').style.$property").removeSurrounding("\"")

        fun text(id: String) = eval("document.getElementById('$id').textContent")

        fun isWrapped(id: String) =
            eval("document.getElementById('$id').hasAttribute('data-astryx-wrap')") == "true"

        fun eval(js: String) = page.eval(js)
    }

    @Test
    fun zoomedIn_wrapsWideTextToTheVisibleWidth() {
        val h = Harness()
        h.load()

        h.zoom(scale = 2.0, width = 200)

        // The visible width less an 8px gutter on each side.
        assertEquals("184px", h.maxWidth("post"))
        assertTrue(h.isWrapped("post"))
    }

    @Test
    fun zoomedIn_leavesNarrowShortVideoAndFarAwayBlocksAlone() {
        val h = Harness()
        h.load()

        h.zoom(scale = 2.0, width = 200)

        for (id in listOf("narrow", "short", "withVideo", "far")) {
            assertEquals("$id must keep its width", "", h.maxWidth(id))
            assertTrue("$id must not be marked", !h.isWrapped(id))
        }
    }

    @Test
    fun zoomingFurther_narrowsEveryWrappedBlock() {
        val h = Harness()
        h.load()
        h.zoom(scale = 2.0, width = 200)

        h.zoom(scale = 4.0, width = 100)

        assertEquals("84px", h.maxWidth("post"))
        assertEquals("84px", h.maxWidth("styled"))
    }

    @Test
    fun zoomingOut_restoresTheBlocksOwnStyles() {
        val h = Harness()
        h.load()
        h.zoom(scale = 2.0, width = 200)

        h.zoom(scale = 1.0, width = 300)

        assertEquals("", h.maxWidth("post"))
        assertEquals("390px", h.maxWidth("styled"))
        assertEquals("0", h.eval("document.querySelectorAll('[data-astryx-wrap]').length"))
    }

    @Test
    fun notZoomed_changesNothing() {
        val h = Harness()
        h.load()

        h.zoom(scale = 1.0, width = 300)

        assertEquals("", h.maxWidth("post"))
        assertEquals("390px", h.maxWidth("styled"))
    }

    @Test
    fun serverLaidOutText_wrapsJoinsTheServersLineBreaksAndGrowsItsBox() {
        val h = Harness()
        h.load()
        val textHeightBefore = h.eval("document.getElementById('server').offsetHeight").toDouble()

        h.zoom(scale = 2.0, width = 200)

        assertTrue(h.isWrapped("server"))
        assertEquals("pre-wrap", h.style("server", "whiteSpace"))
        assertEquals(
            "\"abcd abcd abcd abcd abcd abcd efghijk rest\\n\\nShort\\nNext line text\"",
            h.text("server")
        )
        val boxHeight = h.style("box", "height").removeSuffix("px").toDouble()
        val textHeight = h.eval("document.getElementById('server').offsetHeight").toDouble()
        // The fixed-height box grows by as much as its text did.
        assertTrue("box must grow with its text ($boxHeight)", boxHeight > 60)
        assertEquals(60 + textHeight - textHeightBefore, boxHeight, 2.0)
    }

    @Test
    fun zoomingOut_restoresServerTextAndBoxes() {
        val h = Harness()
        h.load()
        h.zoom(scale = 2.0, width = 200)

        h.zoom(scale = 1.0, width = 300)

        assertEquals(
            "\"abcd abcd abcd abcd abcd abcd\\nefghijk rest\\n\\nShort\\nNext line text\"",
            h.text("server")
        )
        assertEquals("pre", h.style("server", "whiteSpace"))
        assertEquals("60px", h.style("box", "height"))
        assertEquals("", h.style("hebrew", "marginLeft"))
    }

    @Test
    fun zoomedIn_leavesTextInABoxWithAVideoOrInAFixedBarAlone() {
        val h = Harness()
        h.load()

        h.zoom(scale = 2.0, width = 200)

        for (id in listOf("besideVideo", "inBar")) {
            assertEquals("$id must keep its width", "", h.maxWidth(id))
            assertTrue("$id must not be marked", !h.isWrapped(id))
        }
        assertEquals("60px", h.style("videoBox", "height"))
    }

    @Test
    fun zoomedIn_movesRightToLeftTextInALeftToRightBoxToTheRight() {
        val h = Harness()
        h.load()

        h.zoom(scale = 2.0, width = 200)

        assertTrue(h.isWrapped("hebrew"))
        assertEquals("auto", h.style("hebrew", "marginLeft"))
        val right = h.eval("document.getElementById('hebrew').getBoundingClientRect().right").toDouble()
        val boxRight = h.eval("document.getElementById('hebrewBox').getBoundingClientRect().right").toDouble()
        assertEquals(boxRight, right, 1.0)
    }

    @Test
    fun runningTheScriptTwice_installsItOnce() {
        val h = Harness()
        h.load()
        h.eval("window.__first = window.__astryxTextWrap;")

        h.eval(
            InstrumentationRegistry.getInstrumentation().targetContext.resources
                .openRawResource(R.raw.text_wrap).bufferedReader().use { it.readText() }
        )

        assertEquals("true", h.eval("window.__first === window.__astryxTextWrap"))
    }
}
