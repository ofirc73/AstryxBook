package com.astryx.book
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.notifications.Posting
import com.astryx.book.notifications.PostingService
import com.astryx.book.ui.screens.ShareMediaJsTest
import com.astryx.book.ui.screens.StoryLayoutJsTest
import com.astryx.book.utils.ShareMediaQueue
import com.astryx.book.utils.SharedFile
import com.astryx.book.utils.ShareTarget
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URLDecoder

/**
 * Sharing photos into Facebook from another app (the gallery's share sheet), through each of the
 * app's share targets: the files are taken in, and the right Facebook page opens for them, in
 * the right view (Feed, profile picture and groups in the main view; Story and Reel in the
 * Messages layer, Facebook's desktop creators). What the pages then do with the files is
 * ShareMediaJsTest's (and the live check's, for Feed).
 *
 * No login here: the pages may be Facebook's login page with the page asked for in its address,
 * which is why addresses are checked decoded.
 */
@RunWith(AndroidJUnit4::class)
class ShareIntoFacebookTest {

    private val app = AppHarness()

    @Before
    fun setUp() {
        File(app.context.filesDir, "datastore").deleteRecursively()
        ShareMediaQueue.clear()
        // Posting asks for it, and Android's dialog over the app hid the posting bar (CI).
        allowNotifications()
    }

    @After
    fun tearDown() {
        app.finish()
        ShareMediaQueue.clear()
        File(app.context.filesDir, "datastore").deleteRecursively()
    }

