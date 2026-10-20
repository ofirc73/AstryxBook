package com.eepiemi.materialbook

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.util.Rational
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

    fun start(intent: Intent = Intent(context, MainActivity::class.java)) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue("MainActivity never came up", waitFor(15_000) { activity() != null })
    }

    /** Finishes every activity the test left, so the next test starts the app afresh. */
    fun finish() {
        instrumentation.runOnMainSync {
            for (stage in listOf(Stage.RESUMED, Stage.PAUSED, Stage.STOPPED, Stage.STARTED, Stage.CREATED)) {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage).forEach { it.finish() }
            }
        }
        waitFor(5_000) { activity() == null }
    }

    fun activity(): MainActivity? {
        var found: Activity? = null
        instrumentation.runOnMainSync {
            val registry = ActivityLifecycleMonitorRegistry.getInstance()
            found = (registry.getActivitiesInStage(Stage.RESUMED) + registry.getActivitiesInStage(Stage.PAUSED))
                .firstOrNull { it is MainActivity }
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

    fun url(webView: WebView): String = onActivity { webView.url.orEmpty() }

    /** [js]'s value as evaluateJavascript gives it (JSON: strings quoted, "null" for none). */
    fun eval(js: String, webView: WebView = mainWebView()): String {
        var result: String? = null
        val latch = CountDownLatch(1)
        instrumentation.runOnMainSync { webView.evaluateJavascript(js) { result = it; latch.countDown() } }
        assertTrue("evaluateJavascript never returned", latch.await(10, TimeUnit.SECONDS))
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
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
    }

    // Back to full screen the way a tap on the PiP window's expand does: the activity to the front.
    fun leavePip() {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
        assertTrue("never left PiP", waitFor { !isInPip() })
    }
}
