package com.astryx.book.ui.screens
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import com.eepiemi.materialbook.R
import org.json.JSONTokener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * reel_comments_audio.js: a reel's sound goes on while its comments are open, and Back hands it
 * back to the reel in step. Facebook's moves are the ones read on the emulator (2026-10-31): about
 * a second after the tap the comments open at "/" (pushState) and the reel's <video> is set back
 * to 0 and removed (v1.39.0 read its position after that and started the sound at 0); Back passes
 * through /watch/, where the new reel's 'play' fires, then lands on the reel's path (its 'playing'
 * fires there) and gets its query back a moment later. Faked: the reel's paused, muted, currentTime,
 * readyState, seeking and buffered (window.__buf: [start, end] ranges, all of it loaded at first); fetch (window.__fetched); Web Audio, its clock (window.__now, s), the
 * sounds started (window.__sources: at, stopped, out.gain.value 0 silent or 1 heard), the gain the
 * reel back is silenced through (window.__silencer) and the elements routed through it
 * (window.__routed).
 */
@RunWith(AndroidJUnit4::class)
class ReelCommentsAudioJsTest {

    private class Harness {
        private val page = PageFixture(width = 1080, height = 2000)

        fun eval(js: String) = page.eval(js)

        // [js]'s value as JSON text.
        fun json(js: String) = JSONTokener(eval("JSON.stringify($js)")).nextValue() as String

        fun waitFor(js: String, ms: Long = 5000) = page.waitFor(ms) { eval(js) == "true" }

        // A reel page: the reel playing at [time] s in its player (data-video-url, and its stream's
        // tracks in data-extra, with an audio track unless not [streamAudio]), and its comment
        // button with Facebook's icon and [label]. [decode]: the sound is decoded at once (else when
        // window.__decoded() is called); [fetchFails]: its sound can't be fetched.
        fun load(
            path: String = REEL_PATH,
            label: String = "Comment on reel by Someone, 107 comments",
            icon: Boolean = true,
            muted: Boolean = false,
            playing: Boolean = true,
            time: Double = 12.5,
            decode: Boolean = true,
            fetchFails: Boolean = false,
            streamAudio: Boolean = true,
            setup: String = "",
        ) {
            page.load(
                "https://m.facebook.com$path",
                "<html><head><meta name='viewport' content='width=device-width'></head><body style='margin:0'>" +
                    "<div id='reel'><div data-mcomponent='MVideo' data-video-url='$MP4'" +
                    " data-extra='${if (streamAudio) EXTRA else EXTRA_NO_AUDIO}'>" +
                    "<video id='v1' style='width:400px;height:700px'></video></div>" +
                    "<div id='comment' role='button' aria-label='$label'>" +
                    "<span>${if (icon) "&#xF0379;" else "x"}</span><span>107</span></div></div></body></html>"
            )
            eval(
                """
                window.__fetched = [];
                window.fetch = function (u) {
                  window.__fetched.push(u);
                  return $fetchFails ? Promise.reject(new Error('offline')) :
                    Promise.resolve({ ok: true, arrayBuffer: function () { return Promise.resolve(new ArrayBuffer(8)); } });
                };
                window.__now = 0; window.__sources = []; window.__routed = [];
                window.AudioContext = function () {
                  this.state = 'running'; this.destination = {};
                  Object.defineProperty(this, 'currentTime', { get: function () { return window.__now; } });
                };
                AudioContext.prototype.suspend = function () { this.state = 'suspended'; window.__ctxState = this.state; };
                AudioContext.prototype.resume = function () { this.state = 'running'; window.__ctxState = this.state; };
                AudioContext.prototype.decodeAudioData = function () {
                  return $decode ? Promise.resolve({ duration: 30 }) :
                    new Promise(function (r) { window.__decoded = function () { r({ duration: 30 }); }; });
                };
                AudioContext.prototype.createBufferSource = function () {
                  return { connect: function (g) { this.out = g; },
                    start: function (when, at) { this.at = at; window.__sources.push(this); },
                    stop: function () { this.stopped = true; } };
                };
                AudioContext.prototype.createGain = function () {
                  return { gain: { value: 1, setTargetAtTime: function (v) { this.value = v; } }, connect: function () {} };
                };
                AudioContext.prototype.createMediaElementSource = function (v) {
                  // Once per element, as Web Audio allows.
                  if (v.__routedOnce) throw new DOMException('already routed', 'InvalidStateError');
                  v.__routedOnce = true;
                  window.__routed.push(v.id);
                  return { connect: function (g) { window.__silencer = g; } };
                };
                window.__fakeVideo = function (v, time, playing, muted) {
                  v.__time = time; v.__paused = !playing; v.muted = muted; v.__rs = 4; v.__buf = [[0, 30]];
                  Object.defineProperty(v, 'paused', { get: function () { return this.__paused; } });
                  Object.defineProperty(v, 'duration', { get: function () { return 30; } });
                  Object.defineProperty(v, 'readyState', { get: function () { return this.__rs; } });
                  Object.defineProperty(v, 'seeking', { get: function () { return false; } });
                  Object.defineProperty(v, 'buffered', { get: function () {
                    var b = this.__buf;
                    return { length: b.length, start: function (i) { return b[i][0]; }, end: function (i) { return b[i][1]; } };
                  } });
                  Object.defineProperty(v, 'currentTime', {
                    get: function () { return this.__time; }, set: function (t) { this.__time = t; } });
                };
                __fakeVideo(document.getElementById('v1'), $time, $playing, $muted);
                $setup
                1
                """.trimIndent()
            )
            page.run(R.raw.reel_comments_audio)
        }

