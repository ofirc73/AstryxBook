package com.astryx.book
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.util.Rational
import android.view.InputDevice
import android.view.MotionEvent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.assertTrue
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.eepiemi.materialbook.MainActivity

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

    /**
     * What's on screen, for a failure message: the app's activities by stage (finishing or not,
     * with the window's focus or not) and the app whose window the checks' text lookups read.
     */
    fun screenState(): String {
        var activities = ""
        instrumentation.runOnMainSync {
            val registry = ActivityLifecycleMonitorRegistry.getInstance()
            activities = Stage.values().mapNotNull { stage ->
                registry.getActivitiesInStage(stage).filter { it is MainActivity }.takeIf { it.isNotEmpty() }
                    ?.joinToString(",", "$stage=") {
                        (if (it.isFinishing) "finishing" else "live") + (if (it.hasWindowFocus()) "+focus" else "")
                    }
            }.joinToString(" ")
        }
        val active = runCatching { instrumentation.uiAutomation.rootInActiveWindow?.packageName }.getOrNull()
        // The window Android sends keys and touches to, as the system has it (a dialog of another
        // app's, "isn't responding", would be named here).
        val focus = runCatching {
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("dumpsys window"))
                .bufferedReader().readLines()
                .filter { "mCurrentFocus" in it || "mFocusedApp" in it }
                .joinToString(" | ") { it.trim() }
        }.getOrDefault("?")
        return "activities: $activities; active window: $active; $focus"
    }

    /**
     * Presses Wait on Android's "isn't responding" dialog, when one is up: on CI's emulator (software
     * drawing) the launcher stops answering now and then, and its dialog over the app took the tests'
     * taps and was the window their screen reads saw (2026-11-12: the camera and connection tests
     * failed only there, the app fine underneath). Called before every tap and screen read; true if
     * there was one.
     */
    fun dismissNotResponding(): Boolean {
        val root = runCatching { instrumentation.uiAutomation.rootInActiveWindow }.getOrNull() ?: return false
        if (root.packageName?.toString() != "android") return false
        val wait = root.findAccessibilityNodeInfosByViewId("android:id/aerr_wait").firstOrNull() ?: return false
        Log.w("AppHarness", "An app isn't responding: pressed Wait on Android's dialog")
        wait.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        // Until the app's window has the focus back: a tap sent while the dialog was still going
        // reached nothing (on the emulator, the launcher frozen to raise the dialog).
        waitFor(5_000) { runCatching { activity()?.let { a -> onActivity { a.hasWindowFocus() } } }.getOrNull() == true }
        return true
    }

    /** A file the app saved to Downloads: its MediaStore id and size. */
    data class SavedDownload(val id: Long, val size: Long)

    /** The app's files ([extension]) added to Downloads at or after [sinceSeconds] (MediaStore's clock). */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun savedDownloads(sinceSeconds: Long, extension: String): List<SavedDownload> =
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.SIZE),
            "${MediaStore.Downloads.DISPLAY_NAME} LIKE 'Facebook_%.$extension' AND ${MediaStore.Downloads.DATE_ADDED} >= ?",
            arrayOf(sinceSeconds.toString()),
            null,
        )?.use { c -> List(c.count) { c.moveToNext(); SavedDownload(c.getLong(0), c.getLong(1)) } }.orEmpty()

    /** Removes [files] from Downloads. */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun deleteDownloads(files: List<SavedDownload>) = files.forEach {
        context.contentResolver.delete(ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, it.id), null, null)
    }

    /** Starts the app and loads [html] as its page at [url] ([loadPage]). */
    fun startOnPage(url: String, html: String, asHistory: Boolean = false) {
        start()
        assertTrue("the app's WebView never appeared", waitFor { webViews().isNotEmpty() })
        loadPage(url, html, asHistory = asHistory)
    }

    /**
     * A file the app's own FileProvider serves (a share's photo, a picker's answer): [name] in the
     * cache folder it shares, holding [bytes].
     */
    fun servedFile(name: String, bytes: ByteArray): File =
        File(File(context.cacheDir, "clipboard_images").apply { mkdirs() }, name).apply { writeBytes(bytes) }

    /** The address another app (or the page) reads [file] by, a [servedFile]. */
    fun servedUri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** A plain PNG picture of one colour, as bytes. */
    fun picture(width: Int = 64, height: Int = 64, color: Int = Color.rgb(200, 80, 40)): ByteArray =
        ByteArrayOutputStream().also {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()

    /** Starts MainActivity as a tap on an app notification does: with its [action]. */
    fun start(action: String) = start(Intent(action, null, context, MainActivity::class.java))

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

    /** Whether the on-screen keyboard is up. */
    fun keyboardShown(): Boolean = onActivity {
        ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
    }

    /**
     * A real touch at the middle of the element [js] scrolls into view and gives the box of (CSS px
     * in the visual viewport, and that viewport's width: "left,top,width,height,viewportWidth"; ""
     * while there's none): a link that opens a new window (target=_blank, as a shared reel's)
     * ignores a click from a script. False when there's nothing to tap. Held for [holdMs], a long
     * press.
     */
    fun tapIn(webView: WebView, js: String, holdMs: Long = TAP_MS): Boolean {
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
        // The touch must reach the page: on CI's emulator the first tap after a test closed the app
        // reached nothing now and then (the closing window still taking touches as Android removed
        // it; 2026-11-12, the app focused, the page saw no pointerdown). Tapped again while the same
        // page hasn't seen one; a page that's gone (the tap opened another) counts as reached.
        for (attempt in 1..TAP_TRIES) {
            eval(TAP_PROBE_JS, webView)
            tapAt(x, y, holdMs)
            if (waitFor(TAP_SEEN_MS) { eval("window.__astryxTapSeen !== false", webView) == "true" }) return true
            Log.w("AppHarness", "A tap didn't reach the page (try $attempt): tapping again")
        }
        return true
    }

    /** A finger's tap at ([x], [y]) on the screen, in px, held down for [holdMs]. */
    private fun tapAt(x: Float, y: Float, holdMs: Long = TAP_MS) {
        dismissNotResponding()
        val downAt = SystemClock.uptimeMillis()
        for ((action, time) in listOf(MotionEvent.ACTION_DOWN to downAt, MotionEvent.ACTION_UP to downAt + holdMs)) {
            if (action == MotionEvent.ACTION_UP) Thread.sleep(holdMs)
            val event = MotionEvent.obtain(downAt, time, action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.uiAutomation.injectInputEvent(event, true)
            event.recycle()
        }
    }

    /**
     * [tapIn] on the element [elementJs] evaluates to (an expression; null or undefined while there's
     * none), scrolled to the middle of the screen first unless not to [scroll] (a reel's button: the
     * reels scroller would snap to another reel). Held for [holdMs], a long press.
     */
    fun tapOn(webView: WebView, elementJs: String, scroll: Boolean = true, holdMs: Long = TAP_MS): Boolean = tapIn(
        webView,
        "(function () { var e = ($elementJs); if (!e) return '';" +
            (if (scroll) " e.scrollIntoView({ block: 'center' });" else "") +
            " var r = e.getBoundingClientRect(); return [r.left, r.top, r.width, r.height, visualViewport.width].join(','); })()",
        holdMs,
    )

    /**
     * A real two-finger pinch in [area] (screen px): along its longer side, from a fifth of that
     * side apart to [factor] times as far (below 1 pinches in, above 1 spreads out).
     */
    fun pinch(area: android.graphics.Rect, factor: Float) {
        dismissNotResponding()
        val horizontal = area.width() > area.height()
        val from = maxOf(area.width(), area.height()) / 5f
        val to = from * factor
        val cx = area.exactCenterX()
        val cy = area.exactCenterY()
        val properties = Array(2) { i ->
            MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER }
        }
        val downAt = SystemClock.uptimeMillis()
        var now = downAt
        fun send(action: Int, pointers: Int, distance: Float) {
            val coords = Array(2) { i ->
                val sign = if (i == 0) -1 else 1
                MotionEvent.PointerCoords().apply {
                    x = if (horizontal) cx + sign * distance else cx
                    y = if (horizontal) cy else cy + sign * distance
                    pressure = 1f
                    size = 1f
                }
            }
            val event = MotionEvent.obtain(
                downAt, now, action, pointers, properties, coords, 0, 0, 1f, 1f, 0, 0,
                InputDevice.SOURCE_TOUCHSCREEN, 0
            )
            instrumentation.uiAutomation.injectInputEvent(event, true)
            event.recycle()
        }
        val second = 1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT
        send(MotionEvent.ACTION_DOWN, 1, from)
        send(MotionEvent.ACTION_POINTER_DOWN or second, 2, from)
        for (step in 1..20) {
            Thread.sleep(16)
            now += 16
            send(MotionEvent.ACTION_MOVE, 2, from + (to - from) * step / 20)
        }
        Thread.sleep(300)
        now += 300
        send(MotionEvent.ACTION_POINTER_UP or second, 2, to)
        send(MotionEvent.ACTION_UP, 1, to)
    }

    /** [view]'s box on the screen. */
    fun screenBox(view: View): android.graphics.Rect = onActivity {
        val at = IntArray(2).also(view::getLocationOnScreen)
        android.graphics.Rect(at[0], at[1], at[0] + view.width, at[1] + view.height)
    }

    /** The Messages layer's WebView, once it's open. */
    fun layerWebView(): WebView? = webViews().getOrNull(1)

    /** What [webView]'s own client answers a subresource request for [url] with (null: loaded as usual). */
    fun clientAnswer(webView: WebView, url: String): WebResourceResponse? {
        val request = object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse(url)
            override fun isForMainFrame() = false
            override fun isRedirect() = false
            override fun hasGesture() = false
            override fun getMethod() = "GET"
            override fun getRequestHeaders() = emptyMap<String, String>()
        }
        return onActivity { webView.webViewClient.shouldInterceptRequest(webView, request) }
    }

    /**
     * Keeps [webView] off the network and stops what it's loading: the real page the layer opened
     * (heavy, and logged out it goes on redirecting) can't land over a test page loaded after it.
     */
    fun keepOffTheNetwork(webView: WebView) = onActivity { webView.settings.blockNetworkLoads = true; webView.stopLoading() }

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
                evalWithin(3_000, "location.href === '$url' && document.readyState === 'complete' && !!window.__testPage", webView) == "true"
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

    // The screen's elements showing [text], each one's bounds in px: the accessibility tree walked
    // node by node (the app's Compose UI answers that, not findAccessibilityNodeInfosByText).
    private fun shownBounds(text: String): List<android.graphics.Rect> {
        dismissNotResponding()
        // Read afresh: a cached copy of the screen kept a bar the app had already removed for 10 s
        // (CI's emulator, 2026-11-12, ShareIntoFacebookTest's posting bar).
        if (Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
        val found = mutableListOf<android.graphics.Rect>()
        val queue = ArrayDeque(listOfNotNull(instrumentation.uiAutomation.rootInActiveWindow))
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.text?.toString() == text) found += android.graphics.Rect().also(node::getBoundsInScreen)
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
        return found
    }

    /** Whether [text] is on screen, in the app's own (Compose) UI too. */
    fun textShown(text: String): Boolean = shownBounds(text).isNotEmpty()

    /** A tap on the on-screen element with [text] (a snackbar's action, say). */
    fun tapText(text: String): Boolean {
        val box = shownBounds(text).firstOrNull() ?: return false
        tapAt(box.exactCenterX(), box.exactCenterY())
        return true
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
        dismissNotResponding()
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
    }

    fun url(webView: WebView): String = onActivity { webView.url.orEmpty() }

    /** [js]'s value as evaluateJavascript gives it (JSON: strings quoted, "null" for none). */
    fun eval(js: String, webView: WebView = mainWebView()): String =
        evalWithin(20_000, js, webView) ?: throw AssertionError("evaluateJavascript never returned")

    /**
     * [js]'s value, or null when no answer came within [ms]: one sent as a page is replaced can go
     * unanswered (loadPage's checks while its page loads).
     */
    private fun evalWithin(ms: Long, js: String, webView: WebView): String? {
        var result: String? = null
        val latch = CountDownLatch(1)
        instrumentation.runOnMainSync { webView.evaluateJavascript(js) { result = it; latch.countDown() } }
        return if (latch.await(ms, TimeUnit.MILLISECONDS)) result ?: "null" else null
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
        assertTrue({ "never left PiP (the app was told it's in PiP: ${pipDelivered()})" }, waitFor(15_000) { !isInPip() })
    }
}

