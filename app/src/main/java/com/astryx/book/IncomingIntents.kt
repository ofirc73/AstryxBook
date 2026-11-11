package com.astryx.book
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.astryx.book.utils.ShareMedia
import com.astryx.book.utils.ShareMediaQueue
import com.astryx.book.utils.ShareTarget
import com.astryx.book.utils.appLinkUrl
import com.astryx.book.utils.copySharedMedia
import com.astryx.book.utils.facebookShareUrl
import com.astryx.book.utils.sharedUris
import com.eepiemi.materialbook.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// What MainActivity is opened with (or sent while it runs, onNewIntent): photos and videos shared
// through one of the share entries, or a link.

/** The target of photos or videos shared through one of the share entries, if this is one. */
internal fun mediaShareTarget(intent: Intent?): ShareTarget? {
    if (intent?.action != Intent.ACTION_SEND && intent?.action != Intent.ACTION_SEND_MULTIPLE) return null
    return ShareMedia.targetOf(intent.component?.className)
}

/**
 * Copies the shared items the target takes into the cache (off the main thread; the sharing
 * app's permission to read them lasts only while the activity has the intent), then calls
 * [onReady], which has the WebView open Facebook's composer for them. Says so when some or all were
 * left out.
 */
internal fun receiveSharedMedia(
    activity: ComponentActivity,
    intent: Intent,
    target: ShareTarget,
    onReady: (ShareTarget) -> Unit,
) {
    val uris = sharedUris(intent)
    val type = intent.type
    activity.lifecycleScope.launch {
        val (files, skipped) = withContext(Dispatchers.IO) {
            copySharedMedia(activity, target, uris, type)
        }
        if (files.isEmpty()) {
            val nothing = when (target) {
                ShareTarget.FEED -> R.string.share_media_nothing_feed
                ShareTarget.STORY -> R.string.share_media_nothing_story
                ShareTarget.PROFILE_PICTURE, ShareTarget.GROUP -> R.string.share_media_nothing_photos
                ShareTarget.REEL -> R.string.share_media_nothing_reel
            }
            Toast.makeText(activity, nothing, Toast.LENGTH_LONG).show()
            return@launch
        }
        if (skipped > 0) {
            Toast.makeText(
                activity,
                activity.resources.getQuantityString(R.plurals.share_media_skipped, skipped, skipped),
                Toast.LENGTH_LONG
            ).show()
        }
        ShareMediaQueue.start(target, files)
        onReady(target)
    }
}

/**
 * What the intent MainActivity is created with asks for: the photos or videos of a share
 * ([mediaTarget]), or a link's page ([linkUrl]), or neither (a plain start).
 */
internal class StartRequest(val mediaTarget: ShareTarget?, val linkUrl: String?)

/**
 * Whether the intent is one Android hands the activity again, not a new request. Opened from
 * Recents after Back had closed it, the activity gets the intent it was first started with
 * (marked FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY): a share from days ago opened Facebook's composer
 * again with its photos, or said there was nothing to share once their permission was gone, and a
 * link opened its page again instead of the start page.
 */
internal fun isReplayedLaunch(intentFlags: Int): Boolean =
    intentFlags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0

/**
 * The [StartRequest] of [intent]. A replayed intent ([isReplayedLaunch]) asks for nothing. An
 * activity Android creates again ([restored]: after a change of font size or language, or once
 * its process was killed) keeps its link, the page it shows, but doesn't take the share's files
 * a second time.
 */
internal fun startRequest(context: Context, intent: Intent?, restored: Boolean): StartRequest {
    if (intent == null || isReplayedLaunch(intent.flags)) return StartRequest(null, null)
    val mediaTarget = mediaShareTarget(intent)
    return if (mediaTarget != null) StartRequest(mediaTarget.takeUnless { restored }, null)
    else StartRequest(null, linkUrl(context, intent))
}

/**
 * The page an intent asks for: a link shared from another app opens Facebook's share
 * dialog (shared text without a link can't be shared to Facebook's web share, so the app
 * just opens); a link or launcher shortcut opens its page if it's a Facebook one.
 */
internal fun linkUrl(context: Context, intent: Intent?): String? = if (intent?.action == Intent.ACTION_SEND) {
    facebookShareUrl(intent.getStringExtra(Intent.EXTRA_TEXT)).also {
        if (it == null) Toast.makeText(context, R.string.share_only_links, Toast.LENGTH_LONG).show()
    }
} else {
    appLinkUrl(intent?.data?.toString())
}
