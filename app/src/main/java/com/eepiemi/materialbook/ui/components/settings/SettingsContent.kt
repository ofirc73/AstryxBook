package com.eepiemi.materialbook.ui.components.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eepiemi.materialbook.R
import com.eepiemi.materialbook.ui.viewmodel.SettingsViewModel
import com.eepiemi.materialbook.utils.AppLock
import com.eepiemi.materialbook.utils.KeepScreenOn
import com.eepiemi.materialbook.utils.TextZoom

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


    // The settings the page's scripts and user agent are built from (MainViewModel.refresh);
    // the rest apply at once.
    val pageSettings = listOf(
        viewModel.messagesDesktop, viewModel.removeAds, viewModel.enableDownloadContent,
        viewModel.enableCopyToClipboard, viewModel.desktopLayout, viewModel.stickyNavbar,
        viewModel.pinchToZoom, viewModel.textWrapOnZoom, viewModel.materialYou, viewModel.amoledBlack,
        viewModel.hideSuggested, viewModel.hideReels, viewModel.hideStories,
        viewModel.hidePeopleYouMayKnow, viewModel.hideGroups
    ).map { it.collectAsState().value }
    val initialPageSettings = remember { pageSettings }
    val pageSettingsChanged = pageSettings != initialPageSettings
    LaunchedEffect(pageSettingsChanged) { onPageSettingsChanged(pageSettingsChanged) }

    // What the dialogs below show.
    val context = LocalContext.current
    val appLock = viewModel.appLock.collectAsState()
    val appLockLabels = AppLock.ALL.associateWith { appLockLabel(it) }
    val textZoom = viewModel.textZoom.collectAsState()
    val textZoomLabels = TextZoom.ALL.associateWith { textZoomLabel(it) }
    val keepScreenOn = viewModel.keepScreenOn.collectAsState()
    val keepScreenOnLabels = KeepScreenOn.ALL.associateWith { keepScreenOnLabel(it) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FeedSettings(viewModel, onCustomizeFeed = { isOpenDialog = true })
        AppearanceSettings(viewModel, onTextSize = { isTextZoomDialog = true })
        VideoSettings(
            viewModel,
            onPipRatio = { isPipRatioDialog = true },
            onKeepScreenOn = { isKeepScreenOnDialog = true }
        )
        MessagesLinksSettings(viewModel, onOpenLinksHint = { isOpenLinksHint = true })
        NotificationsSettings(viewModel)
        PrivacySettings(
            viewModel,
            onAppLock = { isAppLockDialog = true },
            onLogOut = { isLogOutDialog = true }
        )
        BackupSettings(viewModel)
        DeveloperSettings(viewModel)
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
