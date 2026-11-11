package com.astryx.book.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Tells the moment the connection comes back: a network that reaches the internet, after there
 * was none. A network that was there all along isn't one: the page failed for another reason (a
 * name that doesn't resolve, a refused connection), and trying again by itself would only fail
 * again, over and over.
 */
class NetworkReturn(online: Boolean) {
    private var offline = !online

    /** The network in use is gone. */
    fun onLost() {
        offline = true
    }

    /** A network reaches the internet: true if that's the connection coming back, once. */
    fun onOnline(): Boolean {
        if (!offline) return false
        offline = false
        return true
    }
}

private fun isOnline(manager: ConnectivityManager): Boolean =
    manager.getNetworkCapabilities(manager.activeNetwork)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

/**
 * Calls [onReturn] (on the main thread) when the connection comes back ([NetworkReturn]), for as
 * long as this is on screen.
 */
@Composable
fun WhenConnectionReturns(onReturn: () -> Unit) {
    val context = LocalContext.current
    val currentOnReturn by rememberUpdatedState(onReturn)
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val main = Handler(Looper.getMainLooper())
        val connection = NetworkReturn(online = manager != null && isOnline(manager))
        // Android calls these on its own thread.
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                main.post { connection.onLost() }
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return
                main.post { if (connection.onOnline()) currentOnReturn() }
            }
        }
        val registered = runCatching { manager?.registerDefaultNetworkCallback(callback) }.getOrNull() != null
        onDispose {
            if (registered) runCatching { manager?.unregisterNetworkCallback(callback) }
            main.removeCallbacksAndMessages(null)
        }
    }
}
