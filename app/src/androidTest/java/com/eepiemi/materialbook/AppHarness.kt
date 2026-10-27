package com.eepiemi.materialbook

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Rational
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Drives the whole app for app-level tests: starts MainActivity with an intent (as a launcher, a
 * share or a link would), finds its WebViews (the main view first, then the Messages layer's) and
 * runs JavaScript in them, enters and leaves picture-in-picture.
 */
class AppHarness {

    val context: Context = ApplicationProvider.getApplicationContext()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /**
     * Starts (or, as Android does for this singleTask activity, brings back) MainActivity with
     * [intent] and waits until it's on screen.
     */
    fun start(intent: Intent = Intent(context, MainActivity::class.java)) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue("MainActivity never came up", waitFor(15_000) { activity()?.let { !it.isFinishing } == true })
    }

    private fun anyMainActivity(): Boolean {
        var any = false
        instrumentation.runOnMainSync {
            val registry = ActivityLifecycleMonitorRegistry.getInstance()
            any = listOf(Stage.PRE_ON_CREATE, Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED, Stage.STOPPED)
                .any { stage -> registry.getActivitiesInStage(stage).any { it is MainActivity } }
        }
        return any
    }

    /**
     * Finishes every activity the test left and waits until they're destroyed, so the next test
     * starts the app afresh (a finishing activity still found by [activity] handed the next test
     * the old WebView).
     */
    fun finish() {
        instrumentation.runOnMainSync {
            for (stage in listOf(Stage.RESUMED, Stage.PAUSED, Stage.STOPPED, Stage.STARTED, Stage.CREATED)) {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage).forEach { it.finish() }
            }
        }
        waitFor(10_000) { !anyMainActivity() }
    }

    fun activity(): MainActivity? {
        var found: Activity? = null
        instrumentation.runOnMainSync {
            val registry = ActivityLifecycleMonitorRegistry.getInstance()
            found = (registry.getActivitiesInStage(Stage.RESUMED) + registry.getActivitiesInStage(Stage.PAUSED))
                .filter { it is MainActivity }.let { list -> list.firstOrNull { !it.isFinishing } ?: list.firstOrNull() }
        }
        return found as MainActivity?
    }

    fun <T> onActivity(block: (MainActivity) -> T): T {
        val activity = activity() ?: error("no MainActivity")
        var result: T? = null
        instrumentation.runOnMainSync { result = block(activity) }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    /** The app's WebViews in drawing order: the main view, then the Messages layer when open. */
    fun webViews(): List<WebView> = onActivity { activity ->
        val found = mutableListOf<WebView>()
        fun walk(v: View) {
            if (v is WebView) found += v
            else if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(activity.window.decorView)
        found
    }

    fun mainWebView(): WebView = webViews().first()

    /**
     * The Messages layer is on screen: one kept out of sight behind a reel or post it opened
     * (MessagesLayerState.hidden) doesn't count.
     */
    fun layerOpen(): Boolean = webViews().drop(1).any { webView -> onActivity { webView.visibility == View.VISIBLE } }

    /**
     * A real touch at the middle of the element [js] scrolls into view and gives the box of (CSS px
     * in the visual viewport, and that viewport's width: "left,top,width,height,viewportWidth"; ""
     * while there's none): a link that opens a new window (target=_blank, as a shared reel's)
     * ignores a click from a script. False when there's nothing to tap.
     */
    fun tapIn(webView: WebView, js: String): Boolean {
        fun box() = eval(js, webView).trim('"').split(',').mapNotNull { it.toFloatOrNull() }
        if (box().size < 5) return false
        // Measured again once the scroll has settled.
        Thread.sleep(800)
        val box = box()
        if (box.size < 5 || box[4] <= 0f) return false
        val (x, y) = onActivity {
            val at = IntArray(2).also { webView.getLocationOnScreen(it) }
            val scale = webView.width / box[4]
            Pair(at[0] + (box[0] + box[2] / 2) * scale, at[1] + (box[1] + box[3] / 2) * scale)
        }
        val downAt = SystemClock.uptimeMillis()
        for ((action, time) in listOf(MotionEvent.ACTION_DOWN to downAt, MotionEvent.ACTION_UP to downAt + 80)) {
            if (action == MotionEvent.ACTION_UP) Thread.sleep(80)
            val event = MotionEvent.obtain(downAt, time, action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.uiAutomation.injectInputEvent(event, true)
            event.recycle()
        }
        return true
    }

    /** The Messages layer's WebView, once it's open. */
    fun layerWebView(): WebView? = webViews().getOrNull(1)

    /**
     * Loads [html] into [webView] as if it came from [url] (a Facebook address, so the app's
     * scripts and effects treat it as Facebook's), and waits for the app's scripts to have run.
     * With [asHistory] the WebView also reports [url] as its address (WebView.url), as the app's
     * Back handling reads it; a reload then fetches the real page.
     */
    fun loadPage(url: String, html: String, webView: WebView = mainWebView(), asHistory: Boolean = false) {
        // Whatever the WebView was loading (the start page, a page the app opened) finishes
        // first: a load still under way replaced the test page with the real one at its address.
        waitFor(20_000) { onActivity { webView.progress } == 100 }
        Thread.sleep(1000)
        var loaded = false
        for (attempt in 1..3) {
            instrumentation.runOnMainSync {
                webView.loadDataWithBaseURL(url, html, "text/html", "UTF-8", url.takeIf { asHistory })
            }
            loaded = waitFor {
                eval("location.href === '$url' && document.readyState === 'complete' && !!window.__testPage", webView) == "true"
            }
            if (loaded) break
            waitFor(20_000) { onActivity { webView.progress } == 100 }
        }
        assertTrue("the test page never loaded (the WebView shows ${eval("location.href + ' ' + document.readyState", webView)})", loaded)
        Thread.sleep(1500) // the app's scripts run once the page has finished loading
    }

    /** The page tells the app a video is playing, as pip_video_detector.js does for a real one. */
    fun reportPlaying(webView: WebView = mainWebView()) {
        eval(
            "window.AstryxPage.postMessage(JSON.stringify({ call: 'videoState', playing: true, width: 720, height: 1280, why: '' })); 1",
            webView
        )
        Thread.sleep(500)
    }

    /** MainActivity is alive but in the background (Home, or Back leaving the app). */
    fun isInBackground(): Boolean {
        var stopped = false
        instrumentation.runOnMainSync {
            stopped = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.STOPPED)
                .any { it is MainActivity && !it.isFinishing }
        }
        return stopped
    }

    fun pressBack() {
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
    }

    fun url(webView: WebView): String = onActivity { webView.url.orEmpty() }

    /** [js]'s value as evaluateJavascript gives it (JSON: strings quoted, "null" for none). */
    fun eval(js: String, webView: WebView = mainWebView()): String {
        var result: String? = null
        val latch = CountDownLatch(1)
        instrumentation.runOnMainSync { webView.evaluateJavascript(js) { result = it; latch.countDown() } }
        assertTrue("evaluateJavascript never returned", latch.await(20, TimeUnit.SECONDS))
        return result ?: "null"
    }

    fun waitFor(ms: Long = 10_000, check: () -> Boolean): Boolean {
        val until = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < until) {
            if (runCatching(check).getOrDefault(false)) return true
            Thread.sleep(200)
        }
        return false
    }

    fun isInPip(): Boolean = activity()?.let { a -> onActivity { a.isInPictureInPictureMode } } ?: false

    fun enterPip() {
        onActivity { it.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(9, 16)).build()) }
        assertTrue("never entered PiP", waitFor { isInPip() })
    }

    fun pressHome() {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME")
        ).use { it.readBytes() }
    }

    // The app has been told it's in PiP (onPictureInPictureModeChanged, its private isInPipMode),
    // which can come a second after isInPictureInPictureMode turns true.
    private fun pipDelivered(): Boolean = activity()?.let { a ->
        onActivity {
            val field = MainActivity::class.java.getDeclaredField("isInPipMode\$delegate").apply { isAccessible = true }
            (field.get(a) as androidx.compose.runtime.State<*>).value == true
        }
    } ?: false

    // Back to full screen the way a tap on the PiP window's expand does: the activity to the front.
    // Only once the app has been told it's in PiP: asked 0.2 s after Home, while Android was still
    // entering PiP, the window stayed in PiP for good and the app never heard of PiP at all (CI's
    // logcat of aReelBufferingRightBeforeHome_stillGoesToPip, 2026-10-27; it failed so about 1 in
    // 3 runs since 2026-10-24). On a busy CI emulator the first request now and then left the window
    // in PiP too: asked once more then.
    fun leavePip() {
        waitFor(5_000) { pipDelivered() }
        val toFront = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        context.startActivity(toFront)
        if (waitFor { !isInPip() }) return
        context.startActivity(toFront)
        assertTrue("never left PiP (the app was told it's in PiP: ${pipDelivered()})", waitFor(15_000) { !isInPip() })
    }
}
