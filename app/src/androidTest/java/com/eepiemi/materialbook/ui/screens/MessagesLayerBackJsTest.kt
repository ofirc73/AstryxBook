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
 * Back in the Messages layer (messages_layer_nav.js, `__astryxLayerBack`): in a chat it presses the
 * chat header's own Back button, the icon at the top left with the chat's name beside it; on the
 * chat list it leaves the layer to the app. The fixtures are the desktop site's rows at phone width
 * as read from the emulator (2026-10-24): the Back button 36 px at (32, 84), the name 12 px to its
 * right, the message box at the bottom (laid out with no width while the chat list shows).
 */
@RunWith(AndroidJUnit4::class)
class MessagesLayerBackJsTest {

    private lateinit var webView: WebView

    private fun load(html: String) {
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
                loadDataWithBaseURL("https://www.facebook.com/messages/e2ee/t/1/", html, "text/html", "UTF-8", null)
            }
        }
        assertTrue("page never finished loading", latch.await(10, TimeUnit.SECONDS))
        val script = InstrumentationRegistry.getInstrumentation().targetContext.resources
            .openRawResource(R.raw.messages_layer_nav).bufferedReader().use { it.readText() }
        eval("window.LayerBridge = { onUrlChanged: function () {} }; 1")
        eval(script)
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

    private fun back() = eval("window.__astryxLayerBack()")
    private fun pressed() = eval("window.__pressed || ''")

    @Test
    fun aChat_namedByALink_goesBackToTheChatList() {
        load(page(NAME_LINK, BOX_SHOWN))
        assertEquals("\"chat\"", back())
        assertEquals("\"back\"", pressed())
    }

    @Test
    fun anEncryptedChat_namedByAHeading_goesBackToTheChatList() {
        load(page(NAME_HEADING, BOX_SHOWN))
        assertEquals("\"chat\"", back())
        assertEquals("\"back\"", pressed())
    }

    @Test
    fun theChatList_beside_aHeading_leavesTheLayer() {
        // The list's own icon beside its heading, and the chat's box laid out with no width.
        load(page(NAME_HEADING, BOX_HIDDEN))
        assertEquals("\"list\"", back())
        assertEquals("\"\"", pressed())
    }

    private companion object {
        const val BUTTON =
            "<div role='button' onclick=\"window.__pressed = 'back'\" " +
                "style='position:absolute;left:32px;top:84px;width:36px;height:36px'><svg width='20' height='20'></svg></div>"
        const val NAME_LINK = "<a href='/me/' style='position:absolute;left:80px;top:90px;width:120px;height:24px'>Name</a>"
        const val NAME_HEADING = "<h3 style='position:absolute;left:80px;top:90px;width:120px;height:24px;margin:0'>Name</h3>"
        const val BOX_SHOWN =
            "<div role='textbox' contenteditable='true' style='position:absolute;left:150px;top:700px;width:135px;height:23px'></div>"
        const val BOX_HIDDEN =
            "<div role='textbox' contenteditable='true' style='position:absolute;left:150px;top:700px;width:0;height:23px'></div>"

        fun page(name: String, box: String) =
            "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'></head>" +
                "<body style='margin:0'>$BUTTON$name$box</body></html>"
    }
}
