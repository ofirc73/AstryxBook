package com.astryx.book.utils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartPageTest {

    @Test
    fun noLink_opensHome_orFeedsWhenOn() {
        assertEquals(HOME_PAGE_URL, startPageUrl(null, startOnFeeds = false))
        assertEquals(FEEDS_PAGE_URL, startPageUrl(null, startOnFeeds = true))
    }

    @Test
    fun aLink_alwaysWins() {
        val link = "https://m.facebook.com/groups/123/"
        assertEquals(link, startPageUrl(link, startOnFeeds = false))
        assertEquals(link, startPageUrl(link, startOnFeeds = true))
    }

    @Test
    fun feeds_startsOnTheMobileMenu_markedForOpenFeedsJs() {
        // open_feeds.js acts only on this mark, and only on the mobile site's menu.
        assertTrue(FEEDS_PAGE_URL.startsWith("https://m.facebook.com/bookmarks/"))
        assertTrue(FEEDS_PAGE_URL.endsWith("#astryx-feeds"))
    }

    @Test
    fun bothPages_areFacebookPagesTheAppShows() {
        assertTrue(isFacebookWebUrl(HOME_PAGE_URL))
        assertTrue(isFacebookWebUrl(FEEDS_PAGE_URL))
    }
}
