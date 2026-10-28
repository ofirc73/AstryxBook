package com.astryx.book.ui.screens
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for the PiP keep-playing guard (pipKeepPlayingActivateJs, PIP_KEEP_PLAYING_DISARM_JS,
 * and PIP_TOGGLE_JS's intent) against a real WebView, same harness approach as
 * PipHandoffJsTest. play()/pause() are spied and dispatch real pause events, the way
 * Facebook's player pausing the video after a resize would.
 */
@RunWith(AndroidJUnit4::class)
class PipKeepPlayingJsTest {

    private class Harness {
        private val page = PageFixture()

        fun load() {
            page.load("https://example.com/", "<html><body><video id='pip' style='width:10px;height:10px;'></video></body></html>")
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

        fun eval(js: String) = page.eval(js)

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
