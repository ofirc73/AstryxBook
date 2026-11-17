package com.astryx.book
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.astryx.book.utils.StoryText
import com.astryx.book.utils.StoryTextLayer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A story's photo with the native share screen's texts drawn onto it (StoryText.render), on a real
 * canvas: what goes to Facebook in place of the shared photo. No live check: nothing of it depends
 * on Facebook's page.
 */
@RunWith(AndroidJUnit4::class)
class StoryTextRenderTest {
    private val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "story_text_test").apply { mkdirs() }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun photo(name: String, width: Int, height: Int): File {
        val file = File(dir, name)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.DKGRAY) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        return file
    }

    @Test
    fun render_drawsTheTextOntoThePhoto_atItsPlace() {
        val source = photo("photo.jpg", 600, 1000)
        val out = File(dir, "out.jpg")
        val layer = StoryTextLayer("MMMM", color = Color.WHITE, background = true, x = 0.5f, y = 0.2f, size = 0.12f)
        assertTrue(StoryText.render(source, listOf(layer), out))
        val drawn = BitmapFactory.decodeFile(out.path)
        assertEquals(600, drawn.width)
        assertEquals(1000, drawn.height)
        // The white box at the text's place, the photo's grey elsewhere.
        val box = StoryText.bounds(layer, 600, 1000)
        val inBox = drawn.getPixel(box.left.toInt() + 4, box.centerY().toInt())
        assertTrue("no box where the text is: ${Integer.toHexString(inBox)}", Color.red(inBox) > 200)
        assertTrue("the photo changed away from the text", Color.red(drawn.getPixel(300, 900)) < 100)
    }

    @Test
    fun render_keepsAPhotoWithinTheStorySize() {
        val source = photo("big.jpg", 4096, 3072)
        val out = File(dir, "big_out.jpg")
        assertTrue(StoryText.render(source, listOf(StoryTextLayer("x")), out))
        val drawn = BitmapFactory.decodeFile(out.path)
        assertEquals(StoryText.STORY_MAX_SIDE, maxOf(drawn.width, drawn.height))
        assertEquals(4096f / 3072, drawn.width.toFloat() / drawn.height, 0.01f)
    }

    @Test
    fun render_failsOnAFileThatIsntAPhoto() {
        val notAPhoto = File(dir, "text.jpg").apply { writeText("not a photo") }
        assertFalse(StoryText.render(notAPhoto, listOf(StoryTextLayer("x")), File(dir, "none.jpg")))
    }
}
