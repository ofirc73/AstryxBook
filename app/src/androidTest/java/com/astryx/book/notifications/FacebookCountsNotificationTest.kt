package com.astryx.book.notifications
import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Android notifications FacebookCountsWorker posts for the counts a check found (the
 * fetching and parsing are covered by FacebookCountsTest): new unread posts one, fewer updates
 * it, none clears it.
 */
@RunWith(AndroidJUnit4::class)
class FacebookCountsNotificationTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)

    private fun counts(notifications: Int = 0, messages: Int = 0, friends: Int = 0) = mapOf(
        FacebookCounter.NOTIFICATIONS to UnreadCount(notifications),
        FacebookCounter.MESSAGES to UnreadCount(messages),
        FacebookCounter.FRIEND_REQUESTS to UnreadCount(friends),
    )

    private fun shown(counter: FacebookCounter): Notification? =
        manager.activeNotifications.firstOrNull { it.id == counter.notificationId }?.notification

    private fun title(counter: FacebookCounter) =
        shown(counter)?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    // NotificationManager.notify and cancel are asynchronous: activeNotifications can lag behind
    // them for a moment (seen on a fresh CI emulator). The count shown once it has settled on
    // [expected] (null: no notification), or after 3 s.
    private fun number(counter: FacebookCounter, expected: Int?): Int? {
        val until = SystemClock.elapsedRealtime() + 3000
        while (shown(counter)?.number != expected && SystemClock.elapsedRealtime() < until) {
            SystemClock.sleep(50)
        }
        return shown(counter)?.number
    }

    @Before
    fun setUp() {
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        FacebookCountsWorker.schedule(context, enabled = false)
        FacebookCounter.entries.forEach { number(it, null) }
    }

    @After
    fun tearDown() = FacebookCountsWorker.schedule(context, enabled = false)

    @Test
    fun newUnread_isNotified_withItsCount() {
        FacebookCountsWorker.applyCounts(context, counts(notifications = 3, friends = 1), notify = true)
        assertEquals(3, number(FacebookCounter.NOTIFICATIONS, 3))
        assertEquals(1, number(FacebookCounter.FRIEND_REQUESTS, 1))
        assertNull(number(FacebookCounter.MESSAGES, null))
    }

    @Test
    fun fewerUnread_updatesIt_noneClearsIt() {
        FacebookCountsWorker.applyCounts(context, counts(notifications = 3), notify = true)
        assertEquals(3, number(FacebookCounter.NOTIFICATIONS, 3))
        val first = title(FacebookCounter.NOTIFICATIONS)
        FacebookCountsWorker.applyCounts(context, counts(notifications = 1), notify = true)
        assertEquals(1, number(FacebookCounter.NOTIFICATIONS, 1))
        assertNotEquals(first, title(FacebookCounter.NOTIFICATIONS))
        FacebookCountsWorker.applyCounts(context, counts(notifications = 0), notify = true)
        assertNull(number(FacebookCounter.NOTIFICATIONS, null))
    }

    @Test
    fun countsRecordedWithoutNotifying_areTheBaseline() {
        // What the user left the app with: not notified, and the same count later isn't either.
        FacebookCountsWorker.applyCounts(context, counts(messages = 2), notify = false)
        assertNull(number(FacebookCounter.MESSAGES, null))
        FacebookCountsWorker.applyCounts(context, counts(messages = 2), notify = true)
        assertNull(number(FacebookCounter.MESSAGES, null))
        FacebookCountsWorker.applyCounts(context, counts(messages = 4), notify = true)
        assertEquals(4, number(FacebookCounter.MESSAGES, 4))
    }

    @Test
    fun notificationTexts_showWhenExpanded_butNotOnTheLockScreen() {
        val texts = listOf("Dana commented on your post", "Noa reacted to your photo")
        FacebookCountsWorker.applyCounts(context, counts(notifications = 2), notify = true, notificationTexts = texts)
        assertEquals(2, number(FacebookCounter.NOTIFICATIONS, 2))
        val notification = shown(FacebookCounter.NOTIFICATIONS)!!
        assertEquals(texts, notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map { it.toString() })
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        // The lock screen's version: the count, no text.
        assertNull(notification.publicVersion?.extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES))
        assertNull(notification.publicVersion?.extras?.getCharSequence(Notification.EXTRA_TEXT))
        // Messages and friend requests never carry them.
        FacebookCountsWorker.applyCounts(context, counts(notifications = 2, messages = 1), notify = true, notificationTexts = texts)
        assertEquals(1, number(FacebookCounter.MESSAGES, 1))
        assertNull(shown(FacebookCounter.MESSAGES)?.extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES))
    }

    @Test
    fun aNotificationTap_opensItsTab() {
        FacebookCountsWorker.applyCounts(context, counts(notifications = 2), notify = true)
        assertEquals(2, number(FacebookCounter.NOTIFICATIONS, 2))
        val intent = shown(FacebookCounter.NOTIFICATIONS)?.contentIntent
        assertNotNull(intent)
        assertEquals(context.packageName, intent!!.creatorPackage)
    }
}
