package com.eepiemi.materialbook.ui.screens

import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * PIP_VIDEO_GONE_WATCH_JS: after focus mode, tells the app (which closes the PiP window) once the marked PiP video has been off the page for a while, and only then.
 */
@RunWith(AndroidJUnit4::class)
class PipVideoGoneJsTest {

    private val gone = AtomicInteger()
    private lateinit var webView: WebView

    inner class FakePipBridge {
        @JavascriptInterface
        fun onPipVideoGone() {
            gone.incrementAndGet()
        }
    }

    private fun load(html: String) {
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                addJavascriptInterface(FakePipBridge(), "PipBridge")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        latch.countDown()
                    }
                }
                loadDataWithBaseURL("https://example.com/", html, "text/html", "UTF-8", null)
            }
        }
        assertTrue("page never finished loading", latch.await(10, TimeUnit.SECONDS))
    }

    private fun eval(js: String): String {
        var result: String? = null
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView.evaluateJavascript(js) { result = it; latch.countDown() }
        }
        assertTrue("evaluateJavascript never returned", latch.await(10, TimeUnit.SECONDS))
        return result ?: "null"
    }

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
        eval("var v = document.getElementById('reel'); v.remove(); setTimeout(function() { document.body.appendChild(v); }, 1000); 1")
        Thread.sleep(5000)
        assertEquals(0, gone.get())
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
