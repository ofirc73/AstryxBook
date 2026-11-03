package com.astryx.book.utils.jsBridge
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.webkit.WebView
import android.widget.Toast
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.eepiemi.materialbook.R
import com.astryx.book.utils.FEED_COMPOSER_PAGE_URL
import com.astryx.book.utils.ShareMediaQueue
import com.astryx.book.utils.ShareTarget
import com.astryx.book.utils.SharedFile
import com.astryx.book.utils.clearSharedMedia
import com.astryx.book.utils.isFacebookWebUrl
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import com.eepiemi.materialbook.utils.jsBridge.FACEBOOK_PAGE_ORIGINS

// The page object share_media.js asks for the shared files through.
const val SHARE_LISTENER_NAME = "AstryxShare"

private val reader = Executors.newSingleThreadExecutor()

/**
 * Feed shares from Home since the app started: taps on Home's composer box, and the composer's loads
 * where the box couldn't open it (the live check reads them).
 */
internal object HomeComposer {
    val taps = AtomicInteger()
    val loads = AtomicInteger()
}

/**
 * Hands the files of the share in progress ([ShareMediaQueue]) to share_media.js: only the main
 * frame of a Facebook page can ask, like the other page bridges. A file goes as a header (JSON:
 * name, type) followed by its bytes, as an ArrayBuffer where WebView supports one, else as base64.
 * Without WebView's message listener (WebView older than 83) there's no way to share.
 *
 * Page messages (JSON), each with the share's target (ShareTarget.key): {call: "next"} (one
 * file), {call: "all"} (every file left), {call: "finished"}, {call: "failed", reason}; and, while
 * a feed share's files wait, {call: "tap", x, y, width} (a real tap on Home's composer box, at a
 * point of the visual viewport, [width] CSS px wide) and {call: "openComposer", reason} (loads
 * the composer, where Home's box couldn't open it).
 */
fun installShareMediaBridge(webView: WebView, context: Context) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
    val binary = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_ARRAY_BUFFER)
    val main = Handler(Looper.getMainLooper())
    WebViewCompat.addWebMessageListener(webView, SHARE_LISTENER_NAME, FACEBOOK_PAGE_ORIGINS) { _, message, origin, isMainFrame, reply ->
        if (!isMainFrame || !isFacebookWebUrl(origin.toString())) return@addWebMessageListener
        val msg = message.data?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return@addWebMessageListener
        val target = ShareTarget.ofKey(msg.optString("target"))
        when (msg.optString("call")) {
            "next" -> {
                val file = target?.let { ShareMediaQueue.take(it) }
                if (file == null) reply.postMessage(DONE)
                else sendFiles(reply, listOf(file), binary, main, remaining = ShareMediaQueue.remaining(target))
            }
            "all" -> {
                val files = target?.let { ShareMediaQueue.takeAll(it) }.orEmpty()
                if (files.isEmpty()) reply.postMessage(DONE) else sendFiles(reply, files, binary, main, remaining = 0)
            }
            "finished" -> finishIfDone(context, target)
            "tap" -> if (target == ShareTarget.FEED && ShareMediaQueue.pending(target)) {
                HomeComposer.taps.incrementAndGet()
                tapPage(webView, msg.optDouble("x"), msg.optDouble("y"), msg.optDouble("width"))
            }
            "openComposer" -> if (target == ShareTarget.FEED && ShareMediaQueue.pending(target)) {
                Log.i("Astryxbook", "Share: Home's composer box didn't open the composer (${msg.optString("reason")}): loading it")
                HomeComposer.loads.incrementAndGet()
                webView.loadUrl(FEED_COMPOSER_PAGE_URL)
            }
            "failed" -> {
                Log.w("Astryxbook", "Share: the page didn't take the files (${msg.optString("reason")})")
                val text = if (msg.optString("reason") == "noComposer") R.string.share_media_no_composer
                else R.string.share_media_failed
                Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                ShareMediaQueue.clear()
                clearSharedMedia(context)
            }
        }
    }
}

private const val DONE = "{\"done\":true}"

// A finger's tap on [webView] at ([x], [y]), CSS px of a visual viewport [width] px wide: unlike a
// script's click() it counts as the user's, so the history entry the page then adds stays for Back.
private fun tapPage(webView: WebView, x: Double, y: Double, width: Double) {
    if (!(width > 0) || x.isNaN() || y.isNaN()) return
    val scale = webView.width / width
    val downAt = SystemClock.uptimeMillis()
    fun touch(action: Int, at: Long) {
        val event = MotionEvent.obtain(downAt, at, action, (x * scale).toFloat(), (y * scale).toFloat(), 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        webView.dispatchTouchEvent(event)
        event.recycle()
    }
    touch(MotionEvent.ACTION_DOWN, downAt)
    webView.postDelayed({ touch(MotionEvent.ACTION_UP, SystemClock.uptimeMillis()) }, TAP_MS)
}

private const val TAP_MS = 60L

// The header, then each file's bytes, read off the main thread and posted on it.
private fun sendFiles(reply: JavaScriptReplyProxy, files: List<SharedFile>, binary: Boolean, main: Handler, remaining: Int) {
    val header = JSONObject()
        .put("count", files.size)
        .put("remaining", remaining)
        .put("base64", !binary)
        .put("files", JSONArray(files.map { JSONObject().put("name", it.name).put("type", it.mimeType) }))
    reply.postMessage(header.toString())
    reader.execute {
        files.forEach { shared ->
            val bytes = runCatching { shared.file.readBytes() }
                .onFailure { Log.w("Astryxbook", "Share: couldn't read a copy", it) }
                .getOrDefault(ByteArray(0))
            main.post {
                if (binary) reply.postMessage(bytes)
                else reply.postMessage(Base64.encodeToString(bytes, Base64.NO_WRAP))
            }
        }
    }
}

// The page is through with the share: drop the copies once nothing's left to offer.
private fun finishIfDone(context: Context, target: ShareTarget?) {
    if (target == null || ShareMediaQueue.remaining(target) == 0) {
        ShareMediaQueue.clear()
        clearSharedMedia(context)
    }
}
