package com.eepiemi.materialbook

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Rational
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.astryx.book.notifications.AppVisibility
import com.astryx.book.notifications.FacebookCountsWorker
import com.astryx.book.ui.screens.AppLockScreen
import com.eepiemi.materialbook.ui.screens.MaterialbookWebView
import com.astryx.book.utils.isAppLockEnabled
import com.astryx.book.utils.locksOnScreenOff
import com.astryx.book.utils.startPageUrl
import com.astryx.book.utils.shareStartPageUrl
import com.astryx.book.utils.ShareTarget
import com.astryx.book.utils.isWebViewTooOld
import com.astryx.book.ui.components.WebViewUpdateDialog
import androidx.webkit.WebViewCompat
import com.astryx.book.ui.screens.ShareMediaRequest
import com.eepiemi.materialbook.ui.screens.OpenLinkRequest
import com.eepiemi.materialbook.utils.appOrientation
import com.astryx.book.utils.PipWindowParams
import com.astryx.book.utils.pipParamsNeedSending
import com.astryx.book.utils.steadyPipRatio
import com.astryx.book.utils.publishAppShortcuts
import com.eepiemi.materialbook.ui.theme.MaterialbookTheme
import com.eepiemi.materialbook.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import com.astryx.book.AppLockController
import com.astryx.book.LockScreenAudioController
import com.astryx.book.linkUrl
import com.astryx.book.mediaShareTarget
import com.astryx.book.receiveSharedMedia

private const val TAG = "AstryxbookPiP"
private const val ACTION_PIP_TOGGLE = "com.astryx.book.PIP_TOGGLE_PLAYBACK"

// How recently a video must have been playing to count as "playing when PiP started".
private const val PIP_RECENTLY_PLAYING_MS = 3000L

// A FragmentActivity (a ComponentActivity) because app lock's BiometricPrompt needs one
// (AppLockController).
class MainActivity : FragmentActivity() {

    // Shared with the composable tree below (passed explicitly rather than
    // relying on Compose's viewModel() default resolution) so onUserLeaveHint
    // can read the PiP setting without extra plumbing through the UI layer.
    private val settingsVM: SettingsViewModel by viewModels()

    // Live playback state reported by PipBridge — not settings-backed, so it
    // isn't part of SettingsViewModel; just a plain flag read at the one
    // moment it matters (onUserLeaveHint).
    @Volatile
    private var isVideoPlaying = false

    @Volatile
    private var currentAspectRatio = Rational(16, 9)

    // Last known video dimensions — persisted so reapplyPipParams() can
    // recompute the correct portrait ratio when the user changes the setting
    // while a video is already playing (no new JS bridge event fires in that case).
    @Volatile
    private var lastVideoWidth = 0
    @Volatile
    private var lastVideoHeight = 0

    // Bumped by pipActionReceiver on each Play/Pause tap from the PiP
    // overlay; observed by the composable to trigger a one-off JS call back
    // into the WebView (the reverse direction of PipBridge, which only goes
    // JS -> native).
    private var pipToggleTrigger by mutableIntStateOf(0)
    // The page is reloaded for the full window the next time the app shows (see closeEmptyPip);
    // until then the loading screen covers it.
    private var relayoutTrigger by mutableIntStateOf(0)
    private var relayoutPending by mutableStateOf(false)

    // Drives the CSS-injection focus mode (hide page chrome, make the video
    // fill the viewport) on PiP enter/exit.
    private var isInPipMode by mutableStateOf(false)
    // Created inside a PiP window (see onStart).
    private var createdInPip = false

    // Whether a video was playing when PiP started (drives the keep-playing
    // guard), and when one was last reported playing: Facebook pauses it as
    // PiP resizes the page, which can be reported before PiP is.
    private var pipStartedWhilePlaying by mutableStateOf(false)
    @Volatile
    private var lastPlayingAt = 0L

    // Bumped in onUserLeaveHint, the earliest moment we know PiP is about to
    // engage — observed by the composable to freeze the detector's "last
    // active video" tracking immediately. Without this, Facebook's own
    // controller can pause reel A and autoplay a different reel B in the gap
    // (up to ~3.5s observed) between onUserLeaveHint and
    // onPictureInPictureModeChanged, and PIP_FOCUS_MODE_JS would then lock
    // onto whichever video is "last active" *at focus-mode-run time* — by
    // then already B, not the A the aspect ratio was computed from.
    private var pipEnteringTrigger by mutableIntStateOf(0)

