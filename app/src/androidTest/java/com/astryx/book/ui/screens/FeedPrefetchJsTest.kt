package com.astryx.book.ui.screens
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * feed_prefetch.js: Facebook watches a list's loading row (its last row, with
 * data-on-first-visibility-action-id) with a plain IntersectionObserver and asks for the next
 * posts when it's on screen; the script also watches it LEAD below the window and passes that on
 * as Facebook's own entry. Read on the phone (2026-10-30). The native observer is a stub
 * ([PageFixture.stubIntersectionObserver]): observer 0 is the page's, 1 the script's early one.
 */
@RunWith(AndroidJUnit4::class)
class FeedPrefetchJsTest {

    private class Harness {
        private val page = PageFixture()

        // The feed list with a post row and its loading row, a first-visibility mark outside any
        // list, and the page's observer ([options] its options) watching [watched].
        fun load(options: String = "{}", vararg watched: String) {
            page.load(
                "https://m.facebook.com/",
                "<html><body><div data-type='vscroller'><div id='post'>A post</div>" +
                    "<div id='loading' data-on-first-visibility-action-id='16479'></div></div>" +
                    "<div id='seen' data-on-first-visibility-action-id='32337'></div></body></html>"
            )
            page.stubIntersectionObserver()
            page.run(R.raw.feed_prefetch)
            page.eval(
                """
                window.__seen = [];
                window.__io = new IntersectionObserver(function(entries, obs) {
                  entries.forEach(function(e) { window.__seen.push(e.target.id + ':' + (obs === window.__io)); });
                }, $options);
                ${watched.joinToString("") { "window.__io.observe(document.getElementById('$it'));" }}
                1
                """.trimIndent()
            )
        }

        fun eval(js: String) = page.eval(js).trim('"')

        fun seen() = eval("window.__seen.join(',')")

        // The early observer's margin and what it watches, or "none".
        fun early() = eval(
            "window.__ios[1] ? window.__ios[1].opts.rootMargin + ' ' + " +
                "window.__ios[1].targets.map(function(t) { return t.id; }).join(',') : 'none'"
        )
    }

    @Test
    fun theLoadingRow_isWatchedTenScreensAhead() {
        val h = Harness()
        h.load("{}", "post", "loading")

        assertEquals("0px 0px 1000% 0px loading", h.early())
    }

    @Test
    fun theLoadingRowNearingTheScreen_reachesFacebooksCallbackAsItsOwnObservers() {
        val h = Harness()
        h.load("{}", "loading")

        h.eval("window.__deliver(1, 'loading', 0.01)")

        assertEquals("loading:true", h.seen())
    }

    @Test
    fun theEarlyWatchsNotNearEntries_areNotPassedOn() {
        val h = Harness()
        h.load("{}", "loading")

        h.eval("window.__deliver(1, 'loading', 0)")

        assertEquals("", h.seen())
    }

    @Test
    fun aFirstVisibilityMarkOutsideAList_keepsFacebooksTiming() {
        // Such marks log what the user saw: reporting them early would log posts not yet seen.
        val h = Harness()
        h.load("{}", "seen")

        h.eval("window.__deliver(1, 'seen', 0.01)")

        assertEquals("", h.seen())
        h.eval("window.__deliver(0, 'seen', 0.01)")
        assertEquals("seen:true", h.seen())
    }

    @Test
    fun facebookUnobservingTheRow_stopsTheEarlyWatch() {
        val h = Harness()
        h.load("{}", "loading")

        h.eval("window.__io.unobserve(document.getElementById('loading'))")

        assertEquals("0px 0px 1000% 0px ", h.early())
    }

    @Test
    fun anObserverWithItsOwnMargin_isLeftAlone() {
        val h = Harness()
        h.load("{ rootMargin: '100px' }", "loading")

        assertEquals("none", h.early())
    }

    @Test
    fun wrappedObserver_isStillAnIntersectionObserver() {
        val h = Harness()
        h.load()

        assertEquals("true", h.eval("window.__io instanceof IntersectionObserver"))
    }
}
