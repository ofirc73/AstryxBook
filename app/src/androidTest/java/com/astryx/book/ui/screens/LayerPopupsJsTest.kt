package com.astryx.book.ui.screens
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import com.astryx.book.assertTrue
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * layer_popups.js keeps Facebook's desktop popups usable in the Messages layer: a search box a popup
 * focuses by itself is let go (the keyboard it brought up closed the popup: a reaction's "+" showed
 * nothing), one the user tapped keeps its focus; a popup Facebook placed off the screen is moved onto
 * it; Back closes an open popup by Escape, and passes over one that stayed. The popups are shaped as
 * read on the emulator (2026-11-24): a [role=dialog] in a host placed by an inline absolute top,
 * the dialog taller than the host's max-height. A test WebView isn't focused, so focus is sent as
 * its event and blur() recorded.
 */
@RunWith(AndroidJUnit4::class)
class LayerPopupsJsTest {

    private val page = PageFixture(width = 1080, height = 2000)

    private fun open(html: String) {
        page.load("https://www.facebook.com/messages/e2ee/t/1/", html)
        page.run(R.raw.layer_popups)
    }

    private fun eval(js: String) = page.eval(js)

    // The picker's box focused, after a pointerdown on [tapped] (none: by the popup itself); whether it was let go.
    private fun focusBox(tapped: String? = null): String {
        eval("var b = document.getElementById('search'); window.__blurred = false; b.blur = function () { window.__blurred = true; };" +
            (tapped?.let { " document.getElementById('$it').dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));" } ?: "") +
            " b.dispatchEvent(new FocusEvent('focusin', { bubbles: true })); 1")
        return eval("window.__blurred")
    }

    @Test
    fun aSearchBoxThePopupFocuses_isLetGo() {
        open(PICKER)
        assertEquals("the box the popup focused kept its focus (the keyboard then closes the popup)", "true", focusBox())
    }

    @Test
    fun aSearchBoxTheUserTapped_keepsItsFocus() {
        open(PICKER)
        assertEquals("the box the user tapped was let go", "false", focusBox(tapped = "search"))
        // A tap elsewhere (the reaction's "+") isn't a tap on the box.
        assertEquals("true", focusBox(tapped = "plus"))
    }

    @Test
    fun aBoxOutsideAPopup_keepsItsFocus() {
        open(PICKER)
        eval("var m = document.getElementById('message'); window.__blurred = false; m.blur = function () { window.__blurred = true; };" +
            " m.dispatchEvent(new FocusEvent('focusin', { bubbles: true })); 1")
        assertEquals("the message box was let go", "false", eval("window.__blurred"))
    }

    @Test
    fun aPopupPlacedBelowTheScreen_isMovedOntoIt() {
        open("<div id='message-row'></div>")
        // As Facebook placed the message box's emoji picker: its top 28 px above the screen's bottom.
        eval("var h = document.createElement('div'); h.id = 'host';" +
            " h.style.cssText = 'position: absolute; left: 8px; top: ' + (innerHeight - 28) + 'px; max-height: 283px; width: 300px';" +
            " h.innerHTML = \"<div role='dialog' id='picker' style='height: 313px'>emoji</div>\"; document.body.appendChild(h); 1")
        assertTrue({ "the picker wasn't moved onto the screen (${eval("JSON.stringify(document.getElementById('picker').getBoundingClientRect())")})" },
            page.waitFor {
                eval("(function (r) { return r.top >= 0 && r.bottom <= innerHeight - 7; })(document.getElementById('picker').getBoundingClientRect())") == "true"
            })
    }

    @Test
    fun back_closesAnOpenPopupByEscape_andPassesOverOneThatStays() {
        open(PICKER + "<div role='menu' id='stubborn' style='height: 40px'>reactions</div>")
        eval("document.getElementById('picker').addEventListener('keydown', function (e) { if (e.key === 'Escape') this.remove(); }); 1")
        // The last one first: the menu, which ignores Escape.
        assertEquals("true", eval("window.__astryxClosePopup()"))
        assertEquals("the menu was there", "true", eval("!!document.getElementById('stubborn')"))
        // Passed over now: the picker closes.
        assertEquals("true", eval("window.__astryxClosePopup()"))
        assertEquals("the picker wasn't closed by Escape", "false", eval("!!document.getElementById('picker')"))
        // Nothing else open: Back goes on.
        assertEquals("false", eval("window.__astryxClosePopup()"))
    }

    private companion object {
        // A chat's message box, the reaction's "+", and the reactions picker with its search box.
        const val PICKER = "<div role='textbox' contenteditable='true' id='message'>Aa</div>" +
            "<div role='button' id='plus'>+</div>" +
            "<div style='position: absolute; top: 120px; left: 8px'><div role='dialog' id='picker' style='height: 300px'>" +
            "<input id='search' aria-label='Search emoji'></div></div>"
    }
}
