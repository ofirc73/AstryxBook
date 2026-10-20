package com.eepiemi.materialbook

import com.eepiemi.materialbook.data.local.SettingsDataStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * The app itself (MainActivity and MaterialbookWebView's PiP effects) entering and leaving real
 * picture-in-picture, on a test page in the app's own WebView (no Facebook login: the page is
 * loaded over whatever the start page was, with a Facebook address). Guards the PiP wiring that
 * the page-script tests (PipFocusModeJsTest...) can't reach: which scripts run on entering and
 * leaving PiP, with which page, in which order.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityPipTest {

    private val app = AppHarness()

    @Before
    fun setUp() {
        File(app.context.filesDir, "datastore").deleteRecursively()
        runBlocking {
            val settings = SettingsDataStore(app.context)
            settings.setPipEnabled(true)
            settings.setPinchToZoom(true)
        }
        app.start()
    }

    @After
    fun tearDown() {
        app.finish()
        // Back to the defaults: DataStore keeps its values in memory, deleting the file isn't enough
        // for the tests after these (SettingsDefaultsTest).
        runBlocking {
            val settings = SettingsDataStore(app.context)
            settings.setPipEnabled(false)
            settings.setPinchToZoom(false)
        }
        File(app.context.filesDir, "datastore").deleteRecursively()
    }

    private fun eval(js: String) = app.eval(js)

    // A reel-like page, with a Facebook address so the app's scripts and effects treat it as
    // Facebook's: one large video inside a few boxes, and something else beside it.
    private fun loadReelPage() {
        assertTrue("the app's WebView never appeared", app.waitFor { app.webViews().isNotEmpty() })
        val webView = app.mainWebView()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView.loadDataWithBaseURL(
                "https://m.facebook.com/reel/123/",
                "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'></head>" +
                    "<body style='margin:0'><div id='scroller'><div id='slot'>" +
                    "<video id='reel' muted playsinline style='display:block;width:100%;height:600px;background:#123'></video>" +
                    "</div></div><div id='caption'>caption</div>" +
                    "<script>window.__loaded = true;</script></body></html>",
                "text/html", "UTF-8", null
            )
        }
        assertTrue("the test page never loaded", app.waitFor { eval("!!window.__loaded") == "true" })
        Thread.sleep(1500) // the app's scripts run once the page has finished loading
    }

    @Test
    fun enteringPip_appliesFocusMode_andLeavingRestoresThePage() {
        loadReelPage()
        app.enterPip()
        assertTrue("focus mode never ran", app.waitFor { eval("document.body.hasAttribute('data-astryx-pip-active')") == "true" })
        assertEquals("true", eval("document.getElementById('reel').hasAttribute('data-astryx-pip-video')"))
        // The rest of the page is hidden in the PiP window.
        assertEquals("\"none\"", eval("getComputedStyle(document.getElementById('caption')).display"))

        app.leavePip()
        assertTrue("focus mode was never restored", app.waitFor { eval("document.body.hasAttribute('data-astryx-pip-active')") == "false" })
        assertEquals("false", eval("document.getElementById('reel').hasAttribute('data-astryx-pip-video')"))
        assertTrue(eval("getComputedStyle(document.getElementById('caption')).display") != "\"none\"")
    }

    @Test
    fun aWrapperFacebookAddsInPip_isKeptVisible() {
        // Facebook's reels scroller re-wraps the video after focus mode ran (v1.32.6: black PiP).
        loadReelPage()
        app.enterPip()
        assertTrue(app.waitFor { eval("document.body.hasAttribute('data-astryx-pip-active')") == "true" })
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
        InstrumentationRegistry.getInstrumentation().runOnMainSync { webView.zoomBy(2.5f) }
        assertTrue("the page never zoomed", app.waitFor { eval("visualViewport.scale").toFloat() > 2f })

        app.enterPip()
        // The whole page width in the PiP window, as an unzoomed PiP shows it.
        assertTrue("the PiP window still shows a zoomed-in part of the page", app.waitFor {
            eval("window.innerWidth / visualViewport.width").toFloat() < 1.05f
        })

        app.leavePip()
        assertTrue("the zoom never came back", app.waitFor { eval("visualViewport.scale").toFloat() > 2f })
    }

    companion object {
        /** How many of VIDEO's ancestors are hidden (0 when the video can show). */
        const val HIDDEN_ANCESTORS_JS = "(function () { var n = 0; for (var e = VIDEO; e && e !== document.body;" +
            " e = e.parentElement) if (getComputedStyle(e).display === 'none') n++; return n; })()"
    }
}
