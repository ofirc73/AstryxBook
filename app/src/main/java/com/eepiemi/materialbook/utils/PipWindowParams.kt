package com.eepiemi.materialbook.utils

import android.util.Rational
import kotlin.math.abs

// A PiP window the user pinched or dragged to a new size went back to its first size as soon as
// the fingers lifted (Samsung's One UI puts it back on each PiP params update). Facebook pauses the
// video after every resize of the page and the keep-playing guard resumes it, so each resize made
// the app send the window a Play button, then a Pause button. While in PiP, MainActivity sends only
// updates that change what the window shows, and holds a pause the page reports for a moment.

/** What the PiP window shows: its aspect ratio and the Play/Pause button. */
internal data class PipWindowParams(val ratio: Rational, val playing: Boolean)

/** How long a pause the page reports in PiP waits before the window is sent the Play button. */
internal const val PIP_PAUSE_SETTLE_MS = 1500L

/** A pause this soon after a tap on the window's Play/Pause button is the user's: sent at once. */
internal const val PIP_USER_TOGGLE_MS = 3000L

/**
 * When to send the PiP window [next]: null when it already shows that, 0 for now, else a delay
 * in ms (cancelled by the next report). Outside PiP every update is sent at once, as before.
 */
internal fun pipParamsDelayMs(
    inPip: Boolean,
    sent: PipWindowParams?,
    next: PipWindowParams,
    msSinceUserToggle: Long,
): Long? = when {
    !inPip -> 0L
    next == sent -> null
    sent?.playing == true && !next.playing && msSinceUserToggle >= PIP_USER_TOGGLE_MS -> PIP_PAUSE_SETTLE_MS
    else -> 0L
}

/**
 * The PiP window keeps its ratio when the video's changes by less than 2%: Facebook switching the
 * video's quality (854x480 after 1280x720) would otherwise resize the window.
 */
internal fun steadyPipRatio(current: Rational, next: Rational): Rational =
    if (abs(next.toFloat() / current.toFloat() - 1f) < 0.02f) current else next
