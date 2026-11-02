package com.astryx.book.notifications
import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.eepiemi.materialbook.MainActivity
import com.eepiemi.materialbook.R

private const val TAG = "Astryxbook"
private const val CHANNEL_ID = "story_posting"
private const val POSTING_NOTIFICATION_ID = 2101
private const val OUTCOME_NOTIFICATION_ID = 2102

/**
 * A story posting from the Messages layer's story creator (story_posting.js), as the Facebook app
 * does it: Facebook's page uploads it in the layer's WebView, which stays while the user hides the
 * creator (Back) or leaves the app, and this foreground service shows "Posting your story" in the
 * notification shade meanwhile. Being in the foreground also keeps Android from ending the app's
 * process, and the upload with it, while the app is out of sight. [StoryPosting.ended] stops it.
 */
class StoryPostingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = StoryPosting.builder(this)
            .setContentTitle(getString(R.string.story_posting))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        runCatching {
            ServiceCompat.startForeground(
                this, POSTING_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        }.onFailure {
            // Only started from a tap on Share, with the app on screen; never expected.
            Log.w(TAG, "Story posting: foreground start failed", it)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    // Android 15+ gives a data-sync service 6 hours a day: the page goes on posting without it.
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }
}

object StoryPosting {
    /** How a story's posting ended, for its notification. */
    enum class Outcome(val text: Int) {
        POSTED(R.string.story_posted),
        FAILED(R.string.story_post_failed),
        // The creator went away while posting (the app was closed, the page's renderer was lost).
        INTERRUPTED(R.string.story_post_interrupted),
    }

    /** What a tap on the notifications opens MainActivity with: the creator, if it's kept hidden. */
    const val ACTION_SHOW = "com.astryx.book.action.SHOW_STORY"

    /** Taps on the notifications (MainActivity, ACTION_SHOW): MaterialbookWebView shows the creator again. */
    var showRequests by mutableIntStateOf(0)

    /** Share to story pressed: the ongoing notification, until [ended]. */
    fun started(context: Context) {
        Log.d(TAG, "Story posting: started")
        runCatching { ContextCompat.startForegroundService(context, Intent(context, StoryPostingService::class.java)) }
            .onFailure { Log.w(TAG, "Story posting: service not started", it) }
        NotificationManagerCompat.from(context).cancel(OUTCOME_NOTIFICATION_ID)
    }

    /** Posting ended: the ongoing notification goes, and [notify] (if any) is said in its place. */
    fun ended(context: Context, outcome: Outcome, notify: Boolean) {
        Log.d(TAG, "Story posting: ${outcome.name.lowercase()}")
        context.stopService(Intent(context, StoryPostingService::class.java))
        if (!notify || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val notification = builder(context)
            .setContentTitle(context.getString(outcome.text))
            .setAutoCancel(true)
            .setCategory(if (outcome == Outcome.POSTED) NotificationCompat.CATEGORY_SOCIAL else NotificationCompat.CATEGORY_ERROR)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(OUTCOME_NOTIFICATION_ID, notification) }
            .onFailure { Log.w(TAG, "Story posting: notify failed", it) }
    }

    internal fun builder(context: Context): NotificationCompat.Builder {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.story_posting_channel))
                .build()
        )
        val open = PendingIntent.getActivity(
            context, POSTING_NOTIFICATION_ID,
            Intent(ACTION_SHOW, null, context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
    }
}
