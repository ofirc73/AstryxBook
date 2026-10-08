package com.eepiemi.materialbook.ui.screens

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Tests for pip_visibility.js, the IntersectionObserver wrapper that keeps Facebook's
 * desktop player from pausing the PiP video as "less than 50% visible". The native
 * IntersectionObserver is replaced by a stub before the script runs, so the tests deliver
 * entries themselves (a test WebView isn't rendered, so real observers wouldn't fire).
 */
@RunWith(AndroidJUnit4::class)
class PipVisibilityJsTest {

    private class Harness {
        lateinit var webView: WebView
            private set

        fun load() {
            val latch = CountDownLatch(1)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            latch.countDown()
                        }
                    }
                    loadDataWithBaseURL(
                        "https://www.facebook.com/",
                        "<html><body><div id='player'><div><video id='pip'></video></div></div>" +
                            "<div id='other'></div></body></html>",
                        "text/html", "UTF-8", null
                    )
                }
            }
            assertTrue("page never finished loading", latch.await(10, TimeUnit.SECONDS))
            // Stub native observer: records callbacks; __deliver(i, id, ratio) fires one entry.
            eval(
                """
                window.__ios = [];
                window.IntersectionObserver = function(cb) { this.cb = cb; window.__ios.push(this); };
                IntersectionObserver.prototype.observe = function() {};
                IntersectionObserver.prototype.unobserve = function() {};
                IntersectionObserver.prototype.disconnect = function() {};
                window.__deliver = function(i, id, ratio) {
                  var o = window.__ios[i], t = document.getElementById(id);
                  o.cb.call(o, [{ target: t, intersectionRatio: ratio, isIntersecting: ratio > 0,
                    rootBounds: null, boundingClientRect: t.getBoundingClientRect(), time: 1 }], o);
                };
                """.trimIndent()
            )
            val script = InstrumentationRegistry.getInstrumentation().targetContext.resources
                .openRawResource(R.raw.pip_visibility).bufferedReader().use { it.readText() }
            eval(script)
            // The page's own observer, watching the player box and an unrelated element.
            eval(
                """
                window.__seen = [];
                window.__io = new IntersectionObserver(function(entries) {
                  entries.forEach(function(e) { window.__seen.push(e.target.id + ':' + e.intersectionRatio); });
                });
                window.__io.observe(document.getElementById('player'));
                window.__io.observe(document.getElementById('other'));
                """.trimIndent()
            )
        }

        fun eval(js: String): String {
            var result: String? = null
            val latch = CountDownLatch(1)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                webView.evaluateJavascript(js) { value ->
                    result = value
                    latch.countDown()
                }
            }
            assertTrue("evaluateJavascript never returned", latch.await(10, TimeUnit.SECONDS))
            return result ?: "null"
        }

        fun deliver(id: String, ratio: Double) = eval("window.__deliver(0, '$id', $ratio)")
        fun seen() = eval("window.__seen.join(',')").trim('"')
        fun enterPip() = eval(
            "window.__astryxPipActive = true;" +
                "window.__astryxPipVideo = function() { return document.getElementById('pip'); };"
        )
    }

    @Test
    fun outsidePip_entriesPassThrough() {
        val h = Harness()
        h.load()

        h.deliver("player", 0.25)

        assertEquals("player:0.25", h.seen())
    }

    @Test
    fun inPip_pipVideosPlayerReportsFullyVisible() {
        val h = Harness()
        h.load()
        h.enterPip()

        h.deliver("player", 0.25)
        h.deliver("other", 0.25)

        assertEquals("player:1,other:0.25", h.seen())
    }

    @Test
    fun replay_redeliversVisibleOnlyForThePipVideosPlayer() {
        val h = Harness()
        h.load()
        h.enterPip()

        assertEquals("1", h.eval("window.__astryxPipVisibleReplay()"))
        assertEquals("player:1", h.seen())
    }

    @Test
    fun replay_skipsUnobservedTargets() {
        val h = Harness()
        h.load()
        h.enterPip()

        h.eval("window.__io.unobserve(document.getElementById('player'))")
        assertEquals("0", h.eval("window.__astryxPipVisibleReplay()"))
        h.eval("window.__io.observe(document.getElementById('player')); window.__io.disconnect()")
        assertEquals("0", h.eval("window.__astryxPipVisibleReplay()"))
    }

    @Test
    fun afterPip_entriesPassThroughAgain() {
        val h = Harness()
        h.load()
        h.enterPip()
        h.eval(PIP_KEEP_PLAYING_DISARM_JS)

        h.deliver("player", 0.25)

        assertEquals("0", h.eval("window.__astryxPipVisibleReplay()"))
        assertEquals("player:0.25", h.seen())
    }

    @Test
    fun keepPlayingActivation_replaysVisibility() {
        val h = Harness()
        h.load()
        h.eval("window.__astryxLastActiveVideo = document.getElementById('pip');")

        h.eval(pipKeepPlayingActivateJs(wantsPlay = true))

        assertEquals("player:1", h.seen())
    }

    @Test
    fun wrappedObserver_isStillAnIntersectionObserver() {
        val h = Harness()
        h.load()

        assertEquals("true", h.eval("window.__io instanceof IntersectionObserver"))
    }
}
