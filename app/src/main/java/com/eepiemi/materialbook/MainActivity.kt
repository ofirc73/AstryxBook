package com.eepiemi.materialbook

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.eepiemi.materialbook.ui.screens.MaterialbookWebView
import com.eepiemi.materialbook.ui.theme.MaterialbookTheme
import com.eepiemi.materialbook.ui.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    // Shared with the composable tree below (passed explicitly rather than
    // relying on Compose's viewModel() default resolution) so onUserLeaveHint
    // can read the PiP setting without extra plumbing through the UI layer.
    private val settingsVM: SettingsViewModel by viewModels()

    // Live playback state reported by PipBridge — not settings-backed, so it
    // isn't part of SettingsViewModel; just a plain flag read at the one
    // moment it matters (onUserLeaveHint).
    @Volatile
    private var isVideoPlaying = false

    @Volatile
    private var currentAspectRatio = Rational(16, 9)

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val intentUrl = intent?.data?.toString()
            MaterialbookTheme {
                MaterialbookWebView(
                    url = intentUrl
                        ?: "https://facebook.com/",
                    settingsVM = settingsVM,
                    onVideoPlayingChanged = { isPlaying, videoWidth, videoHeight ->
                        updateVideoPlaybackState(isPlaying, videoWidth, videoHeight)
                    }
                )
            }
        }
    }

    private fun updateVideoPlaybackState(
        isPlaying: Boolean,
        videoWidth: Int,
        videoHeight: Int
    ) {
        isVideoPlaying = isPlaying
        if (videoWidth > 0 && videoHeight > 0) {
            currentAspectRatio = if (videoHeight > videoWidth) {
                Rational(9, 16)
            } else {
                Rational(16, 9)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val pipParams = PictureInPictureParams.Builder()
                .setAspectRatio(currentAspectRatio)
                .setAutoEnterEnabled(isVideoPlaying && settingsVM.pipEnabled.value)
                .build()
            setPictureInPictureParams(pipParams)
        }
    }

    // Called right before the user leaves via Home or the recents switcher —
    // the standard Android hook for triggering Picture-in-Picture (same point
    // YouTube and other video apps use it from).
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            isVideoPlaying &&
            settingsVM.pipEnabled.value
        ) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(currentAspectRatio)
                    .build()
            )
        }
    }
}
