package com.eepiemi.materialbook.ui.screens

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.webkit.CookieManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberSaveableWebViewState
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.eepiemi.materialbook.R
import com.eepiemi.materialbook.notifications.FacebookCounts
import com.eepiemi.materialbook.notifications.FacebookCountsWorker
import com.eepiemi.materialbook.ui.components.ExternalLinkDialog
import com.eepiemi.materialbook.ui.components.MessengerLinkDialog
import com.eepiemi.materialbook.ui.components.NetworkErrorDialog
import com.eepiemi.materialbook.ui.components.settings.SettingsDialog
import com.eepiemi.materialbook.ui.viewmodel.MainViewModel
import com.eepiemi.materialbook.ui.viewmodel.SettingsViewModel
import com.eepiemi.materialbook.utils.DESKTOP_USER_AGENT
import com.eepiemi.materialbook.utils.lookUpPhotoPost
import com.eepiemi.materialbook.utils.ExternalLinkAction
import com.eepiemi.materialbook.utils.ExternalRequestInterceptor
import com.eepiemi.materialbook.utils.externalLinkAction
import com.eepiemi.materialbook.utils.isMessengerAppLink
import com.eepiemi.materialbook.utils.FullscreenController
import com.eepiemi.materialbook.utils.FEEDS_PAGE_URL
import com.eepiemi.materialbook.utils.REEL_CREATE_URL
import com.eepiemi.materialbook.utils.STORY_CREATE_URL
import com.eepiemi.materialbook.utils.ShareMediaQueue
import com.eepiemi.materialbook.utils.ShareTarget
import com.eepiemi.materialbook.utils.shareStartPageUrl
import com.eepiemi.materialbook.utils.HOME_PAGE_URL
import com.eepiemi.materialbook.utils.appOrientation
import com.eepiemi.materialbook.utils.appWebViewParams
import com.eepiemi.materialbook.utils.clearWebData
import com.eepiemi.materialbook.utils.deadWebViewUrl
import com.eepiemi.materialbook.utils.rendererRecoveryUrl
import com.eepiemi.materialbook.utils.jsBridge.installClipboardBridge
import com.eepiemi.materialbook.utils.jsBridge.installDownloadBridge
import com.eepiemi.materialbook.utils.jsBridge.installShareMediaBridge
import com.eepiemi.materialbook.utils.jsBridge.installPageBridges
import com.eepiemi.materialbook.utils.jsBridge.PageBridges
import com.eepiemi.materialbook.utils.jsBridge.LayerBridge
import com.eepiemi.materialbook.utils.jsBridge.MaterialbookSettings
import com.eepiemi.materialbook.utils.jsBridge.ThemeChange
import com.eepiemi.materialbook.utils.jsBridge.DownloadEvents
import com.eepiemi.materialbook.utils.jsBridge.MaterialYouBridge
import com.eepiemi.materialbook.utils.jsBridge.PipBridge
import com.eepiemi.materialbook.utils.jsBridge.ScreenBridge
import com.eepiemi.materialbook.audio.PipHandback
import com.eepiemi.materialbook.utils.effectiveDesktop
import com.eepiemi.materialbook.utils.fbRedirectSanitizer
import com.eepiemi.materialbook.utils.hardenWebView
import com.eepiemi.materialbook.utils.intentFallbackUrl
import com.eepiemi.materialbook.utils.KeepScreenOn
import com.eepiemi.materialbook.utils.TextZoom
import com.eepiemi.materialbook.utils.isMessagesLink
import com.eepiemi.materialbook.utils.isStoryCreatePage
import com.eepiemi.materialbook.utils.isWebUrl
import com.eepiemi.materialbook.utils.openInCustomTab
import com.eepiemi.materialbook.utils.messagesDesktopUrl
import com.eepiemi.materialbook.utils.shareablePageUrl
import com.eepiemi.materialbook.utils.rememberAutoDesktop
import com.eepiemi.materialbook.utils.rememberImeHeight
import com.eepiemi.materialbook.utils.shouldKeepScreenOn
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.delay

private const val HOME_URL = HOME_PAGE_URL

// Leaving PiP after a lost renderer: how long the window takes to get its full size back, and
// how many zoom-out steps (x0.8 each) can bring a page from the PiP window's width back.
private const val PIP_EXIT_SETTLE_MS = 600L
// A shared photo's page: how long to wait for a fresh load before using the page showing.
private const val SHARE_LOAD_WAIT_MS = 2000L
private const val MAX_ZOOM_OUT_STEPS = 20
// A pinch zoom undone in PiP: from just above 1 (none) up to what zoomBy takes; checked once the
// PiP window has its size.
private const val PINCH_RATIO_MIN = 1.05f
private const val PINCH_RATIO_MAX = 100f
private const val PIP_PINCH_CHECK_DELAY_MS = 300L

/** A link the running app was sent (onNewIntent); [seq] tells two taps on the same link apart. */
data class OpenLinkRequest(val seq: Int, val url: String)

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

