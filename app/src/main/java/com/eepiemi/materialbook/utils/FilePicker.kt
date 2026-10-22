package com.eepiemi.materialbook.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.multiplatform.webview.web.AccompanistWebChromeClient
import com.multiplatform.webview.web.PlatformWebViewParams

// src: https://github.com/KevinnZou/compose-webview-multiplatform

/**
 * How many pages a WebView has started loading (new pages only: Facebook's in-page navigation
 * starts none). Effects that act on each page once it has loaded key on it next to the loading
 * state: a page that starts and finishes loading within one frame (a test page, a page from the
 * cache) leaves the loading state at Finished, so without it they never ran on that page (the
 * app's scripts, PiP, sharing).
 */
@Stable
class PageStarts {
    var count by mutableIntStateOf(0)
        internal set
}

/**
 * The WebView clients every WebView in the app gets: file picker, fullscreen video and camera/
 * microphone requests (chrome client), and [onRendererGone] when the page's renderer process is
 * lost (see RendererGoneClient), after leaving fullscreen: its video view is gone with it.
 */
@Composable
fun appWebViewParams(
    fullscreen: FullscreenController,
    pageStarts: PageStarts,
    onRendererGone: (WebView) -> Unit,
): PlatformWebViewParams {
    val currentOnRendererGone by rememberUpdatedState(onRendererGone)
    val webViewClient = remember(fullscreen, pageStarts) {
        RendererGoneClient(onPageStarted = { pageStarts.count++ }) { view ->
            fullscreen.hide()
            currentOnRendererGone(view)
        }
    }

    var fileChooserIntent by remember { mutableStateOf<Intent?>(null) }

    // Messenger calls: the camera/microphone request waiting for Android's permission prompt.
    val context = LocalContext.current
    var pendingMediaRequest by remember { mutableStateOf<PendingMediaRequest?>(null) }
    val mediaPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val pending = pendingMediaRequest ?: return@rememberLauncherForActivityResult
            pendingMediaRequest = null
            pending.grantWhatAndroidAllows(context)
        }

    val webViewChromeClient =
        remember(fullscreen) {
            AppWebChromeClient(
                fullscreen,
                onShowFilePicker = { fileChooserIntent = it },
                onMediaRequest = { request, resources ->
                    val pending = PendingMediaRequest(request, resources)
                    val missing = pending.missingPermissions(context)
                    if (missing.isEmpty()) {
                        pending.grantWhatAndroidAllows(context)
                    } else {
                        // One prompt at a time: a newer request replaces (and denies) an older one.
                        pendingMediaRequest?.request?.deny()
                        pendingMediaRequest = pending
                        runCatching { mediaPermissionLauncher.launch(missing) }
                            .onFailure { pendingMediaRequest = null; request.deny() }
                    }
                },
                onMediaRequestCanceled = { request ->
                    if (pendingMediaRequest?.request == request) pendingMediaRequest = null
                },
            )
        }

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result: ActivityResult ->
            if (result.resultCode != Activity.RESULT_OK) {
                webViewChromeClient.cancelFileChooser()
                return@rememberLauncherForActivityResult
            }

            val intent = result.data
            if (intent == null) {
                webViewChromeClient.cancelFileChooser()
                return@rememberLauncherForActivityResult
            }

            val singleFile: Uri? = intent.data
            val multiFiles: List<Uri>? = intent.getUris()

            when {
                singleFile != null -> webViewChromeClient.onReceiveFiles(arrayOf(singleFile))
                multiFiles != null -> webViewChromeClient.onReceiveFiles(multiFiles.toTypedArray())
                else -> {
                    webViewChromeClient.cancelFileChooser()
                }
            }
        }

    LaunchedEffect(key1 = fileChooserIntent) {
        fileChooserIntent?.let {
            try {
                launcher.launch(fileChooserIntent!!)
            } catch (_: ActivityNotFoundException) {
                webViewChromeClient.cancelFileChooser()
            }
        }
    }

    return PlatformWebViewParams(client = webViewClient, chromeClient = webViewChromeClient)
}

private fun Intent.getUris(): List<Uri>? {
    val clipData = clipData ?: return null
    return (0 until clipData.itemCount).map { clipData.getItemAt(it).uri }
}

/** A page's camera/microphone request and the web resources it may get (see webMediaGrant). */
private class PendingMediaRequest(val request: PermissionRequest, val resources: List<String>) {
    fun missingPermissions(context: Context): Array<String> =
        resources.map(::androidPermissionFor).distinct()
            .filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
            .toTypedArray()

    // Grants each resource whose runtime permission the app holds; denies if none.
    fun grantWhatAndroidAllows(context: Context) {
        val allowed = resources.filter {
            ContextCompat.checkSelfPermission(context, androidPermissionFor(it)) == PackageManager.PERMISSION_GRANTED
        }
        runCatching {
            if (allowed.isEmpty()) request.deny() else request.grant(allowed.toTypedArray())
        }
    }
}

private class AppWebChromeClient(
    private val fullscreen: FullscreenController,
    private val onShowFilePicker: (Intent) -> Unit,
    private val onMediaRequest: (PermissionRequest, List<String>) -> Unit,
    private val onMediaRequestCanceled: (PermissionRequest) -> Unit,
) : AccompanistWebChromeClient() {
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    // HTML5 fullscreen: without these two, element.requestFullscreen() from
    // the page (Facebook's fullscreen button) goes nowhere.
    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        if (view == null || callback == null) return
        fullscreen.show(view, callback)
    }

    override fun onHideCustomView() {
        fullscreen.hide()
    }

    // Camera and microphone for Messenger calls: Facebook's own pages only (webMediaGrant),
    // and only once Android has given the app the matching permission. Everything else is
    // denied, as before (WebChromeClient's default).
    override fun onPermissionRequest(request: PermissionRequest) {
        val resources = webMediaGrant(request.origin.toString(), request.resources)
        if (resources == null) request.deny() else onMediaRequest(request, resources)
    }

    override fun onPermissionRequestCanceled(request: PermissionRequest) {
        onMediaRequestCanceled(request)
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?,
    ): Boolean {
        this.filePathCallback = filePathCallback
        val filePickerIntent = fileChooserParams?.createIntent()

        if (filePickerIntent == null) cancelFileChooser()
        else onShowFilePicker(filePickerIntent)

        return true
    }

    fun onReceiveFiles(uris: Array<Uri>) {
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
    }

    fun cancelFileChooser() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
    }
}