package com.eepiemi.materialbook.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PipExitZoomTest {

    @Test
    fun aPageAtTheZoomItHadIsLeftAlone() {
        assertNull(pipExitZoomBy(current = 1f, wanted = 1f))
        assertNull(pipExitZoomBy(current = 1.03f, wanted = 1f))
        assertNull(pipExitZoomBy(current = 2.5f, wanted = 2.5f))
    }

    @Test
    fun aZoomTakenInPip_isUndone() {
        // A resized PiP window of a Facebook video page: back x1.94 (Galaxy A56).
        assertEquals(1f / 1.94f, pipExitZoomBy(current = 1.94f, wanted = 1f)!!, 0.001f)
    }

    @Test
    fun theUsersPinchZoom_comesBack() {
        // Zoomed all the way out in PiP, as before: the user's x2.5 comes back.
        assertEquals(2.5f, pipExitZoomBy(current = 1f, wanted = 2.5f)!!, 0.001f)
        // And from whatever zoom the page took in PiP.
        assertEquals(2.5f / 1.94f, pipExitZoomBy(current = 1.94f, wanted = 2.5f)!!, 0.001f)
    }

    @Test
    fun aReadingThatMakesNoSense_changesNothing() {
        assertNull(pipExitZoomBy(current = 0f, wanted = 1f))
        assertNull(pipExitZoomBy(current = Float.NaN, wanted = 1f))
    }
}
