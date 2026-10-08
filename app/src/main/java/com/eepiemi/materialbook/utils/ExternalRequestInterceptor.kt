package com.eepiemi.materialbook.utils

import com.multiplatform.webview.request.RequestInterceptor
import com.multiplatform.webview.request.WebRequest
import com.multiplatform.webview.request.WebRequestInterceptResult
import com.multiplatform.webview.web.WebViewNavigator

class ExternalRequestInterceptor(
    private val handleExternalUrl: (String) -> Unit,
    private val tryOpenMessagesDesktop: (String) -> Boolean = { false },
    private val isDesktopView: () -> Boolean = { false },
) : RequestInterceptor {

    override fun onInterceptUrlRequest(
        request: WebRequest,
        navigator: WebViewNavigator
    ): WebRequestInterceptResult {

        if (request.isForMainFrame && isMessagesLink(request.url)) {
            // Messages/Messenger entry points open in the Messages layer (desktop site in its
            // own WebView) when enabled, so this page stays where it is underneath.
            if (tryOpenMessagesDesktop(request.url)) return WebRequestInterceptResult.Reject
            // This view already shows the desktop site (Desktop layout, large screens): open
            // web Messages links (m.me, messenger.com, mobile /messages) here as the desktop
            // Messages page instead of sending them to the browser.
            val url = request.url
            if (isDesktopView() && url.startsWith("http", ignoreCase = true) && !isDesktopMessagesUrl(url)) {
                navigator.loadUrl(messagesDesktopUrl(url))
                return WebRequestInterceptResult.Reject
            }
        }

        // Facebook redirects full loads of some pages (profiles) to its app via intent://;
        // open the web fallback here instead, so the page stays in this app.
        if (request.isForMainFrame) {
            intentFallbackUrl(request.url)?.takeIf { isFacebookWebUrl(it) }?.let { fallback ->
                navigator.loadUrl(fallback)
                return WebRequestInterceptResult.Reject
            }
        }

        return if (isFacebookWebUrl(request.url) && request.isForMainFrame) {
            WebRequestInterceptResult.Allow
        } else {
            handleExternalUrl(fbRedirectSanitizer(request.url))
            WebRequestInterceptResult.Reject
        }
    }
}