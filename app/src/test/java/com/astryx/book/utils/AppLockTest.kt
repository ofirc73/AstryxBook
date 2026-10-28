package com.astryx.book.utils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {

    @Test
    fun off_andUnknownValues_areNotEnabled() {
        assertFalse(isAppLockEnabled(AppLock.OFF))
        assertFalse(isAppLockEnabled("bogus"))
        assertFalse(isAppLockEnabled(""))
    }

    @Test
    fun everyOtherValue_isEnabled() {
        AppLock.ALL.filter { it != AppLock.OFF }.forEach { assertTrue(it, isAppLockEnabled(it)) }
    }

    @Test
    fun off_neverLocks() {
        assertFalse(shouldLockOnReturn(AppLock.OFF, hiddenAtMillis = 0, nowMillis = 10_000_000))
    }

    @Test
    fun notHidden_doesNotLock() {
        assertFalse(shouldLockOnReturn(AppLock.IMMEDIATELY, hiddenAtMillis = null, nowMillis = 10_000))
    }

    @Test
    fun immediately_locksOnAnyReturn() {
        assertTrue(shouldLockOnReturn(AppLock.IMMEDIATELY, hiddenAtMillis = 5_000, nowMillis = 5_000))
        assertTrue(shouldLockOnReturn(AppLock.IMMEDIATELY, hiddenAtMillis = 5_000, nowMillis = 5_001))
    }

    @Test
    fun gracePeriod_locksOnlyOnceItHasPassed() {
        val hidden = 1_000L
        assertFalse(shouldLockOnReturn(AppLock.AFTER_1_MIN, hidden, hidden + 59_999))
        assertTrue(shouldLockOnReturn(AppLock.AFTER_1_MIN, hidden, hidden + 60_000))
        assertFalse(shouldLockOnReturn(AppLock.AFTER_5_MIN, hidden, hidden + 299_999))
        assertTrue(shouldLockOnReturn(AppLock.AFTER_5_MIN, hidden, hidden + 300_000))
        assertFalse(shouldLockOnReturn(AppLock.AFTER_15_MIN, hidden, hidden + 899_999))
        assertTrue(shouldLockOnReturn(AppLock.AFTER_15_MIN, hidden, hidden + 900_000))
    }

    @Test
    fun unknownStoredValue_neverLocks() {
        assertFalse(shouldLockOnReturn("bogus", hiddenAtMillis = 0, nowMillis = 10_000_000))
    }

    @Test
    fun screenOff_locksOnlyWithTheLockAndTheOptionOn() {
        assertTrue(locksOnScreenOff(AppLock.AFTER_15_MIN, lockOnScreenOff = true))
        assertTrue(locksOnScreenOff(AppLock.IMMEDIATELY, lockOnScreenOff = true))
        assertFalse(locksOnScreenOff(AppLock.AFTER_15_MIN, lockOnScreenOff = false))
        assertFalse(locksOnScreenOff(AppLock.OFF, lockOnScreenOff = true))
        assertFalse(locksOnScreenOff("bogus", lockOnScreenOff = true))
    }
}
