package com.eepiemi.materialbook.ui.screens

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * scripts.js presses "Not now" on Facebook's "Facebook is better on the app" sheet, which comes
 * up over the feed when the app opens. The fixture is that sheet as read from the phone
 * (2026-10-17): a dialog screen with a bottom sheet holding the Facebook logo, a title, a line of
 * text and the two buttons. A sheet that comes up right after a tap is the user's own and stays.
 */
@RunWith(AndroidJUnit4::class)
class AppPromptJsTest {

    private class Harness {
        lateinit var webView: WebView
            private set

        fun load() {
            val latch = CountDownLatch(1)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    layout(0, 0, 1080, 2000)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            latch.countDown()
                        }
                    }
                    loadDataWithBaseURL(
                        "https://m.facebook.com/",
                        "<html><head><meta name='viewport' content='width=device-width'></head>" +
                            "<body style='margin:0'><div id='feed'>feed</div></body></html>",
                        "text/html", "UTF-8", null
                    )
                }
            }
            assertTrue("page never finished loading", latch.await(10, TimeUnit.SECONDS))
            val scripts = InstrumentationRegistry.getInstrumentation().targetContext.resources
                .openRawResource(R.raw.scripts).bufferedReader().use { it.readText() }
            eval(scripts)
        }

        fun eval(js: String): String {
            var result: String? = null
            val latch = CountDownLatch(1)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                webView.evaluateJavascript(js) { value ->
                    result = value
                    latch.countDown()
                }
            }
            assertTrue("evaluateJavascript never returned", latch.await(10, TimeUnit.SECONDS))
            return result ?: "null"
        }

        fun waitFor(js: String, ms: Long = 3000): Boolean {
            val until = System.currentTimeMillis() + ms
            while (System.currentTimeMillis() < until) {
                if (eval(js) == "true") return true
                Thread.sleep(100)
            }
            return false
        }

        // Facebook adds the sheet's screen to the page; its buttons record a press.
        fun addSheet(extra: String = "", sheet: String = SHEET) {
            eval(
                "window.__pressed = null; var d = document.createElement('div'); d.innerHTML = \"$sheet\".replace('EXTRA', \"$extra\");" +
                    " document.body.appendChild(d.firstChild);" +
                    " document.querySelectorAll('[role=button]').forEach(function (b) {" +
                    " b.addEventListener('click', function () { window.__pressed = b.getAttribute('aria-label'); }); }); 1"
            )
        }
    }

    @Test
    fun theAppSheet_isPressedAwayWithNotNow() {
        val h = Harness()
        h.load()
        h.addSheet()
        assertTrue(h.waitFor("window.__pressed === 'Not now'"))
    }

    @Test
    fun theEnglishSheet_isPressedAway_evenRightAfterATap() {
        // Its words name it for sure.
        val h = Harness()
        h.load()
        h.eval("document.getElementById('feed').dispatchEvent(new PointerEvent('pointerdown', {bubbles: true})); 1")
        h.addSheet()
        assertTrue(h.waitFor("window.__pressed === 'Not now'"))
    }

    @Test
    fun theHebrewSheet_isPressedAway_evenRightAfterATap() {
        // Known by its "Not now" button; its title isn't (a stand-in here).
        val h = Harness()
        h.load()
        h.eval("document.getElementById('feed').dispatchEvent(new PointerEvent('pointerdown', {bubbles: true})); 1")
        h.addSheet(sheet = sheet("\u05E4\u05D9\u05D9\u05E1\u05D1\u05D5\u05E7 \u05D8\u05D5\u05D1 \u05D9\u05D5\u05EA\u05E8 \u05D1\u05D0\u05E4\u05DC\u05D9\u05E7\u05E6\u05D9\u05D4", "\u05E7\u05D1\u05DC \u05D0\u05EA \u05D4\u05D0\u05E4\u05DC\u05D9\u05E7\u05E6\u05D9\u05D4", HE_NOT_NOW))
        assertTrue(h.waitFor("window.__pressed === '$HE_NOT_NOW'"))
    }

    @Test
    fun aSheetInAnotherLanguage_isPressedAwayWithItsSecondButton() {
        val h = Harness()
        h.load()
        h.addSheet(sheet = sheet("Facebook ist in der App besser", "App holen", "Jetzt nicht"))
        assertTrue(h.waitFor("window.__pressed === 'Jetzt nicht'"))
    }

    @Test
    fun anUnknownSheetRightAfterATap_isLeftToTheUser() {
        // A confirmation the user opened, shaped like the app sheet.
        val h = Harness()
        h.load()
        h.eval("document.getElementById('feed').dispatchEvent(new PointerEvent('pointerdown', {bubbles: true})); 1")
        h.addSheet(sheet = sheet("Delete post?", "Delete", "Cancel"))
        Thread.sleep(800)
        assertEquals("null", h.eval("window.__pressed"))
    }

    @Test
    fun aSheetToTypeIn_isLeftAlone() {
        val h = Harness()
        h.load()
        h.addSheet("<textarea></textarea>")
        Thread.sleep(800)
        assertEquals("null", h.eval("window.__pressed"))
    }

    @Test
    fun addStoryInStoryViewer_opensTheInAppCreatorAndDoesNotOpenFacebooksAppGate() {
        val h = Harness()
        h.load()
        h.eval(
            "window.__pageCalls = []; window.__facebookClicks = 0;" +
                "window.AstryxPage = { postMessage: function (message) { window.__pageCalls.push(JSON.parse(message)); } };" +
                "history.replaceState({}, '', '/stories/123/story-id');" +
                "var button = document.createElement('div'); button.id = 'add-story';" +
                "button.setAttribute('role', 'button'); button.setAttribute('data-action-id', '32732');" +
                "button.innerHTML = '<div class=\"fl ac\"><span>' + String.fromCodePoint(0xF31CC) + '</span></div>' +" +
                "'<span>Add story</span>';" +
                "button.addEventListener('click', function () { window.__facebookClicks++; });" +
                "document.body.appendChild(button); button.querySelector('span').click(); 1"
        )

        assertEquals(
            "layerUrl",
            h.eval("window.__pageCalls[0].call").removeSurrounding("\"")
        )
        assertEquals(
            "https://www.facebook.com/stories/create",
            h.eval("window.__pageCalls[0].url").removeSurrounding("\"")
        )
        assertEquals("0", h.eval("window.__facebookClicks"))
    }

    @Test
    fun createStoryOnMainFeed_opensTheInAppCreatorAndAvoidsFacebooksOpenAppSheet() {
        val h = Harness()
        h.load()
        h.eval(
            "window.__pageCalls = []; window.__facebookClicks = 0;" +
                "window.AstryxPage = { postMessage: function (message) { window.__pageCalls.push(JSON.parse(message)); } };" +
                "history.replaceState({}, '', '/');" +
                "var button = document.createElement('div'); button.id = 'create-story-card';" +
                "button.setAttribute('role', 'button'); button.setAttribute('data-action-id', '32526');" +
                "button.innerHTML = '<img id=\"cover\"><div role=\"button\" aria-label=\"Create story\"><span>Create story</span></div>';" +
                "button.addEventListener('click', function () { window.__facebookClicks++; });" +
                "document.body.appendChild(button); document.getElementById('cover').click(); 1"
        )

        assertEquals("layerUrl", h.eval("window.__pageCalls[0].call").removeSurrounding("\""))
        assertEquals(
            "https://www.facebook.com/stories/create",
            h.eval("window.__pageCalls[0].url").removeSurrounding("\"")
        )
        assertEquals("0", h.eval("window.__facebookClicks"))
    }

    @Test
    fun createStoryInAnyLanguage_isTheTraysFirstCard() {
        // Facebook in Hebrew: the card's label isn't "Create story"; it's the stories tray's first
        // card (story-sized buttons side by side). A friend's story card is left to Facebook.
        val h = Harness()
        h.load()
        h.eval(
            "window.__pageCalls = []; window.__facebookClicks = 0;" +
                "window.AstryxPage = { postMessage: function (message) { window.__pageCalls.push(JSON.parse(message)); } };" +
                "history.replaceState({}, '', '/');" +
                "var tray = document.createElement('div'); tray.style.cssText = 'display:flex;direction:rtl';" +
                "['create', 'mine', 'friend'].forEach(function (id) {" +
                "  var c = document.createElement('div'); c.id = id; c.setAttribute('role', 'button');" +
                "  c.style.cssText = 'width:114px;height:202px;flex:none';" +
                "  c.innerHTML = '<img style=\"width:100px;height:100px\"><div role=\"button\" aria-label=\"' + id + '\"><span>' + id + '</span></div>';" +
                "  c.addEventListener('click', function () { window.__facebookClicks++; });" +
                "  tray.appendChild(c); });" +
                "document.body.appendChild(tray); 1"
        )
        h.eval("document.querySelector('#friend span').click(); 1")
        assertEquals("0", h.eval("window.__pageCalls.length"))
        assertEquals("1", h.eval("window.__facebookClicks"))

        h.eval("document.querySelector('#create span').click(); 1")
        assertEquals("1", h.eval("window.__pageCalls.length"))
        assertEquals("layerUrl", h.eval("window.__pageCalls[0].call").removeSurrounding("\""))
        assertEquals("1", h.eval("window.__facebookClicks"))
    }

    @Test
    fun addStoryGlyphOutsideAStoryViewer_isLeftToFacebook() {
        val h = Harness()
        h.load()
        h.eval(
            "window.__pageCalls = []; window.AstryxPage = { postMessage: function (message) { window.__pageCalls.push(message); } };" +
                "var button = document.createElement('div'); button.setAttribute('role', 'button');" +
                "button.setAttribute('data-action-id', '32732');" +
                "button.innerHTML = '<div class=\"fl ac\"><span>' + String.fromCodePoint(0xF31CC) + '</span></div>' +" +
                "'<span>Add story</span>'; document.body.appendChild(button); button.click(); 1"
        )
        assertEquals("0", h.eval("window.__pageCalls.length"))
    }

    private companion object {
        fun sheet(title: String, first: String, second: String) =
            "<div class='m bg-s1 dark-mode dialog-screen' data-mcomponent='MScreen' style='position:fixed;top:0;left:0;right:0;bottom:0'>" +
                "<div class='m fixed-container bottom' data-mcomponent='MContainer' data-actual-height='339' style='position:fixed;bottom:0;left:0;right:0'>" +
                "<div data-mcomponent='ServerImageArea'><img style='width:54px;height:54px'></div>" +
                "<h1><span>$title</span></h1>" +
                "<div><span>Get the latest updates, newest features and the fastest experience.</span></div>EXTRA" +
                "<div role='button' aria-label='$first' data-action-id='32755'><span>$first</span></div>" +
                "<div role='button' aria-label='$second' data-action-id='32753'><span>$second</span></div>" +
                "</div></div>"

        const val HE_NOT_NOW = "\u05DC\u05D0 \u05E2\u05DB\u05E9\u05D9\u05D5"

        // Facebook breaks the title over two lines ("...better on the" / "app").
        val SHEET = sheet("Facebook is better on the</span> <span>app", "Get app", "Not now")
    }
}
