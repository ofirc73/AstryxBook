package com.astryx.book
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eepiemi.materialbook.data.local.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A post's Share > Send as a message: m.facebook.com hands the post to the Messenger app with an
 * intent:// link, which the app never launches. The Messages layer opens the chat list instead
 * (with Messages in desktop mode off, after the user picks desktop mode), and the chat the user
 * opens gets the post's link in its message box (put_link_in_chat.js), for the user to send. No
 * login here: the chat list may be Facebook's login page, so the chat is a test page.
 */
@RunWith(AndroidJUnit4::class)
class SendAsMessageTest {

    private val app = AppHarness()

    @Before
    fun setUp() {
        File(app.context.filesDir, "datastore").deleteRecursively()
    }

    @After
    fun tearDown() {
        app.finish()
        runBlocking { SettingsDataStore(app.context).setMessagesDesktop(false) }
        File(app.context.filesDir, "datastore").deleteRecursively()
    }


    // A dialog of the app's is up: the activity's window has lost the focus to it.
    private fun dialogShown() = app.onActivity { !it.hasWindowFocus() }

    // The taps the test page's Send as a message got.
    private fun taps() = runCatching { app.eval("window.__taps || 0", app.mainWebView()).toInt() }.getOrDefault(-1)

    // What the app shows, for failures.
    private fun state(): String {
        val setting = runBlocking { SettingsDataStore(app.context).messagesDesktop.first() }
        val layer = app.layerWebView()?.let { app.url(it) } ?: "none"
        return "taps=${taps()}, dialog=${dialogShown()}, Messages in desktop mode=$setting, layer=$layer"
    }

    // The feed with Send as a message, which opens Messenger's share link as Facebook's does.
    private fun tapSendAsMessage() {
        app.start()
        assertTrue(app.waitFor { app.webViews().isNotEmpty() })
        app.loadPage("https://m.facebook.com/", FEED)
        // Facebook's real page, loaded at start, may push its app (fb://): that dialog goes, and a
        // tap it took instead of the page is made again.
        for (attempt in 1..2) {
            if (dialogShown()) {
                app.pressBack()
                app.waitFor { !dialogShown() }
            }
            assertTrue("Send as a message couldn't be tapped", app.tapOn(app.mainWebView(), "document.getElementById('send')"))
            if (app.waitFor(3_000) { taps() > 0 }) return
        }
        fail("the page never got the tap on Send as a message (${state()})")
    }

    // The layer opened for the post, and a chat opened there (a test page with Messenger's message
    // box, tapped as a user's tap on it would) gets the post's link in the box.
    private fun assertTheChatGetsThePost() {
        assertTrue("the Messages layer never opened for the post (${state()})", app.waitFor(20_000) {
            app.layerWebView() != null && app.layerOpen()
        })
        val layer = app.layerWebView()!!
        app.loadPage(CHAT_URL, CHAT, layer)
        assertTrue("the chat's message box couldn't be tapped", app.tapOn(layer, "document.getElementById('box')"))
        assertTrue("the chat's message box never got the post's link (${state()}; box: " +
            "${app.eval("document.getElementById('box').innerText", layer)})", app.waitFor(15_000) {
            app.eval("document.getElementById('box').innerText.indexOf('$POST') >= 0", layer) == "true"
        })
        assertTrue("the feed was left", app.eval("!!window.__testPage", app.mainWebView()) == "true")
    }

    @Test
    fun withMessagesInDesktopMode_theChatPickedGetsThePostsLink() {
        runBlocking { SettingsDataStore(app.context).setMessagesDesktop(true) }
        tapSendAsMessage()
        assertTheChatGetsThePost()
    }

