package com.eepiemi.materialbook.utils.jsBridge

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.eepiemi.materialbook.R
import com.astryx.book.utils.httpsUrlOn
import com.astryx.book.utils.isFacebookCdnHost
import com.astryx.book.utils.isFacebookWebUrl
import com.astryx.book.utils.openFacebookPage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.security.DigestInputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

// The page object scripts post downloads to (see scripts.js / download_content.js).
const val DOWNLOAD_LISTENER_NAME = "AstryxDownloads"

// Only Facebook/Messenger frames get the download (and clipboard) object at all.
internal val FACEBOOK_PAGE_ORIGINS = setOf(
    "https://facebook.com", "https://*.facebook.com",
    "https://messenger.com", "https://*.messenger.com"
)

/** A file saved to Downloads; [alreadySaved] when the same file was saved earlier this session. */
data class SavedDownload(val uri: Uri, val mimeType: String, val alreadySaved: Boolean)

/**
 * Saved downloads, for the page to show with an Open action. With nobody listening (the page
 * isn't on screen) the bridge shows a toast instead.
 */
object DownloadEvents {
    private val _saved = MutableSharedFlow<SavedDownload>(extraBufferCapacity = 4)
    val saved: SharedFlow<SavedDownload> = _saved.asSharedFlow()

    internal fun emit(download: SavedDownload): Boolean =
        _saved.subscriptionCount.value > 0 && _saved.tryEmit(download)
}

/** The name a download is saved under: `Facebook_20261012_093100.mp4`. */
fun downloadFileName(time: Date, extension: String): String =
    "Facebook_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(time)}.$extension"

/**
 * The MIME type to save a download as, or null when it isn't a photo or video. Parameters
 * (`;codecs=…`) and case are dropped. Anything else (an APK, an HTML page, an unknown type)
 * is refused, so a page can't drop arbitrary files into Downloads.
 */
fun downloadMimeType(mimeType: String): String? {
    val type = mimeType.substringBefore(';').trim().lowercase()
    val (major, minor) = type.split('/', limit = 2).takeIf { it.size == 2 } ?: return null
    if (major != "image" && major != "video") return null
    if (minor.isEmpty() || !minor.all { it.isLetterOrDigit() || it in ".+-" }) return null
    return type
}

/**
 * Exposes downloads to the page. With WebView's message listener (WebView 83+, nearly every
 * device) only the main frame of a Facebook/Messenger page can reach it, not iframes or other
 * origins; on older WebViews it falls back to the `DownloadBridge` JS interface, which is still
 * limited to photos and videos.
 */
fun installDownloadBridge(webView: WebView, context: Context) {
    val bridge = DownloadBridge(context)
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
        webView.addJavascriptInterface(bridge, "DownloadBridge")
        return
    }
    WebViewCompat.addWebMessageListener(webView, DOWNLOAD_LISTENER_NAME, FACEBOOK_PAGE_ORIGINS) { _, message, sourceOrigin, isMainFrame, _ ->
        if (!isMainFrame || !isFacebookWebUrl(sourceOrigin.toString())) return@addWebMessageListener
        val payload = message.data?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return@addWebMessageListener
        if (payload.has("failed")) {
            bridge.downloadFailed()
            return@addWebMessageListener
        }
        // A photo or video on Facebook's media servers: the app fetches it itself (downloadUrl).
        payload.optString("url").takeIf { it.isNotEmpty() }?.let { url ->
            val isVideo = payload.optBoolean("video")
            DownloadBridge.saver.execute { bridge.downloadUrl(url, isVideo) }
            return@addWebMessageListener
        }
        val data = payload.optString("data")
        val mimeType = payload.optString("mimeType")
        // The listener runs on the UI thread; decoding and writing a video doesn't belong there.
        DownloadBridge.saver.execute { bridge.downloadBase64File(data, mimeType) }
    }
}

