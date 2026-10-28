package com.astryx.book.utils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppShortcutsTest {

    @Test
    fun everyShortcutOpensAFacebookPage() {
        APP_SHORTCUTS.forEach { assertTrue(it.url, isFacebookWebUrl(it.url)) }
    }

    @Test
    fun idsAreUnique() {
        assertEquals(APP_SHORTCUTS.size, APP_SHORTCUTS.map { it.id }.toSet().size)
    }

    @Test
    fun messagesShortcut_isAMessagesLink() {
        // So it opens in the Messages layer like the Messages tab (MaterialbookWebView).
        assertTrue(isMessagesLink(APP_SHORTCUTS.single { it.id == "messages" }.url))
    }
}
