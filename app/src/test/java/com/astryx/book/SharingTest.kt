package com.astryx.book
import com.astryx.book.utils.facebookShareUrl
import com.astryx.book.utils.shareablePageUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharingTest {

    private val sharer = "https://m.facebook.com/sharer/sharer.php?u="

    @Test
    fun sharedLink_opensFacebooksShareDialog() {
        assertEquals(sharer + "https%3A%2F%2Fexample.com%2Fa%3Fb%3D1", facebookShareUrl("https://example.com/a?b=1"))
    }

    @Test
    fun linkInsideText_isFound() {
        assertEquals(
            sharer + "https%3A%2F%2Fnews.example%2Fstory",
            facebookShareUrl("Look at this: https://news.example/story. Wild!")
        )
        assertEquals(
            sharer + "http%3A%2F%2Fexample.com%2Fx",
            facebookShareUrl("(http://example.com/x)")
        )
    }

    @Test
    fun firstLinkWins() {
        assertEquals(
            sharer + "https%3A%2F%2Fa.example%2F",
            facebookShareUrl("https://a.example/ and https://b.example/")
        )
    }

    @Test
    fun textWithoutALink_isNotShareable() {
        assertNull(facebookShareUrl(null))
        assertNull(facebookShareUrl(""))
        assertNull(facebookShareUrl("just some words"))
        assertNull(facebookShareUrl("ftp://example.com/file"))
    }

    @Test
    fun sharedPage_isCleanedAndOnTheDesktopHost() {
        assertEquals(
            "https://www.facebook.com/story.php?story_fbid=1&id=2",
            shareablePageUrl("https://m.facebook.com/story.php?story_fbid=1&id=2&mibextid=abc&__tn__=R")
        )
        assertEquals("https://www.facebook.com/reel/123", shareablePageUrl("https://mbasic.facebook.com/reel/123"))
        assertEquals("https://www.facebook.com/groups/9/", shareablePageUrl("https://www.facebook.com/groups/9/"))
    }

    @Test
    fun otherHosts_keepTheirHost() {
        assertEquals("https://m.facebook.community/x", shareablePageUrl("https://m.facebook.community/x"))
        assertEquals("https://example.com/m.facebook.com", shareablePageUrl("https://example.com/m.facebook.com"))
    }
}
