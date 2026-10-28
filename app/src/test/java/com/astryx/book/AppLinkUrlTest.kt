package com.astryx.book
import com.astryx.book.utils.appLinkUrl
import com.astryx.book.utils.isFacebookWebUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLinkUrlTest {

    @Test
    fun facebookLinks_open() {
        for (url in listOf(
            "https://www.facebook.com/groups/123/",
            "https://m.facebook.com/story.php?id=1#c",
            "https://facebook.com/",
            "https://fb.watch/abc123/",
            "https://www.messenger.com/t/1",
        )) assertEquals(url, appLinkUrl(url))
    }

    @Test
    fun otherLinks_areRefused() {
        assertNull(appLinkUrl(null))
        assertNull(appLinkUrl("https://evil.example/"))
        assertNull(appLinkUrl("https://facebook.com.evil.example/"))
        assertNull(appLinkUrl("https://fb.watch.evil.example/"))
        assertNull(appLinkUrl("https://l.facebook.com/l.php?u=https%3A%2F%2Fevil.example"))
        assertNull(appLinkUrl("http://m.facebook.com/"))
        assertNull(appLinkUrl("javascript:alert(1)"))
        assertNull(appLinkUrl("file:///data/data/com.astryx.book/"))
    }

    @Test
    fun userInfoBeforeTheHost_isNotFacebook() {
        // The host here is evil.example; "facebook.com:443" is only the user info.
        assertFalse(isFacebookWebUrl("https://facebook.com:443@evil.example/"))
        assertFalse(isFacebookWebUrl("https://m.facebook.com@evil.example/"))
        assertTrue(isFacebookWebUrl("https://m.facebook.com:443/home.php"))
    }
}
