package com.eepiemi.materialbook.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Outbound links go through fbRedirectSanitizer before opening in the browser.
 * It must strip Facebook's tracking (l.php wrapper, fbclid) without changing
 * the target: encoded query values stay as they are and the #fragment is kept.
 */
class FbRedirectSanitizerTest {

    @Test
    fun keepsEncodedQueryValues() {
        assertEquals(
            "https://example.com/search?q=a%20b&tag=%23x",
            fbRedirectSanitizer("https://example.com/search?q=a%20b&tag=%23x"),
        )
    }

    @Test
    fun keepsFragment() {
        assertEquals(
            "https://example.com/docs?page=2#section-3",
            fbRedirectSanitizer("https://example.com/docs?page=2#section-3"),
        )
        assertEquals(
            "https://example.com/docs#top",
            fbRedirectSanitizer("https://example.com/docs#top"),
        )
    }

    @Test
    fun stripsFbclid() {
        assertEquals(
            "https://example.com/a?x=1&y=2",
            fbRedirectSanitizer("https://example.com/a?x=1&fbclid=IwAR0abc&y=2"),
        )
        assertEquals(
            "https://example.com/a#frag",
            fbRedirectSanitizer("https://example.com/a?fbclid=IwAR0abc#frag"),
        )
    }

    @Test
    fun unwrapsLinkShim() {
        val target = "https%3A%2F%2Fexample.com%2Fp%3Fq%3Da%2520b%26fbclid%3Dxyz%23part"
        assertEquals(
            "https://example.com/p?q=a%20b#part",
            fbRedirectSanitizer("https://l.facebook.com/l.php?u=$target&h=AT0abc"),
        )
    }

    @Test
    fun keepsValuelessParamsAndPort() {
        assertEquals(
            "http://example.com:8080/a?flag&x=1",
            fbRedirectSanitizer("http://example.com:8080/a?flag&x=1"),
        )
    }

    @Test
    fun returnsLinkShimWithoutTargetUnchanged() {
        val link = "https://l.facebook.com/l.php?h=AT0abc"
        assertEquals(link, fbRedirectSanitizer(link))
    }

    @Test
    fun returnsInvalidLinkUnchanged() {
        assertEquals("not a url", fbRedirectSanitizer("not a url"))
    }
}
