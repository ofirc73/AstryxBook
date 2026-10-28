package com.eepiemi.materialbook.utils.jsBridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.eepiemi.materialbook.R
import com.astryx.book.utils.isFacebookWebUrl
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

// The page object copy_to_clipboard.js posts images to.
const val CLIPBOARD_LISTENER_NAME = "AstryxClipboard"

/**
 * Exposes copy-to-clipboard to the page, the same way as downloads (installDownloadBridge):
 * with WebView's message listener only the main frame of a Facebook/Messenger page can reach
 * it, not iframes (an ad could otherwise fill the clipboard); older WebViews fall back to the
 * `ClipboardBridge` JS interface.
 */
fun installClipboardBridge(webView: WebView, context: Context) {
    val bridge = ClipboardBridge(context)
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
        webView.addJavascriptInterface(bridge, "ClipboardBridge")
        return
    }
    WebViewCompat.addWebMessageListener(webView, CLIPBOARD_LISTENER_NAME, FACEBOOK_PAGE_ORIGINS) { _, message, sourceOrigin, isMainFrame, _ ->
        if (!isMainFrame || !isFacebookWebUrl(sourceOrigin.toString())) return@addWebMessageListener
        val payload = message.data?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return@addWebMessageListener
        val data = payload.optString("data")
        val mimeType = payload.optString("mimeType")
        // Decoding and re-encoding the image doesn't belong on the UI thread.
        DownloadBridge.saver.execute { bridge.copyImageToClipboard(data, mimeType) }
    }
}

class ClipboardBridge(private val context: Context) {
    @JavascriptInterface
    fun copyImageToClipboard(base64Data: String, mimeType: String) {
        runCatching {
            if (!base64Data.contains(",")) {
                toast(R.string.copy_failed_invalid_data)
                return
            }

            val data = Base64.decode(base64Data.split(",")[1], Base64.DEFAULT)

            // Decode bitmap to verify it's a valid image
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            if (bitmap == null) {
                toast(R.string.copy_failed_invalid_image)
                return
            }

            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            // Create a temporary file to hold the image
            val cacheDir = File(context.cacheDir, "clipboard_images")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            // Clean up old cached images
            cacheDir.listFiles()?.forEach { file ->
                if (System.currentTimeMillis() - file.lastModified() > 3600000) { // 1 hour
                    file.delete()
                }
            }

            // Save as PNG for maximum compatibility across apps
            val imageFile = File(cacheDir, "clipboard_${System.currentTimeMillis()}.png")
            FileOutputStream(imageFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )

            val clip = ClipData.newUri(context.contentResolver, "Image", contentUri)
            clipboardManager.setPrimaryClip(clip)

            toast(R.string.image_copied_to_clipboard)
        }.onFailure {
            toast(R.string.failed_to_copy_image)
        }
    }

    // Callable from any thread: the message listener runs this bridge on a background one.
    private fun toast(message: Int) {
        ContextCompat.getMainExecutor(context).execute {
            Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show()
        }
    }
}
