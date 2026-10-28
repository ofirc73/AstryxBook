package com.astryx.book
import com.astryx.book.utils.isWebUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which links "Open links in the app" shows in a Custom Tab (the rest leave as before). */
class CustomTabsTest {

    @Test
    fun webLinks_openInACustomTab() {
        listOf("https://example.com", "http://example.com/a?b=1", "HTTPS://EXAMPLE.COM/x#y", " https://example.com/ ")
            .forEach { assertTrue(it, isWebUrl(it)) }
    }

    @Test
    fun otherLinks_doNot() {
        listOf("mailto:a@example.com", "tel:+1555", "market://details?id=x", "https://", "https:///path", "ftp://example.com", "")
            .forEach { assertFalse(it, isWebUrl(it)) }
    }
}