    // What the PiP window was last sent (PipWindowParams.kt). Main thread only.
    private var sentPipParams: PipWindowParams? = null
    private val pipParamsHandler = Handler(Looper.getMainLooper())

    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_PIP_TOGGLE) {
                Log.d(TAG, "pipActionReceiver: toggle requested")
                pipToggleTrigger++
            }
        }
    }

    // Lock-screen audio: the video's sound handed to a native player while the screen is locked in PiP.
    private val lockScreenAudio = LockScreenAudioController(
        this,
        isInPip = { isInPipMode },
        enabled = { settingsVM.pipEnabled.value && settingsVM.pipLockscreenAudio.value },
    )

    // A link sent while the app runs (singleTask: onNewIntent) opens in the existing WebView.
    private var openLinkRequest by mutableStateOf<OpenLinkRequest?>(null)

    // Photos or videos shared from another app ("Post to Facebook", "Facebook story"), once
    // they're copied into the cache: the WebView opens Facebook's composer for them.
    private var shareMediaRequest by mutableStateOf<ShareMediaRequest?>(null)

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_OFF) return
            // App lock: "Lock when the screen turns off" locks on the next start, whatever the
            // grace period (see onStart).
            if (locksOnScreenOff(settingsVM.appLock.value, settingsVM.appLockOnScreenOff.value)) {
                appLockController.lockOnNextStart = true
            }
            lockScreenAudio.onScreenOff()
        }
    }

    // ---- app lock (AppLockController) ----
    private val appLockController = AppLockController(this) { settingsVM.appLock.value }

    /** Whether app lock covers the page now (for the app-level tests). */
    internal val isAppLocked: Boolean get() = appLockController.isLocked

    override fun onStop() {
        super.onStop()
        AppVisibility.onScreen = false
        appLockController.onStop(isChangingConfigurations)
    }

    // An app's Play Store page (the Play Store app, or the site without it).
    private fun openStorePage(packageName: String) {
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
        val web = Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$packageName".toUri())
        runCatching { startActivity(market) }.recoverCatching { startActivity(web) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Phones browse in portrait; only HTML5 fullscreen video rotates (see
        // appOrientation and the fullscreen host in MaterialbookWebView).
        requestedOrientation =
            appOrientation(resources.configuration.smallestScreenWidthDp, isFullscreen = false)

        // chrome://inspect on release builds is a developer setting (Settings: tap the
        // version seven times, then "WebView debugging"); debug builds always allow it.
        // Most PiP fixes in this fork were found through live DevTools sessions against
        // the installed release build, so keep the setting. It's off by default because
        // with it on, anything with USB debugging access can drive the logged-in page.
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG || settingsVM.webViewDebugging.value)
        lifecycleScope.launch {
            settingsVM.webViewDebugging.collect {
                WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG || it)
            }
        }

        publishAppShortcuts(this)
        // "Facebook notifications" (Settings): the background checks follow the setting,
        // including one restored from an imported settings file.
        lifecycleScope.launch {
            settingsVM.facebookNotifications.collect { FacebookCountsWorker.schedule(this@MainActivity, it) }
        }

        val filter = IntentFilter(ACTION_PIP_TOGGLE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipActionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(pipActionReceiver, filter)
        }
        // SCREEN_OFF can only be received by a context-registered receiver.
        ContextCompat.registerReceiver(
            this, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        appLockController.onCreate()
        createdInPip = isInPictureInPictureMode

        // Read once: the page the app starts on (a link it was opened with, or Home/Feeds).
        // A share that opens a mobile page starts there (Home's composer, the profile, the groups);
        // stories and reels open in the layer over the usual start page.
        val mediaTarget = mediaShareTarget(intent)
        val startUrl = mediaTarget?.let { shareStartPageUrl(it) }
            ?: startPageUrl(if (mediaTarget == null) linkUrl(this, intent) else null, settingsVM.startOnFeeds.value)
        mediaTarget?.let { receiveSharedMedia(this, intent, it, ::onSharedMediaReady) }

        // An Android System WebView too old for the page scripts (before Chrome 80): asked to
        // update it, once per app start (not again when the activity is recreated).
        val outdatedWebView = if (savedInstanceState != null) null
        else WebViewCompat.getCurrentWebViewPackage(this)?.takeIf { isWebViewTooOld(it.versionName) }

        setContent {
            val materialYou by settingsVM.materialYou.collectAsState()
            MaterialbookTheme(dynamicColor = materialYou) {
                // Stacks the lock screen over the page; passes the window's exact size on
                // to the page, as the window itself did before.
                Box(Modifier.fillMaxSize(), propagateMinConstraints = true) {
                    MaterialbookWebView(
                        url = startUrl,
                        settingsVM = settingsVM,
                        pipToggleTrigger = pipToggleTrigger,
                        pipEnteringTrigger = pipEnteringTrigger,
                        isInPipMode = isInPipMode,
                        pipStartedWhilePlaying = pipStartedWhilePlaying,
                        pipHandoffReadTrigger = lockScreenAudio.readTrigger,
                        relayoutTrigger = relayoutTrigger,
                        relayoutPending = relayoutPending,
                        pipHandback = lockScreenAudio.handback,
                        openLinkRequest = openLinkRequest,
                        shareMediaRequest = shareMediaRequest,
                        onPipHandoffRead = lockScreenAudio::onPageRead,
                        onPipPageVisible = { runOnUiThread { lockScreenAudio.handBack("page visible") } },
                        onPipVideoGone = { runOnUiThread { closeEmptyPip() } },
                        isAppLocked = appLockController.isLocked,
                        onVideoPlayingChanged = { isPlaying, videoWidth, videoHeight ->
                            updateVideoPlaybackState(isPlaying, videoWidth, videoHeight)
                        }
                    )
                    if (appLockController.isLocked) AppLockScreen(onUnlock = appLockController::authenticate)
                    var askWebViewUpdate by remember { mutableStateOf(outdatedWebView != null) }
                    if (askWebViewUpdate && outdatedWebView != null) {
                        WebViewUpdateDialog(
                            version = outdatedWebView.versionName ?: "",
                            onUpdate = {
                                askWebViewUpdate = false
                                openStorePage(outdatedWebView.packageName)
                            },
                            onDismiss = { askWebViewUpdate = false }
                        )
                    }
                }
            }
        }

        // Re-push PictureInPictureParams whenever the user toggles PiP or
        // changes the portrait ratio — no app restart required.
        lifecycleScope.launch {
            settingsVM.pipEnabled.collect { reapplyPipParams() }
        }
        lifecycleScope.launch {
            settingsVM.pipPortraitRatio.collect { reapplyPipParams() }
        }
        // "Block screenshots" (Settings): screenshots, screen recording, casting and the
        // Recents preview show the app as black. Dialogs (Settings) inherit it when they open.
        lifecycleScope.launch {
            settingsVM.blockScreenshots.collect { block ->
                if (block) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
        // With app lock on, Recents shows no snapshot of the page (Android 13+; screenshots
        // still work unless they're blocked too).
        lifecycleScope.launch {
            settingsVM.appLock.collect { mode ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    setRecentsScreenshotEnabled(!isAppLockEnabled(mode))
                }
            }
        }
    }

    // Facebook took the PiP video off the page, and PiP doesn't move on to another video: the
    // empty, black PiP window closes, as when it's swiped away, the app staying where it was.
    private fun closeEmptyPip() {
        if (!isInPictureInPictureMode) return
        Log.d(TAG, "PiP video gone from the page: closing the PiP window")
        moveTaskToBack(true)
        // Whatever Facebook loaded meanwhile was laid out for the PiP window.
        relayoutPending = true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        mediaShareTarget(intent)?.let {
            receiveSharedMedia(this, intent, it, ::onSharedMediaReady)
            return
        }
        val url = linkUrl(this, intent) ?: return
        openLinkRequest = OpenLinkRequest((openLinkRequest?.seq ?: 0) + 1, url)
    }

    // The shared items are copied (IncomingIntents.kt): the WebView opens Facebook's composer.
    private fun onSharedMediaReady(target: ShareTarget) {
        shareMediaRequest = ShareMediaRequest((shareMediaRequest?.seq ?: 0) + 1, target)
    }


    // Handback signals besides the page's own visibilitychange: the activity
    // becoming visible again (unlock while in PiP, or back to full screen).
    override fun onStart() {
        super.onStart()
        // Android starts the app again inside the PiP window it left when it killed the app's
        // process (Samsung's memory cleanup during a long lock): the new page, with no video,
        // showed whole in the small window (seen on a Galaxy A56). A fresh start has nothing to
        // play in PiP, so the window closes, the app staying in the background.
        if (createdInPip) {
            createdInPip = false
            if (isInPictureInPictureMode) {
                Log.d(TAG, "started inside a PiP window: closing it")
                moveTaskToBack(true)
                // Its start page loads at the PiP window's size.
                relayoutPending = true
            }
        }
        AppVisibility.onScreen = true
        FacebookCountsWorker.onAppShown(this)
        lockScreenAudio.handBack("onStart")
        appLockController.onStart()
    }

    override fun onResume() {
        super.onResume()
        if (relayoutPending) {
            relayoutPending = false
            relayoutTrigger++
        }
        lockScreenAudio.handBack("onResume")
        appLockController.onResume(isInPictureInPictureMode)
        // Also here: a PiP window entered and left within a moment can end with no PiP callback.
        if (!isInPictureInPictureMode) dropPipViewportCookie()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(pipActionReceiver)
        unregisterReceiver(screenOffReceiver)
        lockScreenAudio.onDestroy()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        Log.d(TAG, "onPictureInPictureModeChanged: $isInPictureInPictureMode")
        if (isInPictureInPictureMode) {
            // Facebook pauses the video as PiP resizes the page, possibly before this
            // callback, so "playing a moment ago" counts too; the keep-playing guard in
            // MaterialbookWV resumes it.
            pipStartedWhilePlaying = isVideoPlaying ||
                SystemClock.elapsedRealtime() - lastPlayingAt < PIP_RECENTLY_PLAYING_MS
            Log.d(TAG, "onPictureInPictureModeChanged: startedWhilePlaying=$pipStartedWhilePlaying")
        }
        isInPipMode = isInPictureInPictureMode
        if (!isInPictureInPictureMode) dropPipViewportCookie()

        lockScreenAudio.onPipModeChanged(isInPictureInPictureMode)

        // Facebook's player pauses the video the instant PiP resizes the page
        // (the keep-playing guard in MaterialbookWV resumes it shortly after),
        // but our JS detector only reports state on its own schedule — without
        // this, the Play/Pause button can briefly show the wrong icon (still
        // "Pause" right when it should already say "Play"). Flip + rebuild
        // the action immediately rather than waiting for the next JS report.
        if (isInPictureInPictureMode && isVideoPlaying) {
            isVideoPlaying = false
            Log.d(TAG, "onPictureInPictureModeChanged: immediate icon flip to Play")
            applyPipParams(playing = false)
        }
    }

    // In PiP Facebook can write the PiP window's size into a host-only "vpd" cookie on
    // m.facebook.com, which shadows its own .facebook.com one: later pages were laid out PiP-narrow,
    // even after the app restarted (WebView keeps session cookies; seen on the emulator). The
    // host-only one goes when PiP ends and whenever the app comes back out of PiP; Facebook writes
    // the window's size again as pages load.
    private fun dropPipViewportCookie() {
        runCatching {
            val cookies = CookieManager.getInstance()
            // Secure or not (a non-secure write can't replace a secure cookie).
            cookies.setCookie("https://m.facebook.com", "vpd=; Max-Age=0; Path=/; Secure")
            cookies.setCookie("https://m.facebook.com", "vpd=; Max-Age=0; Path=/")
            cookies.flush()
            Log.d(TAG, "host-only vpd cookie expired")
        }
    }

    private fun buildPlayPauseAction(isPlaying: Boolean): RemoteAction {
        val iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val icon = Icon.createWithResource(this, iconRes)
        val intent = Intent(ACTION_PIP_TOGGLE).setPackage(packageName)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val label = if (isPlaying) "Pause" else "Play"
        return RemoteAction(icon, label, label, pendingIntent)
    }

    // Android throws when the activity is already going (the PiP window dismissed, the app
    // closed) while a video-state report from the page is still on its way: the app crashed.
    // The current ratio and a Play/Pause button for [playing]; [autoEnter] (Android 12+) left
    // unset when null.
    private fun applyPipParams(playing: Boolean, autoEnter: Boolean? = null) {
        if (isFinishing || isDestroyed) return
        val ratio = currentAspectRatio
        val builder = pipParamsBuilder()
            .setAspectRatio(ratio)
            .setActions(listOf(buildPlayPauseAction(playing)))
        if (autoEnter != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
        }
        runCatching { setPictureInPictureParams(builder.build()) }
            .onSuccess { sentPipParams = PipWindowParams(ratio, playing) }
            .onFailure { Log.w(TAG, "setPictureInPictureParams failed", it) }
    }

    private fun pipParamsBuilder(): PictureInPictureParams.Builder {
        return PictureInPictureParams.Builder().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // The WebView fills the activity, so its visible bounds are the
                // appropriate source rectangle for Android 12+ PiP transitions.
                val decorView = window.decorView
                setSourceRectHint(
                    Rect(
                        0,
                        0,
                        decorView.width.coerceAtLeast(1),
                        decorView.height.coerceAtLeast(1)
                    )
                )
            }
        }
    }

    /**
     * Re-pushes [PictureInPictureParams] to the system using the current settings
     * and the last known video dimensions. Called whenever [SettingsViewModel.pipEnabled]
     * or [SettingsViewModel.pipPortraitRatio] changes so the new values take effect
     * immediately — without waiting for the next JS bridge event or an app restart.
     */
    private fun reapplyPipParams() {
        // Recompute ratio from stored dimensions + current portrait setting
        if (lastVideoWidth > 0 && lastVideoHeight > 0) {
            currentAspectRatio = settingsVM.pipRationalForVideo(
                lastVideoWidth,
                lastVideoHeight
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val autoEnter = isVideoPlaying && settingsVM.pipEnabled.value
            Log.d(TAG, "reapplyPipParams: autoEnter=$autoEnter, ratio=$currentAspectRatio")
            applyPipParams(isVideoPlaying, autoEnter)
        }
    }

    private fun updateVideoPlaybackState(
        isPlaying: Boolean,
        videoWidth: Int,
        videoHeight: Int
    ) {
        if (isPlaying || isVideoPlaying) lastPlayingAt = SystemClock.elapsedRealtime()
        isVideoPlaying = isPlaying
        lastVideoWidth = videoWidth
        lastVideoHeight = videoHeight
        if (videoWidth > 0 && videoHeight > 0) {
            val ratio = settingsVM.pipRationalForVideo(videoWidth, videoHeight)
            currentAspectRatio = if (isInPictureInPictureMode) steadyPipRatio(currentAspectRatio, ratio) else ratio
        }
        Log.d(TAG, "updateVideoPlaybackState: isPlaying=$isPlaying, ${videoWidth}x$videoHeight, pipEnabled=${settingsVM.pipEnabled.value}, aspectRatio=$currentAspectRatio")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // PipBridge reports from the JavaScript thread (old WebViews), AstryxPage on the main one.
            if (Looper.myLooper() == Looper.getMainLooper()) sendPlaybackPipParams()
            else pipParamsHandler.post { sendPlaybackPipParams() }
        }
    }

    // In PiP, sends only what changes the window: see PipWindowParams.kt.
    private fun sendPlaybackPipParams() {
        val next = PipWindowParams(currentAspectRatio, isVideoPlaying)
        if (!pipParamsNeedSending(isInPictureInPictureMode, sentPipParams, next)) return
        val autoEnter = isVideoPlaying && settingsVM.pipEnabled.value
        Log.d(TAG, "setPictureInPictureParams: autoEnter=$autoEnter")
        applyPipParams(isVideoPlaying, autoEnter)
    }

    // Called right before the user leaves via Home or the recents switcher.
    // Kept as a universal fallback across ALL API levels — not just pre-S —
    // rather than relying solely on setAutoEnterEnabled above. That API is
    // primarily documented/tested for gesture-navigation swipe transitions;
    // its behavior on a plain Home-button press, on a specific OEM skin, on a
    // specific nav mode, isn't something we can verify without a real device
    // (and OEM skins like Samsung's OneUI layer their own windowing
    // customizations on top of AOSP). A redundant explicit call here when
    // auto-enter already handled it is harmless.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val eligible = isVideoPlaying &&
            settingsVM.pipEnabled.value
        Log.d(TAG, "onUserLeaveHint: sdkInt=${Build.VERSION.SDK_INT}, isVideoPlaying=$isVideoPlaying, pipEnabled=${settingsVM.pipEnabled.value}, eligible=$eligible")
        if (eligible) {
            pipEnteringTrigger++
            try {
                val ratio = currentAspectRatio
                val playing = isVideoPlaying
                enterPictureInPictureMode(
                    pipParamsBuilder()
                        .setAspectRatio(ratio)
                        .setActions(listOf(buildPlayPauseAction(playing)))
                        .build()
                )
                sentPipParams = PipWindowParams(ratio, playing)
                Log.d(TAG, "enterPictureInPictureMode called successfully")
            } catch (e: Exception) {
                Log.e(TAG, "enterPictureInPictureMode failed", e)
            }
        }
    }
}
