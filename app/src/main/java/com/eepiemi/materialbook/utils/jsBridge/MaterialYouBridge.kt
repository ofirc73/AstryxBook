package com.eepiemi.materialbook.utils.jsBridge

import android.webkit.JavascriptInterface

/**
 * The app's current primary colours for `material_you.js`. Read on each call, not fixed when
 * the WebView is created, so turning Material You on or off takes effect on the next reload.
 */
class MaterialYouBridge(
    private val primaryColor: () -> Int,
    private val onPrimaryColor: () -> Int
) {
    private val primary get() = primaryColor()
    private val onPrimary get() = onPrimaryColor()


    @JavascriptInterface
    @Suppress("unused")
    fun getMaterialYouPrimaryRgb(): String {
        return colorToRgb(primary)
    }

    @JavascriptInterface
    @Suppress("unused")
    fun getMaterialYouOnPrimaryRgb(): String {
        return colorToRgb(onPrimary)
    }

    @JavascriptInterface
    @Suppress("unused")
    fun getMaterialYouPrimaryRgbString(): String {
        return colorToRgbString(primary)
    }
    @JavascriptInterface
    @Suppress("unused")
    fun getMaterialYouOnPrimaryRgbString(): String {
        return colorToRgbString(onPrimary)
    }

    private fun colorToRgb(color: Int): String {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return """{"r":$r,"g":$g,"b":$b}"""
    }

    private fun colorToRgbString(color: Int): String {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return "rgb($r, $g, $b)"
    }
}