// How long a tap holds the screen; a long press holds it for LONG_PRESS_MS (Android takes 0.4 s or more).
private const val TAP_MS = 80L
// tapIn: how often a tap the page didn't see is sent again, and how long to wait for it to see one.
private const val TAP_TRIES = 3
private const val TAP_SEEN_MS = 2_000L
// Notes whether the page sees the next pointerdown (false until it does).
private const val TAP_PROBE_JS = "window.__astryxTapSeen = false; document.addEventListener('pointerdown'," +
    " function () { window.__astryxTapSeen = true; }, { capture: true, once: true }); 1"
const val LONG_PRESS_MS = 1000L

/**
 * assertTrue with a message written only when it fails: after the wait in [condition], so what
 * it reports (a page's shape, the app's state) is what was there at the end of the wait. A plain
 * string is built before the condition runs: 2026-11-10's daily run reported Home, the page the
 * reel was still loading over, as what showed after 30 s.
 */
fun assertTrue(message: () -> String, condition: Boolean) {
    if (!condition) throw AssertionError(message())
}

/**
 * Lets the app post notifications (Android 13+) without asking: asked, Android's dialog comes up
 * over the app the first time something posts (PostingService) and hides the app's own screen.
 */
fun allowNotifications() {
    if (android.os.Build.VERSION.SDK_INT < 33) return
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.uiAutomation.grantRuntimePermission(
        instrumentation.targetContext.packageName, android.Manifest.permission.POST_NOTIFICATIONS
    )
}
