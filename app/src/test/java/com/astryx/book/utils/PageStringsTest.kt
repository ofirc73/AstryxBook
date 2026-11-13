package com.astryx.book.utils

import com.eepiemi.materialbook.R
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * pageStrings: the app's words for the page scripts, as one script the bundle runs after.
 * Robolectric for org.json (Android's own copy; the plain JVM stub has no implementation).
 */
@RunWith(RobolectricTestRunner::class)
class PageStringsTest {

    private fun words(string: (Int) -> String): JSONObject {
        val script = pageStrings(string)
        assertTrue(script, script.startsWith("window.__astryxStrings = {") && script.endsWith("};\n"))
        return JSONObject(script.removePrefix("window.__astryxStrings = ").removeSuffix(";\n"))
    }

    @Test
    fun theScriptsWords_areTheAppsStrings() {
        val words = words { "word-$it" }
        assertEquals("word-${R.string.materialbook_settings}", words.getString("settings"))
        assertEquals("word-${R.string.share_via}", words.getString("shareVia"))
        assertEquals("word-${R.string.ad_removed_title}", words.getString("adRemoved"))
        assertEquals("word-${R.string.ad_removed_text}", words.getString("adRemovedText"))
    }

    // A word comes through the script whole: quotes, a line break, right-to-left letters.
    @Test
    fun aWordWithQuotes_staysOneWord() {
        val word = "l'app \"Astryx\"\nשיתוף</script>"
        assertEquals(word, words { word }.getString("shareVia"))
    }
}
