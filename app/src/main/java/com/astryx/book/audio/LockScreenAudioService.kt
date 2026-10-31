package com.astryx.book.audio
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.eepiemi.materialbook.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

private const val TAG = "AstryxbookPiP"

/**
 * Plays a PiP video's audio natively while the screen is locked, after
 * MainActivity hands playback off from the WebView at ACTION_SCREEN_OFF.
 *
 * Start strategy: option B from PIP_LOCKSCREEN_AUDIO_PLAN.md, chosen from the
 * device spike (SM-A566B, Android 16). MainActivity binds a MediaController
 * at PiP entry, while the app is visible, which creates this service idle
 * and not in the foreground (so no notification during an ordinary PiP
 * session). In PiP the controller plays the video's sound silently along
 * with the page (volume 0: no notification, no audio focus, onVolumeChanged);
 * at the lock it turns it up, which shows the notification and promotes the
 * service to the foreground. The system allowed that promotion under
 * ACTIVITY_VISIBILITY_GRACE_PERIOD (the PiP window had just stopped being
 * visible), so it must happen right away at the lock, not after anything
 * slow. Option C (foreground for the whole PiP session)
 * also worked but shows a media notification on every PiP; option A failed
 * in the spike.
 */
@OptIn(UnstableApi::class)
class LockScreenAudioService : MediaSessionService() {

    private var session: MediaSession? = null
    // Whether the sound has been heard: playing silently along with the PiP video
    // (LockScreenAudioController sync, volume 0), it shows no notification, which Media3 would show
    // during every PiP, and doesn't take the audio focus, which made Chromium pause the page.
    private var heard = false
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ false)
            // Streams over the network with the screen off.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        // Loops the video, as Facebook's players do: stopping at its end left the lock screen
        // silent, and handing back at the very end made Facebook close the video viewer (the PiP
        // window went black and came back on the feed).
        player.repeatMode = Player.REPEAT_MODE_ONE
        // Audio only: nothing is on screen while locked.
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .build()
        player.addListener(object : Player.Listener {
            // Turned up at the lock: the audio focus taken from then on, and the notification (the
            // lock-screen controls) shown, which also brings the service to the foreground.
            override fun onVolumeChanged(volume: Float) {
                player.setAudioAttributes(audioAttributes, /* handleAudioFocus = */ volume > 0f)
                if (volume > 0f) triggerNotificationUpdate()
            }

            override fun onPlayerError(error: PlaybackException) {
                // Typically the signed fbcdn URL expired during a long lock
                // (403). No refresh in v1: stop, and let handback restore the
                // WebView video from the last known position.
                Log.e(TAG, "LockScreenAudioService: player error ${error.errorCodeName}", error)
                player.stop()
                stopSelf()
            }
        })

        session = MediaSession.Builder(this, player)
            .setCallback(OwnAppOnlyCallback())
            .setSessionActivity(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()
        setListener(object : Listener {
            override fun onForegroundServiceStartNotAllowedException() {
                Log.e(TAG, "LockScreenAudioService: foreground start not allowed")
            }
        })
        Log.d(TAG, "LockScreenAudioService: created")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        if (session.player.playWhenReady && session.player.volume > 0f) heard = true
        if (heard) super.onUpdateNotification(session, startInForegroundRequired)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "LockScreenAudioService: task removed, stopping")
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        Log.d(TAG, "LockScreenAudioService: destroyed")
        super.onDestroy()
    }

    /**
     * The service has to be exported for Media3 controllers to bind, so any
     * app could otherwise connect and play an arbitrary URL through it. Only
     * this app gets full control; trusted system controllers (lock screen,
     * media notification, Bluetooth) get transport controls but can't change
     * what's playing; everyone else is rejected.
     */
    private inner class OwnAppOnlyCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            if (controller.packageName == packageName) {
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
            }
            if (controller.isTrusted) {
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailablePlayerCommands(
                        MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                            .remove(Player.COMMAND_SET_MEDIA_ITEM)
                            .remove(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                            .build()
                    )
                    .build()
            }
            Log.w(TAG, "LockScreenAudioService: rejected controller ${controller.packageName}")
            return MediaSession.ConnectionResult.reject()
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            if (controller.packageName != packageName) {
                return Futures.immediateFailedFuture(
                    UnsupportedOperationException("media items only accepted from this app")
                )
            }
            // A MediaItem's local configuration (its URI) may be dropped when
            // it crosses from controller to session; the URI also travels in
            // requestMetadata, so rebuild it from there when needed.
            return Futures.immediateFuture(
                mediaItems.map { item ->
                    val uri = item.requestMetadata.mediaUri
                    if (item.localConfiguration == null && uri != null) {
                        item.buildUpon().setUri(uri).build()
                    } else {
                        item
                    }
                }.toMutableList()
            )
        }
    }
}
