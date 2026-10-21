package com.eepiemi.materialbook

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eepiemi.materialbook.data.local.SettingsDataStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The app itself (MainActivity and MaterialbookWebView's PiP effects) entering and leaving real
 * picture-in-picture, on a test page in the app's own WebView (no Facebook login: the page is
 * loaded over whatever the start page was, with a Facebook address). Guards the PiP wiring that
 * the page-script tests (PipFocusModeJsTest...) can't reach: which scripts run on entering and
 * leaving PiP, on which WebView, in which order.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityPipTest {

    private val app = AppHarness()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before
    fun setUp() {
        File(app.context.filesDir, "datastore").deleteRecursively()
        setSettings(pip = true, pinch = true, lockscreenAudio = false)
        app.start()
        assertTrue("the app's WebView never appeared", app.waitFor { app.webViews().isNotEmpty() })
    }

    @After
    fun tearDown() {
        // A failed lock test mustn't leave the screen locked for the tests after it.
        val keyguard = app.context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguard.isKeyguardLocked) unlock(keyguard, InstrumentationRegistry.getArguments().getString("pin"))
        app.finish()
        // Back to the defaults: DataStore keeps its values in memory, deleting the file isn't enough
        // for the tests after these (SettingsDefaultsTest).
        setSettings(pip = false, pinch = false, lockscreenAudio = false)
        File(app.context.filesDir, "datastore").deleteRecursively()
    }

    private fun setSettings(pip: Boolean, pinch: Boolean, lockscreenAudio: Boolean, closeWhenGone: Boolean = true) = runBlocking {
        val settings = SettingsDataStore(app.context)
        settings.setPipEnabled(pip)
        settings.setPinchToZoom(pinch)
        settings.setPipLockscreenAudio(lockscreenAudio)
        settings.setPipCloseWhenVideoGone(closeWhenGone)
    }

    private fun eval(js: String, webView: android.webkit.WebView = app.mainWebView()) = app.eval(js, webView)

    private fun pipActive(webView: android.webkit.WebView = app.mainWebView()) =
        eval("document.body.hasAttribute('data-astryx-pip-active')", webView) == "true"

    private fun loadReelPage(webView: android.webkit.WebView = app.mainWebView(), url: String = REEL_URL) =
        app.loadPage(url, REEL_PAGE, webView)

    // ── entering and leaving ────────────────────────────────────────────────

    @Test
    fun enteringPip_appliesFocusMode_andLeavingRestoresThePage() {
        loadReelPage()
        app.enterPip()
        assertTrue("focus mode never ran", app.waitFor { pipActive() })
        assertEquals("true", eval("document.getElementById('reel').hasAttribute('data-astryx-pip-video')"))
        // The rest of the page is hidden in the PiP window.
        assertEquals("\"none\"", eval("getComputedStyle(document.getElementById('caption')).display"))

        app.leavePip()
        assertTrue("focus mode was never restored", app.waitFor { !pipActive() })
        assertEquals("false", eval("document.getElementById('reel').hasAttribute('data-astryx-pip-video')"))
        assertTrue(eval("getComputedStyle(document.getElementById('caption')).display") != "\"none\"")
    }

    @Test
    fun aPlayingVideo_goesToPipOnHome() {
        // Android 12+ enters PiP by itself on Home once the app has said a video plays.
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        loadReelPage()
        app.reportPlaying()
        app.pressHome()
        assertTrue("a playing video never went to PiP on Home", app.waitFor { app.isInPip() })
        assertTrue("focus mode never ran", app.waitFor { pipActive() })
        app.leavePip()
    }

    @Test
    fun withPipOff_homeLeavesNoPip() {
        setSettings(pip = false, pinch = true, lockscreenAudio = false)
        loadReelPage()
        app.reportPlaying()
        app.pressHome()
        assertTrue("the app never went to the background", app.waitFor { app.isInBackground() })
        Thread.sleep(1500)
        assertFalse("PiP opened with PiP turned off", app.isInPip())
    }

    @Test
    fun leavingPip_keepsTheSamePage() {
        // Leaving PiP must come back to the video the user was on, not reload or navigate.
        loadReelPage()
        eval("window.__samePage = 42; 1")
        app.enterPip()
        assertTrue(app.waitFor { pipActive() })
        app.leavePip()
        assertTrue(app.waitFor { !pipActive() })
        Thread.sleep(1500)
        assertEquals("the page was reloaded or left", "42", eval("window.__samePage"))
        assertEquals("\"$REEL_URL\"", eval("location.href"))
    }

    // ── the effects around focus mode ───────────────────────────────────────

    @Test
    fun keepPlayingGuard_isOnInPipOnly() {
        // Facebook pauses the video on each resize; while in PiP the guard plays it again.
        loadReelPage()
        app.reportPlaying()
        app.enterPip()
        assertTrue("the keep-playing guard never armed", app.waitFor {
            eval("window.__astryxPipActive === true && window.__astryxPipWantsPlay === true") == "true"
        })
        app.leavePip()
        assertTrue("the keep-playing guard stayed on after PiP", app.waitFor {
            eval("window.__astryxPipActive === false && window.__astryxPipWantsPlay === false") == "true"
        })
    }

    @Test
    fun aWrapperFacebookAddsInPip_isKeptVisible() {
        // Facebook's reels scroller re-wraps the video after focus mode ran (v1.32.6: black PiP).
        loadReelPage()
        app.enterPip()
        assertTrue(app.waitFor { pipActive() })
        eval(
            "var v = document.getElementById('reel'); var w = document.createElement('div'); w.id = 'rewrap';" +
                " v.parentElement.insertBefore(w, v); w.appendChild(v); 1"
        )
        assertTrue("the new wrapper was never kept", app.waitFor(3000) {
            eval("document.getElementById('rewrap').hasAttribute('data-astryx-pip-keep')") == "true"
        })
        assertEquals("0", eval(HIDDEN_ANCESTORS_JS.replace("VIDEO", "document.getElementById('reel')")))
        app.leavePip()
    }

    @Test
    fun aPinchZoomedPage_showsItsWholeWidthInPip_andTheZoomComesBack() {
        loadReelPage()
        val webView = app.mainWebView()
        instrumentation.runOnMainSync { webView.zoomBy(2.5f) }
        assertTrue("the page never zoomed", app.waitFor { eval("visualViewport.scale").toFloat() > 2f })

        app.enterPip()
        // The whole page width in the PiP window, as an unzoomed PiP shows it.
        assertTrue("the PiP window still shows a zoomed-in part of the page", app.waitFor {
            eval("window.innerWidth / visualViewport.width").toFloat() < 1.05f
        })

        app.leavePip()
        assertTrue("the zoom never came back", app.waitFor { eval("visualViewport.scale").toFloat() > 2f })
    }

    // ── its video gone ──────────────────────────────────────────────────────

    @Test
    fun aPipVideoTakenOffThePage_closesThePipWindow() {
        // Facebook can drop a long-paused reel and go back to Home: an empty, black PiP window.
        loadReelPage()
        app.enterPip()
        assertTrue(app.waitFor { pipActive() })
        eval("document.getElementById('reel').remove(); 1")
        assertTrue("the empty PiP window stayed open", app.waitFor(10_000) { !app.isInPip() })
        assertTrue("the app came to the front instead of staying in the background", app.isInBackground())
    }

    @Test
    fun withCloseWhenGoneOff_theEmptyPipWindowStays() {
        app.finish()
        setSettings(pip = true, pinch = true, lockscreenAudio = false, closeWhenGone = false)
        app.start()
        assertTrue(app.waitFor { app.webViews().isNotEmpty() })
        loadReelPage()
        app.enterPip()
        assertTrue(app.waitFor { pipActive() })
        eval("document.getElementById('reel').remove(); 1")
        Thread.sleep(6000)
        assertTrue("the PiP window closed with the setting off", app.isInPip())
        app.leavePip()
    }

    // ── which WebView ───────────────────────────────────────────────────────

    @Test
    fun pipFromTheMessagesLayer_actsOnTheLayer() {
        // While the layer is open, PiP follows its page (PipTarget), not the main view's.
        loadReelPage()
        eval("window.AstryxPage.postMessage(JSON.stringify({ call: 'layerUrl', url: 'https://www.facebook.com/stories/create' })); 1")
        assertTrue("the Messages layer never opened", app.waitFor(15_000) { app.layerWebView() != null })
        val layer = app.layerWebView()!!
        loadReelPage(layer, LAYER_URL)

        app.enterPip()
        assertTrue("focus mode never ran in the layer", app.waitFor { pipActive(layer) })
        assertFalse("focus mode ran in the main view under the layer", pipActive())
        app.leavePip()
        assertTrue("focus mode was never restored in the layer", app.waitFor { !pipActive(layer) })
    }

    @Test
    fun afterARendererCrash_pipWorksOnTheNewWebView() {
        // A lost renderer replaces the WebView (RendererRecovery); the PiP effects must follow it.
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        loadReelPage()
        val first = app.mainWebView()
        instrumentation.runOnMainSync { first.webViewRenderProcess?.terminate() }
        assertTrue("the WebView was never replaced", app.waitFor(20_000) {
            app.webViews().firstOrNull()?.let { it !== first } == true
        })
        val second = app.mainWebView()
        assertNotSame(first, second)
        Thread.sleep(3000) // the replacement reloads its page first
        loadReelPage(second)

        app.enterPip()
        assertTrue("focus mode never ran on the new WebView", app.waitFor { pipActive(second) })
        app.leavePip()
        assertTrue(app.waitFor { !pipActive(second) })
    }

    // ── lock-screen audio ───────────────────────────────────────────────────

    @Test
    fun lockingInPip_andUnlocking_keepsThePipWindowAndThePage() {
        // v1.31.x: after a lock in PiP the window went black and the video was gone on unlock.
        val keyguard = app.context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val pin = InstrumentationRegistry.getArguments().getString("pin")
        assumeTrue("a screen lock PIN is set: pass it with -e pin", !keyguard.isDeviceSecure || pin != null)
        setSettings(pip = true, pinch = true, lockscreenAudio = true)
        loadReelPage()
        eval("window.__samePage = 7; 1")
        app.reportPlaying()
        app.enterPip()
        assertTrue(app.waitFor { pipActive() })
        assertTrue("the hand-off was never armed", app.waitFor { eval("!!window.__astryxHandoffListener") == "true" })

        shell("input keyevent KEYCODE_SLEEP")
        Thread.sleep(3000)
        unlock(keyguard, pin)
        assertTrue("the screen never unlocked", app.waitFor(15_000) { !keyguard.isKeyguardLocked })
        Thread.sleep(2000)

        assertTrue("PiP closed over the lock", app.isInPip())
        assertTrue("focus mode was lost over the lock", pipActive())
        assertEquals("the page was reloaded or left over the lock", "7", eval("window.__samePage"))
        assertEquals(
            "the PiP video has hidden ancestors after the lock (a black PiP window)", "0",
            eval(HIDDEN_ANCESTORS_JS.replace("VIDEO", "document.getElementById('reel')"))
        )
        assertEquals("the page's video stayed muted after the hand-back", "false", eval("document.getElementById('reel').muted"))
        app.leavePip()
    }

    // Wakes the screen and unlocks it: a swipe up and the PIN when it has one.
    private fun unlock(keyguard: KeyguardManager, pin: String?) {
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        Thread.sleep(1000)
        if (!keyguard.isKeyguardLocked || pin == null) return
        val metrics = app.context.resources.displayMetrics
        val x = metrics.widthPixels / 2
        shell("input swipe $x ${metrics.heightPixels * 3 / 4} $x ${metrics.heightPixels / 6} 200")
        Thread.sleep(1000)
        shell("input text $pin")
        shell("input keyevent KEYCODE_ENTER")
    }

    // Waits for the command to finish (executeShellCommand itself returns at once).
    private fun shell(command: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { it.readBytes() }
    }

    companion object {
        /** How many of VIDEO's ancestors are hidden (0 when the video can show). */
        const val HIDDEN_ANCESTORS_JS = "(function () { var n = 0; for (var e = VIDEO; e && e !== document.body;" +
            " e = e.parentElement) if (getComputedStyle(e).display === 'none') n++; return n; })()"

        private const val REEL_URL = "https://m.facebook.com/reel/123/"
        private const val LAYER_URL = "https://www.facebook.com/stories/create/"

        // A reel-like page: one large video inside a few boxes, as the mobile site's player
        // (MVideo, with the file in data-video-url, which the lock-screen hand-off reads), and
        // something else beside it.
        private const val REEL_PAGE =
            "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'></head>" +
                "<body style='margin:0'><div id='scroller'><div id='slot' data-mcomponent='MVideo'" +
                " data-video-url='https://video.example.test/reel.mp4'>" +
                "<video id='reel' playsinline style='display:block;width:100%;height:600px;background:#123'></video>" +
                "</div></div><div id='caption'>caption</div>" +
                "<script>window.__testPage = true;</script></body></html>"
    }
}
