package com.astryx.book.ui.screens
import android.webkit.WebView
import androidx.annotation.RawRes
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// evaluateJavascript returns strings JSON-quoted.
private fun String.unquoted() = removeSurrounding("\"")

/**
 * Which media the download button of download_content.js saves, and the copy button of
 * copy_to_clipboard.js copies, against a real WebView (same harness approach as ReelAdsJsTest).
 * fetch is replaced by a stub that records the address, so nothing is downloaded. The fixtures mimic m.facebook.com's players: a <video> with a blob:
 * source inside data-mcomponent="MVideo", which carries the whole MP4 in data-video-url.
 */
@RunWith(AndroidJUnit4::class)
class MediaDownloadJsTest {

    private class Harness {
        private val page = PageFixture(width = 1080, height = 2000)

        fun load(url: String, body: String, @RawRes script: Int = R.raw.download_content) {
            page.load(url, "<html><head></head><body style='margin:0'>$body</body></html>")
            eval(
                "window.__fetched = []; window.fetch = function (u) { window.__fetched.push(String(u));" +
                    " return Promise.resolve(new Response(new Blob(['x'], { type: 'video/mp4' }))); };" +
                    // The app's listener: an address handed to it counts as the one downloaded.
                    "window.__handed = []; window.AstryxDownloads = { postMessage: function (m) {" +
                    " var d = JSON.parse(m); if (d.url) { window.__handed.push(d.url); window.__fetched.push(d.url); } } };" +
                    // The app's clipboard listener: what reached it, as its type.
                    "window.__copied = []; window.AstryxClipboard = { postMessage: function (m) {" +
                    " window.__copied.push(JSON.parse(m).mimeType); } };" +
                    "window.isFeed = function () { return false; };"
            )
            page.run(R.raw.media_viewer)
            page.run(script)
        }

        fun eval(js: String) = page.eval(js)

        /** Presses the download button; the address fetched. */
        fun download(): String = press("materialbook-global-downloader")

        /** Presses the copy button; the address fetched. */
        fun copy(): String = press("materialbook-clipboard-copier")

        private fun press(button: String): String {
            eval("document.getElementById('$button').click()")
            Thread.sleep(200)
            return eval("window.__fetched.join(' ')").unquoted()
        }

        fun shows(button: String) = page.waitFor(3_000) {
            eval("document.getElementById('$button').classList.contains('visible')") == "true"
        }
    }

    // A player as tall as [heightVh]% of the screen, with a playing blob: video.
    private fun player(id: String, heightVh: Int) =
        "<div data-mcomponent='MVideo' data-video-url='https://video.example/$id.mp4' style='height:${heightVh}vh'>" +
            "<video id='$id' style='width:100%;height:100%' src='blob:https://m.facebook.com/$id'></video></div>"

    // An image on Facebook's servers, [size] px square.
    private fun image(name: String, size: Int) =
        "<img src='https://scontent.fbcdn.net/$name.jpg' style='display:block;width:${size}px;height:${size}px'>"

    // A still image, fully on screen.
    private val still = image("still", 300)

    @Test
    fun aReelTallerThanTheScreen_downloadsItsVideo_notAStill() {
        val h = Harness()
        // The reel on screen is taller than the window; the next one waits below it.
        h.load("https://m.facebook.com/reel/1/", still + player("current", 120) + player("next", 120))
        h.eval("window.scrollTo(0, 300)")
        assertEquals("https://video.example/current.mp4", h.download())
    }

    @Test
    fun theReelMostlyOnScreen_isTheOneDownloaded_notTheFirstInThePage() {
        val h = Harness()
        h.load("https://m.facebook.com/reel/1/", player("previous", 100) + player("current", 100))
        // Scrolled to the second reel: the first is above the screen.
        h.eval("window.scrollTo(0, document.getElementById('current').getBoundingClientRect().top + window.scrollY)")
        assertEquals("https://video.example/current.mp4", h.download())
    }

