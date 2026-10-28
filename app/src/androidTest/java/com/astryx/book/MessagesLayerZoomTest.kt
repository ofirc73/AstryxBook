package com.astryx.book
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eepiemi.materialbook.data.local.SettingsDataStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import com.eepiemi.materialbook.MainActivity

/**
 * Pinch to zoom in the Messages layer: a photo sent in a chat opens in the desktop site's photo
 * viewer, small at phone width and without a zoom of its own for fingers, so the layer zooms like
 * the main view. Not in the story and reel creators, whose editors follow finger drags.
 */
@RunWith(AndroidJUnit4::class)
class MessagesLayerZoomTest {

    private val app = AppHarness()

    @Before
    fun setUp() {
        File(app.context.filesDir, "datastore").deleteRecursively()
    }

    @After
    fun tearDown() {
        app.finish()
        runBlocking { SettingsDataStore(app.context).setMessagesDesktop(false) }
        File(app.context.filesDir, "datastore").deleteRecursively()
    }

    private fun zooms(webView: WebView) = app.onActivity {
        webView.settings.supportZoom() && webView.settings.builtInZoomControls && !webView.settings.displayZoomControls
    }

    @Test
    fun aChat_canBePinchZoomed() {
        // A Messages link opens the layer when Messages in desktop mode is on.
        runBlocking { SettingsDataStore(app.context).setMessagesDesktop(true) }
        app.start(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/messages/t/4567/"))
                .setClass(app.context, MainActivity::class.java)
        )
        assertTrue("the Messages layer never opened", app.waitFor(15_000) { app.layerWebView() != null })
        val layer = app.layerWebView()!!
        assertTrue("the Messages layer can't be pinch-zoomed", zooms(layer))

        // And it does zoom (a test page: no login here).
        app.onActivity { layer.settings.blockNetworkLoads = true; layer.stopLoading() }
        app.loadPage(
            "https://www.facebook.com/messages/t/4567/",
            "<html><head><meta name='viewport' content='width=device-width, initial-scale=1, maximum-scale=2'></head>" +
                "<body><img style='width:100%;height:300px;background:#468'><script>window.__testPage = true;</script></body></html>",
            layer
        )
        app.onActivity { layer.zoomBy(1.8f) }
        val zoomed = app.waitFor { app.eval("visualViewport.scale", layer).toFloat() > 1.5f }
        assertTrue("the chat never zoomed (" + app.eval("visualViewport.scale + ' ' + innerWidth + ' ' + Array.prototype.map.call(document.querySelectorAll('meta[name=viewport]'), function (m) { return m.content; }).join(' | ')", layer) + ")", zoomed)
    }

    @Test
    fun theStoryCreator_isNotPinchZoomed() {
        app.start()
        assertTrue(app.waitFor { app.webViews().isNotEmpty() })
        app.loadPage(
            "https://m.facebook.com/home.php",
            "<html><body>feed<script>window.__testPage = true;</script></body></html>"
        )
        app.eval("window.AstryxPage.postMessage(JSON.stringify({ call: 'layerUrl', url: 'https://www.facebook.com/stories/create' })); 1")
        assertTrue("the story creator never opened", app.waitFor(15_000) { app.layerWebView() != null })
        assertFalse("the story creator can be pinch-zoomed (its editor follows finger drags)", zooms(app.layerWebView()!!))
    }
}
