package com.eepiemi.materialbook.ui.components.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Diversity1
import androidx.compose.material.icons.filled.EmojiPeople
import androidx.compose.material.icons.filled.Padding
import androidx.compose.material.icons.filled.Try
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PanoramaWideAngle
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Pinch
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eepiemi.materialbook.BuildConfig
import com.eepiemi.materialbook.R
import com.eepiemi.materialbook.ui.viewmodel.SettingsViewModel
import com.eepiemi.materialbook.utils.AppLock
import com.eepiemi.materialbook.utils.KeepScreenOn
import com.eepiemi.materialbook.utils.canUseAppLock
import com.eepiemi.materialbook.utils.TextZoom
import com.eepiemi.materialbook.utils.effectiveDesktop
import com.eepiemi.materialbook.utils.rememberAutoDesktop

@Composable
fun SettingsContent(
    modifier: Modifier,
    viewModel: SettingsViewModel = viewModel(),
    onLogOut: () -> Unit = {},
    // Whether a setting the page's scripts read has changed since Settings opened (it needs
    // a reload to show).
    onPageSettingsChanged: (Boolean) -> Unit = {},
) {
    var isOpenDialog by rememberSaveable { mutableStateOf(false) }
    var isAppLockDialog by rememberSaveable { mutableStateOf(false) }
    var isLogOutDialog by rememberSaveable { mutableStateOf(false) }
    var isPipRatioDialog by rememberSaveable { mutableStateOf(false) }
    var isKeepScreenOnDialog by rememberSaveable { mutableStateOf(false) }
    var isTextZoomDialog by rememberSaveable { mutableStateOf(false) }
    var isOpenLinksHint by rememberSaveable { mutableStateOf(false) }

    val removeAds = viewModel.removeAds.collectAsState()
    val messagesDesktop = viewModel.messagesDesktop.collectAsState()
    val enableDownloadContent = viewModel.enableDownloadContent.collectAsState()
    val enableCopyToClipboard = viewModel.enableCopyToClipboard.collectAsState()
    val desktopLayout = viewModel.desktopLayout.collectAsState()
    val immersiveMode = viewModel.immersiveMode.collectAsState()
    val stickyNavbar = viewModel.stickyNavbar.collectAsState()
    val pinchToZoom = viewModel.pinchToZoom.collectAsState()
    val materialYou = viewModel.materialYou.collectAsState()
    val amoledBlack = viewModel.amoledBlack.collectAsState()
    val pipEnabled = viewModel.pipEnabled.collectAsState()
    val pipLockscreenAudio = viewModel.pipLockscreenAudio.collectAsState()
    val pipPortraitRatio = viewModel.pipPortraitRatio.collectAsState()
    val keepScreenOn = viewModel.keepScreenOn.collectAsState()
    val keepScreenOnLabels = KeepScreenOn.ALL.associateWith { keepScreenOnLabel(it) }
    val appLock = viewModel.appLock.collectAsState()
    val appLockLabels = AppLock.ALL.associateWith { appLockLabel(it) }
    // Checked on each composition: the user may set a screen lock and come back.
    val context = LocalContext.current
    val appLockAvailable = canUseAppLock(context)
    val textZoom = viewModel.textZoom.collectAsState()
    val textZoomLabels = TextZoom.ALL.associateWith { textZoomLabel(it) }
    val developerOptions = viewModel.developerOptions.collectAsState()
    val webViewDebugging = viewModel.webViewDebugging.collectAsState()
    val openLinksInApp = viewModel.openLinksInApp.collectAsState()
    val hideSuggested = viewModel.hideSuggested.collectAsState()
    val hideReels = viewModel.hideReels.collectAsState()
    val hideStories = viewModel.hideStories.collectAsState()
    val hidePeopleYouMayKnow = viewModel.hidePeopleYouMayKnow.collectAsState()
    val hideGroups = viewModel.hideGroups.collectAsState()

    // The settings the page's scripts and user agent are built from (MainViewModel.refresh);
    // the rest apply at once.
    val pageSettings = listOf(
        messagesDesktop, removeAds, enableDownloadContent, enableCopyToClipboard, desktopLayout,
        stickyNavbar, pinchToZoom, materialYou, amoledBlack, hideSuggested, hideReels,
        hideStories, hidePeopleYouMayKnow, hideGroups
    ).map { it.value }
    val initialPageSettings = remember { pageSettings }
    val pageSettingsChanged = pageSettings != initialPageSettings
    LaunchedEffect(pageSettingsChanged) { onPageSettingsChanged(pageSettingsChanged) }

    // Settings export and import go through the system file picker (no storage permission).
    val toastContext = LocalContext.current
    val toastResources = LocalResources.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.exportSettings(uri) { ok ->
            Toast.makeText(
                toastContext,
                if (ok) R.string.settings_exported else R.string.settings_export_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.importSettings(uri) { applied ->
            val message = if (applied == null) {
                toastResources.getString(R.string.settings_import_failed)
            } else {
                toastResources.getQuantityString(R.plurals.settings_imported, applied, applied)
            }
            Toast.makeText(toastContext, message, Toast.LENGTH_LONG).show()
        }
    }

    val isAutoDesktop = rememberAutoDesktop()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsHeading(stringResource(R.string.settings_section_feed))
        SettingsGroup(
            items = listOf(
                SettingsItem(
                    icon = Icons.Outlined.Shield,
                    title = stringResource(R.string.remove_ads_title),
                    supportingText = stringResource(R.string.hide_sponsored_ads_from_your_feed),
                    isActive = removeAds.value,
                    onClick = { viewModel.setRemoveAds(!removeAds.value) },
                ),
                SettingsItem(
                    icon = Icons.Outlined.GridView,
                    title = stringResource(R.string.customize_feed_title),
                    supportingText = stringResource(R.string.customize_feed),
                    isActive = null,
                    onClick = { isOpenDialog = true },
                ),
                SettingsItem(
                    icon = Icons.Outlined.FileDownload,
                    title = stringResource(R.string.download_content_title),
                    supportingText = stringResource(R.string.enable_download_button_on_media_view),
                    isActive = enableDownloadContent.value,
                    onClick = { viewModel.setEnableDownloadContent(!enableDownloadContent.value) },
                ),
                SettingsItem(
                    icon = Icons.Outlined.ContentCopy,
                    title = stringResource(R.string.copy_to_clipboard_title),
                    supportingText = stringResource(R.string.enable_copy_to_clipboard_button_on_media_view),
                    isActive = enableCopyToClipboard.value,
                    onClick = { viewModel.setEnableCopyToClipboard(!enableCopyToClipboard.value) },
                ),
            )
        )

        SettingsHeading(stringResource(R.string.settings_section_appearance))
        SettingsGroup(
            items = listOf(
                SettingsItem(
                    icon = Icons.Outlined.Palette,
                    title = stringResource(R.string.material_you_title),
                    supportingText = stringResource(R.string.enable_material_you_theming_for_facebook),
                    isActive = materialYou.value,
                    onClick = { viewModel.setMaterialYou(!materialYou.value) }
                ),
                SettingsItem(
                    icon = Icons.Outlined.Circle,
                    title = stringResource(R.string.amoled_black_title),
                    supportingText = stringResource(R.string.amoled_black_summary),
                    isActive = amoledBlack.value,
                    onClick = { viewModel.setAmoledBlack(!amoledBlack.value) }
                ),
                SettingsItem(
                    icon = Icons.Outlined.FormatSize,
                    title = stringResource(R.string.text_size_title),
                    supportingText = textZoomLabels[textZoom.value]
                        ?: textZoomLabels.getValue(TextZoom.DEFAULT),
                    isActive = null,
                    onClick = { isTextZoomDialog = true }
                ),
                SettingsItem(
                    icon = Icons.Default.Padding,
                    title = stringResource(R.string.sticky_navbar_title),
                    supportingText = stringResource(R.string.keep_the_navigation_bar_visible_while_scrolling),
                    isActive = stickyNavbar.value,
                    onClick = { viewModel.setStickyNavbar(!stickyNavbar.value) }
                ),
                SettingsItem(
                    icon = Icons.Outlined.PanoramaWideAngle,
                    title = stringResource(R.string.immersive_mode_title),
                    supportingText = stringResource(R.string.hide_system_bars_for_a_fullscreen_experience),
                    isActive = immersiveMode.value,
                    onClick = { viewModel.setImmersiveMode(!immersiveMode.value) }
                ),
                SettingsItem(
                    icon = Icons.Outlined.DesktopWindows,
                    title = stringResource(R.string.desktop_layout_title),
                    supportingText = stringResource(R.string.force_desktop_layout_may_not_be_suitable_for_smaller_display),
                    // Large screens are always desktop: shown on and locked.
                    // On phones this is a plain toggle.
                    isActive = effectiveDesktop(desktopLayout.value, isAutoDesktop),
                    onClick = { if (!isAutoDesktop) viewModel.setDesktopLayout(!desktopLayout.value) }
                ),
                SettingsItem(
                    icon = Icons.Outlined.Pinch,
                    title = stringResource(R.string.pinch_to_zoom_title),
                    supportingText = stringResource(R.string.use_two_fingers_to_zoom_in_or_out),
                    isActive = pinchToZoom.value,
                    onClick = { viewModel.setPinchToZoom(!pinchToZoom.value) }
                ),
            )
        )

        SettingsHeading(stringResource(R.string.settings_section_video))
        SettingsGroup(
            items = buildList {
                add(
                    SettingsItem(
                        icon = Icons.Outlined.PictureInPictureAlt,
                        title = stringResource(R.string.pip_title),
                        supportingText = stringResource(R.string.shrink_into_a_floating_window_when_leaving_while_a_video_plays),
                        isActive = pipEnabled.value,
                        onClick = { viewModel.setPipEnabled(!pipEnabled.value) }
                    )
                )
                // PiP ratio and lock-screen audio: only shown while PiP is enabled so they
                // don't clutter the settings for users who never use PiP.
                if (pipEnabled.value) {
                    val deviceLabel = run {
                        val mfr = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
                        val model = Build.MODEL
                        if (model.startsWith(mfr, ignoreCase = true)) model else "$mfr $model"
                    }
                    add(
                        SettingsItem(
                            icon = Icons.Outlined.AspectRatio,
                            title = stringResource(R.string.pip_ratio_title),
                            supportingText = "$deviceLabel · ${pipPortraitRatio.value}",
                            isActive = null,
                            onClick = { isPipRatioDialog = true }
                        )
                    )
                    // No POST_NOTIFICATIONS prompt here: notifications for a
                    // media session are exempt from that permission on API 33+
                    // (Android notification-permission docs), so the media
                    // notification and lock-screen controls show without it.
                    add(
                        SettingsItem(
                            icon = Icons.Outlined.Headphones,
                            title = stringResource(R.string.pip_lockscreen_audio_title),
                            supportingText = stringResource(R.string.pip_lockscreen_audio_summary),
                            isActive = pipLockscreenAudio.value,
                            onClick = { viewModel.setPipLockscreenAudio(!pipLockscreenAudio.value) }
                        )
                    )
                }
                add(
                    SettingsItem(
                        icon = Icons.Outlined.LightMode,
                        title = stringResource(R.string.keep_screen_on_title),
                        supportingText = keepScreenOnLabels[keepScreenOn.value]
                            ?: keepScreenOnLabels.getValue(KeepScreenOn.OFF),
                        isActive = null,
                        onClick = { isKeepScreenOnDialog = true }
                    )
                )
            }
        )

        SettingsHeading(stringResource(R.string.settings_section_messages_links))
        SettingsGroup(
            items = buildList {
                add(
                    SettingsItem(
                        icon = Icons.AutoMirrored.Outlined.Message,
                        title = stringResource(R.string.messages_desktop_title),
                        supportingText = stringResource(R.string.messages_desktop_desc),
                        isActive = messagesDesktop.value,
                        onClick = { viewModel.setMessagesDesktop(!messagesDesktop.value) },
                    )
                )
                // Android 12+ opens Facebook links in the browser until the user allows them
                // for this app (facebook.com can't verify for it); this opens that system page,
                // after a hint on what to pick there.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(
                        SettingsItem(
                            icon = Icons.Outlined.Link,
                            title = stringResource(R.string.open_links_title),
                            supportingText = stringResource(R.string.open_links_summary),
                            isActive = null,
                            onClick = { isOpenLinksHint = true }
                        )
                    )
                }
                add(
                    SettingsItem(
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        title = stringResource(R.string.open_web_links_in_app_title),
                        supportingText = stringResource(R.string.open_web_links_in_app_summary),
                        isActive = openLinksInApp.value,
                        onClick = { viewModel.setOpenLinksInApp(!openLinksInApp.value) }
                    )
                )
            }
        )

        SettingsHeading(stringResource(R.string.settings_section_privacy))
        SettingsGroup(
            items = listOf(
                SettingsItem(
                    icon = Icons.Outlined.Lock,
                    title = stringResource(R.string.app_lock_title),
                    supportingText = when {
                        appLockAvailable -> appLockLabels[appLock.value] ?: appLockLabels.getValue(AppLock.OFF)
                        else -> stringResource(R.string.app_lock_open_security_settings)
                    },
                    isActive = null,
                    onClick = {
                        if (appLockAvailable) {
                            isAppLockDialog = true
                        } else {
                            runCatching { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
                        }
                    }
                ),
                SettingsItem(
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    title = stringResource(R.string.log_out_title),
                    supportingText = stringResource(R.string.log_out_summary),
                    isActive = null,
                    isDestructive = true,
                    onClick = { isLogOutDialog = true }
                )
            )
        )

        SettingsHeading(stringResource(R.string.settings_section_backup))
        SettingsGroup(
            items = listOf(
                SettingsItem(
                    icon = Icons.Outlined.Upload,
                    title = stringResource(R.string.export_settings_title),
                    supportingText = stringResource(R.string.export_settings_summary),
                    isActive = null,
                    onClick = { runCatching { exportLauncher.launch(SETTINGS_FILE_NAME) } }
                ),
                SettingsItem(
                    icon = Icons.Outlined.Download,
                    title = stringResource(R.string.import_settings_title),
                    supportingText = stringResource(R.string.import_settings_summary),
                    isActive = null,
                    onClick = { runCatching { importLauncher.launch(SETTINGS_FILE_TYPES) } }
                )
            )
        )

        // Hidden until the version below is tapped seven times.
        if (developerOptions.value) {
            SettingsGroup(
                items = listOf(
                    SettingsItem(
                        icon = Icons.Outlined.BugReport,
                        title = stringResource(R.string.webview_debugging_title),
                        supportingText = stringResource(R.string.webview_debugging_summary),
                        isActive = webViewDebugging.value,
                        onClick = { viewModel.setWebViewDebugging(!webViewDebugging.value) }
                    )
                )
            )
        }

        VersionFooter(
            developerOptions = developerOptions.value,
            onUnlock = { viewModel.setDeveloperOptions(true) }
        )

    }

    if (isAppLockDialog) {
        ChoiceDialog(
            title = stringResource(R.string.app_lock_title),
            options = appLockLabels,
            current = appLock.value,
            onSelect = { mode ->
                viewModel.setAppLock(mode)
                @Suppress("AssignedValueIsNeverRead")
                isAppLockDialog = false
            },
            onDismiss = {
                @Suppress("AssignedValueIsNeverRead")
                isAppLockDialog = false
            }
        )
    }

    if (isLogOutDialog) {
        AlertDialog(
            onDismissRequest = {
                @Suppress("AssignedValueIsNeverRead")
                isLogOutDialog = false
            },
            title = { Text(stringResource(R.string.log_out_confirm_title)) },
            text = { Text(stringResource(R.string.log_out_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    @Suppress("AssignedValueIsNeverRead")
                    isLogOutDialog = false
                    onLogOut()
                }) { Text(stringResource(R.string.log_out_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    @Suppress("AssignedValueIsNeverRead")
                    isLogOutDialog = false
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (isOpenLinksHint) {
        AlertDialog(
            onDismissRequest = {
                @Suppress("AssignedValueIsNeverRead")
                isOpenLinksHint = false
            },
            title = { Text(stringResource(R.string.open_links_title)) },
            text = { Text(stringResource(R.string.open_links_hint_text)) },
            confirmButton = {
                TextButton(onClick = {
                    @Suppress("AssignedValueIsNeverRead")
                    isOpenLinksHint = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                                    "package:${context.packageName}".toUri()
                                )
                            )
                        }
                    }
                }) { Text(stringResource(R.string.open_links_hint_continue)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    @Suppress("AssignedValueIsNeverRead")
                    isOpenLinksHint = false
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (isTextZoomDialog) {
        ChoiceDialog(
            title = stringResource(R.string.text_size_title),
            note = stringResource(R.string.text_size_layout_note),
            options = textZoomLabels,
            current = textZoom.value,
            onSelect = { zoom ->
                viewModel.setTextZoom(zoom)
                @Suppress("AssignedValueIsNeverRead")
                isTextZoomDialog = false
            },
            onDismiss = {
                @Suppress("AssignedValueIsNeverRead")
                isTextZoomDialog = false
            }
        )
    }

    if (isOpenDialog) {
        HideOptionsDialog(
            viewModel = viewModel,
            onDismiss = {
                @Suppress("AssignedValueIsNeverRead")
                isOpenDialog = false
            }
        )
    }

    if (isKeepScreenOnDialog) {
        ChoiceDialog(
            title = stringResource(R.string.keep_screen_on_title),
            options = keepScreenOnLabels,
            current = keepScreenOn.value,
            onSelect = { mode ->
                viewModel.setKeepScreenOn(mode)
                @Suppress("AssignedValueIsNeverRead")
                isKeepScreenOnDialog = false
            },
            onDismiss = {
                @Suppress("AssignedValueIsNeverRead")
                isKeepScreenOnDialog = false
            }
        )
    }

    if (isPipRatioDialog) {
        ChoiceDialog(
            title = stringResource(R.string.pip_ratio_title),
            options = PIP_RATIO_PRESETS.associateWith { pipRatioLabel(it) },
            current = viewModel.pipPortraitRatio.collectAsState().value,
            onSelect = { ratio ->
                viewModel.setPipPortraitRatio(ratio)
                @Suppress("AssignedValueIsNeverRead")
                isPipRatioDialog = false
            },
            onDismiss = {
                @Suppress("AssignedValueIsNeverRead")
                isPipRatioDialog = false
            }
        )
    }

}

@Composable
private fun HideOptionsDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {

    val hideSuggested = viewModel.hideSuggested.collectAsState()
    val hideReels = viewModel.hideReels.collectAsState()
    val hideStories = viewModel.hideStories.collectAsState()
    val hidePeopleYouMayKnow = viewModel.hidePeopleYouMayKnow.collectAsState()
    val hideGroups = viewModel.hideGroups.collectAsState()

    Dialog(
        onDismissRequest = { onDismiss() }
    ) {

        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(color = MaterialTheme.colorScheme.surfaceContainer)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // The switches turn hiding on: on means the item is hidden.
            Text(
                text = stringResource(R.string.hide_from_feed_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            HideDialogItem(
                SettingsItem(
                    icon = Icons.Filled.Try,
                    title = stringResource(R.string.suggested_posts),
                    isActive = hideSuggested.value,
                    onClick = { viewModel.setHideSuggested(!hideSuggested.value) }
                )
            )

            HideDialogItem(
                SettingsItem(
                    icon = Icons.Filled.Camera,
                    title = stringResource(R.string.reels),
                    isActive = hideReels.value,
                    onClick = { viewModel.setHideReels(!hideReels.value) }
                )
            )

            HideDialogItem(
                SettingsItem(
                    icon = Icons.Filled.BurstMode,
                    title = stringResource(R.string.stories),
                    isActive = hideStories.value,
                    onClick = { viewModel.setHideStories(!hideStories.value) }
                )
            )

            if (!effectiveDesktop(viewModel.desktopLayout.collectAsState().value, rememberAutoDesktop())) {
                HideDialogItem(
                    SettingsItem(
                        icon = Icons.Filled.EmojiPeople,
                        title = stringResource(R.string.people_you_may_know),
                        isActive = hidePeopleYouMayKnow.value,
                        onClick = { viewModel.setHidePeopleYouMayKnow(!hidePeopleYouMayKnow.value) }
                    )
                )

                HideDialogItem(
                    SettingsItem(
                        icon = Icons.Filled.Diversity1,
                        title = stringResource(R.string.groups),
                        isActive = hideGroups.value,
                        onClick = { viewModel.setHideGroups(!hideGroups.value) }
                    )
                )
            }
        }
    }

}

@Composable
private fun HideDialogItem(item: SettingsItem) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .clickable { item.onClick() }
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            item.icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onBackground.copy(0.6F)
        )

        Text(
            text = item.title,
            color = MaterialTheme.colorScheme.onBackground.copy(0.6F),
            fontSize = 18.sp,
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1F)
        )

        item.isActive?.let {
            Switch(
                checked = item.isActive,
                onCheckedChange = { item.onClick() },
                modifier = Modifier
            )
        }
    }
}

private val PIP_RATIO_PRESETS = listOf(
    "4:7",
    "2:3",
    "3:4",
    "9:16",
)

@Composable
private fun pipRatioLabel(ratio: String): String = when (ratio) {
    "4:7"  -> stringResource(R.string.pip_ratio_4_7)
    "2:3"  -> stringResource(R.string.pip_ratio_2_3)
    "3:4"  -> stringResource(R.string.pip_ratio_3_4)
    "9:16" -> stringResource(R.string.pip_ratio_9_16)
    else   -> ratio
}

@Composable
private fun appLockLabel(mode: String): String = when (mode) {
    AppLock.IMMEDIATELY -> stringResource(R.string.app_lock_immediately)
    AppLock.AFTER_1_MIN -> stringResource(R.string.app_lock_after_1_min)
    AppLock.AFTER_5_MIN -> stringResource(R.string.app_lock_after_5_min)
    AppLock.AFTER_15_MIN -> stringResource(R.string.app_lock_after_15_min)
    else -> stringResource(R.string.app_lock_off)
}

@Composable
private fun textZoomLabel(zoom: String): String =
    if (zoom == TextZoom.DEFAULT) stringResource(R.string.text_size_default, TextZoom.percent(zoom))
    else stringResource(R.string.text_size_percent, TextZoom.percent(zoom))

private const val DEVELOPER_TAPS = 7

private const val SETTINGS_FILE_NAME = "astryxbook-settings.json"
// What the import picker shows: some file managers label .json files as text or as
// generic binary.
private val SETTINGS_FILE_TYPES = arrayOf("application/json", "text/*", "application/octet-stream")

// The app's version; tapping it seven times shows the developer options.
@Composable
private fun VersionFooter(developerOptions: Boolean, onUnlock: () -> Unit) {
    val context = LocalContext.current
    var taps by remember { mutableIntStateOf(0) }
    Text(
        text = stringResource(R.string.app_version, stringResource(R.string.app_name), BuildConfig.VERSION_NAME),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable {
                if (developerOptions) return@clickable
                taps++
                if (taps >= DEVELOPER_TAPS) {
                    onUnlock()
                    Toast.makeText(context, R.string.developer_options_enabled, Toast.LENGTH_SHORT).show()
                }
            }
            .padding(vertical = 12.dp)
    )
}

@Composable
private fun keepScreenOnLabel(mode: String): String = when (mode) {
    KeepScreenOn.WHILE_VIDEO -> stringResource(R.string.keep_screen_on_video)
    KeepScreenOn.ALWAYS -> stringResource(R.string.keep_screen_on_always)
    else -> stringResource(R.string.keep_screen_on_off)
}

// A titled list of radio buttons; options maps each stored value to its label.
@Composable
private fun ChoiceDialog(
    title: String,
    options: Map<String, String>,
    current: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    note: String? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            options.forEach { (value, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .clickable { onSelect(value) }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(
                        selected = value == current,
                        onClick = { onSelect(value) }
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
        }
    }
}
