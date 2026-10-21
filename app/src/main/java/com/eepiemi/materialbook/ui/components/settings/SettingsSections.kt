package com.eepiemi.materialbook.ui.components.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Subject
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material.icons.filled.Padding
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PanoramaWideAngle
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Pinch
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eepiemi.materialbook.R
import com.eepiemi.materialbook.ui.viewmodel.SettingsViewModel
import com.eepiemi.materialbook.utils.AppLock
import com.eepiemi.materialbook.utils.KeepScreenOn
import com.eepiemi.materialbook.utils.TextZoom
import com.eepiemi.materialbook.utils.canUseAppLock
import com.eepiemi.materialbook.utils.effectiveDesktop
import com.eepiemi.materialbook.utils.isAppLockEnabled
import com.eepiemi.materialbook.utils.rememberAutoDesktop

// The sections of Settings (SettingsContent), each reading its own settings; the dialogs
// they open stay in SettingsContent.

// Feed: ads, start page, the feed's hidden parts, download and copy buttons.
@Composable
internal fun FeedSettings(
    viewModel: SettingsViewModel,
    onCustomizeFeed: () -> Unit,
) {
    val removeAds = viewModel.removeAds.collectAsState()
    val startOnFeeds = viewModel.startOnFeeds.collectAsState()
    val enableDownloadContent = viewModel.enableDownloadContent.collectAsState()
    val enableCopyToClipboard = viewModel.enableCopyToClipboard.collectAsState()
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
                icon = Icons.Outlined.DynamicFeed,
                title = stringResource(R.string.start_on_feeds_title),
                supportingText = stringResource(R.string.start_on_feeds_summary),
                isActive = startOnFeeds.value,
                onClick = { viewModel.setStartOnFeeds(!startOnFeeds.value) },
            ),
            SettingsItem(
                icon = Icons.Outlined.GridView,
                title = stringResource(R.string.customize_feed_title),
                supportingText = stringResource(R.string.customize_feed),
                isActive = null,
                onClick = onCustomizeFeed,
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
}

// Appearance: theme, text size, bars, desktop layout, zoom.
@Composable
internal fun AppearanceSettings(
    viewModel: SettingsViewModel,
    onTextSize: () -> Unit,
) {
    val materialYou = viewModel.materialYou.collectAsState()
    val amoledBlack = viewModel.amoledBlack.collectAsState()
    val textZoom = viewModel.textZoom.collectAsState()
    val textZoomLabels = TextZoom.ALL.associateWith { textZoomLabel(it) }
    val stickyNavbar = viewModel.stickyNavbar.collectAsState()
    val immersiveMode = viewModel.immersiveMode.collectAsState()
    val desktopLayout = viewModel.desktopLayout.collectAsState()
    val isAutoDesktop = rememberAutoDesktop()
    val pinchToZoom = viewModel.pinchToZoom.collectAsState()
    val textWrapOnZoom = viewModel.textWrapOnZoom.collectAsState()
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
                onClick = onTextSize
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
        ) + listOfNotNull(
            // Only shown while pinch to zoom is on: it acts on the zoom.
            SettingsItem(
                icon = Icons.AutoMirrored.Outlined.WrapText,
                title = stringResource(R.string.text_wrap_on_zoom_title),
                supportingText = stringResource(R.string.text_wrap_on_zoom_summary),
                isActive = textWrapOnZoom.value,
                onClick = { viewModel.setTextWrapOnZoom(!textWrapOnZoom.value) }
            ).takeIf { pinchToZoom.value }
        )
    )
}

// Video: picture-in-picture (its ratio and lock-screen audio), keep screen on.
@Composable
internal fun VideoSettings(
    viewModel: SettingsViewModel,
    onPipRatio: () -> Unit,
    onKeepScreenOn: () -> Unit,
) {
    val pipEnabled = viewModel.pipEnabled.collectAsState()
    val pipPortraitRatio = viewModel.pipPortraitRatio.collectAsState()
    val pipLockscreenAudio = viewModel.pipLockscreenAudio.collectAsState()
    val keepScreenOn = viewModel.keepScreenOn.collectAsState()
    val keepScreenOnLabels = KeepScreenOn.ALL.associateWith { keepScreenOnLabel(it) }
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
                        onClick = onPipRatio
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
                    onClick = onKeepScreenOn
                )
            )
        }
    )
}

// Messages and links: Messages in desktop mode, opening links.
@Composable
internal fun MessagesLinksSettings(
    viewModel: SettingsViewModel,
    onOpenLinksHint: () -> Unit,
) {
    val messagesDesktop = viewModel.messagesDesktop.collectAsState()
    val openLinksInApp = viewModel.openLinksInApp.collectAsState()
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
                        onClick = onOpenLinksHint
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
}

