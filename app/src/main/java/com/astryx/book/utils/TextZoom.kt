package com.astryx.book.utils
import java.math.BigDecimal
import kotlin.math.roundToInt

/**
 * Values of the "Text size" setting (shown as "Page zoom"): the main view's page zoom in percent
 * (page_zoom.js; m.facebook.com cancels WebSettings.textZoom), stored as strings like the app's
 * other choice settings. The Messages layer uses it as its text zoom, which the desktop site
 * keeps (only the mobile site cancels it).
 */
object TextZoom {
    const val DEFAULT = "100"
    val ALL = listOf("85", "100", "115", "130", "150")

    /** The stored value as a percentage; an unknown value counts as the default. */
    fun percent(stored: String): Int = stored.takeIf { it in ALL }?.toInt() ?: DEFAULT.toInt()

    /**
     * Facebook's "vpd" cookie for a WebView of [widthPx] x [heightPx] at [percent] zoom. The
     * mobile site's server lays each page out for the viewport this cookie reports (height x
     * width x pixel ratio, in CSS px); its own scripts write it on every load, so after a change
     * of zoom the app sets it ahead of the reload to have that page laid out zoomed as well.
     */
    fun viewportCookie(widthPx: Int, heightPx: Int, density: Float, percent: Int): String {
        val scale = density * percent / 100f
        val width = (widthPx / scale).roundToInt()
        val height = (heightPx / scale).roundToInt()
        val ratio = BigDecimal(density.toString()).stripTrailingZeros().toPlainString()
        return "vpd=v1%3B${height}x${width}x$ratio; domain=.facebook.com; path=/; secure"
    }
}
