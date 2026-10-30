package com.eepiemi.materialbook.utils

import com.astryx.book.utils.intentFallbackUrl
import com.astryx.book.utils.isDesktopMessagesUrl
import com.astryx.book.utils.isFacebookWebUrl
import com.astryx.book.utils.isMessagesLink
import com.astryx.book.utils.messagesDesktopUrl
import com.multiplatform.webview.request.RequestInterceptor
import com.multiplatform.webview.request.WebRequest
import com.multiplatform.webview.request.WebRequestInterceptResult
import com.multiplatform.webview.web.WebViewNavigator

class ExternalRequestInterceptor(
    private val handleExternalUrl: (String) -> Unit,
    private val tryOpenMessagesDesktop: (String) -> Boolean = { false },
    private val isDesktopView: () -> Boolean = { false },
    private val onIntentFallback: (String) -> Unit = {},
) : RequestInterceptor {

    override fun onInterceptUrlRequest(
        request: WebRequest,
        navigator: WebViewNavigator
    ): WebRequestInterceptResult {

        // Frames inside the page (ads, embeds) load as they are but never leave the app or
        // launch another one: only the page itself can do that (as in the Messages layer).
        if (!request.isForMainFrame) return WebRequestInterceptResult.Allow

        if (isMessagesLink(request.url)) {
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
        intentFallbackUrl(request.url)?.takeIf { isFacebookWebUrl(it) }?.let { fallback ->
            onIntentFallback(fallback)
            navigator.loadUrl(fallback)
            return WebRequestInterceptResult.Reject
        }

        return if (isFacebookWebUrl(request.url)) {
            WebRequestInterceptResult.Allow
        } else {
            handleExternalUrl(fbRedirectSanitizer(request.url))
            WebRequestInterceptResult.Reject
        }
    }
}