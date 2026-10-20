package com.eepiemi.materialbook

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eepiemi.materialbook.data.local.SettingsDataStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The app itself (MainActivity and MaterialbookWebView's PiP effects) entering and leaving real
 * picture-in-picture, on a test page in the app's own WebView (no Facebook login: the page is
 * loaded over whatever the start page was, with a Facebook address). Guards the PiP wiring that
 * the page-script tests (PipFocusModeJsTest...) can't reach: which scripts run on entering and
 * leaving PiP, with which page, in which order.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityPipTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        File(context.filesDir, "datastore").deleteRecursively()
        runBlocking {
            val settings = SettingsDataStore(context)
            settings.setPipEnabled(true)
            settings.setPinchToZoom(true)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        runCatching { scenario.close() }
        File(context.filesDir, "datastore").deleteRecursively()
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun mainWebView(): WebView? {
        var found: WebView? = null
        scenario.onActivity { activity ->
            fun walk(v: View) {
                if (found != null) return
                if (v is WebView) { found = v; return }
                if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
            walk(activity.window.decorView)
        }
        return found
    }

    private fun waitFor(ms: Long = 10_000, check: () -> Boolean): Boolean {
        val until = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < until) {
            if (runCatching(check).getOrDefault(false)) return true
            Thread.sleep(200)
        }
        return false
    }

    private fun eval(js: String): String {
        val webView = mainWebView() ?: error("no WebView")
        var result: String? = null
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView.evaluateJavascript(js) { result = it; latch.countDown() }
        }
        assertTrue("evaluateJavascript never returned", latch.await(10, TimeUnit.SECONDS))
        return result ?: "null"
    }

    // A reel-like page, with a Facebook address so the app's scripts and effects treat it as
    // Facebook's: one large video inside a few boxes, and something else beside it.
    private fun loadReelPage() {
        assertTrue("the app's WebView never appeared", waitFor { mainWebView() != null })
        val finished = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val webView = mainWebView()!!
            webView.loadDataWithBaseURL(
                "https://m.facebook.com/reel/123/",
                "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'></head>" +
                    "<body style='margin:0'><div id='scroller'><div id='slot'>" +
                    "<video id='reel' muted playsinline style='display:block;width:100%;height:600px;background:#123'></video>" +
                    "</div></div><div id='caption'>caption</div>" +
                    "<script>window.__loaded = true;</script></body></html>",
                "text/html", "UTF-8", null
            )
            finished.countDown()
        }
        finished.await()
        assertTrue("the test page never loaded", waitFor { eval("!!window.__loaded") == "true" })
        Thread.sleep(1500) // the app's scripts run once the page has finished loading
    }

    private fun enterPip() {
        scenario.onActivity {
            it.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(9, 16)).build())
        }
        assertTrue("never entered PiP", waitFor { var pip = false; scenario.onActivity { pip = it.isInPictureInPictureMode }; pip })
    }

    // Back to full screen the way a tap on the PiP window's expand does: the activity to the front.
    private fun leavePip() {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
        assertTrue("never left PiP", waitFor { var pip = true; scenario.onActivity { pip = it.isInPictureInPictureMode }; !pip })
    }

    // ── tests ────────────────────────────────────────────────────────────────

    @Test
    fun enteringPip_appliesFocusMode_andLeavingRestoresThePage() {
        loadReelPage()
        enterPip()
        assertTrue("focus mode never ran", waitFor { eval("document.body.hasAttribute('data-astryx-pip-active')") == "true" })
        assertEquals("true", eval("document.getElementById('reel').hasAttribute('data-astryx-pip-video')"))
        // The rest of the page is hidden in the PiP window.
        assertEquals("\"none\"", eval("getComputedStyle(document.getElementById('caption')).display"))

        leavePip()
        assertTrue("focus mode was never restored", waitFor { eval("document.body.hasAttribute('data-astryx-pip-active')") == "false" })
        assertEquals("false", eval("document.getElementById('reel').hasAttribute('data-astryx-pip-video')"))
        assertTrue(eval("getComputedStyle(document.getElementById('caption')).display") != "\"none\"")
    }

    @Test
    fun aWrapperFacebookAddsInPip_isKeptVisible() {
        // Facebook's reels scroller re-wraps the video after focus mode ran (v1.32.6: black PiP).
        loadReelPage()
        enterPip()
        assertTrue(waitFor { eval("document.body.hasAttribute('data-astryx-pip-active')") == "true" })
        eval(
            "var v = document.getElementById('reel'); var w = document.createElement('div'); w.id = 'rewrap';" +
                " v.parentElement.insertBefore(w, v); w.appendChild(v); 1"
        )
        assertTrue("the new wrapper was never kept", waitFor(3000) {
            eval("document.getElementById('rewrap').hasAttribute('data-astryx-pip-keep')") == "true"
        })
        assertEquals(
            "0",
            eval("(function () { var n = 0; for (var e = document.getElementById('reel'); e && e !== document.body; e = e.parentElement) if (getComputedStyle(e).display === 'none') n++; return n; })()")
        )
        leavePip()
    }

    @Test
    fun aPinchZoomedPage_showsItsWholeWidthInPip_andTheZoomComesBack() {
        loadReelPage()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { mainWebView()!!.zoomBy(2.5f) }
        assertTrue("the page never zoomed", waitFor { eval("visualViewport.scale").toFloat() > 2f })

        enterPip()
        // The whole page width in the PiP window, as an unzoomed PiP shows it.
        assertTrue("the PiP window still shows a zoomed-in part of the page", waitFor {
            eval("window.innerWidth / visualViewport.width").toFloat() < 1.05f
        })

        leavePip()
        assertTrue("the zoom never came back", waitFor { eval("visualViewport.scale").toFloat() > 2f })
    }
}
