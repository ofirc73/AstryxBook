package com.astryx.book.utils
import android.webkit.WebView
import com.multiplatform.webview.web.WebViewState

/**
 * Settings every WebView in the app gets, called from its onCreated. File access and Safe
 * Browsing are compose-webview's defaults today (it applies its settings right after
 * onCreated); they're set here explicitly so a library update can't change them unnoticed.
 * Geolocation isn't one of the library's settings: it's switched off on the WebView itself
 * (Facebook never needs it, and no prompt handler would grant it anyway).
 *
 * Content access stays at Android's default: file uploads from the picker are content://
 * URIs, and web pages can't load content:// resources in any case.
 */
fun hardenWebView(state: WebViewState, webView: WebView) {
    state.webSettings.androidWebSettings.apply {
        allowFileAccess = false
        safeBrowsingEnabled = true
    }
    state.webSettings.allowFileAccessFromFileURLs = false
    state.webSettings.allowUniversalAccessFromFileURLs = false
    webView.settings.setGeolocationEnabled(false)
}
