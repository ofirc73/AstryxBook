package com.astryx.book.utils
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.webkit.WebView

/**
 * Real touches on a WebView's page: MotionEvents the WebView takes as the user's finger, which a
 * page's script can't make (Facebook ignores script-made input in places: Home's composer box for
 * Back's history, the story creator's clip strip). Points are CSS px in the page's viewport,
 * [width] CSS px wide (the WebView's own width in px over it is the scale).
 */
internal fun tapPage(webView: WebView, x: Double, y: Double, width: Double) =
    touchPage(webView, listOf(x to y), width, TAP_MS, holdMs = 0)

/**
 * A finger drag from [fromX] to [toX] at [y] (see [tapPage]); [onDone] once the finger is up. It
 * stays still a moment before it lifts: lifted moving, a scrolling box went on in a fling (Facebook's
 * clip strip overshot by a third of its width, 2026-11-18).
 */
internal fun dragPage(webView: WebView, fromX: Double, toX: Double, y: Double, width: Double, onDone: () -> Unit = {}) =
    touchPage(webView, (0..DRAG_STEPS).map { fromX + (toX - fromX) * it / DRAG_STEPS to y }, width, DRAG_STEP_MS, DRAG_HOLD_MS, onDone)

// The finger down at the first point, through the others [stepMs] apart, held still [holdMs] at the
// last, and up there.
private fun touchPage(webView: WebView, points: List<Pair<Double, Double>>, width: Double, stepMs: Long, holdMs: Long, onDone: () -> Unit = {}) {
    if (!(width > 0) || points.any { it.first.isNaN() || it.second.isNaN() }) {
        onDone()
        return
    }
    val scale = webView.width / width
    val downAt = SystemClock.uptimeMillis()
    val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER })
    fun touch(action: Int, point: Pair<Double, Double>) {
        val coords = arrayOf(MotionEvent.PointerCoords().apply {
            x = (point.first * scale).toFloat(); y = (point.second * scale).toFloat(); pressure = 1f; size = 1f
        })
        val event = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action, 1, properties, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        webView.dispatchTouchEvent(event)
        event.recycle()
    }
    touch(MotionEvent.ACTION_DOWN, points.first())
    points.drop(1).forEachIndexed { i, point -> webView.postDelayed({ touch(MotionEvent.ACTION_MOVE, point) }, stepMs * (i + 1)) }
    webView.postDelayed({
        touch(MotionEvent.ACTION_UP, points.last())
        onDone()
    }, stepMs * points.size + holdMs)
}

private const val TAP_MS = 60L
private const val DRAG_STEPS = 12
private const val DRAG_STEP_MS = 25L
private const val DRAG_HOLD_MS = 250L
