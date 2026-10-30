package com.astryx.book
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RawRes
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A fresh WebView showing a test page, driven synchronously from the test thread: the setup the
 * fixed-page tests (`*JsTest`) share. Laid out at [width] x [height] (0 leaves it unlaid-out, like a
 * WebView not yet attached; named apart from the WebView's own width and height, which the setup
 * block below sees first); [interfaces] are added (name to object) before each load;
 * [domStorage] turns DOM storage (sessionStorage) on.
 */
class PageFixture(
    private val width: Int = 0,
    private val height: Int = 0,
    private val domStorage: Boolean = false,
    private val interfaces: Map<String, Any> = emptyMap(),
) {
    lateinit var webView: WebView
        private set

    val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** Loads [html] at [url] in a new WebView (after [setUp] on it) and waits until it has finished. */
    fun load(url: String, html: String, setUp: (WebView) -> Unit = {}) {
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                if (domStorage) settings.domStorageEnabled = true
                if (this@PageFixture.width > 0 && this@PageFixture.height > 0) {
                    layout(0, 0, this@PageFixture.width, this@PageFixture.height)
                }
                interfaces.forEach { (name, bridge) -> addJavascriptInterface(bridge, name) }
                setUp(this)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        latch.countDown()
                    }
                }
                loadDataWithBaseURL(url, html, "text/html", "UTF-8", null)
            }
        }
        assertTrue("page never finished loading", latch.await(10, TimeUnit.SECONDS))
    }

    /** [js]'s result, as evaluateJavascript gives it (a JSON value; "null" when there's none). */
    fun eval(js: String): String {
        var result: String? = null
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView.evaluateJavascript(js) { result = it; latch.countDown() }
        }
        assertTrue("evaluateJavascript never returned", latch.await(10, TimeUnit.SECONDS))
        return result ?: "null"
    }

    /** A page script bundled in the app (res/raw). */
    fun script(@RawRes id: Int): String =
        context.resources.openRawResource(id).bufferedReader().use { it.readText() }

    /** Runs the bundled page script [id] on the page. */
    fun run(@RawRes id: Int): String = eval(script(id))

    /**
     * Runs scripts.js's desktop/feed identifiers and Back handler (backHandlerNB) on the page: the
     * script up to its first block that doesn't belong to them.
     */
    fun runBackHandler(): String {
        val all = script(com.eepiemi.materialbook.R.raw.scripts)
        val end = all.indexOf("// Enable press and hold")
        assertTrue("back handler block not found in scripts.js", end > 0)
        return eval(all.substring(0, end))
    }

    /** Whether [check] became true within [ms], checked every [everyMs]. */
    fun waitFor(ms: Long = 5_000, everyMs: Long = 100, check: () -> Boolean): Boolean {
        val until = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < until) {
            if (check()) return true
            Thread.sleep(everyMs)
        }
        return false
    }
}
