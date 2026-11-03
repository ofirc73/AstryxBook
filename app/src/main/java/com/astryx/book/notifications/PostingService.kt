package com.astryx.book.notifications
import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.eepiemi.materialbook.MainActivity
import com.eepiemi.materialbook.R

private const val TAG = "Astryxbook"
// The channel's id from when it was for stories only: kept, so users' channel settings stay.
private const val CHANNEL_ID = "story_posting"
private const val POSTING_NOTIFICATION_ID = 2101

/**
 * A story or a post Facebook's page is uploading, as the Facebook app does it: the page goes on
 * uploading in its WebView (the Messages layer's story creator, kept out of sight; the main view's
 * feed, whose WebSocket carries a post's photos) while the user browses or leaves the app, and this
 * foreground service shows "Posting your story" or "Posting your post" in the notification shade
 * meanwhile. Being in the foreground also keeps Android from ending the app's process, and the
 * upload with it, while the app is out of sight: a post whose photo was still going up was lost
 * when the app was closed (emulator, 2026-11-03). [Posting.ended] stops it.
 */
class PostingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val kind = Posting.showing() ?: run { stopSelf(); return START_NOT_STICKY }
        val notification = Posting.builder(this, kind)
            .setContentTitle(getString(kind.posting))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        runCatching {
            ServiceCompat.startForeground(
                this, POSTING_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        }.onFailure {
            // Only started from a tap on Share or Post, with the app on screen; never expected.
            Log.w(TAG, "Posting: foreground start failed", it)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    // Android 15+ gives a data-sync service 6 hours a day: the page goes on posting without it.
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }
}

object Posting {
    /** What's posting, with what its notifications say; a tap on them opens MainActivity with [action]. */
    enum class Kind(val posting: Int, val posted: Int, val failed: Int?, val interrupted: Int, val outcomeId: Int, val action: String?) {
        // The story creator in the Messages layer (story_posting.js): a tap shows it again.
        STORY(R.string.story_posting, R.string.story_posted, R.string.story_post_failed, R.string.story_post_interrupted, 2102, ACTION_SHOW),
        // A post from the main view's composer (feed_posting.js): a tap brings the app back.
        // No failure: nothing found on the page tells one (a cut connection, Facebook carried on after).
        POST(R.string.post_posting, R.string.post_posted, null, R.string.post_post_interrupted, 2103, null),
    }

    /** How a posting ended, for its notification. */
    enum class Outcome {
        POSTED,
        FAILED,
        // The page went away while posting (the app was closed, the page's renderer was lost).
        INTERRUPTED,
    }

    /** What a tap on a story's notifications opens MainActivity with: the creator, if it's kept hidden. */
    const val ACTION_SHOW = "com.astryx.book.action.SHOW_STORY"

    /** Taps on a story's notifications (MainActivity, ACTION_SHOW): MaterialbookWebView shows the creator again. */
    var showRequests by mutableIntStateOf(0)

    // What's posting now, latest last: the ongoing notification shows the latest.
    private val active = mutableListOf<Kind>()

    @Synchronized
    internal fun showing(): Kind? = active.lastOrNull()

    /** [kind] started posting: the ongoing notification, until [ended]. */
    fun started(context: Context, kind: Kind) {
        Log.d(TAG, "Posting: ${kind.name.lowercase()} started")
        synchronized(this) { active.remove(kind); active += kind }
        startService(context)
        NotificationManagerCompat.from(context).cancel(kind.outcomeId)
    }

    /**
     * [kind]'s posting ended: its ongoing notification goes (the other kind's shows, if it's still
     * posting), and [notify] (if any) is said in its place.
     */
    fun ended(context: Context, kind: Kind, outcome: Outcome, notify: Boolean) {
        Log.d(TAG, "Posting: ${kind.name.lowercase()} ${outcome.name.lowercase()}")
        val left = synchronized(this) { active.remove(kind); active.isNotEmpty() }
        if (left) startService(context) else context.stopService(Intent(context, PostingService::class.java))
        if (!notify || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val text = when (outcome) {
            Outcome.POSTED -> kind.posted
            Outcome.FAILED -> kind.failed ?: return
            Outcome.INTERRUPTED -> kind.interrupted
        }
        val notification = builder(context, kind)
            .setContentTitle(context.getString(text))
            .setAutoCancel(true)
            .setCategory(if (outcome == Outcome.POSTED) NotificationCompat.CATEGORY_SOCIAL else NotificationCompat.CATEGORY_ERROR)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(kind.outcomeId, notification) }
            .onFailure { Log.w(TAG, "Posting: notify failed", it) }
    }

    private fun startService(context: Context) {
        runCatching { ContextCompat.startForegroundService(context, Intent(context, PostingService::class.java)) }
            .onFailure { Log.w(TAG, "Posting: service not started", it) }
    }

    internal fun builder(context: Context, kind: Kind): NotificationCompat.Builder {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.posting_channel))
                .build()
        )
        val open = PendingIntent.getActivity(
            context, kind.outcomeId,
            Intent(kind.action, null, context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
    }
}

/**
 * What a page's posting start calls: [Posting.started] for [kind], and the notification
 * permission asked for (the notifications are the only sign of how it went once out of sight), or,
 * with it, for a story (whose creator goes out of sight) a toast saying it posts in the background;
 * a post has Facebook's own "Uploading your post..." on screen.
 */
@Composable
fun rememberPostingStart(kind: Posting.Kind): () -> Unit {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return {
        Posting.started(context, kind)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else if (kind == Posting.Kind.STORY) Toast.makeText(context, kind.posting, Toast.LENGTH_SHORT).show()
    }
}