class DownloadBridge(private val context: Context) {
    @JavascriptInterface
    @Suppress("unused")
    fun downloadBase64File(base64Data: String, mimeType: String) {
        runCatching {
            val type = downloadMimeType(mimeType)
            if (type == null || !base64Data.contains(",")) {
                toast(R.string.download_failed_invalid_data)
                return
            }

            val data = Base64.decode(base64Data.split(",")[1], Base64.DEFAULT)

            val (finalData, finalMimeType, extension) = if (type.startsWith("image/")) {
                // Convert images to PNG for maximum compatibility; refuse what isn't one.
                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                if (bitmap == null) {
                    toast(R.string.download_failed_invalid_data)
                    return
                }
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                Triple(outputStream.toByteArray(), "image/png", "png")
            } else {
                // Keep videos as-is
                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(type) ?: "mp4"
                Triple(data, type, ext)
            }

            save(ByteArrayInputStream(finalData), finalMimeType, extension)
        }.onFailure {
            toast(R.string.failed_to_save_file)
        }
    }

    /**
     * A photo or video the page shows, at its address on Facebook's media servers
     * (download_content.js): fetched here and written to Downloads as it arrives. The page used to
     * fetch it and hand over the whole file as one base64 string, which a long video couldn't
     * survive (several times the file in memory, against a 192 MB heap). Only Facebook's media
     * servers, over https, redirects included; only a photo or video, as the server labels it.
     */
    fun downloadUrl(url: String, isVideo: Boolean) {
        val address = httpsUrlOn(url, ::isFacebookCdnHost)
        if (address == null) {
            Log.w("Astryxbook", "Download: not an address on Facebook's media servers")
            toast(R.string.download_failed_invalid_data)
            return
        }
        if (isVideo) toast(R.string.download_started)
        runCatching {
            val connection = openFacebookPage(
                address, mapOf("User-Agent" to WebSettings.getDefaultUserAgent(context)), ::isFacebookCdnHost
            ) ?: error("redirected off Facebook's media servers")
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${connection.responseCode}")
                val type = downloadMimeType(connection.contentType.orEmpty())
                    ?: error("not a photo or video (${connection.contentType})")
                val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(type)
                    ?: if (type.startsWith("video/")) "mp4" else "jpg"
                connection.inputStream.use { save(it, type, extension) }
            } finally {
                connection.disconnect()
            }
        }.onFailure {
            Log.w("Astryxbook", "Download failed (${it.javaClass.simpleName}: ${it.message})")
            toast(if (isVideo) R.string.video_download_unavailable else R.string.failed_to_save_file)
        }
    }

    /**
     * Writes [input] to Downloads as it reads it (never whole in memory), named for now, and says
     * so. The same file saved earlier in this run of the app (by its SHA-256) isn't kept twice:
     * the new copy goes and the first is pointed at. Throws when it can't be written.
     */
    internal fun save(input: InputStream, mimeType: String, extension: String) {
        val fileName = downloadFileName(Date(), extension)
        val digest = MessageDigest.getInstance("SHA-256")
        val stream = DigestInputStream(input, digest)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
            val bytes = FileOutputStream(file).use { stream.copyTo(it, COPY_BUFFER) }
            Log.d("Astryxbook", "Download saved: $bytes bytes ($mimeType)")
            toast(R.string.saved_to_downloads)
            return
        }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Downloads refused a new file")
        val bytes = try {
            resolver.openOutputStream(uri)!!.use { stream.copyTo(it, COPY_BUFFER) }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        synchronized(savedThisSession) { savedThisSession[hash] }?.let { first ->
            resolver.delete(uri, null, null)
            saved(SavedDownload(first, mimeType, alreadySaved = true))
            return
        }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        synchronized(savedThisSession) { savedThisSession[hash] = uri }
        // Read by the live check (its size, its type).
        Log.d("Astryxbook", "Download saved: $bytes bytes ($mimeType)")
        saved(SavedDownload(uri, mimeType, alreadySaved = false))
    }

    // The page found no file it could download for the video on screen (download_content.js).
    @JavascriptInterface
    @Suppress("unused")
    fun downloadFailed() = toast(R.string.video_download_unavailable)

    private fun saved(download: SavedDownload) {
        if (!DownloadEvents.emit(download)) {
            toast(if (download.alreadySaved) R.string.download_already_saved else R.string.saved_to_downloads)
        }
    }

    private fun toast(message: Int) {
        ContextCompat.getMainExecutor(context).execute {
            Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        internal val saver = Executors.newSingleThreadExecutor()
        private const val COPY_BUFFER = 64 * 1024
        // Content hash -> the file it was saved as, for this run of the app.
        private val savedThisSession = mutableMapOf<String, Uri>()
    }
}
