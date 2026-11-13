package com.astryx.book.ui.screens

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * scripts.js's "Share via…" row in Facebook's share sheet: added under Copy link, named in the
 * app's language (pageStrings), where it used to follow the page's, in English or Hebrew only.
 * The sheet's Copy link row as m.facebook.com draws it: in the sheet (role=presentation), a button
 * holding its icon (an icon-font glyph) and its label.
 */
@RunWith(AndroidJUnit4::class)
class ShareViaJsTest {

    private class Harness(words: String?) {
        private val page = PageFixture(width = 1080, height = 2000)

        init {
            page.load("https://m.facebook.com/", "<html><body><div id='sheet' role='presentation'></div></body></html>")
            page.stubPageCalls() // the row is added only where the app's listener is
            words?.let { page.eval("window.__astryxStrings = $it; 1") }
            page.run(R.raw.scripts)
            // The sheet opens: its Copy link row.
            page.eval(
                "var b = document.createElement('div'); b.setAttribute('role', 'button'); b.setAttribute('aria-label', 'Copy link');" +
                    " var icon = document.createElement('span'); icon.textContent = String.fromCodePoint(0xF310F);" +
                    " var label = document.createElement('span'); label.textContent = 'Copy link';" +
                    " b.appendChild(icon); b.appendChild(label);" +
                    " var row = document.createElement('div'); row.appendChild(b); document.getElementById('sheet').appendChild(row); 1"
            )
        }

        /** The row's button label and its text, once it's there. */
        fun row(): String {
            assertTrue("no Share via row under Copy link", page.waitFor { page.eval("!!document.getElementById('astryx-share-via')") == "true" })
            return page.eval(
                "(function (r) { return r.querySelector('[role=button]').getAttribute('aria-label') + '|' + r.textContent.trim(); })" +
                    "(document.getElementById('astryx-share-via'))"
            ).removeSurrounding("\"")
        }
    }

    @Test
    fun theRow_isNamedInTheAppsWords() {
        assertEquals("Partager via…|Partager via…", Harness("{ shareVia: 'Partager via…' }").row())
    }

    @Test
    fun withoutTheAppsWords_theRowIsInEnglish() {
        assertEquals("Share via…|Share via…", Harness(null).row())
    }
}
