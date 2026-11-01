package com.astryx.book.ui.screens
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Settings gear scripts.js puts on a reel page (custom-settings-btn-reels), which has no
 * header for the main gear: at the top right (left when mirrored), and below Facebook's tab bar
 * when that's above the reel, as the phone's Reels tab shows it: the gear sat on the bar's last
 * tab, Marketplace (seen on a Galaxy A56, 2026-11-01). The fixture's tab bar is built like
 * Facebook's: a role=tablist strip of six tabs across the top.
 */
@RunWith(AndroidJUnit4::class)
class ReelsSettingsButtonJsTest {

    private class Harness {
        private val page = PageFixture(width = 1080, height = 2000)

        fun load(body: String, dir: String = "ltr") {
            page.load(
                "https://m.facebook.com/reel/123/",
                "<html dir='$dir'><head><meta name='viewport' content='width=device-width'></head>" +
                    "<body style='margin:0;background:#000'>$body<video style='width:100%;height:600px'></video></body></html>"
            )
            page.run(R.raw.scripts)
        }

        fun eval(js: String) = page.eval(js)

        fun waitFor(js: String, ms: Long = 5000) = page.waitFor(ms) { eval(js) == "true" }
    }

    private val gear = "document.getElementById('custom-settings-btn-reels')"

    private fun Harness.gearBox() = eval(
        "(function (r) { return Math.round(r.left) + ',' + Math.round(r.top) + ',' + Math.round(r.right); })" +
            "($gear.getBoundingClientRect())"
    ).removeSurrounding("\"").split(",").map { it.toInt() }

    @Test
    fun withoutTheTabBar_theGearIsAtTheTopRight() {
        val h = Harness()
        h.load("")
        assertTrue("no gear on the reel page", h.waitFor("!!$gear", 6000))
        val (left, top, right) = h.gearBox()
        assertEquals(12, top)
        assertTrue("not at the right (left $left, right $right)", right >= 400 - 12 - 1 && left > 200)
    }

    @Test
    fun belowFacebooksTabBar_whenItsAboveTheReel() {
        val h = Harness()
        h.load(TAB_BAR)
        assertTrue("no gear on the reel page", h.waitFor("!!$gear", 6000))
        assertTrue("the gear is on the tab bar (${h.gearBox()})", h.waitFor("$gear.getBoundingClientRect().top >= 56 + 12", 3000))
        assertEquals("the gear isn't on any tab", "true", h.eval(
            "(function (g) { return [].every.call(document.querySelectorAll('[role=tab]'), function (t) {" +
                " var r = t.getBoundingClientRect(); return g.top >= r.bottom || g.bottom <= r.top || g.left >= r.right || g.right <= r.left; }); })" +
                "($gear.getBoundingClientRect())"
        ))
    }

    @Test
    fun aTabBarShowingLater_movesTheGearBelowIt() {
        // Facebook moves between the feed and reels in-page; the gear is placed again each second.
        val h = Harness()
        h.load("")
        assertTrue(h.waitFor("!!$gear", 6000))
        assertEquals(12, h.gearBox()[1])
        h.eval("document.body.insertAdjacentHTML('afterbegin', \"$TAB_BAR\"); 1")
        assertTrue("the gear stayed on the tab bar", h.waitFor("$gear.getBoundingClientRect().top >= 56 + 12", 3000))
    }

    @Test
    fun aMirroredPage_hasTheGearAtTheLeft_belowTheTabBar() {
        val h = Harness()
        h.load(TAB_BAR, dir = "rtl")
        assertTrue(h.waitFor("!!$gear", 6000))
        assertTrue(h.waitFor("$gear.getBoundingClientRect().top >= 56 + 12", 3000))
        assertEquals(12, h.gearBox()[0])
    }

    private companion object {
        // Six tabs across a 56 px strip at the top, fixed, as on the phone's Reels tab.
        const val TAB_BAR =
            "<div role='tablist' style='position:fixed;top:0;left:0;right:0;height:56px;display:flex;background:#222;z-index:10'>" +
                "<div role='tab' style='flex:1'></div><div role='tab' style='flex:1'></div><div role='tab' style='flex:1'></div>" +
                "<div role='tab' style='flex:1'></div><div role='tab' style='flex:1'></div><div role='tab' style='flex:1'></div></div>"
    }
}
