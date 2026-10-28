package com.astryx.book
import android.Manifest
import android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE
import android.webkit.PermissionRequest.RESOURCE_MIDI_SYSEX
import android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
import android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE
import com.astryx.book.utils.androidPermissionFor
import com.astryx.book.utils.webMediaGrant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which pages get the camera and microphone for calls (see AppWebChromeClient). */
class MediaPermissionsTest {

    private val both = arrayOf(RESOURCE_VIDEO_CAPTURE, RESOURCE_AUDIO_CAPTURE)

    @Test
    fun facebookAndMessengerPages_getCameraAndMicrophone() {
        listOf("https://www.facebook.com/", "https://facebook.com", "https://www.messenger.com/", "https://m.facebook.com")
            .forEach { assertEquals(it, both.toList(), webMediaGrant(it, both)) }
        assertEquals(listOf(RESOURCE_AUDIO_CAPTURE), webMediaGrant("https://www.facebook.com/", arrayOf(RESOURCE_AUDIO_CAPTURE)))
    }

    @Test
    fun otherPages_areDenied() {
        listOf(
            "https://example.com/",
            "http://www.facebook.com/",
            "https://l.facebook.com/",
            "https://facebook.com.example.com/",
            "https://www.facebook.com:443@example.com/",
            "",
        ).forEach { assertNull(it, webMediaGrant(it, both)) }
    }

    @Test
    fun otherResources_areDenied() {
        assertNull(webMediaGrant("https://www.facebook.com/", arrayOf(RESOURCE_PROTECTED_MEDIA_ID)))
        assertNull(webMediaGrant("https://www.facebook.com/", arrayOf(RESOURCE_AUDIO_CAPTURE, RESOURCE_MIDI_SYSEX)))
        assertNull(webMediaGrant("https://www.facebook.com/", emptyArray()))
    }

    @Test
    fun webResources_mapToTheirRuntimePermission() {
        assertEquals(Manifest.permission.CAMERA, androidPermissionFor(RESOURCE_VIDEO_CAPTURE))
        assertEquals(Manifest.permission.RECORD_AUDIO, androidPermissionFor(RESOURCE_AUDIO_CAPTURE))
    }
}
