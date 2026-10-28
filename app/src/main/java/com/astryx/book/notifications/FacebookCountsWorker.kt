package com.astryx.book.notifications
import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.eepiemi.materialbook.MainActivity
import com.eepiemi.materialbook.R
import com.eepiemi.materialbook.data.local.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TAG = "Astryxbook"
private const val WORK_NAME = "facebook_counts"
private const val CHANNEL_ID = "facebook_counts"
private const val PAGE_URL = "https://m.facebook.com/notifications/"
private const val COOKIE_URL = "https://m.facebook.com"
private const val TIMEOUT_MS = 20_000

/**
 * "Facebook notifications" (Settings): every 15 minutes (Android's shortest period for
 * background work), while there's a network, reads the unread counters on Facebook's tab bar
 * from m.facebook.com/notifications/, fetched with the app's own Facebook login (the WebView's
 * cookies), and notifies of new notifications, messages and friend requests. Count only: the
 * labels it reads are the same in every language, unlike the notifications' own markup.
 *
 * While the app is on screen the counts are only recorded, not notified: the user sees them on
 * the page. The last counts are kept in their own preferences file (not a setting).
 */
class FacebookCountsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val html = withContext(Dispatchers.IO) { fetchPage(applicationContext) } ?: return Result.success()
        val counts = FacebookCounts.parse(html) ?: run {
            Log.d(TAG, "Facebook counts: no tab bar in the page (${html.length} chars)")
            return Result.success()
        }
        val recordOnly = inputData.getBoolean(KEY_RECORD_ONLY, false)
        // "Show what they say": the newest notifications' text, from the page already fetched.
        val texts = if (SettingsDataStore(applicationContext).facebookNotificationText.first()) {
            val unread = counts[FacebookCounter.NOTIFICATIONS]?.count ?: 0
            FacebookCounts.parseNotificationTexts(html, minOf(unread, MAX_TEXT_LINES))
        } else {
            emptyList()
        }
        applyCounts(applicationContext, counts, notify = !recordOnly && !AppVisibility.onScreen, texts)
        return Result.success()
    }

    companion object {
        private const val KEY_RECORD_ONLY = "record_only"
        // Lines an expanded notification shows (Android's inbox style shows up to about five).
        private const val MAX_TEXT_LINES = 5

        /**
         * The app left the screen on a page without Facebook's tab bar (Feeds, a profile): fetch
         * the counts once, now, only to record them, so the next checks compare with what the
         * user last had in front of them.
         */
        fun recordCountsNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<FacebookCountsWorker>()
                .setInputData(workDataOf(KEY_RECORD_ONLY to true))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("$WORK_NAME-record", ExistingWorkPolicy.REPLACE, request)
        }

        /** The counts the user leaves the app with, read from the page's tab bar, or [recordCountsNow]. */
        fun onAppHidden(context: Context, tabLabels: List<String>?) {
            val counts = tabLabels?.let { FacebookCounts.parseLabels(it) }
            if (counts != null) applyCounts(context, counts, notify = false) else recordCountsNow(context)
        }

        /** Starts or stops the checks; called whenever the setting is read or changed. */
        fun schedule(context: Context, enabled: Boolean) {
            val workManager = WorkManager.getInstance(context)
            if (!enabled) {
                workManager.cancelUniqueWork(WORK_NAME)
                clearAll(context)
                return
            }
            val request = PeriodicWorkRequestBuilder<FacebookCountsWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            // UPDATE, not KEEP: the job saved by an older version keeps its schedule but takes this
            // request's worker class (the class moved from com.eepiemi.materialbook to
            // com.astryx.book in v1.36.5; a kept job named the old class and failed to start).
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** The app came on screen: what the notifications said is on the page now. */
        fun onAppShown(context: Context) {
            val manager = NotificationManagerCompat.from(context)
            FacebookCounter.entries.forEach { manager.cancel(it.notificationId) }
        }

        private fun clearAll(context: Context) {
            onAppShown(context)
            store(context).edit { clear() }
        }

        private fun store(context: Context) =
            context.getSharedPreferences("facebook_counts", Context.MODE_PRIVATE)

        // The page, as the app's WebView would get it: its cookies (the Facebook login) and user
        // agent. Cookies Facebook sets in the answer go back to the WebView's store, as a
        // browser would keep them. Null when logged out or on any error.
        internal fun fetchPage(context: Context): String? = runCatching {
            val cookies = CookieManager.getInstance()
            val cookie = cookies.getCookie(COOKIE_URL)
            if (cookie == null || !cookie.contains("c_user=")) {
                Log.d(TAG, "Facebook counts: not logged in")
                return null
            }
            val connection = URL(PAGE_URL).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Cookie", cookie)
                connection.setRequestProperty("User-Agent", WebSettings.getDefaultUserAgent(context))
                // The headers WebView sends when it opens a page; with only a few of them,
                // Facebook answers 400.
                connection.setRequestProperty(
                    "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                )
                connection.setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag())
                connection.setRequestProperty("Upgrade-Insecure-Requests", "1")
                connection.setRequestProperty("Sec-Fetch-Dest", "document")
                connection.setRequestProperty("Sec-Fetch-Mode", "navigate")
                connection.setRequestProperty("Sec-Fetch-Site", "none")
                connection.setRequestProperty("Sec-Fetch-User", "?1")
                connection.setRequestProperty("Sec-CH-UA-Mobile", "?1")
                connection.setRequestProperty("Sec-CH-UA-Platform", "\"Android\"")
                connection.setRequestProperty("X-Requested-With", context.packageName)
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "Facebook counts: HTTP ${connection.responseCode}")
                    return null
                }
                connection.headerFields["Set-Cookie"]?.forEach { cookies.setCookie(COOKIE_URL, it) }
                cookies.flush()
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }.onFailure { Log.w(TAG, "Facebook counts: fetch failed", it) }.getOrNull()

        /**
         * Records [counts] and, with [notify], posts, updates or clears the notifications;
         * [notificationTexts] (newest first) go into the one for Facebook's notifications.
         */
        internal fun applyCounts(
            context: Context,
            counts: Map<FacebookCounter, UnreadCount>,
            notify: Boolean,
            notificationTexts: List<String> = emptyList(),
        ) {
            val prefs = store(context)
            counts.forEach { (counter, now) ->
                val before = prefs.getString(counter.name, null)?.let { stored ->
                    UnreadCount(stored.removeSuffix("+").toIntOrNull() ?: 0, stored.endsWith("+"))
                }
                if (notify) {
                    when (val action = FacebookCounts.action(before, now)) {
                        is CountAction.Notify -> post(context, counter, action.unread, alert = true, linesFor(counter, notificationTexts))
                        is CountAction.Update -> post(context, counter, action.unread, alert = false, linesFor(counter, notificationTexts))
                        CountAction.Clear -> NotificationManagerCompat.from(context).cancel(counter.notificationId)
                        CountAction.Nothing -> Unit
                    }
                }
                prefs.edit { putString(counter.name, now.label) }
            }
            Log.d(TAG, "Facebook counts: ${counts.entries.joinToString { "${it.key.tab}=${it.value.label}" }}, notify=$notify")
        }

        private fun linesFor(counter: FacebookCounter, notificationTexts: List<String>) =
            if (counter == FacebookCounter.NOTIFICATIONS) notificationTexts else emptyList()

        private fun post(
            context: Context,
            counter: FacebookCounter,
            unread: UnreadCount,
            alert: Boolean,
            lines: List<String> = emptyList(),
        ) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return
            val manager = NotificationManagerCompat.from(context)
            // Updating a notification that isn't showing (the user swiped it away) would bring it
            // back without anything new.
            if (!alert && manager.activeNotifications.none { it.id == counter.notificationId }) return
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.facebook_notifications_channel))
                    .build()
            )
            val open = PendingIntent.getActivity(
                context, counter.notificationId,
                Intent(Intent.ACTION_VIEW, counter.url.toUri(), context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val text = context.resources.getQuantityString(textFor(counter), unread.count, unread.label)
            fun builder() = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setOnlyAlertOnce(!alert)
                .setCategory(
                    if (counter == FacebookCounter.MESSAGES) NotificationCompat.CATEGORY_MESSAGE
                    else NotificationCompat.CATEGORY_SOCIAL
                )
                .setNumber(unread.count)
            val notification = if (lines.isEmpty()) {
                builder().build()
            } else {
                // What the notifications say is private: the lock screen shows the count only.
                builder()
                    .setContentText(lines.first())
                    .setStyle(NotificationCompat.InboxStyle().also { style -> lines.forEach { style.addLine(it) } })
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(builder().build())
                    .build()
            }
            runCatching { manager.notify(counter.notificationId, notification) }
                .onFailure { Log.w(TAG, "Facebook counts: notify failed", it) }
        }

        private fun textFor(counter: FacebookCounter) = when (counter) {
            FacebookCounter.NOTIFICATIONS -> R.plurals.facebook_new_notifications
            FacebookCounter.MESSAGES -> R.plurals.facebook_new_messages
            FacebookCounter.FRIEND_REQUESTS -> R.plurals.facebook_new_friend_requests
        }
    }
}

/** Whether MainActivity is started (on screen, or in PiP), for the counts worker. */
object AppVisibility {
    @Volatile
    var onScreen = false
}
