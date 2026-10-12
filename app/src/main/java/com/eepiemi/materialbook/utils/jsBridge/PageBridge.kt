package com.eepiemi.materialbook.utils.jsBridge

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.eepiemi.materialbook.utils.isFacebookWebUrl
import org.json.JSONObject

// The page object the app's scripts post their one-way calls to (settings button, theme
// colour, video state, Messages layer navigation). Scripts fall back to the old named bridges
// (SettingsBridge, ThemeBridge, PipBridge, ScreenBridge, LayerBridge) when it's missing.
const val PAGE_LISTENER_NAME = "AstryxPage"

/** The page -> app calls a WebView takes; null ones aren't offered to its page at all. */
class PageBridges(
    val settings: MaterialbookSettings? = null,
    val theme: ThemeChange? = null,
    val pip: PipBridge? = null,
    val screen: ScreenBridge? = null,
    val layer: LayerBridge? = null,
    // "Share via…" in Facebook's share sheet: a Facebook link to hand to the Android share sheet.
    val share: ((String) -> Unit)? = null,
)

/**
 * Exposes [bridges] to the page the same way as downloads (installDownloadBridge): with
 * WebView's message listener only the main frame of a Facebook/Messenger page can reach them,
 * not iframes or other origins (an ad could otherwise open Settings, set the bar colour, fake
 * a playing video to keep the screen on or move PiP). Older WebViews fall back to the JS
 * interfaces. Messages arrive on the UI thread.
 */
fun installPageBridges(webView: WebView, bridges: PageBridges) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
        bridges.settings?.let { webView.addJavascriptInterface(it, "SettingsBridge") }
        bridges.theme?.let { webView.addJavascriptInterface(it, "ThemeBridge") }
        bridges.pip?.let { webView.addJavascriptInterface(it, "PipBridge") }
        bridges.screen?.let { webView.addJavascriptInterface(it, "ScreenBridge") }
        bridges.layer?.let { webView.addJavascriptInterface(it, "LayerBridge") }
        return
    }
    WebViewCompat.addWebMessageListener(webView, PAGE_LISTENER_NAME, FACEBOOK_PAGE_ORIGINS) { _, message, sourceOrigin, isMainFrame, _ ->
        if (!isMainFrame || !isFacebookWebUrl(sourceOrigin.toString())) return@addWebMessageListener
        message.data?.let { dispatchPageMessage(it, bridges) }
    }
}

/**
 * Runs one page message, `{"call": name, ...}`, on [bridges]. Unknown calls, calls to a bridge
 * this page doesn't have and malformed messages are ignored. Returns whether one ran.
 */
fun dispatchPageMessage(data: String, bridges: PageBridges): Boolean {
    val msg = runCatching { JSONObject(data) }.getOrNull() ?: return false
    when (msg.optString("call")) {
        "openSettings" -> bridges.settings?.onSettingsToggle() ?: return false
        "themeColor" -> bridges.theme?.onThemeColorChanged(msg.optString("color").takeIf { it.isNotEmpty() })
            ?: return false
        "videoState" -> bridges.pip?.setVideoState(
            msg.optBoolean("playing"), msg.optInt("width"), msg.optInt("height")
        ) ?: return false
        "pipAnomaly" -> bridges.pip?.logPipAnomaly(msg.optString("message")) ?: return false
        "pageVisible" -> bridges.pip?.onPageVisible() ?: return false
        "screenVideoPlaying" -> bridges.screen?.setVideoPlaying(msg.optBoolean("playing")) ?: return false
        "shareLink" -> {
            val share = bridges.share ?: return false
            val url = msg.optString("url")
            if (url.length > MAX_SHARE_URL_LENGTH || !isFacebookWebUrl(url)) return false
            share(url)
        }
        "layerUrl" -> bridges.layer?.onUrlChanged(msg.optString("url").takeIf { it.isNotEmpty() } ?: return false)
            ?: return false
        else -> return false
    }
    return true
}

private const val MAX_SHARE_URL_LENGTH = 2048
