package com.astryx.book.ui.screens

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import com.astryx.book.assertTrue
import com.astryx.book.utils.ShareMediaQueue
import com.astryx.book.utils.ShareTarget
import com.astryx.book.utils.SharedFile
import com.astryx.book.utils.jsBridge.installShareMediaBridge
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile

/**
 * The real share bridge (ShareMediaBridge) handing a shared file to a page: a video bigger than all
 * the memory the app may use arrives whole, in pieces. Read whole and posted as one message, as it
 * was, a share of up to 250 MB (MAX_VIDEO_BYTES) couldn't fit in the app's memory.
 */
@RunWith(AndroidJUnit4::class)
class ShareHandOverTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val file = File(context.cacheDir, "share-hand-over.mp4")

    @After
    fun tearDown() {
        ShareMediaQueue.clear()
        file.delete()
    }

    @Test
    fun aVideoBiggerThanTheAppsMemory_reachesThePageWhole() {
        val size = Runtime.getRuntime().maxMemory() + 64L * 1024 * 1024
        RandomAccessFile(file, "rw").use { it.setLength(size) }
        ShareMediaQueue.start(ShareTarget.STORY, listOf(SharedFile(file, "0_v.mp4", "video/mp4")))
        val page = PageFixture()
        page.load(
            "https://www.facebook.com/stories/create",
            "<html><body><script>window.__testPage = true;</script></body></html>"
        ) { webView -> installShareMediaBridge(webView, context) }
        // Asks for the next file as share_media.js does and counts what comes, without keeping it.
        page.eval(
            """
            window.__got = { header: null, bytes: 0, pieces: 0, end: null };
            AstryxShare.onmessage = function (e) {
              if (typeof e.data === 'string' && e.data.charAt(0) === '{') {
                if (__got.header) __got.end = e.data; else __got.header = JSON.parse(e.data);
              } else { __got.bytes += e.data.byteLength; __got.pieces++; }
            };
            AstryxShare.postMessage(JSON.stringify({ call: 'next', target: 'story' })); 1
            """.trimIndent()
        )
        assertTrue({ "the video never reached the page whole (${page.eval("JSON.stringify(__got)")})" },
            page.waitFor(120_000, everyMs = 500) { page.eval("__got.end !== null") == "true" })
        assertEquals("\"{\\\"end\\\":true}\"", page.eval("__got.end"))
        assertEquals(size.toString(), page.eval("String(__got.bytes)").removeSurrounding("\""))
        assertTrue("sent as one piece (${page.eval("__got.pieces")})", page.eval("__got.pieces").toInt() > 1)
    }
}