        fun tapComment() = eval("document.getElementById('comment').click(); 1")

        // Facebook's comments opening after the tap: the address is "/", the reel's <video> is set
        // back to 0 and goes.
        fun commentsOpen() = eval("history.pushState(null, '', '/'); var v = document.getElementById('v1');" +
            " if (v) v.__time = 0; document.getElementById('reel').remove(); 1")

        fun openComments() {
            tapComment()
            commentsOpen()
        }

        // The last sound started.
        val sound = "window.__sources[window.__sources.length - 1]"

        fun soundPlaying() = waitFor("!!$sound && !$sound.stopped && $sound.out.gain.value === 1")

        fun soundStopped() = waitFor("!!$sound && $sound.stopped && window.__astryxReelCommentsAudio.position() === -1")

        fun position() = eval("window.__astryxReelCommentsAudio.position()").toDouble()

        // The sound's clock moved on to where the sound is at [at] s.
        fun soundAt(at: Double) = eval("window.__now += $at - window.__astryxReelCommentsAudio.position(); 1")

        // Back to the reel as Facebook does it: /watch/ (the new reel's 'play'), then the reel's
        // path ('playing'), then its query. The new reel, at [newTime], is still loading (__rs = 4
        // when it's ready), loaded from its start to [loadedTo] s.
        fun backToTheReel(newTime: Double = 0.0, loadedTo: Double = 30.0) {
            eval(
                "history.pushState(null, '', '/watch/');" +
                    " var d = document.createElement('div'); d.setAttribute('data-video-url', '$MP4');" +
                    " d.innerHTML = '<video id=\"v2\"></video>'; document.body.appendChild(d);" +
                    " var v = document.getElementById('v2'); __fakeVideo(v, $newTime, true, false); v.__rs = 1; v.__buf = [[0, $loadedTo]];" +
                    " v.dispatchEvent(new Event('play')); 1"
            )
            Thread.sleep(300)
            eval("history.replaceState(null, '', '$REEL_PATH'); document.getElementById('v2').dispatchEvent(new Event('playing'));" +
                " history.replaceState(null, '', '$REEL_PATH?referral_source=external_link'); 1")
        }

        val v2 = "document.getElementById('v2')"

        fun assertNoSound() {
            Thread.sleep(600)
            assertEquals("nothing was to be fetched or played", "0,0",
                eval("window.__fetched.length + ',' + window.__sources.length").removeSurrounding("\""))
        }
    }

