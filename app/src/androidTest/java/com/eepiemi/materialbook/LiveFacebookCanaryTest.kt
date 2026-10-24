package com.eepiemi.materialbook

import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
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
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The app against today's real Facebook, logged in: catches Facebook changing the pages our
 * scripts rely on, which the tests on fixed test pages can't. Needs a device where the debug app
 * is logged in to Facebook, so it runs only when asked (`-e canary true`), never in the CI
 * checks. `facebook-canary.yml` runs it daily on GitHub, logged in to a test account with the
 * session cookies in the FB_TEST_COOKIES secret (passed base64-encoded as `-e fbCookies`);
 * `tools/canary.ps1` runs it on an emulator that is logged in already.
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
        InstrumentationRegistry.getArguments().getString("fbCookies")?.let { logIn(it) }
        val cookie = runCatching { CookieManager.getInstance().getCookie("https://m.facebook.com") }.getOrNull()
        assertTrue("Not logged in to Facebook: log in in the debug app first", cookie?.contains("c_user=") == true)
        File(app.context.filesDir, "datastore").deleteRecursively()
        runBlocking { SettingsDataStore(app.context).setPipEnabled(true) }
    }

    @After
    fun tearDown() {
        app.finish()
        runBlocking {
            SettingsDataStore(app.context).setPipEnabled(false)
            SettingsDataStore(app.context).setMessagesDesktop(false)
        }
    }

    private fun eval(js: String) = app.eval(js)

    // The video PiP would take plays on for [ms], by pip_video_detector.js's idea of playing.
    private fun playsOnFor(ms: Long): Boolean {
        val before = eval(DETECTED_PLAYING_TIME_JS).toDoubleOrNull() ?: return false
        if (before < 0) return false
        Thread.sleep(ms)
        return (eval(DETECTED_PLAYING_TIME_JS).toDoubleOrNull() ?: return false) > before
    }

    // The app's log lines under [tag], without the line prefixes.
    private fun logLines(tag: String): List<String> {
        val out = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("logcat -d -s $tag:D")
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(out).bufferedReader().readLines()
            .filter { "$tag: " in it }.map { it.substringAfter("$tag: ") }
    }

    // A note in the run's output for a check that passed only after working around something:
    // am instrument prints a status's stream text (the workflow turns these into warnings), and
    // logcat has it under AstryxbookCanary.
    private fun note(message: String) {
        Log.w("AstryxbookCanary", message)
        InstrumentationRegistry.getInstrumentation().sendStatus(NOTE_STATUS, Bundle().apply {
            putString(Instrumentation.REPORT_KEY_STREAMRESULT, "\n$NOTE_PREFIX$message\n")
        })
    }

    // The app's last PiP log lines (AstryxbookPiP: video states, PiP params), for a failure message.
    private fun pipLog(): String = runCatching {
        logLines("AstryxbookPiP").takeLast(8).joinToString(" | ")
    }.getOrDefault("unreadable")

    // Whether the PiP params the app sent last had auto-enter on (MainActivity logs each), null if
    // it sent none.
    private fun lastAutoEnter(): Boolean? = runCatching {
        logLines("AstryxbookPiP").lastOrNull { "PictureInPictureParams: autoEnter=" in it || "reapplyPipParams: autoEnter=" in it }
            ?.substringAfter("autoEnter=")?.startsWith("true")
    }.getOrNull()

    // What the app has on screen, for a failure message when the page can't be read: its WebViews
    // and whether it lost a renderer (RendererGoneClient). Nothing of the page or the account.
    private fun appState(): String {
        val webViews = runCatching { app.webViews().size.toString() }.getOrElse { it.javaClass.simpleName }
        val activity = app.activity()?.let { if (it.isFinishing) "finishing" else "on screen" } ?: "none in front"
        val renderer = runCatching { logLines("Astryxbook").filter { "renderer gone" in it } }.getOrDefault(emptyList())
        return "webViews=$webViews activity=$activity renderer lost: ${renderer.joinToString(" | ").ifEmpty { "no" }}"
    }

    // The page's shape, or the app's state when there's no page to read.
    private fun pageShape(): String = runCatching { eval(PAGE_SHAPE_JS) }.getOrElse { "no page (${appState()})" }

    // The session cookies ("c_user=...; xs=...; ..."), base64-encoded, into the app's WebView.
    private fun logIn(encoded: String) {
        val cookies = String(Base64.decode(encoded, Base64.DEFAULT)).split(';').map { it.trim() }.filter { '=' in it }
        val manager = CookieManager.getInstance()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            cookies.forEach { manager.setCookie("https://www.facebook.com", "$it; Domain=.facebook.com; Path=/; Secure") }
            manager.flush()
        }
    }

    private fun open(url: String) {
        app.start()
        assertTrue("the app's WebView never appeared", app.waitFor { app.webViews().isNotEmpty() })
        assertTrue("Facebook never loaded", app.waitFor(30_000) { eval("location.hostname") == "\"m.facebook.com\"" })
        assertEquals(
            "Facebook session expired: log in again (FB_TEST_COOKIES)", "false",
            eval("location.pathname.indexOf('/login') === 0 || !!document.querySelector('input[name=\"pass\"]')")
        )
        eval("location.href = '$url'; 1")
    }

    // The Reels tab with a reel playing. The test account on GitHub gets "Content not found" for
    // the Reels tab (no reels for it there): a public reel, opened by its address, is used instead.
    private fun openPlayingReel() {
        open("https://m.facebook.com/reels/")
        if (app.waitFor(20_000) { eval("document.title === 'Content not found'") == "true" }) {
            eval("location.href = '$PUBLIC_REEL_URL'; 1")
            Thread.sleep(3000)
        }
        var playing = app.waitFor(30_000) { eval("!!document.querySelector('video') && $PLAYING_JS") == "true" }
        if (!playing) {
            // Facebook's autoplay may not start on a slow emulator (GitHub's draws in software):
            // started as a tap would, then given time again.
            eval("(function (v) { if (v) { v.muted = true; var p = v.play(); if (p && p.catch) p.catch(function () {}); } })(document.querySelector('video')); 1")
            playing = app.waitFor(30_000) { eval(PLAYING_JS) == "true" }
        }
        if (!playing) fail("no reel played on /reels/ (${runCatching { eval(VIDEO_STATE_JS) }.getOrDefault("")}; ${pageShape()})")
    }

    @Test
    fun aReel_keepsPlayingInPip() {
        openPlayingReel()
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
        // Unmuted, the reel buffers for a moment (readyState 2, seen on GitHub's emulator), and
        // pip_video_detector.js counts a buffering video as playing only for 2 s after it last
        // played. Home waits until the reel has played on, as the detector counts it.
        if (!app.waitFor(30_000) { playsOnFor(1000) }) {
            fail("the reel never played on after it was unmuted (buffering, paused or muted again), so Home " +
                "was never pressed (${eval(VIDEO_STATE_JS)}; the app's PiP log: ${pipLog()})")
        }

        // Home, as the user leaves the app: Android 12+ enters PiP by itself (auto-enter). The reel
        // can still stop in the instant before Home, or buffer past the detector's 2 s (issue 66):
        // the app then turns auto-enter off and rightly opens no PiP, and gets one more try, noted in
        // the run's output. A Home with auto-enter on and no PiP fails.
        app.pressHome()
        var inPip = app.waitFor { app.isInPip() }
        if (!inPip && lastAutoEnter() == false) {
            note("Home needed a retry: the app had turned PiP's auto-enter off just before Home (the reel " +
                "stopped or buffered). The app's PiP log: ${pipLog()}")
            app.start()
            eval(PLAY_DETECTED_JS)
            if (!app.waitFor(30_000) { playsOnFor(1000) }) {
                fail("the reel stopped before Home and never played on again (${eval(VIDEO_STATE_JS)}; " +
                    "the app's PiP log: ${pipLog()})")
            }
            app.pressHome()
            inPip = app.waitFor { app.isInPip() }
        }
        assertTrue("the playing reel never went to PiP on Home (the app's PiP log: ${pipLog()})", inPip)
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
        val tapCreateStory = {
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
        // On GitHub the app has twice had no WebView here, after Home had loaded: the tap couldn't
        // run, so the message says what the app had instead.
        if (!app.waitFor(30_000) { tapCreateStory() }) {
            fail("no Create story card on Home (scripts.js isCreateStoryCard) (${pageShape()})")
        }
        val creatorOpen = { app.webViews().drop(1).any { app.url(it).contains("/stories/create") } }
        // A tap before the page listens to taps is lost on a slow emulator: tapped once more.
        val opened = app.waitFor(15_000) { creatorOpen() } || (tapCreateStory() && app.waitFor(20_000) { creatorOpen() })
        if (!opened) fail("Create story never opened the story creator in the Messages layer (${appState()})")
    }

    @Test
    fun removeAds_hidesTheFeedsAds() {
        open("https://m.facebook.com/")
        // The first screen can hold only the composer and the stories: the posts come below.
        var found = app.waitFor(20_000) { feedAds().posts > 0 }
        for (screen in 1..5) {
            if (found) break
            eval("window.scrollBy(0, Math.round(window.innerHeight * 0.9)); 1")
            found = app.waitFor(4_000) { feedAds().posts > 0 }
        }
        if (!found) {
            fail("no feed posts in the boxes adblock.js hides (div[data-dcm-id=\"1\"][data-mcomponent=MContainer]) " +
                "(${runCatching { eval(FEED_SHAPE_JS) }.getOrDefault("")}; ${pageShape()})")
        }
        // Down the feed until adblock.js has hidden an ad or a label it missed shows up; Facebook
        // loads more posts as the page scrolls.
        var ads = feedAds()
        for (screen in 1..FEED_SCREENS) {
            if (ads.hidden > 0 || ads.missed > 0) break
            eval("window.scrollBy(0, Math.round(window.innerHeight * 0.9)); 1")
            Thread.sleep(2000)
            ads = feedAds()
        }
        if (ads.missed > 0) {
            fail("${ads.missed} of ${ads.posts} feed posts kept a Sponsored label on the feed: adblock.js no longer " +
                "finds it (its colour, span.f5 or div.native-text.rslh changed). The label: ${ads.missedShape}")
        }
        if (ads.posts >= 6 && ads.hidden * 2 > ads.posts) {
            fail("adblock.js hid ${ads.hidden} of ${ads.posts} feed posts: the colour it finds ad labels by is on " +
                "normal posts too")
        }
        if (ads.hidden == 0) note("no ad on the feed in $FEED_SCREENS screens (${ads.posts} posts): Remove ads wasn't checked there")
    }

    @Test
    fun removeAds_emptiesTheReelsAds() {
        openPlayingReel()
        // Through the reels, one at a time, until adblock.js has emptied an ad or a label it missed
        // shows up; Facebook adds reels as the user moves on.
        var ads = reelAds()
        var stuck = 0
        for (reel in 1..REELS_TO_SEE) {
            if (ads.hidden > 0 || ads.missed > 0 || stuck >= 3) break
            val moved = eval(NEXT_REEL_JS) == "true"
            Thread.sleep(2500)
            stuck = if (moved) 0 else stuck + 1
            ads = reelAds()
        }
        if (ads.missed > 0) {
            fail("${ads.missed} of ${ads.posts} reels kept an ad label: adblock.js no longer finds it (a span " +
                "holding just the label in div.vertically-snappable). The label: ${ads.missedShape}")
        }
        if (ads.posts >= 6 && ads.hidden * 2 > ads.posts) {
            fail("adblock.js emptied ${ads.hidden} of ${ads.posts} reels as ads")
        }
        if (ads.hidden == 0) note("no ad in ${ads.posts} reels: Remove ads wasn't checked on reels")
    }

    @Test
    fun aReelsPlayer_hasItsMp4InDataVideoUrl() {
        // Downloads (download_content.js) and the lock-screen audio hand-off (PIP_HANDOFF_ARM_JS)
        // take a video's file from its player's data-video-url: the <video> itself plays a blob:.
        openPlayingReel()
        val started = eval(VIDEO_FILE_JS).trim('"')
        if (started != "fetching") {
            fail("the playing reel's player has no MP4 in data-video-url (download_content.js, " +
                "PIP_HANDOFF_ARM_JS): $started (${pageShape()})")
        }
        var file = "pending"
        assertTrue("data-video-url's file never answered", app.waitFor(30_000) {
            file = eval("window.__canaryVideoFile").trim('"')
            file != "pending"
        })
        assertTrue(
            "data-video-url isn't an MP4 the page can fetch, as a download does: $file",
            file.startsWith("status=200 ") && (" type=video/" in file || " box=ftyp" in file)
        )
    }

    // What adblock.js did on the feed so far (FEED_ADS_JS).
    private fun feedAds() = AdCount.parse(eval(FEED_ADS_JS))

    // What adblock.js did on the reels so far (REEL_ADS_JS).
    private fun reelAds() = AdCount.parse(eval(REEL_ADS_JS))

    // Posts (or reels) seen, the ads adblock.js hid among them, the ones still showing an ad label
    // and the last such label's shape (tag, class, colour: no text).
    private data class AdCount(val posts: Int, val hidden: Int, val missed: Int, val missedShape: String) {
        companion object {
            fun parse(json: String): AdCount {
                val parts = json.trim('"').split('|')
                if (parts.size < 4) return AdCount(0, 0, 0, "")
                return AdCount(
                    parts[0].toIntOrNull() ?: 0, parts[1].toIntOrNull() ?: 0, parts[2].toIntOrNull() ?: 0, parts[3]
                )
            }
        }
    }

    @Test
    fun theFeed_keepsTheDeviceInfoKeyTheRelayoutClears() {
        // After an empty PiP window, RELAYOUT_PAGE_JS removes this key so that m.facebook.com sends
        // the screen's size again and lays pages out for the full window, not the PiP one's.
        open("https://m.facebook.com/")
        val found = app.waitFor(30_000) {
            eval("sessionStorage.getItem('$DEVICE_INFO_KEY') !== null") == "true"
        }
        if (!found) {
            val keys = eval(
                "Object.keys(sessionStorage).filter(function (k) { return k.indexOf('sharedprefs') === 0; }).join(',')"
            ).trim('"')
            fail("m.facebook.com no longer notes in sessionStorage when it sent the device's details " +
                "($DEVICE_INFO_KEY): RELAYOUT_PAGE_JS can't make it lay pages out for the full window " +
                "after an empty PiP window. Its keys now: ${keys.ifEmpty { "none" }}")
        }
    }

    // The chat the Messages layer checks open (FB_TEST_CHAT, passed as -e fbChat): one of the
    // test account's chats with a photo and a shared reel in it. Null, with a note, when unset.
    private fun testChat(): String? {
        val chat = InstrumentationRegistry.getArguments().getString("fbChat")?.trim()?.takeIf { it.isNotEmpty() }
        if (chat == null) note("FB_TEST_CHAT isn't set: the Messages layer checks didn't run")
        return chat
    }

    // Messages in desktop mode on, and the chat opened from the main view as a tap on a Messages
    // link would: in the Messages layer. Returns the layer.
    private fun openChatInTheLayer(chat: String): WebView {
        runBlocking { SettingsDataStore(app.context).setMessagesDesktop(true) }
        open("https://m.facebook.com/messages/t/$chat/")
        assertTrue("a Messages link never opened the Messages layer (${appState()})", app.waitFor(30_000) {
            app.layerWebView()?.let { app.url(it).contains("/messages/") } == true
        })
        return app.layerWebView()!!
    }

    // The layer on screen: one kept out of sight behind a reel (MessagesLayerState.hidden) doesn't count.
    private fun layerOpen() = app.webViews().drop(1).any { webView -> app.onActivity { webView.visibility == View.VISIBLE } }

    private fun layerShape(layer: WebView) = runCatching { app.eval(LAYER_SHAPE_JS, layer) }.getOrElse { "no layer page (${appState()})" }

    @Test
    fun aPhotoInAChat_fillsTheLayersViewer_andBackClosesIt() {
        val chat = testChat() ?: return
        val layer = openChatInTheLayer(chat)
        if (!app.waitFor(45_000) { app.eval(OPEN_CHAT_PHOTO_JS, layer) == "true" }) {
            fail("no photo to open in the test chat (FB_TEST_CHAT) (${layerShape(layer)})")
        }
        assertTrue(
            "the chat's photo viewer was never laid out for the phone (layer_photo_viewer.js: the photo " +
                "between two arrow columns) (${app.eval(PHOTO_VIEWER_SHAPE_JS, layer)})",
            app.waitFor(20_000) { app.eval(PHOTO_VIEWER_OPEN_JS, layer) == "true" }
        )

        app.pressBack()
        assertTrue("Back didn't close the photo (window.__astryxClosePhotoViewer)", app.waitFor(10_000) {
            app.eval(PHOTO_VIEWER_OPEN_JS, layer) == "false"
        })
        Thread.sleep(1500)
        assertTrue("Back closed the Messages layer too", layerOpen())
        assertTrue("Back left the chat (${layerShape(layer)})", app.eval("location.pathname", layer).contains("/t/"))
    }

    @Test
    fun aReelSharedInAChat_opensInTheMainView_andBackGoesBackToTheChat() {
        val chat = testChat() ?: return
        val layer = openChatInTheLayer(chat)
        if (!app.waitFor(45_000) { app.eval(OPEN_CHAT_REEL_JS, layer) == "true" }) {
            fail("no shared reel (a link to /reel/) in the test chat (FB_TEST_CHAT) (${layerShape(layer)})")
        }
        assertTrue("the reel didn't leave the Messages layer (messagesLayerExit) (${layerShape(layer)})", app.waitFor(20_000) {
            !layerOpen() && app.url(app.mainWebView()).let { it.startsWith("https://m.facebook.com/reel/") }
        })

        app.pressBack()
        assertTrue("Back on the reel never showed the chat again (MessagesLayerState)", app.waitFor(15_000) { layerOpen() })
        assertTrue("the layer came back somewhere other than the chat (${layerShape(layer)})", app.waitFor(15_000) {
            app.eval("location.pathname", layer).contains("/t/")
        })
    }

    @Test
    fun startOnFeeds_pressesTheMenusFeedsEntry() {
        open("https://m.facebook.com/bookmarks/#astryx-feeds")
        val pressed = app.waitFor(30_000) {
            eval("window.__astryxFeeds === true && location.pathname === '/'") == "true"
        }
        if (!pressed) fail("open_feeds.js never found the menu's Feeds entry (its icon or label) (${pageShape()})")
    }

    @Test
    fun facebookNotifications_readTheCountsFromTheTabBar() {
        val html = FacebookCountsWorker.fetchPage(app.context)
        assertNotNull("m.facebook.com/notifications/ couldn't be fetched (FacebookCountsWorker)", html)
        assertNotNull("no tab bar counts in m.facebook.com/notifications/ (FacebookCounts.parse)", FacebookCounts.parse(html!!))
    }

    companion object {
        // The prefix facebook-canary.yml looks for; the status code is one the runner doesn't use.
        private const val NOTE_PREFIX = "CANARY NOTE: "
        private const val NOTE_STATUS = 3
        // A public reel (shared with Public), for when the Reels tab has nothing for the account.
        private const val PUBLIC_REEL_URL = "https://m.facebook.com/reel/1360854416127071/"
        // The page's shape, for a failure message in the public issue (no page text and nothing of
        // the account's: the address without values, the title, counts, and the ids of Facebook's
        // own static icons, which open_feeds.js and scripts.js look for).
        private const val PAGE_SHAPE_JS =
            "(function () { var icons = Array.prototype.slice.call(document.querySelectorAll('img[src*=\"rsrc.php\"]'))" +
                ".map(function (i) { var m = (i.getAttribute('src') || '').match(/([A-Za-z0-9_-]{8,})\\.(png|gif|webp)/); return m ? m[1] : null; })" +
                ".filter(function (x) { return x; }).slice(0, 15);" +
                " return location.pathname + location.search.replace(/=[^&]*/g, '=') + ' title=' + document.title +" +
                " ' buttons=' + document.querySelectorAll('[role=button]').length + ' videos=' + document.querySelectorAll('video').length +" +
                " ' login=' + !!document.querySelector('input[name=pass]') +" +
                " ' feedsIcon=' + !!document.querySelector('img[src*=FTDJiLwLrcM]') + ' feedsLabel=' + !!document.querySelector('[aria-label=Feeds]') +" +
                " ' icons=' + icons.join(','); })()"
        // How far down the feed, and through how many reels, the Remove ads checks look for an ad.
        private const val FEED_SCREENS = 15
        private const val REELS_TO_SEE = 15

        // An ad label found by its text alone ("Sponsored", "Ad"; the test account uses Facebook in
        // English), independently of how adblock.js finds it: an element showing just the label,
        // once icon glyphs, direction marks, spaces and separators are left out. Its shape, for a
        // failure message: tag, class, colour and its parent's tag and class, no text.
        private const val AD_LABEL_FNS = """
            function canaryText(e) {
              return (e.textContent || '').replace(/[\uE000-\uF8FF\u200B-\u200F\u202A-\u202E\u2060-\u2069\uFEFF]/g, '')
                .replace(/[\s\u00B7\u2022|:-]+/g, ' ').trim().toLowerCase();
            }
            function canaryLabelIn(root, words) {
              var all = root.querySelectorAll('*');
              for (var i = 0; i < all.length; i++) {
                var e = all[i];
                if (e.children.length > 2 || !e.getClientRects().length) continue;
                var t = canaryText(e);
                if (words.indexOf(t) < 0) continue;
                // The innermost element showing it.
                for (var deeper = true; deeper;) {
                  deeper = false;
                  for (var c = e.firstElementChild; c; c = c.nextElementSibling) {
                    if (canaryText(c) === t) { e = c; deeper = true; break; }
                  }
                }
                return e;
              }
              return null;
            }
            function canaryShape(e) {
              var p = e.parentElement;
              return e.tagName.toLowerCase() + '.' + String(e.className || '').trim().replace(/\s+/g, '.') +
                ' color=' + (e.style.color || getComputedStyle(e).color) + ' in ' +
                (p ? p.tagName.toLowerCase() + '.' + String(p.className || '').trim().replace(/\s+/g, '.') : 'none');
            }
        """

        // The feed's posts in the boxes adblock.js hides (top-level ones), the ones it hid (hidden,
        // with the grey label it looks for inside) and the visible ones still showing a Sponsored
        // label, counted across scrolls: "posts|hidden|missed|shape".
        private const val FEED_ADS_JS = """
            (function () {
              $AD_LABEL_FNS
              var sel = 'div[data-dcm-id="1"][data-mcomponent="MContainer"]';
              var grey = 'div.native-text.rslh > span.f5[style*="color:#8a8d91"]';
              var s = window.__canaryFeed || (window.__canaryFeed = { posts: 0, hidden: 0, missed: 0, shape: '' });
              var all = document.querySelectorAll(sel);
              for (var i = 0; i < all.length; i++) {
                var p = all[i];
                if (!p.__canarySeen && !(p.parentElement && p.parentElement.closest(sel))) { p.__canarySeen = true; s.posts++; }
                if (p.style.display === 'none') {
                  if (!p.__canaryHidden && p.querySelector(grey)) { p.__canaryHidden = true; s.hidden++; }
                  continue;
                }
                if (p.__canaryMissed) continue;
                var label = canaryLabelIn(p, ['sponsored']);
                if (label) { p.__canaryMissed = true; s.missed++; s.shape = canaryShape(label); }
              }
              return [s.posts, s.hidden, s.missed, s.shape].join('|');
            })()
        """

        // The feed's structure, for a failure message (no text): how many of Facebook's boxes there
        // are, the commonest data-dcm-id values of its MContainers, the text blocks and grey spans
        // adblock.js looks at, and how far the page scrolled.
        private const val FEED_SHAPE_JS = """
            (function () {
              var ids = {};
              var boxes = document.querySelectorAll('[data-mcomponent="MContainer"]');
              for (var i = 0; i < boxes.length; i++) {
                var id = boxes[i].getAttribute('data-dcm-id') || 'none';
                ids[id] = (ids[id] || 0) + 1;
              }
              var top = Object.keys(ids).sort(function (a, b) { return ids[b] - ids[a]; }).slice(0, 5)
                .map(function (k) { return k + ':' + ids[k]; }).join(',');
              return 'components=' + document.querySelectorAll('[data-mcomponent]').length +
                ' containers=' + boxes.length + ' dcmIds=' + (top || 'none') +
                ' textBlocks=' + document.querySelectorAll('div.native-text.rslh').length +
                ' greySpans=' + document.querySelectorAll('span.f5[style*="color:#8a8d91"]').length +
                ' scrollY=' + Math.round(window.scrollY) + ' height=' + document.documentElement.scrollHeight;
            })()
        """

        // The reels seen (div.vertically-snappable), the ones adblock.js emptied as ads and the ones
        // still showing an ad label, counted as the user moves on: "reels|hidden|missed|shape".
        private const val REEL_ADS_JS = """
            (function () {
              $AD_LABEL_FNS
              var s = window.__canaryReels || (window.__canaryReels = { reels: 0, hidden: 0, missed: 0, shape: '' });
              var all = document.querySelectorAll('div.vertically-snappable');
              for (var i = 0; i < all.length; i++) {
                var r = all[i];
                if (!r.__canarySeen) { r.__canarySeen = true; s.reels++; }
                if (r.dataset.adHidden === 'true') {
                  if (!r.__canaryHidden) { r.__canaryHidden = true; s.hidden++; }
                  continue;
                }
                if (r.__canaryMissed) continue;
                var label = canaryLabelIn(r, ['ad', 'sponsored']);
                if (label) { r.__canaryMissed = true; s.missed++; s.shape = canaryShape(label); }
              }
              return [s.reels, s.hidden, s.missed, s.shape].join('|');
            })()
        """

        // Brings the next reel below the one on screen into view, as a swipe up would; false when
        // there's none (yet).
        private const val NEXT_REEL_JS =
            "(function () { var all = document.querySelectorAll('div.vertically-snappable');" +
                " for (var i = 0; i < all.length; i++) { if (all[i].getBoundingClientRect().top > 10) {" +
                " all[i].scrollIntoView(); return true; } } return false; })()"

        // Starts fetching the playing reel's data-video-url as download_content.js does (only its
        // first bytes are read), the answer in window.__canaryVideoFile: "status=.. type=.. box=..".
        // Returns "fetching", or why it couldn't (no address in the result: it carries a signature).
        private const val VIDEO_FILE_JS = """
            (function () {
              var videos = document.querySelectorAll('video'), v = null;
              for (var i = 0; i < videos.length; i++) { if (!videos[i].paused) { v = videos[i]; break; } }
              v = v || videos[0];
              if (!v) return 'no video';
              var player = v.closest('[data-video-url]');
              if (!player) {
                var chain = [];
                for (var e = v.parentElement; e && chain.length < 8; e = e.parentElement) {
                  if (e.getAttribute('data-mcomponent')) chain.push(e.getAttribute('data-mcomponent'));
                }
                return 'no [data-video-url] around the video (its players: ' + (chain.join(',') || 'none') + ')';
              }
              var url = player.getAttribute('data-video-url') || '';
              if (url.indexOf('https://') !== 0) return 'data-video-url is not https (' + url.slice(0, 6) + ')';
              window.__canaryVideoFile = 'pending';
              fetch(url).then(function (r) {
                var type = r.headers.get('content-type') || '';
                if (!r.body) { window.__canaryVideoFile = 'status=' + r.status + ' type=' + type + ' box=none'; return; }
                var reader = r.body.getReader();
                return reader.read().then(function (c) {
                  reader.cancel();
                  var b = c.value || new Uint8Array(0);
                  var box = String.fromCharCode.apply(null, Array.prototype.slice.call(b.subarray(4, 8)));
                  window.__canaryVideoFile = 'status=' + r.status + ' type=' + type + ' box=' + box +
                    ' player=' + (player.getAttribute('data-mcomponent') || 'none');
                });
              }).catch(function (e) { window.__canaryVideoFile = 'error=' + e.name; });
              return 'fetching';
            })()
        """

        // sessionStorage's note that m.facebook.com sent the device's details (RELAYOUT_PAGE_JS).
        private const val DEVICE_INFO_KEY = "sharedprefs.web.last_device_info"

        // The Messages layer's page, for a failure message (no text, nothing of the account's): the
        // address's shape, and counts of what the chat checks look for.
        private const val LAYER_SHAPE_JS =
            "(function () { var big = Array.prototype.filter.call(document.querySelectorAll('img'), function (i) {" +
                " var r = i.getBoundingClientRect(); return r.width >= 100 && r.height >= 100; }).length;" +
                " return location.pathname.replace(/\\d{3,}/g, 'N') + ' main=' + !!document.querySelector('[role=main]') +" +
                " ' imgs=' + document.querySelectorAll('img').length + ' bigImgs=' + big +" +
                " ' reelLinks=' + document.querySelectorAll('a[href*=\"/reel/\"]').length +" +
                " ' inputs=' + document.querySelectorAll('input').length + ' dialogs=' + document.querySelectorAll('[role=dialog]').length; })()"

        // Opens the chat's largest photo (at least 100 px each way, not a shared reel's picture) as
        // a tap would; false while there's none.
        private const val OPEN_CHAT_PHOTO_JS = """
            (function () {
              var root = document.querySelector('[role="main"]') || document.body;
              var imgs = root.querySelectorAll('img'), best = null, area = 0;
              for (var i = 0; i < imgs.length; i++) {
                var r = imgs[i].getBoundingClientRect();
                if (r.width < 100 || r.height < 100 || imgs[i].closest('a[href*="/reel/"]')) continue;
                if (r.width * r.height > area) { area = r.width * r.height; best = imgs[i]; }
              }
              if (!best) return false;
              best.scrollIntoView({ block: 'center' });
              (best.closest('[role="button"], a') || best).click();
              return true;
            })()
        """

        // The photo viewer laid out by layer_photo_viewer.js, on screen.
        private const val PHOTO_VIEWER_OPEN_JS =
            "Array.prototype.some.call(document.querySelectorAll('[data-astryx-photo-row]'), function (e) {" +
                " return e.isConnected && e.getBoundingClientRect().width > 0; })"

        // What layer_photo_viewer.js would have found, for a failure message: the photos shown
        // with object-fit: contain and, for each, the child counts of its ancestors.
        private const val PHOTO_VIEWER_SHAPE_JS = """
            (function () {
              var out = [];
              var imgs = document.querySelectorAll('img');
              for (var i = 0; i < imgs.length && out.length < 3; i++) {
                if (getComputedStyle(imgs[i]).objectFit !== 'contain') continue;
                var counts = [];
                for (var e = imgs[i].parentElement, n = 0; e && n < 8; e = e.parentElement, n++) counts.push(e.children.length);
                out.push(Math.round(imgs[i].getBoundingClientRect().width) + 'px kids=' + counts.join(','));
              }
              return 'contained photos: ' + (out.join('; ') || 'none');
            })()
        """

        // Opens the chat's latest shared reel (a link to a /reel/ page) as a tap would; false while
        // there's none.
        private const val OPEN_CHAT_REEL_JS = """
            (function () {
              var links = document.querySelectorAll('a[href*="/reel/"]');
              if (!links.length) return false;
              var link = links[links.length - 1];
              link.scrollIntoView({ block: 'center' });
              link.click();
              return true;
            })()
        """

        // What the page's videos are doing, for a failure message: a reel that doesn't play on one
        // device only (an emulator without the codec, an error from Facebook's server) shows here.
        private const val VIDEO_STATE_JS =
            "(function () { var v = document.querySelectorAll('video'); if (!v.length) return location.pathname + ': no video';" +
                " return location.pathname + ': ' + Array.prototype.slice.call(v, 0, 3).map(function (e) {" +
                " return 'paused=' + e.paused + ' ready=' + e.readyState + ' net=' + e.networkState +" +
                " ' error=' + (e.error ? e.error.code : 'none') + ' src=' + (e.currentSrc || '').slice(0, 12); }).join('; '); })()"
        private const val PLAYING_JS =
            "(function (v) { return !!v && !v.paused && v.readyState >= 2; })(document.querySelector('video'))"
        // The detector's video played again, unmuted, as a tap would (Facebook may pause it in the
        // background).
        private const val PLAY_DETECTED_JS =
            "(function (v) { if (v) { v.muted = false; var p = v.play(); if (p && p.catch) p.catch(function () {}); } })" +
                "(window.__astryxLastActiveVideo || document.querySelector('video')); 1"
        // The detector's video's playback time while it counts as playing (getActiveVideo in
        // pip_video_detector.js), -1 otherwise.
        private const val DETECTED_PLAYING_TIME_JS =
            "(function (v) { return v && !v.paused && !v.ended && v.readyState > 2 && !v.muted && v.volume > 0" +
                " ? v.currentTime : -1; })(window.__astryxLastActiveVideo)"
    }
}
