package com.astryx.book.ui.screens
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.astryx.book.notifications.AppVisibility
import com.astryx.book.notifications.Posting
import com.astryx.book.notifications.rememberPostingStart

/**
 * A post the main view's page is uploading (feed_posting.js, through the AstryxPage call
 * "feedPosting"), on the app's side: the posting notification (Posting) from Post until Facebook
 * has the post, and "Your post is posted" when the app is out of sight then. The page went (a new
 * page loaded in the main view, its renderer lost): ended without a word, as nothing tells whether
 * Facebook sends the post again from there.
 */
class FeedPosting internal constructor(private val context: Context) {
    internal var start: () -> Unit = {}
    var posting = false
        private set

    /**
     * The page's report: started (null), posted (said when the app is out of sight), or ended
     * unseen (INTERRUPTED: no "Posted" seen, nothing said).
     */
    fun on(ended: Posting.Outcome?) {
        if (ended == null) {
            if (posting) return
            posting = true
            start()
        } else end(ended, notify = ended == Posting.Outcome.POSTED && !AppVisibility.onScreen)
    }

    internal fun pageGone() {
        if (posting) Log.i("Astryxbook", "Posting: the page went while its post was uploading")
        end(Posting.Outcome.INTERRUPTED, notify = false)
    }

    internal fun appClosed() = end(Posting.Outcome.INTERRUPTED, notify = true)

    private fun end(outcome: Posting.Outcome, notify: Boolean) {
        if (!posting) return
        posting = false
        Posting.ended(context, Posting.Kind.POST, outcome, notify)
    }
}

/** The main view's [FeedPosting]; a new page in it ([pageStarts]) ends a posting. */
@Composable
fun rememberFeedPosting(pageStarts: Int): FeedPosting {
    val context = LocalContext.current
    val feedPosting = remember { FeedPosting(context.applicationContext) }
    feedPosting.start = rememberPostingStart(Posting.Kind.POST)
    LaunchedEffect(pageStarts) { feedPosting.pageGone() }
    DisposableEffect(Unit) { onDispose { feedPosting.appClosed() } }
    return feedPosting
}
