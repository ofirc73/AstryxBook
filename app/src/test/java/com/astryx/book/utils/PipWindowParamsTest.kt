package com.astryx.book.utils
import android.util.Rational
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PipWindowParamsTest {

    private val reel = Rational(9, 16)
    private val playing = PipWindowParams(reel, playing = true)
    private val paused = PipWindowParams(reel, playing = false)
    @Test
    fun outsidePip_everyUpdateIsSent() {
        assertTrue(pipParamsNeedSending(inPip = false, sent = playing, next = playing))
        assertTrue(pipParamsNeedSending(inPip = false, sent = playing, next = paused))
    }

    @Test
    fun inPip_anUnchangedUpdateIsNotSent() {
        // Facebook paused after a resize and the guard resumed it: the window still shows Pause.
        assertFalse(pipParamsNeedSending(inPip = true, sent = playing, next = playing))
        assertFalse(pipParamsNeedSending(inPip = true, sent = playing, next = PipWindowParams(Rational(18, 32), true)))
    }

    @Test
    fun inPip_aPauseIsSentAtOnce() {
        assertTrue(pipParamsNeedSending(inPip = true, sent = playing, next = paused))
    }

    @Test
    fun inPip_playAndANewRatioAreSent() {
        assertTrue(pipParamsNeedSending(inPip = true, sent = paused, next = playing))
        assertTrue(pipParamsNeedSending(inPip = true, sent = playing, next = PipWindowParams(Rational(16, 9), true)))
        assertTrue(pipParamsNeedSending(inPip = true, sent = null, next = playing))
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
