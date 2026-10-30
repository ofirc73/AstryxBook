package com.astryx.book
import com.astryx.book.utils.mobileSiteUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// The mobile view sent to the desktop site by a cached redirect goes back to the mobile page.
class MobileSiteTest {

    @Test
    fun aDesktopPage_hasItsMobileAddress_withoutTheRedirectsMark() {
        assertEquals("https://m.facebook.com/?wtsid=rdr_01zo", mobileSiteUrl("https://www.facebook.com/?wtsid=rdr_01zo&_rdr"))
        assertEquals("https://m.facebook.com/", mobileSiteUrl("https://www.facebook.com/?_rdr"))
        assertEquals("https://m.facebook.com/NASA/posts/1/#c", mobileSiteUrl("https://www.facebook.com/NASA/posts/1/#c"))
        assertEquals("https://m.facebook.com/", mobileSiteUrl("https://www.facebook.com"))
    }

    @Test
    fun otherPages_haveNone() {
        assertNull(mobileSiteUrl("https://m.facebook.com/"))
        assertNull(mobileSiteUrl("https://www.facebook.com/messages/t/4567/"))
        assertNull(mobileSiteUrl("http://www.facebook.com/"))
        assertNull(mobileSiteUrl("https://www.facebook.com.example.org/"))
        assertNull(mobileSiteUrl(null))
    }
}
