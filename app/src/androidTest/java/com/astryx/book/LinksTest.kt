package com.astryx.book
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URLDecoder
import com.eepiemi.materialbook.MainActivity

/**
 * Links into and out of the app: a Facebook link opened with the app (from a browser, a chat app
 * or the share sheet) opens in it, also while it's already running (it's singleTask: the link
 * arrives in onNewIntent); a link to another site leaves for the browser and the page stays.
 */
@RunWith(AndroidJUnit4::class)
class LinksTest {

    private val app = AppHarness()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before
    fun setUp() {
        File(app.context.filesDir, "datastore").deleteRecursively()
    }

    @After
    fun tearDown() {
        app.finish()
        File(app.context.filesDir, "datastore").deleteRecursively()
    }

    private fun decoded(url: String) = runCatching { URLDecoder.decode(url, "UTF-8") }.getOrDefault(url)

    // No login here: Facebook may answer with its login page, the link's page in its address.
    private fun opens(id: String) = app.waitFor(20_000) { decoded(app.url(app.mainWebView())).contains(id) }

    private fun view(url: String) =
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).setClass(app.context, MainActivity::class.java)

    @Test
    fun aFacebookLink_opensInTheApp() {
        app.start(view("https://www.facebook.com/groups/$GROUP_ID/"))
        assertTrue("the link's page never opened", opens(GROUP_ID))
    }

    @Test
    fun aLinkSentWhileTheAppRuns_opensInTheSameApp() {
        app.start()
        assertTrue(app.waitFor { app.webViews().isNotEmpty() })
        val running = app.activity()
        app.start(view("https://m.facebook.com/groups/$GROUP_ID/"))
        assertTrue("the link's page never opened", opens(GROUP_ID))
        assertSame("the link started a second app instead of the running one", running, app.activity())
    }

    @Test
    fun aFacebookLinkSharedAsText_opensInTheApp() {
        app.start(
            Intent(Intent.ACTION_SEND).setClass(app.context, MainActivity::class.java)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "Look at this https://www.facebook.com/groups/$GROUP_ID/")
        )
        assertTrue("the shared link's page never opened", opens(GROUP_ID))
    }

    @Test
    fun aLinkToAnotherSite_leavesForTheBrowser_andThePageStays() {
        app.start()
        assertTrue(app.waitFor { app.webViews().isNotEmpty() })
        app.loadPage(
            PAGE_URL,
            "<html><body><a id='out' href='https://example.com/astryx-test'>out</a>" +
                "<script>window.__testPage = true;</script></body></html>"
        )
        val filter = IntentFilter(Intent.ACTION_VIEW).apply {
            addDataScheme("https")
            addDataAuthority("example.com", null)
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        // Answered here, so no browser actually opens.
        val monitor = instrumentation.addMonitor(filter, Instrumentation.ActivityResult(0, null), true)
        val anyView = instrumentation.addMonitor(IntentFilter(Intent.ACTION_VIEW).apply {
            addDataScheme("https"); addDataAuthority("example.com", null)
        }, Instrumentation.ActivityResult(0, null), true)
        try {
            app.eval("document.getElementById('out').click(); 1")
            assertTrue("the link never left for the browser", app.waitFor(10_000) { monitor.hits + anyView.hits > 0 })
            Thread.sleep(1000)
            assertEquals("the page left with the link", "\"$PAGE_URL\"", app.eval("location.href"))
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.removeMonitor(anyView)
        }
    }

    companion object {
        private const val GROUP_ID = "1234567890123"
        private const val PAGE_URL = "https://m.facebook.com/some.page/posts/1/"
    }
}
