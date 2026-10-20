package com.eepiemi.materialbook.ui.screens

import android.view.View
import android.webkit.CookieManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.eepiemi.materialbook.R
import com.eepiemi.materialbook.utils.DESKTOP_USER_AGENT
import com.eepiemi.materialbook.utils.FullscreenController
import com.eepiemi.materialbook.utils.MessagesLayerRoute
import com.eepiemi.materialbook.utils.ShareMediaQueue
import com.eepiemi.materialbook.utils.ShareTarget
import com.eepiemi.materialbook.utils.appWebViewParams
import com.eepiemi.materialbook.utils.deadWebViewUrl
import com.eepiemi.materialbook.utils.hardenWebView
import com.eepiemi.materialbook.utils.isReelCreatePage
import com.eepiemi.materialbook.utils.isStoryCreatePage
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
import com.eepiemi.materialbook.utils.messagesLayerExit
import com.eepiemi.materialbook.utils.messagesLayerRoute
import com.multiplatform.webview.request.RequestInterceptor
import com.multiplatform.webview.request.WebRequest
import com.multiplatform.webview.request.WebRequestInterceptResult
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.WebViewNavigator
import com.multiplatform.webview.web.WebViewState
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import org.json.JSONObject

/**
 * Messages in desktop mode: the desktop Messages page in its own WebView, drawn over the
 * main one. The main view keeps its mobile page (and scroll position) underneath, so
 * closing the layer goes straight back to where the user was.
 *
 * - Facebook pages opened from a chat (a profile, a post) open in the layer too.
 * - Going to one of Facebook's sections (Home, Friends, the Reels tab, ...) from the desktop
 *   page, or opening a reel shared in a chat, closes the layer and shows it in the main view, on
 *   the mobile site ([onLeave], with the layer's page it left from).
 * - Back steps through the layer's own history (a profile back to the chat, a conversation back
 *   to the chat list), then closes the layer.
 * - Non-Facebook links go to [onExternalUrl], as in the main view.
 * - If the page's renderer process is lost, [onRendererGone] gets the page it was showing, to
 *   open a new layer there (by default the layer closes).
 */
/** The page PiP acts on (see MaterialbookWebView): the Messages layer's while it's open. */
class PipTarget(val navigator: WebViewNavigator, val state: WebViewState)

