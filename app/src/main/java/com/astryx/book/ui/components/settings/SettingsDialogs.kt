package com.astryx.book.ui.components.settings
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Diversity1
import androidx.compose.material.icons.filled.EmojiPeople
import androidx.compose.material.icons.filled.Try
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eepiemi.materialbook.BuildConfig
import com.eepiemi.materialbook.R
import com.eepiemi.materialbook.ui.viewmodel.SettingsViewModel
import com.astryx.book.utils.AppLock
import com.astryx.book.utils.KeepScreenOn
import com.astryx.book.utils.TextZoom
import com.eepiemi.materialbook.utils.effectiveDesktop
import com.eepiemi.materialbook.utils.rememberAutoDesktop
import com.eepiemi.materialbook.ui.components.settings.SettingsItem

// Settings' dialogs (choices, hiding parts of the feed), labels and the version footer.

@Composable
internal fun HideOptionsDialog(
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

internal val PIP_RATIO_PRESETS = listOf(
    "4:7",
    "2:3",
    "3:4",
    "9:16",
)

@Composable
internal fun pipRatioLabel(ratio: String): String = when (ratio) {
    "4:7"  -> stringResource(R.string.pip_ratio_4_7)
    "2:3"  -> stringResource(R.string.pip_ratio_2_3)
    "3:4"  -> stringResource(R.string.pip_ratio_3_4)
    "9:16" -> stringResource(R.string.pip_ratio_9_16)
    else   -> ratio
}

@Composable
internal fun appLockLabel(mode: String): String = when (mode) {
    AppLock.IMMEDIATELY -> stringResource(R.string.app_lock_immediately)
    AppLock.AFTER_1_MIN -> stringResource(R.string.app_lock_after_1_min)
    AppLock.AFTER_5_MIN -> stringResource(R.string.app_lock_after_5_min)
    AppLock.AFTER_15_MIN -> stringResource(R.string.app_lock_after_15_min)
    else -> stringResource(R.string.app_lock_off)
}

@Composable
internal fun textZoomLabel(zoom: String): String =
    if (zoom == TextZoom.DEFAULT) stringResource(R.string.text_size_default, TextZoom.percent(zoom))
    else stringResource(R.string.text_size_percent, TextZoom.percent(zoom))


private const val DEVELOPER_TAPS = 7

// The app's version; tapping it seven times shows the developer options.
@Composable
internal fun VersionFooter(developerOptions: Boolean, onUnlock: () -> Unit) {
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
internal fun keepScreenOnLabel(mode: String): String = when (mode) {
    KeepScreenOn.WHILE_VIDEO -> stringResource(R.string.keep_screen_on_video)
    KeepScreenOn.ALWAYS -> stringResource(R.string.keep_screen_on_always)
    else -> stringResource(R.string.keep_screen_on_off)
}

// A titled list of radio buttons; options maps each stored value to its label.
@Composable
internal fun ChoiceDialog(
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
