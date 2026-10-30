package com.astryx.book.ui.screens
import androidx.annotation.RawRes
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * scripts.js's hideFeedPost, as adblock.js and the hide_* scripts use it, on Home's two layouts read
 * on the emulator (2026-10-30): a post as one card (div[data-dcm-id="1"], as on a post opened from a
 * link) and a post as rows of the feed's list (header, text, actions), each with the post's
 * data-tracking-duration-id, as on Home since then. A thin gap row sits between posts.
 */
@RunWith(AndroidJUnit4::class)
class FeedPostJsTest {

    private class Harness {
        private val page = PageFixture(width = 1080, height = 2000)

        fun load(feed: String, vararg scripts: Int) {
            page.load("https://m.facebook.com/", "<html><body>$feed</body></html>")
            page.runBackHandler()
            scripts.forEach { page.run(it) }
        }

        // Which of [ids] are hidden, as "id=true,…".
        fun hidden(vararg ids: String): String = page.eval(
            ids.joinToString(",", "[", "]") { "'$it'" } +
                ".map(id => id + '=' + (getComputedStyle(document.getElementById(id)).display === 'none')).join(',')"
        ).removeSurrounding("\"")
    }

    private fun feed(vararg posts: String) =
        "<div data-type='vscroller'><div id='composer' style='height:68px'>What's on your mind?</div>" +
            posts.joinToString("") + "</div>"

    private fun gap(id: String) = "<div id='$id' style='height:1px'></div>"

    // A post as rows: gap, header (holding [header]), text, actions; ids "<id>-gap", "<id>-header"….
    private fun rows(id: String, track: Int, header: String = "", body: String = "") =
        gap("$id-gap") + listOf("header" to "A Page $header", "text" to "Some text $body", "actions" to "Like")
            .joinToString("") { (part, inside) ->
                "<div id='$id-$part' data-tracking-duration-id='$track' data-mcomponent='MContainer' " +
                    "style='height:60px'>$inside</div>"
            }

    // A post as one card, after its gap.
    private fun card(id: String, track: Int, header: String = "") =
        gap("$id-gap") + "<div id='$id' data-dcm-id='1' data-tracking-duration-id='$track' " +
            "data-mcomponent='MContainer' style='height:300px'><div>A Page $header</div><div>Some text</div></div>"

    // A feed ad's label, as adblock.js finds it (its grey).
    private val adLabel = "<div class='native-text rslh'><span class='f5' style='color:#8a8d91'>Sponsored</span></div>"

    // A suggested post's "•" before Follow, as hide_suggested.js finds it.
    private val suggested = "<div data-mcomponent='TextArea' data-type='text' data-tti-phase='-1'>" +
        "<div class='native-text'><span>•</span></div></div>"

    private fun hides(@RawRes script: Int, marker: String, inBody: Boolean = false): String {
        val h = Harness()
        h.load(
            feed(
                rows("a", 1),
                if (inBody) rows("b", 2, body = marker) else rows("b", 2, header = marker),
                rows("c", 3)
            ),
            script
        )
        return h.hidden("a-header", "a-actions", "b-gap", "b-header", "b-text", "b-actions", "c-gap", "c-header")
    }

    private val wholeSecondPost =
        "a-header=false,a-actions=false,b-gap=true,b-header=true,b-text=true,b-actions=true,c-gap=false,c-header=false"

    @Test
    fun anAdAsRows_isHiddenWhole_withTheGapAboveIt() {
        assertEquals(wholeSecondPost, hides(R.raw.adblock, adLabel))
    }

    @Test
    fun anAdAsACard_isHidden_withTheGapAboveIt() {
        val h = Harness()
        h.load(feed(card("a", 1), card("b", 2, adLabel), card("c", 3)), R.raw.adblock)

        assertEquals("a=false,b-gap=true,b=true,c-gap=false,c=false", h.hidden("a", "b-gap", "b", "c-gap", "c"))
    }

    @Test
    fun aSuggestedPostAsRows_isHiddenWhole() {
        assertEquals(wholeSecondPost, hides(R.raw.hide_suggested, suggested))
    }

    @Test
    fun peopleYouMayKnowAsRows_isHiddenWhole() {
        assertEquals(wholeSecondPost, hides(R.raw.hide_pymk, "<div class='hscroller'>People</div>", inBody = true))
    }

    @Test
    fun reelsAsRows_areHiddenWhole() {
        assertEquals(
            wholeSecondPost,
            hides(R.raw.hide_reels, "<div class='fl ac'><div class='native-text'>&#xF2020;</div></div>", inBody = true)
        )
    }

    @Test
    fun anAdsLabelOutsideTheFeed_hidesNothing() {
        // A photo opened directly has the grey label with no feed list around it.
        val h = Harness()
        h.load("<div id='photo' style='height:600px'>$adLabel</div>", R.raw.adblock)

        assertEquals("photo=false", h.hidden("photo"))
    }
}
