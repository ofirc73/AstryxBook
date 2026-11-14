package com.astryx.book.ui.screens
import com.astryx.book.utils.pageScript
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.astryx.book.PageFixture
import com.eepiemi.materialbook.R
import org.json.JSONObject
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
            val html = """
                <html><body>
                  <div id="box" style="height:60px;width:320px">
                    <div id="server" style="$SERVER_STYLE"></div>
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
            eval("document.getElementById('server').textContent = ${JSONObject.quote(SERVER_TEXT)};")
            page.run(R.raw.server_lines)
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
            JSONObject.quote(SERVER_TEXT_JOINED),
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
            JSONObject.quote(SERVER_TEXT),
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
    fun theKeyboardOpening_isNotTakenForAPinch() {
        // Only the view's height changes: taken for a pinch, every block was fitted again and the
        // view panned sideways, away from the comment box just tapped.
        val h = Harness()
        h.load()
        h.zoom(scale = 2.0, width = 200)
        h.eval("window.__pans = 0; Element.prototype.scrollIntoView = function () { window.__pans++; };")

        h.eval("window.__z.height = 300; visualViewport.dispatchEvent(new Event('resize'));")
        Thread.sleep(600)

        assertEquals("0", h.eval("window.__pans"))
        assertEquals("184px", h.maxWidth("post"))
    }

    @Test
    fun textTakenOffThePage_isPutBack_whileZoomedIn() {
        // Rows of a feed scrolled past: each wrapped one was held, as it was, until the user zoomed
        // out. Put back and forgotten, one Facebook shows again is wrapped like any other.
        val h = Harness()
        h.load()
        h.zoom(scale = 2.0, width = 200)
        h.eval("window.__gone = [document.getElementById('post'), document.getElementById('box')]; window.__gone.forEach(function (el) { el.remove(); });")

        h.eval("window.__astryxTextWrap.update(false);")

        assertEquals("\"\"", h.eval("window.__gone[0].style.maxWidth"))
        assertEquals("\"60px\"", h.eval("window.__gone[1].style.height"))
        assertEquals(JSONObject.quote(SERVER_TEXT), h.eval("window.__gone[1].querySelector('#server').textContent"))
    }

    @Test
    fun textInTwoWrappedBlocks_joinedTwice_isStillPutBack() {
        // A block inside another wrapped block, its text judged once for each (two line limits):
        // recorded twice, its zoom-out threw half-way and left the blocks before it narrow. The
        // judging is faked here: each join turns the text's first line break into a space.
        val h = Harness()
        h.load()
        val inner = "first line of the inner text\nsecond line of it here\nthird line"
        h.eval(
            """
            var nest = document.createElement('div');
            nest.id = 'nest';
            nest.style.cssText = 'white-space:pre;width:400px';
            nest.appendChild(document.createTextNode('The outer block has some text of its own. '));
            var inner = document.createElement('div');
            inner.id = 'inner';
            inner.style.width = '360px';
            inner.textContent = ${JSONObject.quote(inner)};
            nest.appendChild(inner);
            document.body.insertBefore(nest, document.body.firstChild);
            window.__astryxServerLines.join = function (block) {
              var out = [], walker = document.createTreeWalker(block, NodeFilter.SHOW_TEXT), node;
              while ((node = walker.nextNode())) out.push({ node: node, text: node.nodeValue.replace('\n', ' ') });
              return out;
            };
            """.trimIndent()
        )
        h.zoom(scale = 2.0, width = 200)
        assertTrue(h.isWrapped("nest"))
        assertTrue(h.isWrapped("inner"))

        h.zoom(scale = 1.0, width = 300)

        assertEquals(JSONObject.quote(inner), h.text("inner"))
        assertEquals("", h.maxWidth("nest"))
        assertEquals("0", h.eval("document.querySelectorAll('[data-astryx-wrap]').length"))
    }

    @Test
    fun runningTheScriptTwice_installsItOnce() {
        val h = Harness()
        h.load()
        h.eval("window.__first = window.__astryxTextWrap;")

        h.eval(
            InstrumentationRegistry.getInstrumentation().targetContext.resources.pageScript(R.raw.text_wrap)
        )

        assertEquals("true", h.eval("window.__first === window.__astryxTextWrap"))
    }

    internal companion object {
        // A text block as m.facebook.com's server lays it out (server_lines.js): 29 monospace
        // characters, then a word that wouldn't have fit on the line in 300px: a break the server
        // made. The others are the author's (a blank line, a short line).
        const val SERVER_TEXT = "abcd abcd abcd abcd abcd abcd\nefghijk rest\n\nShort\nNext line text"
        const val SERVER_TEXT_JOINED = "abcd abcd abcd abcd abcd abcd efghijk rest\n\nShort\nNext line text"
        const val SERVER_STYLE = "white-space:pre;font:16px monospace;width:300px"
    }
}
