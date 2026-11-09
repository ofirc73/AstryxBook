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
import androidx.compose.runtime.mutableStateOf
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
// Default importance, for an icon in the status bar while posting as the Facebook app shows, but
// without a sound. Its low-importance predecessor (stories only, then posts) showed only in the
// shade's Silent section, which went unseen on the phone (2026-11-03); Android won't raise a
// channel's importance, so it's a new channel and the old one is deleted.
private const val CHANNEL_ID = "posting"
private const val OLD_CHANNEL_ID = "story_posting"
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

    // A service started with startForegroundService must call startForeground, or Android ends the
    // app ("did not then call Service.startForeground()"): a posting that ended before this ran
    // (started and posted within a moment) still goes into the foreground, then out at once.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val showing = Posting.showing()
        val kind = showing ?: Posting.Kind.POST
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
            Posting.foreground = true
        }.onFailure {
            // Only started from a tap on Share or Post, with the app on screen; never expected.
            Log.w(TAG, "Posting: foreground start failed", it)
            stopSelf()
        }
        if (showing == null) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Posting.foreground = false
        super.onDestroy()
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

    /** What a tap on "Your story is posted" opens MainActivity with: the user's own story. */
    const val ACTION_VIEW_STORY = "com.astryx.book.action.VIEW_STORY"

    /**
     * The action of a notification tap (takeTap) MaterialbookWebView hasn't acted on yet: the
     * story creator shows again (ACTION_SHOW) or the user's own stories open (ACTION_VIEW_STORY),
     * once (a count kept over from an earlier tap opened them again on every new screen).
     */
    var tapPending by mutableStateOf<String?>(null)

    /** Keeps [intent]'s action for MaterialbookWebView if it's a notification tap's: true if it is. */
    fun takeTap(intent: Intent): Boolean {
        if (intent.action != ACTION_SHOW && intent.action != ACTION_VIEW_STORY) return false
        tapPending = intent.action
        return true
    }

    /** What's posting now (the latest), for the bar over the page (PostingBar); null when nothing is. */
    var current by mutableStateOf<Kind?>(null)
        private set

    /** The last posting Facebook said is posted, and when (for the snackbar and the stories row). */
    data class Posted(val kind: Kind, val at: Long)

    var lastPosted by mutableStateOf<Posted?>(null)
        private set

    // What's posting now, latest last: the ongoing notification shows the latest.
    private val active = mutableListOf<Kind>()

    // PostingService has called startForeground (and isn't destroyed yet): only then may it be stopped
    // from outside; before that it stops itself, once in the foreground.
    @Volatile
    internal var foreground = false

    @Synchronized
    internal fun showing(): Kind? = active.lastOrNull()

    /** [kind] started posting: the ongoing notification, until [ended]. */
    fun started(context: Context, kind: Kind) {
        Log.d(TAG, "Posting: ${kind.name.lowercase()} started")
        synchronized(this) { active.remove(kind); active += kind }
        current = showing()
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
        current = showing()
        if (outcome == Outcome.POSTED) lastPosted = Posted(kind, System.currentTimeMillis())
        if (left || !foreground) startService(context) else context.stopService(Intent(context, PostingService::class.java))
        if (!notify || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val text = when (outcome) {
            Outcome.POSTED -> kind.posted
            Outcome.FAILED -> kind.failed ?: return
            Outcome.INTERRUPTED -> kind.interrupted
        }
        // A posted story's notification opens it.
        val action = if (kind == Kind.STORY && outcome == Outcome.POSTED) ACTION_VIEW_STORY else kind.action
        val notification = builder(context, kind, action)
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

    /** The channel a story's and a post's notifications go to, called [name]; the old one deleted. */
    internal fun createChannel(notifications: NotificationManagerCompat, name: CharSequence) {
        notifications.deleteNotificationChannel(OLD_CHANNEL_ID)
        notifications.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(name)
                .setSound(null, null)
                .setVibrationEnabled(false)
                .build()
        )
    }

    internal fun builder(context: Context, kind: Kind, action: String? = kind.action): NotificationCompat.Builder {
        createChannel(NotificationManagerCompat.from(context), context.getString(R.string.posting_channel))
        val open = PendingIntent.getActivity(
            context, kind.outcomeId,
            Intent(action, null, context, MainActivity::class.java),
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
