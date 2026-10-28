package com.astryx.book.ui.screens
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.astryx.book.notifications.FacebookCounts
import com.astryx.book.notifications.FacebookCountsWorker
import com.eepiemi.materialbook.ui.viewmodel.SettingsViewModel
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebViewState

/**
 * What MaterialbookWebView does each time the app goes out of sight (the activity stops), for
 * the main view's page ([state]): records the Facebook notification counts the user leaves with,
 * and empties the HTTP cache with "Clear cache when leaving".
 */
@Composable
internal fun AppLeaveEffects(settingsVM: SettingsViewModel, state: WebViewState) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    // "Clear cache when leaving" (Settings): the HTTP cache (the photos, videos and pages
    // Facebook sent, shared by every WebView in the app) is emptied each time the app goes out
    // of sight. The login, site data and copied images stay: a copied image may be pasted in
    // the app the user just switched to.
    val clearCacheOnLeave by settingsVM.clearCacheOnLeave.collectAsState()
    val currentClearCacheOnLeave by rememberUpdatedState(clearCacheOnLeave)
    // "Facebook notifications" (Settings): the counts the user leaves the app with are the ones
    // background checks compare against, read from the page's tab bar (no network), or fetched
    // once if the page has none (Feeds, a profile).
    val facebookNotifications by settingsVM.facebookNotifications.collectAsState()
    val currentFacebookNotifications by rememberUpdatedState(facebookNotifications)
    val lifecycle = (activity as? LifecycleOwner)?.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_STOP) return@LifecycleEventObserver
            val webView = runCatching { state.nativeWebView }.getOrNull()
            if (currentFacebookNotifications) {
                if (webView != null && state.loadingState is LoadingState.Finished) {
                    webView.evaluateJavascript(FacebookCounts.TAB_LABELS_JS) { result ->
                        val labels = runCatching {
                            val array = org.json.JSONArray(result)
                            List(array.length()) { array.getString(it) }
                        }.getOrNull()
                        FacebookCountsWorker.onAppHidden(context, labels)
                    }
                } else {
                    FacebookCountsWorker.onAppHidden(context, null)
                }
            }
            if (currentClearCacheOnLeave) webView?.clearCache(true)
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
}
