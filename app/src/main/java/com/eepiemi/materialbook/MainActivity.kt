package com.eepiemi.materialbook

import android.app.Activity
import android.app.KeyguardManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.ComponentName
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
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.eepiemi.materialbook.audio.LockScreenAudioService
import com.eepiemi.materialbook.audio.PipHandback
import com.eepiemi.materialbook.audio.isHandoffEligible
import com.eepiemi.materialbook.audio.parseHandoffRead
import com.eepiemi.materialbook.ui.screens.AppLockScreen
import com.eepiemi.materialbook.ui.screens.MaterialbookWebView
import com.eepiemi.materialbook.utils.AppLock
import com.eepiemi.materialbook.utils.canUseAppLock
import com.eepiemi.materialbook.utils.isAppLockEnabled
import com.eepiemi.materialbook.utils.shouldLockOnReturn
import com.eepiemi.materialbook.ui.screens.OpenLinkRequest
import com.eepiemi.materialbook.utils.appLinkUrl
import com.eepiemi.materialbook.utils.appOrientation
import com.eepiemi.materialbook.utils.facebookShareUrl
import com.eepiemi.materialbook.utils.publishAppShortcuts
import com.eepiemi.materialbook.ui.theme.MaterialbookTheme
import com.eepiemi.materialbook.ui.viewmodel.SettingsViewModel
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch

private const val TAG = "AstryxbookPiP"
private const val ACTION_PIP_TOGGLE = "com.astryx.book.PIP_TOGGLE_PLAYBACK"

// Cap on PIP_HANDOFF_READ_JS answering after screen-off (it took 5-30ms in
// the device spike); the foreground grace period leaves no room to wait.
private const val HANDOFF_READ_TIMEOUT_MS = 300L

// How recently a video must have been playing to count as "playing when PiP started".
private const val PIP_RECENTLY_PLAYING_MS = 3000L

