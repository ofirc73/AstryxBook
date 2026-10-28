package com.astryx.book.utils
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import java.io.File

/**
 * "Log out and clear data": everything the web side of the app keeps. Cookies (the
 * Facebook login), Web Storage (localStorage, IndexedDB, service workers and their
 * caches), the HTTP cache, form and HTTP-auth data, and the images copied to the
 * clipboard. Shared by every WebView in the app, the Messages layer's included. The app's
 * own settings stay. [onDone] runs on the calling (main) thread once the cookies are gone.
 */
fun clearWebData(context: Context, webView: WebView?, onDone: () -> Unit) {
    webView?.apply {
        stopLoading()
        clearCache(true)
        clearFormData()
        clearHistory()
    }
    WebStorage.getInstance().deleteAllData()
    WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword()
    File(context.cacheDir, "clipboard_images").deleteRecursively()
    val cookies = CookieManager.getInstance()
    cookies.removeAllCookies {
        cookies.flush()
        onDone()
    }
}
