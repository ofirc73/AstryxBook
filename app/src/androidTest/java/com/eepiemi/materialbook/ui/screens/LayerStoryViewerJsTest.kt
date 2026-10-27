package com.eepiemi.materialbook.ui.screens

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eepiemi.materialbook.PageFixture
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * layer_story_viewer.js lays out the desktop stories viewer (a story opened from a chat in the
 * Messages layer) for a phone. The fixture is the viewer's layout as read from a Galaxy A56
 * (2026-10-27): a 360 px Stories column (its navigation fixed below the bar) beside the story's pane
 * in one row of the viewer's dialog, the viewer's close button in a fixed box at the top, and
 * Facebook's banner (outside the dialog) with the account buttons in another.
 */
@RunWith(AndroidJUnit4::class)
class LayerStoryViewerJsTest {

    private val page = PageFixture(width = 1080, height = 2000)

    private fun load(url: String) {
        page.load(url, VIEWER)
        page.eval("window.__resized = 0; window.addEventListener('resize', function () { window.__resized++; }); 1")
        page.run(R.raw.layer_story_viewer)
        Thread.sleep(300)
    }

    private fun eval(js: String) = page.eval(js)

    private fun display(id: String) = eval("getComputedStyle(document.getElementById('$id')).display")
    private fun paneWidth() = eval("document.getElementById('pane').getBoundingClientRect().width").toFloat()
    private fun windowWidth() = eval("innerWidth").toFloat()

    @Test
    fun aStory_takesTheWholeWidth_withoutTheColumnOrTheBar() {
        load(STORY_URL)
        assertEquals("the Stories column is still beside the story", "\"none\"", display("column"))
        assertTrue("the story's pane stayed narrow (${paneWidth()} of ${windowWidth()})", paneWidth() >= windowWidth() - 1)
        assertEquals("the viewer's close button is still over the story", "\"none\"", display("close"))
        assertEquals("Facebook's banner is still over the story", "\"none\"", display("banner"))
        // The story's own header (not fixed) stays.
        assertEquals("\"block\"", display("header"))
        assertTrue("Facebook wasn't told to size the story again", eval("window.__resized").toInt() > 0)
    }

    @Test
    fun backInTheChat_theBarShowsAgain() {
        load(STORY_URL)
        eval("history.pushState({}, '', '/messages/e2ee/t/1/'); document.body.appendChild(document.createElement('p')); 1")
        Thread.sleep(500)
        assertEquals("Facebook's banner stayed hidden in the chat", "\"block\"", display("banner"))
        assertEquals("\"block\"", display("column"))
    }

    @Test
    fun aChatOrTheStoryCreator_isLeftAlone() {
        for (url in listOf("https://www.facebook.com/messages/e2ee/t/1/", "https://www.facebook.com/stories/create/")) {
            load(url)
            assertEquals("$url: the column was hidden", "\"block\"", display("column"))
            assertEquals("$url: the banner was hidden", "\"block\"", display("banner"))
        }
    }

    companion object {
        private const val STORY_URL = "https://www.facebook.com/stories/1000/UzpfSVNDOjE=/"
        private const val VIEWER =
            "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'></head>" +
                "<body style='margin:0'>" +
                "<div id='banner' style='position:fixed;top:0;left:0;right:0;height:56px;z-index:3'>" +
                "<a href='/'>Facebook</a><div role='navigation' aria-label='Account Controls and Settings'>" +
                "<div role='button' aria-label='Messenger'>m</div></div></div>" +
                "<div role='dialog' style='position:fixed;top:0;left:0;right:0;bottom:0'>" +
                "<div id='close' style='position:fixed;top:8px;left:16px;width:40px;height:40px;z-index:1'>" +
                "<div role='button' aria-label='Close'>x</div></div>" +
                "<div id='row' style='display:flex;height:100%'>" +
                "<div id='column' style='flex:0 1 auto;width:360px'>" +
                "<div role='navigation' style='position:fixed;top:56px;bottom:0;width:360px'>Stories</div></div>" +
                "<div id='pane' style='flex:1 1 auto;position:relative'>" +
                "<div id='header' style='position:absolute;top:12px;left:4px;right:4px;height:36px'>" +
                "<a href='/me'>Name</a><div role='button' aria-label='Pause'>||</div></div></div>" +
                "</div></div></body></html>"
    }
}