    private fun photo(name: String): Uri {
        val dir = File(app.context.cacheDir, "clipboard_images").apply { mkdirs() }
        val file = File(dir, name)
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 80, 40)) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(app.context, "${app.context.packageName}.fileprovider", file)
    }

    // Reels take videos only; the file's content doesn't matter until Facebook reads it.
    private fun video(name: String): Uri {
        val dir = File(app.context.cacheDir, "clipboard_images").apply { mkdirs() }
        val file = File(dir, name).apply { writeBytes(ByteArray(2048) { it.toByte() }) }
        return FileProvider.getUriForFile(app.context, "${app.context.packageName}.fileprovider", file)
    }

    private fun share(alias: String, vararg uris: Uri, type: String = "image/png") {
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris.toList()))
        }
        app.start(
            intent.setComponent(ComponentName(app.context.packageName, "com.eepiemi.materialbook.$alias"))
                .setType(type)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    private fun decoded(url: String) = runCatching { URLDecoder.decode(url, "UTF-8") }.getOrDefault(url)

    private fun assertOpens(target: ShareTarget, inLayer: Boolean, path: String) {
        assertTrue("the shared photo never reached the share queue for $target", app.waitFor(15_000) {
            ShareMediaQueue.pending(target)
        })
        assertTrue("$target never opened $path${if (inLayer) " in the Messages layer" else " in the main view"}", app.waitFor(20_000) {
            val webView = if (inLayer) app.layerWebView() else app.mainWebView()
            webView != null && decoded(app.url(webView)).contains(path)
        })
        if (!inLayer) assertTrue("$target opened the Messages layer", app.webViews().size == 1)
    }

    // The story creator laid out by story_layout.js (StoryLayoutJsTest's), its photo shown.
    private fun openCreator(): android.webkit.WebView {
        share("ShareToStory", photo("story.png"))
        assertOpens(ShareTarget.STORY, inLayer = true, path = "/stories/create")
        ShareMediaQueue.clear()
        val layer = app.layerWebView()!!
        // Logged out (CI), the real creator went on redirecting under the test page: a late move to
        // the stories page then counted as the story posted.
        app.keepOffTheNetwork(layer)
        app.loadPage(STORY_CREATE_URL, CREATOR, layer)
        assertTrue("story_layout.js never marked Discard / Share", app.waitFor {
            app.eval("!!document.querySelector('[data-astryx-story=\"actions\"]')", layer) == "true"
        })
        return layer
    }

    // Facebook's Share to story while the story posts (aria-busy, read on the emulator 2026-11-02).
    private fun setPosting(layer: android.webkit.WebView, posting: Boolean) = app.eval(
        "var share = document.querySelectorAll('#actions [role=\"button\"]')[1];" +
            "share.setAttribute('aria-busy', '$posting'); share.setAttribute('aria-disabled', '$posting'); 1",
        layer
    )

    @Suppress("DEPRECATION") // getRunningServices still lists the app's own services
    private fun postingNotificationShows(): Boolean =
        app.context.getSystemService(ActivityManager::class.java).getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == PostingService::class.java.name && it.foreground }

    private fun tapPostingNotification() = app.start(Posting.ACTION_SHOW)

    private companion object {
        // A text-story background, as the creator's page preloads them (2026-11-03), and a story photo.
        const val BACKGROUND = "https://scontent.ftlv19-3.fna.fbcdn.net/v/t39.10873-6/51627537_2013736385406998_2008135126298394624_n.jpg?_nc_cat=108"
        const val PHOTO = "https://scontent.ftlv19-1.fna.fbcdn.net/v/t39.30808-6/603899071_71060694897844_31727812138_n.jpg"
        const val CREATOR = "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'></head><body>" +
            StoryLayoutJsTest.CREATOR + "<script>window.__showFrame(); window.__testPage = true;</script></body></html>"
        const val STORY_CREATE_URL = "https://www.facebook.com/stories/create/"
        const val PAGE =
            "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'></head>" +
                "<body><p>test page</p><script>window.__testPage = true;</script></body></html>"
        // Facebook's stories page as Share moves to it: the Your story card, with the Add to story
        // link drawn over it (see OpenOwnStoryJsTest).
        const val STORIES_PAGE =
            "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'></head>" +
                "<body style='margin:0'><div role='navigation' style='width:360px'><div style='position:relative'>" +
                "<div role='button' onclick=\"window.__opened='own'\" style='margin:8px;width:344px;height:76px'>Me 1m</div>" +
                "<div style='position:absolute;top:8px;right:16px'>" +
                "<a href='/stories/create/' style='display:block;width:60px;height:60px'>+</a></div></div></div>" +
                "<script>window.__testPage = true;</script></body></html>"
    }

    @Test
    fun toFeed_opensTheComposer() {
        share("ShareToFeed", photo("feed.png"))
        assertOpens(ShareTarget.FEED, inLayer = false, path = "/composer")
    }

    @Test
    fun severalPhotosToFeed_areAllTakenIn() {
        share("ShareToFeed", photo("one.png"), photo("two.png"))
        assertOpens(ShareTarget.FEED, inLayer = false, path = "/composer")
        assertTrue("the first photo is missing", ShareMediaQueue.take(ShareTarget.FEED) != null)
        assertTrue("the second photo is missing", ShareMediaQueue.take(ShareTarget.FEED) != null)
    }

    @Test
    fun toFeed_withHomeShowing_opensTheComposerFromHomesBox_withARealTap_thatBackGoesBackFrom() {
        // No load of /composer/ (Facebook's server would draw all of Home's feed first): the app taps
        // Home's box, a real tap, so Back's history keeps the composer's entry (from a script's
        // click(), Chromium's Back skipped it and left the app).
        app.start()
        app.loadPage(
            "https://m.facebook.com/",
            "<html><body>${ShareMediaJsTest.homePage()}<script>${ShareMediaJsTest.PHOTO_ICON} ${ShareMediaJsTest.ADD_PHOTO_ICON}" +
                "document.getElementById('field').addEventListener('click', function () {" +
                "  window.__realTap = !!(navigator.userActivation && navigator.userActivation.isActive); });" +
                "document.getElementById('in').addEventListener('change', function (e) { window.__taken = e.target.files.length; });" +
                "window.__testPage = true;</script></body></html>",
            asHistory = true,
        )
        share("ShareToFeed", photo("feed.png"))

        assertTrue("the composer opened from Home never took the photo (${app.eval("location.pathname")})", app.waitFor(15_000) {
            app.eval("window.__taken === 1 && location.pathname === '/composer/' && !!window.__testPage") == "true"
        })
        assertEquals("the box's tap wasn't a real one", "true", app.eval("window.__realTap"))
        app.pressBack()
        assertTrue("Back didn't go back to Home (${app.eval("location.pathname")})", app.waitFor {
            app.eval("location.pathname") == "\"/\""
        })
        assertFalse("Back left the app", app.isInBackground())
    }

    @Test
    fun toYourStory_opensTheStoryCreatorInTheLayer() {
        share("ShareToStory", photo("story.png"))
        assertOpens(ShareTarget.STORY, inLayer = true, path = "/stories/create")
    }

    // After Share to story the creator moves in-page to Facebook's stories page (seen on a Galaxy
    // A56, 2026-10-27): once nothing is left to share, the user's own stories open there, the new
    // one included, and Back closes the story screen...
    @Test
    fun facebooksStoriesPage_afterTheLastStory_showsYourStories_andBackClosesIt() {
        share("ShareToStory", photo("story.png"))
        assertOpens(ShareTarget.STORY, inLayer = true, path = "/stories/create")
        // The creator took the photo: nothing is left to share.
        ShareMediaQueue.clear()
        val layer = app.layerWebView()!!
        app.loadPage(STORY_CREATE_URL, STORIES_PAGE, layer)
        app.eval("history.pushState({}, '', '/stories'); 1", layer)
        assertTrue("your story's card on Facebook's stories page was never opened", app.waitFor {
            app.eval("window.__opened || ''", layer) == "\"own\""
        })
        assertTrue("the story screen closed instead of showing your stories", app.layerOpen())
        app.pressBack()
        assertTrue("Back didn't close the story screen", app.waitFor { !app.layerOpen() })
    }

    // ... or closes at once there when there's no story of the user's to show...
    @Test
    fun facebooksStoriesPage_withNoStoryOfYoursOnIt_closesTheStoryScreen() {
        share("ShareToStory", photo("story.png"))
        assertOpens(ShareTarget.STORY, inLayer = true, path = "/stories/create")
        ShareMediaQueue.clear()
        val layer = app.layerWebView()!!
        app.loadPage(STORY_CREATE_URL, PAGE, layer)
        app.eval("history.pushState({}, '', '/stories'); 1", layer)
        assertTrue("the story screen stayed open on Facebook's stories page", app.waitFor(15_000) { !app.layerOpen() })
    }

    // ... and opens the creator again for the next shared file.
    @Test
    fun facebooksStoriesPage_withAnotherFileToShare_opensTheCreatorAgain() {
        share("ShareToStory", photo("one.png"), photo("two.png"))
        assertOpens(ShareTarget.STORY, inLayer = true, path = "/stories/create")
        val layer = app.layerWebView()!!
        app.loadPage(STORY_CREATE_URL, PAGE, layer)
        app.eval("history.pushState({}, '', '/stories'); 1", layer)
        // The test page goes: the creator is loaded anew (it may be a new WebView).
        assertTrue("the creator never opened again for the next file", app.waitFor(20_000) {
            val now = app.layerWebView()
            app.layerOpen() && now != null && decoded(app.url(now)).contains("/stories/create") &&
                app.eval("!!window.__testPage", now) == "false"
        })
    }

    @Test
    fun aVideoToReels_opensTheReelCreatorInTheLayer() {
        share("ShareToReel", video("reel.mp4"), type = "video/mp4")
        assertOpens(ShareTarget.REEL, inLayer = true, path = "/reels/create")
    }

    @Test
    fun toYourGroups_opensTheGroupsPage() {
        share("ShareToGroup", photo("group.png"))
        assertOpens(ShareTarget.GROUP, inLayer = false, path = "/groups")
    }

    @Test
    fun asProfilePicture_opensTheProfile() {
        share("ShareToProfilePicture", photo("profile.png"))
        assertOpens(ShareTarget.PROFILE_PICTURE, inLayer = false, path = "/profile.php")
    }

    // Share to story pressed: the creator goes out of sight and the story goes on posting there, in
    // the background, with "Posting your story" in the notification shade (as the Facebook app does);
    // the notification shows the creator again, Back hides it. Posted, it closes.
    @Test
    fun aStoryPosting_goesOnOutOfSight_withItsNotification_untilItsPosted() {
        val layer = openCreator()
        setPosting(layer, true)
        assertTrue("the creator stayed on screen while the story posts", app.waitFor { !app.layerOpen() })
        assertTrue("the posting notification never showed", app.waitFor { postingNotificationShows() })
        assertTrue("the creator was closed, and the upload with it", app.layerWebView() === layer)

        tapPostingNotification()
        assertTrue("the notification didn't show the creator", app.waitFor { app.layerOpen() })
        app.pressBack()
        assertTrue("Back closed the posting creator instead of hiding it", app.waitFor { !app.layerOpen() })
        assertTrue(app.layerWebView() === layer)

        // Posted: Share no longer busy, and Facebook moves on to its stories page (within the 2 s
        // story_posting.js waits before calling it a failure). In that order: the layer, out of
        // sight, closes on the move, and a WebView gone by then never answers.
        setPosting(layer, false)
        app.eval("history.pushState({}, '', '/stories'); 1", layer)
        assertTrue("the posting notification stayed after the story was posted", app.waitFor { !postingNotificationShows() })
        assertTrue("the creator stayed after the story was posted", app.waitFor { app.layerWebView() == null })

        // On screen: "Your story is posted", whose View opens the user's own stories in the layer.
        val view = app.context.getString(com.eepiemi.materialbook.R.string.posting_view)
        assertTrue("no \"Your story is posted\" with View (PostedStoryEffects)", app.waitFor {
            app.textShown(app.context.getString(com.eepiemi.materialbook.R.string.story_posted)) && app.textShown(view)
        })
        assertTrue(app.tapText(view))
        assertOwnStoriesOpen("View")
    }

    // Several photos shared to Story, the first one posted while the app was away: the creator opens
    // again for the next one, and a tap on "Your story is posted" shows it there, rather than the
    // stories page, which took the files left with it (seen on the emulator, 2026-11-10).
    @Test
    fun aStoryPosted_withMoreToShare_theNotificationShowsTheCreatorForTheNextFile() {
        val layer = openCreator()
        val files = listOf("one.png", "two.png").map { name ->
            val file = File(app.context.cacheDir, "clipboard_images/$name").apply { parentFile?.mkdirs(); writeBytes(ByteArray(8)) }
            SharedFile(file, name, "image/png")
        }
        ShareMediaQueue.start(ShareTarget.STORY, files)
        ShareMediaQueue.take(ShareTarget.STORY) // the first one, in the creator
        setPosting(layer, true)
        assertTrue(app.waitFor { !app.layerOpen() })
        setPosting(layer, false)
        app.eval("history.pushState({}, '', '/stories'); 1", layer)
        assertTrue("the creator never opened again for the next file", app.waitFor(20_000) {
            app.layerOpen() && app.layerWebView() !== layer
        })
        val creator = app.layerWebView()
        // The notification's tap, with the app showing (onNewIntent, as from the background). Not
        // after Home: its key reaches the launcher a moment later, and on CI it came after this
        // start and sent the app away.
        app.start(Posting.ACTION_VIEW_STORY)
        assertTrue("the notification didn't show the creator", app.waitFor(10_000) { app.layerOpen() })
        // The creator kept (it takes the next file; the real page here, on the network).
        val other = app.waitFor(2_000) { app.layerWebView().let { it != null && it !== creator } }
        assertFalse("the notification replaced the creator holding the next file", other)
        assertTrue("the creator holding the next file isn't showing", app.waitFor(10_000) { app.layerWebView() === creator })
    }

    // A story posted: the feed's stories row gets it some 30 s later, so Home, at its top, is
    // reloaded then (PostedStoryEffects); not before.
    @Test
    fun aStoryPosted_reloadsHomeAtItsTop_aboutHalfAMinuteLater() {
        app.start()
        app.loadPage("https://m.facebook.com/", "<html><body><script>window.__testPage = true;</script></body></html>", asHistory = true)
        app.onActivity {
            Posting.started(it, Posting.Kind.STORY)
            Posting.ended(it, Posting.Kind.STORY, Posting.Outcome.POSTED, notify = false)
        }
        Thread.sleep(20_000)
        assertEquals("Home reloaded before the stories row could have the story", "true", app.eval("!!window.__testPage"))
        assertTrue("Home wasn't reloaded for the stories row", app.waitFor(20_000) { app.eval("!!window.__testPage") != "true" })
    }

    // A tap on "Your story is posted" (its notification): the user's own stories, in the layer.
    @Test
    fun aPostedStorysNotification_opensYourStoriesInTheLayer() {
        app.start()
        app.start(Posting.ACTION_VIEW_STORY)
        assertOwnStoriesOpen("the notification")
    }

    // Facebook's stories page in the layer (logged out here: its login page, the stories page in its address).
    private fun assertOwnStoriesOpen(by: String) = assertTrue("$by didn't open Facebook's stories page in the layer",
        app.waitFor(20_000) { app.layerWebView()?.let { decoded(app.url(it)).contains("/stories") } == true })

    // A feed post whose photos are still going up after Post (feed_posting.js, in the main view): the
    // app stays in the foreground with "Posting your post" until it's sent; a new page in the main
    // view takes the upload with it and ends that.
    @Test
    fun aFeedPostStillUploading_postsInTheForeground_untilItsSent() {
        app.start()
        // Its own address: the start page is at m.facebook.com/, and a second page there leaves
        // evaluateJavascript unanswered.
        app.loadPage("https://m.facebook.com/?n=1", "<html><body><script>window.__testPage = true;</script></body></html>")
        assertEquals("feed_posting.js isn't in the main view", "true", app.eval("!!window.__astryxFeedPosting"))
        fun report(state: String) =
            app.eval("window.AstryxPage.postMessage(JSON.stringify({ call: 'feedPosting', state: '$state' })); 1")

        report("posting")
        assertTrue("the posting notification never showed", app.waitFor { postingNotificationShows() })
        val postingText = app.context.getString(com.eepiemi.materialbook.R.string.post_posting)
        assertTrue("no posting bar over the page (PostingBar)", app.waitFor { app.textShown(postingText) })
        report("posted")
        assertTrue("the posting notification stayed after the post was sent", app.waitFor { !postingNotificationShows() })
        assertTrue("the posting bar stayed after the post was sent", app.waitFor { !app.textShown(postingText) })

        report("posting")
        assertTrue(app.waitFor { postingNotificationShows() })
        app.loadPage("https://m.facebook.com/?n=2", "<html><body><script>window.__testPage = true;</script></body></html>")
        assertTrue("the posting notification stayed after its page went", app.waitFor { !postingNotificationShows() })
    }

    // A failed upload: Facebook's editor comes back (still the creator), kept out of sight until the
    // notification shows it again, for another try.
    @Test
    fun aStoryThatFailsToPost_isKept_forTheNotificationToShowAgain() {
        val layer = openCreator()
        setPosting(layer, true)
        assertTrue(app.waitFor { !app.layerOpen() && postingNotificationShows() })
        setPosting(layer, false)
        assertTrue("the posting notification stayed after the upload failed", app.waitFor { !postingNotificationShows() })
        // Back is the main view's (a page back, or leaving the app from the feed): the failed story
        // is kept meanwhile.
        app.pressBack()
        Thread.sleep(1000)
        tapPostingNotification()
        assertTrue("the notification didn't show the failed story", app.waitFor { app.layerOpen() })
        assertTrue("the failed story was dropped", app.layerWebView() === layer)
    }

    // The story creator's page preloads all its text-story backgrounds (16 MB): during a story
    // share they're answered empty while the shared files wait (StoryBackgrounds), then load as
    // usual (a text story made there afterwards). Create story on the feed loads them.
    @Test
    fun aStoryShare_skipsTheCreatorsBackgrounds_untilItsFilesAreTaken() {
        share("ShareToStory", photo("story.png"))
        assertOpens(ShareTarget.STORY, inLayer = true, path = "/stories/create")
        val layer = app.layerWebView()!!
        assertEquals("a background was loaded during the story share", 204, app.clientAnswer(layer, BACKGROUND)?.statusCode)
        assertEquals("other pictures were answered empty", null, app.clientAnswer(layer, PHOTO))
        ShareMediaQueue.clear()
        assertEquals("a background stayed empty once the files were taken", null, app.clientAnswer(layer, BACKGROUND))
    }

    @Test
    fun createStory_loadsTheCreatorsBackgrounds() {
        app.start()
        // As scripts.js's Create story does; asked again until the page (still loading) takes it.
        assertTrue("Create story never opened the creator", app.waitFor(20_000) {
            app.eval("window.AstryxPage && window.AstryxPage.postMessage(JSON.stringify({ call: 'layerUrl', url: '$STORY_CREATE_URL' })); 1")
            Thread.sleep(500)
            app.layerWebView() != null
        })
        assertEquals(null, app.clientAnswer(app.layerWebView()!!, BACKGROUND))
    }
}
