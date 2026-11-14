package com.astryx.book.ui.screens
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for sticky_navbar.js (upstream's *Sticky navbar*, mobile site): Facebook's top bar and tab
 * bar are pinned to the top, and the feed's list starts below them. The fixture is the shape the
 * script looks for on m.facebook.com; scripts.js's isDesktopMode/isFeed are stubbed. Live:
 * stickyNavbar_keepsTheTabBarOnScreen_downTheFeed.
 */
@RunWith(AndroidJUnit4::class)
class StickyNavbarJsTest {

    private val page = PageFixture(width = 1080, height = 2000)

    private val feed =
        "<div id='nav' class='m' data-tti-phase='-1' data-mcomponent='MContainer' data-type='container' data-focusable='true' style='height:50px'>" +
            "<div aria-label='Facebook logo'></div></div>" +
            "<div id='tabs' class='m' role='tablist' data-tti-phase='-1' data-type='container' data-mcomponent='MContainer' style='height:40px'>" +
            "<div aria-label='feed, 1 of 5'></div></div>" +
            "<div id='list' data-type='vscroller' data-is-pull-to-refresh-allowed='true'><div id='rows' style='height:5000px'>posts</div></div>"

    private fun style(id: String, property: String) =
        page.eval("document.getElementById('$id').style.$property").removeSurrounding("\"")

    @Test
    fun theTopBarAndTabBar_arePinned_andTheFeedStartsBelowThem() {
        page.load("https://m.facebook.com/", "<html><body>$feed</body></html>")
        page.eval("window.isDesktopMode = function () { return false; }; window.isFeed = function () { return true; }; 1")
        page.run(R.raw.sticky_navbar)

        assertEquals("fixed", style("nav", "position"))
        assertEquals("0px", style("nav", "top"))
        assertEquals("fixed", style("tabs", "position"))
        assertEquals("the tab bar sits under the top bar", "50px", style("tabs", "top"))
        assertEquals("the list's first row starts under both", "90px", style("rows", "marginTop"))
    }
}
