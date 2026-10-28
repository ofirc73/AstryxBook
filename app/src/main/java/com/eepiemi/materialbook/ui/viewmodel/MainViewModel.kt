package com.eepiemi.materialbook.ui.viewmodel

import android.content.res.Resources
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.eepiemi.materialbook.R
import com.astryx.book.utils.Script
import com.astryx.book.utils.bundledScripts


class MainViewModel(
    resources: Resources,
    settings: SettingsViewModel
): ViewModel() {

    private val _themeColor = mutableStateOf(Color.Transparent)
    val themeColor: State<Color> = _themeColor

    private val _scripts = mutableStateOf<String?>(null)
    val scripts: State<String?> = _scripts

    init {
        loadScripts(
            resources,
            settings
        )
    }

    fun setThemeColor(color: Color) {
        _themeColor.value = color
    }

    private fun loadScripts(
        resources: Resources,
        settings: SettingsViewModel
    ) {
        val scripts = listOf(
            Script(true, R.raw.scripts), // always apply
            Script(true, R.raw.memory_photos),
            Script(settings.messagesDesktop.value, R.raw.messages_tab),
            Script(settings.removeAds.value, R.raw.adblock),
            Script(settings.enableDownloadContent.value, R.raw.download_content),
            Script(settings.enableCopyToClipboard.value, R.raw.copy_to_clipboard),
            Script(settings.stickyNavbar.value, R.raw.sticky_navbar),
            Script(!settings.pinchToZoom.value, R.raw.pinch_to_zoom),
            Script(settings.pinchToZoom.value && settings.textWrapOnZoom.value, R.raw.text_wrap),
            Script(settings.materialYou.value, R.raw.material_you),
            Script(settings.amoledBlack.value, R.raw.amoled_black),
            Script(settings.hideSuggested.value, R.raw.hide_suggested),
            Script(settings.hideReels.value, R.raw.hide_reels),
            Script(settings.hideStories.value, R.raw.hide_stories),
            Script(settings.hidePeopleYouMayKnow.value, R.raw.hide_pymk),
            Script(settings.hideGroups.value, R.raw.hide_groups)
            // pip_video_detector.js deliberately NOT included here - it's core
            // PiP infrastructure, not a cosmetic feature, and needs to run as
            // early as possible, so MaterialbookWV.kt evaluates it on its own
            // rather than as part of this combined blob.
        )

        _scripts.value = bundledScripts(scripts) { resId ->
            resources.openRawResource(resId).bufferedReader().use { it.readText() }
        }
    }

    fun refresh(
        resources: Resources,
        settings: SettingsViewModel
    ) {
        clearScripts()
        loadScripts(resources, settings)
    }

    private fun clearScripts() {
        _scripts.value = null
    }
}