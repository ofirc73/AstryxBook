package com.eepiemi.materialbook.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class FbRedirectSanitizerTest {

    @Test
    fun dropsFbclid() {
        assertEquals(
            "https://example.com/page?a=1&b=2",
            fbRedirectSanitizer("https://example.com/page?a=1&fbclid=IwAR0abc&b=2")
        )
    }

    @Test
    fun dropsQueryWhenOnlyFbclid() {
        assertEquals(
            "https://example.com/page",
            fbRedirectSanitizer("https://example.com/page?fbclid=IwAR0abc")
        )
    }

    @Test
    fun keepsEncodedValuesAsTheyAre() {
        assertEquals(
            "https://example.com/search?q=a%20b%26c&x=%2F",
            fbRedirectSanitizer("https://example.com/search?q=a%20b%26c&x=%2F&fbclid=1")
        )
    }

    @Test
    fun keepsFragment() {
        assertEquals(
            "https://example.com/docs?a=1#section-2",
            fbRedirectSanitizer("https://example.com/docs?a=1&fbclid=1#section-2")
        )
        assertEquals(
            "https://example.com/docs#top",
            fbRedirectSanitizer("https://example.com/docs#top")
        )
    }

    @Test
    fun keepsParamsWithoutValue() {
        assertEquals(
            "https://example.com/p?flag&a=1",
            fbRedirectSanitizer("https://example.com/p?flag&a=1&fbclid=1")
        )
    }

    @Test
    fun keepsNonDefaultPort() {
        assertEquals(
            "https://example.com:8443/p",
            fbRedirectSanitizer("https://example.com:8443/p?fbclid=1")
        )
    }

    @Test
    fun unwrapsLinkShim() {
        assertEquals(
            "https://example.com/article?id=5&q=a%20b#c",
            fbRedirectSanitizer(
                "https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2Farticle%3Fid%3D5%26q%3Da%2520b%26fbclid%3DIwAR0%23c&h=AT0xyz"
            )
        )
    }

    @Test
    fun linkShimWithoutTargetIsLeftAlone() {
        val link = "https://l.facebook.com/l.php?h=AT0xyz"
        assertEquals(link, fbRedirectSanitizer(link))
    }

    @Test
    fun invalidUrlIsLeftAlone() {
        assertEquals("not a url", fbRedirectSanitizer("not a url"))
    }
}
