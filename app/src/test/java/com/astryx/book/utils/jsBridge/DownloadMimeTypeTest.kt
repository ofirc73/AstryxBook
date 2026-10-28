package com.astryx.book.utils.jsBridge
import com.eepiemi.materialbook.utils.jsBridge.downloadMimeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Downloads from the page are limited to photos and videos (see DownloadBridge). */
class DownloadMimeTypeTest {

    @Test
    fun photosAndVideosAreAccepted() {
        assertEquals("image/jpeg", downloadMimeType("image/jpeg"))
        assertEquals("image/webp", downloadMimeType("image/webp"))
        assertEquals("video/mp4", downloadMimeType("video/mp4"))
    }

    @Test
    fun caseAndParametersAreNormalized() {
        assertEquals("video/mp4", downloadMimeType("Video/MP4; codecs=\"avc1.42E01E\""))
        assertEquals("image/png", downloadMimeType(" image/png "))
    }

    @Test
    fun otherTypesAreRefused() {
        assertNull(downloadMimeType("application/vnd.android.package-archive"))
        assertNull(downloadMimeType("application/octet-stream"))
        assertNull(downloadMimeType("text/html"))
        assertNull(downloadMimeType("audio/mpeg"))
    }

    @Test
    fun malformedTypesAreRefused() {
        assertNull(downloadMimeType(""))
        assertNull(downloadMimeType("image"))
        assertNull(downloadMimeType("image/"))
        assertNull(downloadMimeType("image/../apk"))
        assertNull(downloadMimeType("imagex/png"))
    }
}
