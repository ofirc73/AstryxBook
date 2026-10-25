package com.eepiemi.materialbook.ui.screens

import android.webkit.JavascriptInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eepiemi.materialbook.PageFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * PIP_VIDEO_GONE_WATCH_JS: after focus mode, tells the app (which closes the PiP window) once the marked PiP video has been off the page for a while, and only then.
 */
@RunWith(AndroidJUnit4::class)
class PipVideoGoneJsTest {

    private val gone = AtomicInteger()

    inner class FakePipBridge {
        @JavascriptInterface
        fun onPipVideoGone() {
            gone.incrementAndGet()
        }
    }

    private val page = PageFixture(interfaces = mapOf("PipBridge" to FakePipBridge()))

    private fun load(html: String) = page.load("https://example.com/", html)

    private fun eval(js: String) = page.eval(js)

    private fun startPip() {
        load(PAGE)
        eval("window.__astryxLastActiveVideo = document.getElementById('reel'); 1")
        eval(PIP_FOCUS_MODE_JS + PIP_VIDEO_GONE_WATCH_JS)
        assertEquals("true", eval("document.getElementById('reel').hasAttribute('data-astryx-pip-video')"))
    }

    @Test
    fun aVideoTakenOffThePage_isReportedOnce() {
        startPip()
        eval("document.getElementById('reel').remove(); 1")
        val until = System.currentTimeMillis() + 8000
        while (gone.get() == 0 && System.currentTimeMillis() < until) Thread.sleep(200)
        assertEquals(1, gone.get())
        Thread.sleep(1500)
        assertEquals("reported more than once", 1, gone.get())
    }

    @Test
    fun aVideoBackOnThePageSoon_isNotReported() {
        // Facebook re-wraps the player at times: the video leaves the page for a moment.
        startPip()
        eval("var v = document.getElementById('reel'); v.remove(); setTimeout(function() { document.body.appendChild(v); }, 100); 1")
        Thread.sleep(5000)
        assertEquals(0, gone.get())
    }

    @Test
    fun withNoVideoLeftOnThePage_itIsReportedWithinHalfASecondOrSo() {
        // Facebook went back to Home: the window is black until the app closes it.
        startPip()
        val removedAt = System.currentTimeMillis()
        eval("document.getElementById('reel').remove(); 1")
        while (gone.get() == 0 && System.currentTimeMillis() - removedAt < 5000) Thread.sleep(50)
        assertEquals(1, gone.get())
        val took = System.currentTimeMillis() - removedAt
        assertTrue("took $took ms", took < 1200)
    }

    @Test
    fun withAnotherVideoOnThePage_itWaitsLonger() {
        // Facebook may be swapping the player: the longer wait.
        startPip()
        eval("document.body.appendChild(document.createElement('video')); document.getElementById('reel').remove(); 1")
        Thread.sleep(1800)
        assertEquals(0, gone.get())
        val until = System.currentTimeMillis() + 5000
        while (gone.get() == 0 && System.currentTimeMillis() < until) Thread.sleep(100)
        assertEquals(1, gone.get())
    }

    @Test
    fun aVideoStillThere_isNotReported() {
        startPip()
        Thread.sleep(4500)
        assertEquals(0, gone.get())
    }

    @Test
    fun withNoVideoMarked_nothingIsReported() {
        load("<html><body><div id='caption'>No video</div></body></html>")
        eval("document.body.setAttribute('data-astryx-pip-active', ''); 1")
        eval(PIP_VIDEO_GONE_WATCH_JS)
        Thread.sleep(4500)
        assertEquals(0, gone.get())
    }

    @Test
    fun afterPipEnds_aLaterRemovalIsNotReported() {
        startPip()
        eval(PIP_RESTORE_MODE_JS)
        eval("document.getElementById('reel').remove(); 1")
        Thread.sleep(4500)
        assertEquals(0, gone.get())
        assertEquals("the watch kept running", "null", eval("window.__astryxPipGoneWatch"))
    }

    private companion object {
        const val PAGE = """<html><body>
            <div id="reelContainer"><video id="reel" width="360" height="640"></video></div>
            <div id="caption">Caption</div>
            </body></html>"""
    }
}
