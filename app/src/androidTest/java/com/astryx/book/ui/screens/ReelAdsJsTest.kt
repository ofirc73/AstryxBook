package com.astryx.book.ui.screens
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

// evaluateJavascript returns strings JSON-quoted.
private fun String.unquoted() = removeSurrounding("\"")

/**
 * Tests for the Reels part of adblock.js against a real WebView, same harness approach as
 * BackHandlerJsTest. Each fixture reel is a `div.vertically-snappable`, as on m.facebook.com's
 * Reels viewer; a hidden ad is marked `data-ad-hidden="true"`.
 */
@RunWith(AndroidJUnit4::class)
class ReelAdsJsTest {

    private class Harness {
        private val page = PageFixture(width = 1080, height = 2000)

        fun load(body: String) {
            page.load("https://m.facebook.com/reel/1", "<html><body>$body</body></html>")
            // The mobile site (scripts.js defines this for the page scripts).
            eval("window.isDesktopMode = () => false;")
            page.run(R.raw.adblock)
        }

        fun eval(js: String) = page.eval(js)

        fun hidden(vararg ids: String): String = eval(
            ids.joinToString(",", "[", "]") { "'$it'" } +
                ".map(id => document.getElementById(id).dataset.adHidden === 'true').join(',')"
        ).unquoted()
    }

    private fun reel(id: String, vararg spans: String) =
        "<div class='vertically-snappable' id='$id' style='height:400px'>" +
            spans.joinToString("") { "<span>$it</span>" } + "</div>"

    @Test
    fun sponsoredLabel_inEveryScript_hidesTheReel() {
        val h = Harness()
        h.load(
            reel("en", "Some Page", "Sponsored") +
                reel("he", "עמוד כלשהו", "ממומן") +
                reel("ru", "Страница", "Реклама") +
                reel("ja", "ページ", "広告") +
                reel("hi", "पेज", "प्रायोजित")
        )

        assertEquals("true,true,true,true,true", h.hidden("en", "he", "ru", "ja", "hi"))
    }

    @Test
    fun labelInsideAnotherWord_isNotAnAd() {
        val h = Harness()
        h.load(
            reel("en", "Add friend") +
                reel("he", "תכנים ממומנים בקרוב") +
                reel("ru", "Рекламами") +
                reel("ja", "広告主の話")
        )

        assertEquals("false,false,false,false", h.hidden("en", "he", "ru", "ja"))
    }

    @Test
    fun facebooksReelAdLabel_asOnThePhone_hidesTheReel() {
        // Sponsored reels on m.facebook.com (Facebook in English and in Hebrew): the label is a span
        // of its own at the bottom left, next to a megaphone icon that's a separate element.
        val h = Harness()
        h.load(
            "<div class='vertically-snappable' id='ad' style='height:400px'><span>Some Brand</span>" +
                "<div class='native-text rslh' dir='auto'><span class='f19'>Ad</span></div></div>"
        )

        assertEquals("true", h.hidden("ad"))
    }

    @Test
    fun aCaptionUsingALabelWord_isNotAnAd() {
        // Only a span that is the label counts, not one that mentions it.
        val h = Harness()
        h.load(
            reel("en", "My friend", "Check out this Ad we made for school") +
                reel("he", "חברה", "הסרט הופק במימון קרן הקולנוע") +
                reel("he2", "חברה", "זה לא תוכן ממומן, פשוט אהבתי")
        )

        assertEquals("false,false,false", h.hidden("en", "he", "he2"))
    }

    @Test
    fun reelAddedLater_isCheckedToo() {
        val h = Harness()
        h.load("<div id='feed'>${reel("first", "A friend's reel")}</div>")
        h.eval(
            "document.getElementById('feed').insertAdjacentHTML('beforeend', " +
                "\"${reel("later", "Pagina", "Sponsorizzato")}\");"
        )

        // The MutationObserver has run by now: its callback is a microtask of the insert.
        assertEquals("false,true", h.hidden("first", "later"))
    }
}
