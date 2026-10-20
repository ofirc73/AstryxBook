package com.eepiemi.materialbook.ui.screens

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * layer_photo_viewer.js widens a chat photo in the desktop viewer (Messages layer). The fixture is
 * the viewer's layout as read from a Galaxy A56 (2026-10-20): a row with a 72 px arrow column on
 * each side and the photo's column between them, the photo an object-fit: contain image in a few
 * boxes sized by the viewer.
 */
@RunWith(AndroidJUnit4::class)
class LayerPhotoViewerJsTest {

    private lateinit var webView: WebView

    private fun load(url: String, html: String = VIEWER) {
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                layout(0, 0, 1080, 2000)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        latch.countDown()
                    }
                }
                loadDataWithBaseURL(url, html, "text/html", "UTF-8", null)
            }
        }
        assertTrue("page never finished loading", latch.await(10, TimeUnit.SECONDS))
        val script = InstrumentationRegistry.getInstrumentation().targetContext.resources
            .openRawResource(R.raw.layer_photo_viewer).bufferedReader().use { it.readText() }
        eval(script)
        Thread.sleep(300)
    }

    private fun eval(js: String): String {
        var result: String? = null
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView.evaluateJavascript(js) { result = it; latch.countDown() }
        }
        assertTrue("evaluateJavascript never returned", latch.await(10, TimeUnit.SECONDS))
        return result ?: "null"
    }

    private fun photoWidth() = eval("document.getElementById('photo').getBoundingClientRect().width").toFloat()
    private fun rowWidth() = eval("document.getElementById('row').getBoundingClientRect().width").toFloat()

    @Test
    fun aChatPhoto_takesTheRowsWidth_withTheArrowsOverIt() {
        load("https://www.facebook.com/messages/e2ee/t/1/")
        assertTrue("the photo stayed in its narrow column (${photoWidth()} of ${rowWidth()})", photoWidth() > rowWidth() * 0.9f)
        assertEquals("\"absolute\"", eval("getComputedStyle(document.getElementById('prev')).position"))
        assertEquals("\"absolute\"", eval("getComputedStyle(document.getElementById('next')).position"))
        // The arrows are still there to tap, over the photo.
        assertTrue(eval("document.getElementById('next').getBoundingClientRect().right").toFloat() >= rowWidth() - 1)
        assertEquals("\"16px\"", eval("getComputedStyle(document.getElementById('photo')).borderTopLeftRadius"))
    }

    @Test
    fun aPhotoAddedLater_isWidenedToo() {
        // The viewer opens over the chat after the page loaded.
        load("https://www.facebook.com/messages/e2ee/t/1/", "<html><body style='margin:0;width:384px'><p>chat</p></body></html>")
        eval("document.body.insertAdjacentHTML('beforeend', \"$ROW\"); 1")
        Thread.sleep(300)
        assertTrue("a viewer opened after the page loaded stayed narrow (${photoWidth()})", photoWidth() > rowWidth() * 0.9f)
    }

    @Test
    fun theStoryCreator_isLeftAlone() {
        load("https://www.facebook.com/stories/create/")
        assertTrue("the creator's layout was changed", photoWidth() < rowWidth() * 0.7f)
    }

    companion object {
        private const val ROW =
                "<div id='row' style='display:flex;justify-content:space-between;width:384px;height:708px'>" +
                "<div id='prev' style='width:72px;height:708px;flex:none'>prev</div>" +
                "<div id='middle' style='display:flex;flex:0 0 216px;width:216px'><div style='width:100%;display:flex;justify-content:center;align-items:center'>" +
                "<div style='width:100%;max-height:calc(100vh - 76px)'><div style='position:relative;padding-top:75%'>" +
                "<img id='photo' style='position:absolute;top:0;left:0;width:100%;height:100%;object-fit:contain;background:#468'>" +
                "</div></div></div></div>" +
                "<div id='next' style='width:72px;height:708px;flex:none'>next</div>" +
                "</div>"
        private const val VIEWER =
            "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'></head>" +
                "<body style='margin:0;width:384px'>" + ROW + "</body></html>"
    }
}
