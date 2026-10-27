package com.eepiemi.materialbook

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eepiemi.materialbook.data.local.SettingsDataStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URLDecoder

/**
 * A post's Share > Send as a message: m.facebook.com hands the post to the Messenger app with an
 * intent:// link, which the app never launches. The post goes to the desktop site's Send in
 * Messenger dialog in the Messages layer instead (with Messages in desktop mode off, after the
 * user picks desktop mode). No login here: the layer may show Facebook's login page with the
 * dialog's address in its own, which is why addresses are checked decoded.
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

    private fun decoded(url: String) = runCatching { URLDecoder.decode(url, "UTF-8") }.getOrDefault(url)

    // A dialog of the app's is up: the activity's window has lost the focus to it.
    private fun dialogShown() = app.onActivity { !it.hasWindowFocus() }

    // The feed with Send as a message, which opens Messenger's share link as Facebook's does.
    private fun tapSendAsMessage() {
        app.start()
        assertTrue(app.waitFor { app.webViews().isNotEmpty() })
        app.loadPage("https://m.facebook.com/", FEED)
        // Facebook's real page, loaded at start, may have pushed its app (fb://): that dialog goes.
        if (dialogShown()) {
            app.pressBack()
            app.waitFor { !dialogShown() }
        }
        assertTrue("Send as a message couldn't be tapped", app.tapIn(app.mainWebView(), SEND_BOX_JS))
    }

    private fun assertTheDialogOpensForThePost() {
        assertTrue("Send in Messenger never opened for the post in the Messages layer", app.waitFor(20_000) {
            val layer = app.layerWebView()
            layer != null && app.layerOpen() && decoded(app.url(layer)).let { it.contains("/share_as_message/") && it.contains(POST) }
        })
        assertTrue("the feed was left", app.eval("!!window.__testPage", app.mainWebView()) == "true")
    }

    @Test
    fun withMessagesInDesktopMode_thePostOpensInSendInMessenger() {
        runBlocking { SettingsDataStore(app.context).setMessagesDesktop(true) }
        tapSendAsMessage()
        assertTheDialogOpensForThePost()
    }

    @Test
    fun withoutMessagesInDesktopMode_desktopModeIsOffered_andOpensSendInMessenger() {
        tapSendAsMessage()
        assertTrue("desktop mode was never offered (no dialog)", app.waitFor { dialogShown() })
        Thread.sleep(500)
        // Use desktop mode, the dialog's first button, by keyboard (the test's UiAutomation saw no
        // window for the dialog, and uiautomator can't run beside it).
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
        assertTheDialogOpensForThePost()
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
                " onclick=\"window.open('$SHARE_LINK', '_self')\">Send as a message</div>" +
                "<script>window.__testPage = true;</script></body></html>"
        const val SEND_BOX_JS =
            "(function () { var e = document.getElementById('send'); if (!e) return ''; e.scrollIntoView(); " +
                "var r = e.getBoundingClientRect(); return [r.left, r.top, r.width, r.height, visualViewport.width].join(','); })()"
    }
}
