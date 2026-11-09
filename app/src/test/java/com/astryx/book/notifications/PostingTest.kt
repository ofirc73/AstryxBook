package com.astryx.book.notifications
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// Robolectric: Posting starts and stops PostingService.
@RunWith(RobolectricTestRunner::class)
class PostingTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun aStoryAndAPostTogether_showTheLatest_thenTheOneStillPosting() {
        Posting.started(context, Posting.Kind.STORY)
        Posting.started(context, Posting.Kind.POST)
        assertEquals(Posting.Kind.POST, Posting.showing())
        Posting.ended(context, Posting.Kind.POST, Posting.Outcome.POSTED, notify = false)
        assertEquals(Posting.Kind.STORY, Posting.showing())
        Posting.ended(context, Posting.Kind.STORY, Posting.Outcome.POSTED, notify = false)
        assertNull(Posting.showing())
    }

    // Notification channels came in Android 8 (Robolectric runs these at its lowest SDK otherwise).
    @Test
    @Config(sdk = [34])
    fun storiesAndPosts_notifyOnAChannelWithAnIconAndNoSound_replacingTheSilentOne() {
        // One channel for both kinds (Posting.builder): a story's posting shows the icon too.
        val notifications = NotificationManagerCompat.from(context)
        notifications.createNotificationChannel(
            NotificationChannelCompat.Builder("story_posting", NotificationManagerCompat.IMPORTANCE_LOW).setName("old").build()
        )
        Posting.createChannel(notifications, "Posting")
        val channel = notifications.getNotificationChannelCompat("posting")
        assertEquals(NotificationManagerCompat.IMPORTANCE_DEFAULT, channel?.importance)
        assertNull("it makes a sound", channel?.sound)
        assertFalse("it vibrates", channel!!.shouldVibrate())
        assertNull("the silent channel is still there", notifications.getNotificationChannelCompat("story_posting"))
    }

    @Test
    fun whatsPosting_andWhatWasPosted_areThereForTheScreen() {
        // PostingBar and PostedStoryEffects read them.
        Posting.started(context, Posting.Kind.STORY)
        assertEquals(Posting.Kind.STORY, Posting.current)
        Posting.ended(context, Posting.Kind.STORY, Posting.Outcome.POSTED, notify = false)
        assertNull(Posting.current)
        assertEquals(Posting.Kind.STORY, Posting.lastPosted?.kind)
        val posted = Posting.lastPosted
        // Interrupted isn't posted.
        Posting.started(context, Posting.Kind.POST)
        Posting.ended(context, Posting.Kind.POST, Posting.Outcome.INTERRUPTED, notify = false)
        assertEquals(posted, Posting.lastPosted)
    }

    @Test
    fun startedAgain_isStillOnce() {
        Posting.started(context, Posting.Kind.POST)
        Posting.started(context, Posting.Kind.POST)
        Posting.ended(context, Posting.Kind.POST, Posting.Outcome.FAILED, notify = false)
        assertNull(Posting.showing())
    }
}
