package com.astryx.book.ui.screens
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for the lock-screen audio handoff scripts (PIP_HANDOFF_ARM_JS,
 * PIP_HANDOFF_READ_JS, PIP_HANDOFF_DISARM_JS, pipHandbackJs) against a real
 * WebView with a synthetic fixture, same harness approach as
 * PipFocusModeJsTest. The events told to the app go through a fake PipBridge
 * (the page has no AstryxPage). The page's hidden/visible transitions are simulated by
 * overriding document.visibilityState and dispatching visibilitychange.
 */
@RunWith(AndroidJUnit4::class)
class PipHandoffJsTest {

    private fun String.unquoted() = removeSurrounding("\"")

    private class Harness {
        // The handoff events told to the app ("sync <src>", "hidden", "visible", "handbackDone"),
        // and the last sync's data.
        val events: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
        @Volatile
        var lastSync: JSONObject? = null

        inner class FakePipBridge {
            @JavascriptInterface
            fun onHandoffEvent(event: String, data: String) {
                val json = JSONObject(data)
                if (event == "sync") lastSync = json
                events += "$event ${json.optString("src")}".trim()
            }
        }

        private val page = PageFixture(interfaces = mapOf("PipBridge" to FakePipBridge()))

        // With DOM storage, where the handback keeps its lead, as the app's WebView has it.
        fun loadHtml(html: String) = page.load("https://example.com/", html) { it.settings.domStorageEnabled = true }

        fun eval(js: String) = page.eval(js)

        fun waitFor(check: () -> Boolean) = page.waitFor(check = check)
    }

    // Fixture: a marked PiP video plus a bigger unmarked one. currentSrc,
    // currentTime and paused are overridden per element so tests don't need
    // real media; play()/pause() are spied the same way PipFocusModeJsTest does.
    // [pipSrc] replaces the PiP video's https source (e.g. a blob: one), and
    // [pipPlayerUrl] wraps it in a player carrying data-video-url, like Facebook's MVideo.
    private fun Harness.loadFixture(
        pipPlaying: Boolean = true,
        pipMuted: Boolean = false,
        pipSrc: String? = null,
        pipPlayerUrl: String? = null,
    ) {
        val pipVideo = """<video id="pip" data-astryx-pip-video="true" style="width:10px;height:10px;"></video>"""
        val pipMarkup = if (pipPlayerUrl == null) pipVideo
        else """<div data-mcomponent="MVideo" data-video-url="$pipPlayerUrl">$pipVideo</div>"""
        loadHtml(
            """
            <html><body>
              $pipMarkup
              <video id="other" style="width:500px;height:500px;"></video>
            </body></html>
            """.trimIndent()
        )
        eval(
            """
            localStorage.clear();
            window.__vis = 'visible';
            Object.defineProperty(document, 'visibilityState', { get: function() { return window.__vis; } });
            window.__calls = [];
            HTMLMediaElement.prototype.play = function() { window.__calls.push('play:' + this.id); this.__paused = false; return undefined; };
            HTMLMediaElement.prototype.pause = function() { window.__calls.push('pause:' + this.id); this.__paused = true; };
            ['pip', 'other'].forEach(function(id) {
              var v = document.getElementById(id);
              v.__paused = true;
              Object.defineProperty(v, 'paused', { get: function() { return this.__paused; } });
              var src = id === 'pip' && ${if (pipSrc == null) "null" else "'$pipSrc'"};
              Object.defineProperty(v, 'currentSrc', { get: function() { return src || 'https://video.example.net/' + id + '.mp4'; } });
              v.__time = 12.5;
              Object.defineProperty(v, 'readyState', { get: function() { return 4; } });
              Object.defineProperty(v, 'seeking', { get: function() { return false; } });
              Object.defineProperty(v, 'currentTime', {
                get: function() { return this.__time; },
                set: function(t) { this.__time = t; }
              });
            });
            var pip = document.getElementById('pip');
            pip.__paused = ${!pipPlaying};
            pip.muted = $pipMuted;
            """.trimIndent()
        )
    }

