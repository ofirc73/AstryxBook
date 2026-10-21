package com.eepiemi.materialbook.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eepiemi.materialbook.utils.AppLock
import com.eepiemi.materialbook.utils.KeepScreenOn
import com.eepiemi.materialbook.utils.TextZoom
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "materialbook_prefs")

class SettingsDataStore(private val context: Context) {
    companion object {
        val REMOVE_ADS = booleanPreferencesKey("remove_ads")
        val ENABLE_DOWNLOAD_CONTENT = booleanPreferencesKey("enable_download_content")
        val ENABLE_COPY_TO_CLIPBOARD = booleanPreferencesKey("enable_copy_to_clipboard")
        val DESKTOP_LAYOUT = booleanPreferencesKey("desktop_layout")
        val IMMERSIVE_MODE = booleanPreferencesKey("immersive_mode")
        val STICKY_NAVBAR = booleanPreferencesKey("sticky_navbar")
        val PINCH_TO_ZOOM = booleanPreferencesKey("pinch_to_zoom")
        val MATERIAL_YOU = booleanPreferencesKey("material_you")
        val AMOLED_BLACK = booleanPreferencesKey("amoled_black")
        val HIDE_SUGGESTED = booleanPreferencesKey("hide_suggestion")
        val HIDE_REELS = booleanPreferencesKey("hide_reels")
        val HIDE_STORIES = booleanPreferencesKey("hide_stories")
        val HIDE_PEOPLE_YOU_MAY_KNOW = booleanPreferencesKey("hide_people_you_may_know")
        val HIDE_GROUPS = booleanPreferencesKey("hide_groups")
        val MESSAGES_DESKTOP = booleanPreferencesKey("messages_desktop")
        val PIP_ENABLED = booleanPreferencesKey("pip_enabled")
        val PIP_PORTRAIT_RATIO = stringPreferencesKey("pip_portrait_ratio")
        val PIP_LOCKSCREEN_AUDIO = booleanPreferencesKey("pip_lockscreen_audio")
        val KEEP_SCREEN_ON = stringPreferencesKey("keep_screen_on")
        val APP_LOCK = stringPreferencesKey("app_lock")
        val TEXT_ZOOM = stringPreferencesKey("text_zoom")
        val DEVELOPER_OPTIONS = booleanPreferencesKey("developer_options")
        val WEBVIEW_DEBUGGING = booleanPreferencesKey("webview_debugging")
        val OPEN_LINKS_IN_APP = booleanPreferencesKey("open_links_in_app")
        val TEXT_WRAP_ON_ZOOM = booleanPreferencesKey("text_wrap_on_zoom")
        val START_ON_FEEDS = booleanPreferencesKey("start_on_feeds")
        val APP_LOCK_ON_SCREEN_OFF = booleanPreferencesKey("app_lock_on_screen_off")
        val BLOCK_SCREENSHOTS = booleanPreferencesKey("block_screenshots")
        val CLEAR_CACHE_ON_LEAVE = booleanPreferencesKey("clear_cache_on_leave")
        val FACEBOOK_NOTIFICATIONS = booleanPreferencesKey("facebook_notifications")
        val FACEBOOK_NOTIFICATION_TEXT = booleanPreferencesKey("facebook_notification_text")

        // Legacy: set by the old auto-desktop logic when it wrote desktop_layout
        // on the user's behalf. Only read by the migration below.
        val LEGACY_REVERT_DESKTOP = booleanPreferencesKey("is_revert_desktop")

        /**
         * If the legacy revert flag is set, desktop_layout was written by the
         * old auto logic, not the user: reset it and drop the flag. A
         * desktop_layout without the flag is the user's own choice and stays.
         */
        fun migrateLegacyAutoDesktop(prefs: MutablePreferences) {
            if (prefs[LEGACY_REVERT_DESKTOP] == true) {
                prefs[DESKTOP_LAYOUT] = false
            }
            prefs.remove(LEGACY_REVERT_DESKTOP)
        }
    }

    val prefs = context.dataStore.data

    suspend fun migrateLegacyAutoDesktop() {
        if (prefs.first().contains(LEGACY_REVERT_DESKTOP)) {
            context.dataStore.edit { migrateLegacyAutoDesktop(it) }
        }
    }

    val removeAds = context.dataStore.data.map { it[REMOVE_ADS] ?: true }
    suspend fun setRemoveAds(removeAds: Boolean) {
        context.dataStore.edit { it[REMOVE_ADS] = removeAds }
    }

    val enableDownloadContent = context.dataStore.data.map { it[ENABLE_DOWNLOAD_CONTENT] ?: true }
    suspend fun setEnableDownloadContent(enableDownloadContent: Boolean) {
        context.dataStore.edit { it[ENABLE_DOWNLOAD_CONTENT] = enableDownloadContent }
    }

