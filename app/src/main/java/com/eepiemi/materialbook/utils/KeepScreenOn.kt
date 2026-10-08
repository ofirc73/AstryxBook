package com.eepiemi.materialbook.utils

/** Values of the "Keep screen on" setting (stored as strings). */
object KeepScreenOn {
    const val OFF = "off"
    const val WHILE_VIDEO = "video"
    const val ALWAYS = "always"

    val ALL = listOf(OFF, WHILE_VIDEO, ALWAYS)
}

/**
 * Whether the screen should stay on. An unknown stored value counts as off. "Always" means
 * while the app is on screen: the flag only applies while the view is shown.
 */
fun shouldKeepScreenOn(mode: String, isVideoPlaying: Boolean): Boolean = when (mode) {
    KeepScreenOn.ALWAYS -> true
    KeepScreenOn.WHILE_VIDEO -> isVideoPlaying
    else -> false
}
