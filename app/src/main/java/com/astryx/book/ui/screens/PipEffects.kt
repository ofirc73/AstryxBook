package com.astryx.book.ui.screens
import android.util.Log
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.astryx.book.audio.PipHandback
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebViewNavigator
import com.multiplatform.webview.web.WebViewState
import kotlinx.coroutines.delay
import kotlin.math.abs

// Leaving PiP: how long the window takes to get its full size back, and how many zoom-out steps
// (x0.8 each) can bring a page from the PiP window's width back.
internal const val PIP_EXIT_SETTLE_MS = 600L
internal const val MAX_ZOOM_OUT_STEPS = 20
// A pinch zoom undone in PiP: from just above 1 (none) up to what zoomBy takes; checked once the
// PiP window has its size.
private const val PINCH_RATIO_MIN = 1.05f
private const val PINCH_RATIO_MAX = 100f
private const val PIP_PINCH_CHECK_DELAY_MS = 300L
// Leaving PiP: a page within 5% of the zoom it should have is left alone.
private const val PIP_EXIT_ZOOM_TOLERANCE = 0.05f

/**
 * What to zoom the page by (WebView.zoomBy) on leaving PiP so it shows at [wanted] (the user's
 * pinch zoom from before PiP, 1 for none) instead of [current] (both the page's width over the
 * width the window shows); null when it already does.
 */
internal fun pipExitZoomBy(current: Float, wanted: Float): Float? {
    if (!(current > 0f) || !(wanted > 0f)) return null
    val by = wanted / current
    return if (abs(by - 1f) < PIP_EXIT_ZOOM_TOLERANCE) null else by.coerceIn(0.02f, 50f)
}

/**
 * MaterialbookWebView's picture-in-picture effects: what runs on the page PiP shows
 * ([pipNavigator]/[pipState], the Messages layer's while it's open, see PipTarget) as PiP starts
 * and ends, on the PiP window's Play/Pause button, around HTML5 fullscreen, and for the
 * lock-screen audio hand-off and hand-back. Each effect is kept separate from focus mode on
 * purpose (see CLAUDE.md); the scripts are in PipScripts.kt. Covered end to end by
 * MainActivityPipTest.
 */
