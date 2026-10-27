package com.eepiemi.materialbook

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eepiemi.materialbook.utils.ShareMediaQueue
import com.eepiemi.materialbook.utils.ShareTarget
import org.junit.After
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

    private companion object {
        const val STORY_CREATE_URL = "https://www.facebook.com/stories/create/"
        const val PAGE =
            "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'></head>" +
                "<body><p>test page</p><script>window.__testPage = true;</script></body></html>"
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
    fun toYourStory_opensTheStoryCreatorInTheLayer() {
        share("ShareToStory", photo("story.png"))
        assertOpens(ShareTarget.STORY, inLayer = true, path = "/stories/create")
    }

    // After Share to story the creator moves in-page to Facebook's stories page (seen on a Galaxy
    // A56, 2026-10-27): the story screen closes once nothing is left to share, and the feed under it
    // is loaded again (its stories tray was the page from before the share)...
    @Test
    fun facebooksStoriesPage_afterTheLastStory_closesTheStoryScreen_andLoadsTheFeedAgain() {
        share("ShareToStory", photo("story.png"))
        assertOpens(ShareTarget.STORY, inLayer = true, path = "/stories/create")
        // The creator took the photo: nothing is left to share.
        ShareMediaQueue.clear()
        val layer = app.layerWebView()!!
        app.loadPage(STORY_CREATE_URL, PAGE, layer)
        app.eval("window.__feedBeforeTheStory = 1; 1", app.mainWebView())
        app.eval("history.pushState({}, '', '/stories'); 1", layer)
        assertTrue("the story screen stayed open on Facebook's stories page", app.waitFor { !app.layerOpen() })
        assertTrue("the feed wasn't loaded again", app.waitFor(20_000) {
            app.eval("typeof window.__feedBeforeTheStory", app.mainWebView()) == "\"undefined\"" &&
                app.url(app.mainWebView()).startsWith("https://m.facebook.com/")
        })
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
}