    val enableCopyToClipboard = context.dataStore.data.map { it[ENABLE_COPY_TO_CLIPBOARD] ?: true }
    suspend fun setEnableCopyToClipboard(enableCopyToClipboard: Boolean) {
        context.dataStore.edit { it[ENABLE_COPY_TO_CLIPBOARD] = enableCopyToClipboard }
    }

    val desktopLayout = context.dataStore.data.map { it[DESKTOP_LAYOUT] ?: false }
    suspend fun setDesktopLayout(desktopLayout: Boolean) {
        context.dataStore.edit { it[DESKTOP_LAYOUT] = desktopLayout }
    }

    val immersiveMode = context.dataStore.data.map { it[IMMERSIVE_MODE] ?: false}
    suspend fun setImmersiveMode(immersiveMode: Boolean) {
        context.dataStore.edit { it[IMMERSIVE_MODE] = immersiveMode }
    }

    val stickyNavbar = context.dataStore.data.map { it[STICKY_NAVBAR] ?: true }
    suspend fun setStickyNavbar(stickyNavbar: Boolean) {
        context.dataStore.edit { it[STICKY_NAVBAR] = stickyNavbar }
    }

    val pinchToZoom = context.dataStore.data.map { it[PINCH_TO_ZOOM] ?: false }
    suspend fun setPinchToZoom(pinchToZoom: Boolean) {
        context.dataStore.edit { it[PINCH_TO_ZOOM] = pinchToZoom }
    }

    // Off by default; only takes effect while pinch to zoom is on (text_wrap.js).
    val textWrapOnZoom = context.dataStore.data.map { it[TEXT_WRAP_ON_ZOOM] ?: false }
    suspend fun setTextWrapOnZoom(enabled: Boolean) {
        context.dataStore.edit { it[TEXT_WRAP_ON_ZOOM] = enabled }
    }

    val materialYou = context.dataStore.data.map { it[MATERIAL_YOU] ?: false }
    suspend fun setMaterialYou(materialYou: Boolean) {
        context.dataStore.edit { it[MATERIAL_YOU] = materialYou }
    }

    val amoledBlack = context.dataStore.data.map { it[AMOLED_BLACK] ?: false }
    suspend fun setAmoledBlack(amoledBlack: Boolean) {
        context.dataStore.edit { it[AMOLED_BLACK] = amoledBlack }
    }

    val hideSuggested = context.dataStore.data.map { it[HIDE_SUGGESTED] ?: false }
    suspend fun setHideSuggested(hideSuggestion: Boolean) {
        context.dataStore.edit { it[HIDE_SUGGESTED] = hideSuggestion }
    }

    val hideReels = context.dataStore.data.map { it[HIDE_REELS] ?: false }
    suspend fun setHideReels(hideReels: Boolean) {
        context.dataStore.edit { it[HIDE_REELS] = hideReels }
    }

    val hideStories = context.dataStore.data.map { it[HIDE_STORIES] ?: false }
    suspend fun setHideStories(hideStories: Boolean) {
        context.dataStore.edit { it[HIDE_STORIES] = hideStories }
    }

    val hidePeopleYouMayKnow = context.dataStore.data.map { it[HIDE_PEOPLE_YOU_MAY_KNOW] ?: false }
    suspend fun setHidePeopleYouMayKnow(hidePeopleYouMayKnow: Boolean) {
        context.dataStore.edit { it[HIDE_PEOPLE_YOU_MAY_KNOW] = hidePeopleYouMayKnow }
    }

    val messagesDesktop = context.dataStore.data.map { it[MESSAGES_DESKTOP] ?: false }
    suspend fun setMessagesDesktop(messagesDesktop: Boolean) {
        context.dataStore.edit { it[MESSAGES_DESKTOP] = messagesDesktop }
    }

    val hideGroups = context.dataStore.data.map { it[HIDE_GROUPS] ?: false }
    suspend fun setHideGroups(hideGroups: Boolean) {
        context.dataStore.edit { it[HIDE_GROUPS] = hideGroups }
    }

    // Off by default: entering a floating window unexpectedly is surprising
    // behavior, same reasoning as materialYou/amoledBlack defaulting off.
    val pipEnabled = context.dataStore.data.map { it[PIP_ENABLED] ?: false }
    suspend fun setPipEnabled(pipEnabled: Boolean) {
        context.dataStore.edit { it[PIP_ENABLED] = pipEnabled }
    }

    // Off by default, like PiP itself: opt-in to keep playing audio natively
    // (with a media notification) after the screen locks during PiP.
    val pipLockscreenAudio = context.dataStore.data.map { it[PIP_LOCKSCREEN_AUDIO] ?: false }
    suspend fun setPipLockscreenAudio(enabled: Boolean) {
        context.dataStore.edit { it[PIP_LOCKSCREEN_AUDIO] = enabled }
    }