@Composable
fun MessagesLayer(
    url: String,
    userScripts: String?,
    fullscreen: FullscreenController,
    isFullscreen: Boolean,
    modifier: Modifier = Modifier,
    background: Color,
    primaryColor: Int,
    onPrimaryColor: Int,
    onClose: () -> Unit,
    onExternalUrl: (String) -> Unit,
    pipEnabled: Boolean = false,
    onPipTarget: (PipTarget?) -> Unit = {},
    onVideoPlayingChanged: (Boolean, Int, Int) -> Unit = { _, _, _ -> },
    onPipPageVisible: () -> Unit = {},
    trackVideoPlaying: Boolean = false,
    onScreenVideoPlayingChanged: (Boolean) -> Unit = {},
    onLeave: (mainUrl: String?, fromUrl: String?) -> Unit = { _, _ -> onClose() },
    onOpenSettings: () -> Unit = {},
    onThemeColor: (Color) -> Unit = {},
    textZoom: Int = 100,
    onRendererGone: (pageUrl: String?) -> Unit = { onClose() },
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val currentOnExternalUrl by rememberUpdatedState(onExternalUrl)
    val currentOnLeave by rememberUpdatedState(onLeave)
    val currentOnOpenSettings by rememberUpdatedState(onOpenSettings)
    val currentOnThemeColor by rememberUpdatedState(onThemeColor)
    val currentOnRendererGone by rememberUpdatedState(onRendererGone)

    // The user agent is applied by the library when it creates the WebView, before the
    // first load (setting it is idempotent, so doing it on every composition is fine).
    val state = rememberWebViewState(url).also {
        it.webSettings.customUserAgentString = DESKTOP_USER_AGENT
    }
    // Opened for a reel share: the desktop reels page (where the reel creator opens) stays in
    // the layer instead of leaving for the main view's Reels tab.
    val reelShare = isReelCreatePage(url)
    // The last page the layer showed (a chat), for [onLeave]: a reel opened from it goes back
    // there on Back.
    var pageUrl by remember { mutableStateOf(url) }
    val navigator = rememberWebViewNavigator(
        requestInterceptor = object : RequestInterceptor {
            override fun onInterceptUrlRequest(
                request: WebRequest,
                navigator: WebViewNavigator
            ): WebRequestInterceptResult =
                when (val route = layerRoute(request.url, request.isForMainFrame, reelShare)) {
                    MessagesLayerRoute.Allow -> {
                        if (request.isForMainFrame) pageUrl = request.url
                        WebRequestInterceptResult.Allow
                    }
                    is MessagesLayerRoute.Remap -> {
                        navigator.loadUrl(route.url)
                        WebRequestInterceptResult.Reject
                    }
                    is MessagesLayerRoute.External -> {
                        currentOnExternalUrl(route.url)
                        WebRequestInterceptResult.Reject
                    }
                    is MessagesLayerRoute.Leave -> {
                        currentOnLeave(route.mainUrl, pageUrl)
                        WebRequestInterceptResult.Reject
                    }
                }
        }
    )

    // Composed after the main view's handlers, so it gets Back first; disabled while a
    // video is fullscreen, so Back leaves fullscreen through the main view's handler.
    BackHandler(enabled = !isFullscreen) {
        // Asked of the WebView itself: opening a conversation is an in-page navigation,
        // which navigator.canGoBack doesn't always pick up.
        val canGoBack = runCatching { state.nativeWebView.canGoBack() }.getOrDefault(false)
        if (canGoBack) navigator.navigateBack() else onClose()
    }

    // Text size (Settings), when it changes while the layer is open. Not on the story or reel
    // creator: its desktop layout is fitted to the screen, and larger text overflows it.
    val layerTextZoom = if (isStoryCreatePage(url) || isReelCreatePage(url)) 100 else textZoom
    LaunchedEffect(layerTextZoom) {
        runCatching { state.nativeWebView }.getOrNull()?.settings?.textZoom = layerTextZoom
    }

    // Same page scripts as the main view (download hook, theme, ...).
    val loadingState = state.loadingState
    LaunchedEffect(loadingState, userScripts) {
        if (loadingState is LoadingState.Finished && userScripts != null) {
            // Marks the page as the layer's for the scripts that differ there (pinch_to_zoom.js).
            navigator.evaluateJavaScript("window.__astryxMessagesLayer = true;\n$userScripts") {}
        }
    }

    // A story share (ShareMedia): the desktop story creator takes the shared files one at a time,
    // laid out like the Facebook app's story editor (story_layout.js). A reel share: the reels
    // page's Create reel takes the shared video.
    LaunchedEffect(loadingState) {
        if (loadingState !is LoadingState.Finished) return@LaunchedEffect
        val page = state.lastLoadedUrl
        if (isStoryCreatePage(page)) {
            val layout = resources.openRawResource(R.raw.story_layout).bufferedReader().use { it.readText() }
            val done = JSONObject.quote(resources.getString(R.string.story_panel_done))
            navigator.evaluateJavaScript(
                "window.__astryxStoryDone = $done;" +
                    layout
            ) {}
        }
        val target = when {
            isStoryCreatePage(page) && ShareMediaQueue.pending(ShareTarget.STORY) -> ShareTarget.STORY
            reelShare && isReelCreatePage(page) && ShareMediaQueue.pending(ShareTarget.REEL) -> ShareTarget.REEL
            else -> null
        }
        target?.let { navigator.evaluateJavaScript(shareMediaScript(resources, it)) {} }
    }

    // The desktop site's in-page navigation, for leaving to a section (see messagesLayerExit).
    LaunchedEffect(loadingState) {
        if (loadingState is LoadingState.Finished) {
            val script = resources.openRawResource(R.raw.messages_layer_nav)
                .bufferedReader().use { it.readText() }
            navigator.evaluateJavaScript(script) {}
        }
    }

    // PiP for videos in the layer (a shared reel, a video in a chat): the same detector as the
    // main view reports them (AstryxPage, or PipBridge on old WebViews), and while the layer is open PiP acts on its
    // page. When it closes, its videos are gone: say so, or leaving the app afterwards would
    // still count as "video playing".
    LaunchedEffect(loadingState, pipEnabled) {
        if (loadingState is LoadingState.Finished && pipEnabled) {
            val detector = resources.openRawResource(R.raw.pip_video_detector)
                .bufferedReader().use { it.readText() }
            navigator.evaluateJavaScript(detector) {}
        }
    }
    // "Keep screen on: while a video plays" (see MaterialbookWebView).
    val currentOnScreenVideoPlayingChanged by rememberUpdatedState(onScreenVideoPlayingChanged)
    LaunchedEffect(loadingState, trackVideoPlaying) {
        if (loadingState is LoadingState.Loading) currentOnScreenVideoPlayingChanged(false)
        if (loadingState is LoadingState.Finished && trackVideoPlaying) {
            val script = resources.openRawResource(R.raw.video_playing)
                .bufferedReader().use { it.readText() }
            navigator.evaluateJavaScript(script) {}
        }
    }

    val currentOnPipTarget by rememberUpdatedState(onPipTarget)
    val currentOnVideoPlayingChanged by rememberUpdatedState(onVideoPlayingChanged)
    DisposableEffect(navigator, state) {
        currentOnPipTarget(PipTarget(navigator, state))
        onDispose {
            currentOnPipTarget(null)
            currentOnVideoPlayingChanged(false, 0, 0)
            currentOnScreenVideoPlayingChanged(false)
        }
    }

    Box(modifier = modifier.zIndex(1F).background(background)) {
        WebView(
            modifier = Modifier.fillMaxSize(),
            state = state,
            navigator = navigator,
            // The WebView can't be used once its renderer is gone (see RendererGoneClient):
            // the caller replaces the layer, and onDispose below destroys this one.
            platformWebViewParams = appWebViewParams(fullscreen, onRendererGone = { dead ->
                currentOnRendererGone(deadWebViewUrl(dead) ?: state.lastLoadedUrl)
            }),
            captureBackPresses = false,
            // The layer is gone for good once closed: free its WebView (and its page).
            onDispose = { webView -> webView.destroy() },
            onCreated = { webView ->
                hardenWebView(state, webView)
                state.webSettings.androidWebSettings.textZoom = layerTextZoom
                // The story and reel creators (a story or reel share) are laid out for a desktop window:
                // at phone width their preview is off screen. Lay them out wide and fit them to the screen.
                if (isStoryCreatePage(url) || isReelCreatePage(url)) {
                    state.webSettings.androidWebSettings.useWideViewPort = true
                    webView.settings.loadWithOverviewMode = true
                }
                // Pinch to zoom, as in the main view: the desktop photo viewer shows a photo sent in a
                // chat small at phone width and has no zoom of its own for fingers (seen on a Galaxy
                // A56). Not in the creators, whose editors follow finger drags.
                if (!isStoryCreatePage(url) && !isReelCreatePage(url)) {
                    webView.settings.setSupportZoom(true)
                    webView.settings.builtInZoomControls = true
                    webView.settings.displayZoomControls = false
                }
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
                state.webSettings.apply {
                    isJavaScriptEnabled = true
                    androidWebSettings.apply {
                        domStorageEnabled = true
                        hideDefaultVideoPoster = true
                        mediaPlaybackRequiresUserGesture = false
                    }
                }
                webView.apply {
                    installDownloadBridge(this, context)
                    installClipboardBridge(this, context)
                    installShareMediaBridge(this, context)
                    addJavascriptInterface(MaterialYouBridge({ primaryColor }, { onPrimaryColor }), "MaterialYouBridge")
                    installPageBridges(
                        this,
                        PageBridges(
                            // The gear scripts.js adds to the desktop header opens the app's Settings.
                            settings = MaterialbookSettings { currentOnOpenSettings() },
                            // The page's theme-color (scripts.js), for the system bars over the layer.
                            theme = ThemeChange { currentOnThemeColor(Color(it)) },
                            pip = PipBridge(
                                { playing, width, height -> currentOnVideoPlayingChanged(playing, width, height) },
                                onPipPageVisible
                            ),
                            screen = ScreenBridge { currentOnScreenVideoPlayingChanged(it) },
                            layer = LayerBridge { newUrl ->
                                val exit = messagesLayerExit(newUrl)?.takeUnless { reelShare && isReelCreatePage(newUrl) }
                                ContextCompat.getMainExecutor(context).execute {
                                    if (exit != null) currentOnLeave(exit.mainUrl, pageUrl) else pageUrl = newUrl
                                }
                            },
                        )
                    )
                    installPipVisibilityScript(this)
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    overScrollMode = View.OVER_SCROLL_NEVER
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                }
            }
        )
        if (loadingState is LoadingState.Loading) {
            LinearProgressIndicator(
                progress = { loadingState.progress },
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
            )
        }
    }
}

// The layer's route for [url]; during a reel share the desktop reels page stays in the layer.
private fun layerRoute(url: String, isMainFrame: Boolean, reelShare: Boolean): MessagesLayerRoute {
    val route = messagesLayerRoute(url, isMainFrame)
    return if (reelShare && route is MessagesLayerRoute.Leave && isReelCreatePage(url)) MessagesLayerRoute.Allow else route
}