    @Test
    fun theCommentButton_playsTheReelsSound_fromWhereFacebookStoppedTheReel() {
        // The reel plays on for a moment after the tap (about a second on the emulator), then
        // Facebook sets it back to 0 as it removes it: the sound starts where it was before that.
        val h = Harness()
        h.load(time = 12.5)
        h.tapComment()
        assertEquals("[\"$AUDIO_URL\"]", h.json("window.__fetched"))
        h.eval("document.getElementById('v1').__time = 13.25; 1")
        // Silent, in step with the reel while it plays.
        assertTrue("no silent sound in step with the playing reel (${h.json("window.__sources")})",
            h.waitFor("!!${h.sound} && !${h.sound}.stopped && ${h.sound}.out.gain.value === 0 && Math.abs(${h.sound}.at - 13.25) < 0.01"))
        val sounds = h.eval("window.__sources.length")
        // Facebook's pause(): heard at once (v1.39.2 started it only after noticing: a short cut).
        assertEquals("the sound wasn't heard as Facebook paused the reel", "1",
            h.eval("document.getElementById('v1').pause(); ${h.sound}.out.gain.value"))
        h.commentsOpen()
        assertTrue("the reel's sound stopped", h.soundPlaying())
        assertEquals("the sound started again rather than going on", sounds, h.eval("window.__sources.length"))
        assertEquals(13.25, h.eval("${h.sound}.at").toDouble(), 0.01)
        assertEquals("true", h.eval("${h.sound}.loop"))
        h.eval("window.__now = 2; 1")
        assertEquals(15.25, h.position(), 0.1)
    }

    @Test
    fun aSoundDecodedAfterFacebookStoppedTheReel_startsWhereTheReelIsBy_then() {
        val h = Harness()
        h.load(time = 12.5, decode = false)
        h.openComments()
        Thread.sleep(500)
        assertEquals("0", h.eval("window.__sources.length"))
        h.eval("window.__decoded(); 1")
        assertTrue(h.soundPlaying())
        val at = h.eval("${h.sound}.at").toDouble()
        assertTrue("the sound started at $at s, not about 0.5 s on from 12.5 s", at in 12.9..13.5)
    }

    @Test
    fun theTouchOnTheButton_fetchesTheSound_andTheTapUsesIt() {
        val h = Harness()
        h.load()
        h.eval("document.getElementById('comment').dispatchEvent(new PointerEvent('pointerdown', { bubbles: true })); 1")
        assertEquals("1", h.eval("window.__fetched.length"))
        h.openComments()
        assertTrue(h.soundPlaying())
        assertEquals("the tap fetched the sound again", "1", h.eval("window.__fetched.length"))
    }

    @Test
    fun back_silencesTheReel_untilItPlaysInStepWithTheSound_thenStopsTheSound() {
        val h = Harness()
        h.load()
        h.openComments()
        assertTrue(h.soundPlaying())
        h.soundAt(21.0)
        h.backToTheReel()
        assertEquals("the reel back wasn't silenced through Web Audio", "[\"v2\"]", h.json("window.__routed"))
        assertEquals("0", h.eval("window.__silencer.gain.value"))
        assertEquals("the reel back was muted (Facebook's player then stalls loading it)", "false", h.eval("${h.v2}.muted"))
        // Moved past the sound by the first lead (0.3 s), once.
        assertTrue("the reel back wasn't moved past the sound (${h.eval("${h.v2}.currentTime")})",
            h.waitFor("Math.abs(${h.v2}.currentTime - 21.3) < 0.05"))
        // Ready at 21.3 while the sound is at 21: ahead, so it plays slower, by the gap per second
        // (0.5x until in step before, seen on the phone).
        h.eval("${h.v2}.__rs = 4; 1")
        assertTrue(h.waitFor("${h.v2}.playbackRate === 0.7"))
        assertEquals("false", h.eval("!!${h.sound}.stopped"))
        // In step: heard again, at its own speed, and the sound stops.
        h.soundAt(21.3)
        assertTrue("the sound went on with the reel in step", h.soundStopped())
        assertEquals("1", h.eval("window.__silencer.gain.value"))
        assertEquals("1", h.eval("${h.v2}.playbackRate"))
    }

