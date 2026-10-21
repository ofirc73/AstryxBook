package com.eepiemi.materialbook.data.local

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.AMOLED_BLACK
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.APP_LOCK
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.APP_LOCK_ON_SCREEN_OFF
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.BLOCK_SCREENSHOTS
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.CLEAR_CACHE_ON_LEAVE
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.DESKTOP_LAYOUT
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.ENABLE_COPY_TO_CLIPBOARD
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.ENABLE_DOWNLOAD_CONTENT
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.FACEBOOK_NOTIFICATIONS
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.FACEBOOK_NOTIFICATION_TEXT
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.HIDE_GROUPS
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.HIDE_PEOPLE_YOU_MAY_KNOW
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.HIDE_REELS
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.HIDE_STORIES
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.HIDE_SUGGESTED
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.IMMERSIVE_MODE
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.KEEP_SCREEN_ON
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.MATERIAL_YOU
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.MESSAGES_DESKTOP
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.OPEN_LINKS_IN_APP
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.PINCH_TO_ZOOM
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.PIP_ENABLED
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.PIP_LOCKSCREEN_AUDIO
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.PIP_PORTRAIT_RATIO
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.REMOVE_ADS
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.START_ON_FEEDS
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.STICKY_NAVBAR
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.TEXT_WRAP_ON_ZOOM
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.TEXT_ZOOM
import org.json.JSONObject

/**
 * Settings export and import (Settings → Export / Import settings): a small JSON file,
 *
 *     {"format": "astryxbook-settings", "version": 1, "settings": {"remove_ads": true, …}}
 *
 * Only the keys listed here travel, each with its type: an imported value of the wrong type
 * is skipped, never stored (reading it back would crash the app on every launch). Developer
 * options and WebView debugging are left out on purpose, so a shared file can't turn on
 * remote debugging; the legacy migration flag is internal. A new setting must be added to
 * one of these lists or to [NOT_EXPORTED] (SettingsBackupTest checks).
 */
val EXPORTED_BOOLEAN_SETTINGS: List<Preferences.Key<Boolean>> = listOf(
    REMOVE_ADS, ENABLE_DOWNLOAD_CONTENT, ENABLE_COPY_TO_CLIPBOARD, DESKTOP_LAYOUT,
    IMMERSIVE_MODE, STICKY_NAVBAR, PINCH_TO_ZOOM, MATERIAL_YOU, AMOLED_BLACK,
    HIDE_SUGGESTED, HIDE_REELS, HIDE_STORIES, HIDE_PEOPLE_YOU_MAY_KNOW, HIDE_GROUPS,
    MESSAGES_DESKTOP, PIP_ENABLED, PIP_LOCKSCREEN_AUDIO, OPEN_LINKS_IN_APP, TEXT_WRAP_ON_ZOOM,
    START_ON_FEEDS, APP_LOCK_ON_SCREEN_OFF, BLOCK_SCREENSHOTS, CLEAR_CACHE_ON_LEAVE,
    FACEBOOK_NOTIFICATIONS, FACEBOOK_NOTIFICATION_TEXT,
)

// App lock travels too: on a device without a screen lock it simply stays off (canUseAppLock).
val EXPORTED_STRING_SETTINGS: List<Preferences.Key<String>> = listOf(
    PIP_PORTRAIT_RATIO, KEEP_SCREEN_ON, TEXT_ZOOM, APP_LOCK,
)

/** Settings that never travel: see above. */
val NOT_EXPORTED = setOf("developer_options", "webview_debugging", "is_revert_desktop")

const val SETTINGS_FILE_FORMAT = "astryxbook-settings"
const val SETTINGS_FILE_VERSION = 1

// Stored strings are short choice values ("4:7", "video", "115").
private const val MAX_STRING_SETTING_LENGTH = 32

/** The settings that are set (defaults aren't written), as an export file. */
fun exportSettingsJson(prefs: Preferences): String {
    val settings = JSONObject()
    EXPORTED_BOOLEAN_SETTINGS.forEach { key -> prefs[key]?.let { settings.put(key.name, it) } }
    EXPORTED_STRING_SETTINGS.forEach { key -> prefs[key]?.let { settings.put(key.name, it) } }
    return JSONObject()
        .put("format", SETTINGS_FILE_FORMAT)
        .put("version", SETTINGS_FILE_VERSION)
        .put("settings", settings)
        .toString(2)
}

/**
 * Applies an export file to [prefs]: every known setting it holds with the right type.
 * Returns how many were applied. Throws IllegalArgumentException for a file that isn't a
 * settings file (or is from a newer, incompatible version).
 */
fun importSettingsJson(json: String, prefs: MutablePreferences): Int {
    val root = runCatching { JSONObject(json) }.getOrNull()
    require(root != null && root.optString("format") == SETTINGS_FILE_FORMAT) { "Not an Astryxbook settings file" }
    require(root.optInt("version", -1) == SETTINGS_FILE_VERSION) { "Unsupported settings file version" }
    val settings = root.optJSONObject("settings") ?: throw IllegalArgumentException("No settings in the file")
    var applied = 0
    EXPORTED_BOOLEAN_SETTINGS.forEach { key ->
        (settings.opt(key.name) as? Boolean)?.let { prefs[key] = it; applied++ }
    }
    EXPORTED_STRING_SETTINGS.forEach { key ->
        (settings.opt(key.name) as? String)?.takeIf { it.length <= MAX_STRING_SETTING_LENGTH }
            ?.let { prefs[key] = it; applied++ }
    }
    return applied
}
