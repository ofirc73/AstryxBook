package com.eepiemi.materialbook

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eepiemi.materialbook.data.local.SettingsDataStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URLDecoder

/**
 * The app's page features as the app wires them: its scripts run on Facebook's pages, a page
 * setting (Hide stories) applies to the page, Start on Feeds opens
 * the menu it presses Feeds in, and Back on the first page leaves the app to the background (so
 * coming back doesn't reload the feed). Back through pages needs real page history, which test
 * pages don't make; MessagesLayerReelTest covers Back between the views.
 */
@RunWith(AndroidJUnit4::class)
class PagesAndSettingsTest {

    private val app = AppHarness()

    @Before
    fun setUp() {
        File(app.context.filesDir, "datastore").deleteRecursively()
    }

    @After
    fun tearDown() {
        app.finish()
        runBlocking {
            val settings = SettingsDataStore(app.context)
            settings.setHideStories(false)
            settings.setStartOnFeeds(false)
        }
        File(app.context.filesDir, "datastore").deleteRecursively()
    }

    private fun startOnPage(url: String = FEED_URL, html: String = FEED_PAGE, asHistory: Boolean = false) {
        app.start()
        assertTrue("the app's WebView never appeared", app.waitFor { app.webViews().isNotEmpty() })
        app.loadPage(url, html, asHistory = asHistory)
    }

    @Test
    fun theAppsScripts_runOnFacebooksPages() {
        startOnPage()
        assertEquals("scripts.js never ran on the page", "\"function\"", app.eval("typeof window.backHandlerNB"))
    }

    @Test
    fun hideStories_hidesTheStoriesTray() {
        // Settings apply their scripts on the next page load (Settings reloads the page on close).
        runBlocking { SettingsDataStore(app.context).setHideStories(true) }
        startOnPage()
        assertTrue("the stories tray shows with Hide stories on", app.waitFor {
            app.eval("getComputedStyle(document.getElementById('stories')).display") == "\"none\""
        })
    }

    @Test
    fun hideStoriesOff_leavesTheTray() {
        startOnPage()
        Thread.sleep(1000)
        assertTrue("the stories tray was hidden with Hide stories off",
            app.eval("getComputedStyle(document.getElementById('stories')).display") != "\"none\"")
    }

    @Test
    fun startOnFeeds_opensTheMenuToPressFeeds() {
        runBlocking { SettingsDataStore(app.context).setStartOnFeeds(true) }
        app.start()
        assertTrue(app.waitFor { app.webViews().isNotEmpty() })
        // No login here: Facebook may answer with its login page, the menu in its address.
        assertTrue("Start on Feeds didn't open the menu", app.waitFor(20_000) {
            val url = app.url(app.mainWebView())
            runCatching { URLDecoder.decode(url, "UTF-8") }.getOrDefault(url).contains("/bookmarks/")
        })
    }

    @Test
    fun backOnTheFirstPage_leavesTheAppToTheBackground() {
        startOnPage()
        val webView = app.mainWebView()
        app.onActivity { webView.clearHistory() }
        app.pressBack()
        assertTrue("Back on the first page never left the app", app.waitFor { app.isInBackground() })
    }

    companion object {
        private const val FEED_URL = "https://m.facebook.com/home.php"
        // A feed with a stories tray as the mobile site marks it (hide_stories.js).
        private const val FEED_PAGE =
            "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'></head><body>" +
                "<div id='stories' data-mcomponent='MContainer' data-srat='43' style='height:200px'>stories</div>" +
                "<div id='post' style='height:900px'>post</div>" +
                "<script>window.__testPage = true;</script></body></html>"
    }
}
