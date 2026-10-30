package com.astryx.book.ui.screens
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * open_new_post.js: a post published from the composer opened on a page other than Home (a memory
 * shared from the Memories page) opens the user's profile, and there the newest post. The pages are
 * m.facebook.com's as read on the emulator (2026-10-30): the composer at /composer/, its snackbar
 * after posting, and a post's time in its header. The navigation is caught (__astryxNewPostGo).
 */
@RunWith(AndroidJUnit4::class)
class OpenNewPostJsTest {

    private class Harness {
        private val page = PageFixture(width = 1080, height = 2000, domStorage = true)

        fun load(path: String, body: String = "", setup: String = "") {
            page.load(
                "https://m.facebook.com$path",
                "<html><head><meta name='viewport' content='width=device-width'></head><body style='margin:0'>$body</body></html>"
            )
            eval(
                "window.__astryxNewPostGo = function (url) { window.__went = url; };" +
                    " window.__astryxNewPostFindMs = 2000; document.cookie = 'c_user=$USER; path=/'; $setup 1"
            )
            page.run(R.raw.open_new_post)
        }

        fun eval(js: String) = page.eval(js)

        fun waitFor(js: String, ms: Long = 5000) = page.waitFor(ms) { eval(js) == "true" }

        // Facebook's composer opened from this page, and left (by posting or not): its moves, as
        // recorded on the emulator.
        fun openTheComposer() = eval("history.replaceState(null, '', '/composer/'); 1")

        fun leaveTheComposer(back: String) =
            eval("history.pushState(null, '', '/composer/'); history.replaceState(null, '', '$back'); 1")

        fun openAndLeaveTheComposer(back: String) {
            openTheComposer()
            leaveTheComposer(back)
        }

        fun snackbar(text: String) =
            eval("var s = document.createElement('div'); s.className = 'snackbar-container'; s.textContent = '$text';" +
                " document.body.appendChild(s); 1")

        fun snackbarSays(text: String) =
            eval("document.querySelector('.snackbar-container').textContent = '$text'; 1")

        // Facebook's snackbar after Post, as read on the emulator.
        fun uploading() = snackbar("Uploading your post...")

        fun posted() = snackbarSays("Posted")

        fun assertOpenedTheProfile() {
            assertTrue("the profile never opened", waitFor("window.__went === '$PROFILE'"))
            assertEquals("the profile's page wasn't told to open the newest post", "true",
                eval("Number(sessionStorage.getItem('astryxOpenNewPost')) > 0"))
        }

        fun assertStayed() = assertEquals("undefined", eval("typeof window.__went").removeSurrounding("\""))

        // The profile the script opened [agoMs] ago, with [body]; its clicks recorded (__clicked).
        fun loadTheProfile(agoMs: Long, body: String) = load(
            PROFILE, body,
            "sessionStorage.setItem('astryxOpenNewPost', String(Date.now() - $agoMs));" +
                " window.__clicked = []; document.addEventListener('click', function (e) {" +
                " var a = e.target.closest('[data-action-id]'); if (a) window.__clicked.push(a.id); }, true);"
        )
    }

    @Test
    fun aPostFromTheMemoriesPage_opensTheProfile() {
        // Facebook's order: the snackbar on the composer, then back to Memories, then "Posted".
        val h = Harness()
        h.load("/onthisday/")
        h.openTheComposer()
        h.uploading()
        h.leaveTheComposer("/onthisday/")
        h.posted()
        h.assertOpenedTheProfile()
    }

    @Test
    fun aSnackbarJustAfterLeavingTheComposer_countsToo() {
        val h = Harness()
        h.load("/onthisday/")
        h.openAndLeaveTheComposer("/onthisday/")
        h.uploading()
        h.posted()
        h.assertOpenedTheProfile()
    }

    @Test
    fun leavingTheComposerWithoutPosting_staysPut() {
        val h = Harness()
        h.load("/onthisday/")
        h.openAndLeaveTheComposer("/onthisday/")
        // No snackbar right after; one long after (something else's) doesn't count.
        Thread.sleep(5000)
        h.snackbar("Link copied")
        Thread.sleep(1500)
        h.assertStayed()
    }

    @Test
    fun aPostFromHome_isLeftToFacebook() {
        // From Home, Facebook opens the new post itself (at the profile's address).
        val h = Harness()
        h.load("/")
        h.openAndLeaveTheComposer("/")
        h.uploading()
        h.eval("history.pushState(null, '', '/ofirco/'); 1")
        h.posted()
        Thread.sleep(1500)
        h.assertStayed()
    }

    @Test
    fun theProfileItOpened_opensItsNewestPost_byItsTime() {
        val h = Harness()
        h.loadTheProfile(2000, post("p1", "Just now") + post("p2", "1h"))
        assertTrue("the newest post's time was never tapped", h.waitFor("window.__clicked.length > 0"))
        assertEquals("\"p1\"", h.eval("window.__clicked[0]"))
        assertEquals("null", h.eval("sessionStorage.getItem('astryxOpenNewPost')"))
    }

    @Test
    fun aProfileOpenedLater_isLeftAlone() {
        val h = Harness()
        h.loadTheProfile(60000, post("p1", "Just now"))
        Thread.sleep(1500)
        assertEquals("0", h.eval("window.__clicked.length"))
    }

    private companion object {
        const val USER = "61595183402434"
        const val PROFILE = "/profile.php?id=$USER"
    }

    // A post's header on a profile: its author's name, then its time between left-to-right marks
    // and the audience's icon (an icon-font glyph), tappable.
    private fun post(id: String, time: String) =
        "<div data-tracking-duration-id='$id'><div class='native-text'>Test User</div>" +
            "<div data-action-id='$id-time' id='$id' style='height:20px'>" +
            "<div class='native-text'>\u200E$time\u200E\uDB80\uDD2D\uDB8C\uDE62</div></div></div>"
}
