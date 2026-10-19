package com.eepiemi.materialbook.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewVersionTest {

    @Test
    fun theMajorVersion_isTheFirstNumber() {
        assertEquals(69, webViewMajor("69.0.3497.100"))
        assertEquals(154, webViewMajor("154.0.8037.106"))
        assertNull(webViewMajor(null))
        assertNull(webViewMajor("unknown"))
    }

    @Test
    fun webViewsBeforeChrome80_areTooOld() {
        // The stock API 26 emulator image's WebView.
        assertTrue(isWebViewTooOld("69.0.3497.100"))
        assertTrue(isWebViewTooOld("79.0.3945.136"))
        assertFalse(isWebViewTooOld("80.0.3987.99"))
        assertFalse(isWebViewTooOld("154.0.8037.106"))
    }

    @Test
    fun anUnknownVersion_isNotReported() {
        assertFalse(isWebViewTooOld(null))
        assertFalse(isWebViewTooOld(""))
    }
}
