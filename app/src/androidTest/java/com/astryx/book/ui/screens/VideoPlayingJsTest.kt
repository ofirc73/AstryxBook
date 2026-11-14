package com.astryx.book.ui.screens
import android.webkit.JavascriptInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

/**
 * Tests for video_playing.js (*Keep screen on: while a video plays*): the page tells the app
 * whether a video on it plays and shows, muted or not, once per change. Media is faked the way
 * PipHandoffJsTest fakes it (paused, readyState). Live: keepScreenOnWhileVideo_keepsItOnWhileAReelPlays.
 */
@RunWith(AndroidJUnit4::class)
class VideoPlayingJsTest {

    // What the page told the app (ScreenBridge, as on WebViews without AstryxPage).
    private val told: MutableList<Boolean> = Collections.synchronizedList(mutableListOf())

    inner class FakeScreenBridge {
        @JavascriptInterface
        fun setVideoPlaying(playing: Boolean) {
            told += playing
        }
    }

    private val page = PageFixture(width = 1080, height = 2000, interfaces = mapOf("ScreenBridge" to FakeScreenBridge()))

    // A shown, muted video, paused; [play] and [pause] change it and fire the event the page gets.
    private fun load() {
        page.load(
            "https://m.facebook.com/reel/1/",
            "<html><body><video id='v' muted style='width:300px;height:500px'></video></body></html>"
        )
        page.eval(
            """
            var v = document.getElementById('v');
            v.__paused = true;
            Object.defineProperty(v, 'paused', { get: function () { return this.__paused; } });
            Object.defineProperty(v, 'readyState', { get: function () { return 4; } });
            window.__set = function (paused) { v.__paused = paused; v.dispatchEvent(new Event(paused ? 'pause' : 'playing')); };
            1
            """.trimIndent()
        )
        page.run(R.raw.video_playing)
    }

    @Test
    fun aShownVideoPlaying_isTold_andItsPause_once_each() {
        load()
        page.eval("window.__set(false); window.__set(false); 1")
        page.eval("window.__set(true); 1")
        assertTrue(told.toString(), page.waitFor { told.size >= 3 })
        assertEquals("not playing at first, then playing (a muted video too), then paused", listOf(false, true, false), told.toList())
    }

    @Test
    fun aVideoRemovedWhilePlaying_isToldAsStopped_byThePoll() {
        // No event comes for a removed video: the 2 s poll sees it.
        load()
        page.eval("window.__set(false); 1")
        assertTrue(page.waitFor { told.lastOrNull() == true })
        page.eval("document.getElementById('v').remove(); 1")
        assertTrue(told.toString(), page.waitFor(4_000) { told.lastOrNull() == false })
    }
}
