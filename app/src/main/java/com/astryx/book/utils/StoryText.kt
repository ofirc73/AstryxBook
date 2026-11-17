package com.astryx.book.utils
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.ExifInterface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A text the user put on a shared photo on the native share screen (NativeShareScreen): drawn
 * onto the photo before it goes to Facebook, so the story shows it as placed. Its place and size
 * are fractions of the photo, so the same layer draws alike on the screen's preview and on the
 * full-size photo.
 *
 * [x], [y]: the text's centre (0..1 of the photo's width and height). [size]: the text's size, a
 * fraction of the photo's width. [background]: a rounded box of [color] behind the text, which is
 * then black or white, whichever reads on it.
 */
data class StoryTextLayer(
    val text: String,
    val color: Int = Color.WHITE,
    val background: Boolean = false,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val size: Float = StoryText.DEFAULT_SIZE,
)

object StoryText {
    const val DEFAULT_SIZE = 0.08f
    const val MIN_SIZE = 0.03f
    const val MAX_SIZE = 0.3f

    /** The colours the text tool offers, as in the Facebook app's: white first. */
    val COLORS = listOf(
        Color.WHITE, Color.BLACK, 0xFFE53935.toInt(), 0xFFFB8C00.toInt(), 0xFFFDD835.toInt(),
        0xFF43A047.toInt(), 0xFF1E88E5.toInt(), 0xFF8E24AA.toInt(), 0xFFEC407A.toInt(),
    )

    // A line may take this much of the photo's width before it wraps.
    private const val WRAP_WIDTH = 0.9f
    // The box around the text, in text sizes.
    private const val PADDING = 0.3f
    private const val CORNER = 0.35f

    /** The text's colour on [layer]'s box: black on a light colour, white on a dark one. */
    fun textColor(layer: StoryTextLayer): Int {
        if (!layer.background) return layer.color
        val luminance = (0.299 * Color.red(layer.color) + 0.587 * Color.green(layer.color) + 0.114 * Color.blue(layer.color)) / 255
        return if (luminance > 0.6) Color.BLACK else Color.WHITE
    }

    private fun paint(layer: StoryTextLayer, width: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = max(1f, layer.size * width)
        typeface = Typeface.DEFAULT_BOLD
        color = textColor(layer)
        if (!layer.background) setShadowLayer(textSize * 0.08f, 0f, textSize * 0.04f, 0x99000000.toInt())
    }

    private fun layout(layer: StoryTextLayer, width: Int): StaticLayout {
        val paint = paint(layer, width)
        val wrap = max(1, (width * WRAP_WIDTH).roundToInt())
        val text = layer.text.ifEmpty { " " }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, wrap)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()
    }

    // The widest line, the layout's own width being the wrap width.
    private fun usedWidth(layout: StaticLayout): Float =
        (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) } ?: 0f

    /** Where [layer] covers a [width] x [height] photo (its box, padding included), for a finger to find it. */
    fun bounds(layer: StoryTextLayer, width: Int, height: Int): RectF {
        val layout = layout(layer, width)
        val pad = layout.paint.textSize * PADDING
        val w = usedWidth(layout) + 2 * pad
        val h = layout.height + 2 * pad
        val cx = layer.x * width
        val cy = layer.y * height
        return RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
    }

    /** Draws [layer] on [canvas], which shows a [width] x [height] photo from its top left. */
    fun draw(canvas: Canvas, layer: StoryTextLayer, width: Int, height: Int) {
        if (layer.text.isBlank()) return
        val layout = layout(layer, width)
        val box = bounds(layer, width, height)
        if (layer.background) {
            val radius = layout.paint.textSize * CORNER
            canvas.drawRoundRect(box, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = layer.color })
        }
        canvas.save()
        // The layout is as wide as its wrap width, its lines centred in it.
        canvas.translate(layer.x * width - layout.width / 2f, layer.y * height - layout.height / 2f)
        layout.draw(canvas)
        canvas.restore()
    }

    /**
     * [file] decoded at most [maxSide] px on its longer side (a power-of-two step down, then
     * scaled), turned as its EXIF says; null when it can't be read.
     */
    fun decode(file: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val matrix = Matrix()
        val scale = min(1f, maxSide.toFloat() / max(decoded.width, decoded.height))
        if (scale < 1f) matrix.postScale(scale, scale)
        exifRotation(file)?.let { matrix.postRotate(it) }
        if (matrix.isIdentity) return decoded
        val turned = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (turned !== decoded) decoded.recycle()
        return turned
    }

    private fun exifRotation(file: File): Float? = runCatching {
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> null
        }
    }.getOrNull()

    /** The longest side a story photo is drawn at: Facebook shows stories at 1080 x 1920. */
    const val STORY_MAX_SIDE = 2048

    /**
     * [source] with [layers] drawn on it, written as a JPEG to [out]; false when it can't be read
     * or written. Without layers the photo is left as it is (the caller hands it on unchanged).
     */
    fun render(source: File, layers: List<StoryTextLayer>, out: File): Boolean {
        val bitmap = decode(source, STORY_MAX_SIDE) ?: return false
        val drawn = if (bitmap.isMutable) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, true).also { bitmap.recycle() }
        return try {
            val canvas = Canvas(drawn)
            layers.forEach { draw(canvas, it, drawn.width, drawn.height) }
            out.outputStream().use { drawn.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        } catch (e: Exception) {
            false
        } finally {
            drawn.recycle()
        }
    }
}
