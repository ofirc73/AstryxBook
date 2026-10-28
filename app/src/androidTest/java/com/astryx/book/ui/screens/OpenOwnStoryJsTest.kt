package com.astryx.book.ui.screens
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * open_own_story.js opens the user's own stories on Facebook's stories page after Share to story
 * (MessagesLayer). The fixture is that page's Stories column as read from a Galaxy A56
 * (2026-10-27): the Your story card (a button) with the Add to story link drawn over its right end,
 * as a sibling, and a friend's card under All stories.
 */
@RunWith(AndroidJUnit4::class)
class OpenOwnStoryJsTest {

    private val page = PageFixture(width = 1080, height = 2000)

    private fun open(html: String): String {
        page.load(STORIES_URL, html)
        return page.run(R.raw.open_own_story)
    }

    @Test
    fun yourStoryCard_isOpened_andThePageMarkedForTheViewerLayout() {
        assertEquals("\"opened\"", open(WITH_OWN_STORY))
        assertEquals("not the user's own card was opened", "\"own\"", page.eval("window.__opened || ''"))
        assertEquals("true", page.eval("window.__astryxSharedStoryOpen === true"))
    }

    @Test
    fun withoutAStoryOfYourOwn_nothingIsOpened() {
        // Only Create a story (one link, the whole card): nothing to show, the layer closes.
        assertEquals("\"none\"", open(WITHOUT_OWN_STORY))
        assertEquals("\"\"", page.eval("window.__opened || ''"))
        assertEquals("false", page.eval("window.__astryxSharedStoryOpen === true"))
    }

    companion object {
        private const val STORIES_URL = "https://www.facebook.com/stories"
        private const val HEAD =
            "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'></head>" +
                "<body style='margin:0'><div role='navigation' style='position:fixed;top:56px;bottom:0;width:360px'>" +
                "<h2>Your story</h2>"
        private const val FRIEND =
            "<h2>All stories</h2>" +
                "<div role='button' onclick=\"window.__opened='friend'\" style='margin:8px;width:344px;height:76px'>Friend</div>"
        private const val WITH_OWN_STORY = HEAD +
            "<div style='position:relative'>" +
            "<div role='button' onclick=\"window.__opened='own'\" style='margin:8px;width:344px;height:76px'>Me 1m</div>" +
            "<div style='position:absolute;top:8px;right:16px'>" +
            "<a href='/stories/create/' aria-label='Add to story' style='display:block;width:60px;height:60px'>+</a></div>" +
            "</div>" + FRIEND + "</div></body></html>"
        private const val WITHOUT_OWN_STORY = HEAD +
            "<a href='/stories/create/' style='display:block;margin:8px;width:344px;height:76px'>Create a story</a>" +
            FRIEND + "</div></body></html>"
    }
}
