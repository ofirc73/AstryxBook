package com.astryx.book.utils
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Where the texts the native share screen draws onto a story's photo land (StoryText). Drawing
 * them is checked on a device (StoryTextRenderTest): Robolectric's canvas here draws nothing.
 */
@RunWith(RobolectricTestRunner::class)
class StoryTextTest {

    @Test
    fun aTextsBox_isCentredOnItsPlace_andGrowsWithItsSize() {
        val layer = StoryTextLayer("Hello", x = 0.25f, y = 0.75f)
        val box = StoryText.bounds(layer, 1000, 2000)
        assertEquals(250f, box.centerX(), 1f)
        assertEquals(1500f, box.centerY(), 1f)
        val bigger = StoryText.bounds(layer.copy(size = layer.size * 2), 1000, 2000)
        assertTrue("a bigger text has a bigger box", bigger.width() > box.width() * 1.5f)
    }

    @Test
    fun theSameText_coversTheSameShareOfThePhoto_atAnySize() {
        // The screen's preview and the full-size photo draw the same layer: it must land alike.
        val layer = StoryTextLayer("Same place", x = 0.4f, y = 0.3f)
        val small = StoryText.bounds(layer, 400, 800)
        val large = StoryText.bounds(layer, 2000, 4000)
        assertEquals(small.width() / 400, large.width() / 2000, 0.02f)
        assertEquals(small.top / 800, large.top / 4000, 0.02f)
    }

    @Test
    fun aLongText_wrapsWithinThePhoto() {
        val box = StoryText.bounds(StoryTextLayer("word ".repeat(40)), 1000, 2000)
        assertTrue("a long text ran past the photo's width: ${box.width()}", box.width() <= 1000f)
    }

    @Test
    fun onABox_theTextIsBlackOnLightColours_andWhiteOnDarkOnes() {
        assertEquals(Color.BLACK, StoryText.textColor(StoryTextLayer("a", color = Color.WHITE, background = true)))
        assertEquals(Color.WHITE, StoryText.textColor(StoryTextLayer("a", color = Color.BLACK, background = true)))
        // Without a box the text is its own colour.
        assertEquals(Color.WHITE, StoryText.textColor(StoryTextLayer("a", color = Color.WHITE)))
    }
}