// A FragmentActivity (a ComponentActivity) because BiometricPrompt needs one.
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

    // Drives the CSS-injection focus mode (hide page chrome, make the video
    // fill the viewport) on PiP enter/exit.
    private var isInPipMode by mutableStateOf(false)

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

    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_PIP_TOGGLE) {
                Log.d(TAG, "pipActionReceiver: toggle requested")
                pipToggleTrigger++
            }
        }
    }

    // ---- Lock-screen audio (see LockScreenAudioService) ----
    // While in PiP with the setting on, locking the screen hands the video's
    // audio from the WebView to a native player; coming back hands it back.

    // Bumped at screen-off to run PIP_HANDOFF_READ_JS; the result comes back
    // through onPipHandoffRead.
    private var pipHandoffReadTrigger by mutableIntStateOf(0)

    // Latest handback request for the composable to run (pipHandbackJs).
    private var pipHandback by mutableStateOf<PipHandback?>(null)
    private var pipHandbackSeq = 0

    // A link sent while the app runs (singleTask: onNewIntent) opens in the existing WebView.
    private var openLinkRequest by mutableStateOf<OpenLinkRequest?>(null)

    // Bound at PiP entry (option B: binding creates the service idle, not in
    // the foreground), used at screen-off to start playback and at handback
    // to read the position.
    private var audioController: ListenableFuture<MediaController>? = null

    // Single guard so screen-off never double-starts a handoff and handback
    // never runs twice.
    private var handoffActive = false
    private var handoffReadPending = false
    private var handoffStartMs = 0L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val handoffReadTimeout = Runnable {
        if (handoffReadPending) {
            handoffReadPending = false
            Log.w(TAG, "handoff: page read timed out after ${HANDOFF_READ_TIMEOUT_MS}ms, aborting")
        }
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_OFF) return
            // Observed order on the device spike (SM-A566B, Android 16): the
            // page gets visibilitychange -> hidden first, Facebook pauses the
            // video 25-60ms later, then SCREEN_OFF arrives ~0.5s after that,
            // still in PiP (PiP stays open across lock and unlock), so the
            // live isInPipMode check here is reliable.
            Log.d(TAG, "handoff: SCREEN_OFF inPip=$isInPipMode enabled=${lockscreenAudioEnabled()} active=$handoffActive pending=$handoffReadPending")
            if (!isInPipMode || !lockscreenAudioEnabled() || handoffActive || handoffReadPending) return
            handoffReadPending = true
            pipHandoffReadTrigger++
            mainHandler.postDelayed(handoffReadTimeout, HANDOFF_READ_TIMEOUT_MS)
        }
    }

    private fun lockscreenAudioEnabled(): Boolean =
        settingsVM.pipEnabled.value && settingsVM.pipLockscreenAudio.value

    private fun connectAudioController(): ListenableFuture<MediaController> =
        audioController ?: MediaController.Builder(
            this, SessionToken(this, ComponentName(this, LockScreenAudioService::class.java))
        ).buildAsync().also { audioController = it }

    private fun releaseAudio() {
        audioController?.let { MediaController.releaseFuture(it) }
        audioController = null
        stopService(Intent(this, LockScreenAudioService::class.java))
    }

    private fun onPipHandoffRead(raw: String?) {
        if (!handoffReadPending) {
            // Timed out already; still undo the mute the read applied.
            Log.w(TAG, "handoff: late page read ignored, restoring WebView video")
            requestHandback(positionMs = null, play = false)
            return
        }
        handoffReadPending = false
        mainHandler.removeCallbacks(handoffReadTimeout)

        val read = parseHandoffRead(raw)
        if (read == null || !isHandoffEligible(read.src, read.wasPlaying)) {
            Log.d(TAG, "handoff: not eligible (src=${read?.src?.take(40)}, wasPlaying=${read?.wasPlaying}), aborting")
            requestHandback(positionMs = null, play = false)
            return
        }

        handoffActive = true
        handoffStartMs = read.positionMs
        val item = MediaItem.Builder()
            .setUri(read.src)
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder().setMediaUri(read.src.toUri()).build()
            )
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(getString(R.string.lockscreen_audio_media_title))
                    .build()
            )
            .build()
        val future = connectAudioController()
        // Nothing slow before play(): the system only allows the service's
        // promotion to the foreground for a short grace period after the PiP
        // window stops being visible (~0.6s after screen-off in the spike).
        future.addListener({
            try {
                val controller = future.get()
                // Start position goes in with the item: a separate seekTo()
                // was applied before the item was set and then reset to 0
                // (seen on device: playback always started from 0:00).
                controller.setMediaItem(item, read.positionMs)
                controller.prepare()
                controller.play()
                Log.d(TAG, "handoff: native playback started at ${read.positionMs}ms")
            } catch (e: Exception) {
                Log.e(TAG, "handoff: starting native playback failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Ends an active handoff. Called from whichever signal comes first; the
     * others are no-ops. ACTION_USER_PRESENT isn't used: the system sent it
     * on unlock in the spike, but this receiver never got it.
     */
    private fun handBack(signal: String) {
        if (!handoffActive) return
        handoffActive = false
        val controller = audioController
            ?.takeIf { it.isDone && !it.isCancelled }
            ?.let { runCatching { it.get() }.getOrNull() }
            ?.takeIf { it.isConnected }
        // After a player error (e.g. an expired URL) the player is stopped
        // but keeps its last position, so this is still the right place to
        // resume from.
        val positionMs = controller?.currentPosition ?: handoffStartMs
        // Paused from the lock-screen controls (or stopped by an error):
        // hand back without resuming.
        val play = controller?.playWhenReady == true &&
            (controller.playbackState == Player.STATE_READY ||
                controller.playbackState == Player.STATE_BUFFERING)
        Log.d(TAG, "handback: signal=$signal positionMs=$positionMs play=$play")
        controller?.stop()
        // An empty timeline makes Media3 drop the notification and call
        // stopForeground right away. Without this the service stays in the
        // foreground (Media3 keeps it there for up to 10 minutes after
        // playback stops) because the reconnect below keeps it bound and
        // alive, so stopService alone never destroys it.
        controller?.clearMediaItems()
        releaseAudio()
        requestHandback(positionMs, play)
        // Still in PiP: get ready for the next lock.
        if (isInPipMode && lockscreenAudioEnabled()) connectAudioController()
    }

    private fun requestHandback(positionMs: Long?, play: Boolean) {
        pipHandback = PipHandback(
            seq = ++pipHandbackSeq,
            positionMs = positionMs,
            play = play,
            rearm = isInPipMode && lockscreenAudioEnabled(),
        )
    }
    // ---- end lock-screen audio ----

    // ---- app lock ----
    // While locked, AppLockScreen covers the page and the system prompt asks for the
    // fingerprint/face or the screen lock. The page keeps running underneath (PiP and
    // lock-screen audio are unaffected). Locks on every cold start, and on coming back
    // after being out of sight for the setting's grace period (see shouldLockOnReturn).
    private var isAppLocked by mutableStateOf(false)
    // When the activity was last stopped, on the monotonic clock; null while visible.
    private var hiddenAt: Long? = null
    // The prompt (or the screen-lock confirmation, which is an activity of its own) is up:
    // the stop/start it causes doesn't count as leaving the app.
    private var isAuthenticating = false
    private lateinit var biometricPrompt: BiometricPrompt

    // Android 8.x: the system's screen-lock confirmation (BiometricPrompt's own fallback
    // there is a fingerprint-only dialog).
    private val confirmDeviceCredential =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            isAuthenticating = false
            if (result.resultCode == Activity.RESULT_OK) isAppLocked = false
        }

    private fun createBiometricPrompt() = BiometricPrompt(
        this,
        ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                isAuthenticating = false
                isAppLocked = false
            }

            // Cancelled or failed for good: stay locked; AppLockScreen's button retries.
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                isAuthenticating = false
            }
        }
    )

    private fun lockIfEnabled() {
        if (isAppLockEnabled(settingsVM.appLock.value) && canUseAppLock(this)) isAppLocked = true
    }

    private fun authenticate() {
        if (!isAppLocked || isAuthenticating) return
        // The screen lock was removed since the app locked: nothing could unlock it.
        if (!canUseAppLock(this)) {
            isAppLocked = false
            return
        }
        isAuthenticating = true
        val title = getString(R.string.app_lock_prompt_title, getString(R.string.app_name))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            biometricPrompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setAllowedAuthenticators(AppLock.AUTHENTICATORS)
                    .build()
            )
        } else {
            @Suppress("DEPRECATION")
            val intent = getSystemService(KeyguardManager::class.java)
                ?.createConfirmDeviceCredentialIntent(title, null)
            if (intent == null) {
                isAuthenticating = false
                isAppLocked = false
            } else {
                confirmDeviceCredential.launch(intent)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isAuthenticating && !isChangingConfigurations) hiddenAt = SystemClock.elapsedRealtime()
    }
    // ---- end app lock ----

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

        biometricPrompt = createBiometricPrompt()
        lockIfEnabled()

        val intentUrl = linkUrl(intent)

        setContent {
            val materialYou by settingsVM.materialYou.collectAsState()
            MaterialbookTheme(dynamicColor = materialYou) {
                // Stacks the lock screen over the page; passes the window's exact size on
                // to the page, as the window itself did before.
                Box(Modifier.fillMaxSize(), propagateMinConstraints = true) {
                    MaterialbookWebView(
                        url = intentUrl
                            ?: "https://facebook.com/",
                        settingsVM = settingsVM,
                        pipToggleTrigger = pipToggleTrigger,
                        pipEnteringTrigger = pipEnteringTrigger,
                        isInPipMode = isInPipMode,
                        pipStartedWhilePlaying = pipStartedWhilePlaying,
                        pipHandoffReadTrigger = pipHandoffReadTrigger,
                        pipHandback = pipHandback,
                        openLinkRequest = openLinkRequest,
                        onPipHandoffRead = ::onPipHandoffRead,
                        onPipPageVisible = { runOnUiThread { handBack("page visible") } },
                        isAppLocked = isAppLocked,
                        onVideoPlayingChanged = { isPlaying, videoWidth, videoHeight ->
                            updateVideoPlaybackState(isPlaying, videoWidth, videoHeight)
                        }
                    )
                    if (isAppLocked) AppLockScreen(onUnlock = ::authenticate)
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
        // With app lock on, Recents shows no snapshot of the page (Android 13+; screenshots
        // still work).
        lifecycleScope.launch {
            settingsVM.appLock.collect { mode ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    setRecentsScreenshotEnabled(!isAppLockEnabled(mode))
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val url = linkUrl(intent) ?: return
        openLinkRequest = OpenLinkRequest((openLinkRequest?.seq ?: 0) + 1, url)
    }

    /**
     * The page an intent asks for: a link shared from another app opens Facebook's share
     * dialog (shared text without a link can't be shared to Facebook's web share, so the app
     * just opens); a link or launcher shortcut opens its page if it's a Facebook one.
     */
    private fun linkUrl(intent: Intent?): String? = if (intent?.action == Intent.ACTION_SEND) {
        facebookShareUrl(intent.getStringExtra(Intent.EXTRA_TEXT)).also {
            if (it == null) Toast.makeText(this, R.string.share_only_links, Toast.LENGTH_LONG).show()
        }
    } else {
        appLinkUrl(intent?.data?.toString())
    }

    // Handback signals besides the page's own visibilitychange: the activity
    // becoming visible again (unlock while in PiP, or back to full screen).
    override fun onStart() {
        super.onStart()
        handBack("onStart")
        if (!isAuthenticating &&
            shouldLockOnReturn(settingsVM.appLock.value, hiddenAt, SystemClock.elapsedRealtime())
        ) {
            lockIfEnabled()
        }
        hiddenAt = null
    }

    override fun onResume() {
        super.onResume()
        handBack("onResume")
        if (isAppLocked && !isInPictureInPictureMode) authenticate()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(pipActionReceiver)
        unregisterReceiver(screenOffReceiver)
        mainHandler.removeCallbacks(handoffReadTimeout)
        handoffActive = false
        releaseAudio()
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

        if (isInPictureInPictureMode) {
            if (lockscreenAudioEnabled()) connectAudioController()
        } else if (handoffActive) {
            handBack("PiP exit")
        } else {
            releaseAudio()
        }

        // Facebook's player pauses the video the instant PiP resizes the page
        // (the keep-playing guard in MaterialbookWV resumes it shortly after),
        // but our JS detector only reports state on its own schedule — without
        // this, the Play/Pause button can briefly show the wrong icon (still
        // "Pause" right when it should already say "Play"). Flip + rebuild
        // the action immediately rather than waiting for the next JS report.
        if (isInPictureInPictureMode && isVideoPlaying) {
            isVideoPlaying = false
            Log.d(TAG, "onPictureInPictureModeChanged: immediate icon flip to Play")
            setPictureInPictureParams(
                pipParamsBuilder()
                    .setAspectRatio(currentAspectRatio)
                    .setActions(listOf(buildPlayPauseAction(false)))
                    .build()
            )
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
            setPictureInPictureParams(
                pipParamsBuilder()
                    .setAspectRatio(currentAspectRatio)
                    .setAutoEnterEnabled(autoEnter)
                    .setActions(listOf(buildPlayPauseAction(isVideoPlaying)))
                    .build()
            )
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
            currentAspectRatio = settingsVM.pipRationalForVideo(videoWidth, videoHeight)
        }
        Log.d(TAG, "updateVideoPlaybackState: isPlaying=$isPlaying, ${videoWidth}x$videoHeight, pipEnabled=${settingsVM.pipEnabled.value}, aspectRatio=$currentAspectRatio")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val autoEnter = isVideoPlaying && settingsVM.pipEnabled.value
            Log.d(TAG, "setPictureInPictureParams: autoEnter=$autoEnter")
            val pipParams = pipParamsBuilder()
                .setAspectRatio(currentAspectRatio)
                .setAutoEnterEnabled(autoEnter)
                .setActions(listOf(buildPlayPauseAction(isPlaying)))
                .build()
            setPictureInPictureParams(pipParams)
        }
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
                enterPictureInPictureMode(
                    pipParamsBuilder()
                        .setAspectRatio(currentAspectRatio)
                        .setActions(listOf(buildPlayPauseAction(isVideoPlaying)))
                        .build()
                )
                Log.d(TAG, "enterPictureInPictureMode called successfully")
            } catch (e: Exception) {
                Log.e(TAG, "enterPictureInPictureMode failed", e)
            }
        }
    }
}
