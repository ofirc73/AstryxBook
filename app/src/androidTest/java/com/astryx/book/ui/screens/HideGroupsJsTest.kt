package com.astryx.book.ui.screens
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for hide_groups.js (upstream's *Hide groups*): the group suggestions m.facebook.com shows
 * on its search page and under a post's comments, hidden; nothing hidden elsewhere. No live check:
 * the test account isn't shown these suggestions reliably, and the script only hides.
 */
@RunWith(AndroidJUnit4::class)
class HideGroupsJsTest {

    private val page = PageFixture()

    // Search results: horizontal rows, each in its own card; the last row is the groups'.
    private val search =
        "<div class='m bg-s1' id='people'><div data-is-h-scrollable='true'>people</div></div>" +
            "<div class='m bg-s1' id='groups'><div data-is-h-scrollable='true'>groups</div></div>"

    // Under a post's comments: the group's header card.
    private val comments = "<div class='m bg-s2' id='group'><h3 class='m' data-tti-phase='-1'>A group</h3></div>"

    private fun shownAt(url: String, body: String, vararg ids: String): List<String> {
        page.load(url, "<html><body>$body</body></html>")
        page.run(R.raw.hide_groups)
        return ids.map { page.eval("getComputedStyle(document.getElementById('$it')).display").removeSurrounding("\"") }
    }

    @Test
    fun onTheSearchPage_theLastRowsCard_isHidden() =
        assertEquals(listOf("block", "none"), shownAt("https://m.facebook.com/search/top/?q=x", search, "people", "groups"))

    @Test
    fun underAPostsComments_theGroupsCard_isHidden() =
        assertEquals(listOf("none"), shownAt("https://m.facebook.com/story.php?story_fbid=1&id=2", comments, "group"))

    @Test
    fun onHome_nothingIsHidden() =
        assertEquals(listOf("block", "block", "block"), shownAt("https://m.facebook.com/", search + comments, "people", "groups", "group"))
}
