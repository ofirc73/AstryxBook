package com.astryx.book.utils.jsBridge
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.WebView
import android.widget.Toast
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.eepiemi.materialbook.R
import com.astryx.book.utils.ShareMediaQueue
import com.astryx.book.utils.ShareTarget
import com.astryx.book.utils.SharedFile
import com.astryx.book.utils.clearSharedMedia
import com.astryx.book.utils.isFacebookWebUrl
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import com.eepiemi.materialbook.utils.jsBridge.FACEBOOK_PAGE_ORIGINS

// The page object share_media.js asks for the shared files through.
const val SHARE_LISTENER_NAME = "AstryxShare"

private val reader = Executors.newSingleThreadExecutor()

/**
 * Hands the files of the share in progress ([ShareMediaQueue]) to share_media.js: only the main
 * frame of a Facebook page can ask, like the other page bridges. A file goes as a header (JSON:
 * name, type) followed by its bytes, as an ArrayBuffer where WebView supports one, else as base64.
 * Without WebView's message listener (WebView older than 83) there's no way to share.
 *
 * Page messages (JSON), each with the share's target (ShareTarget.key): {call: "next"} (one
 * file), {call: "all"} (every file left), {call: "finished"}, {call: "failed", reason}.
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
