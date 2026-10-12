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
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.eepiemi.materialbook.R
import com.eepiemi.materialbook.utils.isFacebookWebUrl
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
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

            val fileName = downloadFileName(Date(), extension)

            // Saving the same photo or video again: point at the first copy instead of a second file.
            val hash = MessageDigest.getInstance("SHA-256").digest(finalData)
                .joinToString("") { "%02x".format(it) }
            synchronized(savedThisSession) { savedThisSession[hash] }?.let { uri ->
                saved(SavedDownload(uri, finalMimeType, alreadySaved = true))
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, finalMimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(finalData)
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)

                    synchronized(savedThisSession) { savedThisSession[hash] = uri }
                    saved(SavedDownload(uri, finalMimeType, alreadySaved = false))
                } ?: toast(R.string.failed_to_save_file)
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)

                FileOutputStream(file).use { it.write(finalData) }
                toast(R.string.saved_to_downloads)
            }
        }.onFailure {
            toast(R.string.failed_to_save_file)
        }
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
        // Content hash -> the file it was saved as, for this run of the app.
        private val savedThisSession = mutableMapOf<String, Uri>()
    }
}
