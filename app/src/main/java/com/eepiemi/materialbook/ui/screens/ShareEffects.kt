package com.eepiemi.materialbook.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import com.eepiemi.materialbook.R
import com.eepiemi.materialbook.utils.REEL_CREATE_URL
import com.eepiemi.materialbook.utils.STORY_CREATE_URL
import com.eepiemi.materialbook.utils.ShareMediaQueue
import com.eepiemi.materialbook.utils.ShareTarget
import com.eepiemi.materialbook.utils.shareStartPageUrl
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebViewNavigator
import kotlinx.coroutines.delay

// A shared photo's page: how long to wait for a fresh load before using the page showing.
private const val SHARE_LOAD_WAIT_MS = 2000L

/** Photos or videos shared into the app for [target]; [seq] tells two shares apart. */
data class ShareMediaRequest(val seq: Int, val target: ShareTarget)

/** share_media.js for [target], with the target it's run for. */
internal fun shareMediaScript(resources: android.content.res.Resources, target: ShareTarget): String {
    val script = resources.openRawResource(R.raw.share_media).bufferedReader().use { it.readText() }
    // The note shown while Facebook prepares the photos (share_media.js showAdding).
    val count = ShareMediaQueue.remaining(target)
    val adding = if (count > 1) resources.getString(R.string.share_media_adding_several, count)
    else resources.getString(R.string.share_media_adding_one)
    return "window.__astryxShareTarget = '${target.key}';\n" +
        "window.__astryxShareAdding = ${org.json.JSONObject.quote(adding)};\n$script"
}

/**
 * Photos and videos shared into the app (ShareMedia), on MaterialbookWebView's side: the page
 * [shareMediaRequest] asks for opens in the main view ([navigator], whose page is in
 * [loadingState]) and gets share_media.js, or the story or reel creator opens in the Messages layer
 * ([onOpenLayer]; [onCloseLayer] closes it for a main-view share). The layer's own side is in
 * MessagesLayer. Covered end to end by ShareIntoFacebookTest.
 */
@Composable
internal fun MainViewShareEffects(
    shareMediaRequest: ShareMediaRequest?,
    navigator: WebViewNavigator,
    loadingState: LoadingState,
    onOpenLayer: (String) -> Unit,
    onCloseLayer: () -> Unit,
) {
    val resources = LocalResources.current
    // Photos and videos shared from another app (ShareMedia): a mobile page in the main view (the
    // feed's composer on Home, the profile, the groups), or a desktop creator in the layer
    // (stories, reels); share_media.js hands them to Facebook's page. A main-view share's script
    // waits for a fresh load of the page it asked for (a page already showing, or the one before,
    // could be gone a moment later).
    var pageShareTarget by remember { mutableStateOf<ShareTarget?>(null) }
    var pageShareLoadSeen by remember { mutableStateOf(false) }
    LaunchedEffect(shareMediaRequest?.seq) {
        val request = shareMediaRequest ?: return@LaunchedEffect
        val pageUrl = shareStartPageUrl(request.target)
        if (pageUrl != null) {
            onCloseLayer()
            pageShareTarget = request.target
            pageShareLoadSeen = false
            navigator.loadUrl(pageUrl)
        } else {
            onOpenLayer(if (request.target == ShareTarget.REEL) REEL_CREATE_URL else STORY_CREATE_URL)
        }
    }
    // A share that starts the app (a cold start from a gallery) can come while the first page is
    // loading: its own load then merges with that one and no fresh load is seen, so the script
    // never ran and the share ended on the feed (seen on a Galaxy A56). When no load starts within
    // SHARE_LOAD_WAIT_MS of the request, the page showing counts as the share's page.
    LaunchedEffect(pageShareTarget, pageShareLoadSeen) {
        val target = pageShareTarget ?: return@LaunchedEffect
        if (pageShareLoadSeen) return@LaunchedEffect
        delay(SHARE_LOAD_WAIT_MS)
        if (!pageShareLoadSeen && loadingState is LoadingState.Finished && ShareMediaQueue.pending(target)) {
            pageShareLoadSeen = true
            navigator.evaluateJavaScript(shareMediaScript(resources, target)) {}
        }
    }
    LaunchedEffect(loadingState, pageShareTarget) {
        val target = pageShareTarget ?: return@LaunchedEffect
        if (loadingState is LoadingState.Loading) {
            pageShareLoadSeen = true
        } else if (loadingState is LoadingState.Finished && pageShareLoadSeen) {
            // The script runs again on every page that finishes loading until it has taken the
            // files: the share's page can be replaced right after it loads, taking the script with
            // it (on a cold start m.facebook.com redirects its first load to "/?wtsid=…", and a
            // share that opens the app ended on the feed). Up to SHARE_PAGE_MS; a group share
            // waits while the user picks the group (opening one can be a full load), for up to
            // GROUP_PICK_MS. share_media.js runs only once per page.
            val wait = if (target == ShareTarget.GROUP) ShareMediaQueue.GROUP_PICK_MS else ShareMediaQueue.SHARE_PAGE_MS
            if (ShareMediaQueue.olderThan(wait)) ShareMediaQueue.clear()
            if (ShareMediaQueue.pending(target)) {
                navigator.evaluateJavaScript(shareMediaScript(resources, target)) {}
            } else {
                pageShareTarget = null
            }
        }
    }
}
