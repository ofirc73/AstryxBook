package com.astryx.book

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.astryx.book.utils.ShareTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

// Robolectric for Intent.
@RunWith(RobolectricTestRunner::class)
class IncomingIntentsTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val link = "https://www.facebook.com/groups/123/"

    private fun view() = Intent(Intent.ACTION_VIEW, Uri.parse(link))

    private fun share() = Intent(Intent.ACTION_SEND).setType("image/png")
        .setClassName("com.astryx.book", "com.eepiemi.materialbook.ShareToFeed")

    @Test
    fun aNewLinkOrShare_isWhatTheAppStartsWith() {
        startRequest(context, view(), restored = false).let {
            assertEquals(link, it.linkUrl)
            assertNull(it.mediaTarget)
        }
        startRequest(context, share(), restored = false).let {
            assertEquals(ShareTarget.FEED, it.mediaTarget)
            assertNull(it.linkUrl)
        }
        startRequest(context, Intent(Intent.ACTION_MAIN), restored = false).let {
            assertNull(it.mediaTarget)
            assertNull(it.linkUrl)
        }
    }

    @Test
    fun anIntentHandedOverAgainFromRecents_asksForNothing() {
        assertTrue(isReplayedLaunch(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
        assertFalse(isReplayedLaunch(Intent.FLAG_ACTIVITY_NEW_TASK))
        listOf(view(), share()).forEach { intent ->
            val request = startRequest(context, intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY), restored = false)
            assertNull(intent.toString(), request.mediaTarget)
            assertNull(intent.toString(), request.linkUrl)
        }
    }

    @Test
    fun anActivityCreatedAgain_keepsItsLink_butDoesntTakeTheShareAgain() {
        assertEquals(link, startRequest(context, view(), restored = true).linkUrl)
        startRequest(context, share(), restored = true).let {
            assertNull(it.mediaTarget)
            // Nor read as a text share, which would say it holds no link.
            assertNull(it.linkUrl)
        }
    }
}