@Composable
internal fun PipEffects(
    pipNavigator: WebViewNavigator,
    pipState: WebViewState,
    // The pages pipState's WebView has started (see PageStarts), next to its loading state.
    pipPageStarts: Int = 0,
    isInPipMode: Boolean,
    isFullscreen: Boolean,
    pipToggleTrigger: Int,
    pipEnteringTrigger: Int,
    pipStartedWhilePlaying: Boolean,
    pipLockscreenAudio: Boolean,
    pipHandoffReadTrigger: Int,
    onPipHandoffRead: (String?) -> Unit,
    pipHandback: PipHandback?,
    onPipVideoGone: () -> Unit = {},
) {
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
    LaunchedEffect(isInPipMode, pipState.loadingState, pipPageStarts) {
        if (pipState.loadingState is LoadingState.Finished) {
            pipNavigator.evaluateJavaScript(
                if (isInPipMode) pipKeepPlayingActivateJs(pipStartedWhilePlaying)
                else PIP_KEEP_PLAYING_DISARM_JS
            ) {}
        }
    }

    // In HTML5 fullscreen the video renders in WebView's custom view, not the
    // page layout, so PiP just shows that view and focus mode (hide page DOM,
    // restyle the <video>) is skipped. Restore still always runs outside PiP:
    // it also unfreezes the active-video tracker that PIP_FREEZE_ACTIVE_VIDEO_JS
    // sets on every PiP attempt, fullscreen or not.
    // A page that finishes loading during PiP, after focus mode ran on an earlier one, is a new
    // page (Facebook reloads a page left alone for an hour or so as the screen comes back on): the
    // PiP video went with the old one, and PiP doesn't move on to another video, so the window
    // closes like when the video leaves the page (PIP_VIDEO_GONE_WATCH_JS, which went with the old
    // page too). PIP_PAGE_IS_PIPS_JS tells it from the same page finishing an in-page navigation.
    var focusModeInPip by remember(pipState) { mutableStateOf(false) }
    val currentOnPipVideoGone by rememberUpdatedState(onPipVideoGone)
    LaunchedEffect(isInPipMode, pipState.loadingState, pipPageStarts) {
        if (pipState.loadingState is LoadingState.Finished) {
            if (!isInPipMode) {
                focusModeInPip = false
                pipNavigator.evaluateJavaScript(PIP_RESTORE_MODE_JS) {}
            } else if (isFullscreen) {
                Log.d("AstryxbookPiP", "PiP entered while fullscreen: skipping focus mode")
            } else if (focusModeInPip) {
                pipNavigator.evaluateJavaScript(PIP_PAGE_IS_PIPS_JS) { same ->
                    if (same == "true") {
                        pipNavigator.evaluateJavaScript(PIP_FOCUS_MODE_JS + PIP_KEEP_CHAIN_JS + PIP_VIDEO_GONE_WATCH_JS) {}
                    } else {
                        Log.d("AstryxbookPiP", "a new page loaded in PiP: the PiP video is gone")
                        currentOnPipVideoGone()
                    }
                }
            } else {
                focusModeInPip = true
                pipNavigator.evaluateJavaScript(PIP_FOCUS_MODE_JS + PIP_KEEP_CHAIN_JS + PIP_VIDEO_GONE_WATCH_JS) {}
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
            pipNavigator.evaluateJavaScript(PIP_FOCUS_MODE_JS + PIP_KEEP_CHAIN_JS + PIP_VIDEO_GONE_WATCH_JS) {}
        }
    }

    // A pinch zoom carried into PiP (pinch to zoom on): the PiP window showed only the zoomed-in
    // part of the page, the video cropped in a corner with Facebook's labels around it (seen on a
    // Galaxy A56). In PiP the page is zoomed all the way out, which fits its whole width as a
    // PiP window normally does; checked on entering PiP and again after each return from the lock
    // screen (the hand-back), which can bring the zoom back. Leaving PiP puts the page back at the
    // user's zoom (none, or that pinch zoom) once the window is full size again, whatever zoom it
    // took in PiP: a video page Facebook keeps at its full-screen width shows zoomed out in the
    // PiP window, a resize of the window left it at scale 1 there, and Chromium kept the window's
    // visible width on the way out, the page zoomed in x1.94 (Galaxy A56). Separate from focus
    // mode, like the effects around it.
    var pipPinchRatio by remember { mutableFloatStateOf(1f) }
    var pipZoomToRestore by remember { mutableStateOf(false) }
    var pipEntryZoomChecked by remember { mutableStateOf(false) }
    LaunchedEffect(isInPipMode, pipHandback) {
        val webView = runCatching { pipState.nativeWebView }.getOrNull() ?: return@LaunchedEffect
        if (isInPipMode) {
            pipZoomToRestore = true
            if (isFullscreen || pipState.loadingState !is LoadingState.Finished) return@LaunchedEffect
            delay(PIP_PINCH_CHECK_DELAY_MS) // the window's resize first
            pipNavigator.evaluateJavaScript(PIP_PINCH_RATIO_JS) { raw ->
                pipEntryZoomChecked = true
                val ratio = raw?.removeSurrounding("\"")?.toFloatOrNull() ?: return@evaluateJavaScript
                if (ratio <= PINCH_RATIO_MIN) return@evaluateJavaScript
                Log.d("AstryxbookPiP", "PiP shows the page zoomed x$ratio: zooming out")
                if (pipPinchRatio <= 1f) pipPinchRatio = minOf(ratio, PINCH_RATIO_MAX)
                repeat(MAX_ZOOM_OUT_STEPS) { if (!webView.zoomOut()) return@evaluateJavaScript }
            }
        } else if (pipZoomToRestore) {
            pipZoomToRestore = false
            pipEntryZoomChecked = false
            val wanted = pipPinchRatio
            pipPinchRatio = 1f
            delay(PIP_EXIT_SETTLE_MS)
            pipNavigator.evaluateJavaScript(PIP_PINCH_RATIO_JS) { raw ->
                val current = raw?.removeSurrounding("\"")?.toFloatOrNull() ?: return@evaluateJavaScript
                val by = pipExitZoomBy(current, wanted) ?: return@evaluateJavaScript
                Log.d("AstryxbookPiP", "left PiP zoomed x$current, wanted x$wanted: zooming by $by")
                webView.zoomBy(by)
            }
        }
    }

    // The user resizing the PiP window can leave the page zoomed in the window (the video page
    // above, at scale 1 instead of zoomed out); the reset on leaving PiP then showed the page
    // zoomed in for about a second first. After each resize the page is zoomed all the way out
    // again, as on entering PiP, so there's no zoom for Chromium to carry out of PiP. Only once
    // the entry check has run (it notes the user's own zoom first).
    DisposableEffect(isInPipMode, pipState) {
        val webView = runCatching { pipState.nativeWebView }.getOrNull()
        if (!isInPipMode || webView == null) return@DisposableEffect onDispose {}
        val check = Runnable {
            if (!pipEntryZoomChecked) return@Runnable
            pipNavigator.evaluateJavaScript(PIP_PINCH_RATIO_JS) { raw ->
                val ratio = raw?.removeSurrounding("\"")?.toFloatOrNull() ?: return@evaluateJavaScript
                if (ratio <= PINCH_RATIO_MIN) return@evaluateJavaScript
                Log.d("AstryxbookPiP", "the resized PiP window shows the page zoomed x$ratio: zooming out")
                repeat(MAX_ZOOM_OUT_STEPS) { if (!webView.zoomOut()) return@evaluateJavaScript }
            }
        }
        val resized = View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                webView.removeCallbacks(check)
                webView.postDelayed(check, PIP_PINCH_CHECK_DELAY_MS)
            }
        }
        webView.addOnLayoutChangeListener(resized)
        onDispose {
            webView.removeOnLayoutChangeListener(resized)
            webView.removeCallbacks(check)
        }
    }

    // Separate from focus mode on purpose (see PIP_PIN_SCREEN_JS): keeps
    // rotation during PiP from making Facebook drop the reel.
    LaunchedEffect(isInPipMode, pipState.loadingState, pipPageStarts) {
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
    // Only once the page has loaded, like the focus-mode effect above: the
    // navigator replays just its last event to a WebView that isn't attached
    // yet, so an unconditional evaluate at startup would replace the initial
    // loadUrl and the page would never load.
    LaunchedEffect(isInPipMode, pipLockscreenAudio, pipState.loadingState, pipPageStarts) {
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
}
