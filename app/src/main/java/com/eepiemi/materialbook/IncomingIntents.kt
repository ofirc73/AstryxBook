package com.eepiemi.materialbook

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.eepiemi.materialbook.utils.ShareMedia
import com.eepiemi.materialbook.utils.ShareMediaQueue
import com.eepiemi.materialbook.utils.ShareTarget
import com.eepiemi.materialbook.utils.appLinkUrl
import com.eepiemi.materialbook.utils.copySharedMedia
import com.eepiemi.materialbook.utils.facebookShareUrl
import com.eepiemi.materialbook.utils.sharedUris
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
