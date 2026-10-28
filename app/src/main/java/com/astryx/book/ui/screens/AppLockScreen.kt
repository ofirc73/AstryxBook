package com.astryx.book.ui.screens
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.eepiemi.materialbook.R

/**
 * Covers the app while app lock is engaged. The page keeps running underneath (a video in
 * PiP or on the lock screen carries on); this only hides it and takes its touches. The
 * system prompt opens by itself (see MainActivity); [onUnlock] opens it again after the
 * user dismissed it.
 */
@Composable
fun AppLockScreen(onUnlock: () -> Unit) {
    val activity = LocalActivity.current
    // Back leaves the app instead of reaching the page's history underneath.
    BackHandler { activity?.moveTaskToBack(true) }

    Column(
        modifier = Modifier
            .zIndex(10F)
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Takes every touch, so none reaches the page below.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.Lock,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.app_lock_locked, stringResource(R.string.app_name)),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Button(onClick = onUnlock) {
            Text(stringResource(R.string.app_lock_unlock))
        }
    }
}
