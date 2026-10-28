package com.astryx.book.notifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FacebookCountsTest {

    // Facebook's tab bar as m.facebook.com sends it (UI in Hebrew; the labels stay English).
    private fun tab(label: String, selected: Boolean = false) =
        "<div role=\"tab\" tabindex=\"0\" aria-label=\"‎$label‎\" aria-selected=\"$selected\" data-focusable=\"true\">"

    private val tabBar = tab("feed, 895 new, 1 of 5") + tab("friends, 2 new, 2 of 5") +
        tab("messages, 3 of 5") + tab("notifications, 99+ new, 4 of 5", selected = true) +
        tab("marketplace, 1 new, 5 of 5") + "<span>מה בא לך לפרסם?</span>"

    @Test
    fun parse_readsTheCountersFromTheTabBar() {
        assertEquals(
            mapOf(
                FacebookCounter.NOTIFICATIONS to UnreadCount(99, capped = true),
                FacebookCounter.MESSAGES to UnreadCount(0),
                FacebookCounter.FRIEND_REQUESTS to UnreadCount(2),
            ),
            FacebookCounts.parse(tabBar)
        )
    }

    @Test
    fun parse_withoutATabBar_knowsNothing() {
        assertNull(FacebookCounts.parse("<html><form id=\"login_form\"></form></html>"))
    }

    @Test
    fun parseLabels_readsTheLabelsTheOpenPageLists() {
        val labels = listOf("‎feed, 1 of 5‎", "‎friends, 2 of 5‎",
            "‎messages, 1 new, 3 of 5‎", "‎notifications, 4 of 5‎", "Close")
        assertEquals(
            mapOf(
                FacebookCounter.NOTIFICATIONS to UnreadCount(0),
                FacebookCounter.MESSAGES to UnreadCount(1),
                FacebookCounter.FRIEND_REQUESTS to UnreadCount(0),
            ),
            FacebookCounts.parseLabels(labels)
        )
        assertNull(FacebookCounts.parseLabels(listOf("Close", "Menu")))
    }

    @Test
    fun moreUnread_thanLastTime_notifies() {
        assertEquals(CountAction.Notify(UnreadCount(3)), FacebookCounts.action(UnreadCount(1), UnreadCount(3)))
        assertEquals(CountAction.Notify(UnreadCount(2)), FacebookCounts.action(null, UnreadCount(2)))
        assertEquals(
            CountAction.Notify(UnreadCount(99, true)),
            FacebookCounts.action(UnreadCount(99), UnreadCount(99, true))
        )
    }

    @Test
    fun theSameCount_doesNothing() {
        assertEquals(CountAction.Nothing, FacebookCounts.action(UnreadCount(3), UnreadCount(3)))
        assertEquals(CountAction.Nothing, FacebookCounts.action(null, UnreadCount(0)))
        assertEquals(CountAction.Nothing, FacebookCounts.action(UnreadCount(0), UnreadCount(0)))
    }

    @Test
    fun someRead_updatesQuietly_allRead_clears() {
        assertEquals(CountAction.Update(UnreadCount(1)), FacebookCounts.action(UnreadCount(3), UnreadCount(1)))
        assertEquals(CountAction.Clear, FacebookCounts.action(UnreadCount(3), UnreadCount(0)))
    }

    // A notification on m.facebook.com/notifications/ as the server sends it: the item's label is
    // the text, date and read state; its options button quotes the text alone.
    private fun item(id: Int, label: String, options: String?) =
        "<div data-tti-phase=\"-1\" data-tracking-duration-id=\"$id\" data-mcomponent=\"MContainer\" class=\"m\">" +
            "<div role=\"button\" tabindex=\"0\" aria-label=\"$label\" data-focusable=\"true\"></div>" +
            (options?.let { "<div role=\"button\" tabindex=\"0\" aria-label=\"$it\"></div>" } ?: "") + "</div>"

    private val notificationsPage =
        "<div role=\"button\" aria-label=\"Mark all notifications as read\"></div>" +
            "<div data-tracking-duration-id=\"1\"><h2>New</h2></div>" +
            item(2, "Dana commented on your post: &quot;Nice!&quot; 5 minutes ago Unread",
                "See more notification options for notification titled &quot;Dana commented on your post: &quot;Nice!&quot;&quot;") +
            item(3, "See who&#x27;s also on Facebook. Tap here to get help finding friends. October 8 at 12:13 PM Unread",
                "See more notification options for notification titled &quot;See who&#x27;s also on Facebook. Tap here to get help finding friends.&quot;") +
            item(4, "Your memory from 3 years ago Yesterday", null)

    @Test
    fun notificationTexts_areTheNewest_withoutTheirDates() {
        assertEquals(
            listOf("Dana commented on your post: \"Nice!\"", "See who's also on Facebook. Tap here to get help finding friends."),
            FacebookCounts.parseNotificationTexts(notificationsPage, 2)
        )
        assertEquals(1, FacebookCounts.parseNotificationTexts(notificationsPage, 1).size)
        assertEquals(emptyList<String>(), FacebookCounts.parseNotificationTexts(notificationsPage, 0))
    }

    @Test
    fun notificationText_inAnotherLanguage_andQuotes() {
        // Facebook in Hebrew: single quotes and right-to-left marks around the parts.
        val page = item(
            2,
            "רוצה לראות מי עוד נמצא בפייסבוק? אפשר להקיש כאן כדי לקבל עזרה במציאת חברים. ‏8 באוקטובר‏ ב-‏12:13‏ לא נקראה",
            "הצגת אפשרויות התראה נוספות עבור ההתראה שכותרתה &#x27;‏רוצה לראות מי עוד נמצא בפייסבוק? אפשר להקיש כאן כדי לקבל עזרה במציאת חברים.‏&#x27;"
        )
        assertEquals(
            listOf("רוצה לראות מי עוד נמצא בפייסבוק? אפשר להקיש כאן כדי לקבל עזרה במציאת חברים."),
            FacebookCounts.parseNotificationTexts(page, 3)
        )
    }

    @Test
    fun notificationWithoutAnOptionsButton_keepsItsWholeLabel() {
        assertEquals(
            "Your memory from 3 years ago Yesterday",
            FacebookCounts.parseNotificationTexts(notificationsPage, 3)[2]
        )
    }

    @Test
    fun aPageWithoutNotifications_hasNoTexts() {
        assertEquals(emptyList<String>(), FacebookCounts.parseNotificationTexts(tabBar, 3))
    }

    @Test
    fun aCappedCount_isShownWithItsPlus() {
        assertEquals("99+", UnreadCount(99, capped = true).label)
        assertEquals("4", UnreadCount(4).label)
    }
}
