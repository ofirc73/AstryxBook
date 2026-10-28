package com.astryx.book.utils
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager

/**
 * Values of the "App lock" setting (stored as strings): off, or how long the app may be
 * out of sight before it locks again, in seconds. A grace period lets the user pick a file
 * or share something (another app on top) without unlocking again.
 */
object AppLock {
    const val OFF = "off"
    const val IMMEDIATELY = "0"
    const val AFTER_1_MIN = "60"
    const val AFTER_5_MIN = "300"
    const val AFTER_15_MIN = "900"

    val ALL = listOf(OFF, IMMEDIATELY, AFTER_1_MIN, AFTER_5_MIN, AFTER_15_MIN)

    /** Fingerprint/face, or the device's PIN, pattern or password: works on every API level. */
    const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
}

/** Whether the lock is on. An unknown stored value counts as off. */
fun isAppLockEnabled(mode: String): Boolean = mode != AppLock.OFF && mode in AppLock.ALL

/**
 * Whether coming back to the app needs unlocking: the lock is on and the app was out of
 * sight (since [hiddenAtMillis], on a monotonic clock) for at least its grace period.
 * Null [hiddenAtMillis] means the app wasn't hidden.
 */
fun shouldLockOnReturn(mode: String, hiddenAtMillis: Long?, nowMillis: Long): Boolean {
    if (!isAppLockEnabled(mode) || hiddenAtMillis == null) return false
    return nowMillis - hiddenAtMillis >= mode.toLong() * 1000
}

/**
 * Whether the device can unlock the app: it has a screen lock (and, from Android 9, the
 * system prompt accepts it). Without one, app lock would lock the user out for good.
 */
fun canUseAppLock(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        BiometricManager.from(context).canAuthenticate(AppLock.AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS
    } else {
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
    }

/**
 * Whether the screen turning off locks the app at once, whatever the grace period: the lock
 * is on along with "Lock when the screen turns off". The grace period is for a quick trip to
 * another app (a file picker, sharing); a phone put away isn't one.
 */
fun locksOnScreenOff(mode: String, lockOnScreenOff: Boolean): Boolean =
    lockOnScreenOff && isAppLockEnabled(mode)
