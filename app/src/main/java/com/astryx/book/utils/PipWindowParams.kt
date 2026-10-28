package com.astryx.book.utils
import android.util.Rational
import kotlin.math.abs

// While in PiP, MainActivity sends the window only updates that change what it shows. These updates
// don't undo a resize (checked on One UI 9: the window kept its size through them); a window that
// stops growing at about half the screen's width has reached Android's largest PiP size for a
// portrait ratio (its longest side about the screen's width).

/** What the PiP window shows: its aspect ratio and the Play/Pause button. */
internal data class PipWindowParams(val ratio: Rational, val playing: Boolean)

/** Whether the PiP window should be sent [next]. Outside PiP every update is sent, as before. */
internal fun pipParamsNeedSending(inPip: Boolean, sent: PipWindowParams?, next: PipWindowParams): Boolean =
    !inPip || next != sent

/**
 * The PiP window keeps its ratio when the video's changes by less than 2%: Facebook switching the
 * video's quality (854x480 after 1280x720) would otherwise resize the window.
 */
internal fun steadyPipRatio(current: Rational, next: Rational): Rational =
    if (abs(next.toFloat() / current.toFloat() - 1f) < 0.02f) current else next