    private fun Harness.setVisibility(state: String) {
        eval("window.__vis = '$state'; document.dispatchEvent(new Event('visibilitychange'));")
    }

    private fun Harness.read(): JSONObject {
        val raw = eval(PIP_HANDOFF_READ_JS)
        return JSONObject(JSONTokener(raw).nextValue() as String)
    }

    // ── Snapshot at hide ─────────────────────────────────────────────────────

    @Test
    fun read_returnsSnapshotTakenWhenHidden_evenIfPausedAfterwards() {
        val h = Harness()
        h.loadFixture(pipPlaying = true)
        h.eval(PIP_HANDOFF_ARM_JS)

        h.setVisibility("hidden")
        // Facebook pauses the video right after the page is hidden.
        h.eval("document.getElementById('pip').pause();")

        val read = h.read()
        assertEquals("https://video.example.net/pip.mp4", read.getString("src"))
        assertEquals(12.5, read.getDouble("time"), 0.001)
        assertTrue("wasPlaying must reflect the state at hide", read.getBoolean("wasPlaying"))
    }

    @Test
    fun read_usesThePlayersMp4_whenTheVideoPlaysFromABlob() {
        val h = Harness()
        h.loadFixture(
            pipSrc = "blob:https://m.facebook.com/1234",
            pipPlayerUrl = "https://video.fbcdn.example/v/pip.mp4?oh=1&amp;oe=2",
        )
        h.eval(PIP_HANDOFF_ARM_JS)
        h.setVisibility("hidden")

        val read = h.read()
        assertEquals("https://video.fbcdn.example/v/pip.mp4?oh=1&oe=2", read.getString("src"))
        assertEquals(12.5, read.getDouble("time"), 0.001)
        assertTrue(read.getBoolean("wasPlaying"))
    }

    @Test
    fun read_keepsTheBlob_whenThePlayerHasNoMp4() {
        val h = Harness()
        h.loadFixture(pipSrc = "blob:https://m.facebook.com/1234")
        h.eval(PIP_HANDOFF_ARM_JS)
        h.setVisibility("hidden")

        // Not https, so MainActivity skips the handoff (isHandoffEligible).
        assertEquals("blob:https://m.facebook.com/1234", h.read().getString("src"))
    }

    @Test
    fun read_ignoresANonHttpsPlayerUrl() {
        val h = Harness()
        h.loadFixture(
            pipSrc = "blob:https://m.facebook.com/1234",
            pipPlayerUrl = "javascript:alert(1)",
        )
        h.eval(PIP_HANDOFF_ARM_JS)
        h.setVisibility("hidden")

        assertEquals("blob:https://m.facebook.com/1234", h.read().getString("src"))
    }

    @Test
    fun read_reportsNotPlaying_whenVideoWasAlreadyPausedAtHide() {
        val h = Harness()
        h.loadFixture(pipPlaying = false)
        h.eval(PIP_HANDOFF_ARM_JS)
        h.setVisibility("hidden")

        assertEquals(false, h.read().getBoolean("wasPlaying"))
    }

    @Test
    fun read_withoutSnapshot_returnsEmptySrc_andLeavesVideoAlone() {
        val h = Harness()
        h.loadFixture()
        h.eval(PIP_HANDOFF_ARM_JS) // armed, but the page was never hidden

        val read = h.read()

        assertEquals("", read.getString("src"))
        assertEquals("false", h.eval("document.getElementById('pip').muted"))
        assertEquals(
            "false",
            h.eval("document.getElementById('pip').hasAttribute('data-astryx-handoff-muted')")
        )
    }

    @Test
    fun read_mutesAndPausesTheSnapshottedVideo_andSavesPriorMute() {
        val h = Harness()
        h.loadFixture(pipPlaying = true, pipMuted = false)
        h.eval(PIP_HANDOFF_ARM_JS)
        h.setVisibility("hidden")

        h.read()

        assertEquals("true", h.eval("document.getElementById('pip').muted"))
        assertEquals(
            "false",
            h.eval("document.getElementById('pip').getAttribute('data-astryx-handoff-muted')").unquoted()
        )
        assertTrue(h.eval("window.__calls.join(',')").contains("pause:pip"))
        assertTrue(
            "the other video must not be touched",
            !h.eval("window.__calls.join(',')").contains("other")
        )
    }

