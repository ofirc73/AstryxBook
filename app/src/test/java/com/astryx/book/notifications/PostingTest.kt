package com.astryx.book.notifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

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

    @Test
    fun startedAgain_isStillOnce() {
        Posting.started(context, Posting.Kind.POST)
        Posting.started(context, Posting.Kind.POST)
        Posting.ended(context, Posting.Kind.POST, Posting.Outcome.FAILED, notify = false)
        assertNull(Posting.showing())
    }
}