    @Test
    fun back_movesTheReelOnlyOnceFacebookLoadedItFromItsStartToTheSound() {
        // Moved before, Facebook loaded it from there on only, and when it started again after its
        // end, never loaded its start: it stopped there for good (v1.39.2-v1.39.4, on the emulator).
        val h = Harness()
        h.load()
        h.openComments()
        assertTrue(h.soundPlaying())
        h.soundAt(21.0)
        h.backToTheReel(loadedTo = 18.4)
        Thread.sleep(400)
        assertEquals("the reel back was moved before it was loaded up to the sound", "0", h.eval("${h.v2}.currentTime"))
        assertEquals("false", h.eval("!!${h.sound}.stopped"))
        // Held still meanwhile: played from its start for that second, it was seen to jump.
        assertEquals("0.0625", h.eval("${h.v2}.playbackRate"))
        h.eval("${h.v2}.__buf = [[0, 30]]; 1")
        assertTrue("the reel back wasn't moved once loaded (${h.eval("${h.v2}.currentTime")})",
            h.waitFor("Math.abs(${h.v2}.currentTime - 21.3) < 0.05"))
        assertEquals("1", h.eval("${h.v2}.playbackRate"))
    }

    @Test
    fun back_aReelThatDoesntLoadUpToTheSound_isMovedAfterThreeSeconds() {
        // A long reel, loaded as it plays: it's not kept playing silently from its start.
        val h = Harness()
        h.load()
        h.openComments()
        assertTrue(h.soundPlaying())
        h.soundAt(21.0)
        h.backToTheReel(loadedTo = 5.0)
        Thread.sleep(2000)
        assertEquals("0", h.eval("${h.v2}.currentTime"))
        assertTrue("the reel back was never moved (${h.eval("${h.v2}.currentTime")})",
            h.waitFor("Math.abs(${h.v2}.currentTime - 21.3) < 0.05", ms = 3000))
    }

    @Test
    fun theAudioContext_restsWhileNothingPlaysThroughIt() {
        // The reel back keeps its sound in Web Audio: the context runs while the reel plays.
        val h = Harness()
        h.load()
        h.openComments()
        assertTrue(h.soundPlaying())
        h.soundAt(21.0)
        h.backToTheReel()
        assertTrue(h.waitFor("Math.abs(${h.v2}.currentTime - 21.3) < 0.05"))
        h.eval("${h.v2}.__rs = 4; 1")
        h.soundAt(21.3)
        assertTrue(h.soundStopped())
        assertEquals("the context rested with the reel playing through it", "undefined",
            h.eval("typeof window.__ctxState").removeSurrounding("\""))
        h.eval("${h.v2}.__paused = true; ${h.v2}.dispatchEvent(new Event('pause')); 1")
        assertEquals("suspended", h.eval("window.__ctxState").removeSurrounding("\""))
        h.eval("${h.v2}.__paused = false; ${h.v2}.dispatchEvent(new Event('play')); 1")
        assertEquals("running", h.eval("window.__ctxState").removeSurrounding("\""))
    }

    @Test
    fun back_aReelFarAheadOfTheSound_isHeldStill_untilTheSoundGetsThere() {
        // Its seek took 1-3 s on the emulator, each time different: landed 2.6 s ahead, it took over
        // at once, a jump in the sound.
        val h = Harness()
        h.load()
        h.openComments()
        assertTrue(h.soundPlaying())
        h.soundAt(21.0)
        h.backToTheReel()
        assertTrue(h.waitFor("Math.abs(${h.v2}.currentTime - 21.3) < 0.05"))
        h.eval("${h.v2}.__time = 23.6; ${h.v2}.__rs = 4; 1")
        assertTrue(h.waitFor("${h.v2}.playbackRate === 0.0625"))
        assertEquals("the sound stopped with the reel far ahead", "false", h.eval("!!${h.sound}.stopped"))
        h.soundAt(23.6)
        assertTrue(h.soundStopped())
        assertEquals("1", h.eval("${h.v2}.playbackRate"))
    }