    // ── Events to the app ────────────────────────────────────────────────────

    @Test
    fun armed_tellsTheAppEverySecond_wherePipVideosSoundIs_forTheNativePlayerToPlayItSilently() {
        // Started only at the lock, the native player was heard 0.13 s after the page stopped, then
        // the phone's output latency: a break (Galaxy A56).
        val h = Harness()
        h.loadFixture(pipPlaying = true)
        h.eval(PIP_HANDOFF_ARM_JS)
        assertTrue(h.events.toString(), h.waitFor { h.events.firstOrNull() == "sync https://video.example.net/pip.mp4" })
        assertEquals(12.5, h.lastSync!!.getDouble("time"), 0.001)
        assertTrue(h.lastSync!!.getBoolean("playing"))
        h.eval("$pip.__time = 14; $pip.__paused = true;")
        assertTrue("no sync a second later", h.waitFor { h.lastSync!!.getDouble("time") == 14.0 })
        assertEquals(false, h.lastSync!!.getBoolean("playing"))

        // Not while the page is hidden.
        h.setVisibility("hidden")
        h.events.clear()
        Thread.sleep(1500)
        assertEquals(emptyList<String>(), h.events.filter { it.startsWith("sync") })
    }

    @Test
    fun hidingThePage_tellsTheAppAtOnce_whileThePipVideoPlays() {
        // The app starts the native player then: ACTION_SCREEN_OFF came 0.5 s later (device spike).
        val h = Harness()
        h.loadFixture(pipPlaying = true)
        h.eval(PIP_HANDOFF_ARM_JS)
        h.setVisibility("hidden")
        assertTrue(h.events.toString(), h.waitFor { h.events.lastOrNull() == "hidden" })

        val paused = Harness()
        paused.loadFixture(pipPlaying = false)
        paused.eval(PIP_HANDOFF_ARM_JS)
        paused.setVisibility("hidden")
        paused.eval("1")
        Thread.sleep(300)
        assertTrue("a paused video has nothing to hand off", "hidden" !in paused.events)
    }

    @Test
    fun theSound_isTheStreamsAudioTrack_whenReelCommentsAudioReadsOne() {
        // As loud as the video, where the MP4 in data-video-url is quieter (reel_comments_audio.js).
        val h = Harness()
        h.loadFixture(
            pipSrc = "blob:https://m.facebook.com/1234",
            pipPlayerUrl = "https://video.fbcdn.example/v/pip.mp4",
        )
        h.eval("window.__astryxReelCommentsAudio = { soundUrl: function (p) { return 'https://video.fbcdn.example/m412/audio.mp4'; } };")
        h.eval(PIP_HANDOFF_ARM_JS)
        h.setVisibility("hidden")
        assertEquals("https://video.fbcdn.example/m412/audio.mp4", h.read().getString("src"))
    }

    @Test
    fun visibleAgain_notifiesBridge_onlyWhileHandoffActive() {
        val h = Harness()
        h.loadFixture(pipPlaying = false)
        h.eval(PIP_HANDOFF_ARM_JS)

        h.setVisibility("hidden")
        h.setVisibility("visible") // no handoff happened yet
        h.eval("1") // flush
        Thread.sleep(300)
        assertTrue(h.events.toString(), "visible" !in h.events)

        h.setVisibility("hidden")
        h.read()
        h.setVisibility("visible")
        assertTrue(h.events.toString(), h.waitFor { h.events.count { it == "visible" } == 1 })
    }

    // ── Handback ─────────────────────────────────────────────────────────────

    // Hidden at a lock while playing, read (muted and paused, as the native player takes over),
    // visible again (unlocked).
    private fun Harness.locked(pipMuted: Boolean = false, duration: Int? = null) {
        loadFixture(pipPlaying = true, pipMuted = pipMuted)
        duration?.let { eval("Object.defineProperty(document.getElementById('pip'), 'duration', { get: function () { return $it; } });") }
        eval(PIP_HANDOFF_ARM_JS)
        setVisibility("hidden")
        read()
        setVisibility("visible")
        eval("window.__calls = [];")
        waitFor { events.lastOrNull() == "visible" }
        events.clear()
    }

