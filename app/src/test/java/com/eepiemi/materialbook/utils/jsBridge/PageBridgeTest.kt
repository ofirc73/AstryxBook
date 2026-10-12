package com.eepiemi.materialbook.utils.jsBridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric for org.json (Android's own copy; the plain JVM stub has no implementation).
@RunWith(RobolectricTestRunner::class)
class PageBridgeTest {

    private val calls = mutableListOf<String>()
    private val bridges = PageBridges(
        settings = MaterialbookSettings { calls += "settings" },
        theme = ThemeChange { calls += "theme $it" },
        pip = PipBridge({ playing, w, h -> calls += "video $playing ${w}x$h" }, { calls += "visible" }),
        screen = ScreenBridge { calls += "screen $it" },
        layer = LayerBridge { calls += "layer $it" },
    )

    @Test
    fun dispatchesEachCall() {
        assertTrue(dispatchPageMessage("""{"call":"openSettings"}""", bridges))
        assertTrue(dispatchPageMessage("""{"call":"themeColor","color":"#ff0000"}""", bridges))
        assertTrue(dispatchPageMessage("""{"call":"videoState","playing":true,"width":1280,"height":720}""", bridges))
        assertTrue(dispatchPageMessage("""{"call":"pipAnomaly","message":"div#x"}""", bridges))
        assertTrue(dispatchPageMessage("""{"call":"pageVisible"}""", bridges))
        assertTrue(dispatchPageMessage("""{"call":"screenVideoPlaying","playing":true}""", bridges))
        assertTrue(dispatchPageMessage("""{"call":"layerUrl","url":"https://www.facebook.com/friends/"}""", bridges))
        assertEquals(
            listOf(
                "settings",
                "theme ${0xFFFF0000.toInt()}",
                "video true 1280x720",
                "visible",
                "screen true",
                "layer https://www.facebook.com/friends/",
            ),
            calls
        )
    }

    @Test
    fun ignoresUnknownAndMalformedMessages() {
        assertFalse(dispatchPageMessage("""{"call":"downloadEverything"}""", bridges))
        assertFalse(dispatchPageMessage("""{"nope":1}""", bridges))
        assertFalse(dispatchPageMessage("not json", bridges))
        assertFalse(dispatchPageMessage("""{"call":"layerUrl"}""", bridges))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun ignoresCallsToBridgesThePageDoesNotHave() {
        val mainView = PageBridges(settings = MaterialbookSettings { calls += "settings" })
        assertFalse(dispatchPageMessage("""{"call":"layerUrl","url":"https://www.facebook.com/"}""", mainView))
        assertFalse(dispatchPageMessage("""{"call":"pageVisible"}""", mainView))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun shareLink_passesOnFacebookLinksOnly() {
        val shared = mutableListOf<String>()
        val withShare = PageBridges(share = { shared += it })
        assertTrue(dispatchPageMessage("""{"call":"shareLink","url":"https://www.facebook.com/share/1abc/"}""", withShare))
        assertFalse(dispatchPageMessage("""{"call":"shareLink","url":"https://evil.example/x"}""", withShare))
        assertFalse(dispatchPageMessage("""{"call":"shareLink","url":"javascript:alert(1)"}""", withShare))
        assertFalse(dispatchPageMessage("""{"call":"shareLink"}""", withShare))
        assertEquals(listOf("https://www.facebook.com/share/1abc/"), shared)
        // A view without the share bridge doesn't take it.
        assertFalse(dispatchPageMessage("""{"call":"shareLink","url":"https://www.facebook.com/share/1abc/"}""", bridges))
    }
}
