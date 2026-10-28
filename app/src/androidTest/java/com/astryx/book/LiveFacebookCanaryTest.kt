package com.astryx.book
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eepiemi.materialbook.data.local.SettingsDataStore
import com.astryx.book.notifications.FacebookCounts
import com.astryx.book.notifications.FacebookCountsWorker
import com.astryx.book.utils.ShareMediaQueue
import com.astryx.book.utils.ShareTarget
import com.astryx.book.utils.TextZoom
import com.astryx.book.utils.adsPixelBlocks
import com.astryx.book.utils.lookUpPhotoPost
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.json.JSONObject
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

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
        dropPipNarrowViewportCookie()
        // With the screen off the app's WebView is never created (it's made during layout).
        shell("input keyevent KEYCODE_WAKEUP")
    }

    private fun shell(command: String): List<String> {
        val out = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(out).bufferedReader().readLines()
    }

    // PiP can leave a host-only vpd cookie with the PiP window's size on m.facebook.com, which
    // shadows the .facebook.com one and lays pages out PiP-narrow (CLAUDE.md): the app tests' PiP
    // runs leave one on a shared emulator, and on the narrow Home the first story-sized card was
    // someone's story, not Create story. Removed (only the host-only one), and noted, so the checks
    // see Facebook's normal layout.
    private fun dropPipNarrowViewportCookie() {
        val manager = CookieManager.getInstance()
        val vpds = manager.getCookie("https://m.facebook.com").orEmpty().split(';').count { it.trim().startsWith("vpd=") }
        if (vpds < 2) return
        note("a second (host-only) vpd cookie was on m.facebook.com, as PiP leaves: removed before the checks")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            manager.setCookie("https://m.facebook.com", "vpd=; Max-Age=0; Path=/")
            manager.flush()
        }
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
    private fun logLines(tag: String): List<String> =
        shell("logcat -d -s $tag:D").filter { "$tag: " in it }.map { it.substringAfter("$tag: ") }

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
        // What the window holds instead, when there's no WebView: its views' classes, a few deep.
        val views = runCatching {
            app.onActivity { a ->
                val names = mutableListOf<String>()
                fun walk(v: android.view.View, depth: Int) {
                    if (names.size >= 25) return
                    names += "${"-".repeat(depth)}${v.javaClass.simpleName}${if (v.isShown) "" else "(hidden)"}"
                    if (v is android.view.ViewGroup && depth < 6) for (i in 0 until v.childCount) walk(v.getChildAt(i), depth + 1)
                }
                walk(a.window.decorView, 0)
                names.joinToString(" ")
            }
        }.getOrElse { it.javaClass.simpleName }
        val renderer = runCatching { logLines("Astryxbook").filter { "renderer gone" in it } }.getOrDefault(emptyList())
        // Awake, Asleep or Dozing: no WebView is made with the screen off.
        val screen = runCatching { shell("dumpsys power").firstOrNull { "mWakefulness=" in it }?.substringAfter("=")?.trim() }
            .getOrNull() ?: "?"
        return "webViews=$webViews activity=$activity screen=$screen views=[$views] renderer lost: ${renderer.joinToString(" | ").ifEmpty { "no" }}"
    }

    // The page showing what [js] checks for, or a failure ([message]). Facebook's pages sometimes stay
    // blank on GitHub's emulator for good (only their shell: Home at /?wtsid=…, three runs on
    // 2026-10-25; the public reel, "title=Facebook buttons=0", 2026-10-28): reloaded once then, with a
    // note, before the check fails.
    private fun awaitFeed() = awaitPage("the feed never loaded", FEED_SHOWN_JS)

    private fun awaitPage(message: String, js: String) {
        if (app.waitFor(30_000) { eval(js) == "true" }) return
        note("$message within 30 s (${pageShape()}): reloaded once")
        eval("location.reload(); 1")
        Thread.sleep(2000)
        assertTrue("$message, even after a reload (${pageShape()})", app.waitFor(30_000) { eval(js) == "true" })
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
        // A session Facebook ended shows its logged-out Home ("Facebook - log in or sign up", at /,
        // with Log in and Create account buttons but no password box; 2026-10-27) and drops the
        // login cookie, so the page's own title and the cookie are read too, once it has loaded.
        app.waitFor(10_000) { eval("document.readyState") == "\"complete\"" }
        val loginCookie = CookieManager.getInstance().getCookie("https://m.facebook.com")?.contains("c_user=") == true
        val loginPage = eval(
            "location.pathname.indexOf('/login') === 0 || !!document.querySelector('input[name=\"pass\"]') || " +
                "/log in/i.test(document.title)"
        ) == "true"
        if (!loginCookie || loginPage) fail("Facebook session expired: log in again (FB_TEST_COOKIES) (${pageShape()})")
        eval("location.href = '$url'; 1")
    }

    // The Reels tab with a reel playing. The test account on GitHub gets "Content not found" for
    // the Reels tab (no reels for it there): a public reel, opened by its address, is used instead.
    private fun openPlayingReel() {
        open("https://m.facebook.com/reels/")
        // A public reel when the tab has none to show, by 20 s: "Content not found" (which came
        // later than that once, 2026-10-25, failing the check on /reels/), or no video yet.
        app.waitFor(20_000) { eval("document.title === 'Content not found' || !!document.querySelector('video')") == "true" }
        if (eval("!!document.querySelector('video')") != "true") {
            eval("location.href = '$PUBLIC_REEL_URL'; 1")
            Thread.sleep(3000)
            awaitPage("the public reel never showed its video", "!!document.querySelector('video')")
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
        // In PiP Facebook can write the PiP window's size into a host-only vpd cookie, which laid
        // later pages out PiP-narrow: the app drops it as it comes back (MainActivity.dropPipViewportCookie).
        assertTrue("a PiP-narrow vpd cookie outlived PiP (${eval(VPD_WIDTHS_JS)})", app.waitFor(10_000) {
            eval(NO_NARROW_VPD_JS) == "true"
        })
    }

    // A plain picture of [width] x [height] shared to the app's [alias] (ShareToFeed, ...), as the
    // share sheet would: served through the app's own FileProvider.
    private fun sharePicture(alias: String, width: Int = 64, height: Int = 64) {
        val dir = File(app.context.cacheDir, "clipboard_images").apply { mkdirs() }
        val file = File(dir, "canary.png")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(30, 120, 200)) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(app.context, "${app.context.packageName}.fileprovider", file)

        app.start(
            Intent(Intent.ACTION_SEND)
                .setComponent(ComponentName(app.context.packageName, "com.eepiemi.materialbook.$alias"))
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    @Test
    fun sharingAPhotoToFeed_opensTheComposerWithIt() {
        sharePicture("ShareToFeed")
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
        awaitFeed()
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
            fail("${ads.missed} Sponsored labels stayed on the feed: adblock.js no longer finds feed ads (by its " +
                "grey span.f5 in div.native-text.rslh, hiding the post's div[data-dcm-id=\"1\"][data-mcomponent=MContainer]). " +
                "The label: ${ads.missedShape}")
        }
        // The test account has no friends, so its feed may hold no posts at all (suggestions only):
        // no post boxes is noted, not failed. An ad left showing fails above either way.
        if (ads.posts == 0) {
            note("no feed posts in the boxes adblock.js hides (div[data-dcm-id=\"1\"][data-mcomponent=MContainer]) " +
                "in $FEED_SCREENS screens (${runCatching { eval(FEED_SHAPE_JS) }.getOrDefault("")})")
            return
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

    // The chat the Messages layer checks open (-e fbChat): one of the test account's chats with a
    // photo and a shared reel in it, given as its address, what follows /messages/ in it
    // ("e2ee/t/<id>" for an end-to-end encrypted chat) or a bare id. Returns that path ("t/<id>",
    // "e2ee/t/<id>"); without it the check is skipped. Only a device that holds the chat's keys
    // shows an end-to-end encrypted chat's messages: GitHub's emulator, logged in by cookies, gets
    // the chat's restore prompt instead (2026-10-24), so these checks run on the local emulator.
    private fun testChat(): String {
        val chat = InstrumentationRegistry.getArguments().getString("fbChat")?.trim()?.takeIf { it.isNotEmpty() }
        assumeTrue("the Messages layer checks need a test chat (-e fbChat)", chat != null)
        return chatPath(chat!!)
    }

    // Messages in desktop mode on, and the chat opened from the main view as a tap on a Messages
    // link would: in the Messages layer (messagesDesktopUrl keeps an e2ee/t/<id> path). An
    // end-to-end encrypted chat opened at its address shows the chat list first at phone width
    // (Facebook's): its row is tapped, as the user would. Returns the layer.
    private fun openChatInTheLayer(chat: String): WebView {
        runBlocking { SettingsDataStore(app.context).setMessagesDesktop(true) }
        open("https://m.facebook.com/messages/$chat/")
        assertTrue("a Messages link never opened the Messages layer (${appState()})", app.waitFor(30_000) {
            app.layerWebView()?.let { app.url(it).contains("/messages/") } == true
        })
        val layer = app.layerWebView()!!
        assertTrue("the test chat (FB_TEST_CHAT) never showed in the Messages layer (${layerShape(layer)})",
            app.waitFor(45_000) { app.eval(chatShownJs(chat), layer) == "true" })
        return layer
    }

    private fun layerShape(layer: WebView) = runCatching { app.eval(LAYER_SHAPE_JS, layer) }.getOrElse { "no layer page (${appState()})" }

    @Test
    fun aPhotoInAChat_fillsTheLayersViewer_andBackClosesIt() {
        val chat = testChat()
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
        assertTrue("Back closed the Messages layer too", app.layerOpen())
        assertTrue("Back left the chat (${layerShape(layer)})", app.eval("location.pathname", layer).contains("/t/"))
    }

    @Test
    fun aReelSharedInAChat_opensInTheMainView_andBackGoesBackToTheChat() {
        val chat = testChat()
        val layer = openChatInTheLayer(chat)
        if (!app.waitFor(45_000) { app.tapOn(layer, CHAT_REEL_JS) }) {
            fail("no shared reel (a link to /reel/ or /share/r/) in the test chat (FB_TEST_CHAT) (${layerShape(layer)})")
        }
        assertTrue("the reel didn't leave the Messages layer (messagesLayerExit) (${layerShape(layer)})", app.waitFor(20_000) {
            !app.layerOpen() && app.url(app.mainWebView()).let { it.startsWith("https://m.facebook.com/reel/") }
        })

        app.pressBack()
        assertTrue("Back on the reel never showed the chat again (MessagesLayerState)", app.waitFor(15_000) { app.layerOpen() })
        assertTrue("the layer came back somewhere other than the chat (${layerShape(layer)})", app.waitFor(15_000) {
            app.eval("location.pathname", layer).contains("/t/")
        })
        // Under the chat, the main view goes back to where it was: each reel opened from a chat
        // stayed there otherwise, and once Messages closed, every Back showed one of them again.
        assertTrue("the main view stayed on the reel under the chat (MessagesLayerState.mainBeforeReel)",
            app.waitFor(10_000) { !app.url(app.mainWebView()).startsWith("https://m.facebook.com/reel/") })
    }

    @Test
    fun aPostSharedInAChat_opensInTheMainView_andBackShowsTheChatAtIt() {
        val chat = testChat()
        val layer = openChatInTheLayer(chat)
        if (!app.waitFor(45_000) { app.tapOn(layer, CHAT_POST_JS) }) {
            fail("no shared post (a link to /share/p/) in the test chat (${layerShape(layer)})")
        }
        // Like a reel: the post in the main view (m.facebook.com shows it as Home, the post at the
        // top), the chat kept out of sight behind it.
        assertTrue("the shared post didn't leave the Messages layer (messagesLayerExit) (${layerShape(layer)})",
            app.waitFor(30_000) { !app.layerOpen() })
        // Shown as Home, with the post as a "From your link" card further down: scrolled to it.
        assertTrue("the post opened from the chat isn't at the top of the main view (linked_post.js)",
            app.waitFor(15_000) { eval(LINKED_POST_AT_TOP_JS) == "true" })

        app.pressBack()
        assertTrue("Back on the post never showed the chat again (MessagesLayerState)", app.waitFor(15_000) { app.layerOpen() })
        assertTrue("Back from the shared post didn't show the chat (${layerShape(layer)})", app.waitFor(20_000) {
            app.eval(CHAT_OPEN_JS, layer) == "true"
        })
        // The same chat, where it was: not reloaded at its latest messages.
        assertTrue("the chat came back without the shared post on screen",
            app.waitFor(10_000) { app.eval(CHAT_POST_ON_SCREEN_JS, layer) == "true" })
    }

    @Test
    fun aPostThenAReelFromAChat_backEndsOnTheFeed_withNothingLeftBehind() {
        // The phone's sequence (2026-10-25): each Back after Messages closed played the reel again.
        val chat = testChat()
        val layer = openChatInTheLayer(chat)
        val main = app.mainWebView()
        val start = app.onActivity { main.copyBackForwardList().currentIndex }
        val backToTheChat = { what: String ->
            app.pressBack()
            assertTrue("Back on the $what never showed the chat again", app.waitFor(15_000) {
                app.layerOpen() && app.eval(CHAT_OPEN_JS, layer) == "true"
            })
        }

        if (!app.waitFor(45_000) { app.tapOn(layer, CHAT_POST_JS) }) fail("no shared post in the test chat (${layerShape(layer)})")
        assertTrue("the shared post didn't leave the Messages layer", app.waitFor(30_000) { !app.layerOpen() })
        backToTheChat("post")
        if (!app.waitFor(45_000) { app.tapOn(layer, CHAT_REEL_JS) }) fail("no shared reel in the test chat (${layerShape(layer)})")
        assertTrue("the reel didn't leave the Messages layer", app.waitFor(30_000) {
            !app.layerOpen() && app.url(main).startsWith("https://m.facebook.com/reel/")
        })
        backToTheChat("reel")
        assertTrue("the main view didn't go back to where it was before the post and the reel", app.waitFor(10_000) {
            app.onActivity { main.copyBackForwardList().currentIndex } <= start
        })

        app.pressBack()
        assertTrue("Back in the chat didn't show the chat list", app.waitFor(10_000) {
            app.layerOpen() && app.eval(chatListShownJs(chat), layer) == "true"
        })
        app.pressBack()
        assertTrue("Back on the chat list didn't close Messages", app.waitFor(10_000) { !app.layerOpen() })
        Thread.sleep(2000)
        // Nothing the chat opened is left in the main view, on screen or behind it.
        val pages = app.onActivity {
            val history = main.copyBackForwardList()
            (0..history.currentIndex).map { history.getItemAtIndex(it).url }
        }
        val left = pages.filter { "/reel/" in it || "/share/p/" in it || "/posts/" in it }
        assertTrue("the main view kept what the chat opened: ${left.map { it.replace(Regex("\\d{6,}"), "N").substringBefore('?') }}",
            left.isEmpty())
    }

    @Test
    fun aReelThatComesBackAsAStory_opensItsReelPage() {
        // Some reels' share links (a reel posted as a video in a post) come back as the story's
        // address, which shows the reel as a post on Home (seen on a Galaxy A56, 2026-10-25; the
        // test account's links go to /reel/<id> directly). Followed inside Messages, such a story
        // opens the reel's own page in the main view (MessagesDesktop reelOfStory); this checks
        // Facebook still shows the reel at /reel/<story_fbid>. No chat needed: Messages' inbox.
        runBlocking { SettingsDataStore(app.context).setMessagesDesktop(true) }
        open("https://m.facebook.com/messages/")
        assertTrue("Messages never opened in the layer (${appState()})", app.waitFor(30_000) {
            app.layerWebView()?.let { app.url(it).contains("/messages") } == true
        })
        val layer = app.layerWebView()!!
        // Once the inbox has loaded: a move made while it still loads was lost to the page's own.
        assertTrue("the Messages inbox never finished loading (${layerShape(layer)})", app.waitFor(30_000) {
            app.onActivity { layer.progress } == 100 && app.eval("!!window.__astryxLayerNavInstalled", layer) == "true"
        })
        Thread.sleep(2000)
        app.eval("location.href = '$STORY_OF_A_REEL'; 1", layer)
        assertTrue("a reel's story didn't open the reel's page in the main view (MessagesDesktop reelOfStory) (${appState()})",
            app.waitFor(30_000) { app.url(app.mainWebView()).startsWith("https://m.facebook.com/reel/$STORY_REEL_ID") })
        assertTrue("Facebook no longer shows the reel at /reel/<story_fbid> (${pageShape()})", app.waitFor(30_000) {
            eval("document.querySelectorAll('video').length > 0") == "true"
        })
    }

    @Test
    fun backInAChat_goesToTheChatList_andThenClosesTheLayer() {
        val chat = testChat()
        val layer = openChatInTheLayer(chat)
        app.pressBack()
        assertTrue(
            "Back in the chat didn't go to the chat list (messages_layer_nav.js chatBackButton: the header's Back " +
                "button, right beside the chat's name) (${layerShape(layer)})",
            app.waitFor(10_000) { !app.layerOpen() || app.eval(chatListShownJs(chat), layer) == "true" }
        )
        assertTrue("Back in the chat closed the Messages layer (no Back button found in the chat's header)", app.layerOpen())
        app.pressBack()
        assertTrue("Back on the chat list didn't close the Messages layer", app.waitFor(10_000) { !app.layerOpen() })
    }

    @Test
    fun sharingAPhotoToAGroup_opensItsComposerWithIt() {
        sharePicture("ShareToGroup")
        assertTrue("the share never opened the groups (GROUPS_PAGE_URL) (${appState()})", app.waitFor(45_000) {
            app.url(app.mainWebView()).contains("/groups")
        })
        Thread.sleep(3000)
        // The user picks the group: the public test group the test account joined.
        eval("location.href = '$TEST_GROUP_URL'; 1")
        assertTrue(
            "the photo never reached the group's composer (share_media.js groupPhotoButton: the Photo button, a " +
                "data-action-id container on a group's page) (${pageShape()})",
            app.waitFor(60_000) { eval("document.querySelectorAll('img[src^=\"data:image\"]').length > 0") == "true" }
        )
    }

    @Test
    fun sharingAPhotoToYourStory_laysOutTheStoryEditor() {
        sharePicture("ShareToStory", 720, 1280)
        assertTrue("the share never opened the story creator in the Messages layer (${appState()})", app.waitFor(45_000) {
            app.layerWebView()?.let { app.url(it).contains("/stories/create") } == true
        })
        val layer = app.layerWebView()!!
        var shape = ""
        val laidOut = app.waitFor(60_000) {
            shape = app.eval(STORY_LAYOUT_JS, layer).trim('"')
            shape.startsWith("ok ")
        }
        if (!laidOut) {
            fail("story_layout.js never laid out the story editor with the shared photo (found by role: the " +
                "preview, the tools sidebar, Discard / Share): $shape")
        }
    }

    @Test
    fun theStoryEditorsMusicList_isClosedByATapOnDiscard() {
        // Facebook's popups aren't modal (Share posted a story with the music list open, Galaxy A56,
        // 2026-10-27): story_layout.js dims Discard and Share while one is open and a tap on either
        // only closes it, through its close button. Tried with Discard, never Share: had the tap
        // gone through, it would only ask "Discard story?".
        sharePicture("ShareToStory", 720, 1280)
        assertTrue("the share never opened the story creator in the Messages layer (${appState()})", app.waitFor(45_000) {
            app.layerWebView()?.let { app.url(it).contains("/stories/create") } == true
        })
        val layer = app.layerWebView()!!
        assertTrue("story_layout.js never laid out the story editor", app.waitFor(60_000) {
            app.eval(STORY_LAYOUT_JS, layer).trim('"').startsWith("ok ")
        })
        val opened = app.eval(
            "(function () { var t = [].filter.call(document.querySelectorAll('[data-astryx-story=tool]'), function (b) {" +
                " return /music/i.test(b.innerText); })[0]; if (!t) return false; t.click(); return true; })()", layer
        ) == "true"
        if (!opened) {
            note("no Add music tool on the story editor (by its English label): the music list wasn't checked")
            return
        }
        assertTrue("the music list never opened as a popup (data-astryx-story-popup)", app.waitFor(15_000) {
            app.eval("document.documentElement.classList.contains('astryx-story-popup-open')", layer) == "true"
        })
        app.eval("document.querySelector('[data-astryx-story=actions] [role=button]').click(); 1", layer)
        assertTrue("a tap on Discard didn't close the music list (its close button, found at the popup's top right)",
            app.waitFor(5_000) {
                app.eval("document.documentElement.classList.contains('astryx-story-popup-open')", layer) == "false"
            })
        assertEquals("the tap on Discard went through with the music list open", "false", app.eval(
            "${shownJs("[role=dialog]")}.length > 0", layer
        ))
    }

    @Test
    fun facebooksStoriesPage_afterAStory_showsYourStoriesOrClosesTheStoryScreen() {
        // After Share to story the creator moves in-page to Facebook's stories page (seen on a
        // Galaxy A56, 2026-10-27; nothing is posted here): that page must still be at
        // www.facebook.com/stories, with the Add to story link open_own_story.js finds the user's
        // own card by; the user's stories open there (laid out full screen, layer_story_viewer.js)
        // and Back closes the story screen, or, with no story of the account's own, it closes at once.
        sharePicture("ShareToStory", 720, 1280)
        assertTrue("the share never opened the story creator in the Messages layer (${appState()})", app.waitFor(45_000) {
            app.layerWebView()?.let { app.url(it).contains("/stories/create") } == true
        })
        val layer = app.layerWebView()!!
        // With the photo still waiting, the stories page would open the creator again for it.
        assertTrue("the story creator never took the shared photo (share_media.js)", app.waitFor(60_000) {
            !ShareMediaQueue.pending(ShareTarget.STORY)
        })
        app.eval(STORIES_PAGE_FETCH_JS, layer)
        var stories = ""
        app.waitFor(30_000) {
            stories = app.eval("window.__astryxStoriesPage || ''", layer).trim('"')
            stories.isNotEmpty()
        }
        assertEquals("Facebook's stories page isn't at www.facebook.com/stories (status and address after redirects)",
            "200 /stories", stories)
        // The real page in the layer, loaded by the WebView itself: through the app's routing a full
        // load of it would close the layer (Share moves there in-page).
        app.onActivity { layer.loadUrl("https://www.facebook.com/stories/") }
        assertTrue("Facebook's stories page has no Add to story link, which open_own_story.js finds your card by " +
            "(${app.eval(STORIES_SHAPE_JS, layer)})", app.waitFor(45_000) {
            app.eval(ADD_TO_STORY_SHOWN_JS, layer) == "true"
        })
        // The address changes in-page, as Share's move does: the app reads it as Share's.
        app.eval("history.pushState({}, '', '/stories'); 1", layer)
        var opened = false
        assertTrue("the story screen neither showed your stories nor closed on Facebook's stories page " +
            "(${app.eval(STORIES_SHAPE_JS, layer)}; ${appState()})", app.waitFor(20_000) {
            opened = app.layerOpen() && app.eval("window.__astryxSharedStoryOpen === true", layer) == "true"
            opened || !app.layerOpen()
        })
        if (!opened) {
            note("the test account has no story of its own: only checked that the stories page closes the story screen")
            return
        }
        // Laid out at phone width only: the creator's wide layout (story_layout.js's viewport) can
        // leave this full load wider than that, while Share's in-page move keeps the creator's.
        val width = app.eval("innerWidth", layer).toFloatOrNull() ?: 0f
        if (width < 720) {
            assertTrue("your stories aren't laid out full screen (layer_story_viewer.js)", app.waitFor(10_000) {
                app.eval("document.documentElement.classList.contains('astryx-story-viewer')", layer) == "true"
            })
        } else {
            note("the stories page loaded ${width.toInt()} px wide: the full-screen layout wasn't checked")
        }
        app.pressBack()
        assertTrue("Back didn't close the story screen (${appState()})", app.waitFor { !app.layerOpen() })
    }

    @Test
    fun sendAsAMessage_opensTheChatList_andTheChatPickedGetsThePostsLink() {
        // A post's Share > Send as a message hands the post to the Messenger app
        // (intent://share/?link=<the post>…, messengerShareLink), which the app opens as the chat
        // list in the Messages layer; the chat the user opens gets the post's link in its message box
        // (put_link_in_chat.js). That part needs the test chat (-e fbChat, local only). Nothing is
        // sent: the box is emptied again.
        runBlocking { SettingsDataStore(app.context).setMessagesDesktop(true) }
        shell("logcat -c")
        open("https://m.facebook.com/")
        awaitPage("the feed has no post with a Share button (its icon, U+F037A)", "!!(${iconActionJs(SHARE_ICON)})")
        eval(RECORD_WINDOW_OPEN_JS)
        // Real taps: on GitHub's emulator (Android 34) a script's click on Send as a message opened
        // nothing (2026-10-28), Facebook's window.open wanting a user's tap. A tap on Share right as the
        // feed appears can go unanswered, and some posts' sheets have no Send as a message: every 4 s
        // the next post's Share is tapped (a tap on a sheet left open closes it first).
        var sharedAt = 0L
        var shares = 0
        var sheet = ""
        val main = app.mainWebView()
        val sent = app.waitFor(30_000) {
            if (app.tapOn(main, iconActionJs(MESSENGER_ICON))) return@waitFor true
            if (System.currentTimeMillis() - sharedAt > 4000) {
                sheet = eval(SHEET_ITEMS_JS).trim('"')
                app.tapOn(main, iconActionJs(SHARE_ICON, index = shares++))
                sharedAt = System.currentTimeMillis()
            }
            false
        }
        assertTrue("no post's Share sheet had Send as a message (its Messenger icon, U+F3031; $shares Share taps). " +
            "The last sheet: ${sheet.ifEmpty { "none" }}", sent)
        // The app read the post from Facebook's link (messengerShareLink) and opened the chat list for
        // it (MessagesLayer logs it). What the page opened is named on failure.
        val waiting = app.waitFor(30_000) {
            app.layerOpen() && app.layerWebView()?.let { app.url(it).contains("/messages") } == true &&
                logLines("Astryxbook").any { "Link to send: waiting for a chat" in it }
        }
        if (!waiting) {
            val opened = runCatching { eval("window.__canaryOpened || ''").trim('"') }.getOrDefault("")
            fail("Send as a message didn't open the chat list with the post to send (messengerShareLink: Facebook's " +
                "link changed?). The page opened: ${opened.replace(Regex("\\d{6,}"), "N").ifEmpty { "nothing seen" }} " +
                "(${appState()})")
        }
        val layer = app.layerWebView()!!
        val chat = InstrumentationRegistry.getArguments().getString("fbChat")?.trim()?.takeIf { it.isNotEmpty() }?.let { chatPath(it) }
        if (chat == null) {
            note("no test chat (-e fbChat): the post's link in a chat's message box wasn't checked")
            return
        }
        // Picked from the chat list, as the user would (chatShownJs taps its row).
        assertTrue("the test chat never showed in the Messages layer (${layerShape(layer)})",
            app.waitFor(45_000) { app.eval(chatShownJs(chat), layer) == "true" })
        // A user's tap on the box gives the page the focus the script needs (the row was clicked by script).
        assertTrue("the chat's message box couldn't be tapped", app.tapOn(layer, MESSAGE_BOX_FIND_JS))
        val tappedAt = System.currentTimeMillis()
        val placed = app.waitFor(20_000) {
            logLines("Astryxbook").any { "Link to send: in the chat's message box" in it }
        }
        // About 0.4 s on the emulator (2026-10-28; 4 s before the page watched for the box).
        val took = System.currentTimeMillis() - tappedAt
        if (placed && took > 2000) note("the post's link took ${took / 1000.0} s to reach the chat's message box")
        // Never sent: emptied whatever happened.
        shell("input keycombination 113 29")
        shell("input keyevent 67")
        assertTrue("the chat's message box never got the post's link (put_link_in_chat.js; box: " +
            "${app.eval(MESSAGE_BOX_TEXT_JS, layer).replace(Regex("\\d{6,}"), "N")})", placed)
        assertTrue("the post's link was left in the chat's message box", app.waitFor(5_000) {
            app.eval("($MESSAGE_BOX_TEXT_JS).trim() === ''", layer) == "true"
        })
    }

    @Test
    fun theFeed_hasItsAdsPixelBlocked() {
        // Every full m.facebook.com load adds Facebook's Google Ads pixel frames
        // (www.fbsbx.com/paid_ads_pixel/), which AdsPixelBlockingClient answers with an empty 204.
        val before = adsPixelBlocks.get()
        open("https://m.facebook.com/")
        awaitFeed()
        Thread.sleep(5000) // the pixel's frames come after the feed
        val frames = eval(FRAMES_JS).trim('"')
        val blocked = adsPixelBlocks.get() - before
        if (GOOGLE_ADS_FRAME.containsMatchIn(frames)) {
            fail("the feed has frames from Google's ad hosts: AdsPixelBlockingClient (isAdsPixelUrl) no longer " +
                "catches Facebook's ads pixel. Frames: $frames")
        }
        if (blocked == 0) {
            if ("paid_ads_pixel" in frames) {
                fail("the ads pixel's frames are on the feed but AdsPixelBlockingClient answered none of their " +
                    "requests (isAdsPixelUrl). Frames: $frames")
            }
            note("no ads pixel on the feed: Facebook no longer adds www.fbsbx.com/paid_ads_pixel/ frames, or " +
                "moved them. Frames: ${frames.ifEmpty { "none" }}")
        }
    }

    @Test
    fun aPhotosDesktopPage_namesThePostItsTilesShow() {
        // memory_photos.js opens a memory's photo in its post, which PhotoPost reads from the
        // desktop photo page's tracking data (top_level_post_id, photo_attachments_list). Checked
        // on a public post with three photos, memories being rare.
        val reply = AtomicReference<String>()
        lookUpPhotoPost(PUBLIC_POST_PHOTO) { reply.set(it) }
        if (!app.waitFor(45_000) { reply.get() != null }) {
            val log = runCatching { logLines("Astryxbook").filter { "Photo post of" in it }.takeLast(3) }.getOrDefault(emptyList())
            fail("the desktop photo page no longer names its post (PhotoPost: top_level_post_id, " +
                "content_owner_id_new, photo_attachments_list): a memory's photos open one at a time. " +
                "The app's log: ${log.joinToString(" | ").replace(Regex("\\d{6,}"), "N").ifEmpty { "nothing" }}")
        }
        val post = JSONObject(reply.get())
        assertTrue("PhotoPost named another post: ${post.getString("url").replace(Regex("\\d{6,}"), "N")}",
            post.getString("url").endsWith("/posts/$PUBLIC_POST/"))
        assertEquals("PhotoPost put the photo at the wrong tile", PUBLIC_POST_PHOTO_INDEX, post.getInt("index"))
    }

    @Test
    fun aReel_showsNoOpenAppButton() {
        openPlayingReel()
        Thread.sleep(3000)
        val parts = eval(OPEN_APP_JS).trim('"').split('|')
        val shown = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val hidden = parts.getOrNull(1)?.toIntOrNull() ?: 0
        if (shown > 0) {
            fail("Facebook's Open app button shows on the reel: scripts.js hideOpenAppButtons no longer finds it " +
                "(by its icon, U+F196C in div.fl.ac span). The button: ${parts.getOrElse(2) { "" }}")
        }
        if (hidden == 0) note("no Open app button on the reel: hideOpenAppButtons had nothing to hide")
    }

    @Test
    fun theFeed_isLaidOutAsTextWrapExpects() {
        // text_wrap.js (Wrap text when zoomed) relies on the server's layout: text blocks are
        // white-space: pre with the line breaks in the text, inside boxes of a fixed inline height.
        open("https://m.facebook.com/")
        awaitPage("the feed never loaded", "document.querySelectorAll('div.native-text').length > 0")
        eval("window.scrollBy(0, window.innerHeight); 1")
        Thread.sleep(2000)
        val counts = eval(TEXT_LAYOUT_JS).trim('"').split('|').map { it.toIntOrNull() ?: 0 }
        val blocks = counts.getOrElse(0) { 0 }
        val pre = counts.getOrElse(1) { 0 }
        val broken = counts.getOrElse(2) { 0 }
        val sized = counts.getOrElse(3) { 0 }
        assertTrue("no text blocks (div.native-text) on the feed (${pageShape()})", blocks > 0)
        assertTrue("the feed's text blocks are no longer white-space: pre ($pre of $blocks): text_wrap.js joins " +
            "and re-wraps only server-broken text", pre * 2 >= blocks)
        assertTrue("no feed text block sits in a box of a fixed inline height ($sized of $blocks): text_wrap.js " +
            "grows those boxes as the text wraps", sized > 0)
        if (broken == 0) note("no feed text block had the server's line breaks in it ($pre white-space: pre blocks)")
    }

    @Test
    fun pageZoom_laysTheFeedOutForTheZoomedWidth() {
        runBlocking { SettingsDataStore(app.context).setTextZoom(PAGE_ZOOM) }
        try {
            open("https://m.facebook.com/")
            awaitFeed()
            // The server lays a page out for the vpd cookie written as the page before it loaded.
            eval("location.reload(); 1")
            Thread.sleep(3000)
            awaitPage("the feed never loaded again", FEED_SHOWN_JS)
            Thread.sleep(2000)
            val parts = eval(ZOOM_STATE_JS).trim('"').split('|')
            val z = parts.take(4).map { it.toDoubleOrNull() ?: 0.0 }
            val outer = z.getOrElse(0) { 0.0 }
            val inner = z.getOrElse(1) { 0.0 }
            val scale = z.getOrElse(2) { 0.0 }
            val laidOut = z.getOrElse(3) { 0.0 }
            val vpdWidths = parts.getOrElse(4) { "" }.split(',').filter { it.isNotEmpty() }
            val zoom = PAGE_ZOOM.toInt() / 100.0
            val expected = Math.round(outer / zoom).toDouble()
            assertTrue(
                "page_zoom.js's viewport no longer takes: the page is ${inner.toInt()} px wide at " +
                    "${"%.2f".format(scale)}x, not ${expected.toInt()} px at ${zoom}x",
                Math.abs(inner - expected) <= 2 && Math.abs(scale - zoom) < 0.05
            )
            assertTrue(
                "no vpd cookie reports the zoomed width (${vpdWidths.joinToString().ifEmpty { "no vpd" }} px, not " +
                    "${expected.toInt()}): Facebook no longer writes it as vpd=v1;<height>x<width>x<density> (page_zoom.js)",
                vpdWidths.any { w -> w.toDoubleOrNull()?.let { Math.abs(it - expected) <= 2 } == true }
            )
            // A host-only vpd that PiP left on m.facebook.com shadows the app's (CLAUDE.md).
            vpdWidths.filter { w -> w.toDoubleOrNull()?.let { it < expected * 0.6 } == true }.takeIf { it.isNotEmpty() }?.let {
                note("a PiP-narrow vpd cookie (${it.joinToString()} px wide) sits beside the zoomed one")
            }
            assertTrue(
                "m.facebook.com laid the feed out ${laidOut.toInt()} px wide in a ${inner.toInt()} px viewport: its " +
                    "server no longer follows the vpd cookie",
                laidOut <= inner + 4
            )
        } finally {
            // Facebook writes the window's own size into vpd again as the next page loads.
            runBlocking { SettingsDataStore(app.context).setTextZoom(TextZoom.DEFAULT) }
            app.finish()
            app.start()
            app.waitFor(30_000) { app.webViews().isNotEmpty() && eval("document.readyState") == "\"complete\"" }
        }
    }

    @Test
    fun theFeed_showsNoGetTheAppSheet() {
        // Facebook's "Facebook is better on the app" sheet comes up over the feed now and then;
        // scripts.js (dismissAppPrompt) presses it away.
        open("https://m.facebook.com/")
        awaitFeed()
        Thread.sleep(8000)
        val sheet = eval(APP_SHEET_JS).trim('"')
        if (sheet.isNotEmpty()) {
            fail("Facebook's \"better on the app\" sheet stayed over the feed: scripts.js dismissAppPrompt no longer " +
                "presses it away (a .dialog-screen .bottom.fixed-container with one h1, two buttons and a picture). " +
                "The sheet: $sheet")
        }
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
        // The public test group the test account joined ("Android Developer With Android Studio",
        // 2026-10-15) for group shares. Nothing is ever posted there.
        private const val TEST_GROUP_URL = "https://m.facebook.com/groups/355723031242469/"

        // A public post of NASA's with three photos, and its second photo (tiles count from 0).
        private const val PUBLIC_POST = "1658876782274358"
        private const val PUBLIC_POST_PHOTO = "1658876718941031"
        private const val PUBLIC_POST_PHOTO_INDEX = 1

        // The zoom the Page zoom check uses (Settings' 130%).
        private const val PAGE_ZOOM = "130"

        // The page's frames, as host and path (no query: the pixel's carries ids).
        private const val FRAMES_JS =
            "Array.prototype.map.call(document.querySelectorAll('iframe'), function (f) {" +
                " var a = document.createElement('a'); a.href = f.src || 'about:blank';" +
                " return a.hostname + a.pathname; }).join(',')"
        private val GOOGLE_ADS_FRAME =
            Regex("doubleclick|googletagmanager|googlesyndication|googleadservices|google\\.[a-z.]+/(?:pagead|ads)")

        // The reel page's Open app buttons, found by their label ("Open app"; the test account uses
        // Facebook in English) independently of scripts.js's icon: "shown|hidden|shape" (the last
        // shown one's shape: its spans' tag and class chain, no text).
        private const val OPEN_APP_JS = """
            (function () {
              var shown = 0, hidden = 0, shape = '';
              var buttons = document.querySelectorAll('div[role="button"]');
              for (var i = 0; i < buttons.length; i++) {
                var b = buttons[i];
                var t = (b.textContent || '').replace(/[\uD800-\uDBFF][\uDC00-\uDFFF]|[\uE000-\uF8FF]/g, '')
                  .replace(/\s+/g, ' ').trim().toLowerCase();
                if (t !== 'open app') continue;
                var r = b.getBoundingClientRect();
                if (b.style.display === 'none' || !r.width || !r.height) { hidden++; continue; }
                shown++;
                var spans = b.querySelectorAll('span'), chain = [];
                for (var s = 0; s < spans.length && chain.length < 3; s++) {
                  var p = spans[s].parentElement;
                  chain.push((p ? p.tagName.toLowerCase() + '.' + String(p.className).trim().replace(/\s+/g, '.') + ' > ' : '') +
                    'span.' + String(spans[s].className).trim().replace(/\s+/g, '.'));
                }
                shape = chain.join(', ');
              }
              return [shown, hidden, shape].join('|');
            })()
        """

        // The feed's text blocks (div.native-text), the white-space: pre ones, those holding line
        // breaks and those inside a box with a fixed inline px height (up to 6 levels up):
        // "blocks|pre|broken|sized".
        private const val TEXT_LAYOUT_JS = """
            (function () {
              var blocks = document.querySelectorAll('div.native-text'), pre = 0, broken = 0, sized = 0;
              for (var i = 0; i < blocks.length; i++) {
                var b = blocks[i];
                if (getComputedStyle(b).whiteSpace === 'pre') {
                  pre++;
                  if (/\n/.test(b.textContent || '')) broken++;
                }
                for (var e = b, n = 0; e && n < 6; e = e.parentElement, n++) {
                  if (/px$/.test(e.style.height)) { sized++; break; }
                }
              }
              return [blocks.length, pre, broken, sized].join('|');
            })()
        """

        // The window's width, the page's (layout) width and scale, the width the page is laid out
        // to and the widths in the vpd cookies ("v1;<height>x<width>x<density>"; a host-only one
        // PiP left can sit beside the .facebook.com one): "outer|inner|scale|scrollWidth|w1,w2".
        private const val ZOOM_STATE_JS = """
            (function () {
              var widths = document.cookie.split('; ').filter(function (c) { return c.indexOf('vpd=') === 0; })
                .map(function (c) { var m = decodeURIComponent(c.slice(4)).match(/^v1;(\d+)x(\d+)x/); return m ? m[2] : '?'; });
              return [window.outerWidth, window.innerWidth, window.visualViewport.scale,
                document.documentElement.scrollWidth, widths.join(',')].join('|');
            })()
        """

        // The "better on the app" sheet, if one shows: "" or its shape (no text).
        private const val APP_SHEET_JS = """
            (function () {
              var all = document.querySelectorAll('h1, div, span');
              for (var i = 0; i < all.length; i++) {
                var e = all[i];
                if (e.children.length > 2) continue;
                if ((e.textContent || '').replace(/\s+/g, ' ').trim() !== 'Facebook is better on the app') continue;
                var r = e.getBoundingClientRect();
                if (!r.width || !r.height) continue;
                var sheet = document.querySelector('.dialog-screen .bottom.fixed-container');
                return 'title=' + e.tagName.toLowerCase() + ' sheet=' + !!sheet +
                  (sheet ? ' buttons=' + sheet.querySelectorAll('[role="button"]').length + ' h1s=' +
                    sheet.querySelectorAll('h1').length + ' imgs=' + sheet.querySelectorAll('img').length : '');
              }
              return '';
            })()
        """

        // The story editor as story_layout.js tags it: "ok ..." once it has the preview (as wide as
        // the screen), the tools sidebar with two or more tools and the Discard / Share row, else
        // what it has.
        private const val STORY_LAYOUT_JS = """
            (function () {
              function count(v) { return document.querySelectorAll('[data-astryx-story="' + v + '"]').length; }
              var main = document.querySelector('[data-astryx-story="main"]');
              var r = main ? main.getBoundingClientRect() : null;
              var shape = 'main=' + count('main') + ' tools=' + count('tools') + ' tool=' + count('tool') +
                ' actions=' + count('actions') + ' mainWidth=' + (r ? Math.round(r.width) : 0) + '/' + window.innerWidth +
                ' error=' + !!window.__astryxStoryLayoutError;
              var ok = count('main') === 1 && count('tools') === 1 && count('tool') >= 2 && count('actions') === 1 &&
                !window.__astryxStoryLayoutError && r && r.width >= window.innerWidth * 0.9;
              return (ok ? 'ok ' : '') + shape;
            })()
        """

        // The prefix facebook-canary.yml looks for; the status code is one the runner doesn't use.
        private const val NOTE_PREFIX = "CANARY NOTE: "
        private const val NOTE_STATUS = 3
        // A public reel posted as a video in a post ("A Day With My Cat"), as the story address its
        // share link came back as: story_fbid is the reel's id, share_url a reel share link (a
        // made-up one: a real one names whoever shared it; only its shape counts).
        private const val STORY_REEL_ID = "1427052772729171"
        private const val STORY_OF_A_REEL = "https://m.facebook.com/story.php?story_fbid=$STORY_REEL_ID&id=61594936681030" +
            "&share_url=https%3A%2F%2Fwww.facebook.com%2Fshare%2Fr%2FAstryxCanary%2F"
        // Fetches Facebook's stories page as the creator would move to it: "<status> <path>" once
        // redirects are followed, in window.__astryxStoriesPage.
        private const val STORIES_PAGE_FETCH_JS =
            "fetch('/stories', { credentials: 'include' }).then(function (r) {" +
                " window.__astryxStoriesPage = r.status + ' ' + new URL(r.url).pathname.replace(/\\/$/, ''); })" +
                ".catch(function (e) { window.__astryxStoriesPage = 'error ' + e; }); 1"
        // A visible link to the story creator on the stories page (Add to story, or Create a story).
        // A feed post's Share button and its sheet's Send as a message, found by their icons (the
        // icon font's U+F037A share arrow and U+F3031 Messenger logo, read on the emulator 2026-10-27):
        // [glyph]'s [index]th item, for tapOn; undefined while there's none.
        private fun iconActionJs(glyph: String, index: Int = 0) =
            "${shownJs("[data-action-id]")}.filter(function (e) {" +
                " return (e.innerText || '').trim().indexOf('$glyph') === 0; })[$index]"
        // The Share sheet's items on screen (their labels), for failures; "" without a sheet.
        private const val SHEET_ITEMS_JS =
            "[].filter.call(document.querySelectorAll('[data-action-id][aria-label]'), function (e) {" +
                " var r = e.getBoundingClientRect(); return r.width > 200 && r.height > 30 && r.height < 80 && r.top > innerHeight / 3; })" +
                ".map(function (e) { return e.getAttribute('aria-label'); }).join(', ')"
        // The feed drawn (m.facebook.com's components on the page).
        private const val FEED_SHOWN_JS = "document.querySelectorAll('[data-mcomponent]').length > 0"
        private const val SHARE_ICON = "\\uDB80\\uDF7A"
        private const val MESSENGER_ICON = "\\uDB8C\\uDC31"
        // Records the page's window.open (m.facebook.com's Send as a message opens Messenger's share link with it).
        private const val RECORD_WINDOW_OPEN_JS =
            "(function () { if (window.__canaryOpenHook) return 1; window.__canaryOpenHook = true; var o = window.open;" +
                " window.open = function (u) { window.__canaryOpened = String(u); return o.apply(this, arguments); }; return 1; })()"
        // The elements matching [selector] that have a size, in page order (a JS array).
        private fun shownJs(selector: String) =
            "[].filter.call(document.querySelectorAll(${JSONObject.quote(selector)}), function (e) {" +
                " var r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; })"
        // The open chat's message box (Messenger's Lexical editor), for tapOn and the checks that read
        // it; undefined on the chat list, which keeps a box with no size.
        private val MESSAGE_BOX_FIND_JS = shownJs("[role=textbox][contenteditable=true]") + "[0]"
        private val MESSAGE_BOX_TEXT_JS = "(function () { var e = $MESSAGE_BOX_FIND_JS; return e ? e.innerText : ''; })()"
        private const val ADD_TO_STORY_SHOWN_JS =
            "[].some.call(document.querySelectorAll('a[href*=\"/stories/create\"]'), function (a) {" +
                " return a.getBoundingClientRect().width > 0; })"
        // What the stories page shows, for failures: address, headings, creator links and their boxes.
        private const val STORIES_SHAPE_JS =
            "location.pathname + ' | ' + [].map.call(document.querySelectorAll('h1, h2, h3'), function (h) {" +
                " return h.innerText.slice(0, 30); }).join(', ') + ' | ' +" +
                " [].map.call(document.querySelectorAll('a[href*=\"/stories/create\"]'), function (a) {" +
                " var r = a.getBoundingClientRect(); return Math.round(r.width) + 'x' + Math.round(r.height); }).join(', ')"
        // The widths in the page's vpd cookies ("v1;<height>x<width>x<density>").
        private const val VPD_WIDTHS_JS =
            "document.cookie.split('; ').filter(function (c) { return c.indexOf('vpd=') === 0; })" +
                ".map(function (c) { var m = decodeURIComponent(c.slice(4)).match(/^v1;\\d+x(\\d+)x/); return m ? m[1] : '?'; }).join(',')"
        // No vpd cookie narrower than 60% of the window (a PiP window's).
        private const val NO_NARROW_VPD_JS =
            "(function () { var w = $VPD_WIDTHS_JS.split(','); return w.every(function (x) {" +
                " return !x || x === '?' || Number(x) >= window.outerWidth * 0.6; }); })()"
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

        // The feed's posts in the boxes adblock.js hides (top-level ones) and the ones it hid
        // (hidden, with the grey label it looks for inside), and the Sponsored labels still showing
        // anywhere on the feed (leaf elements; found by their text, not as adblock.js finds them),
        // counted across scrolls: "posts|hidden|missed|shape". A missed label's shape names the
        // Facebook boxes around it (data-mcomponent, #data-dcm-id), for fixing adblock.js.
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
                if (p.style.display === 'none' && !p.__canaryHidden && p.querySelector(grey)) { p.__canaryHidden = true; s.hidden++; }
              }
              var els = document.body.querySelectorAll('*');
              for (var j = 0; j < els.length; j++) {
                var e = els[j];
                if (e.children.length || e.__canaryLabel || canaryText(e) !== 'sponsored' || !e.getClientRects().length) continue;
                e.__canaryLabel = true;
                s.missed++;
                var chain = [];
                for (var a = e.parentElement, n = 0; a && n < 15; a = a.parentElement, n++) {
                  var m = a.getAttribute('data-mcomponent');
                  if (m) chain.push(m + (a.hasAttribute('data-dcm-id') ? '#' + a.getAttribute('data-dcm-id') : ''));
                }
                s.shape = canaryShape(e) + ' boxes=' + (chain.join('<') || 'none');
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
                " ' reelLinks=' + document.querySelectorAll('a[href*=\"/reel/\"], a[href*=\"/share/r/\"]').length +" +
                " ' inputs=' + document.querySelectorAll('input').length + ' dialogs=' + document.querySelectorAll('[role=dialog]').length; })()"

        // Opens the chat's largest photo (at least 100 px each way, not a link's picture: a shared reel,
        // post or link preview, which is a link; a link's preview at the chat's end was opened instead
        // while the photo above it was still small, 2026-10-28) as a tap would; false while there's none.
        private const val OPEN_CHAT_PHOTO_JS = """
            (function () {
              var root = document.querySelector('[role="main"]') || document.body;
              var imgs = root.querySelectorAll('img'), best = null, area = 0;
              for (var i = 0; i < imgs.length; i++) {
                var r = imgs[i].getBoundingClientRect();
                if (r.width < 100 || r.height < 100 || imgs[i].closest('a[href]')) continue;
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

        // The chat's latest shared reel (a link to a /reel/ page, or Facebook's share link for one),
        // for tapOn; undefined while there's none.
        private val CHAT_REEL_JS = shownJs("a[href*=\"/reel/\"], a[href*=\"/share/r/\"]") + ".pop()"

        // The chat's latest shared post (Facebook's /share/p/ link), for tapOn.
        private val CHAT_POST_JS = shownJs("a[href*=\"/share/p/\"]") + ".pop()"

        // The "From your link" label (the test account's Facebook is in English), found by its
        // text, independently of linked_post.js, in the top third of the screen.
        private const val LINKED_POST_AT_TOP_JS = """
            (function () {
              var w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT), n;
              while ((n = w.nextNode())) {
                if (n.nodeValue.trim() !== 'From your link') continue;
                var r = n.parentElement.getBoundingClientRect();
                return r.height > 0 && r.top >= 0 && r.top < window.innerHeight / 3;
              }
              return false;
            })()
        """

        // The chat's shared post on screen.
        private const val CHAT_POST_ON_SCREEN_JS =
            "Array.prototype.some.call(document.querySelectorAll('a[href*=\"/share/p/\"]'), function (a) {" +
                " var r = a.getBoundingClientRect(); return r.height > 0 && r.bottom > 0 && r.top < window.innerHeight; })"

        // A chat on screen: its message box has a size (the chat list keeps it with none).
        private val CHAT_OPEN_JS = "!!($MESSAGE_BOX_FIND_JS)"

        // What follows /messages/ in FB_TEST_CHAT's chat ("t/<id>", "e2ee/t/<id>"), given as its
        // address, that path or a bare id.
        private fun chatPath(chat: String): String {
            val path = chat.substringAfter("/messages/", chat).substringBefore('?').substringBefore('#').trim('/')
            val full = if ('/' in path) path else "t/$path"
            require(Regex("^(e2ee/)?t/[A-Za-z0-9._-]+$").matches(full)) { "FB_TEST_CHAT isn't a chat: $full" }
            return full
        }

        // The chat's row in the chat list, when on screen (a JS array).
        private fun chatRowJs(chat: String) = shownJs("a[href=\"/messages/$chat/\"]")

        // Whether the layer shows the chat list: the chat's row on screen, and no message box.
        private fun chatListShownJs(chat: String) = """
            (function () {
              if ($MESSAGE_BOX_FIND_JS) return false;
              return ${chatRowJs(chat)}.length > 0;
            })()
        """

        // Whether the chat at [chat] (chatPath's) shows in the layer: its message box is on screen.
        // While the chat list shows instead, the chat's row is tapped (at most every 3 s).
        private fun chatShownJs(chat: String) = """
            (function () {
              if ($MESSAGE_BOX_FIND_JS) return true;
              var row = ${chatRowJs(chat)}[0];
              if (row && !(Date.now() - (window.__canaryRowTap || 0) < 3000)) {
                window.__canaryRowTap = Date.now();
                row.click();
              }
              return false;
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
