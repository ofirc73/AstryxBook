package com.astryx.book.ui.screens
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * memory_photos.js: a memory card's photo opens alone (photo.php?fbid=…); the script asks the app
 * for the photo's post and opens it at that photo. The app's listener is a stand-in that records
 * what the page sends and hands back answers; the navigation is caught (__astryxMemoryGo).
 */
@RunWith(AndroidJUnit4::class)
class MemoryPhotosJsTest {

    private class Harness {
        private val page = PageFixture(width = 1080, height = 2000, domStorage = true)

        fun load(body: String, setup: String = "") {
            page.load(
                "https://m.facebook.com/",
                "<html><head><meta name='viewport' content='width=device-width'></head>" +
                    "<body style='margin:0'>$body</body></html>"
            )
            eval(APP + setup)
            page.run(R.raw.memory_photos)
        }

        fun eval(js: String) = page.eval(js)

        fun waitFor(js: String, ms: Long = 5000) = page.waitFor(ms) { eval(js) == "true" }

        // What Facebook does when a tile is tapped: the tap, then the photo's address.
        fun tapAndOpen(tile: String, address: String) {
            eval("document.getElementById('$tile').click(); history.replaceState(null, '', '$address'); 1")
        }

        fun sent() = eval("JSON.stringify(window.__sent)")
    }

    @Test
    fun aMemoryPhoto_asksForItsPost() {
        val h = Harness()
        h.load(card("m", 3))
        h.tapAndOpen("m0", "/photo.php?fbid=10158336853136398")
        assertEquals("1", h.eval("window.__sent.length"))
        assertEquals("\"photoPost\"", h.eval("JSON.parse(window.__sent[0]).call"))
        assertEquals("\"10158336853136398\"", h.eval("JSON.parse(window.__sent[0]).fbid"))
    }

    @Test
    fun aMemoryPhotoOpenedAsANewPage_asksFromThatPage() {
        // Soon after the feed loads, Facebook may open the tapped photo with a full load: the
        // feed's page (and the answer to it) is gone, so the photo's own page asks.
        val h = Harness()
        h.load(
            card("x", 1),
            "sessionStorage.setItem('astryxMemoryTap', String(Date.now() - 1500));" +
                " history.replaceState(null, '', '/photo.php?fbid=77');"
        )
        assertEquals("1", h.eval("window.__sent.length"))
        assertEquals("\"77\"", h.eval("JSON.parse(window.__sent[0]).fbid"))
        assertEquals("null", h.eval("sessionStorage.getItem('astryxMemoryTap')"))
    }

    @Test
    fun anOldTap_doesntMakeALaterPhotoPageAsk() {
        val h = Harness()
        h.load(
            card("x", 1),
            "sessionStorage.setItem('astryxMemoryTap', String(Date.now() - 60000));" +
                " history.replaceState(null, '', '/photo.php?fbid=77');"
        )
        assertEquals("0", h.eval("window.__sent.length"))
    }

    @Test
    fun aSinglePhotoPost_orAPhotoWithItsAlbum_isLeftAlone() {
        val h = Harness()
        h.load(card("s", 1) + card("m", 3))
        h.tapAndOpen("s0", "/photo.php?fbid=1")
        h.tapAndOpen("m1", "/photo.php?fbid=2&set=a.3")
        assertEquals("\"[]\"", h.sent())
    }

    @Test
    fun theAnswer_opensThePostInThePhotosPlace() {
        val h = Harness()
        h.load(card("m", 3))
        h.tapAndOpen("m1", "/photo.php?fbid=42")
        h.eval("window.__answer({fbid: '42', url: 'https://m.facebook.com/1/posts/900/', index: 1}); 1")
        assertEquals("\"https://m.facebook.com/1/posts/900/\"", h.eval("window.__went"))
        assertEquals("1", h.eval("JSON.parse(sessionStorage.getItem('astryxPhotoPost')).index"))
    }

    @Test
    fun aLateAnswer_afterThePhotoWasClosed_doesNothing() {
        val h = Harness()
        h.load(card("m", 3))
        h.tapAndOpen("m1", "/photo.php?fbid=42")
        h.eval("history.replaceState(null, '', '/'); window.__answer({fbid: '42', url: 'https://m.facebook.com/1/posts/900/', index: 1}); 1")
        assertEquals("null", h.eval("window.__went"))
    }

    @Test
    fun thePostsPage_tapsThePhotoTheMemoryOpened() {
        val h = Harness()
        // The post (here its card, after a story tray outside any card) as the feed shows it.
        h.load(
            "<div data-action-id='9'><img style='width:100px;height:180px'></div>" + card("p", 5),
            "sessionStorage.setItem('astryxPhotoPost', JSON.stringify({index: 2, at: Date.now()}));"
        )
        assertTrue(h.waitFor("window.__clicked === 'p2'"))
        assertEquals("null", h.eval("sessionStorage.getItem('astryxPhotoPost')"))
    }

    @Test
    fun anOldRequest_isNotActedOn() {
        val h = Harness()
        h.load(card("p", 5), "sessionStorage.setItem('astryxPhotoPost', JSON.stringify({index: 2, at: Date.now() - 600000}));")
        Thread.sleep(800)
        assertEquals("\"undefined\"", h.eval("typeof window.__clicked"))
    }

    private companion object {
        // The app's AstryxPage listener, recording messages; __answer replies as the app would.
        const val APP =
            "window.__sent = []; window.__went = null; window.__astryxMemoryFindMs = 2000; window.__astryxMemorySettleMs = 200;" +
                " window.__astryxMemoryGo = function (url) { window.__went = url; };" +
                " window.AstryxPage = { postMessage: function (m) { window.__sent.push(m); }," +
                " addEventListener: function (type, f) { window.__answer = function (a) { f({ data: JSON.stringify(a) }); }; } };" +
                " document.addEventListener('click', function (e) { var t = e.target.closest('[data-action-id]');" +
                " if (t && t.id) window.__clicked = t.id; });"

        // A feed card with [count] photo tiles (Facebook's action containers holding an image) and
        // its reactions row: an action as big as a tile, holding small icons.
        fun card(id: String, count: Int) =
            "<div data-tracking-duration-id='$id'>" +
                (0 until count).joinToString("") {
                    "<div data-action-id='$id$it' id='$id$it' style='display:inline-block;width:120px;height:120px'>" +
                        "<img style='width:120px;height:120px'></div>"
                } +
                "<div data-action-id='${id}r' id='${id}r' style='width:380px;height:70px'>" +
                "<img style='width:18px;height:18px'><img style='width:18px;height:18px'></div>" +
                "</div>"
    }
}
