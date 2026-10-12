package com.eepiemi.materialbook.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.eepiemi.materialbook.R
import com.eepiemi.materialbook.utils.linkScheme

/** Asks before a page sends the user into another app (see externalLinkAction). */
@Composable
fun ExternalLinkDialog(
    link: String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.external_link_title)) },
        // The scheme, not the whole link: Facebook's carry long tokens that mean nothing to a reader.
        text = { Text(stringResource(R.string.external_link_text, linkScheme(link))) },
        confirmButton = {
            TextButton(onClick = onOpen) { Text(stringResource(R.string.external_link_open)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

/**
 * Facebook sends its Messages tab to the Messenger app. Offers Messages in desktop mode
 * instead (read and send in this app), or the Messenger app.
 */
@Composable
fun MessengerLinkDialog(
    onUseDesktop: () -> Unit,
    onOpenApp: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.messenger_link_title)) },
        text = { Text(stringResource(R.string.messenger_link_text)) },
        confirmButton = {
            TextButton(onClick = onUseDesktop) { Text(stringResource(R.string.messenger_link_use_desktop)) }
        },
        dismissButton = {
            TextButton(onClick = onOpenApp) { Text(stringResource(R.string.messenger_link_open_app)) }
        }
    )
}
