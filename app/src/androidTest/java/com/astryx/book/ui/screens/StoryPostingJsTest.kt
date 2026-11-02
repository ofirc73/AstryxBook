package com.astryx.book.ui.screens
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * story_posting.js tells the app (AstryxPage "storyPosting") when Share to story starts posting and
 * when it fails. The fixture is the desktop creator's bottom row as story_layout.js marks it, with
 * Share's attributes read on the emulator (2026-11-02): aria-busy and aria-disabled "true" while
 * posting; after a failed upload aria-busy goes back to "false" and the page stays the creator.
 */
@RunWith(AndroidJUnit4::class)
class StoryPostingJsTest {

    private val page = PageFixture()

    private fun open(html: String = CREATOR) {
        page.load(CREATOR_URL, html)
        page.stubPageCalls()
        page.run(R.raw.story_posting)
    }

    private fun calls(): String = page.eval("window.__pageCalls.map(function (c) { return c.call + ' ' + c.state; }).join(', ')")

    private fun setBusy(busy: Boolean) = page.eval(
        "var share = document.getElementById('share'); share.setAttribute('aria-busy', '$busy');" +
            "share.setAttribute('aria-disabled', '$busy'); 1"
    )

    @Test
    fun sharePosting_isReported_andAFailedUpload_too() {
        open()
        setBusy(true)
        assertTrue("posting never reported", page.waitFor { calls() == "\"storyPosting posting\"" })
        setBusy(false)
        assertTrue("the failure never reported", page.waitFor { calls() == "\"storyPosting posting, storyPosting failed\"" })
        // Share again: posting again.
        setBusy(true)
        assertTrue(page.waitFor { calls() == "\"storyPosting posting, storyPosting failed, storyPosting posting\"" })
    }

    @Test
    fun aStoryPosted_isNotAFailure() {
        open()
        setBusy(true)
        assertTrue(page.waitFor { calls() == "\"storyPosting posting\"" })
        // Facebook moves on to its stories page in-page; the app learns it from the layer's URL.
        page.eval("history.pushState({}, '', '/stories'); 1")
        setBusy(false)
        Thread.sleep(POSTED_WAIT_MS)
        assertEquals("\"storyPosting posting\"", calls())
    }

    @Test
    fun aBusyButtonOutsideTheBottomRow_isNotPosting() {
        // A tool loading (the music list) is not Share.
        open(CREATOR.replace("data-astryx-story='actions'", ""))
        setBusy(true)
        Thread.sleep(POSTED_WAIT_MS)
        assertEquals("\"\"", calls())
    }

    companion object {
        private const val CREATOR_URL = "https://www.facebook.com/stories/create"
        // Longer than the script's check (500 ms) plus its wait for a failure (2 s).
        private const val POSTED_WAIT_MS = 3_000L
        private const val CREATOR =
            "<html><body><div role='region'><div data-astryx-story='actions'>" +
                "<div role='button' aria-label='Discard'>Discard</div>" +
                "<div role='button' id='share' aria-busy='false' aria-label='Share to story'>Share to story</div>" +
                "</div></div></body></html>"
    }
}
