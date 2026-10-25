package com.eepiemi.materialbook.ui.screens

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eepiemi.materialbook.PageFixture
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

// evaluateJavascript returns strings JSON-quoted.
private fun String.unquoted() = removeSurrounding("\"")

/**
 * Which media the download button of download_content.js saves, against a real WebView (same
 * harness approach as ReelAdsJsTest). fetch is replaced by a stub that records the address, so
 * nothing is downloaded. The fixtures mimic m.facebook.com's players: a <video> with a blob:
 * source inside data-mcomponent="MVideo", which carries the whole MP4 in data-video-url.
 */
@RunWith(AndroidJUnit4::class)
class MediaDownloadJsTest {

    private class Harness {
        private val page = PageFixture(width = 1080, height = 2000)

        fun load(url: String, body: String) {
            page.load(url, "<html><head></head><body style='margin:0'>$body</body></html>")
            eval(
                "window.__fetched = []; window.fetch = function (u) { window.__fetched.push(String(u));" +
                    " return Promise.resolve(new Response(new Blob(['x'], { type: 'video/mp4' }))); };" +
                    "window.AstryxDownloads = { postMessage: function () {} };" +
                    "window.isFeed = function () { return false; };"
            )
            page.run(R.raw.download_content)
        }

        fun eval(js: String) = page.eval(js)

        /** Presses the download button; the address fetched. */
        fun download(): String {
            eval("document.getElementById('materialbook-global-downloader').click()")
            Thread.sleep(200)
            return eval("window.__fetched.join(' ')").unquoted()
        }
    }

    // A player as tall as [heightVh]% of the screen, with a playing blob: video.
    private fun player(id: String, heightVh: Int) =
        "<div data-mcomponent='MVideo' data-video-url='https://video.example/$id.mp4' style='height:${heightVh}vh'>" +
            "<video id='$id' style='width:100%;height:100%' src='blob:https://m.facebook.com/$id'></video></div>"

    // A still image, fully on screen.
    private val still = "<img src='https://scontent.fbcdn.net/still.jpg' style='display:block;width:300px;height:300px'>"

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
    fun aPhotoOnScreen_isDownloadedWhenThereIsNoVideo() {
        val h = Harness()
        h.load("https://m.facebook.com/photo.php?fbid=1", still)
        assertEquals("https://scontent.fbcdn.net/still.jpg", h.download())
    }
}
