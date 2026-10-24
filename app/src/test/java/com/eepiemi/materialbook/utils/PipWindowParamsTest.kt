package com.eepiemi.materialbook.utils

import android.util.Rational
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PipWindowParamsTest {

    private val reel = Rational(9, 16)
    private val playing = PipWindowParams(reel, playing = true)
    private val paused = PipWindowParams(reel, playing = false)
    private val longAgo = PIP_USER_TOGGLE_MS + 1

    @Test
    fun outsidePip_everyUpdateIsSentAtOnce() {
        assertEquals(0L, pipParamsDelayMs(inPip = false, sent = playing, next = playing, msSinceUserToggle = longAgo))
        assertEquals(0L, pipParamsDelayMs(inPip = false, sent = playing, next = paused, msSinceUserToggle = longAgo))
    }

    @Test
    fun inPip_anUnchangedUpdateIsNotSent() {
        // Facebook paused after a resize and the guard resumed it: the window still shows Pause.
        assertNull(pipParamsDelayMs(inPip = true, sent = playing, next = playing, msSinceUserToggle = longAgo))
        assertNull(pipParamsDelayMs(inPip = true, sent = playing, next = PipWindowParams(Rational(18, 32), true), msSinceUserToggle = longAgo))
    }

    @Test
    fun inPip_aPauseFromThePageIsHeld() {
        assertEquals(PIP_PAUSE_SETTLE_MS, pipParamsDelayMs(inPip = true, sent = playing, next = paused, msSinceUserToggle = longAgo))
    }

    @Test
    fun inPip_aPauseAfterTheUsersTapIsSentAtOnce() {
        assertEquals(0L, pipParamsDelayMs(inPip = true, sent = playing, next = paused, msSinceUserToggle = 400))
    }

    @Test
    fun inPip_playAndANewRatioAreSentAtOnce() {
        assertEquals(0L, pipParamsDelayMs(inPip = true, sent = paused, next = playing, msSinceUserToggle = longAgo))
        assertEquals(0L, pipParamsDelayMs(inPip = true, sent = playing, next = PipWindowParams(Rational(16, 9), true), msSinceUserToggle = longAgo))
        assertEquals(0L, pipParamsDelayMs(inPip = true, sent = null, next = playing, msSinceUserToggle = longAgo))
    }

    @Test
    fun steadyRatio_keepsTheWindowsRatioForAQualitySwitch() {
        assertEquals(Rational(16, 9), steadyPipRatio(Rational(16, 9), Rational(854, 480)))
    }

    @Test
    fun steadyRatio_takesAnotherVideosRatio() {
        assertEquals(Rational(4, 3), steadyPipRatio(Rational(16, 9), Rational(4, 3)))
        assertEquals(Rational(239, 100), steadyPipRatio(Rational(16, 9), Rational(239, 100)))
    }
}