    @Test
    fun aSecondBackFromTheSameReel_silencesItAgain() {
        // Facebook brings the reel back in the same <video>, which Web Audio takes only once: a
        // second Back heard it from 0 along with the sound (on the phone).
        val h = Harness()
        h.load()
        h.openComments()
        assertTrue(h.soundPlaying())
        h.soundAt(21.0)
        h.backToTheReel()
        h.eval("${h.v2}.__rs = 4; 1")
        h.soundAt(21.3)
        assertTrue(h.soundStopped())
        // The same reel's comments again: its button, the reel playing on screen, then gone.
        h.eval(
            "var v = ${h.v2}; v.style.cssText = 'width:400px;height:700px;display:block'; v.__time = 5;" +
                " var b = document.createElement('div'); b.id = 'comment2'; b.setAttribute('role', 'button');" +
                " b.setAttribute('aria-label', 'Comment on reel by Someone'); v.parentNode.appendChild(b); b.click();" +
                " history.pushState(null, '', '/'); var player = v.parentNode; window.__player = player; player.remove(); 1"
        )
        assertTrue(h.soundPlaying())
        // Back: the same <video> again.
        h.eval(
            "history.pushState(null, '', '/watch/'); document.body.appendChild(window.__player);" +
                " ${h.v2}.__time = 0; ${h.v2}.__rs = 1; ${h.v2}.dispatchEvent(new Event('play')); 1"
        )
        assertEquals("routed through Web Audio once only", "[\"v2\"]", h.json("window.__routed"))
        assertEquals("the reel back wasn't silenced again", "0", h.eval("window.__silencer.gain.value"))
    }

    @Test
    fun back_aReelThatLoadedFarBehindTheSound_takesOverAtOnce() {
        // Facebook took longer to load it than the lead (up to 9 s on the emulator).
        val h = Harness()
        h.load()
        h.openComments()
        assertTrue(h.soundPlaying())
        h.soundAt(21.0)
        h.backToTheReel()
        assertTrue(h.waitFor("Math.abs(${h.v2}.currentTime - 21.3) < 0.05"))
        h.soundAt(24.0)
        h.eval("${h.v2}.__rs = 4; 1")
        assertTrue(h.soundStopped())
        assertEquals("1", h.eval("window.__silencer.gain.value"))
        assertEquals("1", h.eval("${h.v2}.playbackRate"))
    }

    @Test
    fun back_nearTheMp4sEnd_startsTheReelAgainRatherThanAtItsEnd() {
        // Facebook takes a reel at its very end for a finished one.
        val h = Harness()
        h.load()
        h.openComments()
        assertTrue(h.soundPlaying())
        h.soundAt(29.3)
        h.backToTheReel(newTime = 0.4)
        Thread.sleep(300)
        assertEquals("0.4", h.eval("${h.v2}.currentTime"))
    }

    @Test
    fun anotherPageFromTheComments_stopsTheSound() {
        // A commenter's profile, say. The moment at /watch/ on the way back must not stop it.
        val h = Harness()
        h.load()
        h.openComments()
        assertTrue(h.soundPlaying())
        h.eval("history.pushState(null, '', '/watch/'); 1")
        Thread.sleep(400)
        h.eval("history.pushState(null, '', '/'); 1")
        Thread.sleep(1200)
        assertEquals("a moment at /watch/ stopped the sound", "false", h.eval("!!${h.sound}.stopped"))
        h.eval("history.pushState(null, '', '/someone.profile/'); 1")
        assertTrue("the sound went on on another page", h.soundStopped())
    }

    @Test
    fun thePageHidden_pausesTheSound_andShownAgain_playsItOnFromThere() {
        val h = Harness()
        h.load(setup = "window.__vis = 'visible'; Object.defineProperty(document, 'visibilityState', { get: function () { return window.__vis; } });")
        h.openComments()
        assertTrue(h.soundPlaying())
        h.soundAt(20.0)
        h.eval("window.__vis = 'hidden'; document.dispatchEvent(new Event('visibilitychange')); 1")
        assertTrue(h.soundStopped())
        h.eval("window.__now += 30; window.__vis = 'visible'; document.dispatchEvent(new Event('visibilitychange')); 1")
        assertTrue(h.soundPlaying())
        assertEquals(20.0, h.eval("${h.sound}.at").toDouble(), 0.01)
    }

