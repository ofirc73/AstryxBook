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
 * linked_post.js: a post opened from a chat shows as m.facebook.com's Home, with the post as a
 * "From your link" card under the feed's first post. The fixture is that feed as read from the
 * emulator (2026-10-25): top-level cards (MContainer, data-dcm-id 1) whose first row is the author's
 * picture and name (65 px), the linked one's a 32 px text label, under a fixed top bar.
 */
@RunWith(AndroidJUnit4::class)
class LinkedPostJsTest {

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
                loadDataWithBaseURL("https://m.facebook.com/", html, "text/html", "UTF-8", null)
            }
        }
        assertTrue("page never finished loading", latch.await(10, TimeUnit.SECONDS))
        val script = InstrumentationRegistry.getInstrumentation().targetContext.resources
            .openRawResource(R.raw.linked_post).bufferedReader().use { it.readText() }
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

    private fun waitFor(ms: Long = 5_000, check: () -> Boolean): Boolean {
        val until = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < until) {
            if (check()) return true
            Thread.sleep(100)
        }
        return false
    }

    @Test
    fun theLinkedPost_isScrolledToTheTop_underTheBar() {
        load(feed(linkedAt = 2))
        assertTrue("the linked post was never scrolled under the bar (${eval("document.getElementById('linked').getBoundingClientRect().top")})",
            waitFor { eval("Math.round(document.getElementById('linked').getBoundingClientRect().top)") == "100" })
    }

    @Test
    fun aFeedWithoutALinkedPost_staysWhereItIs() {
        load(feed(linkedAt = -1))
        Thread.sleep(1500)
        assertEquals("0", eval("Math.round(window.scrollY)"))
    }

    @Test
    fun aTouch_stopsIt() {
        // The linked card comes in late, after the user has started reading.
        load(feed(linkedAt = -1))
        eval("document.dispatchEvent(new Event('touchstart')); 1")
        eval("document.getElementById('feed').insertAdjacentHTML('beforeend', \"${card("linked", label = true)}\"); 1")
        Thread.sleep(1500)
        assertEquals("0", eval("Math.round(window.scrollY)"))
    }

    private companion object {
        // A feed card: its first row the author's picture and name (65 px) or a text label (32 px).
        fun card(id: String, label: Boolean) =
            "<div id='$id' data-mcomponent='MContainer' data-dcm-id='1' style='height:900px'>" +
                (if (label) "<div style='height:32px'>From your link</div>"
                else "<div style='height:65px'><img style='width:40px;height:40px'>Author</div>") +
                "<div data-mcomponent='MContainer' data-dcm-id='1' style='height:400px'>post</div></div>"

        fun feed(linkedAt: Int) =
            "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'></head>" +
                "<body style='margin:0'><div style='position:fixed;top:0;left:0;right:0;height:100px;background:#fff'>bar</div>" +
                "<div id='feed' style='padding-top:100px'>" +
                (0 until 4).joinToString("") { card(if (it == linkedAt) "linked" else "c$it", label = it == linkedAt) } +
                "</div></body></html>"
    }
}
