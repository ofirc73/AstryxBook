package com.astryx.book.ui.screens
import androidx.annotation.RawRes
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import com.eepiemi.materialbook.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Material You and AMOLED black (`material_you.js`, `amoled_black.js`) recolour what Facebook adds
 * or restyles after the page loaded: they look only at what changed, not at the whole page on every
 * change (which took about 300 ms of CPU a second on a changing feed).
 */
@RunWith(AndroidJUnit4::class)
class PageColorsJsTest {

    private val page = PageFixture()

    // The page with the script running (after scripts.js's helpers, as in the app's bundle).
    private fun load(@RawRes script: Int) {
        page.load("https://m.facebook.com/", "<html><head><meta name='theme-color' content='#ffffff'></head><body></body></html>")
        page.runBackHandler()
        page.eval(
            """
            window.MaterialYouBridge = {
              getMaterialYouPrimaryRgbString: function () { return 'rgb(103, 80, 164)'; },
              getMaterialYouOnPrimaryRgbString: function () { return 'rgb(255, 255, 255)'; },
              getMaterialYouPrimaryRgb: function () { return '{"r":103,"g":80,"b":164}'; },
              getMaterialYouOnPrimaryRgb: function () { return '{"r":255,"g":255,"b":255}'; }
            }; 1
            """.trimIndent()
        )
        page.run(script)
    }

    // Whether the element with [id] gets a style holding [text].
    private fun styleBecomes(id: String, text: String) = page.waitFor(2_000) {
        page.eval("(document.getElementById('$id').getAttribute('style') || '').indexOf('$text') >= 0") == "true"
    }

    // A post as Facebook adds it: a box with no style of its own, the styled elements inside it.
    private fun addPost(inside: String) = page.eval(
        "var d = document.createElement('div'); d.innerHTML = \"$inside\"; document.body.appendChild(d); 1"
    )

    @Test
    fun materialYou_recoloursAPostAddedLater() {
        load(R.raw.material_you)
        addPost("<div id='like' style='color:#0866ff;'>Like</div>")

        assertTrue(styleBecomes("like", "103, 80, 164"))
    }

    @Test
    fun materialYou_recoloursAnElementRestyledLater() {
        load(R.raw.material_you)
        addPost("<div id='button' style='height:20px;'>Follow</div>")
        page.eval("document.getElementById('button').setAttribute('style', 'height:20px;background-color:#1877f2;'); 1")

        assertTrue(styleBecomes("button", "103, 80, 164"))
    }

    @Test
    fun amoledBlack_blackensAPostAddedLater() {
        load(R.raw.amoled_black)
        addPost("<div id='card' style='background-color:#242526;'>A post</div>")

        assertTrue(styleBecomes("card", "#000000"))
    }

    @Test
    fun amoledBlack_blackensAnElementRestyledLater() {
        load(R.raw.amoled_black)
        addPost("<div id='sheet' style='height:20px;'>A sheet</div>")
        // As Facebook writes it: the script reads the style's text, which `style.backgroundColor`
        // would turn into rgb(36, 37, 38).
        page.eval("document.getElementById('sheet').setAttribute('style', 'height:20px;background-color:#242526;'); 1")

        assertTrue(styleBecomes("sheet", "#000000"))
    }
}
