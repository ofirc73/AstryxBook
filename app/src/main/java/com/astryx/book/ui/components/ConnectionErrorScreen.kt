package com.astryx.book.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.astryx.book.utils.WhenConnectionReturns
import com.eepiemi.materialbook.R
import java.util.concurrent.atomic.AtomicInteger

/** The times the page was loaded again from [ConnectionErrorScreen] since the app started (for the tests). */
internal val connectionRetries = AtomicInteger()

/**
 * Shown over the page when Facebook can't be reached (isConnectionError), in place of the browser's
 * own error page: what to do, a button that loads the page again, and it loads again by itself when
 * the connection comes back ([WhenConnectionReturns]). The page stays underneath, with its history:
 * the dialog this replaces took the page away and could only close the app.
 */
@Composable
fun ConnectionErrorScreen(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val retry = {
        connectionRetries.incrementAndGet()
        onRetry()
    }
    WhenConnectionReturns(retry)
    // A Surface takes the touches, so none reaches the page below.
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.network_error_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.network_error_description),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Button(onClick = retry) {
                Text(stringResource(R.string.connection_retry))
            }
            Text(
                stringResource(R.string.connection_auto_retry),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
