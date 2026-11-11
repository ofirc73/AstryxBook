package com.astryx.book.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.astryx.book.utils.CaptureKind
import com.eepiemi.materialbook.R

/**
 * Where a file the page asked for comes from, when the camera can give one too ([kinds], see
 * captureKinds): the phone's photos and files (Android's picker, as before), a photo taken now or
 * a video recorded now. As a browser asks on a page's Photo button.
 */
@Composable
fun AttachSourceDialog(
    kinds: Set<CaptureKind>,
    onFiles: () -> Unit,
    onCapture: (CaptureKind) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.attach_title)) },
        text = {
            Column {
                Source(R.string.attach_files, onFiles)
                if (CaptureKind.PHOTO in kinds) Source(R.string.attach_take_photo) { onCapture(CaptureKind.PHOTO) }
                if (CaptureKind.VIDEO in kinds) Source(R.string.attach_record_video) { onCapture(CaptureKind.VIDEO) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Source(label: Int, onClick: () -> Unit) {
    Text(
        stringResource(label),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
    )
}