    // Default "4:7" is the empirically safe portrait ratio on Samsung A56 (and similar OEMs)
    // where requesting true 9:16 causes the PiP window to overflow past the screen edge.
    val pipPortraitRatio = context.dataStore.data.map { it[PIP_PORTRAIT_RATIO] ?: "4:7" }
    suspend fun setPipPortraitRatio(ratio: String) {
        context.dataStore.edit { it[PIP_PORTRAIT_RATIO] = ratio }
    }

    // One of KeepScreenOn's values; off by default, like Android's own screen timeout.
    val keepScreenOn = context.dataStore.data.map { it[KEEP_SCREEN_ON] ?: KeepScreenOn.OFF }
    suspend fun setKeepScreenOn(mode: String) {
        context.dataStore.edit { it[KEEP_SCREEN_ON] = mode }
    }

    // One of AppLock's values; off by default.
    val appLock = context.dataStore.data.map { it[APP_LOCK] ?: AppLock.OFF }
    suspend fun setAppLock(mode: String) {
        context.dataStore.edit { it[APP_LOCK] = mode }
    }

    // Off by default: with app lock on, turning the screen off locks the app at once instead
    // of after the lock's grace period.
    val appLockOnScreenOff = context.dataStore.data.map { it[APP_LOCK_ON_SCREEN_OFF] ?: false }
    suspend fun setAppLockOnScreenOff(enabled: Boolean) {
        context.dataStore.edit { it[APP_LOCK_ON_SCREEN_OFF] = enabled }
    }

    // Off by default: screenshots, screen recording and casting show the app as black.
    val blockScreenshots = context.dataStore.data.map { it[BLOCK_SCREENSHOTS] ?: false }
    suspend fun setBlockScreenshots(enabled: Boolean) {
        context.dataStore.edit { it[BLOCK_SCREENSHOTS] = enabled }
    }

    // Off by default: the WebView's HTTP cache is emptied each time the app goes out of sight.
    val clearCacheOnLeave = context.dataStore.data.map { it[CLEAR_CACHE_ON_LEAVE] ?: false }
    suspend fun setClearCacheOnLeave(enabled: Boolean) {
        context.dataStore.edit { it[CLEAR_CACHE_ON_LEAVE] = enabled }
    }

    // Off by default: no background checks for Facebook's unread counts (FacebookCountsWorker).
    val facebookNotifications = context.dataStore.data.map { it[FACEBOOK_NOTIFICATIONS] ?: false }
    suspend fun setFacebookNotifications(enabled: Boolean) {
        context.dataStore.edit { it[FACEBOOK_NOTIFICATIONS] = enabled }
    }

    // Off by default: Facebook notifications show counts only, not what the notifications say.
    val facebookNotificationText = context.dataStore.data.map { it[FACEBOOK_NOTIFICATION_TEXT] ?: false }
    suspend fun setFacebookNotificationText(enabled: Boolean) {
        context.dataStore.edit { it[FACEBOOK_NOTIFICATION_TEXT] = enabled }
    }

    // Off by default: the app opens on Facebook's Home feed, as Facebook orders it.
    val startOnFeeds = context.dataStore.data.map { it[START_ON_FEEDS] ?: false }
    suspend fun setStartOnFeeds(enabled: Boolean) {
        context.dataStore.edit { it[START_ON_FEEDS] = enabled }
    }

    // One of TextZoom's values; 100% by default.
    val textZoom = context.dataStore.data.map { it[TEXT_ZOOM] ?: TextZoom.DEFAULT }
    suspend fun setTextZoom(zoom: String) {
        context.dataStore.edit { it[TEXT_ZOOM] = zoom }
    }

    // Hidden until the version in Settings is tapped seven times.
    val developerOptions = context.dataStore.data.map { it[DEVELOPER_OPTIONS] ?: false }
    suspend fun setDeveloperOptions(enabled: Boolean) {
        context.dataStore.edit { it[DEVELOPER_OPTIONS] = enabled }
    }

    // Off by default: with it on, anyone with USB debugging access can inspect and drive the
    // logged-in page (chrome://inspect). Debug builds always allow it.
    val webViewDebugging = context.dataStore.data.map { it[WEBVIEW_DEBUGGING] ?: false }
    suspend fun setWebViewDebugging(enabled: Boolean) {
        context.dataStore.edit { it[WEBVIEW_DEBUGGING] = enabled }
    }

    // Off by default: web links leave the app for the browser, as before.
    val openLinksInApp = context.dataStore.data.map { it[OPEN_LINKS_IN_APP] ?: false }
    suspend fun setOpenLinksInApp(enabled: Boolean) {
        context.dataStore.edit { it[OPEN_LINKS_IN_APP] = enabled }
    }

    /** All settings as an export file (see SettingsBackup). */
    suspend fun exportJson(): String = exportSettingsJson(prefs.first())

    /** Applies an export file; returns how many settings it set. Throws on a file that isn't one. */
    suspend fun importJson(json: String): Int {
        var applied = 0
        context.dataStore.edit { applied = importSettingsJson(json, it) }
        return applied
    }
}
