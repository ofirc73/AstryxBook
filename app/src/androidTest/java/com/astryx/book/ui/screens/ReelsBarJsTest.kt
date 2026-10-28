package com.astryx.book.ui.screens
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Reels bar: Facebook's blue "Open app" button (hidden by scripts.js, which always runs; it
 * used to be hidden by download_content.js only, with Download on) and the copy button
 * (copy_to_clipboard.js), which a Like on a reel brought up although a reel has nothing to copy
 * (in the Reels tab, /reel/<id>/, and on a reel opened from the feed, /<page>/videos/<id>/).
 */
@RunWith(AndroidJUnit4::class)
class ReelsBarJsTest {

    private class Harness {
        private val page = PageFixture(width = 1080, height = 2000)

        fun load(url: String, body: String) {
            page.load(
                url,
                "<html><head><meta name='viewport' content='width=device-width'></head>" +
                    "<body style='margin:0'>$body</body></html>"
            )
        }

        fun run(script: Int) {
            page.run(script)
        }

        fun eval(js: String) = page.eval(js)

        fun waitFor(js: String, ms: Long = 5000) = page.waitFor(ms) { eval(js) == "true" }
    }

    @Test
    fun openApp_isHidden_withoutTheDownloadScript() {
        val h = Harness()
        h.load("https://m.facebook.com/reel/123", REELS_BAR)
        h.run(R.raw.scripts)
        // Facebook adds it once the page has drawn.
        h.eval(ADD_OPEN_APP)
        assertTrue(h.waitFor("getComputedStyle(document.getElementById('open-app')).display === 'none'"))
        assertEquals("\"block\"", h.eval("getComputedStyle(document.getElementById('search')).display"))
    }

    @Test
    fun theCopyButton_staysHiddenOnReels() {
        // A large Facebook image on screen, the way a Like's animation or a reel's poster puts one.
        val h = Harness()
        h.load("https://m.facebook.com/reel/123", "<div class='story_viewer'>" + BIG_IMAGE + "</div>")
        h.run(R.raw.copy_to_clipboard)
        Thread.sleep(800)
        assertEquals("false", h.eval("document.getElementById('materialbook-clipboard-copier').classList.contains('visible')"))
    }

    @Test
    fun theCopyButton_staysHiddenOnAReelOpenedFromTheFeed() {
        // A reel from the feed opens at /<page>/videos/<id>/; its poster is the large image.
        val h = Harness()
        h.load(
            "https://m.facebook.com/somepage/videos/some-title/1089723017184273/",
            "<div data-mcomponent='MVideo'>" + BIG_IMAGE + "<video></video></div>"
        )
        h.run(R.raw.copy_to_clipboard)
        Thread.sleep(800)
        assertEquals("false", h.eval("document.getElementById('materialbook-clipboard-copier').classList.contains('visible')"))
    }

    @Test
    fun theCopyButton_stillShowsOnAPhoto() {
        val h = Harness()
        h.load("https://m.facebook.com/photo.php", BIG_IMAGE)
        h.run(R.raw.copy_to_clipboard)
        assertTrue(h.waitFor("document.getElementById('materialbook-clipboard-copier').classList.contains('visible')"))
    }

    private companion object {
        const val REELS_BAR =
            "<div id='bar'><div role='button' id='search'><span>S</span></div></div>"

        // Facebook's Open app button: its icon (U+F196C) and label in a div.fl.ac.
        const val ADD_OPEN_APP =
            "(function () { var b = document.createElement('div'); b.setAttribute('role', 'button'); b.id = 'open-app';" +
                " b.innerHTML = \"<div class='fl ac'><span></span><span>Open app</span></div>\";" +
                " b.querySelector('span').textContent = String.fromCodePoint(0xF196C);" +
                " document.getElementById('bar').appendChild(b); })()"

        const val BIG_IMAGE =
            "<img src='https://scontent.fbcdn.net/a.jpg' width='300' height='300' style='display:block;width:300px;height:300px'>"
    }
}
