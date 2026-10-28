package com.astryx.book.utils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextZoomTest {

    @Test
    fun storedValues_areTheirPercentage() {
        TextZoom.ALL.forEach { assertEquals(it.toInt(), TextZoom.percent(it)) }
    }

    @Test
    fun unknownStoredValue_isTheDefault() {
        listOf("", "bogus", "0", "1000", "100.0").forEach { assertEquals(100, TextZoom.percent(it)) }
    }

    @Test
    fun viewportCookie_reportsTheZoomedViewport_likeFacebooksOwn() {
        // A 1080x2208 px WebView at 2.625 px per CSS px: Facebook writes v1;841x411x2.625.
        assertEquals(
            "vpd=v1%3B841x411x2.625; domain=.facebook.com; path=/; secure",
            TextZoom.viewportCookie(1080, 2208, 2.625f, 100)
        )
        assertEquals(
            "vpd=v1%3B647x316x2.625; domain=.facebook.com; path=/; secure",
            TextZoom.viewportCookie(1080, 2208, 2.625f, 130)
        )
    }

    @Test
    fun viewportCookie_writesAWholePixelRatio_withoutDecimals() {
        assertTrue(TextZoom.viewportCookie(1080, 2160, 3f, 100).startsWith("vpd=v1%3B720x360x3;"))
    }

    @Test
    fun defaultIsOneOfTheChoices() {
        assertTrue(TextZoom.DEFAULT in TextZoom.ALL)
        assertEquals(100, TextZoom.percent(TextZoom.DEFAULT))
    }
}