    @Test
    fun theButtonIsFoundByItsIcon_inAnyLanguage() {
        val h = Harness()
        // "Comment" in Hebrew.
        h.load(label = "&#x5EA;&#x5D2;&#x5D5;&#x5D1;&#x5D4;")
        assertEquals("true", h.eval("window.__astryxReelCommentsAudio.hasCommentIcon(document.getElementById('comment'))"))
        h.openComments()
        assertTrue("a translated label lost the sound", h.soundPlaying())
    }

    @Test
    fun otherButtons_aMutedOrPausedReel_andPagesOtherThanReels_makeNoSound() {
        val h = Harness()
        h.load(label = "Share reel by Someone", icon = false)
        h.openComments()
        h.assertNoSound()

        h.load(muted = true)
        h.openComments()
        h.assertNoSound()

        h.load(playing = false)
        h.openComments()
        h.assertNoSound()

        h.load(path = "/someone/posts/123/")
        h.openComments()
        h.assertNoSound()
    }

    @Test
    fun commentsThatDontOpen_leaveNothingWaiting() {
        // The tap reached the button but Facebook drew nothing new: the reel keeps its <video>.
        val h = Harness()
        h.load()
        h.tapComment()
        Thread.sleep(3500)
        h.eval("document.getElementById('reel').remove(); history.pushState(null, '', '/'); 1")
        Thread.sleep(600)
        assertEquals("a sound was heard or left playing", "true",
            h.eval("window.__sources.every(function (s) { return s.stopped && s.out.gain.value === 0; })"))
    }

    @Test
    fun aStreamWithoutItsAudioTrack_playsTheMp4sSound() {
        // The MP4's sound is quieter than the stream's (8.7 dB on the emulator's test reel), but
        // the same.
        val h = Harness()
        h.load(streamAudio = false)
        h.openComments()
        assertTrue(h.soundPlaying())
        assertEquals("[\"$MP4_URL\"]", h.json("window.__fetched"))
    }

    @Test
    fun aSoundThatCantBeFetched_leavesTheCommentsQuiet() {
        val h = Harness()
        h.load(fetchFails = true)
        h.openComments()
        Thread.sleep(600)
        assertEquals("0", h.eval("window.__sources.length"))
        assertEquals(-1.0, h.position(), 0.0)
    }

    @Test
    fun theMessagesLayer_isLeftAlone() {
        val h = Harness()
        h.load(setup = "window.__astryxMessagesLayer = true;")
        h.openComments()
        h.assertNoSound()
        assertEquals("undefined", h.eval("typeof window.__astryxReelCommentsAudio").removeSurrounding("\""))
    }

    private companion object {
        const val REEL_PATH = "/reel/1360854416127071/"
        // As the page's attribute holds it, and as the script reads it.
        const val MP4 = "https://video.fbcdn.example/v/reel.mp4?oh=1&amp;oe=2"
        const val MP4_URL = "https://video.fbcdn.example/v/reel.mp4?oh=1&oe=2"
        // The stream's tracks as data-extra lists them: its audio track's whole file at base_url.
        const val AUDIO_URL = "https://video.fbcdn.example/m412/audio.mp4?oh=3"
        const val VIDEO_TRACK = "{\"mime_type\":\"video/mp4\",\"base_url\":\"https://video.fbcdn.example/m366/video.mp4\"}"
        const val EXTRA = "{\"dash_prefetch_representations\":{\"representations\":[$VIDEO_TRACK," +
            "{\"mime_type\":\"audio/mp4\",\"base_url\":\"$AUDIO_URL\"}]}}"
        const val EXTRA_NO_AUDIO = "{\"dash_prefetch_representations\":{\"representations\":[$VIDEO_TRACK]}}"
    }
}
