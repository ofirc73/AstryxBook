package com.eepiemi.materialbook.ui.screens

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

/**
 * Tests for the PiP keep-playing guard (pipKeepPlayingActivateJs, PIP_KEEP_PLAYING_DISARM_JS,
 * and PIP_TOGGLE_JS's intent) against a real WebView, same harness approach as
 * PipHandoffJsTest. play()/pause() are spied and dispatch real pause events, the way
 * Facebook's player pausing the video after a resize would.
 */
@RunWith(AndroidJUnit4::class)
class PipKeepPlayingJsTest {

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
                        "https://example.com/",
                        "<html><body><video id='pip' style='width:10px;height:10px;'></video></body></html>",
                        "text/html", "UTF-8", null
                    )
                }
            }
            assertTrue("page never finished loading", latch.await(10, TimeUnit.SECONDS))
            eval(
                """
                window.__plays = 0;
                var v = document.getElementById('pip');
                v.__paused = false;
                Object.defineProperty(v, 'paused', { get: function() { return this.__paused; } });
                HTMLMediaElement.prototype.play = function() {
                  window.__plays++; this.__paused = false; return undefined;
                };
                HTMLMediaElement.prototype.pause = function() {
                  this.__paused = true; this.dispatchEvent(new Event('pause'));
                };
                window.__astryxLastActiveVideo = v;
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

        // The guard resumes 150 ms after a pause; give it time.
        fun settle() {
            Thread.sleep(400)
            eval("1")
        }

        fun plays() = eval("window.__plays").toInt()
        fun facebookPauses() = eval("document.getElementById('pip').pause()")
        fun paused() = eval("document.getElementById('pip').paused") == "true"
    }

    private fun Harness.enterPip() {
        facebookPauses()                         // Facebook's pause on the PiP resize
        eval(pipKeepPlayingActivateJs(wantsPlay = true)) // PiP engaged, video was playing
        settle()
    }

    @Test
    fun pauseOnPipEntry_isUndone() {
        val h = Harness()
        h.load()

        h.enterPip()

        assertEquals(1, h.plays())
        assertEquals(false, h.paused())
    }

    @Test
    fun laterPauseDuringPip_isUndone() {
        val h = Harness()
        h.load()
        h.enterPip()

        h.facebookPauses()
        h.settle()

        assertEquals(2, h.plays())
        assertEquals(false, h.paused())
    }

    @Test
    fun videoNotPlayingWhenPipStarted_staysPaused() {
        val h = Harness()
        h.load()
        h.eval("document.getElementById('pip').__paused = true;")

        h.eval(pipKeepPlayingActivateJs(wantsPlay = false))
        h.facebookPauses()
        h.settle()

        assertEquals(0, h.plays())
    }

    @Test
    fun withoutPip_pausesAreNotTouched() {
        val h = Harness()
        h.load()

        h.facebookPauses()
        h.settle()

        assertEquals(0, h.plays())
    }

    @Test
    fun userPauseFromThePipButton_sticks() {
        val h = Harness()
        h.load()
        h.enterPip()
        h.eval("window.__plays = 0;")

        h.eval(PIP_TOGGLE_JS) // playing -> the user pauses
        h.settle()

        assertEquals(0, h.plays())
        assertEquals(true, h.paused())

        h.eval(PIP_TOGGLE_JS) // and plays again: the guard is back on
        h.facebookPauses()
        h.settle()
        assertEquals(false, h.paused())
    }

    @Test
    fun lockScreenHandoff_isNotFought() {
        val h = Harness()
        h.load()
        h.enterPip()
        h.eval("window.__plays = 0;")

        h.eval("document.getElementById('pip').setAttribute('data-astryx-handoff-muted', 'false');")
        h.facebookPauses()
        h.settle()

        assertEquals(0, h.plays())
    }

    @Test
    fun afterLeavingPip_pausesStick() {
        val h = Harness()
        h.load()
        h.enterPip()
        h.eval("window.__plays = 0;")

        h.eval(PIP_KEEP_PLAYING_DISARM_JS)
        h.facebookPauses()
        h.settle()

        assertEquals(0, h.plays())
    }

    @Test
    fun resumesAreRateLimited() {
        val h = Harness()
        h.load()
        h.enterPip()

        repeat(10) {
            h.facebookPauses()
            h.settle()
        }

        assertEquals("6 resumes per 10 s at most", 6, h.plays())
    }
}
