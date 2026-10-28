package com.astryx.book
import android.app.Activity
import android.app.KeyguardManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.astryx.book.utils.AppLock
import com.astryx.book.utils.canUseAppLock
import com.astryx.book.utils.isAppLockEnabled
import com.astryx.book.utils.shouldLockOnReturn
import com.eepiemi.materialbook.R

/**
 * App lock for [activity]. While locked, AppLockScreen covers the page and the system prompt asks
 * for the fingerprint/face or the screen lock. The page keeps running underneath (PiP and
 * lock-screen audio are unaffected). Locks on every cold start, and on coming back after being
 * out of sight for the setting's grace period (see shouldLockOnReturn). [mode] is the setting
 * (AppLock). Created with the activity: it registers for an activity result.
 */
class AppLockController(private val activity: FragmentActivity, private val mode: () -> String) {

    var isLocked by mutableStateOf(false)
        private set

    // When the activity was last stopped, on the monotonic clock; null while visible.
    private var hiddenAt: Long? = null
    // The screen turned off with "Lock when the screen turns off" on (MainActivity's
    // screenOffReceiver).
    var lockOnNextStart = false
    // The prompt (or the screen-lock confirmation, which is an activity of its own) is up:
    // the stop/start it causes doesn't count as leaving the app.
    private var isAuthenticating = false
    private lateinit var biometricPrompt: BiometricPrompt

    // Android 8.x: the system's screen-lock confirmation (BiometricPrompt's own fallback
    // there is a fingerprint-only dialog).
    private val confirmDeviceCredential =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            isAuthenticating = false
            if (result.resultCode == Activity.RESULT_OK) isLocked = false
        }

    /** From the activity's onCreate: sets up the prompt and locks a cold start. */
    fun onCreate() {
        biometricPrompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    isAuthenticating = false
                    isLocked = false
                }

                // Cancelled or failed for good: stay locked; AppLockScreen's button retries.
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    isAuthenticating = false
                }
            }
        )
        lockIfEnabled()
    }

    private fun lockIfEnabled() {
        if (isAppLockEnabled(mode()) && canUseAppLock(activity)) isLocked = true
    }

    fun authenticate() {
        if (!isLocked || isAuthenticating) return
        // The screen lock was removed since the app locked: nothing could unlock it.
        if (!canUseAppLock(activity)) {
            isLocked = false
            return
        }
        isAuthenticating = true
        val title = activity.getString(R.string.app_lock_prompt_title, activity.getString(R.string.app_name))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            biometricPrompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setAllowedAuthenticators(AppLock.AUTHENTICATORS)
                    .build()
            )
        } else {
            @Suppress("DEPRECATION")
            val intent = activity.getSystemService(KeyguardManager::class.java)
                ?.createConfirmDeviceCredentialIntent(title, null)
            if (intent == null) {
                isAuthenticating = false
                isLocked = false
            } else {
                confirmDeviceCredential.launch(intent)
            }
        }
    }

    /** From onStart: locks again after the grace period, or after the screen went off. */
    fun onStart() {
        if (!isAuthenticating && (lockOnNextStart ||
                shouldLockOnReturn(mode(), hiddenAt, SystemClock.elapsedRealtime()))
        ) {
            lockIfEnabled()
        }
        hiddenAt = null
        lockOnNextStart = false
    }

    /** From onResume: asks right away while locked (not in PiP, where nothing can be asked). */
    fun onResume(isInPictureInPictureMode: Boolean) {
        if (isLocked && !isInPictureInPictureMode) authenticate()
    }

    /** From onStop: the app went out of sight (not for the prompt or a configuration change). */
    fun onStop(isChangingConfigurations: Boolean) {
        if (!isAuthenticating && !isChangingConfigurations) hiddenAt = SystemClock.elapsedRealtime()
    }
}
