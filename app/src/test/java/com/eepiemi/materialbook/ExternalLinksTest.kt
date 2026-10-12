package com.eepiemi.materialbook

import com.eepiemi.materialbook.utils.ExternalLinkAction.BLOCK
import com.eepiemi.materialbook.utils.ExternalLinkAction.CONFIRM
import com.eepiemi.materialbook.utils.ExternalLinkAction.OPEN
import com.eepiemi.materialbook.utils.externalLinkAction
import com.eepiemi.materialbook.utils.isMessengerAppLink
import com.eepiemi.materialbook.utils.linkScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalLinksTest {

    @Test
    fun webEmailAndPhoneLinks_open() {
        assertEquals(OPEN, externalLinkAction("https://example.com/a?b=1"))
        assertEquals(OPEN, externalLinkAction("HTTP://example.com"))
        assertEquals(OPEN, externalLinkAction("mailto:someone@example.com"))
        assertEquals(OPEN, externalLinkAction("tel:+15551234567"))
    }

    @Test
    fun otherAppsLinks_askFirst() {
        assertEquals(CONFIRM, externalLinkAction("market://details?id=com.example"))
        assertEquals(CONFIRM, externalLinkAction("sms:+15551234567"))
        assertEquals(CONFIRM, externalLinkAction("someapp://pay?to=me&amount=100"))
        assertEquals(CONFIRM, externalLinkAction("fb-messenger://user/123"))
    }

    @Test
    fun localOrScriptLinks_andLinksWithoutScheme_areRefused() {
        assertEquals(BLOCK, externalLinkAction("javascript:alert(1)"))
        assertEquals(BLOCK, externalLinkAction("file:///sdcard/x"))
        assertEquals(BLOCK, externalLinkAction("content://com.example.provider/x"))
        assertEquals(BLOCK, externalLinkAction("data:text/html,hi"))
        assertEquals(BLOCK, externalLinkAction("intent://x#Intent;package=com.example;end"))
        assertEquals(BLOCK, externalLinkAction("/relative/path"))
        assertEquals(BLOCK, externalLinkAction(""))
    }

    @Test
    fun messengerAppLinks_areRecognised() {
        assertTrue(isMessengerAppLink("fb-messenger://threads?vcuid=1&mb=abc"))
        assertTrue(isMessengerAppLink("FB-Messenger://user/123"))
        assertTrue(isMessengerAppLink("fb-messenger-public://user/123"))
        assertFalse(isMessengerAppLink("https://www.facebook.com/messages/"))
        assertFalse(isMessengerAppLink("market://details?id=com.facebook.orca"))
    }

    @Test
    fun linkScheme_namesTheAppKindWithoutTheLink() {
        assertEquals("fb-messenger", linkScheme("fb-messenger://threads?vcuid=1&mb=secret"))
        assertEquals("market", linkScheme(" market://details?id=x"))
        assertEquals("no scheme", linkScheme("no scheme"))
    }
}