    // The page's video put where the native player is now ([positionMs] at [atMs], playing on).
    private fun Harness.inStepWithNative(positionMs: Long, atMs: Long, offsetS: Double = 0.0) =
        eval("document.getElementById('pip').__time = ${positionMs / 1000.0} + (Date.now() - $atMs) / 1000 + $offsetS;")

    private val pip = "document.getElementById('pip')"

    @Test
    fun handback_playsTheVideoMuted_pastTheNativePlayer_andUnmutesItOnceInStep() {
        // Stopped first, the native player left a break: Facebook took 1.7 s to play the video
        // again (its seek, on the emulator).
        val h = Harness()
        h.locked()
        val at = System.currentTimeMillis()
        h.eval(pipHandbackJs(positionMs = 45_250, positionAtMs = at, play = true, rearm = false))

        assertEquals("moved past the native player by the first lead (0.6 s)", 45.85, h.eval("$pip.currentTime").toDouble(), 0.2)
        assertEquals("play:pip", h.eval("window.__calls.join(',')").unquoted())
        assertEquals("unmuted before in step with the native player", "true", h.eval("$pip.muted"))
        assertEquals(emptyList<String>(), h.events.toList())

        h.inStepWithNative(45_250, at)
        assertTrue(h.events.toString(), h.waitFor { h.events == listOf("handbackDone") })
        assertEquals("false", h.eval("$pip.muted"))
        assertEquals("false", h.eval("$pip.hasAttribute('data-astryx-handoff-muted')"))
        assertEquals("1", h.eval("$pip.playbackRate"))
        // How long its move took to play, for the next handback, in later sessions too: a first
        // guess of 1 s put the phone's video ahead, played slower for a moment (seen as jitter).
        assertTrue(h.eval("localStorage.getItem('astryxHandbackLead')"), h.eval("parseFloat(localStorage.getItem('astryxHandbackLead')) > 0") == "true")
    }

    @Test
    fun handback_aheadOfTheNativePlayer_playsSlower_andBehindIt_faster() {
        val h = Harness()
        h.locked()
        val at = System.currentTimeMillis()
        h.eval(pipHandbackJs(positionMs = 45_250, positionAtMs = at, play = true, rearm = false))
        assertTrue(h.waitFor { h.eval("$pip.playbackRate") == "0.5" })
        h.inStepWithNative(45_250, at, offsetS = -1.0)
        assertTrue(h.waitFor { h.eval("$pip.playbackRate") == "1.5" })
        assertEquals("true", h.eval("$pip.muted"))
    }

    @Test
    fun handback_waitsForThePageToBeVisible() {
        // Chromium pauses a hidden page's video: onStart comes before the page is visible.
        val h = Harness()
        h.locked()
        h.eval("window.__vis = 'hidden'; 1")
        h.eval(pipHandbackJs(positionMs = 45_250, positionAtMs = System.currentTimeMillis(), play = true, rearm = false))
        Thread.sleep(300)
        assertEquals(12.5, h.eval("$pip.currentTime").toDouble(), 0.001)
        assertEquals("", h.eval("window.__calls.join(',')").unquoted())
        h.setVisibility("visible")
        assertTrue(h.waitFor { h.eval("window.__calls.join(',')").unquoted() == "play:pip" })
    }

    @Test
    fun handback_ofAVideoThatCantPlay_isDoneAtOnce() {
        val h = Harness()
        h.locked()
        h.eval("HTMLMediaElement.prototype.play = function () { return Promise.reject(new Error('no source')); };")
        h.eval(pipHandbackJs(positionMs = 45_250, positionAtMs = System.currentTimeMillis(), play = true, rearm = false))
        assertTrue(h.events.toString(), h.waitFor { h.events == listOf("handbackDone") })
        assertEquals("false", h.eval("$pip.muted"))
    }

