package com.astryx.book.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.GregorianCalendar

class CameraCaptureTest {

    private val photo = setOf(CaptureKind.PHOTO)
    private val video = setOf(CaptureKind.VIDEO)
    private val both = setOf(CaptureKind.PHOTO, CaptureKind.VIDEO)

    private fun kinds(vararg accept: String) = captureKinds(arrayOf(*accept))

    @Test
    fun aRequestForAnything_takesAPhotoOrAVideo() {
        assertEquals(both, captureKinds(null))
        assertEquals(both, kinds())
        assertEquals(both, kinds(""))
        assertEquals(both, kinds("*/*"))
    }

    @Test
    fun aRequestTakesWhatTheCameraMakes() {
        assertEquals(photo, kinds("image/*"))
        assertEquals(photo, kinds("image/png", "image/jpeg"))
        assertEquals(photo, kinds(".JPG"))
        assertEquals(video, kinds("video/*"))
        assertEquals(video, kinds("video/mp4", "video/quicktime"))
        // Several types, in one string or apart.
        assertEquals(both, kinds("image/*,image/heif,image/heic,video/*,video/mp4,video/x-m4v,video/x-matroska,.mkv"))
        assertEquals(both, kinds("image/*", " Video/* "))
    }

    @Test
    fun aRequestTheCameraCantAnswer_takesNeither() {
        assertEquals(emptySet<CaptureKind>(), kinds("application/pdf"))
        assertEquals(emptySet<CaptureKind>(), kinds(".docx", "text/plain"))
        // The camera makes JPEGs and MP4s.
        assertEquals(emptySet<CaptureKind>(), kinds("image/png", "video/webm"))
        assertEquals(emptySet<CaptureKind>(), kinds("audio/*"))
    }

    @Test
    fun aCapturesName_saysWhatAndWhen() {
        val time = GregorianCalendar(2026, 10, 11, 9, 31, 0).time
        assertEquals("photo_20261111_093100.jpg", captureFileName(CaptureKind.PHOTO, time))
        assertEquals("video_20261111_093100.mp4", captureFileName(CaptureKind.VIDEO, time))
    }
}