    @Test
    fun anHttpsAddress_isHandedToTheApp_notReadByThePage() {
        // Read by the page, the file crossed whole as one base64 string: a long video ran the app
        // out of memory. The app fetches it itself (DownloadBridge.downloadUrl).
        val h = Harness()
        h.load("https://m.facebook.com/reel/1/", player("current", 100))
        h.eval("window.__pageFetches = 0; var f = window.fetch;" +
            " window.fetch = function (u) { window.__pageFetches++; return f(u); }; 1")
        assertEquals("https://video.example/current.mp4", h.download())
        assertEquals("the page fetched the file itself", "0", h.eval("window.__pageFetches"))
        assertEquals("https://video.example/current.mp4", h.eval("window.__handed.join(' ')").unquoted())
    }

    @Test
    fun aPhotoOnScreen_isDownloadedWhenThereIsNoVideo() {
        val h = Harness()
        h.load("https://m.facebook.com/photo.php?fbid=1", still)
        assertEquals("https://scontent.fbcdn.net/still.jpg", h.download())
    }

    @Test
    fun aPhotoOnScreen_isCopied() {
        val h = Harness()
        h.load("https://m.facebook.com/photo.php?fbid=1", image("small", 200) + image("large", 400), R.raw.copy_to_clipboard)
        assertTrue("no copy button on a photo", h.shows("materialbook-clipboard-copier"))
        // The first large one on screen; the app's clipboard gets its bytes.
        assertEquals("https://scontent.fbcdn.net/small.jpg", h.copy())
        assertEquals("1", h.eval("window.__copied.length"))
    }

    // Images too small for the first look (150 px) are still found, the largest first.
    @Test
    fun smallerImages_areFound_theLargestFirst() {
        val body = image("b", 120) + image("c", 140)
        val copy = Harness()
        copy.load("https://m.facebook.com/stories/1/", body, R.raw.copy_to_clipboard)
        assertEquals("https://scontent.fbcdn.net/c.jpg", copy.copy())
        val download = Harness()
        download.load("https://m.facebook.com/stories/1/", body)
        assertEquals("https://scontent.fbcdn.net/c.jpg", download.download())
    }

    // A story drawn as a background image, with no <img> at all.
    @Test
    fun aBackgroundImageOnFacebooksServers_isTheLastResort() {
        val body = "<div style='width:300px;height:300px;background-image:url(\"https://scontent.fbcdn.net/bg.jpg\")'></div>"
        val copy = Harness()
        copy.load("https://m.facebook.com/stories/1/", body, R.raw.copy_to_clipboard)
        assertEquals("https://scontent.fbcdn.net/bg.jpg", copy.copy())
        val download = Harness()
        download.load("https://m.facebook.com/stories/1/", body)
        assertEquals("https://scontent.fbcdn.net/bg.jpg", download.download())
    }

    @Test
    fun theButtons_showOnAHighlight_andNotOnAPagesPhotosTab() {
        // A story highlight's viewer, as the desktop site marks it; its picture is small.
        val highlight = "<div class='x1ey2m1c x9f619 xds687c x17qophe x10l6tqk x13vifvy' role='presentation'>" +
            image("h", 120) + "</div>"
        for ((script, button) in BUTTONS) {
            val shown = Harness()
            shown.load("https://m.facebook.com/somepage/", highlight, script)
            assertTrue("$button isn't shown on a highlight", shown.shows(button))
            val tab = Harness()
            tab.load("https://m.facebook.com/somepage/photos/", "<p>Photos</p>", script)
            Thread.sleep(500)
            assertEquals("$button shows on a Page's Photos tab", "false",
                tab.eval("document.getElementById('$button').classList.contains('visible')"))
        }
    }

    private companion object {
        val BUTTONS = listOf(
            R.raw.download_content to "materialbook-global-downloader",
            R.raw.copy_to_clipboard to "materialbook-clipboard-copier",
        )
    }
}