    // A chat whose message box comes a moment after the tap that opened it and loses the first text
    // put in it, as Messenger's does before its editor takes the box over (2026-10-28): the link goes
    // in as the box appears, again 0.2 s on, and once only (put_link_in_chat.js).
    @Test
    fun aBoxThatComesLate_andLosesTheFirstInsert_getsTheLinkOnce() {
        runBlocking { SettingsDataStore(app.context).setMessagesDesktop(true) }
        tapSendAsMessage()
        assertTrue("the Messages layer never opened for the post (${state()})", app.waitFor(20_000) {
            app.layerWebView() != null && app.layerOpen()
        })
        val layer = app.layerWebView()!!
        app.loadPage(CHAT_URL, LATE_CHAT, layer)
        assertTrue("the chat couldn't be tapped", app.tapOn(layer, "document.getElementById('pane')"))
        val text = "document.getElementById('box') ? document.getElementById('box').innerText : ''"
        assertTrue("the late box never got the post's link (box: ${app.eval(text, layer)}, " +
            "first insert lost: ${app.eval("!!window.__lost", layer)})", app.waitFor(10_000) {
            app.eval("($text).indexOf('$POST') >= 0", layer) == "true"
        })
        assertTrue("the box never lost its first insert: the test didn't test the retry", app.eval("!!window.__lost", layer) == "true")
        Thread.sleep(1500)
        assertEquals("the link went in more than once", "1", app.eval("($text).split('$POST').length - 1", layer))
    }

    @Test
    fun withoutMessagesInDesktopMode_desktopModeIsOffered_andTheChatPickedGetsThePostsLink() {
        tapSendAsMessage()
        assertTrue("desktop mode was never offered (no dialog)", app.waitFor { dialogShown() })
        Thread.sleep(500)
        // Use desktop mode, the dialog's first button, by keyboard (the test's UiAutomation saw no
        // window for the dialog, and uiautomator can't run beside it).
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
        assertTheChatGetsThePost()
    }

    private companion object {
        const val POST = "https://www.facebook.com/groups/355/permalink/3732/"
        // As read on the emulator (2026-10-27): window.open on the item's tap.
        const val SHARE_LINK =
            "intent://share/?link=https%3A%2F%2Fwww.facebook.com%2Fgroups%2F355%2Fpermalink%2F3732%2F&vcuid=6159" +
                "&src=mtouch_diode&entry_point=feed_story_share_as_message#Intent;scheme=fb-messenger;package=com.facebook.orca;end"
        const val FEED =
            "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'></head><body>" +
                "<div id='send' role='button' aria-label='Send as a message' style='margin:40px;height:80px'" +
                " onclick=\"window.__taps = (window.__taps || 0) + 1; window.open('$SHARE_LINK', '_self')\">Send as a message</div>" +
                "<script>window.__testPage = true;</script></body></html>"
        const val CHAT_URL = "https://www.facebook.com/messages/t/4567/"
        // A chat, with Messenger's message box (its Lexical editor: role=textbox, contenteditable).
        const val CHAT =
            "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'></head><body>" +
                "<div id='box' contenteditable='true' role='textbox' aria-label='Message'" +
                " style='margin:40px;min-height:60px;border:1px solid #888'></div>" +
                "<script>window.__testPage = true;</script></body></html>"
        // A chat whose box is added 0.8 s after the chat is tapped, and empties itself the first time
        // text goes in.
        const val LATE_CHAT =
            "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'></head><body>" +
                "<div id='pane' style='margin:40px;height:200px;background:#ddd'></div>" +
                "<script>window.__testPage = true;" +
                " document.getElementById('pane').addEventListener('click', function () {" +
                " if (window.__boxComing) return; window.__boxComing = true; setTimeout(function () {" +
                " var box = document.createElement('div'); box.id = 'box'; box.contentEditable = 'true';" +
                " box.setAttribute('role', 'textbox'); box.style.minHeight = '60px'; box.style.border = '1px solid #888';" +
                " box.addEventListener('input', function () { if (!window.__lost) { window.__lost = true; box.textContent = ''; } });" +
                " document.body.appendChild(box); }, 800); });</script></body></html>"
    }
}
