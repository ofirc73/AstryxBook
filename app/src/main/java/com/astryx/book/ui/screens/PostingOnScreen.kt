package com.astryx.book.ui.screens
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.astryx.book.notifications.AppVisibility
import com.astryx.book.notifications.Posting
import com.eepiemi.materialbook.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * While a story or a post is posting in the background (Posting), a bar over the top of the page
 * says so with a progress line, as the Facebook app does: the creator or composer is gone by then,
 * and the status-bar icon alone went unseen (2026-11).
 */
@Composable
fun PostingBar(modifier: Modifier = Modifier) {
    val kind = Posting.current ?: return
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 2.dp) {
        Column {
            Text(
                stringResource(kind.posting),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

// How long after a story is posted the feed's stories row has it (Galaxy A56, 2026-10-27): the
// stories page has it at once.
private const val STORIES_ROW_MS = 30_000L

// The posted story each of these was done for: once each, for the process (Posting.lastPosted is
// the process's), not again by an activity Android creates again (font size, language).
private var said: Posting.Posted? = null
private var refreshed: Posting.Posted? = null

/**
 * A story posted (Posting.lastPosted): with the app on screen, "Your story is posted" with View,
 * which opens it ([onView], Facebook's stories page); the feed's stories row gets it only some 30 s
 * later, so then [onRefreshFeed] (the main view reloads Home if it's at its top). Each once a story.
 */
@Composable
fun PostedStoryEffects(snackbarHostState: SnackbarHostState, onView: () -> Unit, onRefreshFeed: () -> Unit) {
    val resources = LocalResources.current
    val currentOnView by rememberUpdatedState(onView)
    val currentOnRefreshFeed by rememberUpdatedState(onRefreshFeed)
    val posted = Posting.lastPosted
    LaunchedEffect(posted) {
        if (posted == null || posted.kind != Posting.Kind.STORY) return@LaunchedEffect
        if (said != posted) launch {
            said = posted
            if (!AppVisibility.onScreen) return@launch
            val result = snackbarHostState.showSnackbar(
                message = resources.getString(R.string.story_posted),
                actionLabel = resources.getString(R.string.posting_view),
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) currentOnView()
        }
        if (refreshed == posted) return@LaunchedEffect
        delay(posted.at + STORIES_ROW_MS - System.currentTimeMillis())
        refreshed = posted
        if (AppVisibility.onScreen) currentOnRefreshFeed()
    }
}