// Notifications: Facebook notifications and their text.
@Composable
internal fun NotificationsSettings(
    viewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    val facebookNotifications = viewModel.facebookNotifications.collectAsState()
    val facebookNotificationText = viewModel.facebookNotificationText.collectAsState()
    // Facebook notifications needs Android's notification permission (API 33+): asked when the
    // setting is turned on, which only sticks if it's granted.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setFacebookNotifications(true)
        else Toast.makeText(context, R.string.facebook_notifications_denied, Toast.LENGTH_LONG).show()
    }
    SettingsHeading(stringResource(R.string.settings_section_notifications))
    SettingsGroup(
        items = listOfNotNull(
            SettingsItem(
                icon = Icons.Outlined.Notifications,
                title = stringResource(R.string.facebook_notifications_title),
                supportingText = stringResource(R.string.facebook_notifications_summary),
                isActive = facebookNotifications.value,
                onClick = {
                    val needsPermission = !facebookNotifications.value &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    if (needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else viewModel.setFacebookNotifications(!facebookNotifications.value)
                }
            ),
            // Only while Facebook notifications is on, like the PiP options under PiP.
            if (facebookNotifications.value) SettingsItem(
                icon = Icons.AutoMirrored.Outlined.Subject,
                title = stringResource(R.string.facebook_notification_text_title),
                supportingText = stringResource(R.string.facebook_notification_text_summary),
                isActive = facebookNotificationText.value,
                onClick = { viewModel.setFacebookNotificationText(!facebookNotificationText.value) }
            ) else null
        )
    )
}

// Privacy: app lock, screenshots, cache, log out.
@Composable
internal fun PrivacySettings(
    viewModel: SettingsViewModel,
    onAppLock: () -> Unit,
    onLogOut: () -> Unit,
) {
    val context = LocalContext.current
    val appLock = viewModel.appLock.collectAsState()
    val appLockLabels = AppLock.ALL.associateWith { appLockLabel(it) }
    // Checked on each composition: the user may set a screen lock and come back.
    val appLockAvailable = canUseAppLock(context)
    val appLockOnScreenOff = viewModel.appLockOnScreenOff.collectAsState()
    val blockScreenshots = viewModel.blockScreenshots.collectAsState()
    val clearCacheOnLeave = viewModel.clearCacheOnLeave.collectAsState()
    SettingsHeading(stringResource(R.string.settings_section_privacy))
    SettingsGroup(
        items = listOfNotNull(
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
                        onAppLock()
                    } else {
                        runCatching { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
                    }
                }
            ),
            // Only shown while the lock is on: it changes when the lock locks.
            SettingsItem(
                icon = Icons.Outlined.ScreenLockPortrait,
                title = stringResource(R.string.app_lock_on_screen_off_title),
                supportingText = stringResource(R.string.app_lock_on_screen_off_summary),
                isActive = appLockOnScreenOff.value,
                onClick = { viewModel.setAppLockOnScreenOff(!appLockOnScreenOff.value) }
            ).takeIf { appLockAvailable && isAppLockEnabled(appLock.value) },
            SettingsItem(
                icon = Icons.Outlined.Screenshot,
                title = stringResource(R.string.block_screenshots_title),
                supportingText = stringResource(R.string.block_screenshots_summary),
                isActive = blockScreenshots.value,
                onClick = { viewModel.setBlockScreenshots(!blockScreenshots.value) }
            ),
            SettingsItem(
                icon = Icons.Outlined.CleaningServices,
                title = stringResource(R.string.clear_cache_on_leave_title),
                supportingText = stringResource(R.string.clear_cache_on_leave_summary),
                isActive = clearCacheOnLeave.value,
                onClick = { viewModel.setClearCacheOnLeave(!clearCacheOnLeave.value) }
            ),
            SettingsItem(
                icon = Icons.AutoMirrored.Outlined.Logout,
                title = stringResource(R.string.log_out_title),
                supportingText = stringResource(R.string.log_out_summary),
                isActive = null,
                isDestructive = true,
                onClick = onLogOut
            )
        )
    )
}

// Backup: export and import settings, through the system file picker (no storage permission).
@Composable
internal fun BackupSettings(
    viewModel: SettingsViewModel,
) {
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
}

// Developer options (hidden until the version is tapped seven times) and the version.
@Composable
internal fun DeveloperSettings(
    viewModel: SettingsViewModel,
) {
    val developerOptions = viewModel.developerOptions.collectAsState()
    val webViewDebugging = viewModel.webViewDebugging.collectAsState()
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

internal const val SETTINGS_FILE_NAME = "astryxbook-settings.json"
// What the import picker shows: some file managers label .json files as text or as
// generic binary.
internal val SETTINGS_FILE_TYPES = arrayOf("application/json", "text/*", "application/octet-stream")
