package com.astryx.book.ui.screens
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * reel_comments_audio.js: a reel's sound goes on while its comments are open, and Back moves the
 * reel to where the sound got to. Facebook's moves are the ones read on the emulator (2026-10-31):
 * the comment button opens the comments at "/" (pushState) and removes the reel's <video>; Back
 * passes through /watch/, where the new reel's 'play' fires, then lands on the reel's path (its
 * 'playing' fires there) and gets its query back a moment later. Media is faked: the reel's
 * paused, muted and currentTime, and the script's <audio> (window.__audios).
 */
@RunWith(AndroidJUnit4::class)
class ReelCommentsAudioJsTest {

    private class Harness {
        private val page = PageFixture(width = 1080, height = 2000)

        fun eval(js: String) = page.eval(js)

        fun waitFor(js: String, ms: Long = 5000) = page.waitFor(ms) { eval(js) == "true" }

        // A reel page: the reel playing at [time] s in its player (data-video-url), and its comment
        // button with Facebook's icon and [label].
        fun load(
            path: String = REEL_PATH,
            label: String = "Comment on reel by Someone, 107 comments",
            icon: Boolean = true,
            muted: Boolean = false,
            playing: Boolean = true,
            time: Double = 12.5,
            setup: String = "",
        ) {
            page.load(
                "https://m.facebook.com$path",
                "<html><head><meta name='viewport' content='width=device-width'></head><body style='margin:0'>" +
                    "<div id='reel'><div data-mcomponent='MVideo' data-video-url='$MP4'>" +
                    "<video id='v1' style='width:400px;height:700px'></video></div>" +
                    "<div id='comment' role='button' aria-label='$label'>" +
                    "<span>${if (icon) "&#xF0379;" else "x"}</span><span>107</span></div></div></body></html>"
            )
            eval(
                """
                window.__audios = [];
                window.Audio = function () {
                  var a = { paused: true, currentTime: 0, duration: NaN, src: '', listeners: {},
                    addEventListener: function (n, f) { this.listeners[n] = f; },
                    play: function () { this.paused = false; },
                    pause: function () { this.paused = true; },
                    removeAttribute: function (n) { if (n === 'src') this.src = ''; },
                    load: function () {} };
                  window.__audios.push(a);
                  return a;
                };
                window.__fakeVideo = function (v, time, playing, muted) {
                  v.__time = time; v.__paused = !playing; v.muted = muted;
                  Object.defineProperty(v, 'paused', { get: function () { return this.__paused; } });
                  Object.defineProperty(v, 'duration', { get: function () { return 30; } });
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

        // Facebook's comments opening after the tap: the reel's <video> goes, the address is "/".
        fun openComments() {
            eval("document.getElementById('comment').click(); history.pushState(null, '', '/');" +
                " document.getElementById('reel').remove(); 1")
        }

        fun audio(i: Int = 0) = "window.__audios[$i]"

        fun audioPlaying() = waitFor("!!${audio()} && !${audio()}.paused && ${audio()}.src === '$MP4_URL'")

        // Back to the reel as Facebook does it: /watch/ (the new reel's 'play'), then the reel's
        // path ('playing'), then its query.
        fun backToTheReel(newTime: Double = 0.0) {
            eval(
                "history.pushState(null, '', '/watch/');" +
                    " var d = document.createElement('div'); d.setAttribute('data-video-url', '$MP4');" +
                    " d.innerHTML = '<video id=\"v2\"></video>'; document.body.appendChild(d);" +
                    " var v = document.getElementById('v2'); __fakeVideo(v, $newTime, true, false);" +
                    " v.dispatchEvent(new Event('play')); 1"
            )
            Thread.sleep(300)
            eval("history.replaceState(null, '', '$REEL_PATH'); document.getElementById('v2').dispatchEvent(new Event('playing'));" +
                " history.replaceState(null, '', '$REEL_PATH?referral_source=external_link'); 1")
        }

        fun assertNoAudio() {
            Thread.sleep(600)
            assertEquals("no <audio> was to be made", "0", eval("window.__audios.length"))
        }
    }

    @Test
    fun theCommentButton_keepsTheReelsSoundPlaying_fromWhereItStopped() {
        val h = Harness()
        h.load(time = 12.5)
        h.openComments()
        assertTrue("the reel's MP4 never played (${h.eval("JSON.stringify(window.__audios)")})", h.audioPlaying())
        assertEquals("true", h.eval("${h.audio()}.loop"))
        // Facebook's MP4 isn't loaded yet when it starts: the reel's position is set once it is.
        h.eval("${h.audio()}.duration = 30; ${h.audio()}.listeners.loadedmetadata(); 1")
        assertEquals("12.5", h.eval("${h.audio()}.currentTime"))
        assertEquals("true", h.eval("window.__astryxReelCommentsAudio.audio() === ${h.audio()}"))
    }

    @Test
    fun back_movesTheReelToWhereTheSoundGotTo_andStopsTheSound() {
        val h = Harness()
        h.load()
        h.openComments()
        assertTrue(h.audioPlaying())
        h.eval("${h.audio()}.currentTime = 21; 1")
        h.backToTheReel()
        assertTrue("the reel that came back wasn't moved to the sound's 21 s (${h.eval("document.getElementById('v2').currentTime")})",
            h.waitFor("document.getElementById('v2').currentTime === 21"))
        assertTrue("the sound went on with the reel back", h.waitFor("${h.audio()}.paused && ${h.audio()}.src === ''"))
        assertEquals("null", h.eval("window.__astryxReelCommentsAudio.audio()"))
    }

    @Test
    fun back_atTheMp4sEnd_startsTheReelAgain() {
        // Facebook takes a reel at its very end for a finished one.
        val h = Harness()
        h.load()
        h.openComments()
        assertTrue(h.audioPlaying())
        h.eval("${h.audio()}.currentTime = 29.8; 1")
        h.backToTheReel(newTime = 0.4)
        assertTrue(h.waitFor("${h.audio()}.paused"))
        assertEquals("0", h.eval("document.getElementById('v2').currentTime"))
    }

    @Test
    fun anotherPageFromTheComments_stopsTheSound() {
        // A commenter's profile, say. The moment at /watch/ on the way back must not stop it.
        val h = Harness()
        h.load()
        h.openComments()
        assertTrue(h.audioPlaying())
        h.eval("history.pushState(null, '', '/watch/'); 1")
        Thread.sleep(400)
        h.eval("history.pushState(null, '', '/'); 1")
        Thread.sleep(1200)
        assertEquals("a moment at /watch/ stopped the sound", "false", h.eval("${h.audio()}.paused"))
        h.eval("history.pushState(null, '', '/someone.profile/'); 1")
        assertTrue("the sound went on on another page", h.waitFor("${h.audio()}.paused && ${h.audio()}.src === ''"))
    }

    @Test
    fun thePageHidden_pausesTheSound_andShownAgain_playsIt() {
        val h = Harness()
        h.load(setup = "window.__vis = 'visible'; Object.defineProperty(document, 'visibilityState', { get: function () { return window.__vis; } });")
        h.openComments()
        assertTrue(h.audioPlaying())
        h.eval("window.__vis = 'hidden'; document.dispatchEvent(new Event('visibilitychange')); 1")
        assertEquals("true", h.eval("${h.audio()}.paused"))
        h.eval("window.__vis = 'visible'; document.dispatchEvent(new Event('visibilitychange')); 1")
        assertEquals("false", h.eval("${h.audio()}.paused"))
    }

    @Test
    fun theButtonIsFoundByItsIcon_inAnyLanguage() {
        val h = Harness()
        // "Comment" in Hebrew.
        h.load(label = "&#x5EA;&#x5D2;&#x5D5;&#x5D1;&#x5D4;")
        assertEquals("true", h.eval("window.__astryxReelCommentsAudio.hasCommentIcon(document.getElementById('comment'))"))
        h.openComments()
        assertTrue("a translated label lost the sound", h.audioPlaying())
    }

    @Test
    fun otherButtons_aMutedOrPausedReel_andPagesOtherThanReels_makeNoSound() {
        val h = Harness()
        h.load(label = "Share reel by Someone", icon = false)
        h.openComments()
        h.assertNoAudio()

        h.load(muted = true)
        h.openComments()
        h.assertNoAudio()

        h.load(playing = false)
        h.openComments()
        h.assertNoAudio()

        h.load(path = "/someone/posts/123/")
        h.openComments()
        h.assertNoAudio()
    }

    @Test
    fun commentsThatDontOpen_leaveNothingWaiting() {
        // The tap reached the button but Facebook drew nothing new: the reel keeps its <video>.
        val h = Harness()
        h.load()
        h.eval("document.getElementById('comment').click(); 1")
        Thread.sleep(3500)
        h.eval("document.getElementById('reel').remove(); history.pushState(null, '', '/'); 1")
        h.assertNoAudio()
    }

    @Test
    fun theMessagesLayer_isLeftAlone() {
        val h = Harness()
        h.load(setup = "window.__astryxMessagesLayer = true;")
        h.openComments()
        h.assertNoAudio()
        assertEquals("undefined", h.eval("typeof window.__astryxReelCommentsAudio").removeSurrounding("\""))
    }

    private companion object {
        const val REEL_PATH = "/reel/1360854416127071/"
        // As the page's attribute holds it, and as the script reads it.
        const val MP4 = "https://video.fbcdn.example/v/reel.mp4?oh=1&amp;oe=2"
        const val MP4_URL = "https://video.fbcdn.example/v/reel.mp4?oh=1&oe=2"
    }
}
