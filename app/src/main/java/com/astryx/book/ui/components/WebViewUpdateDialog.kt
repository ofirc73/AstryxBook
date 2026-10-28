package com.astryx.book.ui.components
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.eepiemi.materialbook.R
import com.astryx.book.utils.MIN_WEBVIEW_MAJOR

/**
 * The phone's Android System WebView is older than the page scripts need (see
 * MIN_WEBVIEW_MAJOR): offers its Play Store page. Shown once per app start while it's old.
 */
@Composable
fun WebViewUpdateDialog(
    version: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.webview_update_title)) },
        text = { Text(stringResource(R.string.webview_update_text, version, MIN_WEBVIEW_MAJOR)) },
        confirmButton = {
            TextButton(onClick = onUpdate) { Text(stringResource(R.string.webview_update_open)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.webview_update_later)) }
        }
    )
}
