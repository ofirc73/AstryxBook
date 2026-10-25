package com.eepiemi.materialbook.ui.screens

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eepiemi.materialbook.PageFixture
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for pip_video_detector.js's idea of a playing video (what the app's PiP auto-enter
 * follows), against a real WebView. The video's playback state is faked on the element and
 * `Date.now` is a test clock, so a buffering stall's length is set by the test; each change is
 * followed by a media event, which makes the detector report at once.
 */
@RunWith(AndroidJUnit4::class)
class PipVideoDetectorJsTest {

    private class Harness {
        private val page = PageFixture()

        fun load() {
            page.load("https://m.facebook.com/", "<html><body><video id='reel'></video></body></html>")
            // A test WebView isn't laid out: the video and the viewport get a size by hand.
            eval(
                """
                var v = document.getElementById('reel');
                v.__s = { paused: true, ended: false, readyState: 0, currentTime: 0, muted: false,
                  volume: 1, videoWidth: 720, videoHeight: 1280 };
                Object.keys(v.__s).forEach(function(k) {
                  Object.defineProperty(v, k, { get: function() { return this.__s[k]; }, configurable: true });
                });
                v.getBoundingClientRect = function() {
                  return { left: 0, top: 0, right: 400, bottom: 700, width: 400, height: 700 };
                };
                Object.defineProperty(window, 'innerWidth', { value: 400, configurable: true });
                Object.defineProperty(window, 'innerHeight', { value: 800, configurable: true });
                window.__now = 100000;
                Date.now = function() { return window.__now; };
                window.__reports = [];
                window.AstryxPage = { postMessage: function(m) { window.__reports.push(JSON.parse(m)); } };
                1
                """.trimIndent()
            )
            page.run(R.raw.pip_video_detector)
        }

        fun eval(js: String) = page.eval(js)

        // Changes the video's state ([state] a JS object literal) [laterMs] after the last change,
        // then fires [event] on it.
        fun video(state: String, laterMs: Long = 0, event: String = "waiting") = eval(
            "var v = document.getElementById('reel'); Object.assign(v.__s, $state);" +
                " window.__now += $laterMs; v.dispatchEvent(new Event('$event')); 1"
        )

        fun play() = video("{ paused: false, readyState: 4, currentTime: 1 }", event = "playing")
        fun stall(laterMs: Long = 0) = video("{ readyState: 2 }", laterMs)
        fun later(ms: Long) = video("{}", ms)

        // What the detector last told the app.
        fun playing() = eval("window.__reports[window.__reports.length - 1].playing") == "true"
        fun why() = eval("window.__reports[window.__reports.length - 1].why").trim('"')
        fun everPlaying() = eval("window.__reports.some(function(r) { return r.playing; })") == "true"
    }

    @Test
    fun aLoadedVideoThatPlays_countsAsPlaying() {
        val h = Harness()
        h.load()

        h.play()

        assertTrue(h.playing())
    }

    @Test
    fun aVideoThatHasNotPlayedYet_doesNotCountWhileItLoads() {
        val h = Harness()
        h.load()

        h.video("{ paused: false, readyState: 2 }")
        h.later(500)

        assertEquals(false, h.everPlaying())
    }

    @Test
    fun aPlayingVideoThatBuffersBriefly_keepsCountingAsPlaying() {
        val h = Harness()
        h.load()
        h.play()

        h.stall()
        h.later(1500)

        assertTrue("a reel buffering for 1.5 s stopped counting as playing", h.playing())
    }

    @Test
    fun aVideoThatStaysStalled_stopsCountingAfterTheGrace() {
        val h = Harness()
        h.load()
        h.play()

        h.stall()
        h.later(2100)

        assertEquals(false, h.playing())
        assertEquals("v0:ready2", h.why())
    }

    @Test
    fun aVideoThatPlaysOnBetweenStalls_keepsCounting() {
        val h = Harness()
        h.load()
        h.play()
        h.stall(laterMs = 0)
        h.later(1500)

        h.video("{ readyState: 4, currentTime: 2 }", laterMs = 100, event = "playing")
        h.stall(laterMs = 300)
        h.later(1500)

        assertTrue("the grace didn't restart from the last time the video played", h.playing())
    }

    @Test
    fun aPausedVideo_stopsCountingAtOnce() {
        val h = Harness()
        h.load()
        h.play()

        h.video("{ paused: true }", event = "pause")

        assertEquals(false, h.playing())
        assertEquals("v0:paused", h.why())
    }

    @Test
    fun aVideoPausedWhileItBuffers_stopsCountingAtOnce() {
        val h = Harness()
        h.load()
        h.play()
        h.stall(laterMs = 300)

        h.video("{ paused: true }", event = "pause")
        assertEquals(false, h.playing())

        // Played again while still buffering: it doesn't count until it can play on.
        h.video("{ paused: false }", laterMs = 100, event = "play")
        assertEquals(false, h.playing())
        h.video("{ readyState: 4 }", laterMs = 500, event = "playing")
        assertTrue(h.playing())
    }

    @Test
    fun anEndedVideo_stopsCountingAtOnce() {
        val h = Harness()
        h.load()
        h.play()

        h.video("{ ended: true, readyState: 2 }", event = "ended")

        assertEquals(false, h.playing())
        assertEquals("v0:ended", h.why())
    }
}
