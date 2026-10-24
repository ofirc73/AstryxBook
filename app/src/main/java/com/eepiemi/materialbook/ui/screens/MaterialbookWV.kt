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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewFeature
import com.eepiemi.materialbook.R
import com.eepiemi.materialbook.audio.PipHandback
import com.eepiemi.materialbook.ui.components.ExternalLinkDialog
import com.eepiemi.materialbook.ui.components.MessengerLinkDialog
import com.eepiemi.materialbook.ui.components.NetworkErrorDialog
import com.eepiemi.materialbook.ui.components.settings.SettingsDialog
import com.eepiemi.materialbook.ui.viewmodel.MainViewModel
import com.eepiemi.materialbook.ui.viewmodel.SettingsViewModel
import com.eepiemi.materialbook.utils.DESKTOP_USER_AGENT
import com.eepiemi.materialbook.utils.ExternalLinkAction
import com.eepiemi.materialbook.utils.ExternalRequestInterceptor
import com.eepiemi.materialbook.utils.FEEDS_PAGE_URL
import com.eepiemi.materialbook.utils.FullscreenController
import com.eepiemi.materialbook.utils.HOME_PAGE_URL
import com.eepiemi.materialbook.utils.KeepScreenOn
import com.eepiemi.materialbook.utils.TextZoom
import com.eepiemi.materialbook.utils.appOrientation
import com.eepiemi.materialbook.utils.PageStarts
import com.eepiemi.materialbook.utils.appWebViewParams
import com.eepiemi.materialbook.utils.clearWebData
import com.eepiemi.materialbook.utils.deadWebViewUrl
import com.eepiemi.materialbook.utils.effectiveDesktop
import com.eepiemi.materialbook.utils.externalLinkAction
import com.eepiemi.materialbook.utils.fbRedirectSanitizer
import com.eepiemi.materialbook.utils.hardenWebView
import com.eepiemi.materialbook.utils.intentFallbackUrl
import com.eepiemi.materialbook.utils.isMessagesLink
import com.eepiemi.materialbook.utils.isMessengerAppLink
import com.eepiemi.materialbook.utils.isMobileReelPage
import com.eepiemi.materialbook.utils.isStoryCreatePage
import com.eepiemi.materialbook.utils.isWebUrl
import com.eepiemi.materialbook.utils.jsBridge.DownloadEvents
import com.eepiemi.materialbook.utils.jsBridge.LayerBridge
import com.eepiemi.materialbook.utils.jsBridge.MaterialYouBridge
import com.eepiemi.materialbook.utils.jsBridge.MaterialbookSettings
import com.eepiemi.materialbook.utils.jsBridge.PageBridges
import com.eepiemi.materialbook.utils.jsBridge.PipBridge
import com.eepiemi.materialbook.utils.jsBridge.ScreenBridge
import com.eepiemi.materialbook.utils.jsBridge.ThemeChange
import com.eepiemi.materialbook.utils.jsBridge.installClipboardBridge
import com.eepiemi.materialbook.utils.jsBridge.installDownloadBridge
import com.eepiemi.materialbook.utils.jsBridge.installPageBridges
import com.eepiemi.materialbook.utils.jsBridge.installShareMediaBridge
import com.eepiemi.materialbook.utils.lookUpPhotoPost
import com.eepiemi.materialbook.utils.messagesDesktopUrl
import com.eepiemi.materialbook.utils.openInCustomTab
import com.eepiemi.materialbook.utils.rememberAutoDesktop
import com.eepiemi.materialbook.utils.rememberImeHeight
import com.eepiemi.materialbook.utils.rendererRecoveryUrl
import com.eepiemi.materialbook.utils.shareablePageUrl
import com.eepiemi.materialbook.utils.shouldKeepScreenOn
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberSaveableWebViewState
import com.multiplatform.webview.web.rememberWebViewNavigator
import kotlinx.coroutines.delay

