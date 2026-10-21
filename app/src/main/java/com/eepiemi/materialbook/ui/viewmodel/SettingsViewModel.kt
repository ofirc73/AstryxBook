package com.eepiemi.materialbook.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Rational
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eepiemi.materialbook.data.local.SettingsDataStore
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.AMOLED_BLACK
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.APP_LOCK
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.APP_LOCK_ON_SCREEN_OFF
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.BLOCK_SCREENSHOTS
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.CLEAR_CACHE_ON_LEAVE
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.DESKTOP_LAYOUT
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.DEVELOPER_OPTIONS
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
import com.eepiemi.materialbook.utils.AppLock
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.TEXT_ZOOM
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.WEBVIEW_DEBUGGING
import com.eepiemi.materialbook.utils.KeepScreenOn
import com.eepiemi.materialbook.utils.TextZoom
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking

class SettingsViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val dataStore: SettingsDataStore = SettingsDataStore(application)

    // Migrate before the first read so desktopLayout's initial value already
    // reflects the unstuck state on installs hit by the old auto-desktop logic.
    private val initialPrefs = runBlocking {
        dataStore.migrateLegacyAutoDesktop()
        dataStore.prefs.first()
    }

    val removeAds = dataStore.removeAds.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[REMOVE_ADS] ?: true,
        started = SharingStarted.WhileSubscribed()
    )
    val enableDownloadContent = dataStore.enableDownloadContent.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[ENABLE_DOWNLOAD_CONTENT] ?: true,
        started = SharingStarted.WhileSubscribed()
    )
    val enableCopyToClipboard = dataStore.enableCopyToClipboard.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[ENABLE_COPY_TO_CLIPBOARD] ?: true,
        started = SharingStarted.WhileSubscribed()
    )
    val desktopLayout = dataStore.desktopLayout.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[DESKTOP_LAYOUT] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val immersiveMode = dataStore.immersiveMode.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[IMMERSIVE_MODE] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val stickyNavbar = dataStore.stickyNavbar.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[STICKY_NAVBAR] ?: true,
        started = SharingStarted.WhileSubscribed()
    )
    val pinchToZoom = dataStore.pinchToZoom.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[PINCH_TO_ZOOM] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val textWrapOnZoom = dataStore.textWrapOnZoom.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[TEXT_WRAP_ON_ZOOM] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val materialYou = dataStore.materialYou.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[MATERIAL_YOU] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val amoledBlack = dataStore.amoledBlack.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[AMOLED_BLACK] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val hideSuggested = dataStore.hideSuggested.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[HIDE_SUGGESTED] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val hideReels = dataStore.hideReels.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[HIDE_REELS] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val hideStories = dataStore.hideStories.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[HIDE_STORIES] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val hidePeopleYouMayKnow = dataStore.hidePeopleYouMayKnow.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[HIDE_PEOPLE_YOU_MAY_KNOW] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val messagesDesktop = dataStore.messagesDesktop.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[MESSAGES_DESKTOP] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val hideGroups = dataStore.hideGroups.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[HIDE_GROUPS] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val pipEnabled = dataStore.pipEnabled.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[PIP_ENABLED] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val pipLockscreenAudio = dataStore.pipLockscreenAudio.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[PIP_LOCKSCREEN_AUDIO] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val pipPortraitRatio = dataStore.pipPortraitRatio.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[PIP_PORTRAIT_RATIO] ?: "4:7",
        started = SharingStarted.WhileSubscribed()
    )
    val keepScreenOn = dataStore.keepScreenOn.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[KEEP_SCREEN_ON] ?: KeepScreenOn.OFF,
        started = SharingStarted.WhileSubscribed()
    )
    // Eagerly: MainActivity reads it in onStart, with nothing collecting it.
    val appLock = dataStore.appLock.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[APP_LOCK] ?: AppLock.OFF,
        started = SharingStarted.Eagerly
    )
    // Eagerly: MainActivity's screen-off receiver reads it, with nothing collecting it.
    val appLockOnScreenOff = dataStore.appLockOnScreenOff.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[APP_LOCK_ON_SCREEN_OFF] ?: false,
        started = SharingStarted.Eagerly
    )
    val blockScreenshots = dataStore.blockScreenshots.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[BLOCK_SCREENSHOTS] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val clearCacheOnLeave = dataStore.clearCacheOnLeave.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[CLEAR_CACHE_ON_LEAVE] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val facebookNotifications = dataStore.facebookNotifications.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[FACEBOOK_NOTIFICATIONS] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val facebookNotificationText = dataStore.facebookNotificationText.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[FACEBOOK_NOTIFICATION_TEXT] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    // Read once, for the page the app starts on.
    val startOnFeeds = dataStore.startOnFeeds.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[START_ON_FEEDS] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val textZoom = dataStore.textZoom.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[TEXT_ZOOM] ?: TextZoom.DEFAULT,
        started = SharingStarted.WhileSubscribed()
    )
    val developerOptions = dataStore.developerOptions.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[DEVELOPER_OPTIONS] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val openLinksInApp = dataStore.openLinksInApp.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[OPEN_LINKS_IN_APP] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    // Eagerly: MainActivity applies it with nothing collecting it.
    val webViewDebugging = dataStore.webViewDebugging.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[WEBVIEW_DEBUGGING] ?: false,
        started = SharingStarted.Eagerly
    )

    fun setRemoveAds(removeAds: Boolean) {
        viewModelScope.launch {
            dataStore.setRemoveAds(removeAds)
        }
    }

    fun setEnableDownloadContent(enableDownloadContent: Boolean) {
        viewModelScope.launch {
            dataStore.setEnableDownloadContent(enableDownloadContent)
        }
    }

    fun setEnableCopyToClipboard(enableCopyToClipboard: Boolean) {
        viewModelScope.launch {
            dataStore.setEnableCopyToClipboard(enableCopyToClipboard)
        }
    }

    fun setDesktopLayout(desktopLayout: Boolean) {
        viewModelScope.launch {
            dataStore.setDesktopLayout(desktopLayout)
        }
    }

    fun setImmersiveMode(immersiveMode: Boolean) {
        viewModelScope.launch {
            dataStore.setImmersiveMode(immersiveMode)
        }
    }

    fun setStickyNavbar(stickyNavbar: Boolean) {
        viewModelScope.launch {
            dataStore.setStickyNavbar(stickyNavbar)
        }
    }

    fun setPinchToZoom(pinchToZoom: Boolean) {
        viewModelScope.launch {
            dataStore.setPinchToZoom(pinchToZoom)
        }
    }

    fun setTextWrapOnZoom(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setTextWrapOnZoom(enabled)
        }
    }

    fun setMaterialYou(materialYou: Boolean) {
        viewModelScope.launch {
            dataStore.setMaterialYou(materialYou)
        }
    }

    fun setAmoledBlack(amoledBlack: Boolean) {
        viewModelScope.launch {
            dataStore.setAmoledBlack(amoledBlack)
        }
    }

    fun setHideSuggested(hideSuggested: Boolean) {
        viewModelScope.launch {
            dataStore.setHideSuggested(hideSuggested)
        }
    }

    fun setHideReels(hideReels: Boolean) {
        viewModelScope.launch {
            dataStore.setHideReels(hideReels)
        }
    }

    fun setHideStories(hideStories: Boolean) {
        viewModelScope.launch {
            dataStore.setHideStories(hideStories)
        }
    }

    fun setHidePeopleYouMayKnow(hidePeopleYouMayKnow: Boolean) {
        viewModelScope.launch {
            dataStore.setHidePeopleYouMayKnow(hidePeopleYouMayKnow)
        }
    }

    fun setMessagesDesktop(messagesDesktop: Boolean) {
        viewModelScope.launch {
            dataStore.setMessagesDesktop(messagesDesktop)
        }
    }

    fun setHideGroups(hideGroups: Boolean) {
        viewModelScope.launch {
            dataStore.setHideGroups(hideGroups)
        }
    }

    fun setPipEnabled(pipEnabled: Boolean) {
        viewModelScope.launch {
            dataStore.setPipEnabled(pipEnabled)
        }
    }

    fun setPipLockscreenAudio(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setPipLockscreenAudio(enabled)
        }
    }

    fun setPipPortraitRatio(ratio: String) {
        viewModelScope.launch {
            dataStore.setPipPortraitRatio(ratio)
        }
    }

    fun setKeepScreenOn(mode: String) {
        viewModelScope.launch {
            dataStore.setKeepScreenOn(mode)
        }
    }

    fun setAppLock(mode: String) {
        viewModelScope.launch {
            dataStore.setAppLock(mode)
        }
    }

    fun setTextZoom(zoom: String) {
        viewModelScope.launch {
            dataStore.setTextZoom(zoom)
        }
    }

    fun setDeveloperOptions(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setDeveloperOptions(enabled)
        }
    }

    fun setWebViewDebugging(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setWebViewDebugging(enabled)
        }
    }

    fun setAppLockOnScreenOff(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setAppLockOnScreenOff(enabled)
        }
    }

    fun setBlockScreenshots(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setBlockScreenshots(enabled)
        }
    }

    fun setFacebookNotificationText(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setFacebookNotificationText(enabled)
        }
    }

    fun setFacebookNotifications(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setFacebookNotifications(enabled)
        }
    }

    fun setClearCacheOnLeave(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setClearCacheOnLeave(enabled)
        }
    }

    fun setStartOnFeeds(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setStartOnFeeds(enabled)
        }
    }

    fun setOpenLinksInApp(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setOpenLinksInApp(enabled)
        }
    }

    /** Writes all settings to [uri] (a file the user picked); [onDone] gets whether it worked. */
    fun exportSettings(uri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching {
                val json = dataStore.exportJson()
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!
                        .use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
            }.isSuccess
            onDone(ok)
        }
    }

    /**
     * Reads settings from [uri] (a file the user picked); [onDone] gets how many were applied,
     * or null when the file isn't an Astryxbook settings file.
     */
    fun importSettings(uri: Uri, onDone: (Int?) -> Unit) {
        viewModelScope.launch {
            val applied = runCatching {
                val json = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)!!
                        .use { it.readAtMost(MAX_SETTINGS_FILE_BYTES) }
                        .toString(Charsets.UTF_8)
                }
                dataStore.importJson(json)
            }.getOrNull()
            onDone(applied)
        }
    }

    fun parsedPipRational(): Rational = parsedPipRational(pipPortraitRatio.value)

    fun pipRationalForVideo(videoWidth: Int, videoHeight: Int): Rational =
        calculatePipRational(videoWidth, videoHeight, parsedPipRational())

    companion object {
        // A settings file is a few hundred bytes; anything bigger isn't one.
        private const val MAX_SETTINGS_FILE_BYTES = 64 * 1024

        private val MIN_PIP_ASPECT_RATIO = 100f / 239f
        private val MAX_PIP_ASPECT_RATIO = 239f / 100f

        /**
         * Converts a stored "W:H" string to a [Rational] suitable for
         * [android.app.PictureInPictureParams.Builder.setAspectRatio].
         * Falls back to Rational(4, 7) on any parse error so a corrupt pref
         * can never crash the app.
         */
        fun parsedPipRational(stored: String): Rational {
            return try {
                val parts = stored.split(":")
                Rational(parts[0].trim().toInt(), parts[1].trim().toInt())
            } catch (_: Exception) {
                Rational(4, 7)
            }
        }

        /**
         * Uses the selected ratio for portrait video and the detected source
         * ratio for landscape video. Extreme landscape ratios are clamped to
         * Android's documented PiP range; unknown dimensions use 16:9.
         */
        fun calculatePipRational(
            videoWidth: Int,
            videoHeight: Int,
            portraitRatio: Rational,
        ): Rational {
            if (videoWidth <= 0 || videoHeight <= 0) {
                return Rational(16, 9)
            }
            if (videoHeight > videoWidth) {
                return portraitRatio
            }

            val sourceRatio = videoWidth.toFloat() / videoHeight.toFloat()
            return when {
                sourceRatio < MIN_PIP_ASPECT_RATIO -> Rational(100, 239)
                sourceRatio > MAX_PIP_ASPECT_RATIO -> Rational(239, 100)
                else -> reducedRational(videoWidth, videoHeight)
            }
        }

        private fun reducedRational(numerator: Int, denominator: Int): Rational {
            var a = numerator
            var b = denominator
            while (b != 0) {
                val remainder = a % b
                a = b
                b = remainder
            }
            return Rational(numerator / a, denominator / a)
        }
    }
}

// The stream's bytes, refusing (rather than reading into memory) one longer than [limit].
private fun java.io.InputStream.readAtMost(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        out.write(buffer, 0, read)
        require(out.size() <= limit) { "Settings file too large" }
    }
    return out.toByteArray()
}
