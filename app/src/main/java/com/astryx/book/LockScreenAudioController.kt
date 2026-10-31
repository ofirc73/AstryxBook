package com.astryx.book
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
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
import org.json.JSONObject
import kotlin.math.abs

private const val TAG = "AstryxbookPiP"
// Cap on PIP_HANDOFF_READ_JS answering after screen-off (it took 5-30ms in
// the device spike); the foreground grace period leaves no room to wait.
private const val HANDOFF_READ_TIMEOUT_MS = 300L
// The page gets in step with the native player within seconds (pipHandbackJs gives up after 8 s);
// past this the native player stops anyway.
private const val HANDBACK_TIMEOUT_MS = 10_000L
// The silent native player is moved to the page past this off (a loop, a stall), a little ahead
// (seeking takes it a moment); closer, it plays at the page's own speed, a little faster or slower
// until in step. Moved at the lock (a short gap) past HANDOFF_DRIFT_MS: closer, the sound skips that
// little rather than stopping for a moment.
private const val SYNC_MOVE_MS = 500L
private const val SYNC_MOVE_LEAD_MS = 100L
private const val HANDOFF_DRIFT_MS = 300L

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
    // The native player plays on while the page gets in step with it (pipHandbackJs), until the
    // page's 'handbackDone'.
    private var handbackPending = false
    // The PiP video's sound, which the native player plays silently in step with the page while in
    // PiP (sync), so that the lock only turns it up: started at the lock, it came 0.13 s after the
    // page stopped, then the phone's output latency, a break heard on a Galaxy A56 (and 0.5 s more
    // to load it then, on the emulator).
    private var preparedSrc: String? = null
    // The page's last sync (its time and when, elapsedRealtime), for its speed: the emulator's
    // WebView played the PiP video at 0.84x, and kept in step by moving it each second, the native
    // player's audio output was made anew each time.
    private var lastSyncTimeMs = -1L
    private var lastSyncAt = 0L
    // Syncs in a row with the page paused: the native player pauses at the second. Facebook's
    // players pause the PiP video for a moment now and then (the keep-playing guard plays it
    // again), and following each, the native player was paused at the lock now and then (a gap).
    private var pausedSyncs = 0

    private val mainHandler = Handler(Looper.getMainLooper())
    // Lazy: the controller is made with the activity, before it has a context.
    private val powerManager by lazy { activity.getSystemService(PowerManager::class.java) }
    private val handbackTimeout = Runnable {
        Log.w(TAG, "handback: the page never got in step, stopping the native player")
        finishHandback()
    }
    private val handoffReadTimeout = Runnable {
        if (handoffReadPending) {
            handoffReadPending = false
            Log.w(TAG, "handoff: page read timed out after ${HANDOFF_READ_TIMEOUT_MS}ms, aborting")
        }
    }

    /** From the activity's SCREEN_OFF receiver: starts a hand-off unless the page's 'hidden' did. */
    fun onScreenOff() = startHandoff("SCREEN_OFF")

    /**
     * The page's handoff events (PIP_HANDOFF_ARM_JS, pipHandbackJs) with their [data] (JSON):
     * 'sync' keeps the silent native player in step with the PiP video, 'hidden' starts a hand-off
     * when the screen is going off, 'visible' is a handback signal, 'handbackDone' stops the
     * native player.
     */
    fun onHandoffEvent(event: String, data: String) {
        when (event) {
            "sync" -> runCatching { JSONObject(data) }.getOrNull()?.let {
                sync(it.optString("src"), (it.optDouble("time", 0.0) * 1000).toLong(), it.optBoolean("playing"))
            }
            // Hidden in PiP for something else (the PiP window closing) leaves the screen on.
            "hidden" -> if (powerManager?.isInteractive == false) startHandoff("page hidden")
                else Log.d(TAG, "handoff: page hidden with the screen on, no handoff")
            "visible" -> handBack("page visible")
            "handbackDone" -> finishHandback()
        }
    }

    // Starts a hand-off by reading the page's video. Observed order on the device spike (SM-A566B,
    // Android 16): the page gets visibilitychange -> hidden first, Facebook pauses the video
    // 25-60ms later, then SCREEN_OFF arrives ~0.5s after that, still in PiP (PiP stays open across
    // lock and unlock), so the live isInPip() check here is reliable. The page's 'hidden' starts
    // it at once, the native player then filling those 0.5 s (a break before).
    private fun startHandoff(signal: String) {
        Log.d(TAG, "handoff: $signal inPip=${isInPip()} enabled=${enabled()} active=$handoffActive pending=$handoffReadPending")
        if (!isInPip() || !enabled() || handoffActive || handoffReadPending || handbackPending) return
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
        mainHandler.removeCallbacks(handbackTimeout)
        handbackPending = false
        handoffActive = false
        releaseAudio()
    }

    private fun connectAudioController(): ListenableFuture<MediaController> =
        audioController ?: MediaController.Builder(
            activity, SessionToken(activity, ComponentName(activity, LockScreenAudioService::class.java))
        ).buildAsync().also { audioController = it }

    // The native player's controller, when it's connected.
    private fun connectedController(): MediaController? = audioController
        ?.takeIf { it.isDone && !it.isCancelled }
        ?.let { runCatching { it.get() }.getOrNull() }
        ?.takeIf { it.isConnected }

    private fun releaseAudio() {
        audioController?.let { MediaController.releaseFuture(it) }
        audioController = null
        preparedSrc = null
        activity.stopService(Intent(activity, LockScreenAudioService::class.java))
    }

    // Plays [src] silently at the page's [timeMs] while it's [playing] (LockScreenAudioService
    // neither takes the audio focus, which made Chromium pause the page, nor shows a notification
    // until it's heard).
    private fun sync(src: String, timeMs: Long, playing: Boolean) {
        if (!isInPip() || !enabled() || handoffActive || handoffReadPending || handbackPending ||
            !isHandoffEligible(src, wasPlaying = true)
        ) return
        val future = connectAudioController()
        future.addListener({
            val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
            if (handoffActive || handoffReadPending || handbackPending) return@addListener
            controller.volume = 0f
            val now = SystemClock.elapsedRealtime()
            if (src != preparedSrc || controller.playbackState == Player.STATE_IDLE) {
                preparedSrc = src
                controller.setMediaItem(soundItem(src), timeMs)
                controller.prepare()
                Log.d(TAG, "handoff: the sound plays silently along")
            } else if (playing) {
                val offMs = controller.currentPosition - timeMs
                if (abs(offMs) > SYNC_MOVE_MS) {
                    Log.d(TAG, "handoff: silent sound moved to the page (${offMs}ms off)")
                    controller.seekTo(timeMs + SYNC_MOVE_LEAD_MS)
                    controller.setPlaybackSpeed(1f)
                } else {
                    // The page's speed since the last sync (1 across a loop or a pause), less half of
                    // the gap per second.
                    val pageSpeed = if (lastSyncTimeMs in 0 until timeMs && now > lastSyncAt) {
                        (timeMs - lastSyncTimeMs).toFloat() / (now - lastSyncAt)
                    } else 1f
                    controller.setPlaybackSpeed((pageSpeed - offMs / 2000f).coerceIn(0.5f, 1.5f))
                }
            }
            lastSyncTimeMs = if (playing) timeMs else -1L
            lastSyncAt = now
            pausedSyncs = if (playing) 0 else pausedSyncs + 1
            controller.playWhenReady = pausedSyncs < 2
        }, ContextCompat.getMainExecutor(activity))
    }

    private fun soundItem(src: String) = MediaItem.Builder()
        .setUri(src)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(src.toUri()).build())
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(activity.getString(R.string.lockscreen_audio_media_title)).build()
        )
        .build()

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
        val future = connectAudioController()
        // Nothing slow before play(): the system only allows the service's
        // promotion to the foreground for a short grace period after the PiP
        // window stops being visible (~0.6s after screen-off in the spike).
        future.addListener({
            try {
                val controller = future.get()
                // Playing silently along (sync), unless that failed (the player is then idle).
                val loaded = read.src == preparedSrc && controller.playbackState != Player.STATE_IDLE
                val offMs = controller.currentPosition - read.positionMs
                // Playing: set to and ready (isPlaying, the controller's estimate, read false at times).
                val playing = controller.playWhenReady && controller.playbackState == Player.STATE_READY
                val inStep = loaded && playing && abs(offMs) <= HANDOFF_DRIFT_MS
                controller.setPlaybackSpeed(1f)
                if (inStep) {
                    // Just turned up, below.
                } else if (loaded) {
                    controller.seekTo(read.positionMs)
                } else {
                    // Start position goes in with the item: a separate seekTo()
                    // was applied before the item was set and then reset to 0
                    // (seen on device: playback always started from 0:00).
                    controller.setMediaItem(soundItem(read.src), read.positionMs)
                    controller.prepare()
                }
                controller.volume = 1f
                controller.play()
                Log.d(TAG, "handoff: native playback heard at ${read.positionMs}ms (in step: $inStep, " +
                    "${offMs}ms off, playing: $playing, loaded before: $loaded)")
            } catch (e: Exception) {
                Log.e(TAG, "handoff: starting native playback failed", e)
            }
        }, ContextCompat.getMainExecutor(activity))
    }

    /**
     * Ends an active handoff. Called from whichever signal comes first; the
     * others are no-ops. ACTION_USER_PRESENT isn't used: the system sent it
     * on unlock in the spike, but this receiver never got it. A playing native
     * player plays on until the page plays in step with it ('handbackDone'):
     * stopped first, the page's video took 1.7 s to play again (Facebook's
     * seek, on the emulator), a break.
     */
    fun handBack(signal: String) {
        if (!handoffActive) return
        handoffActive = false
        val controller = connectedController()
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
        handbackPending = true
        requestHandback(positionMs, play)
        if (play) mainHandler.postDelayed(handbackTimeout, HANDBACK_TIMEOUT_MS) else finishHandback()
    }

    // Stops the native player once the page has it back.
    private fun finishHandback() {
        if (!handbackPending) return
        handbackPending = false
        mainHandler.removeCallbacks(handbackTimeout)
        val controller = connectedController()
        Log.d(TAG, "handback: done, native player stopped at ${controller?.currentPosition}ms")
        controller?.stop()
        // An empty timeline makes Media3 drop the notification and call
        // stopForeground right away. Without this the service stays in the
        // foreground (Media3 keeps it there for up to 10 minutes after
        // playback stops) because the reconnect below keeps it bound and
        // alive, so stopService alone never destroys it.
        controller?.clearMediaItems()
        releaseAudio()
        // Still in PiP: get ready for the next lock.
        if (isInPip() && enabled()) connectAudioController()
    }

    private fun requestHandback(positionMs: Long?, play: Boolean) {
        handback = PipHandback(
            seq = ++handbackSeq,
            positionMs = positionMs,
            positionAtMs = System.currentTimeMillis(),
            play = play,
            rearm = isInPip() && enabled(),
        )
    }
}
