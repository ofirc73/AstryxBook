package com.astryx.book
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.astryx.book.audio.LockScreenAudioService
import com.astryx.book.audio.PipHandback
import com.astryx.book.audio.isHandoffEligible
import com.astryx.book.audio.parseHandoffRead
import com.eepiemi.materialbook.R
import com.google.common.util.concurrent.ListenableFuture

private const val TAG = "AstryxbookPiP"
// Cap on PIP_HANDOFF_READ_JS answering after screen-off (it took 5-30ms in
// the device spike); the foreground grace period leaves no room to wait.
private const val HANDOFF_READ_TIMEOUT_MS = 300L

/**
 * Lock-screen audio (see LockScreenAudioService) for [activity]: while in PiP ([isInPip]) with the
 * setting on ([enabled]), locking the screen hands the video's audio from the WebView to a native
 * player, and coming back hands it back. MaterialbookWebView runs the page's side: it reads the
 * video when [readTrigger] changes (answering [onPageRead]) and runs each [handback]. Covered by
 * MainActivityPipTest (locking and unlocking in PiP) and PipHandoffJsTest.
 */
class LockScreenAudioController(
    private val activity: ComponentActivity,
    private val isInPip: () -> Boolean,
    private val enabled: () -> Boolean,
) {
    // Bumped at screen-off to run PIP_HANDOFF_READ_JS; the result comes back
    // through onPageRead.
    var readTrigger by mutableIntStateOf(0)
        private set

    // Latest handback request for the composable to run (pipHandbackJs).
    var handback by mutableStateOf<PipHandback?>(null)
        private set
    private var handbackSeq = 0

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

    /** From the activity's SCREEN_OFF receiver: starts a hand-off, by reading the page's video. */
    fun onScreenOff() {
        // Observed order on the device spike (SM-A566B, Android 16): the
        // page gets visibilitychange -> hidden first, Facebook pauses the
        // video 25-60ms later, then SCREEN_OFF arrives ~0.5s after that,
        // still in PiP (PiP stays open across lock and unlock), so the
        // live isInPip() check here is reliable.
        Log.d(TAG, "handoff: SCREEN_OFF inPip=$isInPip() enabled=${enabled()} active=$handoffActive pending=$handoffReadPending")
        if (!isInPip() || !enabled() || handoffActive || handoffReadPending) return
        handoffReadPending = true
        readTrigger++
        mainHandler.postDelayed(handoffReadTimeout, HANDOFF_READ_TIMEOUT_MS)
    }

    /** From onPictureInPictureModeChanged. */
    fun onPipModeChanged(inPip: Boolean) {
        if (inPip) {
            if (enabled()) connectAudioController()
        } else if (handoffActive) {
            handBack("PiP exit")
        } else {
            releaseAudio()
        }
    }

    /** From onDestroy. */
    fun onDestroy() {
        mainHandler.removeCallbacks(handoffReadTimeout)
        handoffActive = false
        releaseAudio()
    }

    private fun connectAudioController(): ListenableFuture<MediaController> =
        audioController ?: MediaController.Builder(
            activity, SessionToken(activity, ComponentName(activity, LockScreenAudioService::class.java))
        ).buildAsync().also { audioController = it }

    private fun releaseAudio() {
        audioController?.let { MediaController.releaseFuture(it) }
        audioController = null
        activity.stopService(Intent(activity, LockScreenAudioService::class.java))
    }

    /** The page's answer to PIP_HANDOFF_READ_JS (MaterialbookWebView's onPipHandoffRead). */
    fun onPageRead(raw: String?) {
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
                    .setTitle(activity.getString(R.string.lockscreen_audio_media_title))
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
        }, ContextCompat.getMainExecutor(activity))
    }

    /**
     * Ends an active handoff. Called from whichever signal comes first; the
     * others are no-ops. ACTION_USER_PRESENT isn't used: the system sent it
     * on unlock in the spike, but this receiver never got it.
     */
    fun handBack(signal: String) {
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
        if (isInPip() && enabled()) connectAudioController()
    }

    private fun requestHandback(positionMs: Long?, play: Boolean) {
        handback = PipHandback(
            seq = ++handbackSeq,
            positionMs = positionMs,
            play = play,
            rearm = isInPip() && enabled(),
        )
    }
}
