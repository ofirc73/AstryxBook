package com.astryx.book
import com.eepiemi.materialbook.utils.fbRedirectSanitizer
import com.eepiemi.materialbook.utils.stripTrackingParams
import com.eepiemi.materialbook.utils.unwrapFacebookRedirect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Links leaving the app: unwrapped from Facebook's redirect and without trackers. */
class FbRedirectSanitizerTest {

    @Test
    fun unwrapsFacebookRedirects() {
        val wrapped = "https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2Fa%3Fb%3D1&h=AT0abc&__tn__=-UK-R"
        assertEquals("https://example.com/a?b=1", fbRedirectSanitizer(wrapped))
        assertEquals(
            "https://example.com/",
            fbRedirectSanitizer("https://lm.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2F&h=x")
        )
    }

    @Test
    fun redirectToANonWebTarget_isNotUnwrapped() {
        assertNull(unwrapFacebookRedirect("https://l.facebook.com/l.php?u=javascript%3Aalert(1)&h=x"))
        assertNull(unwrapFacebookRedirect("https://l.facebook.com/l.php?h=x"))
        assertNull(unwrapFacebookRedirect("https://www.facebook.com/l.php?u=https%3A%2F%2Fexample.com"))
    }

    @Test
    fun stripsFacebookTrackers() {
        assertEquals(
            "https://example.com/post?id=7",
            stripTrackingParams("https://example.com/post?fbclid=IwAR0x&id=7&mibextid=Zxz2cZ&rdid=abc")
        )
        assertEquals(
            "https://www.facebook.com/share/p/1A2b/",
            stripTrackingParams("https://www.facebook.com/share/p/1A2b/?mibextid=WC7FNe&__cft__[0]=AZW&__tn__=%2CO%2CP-R")
        )
        assertEquals(
            "https://www.facebook.com/x?story_fbid=1&id=2",
            stripTrackingParams("https://www.facebook.com/x?story_fbid=1&__cft__%5B0%5D=AZW&__xts__%5B0%5D=68&id=2")
        )
    }

    @Test
    fun stripsCampaignAndAdTrackers() {
        assertEquals(
            "https://shop.example/item?color=red",
            stripTrackingParams("https://shop.example/item?utm_source=fb&color=red&UTM_Campaign=x&gclid=1&msclkid=2&igshid=3")
        )
    }

    @Test
    fun keepsEncodingFragmentAndOrder() {
        assertEquals(
            "https://example.com/s?q=a%20b%2Bc&z=1&a=2#section-3",
            stripTrackingParams("https://example.com/s?q=a%20b%2Bc&fbclid=x&z=1&a=2#section-3")
        )
        // A '?' after the '#' is part of the fragment.
        assertEquals("https://example.com/#a?fbclid=1", stripTrackingParams("https://example.com/#a?fbclid=1"))
    }

    @Test
    fun valuelessAndEmptyParams() {
        assertEquals("https://example.com/?flag&x=1", stripTrackingParams("https://example.com/?flag&&x=1&fbclid"))
    }

    @Test
    fun allTrackers_dropsTheQuestionMark() {
        assertEquals("https://example.com/p#top", stripTrackingParams("https://example.com/p?fbclid=1&utm_medium=x#top"))
    }

    @Test
    fun linksWithoutAQuery_areUnchanged() {
        listOf("https://example.com/", "mailto:a@example.com", "tel:+1555", "https://example.com/#x").forEach {
            assertEquals(it, fbRedirectSanitizer(it))
        }
    }
}