@Composable
fun MaterialbookWebView(
    url: String,
    settingsVM: SettingsViewModel = viewModel(),
    pipToggleTrigger: Int = 0,
    pipEnteringTrigger: Int = 0,
    isInPipMode: Boolean = false,
    pipStartedWhilePlaying: Boolean = false,
    pipHandoffReadTrigger: Int = 0,
    pipHandback: PipHandback? = null,
    openLinkRequest: OpenLinkRequest? = null,
    shareMediaRequest: ShareMediaRequest? = null,
    onPipHandoffRead: (String?) -> Unit = {},
    onPipPageVisible: () -> Unit = {},
    isAppLocked: Boolean = false,
    onVideoPlayingChanged: (Boolean, Int, Int) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val resources = LocalResources.current

    val state = rememberSaveableWebViewState(url)

    val isDesktop by settingsVM.desktopLayout.collectAsState()
    val isAutoDesktop = rememberAutoDesktop()
    val isEffectiveDesktop = effectiveDesktop(isDesktop, isAutoDesktop)

    val showNotSupported = {
        Toast.makeText(
            context,
            resources.getString(R.string.not_supported),
            Toast.LENGTH_SHORT
        ).show()
    }
    // "Open links in the app": web links open in a Custom Tab (the browser's own tab, drawn
    // over this app, without its Facebook login). Read at call time: the request interceptor
    // keeps the first composition's lambdas.
    val openLinksInApp by settingsVM.openLinksInApp.collectAsState()
    val currentOpenLinksInApp by rememberUpdatedState(openLinksInApp)
    val launchExternalUrl: (String) -> Unit = launch@{ target ->
        if (currentOpenLinksInApp && isWebUrl(target) && openInCustomTab(context, target)) return@launch
        // BROWSABLE and no selector: the link only reaches activities that accept links from
        // a browser, as it would from Chrome.
        val intent = Intent(Intent.ACTION_VIEW, target.toUri())
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .apply { selector = null }
        runCatching { context.startActivity(intent) }.onFailure {
            Toast.makeText(context, resources.getString(R.string.no_app_for_link), Toast.LENGTH_SHORT).show()
        }
    }
    // A link into another app (not web, email or phone) waits here for the user's OK.
    var pendingExternalLink by remember { mutableStateOf<String?>(null) }
    // A Messenger app link (Facebook's Messages tab sends one) while Messages in desktop mode
    // is off: offered as desktop mode instead, so the tab isn't a dead end without Messenger.
    var pendingMessengerLink by remember { mutableStateOf<String?>(null) }
    val currentIsInPipMode by rememberUpdatedState(isInPipMode)
    val openExternalUrl: (String) -> Unit = openExternalUrl@{ externalUrl ->
        // A page in PiP can't be tapped, so a link leaving the app then is one the page sent by
        // itself (logged out, Facebook pushes its app with fb:// on load): its dialog would cover
        // the video, and nothing should open over the user's other app. Ignored.
        if (currentIsInPipMode) {
            Log.d("Astryxbook", "External link ignored in PiP: ${externalUrl.substringBefore(':')}:")
            return@openExternalUrl
        }
        // intent:// links are never used to launch the app they name (Facebook uses them to
        // push its own apps); if one carries a web fallback, that page is opened instead.
        val target = if (externalUrl.startsWith("intent:", ignoreCase = true)) {
            intentFallbackUrl(externalUrl)
        } else {
            externalUrl
        }
        if (target == null) {
            showNotSupported()
        } else {
            when (externalLinkAction(target)) {
                ExternalLinkAction.OPEN -> launchExternalUrl(target)
                ExternalLinkAction.CONFIRM ->
                    if (isMessengerAppLink(target) && !settingsVM.messagesDesktop.value && !isEffectiveDesktop) {
                        pendingMessengerLink = target
                    } else {
                        pendingExternalLink = target
                    }
                ExternalLinkAction.BLOCK -> showNotSupported()
            }
        }
    }

    // Messages in desktop mode: Messages links open in MessagesLayer, a separate desktop
    // WebView over this one, so this page (and its scroll position) stays as it is. Not
    // used when this view is already the desktop site (Desktop layout, large screens),
    // which shows Messages by itself. Saved so the layer comes back after recreation.
    val messagesDesktopSetting by settingsVM.messagesDesktop.collectAsState()
    val currentMessagesDesktopSetting by rememberUpdatedState(messagesDesktopSetting)
    val currentIsEffectiveDesktop by rememberUpdatedState(isEffectiveDesktop)
    var messagesLayerUrl by rememberSaveable { mutableStateOf<String?>(null) }
    pendingExternalLink?.let { link ->
        ExternalLinkDialog(
            link = link,
            onOpen = {
                pendingExternalLink = null
                launchExternalUrl(link)
            },
            onDismiss = { pendingExternalLink = null }
        )
    }
    pendingMessengerLink?.let { link ->
        MessengerLinkDialog(
            onUseDesktop = {
                pendingMessengerLink = null
                settingsVM.setMessagesDesktop(true)
                messagesLayerUrl = messagesDesktopUrl(link)
            },
            onOpenApp = {
                pendingMessengerLink = null
                launchExternalUrl(link)
            },
            onDismiss = { pendingMessengerLink = null }
        )
    }
    val navigator = rememberWebViewNavigator(
        requestInterceptor = ExternalRequestInterceptor(
            tryOpenMessagesDesktop = { messagesUrl ->
                if (currentMessagesDesktopSetting && !currentIsEffectiveDesktop) {
                    messagesLayerUrl = messagesDesktopUrl(messagesUrl)
                    true
                } else {
                    false
                }
            },
            isDesktopView = { currentIsEffectiveDesktop },
            handleExternalUrl = openExternalUrl
        )
    )

    // loadUrl doesn't go through the interceptor: a Messages link the app was opened with (a
    // launcher shortcut, a link from another app) goes where tapping it in the page would, the
    // Messages layer or this view's desktop Messages page. A link sent to the running app
    // ([whileRunning]) closes the Messages layer to show its page, or opens in the layer over
    // the page that's already here.
    fun openAppLink(link: String, whileRunning: Boolean) {
        when {
            !isMessagesLink(link) || !link.startsWith("http", ignoreCase = true) -> {
                if (whileRunning) messagesLayerUrl = null
                navigator.loadUrl(link)
            }
            currentMessagesDesktopSetting && !currentIsEffectiveDesktop -> {
                messagesLayerUrl = messagesDesktopUrl(link)
                if (!whileRunning) navigator.loadUrl(HOME_URL)
            }
            currentIsEffectiveDesktop -> navigator.loadUrl(messagesDesktopUrl(link))
            else -> navigator.loadUrl(link)
        }
    }

    LaunchedEffect(navigator) {
        if (state.viewState == null) openAppLink(url, whileRunning = false)
    }

    // A link tapped elsewhere while the app runs (singleTask) opens in this WebView rather
    // than a second copy of the app. Guarded on a request so the navigator isn't sent a
    // load at composition (see CLAUDE.md).
    LaunchedEffect(openLinkRequest?.seq) {
        openLinkRequest?.let { openAppLink(it.url, whileRunning = true) }
    }

    // Page zoom (Settings, stored as "Text size"): page_zoom.js at document start, installed when
    // the WebView is created (onCreated). m.facebook.com cancels WebView's own textZoom, which is
    // left for WebViews without document-start scripts. A change re-installs the script on the
    // WebView that's there and reloads the page, with Facebook's viewport cookie set ahead so
    // the server lays that page out for the new width too.
    val textZoom by settingsVM.textZoom.collectAsState()
    val textZoomPercent = TextZoom.percent(textZoom)
    var pageZoomScript by remember { mutableStateOf<ScriptHandler?>(null) }
    var pageZoomPercent by remember { mutableIntStateOf(textZoomPercent) }
    var pageZoomByScript by remember { mutableStateOf(true) }
    LaunchedEffect(textZoomPercent) {
        if (textZoomPercent == pageZoomPercent) return@LaunchedEffect
        pageZoomPercent = textZoomPercent
        val webView = runCatching { state.nativeWebView }.getOrNull() ?: return@LaunchedEffect
        if (!pageZoomByScript) {
            webView.settings.textZoom = textZoomPercent
            return@LaunchedEffect
        }
        pageZoomScript?.remove()
        pageZoomScript = installPageZoomScript(webView, textZoomPercent)
        webView.settings.useWideViewPort = textZoomPercent < 100
        if (webView.width > 0 && webView.height > 0) {
            CookieManager.getInstance().setCookie(
                "https://m.facebook.com",
                TextZoom.viewportCookie(
                    webView.width, webView.height, resources.displayMetrics.density, textZoomPercent
                )
            )
        }
        if (state.loadingState is LoadingState.Finished) navigator.reload()
    }

    // PiP follows the page on screen: the Messages layer's while it's open (it reports its
    // own videos through PipBridge too), else this view's. Everything below acts on that
    // page; the PiP scripts themselves are the same for both.
    var layerPipTarget by remember { mutableStateOf<PipTarget?>(null) }
    val pipNavigator = layerPipTarget?.navigator ?: navigator
    val pipState = layerPipTarget?.state ?: state

    // Fired from the PiP overlay's native Play/Pause button (see MainActivity's
    // pipActionReceiver) — the only reverse (native -> JS) channel we have,
    // vs PipBridge which only goes JS -> native.
    LaunchedEffect(pipToggleTrigger) {
        if (pipToggleTrigger > 0) {
            pipNavigator.evaluateJavaScript(PIP_TOGGLE_JS) {}
        }
    }

    // Fired from onUserLeaveHint, the earliest moment PiP is known to be
    // eligible — freezes the detector's "last active video" before
    // Facebook's own reels controller gets a chance to pause it and autoplay
    // a different one first. Deliberately separate from the isInPipMode
    // effect below, which only fires once PiP has visually engaged (up to
    // ~3.5s later) — too late to close this race.
    LaunchedEffect(pipEnteringTrigger) {
        if (pipEnteringTrigger > 0) {
            pipNavigator.evaluateJavaScript(PIP_FREEZE_ACTIVE_VIDEO_JS) {}
        }
    }

    // Keep-playing guard (see pipKeepPlayingActivateJs): active only while PiP is engaged.
    LaunchedEffect(isInPipMode, pipState.loadingState) {
        if (pipState.loadingState is LoadingState.Finished) {
            pipNavigator.evaluateJavaScript(
                if (isInPipMode) pipKeepPlayingActivateJs(pipStartedWhilePlaying)
                else PIP_KEEP_PLAYING_DISARM_JS
            ) {}
        }
    }

    // Set by FullscreenController's host below while an HTML5 fullscreen
    // custom view is showing.
    var isFullscreen by remember { mutableStateOf(false) }

    // In HTML5 fullscreen the video renders in WebView's custom view, not the
    // page layout, so PiP just shows that view and focus mode (hide page DOM,
    // restyle the <video>) is skipped. Restore still always runs outside PiP:
    // it also unfreezes the active-video tracker that PIP_FREEZE_ACTIVE_VIDEO_JS
    // sets on every PiP attempt, fullscreen or not.
    LaunchedEffect(isInPipMode, pipState.loadingState) {
        if (pipState.loadingState is LoadingState.Finished) {
            if (!isInPipMode) {
                pipNavigator.evaluateJavaScript(PIP_RESTORE_MODE_JS) {}
            } else if (isFullscreen) {
                Log.d("AstryxbookPiP", "PiP entered while fullscreen: skipping focus mode")
            } else {
                pipNavigator.evaluateJavaScript(PIP_FOCUS_MODE_JS + PIP_KEEP_CHAIN_JS) {}
            }
        }
    }

    // Landscape fullscreen -> PiP: WebView ends fullscreen right after PiP
    // starts (see PIP_HOLD_FULLSCREENCHANGE_JS). Hold that change back from
    // the page while in PiP so Facebook keeps the video, apply focus mode once
    // fullscreen has ended (the skip above only covered PiP entry), and on
    // the way out of PiP replay the change at the final window size.
    var fullscreenEndedInPip by remember { mutableStateOf(false) }
    var holdingFullscreenChange by remember { mutableStateOf(false) }
    LaunchedEffect(isInPipMode) {
        if (isInPipMode && isFullscreen && pipState.loadingState is LoadingState.Finished) {
            holdingFullscreenChange = true
            pipNavigator.evaluateJavaScript(PIP_HOLD_FULLSCREENCHANGE_JS) {}
        } else if (!isInPipMode && holdingFullscreenChange) {
            holdingFullscreenChange = false
            if (fullscreenEndedInPip) {
                fullscreenEndedInPip = false
                pipNavigator.evaluateJavaScript(PIP_RELAYOUT_AFTER_FULLSCREEN_JS) {}
            } else {
                pipNavigator.evaluateJavaScript(PIP_RELEASE_FULLSCREENCHANGE_JS) {}
            }
        }
    }
    LaunchedEffect(isFullscreen) {
        if (!isFullscreen && isInPipMode && pipState.loadingState is LoadingState.Finished) {
            Log.d("AstryxbookPiP", "fullscreen ended during PiP: applying focus mode")
            fullscreenEndedInPip = true
            pipNavigator.evaluateJavaScript(PIP_FOCUS_MODE_JS + PIP_KEEP_CHAIN_JS) {}
        }
    }

    // A pinch zoom carried into PiP (pinch to zoom on): the PiP window showed only the zoomed-in
    // part of the page, the video cropped in a corner with Facebook's labels around it (seen on a
    // Galaxy A56). In PiP the page is zoomed all the way out, which fits its whole width as a
    // PiP window normally does; checked on entering PiP and again after each return from the lock
    // screen (the hand-back), which can bring the zoom back. Leaving PiP puts the user's zoom back
    // once the window is full size again. Separate from focus mode, like the effects around it.
    var pipPinchRatio by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(isInPipMode, pipHandback) {
        val webView = runCatching { pipState.nativeWebView }.getOrNull() ?: return@LaunchedEffect
        if (isInPipMode) {
            if (isFullscreen || pipState.loadingState !is LoadingState.Finished) return@LaunchedEffect
            delay(PIP_PINCH_CHECK_DELAY_MS) // the window's resize first
            pipNavigator.evaluateJavaScript(PIP_PINCH_RATIO_JS) { raw ->
                val ratio = raw?.removeSurrounding("\"")?.toFloatOrNull() ?: return@evaluateJavaScript
                if (ratio <= PINCH_RATIO_MIN) return@evaluateJavaScript
                Log.d("AstryxbookPiP", "PiP shows the page zoomed x$ratio: zooming out")
                if (pipPinchRatio <= 1f) pipPinchRatio = minOf(ratio, PINCH_RATIO_MAX)
                repeat(MAX_ZOOM_OUT_STEPS) { if (!webView.zoomOut()) return@evaluateJavaScript }
            }
        } else if (pipPinchRatio > 1f) {
            val ratio = pipPinchRatio
            pipPinchRatio = 1f
            delay(PIP_EXIT_SETTLE_MS)
            webView.zoomBy(ratio)
        }
    }

    // Separate from focus mode on purpose (see PIP_PIN_SCREEN_JS): keeps
    // rotation during PiP from making Facebook drop the reel.
    LaunchedEffect(isInPipMode, pipState.loadingState) {
        if (pipState.loadingState is LoadingState.Finished) {
            pipNavigator.evaluateJavaScript(
                if (isInPipMode) PIP_PIN_SCREEN_JS else PIP_UNPIN_SCREEN_JS
            ) {}
        }
    }

    // Native rendering workaround, not a page/JS issue: confirmed live via
    // DevTools that the video element is correctly selected, actively
    // playing, and has real decoded frame data (readyState 4 / HAVE_ENOUGH_
    // DATA) at the exact moment the PiP window shows solid black with audio
    // still playing. Correct data, wrong pixels - that's WebView's
    // hardware-accelerated video compositor layer (see setLayerType
    // LAYER_TYPE_HARDWARE below) holding a stale/blank buffer across the
    // window resize the system performs when PiP engages, a known class of
    // Android bug for hardware-accelerated video surfaces. Discarding and
    // rebuilding the layer forces a fresh composite instead of reusing
    // whatever stale buffer survived the resize. The short delay gives
    // Android's own PiP enter/exit transition animation (roughly 200-300ms)
    // a moment to finish before we act, since forcing this mid-transition is
    // unlikely to stick.
    LaunchedEffect(isInPipMode) {
        delay(150)
        // Also runs at startup, when the WebView may not exist yet (it's created during
        // layout, which doesn't happen while the screen is off); nothing to rebuild then.
        val webView = runCatching { pipState.nativeWebView }.getOrNull() ?: return@LaunchedEffect
        webView.setLayerType(View.LAYER_TYPE_NONE, null)
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.invalidate()
    }

    // Second, complementary attempt at the same black-pip bug (see
    // PIP_NUDGE_COMPOSITOR_JS's comment) - runs after the native invalidate
    // above (same 150ms settle delay, then immediately follows it) since we
    // don't have certainty about which layer is actually responsible; no real
    // cost to running both.
    LaunchedEffect(isInPipMode) {
        if (isInPipMode) {
            delay(150)
            pipNavigator.evaluateJavaScript(PIP_NUDGE_COMPOSITOR_JS) {}
        }
    }

    // Lock-screen audio handoff/handback, driven from MainActivity (see
    // LockScreenAudioService). Confirmed on device that these effects still
    // run right after screen-off (the read answered within ~30ms).
    val pipLockscreenAudio by settingsVM.pipLockscreenAudio.collectAsState()
    // Only once the page has loaded, like the focus-mode effect above: the
    // navigator replays just its last event to a WebView that isn't attached
    // yet, so an unconditional evaluate at startup would replace the initial
    // loadUrl and the page would never load.
    LaunchedEffect(isInPipMode, pipLockscreenAudio, pipState.loadingState) {
        if (pipState.loadingState is LoadingState.Finished) {
            pipNavigator.evaluateJavaScript(
                if (isInPipMode && pipLockscreenAudio) PIP_HANDOFF_ARM_JS else PIP_HANDOFF_DISARM_JS
            ) {}
        }
    }
    val currentOnPipHandoffRead by rememberUpdatedState(onPipHandoffRead)
    LaunchedEffect(pipHandoffReadTrigger) {
        if (pipHandoffReadTrigger > 0) {
            pipNavigator.evaluateJavaScript(PIP_HANDOFF_READ_JS) { currentOnPipHandoffRead(it) }
        }
    }
    LaunchedEffect(pipHandback) {
        pipHandback?.let { request ->
            pipNavigator.evaluateJavaScript(
                pipHandbackJs(request.positionMs, request.play, request.rearm)
            ) {
                Log.d("AstryxbookPiP", "handback JS: $request -> $it")
            }
        }
    }

    // allow exiting while scrolling to top.
    var exitScroll by remember { mutableStateOf(false) }
    // Leaving the app from the feed sends it to the background instead of finishing it, like
    // Home does: finishing dropped the WebView, so coming back reloaded the feed from the top.
    val leaveApp = { activity?.moveTaskToBack(true) }
    BackHandler {
        if (exitScroll) {
            leaveApp()
        } else {
            navigator.evaluateJavaScript("backHandlerNB();") {
                val backHandled = it.removeSurrounding("\"")
                when (backHandled) {
                    "false" -> {
                        // Asked of the WebView itself, as in the Messages layer: Facebook opens
                        // some pages in-page (the composer a share opens), which
                        // navigator.canGoBack doesn't always pick up, so Back left the app.
                        val canGoBack = runCatching { state.nativeWebView.canGoBack() }
                            .getOrDefault(navigator.canGoBack)
                        if (canGoBack) {
                            navigator.navigateBack()
                        } else {
                            leaveApp()
                        }
                    }
                    "exit" -> leaveApp()
                    "scrolling" -> exitScroll = true
                }
            }
        }
    }

    LaunchedEffect(exitScroll) {
        if (exitScroll) {
            delay(800)
            exitScroll = false
        }
    }

    var isLoading by rememberSaveable { mutableStateOf(true) }
    val isError = state.errorsForCurrentRequest.lastOrNull()?.isFromMainFrame == true

    val viewModel: MainViewModel = viewModel {
        MainViewModel(
            resources = resources,
            settings = settingsVM
        )
    }

    val themeColor by viewModel.themeColor
    var settingsToggle by rememberSaveable { mutableStateOf(false) }
    // The Messages layer's own page colour (its theme-color, e.g. the desktop site's dark mode)
    // while it's open, so the system bars match the page on screen; the main page's otherwise.
    var layerThemeColor by remember(messagesLayerUrl) { mutableStateOf<Color?>(null) }
    val barColor = layerThemeColor?.takeIf { messagesLayerUrl != null && !settingsToggle } ?: themeColor
    // Manual handling to fix visual & padding bug on settings dialog.
    var isImmersiveMode by rememberSaveable { mutableStateOf(settingsVM.immersiveMode.value) }

    fun setWindow(immersive: Boolean) {
        val window = activity?.window ?: return
        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)

        if (immersive) {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            val isLight = ColorUtils.calculateLuminance(barColor.toArgb()) > 0.5
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.isAppearanceLightStatusBars = isLight
            windowInsetsController.isAppearanceLightNavigationBars = isLight
        }
        isImmersiveMode = immersive
    }

    // A system light/dark switch doesn't recreate the activity (configChanges), but it resets
    // the bars to the theme's icon colours, which left white icons on Facebook's white page.
    val uiMode = LocalConfiguration.current.uiMode
    LaunchedEffect(isImmersiveMode, barColor.value, uiMode) {
        setWindow(isImmersiveMode)
    }

    // HTML5 fullscreen video: the custom view goes into a black overlay on
    // the window's decor view, above the Compose content. Rotation doesn't
    // recreate the activity (configChanges), so the overlay just resizes.
    // On phones the app is otherwise held in portrait (see appOrientation);
    // fullscreen lifts that so the video can follow auto-rotate, and leaving
    // fullscreen puts it back, so Facebook lays its viewer out in portrait.
    val fullscreen = remember(activity) {
        var overlay: FrameLayout? = null
        FullscreenController(object : FullscreenController.Host {
            override fun attach(view: View) {
                val window = activity?.window ?: return
                val container = FrameLayout(activity).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                }
                (window.decorView as FrameLayout).addView(
                    container, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                )
                overlay = container
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
                isFullscreen = true
                activity.requestedOrientation =
                    appOrientation(activity.resources.configuration.smallestScreenWidthDp, isFullscreen = true)
                Log.d("AstryxbookPiP", "HTML5 fullscreen: shown")
            }

            override fun detach(view: View) {
                overlay?.let { container ->
                    container.removeView(view)
                    (container.parent as? ViewGroup)?.removeView(container)
                }
                overlay = null
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                setWindow(settingsVM.immersiveMode.value)
                isFullscreen = false
                activity?.let {
                    it.requestedOrientation = appOrientation(
                        it.resources.configuration.smallestScreenWidthDp,
                        isFullscreen = false,
                        isMessagesLayerOpen = messagesLayerUrl != null
                    )
                }
                Log.d("AstryxbookPiP", "HTML5 fullscreen: hidden")
            }
        })
    }

    // WebView/activity going away while fullscreen: detach and tell WebView.
    DisposableEffect(fullscreen) {
        onDispose { fullscreen.hide() }
    }

    // Declared after the page-level BackHandler above so it takes precedence.
    BackHandler(enabled = isFullscreen) {
        fullscreen.hide()
    }

    val userScripts by viewModel.scripts
    val loadingState = state.loadingState

    val pipEnabled by settingsVM.pipEnabled.collectAsState()

    // Evaluated on its own rather than as part of userScripts (see
    // MainViewModel.kt's loadScripts for why). Confirmed via field reports:
    // when it waited on the other scripts (then fetched over the network), the
    // very first PiP attempt after a fresh install or app-cache clear did not
    // trigger PiP at all, since PipBridge never reported isVideoPlaying in time.
    LaunchedEffect(loadingState, pipEnabled) {
        if (loadingState is LoadingState.Finished && pipEnabled) {
            val detectorScript = context.resources.openRawResource(R.raw.pip_video_detector)
                .bufferedReader().use { it.readText() }
            navigator.evaluateJavaScript(detectorScript) {}
        }
    }

    // Keep screen on (setting). "While a video plays" listens to video_playing.js in both
    // WebViews (the Messages layer reports its own); the view flag only applies while the
    // app is on screen, and doesn't touch the window flag the fullscreen host sets.
    val keepScreenOnMode by settingsVM.keepScreenOn.collectAsState()
    val trackVideoPlaying = keepScreenOnMode == KeepScreenOn.WHILE_VIDEO
    var mainVideoPlaying by remember { mutableStateOf(false) }
    var layerVideoPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(loadingState, trackVideoPlaying) {
        if (loadingState is LoadingState.Loading) mainVideoPlaying = false
        if (loadingState is LoadingState.Finished && trackVideoPlaying) {
            val script = resources.openRawResource(R.raw.video_playing)
                .bufferedReader().use { it.readText() }
            navigator.evaluateJavaScript(script) {}
        }
    }
    val hostView = LocalView.current
    val keepScreenOn = shouldKeepScreenOn(keepScreenOnMode, mainVideoPlaying || layerVideoPlaying)
    DisposableEffect(hostView, keepScreenOn) {
        hostView.keepScreenOn = keepScreenOn
        onDispose { hostView.keepScreenOn = false }
    }

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

    // The Messages tab hook, whenever Messages in desktop mode is on. userScripts only picks up
    // settings on "Apply immediately?" or a restart; without the hook the tab still reaches the
    // layer through its fb-messenger:// link, but Facebook then leaves its "Get the Messenger
    // app" page on the page underneath. Bundled like the PiP detector; the script guards
    // against running twice, so the copy in userScripts is harmless.
    LaunchedEffect(loadingState, messagesDesktopSetting) {
        if (loadingState is LoadingState.Finished && messagesDesktopSetting) {
            val tabHook = resources.openRawResource(R.raw.messages_tab)
                .bufferedReader().use { it.readText() }
            navigator.evaluateJavaScript(tabHook) {}
        }
    }

    // "Start on Feeds" on a WebView without document-start scripts (installOpenFeedsScript): the
    // script runs once the start page has loaded, so the menu shows for a moment. Facebook may
    // have dropped the #astryx-feeds mark from the URL by then; __astryxFeedsForce stands in.
    var openFeedsPending by remember { mutableStateOf(url == FEEDS_PAGE_URL) }
    var openFeedsAtDocumentStart by remember { mutableStateOf(true) }
    LaunchedEffect(loadingState) {
        if (loadingState is LoadingState.Finished && openFeedsPending) {
            openFeedsPending = false
            if (!openFeedsAtDocumentStart) {
                val openFeeds = resources.openRawResource(R.raw.open_feeds)
                    .bufferedReader().use { it.readText() }
                navigator.evaluateJavaScript("window.__astryxFeedsForce = true;\n$openFeeds") {}
            }
        }
    }

    LaunchedEffect(loadingState, userScripts) {
        if (loadingState is LoadingState.Finished) {
            userScripts?.let { scripts ->
                navigator.evaluateJavaScript(scripts) {
                    isLoading = false
                    // Correlates against PipBridge/PiP logcat timestamps to confirm
                    // or rule out a cold-start race: userScripts may not finish
                    // evaluating before the user tries PiP.
                    Log.d("AstryxbookPiP", "userScripts finished evaluating")
                }
            }
        }
    }

    if (isError && isLoading) {
        NetworkErrorDialog { activity?.finish() }
        return
    }

    val colorScheme = MaterialTheme.colorScheme
    val originalColor = remember { mutableStateOf(themeColor) }

    if (settingsToggle) {
        setWindow(false)
        SettingsDialog(
            onDismiss = {
                setWindow(settingsVM.immersiveMode.value)
                viewModel.setThemeColor(originalColor.value)
                settingsToggle = false
            },
            onReload = {
                isLoading = true
                viewModel.setThemeColor(Color.Transparent)
                setWindow(settingsVM.immersiveMode.value)
                viewModel.refresh(
                    resources = resources,
                    settings = settingsVM
                )
                navigator.reload()
            },
            onLogOut = {
                messagesLayerUrl = null
                clearWebData(context, runCatching { state.nativeWebView }.getOrNull()) {
                    navigator.loadUrl(HOME_URL)
                }
            },
            onSharePage = {
                // The page on screen (the Messages layer's while it's open); the WebView's own
                // URL follows Facebook's in-page navigation, lastLoadedUrl may not.
                val pageUrl = runCatching { pipState.nativeWebView.url }.getOrNull()
                    ?: pipState.lastLoadedUrl
                if (!pageUrl.isNullOrEmpty()) {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, shareablePageUrl(pageUrl))
                    runCatching {
                        context.startActivity(Intent.createChooser(send, resources.getString(R.string.share_page)))
                    }
                }
            }
        )
    }

    // App lock covers the Compose content only: leave what sits above it (a fullscreen
    // video on the decor view, the Settings dialog's own window) when it locks.
    LaunchedEffect(isAppLocked) {
        if (isAppLocked) {
            fullscreen.hide()
            if (settingsToggle) {
                setWindow(settingsVM.immersiveMode.value)
                viewModel.setThemeColor(originalColor.value)
                settingsToggle = false
            }
        }
    }

    LaunchedEffect(settingsToggle) {
        if (settingsToggle) {
            originalColor.value = themeColor
            viewModel.setThemeColor(colorScheme.background)
        }
    }

    if (isLoading) {
        SplashLoading(
            if (loadingState is LoadingState.Loading) {
                loadingState.progress
            } else {
                0.8F
            }
        )
    }


    LaunchedEffect(isEffectiveDesktop) {
        val userAgent = if (isEffectiveDesktop) DESKTOP_USER_AGENT else ""
        // The WebView is created during layout, so on a cold start this can run first:
        // the library applies webSettings when it creates it, and a live WebView is
        // updated directly.
        state.webSettings.customUserAgentString = userAgent.ifEmpty { null }
        runCatching { state.nativeWebView }.getOrNull()?.settings?.userAgentString = userAgent
    }

    // While the Messages layer is open the phone may rotate (see appOrientation), and the
    // page underneath keeps running. On open: pause its videos, so a playing reel doesn't go
    // on behind the chat, and note its scroll position. On close: once the screen is back in
    // portrait (a landscape layout reflows the feed and moves it), scroll it back there.
    val isMessagesLayerOpen = messagesLayerUrl != null
    LaunchedEffect(isMessagesLayerOpen) {
        // While fullscreen, the fullscreen host owns the orientation (and resets it on exit).
        if (!isFullscreen) {
            activity?.let {
                it.requestedOrientation = appOrientation(
                    it.resources.configuration.smallestScreenWidthDp,
                    isFullscreen = false,
                    isMessagesLayerOpen = isMessagesLayerOpen
                )
            }
        }
    }
    LaunchedEffect(isMessagesLayerOpen) {
        if (state.loadingState !is LoadingState.Finished) return@LaunchedEffect
        if (isMessagesLayerOpen) {
            navigator.evaluateJavaScript(
                "document.querySelectorAll('video').forEach(function(v) { v.pause(); });" +
                    "window.__mbLayerScrollY = window.scrollY;"
            ) {}
        } else {
            val restore = "if (typeof window.__mbLayerScrollY === 'number' && " +
                "Math.abs(window.scrollY - window.__mbLayerScrollY) > 4) " +
                "window.scrollTo(0, window.__mbLayerScrollY);"
            // The rotation back and Facebook's reflow take a moment; try twice, then forget it.
            delay(400)
            navigator.evaluateJavaScript(restore) {}
            delay(600)
            navigator.evaluateJavaScript("$restore window.__mbLayerScrollY = undefined;") {}
        }
    }

    // needed to consume extra padding when keyboard is open
    val barsInsets = WindowInsets.systemBars.asPaddingValues()
    val imeHeight = rememberImeHeight()

    val primaryColor = colorScheme.primary.toArgb()
    val onPrimaryColor  = colorScheme.onPrimary.toArgb()
    val currentPrimaryColor by rememberUpdatedState(primaryColor)
    val currentOnPrimaryColor by rememberUpdatedState(onPrimaryColor)

    val pageModifier = Modifier
        .fillMaxSize()
        .background(barColor)
        .then(
            if (isImmersiveMode) {
                Modifier.padding(bottom = imeHeight)
            } else {
                Modifier.padding(
                    top = barsInsets.calculateTopPadding(),
                    bottom = maxOf(barsInsets.calculateBottomPadding(), imeHeight)
                )
            }
        )

    // A lost renderer (see RendererGoneClient) leaves the WebView unusable: a new one takes its
    // place (the key below) with the same onCreated setup, and once it exists the navigator
    // reopens the page that was showing. The navigator replays only its last event to a new
    // WebView (see CLAUDE.md), so the load is sent after the WebView is there, not with the loss.
    var webViewGeneration by remember { mutableIntStateOf(0) }
    var pendingRecoveryUrl by remember { mutableStateOf<String?>(null) }
    var replacementWebView by remember { mutableStateOf<android.webkit.WebView?>(null) }
    var lastRendererLossAt by remember { mutableStateOf<Long?>(null) }
    val deadWebViews = remember { mutableSetOf<android.webkit.WebView>() }
    LaunchedEffect(replacementWebView) {
        if (replacementWebView == null) return@LaunchedEffect
        val recoveryUrl = pendingRecoveryUrl ?: return@LaunchedEffect
        pendingRecoveryUrl = null
        navigator.loadUrl(recoveryUrl)
    }
    // A WebView that takes over in PiP starts out at the PiP window's size, and leaving PiP can
    // leave its page zoomed in to that width (about 3x on a phone, seen with pinch to zoom on).
    // Its page was loaded in PiP, so there's no zoom of the user's to keep: zoom it all the way
    // out once the window is full size again.
    var replacedInPip by remember { mutableStateOf(false) }
    LaunchedEffect(isInPipMode) {
        if (isInPipMode || !replacedInPip) return@LaunchedEffect
        replacedInPip = false
        delay(PIP_EXIT_SETTLE_MS)
        val webView = runCatching { state.nativeWebView }.getOrNull() ?: return@LaunchedEffect
        repeat(MAX_ZOOM_OUT_STEPS) { if (!webView.zoomOut()) return@LaunchedEffect }
    }

    key(webViewGeneration) {
        WebView(
            modifier = pageModifier,
            state = state,
            navigator = navigator,
            platformWebViewParams = appWebViewParams(fullscreen, onRendererGone = { dead ->
                val now = SystemClock.elapsedRealtime()
                pendingRecoveryUrl = rendererRecoveryUrl(
                    deadWebViewUrl(dead) ?: state.lastLoadedUrl, HOME_URL, lastRendererLossAt, now
                )
                lastRendererLossAt = now
                if (isInPipMode) replacedInPip = true
                // Its videos are gone with it; the reloaded page reports its own.
                mainVideoPlaying = false
                onVideoPlayingChanged(false, 0, 0)
                replacementWebView = null
                deadWebViews += dead
                webViewGeneration++
            }),
            captureBackPresses = false,
            // Only a WebView whose renderer is gone is destroyed here, once it's out of the layout.
            onDispose = { webView -> if (deadWebViews.remove(webView)) webView.destroy() },
            onCreated = { webView ->
                hardenWebView(state, webView)
                pageZoomPercent = textZoomPercent
                pageZoomByScript = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
                pageZoomScript = installPageZoomScript(webView, textZoomPercent)
                state.webSettings.androidWebSettings.textZoom = if (pageZoomByScript) 100 else textZoomPercent
                // A zoom below 100% lays the page out wider than the screen, which WebView allows
                // only with a wide viewport.
                state.webSettings.androidWebSettings.useWideViewPort = pageZoomByScript && textZoomPercent < 100

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                // Facebook and Messenger are first-party; refusing other sites' cookies in embeds
                // (ads, link previews) keeps them from tracking across pages.
                cookieManager.setAcceptThirdPartyCookies(webView, false)
                cookieManager.flush()

                state.webSettings.apply {
                    isJavaScriptEnabled = true

                    androidWebSettings.apply {
                        //isDebugInspectorInfoEnabled = true
                        domStorageEnabled = true
                        hideDefaultVideoPoster = true
                        mediaPlaybackRequiresUserGesture = false
                    }
                }

                webView.apply {
                    // Page -> app calls, downloads and copy to clipboard: Facebook's own main
                    // frame only.
                    installPageBridges(
                        this,
                        PageBridges(
                            settings = MaterialbookSettings { settingsToggle = true },
                            theme = ThemeChange { if (!settingsToggle) viewModel.setThemeColor(Color(it)) },
                            pip = PipBridge(onVideoPlayingChanged, onPipPageVisible),
                            screen = ScreenBridge { mainVideoPlaying = it },
                            layer = LayerBridge { pageUrl ->
                                if (isStoryCreatePage(pageUrl)) messagesLayerUrl = pageUrl
                            },
                            share = { link ->
                                val send = Intent(Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, link)
                                runCatching {
                                    context.startActivity(Intent.createChooser(send, resources.getString(R.string.share_via)))
                                }
                            },
                            photoPost = { fbid, reply -> lookUpPhotoPost(fbid, reply) },
                        )
                    )
                    installDownloadBridge(this, context)
                    installClipboardBridge(this, context)
                    installShareMediaBridge(this, context)
                    addJavascriptInterface(
                        MaterialYouBridge({ currentPrimaryColor }, { currentOnPrimaryColor }),
                        "MaterialYouBridge"
                    )
                    installPipVisibilityScript(this)
                    openFeedsAtDocumentStart = installOpenFeedsScript(this)

                    setLayerType(View.LAYER_TYPE_HARDWARE, null)

                    overScrollMode = View.OVER_SCROLL_NEVER
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false

                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                }
                if (pendingRecoveryUrl != null) replacementWebView = webView
            }
        )
    }

    var layerGeneration by remember { mutableIntStateOf(0) }
    var lastLayerRendererLossAt by remember { mutableStateOf<Long?>(null) }

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
            messagesLayerUrl = null
            pageShareTarget = request.target
            pageShareLoadSeen = false
            navigator.loadUrl(pageUrl)
        } else {
            messagesLayerUrl = if (request.target == ShareTarget.REEL) REEL_CREATE_URL else STORY_CREATE_URL
            layerGeneration++
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
    messagesLayerUrl?.let { layerUrl ->
        // A new Messages link while the layer is open (a notification, say) recreates it, and
        // so does a lost renderer (onRendererGone), at the page it was showing.
        key(layerUrl, layerGeneration) {
            MessagesLayer(
                url = layerUrl,
                userScripts = userScripts,
                fullscreen = fullscreen,
                isFullscreen = isFullscreen,
                modifier = pageModifier,
                background = barColor,
                primaryColor = primaryColor,
                onPrimaryColor = onPrimaryColor,
                onClose = {
                    // Closing the layer gives up the stories (or the reel) still waiting to be offered.
                    if (ShareMediaQueue.pending(ShareTarget.STORY) || ShareMediaQueue.pending(ShareTarget.REEL)) {
                        ShareMediaQueue.clear()
                    }
                    messagesLayerUrl = null
                },
                pipEnabled = pipEnabled,
                onPipTarget = { layerPipTarget = it },
                onVideoPlayingChanged = onVideoPlayingChanged,
                onPipPageVisible = onPipPageVisible,
                trackVideoPlaying = trackVideoPlaying,
                textZoom = textZoomPercent,
                onScreenVideoPlayingChanged = { layerVideoPlaying = it },
                onLeave = leave@{ mainUrl ->
                    // A story was shared and Facebook moved on (to its feed): the next shared
                    // file gets the story creator again instead.
                    if (ShareMediaQueue.pending(ShareTarget.STORY)) {
                        messagesLayerUrl = STORY_CREATE_URL
                        layerGeneration++
                        return@leave
                    }
                    // Both the interceptor and the in-page watcher can report the same exit.
                    if (messagesLayerUrl != null) {
                        messagesLayerUrl = null
                        mainUrl?.let { navigator.loadUrl(it) }
                    }
                },
                onOpenSettings = { settingsToggle = true },
                onThemeColor = { layerThemeColor = it },
                onRendererGone = { pageUrl ->
                    val now = SystemClock.elapsedRealtime()
                    messagesLayerUrl = rendererRecoveryUrl(pageUrl, layerUrl, lastLayerRendererLossAt, now)
                    lastLayerRendererLossAt = now
                    layerGeneration++
                },
                onExternalUrl = { externalUrl -> openExternalUrl(fbRedirectSanitizer(externalUrl)) }
            )
        }
    }

    // Saved downloads, with an Open action (DownloadBridge shows a toast when this isn't on screen).
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        DownloadEvents.saved.collect { download ->
            val result = snackbarHostState.showSnackbar(
                message = resources.getString(
                    if (download.alreadySaved) R.string.download_already_saved else R.string.saved_to_downloads
                ),
                actionLabel = resources.getString(R.string.download_open),
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                val view = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(download.uri, download.mimeType)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching { context.startActivity(view) }.onFailure { showNotSupported() }
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(barsInsets),
        contentAlignment = Alignment.BottomCenter
    ) {
        SnackbarHost(snackbarHostState)
    }
}
