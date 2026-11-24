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
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.astryx.book.notifications.AppVisibility
import com.astryx.book.notifications.Posting
import com.eepiemi.materialbook.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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

// How long after a story is posted the feed's stories row has it: about 30 s (Galaxy A56,
// 2026-10-27), at times longer (still missing at 30 s on the phone, 2026-11-17). The stories page
// has it at once.
private const val STORIES_ROW_MS = 30_000L
// While the row doesn't show it yet (or Home isn't at its top), Home is looked at again this long after, up to STORIES_ROW_RELOADS
// reloads in all (about two minutes); where the row's label for the user's story isn't known
// (Facebook in a language other than English), up to STORIES_ROW_RELOADS_BLIND.
private const val STORIES_ROW_RETRY_MS = 20_000L
private const val STORIES_ROW_RELOADS = 5
private const val STORIES_ROW_RELOADS_BLIND = 2
// Coming back to the app later than this after the story was posted, Home isn't reloaded for it.
private const val STORIES_ROW_WINDOW_MS = 15 * 60_000L

// Home's stories row, at Home's top: "shown" when it has the user's story (the card m.facebook.com
// labels "View your story. 1 unseen story", read on the emulator 2026-11-17), else Home is reloaded:
// "reloaded", or "blind" where the row's labels aren't English (no "Create story" card by that
// label), so the user's card can't be told. "away" when Home is scrolled down: left as it is.
internal const val STORIES_ROW_JS = "(function () {" +
    " if (window.scrollY >= 300) return 'away';" +
    " if (document.querySelector('[aria-label^=\"View your story\"]')) return 'shown';" +
    " var known = !!document.querySelector('[aria-label=\"Create story\"]');" +
    " location.reload(); return known ? 'reloaded' : 'blind'; })()"

// The posted story each of these was done for: once each, for the process (Posting.lastPosted is
// the process's), not again by an activity Android creates again (font size, language).
private var said: Posting.Posted? = null
private var refreshed: Posting.Posted? = null

/**
 * A story posted (Posting.lastPosted): with the app on screen, "Your story is posted" with View,
 * which opens it ([onView], Facebook's stories page). The feed's stories row gets it only some 30 s
 * later, so then Home, if it's at its top, is reloaded ([STORIES_ROW_JS], run by [onHome]: the main
 * view's page when it's Home, else null), and again while its row doesn't show the story. With the
 * app away then (the native share screen's Share sends it back to the app the share came from),
 * that waits until it's on screen again; with Home not showing, or scrolled down, until it's at
 * Home's top (looked at every [STORIES_ROW_RETRY_MS], within [STORIES_ROW_WINDOW_MS]). Each once a story.
 */
@Composable
fun PostedStoryEffects(snackbarHostState: SnackbarHostState, onView: () -> Unit, onHome: suspend (script: String) -> String?) {
    val resources = LocalResources.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnView by rememberUpdatedState(onView)
    val currentOnHome by rememberUpdatedState(onHome)
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
        var reloads = 0
        while (true) {
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            if (System.currentTimeMillis() - posted.at > STORIES_ROW_WINDOW_MS) return@LaunchedEffect
            // Not on Home, or Home scrolled down: looked at again a little later (the user may go to
            // Home's top meanwhile). Ending there left the row without the story when the app came
            // back elsewhere (v1.50.1, the user's report).
            val row = currentOnHome(STORIES_ROW_JS)
            Log.d("Astryxbook", "Posting: stories row ${row ?: "not on Home"}")
            if (row == null || row.contains("away")) {
                delay(STORIES_ROW_RETRY_MS)
                continue
            }
            if (!row.contains("reloaded") && !row.contains("blind")) return@LaunchedEffect
            reloads++
            Log.d("Astryxbook", "Posting: Home reloaded for the stories row ($reloads)")
            val most = if (row.contains("blind")) STORIES_ROW_RELOADS_BLIND else STORIES_ROW_RELOADS
            if (reloads >= most) return@LaunchedEffect
            delay(STORIES_ROW_RETRY_MS)
        }
    }
}
