package com.astryx.book.ui.screens
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.astryx.book.PageFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * STORIES_ROW_JS (PostedStoryEffects): after a story is posted, Home at its top is reloaded until its
 * stories row shows the user's story. The row as m.facebook.com draws it (read on the emulator,
 * 2026-11-17): "Create story", then a card per story, the user's own labelled "View your story. …".
 */
@RunWith(AndroidJUnit4::class)
class StoriesRowJsTest {

    private val page = PageFixture(width = 1080, height = 2000)

    // Home with its row ([cards]) and a feed tall enough to scroll. Its title is changed once loaded,
    // so a reload shows in the WebView's title: a page reloaded at the same address can leave
    // evaluateJavascript unanswered (ShareIntoFacebookTest), so nothing is evaluated after one.
    private fun home(cards: String) {
        page.load(
            "https://m.facebook.com/",
            "<html><head><title>Home</title></head><body style='margin:0'><div id='row' style='display:flex'>$cards</div>" +
                "<div style='height:4000px'>feed</div></body></html>"
        )
        page.eval("document.title = '$MARKED'; 1")
    }

    private fun card(label: String) = "<div role='button' aria-label='$label' style='width:115px;height:207px'>$label</div>"
    private val createStory = card("Create story")
    private val someonesStory = card("View National Geographic&#39;s story. 2 unseen stories")

    private fun run(): String = page.eval(STORIES_ROW_JS).removeSurrounding("\"")
    private fun title(): String? {
        var title: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { title = page.webView.title }
        return title
    }
    private fun reloaded(): Boolean = page.waitFor { title() != MARKED }

    @Test
    fun theRowWithYourStory_isLeftAsItIs() {
        home(createStory + card("View your story. 1 unseen story") + someonesStory)
        assertEquals("shown", run())
        Thread.sleep(1000)
        assertEquals("Home was reloaded with your story in its row", MARKED, title())
    }

    @Test
    fun theRowWithoutYourStory_hasHomeReloaded() {
        home(createStory + someonesStory)
        assertEquals("reloaded", run())
        assertTrue("Home wasn't reloaded", reloaded())
    }

    // Facebook in another language: the user's card can't be told, so Home is reloaded, fewer times.
    @Test
    fun aRowInAnotherLanguage_isReloadedBlind() {
        home(card("יצירת סטורי") + card("סטורי"))
        assertEquals("blind", run())
        assertTrue("Home wasn't reloaded", reloaded())
    }

    @Test
    fun homeScrolledDown_isLeftWhereTheUserIs() {
        home(createStory + someonesStory)
        page.eval("window.scrollTo(0, 1200); 1")
        assertEquals("away", run())
        Thread.sleep(1000)
        assertEquals("Home was reloaded under the user", MARKED, title())
    }

    private companion object {
        const val MARKED = "Home, not reloaded"
    }
}
