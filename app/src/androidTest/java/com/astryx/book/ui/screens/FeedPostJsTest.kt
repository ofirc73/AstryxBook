package com.astryx.book.ui.screens
import androidx.annotation.RawRes
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.FEED_AD_LABEL
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
            page.stubPageCalls()
            scripts.forEach { page.run(it) }
        }

        // The ads the page told the app it hid (adblock.js, AstryxPage call adHidden), as "adHidden feed" each.
        fun adsCounted(): String = page.pageCalls("kind").removeSurrounding("\"")

        // Something added to the page, as Facebook does all the time: adblock.js looks at the feed again.
        fun eval(js: String) = page.eval(js)

        fun change(html: String = "<div>more</div>") {
            page.eval("document.querySelector('[data-type=vscroller]').insertAdjacentHTML('beforeend', \"$html\"); 1")
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

    private val adLabel = FEED_AD_LABEL

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
        assertEquals("adHidden feed", h.adsCounted())
    }

    // The app counts the ads hidden (developer options, the log): the one way to see on a phone that
    // the feed's ads are still found, the test account being shown none.
    @Test
    fun anAdHidden_isCountedOnce_howeverOftenThePageChanges() {
        val h = Harness()
        h.load(feed(rows("a", 1), rows("b", 2, header = adLabel), rows("c", 3)), R.raw.adblock)
        h.change()
        h.change()
        assertEquals("adHidden feed", h.adsCounted())

        h.change(rows("d", 4, header = adLabel))
        assertEquals("adHidden feed, adHidden feed", h.adsCounted())
    }

    // adblock.js looks at the feed again on every change: an ad already hidden whole is left alone,
    // and one whose next row came later is hidden again, whole.
    @Test
    fun anAdAlreadyHidden_isLeftAlone_untilARowOfItComes() {
        val h = Harness()
        h.load(feed(rows("a", 1), rows("b", 2, header = adLabel)), R.raw.adblock)
        h.eval("window.__hides = 0; var hide = window.hideFeedPost;" +
            " window.hideFeedPost = function (el) { window.__hides++; return hide(el); }; 1")
        h.change()
        h.change()
        assertEquals("an ad hidden whole was hidden again", "0", h.eval("window.__hides"))

        h.eval("document.getElementById('b-actions').insertAdjacentHTML('afterend'," +
            " \"<div id='b-late' data-tracking-duration-id='2' style='height:60px'>Shop now</div>\"); 1")
        assertEquals("b-header=true,b-late=true", h.hidden("b-header", "b-late"))
        assertEquals("adHidden feed", h.adsCounted())
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
        assertEquals("", h.adsCounted())
    }
}
