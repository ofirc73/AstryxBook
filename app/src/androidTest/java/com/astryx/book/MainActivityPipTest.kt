package com.astryx.book
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eepiemi.materialbook.data.local.SettingsDataStore
import com.astryx.book.ui.screens.PIP_EXIT_SETTLE_MS
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

    private fun setSettings(pip: Boolean, pinch: Boolean, lockscreenAudio: Boolean) = runBlocking {
        val settings = SettingsDataStore(app.context)
        settings.setPipEnabled(pip)
        settings.setPinchToZoom(pinch)
        settings.setPipLockscreenAudio(lockscreenAudio)
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
    fun comingBackToTheApp_dropsTheViewportCookiePipLeft() {
        // In PiP Facebook can write the PiP window's size into a host-only "vpd" cookie on
        // m.facebook.com, beside its own .facebook.com one, and later pages were laid out PiP-narrow.
        // The app drops it when PiP ends and whenever it comes back out of PiP; this checks the
        // second (a PiP window entered and left within a moment can end with no PiP callback, and
        // that's also how a test's PiP went, now and then).
        val cookies = CookieManager.getInstance()
        val vpds = { cookies.getCookie("https://m.facebook.com").orEmpty().split(';').map { it.trim() }.filter { it.startsWith("vpd=") } }
        app.pressHome()
        assertTrue("the app never went to the background", app.waitFor { app.isInBackground() })
        instrumentation.runOnMainSync {
            cookies.setCookie("https://m.facebook.com", "vpd=v1%3B841x411x2.625; Domain=.facebook.com; Path=/; Secure")
            cookies.setCookie("https://m.facebook.com", "vpd=v1%3B227x130x2.625; Path=/; Secure")
        }
        assertEquals("the test's two vpd cookies weren't both set", 2, vpds().size)

        app.leavePip() // the activity to the front, as the PiP window's expand does
        assertTrue("the PiP window's vpd cookie outlived the app's return (${vpds()})", app.waitFor {
            vpds() == listOf("vpd=v1%3B841x411x2.625")
        })
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
    fun aReelBufferingRightBeforeHome_stillGoesToPip() {
        // The daily live check's reel stalled for data just before Home (issues #59, #61, #66),
        // and the app had turned auto-enter off.
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        loadReelPage()
        reelPlays()

        reelVideo("{ readyState: 2 }", "waiting")
        Thread.sleep(500) // the detector checks again meanwhile (every 300 ms)
        app.pressHome()

        assertTrue("a reel buffering for a moment never went to PiP on Home", app.waitFor { app.isInPip() })
        app.leavePip()
    }

    @Test
    fun aPausedReel_homeLeavesNoPip() {
        loadReelPage()
        reelPlays()

        reelVideo("{ paused: true }", "pause")
        Thread.sleep(500)
        app.pressHome()

        assertTrue("the app never went to the background", app.waitFor { app.isInBackground() })
        Thread.sleep(1500)
        assertFalse("a paused reel went to PiP on Home", app.isInPip())
    }

    // The test reel's playback, faked on its <video> (it has no file): [state] (a JS object
    // literal) changes it, then [event] fires so pip_video_detector.js reports at once.
    private fun reelVideo(state: String, event: String) {
        eval(
            "(function (v) { if (!v.__s) { v.__s = { paused: true, ended: false, readyState: 0, currentTime: 0," +
                " muted: false, volume: 1, videoWidth: 720, videoHeight: 1280 };" +
                " Object.keys(v.__s).forEach(function (k) { Object.defineProperty(v, k," +
                " { get: function () { return this.__s[k]; }, configurable: true }); }); }" +
                " Object.assign(v.__s, $state); v.dispatchEvent(new Event('$event')); })" +
                "(document.getElementById('reel')); 1"
        )
    }

    // The test reel plays, and the app has heard it from pip_video_detector.js.
    private fun reelPlays() {
        reelVideo("{ paused: false, readyState: 4, currentTime: 1 }", "playing")
        assertTrue("pip_video_detector.js never found the playing reel", app.waitFor {
            eval("window.__astryxLastActiveVideo === document.getElementById('reel')") == "true"
        })
        Thread.sleep(500) // the report reaching MainActivity's PiP params
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

    // ── resizing the window ─────────────────────────────────────────────────

    @Test
    fun aPinchedPipWindow_keepsItsSize_forAPortraitReel() = pinchedWindowKeepsItsSize(720, 1280)

    @Test
    fun aPinchedPipWindow_keepsItsSize_forALandscapeVideo() = pinchedWindowKeepsItsSize(1280, 720)

    // The user resizes the PiP window; the Play/Pause updates the app then sends the window (the
    // page reporting a pause and a play) must leave the user's size alone.
    private fun pinchedWindowKeepsItsSize(videoWidth: Int, videoHeight: Int) {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        loadReelPage()
        reportVideo(true, videoWidth, videoHeight)
        app.pressHome()
        assertTrue("a playing video never went to PiP on Home", app.waitFor { app.isInPip() })
        assertTrue(app.waitFor { pipActive() })
        Thread.sleep(1000)
        val entered = pipWindow()
        assertEquals("the window doesn't have the video's shape", videoWidth > videoHeight, entered.width() > entered.height())

        resizePipWindow(entered)
        Thread.sleep(1000)
        val pinched = pipWindow()

        reportVideo(false, 0, 0)
        Thread.sleep(800)
        reportVideo(true, videoWidth, videoHeight)
        Thread.sleep(2000)
        val after = pipWindow()
        assertEquals("the window lost the size it was pinched to", pinched.width(), after.width())
        assertEquals("the window lost the size it was pinched to", pinched.height(), after.height())
        app.leavePip()
    }

    @Test
    fun aZoomThePageTookInPip_isUndoneOnLeavingIt() {
        // Resizing the PiP window of one of Facebook's video pages (shown zoomed out there, at
        // its full-screen width) left the page at scale 1 in the window, and Chromium kept the
        // window's visible width on the way out: the page came back zoomed in (x1.94, Galaxy A56).
        // Same end state here: the page zoomed while in PiP.
        loadReelPage()
        app.enterPip()
        assertTrue(app.waitFor { pipActive() })
        Thread.sleep(1000)
        val webView = app.mainWebView()
        instrumentation.runOnMainSync { webView.zoomBy(2f) }
        assertTrue("the page never zoomed in PiP", app.waitFor { eval("visualViewport.scale").toFloat() > 1.5f })

        app.leavePip()
        Thread.sleep(PIP_EXIT_SETTLE_MS + 1500)
        val ratio = eval("window.innerWidth / visualViewport.width").toFloat()
        assertTrue("the page came back zoomed x$ratio", ratio in 0.95f..1.05f)
    }

    @Test
    fun aResizedPipWindow_showsThePageZoomedOutAgain() {
        // The zoom a resize left the page at in PiP showed for a moment after leaving PiP, until
        // the reset above: the page is zoomed out again after each resize instead.
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        loadReelPage()
        reportVideo(true, 720, 1280)
        app.pressHome()
        assertTrue("a playing video never went to PiP on Home", app.waitFor { app.isInPip() })
        assertTrue(app.waitFor { pipActive() })
        Thread.sleep(1000)
        val webView = app.mainWebView()
        instrumentation.runOnMainSync { webView.zoomBy(2f) }
        assertTrue("the page never zoomed in PiP", app.waitFor { eval("visualViewport.scale").toFloat() > 1.5f })

        resizePipWindow(pipWindow())
        assertTrue("the resized PiP window still shows the page zoomed in", app.waitFor(3000) {
            eval("window.innerWidth / visualViewport.width").toFloat() < 1.05f
        })
        app.leavePip()
    }

    private fun reportVideo(playing: Boolean, width: Int, height: Int) {
        eval(
            "window.AstryxPage.postMessage(JSON.stringify({ call: 'videoState', playing: $playing," +
                " width: $width, height: $height, why: '' })); 1"
        )
        Thread.sleep(300)
    }

    // The PiP window's bounds on the screen.
    private fun pipWindow(): android.graphics.Rect = app.onActivity {
        val view = it.window.decorView
        val at = IntArray(2).also(view::getLocationOnScreen)
        android.graphics.Rect(at[0], at[1], at[0] + view.width, at[1] + view.height)
    }

    // Pinches the PiP window smaller, or bigger when it's already as small as it gets (Android keeps
    // the size the last test left). Leaves PiP and skips the test when the window doesn't resize.
    private fun resizePipWindow(window: android.graphics.Rect) {
        app.pinch(window, 0.6f)
        if (app.waitFor(3000) { pipWindow().width() < window.width() * 0.9 }) return
        app.pinch(window, 1.6f)
        if (app.waitFor(3000) { pipWindow().width() > window.width() * 1.1 }) return
        app.leavePip()
        assumeTrue("this Android's PiP doesn't resize on an injected pinch", false)
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
        eval("window.__beforePip = true; sessionStorage.setItem('sharedprefs.web.last_device_info', '1'); 1")
        app.enterPip()
        assertTrue(app.waitFor { pipActive() })
        eval("document.getElementById('reel').remove(); 1")
        assertTrue("the empty PiP window stayed open", app.waitFor(10_000) { !app.isInPip() })
        assertTrue("the app came to the front instead of staying in the background", app.isInBackground())
        assertPageReloadedOnReturn()
    }

    // Back in front after closing an empty PiP window, the page is reloaded for the full window:
    // what Facebook loaded meanwhile was laid out for the PiP window (a third of the screen).
    private fun assertPageReloadedOnReturn() {
        app.leavePip()
        assertTrue("the page laid out for the PiP window was never reloaded", app.waitFor(10_000) {
            eval("window.__beforePip === undefined && document.readyState === 'complete'") == "true"
        })
        // Facebook's client then sends the screen size again.
        assertEquals("null", eval("sessionStorage.getItem('sharedprefs.web.last_device_info')"))
    }

    @Test
    fun theAppStartedAgainInsideThePipWindow_closesIt() {
        // Android restarts an app whose process it killed in PiP inside the same PiP window: the
        // fresh start page showed whole in the window (v1.35.1 on a Galaxy A56). recreate() starts
        // the activity again in the window the same way.
        loadReelPage()
        app.enterPip()
        assertTrue(app.waitFor { pipActive() })
        app.onActivity { it.recreate() }
        assertTrue("the PiP window stayed open over the restarted app", app.waitFor(10_000) { !app.isInPip() })
        assertTrue("the app came to the front instead of staying in the background", app.waitFor { app.isInBackground() })
        // The restarted app's page loaded at the PiP window's size.
        app.leavePip()
        val reloads = app.waitFor(10_000) { eval("document.readyState") == "\"complete\"" }
        assertTrue(reloads)
    }

    @Test
    fun aNewPageLoadedInPip_closesThePipWindow() {
        // Facebook reloads a page left alone for an hour or so as the screen comes back on: the
        // new page has no PiP video, and the window went black (v1.34.0 on a Galaxy A56).
        loadReelPage()
        app.enterPip()
        assertTrue(app.waitFor { pipActive() })
        // Not app.loadPage: it waits on the page through evaluateJavascript, which doesn't answer
        // once the app is in the background. The page takes half a second to load, as a page from
        // the network does: a page that loads within a frame is never seen loading by the app
        // (its loading state goes back to finished before the next frame).
        val slowPage = REEL_PAGE.replace(
            "</body>", "<script>var t = Date.now(); while (Date.now() - t < 500) {}</script></body>"
        )
        val webView = app.mainWebView()
        instrumentation.runOnMainSync {
            webView.loadDataWithBaseURL(REEL_URL, slowPage, "text/html", "UTF-8", null)
        }
        assertTrue("the PiP window stayed open over a new page", app.waitFor(10_000) { !app.isInPip() })
        assertTrue("the app came to the front instead of staying in the background", app.isInBackground())
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

    // Wakes the screen and unlocks it: a swipe up and the PIN when it has one. Tried a few times: a
    // single try that missed (the PIN pad not up yet) left the emulator locked for every test after
    // this one, the tear-down's unlock missing the same way (API 36 image, 2026-10-24). The swipe
    // goes through the touchscreen source: a plain `input swipe` at times didn't bring up the pad.
    private fun unlock(keyguard: KeyguardManager, pin: String?) {
        val metrics = app.context.resources.displayMetrics
        val x = metrics.widthPixels / 2
        repeat(3) {
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
            Thread.sleep(1000)
            if (!keyguard.isKeyguardLocked || pin == null) return
            shell("input touchscreen swipe $x ${metrics.heightPixels * 3 / 4} $x ${metrics.heightPixels / 6} 300")
            Thread.sleep(1500)
            shell("input text $pin")
            shell("input keyevent KEYCODE_ENTER")
            if (app.waitFor(4000) { !keyguard.isKeyguardLocked }) return
        }
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