    @Test
    fun handback_atTheVideosEnd_startsItOver() {
        // The lock-screen player reached the end of a 260 s video; seeking the page's video
        // there made Facebook close the viewer (back to the feed, a black PiP window).
        val h = Harness()
        h.locked(duration = 260)
        h.eval(pipHandbackJs(positionMs = 259_766, positionAtMs = System.currentTimeMillis(), play = true, rearm = false))
        assertEquals(0.0, h.eval("$pip.currentTime").toDouble(), 0.001)
    }

    @Test
    fun handback_beforeTheEnd_keepsThePosition() {
        val h = Harness()
        h.locked(duration = 260)
        h.eval(pipHandbackJs(positionMs = 200_000, positionAtMs = System.currentTimeMillis(), play = true, rearm = false))
        assertEquals(200.6, h.eval("$pip.currentTime").toDouble(), 0.2)
    }

    @Test
    fun handback_doesNotPlay_whenNativePlayerWasPaused() {
        val h = Harness()
        h.locked()
        h.eval(pipHandbackJs(positionMs = 20_000, positionAtMs = System.currentTimeMillis(), play = false, rearm = false))
        assertEquals("", h.eval("window.__calls.join(',')").unquoted())
        assertEquals(20.0, h.eval("$pip.currentTime").toDouble(), 0.2)
        assertEquals("false", h.eval("$pip.muted"))
        assertTrue(h.events.toString(), h.waitFor { h.events == listOf("handbackDone") })
    }

    @Test
    fun handback_restoresMutedTrue_whenVideoWasMutedBefore() {
        val h = Harness()
        h.locked(pipMuted = true)
        h.eval(pipHandbackJs(positionMs = 1_000, positionAtMs = System.currentTimeMillis(), play = false, rearm = false))
        assertEquals("true", h.eval("$pip.muted"))
    }

    @Test
    fun abortedHandback_withoutPosition_leavesTimeAlone() {
        val h = Harness()
        h.locked()
        h.eval(pipHandbackJs(positionMs = null, positionAtMs = System.currentTimeMillis(), play = false, rearm = false))
        assertEquals(12.5, h.eval("$pip.currentTime").toDouble(), 0.001)
        assertEquals("false", h.eval("$pip.muted"))
    }

    @Test
    fun handback_clearsSnapshotAndListener_andRearmsWhenAsked() {
        val h = Harness()
        h.locked()

        h.eval(pipHandbackJs(positionMs = 1_000, positionAtMs = System.currentTimeMillis(), play = false, rearm = false))
        assertEquals("null", h.eval("window.__astryxHandoffSnapshot"))
        assertEquals("null", h.eval("window.__astryxHandoffListener"))
        h.setVisibility("hidden")
        assertEquals("null", h.eval("window.__astryxHandoffSnapshot"))

        h.eval(pipHandbackJs(positionMs = null, positionAtMs = System.currentTimeMillis(), play = false, rearm = true))
        h.setVisibility("visible")
        h.eval("$pip.__paused = false;")
        h.setVisibility("hidden")
        assertEquals("true", h.eval("window.__astryxHandoffSnapshot.wasPlaying"))
    }

    @Test
    fun disarm_removesListenerAndSnapshot() {
        val h = Harness()
        h.loadFixture(pipPlaying = true)
        h.eval(PIP_HANDOFF_ARM_JS)
        h.setVisibility("hidden")

        h.eval(PIP_HANDOFF_DISARM_JS)

        assertEquals("null", h.eval("window.__astryxHandoffSnapshot"))
        h.setVisibility("visible")
        h.setVisibility("hidden")
        assertEquals("null", h.eval("window.__astryxHandoffSnapshot"))
    }

    @Test
    fun arm_isIdempotent() {
        val h = Harness()
        h.loadFixture(pipPlaying = true)
        h.eval(PIP_HANDOFF_ARM_JS)
        val first = h.eval("String(window.__astryxHandoffListener)")
        h.eval(PIP_HANDOFF_ARM_JS)

        assertEquals(first, h.eval("String(window.__astryxHandoffListener)"))
    }
}