private const val HOME_URL = HOME_PAGE_URL

// The main view's videos while the Messages layer is over it: paused, and paused again whenever
// they start (Facebook's reel player starts a paused reel again, so a reel went on playing, with
// its sound, under the chat; seen on a Galaxy A56). The guard goes when the layer closes or hides.
private const val PAUSE_UNDER_LAYER_JS = "(function () {" +
    " document.querySelectorAll('video').forEach(function (v) { v.pause(); });" +
    " if (window.__astryxPauseUnderLayer) return;" +
    " window.__astryxPauseUnderLayer = function (e) { if (e.target && e.target.pause) e.target.pause(); };" +
    " document.addEventListener('play', window.__astryxPauseUnderLayer, true);" +
    " })();"
private const val RESUME_UNDER_LAYER_JS = "(function () {" +
    " if (!window.__astryxPauseUnderLayer) return;" +
    " document.removeEventListener('play', window.__astryxPauseUnderLayer, true);" +
    " window.__astryxPauseUnderLayer = null;" +
    " })();"

// A reel opened from a chat: the longest the chat stays on screen while the reel loads.
private const val LAYER_CLOSE_WAIT_MS = 8000L
// Longest the loading screen covers the page for the relayout after an empty PiP window.
private const val RELAYOUT_COVER_MAX_MS = 8000L

/** A link the running app was sent (onNewIntent); [seq] tells two taps on the same link apart. */
data class OpenLinkRequest(val seq: Int, val url: String)

