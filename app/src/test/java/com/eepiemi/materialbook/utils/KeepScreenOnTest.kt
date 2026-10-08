package com.eepiemi.materialbook.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepScreenOnTest {

    @Test
    fun off_neverKeepsTheScreenOn() {
        assertFalse(shouldKeepScreenOn(KeepScreenOn.OFF, isVideoPlaying = false))
        assertFalse(shouldKeepScreenOn(KeepScreenOn.OFF, isVideoPlaying = true))
    }

    @Test
    fun whileVideo_followsPlayback() {
        assertFalse(shouldKeepScreenOn(KeepScreenOn.WHILE_VIDEO, isVideoPlaying = false))
        assertTrue(shouldKeepScreenOn(KeepScreenOn.WHILE_VIDEO, isVideoPlaying = true))
    }

    @Test
    fun always_ignoresPlayback() {
        assertTrue(shouldKeepScreenOn(KeepScreenOn.ALWAYS, isVideoPlaying = false))
        assertTrue(shouldKeepScreenOn(KeepScreenOn.ALWAYS, isVideoPlaying = true))
    }

    @Test
    fun unknownStoredValue_countsAsOff() {
        assertFalse(shouldKeepScreenOn("bogus", isVideoPlaying = true))
        assertFalse(shouldKeepScreenOn("", isVideoPlaying = true))
    }

    @Test
    fun storedValues_areStable() {
        // Persisted in DataStore: changing these strings would reset users' choice.
        assertEquals(listOf("off", "video", "always"), KeepScreenOn.ALL)
    }
}
