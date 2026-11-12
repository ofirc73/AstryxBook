package com.astryx.book

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.ui.components.connectionRetries
import com.eepiemi.materialbook.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A page Facebook can't be reached for: the app's own screen covers it, with a button that loads
 * the page again, and goes once a page loads. The page stays underneath (the dialog it replaces
 * took the WebView away and could only close the app).
 */
@RunWith(AndroidJUnit4::class)
class ConnectionErrorTest {

    private val app = AppHarness()
    private val retry get() = app.context.getString(R.string.connection_retry)

    @Before
    fun setUp() {
        File(app.context.filesDir, "datastore").deleteRecursively()
    }

    @After
    fun tearDown() {
        app.finish()
        File(app.context.filesDir, "datastore").deleteRecursively()
    }

    @Test
    fun aPageThatCantBeReached_offersToTryAgain_andAPageThatLoadsClearsIt() {
        app.startOnPage(PAGE_URL, PAGE)
        assertFalse("the error screen shows over a page that loaded", app.textShown(retry))

        val main = app.mainWebView()
        fun page() = "the WebView at ${app.url(main)}, ${app.onActivity { main.progress }}% loaded; ${app.screenState()}"
        app.onActivity { main.loadUrl(UNREACHABLE_URL) }
        assertTrue({ "no Try again for a page that can't be reached (${page()})" }, app.waitFor(30_000) { app.textShown(retry) })
        assertEquals("the page was taken away with the error", 1, app.webViews().size)

        val retries = connectionRetries.get()
        assertTrue(app.tapText(retry))
        assertTrue("Try again didn't load the page again", app.waitFor { connectionRetries.get() == retries + 1 })
        assertTrue({ "the page failed again, without its error screen (${page()})" }, app.waitFor(30_000) { app.textShown(retry) })

        app.loadPage("$PAGE_URL?n=2", PAGE)
        assertTrue("the error screen stayed over a page that loaded", app.waitFor { !app.textShown(retry) })
    }

    companion object {
        private const val PAGE_URL = "https://m.facebook.com/some.page/posts/2/"
        // Nothing listens there: the connection is refused at once, with or without a network (a name
        // that doesn't resolve took CI's emulator over 30 s to give up on).
        private const val UNREACHABLE_URL = "https://127.0.0.1:47111/"
        private const val PAGE = "<html><body><p>A page</p><script>window.__testPage = true;</script></body></html>"
    }
}
