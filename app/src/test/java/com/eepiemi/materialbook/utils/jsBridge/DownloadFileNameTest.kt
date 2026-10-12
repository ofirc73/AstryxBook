package com.eepiemi.materialbook.utils.jsBridge

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/** Downloads are named after where they came from and when, not a bare timestamp. */
class DownloadFileNameTest {

    @Test
    fun namesFilesFacebookDateAndTime() {
        val time = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 12, 9, 31, 5) }.time
        assertEquals("Facebook_20261012_093105.mp4", downloadFileName(time, "mp4"))
        assertEquals("Facebook_20261012_093105.png", downloadFileName(time, "png"))
    }
}
