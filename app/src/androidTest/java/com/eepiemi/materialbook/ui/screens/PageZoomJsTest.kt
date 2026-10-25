package com.eepiemi.materialbook.ui.screens

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eepiemi.materialbook.PageFixture
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// evaluateJavascript returns strings JSON-quoted.
private fun String.unquoted() = removeSurrounding("\"")

/**
 * Tests for page_zoom.js against a real WebView, same harness approach as ReelAdsJsTest. The app
 * installs the script at document start; here it runs after the fixture has loaded, which it
 * also handles (it zooms the viewport <meta>s already there). A test WebView isn't in a window,
 * so the window's size (outerWidth/outerHeight, both "replaceable") is set by each test.
 */
@RunWith(AndroidJUnit4::class)
class PageZoomJsTest {

    private class Harness {
        private val page = PageFixture()

        fun load(zoom: Double, outerWidth: Int = 412, outerHeight: Int = 842) {
            page.load(
                "https://m.facebook.com/",
                "<html><head><meta name='viewport' id='fb' content='width=device-width, " +
                    "initial-scale=1, interactive-widget=resizes-content'></head>" +
                    "<body></body></html>"
            )
            eval(
                "window.outerWidth = $outerWidth; window.outerHeight = $outerHeight;" +
                    "Object.defineProperty(window, 'screen', { value: { width: 412, height: 915 } });" +
                    "window.__astryxPageZoom = $zoom;"
            )
            page.run(R.raw.page_zoom)
        }

        fun eval(js: String) = page.eval(js)

        fun viewport(id: String = "fb"): String =
            eval("document.getElementById('$id').getAttribute('content')").unquoted()

        // Lets the page's MutationObserver run.
        fun settle() {
            eval("new Promise(r => setTimeout(() => r(1), 50))")
            Thread.sleep(100)
        }
    }

    @Test
    fun zoomIn_narrowsTheLayout_andShowsItAtTheZoom() {
        val h = Harness()
        h.load(1.3)
        // 412 / 1.3 = 316.9: the window's width in the zoomed CSS px.
        assertEquals("width=317, initial-scale=1.3, interactive-widget=resizes-content", h.viewport())
        assertEquals("317", h.eval("window.__astryxPageZoomWidth()"))
        assertEquals("1.3", h.eval("window.__astryxPageZoomScale()"))
    }

    @Test
    fun zoomOut_widensTheLayout() {
        val h = Harness()
        h.load(0.85)
        assertEquals("width=485, initial-scale=0.85, interactive-widget=resizes-content", h.viewport())
    }

    @Test
    fun aViewportThePageChangesLater_isZoomedAgain() {
        val h = Harness()
        h.load(1.5)
        h.eval(
            "document.getElementById('fb').setAttribute('content', " +
                "'width=device-width, initial-scale=1.0, maximum-scale=10.0, user-scalable=yes')"
        )
        h.settle()
        assertEquals("width=275, initial-scale=1.5, maximum-scale=10.0, user-scalable=yes", h.viewport())
    }

    @Test
    fun aViewportAddedLater_isZoomed_andAZoomLockAllowsTheZoom() {
        val h = Harness()
        h.load(1.3)
        // pinch_to_zoom.js's lock: maximum-scale 1 would keep the page at 100%.
        h.eval(
            "var m = document.createElement('meta'); m.name = 'viewport'; m.id = 'lock';" +
                "m.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';" +
                "document.head.appendChild(m);"
        )
        h.settle()
        assertEquals("width=317, initial-scale=1.3, maximum-scale=1.3, user-scalable=no", h.viewport("lock"))
    }

    @Test
    fun facebooksViewportCookie_reportsTheZoomedViewport_andOtherCookiesAreLeftAlone() {
        val h = Harness()
        h.load(1.3)
        h.eval("document.cookie = 'vpd=v1%3B842x412x2.625; path=/; secure'; document.cookie = 'astryxtest=842x412; path=/; secure';")
        val cookies = h.eval("document.cookie").unquoted()
        assertTrue(cookies, cookies.contains("vpd=v1%3B648x317x2.625"))
        assertTrue(cookies, cookies.contains("astryxtest=842x412"))
    }

    @Test
    fun smallWindow_keepsThePagesOwnViewport_andItsCookie() {
        // A PiP window: about 130 CSS px wide.
        val h = Harness()
        h.load(1.3, outerWidth = 130, outerHeight = 227)
        assertEquals("width=device-width, initial-scale=1, interactive-widget=resizes-content", h.viewport())
        assertEquals("1", h.eval("window.__astryxPageZoomScale()"))
        h.eval("document.cookie = 'vpd=v1%3B227x130x2.625; path=/; secure';")
        assertTrue(h.eval("document.cookie").contains("vpd=v1%3B227x130x2.625"))
    }

    @Test
    fun leavingASmallWindow_zoomsAgain() {
        val h = Harness()
        h.load(1.3, outerWidth = 130, outerHeight = 227)
        h.eval("window.outerWidth = 412; window.outerHeight = 842; window.dispatchEvent(new Event('resize'));")
        assertEquals("width=317, initial-scale=1.3, interactive-widget=resizes-content", h.viewport())
    }

    @Test
    fun noZoom_leavesThePageAlone() {
        val h = Harness()
        h.load(1.0)
        assertEquals("width=device-width, initial-scale=1, interactive-widget=resizes-content", h.viewport())
        assertEquals("undefined", h.eval("typeof window.__astryxPageZoomWidth").unquoted())
    }
}
