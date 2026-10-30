package com.astryx.book
import com.astryx.book.utils.BackHistory
import com.astryx.book.utils.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Back in the main view when Facebook bounces history pages through intent:// (full loads of a
// reel redirect to its app; the app opens the web fallback as a new entry).
class BackHistoryTest {

    private val home = HistoryEntry("https://m.facebook.com/", "https://m.facebook.com/")
    private val reel = "https://m.facebook.com/reel/1474390431207398/"
    private val otherReel = "https://m.facebook.com/reel/1360854416127071/"

    private fun page(url: String) = HistoryEntry(url, url)

    // An entry the app opened as the web fallback of an intent:// redirect.
    private fun fallback(url: String, wtsid: String) =
        HistoryEntry(url, "$url?referral_source=external_link&wtsid=rdr_$wtsid&from_intent_redirect=1")

    @Test
    fun anOrdinaryPage_goesOneStepBack() {
        val history = BackHistory()
        assertEquals(0, history.backTarget(listOf(home, page(reel)), 1, pageStarts = 3, now = 0))
        assertNull(history.backTarget(listOf(home), 0, pageStarts = 3, now = 0))
    }

    @Test
    fun aBouncedReel_goesPastTheCopiesOfItself() {
        // The phone's history (2026-10-30), restored after the app's process was: the reel from
        // the feed, then a copy for every Back that bounced.
        val entries = listOf(
            home, home, page(reel), page("$reel?referral_source=native_in_feed_unit"),
            fallback(reel, "a"), fallback(reel, "b"), fallback(reel, "c"),
        )
        assertEquals(1, BackHistory().backTarget(entries, 6, pageStarts = 3, now = 0))
    }

    @Test
    fun aReelNotBounced_keepsItsEarlierEntries() {
        // The same reel's own earlier state (a sheet it opened in-page), not a copy.
        val entries = listOf(home, page(reel), page(reel))
        assertEquals(1, BackHistory().backTarget(entries, 2, pageStarts = 3, now = 0))
    }

    @Test
    fun aBackStepThatBounced_countsAsTheEntryItWentTo() {
        // Home, reel A (to be loaded again: it bounces), reel B. Back from B goes to A, Facebook
        // sends A to its app, and A's fallback lands on top of B.
        val history = BackHistory()
        val entries = mutableListOf(home, page(otherReel), page(reel))
        assertEquals(1, history.backTarget(entries, 2, pageStarts = 5, now = 1_000))
        history.onIntentFallback("$otherReel?wtsid=rdr_x&from_intent_redirect=1", pageStarts = 5, now = 1_700)
        entries += HistoryEntry(otherReel, "$otherReel?wtsid=rdr_x&from_intent_redirect=1")
        // Back from it goes on before A, not to B (which would bounce back to A again).
        assertEquals(0, history.backTarget(entries, 3, pageStarts = 6, now = 3_000))
    }

    @Test
    fun aFallbackAfterBackStepsEnded_isAPageOfItsOwn() {
        val history = BackHistory()
        val profile = "https://m.facebook.com/some.one/?wtsid=rdr_y&from_intent_redirect=1"
        val notifications = page("https://m.facebook.com/notifications/")
        // A page started after Back (it didn't bounce), then a profile link tapped there bounced.
        history.backTarget(listOf(home, notifications, page(reel)), 2, pageStarts = 5, now = 1_000)
        history.onIntentFallback(profile, pageStarts = 6, now = 1_500)
        val entries = listOf(home, notifications, HistoryEntry("https://m.facebook.com/some.one/", profile))
        assertEquals(1, history.backTarget(entries, 2, pageStarts = 7, now = 10_000))
        // Too late is no Back step either.
        val late = BackHistory()
        late.backTarget(entries, 2, pageStarts = 7, now = 0)
        late.onIntentFallback(profile, pageStarts = 7, now = 6_000)
        assertEquals(1, late.backTarget(entries, 2, pageStarts = 7, now = 7_000))
    }
}
