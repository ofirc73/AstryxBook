package com.eepiemi.materialbook

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.webkit.CookieManager
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eepiemi.materialbook.data.local.SettingsDataStore
import com.eepiemi.materialbook.notifications.FacebookCounts
import com.eepiemi.materialbook.notifications.FacebookCountsWorker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The app against today's real Facebook, logged in: catches Facebook changing the pages our
 * scripts rely on, which the tests on fixed test pages can't. Needs a device where the debug app
 * is logged in to Facebook, so it runs only when asked (`-e canary true`), never in CI:
 * `tools/canary.ps1` runs it on the emulator.
 *
 * Nothing here posts or shares: the share and story checks stop at the open editor, and the app
 * is closed with the draft unsent.
 */
@RunWith(AndroidJUnit4::class)
class LiveFacebookCanaryTest {

    private val app = AppHarness()

    @Before
    fun setUp() {
        assumeTrue(
            "Live Facebook checks run only with -e canary true",
            InstrumentationRegistry.getArguments().getString("canary") == "true"
        )
        val cookie = runCatching { CookieManager.getInstance().getCookie("https://m.facebook.com") }.getOrNull()
        assertTrue("Not logged in to Facebook: log in in the debug app first", cookie?.contains("c_user=") == true)
        File(app.context.filesDir, "datastore").deleteRecursively()
        runBlocking { SettingsDataStore(app.context).setPipEnabled(true) }
    }

    @After
    fun tearDown() {
        app.finish()
        runBlocking { SettingsDataStore(app.context).setPipEnabled(false) }
    }

    private fun eval(js: String) = app.eval(js)

    private fun open(url: String) {
        app.start()
        assertTrue("the app's WebView never appeared", app.waitFor { app.webViews().isNotEmpty() })
        assertTrue("Facebook never loaded", app.waitFor(30_000) { eval("location.hostname") == "\"m.facebook.com\"" })
        eval("location.href = '$url'; 1")
    }

    @Test
    fun aReel_keepsPlayingInPip() {
        open("https://m.facebook.com/reels/")
        assertTrue("no reel played on /reels/", app.waitFor(30_000) { eval("!!document.querySelector('video') && $PLAYING_JS") == "true" })
        assertTrue(
            "the reels' containers (div.vertically-snappable, adblock.js) are gone",
            eval("!!document.querySelector('div.vertically-snappable')") == "true"
        )
        // Reels start muted, and a muted video opens no PiP: unmuted as a tap on the sound would.
        eval("document.querySelector('video').muted = false; 1")
        // The app's scripts run once the page has loaded: PiP needs pip_video_detector.js's video.
        assertTrue("pip_video_detector.js never found the playing reel", app.waitFor(30_000) {
            eval("!!window.__astryxLastActiveVideo && !window.__astryxLastActiveVideo.paused") == "true"
        })
        Thread.sleep(1000)

        // Home, as the user leaves the app: Android 12+ enters PiP by itself (auto-enter).
        app.pressHome()
        assertTrue("the playing reel never went to PiP on Home", app.waitFor { app.isInPip() })
        assertTrue("focus mode never ran", app.waitFor { eval("document.body.hasAttribute('data-astryx-pip-active')") == "true" })
        Thread.sleep(3000) // Facebook re-wraps and pauses the video in the first seconds of PiP
        assertEquals(
            "the PiP video has hidden ancestors (a black PiP window)", "0",
            eval(MainActivityPipTest.HIDDEN_ANCESTORS_JS.replace("VIDEO", "document.querySelector('[data-astryx-pip-video]')"))
        )
        assertEquals(
            "the PiP video has no size (a black PiP window)", "true",
            eval("(function () { var r = document.querySelector('[data-astryx-pip-video]').getBoundingClientRect(); return r.width > 0 && r.height > 0; })()")
        )
        assertTrue("the reel stopped playing in PiP", app.waitFor(5_000) {
            eval(PLAYING_JS.replace("document.querySelector('video')", "document.querySelector('[data-astryx-pip-video]')")) == "true"
        })

        app.leavePip()
        assertTrue("focus mode was never restored", app.waitFor { eval("document.body.hasAttribute('data-astryx-pip-active')") == "false" })
    }

    @Test
    fun sharingAPhotoToFeed_opensTheComposerWithIt() {
        val dir = File(app.context.cacheDir, "clipboard_images").apply { mkdirs() }
        val file = File(dir, "canary.png")
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(30, 120, 200)) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(app.context, "${app.context.packageName}.fileprovider", file)

        app.start(
            Intent(Intent.ACTION_SEND)
                .setComponent(ComponentName(app.context.packageName, "com.eepiemi.materialbook.ShareToFeed"))
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
        assertTrue("the composer never opened (share_media.js, FEED_COMPOSER_PAGE_URL)", app.waitFor(45_000) {
            app.url(app.mainWebView()).contains("/composer")
        })
        assertTrue("the photo never reached the composer (share_media.js: the Photos button or its file input)", app.waitFor(45_000) {
            eval("document.querySelectorAll('img[src^=\"data:image\"]').length > 0") == "true"
        })
    }

    @Test
    fun createStoryOnHome_opensTheStoryCreator() {
        open("https://m.facebook.com/")
        // The tray's first story-sized card near the top of Home (isCreateStoryCard in scripts.js).
        val clicked = app.waitFor(30_000) {
            eval(
                """
                (function () {
                  var cards = Array.prototype.slice.call(document.querySelectorAll('[role="button"], [data-action-id]'));
                  var card = cards.filter(function (el) {
                    var r = el.getBoundingClientRect();
                    return r.width >= 80 && r.width <= 160 && r.height >= 140 && r.height <= 260;
                  })[0];
                  if (!card || card.getBoundingClientRect().top + window.scrollY > 450) return false;
                  (card.querySelector('[role="button"]') || card).click();
                  return true;
                })()
                """.trimIndent()
            ) == "true"
        }
        assertTrue("no Create story card on Home (scripts.js isCreateStoryCard)", clicked)
        assertTrue("Create story never opened the story creator in the Messages layer", app.waitFor(30_000) {
            app.webViews().drop(1).any { app.url(it).contains("/stories/create") }
        })
    }

    @Test
    fun startOnFeeds_pressesTheMenusFeedsEntry() {
        open("https://m.facebook.com/bookmarks/#astryx-feeds")
        assertTrue("open_feeds.js never found the menu's Feeds entry (its icon or label)", app.waitFor(30_000) {
            eval("window.__astryxFeeds === true && location.pathname === '/'") == "true"
        })
    }

    @Test
    fun facebookNotifications_readTheCountsFromTheTabBar() {
        val html = FacebookCountsWorker.fetchPage(app.context)
        assertNotNull("m.facebook.com/notifications/ couldn't be fetched (FacebookCountsWorker)", html)
        assertNotNull("no tab bar counts in m.facebook.com/notifications/ (FacebookCounts.parse)", FacebookCounts.parse(html!!))
    }

    companion object {
        private const val PLAYING_JS =
            "(function (v) { return !!v && !v.paused && v.readyState >= 2; })(document.querySelector('video'))"
    }
}