@Composable
fun MaterialbookWebView(
    url: String,
    settingsVM: SettingsViewModel = viewModel(),
    pipToggleTrigger: Int = 0,
    pipEnteringTrigger: Int = 0,
    isInPipMode: Boolean = false,
    pipStartedWhilePlaying: Boolean = false,
    pipHandoffReadTrigger: Int = 0,
    relayoutTrigger: Int = 0,
    relayoutPending: Boolean = false,
    pipHandback: PipHandback? = null,
    openLinkRequest: OpenLinkRequest? = null,
    shareMediaRequest: ShareMediaRequest? = null,
    onPipHandoffRead: (String?) -> Unit = {},
    onPipPageVisible: () -> Unit = {},
    onPipVideoGone: () -> Unit = {},
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
    val messagesLayer = rememberMessagesLayerState()
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
                messagesLayer.open(messagesDesktopUrl(link))
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
                    messagesLayer.open(messagesDesktopUrl(messagesUrl))
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
                if (whileRunning) messagesLayer.close()
                navigator.loadUrl(link)
            }
            currentMessagesDesktopSetting && !currentIsEffectiveDesktop -> {
                messagesLayer.open(messagesDesktopUrl(link))
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

    // After the app closed an empty PiP window (MainActivity.closeEmptyPip): Facebook had loaded
    // pages while the app was in PiP and kept laying them out for the PiP window's width, a third
    // of the screen, even after reloads (seen on a Galaxy A56); only a fresh app start helped.
    // m.facebook.com's client sends the device's details (screen size included) once per session
    // and notes when in sessionStorage (RELAYOUT_PAGE_JS); without that note it sends them again.
    // Once the window has its full size again, the note goes and the page is reloaded, with the
    // viewport cookie set ahead as above.
    LaunchedEffect(relayoutTrigger) {
        if (relayoutTrigger == 0) return@LaunchedEffect
        delay(PIP_EXIT_SETTLE_MS)
        val webView = runCatching { state.nativeWebView }.getOrNull() ?: return@LaunchedEffect
        if (webView.width > 0 && webView.height > 0) {
            CookieManager.getInstance().setCookie(
                "https://m.facebook.com",
                TextZoom.viewportCookie(
                    webView.width, webView.height, resources.displayMetrics.density, textZoomPercent
                )
            )
        }
        Log.d("AstryxbookPiP", "back from an empty PiP window: reloading the page for the full window")
        navigator.evaluateJavaScript(RELAYOUT_PAGE_JS) { navigator.reload() }
    }

    // PiP follows the page on screen: the Messages layer's while it's open (it reports its
    // own videos through PipBridge too), else this view's. Everything below acts on that
    // page; the PiP scripts themselves are the same for both.
    var layerPipTarget by remember { mutableStateOf<PipTarget?>(null) }
    val mainPageStarts = remember { PageStarts() }
    // Not the layer kept out of sight behind a reel (MessagesLayerState.hidden).
    val shownLayerPipTarget = layerPipTarget?.takeUnless { messagesLayer.hidden }
    val pipNavigator = shownLayerPipTarget?.navigator ?: navigator
    val pipState = shownLayerPipTarget?.state ?: state
    val pipPageStarts = (shownLayerPipTarget?.pageStarts ?: mainPageStarts).count

    // Set by FullscreenController's host below while an HTML5 fullscreen
    // custom view is showing.
    var isFullscreen by remember { mutableStateOf(false) }

    val pipLockscreenAudio by settingsVM.pipLockscreenAudio.collectAsState()
    PipEffects(
        pipNavigator = pipNavigator,
        pipState = pipState,
        pipPageStarts = pipPageStarts,
        isInPipMode = isInPipMode,
        isFullscreen = isFullscreen,
        pipToggleTrigger = pipToggleTrigger,
        pipEnteringTrigger = pipEnteringTrigger,
        pipStartedWhilePlaying = pipStartedWhilePlaying,
        pipLockscreenAudio = pipLockscreenAudio,
        pipHandoffReadTrigger = pipHandoffReadTrigger,
        onPipHandoffRead = onPipHandoffRead,
        pipHandback = pipHandback,
        onPipVideoGone = onPipVideoGone,
    )

    // allow exiting while scrolling to top.
    var exitScroll by remember { mutableStateOf(false) }
    // The main view's page starts when it loaded anew the page a chat's reel had replaced (see
    // the history clean-up below); null otherwise.
    var dropMainHistoryAfter by remember { mutableStateOf<Int?>(null) }
    // Leaving the app from the feed sends it to the background instead of finishing it, like
    // Home does: finishing dropped the WebView, so coming back reloaded the feed from the top.
    val leaveApp = { activity?.moveTaskToBack(true) }
    BackHandler {
        val mainBeforeReel = messagesLayer.mainBeforeReel
        val mainIndex = runCatching { state.nativeWebView.copyBackForwardList().currentIndex }.getOrNull()
        if (messagesLayer.backToChat(runCatching { state.nativeWebView.url }.getOrNull(), mainIndex)) {
            // Back on a reel opened from a chat: the chat again, with the main view back on the page
            // it showed before the reel (under the chat). When the reel took that page's place in
            // the main view's history (it loaded before that page was kept), that page anew.
            mainBeforeReel?.let { place ->
                runCatching {
                    val webView = state.nativeWebView
                    val steps = place.index - webView.copyBackForwardList().currentIndex
                    if (steps < 0 && webView.canGoBackOrForward(steps)) {
                        webView.goBackOrForward(steps)
                    } else if (place.url != null && place.url != webView.url) {
                        dropMainHistoryAfter = mainPageStarts.count
                        navigator.loadUrl(place.url)
                    }
                }
            }
        } else if (exitScroll) {
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
    var layerThemeColor by remember(messagesLayer.url) { mutableStateOf<Color?>(null) }
    val barColor = layerThemeColor?.takeIf { messagesLayer.isShown && !settingsToggle } ?: themeColor
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
                        isMessagesLayerOpen = messagesLayer.isShown
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
    // Next to loadingState in the effects below: see PageStarts.
    val pageStarts = mainPageStarts.count

    val pipEnabled by settingsVM.pipEnabled.collectAsState()

    // Evaluated on its own rather than as part of userScripts (see
    // MainViewModel.kt's loadScripts for why). Confirmed via field reports:
    // when it waited on the other scripts (then fetched over the network), the
    // very first PiP attempt after a fresh install or app-cache clear did not
    // trigger PiP at all, since PipBridge never reported isVideoPlaying in time.
    LaunchedEffect(loadingState, pageStarts, pipEnabled) {
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
    LaunchedEffect(loadingState, pageStarts, trackVideoPlaying) {
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

    AppLeaveEffects(settingsVM = settingsVM, state = state)

    // The Messages tab hook, whenever Messages in desktop mode is on. userScripts only picks up
    // settings on "Apply immediately?" or a restart; without the hook the tab still reaches the
    // layer through its fb-messenger:// link, but Facebook then leaves its "Get the Messenger
    // app" page on the page underneath. Bundled like the PiP detector; the script guards
    // against running twice, so the copy in userScripts is harmless.
    LaunchedEffect(loadingState, pageStarts, messagesDesktopSetting) {
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
    LaunchedEffect(loadingState, pageStarts) {
        if (loadingState is LoadingState.Finished && openFeedsPending) {
            openFeedsPending = false
            if (!openFeedsAtDocumentStart) {
                val openFeeds = resources.openRawResource(R.raw.open_feeds)
                    .bufferedReader().use { it.readText() }
                navigator.evaluateJavaScript("window.__astryxFeedsForce = true;\n$openFeeds") {}
            }
        }
    }

    LaunchedEffect(loadingState, pageStarts, userScripts) {
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
                messagesLayer.close()
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

    // Back from an empty PiP window, the page is laid out for the PiP window until the relayout
    // reload (relayoutTrigger): the loading screen covers it from the moment the window closes,
    // and goes when the reloaded page's scripts have run, as on a start. Never left up for long.
    LaunchedEffect(relayoutPending) {
        if (relayoutPending) isLoading = true
    }
    LaunchedEffect(relayoutTrigger) {
        if (relayoutTrigger == 0) return@LaunchedEffect
        delay(RELAYOUT_COVER_MAX_MS)
        isLoading = false
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
    val isMessagesLayerOpen = messagesLayer.isShown
    LaunchedEffect(messagesLayer.closeOnReel, state.loadingState, mainPageStarts.count) {
        if (!messagesLayer.closeOnReel) return@LaunchedEffect
        // The reel's page, or the post's (shown at Home's address: the main view has moved on).
        val before = messagesLayer.mainBeforeReel?.index
        val onReel = state.loadingState is LoadingState.Finished && runCatching {
            val webView = state.nativeWebView
            isMobileReelPage(webView.url) || (before != null && webView.copyBackForwardList().currentIndex > before)
        }.getOrDefault(false)
        if (!onReel) delay(LAYER_CLOSE_WAIT_MS)
        messagesLayer.reelShown()
    }
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
    // The page loaded anew under the chat (the reel had taken its place in the main view's
    // history): once it has loaded, the reel behind it leaves the history too. Otherwise Back,
    // once Messages closed, showed the reel again.
    LaunchedEffect(dropMainHistoryAfter, mainPageStarts.count, state.loadingState) {
        val after = dropMainHistoryAfter ?: return@LaunchedEffect
        if (mainPageStarts.count <= after || state.loadingState !is LoadingState.Finished) return@LaunchedEffect
        dropMainHistoryAfter = null
        runCatching { state.nativeWebView.clearHistory() }
    }

    // A page the main view loads while the layer is over it (Back from a reel a chat opened takes
    // the main view back under the chat) is kept paused too.
    LaunchedEffect(mainPageStarts.count, state.loadingState) {
        if (!isMessagesLayerOpen || state.loadingState !is LoadingState.Finished) return@LaunchedEffect
        navigator.evaluateJavaScript(PAUSE_UNDER_LAYER_JS) {}
    }

    LaunchedEffect(isMessagesLayerOpen) {
        if (state.loadingState !is LoadingState.Finished) return@LaunchedEffect
        if (isMessagesLayerOpen) {
            navigator.evaluateJavaScript(PAUSE_UNDER_LAYER_JS + "window.__mbLayerScrollY = window.scrollY;") {}
        } else {
            navigator.evaluateJavaScript(RESUME_UNDER_LAYER_JS) {}
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

    val pagePadding = if (isImmersiveMode) {
        Modifier.padding(bottom = imeHeight)
    } else {
        Modifier.padding(
            top = barsInsets.calculateTopPadding(),
            bottom = maxOf(barsInsets.calculateBottomPadding(), imeHeight)
        )
    }
    val pageModifier = Modifier
        .fillMaxSize()
        .background(barColor)
        .then(pagePadding)

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
            platformWebViewParams = appWebViewParams(fullscreen, mainPageStarts, onRendererGone = { dead ->
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
            // A WebView whose renderer is gone is destroyed once it's out of the layout, and so is
            // the page when the activity goes (not for a configuration change, which the activity
            // handles itself anyway): left alive, a finished activity's page went on playing its
            // video, with its sound, in the background.
            onDispose = { webView ->
                if (deadWebViews.remove(webView) || activity?.isChangingConfigurations != true) webView.destroy()
            },
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
                            pip = PipBridge(onVideoPlayingChanged, onPipPageVisible, onPipVideoGone),
                            screen = ScreenBridge { mainVideoPlaying = it },
                            layer = LayerBridge { pageUrl ->
                                if (isStoryCreatePage(pageUrl)) messagesLayer.open(pageUrl)
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


    MainViewShareEffects(
        shareMediaRequest = shareMediaRequest,
        navigator = navigator,
        loadingState = loadingState,
        pageStarts = pageStarts,
        onOpenLayer = { layerUrl ->
            messagesLayer.open(layerUrl)
            messagesLayer.generation++
        },
        onCloseLayer = { messagesLayer.close() },
    )
    messagesLayer.url?.let { layerUrl ->
        // A new Messages link while the layer is open (a notification, say) recreates it, and
        // so does a lost renderer (onRendererGone), at the page it was showing.
        key(layerUrl, messagesLayer.generation) {
            MessagesLayer(
                url = layerUrl,
                userScripts = userScripts,
                fullscreen = fullscreen,
                isFullscreen = isFullscreen,
                // Without a background of its own: a layer kept out of sight behind a reel painted
                // its page colour over the reel (a black screen in dark mode); MessagesLayer paints
                // one while it's shown.
                modifier = Modifier.fillMaxSize().then(pagePadding),
                background = barColor,
                primaryColor = primaryColor,
                onPrimaryColor = onPrimaryColor,
                onClose = { messagesLayer.onClosed() },
                pipEnabled = pipEnabled,
                hidden = messagesLayer.hidden,
                onPipTarget = { layerPipTarget = it },
                onVideoPlayingChanged = onVideoPlayingChanged,
                onPipPageVisible = onPipPageVisible,
                onPipVideoGone = onPipVideoGone,
                trackVideoPlaying = trackVideoPlaying,
                textZoom = textZoomPercent,
                onScreenVideoPlayingChanged = { layerVideoPlaying = it },
                onLeave = { mainUrl, fromUrl ->
                    val mainPlace = runCatching {
                        val webView = state.nativeWebView
                        MainPlace(webView.copyBackForwardList().currentIndex, webView.url)
                    }.getOrNull()
                    messagesLayer.onLeave(mainUrl, fromUrl, mainPlace) { navigator.loadUrl(it) }
                },
                onOpenSettings = { settingsToggle = true },
                onThemeColor = { layerThemeColor = it },
                onRendererGone = { pageUrl ->
                    messagesLayer.onRendererGone(pageUrl, layerUrl, SystemClock.elapsedRealtime())
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
