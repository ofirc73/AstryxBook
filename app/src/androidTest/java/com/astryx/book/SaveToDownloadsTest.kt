package com.astryx.book

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eepiemi.materialbook.utils.jsBridge.DownloadBridge
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStream

/**
 * Saving to Downloads (DownloadBridge.save, which a download from Facebook's media servers and one
 * the page hands over both go through) writes the file as it arrives. The page used to hand over a
 * video whole, as one base64 string several times its size, and a long one ran the app out of
 * memory: here a file bigger than all the memory the app may use is saved.
 */
@RunWith(AndroidJUnit4::class)
class SaveToDownloadsTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    // [size] bytes of a repeating pattern, made as they're read.
    private class Generated(private val size: Long) : InputStream() {
        private var position = 0L

        override fun read(): Int = if (position >= size) -1 else (position++ % 251).toInt()

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= size) return -1
            val count = minOf(length.toLong(), size - position).toInt()
            for (i in 0 until count) buffer[offset + i] = ((position + i) % 251).toByte()
            position += count
            return count
        }
    }

    @Test
    fun aVideoBiggerThanTheAppsMemory_isWrittenToDownloads() {
        // MediaStore's Downloads collection (Android 10+); before it the file goes straight to the folder.
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val size = Runtime.getRuntime().maxMemory() + 64L * 1024 * 1024
        val since = System.currentTimeMillis() / 1000 - 1
        DownloadBridge(context).save(Generated(size), "video/mp4", "mp4")

        val app = AppHarness()
        val saved = app.savedDownloads(since, "mp4")
        try {
            assertEquals("not one new file in Downloads", 1, saved.size)
            assertEquals("the file in Downloads isn't the whole video", size, saved.single().size)
        } finally {
            app.deleteDownloads(saved)
        }
    }
}
